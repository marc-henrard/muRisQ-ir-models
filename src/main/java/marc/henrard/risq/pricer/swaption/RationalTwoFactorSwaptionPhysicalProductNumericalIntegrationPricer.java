/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorFormulas;
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
public class RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer  
    extends SingleCurrencyModelSwaptionPhysicalProductPricer {

  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 10;
  public static final RationalTwoFactorFormulas FORMULAS = RationalTwoFactorFormulas.DEFAULT;

  /** Default implementation. */
  public static final RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer DEFAULT =
      new RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer(NB_INTEGRATION_STEPS_DEFAULT);
  
  /** Minimal number of integration steps in the integration. Default value. */
  private final int nbSteps;

  /**
   * Creates an instance.
   */
  public RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer(int nbIntegrationSteps) {
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
    double[] c = FORMULAS.swapCoefficients(underlying, rates, model2);
    double pvNum = FORMULAS
        .pvNumericalIntegration(c, expiryTime, model2.a1(), model2.a2(), model2.getCorrelation(), nbSteps);
    return CurrencyAmount.of(ccy, pvNum * ((swaption.getLongShort() == LongShort.LONG) ? 1.0 : -1.0));
  }

}
