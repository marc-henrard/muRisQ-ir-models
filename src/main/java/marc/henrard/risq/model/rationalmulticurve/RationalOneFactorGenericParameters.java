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
import java.util.Map;

import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableConstructor;
import org.joda.beans.PropertyDefinition;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;

import marc.henrard.risq.model.generic.ParameterDateCurve;
import marc.henrard.risq.model.generic.TimeMeasurement;
import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorParameters;
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
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2015).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * The coefficient of A1 are index and fixing date dependent.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
@BeanDefinition
public final class RationalOneFactorGenericParameters 
    implements RationalOneFactorParameters, ImmutableBean, Serializable {

  /** The model currency */
  @PropertyDefinition(validate = "notNull")
  private final Currency ccy;
  /** The parameter of the log-normal martingale. */
  @PropertyDefinition
  private final double a;
  /** The time dependent parameter function in front of the martingale in the discount factor evolution. */
  @PropertyDefinition(validate = "notNull")
  private final ParameterDateCurve b0;
  /** The time dependent parameter function in front of the martingale in the Libor process evolution. 
   * One function for each Ibor index. */
  @PropertyDefinition(validate = "notNull")
  private final Map<IborIndex, ParameterDateCurve> b1;
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
  
  /**
   * Constructor.
   * 
   * @param a  the parameter of the log-normal martingale
   * @param b0  the time dependent parameter function in front of the martingale in the discount factor dynamic
   * @param b1  the time dependent parameter function
   * @param dayCount  the day count used to estimate time between dates
   * @param valuationDate  the valuation date
   */
  public static RationalOneFactorGenericParameters of(
      Currency ccy,
      double a, 
      ParameterDateCurve b0, 
      Map<IborIndex, ParameterDateCurve> b1, 
      TimeMeasurement timeMeasure, 
      LocalDate valuationDate) {
    return new RationalOneFactorGenericParameters(ccy, a, b0, b1, timeMeasure, valuationDate, LocalTime.NOON, ZoneOffset.UTC);
  }

  @ImmutableConstructor
  private RationalOneFactorGenericParameters(
      Currency ccy,
      double a,
      ParameterDateCurve b0,
      Map<IborIndex, ParameterDateCurve> b1,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone) {
    this.ccy = ccy;
    this.a = a;
    this.b0 = b0;
    this.b1 = b1;
    this.timeMeasure = timeMeasure;
    this.valuationDate = valuationDate;
    this.valuationTime = valuationTime;
    this.valuationZone = valuationZone;
    this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
  }

  //-----------------------------------------------------------------------

  @Override
  public Currency currency() {
    return ccy;
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
  public PointSensitivityBuilder b0Sensitivity(LocalDate date) {
    return b0.parameterValueCurveSensitivity(date);
  }

  @Override
  public PointSensitivityBuilder b1Sensitivity(IborIndexObservation obs) {
    return b1.get(obs.getIndex()).parameterValueCurveSensitivity(obs.getFixingDate());
  }

  @Override
  public double a() {
    return a;
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }

  //-----------------------------------------------------------------------

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code RationalOneFactorGenericParameters}.
   * @return the meta-bean, not null
   */
  public static RationalOneFactorGenericParameters.Meta meta() {
    return RationalOneFactorGenericParameters.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(RationalOneFactorGenericParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RationalOneFactorGenericParameters.Builder builder() {
    return new RationalOneFactorGenericParameters.Builder();
  }

  @Override
  public RationalOneFactorGenericParameters.Meta metaBean() {
    return RationalOneFactorGenericParameters.Meta.INSTANCE;
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
  public Currency getCcy() {
    return ccy;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the parameter of the log-normal martingale.
   * @return the value of the property
   */
  public double getA() {
    return a;
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
      RationalOneFactorGenericParameters other = (RationalOneFactorGenericParameters) obj;
      return JodaBeanUtils.equal(ccy, other.ccy) &&
          JodaBeanUtils.equal(a, other.a) &&
          JodaBeanUtils.equal(b0, other.b0) &&
          JodaBeanUtils.equal(b1, other.b1) &&
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
    hash = hash * 31 + JodaBeanUtils.hashCode(ccy);
    hash = hash * 31 + JodaBeanUtils.hashCode(a);
    hash = hash * 31 + JodaBeanUtils.hashCode(b0);
    hash = hash * 31 + JodaBeanUtils.hashCode(b1);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(288);
    buf.append("RationalOneFactorGenericParameters{");
    buf.append("ccy").append('=').append(ccy).append(',').append(' ');
    buf.append("a").append('=').append(a).append(',').append(' ');
    buf.append("b0").append('=').append(b0).append(',').append(' ');
    buf.append("b1").append('=').append(b1).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(timeMeasure).append(',').append(' ');
    buf.append("valuationDate").append('=').append(valuationDate).append(',').append(' ');
    buf.append("valuationTime").append('=').append(valuationTime).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RationalOneFactorGenericParameters}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code ccy} property.
     */
    private final MetaProperty<Currency> ccy = DirectMetaProperty.ofImmutable(
        this, "ccy", RationalOneFactorGenericParameters.class, Currency.class);
    /**
     * The meta-property for the {@code a} property.
     */
    private final MetaProperty<Double> a = DirectMetaProperty.ofImmutable(
        this, "a", RationalOneFactorGenericParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code b0} property.
     */
    private final MetaProperty<ParameterDateCurve> b0 = DirectMetaProperty.ofImmutable(
        this, "b0", RationalOneFactorGenericParameters.class, ParameterDateCurve.class);
    /**
     * The meta-property for the {@code b1} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Map<IborIndex, ParameterDateCurve>> b1 = DirectMetaProperty.ofImmutable(
        this, "b1", RationalOneFactorGenericParameters.class, (Class) Map.class);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", RationalOneFactorGenericParameters.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", RationalOneFactorGenericParameters.class, LocalDate.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", RationalOneFactorGenericParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", RationalOneFactorGenericParameters.class, ZoneId.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "ccy",
        "a",
        "b0",
        "b1",
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
        case 98329:  // ccy
          return ccy;
        case 97:  // a
          return a;
        case 3086:  // b0
          return b0;
        case 3087:  // b1
          return b1;
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
    public RationalOneFactorGenericParameters.Builder builder() {
      return new RationalOneFactorGenericParameters.Builder();
    }

    @Override
    public Class<? extends RationalOneFactorGenericParameters> beanType() {
      return RationalOneFactorGenericParameters.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code ccy} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Currency> ccy() {
      return ccy;
    }

    /**
     * The meta-property for the {@code a} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> a() {
      return a;
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
        case 98329:  // ccy
          return ((RationalOneFactorGenericParameters) bean).getCcy();
        case 97:  // a
          return ((RationalOneFactorGenericParameters) bean).getA();
        case 3086:  // b0
          return ((RationalOneFactorGenericParameters) bean).getB0();
        case 3087:  // b1
          return ((RationalOneFactorGenericParameters) bean).getB1();
        case 1642109393:  // timeMeasure
          return ((RationalOneFactorGenericParameters) bean).getTimeMeasure();
        case 113107279:  // valuationDate
          return ((RationalOneFactorGenericParameters) bean).getValuationDate();
        case 113591406:  // valuationTime
          return ((RationalOneFactorGenericParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((RationalOneFactorGenericParameters) bean).getValuationZone();
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
   * The bean-builder for {@code RationalOneFactorGenericParameters}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<RationalOneFactorGenericParameters> {

    private Currency ccy;
    private double a;
    private ParameterDateCurve b0;
    private Map<IborIndex, ParameterDateCurve> b1 = ImmutableMap.of();
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
    private Builder(RationalOneFactorGenericParameters beanToCopy) {
      this.ccy = beanToCopy.getCcy();
      this.a = beanToCopy.getA();
      this.b0 = beanToCopy.getB0();
      this.b1 = ImmutableMap.copyOf(beanToCopy.getB1());
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.valuationDate = beanToCopy.getValuationDate();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 98329:  // ccy
          return ccy;
        case 97:  // a
          return a;
        case 3086:  // b0
          return b0;
        case 3087:  // b1
          return b1;
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
        case 98329:  // ccy
          this.ccy = (Currency) newValue;
          break;
        case 97:  // a
          this.a = (Double) newValue;
          break;
        case 3086:  // b0
          this.b0 = (ParameterDateCurve) newValue;
          break;
        case 3087:  // b1
          this.b1 = (Map<IborIndex, ParameterDateCurve>) newValue;
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
    public RationalOneFactorGenericParameters build() {
      return new RationalOneFactorGenericParameters(
          ccy,
          a,
          b0,
          b1,
          timeMeasure,
          valuationDate,
          valuationTime,
          valuationZone);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the model currency
     * @param ccy  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder ccy(Currency ccy) {
      JodaBeanUtils.notNull(ccy, "ccy");
      this.ccy = ccy;
      return this;
    }

    /**
     * Sets the parameter of the log-normal martingale.
     * @param a  the new value
     * @return this, for chaining, not null
     */
    public Builder a(double a) {
      this.a = a;
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
      StringBuilder buf = new StringBuilder(288);
      buf.append("RationalOneFactorGenericParameters.Builder{");
      buf.append("ccy").append('=').append(JodaBeanUtils.toString(ccy)).append(',').append(' ');
      buf.append("a").append('=').append(JodaBeanUtils.toString(a)).append(',').append(' ');
      buf.append("b0").append('=').append(JodaBeanUtils.toString(b0)).append(',').append(' ');
      buf.append("b1").append('=').append(JodaBeanUtils.toString(b1)).append(',').append(' ');
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
