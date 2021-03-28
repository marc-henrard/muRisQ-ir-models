/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloor;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.ResolvedSwaptionTrade;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;

/**
 * Price of swaption trades in a single currency model.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelSwaptionPhysicalTradePricer {
  
  /** The pricer for the {@link ResolvedIborCapFloor}. */
  private final SingleCurrencyModelSwaptionPhysicalProductPricer swaptionProductPricer;
  /** Pricer for {@link Payment}. */
  private final DiscountingPaymentPricer paymentPricer;

  /**
   * Creates and instance of the trade pricer.
   * 
   * @param swaptionProductPricer  the pricer for the {@link ResolvedSwaption}
   * @param paymentPricer  the pricer for the premium
   */
  public SingleCurrencyModelSwaptionPhysicalTradePricer(
      SingleCurrencyModelSwaptionPhysicalProductPricer swaptionProductPricer,
      DiscountingPaymentPricer paymentPricer) {
    this.swaptionProductPricer = ArgChecker.notNull(swaptionProductPricer, "product pricer");;
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "payment pricer");
  }
  
  /**
   * Returns the underlying product pricer.
   * 
   * @return the pricer
   */
  public SingleCurrencyModelSwaptionPhysicalProductPricer getProductPricer() {
    return swaptionProductPricer;
  }
  
  /**
   * Returns the underlying payment pricer.
   * 
   * @return the pricer
   */
  public DiscountingPaymentPricer getPaymentPricer() {
    return paymentPricer;
  }
  
  /**
   * Calculates the present value of the Ibor cap/floor trade.
   * <p>
   * The result is expressed using the currency of each leg and the premium.
   * 
   * @param trade  the swaption trade
   * @param multicurve  the rates provider
   * @param model  the model parameters
   * @return the present value
   */
  public CurrencyAmount presentValue(
      ResolvedSwaptionTrade trade,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    CurrencyAmount pvProduct = 
        swaptionProductPricer.presentValue(trade.getProduct(), multicurve, model);
    CurrencyAmount pvPremium = paymentPricer.presentValue(trade.getPremium(), multicurve);
    return pvProduct.plus(pvPremium);
  }

}
