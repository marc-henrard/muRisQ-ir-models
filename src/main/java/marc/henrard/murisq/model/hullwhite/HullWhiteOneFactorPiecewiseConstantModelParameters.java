/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.hullwhite;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableConstructor;
import org.joda.beans.gen.PropertyDefinition;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.param.LabelParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;

import java.util.Map;
import java.util.NoSuchElementException;
import org.joda.beans.Bean;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * Interest rate Gaussian HJM one-factor separable model (Hull-White).
 * <p>
 * Wrapper around the Strata objects to fit the {@link SingleCurrencyModelParameters} framework.
 * <p>
 * <i>Implementation Reference: </i>
 * <p>
 * Henrard, M. Hull-White one factor model: results and implementation, muRisQ Model description, May 2020.
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class HullWhiteOneFactorPiecewiseConstantModelParameters
    implements SingleCurrencyModelParameters, ImmutableBean, Serializable {
  
  /** Metadata */
  private final static LabelParameterMetadata METADATA_MR =  LabelParameterMetadata.of("MeanReversion");
  
  /** The model currency */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final Currency currency;
  /** The Strata-type parameters */
  @PropertyDefinition(validate = "notNull")
  private final HullWhiteOneFactorPiecewiseConstantParameters parametersStrata;
  /** The valuation date. All data items in this environment are calibrated for this date. */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate valuationDate;
  /** The valuation time. All data items in this environment are calibrated for this time. */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime valuationTime;
  /** The valuation zone.*/
  @PropertyDefinition(validate = "notNull")
  private final ZoneId valuationZone;
  /** The mechanism to measure time for time to expiry. */
  @PropertyDefinition(validate = "notNull")
  private final TimeMeasurement timeMeasure;
  /** The valuation zone.*/
  private final int nbVolatilities; // Not a property
  /** The valuation zone.*/
  private final ZonedDateTime valuationDateTime; // Not a property

  //-------------------------------------------------------------------------

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }

  public double relativeTime(LocalDate date) {
    return timeMeasure.relativeTime(valuationDateTime, date);
  }
  
  @Override
  public int getParameterCount() {
    return 1 + nbVolatilities; // mean reversion + volatilities
  }

  @Override
  public double getParameter(int parameterIndex) {
    ArgChecker.inRange(parameterIndex, 0, 1 + nbVolatilities, "parameterIndex");
    if (parameterIndex == 0) {
      return parametersStrata.getMeanReversion();
    }
    return parametersStrata.getVolatility().get(1 + parameterIndex);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    ArgChecker.inRange(parameterIndex, 0, 1 + nbVolatilities, "parameterIndex");
    if (parameterIndex == 0) {
      return METADATA_MR;
    }
    return LabelParameterMetadata.of("volatility-" + (parameterIndex - 1));
  }

  @Override
  public HullWhiteOneFactorPiecewiseConstantModelParameters withParameter(int parameterIndex, double newValue) {
    ArgChecker.inRange(parameterIndex, 0, 1 + nbVolatilities, "parameterIndex");
    HullWhiteOneFactorPiecewiseConstantParameters newParameters;
    if (parameterIndex == 0) {
      newParameters =
          HullWhiteOneFactorPiecewiseConstantParameters.of(newValue, parametersStrata.getVolatility(),
              parametersStrata.getVolatilityTime().subArray(1, parametersStrata.getVolatilityTime().size() - 1));

    } else {
      newParameters =
          parametersStrata.withVolatility(parametersStrata.getVolatility().with(parameterIndex, newValue));
    }
    return of(currency, newParameters, valuationDate, valuationTime, valuationZone, timeMeasure);
  }

  //-------------------------------------------------------------------------
  
  @ImmutableConstructor
  private HullWhiteOneFactorPiecewiseConstantModelParameters(
      Currency currency,
      HullWhiteOneFactorPiecewiseConstantParameters parametersStrata,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone,
      TimeMeasurement timeMeasure) {
    
      JodaBeanUtils.notNull(currency, "currency");
      JodaBeanUtils.notNull(parametersStrata, "parameters");
      this.currency = currency;
      this.parametersStrata = parametersStrata;
      this.valuationDate = valuationDate;
      this.valuationTime = valuationTime;
      this.valuationZone = valuationZone;
      this.timeMeasure = timeMeasure;
      this.nbVolatilities = parametersStrata.getVolatility().size();
      this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code HullWhiteOneFactorPiecewiseConstantModelParameters}.
   * @return the meta-bean, not null
   */
  public static HullWhiteOneFactorPiecewiseConstantModelParameters.Meta meta() {
    return HullWhiteOneFactorPiecewiseConstantModelParameters.Meta.INSTANCE;
  }

  static {
    MetaBean.register(HullWhiteOneFactorPiecewiseConstantModelParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param currency  the value of the property, not null
   * @param parametersStrata  the value of the property, not null
   * @param valuationDate  the value of the property, not null
   * @param valuationTime  the value of the property, not null
   * @param valuationZone  the value of the property, not null
   * @param timeMeasure  the value of the property, not null
   * @return the instance
   */
  public static HullWhiteOneFactorPiecewiseConstantModelParameters of(
      Currency currency,
      HullWhiteOneFactorPiecewiseConstantParameters parametersStrata,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone,
      TimeMeasurement timeMeasure) {
    return new HullWhiteOneFactorPiecewiseConstantModelParameters(
      currency,
      parametersStrata,
      valuationDate,
      valuationTime,
      valuationZone,
      timeMeasure);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static HullWhiteOneFactorPiecewiseConstantModelParameters.Builder builder() {
    return new HullWhiteOneFactorPiecewiseConstantModelParameters.Builder();
  }

  @Override
  public HullWhiteOneFactorPiecewiseConstantModelParameters.Meta metaBean() {
    return HullWhiteOneFactorPiecewiseConstantModelParameters.Meta.INSTANCE;
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
   * Gets the Strata-type parameters
   * @return the value of the property, not null
   */
  public HullWhiteOneFactorPiecewiseConstantParameters getParametersStrata() {
    return parametersStrata;
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
   * Gets the mechanism to measure time for time to expiry.
   * @return the value of the property, not null
   */
  public TimeMeasurement getTimeMeasure() {
    return timeMeasure;
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
      HullWhiteOneFactorPiecewiseConstantModelParameters other = (HullWhiteOneFactorPiecewiseConstantModelParameters) obj;
      return JodaBeanUtils.equal(currency, other.currency) &&
          JodaBeanUtils.equal(parametersStrata, other.parametersStrata) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(valuationTime, other.valuationTime) &&
          JodaBeanUtils.equal(valuationZone, other.valuationZone) &&
          JodaBeanUtils.equal(timeMeasure, other.timeMeasure);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(currency);
    hash = hash * 31 + JodaBeanUtils.hashCode(parametersStrata);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(224);
    buf.append("HullWhiteOneFactorPiecewiseConstantModelParameters{");
    buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
    buf.append("parametersStrata").append('=').append(JodaBeanUtils.toString(parametersStrata)).append(',').append(' ');
    buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code HullWhiteOneFactorPiecewiseConstantModelParameters}.
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
        this, "currency", HullWhiteOneFactorPiecewiseConstantModelParameters.class, Currency.class);
    /**
     * The meta-property for the {@code parametersStrata} property.
     */
    private final MetaProperty<HullWhiteOneFactorPiecewiseConstantParameters> parametersStrata = DirectMetaProperty.ofImmutable(
        this, "parametersStrata", HullWhiteOneFactorPiecewiseConstantModelParameters.class, HullWhiteOneFactorPiecewiseConstantParameters.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", HullWhiteOneFactorPiecewiseConstantModelParameters.class, LocalDate.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", HullWhiteOneFactorPiecewiseConstantModelParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", HullWhiteOneFactorPiecewiseConstantModelParameters.class, ZoneId.class);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", HullWhiteOneFactorPiecewiseConstantModelParameters.class, TimeMeasurement.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "currency",
        "parametersStrata",
        "valuationDate",
        "valuationTime",
        "valuationZone",
        "timeMeasure");

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
        case 2138003783:  // parametersStrata
          return parametersStrata;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case 1642109393:  // timeMeasure
          return timeMeasure;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public HullWhiteOneFactorPiecewiseConstantModelParameters.Builder builder() {
      return new HullWhiteOneFactorPiecewiseConstantModelParameters.Builder();
    }

    @Override
    public Class<? extends HullWhiteOneFactorPiecewiseConstantModelParameters> beanType() {
      return HullWhiteOneFactorPiecewiseConstantModelParameters.class;
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
     * The meta-property for the {@code parametersStrata} property.
     * @return the meta-property, not null
     */
    public MetaProperty<HullWhiteOneFactorPiecewiseConstantParameters> parametersStrata() {
      return parametersStrata;
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
     * The meta-property for the {@code timeMeasure} property.
     * @return the meta-property, not null
     */
    public MetaProperty<TimeMeasurement> timeMeasure() {
      return timeMeasure;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return ((HullWhiteOneFactorPiecewiseConstantModelParameters) bean).getCurrency();
        case 2138003783:  // parametersStrata
          return ((HullWhiteOneFactorPiecewiseConstantModelParameters) bean).getParametersStrata();
        case 113107279:  // valuationDate
          return ((HullWhiteOneFactorPiecewiseConstantModelParameters) bean).getValuationDate();
        case 113591406:  // valuationTime
          return ((HullWhiteOneFactorPiecewiseConstantModelParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((HullWhiteOneFactorPiecewiseConstantModelParameters) bean).getValuationZone();
        case 1642109393:  // timeMeasure
          return ((HullWhiteOneFactorPiecewiseConstantModelParameters) bean).getTimeMeasure();
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
   * The bean-builder for {@code HullWhiteOneFactorPiecewiseConstantModelParameters}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<HullWhiteOneFactorPiecewiseConstantModelParameters> {

    private Currency currency;
    private HullWhiteOneFactorPiecewiseConstantParameters parametersStrata;
    private LocalDate valuationDate;
    private LocalTime valuationTime;
    private ZoneId valuationZone;
    private TimeMeasurement timeMeasure;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(HullWhiteOneFactorPiecewiseConstantModelParameters beanToCopy) {
      this.currency = beanToCopy.getCurrency();
      this.parametersStrata = beanToCopy.getParametersStrata();
      this.valuationDate = beanToCopy.getValuationDate();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
      this.timeMeasure = beanToCopy.getTimeMeasure();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return currency;
        case 2138003783:  // parametersStrata
          return parametersStrata;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case 1642109393:  // timeMeasure
          return timeMeasure;
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
        case 2138003783:  // parametersStrata
          this.parametersStrata = (HullWhiteOneFactorPiecewiseConstantParameters) newValue;
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
        case 1642109393:  // timeMeasure
          this.timeMeasure = (TimeMeasurement) newValue;
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
    public HullWhiteOneFactorPiecewiseConstantModelParameters build() {
      return new HullWhiteOneFactorPiecewiseConstantModelParameters(
          currency,
          parametersStrata,
          valuationDate,
          valuationTime,
          valuationZone,
          timeMeasure);
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
     * Sets the Strata-type parameters
     * @param parametersStrata  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder parametersStrata(HullWhiteOneFactorPiecewiseConstantParameters parametersStrata) {
      JodaBeanUtils.notNull(parametersStrata, "parametersStrata");
      this.parametersStrata = parametersStrata;
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
     * Sets the mechanism to measure time for time to expiry.
     * @param timeMeasure  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder timeMeasure(TimeMeasurement timeMeasure) {
      JodaBeanUtils.notNull(timeMeasure, "timeMeasure");
      this.timeMeasure = timeMeasure;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(224);
      buf.append("HullWhiteOneFactorPiecewiseConstantModelParameters.Builder{");
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("parametersStrata").append('=').append(JodaBeanUtils.toString(parametersStrata)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
