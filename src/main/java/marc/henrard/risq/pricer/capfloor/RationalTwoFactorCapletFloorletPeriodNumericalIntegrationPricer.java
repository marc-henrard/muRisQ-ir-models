/**
 * Copyright (C) 2017 - present by Marc Henrard and Paola Mosconi.
 */
package marc.henrard.risq.pricer.capfloor;

import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;

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
 * Implementation: 
 * Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * Mosconi, Paola. (2017) Note on pricing cap/floor in rational multi-curve interest rate model.
 * <p>
 * The martingales are A(1) = exp(a_1 X_t^(1) - 0.5 a_1^2 t) - 1, A(2) = exp(a_2 X_t^(2)  - 0.5 a_2^2 t) - 1.
 * The Libor process numerator is of the form L(0) + b_1 A(1) + b_2 A(2) 
 * The discount factor process numerator is of the form P(0,T) + b_0(T) A(1)
 * 
 * @author Marc Henrard and Paola Mosconi
 */
public class RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer  
    extends SingleCurrencyModelCapletFloorletPeriodPricer{
  
  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 10;
  /** Repository for formulas associated to the two-factor rational model. */
  private static final RationalTwoFactorFormulas FORMULAS = RationalTwoFactorFormulas.DEFAULT;
  
  /** Number of integration steps in the integration. */
  private final int nbSteps;

  /**
   * Creates an instance.
   */
  public RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer(int nbIntegrationSteps) {
    this.nbSteps = nbIntegrationSteps;
  }
  
  /** Default implementation. */
  public static final RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer DEFAULT =
      new RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer(NB_INTEGRATION_STEPS_DEFAULT);

  @Override
  public CurrencyAmount presentValue(
      IborCapletFloorletPeriod caplet, 
      RatesProvider multicurve, 
      SingleCurrencyModelParameters model) {

    ArgChecker.isTrue(model instanceof RationalTwoFactorParameters);
    RationalTwoFactorParameters model2 = (RationalTwoFactorParameters) model;
    validate(multicurve, caplet, model);
    Currency ccy = caplet.getCurrency();
    ZonedDateTime expiryDateTime = caplet.getFixingDateTime();
    double expiryTime = model.relativeTime(expiryDateTime);
    double[] c = FORMULAS.capletCoefficients(caplet, multicurve, model2);
    double pvNum = multicurve.discountFactor(ccy, caplet.getPaymentDate()) /
        multicurve.discountFactor(ccy, caplet.getIborRate().getMaturityDate()) *
        FORMULAS.pvNumericalIntegration(c, expiryTime, model2.a1(), model2.a2(), model2.getCorrelation(), nbSteps);
    return CurrencyAmount.of(ccy, (caplet.getNotional() > 0) ? pvNum : -pvNum);
  }

}
