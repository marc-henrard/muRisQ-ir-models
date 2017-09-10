/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import java.time.ZonedDateTime;
import java.util.function.BiFunction;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.risq.model.rationalmulticurve.RationalParameters;
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
    extends RationalSwaptionPhysicalProductPricer {

  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 10;

  /** Default implementation. */
  public static final RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer DEFAULT =
      new RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer(NB_INTEGRATION_STEPS_DEFAULT);
  
  /** Minimal number of integration steps in the integration. Default value. */
  private final int nbIntegrationSteps;

  /**
   * Creates an instance.
   */
  public RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer(int nbIntegrationSteps) {
    this.nbIntegrationSteps = nbIntegrationSteps;
  }

  @Override
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider rates,
      RationalParameters model) {

    ArgChecker.isTrue(model instanceof RationalTwoFactorParameters);
    RationalTwoFactorParameters model2 = (RationalTwoFactorParameters) model;
    validate(rates, swaption, model);
    Currency ccy = swaption.getUnderlying().getLegs().get(0).getCurrency();
    ZonedDateTime expiryDateTime = swaption.getExpiry();
    double expiryTime = model.relativeTime(expiryDateTime);
    ResolvedSwap underlying = swaption.getUnderlying();
    double[] c = RationalTwoFactorFormulas.swapCoefficients(underlying, rates, model2);
    /* Numerical integration: (c0 + c1 (A1+1) + c2 (A2+1)) exp(-1/2 XT S X) */
    final SwaptionIntegrant integrant = 
        new SwaptionIntegrant(new double[] {model2.a1(), model2.a2() }, model2.getCorrelation(), c, expiryTime);
    final double limit = 12.0;
    final double absoluteTolerance = 1.0E-1;
    final double relativeTolerance = 1.0E-6;
    final RungeKuttaIntegrator1D integrator1D = 
        new RungeKuttaIntegrator1D(absoluteTolerance, relativeTolerance, nbIntegrationSteps);
    final IntegratorRepeated2D integrator2D = new IntegratorRepeated2D(integrator1D);
    double pv = 0.0;
    try {
      pv = 1.0 / (2.0 * Math.PI * Math.sqrt(1 - model2.getCorrelation() * model2.getCorrelation())) * 
          integrator2D.integrate(integrant, new Double[] {-limit, -limit }, new Double[] {limit, limit });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return CurrencyAmount.of(ccy, pv * ((swaption.getLongShort() == LongShort.LONG) ? 1.0 : -1.0));
  }

  /** Inner class to implement the integration used in price replication. */
  private static final class SwaptionIntegrant implements BiFunction<Double, Double, Double> {

    private final double[] a;
    private final double correlation;
    private final double[] coefficients;
    private final double expiryTime;
    private final double expiryTimeSqrt;

    /**
     * Constructor to the integrant function.
     */
    public SwaptionIntegrant(double[] a, double correlation, double[] coefficients, double expiryTime) {
      this.a = a;
      this.correlation = correlation;
      this.coefficients = coefficients;
      this.expiryTime = expiryTime;
      this.expiryTimeSqrt = Math.sqrt(expiryTime);
    }

    @Override
    public Double apply(Double x0, Double x1) {
      double[] x = {x0, x1 };
      double result = 0.0;
      double[] A = new double[3];
      A[0] = 1.0;
      for (int i = 1; i < 3; i++) {
        A[i] = Math.exp(a[i - 1] * expiryTimeSqrt * x[i - 1] - 0.5 * a[i - 1] * a[i - 1] * expiryTime);
      }
      for (int i = 0; i < 3; i++) {
        result += coefficients[i] * A[i];
      }
      if (result > 0) {
        return result * Math.exp(-(x0 * x0 + x1 * x1 - 2 * correlation * x0 * x1)
            / (2.0 * (1 - correlation * correlation)));
      }
      return 0.0;
    }
  }

}
