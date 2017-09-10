/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableConstructor;
import org.joda.beans.PropertyDefinition;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;

import marc.henrard.risq.model.generic.ParameterDateCurve;
import marc.henrard.risq.model.generic.TimeMeasurement;
import java.util.NoSuchElementException;
import java.util.Set;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

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
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class RationalTwoFactorGenericParameters 
    implements RationalTwoFactorParameters, ImmutableBean, Serializable {
  
  /** The model currency */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final Currency currency;
  /** The parameter of the first log-normal martingale. */
  @PropertyDefinition
  private final double a1;
  /** The parameter of the second log-normal martingale. */
  @PropertyDefinition
  private final double a2;
  /** The correlation between the X_1 and the X_2 random variables */
  @PropertyDefinition(overrideGet = true)
  private final double correlation;
  /** The time dependent parameter function in front of the martingale in the discount factor evolution. */
  @PropertyDefinition(validate = "notNull")
  private final ParameterDateCurve b0;
  /** The time dependent parameter function in front of the first martingale in the Libor process evolution. 
   * One function for each Ibor index. */
  @PropertyDefinition(validate = "notNull")
  private final Map<IborIndex, ParameterDateCurve> b1;
  /** The time dependent parameter function in front of the second martingale in the Libor dynamic. 
   * One different function for each Ibor index. */
  @PropertyDefinition(validate = "notNull")
  private final Map<IborIndex, ParameterDateCurve> b2;
  /** The mechanism to measure time for time to expiry. */
  @PropertyDefinition(validate = "notNull")
  private final TimeMeasurement timeMeasure;
  /** The valuation date. All data items in this environment are calibrated for this date. */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate valuationDate;
  /** The valuation time. All data items in this environment are calibrated for this time. */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime valuationTime;
  /** The valuation zone.*/
  @PropertyDefinition(validate = "notNull")
  private final ZoneId valuationZone;
  /** The valuation zoned date and time.*/
  private final ZonedDateTime valuationDateTime;  // Not a property

  @ImmutableConstructor
  private RationalTwoFactorGenericParameters(
      Currency currency,
      double a1,
      double a2,
      double correlation,
      ParameterDateCurve b0,
      Map<IborIndex, ParameterDateCurve> b1,
      Map<IborIndex, ParameterDateCurve> b2,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone) {
    
    this.currency = currency;
    this.a1 = a1;
    this.a2 = a2;
    this.correlation = correlation;
    this.b0 = b0;
    ArgChecker.isTrue(b1.keySet().equals(b2.keySet()), 
        "Index sets should be identic for b1 and b2");
    for(IborIndex index:b1.keySet()) {
      ArgChecker.isTrue(index.getCurrency().equals(currency), 
          "the index currency should be equal to the model currency");
    }
    this.b1 = b1;
    this.b2 = b2;
    this.timeMeasure = timeMeasure;
    this.valuationDate = valuationDate;
    this.valuationTime = valuationTime;
    this.valuationZone = valuationZone;
    this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
  }

  @Override
  public double b0(LocalDate date) {
    return b0.parameterValue(date);
  }

  @Override
  public double b1(IborIndexObservation obs) {
    return b1.get(obs.getIndex()).parameterValue(obs.getFixingDate());
  }

  @Override
  public double b2(IborIndexObservation obs) {
    return b2.get(obs.getIndex()).parameterValue(obs.getFixingDate());
  }

  @Override
  public double a1() {
    return a1;
  }

  @Override
  public double a2() {
    return a2;
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code RationalTwoFactorGenericParameters}.
   * @return the meta-bean, not null
   */
  public static RationalTwoFactorGenericParameters.Meta meta() {
    return RationalTwoFactorGenericParameters.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(RationalTwoFactorGenericParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param currency  the value of the property, not null
   * @param a1  the value of the property
   * @param a2  the value of the property
   * @param correlation  the value of the property
   * @param b0  the value of the property, not null
   * @param b1  the value of the property, not null
   * @param b2  the value of the property, not null
   * @param timeMeasure  the value of the property, not null
   * @param valuationDate  the value of the property, not null
   * @param valuationTime  the value of the property, not null
   * @param valuationZone  the value of the property, not null
   * @return the instance
   */
  public static RationalTwoFactorGenericParameters of(
      Currency currency,
      double a1,
      double a2,
      double correlation,
      ParameterDateCurve b0,
      Map<IborIndex, ParameterDateCurve> b1,
      Map<IborIndex, ParameterDateCurve> b2,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone) {
    return new RationalTwoFactorGenericParameters(
      currency,
      a1,
      a2,
      correlation,
      b0,
      b1,
      b2,
      timeMeasure,
      valuationDate,
      valuationTime,
      valuationZone);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RationalTwoFactorGenericParameters.Builder builder() {
    return new RationalTwoFactorGenericParameters.Builder();
  }

  @Override
  public RationalTwoFactorGenericParameters.Meta metaBean() {
    return RationalTwoFactorGenericParameters.Meta.INSTANCE;
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
   * Gets the model currency
   * @return the value of the property, not null
   */
  @Override
  public Currency getCurrency() {
    return currency;
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
   * Gets the time dependent parameter function in front of the martingale in the discount factor evolution.
   * @return the value of the property, not null
   */
  public ParameterDateCurve getB0() {
    return b0;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the b1.
   * @return the value of the property, not null
   */
  public Map<IborIndex, ParameterDateCurve> getB1() {
    return b1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the b2.
   * @return the value of the property, not null
   */
  public Map<IborIndex, ParameterDateCurve> getB2() {
    return b2;
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
      RationalTwoFactorGenericParameters other = (RationalTwoFactorGenericParameters) obj;
      return JodaBeanUtils.equal(currency, other.currency) &&
          JodaBeanUtils.equal(a1, other.a1) &&
          JodaBeanUtils.equal(a2, other.a2) &&
          JodaBeanUtils.equal(correlation, other.correlation) &&
          JodaBeanUtils.equal(b0, other.b0) &&
          JodaBeanUtils.equal(b1, other.b1) &&
          JodaBeanUtils.equal(b2, other.b2) &&
          JodaBeanUtils.equal(timeMeasure, other.timeMeasure) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(valuationTime, other.valuationTime) &&
          JodaBeanUtils.equal(valuationZone, other.valuationZone);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(currency);
    hash = hash * 31 + JodaBeanUtils.hashCode(a1);
    hash = hash * 31 + JodaBeanUtils.hashCode(a2);
    hash = hash * 31 + JodaBeanUtils.hashCode(correlation);
    hash = hash * 31 + JodaBeanUtils.hashCode(b0);
    hash = hash * 31 + JodaBeanUtils.hashCode(b1);
    hash = hash * 31 + JodaBeanUtils.hashCode(b2);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(384);
    buf.append("RationalTwoFactorGenericParameters{");
    buf.append("currency").append('=').append(currency).append(',').append(' ');
    buf.append("a1").append('=').append(a1).append(',').append(' ');
    buf.append("a2").append('=').append(a2).append(',').append(' ');
    buf.append("correlation").append('=').append(correlation).append(',').append(' ');
    buf.append("b0").append('=').append(b0).append(',').append(' ');
    buf.append("b1").append('=').append(b1).append(',').append(' ');
    buf.append("b2").append('=').append(b2).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(timeMeasure).append(',').append(' ');
    buf.append("valuationDate").append('=').append(valuationDate).append(',').append(' ');
    buf.append("valuationTime").append('=').append(valuationTime).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RationalTwoFactorGenericParameters}.
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
        this, "currency", RationalTwoFactorGenericParameters.class, Currency.class);
    /**
     * The meta-property for the {@code a1} property.
     */
    private final MetaProperty<Double> a1 = DirectMetaProperty.ofImmutable(
        this, "a1", RationalTwoFactorGenericParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code a2} property.
     */
    private final MetaProperty<Double> a2 = DirectMetaProperty.ofImmutable(
        this, "a2", RationalTwoFactorGenericParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code correlation} property.
     */
    private final MetaProperty<Double> correlation = DirectMetaProperty.ofImmutable(
        this, "correlation", RationalTwoFactorGenericParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code b0} property.
     */
    private final MetaProperty<ParameterDateCurve> b0 = DirectMetaProperty.ofImmutable(
        this, "b0", RationalTwoFactorGenericParameters.class, ParameterDateCurve.class);
    /**
     * The meta-property for the {@code b1} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Map<IborIndex, ParameterDateCurve>> b1 = DirectMetaProperty.ofImmutable(
        this, "b1", RationalTwoFactorGenericParameters.class, (Class) Map.class);
    /**
     * The meta-property for the {@code b2} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Map<IborIndex, ParameterDateCurve>> b2 = DirectMetaProperty.ofImmutable(
        this, "b2", RationalTwoFactorGenericParameters.class, (Class) Map.class);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", RationalTwoFactorGenericParameters.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", RationalTwoFactorGenericParameters.class, LocalDate.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", RationalTwoFactorGenericParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", RationalTwoFactorGenericParameters.class, ZoneId.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "currency",
        "a1",
        "a2",
        "correlation",
        "b0",
        "b1",
        "b2",
        "timeMeasure",
        "valuationDate",
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
        case 575402001:  // currency
          return currency;
        case 3056:  // a1
          return a1;
        case 3057:  // a2
          return a2;
        case 1706464642:  // correlation
          return correlation;
        case 3086:  // b0
          return b0;
        case 3087:  // b1
          return b1;
        case 3088:  // b2
          return b2;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public RationalTwoFactorGenericParameters.Builder builder() {
      return new RationalTwoFactorGenericParameters.Builder();
    }

    @Override
    public Class<? extends RationalTwoFactorGenericParameters> beanType() {
      return RationalTwoFactorGenericParameters.class;
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
     * The meta-property for the {@code a1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> a1() {
      return a1;
    }

    /**
     * The meta-property for the {@code a2} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> a2() {
      return a2;
    }

    /**
     * The meta-property for the {@code correlation} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> correlation() {
      return correlation;
    }

    /**
     * The meta-property for the {@code b0} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ParameterDateCurve> b0() {
      return b0;
    }

    /**
     * The meta-property for the {@code b1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Map<IborIndex, ParameterDateCurve>> b1() {
      return b1;
    }

    /**
     * The meta-property for the {@code b2} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Map<IborIndex, ParameterDateCurve>> b2() {
      return b2;
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

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return ((RationalTwoFactorGenericParameters) bean).getCurrency();
        case 3056:  // a1
          return ((RationalTwoFactorGenericParameters) bean).getA1();
        case 3057:  // a2
          return ((RationalTwoFactorGenericParameters) bean).getA2();
        case 1706464642:  // correlation
          return ((RationalTwoFactorGenericParameters) bean).getCorrelation();
        case 3086:  // b0
          return ((RationalTwoFactorGenericParameters) bean).getB0();
        case 3087:  // b1
          return ((RationalTwoFactorGenericParameters) bean).getB1();
        case 3088:  // b2
          return ((RationalTwoFactorGenericParameters) bean).getB2();
        case 1642109393:  // timeMeasure
          return ((RationalTwoFactorGenericParameters) bean).getTimeMeasure();
        case 113107279:  // valuationDate
          return ((RationalTwoFactorGenericParameters) bean).getValuationDate();
        case 113591406:  // valuationTime
          return ((RationalTwoFactorGenericParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((RationalTwoFactorGenericParameters) bean).getValuationZone();
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
   * The bean-builder for {@code RationalTwoFactorGenericParameters}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<RationalTwoFactorGenericParameters> {

    private Currency currency;
    private double a1;
    private double a2;
    private double correlation;
    private ParameterDateCurve b0;
    private Map<IborIndex, ParameterDateCurve> b1 = ImmutableMap.of();
    private Map<IborIndex, ParameterDateCurve> b2 = ImmutableMap.of();
    private TimeMeasurement timeMeasure;
    private LocalDate valuationDate;
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
    private Builder(RationalTwoFactorGenericParameters beanToCopy) {
      this.currency = beanToCopy.getCurrency();
      this.a1 = beanToCopy.getA1();
      this.a2 = beanToCopy.getA2();
      this.correlation = beanToCopy.getCorrelation();
      this.b0 = beanToCopy.getB0();
      this.b1 = ImmutableMap.copyOf(beanToCopy.getB1());
      this.b2 = ImmutableMap.copyOf(beanToCopy.getB2());
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.valuationDate = beanToCopy.getValuationDate();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return currency;
        case 3056:  // a1
          return a1;
        case 3057:  // a2
          return a2;
        case 1706464642:  // correlation
          return correlation;
        case 3086:  // b0
          return b0;
        case 3087:  // b1
          return b1;
        case 3088:  // b2
          return b2;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          this.currency = (Currency) newValue;
          break;
        case 3056:  // a1
          this.a1 = (Double) newValue;
          break;
        case 3057:  // a2
          this.a2 = (Double) newValue;
          break;
        case 1706464642:  // correlation
          this.correlation = (Double) newValue;
          break;
        case 3086:  // b0
          this.b0 = (ParameterDateCurve) newValue;
          break;
        case 3087:  // b1
          this.b1 = (Map<IborIndex, ParameterDateCurve>) newValue;
          break;
        case 3088:  // b2
          this.b2 = (Map<IborIndex, ParameterDateCurve>) newValue;
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
    public RationalTwoFactorGenericParameters build() {
      return new RationalTwoFactorGenericParameters(
          currency,
          a1,
          a2,
          correlation,
          b0,
          b1,
          b2,
          timeMeasure,
          valuationDate,
          valuationTime,
          valuationZone);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the model currency
     * @param currency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder currency(Currency currency) {
      JodaBeanUtils.notNull(currency, "currency");
      this.currency = currency;
      return this;
    }

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
     * Sets the time dependent parameter function in front of the martingale in the discount factor evolution.
     * @param b0  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder b0(ParameterDateCurve b0) {
      JodaBeanUtils.notNull(b0, "b0");
      this.b0 = b0;
      return this;
    }

    /**
     * Sets the b1.
     * @param b1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder b1(Map<IborIndex, ParameterDateCurve> b1) {
      JodaBeanUtils.notNull(b1, "b1");
      this.b1 = b1;
      return this;
    }

    /**
     * Sets the b2.
     * @param b2  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder b2(Map<IborIndex, ParameterDateCurve> b2) {
      JodaBeanUtils.notNull(b2, "b2");
      this.b2 = b2;
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

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(384);
      buf.append("RationalTwoFactorGenericParameters.Builder{");
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("a1").append('=').append(JodaBeanUtils.toString(a1)).append(',').append(' ');
      buf.append("a2").append('=').append(JodaBeanUtils.toString(a2)).append(',').append(' ');
      buf.append("correlation").append('=').append(JodaBeanUtils.toString(correlation)).append(',').append(' ');
      buf.append("b0").append('=').append(JodaBeanUtils.toString(b0)).append(',').append(' ');
      buf.append("b1").append('=').append(JodaBeanUtils.toString(b1)).append(',').append(' ');
      buf.append("b2").append('=').append(JodaBeanUtils.toString(b2)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
