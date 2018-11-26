/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorFormulas;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorParameters;

/**
 * Price of physical delivery European swaptions in the two-factor rational model.
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * The martingales are A(1) = exp(a_1 X_1 + ...) - 1, A(2) = exp(a_2 X_2 + ...) - 1.
 * The Libor process numerator is of the form L(0) + b_2 A(1) + b_3 A(2)
 * The present value is computed by semi-explicit formulas - at most one dimensional numerical integration.
 * 
 * @author Marc Henrard
 */
public class RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer
    extends SingleCurrencyModelSwaptionPhysicalProductPricer {

  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 10;
  /** Repository for formulas associated to the two-factor rational model. */
  private static final RationalTwoFactorFormulas FORMULAS_2 = RationalTwoFactorFormulas.DEFAULT;
  
  /** Number of integration steps in the integration. */
  private final int nbSteps;
  
  /** Default implementation. */
  public static final RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer DEFAULT =
      new RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer(NB_INTEGRATION_STEPS_DEFAULT);

  /**
  * Creates an instance.
  */
  public RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer(int nbIntegrationSteps) {
    this.nbSteps = nbIntegrationSteps;
  }

  @Override
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider rates,
      SingleCurrencyModelParameters model) {

    ArgChecker.isTrue(model instanceof RationalTwoFactorParameters);
    RationalTwoFactorParameters model2 = (RationalTwoFactorParameters) model;
    validate(rates, swaption, model);
    Currency ccy = swaption.getUnderlying().getLegs().get(0).getCurrency();
    ZonedDateTime expiryDateTime = swaption.getExpiry();
    double expiryTime = model.relativeTime(expiryDateTime);
    ResolvedSwap underlying = swaption.getUnderlying();
    double[] c = FORMULAS_2.swapCoefficients(underlying, rates, model2);
    double pvNum = FORMULAS_2
        .pvSemiExplicit(c, expiryTime, model2.a1(), model2.a2(), model2.getCorrelation(), nbSteps);
    return CurrencyAmount.of(ccy, (swaption.getLongShort() == LongShort.LONG) ? pvNum : -pvNum);
  }

}
