/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.node;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableDefaults;
import org.joda.beans.gen.ImmutablePreBuild;
import org.joda.beans.gen.PropertyDefinition;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.currency.FxRateProvider;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.data.FxRateId;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.MarketDataId;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.CurveNode;
import com.opengamma.strata.market.curve.CurveNodeDate;
import com.opengamma.strata.market.curve.CurveNodeDateOrder;
import com.opengamma.strata.market.curve.node.FxSwapCurveNode;
import com.opengamma.strata.market.param.DatedParameterMetadata;
import com.opengamma.strata.market.param.LabelDateParameterMetadata;
import com.opengamma.strata.market.param.TenorDateParameterMetadata;
import com.opengamma.strata.product.ResolvedTrade;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.fx.FxSwapTrade;
import com.opengamma.strata.product.fx.ResolvedFxSwapTrade;
import com.opengamma.strata.product.fx.type.FxSwapTemplate;

import java.util.List;
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
 * A curve node based on a FX swap for an overnight period. 
 * Specific node for Overnight and Tom/Next swaps.
 * 
 * @author Marc Henrard
 */
@BeanDefinition
public class FxSwapOnCurveNode
    implements CurveNode, ImmutableBean, Serializable {

  /**
   * The template for the FX Swap associated with this node.
   */
  @PropertyDefinition(validate = "notNull")
  private final FxSwapTemplate template;
  /**
   * The identifier used to obtain the spot FX rate market value, defaulted from the template.
   * This only needs to be specified if using multiple market data sources.
   */
  @PropertyDefinition(validate = "notNull")
  private final FxRateId fxRateId;
  /**
   * The identifier of the market data values which provides the FX forward points for the
   * near leg. For a ON node, this is ON and TN points; for a TN node, this is TN points. 
   * The points are subtracted to the spot rate.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<ObservableId> nearForwardPointsIds;
  /**
   * The identifier of the market data values which provides the FX forward points for the
   * far leg. For a ON node, this is TN points; for a TN node, this is empty. 
   * The points are subtracted to the spot rate.
   */
  @PropertyDefinition(get = "optional")
  private final ObservableId farForwardPointsId;
  /**
   * The label to use for the node, defaulted.
   * <p>
   * When building, this will default based on the far period if not specified.
   */
  @PropertyDefinition(validate = "notEmpty", overrideGet = true)
  private final String label;
  /**
   * The method by which the date of the node is calculated, defaulted to 'End'.
   */
  @PropertyDefinition
  private final CurveNodeDate date;
  /**
   * The date order rules, used to ensure that the dates in the curve are in order.
   * If not specified, this will default to {@link CurveNodeDateOrder#DEFAULT}.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final CurveNodeDateOrder dateOrder;

  //-------------------------------------------------------------------------
  /**
   * Returns a curve node for an Overnight FX Swap using the specified instrument template and keys.
   * <p>
   * A suitable default label will be created.
   *
   * @param template  the template used for building the instrument for the node
   * @param farForwardPointsId  the identifier of the FX points at the far date
   * @return a node whose instrument is built from the template using a market rate
   */
  public static FxSwapOnCurveNode ofOn(
      FxSwapTemplate template, 
      ObservableId onForwardPointsId, 
      ObservableId tnForwardPointsId) {
    return builder()
        .template(template)
        .nearForwardPointsIds(ImmutableList.of(onForwardPointsId, tnForwardPointsId))
        .farForwardPointsId(tnForwardPointsId)
        .build();
  }
  
  /**
   * Returns a curve node for an Tom/NextFX Swap using the specified instrument template and keys.
   * <p>
   * A suitable default label will be created.
   *
   * @param template  the template used for building the instrument for the node
   * @param farForwardPointsId  the identifier of the FX points at the far date
   * @return a node whose instrument is built from the template using a market rate
   */
  public static FxSwapOnCurveNode ofTn(FxSwapTemplate template, ObservableId tnForwardPointsId) {
    return builder()
        .template(template)
        .nearForwardPointsIds(ImmutableList.of(tnForwardPointsId))
        .build();
  }

  @ImmutableDefaults
  private static void applyDefaults(Builder builder) {
    builder.date = CurveNodeDate.END;
    builder.dateOrder = CurveNodeDateOrder.DEFAULT;
  }

  @ImmutablePreBuild
  private static void preBuild(Builder builder) {
    if (builder.template != null) {
      if (builder.label == null) {
        builder.label = Tenor.of(builder.template.getPeriodToFar()).toString();
      }
      if (builder.fxRateId == null) {
        builder.fxRateId = FxRateId.of(builder.template.getCurrencyPair());
      } else {
        ArgChecker.isTrue(
            builder.fxRateId.getPair().toConventional().equals(builder.template.getCurrencyPair().toConventional()),
            "FxRateId currency pair '{}' must match that of the template '{}'",
            builder.fxRateId.getPair(),
            builder.template.getCurrencyPair());
      }
    }
  }

  //-------------------------------------------------------------------------
  
  @Override
  public Set<? extends MarketDataId<?>> requirements() {
    if(farForwardPointsId == null) { // TN
      return ImmutableSet.of(fxRateId, nearForwardPointsIds.get(0));
    }
    return ImmutableSet.of( // ON
        fxRateId, 
        nearForwardPointsIds.get(0), 
        nearForwardPointsIds.get(1),
        farForwardPointsId);
  }

  @Override
  public LocalDate date(LocalDate valuationDate, ReferenceData refData) {
    return date.calculate(
        () -> calculateEnd(valuationDate, refData),
        () -> calculateLastFixingDate(valuationDate, refData));
  }

  // calculate the end date
  private LocalDate calculateEnd(LocalDate valuationDate, ReferenceData refData) {
    FxSwapTrade trade = template.createTrade(valuationDate, BuySell.BUY, 1, 1, 0, refData);
    return trade.getProduct().getFarLeg().resolve(refData).getPaymentDate();
  }

  // calculate the last fixing date
  private LocalDate calculateLastFixingDate(LocalDate valuationDate, ReferenceData refData) {
    throw new UnsupportedOperationException("Node date of 'LastFixing' is not supported for FxSwap");
  }

  @Override
  public DatedParameterMetadata metadata(LocalDate valuationDate, ReferenceData refData) {
    LocalDate nodeDate = date(valuationDate, refData);
    if (date.isFixed()) {
      return LabelDateParameterMetadata.of(nodeDate, label);
    }
    Tenor tenor = Tenor.of(template.getPeriodToFar());
    return TenorDateParameterMetadata.of(nodeDate, tenor, label);
  }

  @Override
  public FxSwapTrade trade(double quantity, MarketData marketData, ReferenceData refData) {
    FxRate spotFxRate = marketData.getValue(fxRateId);
    double spotRate = spotFxRate.fxRate(template.getCurrencyPair());
    double nearFxPts = 0.0;
    for (ObservableId pt : nearForwardPointsIds) {
      nearFxPts += marketData.getValue(pt);
    }
    double nearFxRate = spotRate - nearFxPts;
    double farFxPts = (farForwardPointsId == null) ? nearFxPts : marketData.getValue(farForwardPointsId);
    BuySell buySell = quantity > 0 ? BuySell.BUY : BuySell.SELL;
    return template.createTrade(marketData.getValuationDate(), buySell, Math.abs(quantity), nearFxRate, farFxPts, refData);
  }

  @Override
  public ResolvedTrade resolvedTrade(double quantity, MarketData marketData, ReferenceData refData) {
    return trade(quantity, marketData, refData).resolve(refData);
  }

  @Override
  public ResolvedFxSwapTrade sampleResolvedTrade(
      LocalDate valuationDate,
      FxRateProvider fxProvider,
      ReferenceData refData) {

    double rate = fxProvider.fxRate(template.getCurrencyPair());
    FxSwapTrade trade = template.createTrade(valuationDate, BuySell.BUY, 1d, rate, 0d, refData);
    return trade.resolve(refData);
  }

  @Override
  public double initialGuess(MarketData marketData, ValueType valueType) {
    if (ValueType.DISCOUNT_FACTOR.equals(valueType)) {
      return 1d;
    }
    return 0d;
  }  

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code FxSwapOnCurveNode}.
   * @return the meta-bean, not null
   */
  public static FxSwapOnCurveNode.Meta meta() {
    return FxSwapOnCurveNode.Meta.INSTANCE;
  }

  static {
    MetaBean.register(FxSwapOnCurveNode.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static FxSwapOnCurveNode.Builder builder() {
    return new FxSwapOnCurveNode.Builder();
  }

  /**
   * Restricted constructor.
   * @param builder  the builder to copy from, not null
   */
  protected FxSwapOnCurveNode(FxSwapOnCurveNode.Builder builder) {
    JodaBeanUtils.notNull(builder.template, "template");
    JodaBeanUtils.notNull(builder.fxRateId, "fxRateId");
    JodaBeanUtils.notNull(builder.nearForwardPointsIds, "nearForwardPointsIds");
    JodaBeanUtils.notEmpty(builder.label, "label");
    JodaBeanUtils.notNull(builder.dateOrder, "dateOrder");
    this.template = builder.template;
    this.fxRateId = builder.fxRateId;
    this.nearForwardPointsIds = ImmutableList.copyOf(builder.nearForwardPointsIds);
    this.farForwardPointsId = builder.farForwardPointsId;
    this.label = builder.label;
    this.date = builder.date;
    this.dateOrder = builder.dateOrder;
  }

  @Override
  public FxSwapOnCurveNode.Meta metaBean() {
    return FxSwapOnCurveNode.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the template for the FX Swap associated with this node.
   * @return the value of the property, not null
   */
  public FxSwapTemplate getTemplate() {
    return template;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the identifier used to obtain the spot FX rate market value, defaulted from the template.
   * This only needs to be specified if using multiple market data sources.
   * @return the value of the property, not null
   */
  public FxRateId getFxRateId() {
    return fxRateId;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the identifier of the market data values which provides the FX forward points for the
   * near leg. For a ON node, this is ON and TN points; for a TN node, this is TN points.
   * The points are subtracted to the spot rate.
   * @return the value of the property, not null
   */
  public ImmutableList<ObservableId> getNearForwardPointsIds() {
    return nearForwardPointsIds;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the identifier of the market data values which provides the FX forward points for the
   * far leg. For a ON node, this is TN points; for a TN node, this is empty.
   * The points are subtracted to the spot rate.
   * @return the optional value of the property, not null
   */
  public Optional<ObservableId> getFarForwardPointsId() {
    return Optional.ofNullable(farForwardPointsId);
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the label to use for the node, defaulted.
   * <p>
   * When building, this will default based on the far period if not specified.
   * @return the value of the property, not empty
   */
  @Override
  public String getLabel() {
    return label;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the method by which the date of the node is calculated, defaulted to 'End'.
   * @return the value of the property
   */
  public CurveNodeDate getDate() {
    return date;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the date order rules, used to ensure that the dates in the curve are in order.
   * If not specified, this will default to {@link CurveNodeDateOrder#DEFAULT}.
   * @return the value of the property, not null
   */
  @Override
  public CurveNodeDateOrder getDateOrder() {
    return dateOrder;
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
      FxSwapOnCurveNode other = (FxSwapOnCurveNode) obj;
      return JodaBeanUtils.equal(template, other.template) &&
          JodaBeanUtils.equal(fxRateId, other.fxRateId) &&
          JodaBeanUtils.equal(nearForwardPointsIds, other.nearForwardPointsIds) &&
          JodaBeanUtils.equal(farForwardPointsId, other.farForwardPointsId) &&
          JodaBeanUtils.equal(label, other.label) &&
          JodaBeanUtils.equal(date, other.date) &&
          JodaBeanUtils.equal(dateOrder, other.dateOrder);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(template);
    hash = hash * 31 + JodaBeanUtils.hashCode(fxRateId);
    hash = hash * 31 + JodaBeanUtils.hashCode(nearForwardPointsIds);
    hash = hash * 31 + JodaBeanUtils.hashCode(farForwardPointsId);
    hash = hash * 31 + JodaBeanUtils.hashCode(label);
    hash = hash * 31 + JodaBeanUtils.hashCode(date);
    hash = hash * 31 + JodaBeanUtils.hashCode(dateOrder);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(256);
    buf.append("FxSwapOnCurveNode{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("template").append('=').append(JodaBeanUtils.toString(template)).append(',').append(' ');
    buf.append("fxRateId").append('=').append(JodaBeanUtils.toString(fxRateId)).append(',').append(' ');
    buf.append("nearForwardPointsIds").append('=').append(JodaBeanUtils.toString(nearForwardPointsIds)).append(',').append(' ');
    buf.append("farForwardPointsId").append('=').append(JodaBeanUtils.toString(farForwardPointsId)).append(',').append(' ');
    buf.append("label").append('=').append(JodaBeanUtils.toString(label)).append(',').append(' ');
    buf.append("date").append('=').append(JodaBeanUtils.toString(date)).append(',').append(' ');
    buf.append("dateOrder").append('=').append(JodaBeanUtils.toString(dateOrder)).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code FxSwapOnCurveNode}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code template} property.
     */
    private final MetaProperty<FxSwapTemplate> template = DirectMetaProperty.ofImmutable(
        this, "template", FxSwapOnCurveNode.class, FxSwapTemplate.class);
    /**
     * The meta-property for the {@code fxRateId} property.
     */
    private final MetaProperty<FxRateId> fxRateId = DirectMetaProperty.ofImmutable(
        this, "fxRateId", FxSwapOnCurveNode.class, FxRateId.class);
    /**
     * The meta-property for the {@code nearForwardPointsIds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<ObservableId>> nearForwardPointsIds = DirectMetaProperty.ofImmutable(
        this, "nearForwardPointsIds", FxSwapOnCurveNode.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code farForwardPointsId} property.
     */
    private final MetaProperty<ObservableId> farForwardPointsId = DirectMetaProperty.ofImmutable(
        this, "farForwardPointsId", FxSwapOnCurveNode.class, ObservableId.class);
    /**
     * The meta-property for the {@code label} property.
     */
    private final MetaProperty<String> label = DirectMetaProperty.ofImmutable(
        this, "label", FxSwapOnCurveNode.class, String.class);
    /**
     * The meta-property for the {@code date} property.
     */
    private final MetaProperty<CurveNodeDate> date = DirectMetaProperty.ofImmutable(
        this, "date", FxSwapOnCurveNode.class, CurveNodeDate.class);
    /**
     * The meta-property for the {@code dateOrder} property.
     */
    private final MetaProperty<CurveNodeDateOrder> dateOrder = DirectMetaProperty.ofImmutable(
        this, "dateOrder", FxSwapOnCurveNode.class, CurveNodeDateOrder.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "template",
        "fxRateId",
        "nearForwardPointsIds",
        "farForwardPointsId",
        "label",
        "date",
        "dateOrder");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          return template;
        case -1054985843:  // fxRateId
          return fxRateId;
        case -1544042024:  // nearForwardPointsIds
          return nearForwardPointsIds;
        case -566044884:  // farForwardPointsId
          return farForwardPointsId;
        case 102727412:  // label
          return label;
        case 3076014:  // date
          return date;
        case -263699392:  // dateOrder
          return dateOrder;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public FxSwapOnCurveNode.Builder builder() {
      return new FxSwapOnCurveNode.Builder();
    }

    @Override
    public Class<? extends FxSwapOnCurveNode> beanType() {
      return FxSwapOnCurveNode.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code template} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<FxSwapTemplate> template() {
      return template;
    }

    /**
     * The meta-property for the {@code fxRateId} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<FxRateId> fxRateId() {
      return fxRateId;
    }

    /**
     * The meta-property for the {@code nearForwardPointsIds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<ObservableId>> nearForwardPointsIds() {
      return nearForwardPointsIds;
    }

    /**
     * The meta-property for the {@code farForwardPointsId} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ObservableId> farForwardPointsId() {
      return farForwardPointsId;
    }

    /**
     * The meta-property for the {@code label} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<String> label() {
      return label;
    }

    /**
     * The meta-property for the {@code date} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<CurveNodeDate> date() {
      return date;
    }

    /**
     * The meta-property for the {@code dateOrder} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<CurveNodeDateOrder> dateOrder() {
      return dateOrder;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          return ((FxSwapOnCurveNode) bean).getTemplate();
        case -1054985843:  // fxRateId
          return ((FxSwapOnCurveNode) bean).getFxRateId();
        case -1544042024:  // nearForwardPointsIds
          return ((FxSwapOnCurveNode) bean).getNearForwardPointsIds();
        case -566044884:  // farForwardPointsId
          return ((FxSwapOnCurveNode) bean).farForwardPointsId;
        case 102727412:  // label
          return ((FxSwapOnCurveNode) bean).getLabel();
        case 3076014:  // date
          return ((FxSwapOnCurveNode) bean).getDate();
        case -263699392:  // dateOrder
          return ((FxSwapOnCurveNode) bean).getDateOrder();
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
   * The bean-builder for {@code FxSwapOnCurveNode}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<FxSwapOnCurveNode> {

    private FxSwapTemplate template;
    private FxRateId fxRateId;
    private List<ObservableId> nearForwardPointsIds = ImmutableList.of();
    private ObservableId farForwardPointsId;
    private String label;
    private CurveNodeDate date;
    private CurveNodeDateOrder dateOrder;

    /**
     * Restricted constructor.
     */
    protected Builder() {
      applyDefaults(this);
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(FxSwapOnCurveNode beanToCopy) {
      this.template = beanToCopy.getTemplate();
      this.fxRateId = beanToCopy.getFxRateId();
      this.nearForwardPointsIds = beanToCopy.getNearForwardPointsIds();
      this.farForwardPointsId = beanToCopy.farForwardPointsId;
      this.label = beanToCopy.getLabel();
      this.date = beanToCopy.getDate();
      this.dateOrder = beanToCopy.getDateOrder();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          return template;
        case -1054985843:  // fxRateId
          return fxRateId;
        case -1544042024:  // nearForwardPointsIds
          return nearForwardPointsIds;
        case -566044884:  // farForwardPointsId
          return farForwardPointsId;
        case 102727412:  // label
          return label;
        case 3076014:  // date
          return date;
        case -263699392:  // dateOrder
          return dateOrder;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          this.template = (FxSwapTemplate) newValue;
          break;
        case -1054985843:  // fxRateId
          this.fxRateId = (FxRateId) newValue;
          break;
        case -1544042024:  // nearForwardPointsIds
          this.nearForwardPointsIds = (List<ObservableId>) newValue;
          break;
        case -566044884:  // farForwardPointsId
          this.farForwardPointsId = (ObservableId) newValue;
          break;
        case 102727412:  // label
          this.label = (String) newValue;
          break;
        case 3076014:  // date
          this.date = (CurveNodeDate) newValue;
          break;
        case -263699392:  // dateOrder
          this.dateOrder = (CurveNodeDateOrder) newValue;
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
    public FxSwapOnCurveNode build() {
      preBuild(this);
      return new FxSwapOnCurveNode(this);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the template for the FX Swap associated with this node.
     * @param template  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder template(FxSwapTemplate template) {
      JodaBeanUtils.notNull(template, "template");
      this.template = template;
      return this;
    }

    /**
     * Sets the identifier used to obtain the spot FX rate market value, defaulted from the template.
     * This only needs to be specified if using multiple market data sources.
     * @param fxRateId  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder fxRateId(FxRateId fxRateId) {
      JodaBeanUtils.notNull(fxRateId, "fxRateId");
      this.fxRateId = fxRateId;
      return this;
    }

    /**
     * Sets the identifier of the market data values which provides the FX forward points for the
     * near leg. For a ON node, this is ON and TN points; for a TN node, this is TN points.
     * The points are subtracted to the spot rate.
     * @param nearForwardPointsIds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder nearForwardPointsIds(List<ObservableId> nearForwardPointsIds) {
      JodaBeanUtils.notNull(nearForwardPointsIds, "nearForwardPointsIds");
      this.nearForwardPointsIds = nearForwardPointsIds;
      return this;
    }

    /**
     * Sets the {@code nearForwardPointsIds} property in the builder
     * from an array of objects.
     * @param nearForwardPointsIds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder nearForwardPointsIds(ObservableId... nearForwardPointsIds) {
      return nearForwardPointsIds(ImmutableList.copyOf(nearForwardPointsIds));
    }

    /**
     * Sets the identifier of the market data values which provides the FX forward points for the
     * far leg. For a ON node, this is TN points; for a TN node, this is empty.
     * The points are subtracted to the spot rate.
     * @param farForwardPointsId  the new value
     * @return this, for chaining, not null
     */
    public Builder farForwardPointsId(ObservableId farForwardPointsId) {
      this.farForwardPointsId = farForwardPointsId;
      return this;
    }

    /**
     * Sets the label to use for the node, defaulted.
     * <p>
     * When building, this will default based on the far period if not specified.
     * @param label  the new value, not empty
     * @return this, for chaining, not null
     */
    public Builder label(String label) {
      JodaBeanUtils.notEmpty(label, "label");
      this.label = label;
      return this;
    }

    /**
     * Sets the method by which the date of the node is calculated, defaulted to 'End'.
     * @param date  the new value
     * @return this, for chaining, not null
     */
    public Builder date(CurveNodeDate date) {
      this.date = date;
      return this;
    }

    /**
     * Sets the date order rules, used to ensure that the dates in the curve are in order.
     * If not specified, this will default to {@link CurveNodeDateOrder#DEFAULT}.
     * @param dateOrder  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dateOrder(CurveNodeDateOrder dateOrder) {
      JodaBeanUtils.notNull(dateOrder, "dateOrder");
      this.dateOrder = dateOrder;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(256);
      buf.append("FxSwapOnCurveNode.Builder{");
      int len = buf.length();
      toString(buf);
      if (buf.length() > len) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }

    protected void toString(StringBuilder buf) {
      buf.append("template").append('=').append(JodaBeanUtils.toString(template)).append(',').append(' ');
      buf.append("fxRateId").append('=').append(JodaBeanUtils.toString(fxRateId)).append(',').append(' ');
      buf.append("nearForwardPointsIds").append('=').append(JodaBeanUtils.toString(nearForwardPointsIds)).append(',').append(' ');
      buf.append("farForwardPointsId").append('=').append(JodaBeanUtils.toString(farForwardPointsId)).append(',').append(' ');
      buf.append("label").append('=').append(JodaBeanUtils.toString(label)).append(',').append(' ');
      buf.append("date").append('=').append(JodaBeanUtils.toString(date)).append(',').append(' ');
      buf.append("dateOrder").append('=').append(JodaBeanUtils.toString(dateOrder)).append(',').append(' ');
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
