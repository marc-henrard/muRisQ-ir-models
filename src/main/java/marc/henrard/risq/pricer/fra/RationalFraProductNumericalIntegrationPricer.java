/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.fra;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.fra.FraDiscountingMethod;
import com.opengamma.strata.product.fra.ResolvedFra;
import com.opengamma.strata.product.rate.IborRateComputation;

import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorParameters;

/**
 * Price of forward rate agreements with the ISDA FRA discounting contract definition in the two-factor rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * Model parameters as described in the {@link RationalTwoFactorParameters}.
 * 
 * @author Marc Henrard
 */
public class RationalFraProductNumericalIntegrationPricer {
  
  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 100;

  /** Value related to the numerical integration. */
  private static final double LIMIT_INT = 12.0; // Equivalent to + infinity in normal integrals
  private static final double TOL_ABS = 1.0E-8;
  private static final double TOL_REL = 1.0E-6;
  
  /** Number of integration steps in the integration. */
  private final int nbSteps;

  /** 
   * Creates an instance.
   */
  public RationalFraProductNumericalIntegrationPricer(int nbIntegrationSteps) {
    this.nbSteps = nbIntegrationSteps;
  }
  
  /** Default implementation. */
  public static final RationalFraProductNumericalIntegrationPricer DEFAULT =
      new RationalFraProductNumericalIntegrationPricer(NB_INTEGRATION_STEPS_DEFAULT);
  
  /**
   * Computes the present value of a FRA in the rational model.
   * <p>
   * The result is expressed using the currency of the FRA.
   * <p>
   * The FRA must have a FRA discounting method of the type ISDA.
   * 
   * @param fra  the forward rate agreement to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the present value of the FRA
   */
  public CurrencyAmount presentValue(
      ResolvedFra fra, 
      RatesProvider multicurve, 
      RationalTwoFactorParameters model2) {
    
    double expectation = expectation(fra, multicurve, model2);
    Currency ccy = fra.getCurrency();
    LocalDate u = fra.getStartDate();
    double k = fra.getFixedRate();
    double delta = fra.getYearFraction();
    double p0u = multicurve.discountFactor(ccy, u);
    double pv = p0u - (1.0d + delta * k) * expectation;
    return CurrencyAmount.of(ccy, pv * fra.getNotional());
  }

  /**
   * Computes the par rate of a FRA in the rational model.
   * <p>
   * The FRA must have a FRA discounting method of the type ISDA.
   * 
   * @param fra  the forward rate agreement to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the par rate of the FRA
   */
  public double parRate(
      ResolvedFra fra, 
      RatesProvider multicurve, 
      RationalTwoFactorParameters model2) {

    Currency ccy = fra.getCurrency();
    LocalDate u = fra.getStartDate();
    double delta = fra.getYearFraction();
    double p0u = multicurve.discountFactor(ccy, u);
    double expectation = expectation(fra, multicurve, model2);
    return (p0u / expectation - 1.0d) / delta;
  }
  
  // Expectation used in the present value
  double expectation(
      ResolvedFra fra, 
      RatesProvider multicurve, 
      RationalTwoFactorParameters model2) {
    
    IborRateComputation rateComputation = validate(fra, model2);
    Currency ccy = fra.getCurrency();
    LocalDate u = fra.getStartDate();
    LocalDate v = fra.getEndDate();
    double delta = fra.getYearFraction();
    double p0u = multicurve.discountFactor(ccy, u);
    double p0v = multicurve.discountFactor(ccy, v);
    double l0theta = 
        multicurve.iborIndexRates(rateComputation.getIndex()).rate(rateComputation.getObservation()) * p0v;
    double b0u = model2.b0(u);
    double b0v = model2.b0(v);
    double b1theta = model2.b1(rateComputation.getObservation());
    double b2theta = model2.b2(rateComputation.getObservation());
    double rho = model2.getCorrelation();
    ZonedDateTime fixingTime = 
        rateComputation.getIndex().calculateFixingDateTime(rateComputation.getObservation().getFixingDate());
    double theta = model2.relativeTime(fixingTime);
    double[] c = {delta, p0u, b0u, l0theta, b1theta, b2theta, p0v, b0v}; // delta, p0u, b0u, L0theta, b1theta, b2theta, p0v, b0v
    final PriceIntegrant2 integrant = 
        new PriceIntegrant2(new double[] {model2.a1(), model2.a2() }, rho, c, theta);
    final RungeKuttaIntegrator1D integrator1D = 
        new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbSteps);
    final IntegratorRepeated2D integrator2D = new IntegratorRepeated2D(integrator1D);
    double expectation = 0.0;
    try {
      expectation = 1.0 / (2.0 * Math.PI * Math.sqrt(1 - rho * rho)) * 
          integrator2D.integrate(integrant, new Double[] {-LIMIT_INT, -LIMIT_INT }, new Double[] {LIMIT_INT, LIMIT_INT });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return expectation;
  }
  
  /** Inner class to implement the 2-dimensional integration used in price replication. */
  static final class PriceIntegrant2 implements BiFunction<Double, Double, Double> {

    private final double[] a;
    private final double correlation;
    private final double[] c;
    private final double expiryTime;
    private final double expiryTimeSqrt;

    /**
     * Constructor to the integrant function.
     */
    public PriceIntegrant2(double[] a, double correlation, double[] c, double expiryTime) {
      this.a = a;
      this.correlation = correlation;
      this.c = c; // delta, p0u, b0u, L0theta, b1theta, b2theta, p0v, b0v
      this.expiryTime = expiryTime;
      this.expiryTimeSqrt = Math.sqrt(expiryTime);
    }

    @Override
    public Double apply(Double x0, Double x1) {
      double[] x = {x0, x1 };
      double[] A = new double[2];
      for (int i = 0; i < 2; i++) {
        A[i] = Math.exp(a[i] * expiryTimeSqrt * x[i] - 0.5 * a[i] * a[i] * expiryTime) - 1.0d;
      }
      double payoff = (c[1] + c[2] * A[0]) /
          (1 + c[0] * (c[3] + c[4] * A[0] + c[5] * A[1]) / (c[6] + c[7] * A[0]));
      return payoff * Math.exp(-(x0 * x0 + x1 * x1 - 2 * correlation * x0 * x1)
            / (2.0 * (1 - correlation * correlation)));
    }
  }
  
  // Validate the FRA and return the casted Ibor rate
  private IborRateComputation validate(ResolvedFra fra, RationalTwoFactorParameters model2) {
    ArgChecker.isTrue(fra.getDiscounting().equals(FraDiscountingMethod.ISDA), 
        "FRA discounting method must be ISDA but found {}", fra.getDiscounting());
    ArgChecker.isTrue(fra.getCurrency().equals(model2.getCurrency()), 
        "FRA currency must be equal to the model currency but found {} and {}", 
        fra.getCurrency(), model2.getCurrency());
    ArgChecker.isTrue(fra.getFloatingRate() instanceof IborRateComputation, 
        "Floating rate should be of the type IborRateComputation but found {}", 
        fra.getFloatingRate().getClass());
    return (IborRateComputation) fra.getFloatingRate();
  }
  
}
