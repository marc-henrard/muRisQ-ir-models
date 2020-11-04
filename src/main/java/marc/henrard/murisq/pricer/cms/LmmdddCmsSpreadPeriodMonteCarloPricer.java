/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.cms;

import java.io.Serializable;
import java.time.ZonedDateTime;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.math.impl.random.RandomNumberGenerator;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;

import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.pricer.decomposition.MulticurveDecisionScheduleCalculator;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;
import marc.henrard.murisq.pricer.montecarlo.LmmdddMonteCarloEuropeanPricer;
import marc.henrard.murisq.product.cms.CmsSpreadPeriodResolved;

import java.util.List;
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
 * Monte Carlo pricer for CMS spread periods (coupons, caplets, floorlets) in the Libor Market Model with
 * deterministic multiplicative spread.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public final class LmmdddCmsSpreadPeriodMonteCarloPricer
    implements LmmdddMonteCarloEuropeanPricer<CmsSpreadPeriodResolved>, ImmutableBean, Serializable {

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
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final RandomNumberGenerator numberGenerator;
  /** The methods related to the model evolution. */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final LiborMarketModelMonteCarloEvolution evolution;

  @Override
  public MulticurveEquivalent multicurveEquivalent(CmsSpreadPeriodResolved product) {
    return MulticurveDecisionScheduleCalculator
        .decisionSchedule(product).getSchedules().get(0);
  }

  @Override
  public DoubleArray aggregation(
      CmsSpreadPeriodResolved cmsSpread,
      MulticurveEquivalent me,
      List<MulticurveEquivalentValues> valuesExpiry) {

    ResolvedSwapLeg fixedLeg1 = cmsSpread.getUnderlyingSwap1().getLegs(SwapLegType.FIXED).get(0);
    int nbFixed1 = fixedLeg1.getPaymentPeriods().size();
    ResolvedSwapLeg iborLeg1 = cmsSpread.getUnderlyingSwap1().getLegs(SwapLegType.IBOR).get(0);
    int nbIbor1 = iborLeg1.getPaymentPeriods().size();
    int nbPathsA = valuesExpiry.size();
    int nbDF = me.getDiscountFactorPayments().size(); // Last DF payment corresponds to the coupon payment date
    double[] fixTimes = new double[nbDF];
    for (int i = 0; i < nbDF; i++) {
      fixTimes[i] = model.getTimeMeasure()
          .relativeTime(model.getValuationDate(), me.getDiscountFactorPayments().get(i).getPaymentDate());
    }
    int[] fixIndices = model.getIborTimeIndex(fixTimes);
    int nbIbor = me.getIborComputations().size();
    double[] iborPaymentTimes = new double[nbIbor]; // payment time
    double[] iborEffectiveTimes = new double[nbIbor]; // effective time, to find the right forward rate
    for (int i = 0; i < nbIbor; i++) {
      iborPaymentTimes[i] = model.getTimeMeasure()
          .relativeTime(model.getValuationDate(), me.getIborPayments().get(i).getPaymentDate());
      iborEffectiveTimes[i] = model.getTimeMeasure()
          .relativeTime(model.getValuationDate(), me.getIborComputations().get(i).getEffectiveDate());
    }
    int[] iborPaymentIndices = model.getIborTimeIndex(iborPaymentTimes);
    int[] iborEffectiveIndices = model.getIborTimeIndex(iborEffectiveTimes);
    double[][] discounting = discounting(model, valuesExpiry);
    // Swap rates
    double[][] swapRate = new double[2][nbPathsA];
    for (int looppath = 0; looppath < nbPathsA; looppath++) {
      MulticurveEquivalentValues valuePath = valuesExpiry.get(looppath);
      double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
      double[] pvbp = new double[2]; // path value numeraire re-based
      for (int i = 0; i < 2; i++) {
        int istart = (i == 0) ? 0 : nbFixed1;
        int iend = (i == 0) ? nbFixed1 : nbDF - 1;
        for (int loopfix = istart; loopfix < iend; loopfix++) {
          pvbp[i] += me.getDiscountFactorPayments().get(loopfix).getPaymentAmount().getAmount() *
              discounting[looppath][fixIndices[loopfix]];
        }
      }
      double[] pvIborLeg = new double[2]; // path value numeraire re-based
      for (int i = 0; i < 2; i++) {
        int istart = (i == 0) ? 0 : nbIbor1;
        int iend = (i == 0) ? nbIbor1 : nbIbor;
        for (int loopibor = istart; loopibor < iend; loopibor++) {
          int ipay = iborPaymentIndices[loopibor];
          int ifwd = iborEffectiveIndices[loopibor];
          double iborRate = model.iborRateFromDscForwards(valueFwdPath[ifwd], ifwd);
          pvIborLeg[i] += me.getIborPayments().get(loopibor).getPaymentAmount().getAmount() *
              iborRate * discounting[looppath][ipay];
        }
      }
      for (int i = 0; i < 2; i++) {
        swapRate[i][looppath] = -pvIborLeg[i] / pvbp[i];
      }
    }
    // PV
    double[] pv = new double[nbPathsA];
    double[] payoffs = cmsSpread.payoff(swapRate[0], swapRate[1]);
    for (int looppath = 0; looppath < nbPathsA; looppath++) {
      pv[looppath] = discounting[looppath][fixIndices[nbDF - 1]] * payoffs[looppath];
    }
    return DoubleArray.ofUnsafe(pv);
  }
  
  public double presentValueDoubleFast(
      CmsSpreadPeriodResolved product, 
      RatesProvider multicurve) {

    MulticurveEquivalent mce = multicurveEquivalent(product);
    MulticurveEquivalentValues initialValues = initialValues(mce, multicurve);
    Triple<Integer, Integer, Integer> decomposition = decomposition(); // fullblocks, path block, residual
    double pv = 0.0;
    for (int loopblock = 0; loopblock < decomposition.getFirst(); loopblock++) {
      double[][] valuesExpiry =
          evolveFast(initialValues, mce.getDecisionTime(), decomposition.getSecond());
      pv += aggregationFast(product, mce, valuesExpiry).sum();
    }
    if (decomposition.getThird() > 0) { // Residual number of path if non zero.
      double[][] valuesExpiryResidual =
          evolveFast(initialValues, mce.getDecisionTime(), decomposition.getThird());
      pv += aggregationFast(product, mce, valuesExpiryResidual).sum();
    }
    double initialNumeraireValue = numeraireInitialValue(multicurve);
    pv = pv /getNbPaths() * initialNumeraireValue;
    return pv;
  }
  
  /**
   * 
   * @param initialValues
   * @param expiry
   * @param numberPaths
   * @return overnight rates, dimensions lmm periods x path
   */
  public double[][] evolveFast(
      MulticurveEquivalentValues initialValues,
      ZonedDateTime expiry,
      int numberPaths) {

    return getEvolution()
        .evolveOneStepFast(expiry, initialValues, getModel(), getNumberGenerator(), numberPaths);
  }

  /**
   * 
   * @param cmsSpread
   * @param me
   * @param valuesExpiry  overnight rates, dimensions: path x lmm periods
   * @return
   */
  public DoubleArray aggregationFast(
      CmsSpreadPeriodResolved cmsSpread,
      MulticurveEquivalent me,
      double[][] valuesExpiry) {

    ResolvedSwapLeg fixedLeg1 = cmsSpread.getUnderlyingSwap1().getLegs(SwapLegType.FIXED).get(0);
    int nbFixed1 = fixedLeg1.getPaymentPeriods().size();
    ResolvedSwapLeg iborLeg1 = cmsSpread.getUnderlyingSwap1().getLegs(SwapLegType.IBOR).get(0);
    int nbIbor1 = iborLeg1.getPaymentPeriods().size();
    int nbPathsA = valuesExpiry.length;
    int nbDF = me.getDiscountFactorPayments().size(); // Last DF payment corresponds to the coupon payment date
    double[] fixTimes = new double[nbDF];
    for (int i = 0; i < nbDF; i++) {
      fixTimes[i] = model.getTimeMeasure()
          .relativeTime(model.getValuationDate(), me.getDiscountFactorPayments().get(i).getPaymentDate());
    }
    int[] fixIndices = model.getIborTimeIndex(fixTimes);
    int nbIbor = me.getIborComputations().size();
    double[] iborPaymentTimes = new double[nbIbor]; // payment time
    double[] iborEffectiveTimes = new double[nbIbor]; // effective time, to find the right forward rate
    for (int i = 0; i < nbIbor; i++) {
      iborPaymentTimes[i] = model.getTimeMeasure()
          .relativeTime(model.getValuationDate(), me.getIborPayments().get(i).getPaymentDate());
      iborEffectiveTimes[i] = model.getTimeMeasure()
          .relativeTime(model.getValuationDate(), me.getIborComputations().get(i).getEffectiveDate());
    }
    int[] iborPaymentIndices = model.getIborTimeIndex(iborPaymentTimes);
    int[] iborEffectiveIndices = model.getIborTimeIndex(iborEffectiveTimes);
    double[][] discounting = discountingFast(valuesExpiry);
    // Swap rates
    double[][] swapRate = new double[2][nbPathsA];
    for (int looppath = 0; looppath < nbPathsA; looppath++) {
      double[] valueFwdPath = valuesExpiry[looppath];
      double[] pvbp = new double[2]; // path value numeraire re-based
      for (int i = 0; i < 2; i++) {
        int istart = (i == 0) ? 0 : nbFixed1;
        int iend = (i == 0) ? nbFixed1 : nbDF - 1;
        for (int loopfix = istart; loopfix < iend; loopfix++) {
          pvbp[i] += me.getDiscountFactorPayments().get(loopfix).getPaymentAmount().getAmount() *
              discounting[looppath][fixIndices[loopfix]];
        }
      }
      double[] pvIborLeg = new double[2]; // path value numeraire re-based
      for (int i = 0; i < 2; i++) {
        int istart = (i == 0) ? 0 : nbIbor1;
        int iend = (i == 0) ? nbIbor1 : nbIbor;
        for (int loopibor = istart; loopibor < iend; loopibor++) {
          int ipay = iborPaymentIndices[loopibor];
          int ifwd = iborEffectiveIndices[loopibor];
          double iborRate = model.iborRateFromDscForwards(valueFwdPath[ifwd], ifwd);
          pvIborLeg[i] += me.getIborPayments().get(loopibor).getPaymentAmount().getAmount() *
              iborRate * discounting[looppath][ipay];
        }
      }
      for (int i = 0; i < 2; i++) {
        swapRate[i][looppath] = -pvIborLeg[i] / pvbp[i];
      }
    }
    // PV
    double[] pv = new double[nbPathsA];
    double[] payoffs = cmsSpread.payoff(swapRate[0], swapRate[1]);
    for (int looppath = 0; looppath < nbPathsA; looppath++) {
      pv[looppath] = discounting[looppath][fixIndices[nbDF - 1]] * payoffs[looppath];
    }
    return DoubleArray.ofUnsafe(pv);
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code LmmdddCmsSpreadPeriodMonteCarloPricer}.
   * @return the meta-bean, not null
   */
  public static LmmdddCmsSpreadPeriodMonteCarloPricer.Meta meta() {
    return LmmdddCmsSpreadPeriodMonteCarloPricer.Meta.INSTANCE;
  }

  static {
    MetaBean.register(LmmdddCmsSpreadPeriodMonteCarloPricer.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static LmmdddCmsSpreadPeriodMonteCarloPricer.Builder builder() {
    return new LmmdddCmsSpreadPeriodMonteCarloPricer.Builder();
  }

  private LmmdddCmsSpreadPeriodMonteCarloPricer(
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
  public LmmdddCmsSpreadPeriodMonteCarloPricer.Meta metaBean() {
    return LmmdddCmsSpreadPeriodMonteCarloPricer.Meta.INSTANCE;
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
  @Override
  public RandomNumberGenerator getNumberGenerator() {
    return numberGenerator;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the methods related to the model evolution.
   * @return the value of the property, not null
   */
  @Override
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
      LmmdddCmsSpreadPeriodMonteCarloPricer other = (LmmdddCmsSpreadPeriodMonteCarloPricer) obj;
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
    buf.append("LmmdddCmsSpreadPeriodMonteCarloPricer{");
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
   * The meta-bean for {@code LmmdddCmsSpreadPeriodMonteCarloPricer}.
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
        this, "nbPaths", LmmdddCmsSpreadPeriodMonteCarloPricer.class, Integer.TYPE);
    /**
     * The meta-property for the {@code pathNumberBlock} property.
     */
    private final MetaProperty<Integer> pathNumberBlock = DirectMetaProperty.ofImmutable(
        this, "pathNumberBlock", LmmdddCmsSpreadPeriodMonteCarloPricer.class, Integer.TYPE);
    /**
     * The meta-property for the {@code model} property.
     */
    private final MetaProperty<LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters> model = DirectMetaProperty.ofImmutable(
        this, "model", LmmdddCmsSpreadPeriodMonteCarloPricer.class, LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class);
    /**
     * The meta-property for the {@code numberGenerator} property.
     */
    private final MetaProperty<RandomNumberGenerator> numberGenerator = DirectMetaProperty.ofImmutable(
        this, "numberGenerator", LmmdddCmsSpreadPeriodMonteCarloPricer.class, RandomNumberGenerator.class);
    /**
     * The meta-property for the {@code evolution} property.
     */
    private final MetaProperty<LiborMarketModelMonteCarloEvolution> evolution = DirectMetaProperty.ofImmutable(
        this, "evolution", LmmdddCmsSpreadPeriodMonteCarloPricer.class, LiborMarketModelMonteCarloEvolution.class);
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
    public LmmdddCmsSpreadPeriodMonteCarloPricer.Builder builder() {
      return new LmmdddCmsSpreadPeriodMonteCarloPricer.Builder();
    }

    @Override
    public Class<? extends LmmdddCmsSpreadPeriodMonteCarloPricer> beanType() {
      return LmmdddCmsSpreadPeriodMonteCarloPricer.class;
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
          return ((LmmdddCmsSpreadPeriodMonteCarloPricer) bean).getNbPaths();
        case -1504032417:  // pathNumberBlock
          return ((LmmdddCmsSpreadPeriodMonteCarloPricer) bean).getPathNumberBlock();
        case 104069929:  // model
          return ((LmmdddCmsSpreadPeriodMonteCarloPricer) bean).getModel();
        case 1709932938:  // numberGenerator
          return ((LmmdddCmsSpreadPeriodMonteCarloPricer) bean).getNumberGenerator();
        case 261136251:  // evolution
          return ((LmmdddCmsSpreadPeriodMonteCarloPricer) bean).getEvolution();
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
   * The bean-builder for {@code LmmdddCmsSpreadPeriodMonteCarloPricer}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<LmmdddCmsSpreadPeriodMonteCarloPricer> {

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
    private Builder(LmmdddCmsSpreadPeriodMonteCarloPricer beanToCopy) {
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
    public LmmdddCmsSpreadPeriodMonteCarloPricer build() {
      return new LmmdddCmsSpreadPeriodMonteCarloPricer(
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
      buf.append("LmmdddCmsSpreadPeriodMonteCarloPricer.Builder{");
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
