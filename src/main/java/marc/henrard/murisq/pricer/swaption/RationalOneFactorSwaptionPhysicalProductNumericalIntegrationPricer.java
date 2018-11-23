/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.ZonedDateTime;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.rationalmulticurve.RationalOneFactorFormulas;
import marc.henrard.murisq.model.rationalmulticurve.RationalOneFactorParameters;

/**
 * Price physical delivery European swaptions in the simplified one-factor rational model by numerical integration.
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
public class RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer
    extends SingleCurrencyModelSwaptionPhysicalProductPricer {

  /**
   * Default implementation.
   */
  public static final RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer DEFAULT = 
      new RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer();

  /** Minimal number of integration steps in the integration. */
  private static final int NB_INTEGRATION = 50;
  /** The rational model formulas.  */
  private final static RationalOneFactorFormulas FORMULAS = RationalOneFactorFormulas.DEFAULT;
  
  /**
   * Creates an instance.
   */
  public RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer() {
  }

  @Override
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption, 
      RatesProvider rates, 
      SingleCurrencyModelParameters model) {
    
    ArgChecker.isTrue(model instanceof RationalOneFactorParameters);
    RationalOneFactorParameters model1 = (RationalOneFactorParameters) model;
    validate(rates, swaption, model);
    double[] c = FORMULAS.swapCoefficients(swaption.getUnderlying(), rates, model1);
    Currency ccy = swaption.getUnderlying().getLegs().get(0).getCurrency();
    ZonedDateTime expiryDateTime = swaption.getExpiry();
    double expiryTime = model.relativeTime(expiryDateTime);
    /* Numerical integration: (c0 + c1 A1) exp(-1/2 X^2) */
    final SwaptionIntegrant integrant = new SwaptionIntegrant(model1.a(), c, expiryTime);
    final double limit = 12.0;
    final double absoluteTolerance = 1.0E-1;
    final double relativeTolerance = 1.0E-6;
    final RungeKuttaIntegrator1D integrator1D = new RungeKuttaIntegrator1D(absoluteTolerance, relativeTolerance, NB_INTEGRATION);
    double pv = 0.0;
    try {
      pv = 1.0 / Math.sqrt(2.0 * Math.PI) * 
          integrator1D.integrate(integrant, new Double[] {-limit }, new Double[] {limit });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return CurrencyAmount.of(ccy, pv * ((swaption.getLongShort() == LongShort.LONG) ? 1.0 : -1.0));
  }

  /** Inner class to implement the integration used in price replication. */
  private static final class SwaptionIntegrant implements Function<Double, Double> {

    private final double a;
    private final double[] coefficients;
    private final double expiryTime;
    private final double expiryTimeSqrt;

    /**
     * Constructor to the integrant function.
     */
    public SwaptionIntegrant(double a, double[] coefficients, double expiryTime) {
      this.a = a;
      this.coefficients = coefficients;
      this.expiryTime = expiryTime;
      this.expiryTimeSqrt = Math.sqrt(expiryTime);
    }

    @Override
    public Double apply(final Double x0) {
      double A = Math.exp(a * expiryTimeSqrt * x0 - 0.5 * a * a * expiryTime);
      double result = coefficients[0];
      result += coefficients[1] * A;
      if (result > 0) {
        return result * Math.exp(-0.5 * x0 * x0);
      }
      return 0.0;
    }
  }

}
