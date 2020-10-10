/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package com.opengamma.strata.product.swap;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.joda.beans.Bean;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutablePreBuild;
import org.joda.beans.gen.ImmutableValidator;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DateAdjuster;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.product.swap.NegativeRateMethod;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RateCalculation;
import com.opengamma.strata.product.swap.SwapLegType;

import marc.henrard.murisq.product.rate.IborRatchetRateComputation;
/**
 * Defines the calculation of a floating ratchet leg based on an Ibor index.
 * <p>
 * Inspired by {@link IborRateCalculation}.
 */
@BeanDefinition(factoryName = "of")
public final class IborRatchetRateCalculation
    implements RateCalculation, ImmutableBean, Serializable {

  /**
   * The day count convention.
   * <p>
   * This is used to convert dates to a numerical value.
   * <p>
   * When building, this will default to the day count of the index if not specified.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final DayCount dayCount;
  /**
   * The Ibor index.
   * <p>
   * The rate to be paid is based on this index
   * It will be a well known market index such as 'GBP-LIBOR-3M'.
   */
  @PropertyDefinition(validate = "notNull")
  private final IborIndex index;
  /**
   * The offset of the fixing date from each adjusted reset date.
   * <p>
   * The offset is applied to the base date specified by {@code fixingRelativeTo}.
   * The offset is typically a negative number of business days.
   * <p>
   * Note that in most cases, the reset frequency matches the accrual frequency
   * and thus there is only one fixing for the accrual period.
   * <p>
   * When building, this will default to the fixing offset of the index if not specified.
   */
  @PropertyDefinition(validate = "notNull")
  private final DaysAdjustment fixingDateOffset;
  /**
   * The ratchet coefficients: main previous, main Ibor, main fixed, floor previous, floor Ibor, floor fixed,
   * cap previous, cap Ibor, cap fixed.
   */
  @PropertyDefinition(validate = "notNull")
  private final List<ValueSchedule> coefficients;

  //-------------------------------------------------------------------------
  /**
   * Obtains a rate calculation for the specified index.
   * <p>
   * The ratchet coefficients are main previous, main Ibor, main fixed, floor previous, floor Ibor, floor fixed,
   * cap previous, cap Ibor, cap fixed.
   * 
   * @param index  the index
   * @param coefficients  the coefficients as ValueSchedule
   * @return the calculation
   */
  public static IborRatchetRateCalculation of(IborIndex index, List<ValueSchedule> coefficients) {
    return IborRatchetRateCalculation.builder().index(index).coefficients(coefficients).build();
  }

  //-------------------------------------------------------------------------

  @ImmutablePreBuild
  private static void preBuild(Builder builder) {
    if (builder.index != null) {
      if (builder.dayCount == null) {
        builder.dayCount = builder.index.getDayCount();
      }
      if (builder.fixingDateOffset == null) {
        builder.fixingDateOffset = builder.index.getFixingDateOffset();
      }
    }
  }
  
  @ImmutableValidator
  private void validate() {
    ArgChecker.notNull(dayCount, "dayCount");
    ArgChecker.notNull(index, "index");
    ArgChecker.notNull(fixingDateOffset, "fixingDateOffset");
    ArgChecker.notNull(coefficients, "coefficients");
    ArgChecker.isTrue(coefficients.size() == 9, "coefficients must have a size of 9");
    for (int i = 0; i < 3; i++) {
      ArgChecker.isTrue(coefficients.get(3 * i).getInitialValue() == 0.0d,
          "initial coupon should not depend on previous value");
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public SwapLegType getType() {
    return SwapLegType.OTHER;
  }

  @Override
  public void collectCurrencies(ImmutableSet.Builder<Currency> builder) {
    builder.add(index.getCurrency());
  }

  @Override
  public void collectIndices(ImmutableSet.Builder<Index> builder) {
    builder.add(index);
  }

  @Override
  public ImmutableList<RateAccrualPeriod> createAccrualPeriods(
      Schedule accrualSchedule,
      Schedule paymentSchedule, // not used
      ReferenceData refData) {

    ArgChecker.notNull(accrualSchedule, "accrualSchedule must not be null");
    // resolve against reference data once
    DateAdjuster fixingDateAdjuster = fixingDateOffset.resolve(refData);
    Function<LocalDate, IborIndexObservation> iborObservationFn = index.resolve(refData);
    // build accrual periods
    DoubleArray mainPrevious = coefficients.get(0).resolveValues(accrualSchedule);
    ArgChecker.isTrue(mainPrevious.get(0) == 0.0, "main contribution of previous must be 0 for firt coupon");
    DoubleArray mainIbor = coefficients.get(1).resolveValues(accrualSchedule);
    DoubleArray mainFixed = coefficients.get(2).resolveValues(accrualSchedule);
    DoubleArray floorPrevious = coefficients.get(3).resolveValues(accrualSchedule);
    ArgChecker.isTrue(floorPrevious.get(0) == 0.0, "floor contribution of previous must be 0 for firt coupon");
    DoubleArray floorIbor = coefficients.get(4).resolveValues(accrualSchedule);
    DoubleArray floorFixed = coefficients.get(5).resolveValues(accrualSchedule);
    DoubleArray capPrevious = coefficients.get(6).resolveValues(accrualSchedule);
    ArgChecker.isTrue(capPrevious.get(0) == 0.0, "cap contribution of previous must be 0 for firt coupon");
    DoubleArray capIbor = coefficients.get(7).resolveValues(accrualSchedule);
    DoubleArray capFixed = coefficients.get(8).resolveValues(accrualSchedule);
    ImmutableList.Builder<RateAccrualPeriod> accrualPeriods = ImmutableList.builder();
    for (int i = 0; i < accrualSchedule.size(); i++) {
      SchedulePeriod periodAccrual = accrualSchedule.getPeriod(i);
      IborRatchetRateComputation rateComputation = createRateComputation(
          periodAccrual, fixingDateAdjuster, iborObservationFn,
          DoubleArray.of(mainPrevious.get(i), mainIbor.get(i), mainFixed.get(i)),
          DoubleArray.of(floorPrevious.get(i), floorIbor.get(i), floorFixed.get(i)),
          DoubleArray.of(capPrevious.get(i), capIbor.get(i), capFixed.get(i)));
      double yearFraction = periodAccrual.yearFraction(dayCount, accrualSchedule);
      accrualPeriods.add(new RateAccrualPeriod(periodAccrual, yearFraction, rateComputation,
          1.0, 0.0, NegativeRateMethod.ALLOW_NEGATIVE));
    }
    return accrualPeriods.build();
  }

  // creates the rate computation
  private IborRatchetRateComputation createRateComputation(
      SchedulePeriod period,
      DateAdjuster fixingDateAdjuster,
      Function<LocalDate, IborIndexObservation> iborObservationFn, 
      DoubleArray mainCoefficients, 
      DoubleArray floorCoefficients, 
      DoubleArray capCoefficients) {

    LocalDate fixingDate = fixingDateAdjuster.adjust(period.getStartDate());
    IborRatchetRateComputation computation = IborRatchetRateComputation
        .of(iborObservationFn.apply(fixingDate), mainCoefficients, floorCoefficients, capCoefficients);
    return computation;
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code IborRatchetRateCalculation}.
   * @return the meta-bean, not null
   */
  public static IborRatchetRateCalculation.Meta meta() {
    return IborRatchetRateCalculation.Meta.INSTANCE;
  }

  static {
    MetaBean.register(IborRatchetRateCalculation.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param dayCount  the value of the property, not null
   * @param index  the value of the property, not null
   * @param fixingDateOffset  the value of the property, not null
   * @param coefficients  the value of the property, not null
   * @return the instance
   */
  public static IborRatchetRateCalculation of(
      DayCount dayCount,
      IborIndex index,
      DaysAdjustment fixingDateOffset,
      List<ValueSchedule> coefficients) {
    return new IborRatchetRateCalculation(
      dayCount,
      index,
      fixingDateOffset,
      coefficients);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static IborRatchetRateCalculation.Builder builder() {
    return new IborRatchetRateCalculation.Builder();
  }

  private IborRatchetRateCalculation(
      DayCount dayCount,
      IborIndex index,
      DaysAdjustment fixingDateOffset,
      List<ValueSchedule> coefficients) {
    JodaBeanUtils.notNull(dayCount, "dayCount");
    JodaBeanUtils.notNull(index, "index");
    JodaBeanUtils.notNull(fixingDateOffset, "fixingDateOffset");
    JodaBeanUtils.notNull(coefficients, "coefficients");
    this.dayCount = dayCount;
    this.index = index;
    this.fixingDateOffset = fixingDateOffset;
    this.coefficients = ImmutableList.copyOf(coefficients);
    validate();
  }

  @Override
  public IborRatchetRateCalculation.Meta metaBean() {
    return IborRatchetRateCalculation.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day count convention.
   * <p>
   * This is used to convert dates to a numerical value.
   * <p>
   * When building, this will default to the day count of the index if not specified.
   * @return the value of the property, not null
   */
  @Override
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the Ibor index.
   * <p>
   * The rate to be paid is based on this index
   * It will be a well known market index such as 'GBP-LIBOR-3M'.
   * @return the value of the property, not null
   */
  public IborIndex getIndex() {
    return index;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the offset of the fixing date from each adjusted reset date.
   * <p>
   * The offset is applied to the base date specified by {@code fixingRelativeTo}.
   * The offset is typically a negative number of business days.
   * <p>
   * Note that in most cases, the reset frequency matches the accrual frequency
   * and thus there is only one fixing for the accrual period.
   * <p>
   * When building, this will default to the fixing offset of the index if not specified.
   * @return the value of the property, not null
   */
  public DaysAdjustment getFixingDateOffset() {
    return fixingDateOffset;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the ratchet coefficients: main previous, main Ibor, main fixed, floor previous, floor Ibor, floor fixed,
   * cap previous, cap Ibor, cap fixed.
   * @return the value of the property, not null
   */
  public List<ValueSchedule> getCoefficients() {
    return coefficients;
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
      IborRatchetRateCalculation other = (IborRatchetRateCalculation) obj;
      return JodaBeanUtils.equal(dayCount, other.dayCount) &&
          JodaBeanUtils.equal(index, other.index) &&
          JodaBeanUtils.equal(fixingDateOffset, other.fixingDateOffset) &&
          JodaBeanUtils.equal(coefficients, other.coefficients);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(dayCount);
    hash = hash * 31 + JodaBeanUtils.hashCode(index);
    hash = hash * 31 + JodaBeanUtils.hashCode(fixingDateOffset);
    hash = hash * 31 + JodaBeanUtils.hashCode(coefficients);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("IborRatchetRateCalculation{");
    buf.append("dayCount").append('=').append(dayCount).append(',').append(' ');
    buf.append("index").append('=').append(index).append(',').append(' ');
    buf.append("fixingDateOffset").append('=').append(fixingDateOffset).append(',').append(' ');
    buf.append("coefficients").append('=').append(JodaBeanUtils.toString(coefficients));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code IborRatchetRateCalculation}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", IborRatchetRateCalculation.class, DayCount.class);
    /**
     * The meta-property for the {@code index} property.
     */
    private final MetaProperty<IborIndex> index = DirectMetaProperty.ofImmutable(
        this, "index", IborRatchetRateCalculation.class, IborIndex.class);
    /**
     * The meta-property for the {@code fixingDateOffset} property.
     */
    private final MetaProperty<DaysAdjustment> fixingDateOffset = DirectMetaProperty.ofImmutable(
        this, "fixingDateOffset", IborRatchetRateCalculation.class, DaysAdjustment.class);
    /**
     * The meta-property for the {@code coefficients} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<ValueSchedule>> coefficients = DirectMetaProperty.ofImmutable(
        this, "coefficients", IborRatchetRateCalculation.class, (Class) List.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "dayCount",
        "index",
        "fixingDateOffset",
        "coefficients");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          return dayCount;
        case 100346066:  // index
          return index;
        case 873743726:  // fixingDateOffset
          return fixingDateOffset;
        case -1037599266:  // coefficients
          return coefficients;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public IborRatchetRateCalculation.Builder builder() {
      return new IborRatchetRateCalculation.Builder();
    }

    @Override
    public Class<? extends IborRatchetRateCalculation> beanType() {
      return IborRatchetRateCalculation.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    /**
     * The meta-property for the {@code index} property.
     * @return the meta-property, not null
     */
    public MetaProperty<IborIndex> index() {
      return index;
    }

    /**
     * The meta-property for the {@code fixingDateOffset} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DaysAdjustment> fixingDateOffset() {
      return fixingDateOffset;
    }

    /**
     * The meta-property for the {@code coefficients} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<ValueSchedule>> coefficients() {
      return coefficients;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          return ((IborRatchetRateCalculation) bean).getDayCount();
        case 100346066:  // index
          return ((IborRatchetRateCalculation) bean).getIndex();
        case 873743726:  // fixingDateOffset
          return ((IborRatchetRateCalculation) bean).getFixingDateOffset();
        case -1037599266:  // coefficients
          return ((IborRatchetRateCalculation) bean).getCoefficients();
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
   * The bean-builder for {@code IborRatchetRateCalculation}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<IborRatchetRateCalculation> {

    private DayCount dayCount;
    private IborIndex index;
    private DaysAdjustment fixingDateOffset;
    private List<ValueSchedule> coefficients = ImmutableList.of();

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(IborRatchetRateCalculation beanToCopy) {
      this.dayCount = beanToCopy.getDayCount();
      this.index = beanToCopy.getIndex();
      this.fixingDateOffset = beanToCopy.getFixingDateOffset();
      this.coefficients = ImmutableList.copyOf(beanToCopy.getCoefficients());
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          return dayCount;
        case 100346066:  // index
          return index;
        case 873743726:  // fixingDateOffset
          return fixingDateOffset;
        case -1037599266:  // coefficients
          return coefficients;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        case 100346066:  // index
          this.index = (IborIndex) newValue;
          break;
        case 873743726:  // fixingDateOffset
          this.fixingDateOffset = (DaysAdjustment) newValue;
          break;
        case -1037599266:  // coefficients
          this.coefficients = (List<ValueSchedule>) newValue;
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
    public IborRatchetRateCalculation build() {
      preBuild(this);
      return new IborRatchetRateCalculation(
          dayCount,
          index,
          fixingDateOffset,
          coefficients);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the day count convention.
     * <p>
     * This is used to convert dates to a numerical value.
     * <p>
     * When building, this will default to the day count of the index if not specified.
     * @param dayCount  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCount(DayCount dayCount) {
      JodaBeanUtils.notNull(dayCount, "dayCount");
      this.dayCount = dayCount;
      return this;
    }

    /**
     * Sets the Ibor index.
     * <p>
     * The rate to be paid is based on this index
     * It will be a well known market index such as 'GBP-LIBOR-3M'.
     * @param index  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder index(IborIndex index) {
      JodaBeanUtils.notNull(index, "index");
      this.index = index;
      return this;
    }

    /**
     * Sets the offset of the fixing date from each adjusted reset date.
     * <p>
     * The offset is applied to the base date specified by {@code fixingRelativeTo}.
     * The offset is typically a negative number of business days.
     * <p>
     * Note that in most cases, the reset frequency matches the accrual frequency
     * and thus there is only one fixing for the accrual period.
     * <p>
     * When building, this will default to the fixing offset of the index if not specified.
     * @param fixingDateOffset  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingDateOffset(DaysAdjustment fixingDateOffset) {
      JodaBeanUtils.notNull(fixingDateOffset, "fixingDateOffset");
      this.fixingDateOffset = fixingDateOffset;
      return this;
    }

    /**
     * Sets the ratchet coefficients: main previous, main Ibor, main fixed, floor previous, floor Ibor, floor fixed,
     * cap previous, cap Ibor, cap fixed.
     * @param coefficients  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder coefficients(List<ValueSchedule> coefficients) {
      JodaBeanUtils.notNull(coefficients, "coefficients");
      this.coefficients = coefficients;
      return this;
    }

    /**
     * Sets the {@code coefficients} property in the builder
     * from an array of objects.
     * @param coefficients  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder coefficients(ValueSchedule... coefficients) {
      return coefficients(ImmutableList.copyOf(coefficients));
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("IborRatchetRateCalculation.Builder{");
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("index").append('=').append(JodaBeanUtils.toString(index)).append(',').append(' ');
      buf.append("fixingDateOffset").append('=').append(JodaBeanUtils.toString(fixingDateOffset)).append(',').append(' ');
      buf.append("coefficients").append('=').append(JodaBeanUtils.toString(coefficients));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
