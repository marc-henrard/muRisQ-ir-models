/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.decomposition;

import java.io.Serializable;

import org.joda.beans.ImmutableBean;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.swap.NotionalExchange;

import org.joda.beans.Bean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.MetaBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

/**
 * Class describing the dates and amount required to price interest rate derivatives in the multi-curve framework. 
 * The data type is used in particular for Monte Carlo pricing.
 * <p>
 * One 'equivalent' is generated for each decision time, they are not path dependent but a simplified description
 * of the decision or option to take.
 */
@BeanDefinition(factoryName = "of")
public final class MulticurveEquivalent
    implements ImmutableBean, Serializable {

  /** The date at which an exercise or fixing takes place. */
  @PropertyDefinition
  private final ZonedDateTime decisionTime;
  /** The dates and amounts impacting the value through discount factors at each decision date. */
  @PropertyDefinition(validate = "notNull")
  private final List<NotionalExchange> discountFactorPayments;
  /** The rates impacting the value through ibor fixing at the decision date. */
  @PropertyDefinition(validate = "notNull")
  private final List<IborRateComputation> iborComputations;
  /** The reference amounts and payment date for each ibor observation. */
  @PropertyDefinition(validate = "notNull")
  private final List<NotionalExchange> iborPayments;
  /** The date impacting the value through overnight compounded periods at the decision date. */
  @PropertyDefinition(validate = "notNull")
  private final List<OvernightCompoundedRateComputation> onComputations;
  /** The reference amounts and payment date for each overnight observation. */
  @PropertyDefinition(validate = "notNull")
  private final List<NotionalExchange> onPayments;

  //-------------------------------------------------------------------------
  /**
   * Creates an empty schedule with all the lists empty and the decision date the given date.
   * 
   * @return the schedule
   */
  public static MulticurveEquivalent empty() {
    return MulticurveEquivalent.builder()
        .discountFactorPayments(new ArrayList<NotionalExchange>())
        .iborComputations(new ArrayList<IborRateComputation>())
        .iborPayments(new ArrayList<NotionalExchange>())
        .onComputations(new ArrayList<OvernightCompoundedRateComputation>())
        .onPayments(new ArrayList<NotionalExchange>())
        .build();
  }

  //-------------------------------------------------------------------------
  /**
   * Combines this schedule with another instance.
   * <p>
   * This returns a new schedule instance with the specified payments added.
   * This instance is immutable and unaffected by this method.
   * The result may contain duplicate payments.
   * 
   * @param other  the other schedule
   * @return an instance based on this one, with the other instance added
   */
  public MulticurveEquivalent combinedWith(MulticurveEquivalent other) {
    ArgChecker.isTrue(this.decisionTime == null ||
        other.decisionTime == null ||
        this.decisionTime.equals(other.decisionTime), "decision dates should be equal");
    List<NotionalExchange> combinedDiscountFactorPayments = new ArrayList<>(discountFactorPayments);
    combinedDiscountFactorPayments.addAll(other.discountFactorPayments);
    List<IborRateComputation> combinedIborObservations = new ArrayList<>(iborComputations);
    combinedIborObservations.addAll(other.iborComputations);
    List<NotionalExchange> combinedIborPayments = new ArrayList<>(iborPayments);
    combinedIborPayments.addAll(other.iborPayments);
    List<OvernightCompoundedRateComputation> combinedOnObservations = new ArrayList<>(onComputations);
    combinedOnObservations.addAll(other.onComputations);
    List<NotionalExchange> combinedOnPayments = new ArrayList<>(onPayments);
    combinedOnPayments.addAll(other.onPayments);
    return MulticurveEquivalent.builder()
        .decisionTime((decisionTime != null) ? decisionTime : other.decisionTime)
        .discountFactorPayments(combinedDiscountFactorPayments)
        .iborComputations(combinedIborObservations)
        .iborPayments(combinedIborPayments)
        .onComputations(combinedOnObservations)
        .onPayments(combinedOnPayments)
        .build();
  }
  
  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code MulticurveEquivalent}.
   * @return the meta-bean, not null
   */
  public static MulticurveEquivalent.Meta meta() {
    return MulticurveEquivalent.Meta.INSTANCE;
  }

  static {
    MetaBean.register(MulticurveEquivalent.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param decisionTime  the value of the property
   * @param discountFactorPayments  the value of the property, not null
   * @param iborComputations  the value of the property, not null
   * @param iborPayments  the value of the property, not null
   * @param onComputations  the value of the property, not null
   * @param onPayments  the value of the property, not null
   * @return the instance
   */
  public static MulticurveEquivalent of(
      ZonedDateTime decisionTime,
      List<NotionalExchange> discountFactorPayments,
      List<IborRateComputation> iborComputations,
      List<NotionalExchange> iborPayments,
      List<OvernightCompoundedRateComputation> onComputations,
      List<NotionalExchange> onPayments) {
    return new MulticurveEquivalent(
      decisionTime,
      discountFactorPayments,
      iborComputations,
      iborPayments,
      onComputations,
      onPayments);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static MulticurveEquivalent.Builder builder() {
    return new MulticurveEquivalent.Builder();
  }

  private MulticurveEquivalent(
      ZonedDateTime decisionTime,
      List<NotionalExchange> discountFactorPayments,
      List<IborRateComputation> iborComputations,
      List<NotionalExchange> iborPayments,
      List<OvernightCompoundedRateComputation> onComputations,
      List<NotionalExchange> onPayments) {
    JodaBeanUtils.notNull(discountFactorPayments, "discountFactorPayments");
    JodaBeanUtils.notNull(iborComputations, "iborComputations");
    JodaBeanUtils.notNull(iborPayments, "iborPayments");
    JodaBeanUtils.notNull(onComputations, "onComputations");
    JodaBeanUtils.notNull(onPayments, "onPayments");
    this.decisionTime = decisionTime;
    this.discountFactorPayments = ImmutableList.copyOf(discountFactorPayments);
    this.iborComputations = ImmutableList.copyOf(iborComputations);
    this.iborPayments = ImmutableList.copyOf(iborPayments);
    this.onComputations = ImmutableList.copyOf(onComputations);
    this.onPayments = ImmutableList.copyOf(onPayments);
  }

  @Override
  public MulticurveEquivalent.Meta metaBean() {
    return MulticurveEquivalent.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the date at which an exercise or fixing takes place.
   * @return the value of the property
   */
  public ZonedDateTime getDecisionTime() {
    return decisionTime;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the dates and amounts impacting the value through discount factors at each decision date.
   * @return the value of the property, not null
   */
  public List<NotionalExchange> getDiscountFactorPayments() {
    return discountFactorPayments;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the rates impacting the value through ibor fixing at the decision date.
   * @return the value of the property, not null
   */
  public List<IborRateComputation> getIborComputations() {
    return iborComputations;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the reference amounts and payment date for each ibor observation.
   * @return the value of the property, not null
   */
  public List<NotionalExchange> getIborPayments() {
    return iborPayments;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the date impacting the value through overnight compounded periods at the decision date.
   * @return the value of the property, not null
   */
  public List<OvernightCompoundedRateComputation> getOnComputations() {
    return onComputations;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the reference amounts and payment date for each overnight observation.
   * @return the value of the property, not null
   */
  public List<NotionalExchange> getOnPayments() {
    return onPayments;
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
      MulticurveEquivalent other = (MulticurveEquivalent) obj;
      return JodaBeanUtils.equal(decisionTime, other.decisionTime) &&
          JodaBeanUtils.equal(discountFactorPayments, other.discountFactorPayments) &&
          JodaBeanUtils.equal(iborComputations, other.iborComputations) &&
          JodaBeanUtils.equal(iborPayments, other.iborPayments) &&
          JodaBeanUtils.equal(onComputations, other.onComputations) &&
          JodaBeanUtils.equal(onPayments, other.onPayments);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(decisionTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(discountFactorPayments);
    hash = hash * 31 + JodaBeanUtils.hashCode(iborComputations);
    hash = hash * 31 + JodaBeanUtils.hashCode(iborPayments);
    hash = hash * 31 + JodaBeanUtils.hashCode(onComputations);
    hash = hash * 31 + JodaBeanUtils.hashCode(onPayments);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(224);
    buf.append("MulticurveEquivalent{");
    buf.append("decisionTime").append('=').append(JodaBeanUtils.toString(decisionTime)).append(',').append(' ');
    buf.append("discountFactorPayments").append('=').append(JodaBeanUtils.toString(discountFactorPayments)).append(',').append(' ');
    buf.append("iborComputations").append('=').append(JodaBeanUtils.toString(iborComputations)).append(',').append(' ');
    buf.append("iborPayments").append('=').append(JodaBeanUtils.toString(iborPayments)).append(',').append(' ');
    buf.append("onComputations").append('=').append(JodaBeanUtils.toString(onComputations)).append(',').append(' ');
    buf.append("onPayments").append('=').append(JodaBeanUtils.toString(onPayments));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code MulticurveEquivalent}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code decisionTime} property.
     */
    private final MetaProperty<ZonedDateTime> decisionTime = DirectMetaProperty.ofImmutable(
        this, "decisionTime", MulticurveEquivalent.class, ZonedDateTime.class);
    /**
     * The meta-property for the {@code discountFactorPayments} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<NotionalExchange>> discountFactorPayments = DirectMetaProperty.ofImmutable(
        this, "discountFactorPayments", MulticurveEquivalent.class, (Class) List.class);
    /**
     * The meta-property for the {@code iborComputations} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<IborRateComputation>> iborComputations = DirectMetaProperty.ofImmutable(
        this, "iborComputations", MulticurveEquivalent.class, (Class) List.class);
    /**
     * The meta-property for the {@code iborPayments} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<NotionalExchange>> iborPayments = DirectMetaProperty.ofImmutable(
        this, "iborPayments", MulticurveEquivalent.class, (Class) List.class);
    /**
     * The meta-property for the {@code onComputations} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<OvernightCompoundedRateComputation>> onComputations = DirectMetaProperty.ofImmutable(
        this, "onComputations", MulticurveEquivalent.class, (Class) List.class);
    /**
     * The meta-property for the {@code onPayments} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<List<NotionalExchange>> onPayments = DirectMetaProperty.ofImmutable(
        this, "onPayments", MulticurveEquivalent.class, (Class) List.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "decisionTime",
        "discountFactorPayments",
        "iborComputations",
        "iborPayments",
        "onComputations",
        "onPayments");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 676112585:  // decisionTime
          return decisionTime;
        case -423483075:  // discountFactorPayments
          return discountFactorPayments;
        case -1568013720:  // iborComputations
          return iborComputations;
        case 1878994953:  // iborPayments
          return iborPayments;
        case -39109877:  // onComputations
          return onComputations;
        case -142331348:  // onPayments
          return onPayments;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public MulticurveEquivalent.Builder builder() {
      return new MulticurveEquivalent.Builder();
    }

    @Override
    public Class<? extends MulticurveEquivalent> beanType() {
      return MulticurveEquivalent.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code decisionTime} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZonedDateTime> decisionTime() {
      return decisionTime;
    }

    /**
     * The meta-property for the {@code discountFactorPayments} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<NotionalExchange>> discountFactorPayments() {
      return discountFactorPayments;
    }

    /**
     * The meta-property for the {@code iborComputations} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<IborRateComputation>> iborComputations() {
      return iborComputations;
    }

    /**
     * The meta-property for the {@code iborPayments} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<NotionalExchange>> iborPayments() {
      return iborPayments;
    }

    /**
     * The meta-property for the {@code onComputations} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<OvernightCompoundedRateComputation>> onComputations() {
      return onComputations;
    }

    /**
     * The meta-property for the {@code onPayments} property.
     * @return the meta-property, not null
     */
    public MetaProperty<List<NotionalExchange>> onPayments() {
      return onPayments;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 676112585:  // decisionTime
          return ((MulticurveEquivalent) bean).getDecisionTime();
        case -423483075:  // discountFactorPayments
          return ((MulticurveEquivalent) bean).getDiscountFactorPayments();
        case -1568013720:  // iborComputations
          return ((MulticurveEquivalent) bean).getIborComputations();
        case 1878994953:  // iborPayments
          return ((MulticurveEquivalent) bean).getIborPayments();
        case -39109877:  // onComputations
          return ((MulticurveEquivalent) bean).getOnComputations();
        case -142331348:  // onPayments
          return ((MulticurveEquivalent) bean).getOnPayments();
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
   * The bean-builder for {@code MulticurveEquivalent}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<MulticurveEquivalent> {

    private ZonedDateTime decisionTime;
    private List<NotionalExchange> discountFactorPayments = ImmutableList.of();
    private List<IborRateComputation> iborComputations = ImmutableList.of();
    private List<NotionalExchange> iborPayments = ImmutableList.of();
    private List<OvernightCompoundedRateComputation> onComputations = ImmutableList.of();
    private List<NotionalExchange> onPayments = ImmutableList.of();

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(MulticurveEquivalent beanToCopy) {
      this.decisionTime = beanToCopy.getDecisionTime();
      this.discountFactorPayments = ImmutableList.copyOf(beanToCopy.getDiscountFactorPayments());
      this.iborComputations = ImmutableList.copyOf(beanToCopy.getIborComputations());
      this.iborPayments = ImmutableList.copyOf(beanToCopy.getIborPayments());
      this.onComputations = ImmutableList.copyOf(beanToCopy.getOnComputations());
      this.onPayments = ImmutableList.copyOf(beanToCopy.getOnPayments());
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 676112585:  // decisionTime
          return decisionTime;
        case -423483075:  // discountFactorPayments
          return discountFactorPayments;
        case -1568013720:  // iborComputations
          return iborComputations;
        case 1878994953:  // iborPayments
          return iborPayments;
        case -39109877:  // onComputations
          return onComputations;
        case -142331348:  // onPayments
          return onPayments;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 676112585:  // decisionTime
          this.decisionTime = (ZonedDateTime) newValue;
          break;
        case -423483075:  // discountFactorPayments
          this.discountFactorPayments = (List<NotionalExchange>) newValue;
          break;
        case -1568013720:  // iborComputations
          this.iborComputations = (List<IborRateComputation>) newValue;
          break;
        case 1878994953:  // iborPayments
          this.iborPayments = (List<NotionalExchange>) newValue;
          break;
        case -39109877:  // onComputations
          this.onComputations = (List<OvernightCompoundedRateComputation>) newValue;
          break;
        case -142331348:  // onPayments
          this.onPayments = (List<NotionalExchange>) newValue;
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
    public MulticurveEquivalent build() {
      return new MulticurveEquivalent(
          decisionTime,
          discountFactorPayments,
          iborComputations,
          iborPayments,
          onComputations,
          onPayments);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the date at which an exercise or fixing takes place.
     * @param decisionTime  the new value
     * @return this, for chaining, not null
     */
    public Builder decisionTime(ZonedDateTime decisionTime) {
      this.decisionTime = decisionTime;
      return this;
    }

    /**
     * Sets the dates and amounts impacting the value through discount factors at each decision date.
     * @param discountFactorPayments  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactorPayments(List<NotionalExchange> discountFactorPayments) {
      JodaBeanUtils.notNull(discountFactorPayments, "discountFactorPayments");
      this.discountFactorPayments = discountFactorPayments;
      return this;
    }

    /**
     * Sets the {@code discountFactorPayments} property in the builder
     * from an array of objects.
     * @param discountFactorPayments  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactorPayments(NotionalExchange... discountFactorPayments) {
      return discountFactorPayments(ImmutableList.copyOf(discountFactorPayments));
    }

    /**
     * Sets the rates impacting the value through ibor fixing at the decision date.
     * @param iborComputations  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborComputations(List<IborRateComputation> iborComputations) {
      JodaBeanUtils.notNull(iborComputations, "iborComputations");
      this.iborComputations = iborComputations;
      return this;
    }

    /**
     * Sets the {@code iborComputations} property in the builder
     * from an array of objects.
     * @param iborComputations  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborComputations(IborRateComputation... iborComputations) {
      return iborComputations(ImmutableList.copyOf(iborComputations));
    }

    /**
     * Sets the reference amounts and payment date for each ibor observation.
     * @param iborPayments  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborPayments(List<NotionalExchange> iborPayments) {
      JodaBeanUtils.notNull(iborPayments, "iborPayments");
      this.iborPayments = iborPayments;
      return this;
    }

    /**
     * Sets the {@code iborPayments} property in the builder
     * from an array of objects.
     * @param iborPayments  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborPayments(NotionalExchange... iborPayments) {
      return iborPayments(ImmutableList.copyOf(iborPayments));
    }

    /**
     * Sets the date impacting the value through overnight compounded periods at the decision date.
     * @param onComputations  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder onComputations(List<OvernightCompoundedRateComputation> onComputations) {
      JodaBeanUtils.notNull(onComputations, "onComputations");
      this.onComputations = onComputations;
      return this;
    }

    /**
     * Sets the {@code onComputations} property in the builder
     * from an array of objects.
     * @param onComputations  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder onComputations(OvernightCompoundedRateComputation... onComputations) {
      return onComputations(ImmutableList.copyOf(onComputations));
    }

    /**
     * Sets the reference amounts and payment date for each overnight observation.
     * @param onPayments  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder onPayments(List<NotionalExchange> onPayments) {
      JodaBeanUtils.notNull(onPayments, "onPayments");
      this.onPayments = onPayments;
      return this;
    }

    /**
     * Sets the {@code onPayments} property in the builder
     * from an array of objects.
     * @param onPayments  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder onPayments(NotionalExchange... onPayments) {
      return onPayments(ImmutableList.copyOf(onPayments));
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(224);
      buf.append("MulticurveEquivalent.Builder{");
      buf.append("decisionTime").append('=').append(JodaBeanUtils.toString(decisionTime)).append(',').append(' ');
      buf.append("discountFactorPayments").append('=').append(JodaBeanUtils.toString(discountFactorPayments)).append(',').append(' ');
      buf.append("iborComputations").append('=').append(JodaBeanUtils.toString(iborComputations)).append(',').append(' ');
      buf.append("iborPayments").append('=').append(JodaBeanUtils.toString(iborPayments)).append(',').append(' ');
      buf.append("onComputations").append('=').append(JodaBeanUtils.toString(onComputations)).append(',').append(' ');
      buf.append("onPayments").append('=').append(JodaBeanUtils.toString(onPayments));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
