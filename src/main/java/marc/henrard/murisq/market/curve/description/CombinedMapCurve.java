/**
 * Copyright (C) 2022 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.description;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.io.Serializable;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.param.CurrencyParameterSensitivity;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.ParameterPerturbation;
import com.opengamma.strata.market.param.UnitParameterSensitivity;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.poi.ss.formula.eval.NotImplementedException;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * A curve obtained by the combination of two other curves and a given mapping between the common x-value and the 
 * y-values of the two underlying curves.
 * TODO: The derivative/sensitivity part still need to be implemented.
 * 
 * @author Marc Henrard
 */
@BeanDefinition(factoryName = "of")
public final class CombinedMapCurve
    implements Curve, ImmutableBean, Serializable {

  /**
   * The first curve.
   */
  @PropertyDefinition(validate = "notNull")
  private final Curve firstCurve;
  /**
   * The second curve. 
   */
  @PropertyDefinition(validate = "notNull")
  private final Curve secondCurve;
  /**
   * The mapping function. Map the x value and the y value of the first curve and second underlying curve to the y-value
   * of the global curve.
   */
  @PropertyDefinition(validate = "notNull")
  private final Function<Triple<Double,Double,Double>, Double> mapping;
  /**
   * The curve metadata.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final CurveMetadata metadata;


  //-------------------------------------------------------------------------
  @Override
  public double yValue(double x) {
    return mapping.apply(Triple.of(x, firstCurve.yValue(x), secondCurve.yValue(x)));
  }

  @Override
  public UnitParameterSensitivity yValueParameterSensitivity(double x) {
    throw new NotImplementedException("parameter sensitivity not implemented yet"); // TODO
  }

  @Override
  public double firstDerivative(double x) {
    throw new NotImplementedException("parameter sensitivity not implemented yet"); // TODO
  }

  //-------------------------------------------------------------------------
  @Override
  public UnitParameterSensitivity createParameterSensitivity(DoubleArray sensitivities) {
    throw new NotImplementedException("parameter sensitivity not implemented yet"); // TODO
  }

  @Override
  public CurrencyParameterSensitivity createParameterSensitivity(Currency currency, DoubleArray sensitivities) {
    throw new NotImplementedException("parameter sensitivity not implemented yet"); // TODO
  }

  //-------------------------------------------------------------------------
  @Override
  public CombinedMapCurve withMetadata(CurveMetadata metadata) {
    return new CombinedMapCurve(firstCurve, secondCurve, mapping, metadata);
  }

  //-------------------------------------------------------------------------
  @Override
  public int getParameterCount() {
    return firstCurve.getParameterCount() + secondCurve.getParameterCount();
  }

  @Override
  public double getParameter(int parameterIndex) {
    if (parameterIndex < firstCurve.getParameterCount()) {
      return firstCurve.getParameter(parameterIndex);
    }
    return secondCurve.getParameter(parameterIndex - firstCurve.getParameterCount());
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    if (parameterIndex < firstCurve.getParameterCount()) {
      return firstCurve.getParameterMetadata(parameterIndex);
    }
    return secondCurve.getParameterMetadata(parameterIndex - firstCurve.getParameterCount());
  }

  @Override
  public CombinedMapCurve withParameter(int parameterIndex, double newValue) {
    if (parameterIndex < firstCurve.getParameterCount()) {
      return new CombinedMapCurve(
          firstCurve.withParameter(parameterIndex, newValue),
          secondCurve,
          mapping,
          metadata);
    }
    return new CombinedMapCurve(
        firstCurve,
        secondCurve.withParameter(parameterIndex - firstCurve.getParameterCount(), newValue),
        mapping,
        metadata);
  }

  @Override
  public CombinedMapCurve withPerturbation(ParameterPerturbation perturbation) {

    Curve newFirstCurve = firstCurve.withPerturbation(
        (idx, value, meta) -> perturbation.perturbParameter(
            idx,
            firstCurve.getParameter(idx),
            firstCurve.getParameterMetadata(idx)));
    int offset = firstCurve.getParameterCount();
    Curve newSecondCurve = secondCurve.withPerturbation(
        (idx, value, meta) -> perturbation.perturbParameter(
            idx + offset,
            secondCurve.getParameter(idx),
            secondCurve.getParameterMetadata(idx)));

    List<ParameterMetadata> newParamMeta = Stream.concat(
        IntStream.range(0, newFirstCurve.getParameterCount())
            .mapToObj(i -> newFirstCurve.getParameterMetadata(i)),
        IntStream.range(0, newSecondCurve.getParameterCount())
            .mapToObj(i -> newSecondCurve.getParameterMetadata(i)))
        .collect(toImmutableList());

    return CombinedMapCurve.of(
        newFirstCurve,
        newSecondCurve,
        mapping,
        metadata.withParameterMetadata(newParamMeta));
  }

  //-------------------------------------------------------------------------
  @Override
  public ImmutableList<Curve> split() {
    return ImmutableList.of(firstCurve, secondCurve);
  }

  @Override
  public CombinedMapCurve withUnderlyingCurve(int curveIndex, Curve curve) {
    if (curveIndex == 0) {
      return new CombinedMapCurve(curve, secondCurve, mapping, metadata);
    }
    if (curveIndex == 1) {
      return new CombinedMapCurve(firstCurve, curve, mapping, metadata);
    }
    throw new IllegalArgumentException("curveIndex is outside the range");
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code CombinedMapCurve}.
   * @return the meta-bean, not null
   */
  public static CombinedMapCurve.Meta meta() {
    return CombinedMapCurve.Meta.INSTANCE;
  }

  static {
    MetaBean.register(CombinedMapCurve.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param firstCurve  the value of the property, not null
   * @param secondCurve  the value of the property, not null
   * @param mapping  the value of the property, not null
   * @param metadata  the value of the property, not null
   * @return the instance
   */
  public static CombinedMapCurve of(
      Curve firstCurve,
      Curve secondCurve,
      Function<Triple<Double,Double,Double>, Double> mapping,
      CurveMetadata metadata) {
    return new CombinedMapCurve(
      firstCurve,
      secondCurve,
      mapping,
      metadata);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static CombinedMapCurve.Builder builder() {
    return new CombinedMapCurve.Builder();
  }

  private CombinedMapCurve(
      Curve firstCurve,
      Curve secondCurve,
      Function<Triple<Double,Double,Double>, Double> mapping,
      CurveMetadata metadata) {
    JodaBeanUtils.notNull(firstCurve, "firstCurve");
    JodaBeanUtils.notNull(secondCurve, "secondCurve");
    JodaBeanUtils.notNull(mapping, "mapping");
    JodaBeanUtils.notNull(metadata, "metadata");
    this.firstCurve = firstCurve;
    this.secondCurve = secondCurve;
    this.mapping = mapping;
    this.metadata = metadata;
  }

  @Override
  public CombinedMapCurve.Meta metaBean() {
    return CombinedMapCurve.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the first curve.
   * @return the value of the property, not null
   */
  public Curve getFirstCurve() {
    return firstCurve;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the second curve.
   * @return the value of the property, not null
   */
  public Curve getSecondCurve() {
    return secondCurve;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the mapping function. Map the x value and the y value of the first curve and second underlying curve to the y-value
   * of the global curve.
   * @return the value of the property, not null
   */
  public Function<Triple<Double,Double,Double>, Double> getMapping() {
    return mapping;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the curve metadata.
   * @return the value of the property, not null
   */
  @Override
  public CurveMetadata getMetadata() {
    return metadata;
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
      CombinedMapCurve other = (CombinedMapCurve) obj;
      return JodaBeanUtils.equal(firstCurve, other.firstCurve) &&
          JodaBeanUtils.equal(secondCurve, other.secondCurve) &&
          JodaBeanUtils.equal(mapping, other.mapping) &&
          JodaBeanUtils.equal(metadata, other.metadata);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(firstCurve);
    hash = hash * 31 + JodaBeanUtils.hashCode(secondCurve);
    hash = hash * 31 + JodaBeanUtils.hashCode(mapping);
    hash = hash * 31 + JodaBeanUtils.hashCode(metadata);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("CombinedMapCurve{");
    buf.append("firstCurve").append('=').append(JodaBeanUtils.toString(firstCurve)).append(',').append(' ');
    buf.append("secondCurve").append('=').append(JodaBeanUtils.toString(secondCurve)).append(',').append(' ');
    buf.append("mapping").append('=').append(JodaBeanUtils.toString(mapping)).append(',').append(' ');
    buf.append("metadata").append('=').append(JodaBeanUtils.toString(metadata));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code CombinedMapCurve}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code firstCurve} property.
     */
    private final MetaProperty<Curve> firstCurve = DirectMetaProperty.ofImmutable(
        this, "firstCurve", CombinedMapCurve.class, Curve.class);
    /**
     * The meta-property for the {@code secondCurve} property.
     */
    private final MetaProperty<Curve> secondCurve = DirectMetaProperty.ofImmutable(
        this, "secondCurve", CombinedMapCurve.class, Curve.class);
    /**
     * The meta-property for the {@code mapping} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Function<Triple<Double,Double,Double>, Double>> mapping = DirectMetaProperty.ofImmutable(
        this, "mapping", CombinedMapCurve.class, (Class) Function.class);
    /**
     * The meta-property for the {@code metadata} property.
     */
    private final MetaProperty<CurveMetadata> metadata = DirectMetaProperty.ofImmutable(
        this, "metadata", CombinedMapCurve.class, CurveMetadata.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "firstCurve",
        "secondCurve",
        "mapping",
        "metadata");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -186618849:  // firstCurve
          return firstCurve;
        case 239629531:  // secondCurve
          return secondCurve;
        case 837556430:  // mapping
          return mapping;
        case -450004177:  // metadata
          return metadata;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public CombinedMapCurve.Builder builder() {
      return new CombinedMapCurve.Builder();
    }

    @Override
    public Class<? extends CombinedMapCurve> beanType() {
      return CombinedMapCurve.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code firstCurve} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Curve> firstCurve() {
      return firstCurve;
    }

    /**
     * The meta-property for the {@code secondCurve} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Curve> secondCurve() {
      return secondCurve;
    }

    /**
     * The meta-property for the {@code mapping} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Function<Triple<Double,Double,Double>, Double>> mapping() {
      return mapping;
    }

    /**
     * The meta-property for the {@code metadata} property.
     * @return the meta-property, not null
     */
    public MetaProperty<CurveMetadata> metadata() {
      return metadata;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -186618849:  // firstCurve
          return ((CombinedMapCurve) bean).getFirstCurve();
        case 239629531:  // secondCurve
          return ((CombinedMapCurve) bean).getSecondCurve();
        case 837556430:  // mapping
          return ((CombinedMapCurve) bean).getMapping();
        case -450004177:  // metadata
          return ((CombinedMapCurve) bean).getMetadata();
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
   * The bean-builder for {@code CombinedMapCurve}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<CombinedMapCurve> {

    private Curve firstCurve;
    private Curve secondCurve;
    private Function<Triple<Double,Double,Double>, Double> mapping;
    private CurveMetadata metadata;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(CombinedMapCurve beanToCopy) {
      this.firstCurve = beanToCopy.getFirstCurve();
      this.secondCurve = beanToCopy.getSecondCurve();
      this.mapping = beanToCopy.getMapping();
      this.metadata = beanToCopy.getMetadata();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -186618849:  // firstCurve
          return firstCurve;
        case 239629531:  // secondCurve
          return secondCurve;
        case 837556430:  // mapping
          return mapping;
        case -450004177:  // metadata
          return metadata;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -186618849:  // firstCurve
          this.firstCurve = (Curve) newValue;
          break;
        case 239629531:  // secondCurve
          this.secondCurve = (Curve) newValue;
          break;
        case 837556430:  // mapping
          this.mapping = (Function<Triple<Double,Double,Double>, Double>) newValue;
          break;
        case -450004177:  // metadata
          this.metadata = (CurveMetadata) newValue;
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
    public CombinedMapCurve build() {
      return new CombinedMapCurve(
          firstCurve,
          secondCurve,
          mapping,
          metadata);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the first curve.
     * @param firstCurve  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder firstCurve(Curve firstCurve) {
      JodaBeanUtils.notNull(firstCurve, "firstCurve");
      this.firstCurve = firstCurve;
      return this;
    }

    /**
     * Sets the second curve.
     * @param secondCurve  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder secondCurve(Curve secondCurve) {
      JodaBeanUtils.notNull(secondCurve, "secondCurve");
      this.secondCurve = secondCurve;
      return this;
    }

    /**
     * Sets the mapping function. Map the x value and the y value of the first curve and second underlying curve to the y-value
     * of the global curve.
     * @param mapping  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder mapping(Function<Triple<Double,Double,Double>, Double> mapping) {
      JodaBeanUtils.notNull(mapping, "mapping");
      this.mapping = mapping;
      return this;
    }

    /**
     * Sets the curve metadata.
     * @param metadata  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder metadata(CurveMetadata metadata) {
      JodaBeanUtils.notNull(metadata, "metadata");
      this.metadata = metadata;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("CombinedMapCurve.Builder{");
      buf.append("firstCurve").append('=').append(JodaBeanUtils.toString(firstCurve)).append(',').append(' ');
      buf.append("secondCurve").append('=').append(JodaBeanUtils.toString(secondCurve)).append(',').append(' ');
      buf.append("mapping").append('=').append(JodaBeanUtils.toString(mapping)).append(',').append(' ');
      buf.append("metadata").append('=').append(JodaBeanUtils.toString(metadata));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
