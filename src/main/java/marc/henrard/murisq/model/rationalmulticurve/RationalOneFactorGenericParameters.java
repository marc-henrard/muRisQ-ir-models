/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.rationalmulticurve;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.param.LabelParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.ParameterizedDataCombiner;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;

import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.generic.ParameterDateCurve;

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
  
  /** The metadata for the parameter A. */
  private final static ParameterMetadata METADATA_A = LabelParameterMetadata.of("a");

  /** The model currency */
  @PropertyDefinition(validate = "notNull")
  private final Currency currency;
  /** The parameter of the log-normal martingale. */
  @PropertyDefinition
  private final double a;
  /** The time dependent parameter function in front of the martingale in the discount factor evolution. */
  @PropertyDefinition(validate = "notNull")
  private final ParameterDateCurve b0;
  /** List of indices for which the model is valid. */
  @PropertyDefinition(validate = "notNull")
  private final List<IborIndex> indices;
  /** The time dependent parameter function in front of the martingale in the Libor process evolution. 
   * One function for each Ibor index, in the same order as the list of indices. */
  @PropertyDefinition(validate = "notNull")
  private final List<ParameterDateCurve> b1;
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
  /** The indices and curve as a map to facilitate search. */
  private final Map<IborIndex, ParameterDateCurve> b1Map;  // Not a property
  /** The parameter combiner. Contains b0 and the curves of b1 in order but not a. */
  private final transient ParameterizedDataCombiner paramCombiner;  // Not a property
  
  /**
   * Constructor.
   * 
   * @param a  the parameter of the log-normal martingale
   * @param b0  the time dependent parameter function in front of the martingale in the discount factor dynamic
   * @param b1Map  the time dependent parameter function
   * @param dayCount  the day count used to estimate time between dates
   * @param valuationDate  the valuation date
   */
  public static RationalOneFactorGenericParameters of(
      Currency ccy,
      double a,
      ParameterDateCurve b0,
      List<IborIndex> listIndices,
      List<ParameterDateCurve> listCurves,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate) {
    return new RationalOneFactorGenericParameters(ccy, a, b0, listIndices, listCurves,
        timeMeasure, valuationDate, LocalTime.NOON, ZoneOffset.UTC);
  }

  @ImmutableConstructor
  private RationalOneFactorGenericParameters(
      Currency ccy,
      double a,
      ParameterDateCurve b0,
      List<IborIndex> listIndices,
      List<ParameterDateCurve> listCurves,
      TimeMeasurement timeMeasure,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone) {
    this.currency = ccy;
    this.a = a;
    this.b0 = b0;
    this.indices = listIndices;
    ArgChecker.isTrue(listIndices.size() == listCurves.size(),
        "number of indices should be equal to number of curves");
    this.b1 = listCurves;
    this.timeMeasure = timeMeasure;
    this.valuationDate = valuationDate;
    this.valuationTime = valuationTime;
    this.valuationZone = valuationZone;
    this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
    ImmutableMap.Builder<IborIndex, ParameterDateCurve> builder = ImmutableMap.builder();
    for(int loopindex=0; loopindex<listIndices.size(); loopindex++) {
      builder.put(listIndices.get(loopindex), listCurves.get(loopindex));
    }
    this.b1Map = builder.build();
    List<ParameterDateCurve> listCombiner = new ArrayList<>();
    listCombiner.add(b0);
    listCombiner.addAll(listCurves);
    this.paramCombiner = ParameterizedDataCombiner.of(listCombiner);
  }

  //-----------------------------------------------------------------------

  @Override
  public double b0(LocalDate date) {
    return b0.parameterValue(date);
  }
  
  @Override
  public ParameterDateCurve b0() {
    return b0;
  }

  @Override
  public double b1(IborIndexObservation obs) {
    return b1Map.get(obs.getIndex()).parameterValue(obs.getFixingDate());
  }

  @Override
  public PointSensitivityBuilder b0Sensitivity(LocalDate date) {
    return b0.parameterValueCurveSensitivity(date);
  }

  @Override
  public PointSensitivityBuilder b1Sensitivity(IborIndexObservation obs) {
    return b1Map.get(obs.getIndex()).parameterValueCurveSensitivity(obs.getFixingDate());
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

  @Override
  public int getParameterCount() {
    return 1 + paramCombiner.getParameterCount();
  }

  @Override
  public double getParameter(int parameterIndex) {
    if(parameterIndex == 0) {
      return a;
    }
    return paramCombiner.getParameter(parameterIndex - 1);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    if(parameterIndex == 0) {
      return METADATA_A;
    }
    return paramCombiner.getParameterMetadata(parameterIndex - 1);
  }

  @Override
  public RationalOneFactorGenericParameters withParameter(int parameterIndex, double newValue) {
    if (parameterIndex == 0) {
      return new RationalOneFactorGenericParameters(
          currency, newValue, b0, indices, b1,
          timeMeasure, valuationDate, valuationTime, valuationZone);
    }
    List<ParameterDateCurve> listModified =
        paramCombiner.withParameter(ParameterDateCurve.class, parameterIndex - 1, newValue);
    return new RationalOneFactorGenericParameters(
        currency, a, listModified.get(0), indices, listModified.subList(1, listModified.size()),
        timeMeasure, valuationDate, valuationTime, valuationZone);
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code RationalOneFactorGenericParameters}.
   * @return the meta-bean, not null
   */
  public static RationalOneFactorGenericParameters.Meta meta() {
    return RationalOneFactorGenericParameters.Meta.INSTANCE;
  }

  static {
    MetaBean.register(RationalOneFactorGenericParameters.Meta.INSTANCE);
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

  //-----------------------------------------------------------------------
  /**
   * Gets the model currency
   * @return the value of the property, not null
   */
  public Currency getCurrency() {
    return currency;
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
   * Gets list of indices for which the model is valid.
   * @return the value of the property, not null
   */
  public List<IborIndex> getIndices() {
    return indices;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the b1.
   * @return the value of the property, not null
   */
  public List<ParameterDateCurve> getB1() {
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
      return JodaBeanUtils.equal(currency, other.currency) &&
          JodaBeanUtils.equal(a, other.a) &&
          JodaBeanUtils.equal(b0, other.b0) &&
          JodaBeanUtils.equal(indices, other.indices) &&
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
    hash = hash * 31 + JodaBeanUtils.hashCode(currency);
    hash = hash * 31 + JodaBeanUtils.hashCode(a);
    hash = hash * 31 + JodaBeanUtils.hashCode(b0);
    hash = hash * 31 + JodaBeanUtils.hashCode(indices);
    hash = hash * 31 + JodaBeanUtils.hashCode(b1);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(320);
    buf.append("RationalOneFactorGenericParameters{");
    buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
    buf.append("a").append('=').append(JodaBeanUtils.toString(a)).append(',').append(' ');
    buf.append("b0").append('=').append(JodaBeanUtils.toString(b0)).append(',').append(' ');
    buf.append("indices").append('=').append(JodaBeanUtils.toString(indices)).append(',').append(' ');
    buf.append("b1").append('=').append(JodaBeanUtils.toString(b1)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
    buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
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
     * The meta-property for the {@code currency} property.
     */
    private final MetaProperty<Currency> currency = DirectMetaProperty.ofImmutable(
        this, "currency", RationalOneFactorGenericParameters.class, Currency.class);
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
     * The meta-property for the {@code indices} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<IborIndex>> indices = DirectMetaProperty.ofImmutable(
        this, "indices", RationalOneFactorGenericParameters.class, (Class) List.class);
    /**
     * The meta-property for the {@code b1} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<ParameterDateCurve>> b1 = DirectMetaProperty.ofImmutable(
        this, "b1", RationalOneFactorGenericParameters.class, (Class) List.class);
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
        "currency",
        "a",
        "b0",
        "indices",
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
        case 575402001:  // currency
          return currency;
        case 97:  // a
          return a;
        case 3086:  // b0
          return b0;
        case 1943391143:  // indices
          return indices;
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
     * The meta-property for the {@code currency} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Currency> currency() {
      return currency;
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
     * The meta-property for the {@code indices} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<IborIndex>> indices() {
      return indices;
    }

    /**
     * The meta-property for the {@code b1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<ParameterDateCurve>> b1() {
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
        case 575402001:  // currency
          return ((RationalOneFactorGenericParameters) bean).getCurrency();
        case 97:  // a
          return ((RationalOneFactorGenericParameters) bean).getA();
        case 3086:  // b0
          return ((RationalOneFactorGenericParameters) bean).getB0();
        case 1943391143:  // indices
          return ((RationalOneFactorGenericParameters) bean).getIndices();
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

    private Currency currency;
    private double a;
    private ParameterDateCurve b0;
    private List<IborIndex> indices = ImmutableList.of();
    private List<ParameterDateCurve> b1 = ImmutableList.of();
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
      this.currency = beanToCopy.getCurrency();
      this.a = beanToCopy.getA();
      this.b0 = beanToCopy.getB0();
      this.indices = ImmutableList.copyOf(beanToCopy.getIndices());
      this.b1 = ImmutableList.copyOf(beanToCopy.getB1());
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
        case 97:  // a
          return a;
        case 3086:  // b0
          return b0;
        case 1943391143:  // indices
          return indices;
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
        case 575402001:  // currency
          this.currency = (Currency) newValue;
          break;
        case 97:  // a
          this.a = (Double) newValue;
          break;
        case 3086:  // b0
          this.b0 = (ParameterDateCurve) newValue;
          break;
        case 1943391143:  // indices
          this.indices = (List<IborIndex>) newValue;
          break;
        case 3087:  // b1
          this.b1 = (List<ParameterDateCurve>) newValue;
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

    @Override
    public RationalOneFactorGenericParameters build() {
      return new RationalOneFactorGenericParameters(
          currency,
          a,
          b0,
          indices,
          b1,
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
     * Sets list of indices for which the model is valid.
     * @param indices  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder indices(List<IborIndex> indices) {
      JodaBeanUtils.notNull(indices, "indices");
      this.indices = indices;
      return this;
    }

    /**
     * Sets the {@code indices} property in the builder
     * from an array of objects.
     * @param indices  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder indices(IborIndex... indices) {
      return indices(ImmutableList.copyOf(indices));
    }

    /**
     * Sets the b1.
     * @param b1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder b1(List<ParameterDateCurve> b1) {
      JodaBeanUtils.notNull(b1, "b1");
      this.b1 = b1;
      return this;
    }

    /**
     * Sets the {@code b1} property in the builder
     * from an array of objects.
     * @param b1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder b1(ParameterDateCurve... b1) {
      return b1(ImmutableList.copyOf(b1));
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
      StringBuilder buf = new StringBuilder(320);
      buf.append("RationalOneFactorGenericParameters.Builder{");
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("a").append('=').append(JodaBeanUtils.toString(a)).append(',').append(' ');
      buf.append("b0").append('=').append(JodaBeanUtils.toString(b0)).append(',').append(' ');
      buf.append("indices").append('=').append(JodaBeanUtils.toString(indices)).append(',').append(' ');
      buf.append("b1").append('=').append(JodaBeanUtils.toString(b1)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
