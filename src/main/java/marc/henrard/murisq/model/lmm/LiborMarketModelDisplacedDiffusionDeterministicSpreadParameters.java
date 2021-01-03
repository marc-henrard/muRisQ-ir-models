/**
 * Copyright (C) 2011 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableConstructor;
import org.joda.beans.gen.PropertyDefinition;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.market.param.ParameterMetadata;

import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * LIBOR/Forward Market model with displaced diffusion.
 * <p>
 * The dynamic is on the discounting forward; the LIBOR rates are obtain by deterministic multiplicative spreads.
 * <p>
 * The model parameters are the volatilities.
 * <p>
 * Implementation reference:
 * Henrard, M. Libor/Forward Market Model in the multi-curve framework, muRisQ Model description, September 2020.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public final class LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters
    implements SingleCurrencyModelParameters, ImmutableBean, Serializable {
  
  /** The model currency */
  private final Currency currency;  // Not a property
  /** The overnight index represented by the forward rates. */
  @PropertyDefinition(validate = "notNull")
  private final OvernightIndex overnightIndex;
  /** The IBOR index modeled by the multiplicative spreads. */
  @PropertyDefinition(validate = "notNull")
  private final IborIndex iborIndex;
  /** The valuation date. All data items in this environment are calibrated for this date. */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate valuationDate;
  /** The valuation time. All data items in this environment are calibrated for this time. */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime valuationTime;
  /** The valuation zone.*/
  @PropertyDefinition(validate = "notNull")
  private final ZoneId valuationZone;
  /** The valuation zoned date and time. */
  private final ZonedDateTime valuationDateTime;  // Not a property
  /** The mechanism to measure time for time to expiry. */
  @PropertyDefinition(validate = "notNull")
  private final TimeMeasurement timeMeasure;
  /** The times separating the Ibor periods. In increasing order. */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray iborTimes;
  /** The accrual factors for the different periods. */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray accrualFactors;
  /** The multiplicative spread between the forward discounting rates and the forward LIBOR rates. */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray multiplicativeSpreads;
  /** The displacements for the different periods. */
  @PropertyDefinition(validate = "notNull")
  private final DoubleArray displacements;
  /** The volatilities. The dimensions of the volatility is number of periods (rows) X number of factors (columns. */
  @PropertyDefinition(validate = "notNull")
  private final DoubleMatrix volatilities;
  /** The mean reversion. */
  @PropertyDefinition
  private final double meanReversion;
  /** The time tolerance to indicate that two dates are equal. */
  @PropertyDefinition(validate = "notNull")
  private final Double timeTolerance;
  
  @ImmutableConstructor
  private LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters(
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone,
      TimeMeasurement timeMeasure,
      DoubleArray iborTimes,
      DoubleArray accrualFactor,
      DoubleArray multiplicativeSpreads,
      DoubleArray displacement,
      DoubleMatrix volatility,
      double meanReversion,
      Double timeTolerance) {
    
    this.overnightIndex = overnightIndex;
    ArgChecker.isTrue(overnightIndex.getCurrency().equals(iborIndex.getCurrency()), 
        "iborIndex and overnightIndex must have the same currency");
    this.iborIndex = iborIndex;
    this.currency = overnightIndex.getCurrency();
    this.valuationDate = valuationDate;
    this.valuationTime = valuationTime;
    this.valuationZone = valuationZone;
    this.valuationDateTime = ZonedDateTime.of(valuationDate, valuationTime, valuationZone);
    this.timeMeasure = timeMeasure;
    this.iborTimes = iborTimes;
    this.accrualFactors = accrualFactor;
    this.multiplicativeSpreads = multiplicativeSpreads;
    this.displacements = displacement;
    this.volatilities = volatility;
    ArgChecker.isTrue(accrualFactor.size() == displacement.size(),
        "number of accrual factors must be equal to number of displacements");
    ArgChecker.isTrue(accrualFactor.size() == multiplicativeSpreads.size(),
        "number of accrual factors must be equal to number of spreads");
    ArgChecker.isTrue(accrualFactor.size() == volatility.rowCount(),
        "number of accrual factors must be equal to number of volatilities rows");
    this.meanReversion = meanReversion;
    this.timeTolerance = timeTolerance; // Default?
  }

  @Override
  public int getParameterCount() {
    return volatilities.size();
  }
  
  public int getFactorCount() {
    return volatilities.columnCount();
  }
  
  public int getIborPeriodsCount() {
    return volatilities.rowCount();
  }

  /**
   * The indices in the ibor times corresponding to the input times.
   * <p>
   * The relevant Ibor time is the first one larger than the (input time minus the time tolerance).
   * 
   * @param times  the times for which the indices are requested
   * @return the indices
   */
  public int[] getIborTimeIndex(double[] times) {
    int nbTimes = times.length;
    int[] timeIndices = new int[nbTimes];
    for (int i = 0; i < nbTimes; i++) {
      int index = Arrays.binarySearch(iborTimes.toArrayUnsafe(), times[i] - timeTolerance);
      if (index > 0) { // exact match
        timeIndices[i] = index;
      } else {
        timeIndices[i] = -index - 1;
      }
    }
    return timeIndices;
  }

  @Override
  /**
   * Order of parameters: By period and in a period by factor.
   */
  public double getParameter(int parameterIndex) {
    int nbColumns = volatilities.columnCount(); // Number of factors
    return volatilities.get(parameterIndex / nbColumns, parameterIndex % nbColumns);
  }

  @Override
  public double relativeTime(ZonedDateTime dateTime) {
    return timeMeasure.relativeTime(valuationDateTime, dateTime);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    throw new RuntimeException("No metadata for this model.");
  }

  @Override
  public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters withParameter(int parameterIndex, double newValue) {
    double[][] volArray = volatilities.toArray();
    int nbRows = volatilities.rowCount();
    volArray[parameterIndex / nbRows][parameterIndex % nbRows] = newValue;
    DoubleMatrix newVolatilities = DoubleMatrix.ofUnsafe(volArray);
    return this.toBuilder().volatilities(newVolatilities).build();
  }
  
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  @Override
  public Currency getCurrency() {
    return currency;
  }
  
  /**
   * Returns the IBOR rate on a given period for a given pseudo-discounting curve forward.
   * 
   * @param dscForward  the forward on the pseudo-discounting curve
   * @param index  the index of the IBOR rate
   * @return the IBOR rate
   */
  public double iborRateFromDscForwards(double dscForward, int index) {
    double iborRate = (multiplicativeSpreads.get(index) * (1 + accrualFactors.get(index) * dscForward) - 1.0d) 
        / accrualFactors.get(index);
    return iborRate;
  }
  
  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters}.
   * @return the meta-bean, not null
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Meta meta() {
    return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Meta.INSTANCE;
  }

  static {
    MetaBean.register(LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Builder builder() {
    return new LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Builder();
  }

  @Override
  public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Meta metaBean() {
    return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the overnight index represented by the forward rates.
   * @return the value of the property, not null
   */
  public OvernightIndex getOvernightIndex() {
    return overnightIndex;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the IBOR index modeled by the multiplicative spreads.
   * @return the value of the property, not null
   */
  public IborIndex getIborIndex() {
    return iborIndex;
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
   * Gets the times separating the Ibor periods. In increasing order.
   * @return the value of the property, not null
   */
  public DoubleArray getIborTimes() {
    return iborTimes;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the accrual factors for the different periods.
   * @return the value of the property, not null
   */
  public DoubleArray getAccrualFactors() {
    return accrualFactors;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the multiplicative spread between the forward discounting rates and the forward LIBOR rates.
   * @return the value of the property, not null
   */
  public DoubleArray getMultiplicativeSpreads() {
    return multiplicativeSpreads;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the displacements for the different periods.
   * @return the value of the property, not null
   */
  public DoubleArray getDisplacements() {
    return displacements;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the volatilities. The dimensions of the volatility is number of periods (rows) X number of factors (columns.
   * @return the value of the property, not null
   */
  public DoubleMatrix getVolatilities() {
    return volatilities;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mean reversion.
   * @return the value of the property
   */
  public double getMeanReversion() {
    return meanReversion;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time tolerance to indicate that two dates are equal.
   * @return the value of the property, not null
   */
  public Double getTimeTolerance() {
    return timeTolerance;
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
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters other = (LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) obj;
      return JodaBeanUtils.equal(overnightIndex, other.overnightIndex) &&
          JodaBeanUtils.equal(iborIndex, other.iborIndex) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(valuationTime, other.valuationTime) &&
          JodaBeanUtils.equal(valuationZone, other.valuationZone) &&
          JodaBeanUtils.equal(timeMeasure, other.timeMeasure) &&
          JodaBeanUtils.equal(iborTimes, other.iborTimes) &&
          JodaBeanUtils.equal(accrualFactors, other.accrualFactors) &&
          JodaBeanUtils.equal(multiplicativeSpreads, other.multiplicativeSpreads) &&
          JodaBeanUtils.equal(displacements, other.displacements) &&
          JodaBeanUtils.equal(volatilities, other.volatilities) &&
          JodaBeanUtils.equal(meanReversion, other.meanReversion) &&
          JodaBeanUtils.equal(timeTolerance, other.timeTolerance);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(overnightIndex);
    hash = hash * 31 + JodaBeanUtils.hashCode(iborIndex);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(iborTimes);
    hash = hash * 31 + JodaBeanUtils.hashCode(accrualFactors);
    hash = hash * 31 + JodaBeanUtils.hashCode(multiplicativeSpreads);
    hash = hash * 31 + JodaBeanUtils.hashCode(displacements);
    hash = hash * 31 + JodaBeanUtils.hashCode(volatilities);
    hash = hash * 31 + JodaBeanUtils.hashCode(meanReversion);
    hash = hash * 31 + JodaBeanUtils.hashCode(timeTolerance);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(448);
    buf.append("LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters{");
    buf.append("overnightIndex").append('=').append(JodaBeanUtils.toString(overnightIndex)).append(',').append(' ');
    buf.append("iborIndex").append('=').append(JodaBeanUtils.toString(iborIndex)).append(',').append(' ');
    buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
    buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
    buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
    buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
    buf.append("iborTimes").append('=').append(JodaBeanUtils.toString(iborTimes)).append(',').append(' ');
    buf.append("accrualFactors").append('=').append(JodaBeanUtils.toString(accrualFactors)).append(',').append(' ');
    buf.append("multiplicativeSpreads").append('=').append(JodaBeanUtils.toString(multiplicativeSpreads)).append(',').append(' ');
    buf.append("displacements").append('=').append(JodaBeanUtils.toString(displacements)).append(',').append(' ');
    buf.append("volatilities").append('=').append(JodaBeanUtils.toString(volatilities)).append(',').append(' ');
    buf.append("meanReversion").append('=').append(JodaBeanUtils.toString(meanReversion)).append(',').append(' ');
    buf.append("timeTolerance").append('=').append(JodaBeanUtils.toString(timeTolerance));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code overnightIndex} property.
     */
    private final MetaProperty<OvernightIndex> overnightIndex = DirectMetaProperty.ofImmutable(
        this, "overnightIndex", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, OvernightIndex.class);
    /**
     * The meta-property for the {@code iborIndex} property.
     */
    private final MetaProperty<IborIndex> iborIndex = DirectMetaProperty.ofImmutable(
        this, "iborIndex", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, IborIndex.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, LocalDate.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, ZoneId.class);
    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code iborTimes} property.
     */
    private final MetaProperty<DoubleArray> iborTimes = DirectMetaProperty.ofImmutable(
        this, "iborTimes", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code accrualFactors} property.
     */
    private final MetaProperty<DoubleArray> accrualFactors = DirectMetaProperty.ofImmutable(
        this, "accrualFactors", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code multiplicativeSpreads} property.
     */
    private final MetaProperty<DoubleArray> multiplicativeSpreads = DirectMetaProperty.ofImmutable(
        this, "multiplicativeSpreads", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code displacements} property.
     */
    private final MetaProperty<DoubleArray> displacements = DirectMetaProperty.ofImmutable(
        this, "displacements", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, DoubleArray.class);
    /**
     * The meta-property for the {@code volatilities} property.
     */
    private final MetaProperty<DoubleMatrix> volatilities = DirectMetaProperty.ofImmutable(
        this, "volatilities", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, DoubleMatrix.class);
    /**
     * The meta-property for the {@code meanReversion} property.
     */
    private final MetaProperty<Double> meanReversion = DirectMetaProperty.ofImmutable(
        this, "meanReversion", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, Double.TYPE);
    /**
     * The meta-property for the {@code timeTolerance} property.
     */
    private final MetaProperty<Double> timeTolerance = DirectMetaProperty.ofImmutable(
        this, "timeTolerance", LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class, Double.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "overnightIndex",
        "iborIndex",
        "valuationDate",
        "valuationTime",
        "valuationZone",
        "timeMeasure",
        "iborTimes",
        "accrualFactors",
        "multiplicativeSpreads",
        "displacements",
        "volatilities",
        "meanReversion",
        "timeTolerance");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 292087662:  // overnightIndex
          return overnightIndex;
        case 1255740790:  // iborIndex
          return iborIndex;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case 1265759210:  // iborTimes
          return iborTimes;
        case -505352107:  // accrualFactors
          return accrualFactors;
        case 1919950890:  // multiplicativeSpreads
          return multiplicativeSpreads;
        case 648803644:  // displacements
          return displacements;
        case -625639549:  // volatilities
          return volatilities;
        case -2016560896:  // meanReversion
          return meanReversion;
        case -1231350848:  // timeTolerance
          return timeTolerance;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Builder builder() {
      return new LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Builder();
    }

    @Override
    public Class<? extends LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters> beanType() {
      return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code overnightIndex} property.
     * @return the meta-property, not null
     */
    public MetaProperty<OvernightIndex> overnightIndex() {
      return overnightIndex;
    }

    /**
     * The meta-property for the {@code iborIndex} property.
     * @return the meta-property, not null
     */
    public MetaProperty<IborIndex> iborIndex() {
      return iborIndex;
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

    /**
     * The meta-property for the {@code iborTimes} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> iborTimes() {
      return iborTimes;
    }

    /**
     * The meta-property for the {@code accrualFactors} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> accrualFactors() {
      return accrualFactors;
    }

    /**
     * The meta-property for the {@code multiplicativeSpreads} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> multiplicativeSpreads() {
      return multiplicativeSpreads;
    }

    /**
     * The meta-property for the {@code displacements} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> displacements() {
      return displacements;
    }

    /**
     * The meta-property for the {@code volatilities} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleMatrix> volatilities() {
      return volatilities;
    }

    /**
     * The meta-property for the {@code meanReversion} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> meanReversion() {
      return meanReversion;
    }

    /**
     * The meta-property for the {@code timeTolerance} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> timeTolerance() {
      return timeTolerance;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 292087662:  // overnightIndex
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getOvernightIndex();
        case 1255740790:  // iborIndex
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getIborIndex();
        case 113107279:  // valuationDate
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getValuationDate();
        case 113591406:  // valuationTime
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getValuationZone();
        case 1642109393:  // timeMeasure
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getTimeMeasure();
        case 1265759210:  // iborTimes
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getIborTimes();
        case -505352107:  // accrualFactors
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getAccrualFactors();
        case 1919950890:  // multiplicativeSpreads
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getMultiplicativeSpreads();
        case 648803644:  // displacements
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getDisplacements();
        case -625639549:  // volatilities
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getVolatilities();
        case -2016560896:  // meanReversion
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getMeanReversion();
        case -1231350848:  // timeTolerance
          return ((LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) bean).getTimeTolerance();
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
   * The bean-builder for {@code LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters> {

    private OvernightIndex overnightIndex;
    private IborIndex iborIndex;
    private LocalDate valuationDate;
    private LocalTime valuationTime;
    private ZoneId valuationZone;
    private TimeMeasurement timeMeasure;
    private DoubleArray iborTimes;
    private DoubleArray accrualFactors;
    private DoubleArray multiplicativeSpreads;
    private DoubleArray displacements;
    private DoubleMatrix volatilities;
    private double meanReversion;
    private Double timeTolerance;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters beanToCopy) {
      this.overnightIndex = beanToCopy.getOvernightIndex();
      this.iborIndex = beanToCopy.getIborIndex();
      this.valuationDate = beanToCopy.getValuationDate();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.iborTimes = beanToCopy.getIborTimes();
      this.accrualFactors = beanToCopy.getAccrualFactors();
      this.multiplicativeSpreads = beanToCopy.getMultiplicativeSpreads();
      this.displacements = beanToCopy.getDisplacements();
      this.volatilities = beanToCopy.getVolatilities();
      this.meanReversion = beanToCopy.getMeanReversion();
      this.timeTolerance = beanToCopy.getTimeTolerance();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 292087662:  // overnightIndex
          return overnightIndex;
        case 1255740790:  // iborIndex
          return iborIndex;
        case 113107279:  // valuationDate
          return valuationDate;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case 1265759210:  // iborTimes
          return iborTimes;
        case -505352107:  // accrualFactors
          return accrualFactors;
        case 1919950890:  // multiplicativeSpreads
          return multiplicativeSpreads;
        case 648803644:  // displacements
          return displacements;
        case -625639549:  // volatilities
          return volatilities;
        case -2016560896:  // meanReversion
          return meanReversion;
        case -1231350848:  // timeTolerance
          return timeTolerance;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 292087662:  // overnightIndex
          this.overnightIndex = (OvernightIndex) newValue;
          break;
        case 1255740790:  // iborIndex
          this.iborIndex = (IborIndex) newValue;
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
        case 1265759210:  // iborTimes
          this.iborTimes = (DoubleArray) newValue;
          break;
        case -505352107:  // accrualFactors
          this.accrualFactors = (DoubleArray) newValue;
          break;
        case 1919950890:  // multiplicativeSpreads
          this.multiplicativeSpreads = (DoubleArray) newValue;
          break;
        case 648803644:  // displacements
          this.displacements = (DoubleArray) newValue;
          break;
        case -625639549:  // volatilities
          this.volatilities = (DoubleMatrix) newValue;
          break;
        case -2016560896:  // meanReversion
          this.meanReversion = (Double) newValue;
          break;
        case -1231350848:  // timeTolerance
          this.timeTolerance = (Double) newValue;
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
    public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters build() {
      return new LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters(
          overnightIndex,
          iborIndex,
          valuationDate,
          valuationTime,
          valuationZone,
          timeMeasure,
          iborTimes,
          accrualFactors,
          multiplicativeSpreads,
          displacements,
          volatilities,
          meanReversion,
          timeTolerance);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the overnight index represented by the forward rates.
     * @param overnightIndex  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder overnightIndex(OvernightIndex overnightIndex) {
      JodaBeanUtils.notNull(overnightIndex, "overnightIndex");
      this.overnightIndex = overnightIndex;
      return this;
    }

    /**
     * Sets the IBOR index modeled by the multiplicative spreads.
     * @param iborIndex  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborIndex(IborIndex iborIndex) {
      JodaBeanUtils.notNull(iborIndex, "iborIndex");
      this.iborIndex = iborIndex;
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

    /**
     * Sets the times separating the Ibor periods. In increasing order.
     * @param iborTimes  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborTimes(DoubleArray iborTimes) {
      JodaBeanUtils.notNull(iborTimes, "iborTimes");
      this.iborTimes = iborTimes;
      return this;
    }

    /**
     * Sets the accrual factors for the different periods.
     * @param accrualFactors  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder accrualFactors(DoubleArray accrualFactors) {
      JodaBeanUtils.notNull(accrualFactors, "accrualFactors");
      this.accrualFactors = accrualFactors;
      return this;
    }

    /**
     * Sets the multiplicative spread between the forward discounting rates and the forward LIBOR rates.
     * @param multiplicativeSpreads  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder multiplicativeSpreads(DoubleArray multiplicativeSpreads) {
      JodaBeanUtils.notNull(multiplicativeSpreads, "multiplicativeSpreads");
      this.multiplicativeSpreads = multiplicativeSpreads;
      return this;
    }

    /**
     * Sets the displacements for the different periods.
     * @param displacements  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder displacements(DoubleArray displacements) {
      JodaBeanUtils.notNull(displacements, "displacements");
      this.displacements = displacements;
      return this;
    }

    /**
     * Sets the volatilities. The dimensions of the volatility is number of periods (rows) X number of factors (columns.
     * @param volatilities  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder volatilities(DoubleMatrix volatilities) {
      JodaBeanUtils.notNull(volatilities, "volatilities");
      this.volatilities = volatilities;
      return this;
    }

    /**
     * Sets the mean reversion.
     * @param meanReversion  the new value
     * @return this, for chaining, not null
     */
    public Builder meanReversion(double meanReversion) {
      this.meanReversion = meanReversion;
      return this;
    }

    /**
     * Sets the time tolerance to indicate that two dates are equal.
     * @param timeTolerance  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder timeTolerance(Double timeTolerance) {
      JodaBeanUtils.notNull(timeTolerance, "timeTolerance");
      this.timeTolerance = timeTolerance;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(448);
      buf.append("LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.Builder{");
      buf.append("overnightIndex").append('=').append(JodaBeanUtils.toString(overnightIndex)).append(',').append(' ');
      buf.append("iborIndex").append('=').append(JodaBeanUtils.toString(iborIndex)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("iborTimes").append('=').append(JodaBeanUtils.toString(iborTimes)).append(',').append(' ');
      buf.append("accrualFactors").append('=').append(JodaBeanUtils.toString(accrualFactors)).append(',').append(' ');
      buf.append("multiplicativeSpreads").append('=').append(JodaBeanUtils.toString(multiplicativeSpreads)).append(',').append(' ');
      buf.append("displacements").append('=').append(JodaBeanUtils.toString(displacements)).append(',').append(' ');
      buf.append("volatilities").append('=').append(JodaBeanUtils.toString(volatilities)).append(',').append(' ');
      buf.append("meanReversion").append('=').append(JodaBeanUtils.toString(meanReversion)).append(',').append(' ');
      buf.append("timeTolerance").append('=').append(JodaBeanUtils.toString(timeTolerance));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
