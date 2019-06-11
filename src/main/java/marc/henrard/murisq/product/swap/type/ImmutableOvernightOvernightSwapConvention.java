/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.joda.beans.Bean;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableValidator;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapLeg;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.OvernightRateSwapLegConvention;

/**
 * A market convention for Overnight-Overnight swap trades.
 * <p>
 * This defines the market convention for a Overnight-Overnight single currency swap.
 * The convention is formed by combining two swap leg conventions in the same currency.
 * <p>
 * The convention defines the relation between dates related to the trade.
 * <ul>
 * <li>Trade date, the date that the trade is agreed
 * <li>Spot date, the base for date calculations, typically 2 business days after the trade date
 * <li>Start date, the date on which the interest calculation starts, often the same as the spot date
 * <li>End date, the date on which the interest calculation ends, typically a number of years after the start date
 * </ul>
 */
@BeanDefinition
public final class ImmutableOvernightOvernightSwapConvention
    implements OvernightOvernightSwapConvention, ImmutableBean, Serializable {

  /**
   * The convention name, such as 'USD-SOFR-3M-FED-FUND-3M'.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final String name;
  /**
   * The market convention of the first overnight leg.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final OvernightRateSwapLegConvention overnightLeg1;
  /**
   * The market convention of the second overnight leg.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final OvernightRateSwapLegConvention overnightLeg2;
  /**
   * The offset of the spot value date from the trade date.
   * <p>
   * The offset is applied to the trade date to find the start date.
   * A typical value is "plus 2 business days".
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final DaysAdjustment spotDateOffset;

  //-------------------------------------------------------------------------

  /**
   * Obtains a convention based on the specified name and leg conventions.
   * <p>
   * The two leg conventions must be in the same currency.
   * 
   * @param name  the unique name of the convention 
   * @param overnightLeg1  the market convention for the first overnight leg
   * @param overnightLeg2  the market convention for the second overnight leg
   * @param spotDateOffset  the offset of the spot value date from the trade date
   * @return the convention
   */
  public static ImmutableOvernightOvernightSwapConvention of(
      String name,
      OvernightRateSwapLegConvention overnightLeg1,
      OvernightRateSwapLegConvention overnightLeg2,
      DaysAdjustment spotDateOffset) {

    return new ImmutableOvernightOvernightSwapConvention(name, overnightLeg1, overnightLeg2, spotDateOffset);
  }

  //-------------------------------------------------------------------------
  @ImmutableValidator
  private void validate() {
    ArgChecker.isTrue(overnightLeg1.getCurrency().equals(overnightLeg1.getCurrency()),
        "Conventions must have same currency");
  }

  //-------------------------------------------------------------------------
  @Override
  public SwapTrade toTrade(
      TradeInfo tradeInfo,
      LocalDate startDate,
      LocalDate endDate,
      BuySell buySell,
      double notional,
      double spread) {

    Optional<LocalDate> tradeDate = tradeInfo.getTradeDate();
    if (tradeDate.isPresent()) {
      ArgChecker.inOrderOrEqual(tradeDate.get(), startDate, "tradeDate", "startDate");
    }
    SwapLeg leg1 = overnightLeg1.toLeg(startDate, endDate, PayReceive.ofPay(buySell.isBuy()), notional, spread);
    SwapLeg leg2 = overnightLeg2.toLeg(startDate, endDate, PayReceive.ofPay(buySell.isSell()), notional);
    return SwapTrade.builder()
        .info(tradeInfo)
        .product(Swap.of(leg1, leg2))
        .build();
  }

  //-------------------------------------------------------------------------
  @Override
  public String toString() {
    return getName();
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code ImmutableOvernightOvernightSwapConvention}.
   * @return the meta-bean, not null
   */
  public static ImmutableOvernightOvernightSwapConvention.Meta meta() {
    return ImmutableOvernightOvernightSwapConvention.Meta.INSTANCE;
  }

  static {
    MetaBean.register(ImmutableOvernightOvernightSwapConvention.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static ImmutableOvernightOvernightSwapConvention.Builder builder() {
    return new ImmutableOvernightOvernightSwapConvention.Builder();
  }

  private ImmutableOvernightOvernightSwapConvention(
      String name,
      OvernightRateSwapLegConvention overnightLeg1,
      OvernightRateSwapLegConvention overnightLeg2,
      DaysAdjustment spotDateOffset) {
    JodaBeanUtils.notNull(name, "name");
    JodaBeanUtils.notNull(overnightLeg1, "overnightLeg1");
    JodaBeanUtils.notNull(overnightLeg2, "overnightLeg2");
    JodaBeanUtils.notNull(spotDateOffset, "spotDateOffset");
    this.name = name;
    this.overnightLeg1 = overnightLeg1;
    this.overnightLeg2 = overnightLeg2;
    this.spotDateOffset = spotDateOffset;
    validate();
  }

  @Override
  public ImmutableOvernightOvernightSwapConvention.Meta metaBean() {
    return ImmutableOvernightOvernightSwapConvention.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the convention name, such as 'USD-SOFR-3M-FED-FUND-3M'.
   * @return the value of the property, not null
   */
  @Override
  public String getName() {
    return name;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the market convention of the first overnight leg.
   * @return the value of the property, not null
   */
  @Override
  public OvernightRateSwapLegConvention getOvernightLeg1() {
    return overnightLeg1;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the market convention of the second overnight leg.
   * @return the value of the property, not null
   */
  @Override
  public OvernightRateSwapLegConvention getOvernightLeg2() {
    return overnightLeg2;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the offset of the spot value date from the trade date.
   * <p>
   * The offset is applied to the trade date to find the start date.
   * A typical value is "plus 2 business days".
   * @return the value of the property, not null
   */
  @Override
  public DaysAdjustment getSpotDateOffset() {
    return spotDateOffset;
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
      ImmutableOvernightOvernightSwapConvention other = (ImmutableOvernightOvernightSwapConvention) obj;
      return JodaBeanUtils.equal(name, other.name) &&
          JodaBeanUtils.equal(overnightLeg1, other.overnightLeg1) &&
          JodaBeanUtils.equal(overnightLeg2, other.overnightLeg2) &&
          JodaBeanUtils.equal(spotDateOffset, other.spotDateOffset);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(name);
    hash = hash * 31 + JodaBeanUtils.hashCode(overnightLeg1);
    hash = hash * 31 + JodaBeanUtils.hashCode(overnightLeg2);
    hash = hash * 31 + JodaBeanUtils.hashCode(spotDateOffset);
    return hash;
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutableOvernightOvernightSwapConvention}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code name} property.
     */
    private final MetaProperty<String> name = DirectMetaProperty.ofImmutable(
        this, "name", ImmutableOvernightOvernightSwapConvention.class, String.class);
    /**
     * The meta-property for the {@code overnightLeg1} property.
     */
    private final MetaProperty<OvernightRateSwapLegConvention> overnightLeg1 = DirectMetaProperty.ofImmutable(
        this, "overnightLeg1", ImmutableOvernightOvernightSwapConvention.class, OvernightRateSwapLegConvention.class);
    /**
     * The meta-property for the {@code overnightLeg2} property.
     */
    private final MetaProperty<OvernightRateSwapLegConvention> overnightLeg2 = DirectMetaProperty.ofImmutable(
        this, "overnightLeg2", ImmutableOvernightOvernightSwapConvention.class, OvernightRateSwapLegConvention.class);
    /**
     * The meta-property for the {@code spotDateOffset} property.
     */
    private final MetaProperty<DaysAdjustment> spotDateOffset = DirectMetaProperty.ofImmutable(
        this, "spotDateOffset", ImmutableOvernightOvernightSwapConvention.class, DaysAdjustment.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "name",
        "overnightLeg1",
        "overnightLeg2",
        "spotDateOffset");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return name;
        case -821781049:  // overnightLeg1
          return overnightLeg1;
        case -821781048:  // overnightLeg2
          return overnightLeg2;
        case 746995843:  // spotDateOffset
          return spotDateOffset;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public ImmutableOvernightOvernightSwapConvention.Builder builder() {
      return new ImmutableOvernightOvernightSwapConvention.Builder();
    }

    @Override
    public Class<? extends ImmutableOvernightOvernightSwapConvention> beanType() {
      return ImmutableOvernightOvernightSwapConvention.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code name} property.
     * @return the meta-property, not null
     */
    public MetaProperty<String> name() {
      return name;
    }

    /**
     * The meta-property for the {@code overnightLeg1} property.
     * @return the meta-property, not null
     */
    public MetaProperty<OvernightRateSwapLegConvention> overnightLeg1() {
      return overnightLeg1;
    }

    /**
     * The meta-property for the {@code overnightLeg2} property.
     * @return the meta-property, not null
     */
    public MetaProperty<OvernightRateSwapLegConvention> overnightLeg2() {
      return overnightLeg2;
    }

    /**
     * The meta-property for the {@code spotDateOffset} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DaysAdjustment> spotDateOffset() {
      return spotDateOffset;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return ((ImmutableOvernightOvernightSwapConvention) bean).getName();
        case -821781049:  // overnightLeg1
          return ((ImmutableOvernightOvernightSwapConvention) bean).getOvernightLeg1();
        case -821781048:  // overnightLeg2
          return ((ImmutableOvernightOvernightSwapConvention) bean).getOvernightLeg2();
        case 746995843:  // spotDateOffset
          return ((ImmutableOvernightOvernightSwapConvention) bean).getSpotDateOffset();
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
   * The bean-builder for {@code ImmutableOvernightOvernightSwapConvention}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<ImmutableOvernightOvernightSwapConvention> {

    private String name;
    private OvernightRateSwapLegConvention overnightLeg1;
    private OvernightRateSwapLegConvention overnightLeg2;
    private DaysAdjustment spotDateOffset;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(ImmutableOvernightOvernightSwapConvention beanToCopy) {
      this.name = beanToCopy.getName();
      this.overnightLeg1 = beanToCopy.getOvernightLeg1();
      this.overnightLeg2 = beanToCopy.getOvernightLeg2();
      this.spotDateOffset = beanToCopy.getSpotDateOffset();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return name;
        case -821781049:  // overnightLeg1
          return overnightLeg1;
        case -821781048:  // overnightLeg2
          return overnightLeg2;
        case 746995843:  // spotDateOffset
          return spotDateOffset;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          this.name = (String) newValue;
          break;
        case -821781049:  // overnightLeg1
          this.overnightLeg1 = (OvernightRateSwapLegConvention) newValue;
          break;
        case -821781048:  // overnightLeg2
          this.overnightLeg2 = (OvernightRateSwapLegConvention) newValue;
          break;
        case 746995843:  // spotDateOffset
          this.spotDateOffset = (DaysAdjustment) newValue;
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
    public ImmutableOvernightOvernightSwapConvention build() {
      return new ImmutableOvernightOvernightSwapConvention(
          name,
          overnightLeg1,
          overnightLeg2,
          spotDateOffset);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the convention name, such as 'USD-SOFR-3M-FED-FUND-3M'.
     * @param name  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder name(String name) {
      JodaBeanUtils.notNull(name, "name");
      this.name = name;
      return this;
    }

    /**
     * Sets the market convention of the first overnight leg.
     * @param overnightLeg1  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder overnightLeg1(OvernightRateSwapLegConvention overnightLeg1) {
      JodaBeanUtils.notNull(overnightLeg1, "overnightLeg1");
      this.overnightLeg1 = overnightLeg1;
      return this;
    }

    /**
     * Sets the market convention of the second overnight leg.
     * @param overnightLeg2  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder overnightLeg2(OvernightRateSwapLegConvention overnightLeg2) {
      JodaBeanUtils.notNull(overnightLeg2, "overnightLeg2");
      this.overnightLeg2 = overnightLeg2;
      return this;
    }

    /**
     * Sets the offset of the spot value date from the trade date.
     * <p>
     * The offset is applied to the trade date to find the start date.
     * A typical value is "plus 2 business days".
     * @param spotDateOffset  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder spotDateOffset(DaysAdjustment spotDateOffset) {
      JodaBeanUtils.notNull(spotDateOffset, "spotDateOffset");
      this.spotDateOffset = spotDateOffset;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("ImmutableOvernightOvernightSwapConvention.Builder{");
      buf.append("name").append('=').append(JodaBeanUtils.toString(name)).append(',').append(' ');
      buf.append("overnightLeg1").append('=').append(JodaBeanUtils.toString(overnightLeg1)).append(',').append(' ');
      buf.append("overnightLeg2").append('=').append(JodaBeanUtils.toString(overnightLeg2)).append(',').append(' ');
      buf.append("spotDateOffset").append('=').append(JodaBeanUtils.toString(spotDateOffset));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
