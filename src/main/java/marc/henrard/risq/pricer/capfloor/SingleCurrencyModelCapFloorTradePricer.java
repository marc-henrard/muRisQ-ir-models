/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloor;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorTrade;

import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;

/**
 * Price of cap/floor trades in a multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelCapFloorTradePricer {
  
  /** The pricer for the {@link ResolvedIborCapFloor}. */
  private final SingleCurrencyModelCapFloorProductPricer capFloorProductPricer;
  /** Pricer for {@link Payment}. */
  private final DiscountingPaymentPricer paymentPricer;

  /**
   * Creates and instance of the trade pricer.
   * 
   * @param capFloorProductPricer  the pricer for the {@link ResolvedIborCapFloor}
   * @param paymentPricer  the pricer for the premium
   */
  public SingleCurrencyModelCapFloorTradePricer(
      SingleCurrencyModelCapFloorProductPricer capFloorProductPricer,
      DiscountingPaymentPricer paymentPricer) {
    this.capFloorProductPricer = ArgChecker.notNull(capFloorProductPricer, "product pricer");;
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "payment pricer");
  }
  
  /**
   * Returns the underlying product pricer.
   * 
   * @return the pricer
   */
  public SingleCurrencyModelCapFloorProductPricer getProductPricer() {
    return capFloorProductPricer;
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
   * @param trade  the Ibor cap/floor trade
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the present value
   */
  public MultiCurrencyAmount presentValue(
      ResolvedIborCapFloorTrade trade,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    MultiCurrencyAmount pvProduct = 
        capFloorProductPricer.presentValue(trade.getProduct(), multicurve, model);
    if (!trade.getPremium().isPresent()) {
      return pvProduct;
    }
    CurrencyAmount pvPremium = paymentPricer.presentValue(trade.getPremium().get(), multicurve);
    return pvProduct.plus(pvPremium);
  }
  

}
