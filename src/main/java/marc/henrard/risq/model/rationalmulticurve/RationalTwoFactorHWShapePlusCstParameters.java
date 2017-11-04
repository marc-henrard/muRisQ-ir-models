/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

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

import marc.henrard.risq.model.generic.TimeMeasurement;

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
 * The function b_0 as a HW shape. The function b_1 is the one implied by b_0 plus a constant c_1. 
 * The function b_2 is a constant c_2. The constants c_1 and c_2 are the same for all indices.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public class RationalTwoFactorHWShapePlusCstParameters
    implements RationalTwoFactorParameters, ImmutableBean, Serializable {
  
  /** Metadata */
  private final static List<ParameterMetadata> METADATA = 
      ImmutableList.of(LabelParameterMetadata.of("a1"),
          LabelParameterMetadata.of("a2"),
          LabelParameterMetadata.of("correlation"),
          LabelParameterMetadata.of("b_0_0"),
          LabelParameterMetadata.of("eta"),
          LabelParameterMetadata.of("kappa"),
          LabelParameterMetadata.of("c1"),
          LabelParameterMetadata.of("c2"));
  
  /** The parameter of the first log-normal martingale. */
  @PropertyDefinition
  private final double a1;
  /** The parameter of the second log-normal martingale. */
  @PropertyDefinition
  private final double a2;
  /** The correlation between the X_1 and the X_2 random variables */
  @PropertyDefinition(overrideGet = true)
  private final double correlation;
  /** The starting value of the b0 curve.  Parameter 1. */
  @PropertyDefinition
  private final double b00;
  /** The volatility parameter.  Parameter 2. */
  @PropertyDefinition
  private final double eta;
  /** The mean reversion parameter.  Parameter 3. */
  @PropertyDefinition
  private final double kappa;
  /** The constant additive spread to the coefficient of the first martingale. */
  @PropertyDefinition
  private final double c1;
  /** The constant additive spread to the coefficient of the second martingale. */
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
  
  public static RationalTwoFactorHWShapePlusCstParameters of(
      DoubleArray parameters,
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime, 
      ZoneId valuationZone) {

    return RationalTwoFactorHWShapePlusCstParameters.builder()
        .a1(parameters.get(0)).a2(parameters.get(1)).correlation(parameters.get(2))
        .b00(parameters.get(3)).eta(parameters.get(4)).kappa(parameters.get(5))
        .c1(parameters.get(6)).c2(parameters.get(7))
        .timeMeasure(timeMeasure).discountFactors(discountFactors).valuationTime(valuationTime)
        .valuationZone(valuationZone).build();
  }

  @ImmutableConstructor
  private RationalTwoFactorHWShapePlusCstParameters(
      double a1, 
      double a2, 
      double correlation, 
      double b00, 
      double eta, 
      double kappa, 
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
    this.eta = ArgChecker.notNegative(eta, "eta");
    this.kappa = ArgChecker.notNegative(kappa, "kappa");
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
      return eta;
    }
    if(parameterIndex == 5) {
      return kappa;
    }
    if(parameterIndex == 6) {
      return c1;
    }
    if(parameterIndex == 7) {
      return c2;
    }
    throw new IllegalArgumentException(
        "RationalOneFactorSimpleHWShapedParameters has only 8 arguments.");
  }

  @Override
  public int getParameterCount() {
    return 8; // Does not include discountFactors
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    return METADATA.get(parameterIndex);
  }

  @Override
  public RationalTwoFactorHWShapePlusCstParameters withParameter(int parameterIndex, double newValue) {
    return new RationalTwoFactorHWShapePlusCstParameters(
        (parameterIndex == 0) ? newValue : a1,
        (parameterIndex == 1) ? newValue : a2,
        (parameterIndex == 2) ? newValue : correlation,
        (parameterIndex == 3) ? newValue : b00,
        (parameterIndex == 4) ? newValue : eta,
        (parameterIndex == 5) ? newValue : kappa,
        (parameterIndex == 6) ? newValue : c1,
        (parameterIndex == 7) ? newValue : c2,
        timeMeasure, discountFactors, valuationTime, valuationZone);
  }

  @Override
  public double b0(LocalDate date) {
    double u = timeMeasure.relativeTime(valuationDateTime, date);
    double pu = discountFactors.discountFactor(date);
    return (b00 - eta /(a1 * kappa) * (1.0d - Math.exp(-kappa * u))) * pu;
  }

  @Override
  public double b1(IborIndexObservation obs) {
    // Same coefficient for all indices
    double delta = obs.getIndex().getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
    return (b0(obs.getEffectiveDate()) - b0(obs.getMaturityDate())) / delta + c1;
  }

  @Override
  public double b2(IborIndexObservation obs) {
    return c2;
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
   * The meta-bean for {@code RationalTwoFactorHWShapePlusCstParameters}.
   * @return the meta-bean, not null
   */
  public static RationalTwoFactorHWShapePlusCstParameters.Meta meta() {
    return RationalTwoFactorHWShapePlusCstParameters.Meta.INSTANCE;
  }

  static {
    MetaBean.register(RationalTwoFactorHWShapePlusCstParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RationalTwoFactorHWShapePlusCstParameters.Builder builder() {
    return new RationalTwoFactorHWShapePlusCstParameters.Builder();
  }

  @Override
  public RationalTwoFactorHWShapePlusCstParameters.Meta metaBean() {
    return RationalTwoFactorHWShapePlusCstParameters.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the parameter of the first log-normal martingale.
   * @return the value of the property
   */
  public double getA1() {
    return a1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the parameter of the second log-normal martingale.
   * @return the value of the property
   */
  public double getA2() {
    return a2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the correlation between the X_1 and the X_2 random variables
   * @return the value of the property
   */
  @Override
  public double getCorrelation() {
    return correlation;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the starting value of the b0 curve.  Parameter 1.
   * @return the value of the property
   */
  public double getB00() {
    return b00;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatility parameter.  Parameter 2.
   * @return the value of the property
   */
  public double getEta() {
    return eta;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mean reversion parameter.  Parameter 3.
   * @return the value of the property
   */
  public double getKappa() {
    return kappa;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the constant additive spread to the coefficient of the first martingale.
   * @return the value of the property
   */
  public double getC1() {
    return c1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the constant additive spread to the coefficient of the second martingale.
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
      RationalTwoFactorHWShapePlusCstParameters other = (RationalTwoFactorHWShapePlusCstParameters) obj;
      return JodaBeanUtils.equal(a1, other.a1) &&
          JodaBeanUtils.equal(a2, other.a2) &&
          JodaBeanUtils.equal(correlation, other.correlation) &&
          JodaBeanUtils.equal(b00, other.b00) &&
          JodaBeanUtils.equal(eta, other.eta) &&
          JodaBeanUtils.equal(kappa, other.kappa) &&
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
    hash = hash * 31 + JodaBeanUtils.hashCode(eta);
    hash = hash * 31 + JodaBeanUtils.hashCode(kappa);
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
    StringBuilder buf = new StringBuilder(416);
    buf.append("RationalTwoFactorHWShapePlusCstParameters{");
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
    buf.append("eta").append('=').append(JodaBeanUtils.toString(eta)).append(',').append(' ');
    buf.append("kappa").append('=').append(JodaBeanUtils.toString(kappa)).append(',').append(' ');
    buf.append("c1").append('=').append(JodaBeanUtils.toString(c1)).append(',').append(' ');
    buf.append("c2").append('=').append(JodaBeanUtils.toString(c2)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
    buf.append("discountFactors").append('=').append(JodaBeanUtils.toString(discountFactors)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RationalTwoFactorHWShapePlusCstParameters}.
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
        this, "a1", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code a2} property.
     */
    private final MetaProperty<Double> a2 = DirectMetaProperty.ofImmutable(
        this, "a2", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code correlation} property.
     */
    private final MetaProperty<Double> correlation = DirectMetaProperty.ofImmutable(
        this, "correlation", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code b00} property.
     */
    private final MetaProperty<Double> b00 = DirectMetaProperty.ofImmutable(
        this, "b00", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code eta} property.
     */
    private final MetaProperty<Double> eta = DirectMetaProperty.ofImmutable(
        this, "eta", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code kappa} property.
     */
    private final MetaProperty<Double> kappa = DirectMetaProperty.ofImmutable(
        this, "kappa", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code c1} property.
     */
    private final MetaProperty<Double> c1 = DirectMetaProperty.ofImmutable(
        this, "c1", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code c2} property.
     */
    private final MetaProperty<Double> c2 = DirectMetaProperty.ofImmutable(
        this, "c2", RationalTwoFactorHWShapePlusCstParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", RationalTwoFactorHWShapePlusCstParameters.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code discountFactors} property.
     */
    private final MetaProperty<DiscountFactors> discountFactors = DirectMetaProperty.ofImmutable(
        this, "discountFactors", RationalTwoFactorHWShapePlusCstParameters.class, DiscountFactors.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", RationalTwoFactorHWShapePlusCstParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", RationalTwoFactorHWShapePlusCstParameters.class, ZoneId.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "a1",
        "a2",
        "correlation",
        "b00",
        "eta",
        "kappa",
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
        case 100754:  // eta
          return eta;
        case 101817675:  // kappa
          return kappa;
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
    public RationalTwoFactorHWShapePlusCstParameters.Builder builder() {
      return new RationalTwoFactorHWShapePlusCstParameters.Builder();
    }

    @Override
    public Class<? extends RationalTwoFactorHWShapePlusCstParameters> beanType() {
      return RationalTwoFactorHWShapePlusCstParameters.class;
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
     * The meta-property for the {@code eta} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> eta() {
      return eta;
    }

    /**
     * The meta-property for the {@code kappa} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> kappa() {
      return kappa;
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
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getA1();
        case 3057:  // a2
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getA2();
        case 1706464642:  // correlation
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getCorrelation();
        case 95714:  // b00
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getB00();
        case 100754:  // eta
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getEta();
        case 101817675:  // kappa
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getKappa();
        case 3118:  // c1
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getC1();
        case 3119:  // c2
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getC2();
        case 1642109393:  // timeMeasure
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getTimeMeasure();
        case -91613053:  // discountFactors
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getDiscountFactors();
        case 113591406:  // valuationTime
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((RationalTwoFactorHWShapePlusCstParameters) bean).getValuationZone();
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
   * The bean-builder for {@code RationalTwoFactorHWShapePlusCstParameters}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<RationalTwoFactorHWShapePlusCstParameters> {

    private double a1;
    private double a2;
    private double correlation;
    private double b00;
    private double eta;
    private double kappa;
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
    protected Builder(RationalTwoFactorHWShapePlusCstParameters beanToCopy) {
      this.a1 = beanToCopy.getA1();
      this.a2 = beanToCopy.getA2();
      this.correlation = beanToCopy.getCorrelation();
      this.b00 = beanToCopy.getB00();
      this.eta = beanToCopy.getEta();
      this.kappa = beanToCopy.getKappa();
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
        case 100754:  // eta
          return eta;
        case 101817675:  // kappa
          return kappa;
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
        case 100754:  // eta
          this.eta = (Double) newValue;
          break;
        case 101817675:  // kappa
          this.kappa = (Double) newValue;
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
    public RationalTwoFactorHWShapePlusCstParameters build() {
      return new RationalTwoFactorHWShapePlusCstParameters(
          a1,
          a2,
          correlation,
          b00,
          eta,
          kappa,
          c1,
          c2,
          timeMeasure,
          discountFactors,
          valuationTime,
          valuationZone);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the parameter of the first log-normal martingale.
     * @param a1  the new value
     * @return this, for chaining, not null
     */
    public Builder a1(double a1) {
      this.a1 = a1;
      return this;
    }

    /**
     * Sets the parameter of the second log-normal martingale.
     * @param a2  the new value
     * @return this, for chaining, not null
     */
    public Builder a2(double a2) {
      this.a2 = a2;
      return this;
    }

    /**
     * Sets the correlation between the X_1 and the X_2 random variables
     * @param correlation  the new value
     * @return this, for chaining, not null
     */
    public Builder correlation(double correlation) {
      this.correlation = correlation;
      return this;
    }

    /**
     * Sets the starting value of the b0 curve.  Parameter 1.
     * @param b00  the new value
     * @return this, for chaining, not null
     */
    public Builder b00(double b00) {
      this.b00 = b00;
      return this;
    }

    /**
     * Sets the volatility parameter.  Parameter 2.
     * @param eta  the new value
     * @return this, for chaining, not null
     */
    public Builder eta(double eta) {
      this.eta = eta;
      return this;
    }

    /**
     * Sets the mean reversion parameter.  Parameter 3.
     * @param kappa  the new value
     * @return this, for chaining, not null
     */
    public Builder kappa(double kappa) {
      this.kappa = kappa;
      return this;
    }

    /**
     * Sets the constant additive spread to the coefficient of the first martingale.
     * @param c1  the new value
     * @return this, for chaining, not null
     */
    public Builder c1(double c1) {
      this.c1 = c1;
      return this;
    }

    /**
     * Sets the constant additive spread to the coefficient of the second martingale.
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
      StringBuilder buf = new StringBuilder(416);
      buf.append("RationalTwoFactorHWShapePlusCstParameters.Builder{");
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
      buf.append("eta").append('=').append(JodaBeanUtils.toString(eta)).append(',').append(' ');
      buf.append("kappa").append('=').append(JodaBeanUtils.toString(kappa)).append(',').append(' ');
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
