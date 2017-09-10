/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import java.time.ZonedDateTime;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
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
 * Reference for the model: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2015).
 * Rational multi-curve models with counterparty-risk valuation adjustments. arXiv.
 * <p>
 * The martingales are A(1) = exp(a_1 X_1 + ...) - 1, A(2) = exp(a_2 X_2 + ...) - 1.
 * The Libor process numerator is of the form L(0) + b_2 A(1) + b_3 A(2)
 * The present value is computed by semi-explicit formulas - at most one dimensional numerical integration.
 * 
 * @author Marc Henrard
 */
public class RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer
    extends RationalSwaptionPhysicalProductPricer {

  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 10;
  private static final double LIMIT_INT = 12.0; // Equivalent to + infinity in normal integrals
  private static final double TOL_ABS = 1.0E-1;
  private static final double TOL_REL = 1.0E-6;
  private static final double SMALL = 1.0E-12;
  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

  /** Default implementation. */
  public static final RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer DEFAULT =
      new RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer(NB_INTEGRATION_STEPS_DEFAULT);

  /** Minimal number of integration steps in the integration. Default value. */
  private final RungeKuttaIntegrator1D integrator1D;

  /**
  * Creates an instance.
  */
  public RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer(int nbIntegrationSteps) {
    this.integrator1D = new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbIntegrationSteps);
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
    double pvNum = pvNumerical(c, expiryTime, model2.a1(), model2.a2(), model2.getCorrelation());
    return CurrencyAmount.of(ccy, (swaption.getLongShort() == LongShort.LONG) ? pvNum : -pvNum);
  }

  private double pvNumerical(double[] x, double t, double a1, double a2, double rho) {
    // t=0?
    if (x[1] < 0) { // Payer-receiver parity
      return x[0] + x[1] + x[2] + pvNumerical(new double[] {-x[0], -x[1], -x[2] }, t, a1, a2, rho);
    }
    if (x[0] >= 0 && x[1] >= 0 && x[2] >= 0) {
      return x[0] + x[1] + x[2];
    }
    if(Math.abs(x[1])<=SMALL && Math.abs(x[2]*x[0]) <= SMALL) {
      return 0.0d;
    }
    double d2 = Math.exp(-0.5 * a2 * a2 * t);
    double sqrtt = Math.sqrt(t);
    double kappa2 = kappa2(x[0], x[2], d2, a2, sqrtt);
    double omega = Math.signum(x[0]);
    if (Math.abs(x[1]) <= SMALL) {
      return x[0]  * NORMAL.getCDF(omega * kappa2)
          + x[1] * NORMAL.getCDF(omega * (kappa2 - rho * a1 * sqrtt))
          + x[2] * NORMAL.getCDF(omega * (kappa2 - a2 * sqrtt));
    }
    double d1 = Math.exp(-0.5 * a1 * a1 * t);
    SwaptionIntegrant1 integrant = new SwaptionIntegrant1(rho, x, sqrtt, a1, a2, d1, d2);
    if(x[0] < 0 && x[2] < 0) { // x[1] > 0 implicit from previous ifs
      double pv = 0.0;
      try {
        pv = 1.0 / Math.sqrt(2.0 * Math.PI) * 
            integrator1D.integrate(integrant, new Double[] {-LIMIT_INT }, new Double[] {LIMIT_INT });
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
      return pv;
    }
    double pv1 = x[0] * NORMAL.getCDF(omega * kappa2) 
        + x[1] * NORMAL.getCDF(omega * (kappa2 - rho * a1 * sqrtt))
        + x[2] * NORMAL.getCDF(omega * (kappa2 - a2 * sqrtt));
    double u = (omega == 1) ? Math.max(-LIMIT_INT, kappa2) : -LIMIT_INT;
    double v = (omega == 1) ? LIMIT_INT : Math.min(LIMIT_INT, kappa2);
    double pv2 = 0.0;
    try {
      pv2 = 1.0 / Math.sqrt(2.0 * Math.PI) * 
          integrator1D.integrate(integrant, new Double[] {u }, new Double[] {v });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return pv1 + pv2; // P1P1-0.0025
  }

  private double kappa2(double x0, double x2, double d2, double a2, double sqrtt) {
    return Math.log(-x0 / (x2 * d2)) / (a2 * sqrtt);
  }



  /** Inner class to implement the integration used in price replication. */
  private static final class SwaptionIntegrant1 implements Function<Double, Double> {

    private final double[] x;
    private final double rho;
    private final double sqrtt;
    private final double a1;
    private final double a2;
    private final double d1;
    private final double d2;
    private final double sqrt1rho;

    /**
     * Constructor to the integrant function.
     */
    public SwaptionIntegrant1(double rho, double[] x, double sqrtt, 
        double a1, double a2, double d1, double d2) {
      this.rho = rho;
      this.x = x;
      this.sqrtt = sqrtt;
      this.a1 = a1;
      this.a2 = a2;
      this.d1 = d1;
      this.d2 = d2;
      sqrt1rho = Math.sqrt(1.0 - rho*rho);
    }

    @Override
    public Double apply(final Double y2) {
      double c1 =  x[0] * Math.exp(-0.5*y2*y2) + x[2] * Math.exp(-0.5*(y2-a2*sqrtt)*(y2-a2*sqrtt));
      double c2 = x[1] * Math.exp(-0.5*(y2-rho*a1*sqrtt)*(y2-rho*a1*sqrtt));
      return NORMAL.getCDF(-(kappa(y2)-rho*y2)/(sqrt1rho)) * c1 
          + NORMAL.getCDF(-(kappa(y2)-rho*y2 - (1-rho*rho)*a1*sqrtt)/(sqrt1rho)) * c2;
    }
    
    private double kappa(double y2) {
      return Math.log(-(x[0] + x[2] * d2 * Math.exp(a2 * sqrtt * y2))/(x[1] * d1))/(a1 * sqrtt);
    }
  }

}
