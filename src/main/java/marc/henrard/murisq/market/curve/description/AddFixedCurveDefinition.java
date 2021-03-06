/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.description;

import java.io.Serializable;
import java.time.LocalDate;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.AddFixedCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveNode;
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
 * Definition of a curve described by a fixed underlying curve and an additive spread. 
 * The nodes and sensitivities are on the spread curve.
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class AddFixedCurveDefinition
    implements CurveDefinition, ImmutableBean, Serializable {

  /**
   * The fixed curve. Also called base or shape curve.
   */
  @PropertyDefinition(validate = "notNull")
  private final Curve fixedCurve;
  /**
   * The spread curve. Also called the variable curve.
   */
  @PropertyDefinition(validate = "notNull")
  private final CurveDefinition spreadCurveDefinition;

  @Override
  public CurveName getName() {
    return spreadCurveDefinition.getName();
  }

  @Override
  public int getParameterCount() {
    return spreadCurveDefinition.getParameterCount();
  }

  @Override
  public ValueType getYValueType() {
    return spreadCurveDefinition.getYValueType();
  }

  @Override
  public ImmutableList<CurveNode> getNodes() {
    return spreadCurveDefinition.getNodes();
  }

  @Override
  public CurveDefinition filtered(LocalDate valuationDate, ReferenceData refData) {
    return spreadCurveDefinition.filtered(valuationDate, refData);
  }

  @Override
  public CurveMetadata metadata(LocalDate valuationDate, ReferenceData refData) {
    return spreadCurveDefinition.metadata(valuationDate, refData);
  }

  @Override
  public Curve curve(LocalDate valuationDate, CurveMetadata metadata, DoubleArray parameters) {
    Curve spreadCurve = spreadCurveDefinition.curve(valuationDate, metadata, parameters);
    return AddFixedCurve.of(fixedCurve, spreadCurve);
  }

  @Override
  public ImmutableList<Double> initialGuess(MarketData marketData) {
    return spreadCurveDefinition.initialGuess(marketData);
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code AddFixedCurveDefinition}.
   * @return the meta-bean, not null
   */
  public static AddFixedCurveDefinition.Meta meta() {
    return AddFixedCurveDefinition.Meta.INSTANCE;
  }

  static {
    MetaBean.register(AddFixedCurveDefinition.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param fixedCurve  the value of the property, not null
   * @param spreadCurveDefinition  the value of the property, not null
   * @return the instance
   */
  public static AddFixedCurveDefinition of(
      Curve fixedCurve,
      CurveDefinition spreadCurveDefinition) {
    return new AddFixedCurveDefinition(
      fixedCurve,
      spreadCurveDefinition);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static AddFixedCurveDefinition.Builder builder() {
    return new AddFixedCurveDefinition.Builder();
  }

  private AddFixedCurveDefinition(
      Curve fixedCurve,
      CurveDefinition spreadCurveDefinition) {
    JodaBeanUtils.notNull(fixedCurve, "fixedCurve");
    JodaBeanUtils.notNull(spreadCurveDefinition, "spreadCurveDefinition");
    this.fixedCurve = fixedCurve;
    this.spreadCurveDefinition = spreadCurveDefinition;
  }

  @Override
  public AddFixedCurveDefinition.Meta metaBean() {
    return AddFixedCurveDefinition.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the fixed curve. Also called base or shape curve.
   * @return the value of the property, not null
   */
  public Curve getFixedCurve() {
    return fixedCurve;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the spread curve. Also called the variable curve.
   * @return the value of the property, not null
   */
  public CurveDefinition getSpreadCurveDefinition() {
    return spreadCurveDefinition;
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
      AddFixedCurveDefinition other = (AddFixedCurveDefinition) obj;
      return JodaBeanUtils.equal(fixedCurve, other.fixedCurve) &&
          JodaBeanUtils.equal(spreadCurveDefinition, other.spreadCurveDefinition);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(fixedCurve);
    hash = hash * 31 + JodaBeanUtils.hashCode(spreadCurveDefinition);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(96);
    buf.append("AddFixedCurveDefinition{");
    buf.append("fixedCurve").append('=').append(JodaBeanUtils.toString(fixedCurve)).append(',').append(' ');
    buf.append("spreadCurveDefinition").append('=').append(JodaBeanUtils.toString(spreadCurveDefinition));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code AddFixedCurveDefinition}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code fixedCurve} property.
     */
    private final MetaProperty<Curve> fixedCurve = DirectMetaProperty.ofImmutable(
        this, "fixedCurve", AddFixedCurveDefinition.class, Curve.class);
    /**
     * The meta-property for the {@code spreadCurveDefinition} property.
     */
    private final MetaProperty<CurveDefinition> spreadCurveDefinition = DirectMetaProperty.ofImmutable(
        this, "spreadCurveDefinition", AddFixedCurveDefinition.class, CurveDefinition.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "fixedCurve",
        "spreadCurveDefinition");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1682092507:  // fixedCurve
          return fixedCurve;
        case 1239696815:  // spreadCurveDefinition
          return spreadCurveDefinition;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public AddFixedCurveDefinition.Builder builder() {
      return new AddFixedCurveDefinition.Builder();
    }

    @Override
    public Class<? extends AddFixedCurveDefinition> beanType() {
      return AddFixedCurveDefinition.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code fixedCurve} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Curve> fixedCurve() {
      return fixedCurve;
    }

    /**
     * The meta-property for the {@code spreadCurveDefinition} property.
     * @return the meta-property, not null
     */
    public MetaProperty<CurveDefinition> spreadCurveDefinition() {
      return spreadCurveDefinition;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1682092507:  // fixedCurve
          return ((AddFixedCurveDefinition) bean).getFixedCurve();
        case 1239696815:  // spreadCurveDefinition
          return ((AddFixedCurveDefinition) bean).getSpreadCurveDefinition();
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
   * The bean-builder for {@code AddFixedCurveDefinition}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<AddFixedCurveDefinition> {

    private Curve fixedCurve;
    private CurveDefinition spreadCurveDefinition;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(AddFixedCurveDefinition beanToCopy) {
      this.fixedCurve = beanToCopy.getFixedCurve();
      this.spreadCurveDefinition = beanToCopy.getSpreadCurveDefinition();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1682092507:  // fixedCurve
          return fixedCurve;
        case 1239696815:  // spreadCurveDefinition
          return spreadCurveDefinition;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1682092507:  // fixedCurve
          this.fixedCurve = (Curve) newValue;
          break;
        case 1239696815:  // spreadCurveDefinition
          this.spreadCurveDefinition = (CurveDefinition) newValue;
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
    public AddFixedCurveDefinition build() {
      return new AddFixedCurveDefinition(
          fixedCurve,
          spreadCurveDefinition);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the fixed curve. Also called base or shape curve.
     * @param fixedCurve  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fixedCurve(Curve fixedCurve) {
      JodaBeanUtils.notNull(fixedCurve, "fixedCurve");
      this.fixedCurve = fixedCurve;
      return this;
    }

    /**
     * Sets the spread curve. Also called the variable curve.
     * @param spreadCurveDefinition  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder spreadCurveDefinition(CurveDefinition spreadCurveDefinition) {
      JodaBeanUtils.notNull(spreadCurveDefinition, "spreadCurveDefinition");
      this.spreadCurveDefinition = spreadCurveDefinition;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(96);
      buf.append("AddFixedCurveDefinition.Builder{");
      buf.append("fixedCurve").append('=').append(JodaBeanUtils.toString(fixedCurve)).append(',').append(' ');
      buf.append("spreadCurveDefinition").append('=').append(JodaBeanUtils.toString(spreadCurveDefinition));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
