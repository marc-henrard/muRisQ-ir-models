/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorFormulas;
import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorParameters;

/**
 * Price physical delivery European swaptions in the simplified one-factor rational model by explicit formula.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2015).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * 
 * @author Marc Henrard
 */
public class RationalOneFactorSwaptionPhysicalProductExplicitPricer {

  /**
   * Default implementation.
   */
  public static final RationalOneFactorSwaptionPhysicalProductExplicitPricer DEFAULT = 
      new RationalOneFactorSwaptionPhysicalProductExplicitPricer();
  
  /** The rational model formulas.  */
  private final static RationalOneFactorFormulas FORMULAS = RationalOneFactorFormulas.DEFAULT;
  
  /**
   * Creates an instance.
   */
  public RationalOneFactorSwaptionPhysicalProductExplicitPricer() {
  }
  
  /**
   * Computes the present value of a swaption in the rational model.
   * <p>
   * The result is expressed using the currency of the swapion.
   * 
   * @param swaption  the product to price
   * @param multicurve  the rates provider with the multi-curve framework
   * @param model  the rational model parameters
   * @return the present value of the swaption product
   */
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption, 
      RatesProvider multicurve, 
      RationalOneFactorParameters model) {
    validate(multicurve, swaption, model);
    double[] c = FORMULAS.swapCoefficients(swaption.getUnderlying(), multicurve, model);
    Currency ccy = swaption.getUnderlying().getLegs().get(0).getCurrency();
    ZonedDateTime expiryDateTime = swaption.getExpiry();
    double expiryTime = model.relativeTime(expiryDateTime);
    if ((c[0] >= 0) && (c[1] >= 0)) { // Always exercised
      return CurrencyAmount.of(ccy, (swaption.getLongShort() == LongShort.LONG) ? c[0] + c[1] : -c[0] - c[1]);
    }
    if ((c[0] <= 0) && (c[1] <= 0)) { // Never exercised
      return CurrencyAmount.zero(ccy);
    }
    double omega = Math.signum(c[1]);
    // Black formula: F = omega c[1], K = - omega c[0], sigma = a
    double pv = BlackFormulaRepository.price(omega * c[1], -omega * c[0], expiryTime, model.a(), c[1] > 0);
    return CurrencyAmount.of(ccy, (swaption.getLongShort() == LongShort.LONG) ? pv : -pv);
  }

  // validate that the rates and volatilities providers are coherent
  private void validate(RatesProvider rates, ResolvedSwaption swaption, RationalOneFactorParameters model) {
    ArgChecker.isTrue(model.getValuationDate().equals(rates.getValuationDate()), 
        "volatility and rate data should be for the same date");
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), "underlying swap should be single currency");
    ArgChecker.isTrue(swaption.getSwaptionSettlement() == PhysicalSwaptionSettlement.DEFAULT, "swaption should be physical settlement");
    ArgChecker.isTrue(swaption.getUnderlying().getLegs().size() == 2, "underlying swap should have two legs");
  }

}
