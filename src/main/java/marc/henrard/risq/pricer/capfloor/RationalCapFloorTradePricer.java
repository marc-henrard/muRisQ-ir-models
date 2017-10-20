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

import marc.henrard.risq.model.rationalmulticurve.RationalParameters;

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
public class RationalCapFloorTradePricer {
  
  /** The pricer for the {@link ResolvedIborCapFloor}. */
  private final RationalCapFloorProductPricer capFloorProductPricer;
  /** Pricer for {@link Payment}. */
  private final DiscountingPaymentPricer paymentPricer;

  public RationalCapFloorTradePricer(
      RationalCapFloorProductPricer capFloorProductPricer,
      DiscountingPaymentPricer paymentPricer) {
    this.capFloorProductPricer = ArgChecker.notNull(capFloorProductPricer, "product pricer");;
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "payment pricer");
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
      RationalParameters model) {

    MultiCurrencyAmount pvProduct = 
        capFloorProductPricer.presentValue(trade.getProduct(), multicurve, model);
    if (!trade.getPremium().isPresent()) {
      return pvProduct;
    }
    CurrencyAmount pvPremium = paymentPricer.presentValue(trade.getPremium().get(), multicurve);
    return pvProduct.plus(pvPremium);
  }
  

}
