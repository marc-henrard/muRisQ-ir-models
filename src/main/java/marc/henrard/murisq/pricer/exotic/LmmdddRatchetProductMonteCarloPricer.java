/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.exotic;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.random.RandomNumberGenerator;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentSchedule;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;
import marc.henrard.murisq.pricer.montecarlo.MonteCarloMultiDatesPricer;
import marc.henrard.murisq.product.rate.IborRatchetRateComputation;

import java.util.Map;
import java.util.NoSuchElementException;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * Monte Carlo pricer for swaptions with physical settlement in the Libor Market Model with
 * deterministic multiplicative spread.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public final class LmmdddRatchetProductMonteCarloPricer 
    implements MonteCarloMultiDatesPricer<ResolvedSwap, LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters>, 
    ImmutableBean, Serializable {

  /** The number of paths */
  @PropertyDefinition
  private final int nbPaths;
  /** The number of paths in a computation block */
  @PropertyDefinition
  private final int pathNumberBlock;
  /** The model */
  @PropertyDefinition(validate = "notNull")
  private final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model;
  /** The random number generator. */
  @PropertyDefinition(validate = "notNull")
  private final RandomNumberGenerator numberGenerator;
  /** The methods related to the model evolution. */
  @PropertyDefinition(validate = "notNull")
  private final LiborMarketModelMonteCarloEvolution evolution;
  
  
  @Override
  public int getNbFactors() {
    return model.getFactorCount();
  }

  @Override
  public MulticurveEquivalentSchedule multicurveEquivalent(ResolvedSwap product) {
    ArgChecker.isTrue(product.getLegs().size()==1, "product must have one leg");
    ResolvedSwapLeg leg = product.getLegs().get(0);
    ArgChecker.isTrue(leg.getType().equals(SwapLegType.OTHER), "leg must be of type OTHER");
    ImmutableList<SwapPaymentPeriod> periods = leg.getPaymentPeriods();
    List<MulticurveEquivalent> schedules = new ArrayList<>();
    for(SwapPaymentPeriod p: periods) {
      ArgChecker.isTrue(p instanceof RatePaymentPeriod, "payment periods must be of type RatePaymentPeriod");
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) p;
      ArgChecker.isTrue(ratePeriod.getAccrualPeriods().size()==1, "one accrual per payment period");
      ArgChecker.isTrue(ratePeriod.getAccrualPeriods().get(0).getRateComputation() instanceof IborRatchetRateComputation,
          "rate computation must be of type IborRatchetRateComputation");
      RateAccrualPeriod accrualPeriod = ratePeriod.getAccrualPeriods().get(0);
      IborRatchetRateComputation ratchetPeriod = (IborRatchetRateComputation) accrualPeriod.getRateComputation();
      schedules.add(
          MulticurveEquivalent.of(
              ratchetPeriod.getIndex().calculateFixingDateTime(ratchetPeriod.getFixingDate()), // fixing time
              ImmutableList.of(), // No fix payment
              ImmutableList.of(IborRateComputation.of(ratchetPeriod.getObservation())),
              ImmutableList.of(NotionalExchange.of(Payment.of(ratchetPeriod.getCurrency(),
                  accrualPeriod.getYearFraction() * ratePeriod.getNotional(), ratePeriod.getPaymentDate()))),
              // Amount: accrual factor * notional
              ImmutableList.of(), // No ON coupon
              ImmutableList.of()));
    }
    return MulticurveEquivalentSchedule.of(schedules);
  }

  @Override
  public double getNumeraireValue(RatesProvider multicurve) {
    // The pseudo-numeraire is the pseudo-discount factor on the last model date.
    DoubleArray iborTimes = model.getIborTimes();
    double numeraireTime = iborTimes.get(iborTimes.size() - 1);
    // Curve and model time measure must be compatible
    return multicurve.discountFactors(model.getCurrency()).discountFactor(numeraireTime);
  }

  @Override
  public MulticurveEquivalentValues initialValues(
      MulticurveEquivalentSchedule mce, 
      RatesProvider multicurve,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model) {
    
    // Model is on dsc forward rate, i.e. DSC forward on LIBOR periods, not instrument dependent
    DoubleArray iborTimes = model.getIborTimes();
    Currency ccy = model.getCurrency();
    DiscountFactors dsc = multicurve.discountFactors(ccy);
    double[] fwdDsc = new double[iborTimes.size() - 1];
    for (int i = 0; i < iborTimes.size() - 1; i++) {
      fwdDsc[i] = (dsc.discountFactor(iborTimes.get(i)) / dsc.discountFactor(iborTimes.get(i + 1)) - 1.0d) /
          model.getAccrualFactors().get(i);
    }
    // The forward rates are stored in ON equivalent values
    return MulticurveEquivalentValues.builder().onRates(DoubleArray.ofUnsafe(fwdDsc)).build();
  }

  @Override
  public List<List<MulticurveEquivalentValues>> evolve(
      MulticurveEquivalentValues initialValues,
      List<ZonedDateTime> expiries,
      int numberSample) {
    
    return evolution.evolveMultiSteps(expiries, initialValues, model, numberGenerator, numberSample);
  }

  @Override
  public double[][] aggregation( // path x cash flows
      MulticurveEquivalentSchedule me,
      ResolvedSwap product, 
      List<List<MulticurveEquivalentValues>> valuesExpiries, // dimensions: paths x expiry
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model){

    int nbPaths = valuesExpiries.size();
    int nbFixings = me.getExpiriesCount();
    ArgChecker.isTrue(product.getLegs().size()==1, "product must have one leg");
    ResolvedSwapLeg leg = product.getLegs().get(0);
    ArgChecker.isTrue(leg.getType().equals(SwapLegType.OTHER), "leg must be of type OTHER");
    ImmutableList<SwapPaymentPeriod> periods = leg.getPaymentPeriods();
    double[] effectiveTimes = new double[nbFixings];
    double[] paymentTimes = new double[nbFixings];
    double[] amounts = new double[nbFixings];
    IborRatchetRateComputation[] ratchetPeriods = new IborRatchetRateComputation[nbFixings];
    for (int loopfixing = 0; loopfixing < nbFixings; loopfixing++) {
      SwapPaymentPeriod p = periods.get(loopfixing);
      ArgChecker.isTrue(p instanceof RatePaymentPeriod, "payment periods must be of type RatePaymentPeriod");
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) p;
      ArgChecker.isTrue(ratePeriod.getAccrualPeriods().size() == 1, "one accrual per payment period");
      ArgChecker.isTrue(
          ratePeriod.getAccrualPeriods().get(0).getRateComputation() instanceof IborRatchetRateComputation,
          "rate computation must be of type IborRatchetRateComputation");
      RateAccrualPeriod accrualPeriod = ratePeriod.getAccrualPeriods().get(0);
      ratchetPeriods[loopfixing] = (IborRatchetRateComputation) accrualPeriod.getRateComputation();
      effectiveTimes[loopfixing] =
          model.getTimeMeasure().relativeTime(model.getValuationDate(), ratchetPeriods[loopfixing].getEffectiveDate());
      paymentTimes[loopfixing] =
          model.getTimeMeasure().relativeTime(model.getValuationDate(), ratePeriod.getPaymentDate());
      amounts[loopfixing] = me.getSchedules().get(loopfixing).getIborPayments().get(0).getPaymentAmount().getAmount();
    }
    int[] indexIborTimes = model.getIborTimeIndex(effectiveTimes);
    int[] indexPaymentTimes = model.getIborTimeIndex(paymentTimes);

    double[][] pv = new double[nbPaths][nbFixings];
    for (int looppath = 0; looppath < nbPaths; looppath++) { // loop paths
      double[] ratchetRates = new double[nbFixings + 1]; // one extra dim to facilitate recursion
      for (int loopfixing = 0; loopfixing < nbFixings; loopfixing++) { // loop expiries
        MulticurveEquivalentValues valuePathExpiry = valuesExpiries.get(looppath).get(loopfixing);
        double[] valueFwdPathExpiry = valuePathExpiry.getOnRates().toArrayUnsafe();
        double[] discounting = discounting(model, valuePathExpiry);
        double iborRate = model
            .iborRateFromDscForwards(valueFwdPathExpiry[indexIborTimes[loopfixing]], indexIborTimes[loopfixing]);
        ratchetRates[loopfixing + 1] = ratchetPeriods[loopfixing].rate(ratchetRates[loopfixing], iborRate);
        pv[looppath][loopfixing] = amounts[loopfixing] * ratchetRates[loopfixing + 1] 
            * discounting[indexPaymentTimes[loopfixing]];
      } // end loop expiries
    } // end loop paths
    return pv;
  }
  
  /**
   * Returns the numeraire rebased discount factors at the different LMM dates.
   * <p>
   * The numeraire is the discount factor at the last date, hence the last discounting is 1 and the 
   * one at other dates are above one for positive rates.
   * 
   * @param model  the interest rate model
   * @param valuesExpiry  the modeled values at expiry
   * @return  the rebased discount factors, dimension: IBOR dates
   */
  public double[] discounting(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      MulticurveEquivalentValues valuesExpiry) {

    int nbFwdPeriods = model.getIborPeriodsCount();
    double[] delta = model.getAccrualFactors().toArrayUnsafe();
    double[] discounting = new double[nbFwdPeriods + 1];
    double[] valueFwdPath = valuesExpiry.getOnRates().toArrayUnsafe();
    discounting[nbFwdPeriods] = 1.0;
    for (int loopdsc = nbFwdPeriods - 1; loopdsc >= 0; loopdsc--) {
      discounting[loopdsc] =
          discounting[loopdsc + 1] * (1.0 + valueFwdPath[loopdsc] * delta[loopdsc]);
    }
    return discounting;
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code LmmdddRatchetProductMonteCarloPricer}.
   * @return the meta-bean, not null
   */
  public static LmmdddRatchetProductMonteCarloPricer.Meta meta() {
    return LmmdddRatchetProductMonteCarloPricer.Meta.INSTANCE;
  }

  static {
    MetaBean.register(LmmdddRatchetProductMonteCarloPricer.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static LmmdddRatchetProductMonteCarloPricer.Builder builder() {
    return new LmmdddRatchetProductMonteCarloPricer.Builder();
  }

  private LmmdddRatchetProductMonteCarloPricer(
      int nbPaths,
      int pathNumberBlock,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      RandomNumberGenerator numberGenerator,
      LiborMarketModelMonteCarloEvolution evolution) {
    JodaBeanUtils.notNull(model, "model");
    JodaBeanUtils.notNull(numberGenerator, "numberGenerator");
    JodaBeanUtils.notNull(evolution, "evolution");
    this.nbPaths = nbPaths;
    this.pathNumberBlock = pathNumberBlock;
    this.model = model;
    this.numberGenerator = numberGenerator;
    this.evolution = evolution;
  }

  @Override
  public LmmdddRatchetProductMonteCarloPricer.Meta metaBean() {
    return LmmdddRatchetProductMonteCarloPricer.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the number of paths
   * @return the value of the property
   */
  public int getNbPaths() {
    return nbPaths;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the number of paths in a computation block
   * @return the value of the property
   */
  public int getPathNumberBlock() {
    return pathNumberBlock;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the model
   * @return the value of the property, not null
   */
  public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters getModel() {
    return model;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the random number generator.
   * @return the value of the property, not null
   */
  public RandomNumberGenerator getNumberGenerator() {
    return numberGenerator;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the methods related to the model evolution.
   * @return the value of the property, not null
   */
  public LiborMarketModelMonteCarloEvolution getEvolution() {
    return evolution;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      LmmdddRatchetProductMonteCarloPricer other = (LmmdddRatchetProductMonteCarloPricer) obj;
      return (nbPaths == other.nbPaths) &&
          (pathNumberBlock == other.pathNumberBlock) &&
          JodaBeanUtils.equal(model, other.model) &&
          JodaBeanUtils.equal(numberGenerator, other.numberGenerator) &&
          JodaBeanUtils.equal(evolution, other.evolution);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(nbPaths);
    hash = hash * 31 + JodaBeanUtils.hashCode(pathNumberBlock);
    hash = hash * 31 + JodaBeanUtils.hashCode(model);
    hash = hash * 31 + JodaBeanUtils.hashCode(numberGenerator);
    hash = hash * 31 + JodaBeanUtils.hashCode(evolution);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(192);
    buf.append("LmmdddRatchetProductMonteCarloPricer{");
    buf.append("nbPaths").append('=').append(nbPaths).append(',').append(' ');
    buf.append("pathNumberBlock").append('=').append(pathNumberBlock).append(',').append(' ');
    buf.append("model").append('=').append(model).append(',').append(' ');
    buf.append("numberGenerator").append('=').append(numberGenerator).append(',').append(' ');
    buf.append("evolution").append('=').append(JodaBeanUtils.toString(evolution));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code LmmdddRatchetProductMonteCarloPricer}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code nbPaths} property.
     */
    private final MetaProperty<Integer> nbPaths = DirectMetaProperty.ofImmutable(
        this, "nbPaths", LmmdddRatchetProductMonteCarloPricer.class, Integer.TYPE);
    /**
     * The meta-property for the {@code pathNumberBlock} property.
     */
    private final MetaProperty<Integer> pathNumberBlock = DirectMetaProperty.ofImmutable(
        this, "pathNumberBlock", LmmdddRatchetProductMonteCarloPricer.class, Integer.TYPE);
    /**
     * The meta-property for the {@code model} property.
     */
    private final MetaProperty<LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters> model = DirectMetaProperty.ofImmutable(
        this, "model", LmmdddRatchetProductMonteCarloPricer.class, LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class);
    /**
     * The meta-property for the {@code numberGenerator} property.
     */
    private final MetaProperty<RandomNumberGenerator> numberGenerator = DirectMetaProperty.ofImmutable(
        this, "numberGenerator", LmmdddRatchetProductMonteCarloPricer.class, RandomNumberGenerator.class);
    /**
     * The meta-property for the {@code evolution} property.
     */
    private final MetaProperty<LiborMarketModelMonteCarloEvolution> evolution = DirectMetaProperty.ofImmutable(
        this, "evolution", LmmdddRatchetProductMonteCarloPricer.class, LiborMarketModelMonteCarloEvolution.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "nbPaths",
        "pathNumberBlock",
        "model",
        "numberGenerator",
        "evolution");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1723700122:  // nbPaths
          return nbPaths;
        case -1504032417:  // pathNumberBlock
          return pathNumberBlock;
        case 104069929:  // model
          return model;
        case 1709932938:  // numberGenerator
          return numberGenerator;
        case 261136251:  // evolution
          return evolution;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public LmmdddRatchetProductMonteCarloPricer.Builder builder() {
      return new LmmdddRatchetProductMonteCarloPricer.Builder();
    }

    @Override
    public Class<? extends LmmdddRatchetProductMonteCarloPricer> beanType() {
      return LmmdddRatchetProductMonteCarloPricer.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code nbPaths} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Integer> nbPaths() {
      return nbPaths;
    }

    /**
     * The meta-property for the {@code pathNumberBlock} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Integer> pathNumberBlock() {
      return pathNumberBlock;
    }

    /**
     * The meta-property for the {@code model} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters> model() {
      return model;
    }

    /**
     * The meta-property for the {@code numberGenerator} property.
     * @return the meta-property, not null
     */
    public MetaProperty<RandomNumberGenerator> numberGenerator() {
      return numberGenerator;
    }

    /**
     * The meta-property for the {@code evolution} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LiborMarketModelMonteCarloEvolution> evolution() {
      return evolution;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1723700122:  // nbPaths
          return ((LmmdddRatchetProductMonteCarloPricer) bean).getNbPaths();
        case -1504032417:  // pathNumberBlock
          return ((LmmdddRatchetProductMonteCarloPricer) bean).getPathNumberBlock();
        case 104069929:  // model
          return ((LmmdddRatchetProductMonteCarloPricer) bean).getModel();
        case 1709932938:  // numberGenerator
          return ((LmmdddRatchetProductMonteCarloPricer) bean).getNumberGenerator();
        case 261136251:  // evolution
          return ((LmmdddRatchetProductMonteCarloPricer) bean).getEvolution();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code LmmdddRatchetProductMonteCarloPricer}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<LmmdddRatchetProductMonteCarloPricer> {

    private int nbPaths;
    private int pathNumberBlock;
    private LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model;
    private RandomNumberGenerator numberGenerator;
    private LiborMarketModelMonteCarloEvolution evolution;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(LmmdddRatchetProductMonteCarloPricer beanToCopy) {
      this.nbPaths = beanToCopy.getNbPaths();
      this.pathNumberBlock = beanToCopy.getPathNumberBlock();
      this.model = beanToCopy.getModel();
      this.numberGenerator = beanToCopy.getNumberGenerator();
      this.evolution = beanToCopy.getEvolution();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1723700122:  // nbPaths
          return nbPaths;
        case -1504032417:  // pathNumberBlock
          return pathNumberBlock;
        case 104069929:  // model
          return model;
        case 1709932938:  // numberGenerator
          return numberGenerator;
        case 261136251:  // evolution
          return evolution;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1723700122:  // nbPaths
          this.nbPaths = (Integer) newValue;
          break;
        case -1504032417:  // pathNumberBlock
          this.pathNumberBlock = (Integer) newValue;
          break;
        case 104069929:  // model
          this.model = (LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) newValue;
          break;
        case 1709932938:  // numberGenerator
          this.numberGenerator = (RandomNumberGenerator) newValue;
          break;
        case 261136251:  // evolution
          this.evolution = (LiborMarketModelMonteCarloEvolution) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public LmmdddRatchetProductMonteCarloPricer build() {
      return new LmmdddRatchetProductMonteCarloPricer(
          nbPaths,
          pathNumberBlock,
          model,
          numberGenerator,
          evolution);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the number of paths
     * @param nbPaths  the new value
     * @return this, for chaining, not null
     */
    public Builder nbPaths(int nbPaths) {
      this.nbPaths = nbPaths;
      return this;
    }

    /**
     * Sets the number of paths in a computation block
     * @param pathNumberBlock  the new value
     * @return this, for chaining, not null
     */
    public Builder pathNumberBlock(int pathNumberBlock) {
      this.pathNumberBlock = pathNumberBlock;
      return this;
    }

    /**
     * Sets the model
     * @param model  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder model(LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model) {
      JodaBeanUtils.notNull(model, "model");
      this.model = model;
      return this;
    }

    /**
     * Sets the random number generator.
     * @param numberGenerator  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder numberGenerator(RandomNumberGenerator numberGenerator) {
      JodaBeanUtils.notNull(numberGenerator, "numberGenerator");
      this.numberGenerator = numberGenerator;
      return this;
    }

    /**
     * Sets the methods related to the model evolution.
     * @param evolution  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder evolution(LiborMarketModelMonteCarloEvolution evolution) {
      JodaBeanUtils.notNull(evolution, "evolution");
      this.evolution = evolution;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(192);
      buf.append("LmmdddRatchetProductMonteCarloPricer.Builder{");
      buf.append("nbPaths").append('=').append(JodaBeanUtils.toString(nbPaths)).append(',').append(' ');
      buf.append("pathNumberBlock").append('=').append(JodaBeanUtils.toString(pathNumberBlock)).append(',').append(' ');
      buf.append("model").append('=').append(JodaBeanUtils.toString(model)).append(',').append(' ');
      buf.append("numberGenerator").append('=').append(JodaBeanUtils.toString(numberGenerator)).append(',').append(' ');
      buf.append("evolution").append('=').append(JodaBeanUtils.toString(evolution));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
