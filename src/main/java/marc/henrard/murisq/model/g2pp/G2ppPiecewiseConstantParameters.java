/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.g2pp;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.joda.beans.ImmutableBean;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableConstructor;
import org.joda.beans.gen.ImmutablePreBuild;
import org.joda.beans.gen.ImmutableValidator;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.LabelParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;

import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.impl.direct.DirectMetaProperty;

/**
 * Interest rate Gaussian HJM two-factor additive model (G2++).
 * <p>
 * <i>Implementation Reference: </i>
 * <p>
 * Henrard, M. G2++ two-factor model. Model implementation documentation. muRisQ documentation. 
 *   First version: 28 December 2009; latest version: 5 June 2020.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public class G2ppPiecewiseConstantParameters
    implements SingleCurrencyModelParameters, ImmutableBean, Serializable {
  
  /** Metadata */
  private final static List<ParameterMetadata> METADATA_1 = 
      ImmutableList.of(
          LabelParameterMetadata.of("correlation"),
          LabelParameterMetadata.of("kappa1"),
          LabelParameterMetadata.of("kappa2"));
  /**
   * The time used to represent infinity.
   * <p>
   * The last element of {@code volatilityTime} must be this value.
   */
  private static final double VOLATILITY_TIME_INFINITY = 1000d;

  /** The model currency */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final Currency currency;
  /** The correlation between the Brownian motions. Parameter 0. */
  @PropertyDefinition
  private final double correlation;
  /** The mean reversion parameter for the first dimension. Parameter 1. */
  @PropertyDefinition
  private final double kappa1;
  /** The mean reversion parameter for the second dimension.  Parameter 2. */
  @PropertyDefinition
  private final double kappa2;
  /**
   * The volatility parameters for the first dimension. Parameters 3 to p1.
   * <p>
   * The volatility is constant between the volatility times, i.e., volatility value at t is {@code volatility.get(i)} 
   * for any t between {@code volatilityTime.get(i)} and {@code volatilityTime.get(i+1)}.
   */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray volatility1; 
  /**
   * The volatility parameters for the second dimension. Parameters p1+1 to parameterCount-1.
   * <p>
   * The volatility is constant between the volatility times, i.e., volatility value at t is {@code volatility.get(i)} 
   * for any t between {@code volatilityTime.get(i)} and {@code volatilityTime.get(i+1)}.
   */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray volatility2;
  /**
   * The times separating the constant volatility periods. The same periods are used for both volatilities.
   * <p>
   * The time should be sorted by increasing order. The first time is 0 and the last time is 1000 (represents infinity).
   * These extra times are added in {@link #of(double, DoubleArray, DoubleArray)}.
   * <p>
   * Not parameters.
   */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray volatilityTime;
  
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
  
  /**
   * Returns the two mean reversions in an array.
   * 
   * @return the mean reversions
   */
  public double[] getMeanReversions() {
    return new double[]{kappa1, kappa2};
  }

  //-------------------------------------------------------------------------

  @Override
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }
  
  @Override
  public int getParameterCount() {
    return 3 + 2 * nbVolatilities;
  }

  @Override
  public double getParameter(int parameterIndex) {
    ArgChecker.inRange(parameterIndex, 0, 3 + 2 * nbVolatilities,
        "parameterIndex");
    if (parameterIndex == 0) {
      return correlation;
    }
    if (parameterIndex == 1) {
      return kappa1;
    }
    if (parameterIndex == 2) {
      return kappa2;
    }
    if (parameterIndex <= 2 + nbVolatilities) {
      return volatility1.get(parameterIndex - 3);
    }
    return volatility2.get(parameterIndex - 3 - nbVolatilities);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    ArgChecker.inRange(parameterIndex, 0, 3 + 2 * nbVolatilities,
        "parameterIndex");
    if (parameterIndex < 3) {
      return METADATA_1.get(parameterIndex);
    }
    if (parameterIndex <= 2 + nbVolatilities) {
      return LabelParameterMetadata.of("volatility1-" + (parameterIndex - 3));
    }
    return LabelParameterMetadata.of("volatility2-" + (parameterIndex - 3 - nbVolatilities));
  }

  @Override
  public G2ppPiecewiseConstantParameters withParameter(int parameterIndex, double newValue) {
    G2ppPiecewiseConstantParameters.Builder builder = this.toBuilder();
    ArgChecker.inRange(parameterIndex, 0, 3 + 2 * nbVolatilities,
        "parameterIndex");
    if (parameterIndex == 0) {
      return builder.correlation(newValue).build();
    }
    if (parameterIndex == 1) {
      return builder.kappa1(newValue).build();
    }
    if (parameterIndex == 2) {
      return builder.kappa2(newValue).build();
    }
    if (parameterIndex <= 2 + nbVolatilities) {
      double[] v = volatility1.toArray();
      v[parameterIndex - 3] = newValue;
      return builder.volatility1(DoubleArray.ofUnsafe(v)).build();
    }
    double[] v = volatility2.toArray();
    v[parameterIndex - 3 - nbVolatilities] = newValue;
    return builder.volatility2(DoubleArray.ofUnsafe(v)).build();
  }

  //-------------------------------------------------------------------------
  @ImmutableValidator
  private void validate() {
    int sizeTime = volatilityTime.size();
    ArgChecker.isTrue(sizeTime == volatility1.size() + 1, "size mismatch between volatility1 and volatilityTime");
    ArgChecker.isTrue(sizeTime == volatility2.size() + 1, "size mismatch between volatility2 and volatilityTime");
    for (int i = 1; i < sizeTime; ++i) {
      ArgChecker.isTrue(volatilityTime.get(i - 1) < volatilityTime.get(i), "volatility times should be increasing");
    }
    ArgChecker.isTrue(kappa1 > 0, "mean reversion must be > 0");
    ArgChecker.isTrue(kappa2 > 0, "mean reversion must be > 0");
  }
  
  @ImmutablePreBuild
  private static void preBuild(Builder builder) {
    DoubleArray volatilityTime = builder.volatilityTime;
    double[] volatilityTime2 = new double[volatilityTime.size() + 2];
    volatilityTime2[0] = 0d;
    volatilityTime2[volatilityTime.size() + 1] = VOLATILITY_TIME_INFINITY;
    System.arraycopy(volatilityTime.toArray(), 0, volatilityTime2, 1, volatilityTime.size());
    builder.volatilityTime(DoubleArray.ofUnsafe(volatilityTime2));
  }
  
  @ImmutableConstructor
  private G2ppPiecewiseConstantParameters(
      Currency currency,
      double correlation,
      double kappa1,
      double kappa2,
      DoubleArray volatility1,
      DoubleArray volatility2,
      DoubleArray volatilityTime,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone,
      TimeMeasurement timeMeasure) {
    
      JodaBeanUtils.notNull(currency, "currency");
      JodaBeanUtils.notNull(volatility1, "volatility1");
      JodaBeanUtils.notNull(volatility2, "volatility2");
      JodaBeanUtils.notNull(volatilityTime, "volatilityTime");
      this.currency = currency;
      this.correlation = correlation;
      this.kappa1 = kappa1;
      this.kappa2 = kappa2;
      this.volatility1 = volatility1;
      this.volatility2 = volatility2;
      this.volatilityTime = volatilityTime;
      this.valuationDate = valuationDate;
      this.valuationTime = valuationTime;
      this.valuationZone = valuationZone;
      this.timeMeasure = timeMeasure;
      this.nbVolatilities = volatility1.size();
      this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
      validate();
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code G2ppPiecewiseConstantParameters}.
   * @return the meta-bean, not null
   */
  public static G2ppPiecewiseConstantParameters.Meta meta() {
    return G2ppPiecewiseConstantParameters.Meta.INSTANCE;
  }

  static {
    MetaBean.register(G2ppPiecewiseConstantParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static G2ppPiecewiseConstantParameters.Builder builder() {
    return new G2ppPiecewiseConstantParameters.Builder();
  }

  @Override
  public G2ppPiecewiseConstantParameters.Meta metaBean() {
    return G2ppPiecewiseConstantParameters.Meta.INSTANCE;
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
   * Gets the correlation between the Brownian motions. Parameter 0.
   * @return the value of the property
   */
  public double getCorrelation() {
    return correlation;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mean reversion parameter for the first dimension. Parameter 1.
   * @return the value of the property
   */
  public double getKappa1() {
    return kappa1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mean reversion parameter for the second dimension.  Parameter 2.
   * @return the value of the property
   */
  public double getKappa2() {
    return kappa2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatility parameters for the first dimension. Parameters 3 to p1.
   * <p>
   * The volatility is constant between the volatility times, i.e., volatility value at t is {@code volatility.get(i)}
   * for any t between {@code volatilityTime.get(i)} and {@code volatilityTime.get(i+1)}.
   * @return the value of the property, not null
   */
  public DoubleArray getVolatility1() {
    return volatility1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatility parameters for the second dimension. Parameters p1+1 to parameterCount-1.
   * <p>
   * The volatility is constant between the volatility times, i.e., volatility value at t is {@code volatility.get(i)}
   * for any t between {@code volatilityTime.get(i)} and {@code volatilityTime.get(i+1)}.
   * @return the value of the property, not null
   */
  public DoubleArray getVolatility2() {
    return volatility2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the times separating the constant volatility periods. The same periods are used for both volatilities.
   * <p>
   * The time should be sorted by increasing order. The first time is 0 and the last time is 1000 (represents infinity).
   * These extra times are added in {@link #of(double, DoubleArray, DoubleArray)}.
   * <p>
   * Not parameters.
   * @return the value of the property, not null
   */
  public DoubleArray getVolatilityTime() {
    return volatilityTime;
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
      G2ppPiecewiseConstantParameters other = (G2ppPiecewiseConstantParameters) obj;
      return JodaBeanUtils.equal(currency, other.currency) &&
          JodaBeanUtils.equal(correlation, other.correlation) &&
          JodaBeanUtils.equal(kappa1, other.kappa1) &&
          JodaBeanUtils.equal(kappa2, other.kappa2) &&
          JodaBeanUtils.equal(volatility1, other.volatility1) &&
          JodaBeanUtils.equal(volatility2, other.volatility2) &&
          JodaBeanUtils.equal(volatilityTime, other.volatilityTime) &&
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
    hash = hash * 31 + JodaBeanUtils.hashCode(correlation);
    hash = hash * 31 + JodaBeanUtils.hashCode(kappa1);
    hash = hash * 31 + JodaBeanUtils.hashCode(kappa2);
    hash = hash * 31 + JodaBeanUtils.hashCode(volatility1);
    hash = hash * 31 + JodaBeanUtils.hashCode(volatility2);
    hash = hash * 31 + JodaBeanUtils.hashCode(volatilityTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(384);
    buf.append("G2ppPiecewiseConstantParameters{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
    buf.append("correlation").append('=').append(JodaBeanUtils.toString(correlation)).append(',').append(' ');
    buf.append("kappa1").append('=').append(JodaBeanUtils.toString(kappa1)).append(',').append(' ');
    buf.append("kappa2").append('=').append(JodaBeanUtils.toString(kappa2)).append(',').append(' ');
    buf.append("volatility1").append('=').append(JodaBeanUtils.toString(volatility1)).append(',').append(' ');
    buf.append("volatility2").append('=').append(JodaBeanUtils.toString(volatility2)).append(',').append(' ');
    buf.append("volatilityTime").append('=').append(JodaBeanUtils.toString(volatilityTime)).append(',').append(' ');
    buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code G2ppPiecewiseConstantParameters}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code currency} property.
     */
    private final MetaProperty<Currency> currency = DirectMetaProperty.ofImmutable(
        this, "currency", G2ppPiecewiseConstantParameters.class, Currency.class);
    /**
     * The meta-property for the {@code correlation} property.
     */
    private final MetaProperty<Double> correlation = DirectMetaProperty.ofImmutable(
        this, "correlation", G2ppPiecewiseConstantParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code kappa1} property.
     */
    private final MetaProperty<Double> kappa1 = DirectMetaProperty.ofImmutable(
        this, "kappa1", G2ppPiecewiseConstantParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code kappa2} property.
     */
    private final MetaProperty<Double> kappa2 = DirectMetaProperty.ofImmutable(
        this, "kappa2", G2ppPiecewiseConstantParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code volatility1} property.
     */
    private final MetaProperty<DoubleArray> volatility1 = DirectMetaProperty.ofImmutable(
        this, "volatility1", G2ppPiecewiseConstantParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code volatility2} property.
     */
    private final MetaProperty<DoubleArray> volatility2 = DirectMetaProperty.ofImmutable(
        this, "volatility2", G2ppPiecewiseConstantParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code volatilityTime} property.
     */
    private final MetaProperty<DoubleArray> volatilityTime = DirectMetaProperty.ofImmutable(
        this, "volatilityTime", G2ppPiecewiseConstantParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", G2ppPiecewiseConstantParameters.class, LocalDate.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", G2ppPiecewiseConstantParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", G2ppPiecewiseConstantParameters.class, ZoneId.class);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", G2ppPiecewiseConstantParameters.class, TimeMeasurement.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "currency",
        "correlation",
        "kappa1",
        "kappa2",
        "volatility1",
        "volatility2",
        "volatilityTime",
        "valuationDate",
        "valuationTime",
        "valuationZone",
        "timeMeasure");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return currency;
        case 1706464642:  // correlation
          return correlation;
        case -1138619322:  // kappa1
          return kappa1;
        case -1138619321:  // kappa2
          return kappa2;
        case 672555180:  // volatility1
          return volatility1;
        case 672555181:  // volatility2
          return volatility2;
        case 70078610:  // volatilityTime
          return volatilityTime;
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
    public G2ppPiecewiseConstantParameters.Builder builder() {
      return new G2ppPiecewiseConstantParameters.Builder();
    }

    @Override
    public Class<? extends G2ppPiecewiseConstantParameters> beanType() {
      return G2ppPiecewiseConstantParameters.class;
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
    public final MetaProperty<Currency> currency() {
      return currency;
    }

    /**
     * The meta-property for the {@code correlation} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> correlation() {
      return correlation;
    }

    /**
     * The meta-property for the {@code kappa1} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> kappa1() {
      return kappa1;
    }

    /**
     * The meta-property for the {@code kappa2} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Double> kappa2() {
      return kappa2;
    }

    /**
     * The meta-property for the {@code volatility1} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<DoubleArray> volatility1() {
      return volatility1;
    }

    /**
     * The meta-property for the {@code volatility2} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<DoubleArray> volatility2() {
      return volatility2;
    }

    /**
     * The meta-property for the {@code volatilityTime} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<DoubleArray> volatilityTime() {
      return volatilityTime;
    }

    /**
     * The meta-property for the {@code valuationDate} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LocalDate> valuationDate() {
      return valuationDate;
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

    /**
     * The meta-property for the {@code timeMeasure} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<TimeMeasurement> timeMeasure() {
      return timeMeasure;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 575402001:  // currency
          return ((G2ppPiecewiseConstantParameters) bean).getCurrency();
        case 1706464642:  // correlation
          return ((G2ppPiecewiseConstantParameters) bean).getCorrelation();
        case -1138619322:  // kappa1
          return ((G2ppPiecewiseConstantParameters) bean).getKappa1();
        case -1138619321:  // kappa2
          return ((G2ppPiecewiseConstantParameters) bean).getKappa2();
        case 672555180:  // volatility1
          return ((G2ppPiecewiseConstantParameters) bean).getVolatility1();
        case 672555181:  // volatility2
          return ((G2ppPiecewiseConstantParameters) bean).getVolatility2();
        case 70078610:  // volatilityTime
          return ((G2ppPiecewiseConstantParameters) bean).getVolatilityTime();
        case 113107279:  // valuationDate
          return ((G2ppPiecewiseConstantParameters) bean).getValuationDate();
        case 113591406:  // valuationTime
          return ((G2ppPiecewiseConstantParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((G2ppPiecewiseConstantParameters) bean).getValuationZone();
        case 1642109393:  // timeMeasure
          return ((G2ppPiecewiseConstantParameters) bean).getTimeMeasure();
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
   * The bean-builder for {@code G2ppPiecewiseConstantParameters}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<G2ppPiecewiseConstantParameters> {

    private Currency currency;
    private double correlation;
    private double kappa1;
    private double kappa2;
    private DoubleArray volatility1;
    private DoubleArray volatility2;
    private DoubleArray volatilityTime;
    private LocalDate valuationDate;
    private LocalTime valuationTime;
    private ZoneId valuationZone;
    private TimeMeasurement timeMeasure;

    /**
     * Restricted constructor.
     */
    protected Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(G2ppPiecewiseConstantParameters beanToCopy) {
      this.currency = beanToCopy.getCurrency();
      this.correlation = beanToCopy.getCorrelation();
      this.kappa1 = beanToCopy.getKappa1();
      this.kappa2 = beanToCopy.getKappa2();
      this.volatility1 = beanToCopy.getVolatility1();
      this.volatility2 = beanToCopy.getVolatility2();
      this.volatilityTime = beanToCopy.getVolatilityTime();
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
        case 1706464642:  // correlation
          return correlation;
        case -1138619322:  // kappa1
          return kappa1;
        case -1138619321:  // kappa2
          return kappa2;
        case 672555180:  // volatility1
          return volatility1;
        case 672555181:  // volatility2
          return volatility2;
        case 70078610:  // volatilityTime
          return volatilityTime;
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
        case 1706464642:  // correlation
          this.correlation = (Double) newValue;
          break;
        case -1138619322:  // kappa1
          this.kappa1 = (Double) newValue;
          break;
        case -1138619321:  // kappa2
          this.kappa2 = (Double) newValue;
          break;
        case 672555180:  // volatility1
          this.volatility1 = (DoubleArray) newValue;
          break;
        case 672555181:  // volatility2
          this.volatility2 = (DoubleArray) newValue;
          break;
        case 70078610:  // volatilityTime
          this.volatilityTime = (DoubleArray) newValue;
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
    public G2ppPiecewiseConstantParameters build() {
      preBuild(this);
      return new G2ppPiecewiseConstantParameters(
          currency,
          correlation,
          kappa1,
          kappa2,
          volatility1,
          volatility2,
          volatilityTime,
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
     * Sets the correlation between the Brownian motions. Parameter 0.
     * @param correlation  the new value
     * @return this, for chaining, not null
     */
    public Builder correlation(double correlation) {
      this.correlation = correlation;
      return this;
    }

    /**
     * Sets the mean reversion parameter for the first dimension. Parameter 1.
     * @param kappa1  the new value
     * @return this, for chaining, not null
     */
    public Builder kappa1(double kappa1) {
      this.kappa1 = kappa1;
      return this;
    }

    /**
     * Sets the mean reversion parameter for the second dimension.  Parameter 2.
     * @param kappa2  the new value
     * @return this, for chaining, not null
     */
    public Builder kappa2(double kappa2) {
      this.kappa2 = kappa2;
      return this;
    }

    /**
     * Sets the volatility parameters for the first dimension. Parameters 3 to p1.
     * <p>
     * The volatility is constant between the volatility times, i.e., volatility value at t is {@code volatility.get(i)}
     * for any t between {@code volatilityTime.get(i)} and {@code volatilityTime.get(i+1)}.
     * @param volatility1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder volatility1(DoubleArray volatility1) {
      JodaBeanUtils.notNull(volatility1, "volatility1");
      this.volatility1 = volatility1;
      return this;
    }

    /**
     * Sets the volatility parameters for the second dimension. Parameters p1+1 to parameterCount-1.
     * <p>
     * The volatility is constant between the volatility times, i.e., volatility value at t is {@code volatility.get(i)}
     * for any t between {@code volatilityTime.get(i)} and {@code volatilityTime.get(i+1)}.
     * @param volatility2  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder volatility2(DoubleArray volatility2) {
      JodaBeanUtils.notNull(volatility2, "volatility2");
      this.volatility2 = volatility2;
      return this;
    }

    /**
     * Sets the times separating the constant volatility periods. The same periods are used for both volatilities.
     * <p>
     * The time should be sorted by increasing order. The first time is 0 and the last time is 1000 (represents infinity).
     * These extra times are added in {@link #of(double, DoubleArray, DoubleArray)}.
     * <p>
     * Not parameters.
     * @param volatilityTime  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder volatilityTime(DoubleArray volatilityTime) {
      JodaBeanUtils.notNull(volatilityTime, "volatilityTime");
      this.volatilityTime = volatilityTime;
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
      StringBuilder buf = new StringBuilder(384);
      buf.append("G2ppPiecewiseConstantParameters.Builder{");
      int len = buf.length();
      toString(buf);
      if (buf.length() > len) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }

    protected void toString(StringBuilder buf) {
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("correlation").append('=').append(JodaBeanUtils.toString(correlation)).append(',').append(' ');
      buf.append("kappa1").append('=').append(JodaBeanUtils.toString(kappa1)).append(',').append(' ');
      buf.append("kappa2").append('=').append(JodaBeanUtils.toString(kappa2)).append(',').append(' ');
      buf.append("volatility1").append('=').append(JodaBeanUtils.toString(volatility1)).append(',').append(' ');
      buf.append("volatility2").append('=').append(JodaBeanUtils.toString(volatility2)).append(',').append(' ');
      buf.append("volatilityTime").append('=').append(JodaBeanUtils.toString(volatilityTime)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
