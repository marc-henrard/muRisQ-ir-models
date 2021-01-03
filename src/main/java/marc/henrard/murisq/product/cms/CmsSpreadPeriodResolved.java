/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.cms;

import java.io.Serializable;
import java.time.LocalDate;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.ResolvedProduct;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapIndex;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalDouble;

import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.impl.direct.DirectMetaProperty;

/**
 * Resolved version of {@link CmsSpreadPeriod}.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public final class CmsSpreadPeriodResolved
    implements ResolvedProduct, ImmutableBean, Serializable {
  
  /**
   * The notional amount, positive if receiving, negative if paying.
   * <p>
   * The notional amount applicable during the period.
   * The currency of the notional is specified by {@code currency}.
   */
  @PropertyDefinition
  private final double notional;
  /**
   * The start date of the payment period.
   * <p>
   * This is the first date in the period.
   * If the schedule adjusts for business days, then this is the adjusted date.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate startDate;
  /**
   * The end date of the payment period.
   * <p>
   * This is the last date in the period.
   * If the schedule adjusts for business days, then this is the adjusted date.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate endDate;
  /**
   * The year fraction that the accrual period represents.
   * <p>
   * The value is usually calculated using a {@link DayCount} which may be different to that of the index.
   * Typically the value will be close to 1 for one year and close to 0.5 for six months.
   * The fraction may be greater than 1, but not less than 0.
   */
  @PropertyDefinition(validate = "ArgChecker.notNegative")
  private final double yearFraction;
  /**
   * The date that payment occurs.
   * <p>
   * If the schedule adjusts for business days, then this is the adjusted date.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate paymentDate;
  /**
   * The date of the index fixing.
   * <p>
   * This is an adjusted date with any business day applied.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate fixingDate;
  /**
   * The optional caplet strike.
   * <p>
   * This defines the strike value of a caplet.
   * <p>
   * If the period is not a caplet, this field will be absent.
   */
  @PropertyDefinition(get = "optional")
  private final Double caplet;
  /**
   * The optional floorlet strike.
   * <p>
   * This defines the strike value of a floorlet.
   * <p>
   * If the period is not a floorlet, this field will be absent.
   */
  @PropertyDefinition(get = "optional")
  private final Double floorlet;
  /**
   * The weight of the first swap rate.
   */
  @PropertyDefinition
  private final double weight1;
  /**
   * The first swap index.
   * <p>
   * The swap rate to be paid is the observed value of this index.
   */
  @PropertyDefinition(validate = "notNull")
  private final SwapIndex index1;
  /**
   * The underlying swap 1.
   * <p>
   * The interest rate swap for which the swap rate 1 is referred. 
   * The swap must have a notional of 1 and a fixed rate of 1.
   */
  @PropertyDefinition(validate = "notNull")
  private final ResolvedSwap underlyingSwap1;
  /**
   * The weight of the second swap rate.
   */
  @PropertyDefinition
  private final double weight2;
  /**
   * The first swap index.
   * <p>
   * The swap rate to be paid is the observed value of this index.
   */
  @PropertyDefinition(validate = "notNull")
  private final SwapIndex index2;
  /**
   * The underlying swap 2.
   * <p>
   * The interest rate swap for which the swap rate 2 is referred. 
   * The swap must have a notional of 1 and a fixed rate of 1.
   */
  @PropertyDefinition(validate = "notNull")
  private final ResolvedSwap underlyingSwap2;
  
  /**
   * Gets the period currency.
   * 
   * @return the currency
   */
  public Currency getCurrency() {
    return index1.getTemplate().getConvention().getFixedLeg().getCurrency();
  }
  
  /**
   * Returns the period payoff for given values of the swap rates.
   * 
   * @param swapRate1  the first swap rate
   * @param swapRate2  the second swap rate
   * @return the payoff
   */
  public double payoff(double swapRate1, double swapRate2) {
    double cpn = weight1 * swapRate1 - weight2 * swapRate2;
    if (caplet != null) {
      return Math.max(cpn - caplet, 0.0d) * notional * yearFraction;
    }
    if (floorlet != null) {
      return Math.max(floorlet - cpn, 0.0d) * notional * yearFraction;
    }
    return cpn * notional * yearFraction;
  }
  
  /**
   * Returns the period payoff for given values of the swap rates. Array version.
   * 
   * @param swapRate1  the first swap rate array
   * @param swapRate2  the second swap rate array
   * @return the payoff
   */
  public double[] payoff(double[] swapRate1, double[] swapRate2) {
    int nbRates = swapRate1.length;
    ArgChecker.isTrue(nbRates == swapRate2.length,
        "both rate arrays must have the same length");
    double[] cpn = new double[nbRates];
    for (int i = 0; i < nbRates; i++) {
      cpn[i] = weight1 * swapRate1[i] - weight2 * swapRate2[i];
    }
    if (caplet != null) {
      double[] cap = new double[nbRates];
      for (int i = 0; i < nbRates; i++) {
        cap[i] = notional * yearFraction * Math.max(cpn[i] - caplet, 0.0d);
      }
      return cap;
    }
    if (floorlet != null) {
      double[] floor = new double[nbRates];
      for (int i = 0; i < nbRates; i++) {
        floor[i] = notional * yearFraction * Math.max(floorlet - cpn[i], 0.0d);
      }
      return floor;
    }
    for (int i = 0; i < nbRates; i++) {
      cpn[i] *= notional * yearFraction;
    }
    return cpn;
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code CmsSpreadPeriodResolved}.
   * @return the meta-bean, not null
   */
  public static CmsSpreadPeriodResolved.Meta meta() {
    return CmsSpreadPeriodResolved.Meta.INSTANCE;
  }

  static {
    MetaBean.register(CmsSpreadPeriodResolved.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static CmsSpreadPeriodResolved.Builder builder() {
    return new CmsSpreadPeriodResolved.Builder();
  }

  private CmsSpreadPeriodResolved(
      double notional,
      LocalDate startDate,
      LocalDate endDate,
      double yearFraction,
      LocalDate paymentDate,
      LocalDate fixingDate,
      Double caplet,
      Double floorlet,
      double weight1,
      SwapIndex index1,
      ResolvedSwap underlyingSwap1,
      double weight2,
      SwapIndex index2,
      ResolvedSwap underlyingSwap2) {
    JodaBeanUtils.notNull(startDate, "startDate");
    JodaBeanUtils.notNull(endDate, "endDate");
    ArgChecker.notNegative(yearFraction, "yearFraction");
    JodaBeanUtils.notNull(paymentDate, "paymentDate");
    JodaBeanUtils.notNull(fixingDate, "fixingDate");
    JodaBeanUtils.notNull(index1, "index1");
    JodaBeanUtils.notNull(underlyingSwap1, "underlyingSwap1");
    JodaBeanUtils.notNull(index2, "index2");
    JodaBeanUtils.notNull(underlyingSwap2, "underlyingSwap2");
    this.notional = notional;
    this.startDate = startDate;
    this.endDate = endDate;
    this.yearFraction = yearFraction;
    this.paymentDate = paymentDate;
    this.fixingDate = fixingDate;
    this.caplet = caplet;
    this.floorlet = floorlet;
    this.weight1 = weight1;
    this.index1 = index1;
    this.underlyingSwap1 = underlyingSwap1;
    this.weight2 = weight2;
    this.index2 = index2;
    this.underlyingSwap2 = underlyingSwap2;
  }

  @Override
  public CmsSpreadPeriodResolved.Meta metaBean() {
    return CmsSpreadPeriodResolved.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the notional amount, positive if receiving, negative if paying.
   * <p>
   * The notional amount applicable during the period.
   * The currency of the notional is specified by {@code currency}.
   * @return the value of the property
   */
  public double getNotional() {
    return notional;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the start date of the payment period.
   * <p>
   * This is the first date in the period.
   * If the schedule adjusts for business days, then this is the adjusted date.
   * @return the value of the property, not null
   */
  public LocalDate getStartDate() {
    return startDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the end date of the payment period.
   * <p>
   * This is the last date in the period.
   * If the schedule adjusts for business days, then this is the adjusted date.
   * @return the value of the property, not null
   */
  public LocalDate getEndDate() {
    return endDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the year fraction that the accrual period represents.
   * <p>
   * The value is usually calculated using a {@link DayCount} which may be different to that of the index.
   * Typically the value will be close to 1 for one year and close to 0.5 for six months.
   * The fraction may be greater than 1, but not less than 0.
   * @return the value of the property
   */
  public double getYearFraction() {
    return yearFraction;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the date that payment occurs.
   * <p>
   * If the schedule adjusts for business days, then this is the adjusted date.
   * @return the value of the property, not null
   */
  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the date of the index fixing.
   * <p>
   * This is an adjusted date with any business day applied.
   * @return the value of the property, not null
   */
  public LocalDate getFixingDate() {
    return fixingDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the optional caplet strike.
   * <p>
   * This defines the strike value of a caplet.
   * <p>
   * If the period is not a caplet, this field will be absent.
   * @return the optional value of the property, not null
   */
  public OptionalDouble getCaplet() {
    return caplet != null ? OptionalDouble.of(caplet) : OptionalDouble.empty();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the optional floorlet strike.
   * <p>
   * This defines the strike value of a floorlet.
   * <p>
   * If the period is not a floorlet, this field will be absent.
   * @return the optional value of the property, not null
   */
  public OptionalDouble getFloorlet() {
    return floorlet != null ? OptionalDouble.of(floorlet) : OptionalDouble.empty();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the weight of the first swap rate.
   * @return the value of the property
   */
  public double getWeight1() {
    return weight1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the first swap index.
   * <p>
   * The swap rate to be paid is the observed value of this index.
   * @return the value of the property, not null
   */
  public SwapIndex getIndex1() {
    return index1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the underlying swap 1.
   * <p>
   * The interest rate swap for which the swap rate 1 is referred.
   * The swap must have a notional of 1 and a fixed rate of 1.
   * @return the value of the property, not null
   */
  public ResolvedSwap getUnderlyingSwap1() {
    return underlyingSwap1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the weight of the second swap rate.
   * @return the value of the property
   */
  public double getWeight2() {
    return weight2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the first swap index.
   * <p>
   * The swap rate to be paid is the observed value of this index.
   * @return the value of the property, not null
   */
  public SwapIndex getIndex2() {
    return index2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the underlying swap 2.
   * <p>
   * The interest rate swap for which the swap rate 2 is referred.
   * The swap must have a notional of 1 and a fixed rate of 1.
   * @return the value of the property, not null
   */
  public ResolvedSwap getUnderlyingSwap2() {
    return underlyingSwap2;
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
      CmsSpreadPeriodResolved other = (CmsSpreadPeriodResolved) obj;
      return JodaBeanUtils.equal(notional, other.notional) &&
          JodaBeanUtils.equal(startDate, other.startDate) &&
          JodaBeanUtils.equal(endDate, other.endDate) &&
          JodaBeanUtils.equal(yearFraction, other.yearFraction) &&
          JodaBeanUtils.equal(paymentDate, other.paymentDate) &&
          JodaBeanUtils.equal(fixingDate, other.fixingDate) &&
          JodaBeanUtils.equal(caplet, other.caplet) &&
          JodaBeanUtils.equal(floorlet, other.floorlet) &&
          JodaBeanUtils.equal(weight1, other.weight1) &&
          JodaBeanUtils.equal(index1, other.index1) &&
          JodaBeanUtils.equal(underlyingSwap1, other.underlyingSwap1) &&
          JodaBeanUtils.equal(weight2, other.weight2) &&
          JodaBeanUtils.equal(index2, other.index2) &&
          JodaBeanUtils.equal(underlyingSwap2, other.underlyingSwap2);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(notional);
    hash = hash * 31 + JodaBeanUtils.hashCode(startDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(endDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(yearFraction);
    hash = hash * 31 + JodaBeanUtils.hashCode(paymentDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(fixingDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(caplet);
    hash = hash * 31 + JodaBeanUtils.hashCode(floorlet);
    hash = hash * 31 + JodaBeanUtils.hashCode(weight1);
    hash = hash * 31 + JodaBeanUtils.hashCode(index1);
    hash = hash * 31 + JodaBeanUtils.hashCode(underlyingSwap1);
    hash = hash * 31 + JodaBeanUtils.hashCode(weight2);
    hash = hash * 31 + JodaBeanUtils.hashCode(index2);
    hash = hash * 31 + JodaBeanUtils.hashCode(underlyingSwap2);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(480);
    buf.append("CmsSpreadPeriodResolved{");
    buf.append("notional").append('=').append(JodaBeanUtils.toString(notional)).append(',').append(' ');
    buf.append("startDate").append('=').append(JodaBeanUtils.toString(startDate)).append(',').append(' ');
    buf.append("endDate").append('=').append(JodaBeanUtils.toString(endDate)).append(',').append(' ');
    buf.append("yearFraction").append('=').append(JodaBeanUtils.toString(yearFraction)).append(',').append(' ');
    buf.append("paymentDate").append('=').append(JodaBeanUtils.toString(paymentDate)).append(',').append(' ');
    buf.append("fixingDate").append('=').append(JodaBeanUtils.toString(fixingDate)).append(',').append(' ');
    buf.append("caplet").append('=').append(JodaBeanUtils.toString(caplet)).append(',').append(' ');
    buf.append("floorlet").append('=').append(JodaBeanUtils.toString(floorlet)).append(',').append(' ');
    buf.append("weight1").append('=').append(JodaBeanUtils.toString(weight1)).append(',').append(' ');
    buf.append("index1").append('=').append(JodaBeanUtils.toString(index1)).append(',').append(' ');
    buf.append("underlyingSwap1").append('=').append(JodaBeanUtils.toString(underlyingSwap1)).append(',').append(' ');
    buf.append("weight2").append('=').append(JodaBeanUtils.toString(weight2)).append(',').append(' ');
    buf.append("index2").append('=').append(JodaBeanUtils.toString(index2)).append(',').append(' ');
    buf.append("underlyingSwap2").append('=').append(JodaBeanUtils.toString(underlyingSwap2));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code CmsSpreadPeriodResolved}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code notional} property.
     */
    private final MetaProperty<Double> notional = DirectMetaProperty.ofImmutable(
        this, "notional", CmsSpreadPeriodResolved.class, Double.TYPE);
    /**
     * The meta-property for the {@code startDate} property.
     */
    private final MetaProperty<LocalDate> startDate = DirectMetaProperty.ofImmutable(
        this, "startDate", CmsSpreadPeriodResolved.class, LocalDate.class);
    /**
     * The meta-property for the {@code endDate} property.
     */
    private final MetaProperty<LocalDate> endDate = DirectMetaProperty.ofImmutable(
        this, "endDate", CmsSpreadPeriodResolved.class, LocalDate.class);
    /**
     * The meta-property for the {@code yearFraction} property.
     */
    private final MetaProperty<Double> yearFraction = DirectMetaProperty.ofImmutable(
        this, "yearFraction", CmsSpreadPeriodResolved.class, Double.TYPE);
    /**
     * The meta-property for the {@code paymentDate} property.
     */
    private final MetaProperty<LocalDate> paymentDate = DirectMetaProperty.ofImmutable(
        this, "paymentDate", CmsSpreadPeriodResolved.class, LocalDate.class);
    /**
     * The meta-property for the {@code fixingDate} property.
     */
    private final MetaProperty<LocalDate> fixingDate = DirectMetaProperty.ofImmutable(
        this, "fixingDate", CmsSpreadPeriodResolved.class, LocalDate.class);
    /**
     * The meta-property for the {@code caplet} property.
     */
    private final MetaProperty<Double> caplet = DirectMetaProperty.ofImmutable(
        this, "caplet", CmsSpreadPeriodResolved.class, Double.class);
    /**
     * The meta-property for the {@code floorlet} property.
     */
    private final MetaProperty<Double> floorlet = DirectMetaProperty.ofImmutable(
        this, "floorlet", CmsSpreadPeriodResolved.class, Double.class);
    /**
     * The meta-property for the {@code weight1} property.
     */
    private final MetaProperty<Double> weight1 = DirectMetaProperty.ofImmutable(
        this, "weight1", CmsSpreadPeriodResolved.class, Double.TYPE);
    /**
     * The meta-property for the {@code index1} property.
     */
    private final MetaProperty<SwapIndex> index1 = DirectMetaProperty.ofImmutable(
        this, "index1", CmsSpreadPeriodResolved.class, SwapIndex.class);
    /**
     * The meta-property for the {@code underlyingSwap1} property.
     */
    private final MetaProperty<ResolvedSwap> underlyingSwap1 = DirectMetaProperty.ofImmutable(
        this, "underlyingSwap1", CmsSpreadPeriodResolved.class, ResolvedSwap.class);
    /**
     * The meta-property for the {@code weight2} property.
     */
    private final MetaProperty<Double> weight2 = DirectMetaProperty.ofImmutable(
        this, "weight2", CmsSpreadPeriodResolved.class, Double.TYPE);
    /**
     * The meta-property for the {@code index2} property.
     */
    private final MetaProperty<SwapIndex> index2 = DirectMetaProperty.ofImmutable(
        this, "index2", CmsSpreadPeriodResolved.class, SwapIndex.class);
    /**
     * The meta-property for the {@code underlyingSwap2} property.
     */
    private final MetaProperty<ResolvedSwap> underlyingSwap2 = DirectMetaProperty.ofImmutable(
        this, "underlyingSwap2", CmsSpreadPeriodResolved.class, ResolvedSwap.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "notional",
        "startDate",
        "endDate",
        "yearFraction",
        "paymentDate",
        "fixingDate",
        "caplet",
        "floorlet",
        "weight1",
        "index1",
        "underlyingSwap1",
        "weight2",
        "index2",
        "underlyingSwap2");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1585636160:  // notional
          return notional;
        case -2129778896:  // startDate
          return startDate;
        case -1607727319:  // endDate
          return endDate;
        case -1731780257:  // yearFraction
          return yearFraction;
        case -1540873516:  // paymentDate
          return paymentDate;
        case 1255202043:  // fixingDate
          return fixingDate;
        case -1367656183:  // caplet
          return caplet;
        case 2022994575:  // floorlet
          return floorlet;
        case 1230441657:  // weight1
          return weight1;
        case -1184239201:  // index1
          return index1;
        case -824575071:  // underlyingSwap1
          return underlyingSwap1;
        case 1230441658:  // weight2
          return weight2;
        case -1184239200:  // index2
          return index2;
        case -824575070:  // underlyingSwap2
          return underlyingSwap2;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public CmsSpreadPeriodResolved.Builder builder() {
      return new CmsSpreadPeriodResolved.Builder();
    }

    @Override
    public Class<? extends CmsSpreadPeriodResolved> beanType() {
      return CmsSpreadPeriodResolved.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code notional} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> notional() {
      return notional;
    }

    /**
     * The meta-property for the {@code startDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> startDate() {
      return startDate;
    }

    /**
     * The meta-property for the {@code endDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> endDate() {
      return endDate;
    }

    /**
     * The meta-property for the {@code yearFraction} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> yearFraction() {
      return yearFraction;
    }

    /**
     * The meta-property for the {@code paymentDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> paymentDate() {
      return paymentDate;
    }

    /**
     * The meta-property for the {@code fixingDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> fixingDate() {
      return fixingDate;
    }

    /**
     * The meta-property for the {@code caplet} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> caplet() {
      return caplet;
    }

    /**
     * The meta-property for the {@code floorlet} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> floorlet() {
      return floorlet;
    }

    /**
     * The meta-property for the {@code weight1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> weight1() {
      return weight1;
    }

    /**
     * The meta-property for the {@code index1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<SwapIndex> index1() {
      return index1;
    }

    /**
     * The meta-property for the {@code underlyingSwap1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ResolvedSwap> underlyingSwap1() {
      return underlyingSwap1;
    }

    /**
     * The meta-property for the {@code weight2} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> weight2() {
      return weight2;
    }

    /**
     * The meta-property for the {@code index2} property.
     * @return the meta-property, not null
     */
    public MetaProperty<SwapIndex> index2() {
      return index2;
    }

    /**
     * The meta-property for the {@code underlyingSwap2} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ResolvedSwap> underlyingSwap2() {
      return underlyingSwap2;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1585636160:  // notional
          return ((CmsSpreadPeriodResolved) bean).getNotional();
        case -2129778896:  // startDate
          return ((CmsSpreadPeriodResolved) bean).getStartDate();
        case -1607727319:  // endDate
          return ((CmsSpreadPeriodResolved) bean).getEndDate();
        case -1731780257:  // yearFraction
          return ((CmsSpreadPeriodResolved) bean).getYearFraction();
        case -1540873516:  // paymentDate
          return ((CmsSpreadPeriodResolved) bean).getPaymentDate();
        case 1255202043:  // fixingDate
          return ((CmsSpreadPeriodResolved) bean).getFixingDate();
        case -1367656183:  // caplet
          return ((CmsSpreadPeriodResolved) bean).caplet;
        case 2022994575:  // floorlet
          return ((CmsSpreadPeriodResolved) bean).floorlet;
        case 1230441657:  // weight1
          return ((CmsSpreadPeriodResolved) bean).getWeight1();
        case -1184239201:  // index1
          return ((CmsSpreadPeriodResolved) bean).getIndex1();
        case -824575071:  // underlyingSwap1
          return ((CmsSpreadPeriodResolved) bean).getUnderlyingSwap1();
        case 1230441658:  // weight2
          return ((CmsSpreadPeriodResolved) bean).getWeight2();
        case -1184239200:  // index2
          return ((CmsSpreadPeriodResolved) bean).getIndex2();
        case -824575070:  // underlyingSwap2
          return ((CmsSpreadPeriodResolved) bean).getUnderlyingSwap2();
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
   * The bean-builder for {@code CmsSpreadPeriodResolved}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<CmsSpreadPeriodResolved> {

    private double notional;
    private LocalDate startDate;
    private LocalDate endDate;
    private double yearFraction;
    private LocalDate paymentDate;
    private LocalDate fixingDate;
    private Double caplet;
    private Double floorlet;
    private double weight1;
    private SwapIndex index1;
    private ResolvedSwap underlyingSwap1;
    private double weight2;
    private SwapIndex index2;
    private ResolvedSwap underlyingSwap2;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(CmsSpreadPeriodResolved beanToCopy) {
      this.notional = beanToCopy.getNotional();
      this.startDate = beanToCopy.getStartDate();
      this.endDate = beanToCopy.getEndDate();
      this.yearFraction = beanToCopy.getYearFraction();
      this.paymentDate = beanToCopy.getPaymentDate();
      this.fixingDate = beanToCopy.getFixingDate();
      this.caplet = beanToCopy.caplet;
      this.floorlet = beanToCopy.floorlet;
      this.weight1 = beanToCopy.getWeight1();
      this.index1 = beanToCopy.getIndex1();
      this.underlyingSwap1 = beanToCopy.getUnderlyingSwap1();
      this.weight2 = beanToCopy.getWeight2();
      this.index2 = beanToCopy.getIndex2();
      this.underlyingSwap2 = beanToCopy.getUnderlyingSwap2();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1585636160:  // notional
          return notional;
        case -2129778896:  // startDate
          return startDate;
        case -1607727319:  // endDate
          return endDate;
        case -1731780257:  // yearFraction
          return yearFraction;
        case -1540873516:  // paymentDate
          return paymentDate;
        case 1255202043:  // fixingDate
          return fixingDate;
        case -1367656183:  // caplet
          return caplet;
        case 2022994575:  // floorlet
          return floorlet;
        case 1230441657:  // weight1
          return weight1;
        case -1184239201:  // index1
          return index1;
        case -824575071:  // underlyingSwap1
          return underlyingSwap1;
        case 1230441658:  // weight2
          return weight2;
        case -1184239200:  // index2
          return index2;
        case -824575070:  // underlyingSwap2
          return underlyingSwap2;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1585636160:  // notional
          this.notional = (Double) newValue;
          break;
        case -2129778896:  // startDate
          this.startDate = (LocalDate) newValue;
          break;
        case -1607727319:  // endDate
          this.endDate = (LocalDate) newValue;
          break;
        case -1731780257:  // yearFraction
          this.yearFraction = (Double) newValue;
          break;
        case -1540873516:  // paymentDate
          this.paymentDate = (LocalDate) newValue;
          break;
        case 1255202043:  // fixingDate
          this.fixingDate = (LocalDate) newValue;
          break;
        case -1367656183:  // caplet
          this.caplet = (Double) newValue;
          break;
        case 2022994575:  // floorlet
          this.floorlet = (Double) newValue;
          break;
        case 1230441657:  // weight1
          this.weight1 = (Double) newValue;
          break;
        case -1184239201:  // index1
          this.index1 = (SwapIndex) newValue;
          break;
        case -824575071:  // underlyingSwap1
          this.underlyingSwap1 = (ResolvedSwap) newValue;
          break;
        case 1230441658:  // weight2
          this.weight2 = (Double) newValue;
          break;
        case -1184239200:  // index2
          this.index2 = (SwapIndex) newValue;
          break;
        case -824575070:  // underlyingSwap2
          this.underlyingSwap2 = (ResolvedSwap) newValue;
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
    public CmsSpreadPeriodResolved build() {
      return new CmsSpreadPeriodResolved(
          notional,
          startDate,
          endDate,
          yearFraction,
          paymentDate,
          fixingDate,
          caplet,
          floorlet,
          weight1,
          index1,
          underlyingSwap1,
          weight2,
          index2,
          underlyingSwap2);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the notional amount, positive if receiving, negative if paying.
     * <p>
     * The notional amount applicable during the period.
     * The currency of the notional is specified by {@code currency}.
     * @param notional  the new value
     * @return this, for chaining, not null
     */
    public Builder notional(double notional) {
      this.notional = notional;
      return this;
    }

    /**
     * Sets the start date of the payment period.
     * <p>
     * This is the first date in the period.
     * If the schedule adjusts for business days, then this is the adjusted date.
     * @param startDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder startDate(LocalDate startDate) {
      JodaBeanUtils.notNull(startDate, "startDate");
      this.startDate = startDate;
      return this;
    }

    /**
     * Sets the end date of the payment period.
     * <p>
     * This is the last date in the period.
     * If the schedule adjusts for business days, then this is the adjusted date.
     * @param endDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder endDate(LocalDate endDate) {
      JodaBeanUtils.notNull(endDate, "endDate");
      this.endDate = endDate;
      return this;
    }

    /**
     * Sets the year fraction that the accrual period represents.
     * <p>
     * The value is usually calculated using a {@link DayCount} which may be different to that of the index.
     * Typically the value will be close to 1 for one year and close to 0.5 for six months.
     * The fraction may be greater than 1, but not less than 0.
     * @param yearFraction  the new value
     * @return this, for chaining, not null
     */
    public Builder yearFraction(double yearFraction) {
      ArgChecker.notNegative(yearFraction, "yearFraction");
      this.yearFraction = yearFraction;
      return this;
    }

    /**
     * Sets the date that payment occurs.
     * <p>
     * If the schedule adjusts for business days, then this is the adjusted date.
     * @param paymentDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder paymentDate(LocalDate paymentDate) {
      JodaBeanUtils.notNull(paymentDate, "paymentDate");
      this.paymentDate = paymentDate;
      return this;
    }

    /**
     * Sets the date of the index fixing.
     * <p>
     * This is an adjusted date with any business day applied.
     * @param fixingDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixingDate(LocalDate fixingDate) {
      JodaBeanUtils.notNull(fixingDate, "fixingDate");
      this.fixingDate = fixingDate;
      return this;
    }

    /**
     * Sets the optional caplet strike.
     * <p>
     * This defines the strike value of a caplet.
     * <p>
     * If the period is not a caplet, this field will be absent.
     * @param caplet  the new value
     * @return this, for chaining, not null
     */
    public Builder caplet(Double caplet) {
      this.caplet = caplet;
      return this;
    }

    /**
     * Sets the optional floorlet strike.
     * <p>
     * This defines the strike value of a floorlet.
     * <p>
     * If the period is not a floorlet, this field will be absent.
     * @param floorlet  the new value
     * @return this, for chaining, not null
     */
    public Builder floorlet(Double floorlet) {
      this.floorlet = floorlet;
      return this;
    }

    /**
     * Sets the weight of the first swap rate.
     * @param weight1  the new value
     * @return this, for chaining, not null
     */
    public Builder weight1(double weight1) {
      this.weight1 = weight1;
      return this;
    }

    /**
     * Sets the first swap index.
     * <p>
     * The swap rate to be paid is the observed value of this index.
     * @param index1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder index1(SwapIndex index1) {
      JodaBeanUtils.notNull(index1, "index1");
      this.index1 = index1;
      return this;
    }

    /**
     * Sets the underlying swap 1.
     * <p>
     * The interest rate swap for which the swap rate 1 is referred.
     * The swap must have a notional of 1 and a fixed rate of 1.
     * @param underlyingSwap1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder underlyingSwap1(ResolvedSwap underlyingSwap1) {
      JodaBeanUtils.notNull(underlyingSwap1, "underlyingSwap1");
      this.underlyingSwap1 = underlyingSwap1;
      return this;
    }

    /**
     * Sets the weight of the second swap rate.
     * @param weight2  the new value
     * @return this, for chaining, not null
     */
    public Builder weight2(double weight2) {
      this.weight2 = weight2;
      return this;
    }

    /**
     * Sets the first swap index.
     * <p>
     * The swap rate to be paid is the observed value of this index.
     * @param index2  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder index2(SwapIndex index2) {
      JodaBeanUtils.notNull(index2, "index2");
      this.index2 = index2;
      return this;
    }

    /**
     * Sets the underlying swap 2.
     * <p>
     * The interest rate swap for which the swap rate 2 is referred.
     * The swap must have a notional of 1 and a fixed rate of 1.
     * @param underlyingSwap2  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder underlyingSwap2(ResolvedSwap underlyingSwap2) {
      JodaBeanUtils.notNull(underlyingSwap2, "underlyingSwap2");
      this.underlyingSwap2 = underlyingSwap2;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(480);
      buf.append("CmsSpreadPeriodResolved.Builder{");
      buf.append("notional").append('=').append(JodaBeanUtils.toString(notional)).append(',').append(' ');
      buf.append("startDate").append('=').append(JodaBeanUtils.toString(startDate)).append(',').append(' ');
      buf.append("endDate").append('=').append(JodaBeanUtils.toString(endDate)).append(',').append(' ');
      buf.append("yearFraction").append('=').append(JodaBeanUtils.toString(yearFraction)).append(',').append(' ');
      buf.append("paymentDate").append('=').append(JodaBeanUtils.toString(paymentDate)).append(',').append(' ');
      buf.append("fixingDate").append('=').append(JodaBeanUtils.toString(fixingDate)).append(',').append(' ');
      buf.append("caplet").append('=').append(JodaBeanUtils.toString(caplet)).append(',').append(' ');
      buf.append("floorlet").append('=').append(JodaBeanUtils.toString(floorlet)).append(',').append(' ');
      buf.append("weight1").append('=').append(JodaBeanUtils.toString(weight1)).append(',').append(' ');
      buf.append("index1").append('=').append(JodaBeanUtils.toString(index1)).append(',').append(' ');
      buf.append("underlyingSwap1").append('=').append(JodaBeanUtils.toString(underlyingSwap1)).append(',').append(' ');
      buf.append("weight2").append('=').append(JodaBeanUtils.toString(weight2)).append(',').append(' ');
      buf.append("index2").append('=').append(JodaBeanUtils.toString(index2)).append(',').append(' ');
      buf.append("underlyingSwap2").append('=').append(JodaBeanUtils.toString(underlyingSwap2));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
