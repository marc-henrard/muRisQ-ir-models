/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableConstructor;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.param.LabelParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;

import marc.henrard.risq.model.generic.TimeMeasurement;

/**
 * Interest rate multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2015).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * The function b0(u) is Hull-White shaped, i.e.
 *   b0(u) = (b0(0) + eta/(a*kappa) (1-exp(-kappa u))) P(0,u)
 * The function b1(theta,u,v) is the one implied by the forward risk free rates: 
 *   b1(theta, u, v) = (b0(u) - b0(v)) / delta
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
@BeanDefinition
public final class RationalOneFactorSimpleHWShapedParameters
    implements RationalOneFactorParameters, ImmutableBean, Serializable  {
  
  /** Metadata */
  private final static List<ParameterMetadata> METADATA = 
      ImmutableList.of(LabelParameterMetadata.of("a"),
          LabelParameterMetadata.of("b_0_0"),
          LabelParameterMetadata.of("eta"),
          LabelParameterMetadata.of("kappa"));
  
  /** The parameter of the log-normal martingale. Parameter 0. */
  @PropertyDefinition
  private final double a;
  /** The starting value of the b0 curve.  Parameter 1. */
  @PropertyDefinition
  private final double b00;
  /** The volatility parameter.  Parameter 2. */
  @PropertyDefinition
  private final double eta;
  /** The mean reversion parameter.  Parameter 3. */
  @PropertyDefinition
  private final double kappa;
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
  
  /**
   * Creates an instance of the model parameters,
   * 
   * @param a  the parameter of the log-normal martingale
   * @param b00  the value at 0 for the function b0
   * @param eta  the volatility level
   * @param kappa  the mean reversion
   * @param timeMeasure  the measurement of time for expiration
   * @param discountFactors  the discount factors
   * @return the instance
   */
  public static RationalOneFactorSimpleHWShapedParameters of(
      double a, 
      double b00, 
      double eta, 
      double kappa, 
      TimeMeasurement timeMeasure, 
      DiscountFactors discountFactors) {
    
    return new RationalOneFactorSimpleHWShapedParameters(
        a, b00, eta, kappa, timeMeasure, discountFactors, LocalTime.NOON, ZoneOffset.UTC);
  }
  
  /**
   * Creates an instance of the model parameters,
   * 
   * @param a  the parameter of the log-normal martingale
   * @param b00  the value at 0 for the function b0
   * @param eta  the volatility level
   * @param kappa  the mean reversion
   * @param timeMeasure  the measurement of time for expiration
   * @param discountFactors  the discount factors
   * @param valuationDateTime  the valuation date, time and zone
   * @return the instance
   */
  public static RationalOneFactorSimpleHWShapedParameters of(
      double a, 
      double b00, 
      double eta, 
      double kappa, 
      TimeMeasurement timeMeasure, 
      DiscountFactors discountFactors,
      ZonedDateTime valuationDateTime) {
    
    ArgChecker.isTrue(discountFactors.getValuationDate().equals(valuationDateTime.toLocalDate()), 
        "discount factor date and valuation date must be the same");
    return new RationalOneFactorSimpleHWShapedParameters(
        a, b00, eta, kappa, timeMeasure, discountFactors, valuationDateTime.toLocalTime(), valuationDateTime.getZone());
  }
  
  /**
   * Creates an instance of the model parameters,
   * 
   * @param a  the parameter of the log-normal martingale
   * @param b00  the value at 0 for the function b0
   * @param eta  the volatility level
   * @param kappa  the mean reversion
   * @param timeMeasure  the measurement of time for expiration
   * @param discountFactors  the discount factors
   * @param valuationTime  the valuation time
   * @param valuationZone  the valuation zone
   * @return the instance
   */
  public static RationalOneFactorSimpleHWShapedParameters of(
      double a,
      double b00,
      double eta,
      double kappa,
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime,
      ZoneId valuationZone) {

    return new RationalOneFactorSimpleHWShapedParameters(
        a, b00, eta, kappa, timeMeasure, discountFactors, valuationTime, valuationZone);
  }
  
  @ImmutableConstructor
  private RationalOneFactorSimpleHWShapedParameters(
      double a, 
      double b00, 
      double eta, 
      double kappa, 
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime, 
      ZoneId valuationZone) {
    
    this.currency = discountFactors.getCurrency();
    this.a = a;
    this.b00 = b00;
    this.eta = eta;
    this.kappa = kappa;
    this.timeMeasure = timeMeasure;
    this.discountFactors = discountFactors;
    this.valuationDate = discountFactors.getValuationDate();
    this.valuationTime = valuationTime;
    this.valuationZone = valuationZone;
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
  public double a() {
    return a;
  }

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  @Override
  public double b0(LocalDate date) {
    double u = discountFactors.relativeYearFraction(date);
    double pu = discountFactors.discountFactor(u);
    return (b00 + eta /(a * kappa) * (1.0d - Math.exp(-kappa * u))) * pu;
  }

  @Override
  public PointSensitivityBuilder b0Sensitivity(LocalDate date) {
    double u = discountFactors.relativeYearFraction(date);
    /* Backward sweep */
    double puBar = (b00 + eta /(a * kappa) * (1.0d - Math.exp(-kappa * u)));
    return discountFactors.zeroRatePointSensitivity(u).multipliedBy(puBar);
  }

  @Override
  public double b1(IborIndexObservation obs) {
    // Same coefficient for all indices
    double delta = obs.getIndex().getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
    return (b0(obs.getEffectiveDate()) - b0(obs.getMaturityDate())) / delta;
  }

  @Override
  public PointSensitivityBuilder b1Sensitivity(IborIndexObservation obs) {
    // Same coefficient for all indices
    double delta = obs.getIndex().getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
    /* Backward sweep */
    return b0Sensitivity(obs.getEffectiveDate()).multipliedBy(1.0d / delta)
        .combinedWith(b0Sensitivity(obs.getMaturityDate()).multipliedBy(-1.0d / delta));
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }

  /**
   * Checks if the model parameters are acceptable of a given data range. 
   * <p>
   * The parameters are acceptable if and only if b_0(0) + eta/(a kappa)(1-exp(-kappa T)) < 1 for T the last date used in the model.
   * 
   * @param maturity  the maturity or last date used in the model
   * @return the boolean indicating of the parameters are acceptable (true) or not (false)
   */
  public boolean checkParameters(LocalDate maturity){
    double u = discountFactors.relativeYearFraction(maturity);
    return (b00 + eta /(a * kappa) * (1.0d - Math.exp(-kappa * u))) < 1;
  }

  @Override
  public int getParameterCount() {
    return 4; // Does not include discountFactors
  }

  @Override
  public double getParameter(int parameterIndex) {
    if(parameterIndex == 0) {
      return a;
    }
    if(parameterIndex == 1) {
      return b00;
    }
    if(parameterIndex == 2) {
      return eta;
    }
    if(parameterIndex == 3) {
      return kappa;
    }
    throw new IllegalArgumentException(
        "RationalOneFactorSimpleHWShapedParameters has only 4 arguments.");
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    return METADATA.get(parameterIndex);
  }

  @Override
  public RationalOneFactorSimpleHWShapedParameters withParameter(int parameterIndex, double newValue) {
    return new RationalOneFactorSimpleHWShapedParameters(
        (parameterIndex == 0) ? newValue : a,
        (parameterIndex == 1) ? newValue : b00,
        (parameterIndex == 2) ? newValue : eta,
        (parameterIndex == 3) ? newValue : kappa,
        timeMeasure, discountFactors, valuationTime, valuationZone);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code RationalOneFactorSimpleHWShapedParameters}.
   * @return the meta-bean, not null
   */
  public static RationalOneFactorSimpleHWShapedParameters.Meta meta() {
    return RationalOneFactorSimpleHWShapedParameters.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(RationalOneFactorSimpleHWShapedParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RationalOneFactorSimpleHWShapedParameters.Builder builder() {
    return new RationalOneFactorSimpleHWShapedParameters.Builder();
  }

  @Override
  public RationalOneFactorSimpleHWShapedParameters.Meta metaBean() {
    return RationalOneFactorSimpleHWShapedParameters.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the parameter of the log-normal martingale. Parameter 0.
   * @return the value of the property
   */
  public double getA() {
    return a;
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
      RationalOneFactorSimpleHWShapedParameters other = (RationalOneFactorSimpleHWShapedParameters) obj;
      return JodaBeanUtils.equal(a, other.a) &&
          JodaBeanUtils.equal(b00, other.b00) &&
          JodaBeanUtils.equal(eta, other.eta) &&
          JodaBeanUtils.equal(kappa, other.kappa) &&
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
    hash = hash * 31 + JodaBeanUtils.hashCode(a);
    hash = hash * 31 + JodaBeanUtils.hashCode(b00);
    hash = hash * 31 + JodaBeanUtils.hashCode(eta);
    hash = hash * 31 + JodaBeanUtils.hashCode(kappa);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(discountFactors);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(288);
    buf.append("RationalOneFactorSimpleHWShapedParameters{");
    buf.append("a").append('=').append(a).append(',').append(' ');
    buf.append("b00").append('=').append(b00).append(',').append(' ');
    buf.append("eta").append('=').append(eta).append(',').append(' ');
    buf.append("kappa").append('=').append(kappa).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(timeMeasure).append(',').append(' ');
    buf.append("discountFactors").append('=').append(discountFactors).append(',').append(' ');
    buf.append("valuationTime").append('=').append(valuationTime).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RationalOneFactorSimpleHWShapedParameters}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code a} property.
     */
    private final MetaProperty<Double> a = DirectMetaProperty.ofImmutable(
        this, "a", RationalOneFactorSimpleHWShapedParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code b00} property.
     */
    private final MetaProperty<Double> b00 = DirectMetaProperty.ofImmutable(
        this, "b00", RationalOneFactorSimpleHWShapedParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code eta} property.
     */
    private final MetaProperty<Double> eta = DirectMetaProperty.ofImmutable(
        this, "eta", RationalOneFactorSimpleHWShapedParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code kappa} property.
     */
    private final MetaProperty<Double> kappa = DirectMetaProperty.ofImmutable(
        this, "kappa", RationalOneFactorSimpleHWShapedParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", RationalOneFactorSimpleHWShapedParameters.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code discountFactors} property.
     */
    private final MetaProperty<DiscountFactors> discountFactors = DirectMetaProperty.ofImmutable(
        this, "discountFactors", RationalOneFactorSimpleHWShapedParameters.class, DiscountFactors.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", RationalOneFactorSimpleHWShapedParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", RationalOneFactorSimpleHWShapedParameters.class, ZoneId.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "a",
        "b00",
        "eta",
        "kappa",
        "timeMeasure",
        "discountFactors",
        "valuationTime",
        "valuationZone");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 97:  // a
          return a;
        case 95714:  // b00
          return b00;
        case 100754:  // eta
          return eta;
        case 101817675:  // kappa
          return kappa;
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
    public RationalOneFactorSimpleHWShapedParameters.Builder builder() {
      return new RationalOneFactorSimpleHWShapedParameters.Builder();
    }

    @Override
    public Class<? extends RationalOneFactorSimpleHWShapedParameters> beanType() {
      return RationalOneFactorSimpleHWShapedParameters.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code a} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> a() {
      return a;
    }

    /**
     * The meta-property for the {@code b00} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> b00() {
      return b00;
    }

    /**
     * The meta-property for the {@code eta} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> eta() {
      return eta;
    }

    /**
     * The meta-property for the {@code kappa} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> kappa() {
      return kappa;
    }

    /**
     * The meta-property for the {@code timeMeasure} property.
     * @return the meta-property, not null
     */
    public MetaProperty<TimeMeasurement> timeMeasure() {
      return timeMeasure;
    }

    /**
     * The meta-property for the {@code discountFactors} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DiscountFactors> discountFactors() {
      return discountFactors;
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

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 97:  // a
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getA();
        case 95714:  // b00
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getB00();
        case 100754:  // eta
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getEta();
        case 101817675:  // kappa
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getKappa();
        case 1642109393:  // timeMeasure
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getTimeMeasure();
        case -91613053:  // discountFactors
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getDiscountFactors();
        case 113591406:  // valuationTime
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((RationalOneFactorSimpleHWShapedParameters) bean).getValuationZone();
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
   * The bean-builder for {@code RationalOneFactorSimpleHWShapedParameters}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<RationalOneFactorSimpleHWShapedParameters> {

    private double a;
    private double b00;
    private double eta;
    private double kappa;
    private TimeMeasurement timeMeasure;
    private DiscountFactors discountFactors;
    private LocalTime valuationTime;
    private ZoneId valuationZone;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(RationalOneFactorSimpleHWShapedParameters beanToCopy) {
      this.a = beanToCopy.getA();
      this.b00 = beanToCopy.getB00();
      this.eta = beanToCopy.getEta();
      this.kappa = beanToCopy.getKappa();
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.discountFactors = beanToCopy.getDiscountFactors();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 97:  // a
          return a;
        case 95714:  // b00
          return b00;
        case 100754:  // eta
          return eta;
        case 101817675:  // kappa
          return kappa;
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
        case 97:  // a
          this.a = (Double) newValue;
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

    /**
     * @deprecated Use Joda-Convert in application code
     */
    @Override
    @Deprecated
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    /**
     * @deprecated Use Joda-Convert in application code
     */
    @Override
    @Deprecated
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    /**
     * @deprecated Loop in application code
     */
    @Override
    @Deprecated
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public RationalOneFactorSimpleHWShapedParameters build() {
      return new RationalOneFactorSimpleHWShapedParameters(
          a,
          b00,
          eta,
          kappa,
          timeMeasure,
          discountFactors,
          valuationTime,
          valuationZone);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the parameter of the log-normal martingale. Parameter 0.
     * @param a  the new value
     * @return this, for chaining, not null
     */
    public Builder a(double a) {
      this.a = a;
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
      StringBuilder buf = new StringBuilder(288);
      buf.append("RationalOneFactorSimpleHWShapedParameters.Builder{");
      buf.append("a").append('=').append(JodaBeanUtils.toString(a)).append(',').append(' ');
      buf.append("b00").append('=').append(JodaBeanUtils.toString(b00)).append(',').append(' ');
      buf.append("eta").append('=').append(JodaBeanUtils.toString(eta)).append(',').append(' ');
      buf.append("kappa").append('=').append(JodaBeanUtils.toString(kappa)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("discountFactors").append('=').append(JodaBeanUtils.toString(discountFactors)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
