/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.rationalmulticurve;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.LabelParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.pricer.DiscountFactors;

import marc.henrard.murisq.basics.time.TimeMeasurement;

/**
 * Interest rate multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * The martingales are A(1) = exp(a_1 X_t^(1) - 0.5 a_1^2 t) - 1, A(2) = exp(a_2 X_t^(2)  - 0.5 a_2^2 t) - 1.
 * The Libor process numerator is of the form L(0) + b_1 A(1) + b_2 A(2) 
 * The discount factor process numerator is of the form P(0,T) + b_0(T) A(1)
 * <p>
 * The function b_0 is inspired by G2++ shape with correlation -1. 
 * The function b_1 is the one implied by b_0 multiplied a constant c_1. 
 * The function b_2 is the one implied by b_0 multiplied a constant c_2.  
 * The constants c_1 and c_2 are the same for all indices.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public class RationalTwoFactor2HWShapeMultiplyParameters
    implements RationalTwoFactorParameters, ImmutableBean, Serializable {
  
  /** Metadata */
  private final static List<ParameterMetadata> METADATA = 
      ImmutableList.of(LabelParameterMetadata.of("a1"),
          LabelParameterMetadata.of("a2"),
          LabelParameterMetadata.of("correlation"),
          LabelParameterMetadata.of("b_0_0"),
          LabelParameterMetadata.of("eta1"),
          LabelParameterMetadata.of("kappa1"),
          LabelParameterMetadata.of("eta2"),
          LabelParameterMetadata.of("kappa2"),
          LabelParameterMetadata.of("c1"),
          LabelParameterMetadata.of("c2"));
  
  /** The parameter of the first log-normal martingale. Parameter 0. */
  @PropertyDefinition
  private final double a1;
  /** The parameter of the second log-normal martingale. Parameter 1. */
  @PropertyDefinition
  private final double a2;
  /** The correlation between the X_1 and the X_2 random variables. Parameter 2. */
  @PropertyDefinition(overrideGet = true)
  private final double correlation;
  /** The starting value of the b0 curve. Parameter 3. */
  @PropertyDefinition
  private final double b00;
  /** The volatility parameter. Parameter 4. */
  @PropertyDefinition
  private final double eta1;
  /** The mean reversion parameter. Parameter 5. */
  @PropertyDefinition
  private final double kappa1;
  /** The volatility parameter. Parameter 6. */
  @PropertyDefinition
  private final double eta2;
  /** The mean reversion parameter. Parameter 7. */
  @PropertyDefinition
  private final double kappa2;
  /** The constant multiplicative spread to the coefficient of the first martingale. 
   * Typically the parameter will be between 1 and 1.5. Parameter 8. */
  @PropertyDefinition
  private final double c1;
  /** The constant multiplicative spread to the coefficient of the second martingale.  
   * Typically the parameter will be between 0 and 0.5. Parameter 9. */
  @PropertyDefinition
  private final double c2;
  /** The mechanism to measure time for time to expiry. */
  @PropertyDefinition(validate = "notNull")
  private final TimeMeasurement timeMeasure;
  /** The discount factors */
  @PropertyDefinition(validate = "notNull")
  private final DiscountFactors discountFactors;
  /** The valuation time. All data items in this environment are calibrated for this time. */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime valuationTime;
  /** The valuation zone.*/
  @PropertyDefinition(validate = "notNull")
  private final ZoneId valuationZone;
  /** The valuation date. All data items in this environment are calibrated for this date. */
  private final LocalDate valuationDate;  // Not a property
  /** The model currency */
  private final Currency currency;  // Not a property
  /** The valuation zone.*/
  private final ZonedDateTime valuationDateTime;  // Not a property
  
  public static RationalTwoFactor2HWShapeMultiplyParameters of(
      DoubleArray parameters,
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime, 
      ZoneId valuationZone) {

    return RationalTwoFactor2HWShapeMultiplyParameters.builder()
        .a1(parameters.get(0)).a2(parameters.get(1)).correlation(parameters.get(2))
        .b00(parameters.get(3)).eta1(parameters.get(4)).kappa1(parameters.get(5))
        .eta2(parameters.get(6)).kappa2(parameters.get(7))
        .c1(parameters.get(8)).c2(parameters.get(9))
        .timeMeasure(timeMeasure).discountFactors(discountFactors).valuationTime(valuationTime)
        .valuationZone(valuationZone).build();
  }

  @ImmutableConstructor
  private RationalTwoFactor2HWShapeMultiplyParameters(
      double a1,
      double a2,
      double correlation,
      double b00,
      double eta1,
      double kappa1,
      double eta2,
      double kappa2,
      double c1,
      double c2,
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime, 
      ZoneId valuationZone) {
    
    this.currency = discountFactors.getCurrency();
    this.a1 = a1;
    this.a2 = a2;
    this.correlation = ArgChecker.inRangeInclusive(correlation, -1.0d, 1.0d, "correlation");
    this.b00 = b00;
    this.eta1 = ArgChecker.notNegative(eta1, "eta1");
    this.kappa1 = ArgChecker.notNegativeOrZero(kappa1, "kappa1");
    this.eta2 = ArgChecker.notNegative(eta2, "eta2");
    this.kappa2 = ArgChecker.notNegativeOrZero(kappa2, "kappa2");
    this.c1 = c1;
    this.c2 = c2;
    this.timeMeasure = ArgChecker.notNull(timeMeasure, "time measure");
    this.discountFactors = ArgChecker.notNull(discountFactors, "discount factors");
    this.valuationDate = discountFactors.getValuationDate();
    this.valuationTime = ArgChecker.notNull(valuationTime, "valuation time");
    this.valuationZone = ArgChecker.notNull(valuationZone, "valuation zone");
    this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
  }

  @Override
  public Currency getCurrency() {
    return currency;
  }

  @Override
  public LocalDate getValuationDate() {
    return valuationDate;
  }

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }

  @Override
  public double getParameter(int parameterIndex) {
    if(parameterIndex == 0) {
      return a1;
    }
    if(parameterIndex == 1) {
      return a2;
    }
    if(parameterIndex == 2) {
      return correlation;
    }
    if(parameterIndex == 3) {
      return b00;
    }
    if(parameterIndex == 4) {
      return eta1;
    }
    if(parameterIndex == 5) {
      return kappa1;
    }
    if(parameterIndex == 6) {
      return eta2;
    }
    if(parameterIndex == 7) {
      return kappa2;
    }
    if(parameterIndex == 8) {
      return c1;
    }
    if(parameterIndex == 9) {
      return c2;
    }
    throw new IllegalArgumentException(
        "RationalOneFactorSimpleHWShapedParameters has only 8 arguments.");
  }

  @Override
  public DoubleArray getParameters() {
    return DoubleArray.of(a1, a2, correlation, b00, eta1, kappa1, eta2, kappa2, c1, c2);
  }

  @Override
  public int getParameterCount() {
    return 10; // Does not include discountFactors
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    return METADATA.get(parameterIndex);
  }

  @Override
  public RationalTwoFactor2HWShapeMultiplyParameters withParameter(int parameterIndex, double newValue) {
    return new RationalTwoFactor2HWShapeMultiplyParameters(
        (parameterIndex == 0) ? newValue : a1,
        (parameterIndex == 1) ? newValue : a2,
        (parameterIndex == 2) ? newValue : correlation,
        (parameterIndex == 3) ? newValue : b00,
        (parameterIndex == 4) ? newValue : eta1,
        (parameterIndex == 5) ? newValue : kappa1,
        (parameterIndex == 6) ? newValue : eta2,
        (parameterIndex == 7) ? newValue : kappa2,
        (parameterIndex == 8) ? newValue : c1,
        (parameterIndex == 9) ? newValue : c2,
        timeMeasure, discountFactors, valuationTime, valuationZone);
  }

  @Override
  public double b0(LocalDate date) {
    validateDate(date);
    double u = timeMeasure.relativeTime(valuationDateTime, date);
    double pu = discountFactors.discountFactor(date);
    return (b00 
        - eta1 /(a1 * kappa1) * (1.0d - Math.exp(-kappa1 * u))
        + eta2 /(a1 * kappa2) * (1.0d - Math.exp(-kappa2 * u))) * pu;
  }

  @Override
  public double b1(IborIndexObservation obs) {
    validateObservation(obs);
    // Same coefficient for all indices
    double delta = obs.getIndex().getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
    return c1 * (b0(obs.getEffectiveDate()) - b0(obs.getMaturityDate())) / delta;
  }

  @Override
  public double b2(IborIndexObservation obs) {
    validateObservation(obs);
    // Same coefficient for all indices
    double delta = obs.getIndex().getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
    return c2 * (b0(obs.getEffectiveDate()) - b0(obs.getMaturityDate())) / delta;
  }

  @Override
  public double a1() {
    return a1;
  }

  @Override
  public double a2() {
    return a2;
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code RationalTwoFactor2HWShapeMultiplyParameters}.
   * @return the meta-bean, not null
   */
  public static RationalTwoFactor2HWShapeMultiplyParameters.Meta meta() {
    return RationalTwoFactor2HWShapeMultiplyParameters.Meta.INSTANCE;
  }

  static {
    MetaBean.register(RationalTwoFactor2HWShapeMultiplyParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RationalTwoFactor2HWShapeMultiplyParameters.Builder builder() {
    return new RationalTwoFactor2HWShapeMultiplyParameters.Builder();
  }

  @Override
  public RationalTwoFactor2HWShapeMultiplyParameters.Meta metaBean() {
    return RationalTwoFactor2HWShapeMultiplyParameters.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the parameter of the first log-normal martingale. Parameter 0.
   * @return the value of the property
   */
  public double getA1() {
    return a1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the parameter of the second log-normal martingale. Parameter 1.
   * @return the value of the property
   */
  public double getA2() {
    return a2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the correlation between the X_1 and the X_2 random variables. Parameter 2.
   * @return the value of the property
   */
  @Override
  public double getCorrelation() {
    return correlation;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the starting value of the b0 curve. Parameter 3.
   * @return the value of the property
   */
  public double getB00() {
    return b00;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatility parameter. Parameter 4.
   * @return the value of the property
   */
  public double getEta1() {
    return eta1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mean reversion parameter. Parameter 5.
   * @return the value of the property
   */
  public double getKappa1() {
    return kappa1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatility parameter. Parameter 6.
   * @return the value of the property
   */
  public double getEta2() {
    return eta2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mean reversion parameter. Parameter 7.
   * @return the value of the property
   */
  public double getKappa2() {
    return kappa2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the c1.
   * @return the value of the property
   */
  public double getC1() {
    return c1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the c2.
   * @return the value of the property
   */
  public double getC2() {
    return c2;
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
   * Gets the discount factors
   * @return the value of the property, not null
   */
  public DiscountFactors getDiscountFactors() {
    return discountFactors;
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
      RationalTwoFactor2HWShapeMultiplyParameters other = (RationalTwoFactor2HWShapeMultiplyParameters) obj;
      return JodaBeanUtils.equal(a1, other.a1) &&
          JodaBeanUtils.equal(a2, other.a2) &&
          JodaBeanUtils.equal(correlation, other.correlation) &&
          JodaBeanUtils.equal(b00, other.b00) &&
          JodaBeanUtils.equal(eta1, other.eta1) &&
          JodaBeanUtils.equal(kappa1, other.kappa1) &&
          JodaBeanUtils.equal(eta2, other.eta2) &&
          JodaBeanUtils.equal(kappa2, other.kappa2) &&
          JodaBeanUtils.equal(c1, other.c1) &&
          JodaBeanUtils.equal(c2, other.c2) &&
          JodaBeanUtils.equal(timeMeasure, other.timeMeasure) &&
          JodaBeanUtils.equal(discountFactors, other.discountFactors) &&
          JodaBeanUtils.equal(valuationTime, other.valuationTime) &&
          JodaBeanUtils.equal(valuationZone, other.valuationZone);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(a1);
    hash = hash * 31 + JodaBeanUtils.hashCode(a2);
    hash = hash * 31 + JodaBeanUtils.hashCode(correlation);
    hash = hash * 31 + JodaBeanUtils.hashCode(b00);
    hash = hash * 31 + JodaBeanUtils.hashCode(eta1);
    hash = hash * 31 + JodaBeanUtils.hashCode(kappa1);
    hash = hash * 31 + JodaBeanUtils.hashCode(eta2);
    hash = hash * 31 + JodaBeanUtils.hashCode(kappa2);
    hash = hash * 31 + JodaBeanUtils.hashCode(c1);
    hash = hash * 31 + JodaBeanUtils.hashCode(c2);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(discountFactors);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(480);
    buf.append("RationalTwoFactor2HWShapeMultiplyParameters{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("a1").append('=').append(JodaBeanUtils.toString(a1)).append(',').append(' ');
    buf.append("a2").append('=').append(JodaBeanUtils.toString(a2)).append(',').append(' ');
    buf.append("correlation").append('=').append(JodaBeanUtils.toString(correlation)).append(',').append(' ');
    buf.append("b00").append('=').append(JodaBeanUtils.toString(b00)).append(',').append(' ');
    buf.append("eta1").append('=').append(JodaBeanUtils.toString(eta1)).append(',').append(' ');
    buf.append("kappa1").append('=').append(JodaBeanUtils.toString(kappa1)).append(',').append(' ');
    buf.append("eta2").append('=').append(JodaBeanUtils.toString(eta2)).append(',').append(' ');
    buf.append("kappa2").append('=').append(JodaBeanUtils.toString(kappa2)).append(',').append(' ');
    buf.append("c1").append('=').append(JodaBeanUtils.toString(c1)).append(',').append(' ');
    buf.append("c2").append('=').append(JodaBeanUtils.toString(c2)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
    buf.append("discountFactors").append('=').append(JodaBeanUtils.toString(discountFactors)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RationalTwoFactor2HWShapeMultiplyParameters}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code a1} property.
     */
    private final MetaProperty<Double> a1 = DirectMetaProperty.ofImmutable(
        this, "a1", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code a2} property.
     */
    private final MetaProperty<Double> a2 = DirectMetaProperty.ofImmutable(
        this, "a2", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code correlation} property.
     */
    private final MetaProperty<Double> correlation = DirectMetaProperty.ofImmutable(
        this, "correlation", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code b00} property.
     */
    private final MetaProperty<Double> b00 = DirectMetaProperty.ofImmutable(
        this, "b00", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code eta1} property.
     */
    private final MetaProperty<Double> eta1 = DirectMetaProperty.ofImmutable(
        this, "eta1", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code kappa1} property.
     */
    private final MetaProperty<Double> kappa1 = DirectMetaProperty.ofImmutable(
        this, "kappa1", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code eta2} property.
     */
    private final MetaProperty<Double> eta2 = DirectMetaProperty.ofImmutable(
        this, "eta2", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code kappa2} property.
     */
    private final MetaProperty<Double> kappa2 = DirectMetaProperty.ofImmutable(
        this, "kappa2", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code c1} property.
     */
    private final MetaProperty<Double> c1 = DirectMetaProperty.ofImmutable(
        this, "c1", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code c2} property.
     */
    private final MetaProperty<Double> c2 = DirectMetaProperty.ofImmutable(
        this, "c2", RationalTwoFactor2HWShapeMultiplyParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", RationalTwoFactor2HWShapeMultiplyParameters.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code discountFactors} property.
     */
    private final MetaProperty<DiscountFactors> discountFactors = DirectMetaProperty.ofImmutable(
        this, "discountFactors", RationalTwoFactor2HWShapeMultiplyParameters.class, DiscountFactors.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", RationalTwoFactor2HWShapeMultiplyParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", RationalTwoFactor2HWShapeMultiplyParameters.class, ZoneId.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "a1",
        "a2",
        "correlation",
        "b00",
        "eta1",
        "kappa1",
        "eta2",
        "kappa2",
        "c1",
        "c2",
        "timeMeasure",
        "discountFactors",
        "valuationTime",
        "valuationZone");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3056:  // a1
          return a1;
        case 3057:  // a2
          return a2;
        case 1706464642:  // correlation
          return correlation;
        case 95714:  // b00
          return b00;
        case 3123423:  // eta1
          return eta1;
        case -1138619322:  // kappa1
          return kappa1;
        case 3123424:  // eta2
          return eta2;
        case -1138619321:  // kappa2
          return kappa2;
        case 3118:  // c1
          return c1;
        case 3119:  // c2
          return c2;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case -91613053:  // discountFactors
          return discountFactors;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public RationalTwoFactor2HWShapeMultiplyParameters.Builder builder() {
      return new RationalTwoFactor2HWShapeMultiplyParameters.Builder();
    }

    @Override
    public Class<? extends RationalTwoFactor2HWShapeMultiplyParameters> beanType() {
      return RationalTwoFactor2HWShapeMultiplyParameters.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code a1} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> a1() {
      return a1;
    }

    /**
     * The meta-property for the {@code a2} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> a2() {
      return a2;
    }

    /**
     * The meta-property for the {@code correlation} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> correlation() {
      return correlation;
    }

    /**
     * The meta-property for the {@code b00} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> b00() {
      return b00;
    }

    /**
     * The meta-property for the {@code eta1} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> eta1() {
      return eta1;
    }

    /**
     * The meta-property for the {@code kappa1} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> kappa1() {
      return kappa1;
    }

    /**
     * The meta-property for the {@code eta2} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> eta2() {
      return eta2;
    }

    /**
     * The meta-property for the {@code kappa2} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> kappa2() {
      return kappa2;
    }

    /**
     * The meta-property for the {@code c1} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> c1() {
      return c1;
    }

    /**
     * The meta-property for the {@code c2} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> c2() {
      return c2;
    }

    /**
     * The meta-property for the {@code timeMeasure} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<TimeMeasurement> timeMeasure() {
      return timeMeasure;
    }

    /**
     * The meta-property for the {@code discountFactors} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<DiscountFactors> discountFactors() {
      return discountFactors;
    }

    /**
     * The meta-property for the {@code valuationTime} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LocalTime> valuationTime() {
      return valuationTime;
    }

    /**
     * The meta-property for the {@code valuationZone} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ZoneId> valuationZone() {
      return valuationZone;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 3056:  // a1
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getA1();
        case 3057:  // a2
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getA2();
        case 1706464642:  // correlation
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getCorrelation();
        case 95714:  // b00
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getB00();
        case 3123423:  // eta1
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getEta1();
        case -1138619322:  // kappa1
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getKappa1();
        case 3123424:  // eta2
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getEta2();
        case -1138619321:  // kappa2
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getKappa2();
        case 3118:  // c1
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getC1();
        case 3119:  // c2
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getC2();
        case 1642109393:  // timeMeasure
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getTimeMeasure();
        case -91613053:  // discountFactors
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getDiscountFactors();
        case 113591406:  // valuationTime
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((RationalTwoFactor2HWShapeMultiplyParameters) bean).getValuationZone();
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
   * The bean-builder for {@code RationalTwoFactor2HWShapeMultiplyParameters}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<RationalTwoFactor2HWShapeMultiplyParameters> {

    private double a1;
    private double a2;
    private double correlation;
    private double b00;
    private double eta1;
    private double kappa1;
    private double eta2;
    private double kappa2;
    private double c1;
    private double c2;
    private TimeMeasurement timeMeasure;
    private DiscountFactors discountFactors;
    private LocalTime valuationTime;
    private ZoneId valuationZone;

    /**
     * Restricted constructor.
     */
    protected Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(RationalTwoFactor2HWShapeMultiplyParameters beanToCopy) {
      this.a1 = beanToCopy.getA1();
      this.a2 = beanToCopy.getA2();
      this.correlation = beanToCopy.getCorrelation();
      this.b00 = beanToCopy.getB00();
      this.eta1 = beanToCopy.getEta1();
      this.kappa1 = beanToCopy.getKappa1();
      this.eta2 = beanToCopy.getEta2();
      this.kappa2 = beanToCopy.getKappa2();
      this.c1 = beanToCopy.getC1();
      this.c2 = beanToCopy.getC2();
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.discountFactors = beanToCopy.getDiscountFactors();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3056:  // a1
          return a1;
        case 3057:  // a2
          return a2;
        case 1706464642:  // correlation
          return correlation;
        case 95714:  // b00
          return b00;
        case 3123423:  // eta1
          return eta1;
        case -1138619322:  // kappa1
          return kappa1;
        case 3123424:  // eta2
          return eta2;
        case -1138619321:  // kappa2
          return kappa2;
        case 3118:  // c1
          return c1;
        case 3119:  // c2
          return c2;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case -91613053:  // discountFactors
          return discountFactors;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 3056:  // a1
          this.a1 = (Double) newValue;
          break;
        case 3057:  // a2
          this.a2 = (Double) newValue;
          break;
        case 1706464642:  // correlation
          this.correlation = (Double) newValue;
          break;
        case 95714:  // b00
          this.b00 = (Double) newValue;
          break;
        case 3123423:  // eta1
          this.eta1 = (Double) newValue;
          break;
        case -1138619322:  // kappa1
          this.kappa1 = (Double) newValue;
          break;
        case 3123424:  // eta2
          this.eta2 = (Double) newValue;
          break;
        case -1138619321:  // kappa2
          this.kappa2 = (Double) newValue;
          break;
        case 3118:  // c1
          this.c1 = (Double) newValue;
          break;
        case 3119:  // c2
          this.c2 = (Double) newValue;
          break;
        case 1642109393:  // timeMeasure
          this.timeMeasure = (TimeMeasurement) newValue;
          break;
        case -91613053:  // discountFactors
          this.discountFactors = (DiscountFactors) newValue;
          break;
        case 113591406:  // valuationTime
          this.valuationTime = (LocalTime) newValue;
          break;
        case 113775949:  // valuationZone
          this.valuationZone = (ZoneId) newValue;
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
    public RationalTwoFactor2HWShapeMultiplyParameters build() {
      return new RationalTwoFactor2HWShapeMultiplyParameters(
          a1,
          a2,
          correlation,
          b00,
          eta1,
          kappa1,
          eta2,
          kappa2,
          c1,
          c2,
          timeMeasure,
          discountFactors,
          valuationTime,
          valuationZone);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the parameter of the first log-normal martingale. Parameter 0.
     * @param a1  the new value
     * @return this, for chaining, not null
     */
    public Builder a1(double a1) {
      this.a1 = a1;
      return this;
    }

    /**
     * Sets the parameter of the second log-normal martingale. Parameter 1.
     * @param a2  the new value
     * @return this, for chaining, not null
     */
    public Builder a2(double a2) {
      this.a2 = a2;
      return this;
    }

    /**
     * Sets the correlation between the X_1 and the X_2 random variables. Parameter 2.
     * @param correlation  the new value
     * @return this, for chaining, not null
     */
    public Builder correlation(double correlation) {
      this.correlation = correlation;
      return this;
    }

    /**
     * Sets the starting value of the b0 curve. Parameter 3.
     * @param b00  the new value
     * @return this, for chaining, not null
     */
    public Builder b00(double b00) {
      this.b00 = b00;
      return this;
    }

    /**
     * Sets the volatility parameter. Parameter 4.
     * @param eta1  the new value
     * @return this, for chaining, not null
     */
    public Builder eta1(double eta1) {
      this.eta1 = eta1;
      return this;
    }

    /**
     * Sets the mean reversion parameter. Parameter 5.
     * @param kappa1  the new value
     * @return this, for chaining, not null
     */
    public Builder kappa1(double kappa1) {
      this.kappa1 = kappa1;
      return this;
    }

    /**
     * Sets the volatility parameter. Parameter 6.
     * @param eta2  the new value
     * @return this, for chaining, not null
     */
    public Builder eta2(double eta2) {
      this.eta2 = eta2;
      return this;
    }

    /**
     * Sets the mean reversion parameter. Parameter 7.
     * @param kappa2  the new value
     * @return this, for chaining, not null
     */
    public Builder kappa2(double kappa2) {
      this.kappa2 = kappa2;
      return this;
    }

    /**
     * Sets the c1.
     * @param c1  the new value
     * @return this, for chaining, not null
     */
    public Builder c1(double c1) {
      this.c1 = c1;
      return this;
    }

    /**
     * Sets the c2.
     * @param c2  the new value
     * @return this, for chaining, not null
     */
    public Builder c2(double c2) {
      this.c2 = c2;
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
     * Sets the discount factors
     * @param discountFactors  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactors(DiscountFactors discountFactors) {
      JodaBeanUtils.notNull(discountFactors, "discountFactors");
      this.discountFactors = discountFactors;
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

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(480);
      buf.append("RationalTwoFactor2HWShapeMultiplyParameters.Builder{");
      int len = buf.length();
      toString(buf);
      if (buf.length() > len) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }

    protected void toString(StringBuilder buf) {
      buf.append("a1").append('=').append(JodaBeanUtils.toString(a1)).append(',').append(' ');
      buf.append("a2").append('=').append(JodaBeanUtils.toString(a2)).append(',').append(' ');
      buf.append("correlation").append('=').append(JodaBeanUtils.toString(correlation)).append(',').append(' ');
      buf.append("b00").append('=').append(JodaBeanUtils.toString(b00)).append(',').append(' ');
      buf.append("eta1").append('=').append(JodaBeanUtils.toString(eta1)).append(',').append(' ');
      buf.append("kappa1").append('=').append(JodaBeanUtils.toString(kappa1)).append(',').append(' ');
      buf.append("eta2").append('=').append(JodaBeanUtils.toString(eta2)).append(',').append(' ');
      buf.append("kappa2").append('=').append(JodaBeanUtils.toString(kappa2)).append(',').append(' ');
      buf.append("c1").append('=').append(JodaBeanUtils.toString(c1)).append(',').append(' ');
      buf.append("c2").append('=').append(JodaBeanUtils.toString(c2)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("discountFactors").append('=').append(JodaBeanUtils.toString(discountFactors)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
