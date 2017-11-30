/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
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
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.minimization.DoubleRangeLimitTransform;
import com.opengamma.strata.math.impl.minimization.NonLinearParameterTransforms;
import com.opengamma.strata.math.impl.minimization.NullTransform;
import com.opengamma.strata.math.impl.minimization.ParameterLimitsTransform;
import com.opengamma.strata.math.impl.minimization.ParameterLimitsTransform.LimitType;
import com.opengamma.strata.math.impl.minimization.SingleRangeLimitTransform;
import com.opengamma.strata.math.impl.minimization.UncoupledParameterTransforms;
import com.opengamma.strata.pricer.DiscountFactors;

import marc.henrard.risq.model.generic.TimeMeasurement;

/**
 * Template for a rational two-factor model {@link RationalTwoFactorHWShapePlusCstParameters}.
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class RationalTwoFactorHWShapePlusCstTemplate
    implements RationalTemplate, ImmutableBean, Serializable  {

  private static final int NB_PARAMETERS = 8;
  private static final double LIMIT_0 = 1.0E-8;
  private static final double LIMIT_A = 1.0E-2; // To avoid singular behavior around 0
  private static final ParameterLimitsTransform[] DEFAULT_TRANSFORMS;
  static {
    DEFAULT_TRANSFORMS = new ParameterLimitsTransform[8];
    DEFAULT_TRANSFORMS[0] = new SingleRangeLimitTransform(LIMIT_A, LimitType.GREATER_THAN); // a1 > 0
    DEFAULT_TRANSFORMS[1] = new SingleRangeLimitTransform(LIMIT_A, LimitType.GREATER_THAN); // a2 > 0
    DEFAULT_TRANSFORMS[2] = new DoubleRangeLimitTransform(-1.0d, 1.0d); // -1 < correlation < 1
    DEFAULT_TRANSFORMS[3] = new DoubleRangeLimitTransform(0.0d, 1.0d); // 0 < b00 < 1
    DEFAULT_TRANSFORMS[4] = new SingleRangeLimitTransform(LIMIT_0, LimitType.GREATER_THAN); // eta > 0
    DEFAULT_TRANSFORMS[5] = new SingleRangeLimitTransform(LIMIT_0, LimitType.GREATER_THAN); // kappa > 0
    DEFAULT_TRANSFORMS[6] = new NullTransform(); // c_1
    DEFAULT_TRANSFORMS[7] = new NullTransform(); // c_2
  }
  private static final List<Function<Double, Boolean>> CONSTRAINTS = new ArrayList<>();
  static {
    CONSTRAINTS.add(a1 -> (a1 > 0));
    CONSTRAINTS.add(a2 -> (a2 > 0));
    CONSTRAINTS.add(rho -> ((rho > -1.0) && (rho < 1.0)));
    CONSTRAINTS.add(b00 -> ((b00 > 0) && (b00 < 1)));
    CONSTRAINTS.add(eta -> (eta > 0));
    CONSTRAINTS.add(kappa -> (kappa > 0));
    CONSTRAINTS.add(c1 -> true);
    CONSTRAINTS.add(c2 -> true);
  }

  /** The mechanism to measure time for time to expiry. */
  @PropertyDefinition(validate = "notNull")
  private final TimeMeasurement timeMeasure;
  /** The discount factors */
  @PropertyDefinition(validate = "notNull")
  private final DiscountFactors discountFactors;
  /** The valuation time. All data items in this environment are calibrated for this time. */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime valuationTime;
  /** The valuation zone.*/
  @PropertyDefinition(validate = "notNull")
  private final ZoneId valuationZone;
  /** The default initial guess.*/
  @PropertyDefinition(validate = "validateInitialGuess")
  private final DoubleArray initialGuess;
  /** The fixed parameters which are not calibrated but set at their guess value. */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final BitSet fixed;
  
  // Validation of initial guess
  private static void validateInitialGuess(DoubleArray initialGuess, String message) {
    ArgChecker.notNull(initialGuess, message);
    ArgChecker.isTrue(initialGuess.size() == NB_PARAMETERS, message);
  }

  @Override
  public int parametersCount() {
    return NB_PARAMETERS;
  }

  @Override
  public DoubleArray initialGuess() {
    return initialGuess;
  }

  @Override
  public RationalTwoFactorHWShapePlusCstParameters generate(DoubleArray parameters) {
    ArgChecker.isTrue(parameters.size() == initialGuess.size(), "Incorrect number of parameters");
    return RationalTwoFactorHWShapePlusCstParameters
        .of(parameters, timeMeasure, discountFactors, valuationTime, valuationZone);
  }

  @Override
  public NonLinearParameterTransforms getTransform() {
    return new UncoupledParameterTransforms(initialGuess, DEFAULT_TRANSFORMS, fixed);
  }

  @Override
  public Function<DoubleArray, Boolean> getConstraints() {
    return (parameters) -> {
      boolean isOk = true;
      int loopp = 0;
      for (int i = 0; i < initialGuess.size(); i++) {
        if (!fixed.get(i)) {
          isOk = (isOk && CONSTRAINTS.get(i).apply(parameters.get(loopp)));
          loopp++;
        }
      }
      return isOk;
    };
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code RationalTwoFactorHWShapePlusCstTemplate}.
   * @return the meta-bean, not null
   */
  public static RationalTwoFactorHWShapePlusCstTemplate.Meta meta() {
    return RationalTwoFactorHWShapePlusCstTemplate.Meta.INSTANCE;
  }

  static {
    MetaBean.register(RationalTwoFactorHWShapePlusCstTemplate.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param timeMeasure  the value of the property, not null
   * @param discountFactors  the value of the property, not null
   * @param valuationTime  the value of the property, not null
   * @param valuationZone  the value of the property, not null
   * @param initialGuess  the value of the property
   * @param fixed  the value of the property, not null
   * @return the instance
   */
  public static RationalTwoFactorHWShapePlusCstTemplate of(
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime,
      ZoneId valuationZone,
      DoubleArray initialGuess,
      BitSet fixed) {
    return new RationalTwoFactorHWShapePlusCstTemplate(
      timeMeasure,
      discountFactors,
      valuationTime,
      valuationZone,
      initialGuess,
      fixed);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static RationalTwoFactorHWShapePlusCstTemplate.Builder builder() {
    return new RationalTwoFactorHWShapePlusCstTemplate.Builder();
  }

  private RationalTwoFactorHWShapePlusCstTemplate(
      TimeMeasurement timeMeasure,
      DiscountFactors discountFactors,
      LocalTime valuationTime,
      ZoneId valuationZone,
      DoubleArray initialGuess,
      BitSet fixed) {
    JodaBeanUtils.notNull(timeMeasure, "timeMeasure");
    JodaBeanUtils.notNull(discountFactors, "discountFactors");
    JodaBeanUtils.notNull(valuationTime, "valuationTime");
    JodaBeanUtils.notNull(valuationZone, "valuationZone");
    validateInitialGuess(initialGuess, "initialGuess");
    JodaBeanUtils.notNull(fixed, "fixed");
    this.timeMeasure = timeMeasure;
    this.discountFactors = discountFactors;
    this.valuationTime = valuationTime;
    this.valuationZone = valuationZone;
    this.initialGuess = initialGuess;
    this.fixed = fixed;
  }

  @Override
  public RationalTwoFactorHWShapePlusCstTemplate.Meta metaBean() {
    return RationalTwoFactorHWShapePlusCstTemplate.Meta.INSTANCE;
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
   * Gets the discount factors
   * @return the value of the property, not null
   */
  public DiscountFactors getDiscountFactors() {
    return discountFactors;
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
   * Gets the default initial guess.
   * @return the value of the property
   */
  public DoubleArray getInitialGuess() {
    return initialGuess;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the fixed parameters which are not calibrated but set at their guess value.
   * @return the value of the property, not null
   */
  @Override
  public BitSet getFixed() {
    return fixed;
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
      RationalTwoFactorHWShapePlusCstTemplate other = (RationalTwoFactorHWShapePlusCstTemplate) obj;
      return JodaBeanUtils.equal(timeMeasure, other.timeMeasure) &&
          JodaBeanUtils.equal(discountFactors, other.discountFactors) &&
          JodaBeanUtils.equal(valuationTime, other.valuationTime) &&
          JodaBeanUtils.equal(valuationZone, other.valuationZone) &&
          JodaBeanUtils.equal(initialGuess, other.initialGuess) &&
          JodaBeanUtils.equal(fixed, other.fixed);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(timeMeasure);
    hash = hash * 31 + JodaBeanUtils.hashCode(discountFactors);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationTime);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationZone);
    hash = hash * 31 + JodaBeanUtils.hashCode(initialGuess);
    hash = hash * 31 + JodaBeanUtils.hashCode(fixed);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(224);
    buf.append("RationalTwoFactorHWShapePlusCstTemplate{");
    buf.append("timeMeasure").append('=').append(timeMeasure).append(',').append(' ');
    buf.append("discountFactors").append('=').append(discountFactors).append(',').append(' ');
    buf.append("valuationTime").append('=').append(valuationTime).append(',').append(' ');
    buf.append("valuationZone").append('=').append(valuationZone).append(',').append(' ');
    buf.append("initialGuess").append('=').append(initialGuess).append(',').append(' ');
    buf.append("fixed").append('=').append(JodaBeanUtils.toString(fixed));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code RationalTwoFactorHWShapePlusCstTemplate}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code timeMeasure} property.
     */
    private final MetaProperty<TimeMeasurement> timeMeasure = DirectMetaProperty.ofImmutable(
        this, "timeMeasure", RationalTwoFactorHWShapePlusCstTemplate.class, TimeMeasurement.class);
    /**
     * The meta-property for the {@code discountFactors} property.
     */
    private final MetaProperty<DiscountFactors> discountFactors = DirectMetaProperty.ofImmutable(
        this, "discountFactors", RationalTwoFactorHWShapePlusCstTemplate.class, DiscountFactors.class);
    /**
     * The meta-property for the {@code valuationTime} property.
     */
    private final MetaProperty<LocalTime> valuationTime = DirectMetaProperty.ofImmutable(
        this, "valuationTime", RationalTwoFactorHWShapePlusCstTemplate.class, LocalTime.class);
    /**
     * The meta-property for the {@code valuationZone} property.
     */
    private final MetaProperty<ZoneId> valuationZone = DirectMetaProperty.ofImmutable(
        this, "valuationZone", RationalTwoFactorHWShapePlusCstTemplate.class, ZoneId.class);
    /**
     * The meta-property for the {@code initialGuess} property.
     */
    private final MetaProperty<DoubleArray> initialGuess = DirectMetaProperty.ofImmutable(
        this, "initialGuess", RationalTwoFactorHWShapePlusCstTemplate.class, DoubleArray.class);
    /**
     * The meta-property for the {@code fixed} property.
     */
    private final MetaProperty<BitSet> fixed = DirectMetaProperty.ofImmutable(
        this, "fixed", RationalTwoFactorHWShapePlusCstTemplate.class, BitSet.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "timeMeasure",
        "discountFactors",
        "valuationTime",
        "valuationZone",
        "initialGuess",
        "fixed");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case -91613053:  // discountFactors
          return discountFactors;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case -431632141:  // initialGuess
          return initialGuess;
        case 97445748:  // fixed
          return fixed;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public RationalTwoFactorHWShapePlusCstTemplate.Builder builder() {
      return new RationalTwoFactorHWShapePlusCstTemplate.Builder();
    }

    @Override
    public Class<? extends RationalTwoFactorHWShapePlusCstTemplate> beanType() {
      return RationalTwoFactorHWShapePlusCstTemplate.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code timeMeasure} property.
     * @return the meta-property, not null
     */
    public MetaProperty<TimeMeasurement> timeMeasure() {
      return timeMeasure;
    }

    /**
     * The meta-property for the {@code discountFactors} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DiscountFactors> discountFactors() {
      return discountFactors;
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
     * The meta-property for the {@code initialGuess} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DoubleArray> initialGuess() {
      return initialGuess;
    }

    /**
     * The meta-property for the {@code fixed} property.
     * @return the meta-property, not null
     */
    public MetaProperty<BitSet> fixed() {
      return fixed;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1642109393:  // timeMeasure
          return ((RationalTwoFactorHWShapePlusCstTemplate) bean).getTimeMeasure();
        case -91613053:  // discountFactors
          return ((RationalTwoFactorHWShapePlusCstTemplate) bean).getDiscountFactors();
        case 113591406:  // valuationTime
          return ((RationalTwoFactorHWShapePlusCstTemplate) bean).getValuationTime();
        case 113775949:  // valuationZone
          return ((RationalTwoFactorHWShapePlusCstTemplate) bean).getValuationZone();
        case -431632141:  // initialGuess
          return ((RationalTwoFactorHWShapePlusCstTemplate) bean).getInitialGuess();
        case 97445748:  // fixed
          return ((RationalTwoFactorHWShapePlusCstTemplate) bean).getFixed();
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
   * The bean-builder for {@code RationalTwoFactorHWShapePlusCstTemplate}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<RationalTwoFactorHWShapePlusCstTemplate> {

    private TimeMeasurement timeMeasure;
    private DiscountFactors discountFactors;
    private LocalTime valuationTime;
    private ZoneId valuationZone;
    private DoubleArray initialGuess;
    private BitSet fixed;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(RationalTwoFactorHWShapePlusCstTemplate beanToCopy) {
      this.timeMeasure = beanToCopy.getTimeMeasure();
      this.discountFactors = beanToCopy.getDiscountFactors();
      this.valuationTime = beanToCopy.getValuationTime();
      this.valuationZone = beanToCopy.getValuationZone();
      this.initialGuess = beanToCopy.getInitialGuess();
      this.fixed = beanToCopy.getFixed();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1642109393:  // timeMeasure
          return timeMeasure;
        case -91613053:  // discountFactors
          return discountFactors;
        case 113591406:  // valuationTime
          return valuationTime;
        case 113775949:  // valuationZone
          return valuationZone;
        case -431632141:  // initialGuess
          return initialGuess;
        case 97445748:  // fixed
          return fixed;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1642109393:  // timeMeasure
          this.timeMeasure = (TimeMeasurement) newValue;
          break;
        case -91613053:  // discountFactors
          this.discountFactors = (DiscountFactors) newValue;
          break;
        case 113591406:  // valuationTime
          this.valuationTime = (LocalTime) newValue;
          break;
        case 113775949:  // valuationZone
          this.valuationZone = (ZoneId) newValue;
          break;
        case -431632141:  // initialGuess
          this.initialGuess = (DoubleArray) newValue;
          break;
        case 97445748:  // fixed
          this.fixed = (BitSet) newValue;
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
    public RationalTwoFactorHWShapePlusCstTemplate build() {
      return new RationalTwoFactorHWShapePlusCstTemplate(
          timeMeasure,
          discountFactors,
          valuationTime,
          valuationZone,
          initialGuess,
          fixed);
    }

    //-----------------------------------------------------------------------
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
     * Sets the discount factors
     * @param discountFactors  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactors(DiscountFactors discountFactors) {
      JodaBeanUtils.notNull(discountFactors, "discountFactors");
      this.discountFactors = discountFactors;
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
     * Sets the default initial guess.
     * @param initialGuess  the new value
     * @return this, for chaining, not null
     */
    public Builder initialGuess(DoubleArray initialGuess) {
      validateInitialGuess(initialGuess, "initialGuess");
      this.initialGuess = initialGuess;
      return this;
    }

    /**
     * Sets the fixed parameters which are not calibrated but set at their guess value.
     * @param fixed  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixed(BitSet fixed) {
      JodaBeanUtils.notNull(fixed, "fixed");
      this.fixed = fixed;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(224);
      buf.append("RationalTwoFactorHWShapePlusCstTemplate.Builder{");
      buf.append("timeMeasure").append('=').append(JodaBeanUtils.toString(timeMeasure)).append(',').append(' ');
      buf.append("discountFactors").append('=').append(JodaBeanUtils.toString(discountFactors)).append(',').append(' ');
      buf.append("valuationTime").append('=').append(JodaBeanUtils.toString(valuationTime)).append(',').append(' ');
      buf.append("valuationZone").append('=').append(JodaBeanUtils.toString(valuationZone)).append(',').append(' ');
      buf.append("initialGuess").append('=').append(JodaBeanUtils.toString(initialGuess)).append(',').append(' ');
      buf.append("fixed").append('=').append(JodaBeanUtils.toString(fixed));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
