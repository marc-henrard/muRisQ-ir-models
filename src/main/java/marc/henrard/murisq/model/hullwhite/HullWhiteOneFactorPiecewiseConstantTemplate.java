/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.hullwhite;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.joda.beans.Bean;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableConstructor;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.minimization.NonLinearParameterTransforms;
import com.opengamma.strata.math.impl.minimization.ParameterLimitsTransform;
import com.opengamma.strata.math.impl.minimization.ParameterLimitsTransform.LimitType;
import com.opengamma.strata.math.impl.minimization.SingleRangeLimitTransform;
import com.opengamma.strata.math.impl.minimization.UncoupledParameterTransforms;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.generic.SingleCurrencyModelTemplate;

/**
 * Template for a rational two-factor model {@link RationalTwoFactorHWShapePlusCstParameters}.
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class HullWhiteOneFactorPiecewiseConstantTemplate
    implements SingleCurrencyModelTemplate, ImmutableBean, Serializable  {

  private static final double LIMIT_0 = 1.0E-8;

  /** The currency. */
  @PropertyDefinition(validate = "notNull")
  private final Currency currency;
  /** The mechanism to measure time for time to expiry. */
  @PropertyDefinition(validate = "notNull")
  private final TimeMeasurement timeMeasure;
  /** The valuation date. All data items in this environment are calibrated for this date. */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate valuationDate;
  /** The valuation time. All data items in this environment are calibrated for this time. */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime valuationTime;
  /** The valuation zone. */
  @PropertyDefinition(validate = "notNull")
  private final ZoneId valuationZone;
  /** The volatility times. */
  @PropertyDefinition
  private final DoubleArray volatilityTimes;
  /** The default initial guess. */
  @PropertyDefinition
  private final DoubleArray initialGuess;
  /** The fixed parameters which are not calibrated but set at their guess value. */
  @PropertyDefinition(overrideGet = true)
  private final BitSet fixed;
  /** The valuation date and time.*/
  private final ZonedDateTime valuationDateTime;  // Not a property
  /** The number of parameters. */
  private final int nbParameters;  // Not a property
  private final ParameterLimitsTransform[] transforms;
  private final List<Function<Double, Boolean>> constraints;

  @ImmutableConstructor
  private HullWhiteOneFactorPiecewiseConstantTemplate(
      Currency currency,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone,
      DoubleArray volatilityTimes,
      DoubleArray initialGuess,
      BitSet fixed) {
    
    this.currency = ArgChecker.notNull(currency, "currency");
    JodaBeanUtils.notNull(timeMeasure, "timeMeasure");
    JodaBeanUtils.notNull(valuationDate, "valuationDate");
    JodaBeanUtils.notNull(valuationTime, "valuationTime");
    JodaBeanUtils.notNull(valuationZone, "valuationZone");
    this.timeMeasure = timeMeasure;
    this.valuationTime = valuationTime;
    this.valuationDate = valuationDate;
    this.valuationZone = valuationZone;
    this.volatilityTimes = volatilityTimes;
    this.nbParameters = volatilityTimes.size() + 2;
    ArgChecker.notNull(initialGuess, "initialGuess");
    ArgChecker.isTrue(initialGuess.size() == nbParameters, "incorrect initial guess size");
    this.initialGuess = initialGuess;
    ArgChecker.notNull(fixed, "fixed");
    ArgChecker.isTrue(fixed.length() <= nbParameters, "incorrect fixed parameters size");
    this.fixed = fixed;
    this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
    this.transforms = new ParameterLimitsTransform[nbParameters];
    this.constraints = new ArrayList<>();
    for (int i = 0; i < nbParameters; i++) {
      transforms[i] = new SingleRangeLimitTransform(LIMIT_0, LimitType.GREATER_THAN);
      constraints.add(p -> (p > 0));
    }
  }

  @Override
  public int parametersCount() {
    return nbParameters;
  }

  @Override
  public DoubleArray initialGuess() {
    return initialGuess;
  }

  @Override
  public HullWhiteOneFactorPiecewiseConstantModelParameters generate(DoubleArray parameters) {
    ArgChecker.isTrue(parameters.size() == initialGuess.size(), "Incorrect number of parameters");
    HullWhiteOneFactorPiecewiseConstantParameters parametersStrata =
        HullWhiteOneFactorPiecewiseConstantParameters.of(parameters.get(0), parameters.subArray(1), volatilityTimes);
    return HullWhiteOneFactorPiecewiseConstantModelParameters
        .of(currency, parametersStrata, valuationDate, valuationTime, valuationZone, timeMeasure);
  }

  @Override
  public NonLinearParameterTransforms getTransform() {
    return new UncoupledParameterTransforms(initialGuess, transforms, fixed);
  }

  @Override
  public Function<DoubleArray, Boolean> getConstraints() {
    return (parameters) -> {
      boolean isOk = true;
      int loopp = 0;
      for (int i = 0; i < initialGuess.size(); i++) {
        if (!fixed.get(i)) {
          isOk = (isOk && constraints.get(i).apply(parameters.get(loopp)));
          loopp++;
        }
      }
      return isOk;
    };
  }

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code HullWhiteOneFactorPiecewiseConstantTemplate}.
   * @return the meta-bean, not null
   */
  public static HullWhiteOneFactorPiecewiseConstantTemplate.Meta meta() {
    return HullWhiteOneFactorPiecewiseConstantTemplate.Meta.INSTANCE;
  }

  static {
    MetaBean.register(HullWhiteOneFactorPiecewiseConstantTemplate.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param currency  the value of the property, not null
   * @param timeMeasure  the value of the property, not null
   * @param valuationDate  the value of the property, not null
   * @param valuationTime  the value of the property, not null
   * @param valuationZone  the value of the property, not null
   * @param volatilityTimes  the value of the property
   * @param initialGuess  the value of the property
   * @param fixed  the value of the property
   * @return the instance
   */
  public static HullWhiteOneFactorPiecewiseConstantTemplate of(
      Currency currency,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone,
      DoubleArray volatilityTimes,
      DoubleArray initialGuess,
      BitSet fixed) {
    return new HullWhiteOneFactorPiecewiseConstantTemplate(
      currency,
      timeMeasure,
      valuationDate,
      valuationTime,
      valuationZone,
      volatilityTimes,
      initialGuess,
      fixed);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static HullWhiteOneFactorPiecewiseConstantTemplate.Builder builder() {
    return new HullWhiteOneFactorPiecewiseConstantTemplate.Builder();
  }

  @Override
  public HullWhiteOneFactorPiecewiseConstantTemplate.Meta metaBean() {
    return HullWhiteOneFactorPiecewiseConstantTemplate.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the currency.
   * @return the value of the property, not null
   */
  public Currency getCurrency() {
    return currency;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mechanism to measure time for time to expiry.
   * @return the value of the property, not null
   */
  public TimeMeasurement getTimeMeasure() {
    return timeMeasure;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation date. All data items in this environment are calibrated for this date.
   * @return the value of the property, not null
   */
  public LocalDate getValuationDate() {
    return valuationDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation time. All data items in this environment are calibrated for this time.
   * @return the value of the property, not null
   */
  public LocalTime getValuationTime() {
    return valuationTime;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation zone.
   * @return the value of the property, not null
   */
  public ZoneId getValuationZone() {
    return valuationZone;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatility times.
   * @return the value of the property
   */
  public DoubleArray getVolatilityTimes() {
    return volatilityTimes;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the default initial guess.
   * @return the value of the property
   */
  public DoubleArray getInitialGuess() {
    return initialGuess;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the fixed parameters which are not calibrated but set at their guess value.
   * @return the value of the property
   */
  @Override
  public BitSet getFixed() {
    return fixed;
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
      HullWhiteOneFactorPiecewiseConstantTemplate other = (HullWhiteOneFactorPiecewiseConstantTemplate) obj;
      return JodaBeanUtils.equal(currency, other.currency) &&
          JodaBeanUtils.equal(timeMeasure, other.timeMeasure) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(valuationTime, other.valuationTime) &&
          JodaBeanUtils.equal(valuationZone, other.valuationZone) &&
          JodaBeanUtils.equal(volatilityTimes, other.volatilityTimes) &&
          JodaBeanUtils.equal(initialGuess, other.initialGuess) &&
          JodaBeanUtils.equal(fixed, other.fixed);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(currency);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    hash = hash * 31 + JodaBeanUtils.hashCode(volatilityTimes);
    hash = hash * 31 + JodaBeanUtils.hashCode(initialGuess);
    hash = hash * 31 + JodaBeanUtils.hashCode(fixed);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(288);
    buf.append("HullWhiteOneFactorPiecewiseConstantTemplate{");
    buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
    buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
    buf.append("volatilityTimes").append('=').append(JodaBeanUtils.toString(volatilityTimes)).append(',').append(' ');
    buf.append("initialGuess").append('=').append(JodaBeanUtils.toString(initialGuess)).append(',').append(' ');
    buf.append("fixed").append('=').append(JodaBeanUtils.toString(fixed));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code HullWhiteOneFactorPiecewiseConstantTemplate}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code currency} property.
     */
    private final MetaProperty<Currency> currency = DirectMetaProperty.ofImmutable(
        this, "currency", HullWhiteOneFactorPiecewiseConstantTemplate.class, Currency.class);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", HullWhiteOneFactorPiecewiseConstantTemplate.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", HullWhiteOneFactorPiecewiseConstantTemplate.class, LocalDate.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", HullWhiteOneFactorPiecewiseConstantTemplate.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", HullWhiteOneFactorPiecewiseConstantTemplate.class, ZoneId.class);
    /**
     * The meta-property for the {@code volatilityTimes} property.
     */
    private final MetaProperty<DoubleArray> volatilityTimes = DirectMetaProperty.ofImmutable(
        this, "volatilityTimes", HullWhiteOneFactorPiecewiseConstantTemplate.class, DoubleArray.class);
    /**
     * The meta-property for the {@code initialGuess} property.
     */
    private final MetaProperty<DoubleArray> initialGuess = DirectMetaProperty.ofImmutable(
        this, "initialGuess", HullWhiteOneFactorPiecewiseConstantTemplate.class, DoubleArray.class);
    /**
     * The meta-property for the {@code fixed} property.
     */
    private final MetaProperty<BitSet> fixed = DirectMetaProperty.ofImmutable(
        this, "fixed", HullWhiteOneFactorPiecewiseConstantTemplate.class, BitSet.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "currency",
        "timeMeasure",
        "valuationDate",
        "valuationTime",
        "valuationZone",
        "volatilityTimes",
        "initialGuess",
        "fixed");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return currency;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case -2122530271:  // volatilityTimes
          return volatilityTimes;
        case -431632141:  // initialGuess
          return initialGuess;
        case 97445748:  // fixed
          return fixed;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public HullWhiteOneFactorPiecewiseConstantTemplate.Builder builder() {
      return new HullWhiteOneFactorPiecewiseConstantTemplate.Builder();
    }

    @Override
    public Class<? extends HullWhiteOneFactorPiecewiseConstantTemplate> beanType() {
      return HullWhiteOneFactorPiecewiseConstantTemplate.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code currency} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Currency> currency() {
      return currency;
    }

    /**
     * The meta-property for the {@code timeMeasure} property.
     * @return the meta-property, not null
     */
    public MetaProperty<TimeMeasurement> timeMeasure() {
      return timeMeasure;
    }

    /**
     * The meta-property for the {@code valuationDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> valuationDate() {
      return valuationDate;
    }

    /**
     * The meta-property for the {@code valuationTime} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalTime> valuationTime() {
      return valuationTime;
    }

    /**
     * The meta-property for the {@code valuationZone} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZoneId> valuationZone() {
      return valuationZone;
    }

    /**
     * The meta-property for the {@code volatilityTimes} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> volatilityTimes() {
      return volatilityTimes;
    }

    /**
     * The meta-property for the {@code initialGuess} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> initialGuess() {
      return initialGuess;
    }

    /**
     * The meta-property for the {@code fixed} property.
     * @return the meta-property, not null
     */
    public MetaProperty<BitSet> fixed() {
      return fixed;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getCurrency();
        case 1642109393:  // timeMeasure
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getTimeMeasure();
        case 113107279:  // valuationDate
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getValuationDate();
        case 113591406:  // valuationTime
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getValuationZone();
        case -2122530271:  // volatilityTimes
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getVolatilityTimes();
        case -431632141:  // initialGuess
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getInitialGuess();
        case 97445748:  // fixed
          return ((HullWhiteOneFactorPiecewiseConstantTemplate) bean).getFixed();
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
   * The bean-builder for {@code HullWhiteOneFactorPiecewiseConstantTemplate}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<HullWhiteOneFactorPiecewiseConstantTemplate> {

    private Currency currency;
    private TimeMeasurement timeMeasure;
    private LocalDate valuationDate;
    private LocalTime valuationTime;
    private ZoneId valuationZone;
    private DoubleArray volatilityTimes;
    private DoubleArray initialGuess;
    private BitSet fixed;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(HullWhiteOneFactorPiecewiseConstantTemplate beanToCopy) {
      this.currency = beanToCopy.getCurrency();
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.valuationDate = beanToCopy.getValuationDate();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
      this.volatilityTimes = beanToCopy.getVolatilityTimes();
      this.initialGuess = beanToCopy.getInitialGuess();
      this.fixed = beanToCopy.getFixed();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return currency;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case -2122530271:  // volatilityTimes
          return volatilityTimes;
        case -431632141:  // initialGuess
          return initialGuess;
        case 97445748:  // fixed
          return fixed;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          this.currency = (Currency) newValue;
          break;
        case 1642109393:  // timeMeasure
          this.timeMeasure = (TimeMeasurement) newValue;
          break;
        case 113107279:  // valuationDate
          this.valuationDate = (LocalDate) newValue;
          break;
        case 113591406:  // valuationTime
          this.valuationTime = (LocalTime) newValue;
          break;
        case 113775949:  // valuationZone
          this.valuationZone = (ZoneId) newValue;
          break;
        case -2122530271:  // volatilityTimes
          this.volatilityTimes = (DoubleArray) newValue;
          break;
        case -431632141:  // initialGuess
          this.initialGuess = (DoubleArray) newValue;
          break;
        case 97445748:  // fixed
          this.fixed = (BitSet) newValue;
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
    public HullWhiteOneFactorPiecewiseConstantTemplate build() {
      return new HullWhiteOneFactorPiecewiseConstantTemplate(
          currency,
          timeMeasure,
          valuationDate,
          valuationTime,
          valuationZone,
          volatilityTimes,
          initialGuess,
          fixed);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the currency.
     * @param currency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder currency(Currency currency) {
      JodaBeanUtils.notNull(currency, "currency");
      this.currency = currency;
      return this;
    }

    /**
     * Sets the mechanism to measure time for time to expiry.
     * @param timeMeasure  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder timeMeasure(TimeMeasurement timeMeasure) {
      JodaBeanUtils.notNull(timeMeasure, "timeMeasure");
      this.timeMeasure = timeMeasure;
      return this;
    }

    /**
     * Sets the valuation date. All data items in this environment are calibrated for this date.
     * @param valuationDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder valuationDate(LocalDate valuationDate) {
      JodaBeanUtils.notNull(valuationDate, "valuationDate");
      this.valuationDate = valuationDate;
      return this;
    }

    /**
     * Sets the valuation time. All data items in this environment are calibrated for this time.
     * @param valuationTime  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder valuationTime(LocalTime valuationTime) {
      JodaBeanUtils.notNull(valuationTime, "valuationTime");
      this.valuationTime = valuationTime;
      return this;
    }

    /**
     * Sets the valuation zone.
     * @param valuationZone  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder valuationZone(ZoneId valuationZone) {
      JodaBeanUtils.notNull(valuationZone, "valuationZone");
      this.valuationZone = valuationZone;
      return this;
    }

    /**
     * Sets the volatility times.
     * @param volatilityTimes  the new value
     * @return this, for chaining, not null
     */
    public Builder volatilityTimes(DoubleArray volatilityTimes) {
      this.volatilityTimes = volatilityTimes;
      return this;
    }

    /**
     * Sets the default initial guess.
     * @param initialGuess  the new value
     * @return this, for chaining, not null
     */
    public Builder initialGuess(DoubleArray initialGuess) {
      this.initialGuess = initialGuess;
      return this;
    }

    /**
     * Sets the fixed parameters which are not calibrated but set at their guess value.
     * @param fixed  the new value
     * @return this, for chaining, not null
     */
    public Builder fixed(BitSet fixed) {
      this.fixed = fixed;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(288);
      buf.append("HullWhiteOneFactorPiecewiseConstantTemplate.Builder{");
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
      buf.append("volatilityTimes").append('=').append(JodaBeanUtils.toString(volatilityTimes)).append(',').append(' ');
      buf.append("initialGuess").append('=').append(JodaBeanUtils.toString(initialGuess)).append(',').append(' ');
      buf.append("fixed").append('=').append(JodaBeanUtils.toString(fixed));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
