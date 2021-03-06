/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.random.RandomNumberGenerator;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.pricer.decomposition.MulticurveDecisionScheduleCalculator;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;
import marc.henrard.murisq.pricer.montecarlo.LmmdddMonteCarloEuropeanPricer;

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
public final class LmmdddSwaptionPhysicalProductMonteCarloPricer 
    implements LmmdddMonteCarloEuropeanPricer<ResolvedSwaption>, ImmutableBean, Serializable {

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
  public MulticurveEquivalent multicurveEquivalent(ResolvedSwaption product) {
    return MulticurveDecisionScheduleCalculator
        .decisionSchedule(product).getSchedules().get(0);
  }
  
  @Override
  public double numeraireInitialValue(RatesProvider multicurve) {
    // The pseudo-numeraire is the pseudo-discount factor on the last model date.
    DoubleArray iborTimes = model.getIborTimes();
    double numeraireTime = iborTimes.get(iborTimes.size() - 1);
    // Curve and model time measure must be compatible
    return multicurve.discountFactors(model.getCurrency()).discountFactor(numeraireTime);
  }

  @Override
  public MulticurveEquivalentValues initialValues(
      MulticurveEquivalent mce, 
      RatesProvider multicurve) {
    
    // Model is on dsc forward rate, i.e. DSC forward on LIBOR periods
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
  public List<MulticurveEquivalentValues> evolve(
      MulticurveEquivalentValues initialValues,
      ZonedDateTime expiry,
      int numberPaths) {
    
    return evolution.evolveOneStep(expiry, initialValues, model, numberGenerator, numberPaths);
  }

  @Override
  public DoubleArray aggregation(
      ResolvedSwaption product,
      MulticurveEquivalent me,
      List<MulticurveEquivalentValues> valuesExpiry) {

    int nbPathsA = valuesExpiry.size();
    int nbFix = me.getDiscountFactorPayments().size();
    double[] fixTimes = new double[nbFix];
    for (int i = 0; i < nbFix; i++) {
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
    double[][] discounting = discounting(valuesExpiry);
    double[] pv = new double[nbPathsA];
    for (int looppath = 0; looppath < nbPathsA; looppath++) {
      MulticurveEquivalentValues valuePath = valuesExpiry.get(looppath);
      double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
      double pvPath = 0.0; // path value numeraire re-based
      for (int loopfix = 0; loopfix < nbFix; loopfix++) {
        pvPath += me.getDiscountFactorPayments().get(loopfix).getPaymentAmount().getAmount() *
            discounting[looppath][fixIndices[loopfix]];
      }
      for (int loopibor = 0; loopibor < nbIbor; loopibor++) {
        int ipay = iborPaymentIndices[loopibor];
        int ifwd = iborEffectiveIndices[loopibor];
        double iborRate = model.iborRateFromDscForwards(valueFwdPath[ifwd], ifwd);
        pvPath += me.getIborPayments().get(loopibor).getPaymentAmount().getAmount() *
            iborRate * discounting[looppath][ipay];
      }
      pv[looppath] = Math.max(0.0, pvPath);
    }
    return DoubleArray.ofUnsafe(pv);
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code LmmdddSwaptionPhysicalProductMonteCarloPricer}.
   * @return the meta-bean, not null
   */
  public static LmmdddSwaptionPhysicalProductMonteCarloPricer.Meta meta() {
    return LmmdddSwaptionPhysicalProductMonteCarloPricer.Meta.INSTANCE;
  }

  static {
    MetaBean.register(LmmdddSwaptionPhysicalProductMonteCarloPricer.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static LmmdddSwaptionPhysicalProductMonteCarloPricer.Builder builder() {
    return new LmmdddSwaptionPhysicalProductMonteCarloPricer.Builder();
  }

  private LmmdddSwaptionPhysicalProductMonteCarloPricer(
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
  public LmmdddSwaptionPhysicalProductMonteCarloPricer.Meta metaBean() {
    return LmmdddSwaptionPhysicalProductMonteCarloPricer.Meta.INSTANCE;
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
      LmmdddSwaptionPhysicalProductMonteCarloPricer other = (LmmdddSwaptionPhysicalProductMonteCarloPricer) obj;
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
    buf.append("LmmdddSwaptionPhysicalProductMonteCarloPricer{");
    buf.append("nbPaths").append('=').append(JodaBeanUtils.toString(nbPaths)).append(',').append(' ');
    buf.append("pathNumberBlock").append('=').append(JodaBeanUtils.toString(pathNumberBlock)).append(',').append(' ');
    buf.append("model").append('=').append(JodaBeanUtils.toString(model)).append(',').append(' ');
    buf.append("numberGenerator").append('=').append(JodaBeanUtils.toString(numberGenerator)).append(',').append(' ');
    buf.append("evolution").append('=').append(JodaBeanUtils.toString(evolution));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code LmmdddSwaptionPhysicalProductMonteCarloPricer}.
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
        this, "nbPaths", LmmdddSwaptionPhysicalProductMonteCarloPricer.class, Integer.TYPE);
    /**
     * The meta-property for the {@code pathNumberBlock} property.
     */
    private final MetaProperty<Integer> pathNumberBlock = DirectMetaProperty.ofImmutable(
        this, "pathNumberBlock", LmmdddSwaptionPhysicalProductMonteCarloPricer.class, Integer.TYPE);
    /**
     * The meta-property for the {@code model} property.
     */
    private final MetaProperty<LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters> model = DirectMetaProperty.ofImmutable(
        this, "model", LmmdddSwaptionPhysicalProductMonteCarloPricer.class, LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class);
    /**
     * The meta-property for the {@code numberGenerator} property.
     */
    private final MetaProperty<RandomNumberGenerator> numberGenerator = DirectMetaProperty.ofImmutable(
        this, "numberGenerator", LmmdddSwaptionPhysicalProductMonteCarloPricer.class, RandomNumberGenerator.class);
    /**
     * The meta-property for the {@code evolution} property.
     */
    private final MetaProperty<LiborMarketModelMonteCarloEvolution> evolution = DirectMetaProperty.ofImmutable(
        this, "evolution", LmmdddSwaptionPhysicalProductMonteCarloPricer.class, LiborMarketModelMonteCarloEvolution.class);
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
    public LmmdddSwaptionPhysicalProductMonteCarloPricer.Builder builder() {
      return new LmmdddSwaptionPhysicalProductMonteCarloPricer.Builder();
    }

    @Override
    public Class<? extends LmmdddSwaptionPhysicalProductMonteCarloPricer> beanType() {
      return LmmdddSwaptionPhysicalProductMonteCarloPricer.class;
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
          return ((LmmdddSwaptionPhysicalProductMonteCarloPricer) bean).getNbPaths();
        case -1504032417:  // pathNumberBlock
          return ((LmmdddSwaptionPhysicalProductMonteCarloPricer) bean).getPathNumberBlock();
        case 104069929:  // model
          return ((LmmdddSwaptionPhysicalProductMonteCarloPricer) bean).getModel();
        case 1709932938:  // numberGenerator
          return ((LmmdddSwaptionPhysicalProductMonteCarloPricer) bean).getNumberGenerator();
        case 261136251:  // evolution
          return ((LmmdddSwaptionPhysicalProductMonteCarloPricer) bean).getEvolution();
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
   * The bean-builder for {@code LmmdddSwaptionPhysicalProductMonteCarloPricer}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<LmmdddSwaptionPhysicalProductMonteCarloPricer> {

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
    private Builder(LmmdddSwaptionPhysicalProductMonteCarloPricer beanToCopy) {
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
    public LmmdddSwaptionPhysicalProductMonteCarloPricer build() {
      return new LmmdddSwaptionPhysicalProductMonteCarloPricer(
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
      buf.append("LmmdddSwaptionPhysicalProductMonteCarloPricer.Builder{");
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
