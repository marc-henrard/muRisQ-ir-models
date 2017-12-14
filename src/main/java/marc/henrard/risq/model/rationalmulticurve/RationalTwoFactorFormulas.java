/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.time.LocalDate;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

/**
 * Interest rate multi-curve rational model.
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
public class RationalTwoFactorFormulas {
  
  /** Value related to the numerical integration. */
  private static final double LIMIT_INT = 12.0; // Equivalent to + infinity in normal integrals
  private static final double TOL_ABS = 1.0E-1;
  private static final double TOL_REL = 1.0E-6;
  /** Limit for small coefficients. */
  private static final double SMALL = 1.0E-12;
  /** Limit for small times. */
  private static final double SMALL_T = 1.0E-6;
  /** Normal distribution implementation. */
  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);
  
  /** The default instance of the formulas. */
  public final static RationalTwoFactorFormulas DEFAULT = new RationalTwoFactorFormulas();
  
  // Private constructor
  private RationalTwoFactorFormulas(){
  }
  
  /**
   * In the rational two-factor model, for the description of a swap dynamic, the constant, the coefficients of
   * exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...).
   * <p>
   * The swap can have any number of leg, they must be of the type fixed, Ibor (no composition) or OIS (compounded).
   * The coefficients computed are valid only if the model date is before the fixing of all the floating payments.
   * 
   * @param swap  the swap
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[] swapCoefficients(
      ResolvedSwap swap, 
      RatesProvider rates,
      RationalTwoFactorParameters model) {

    double[] c = new double[3];
    for (ResolvedSwapLeg leg : swap.getLegs()) {
      double[] cLeg = new double[3];
      if (leg.getType().equals(SwapLegType.FIXED)) {
        cLeg = legFixedCoefficients(leg, rates, model);
      }
      if (leg.getType().equals(SwapLegType.IBOR)) {
        cLeg = legIborCoefficients(leg, rates, model);
      }
      if (leg.getType().equals(SwapLegType.OVERNIGHT)) {
        cLeg = legOisCoefficients(leg, rates, model);
      }
      for (int i = 0; i < 3; i++) {
        c[i] += cLeg[i];
      }
    }
    return c;
  }
  
  /**
   * In the rational two-factor model, for the description of a swap's fixed leg dynamic, the constant, 
   * the coefficients of exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...).
   * <p>
   * The swap leg must be of the type FIXED.
   * The coefficients computed are valid only if the model valuation date is before all the payments.
   * 
   * @param fixedLeg  the fixed leg
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[] legFixedCoefficients(
      ResolvedSwapLeg fixedLeg,
      RatesProvider rates,
      RationalTwoFactorParameters model) {

    Currency ccy = fixedLeg.getCurrency();
    double[] c = new double[3];
    for (SwapPaymentPeriod period : fixedLeg.getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof FixedRateComputation, "fixed");
      FixedRateComputation obs = (FixedRateComputation) accrualPeriod.getRateComputation();
      c[0] += ratePeriod.getNotional() * obs.getRate() * accrualPeriod.getYearFraction()
          * rates.discountFactor(ccy, ratePeriod.getPaymentDate());
      c[1] += ratePeriod.getNotional() * obs.getRate() * accrualPeriod.getYearFraction() 
          * model.b0(ratePeriod.getPaymentDate());
    }
    c[0] -= c[1] + c[2];
    return c;
  }

  /**
   * In the rational two-factor model, for the description of a swap's ibor leg dynamic, the constant, 
   * the coefficients of exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...).
   * <p>
   * The swap leg must be of the type IBOR.
   * The coefficients computed are valid only if the model valuation date is before all the payment fixing dates.
   * 
   * @param iborLeg  the ibor leg
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[] legIborCoefficients(
      ResolvedSwapLeg iborLeg,
      RatesProvider rates,
      RationalTwoFactorParameters model) {

    Currency ccy = iborLeg.getCurrency();
    double[] c = new double[3];
    for (SwapPaymentPeriod period : iborLeg.getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof IborRateComputation, "ibor");
      IborRateComputation obs = (IborRateComputation) accrualPeriod.getRateComputation();
      double df = rates.discountFactor(ccy, ratePeriod.getPaymentDate());
      c[0] += ratePeriod.getNotional() * accrualPeriod.getYearFraction()
          * rates.iborIndexRates(obs.getIndex()).rate(obs.getObservation()) * df;
      c[1] += ratePeriod.getNotional() * accrualPeriod.getYearFraction() * 
          model.b1(obs.getObservation());
      c[2] += ratePeriod.getNotional() * accrualPeriod.getYearFraction()  * 
          model.b2(obs.getObservation());
    }
    c[0] -= c[1] + c[2];
    return c;
  }

  /**
   * In the rational two-factor model, for the description of a swap's ibor leg dynamic, the constant, 
   * the coefficients of exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...).
   * <p>
   * The swap leg must be of the type OVERNIGHT and the accrual period of the type {@link OvernightCompoundedRateComputation}.
   * The coefficients computed are valid only if the model valuation date is before all the payment fixing dates.
   * 
   * @param oisLeg  the OIS leg
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[]  legOisCoefficients(
      ResolvedSwapLeg oisLeg,
      RatesProvider rates,
      RationalTwoFactorParameters model) {

    Currency ccy = oisLeg.getCurrency();
    double[] c = new double[3];
    for (SwapPaymentPeriod period : oisLeg.getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof OvernightCompoundedRateComputation,
          "overnight compounded");
      OvernightCompoundedRateComputation obs =
          (OvernightCompoundedRateComputation) accrualPeriod.getRateComputation();
      double dfPayment = rates.discountFactor(ccy, ratePeriod.getPaymentDate());
      double dfStart = rates.discountFactor(ccy, obs.getStartDate());
      double dfEnd = rates.discountFactor(ccy, obs.getEndDate());
      double spread = accrualPeriod.getSpread();
      double af = accrualPeriod.getYearFraction();
      c[0] += ratePeriod.getNotional() * (dfStart * dfPayment / dfEnd - (1 + af * spread) * dfPayment);
      c[1] += ratePeriod.getNotional() *
          (dfPayment / dfEnd * model.b0(obs.getStartDate()) -
              (1 + af * spread) * model.b0(ratePeriod.getPaymentDate()));
    }
    c[0] -= c[1] + c[2];
    return c;
  }
  
  /**
   * In the rational two-factor model, for the description of a caplet dynamic, the constant, the coefficients of
   * exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...)
   * <p>
   * From the working paper on implementation:
   * c[1] = b_1(\theta) - K b_0(v)
   * c[2] = b_2(\theta)
   * c[0] = L^j(0; \theta) - K P^D(0,v) - (c[1] + c[2])
   * All to be multiplied by omega, notional and accrual factor
   * 
   * @param caplet  the caplet/floorlet period
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[] capletCoefficients(
      IborCapletFloorletPeriod caplet,
      RatesProvider rates,
      RationalTwoFactorParameters model) {

    double strike = caplet.getStrike();
    double factor = Math.abs(caplet.getNotional()) * caplet.getYearFraction();
    if(caplet.getFloorlet().isPresent()) { // Floorlet
      factor *= -1.0d;
    }
    IborIndexObservation obs = caplet.getIborRate().getObservation();
    LocalDate maturity = obs.getMaturityDate();
    double[] c = new double[3];
    c[1] = (model.b1(obs) - strike * model.b0(maturity)) * factor;
    c[2] = model.b2(obs) * factor;
    c[0] = (rates.iborIndexRates(obs.getIndex()).rate(obs) -
        strike) * rates.discountFactor(caplet.getCurrency(), maturity) * factor;
    c[0] -= c[1] + c[2];
    return c;
  }

  /**
   * Computes the value of the 2-D integral (x_0 + x_1 (exp(a_1 X(1) - ...) + 1) + x_2 (exp(a_2 X(2) - ...) + 1))^+
   * <p>
   * The computation is based on the coefficients of the different base random variables. The coefficeints 
   * are the constant, the coefficients of exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...)
   * 
   * @param x  the coefficients
   * @param t  the time to expiry
   * @param a1  the parameter of the first log-normal martingale
   * @param a2  the parameter of the second log-normal martingale
   * @param rho  the correlation between the X_1 and the X_2 random variables
   * @param nbSteps  the minimal number of steps in the numerical integration
   * @return  the value
   */
  public double pvSemiExplicit(double[] x, double t, double a1, double a2, double rho, int nbSteps) {
    RungeKuttaIntegrator1D integrator1D  = 
        new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbSteps);
    ArgChecker.notNegative(t, "time to expiry");
    if (t < SMALL_T) {  // No time value
      return Math.max(x[0] + x[1] + x[2], 0.0);
    }
    if (x[1] < 0) { // Payer-receiver parity
      return x[0] + x[1] + x[2] + pvSemiExplicit(new double[] {-x[0], -x[1], -x[2] }, t, a1, a2, rho, nbSteps);
    }
    if (x[0] >= 0 && x[1] >= 0 && x[2] >= 0) {
      return x[0] + x[1] + x[2];
    }
    if(Math.abs(x[1])<=SMALL && Math.abs(x[2]*x[0]) <= SMALL) {
      return 0.0d;
    }
    if ((x[0] * x[1] < 0) && (Math.abs(x[2]) <= SMALL)) { // Equivalent to 1-factor
      double omega = Math.signum(x[1]);
      double pv = BlackFormulaRepository.price(omega * x[1], -omega * x[0], t, a1, x[1] > 0);
      return pv;
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
    PriceIntegrant1 integrant = new PriceIntegrant1(rho, x, sqrtt, a1, a2, d1, d2);
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
    return pv1 + pv2;
  }

  /* The exercise boundary */
  private static double kappa2(double x0, double x2, double d2, double a2, double sqrtt) {
    return Math.log(-x0 / (x2 * d2)) / (a2 * sqrtt);
  }

  /* Inner class to implement the integration used in price replication. */
  private static final class PriceIntegrant1 implements Function<Double, Double> {

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
    public PriceIntegrant1(double rho, double[] x, double sqrtt, 
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

  /**
   * Computes the value of a derivative by two dimensional numerical integration.
   * <p>
   * The computation is based on the coefficients of the different base random variables. The coefficeints 
   * are the constant, the coefficients of exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...)
   * 
   * @param x  the coefficients
   * @param t  the time to expiry
   * @param a1  the parameter of the first log-normal martingale
   * @param a2  the parameter of the second log-normal martingale
   * @param rho  the correlation between the X_1 and the X_2 random variables
   * @param nbSteps  the minimal number of steps in the numerical integration
   * @return  the value
   */
  public double pvNumericalIntegration(double[] x, double t, double a1, double a2, double rho, int nbSteps) {
    
    final PriceIntegrant2 integrant = 
        new PriceIntegrant2(new double[] {a1, a2 }, rho, x, t);
    final RungeKuttaIntegrator1D integrator1D = 
        new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbSteps);
    final IntegratorRepeated2D integrator2D = new IntegratorRepeated2D(integrator1D);
    double pv = 0.0;
    try {
      pv = 1.0 / (2.0 * Math.PI * Math.sqrt(1 - rho * rho)) * 
          integrator2D.integrate(integrant, new Double[] {-LIMIT_INT, -LIMIT_INT }, new Double[] {LIMIT_INT, LIMIT_INT });
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return pv;
  }

  /** Inner class to implement the 2-dimentional integration used in price replication. */
  private static final class PriceIntegrant2 implements BiFunction<Double, Double, Double> {

    private final double[] a;
    private final double correlation;
    private final double[] coefficients;
    private final double expiryTime;
    private final double expiryTimeSqrt;

    /**
     * Constructor to the integrant function.
     */
    public PriceIntegrant2(double[] a, double correlation, double[] coefficients, double expiryTime) {
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
