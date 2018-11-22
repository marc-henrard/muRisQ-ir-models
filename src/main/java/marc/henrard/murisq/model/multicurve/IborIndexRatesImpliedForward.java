/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joda.beans.Bean;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.Messages;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.MarketDataName;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.ParameterPerturbation;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.IborRateSensitivity;

/**
 * Implementation of {@link IborIndexRates} based on an existing index rates taken at a 
 * forward date and a time series up to the new valuation date.
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class IborIndexRatesImpliedForward
    implements IborIndexRates, ImmutableBean, Serializable {

  /** The underlying provider. */
  @PropertyDefinition(validate = "notNull")
  private final IborIndexRates underlying;
  /** The forward valuation date. */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final LocalDate valuationDate;
  /** The time series of fixings up to the valuation date. */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final LocalDateDoubleTimeSeries fixings;

  @Override
  public double rate(IborIndexObservation observation) {
    if (!observation.getFixingDate().isAfter(getValuationDate())) {
      return pastRate(observation);
    }
    return rateIgnoringFixings(observation);
  }

  /**
   * Returns the rate from the time series, or if absent return the rate from the curve.
   * 
   * @param observation  the IBOR observation
   * @return the rate
   */
  public double pastRate(IborIndexObservation observation) {
    LocalDate fixingDate = observation.getFixingDate();
    OptionalDouble fixedRate = fixings.get(fixingDate);
    if (fixedRate.isPresent()) {
      return fixedRate.getAsDouble();
    } else if (fixingDate.isBefore(getValuationDate())) { // the fixing is required
      if (fixings.isEmpty()) {
        throw new IllegalArgumentException(
            Messages.format("Unable to get fixing for {} on date {}, no time-series supplied", 
                underlying.getIndex(), fixingDate));
      }
      throw new IllegalArgumentException(Messages.format("Unable to get fixing for {} on date {}", 
          underlying.getIndex(), fixingDate));
    } else {
      return rateIgnoringFixings(observation);
    }
  }

  @Override
  public double rateIgnoringFixings(IborIndexObservation observation) {
    return underlying.rateIgnoringFixings(observation);
  }

  @Override
  public PointSensitivityBuilder ratePointSensitivity(IborIndexObservation observation) {
    LocalDate fixingDate = observation.getFixingDate();
    LocalDate valuationDate = getValuationDate();
    if (fixingDate.isBefore(valuationDate) ||
        (fixingDate.equals(valuationDate) && fixings.get(fixingDate).isPresent())) {
      return PointSensitivityBuilder.none();
    }
    return IborRateSensitivity.of(observation, 1d);
  }

  @Override
  public PointSensitivityBuilder rateIgnoringFixingsPointSensitivity(IborIndexObservation observation) {
    return underlying.rateIgnoringFixingsPointSensitivity(observation);
  }

  @Override
  public CurrencyParameterSensitivities parameterSensitivity(IborRateSensitivity pointSensitivity) {
    return underlying.parameterSensitivity(pointSensitivity);
  }

  @Override
  public CurrencyParameterSensitivities createParameterSensitivity(Currency currency, DoubleArray sensitivities) {
    return underlying.createParameterSensitivity(currency, sensitivities);
  }
  
  @Override
  public <T> Optional<T> findData(MarketDataName<T> name) {
    return underlying.findData(name);
  }
  
  @Override
  public int getParameterCount() {
    return underlying.getParameterCount();
  }

  @Override
  public double getParameter(int parameterIndex) {
    return underlying.getParameter(parameterIndex);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    return underlying.getParameterMetadata(parameterIndex);
  }

  @Override
  public IborIndex getIndex() {
    return underlying.getIndex();
  }

  @Override
  public IborIndexRates withParameter(int parameterIndex, double newValue) {
    return IborIndexRatesImpliedForward
        .of(underlying.withParameter(parameterIndex, newValue), valuationDate, fixings);
  }

  @Override
  public IborIndexRates withPerturbation(ParameterPerturbation perturbation) {
    return IborIndexRatesImpliedForward
        .of(underlying.withPerturbation(perturbation), valuationDate, fixings);
  }
  

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code IborIndexRatesImpliedForward}.
   * @return the meta-bean, not null
   */
  public static IborIndexRatesImpliedForward.Meta meta() {
    return IborIndexRatesImpliedForward.Meta.INSTANCE;
  }

  static {
    MetaBean.register(IborIndexRatesImpliedForward.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param underlying  the value of the property, not null
   * @param valuationDate  the value of the property, not null
   * @param fixings  the value of the property, not null
   * @return the instance
   */
  public static IborIndexRatesImpliedForward of(
      IborIndexRates underlying,
      LocalDate valuationDate,
      LocalDateDoubleTimeSeries fixings) {
    return new IborIndexRatesImpliedForward(
      underlying,
      valuationDate,
      fixings);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static IborIndexRatesImpliedForward.Builder builder() {
    return new IborIndexRatesImpliedForward.Builder();
  }

  private IborIndexRatesImpliedForward(
      IborIndexRates underlying,
      LocalDate valuationDate,
      LocalDateDoubleTimeSeries fixings) {
    JodaBeanUtils.notNull(underlying, "underlying");
    JodaBeanUtils.notNull(valuationDate, "valuationDate");
    JodaBeanUtils.notNull(fixings, "fixings");
    this.underlying = underlying;
    this.valuationDate = valuationDate;
    this.fixings = fixings;
  }

  @Override
  public IborIndexRatesImpliedForward.Meta metaBean() {
    return IborIndexRatesImpliedForward.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the underlying provider.
   * @return the value of the property, not null
   */
  public IborIndexRates getUnderlying() {
    return underlying;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the forward valuation date.
   * @return the value of the property, not null
   */
  @Override
  public LocalDate getValuationDate() {
    return valuationDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time series of fixings up to the valuation date.
   * @return the value of the property, not null
   */
  @Override
  public LocalDateDoubleTimeSeries getFixings() {
    return fixings;
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
      IborIndexRatesImpliedForward other = (IborIndexRatesImpliedForward) obj;
      return JodaBeanUtils.equal(underlying, other.underlying) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(fixings, other.fixings);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(underlying);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(fixings);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("IborIndexRatesImpliedForward{");
    buf.append("underlying").append('=').append(underlying).append(',').append(' ');
    buf.append("valuationDate").append('=').append(valuationDate).append(',').append(' ');
    buf.append("fixings").append('=').append(JodaBeanUtils.toString(fixings));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code IborIndexRatesImpliedForward}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code underlying} property.
     */
    private final MetaProperty<IborIndexRates> underlying = DirectMetaProperty.ofImmutable(
        this, "underlying", IborIndexRatesImpliedForward.class, IborIndexRates.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", IborIndexRatesImpliedForward.class, LocalDate.class);
    /**
     * The meta-property for the {@code fixings} property.
     */
    private final MetaProperty<LocalDateDoubleTimeSeries> fixings = DirectMetaProperty.ofImmutable(
        this, "fixings", IborIndexRatesImpliedForward.class, LocalDateDoubleTimeSeries.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "underlying",
        "valuationDate",
        "fixings");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1770633379:  // underlying
          return underlying;
        case 113107279:  // valuationDate
          return valuationDate;
        case -843784602:  // fixings
          return fixings;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public IborIndexRatesImpliedForward.Builder builder() {
      return new IborIndexRatesImpliedForward.Builder();
    }

    @Override
    public Class<? extends IborIndexRatesImpliedForward> beanType() {
      return IborIndexRatesImpliedForward.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code underlying} property.
     * @return the meta-property, not null
     */
    public MetaProperty<IborIndexRates> underlying() {
      return underlying;
    }

    /**
     * The meta-property for the {@code valuationDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> valuationDate() {
      return valuationDate;
    }

    /**
     * The meta-property for the {@code fixings} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDateDoubleTimeSeries> fixings() {
      return fixings;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1770633379:  // underlying
          return ((IborIndexRatesImpliedForward) bean).getUnderlying();
        case 113107279:  // valuationDate
          return ((IborIndexRatesImpliedForward) bean).getValuationDate();
        case -843784602:  // fixings
          return ((IborIndexRatesImpliedForward) bean).getFixings();
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
   * The bean-builder for {@code IborIndexRatesImpliedForward}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<IborIndexRatesImpliedForward> {

    private IborIndexRates underlying;
    private LocalDate valuationDate;
    private LocalDateDoubleTimeSeries fixings;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(IborIndexRatesImpliedForward beanToCopy) {
      this.underlying = beanToCopy.getUnderlying();
      this.valuationDate = beanToCopy.getValuationDate();
      this.fixings = beanToCopy.getFixings();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1770633379:  // underlying
          return underlying;
        case 113107279:  // valuationDate
          return valuationDate;
        case -843784602:  // fixings
          return fixings;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1770633379:  // underlying
          this.underlying = (IborIndexRates) newValue;
          break;
        case 113107279:  // valuationDate
          this.valuationDate = (LocalDate) newValue;
          break;
        case -843784602:  // fixings
          this.fixings = (LocalDateDoubleTimeSeries) newValue;
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
    public IborIndexRatesImpliedForward build() {
      return new IborIndexRatesImpliedForward(
          underlying,
          valuationDate,
          fixings);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the underlying provider.
     * @param underlying  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder underlying(IborIndexRates underlying) {
      JodaBeanUtils.notNull(underlying, "underlying");
      this.underlying = underlying;
      return this;
    }

    /**
     * Sets the forward valuation date.
     * @param valuationDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder valuationDate(LocalDate valuationDate) {
      JodaBeanUtils.notNull(valuationDate, "valuationDate");
      this.valuationDate = valuationDate;
      return this;
    }

    /**
     * Sets the time series of fixings up to the valuation date.
     * @param fixings  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixings(LocalDateDoubleTimeSeries fixings) {
      JodaBeanUtils.notNull(fixings, "fixings");
      this.fixings = fixings;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("IborIndexRatesImpliedForward.Builder{");
      buf.append("underlying").append('=').append(JodaBeanUtils.toString(underlying)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("fixings").append('=').append(JodaBeanUtils.toString(fixings));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
