/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorParameters;

/**
 * Price of physical delivery European swaptions in the two-factor rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * The martingales are A(1) = exp(a_1 X_t^(1) - 0.5 a_1^2 t) - 1, A(2) = exp(a_2 X_t^(2)  - 0.5 a_2^2 t) - 1.
 * The Libor process numerator is of the form L(0) + b_1 A(1) + b_2 A(2) 
 * The discount factor process numerator is of the form P(0,T) + b_0(T) A(1)
 * 
 * @author Marc Henrard
 */
public abstract class RationalTwoFactorSwaptionPhysicalProductPricer {
  
  /**
   * Computes the present value of a swaption in the rational model.
   * <p>
   * The result is expressed using the currency of the swapion.
   * 
   * @param swaption  the product to price
   * @param rates  the rates provider
   * @param model  the rational model parameters
   * @return the present value of the swaption product
   */
  public abstract CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider rates,
      RationalTwoFactorParameters model);
  
  // validate that the rates and volatilities providers are coherent
  protected void validate(RatesProvider rates, ResolvedSwaption swaption, RationalTwoFactorParameters model) {
    ArgChecker.isTrue(model.getValuationDate().equals(rates.getValuationDate()),
        "volatility and rate data should be for the same date");
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), "underlying swap should be single currency");
    ArgChecker.isTrue(swaption.getSwaptionSettlement() == PhysicalSwaptionSettlement.DEFAULT, "swaption should be physical settlement");
    ArgChecker.isTrue(swaption.getUnderlying().getLegs().size() == 2, "underlying swap should have two legs");
  }

}
