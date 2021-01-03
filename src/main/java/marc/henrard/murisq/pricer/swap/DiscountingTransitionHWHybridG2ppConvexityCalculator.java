/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import java.time.LocalDate;
import java.util.function.BiFunction;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.math.impl.integration.Integrator2D;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;

import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantFormulas;
import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantParameters;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Calculator related to the collateral/discounting transition at CCP.
 * <p>
 * Transition is done with cash compensation computed with unchanged (not convexity adjusted) IBOR forward rate as
 * decided by CCP.
 * <p>
 * The calculator uses Hull-White model on the current ON rate, G2++ on the new ON rate and the hybrid model for
 * current ON/IBOR.
 * 
 * @author Marc Henrard
 */
public class DiscountingTransitionHWHybridG2ppConvexityCalculator {

  /* Integration definition */
  private static final double TOL_ABS = 1.0E-1;
  private static final double TOL_REL = 1.0E-6;
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 100;
  private static final RungeKuttaIntegrator1D INTEGRATOR_1D_DEFAULT =
      new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, NB_INTEGRATION_STEPS_DEFAULT);
  private static final Integrator2D<Double, Double> INTEGRATOR_2D_DEFAULT = 
      new IntegratorRepeated2D(INTEGRATOR_1D_DEFAULT);
  private static final double LIMIT_INT = 10.0; // Equivalent to + infinity in normal integrals
  private static final Double[] INTEGRAL_BOUNDS_LOWER = new Double[] {-LIMIT_INT, -LIMIT_INT };
  private static final Double[] INTEGRAL_BOUNDS_UPPER = new Double[] {LIMIT_INT, LIMIT_INT };
  /* Hull-White */
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS_HW = 
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  private static final G2ppPiecewiseConstantFormulas FORMULAS_G2PP =
      G2ppPiecewiseConstantFormulas.DEFAULT;
  /* Matrix algebra */
  private static final MatrixAlgebra ALGEBRA = new CommonsMatrixAlgebra();

  /** The calculator default instance */
  public static final DiscountingTransitionHWHybridG2ppConvexityCalculator DEFAULT = 
      new DiscountingTransitionHWHybridG2ppConvexityCalculator(INTEGRATOR_2D_DEFAULT);
  
  /**
   * The integration mechanism used for the 2D-integration.
   */
  private final Integrator2D<Double, Double> integrator2D;
  
  /**
   * Creates an instance with a given 2D integrator.
   * 
   * @param integrator2D the integrator 
   */
  public DiscountingTransitionHWHybridG2ppConvexityCalculator(Integrator2D<Double, Double> integrator2D) {
    this.integrator2D = integrator2D;
  }
  
  /**
   * Computes the adjusted IBOR forward rate for the collateral/discounting transition.
   * <p>
   * The multi-curve framework contains the discounting and forward in the current ON, the 
   * 
   * @param obsIbor  the IBOR observation for which the adjusted forward is computed.
   * @param indexOn  the ON index for current ON rate
   * @param bigBangDate  the big bang date for the switch from one ON rate to the other
   * @param multicurve  the multi-curve framework with the current ON rate as discounting
   * @param g2pp  the G2++ model for the new ON rate with the first factor modeling the current ON rate
   * @param rho23  the correlation between the G2++ second factor Brownian motion 
   * and the hybrid model IBOR/ON spread factor Brownian motion
   * @param a the IBOR/ON spread factor volatility in the hybrid model 
   * @return  the IBOR adjusted forward
   */
  public double adjustedForward(
      IborIndexObservation obsIbor,
      OvernightIndex indexOn,
      LocalDate bigBangDate,
      ImmutableRatesProvider multicurve,
      G2ppPiecewiseConstantParameters g2pp,
      double rho23,
      double a) {

    Currency ccy = indexOn.getCurrency();
    ArgChecker.isTrue(ccy.equals(obsIbor.getCurrency()), 
        "IBOR and ON observation must be in the same currency");
    LocalDate effectiveDate = obsIbor.getEffectiveDate();
    OvernightIndexObservation obsOn = OvernightIndexObservation.builder()
        .index(indexOn)
        .effectiveDate(effectiveDate)
        .fixingDate(effectiveDate)
        .maturityDate(effectiveDate.plusDays(1)) // Date not used
        .publicationDate(effectiveDate.plusDays(1)) // Date not used
        .build();
    Curve curveDsc = multicurve.getDiscountCurves().get(ccy); // Discounting in the current ON
    Curve curveOn = multicurve.getIndexCurves().get(indexOn);
    ArgChecker.isTrue(curveDsc.getName().equals(curveOn.getName()), 
        "collateral/discounting should be in line with the overnight benchmark");
    IborIndexRates libor = multicurve.iborIndexRates(obsIbor.getIndex());
    OvernightIndexRates c1 = multicurve.overnightIndexRates(obsOn.getIndex());
    DiscountFactors dsc1 = multicurve.discountFactors(ccy);
    LocalDate iborFixingDate = obsIbor.getFixingDate();
    LocalDate iborStartAccrual = obsIbor.getEffectiveDate();
    LocalDate iborEndAccrual = obsIbor.getMaturityDate();
    double delta = obsIbor.getYearFraction();
    double Pc1u = dsc1.discountFactor(iborStartAccrual);
    double Pc1v = dsc1.discountFactor(iborEndAccrual);
    double iborRatec1 = libor.rate(obsIbor);
    double onRatec1 = c1.periodRate(obsOn, iborEndAccrual);
    double spread = iborRatec1 - onRatec1;
    double spreadPvc1 = spread * Pc1v;
    double theta = g2pp.relativeTime(iborFixingDate);
    DoubleArray c = modelCoefficients(bigBangDate, obsIbor, g2pp);
    double forwardAdjusted = 
        adjustedForward(c, Pc1u, Pc1v, delta, theta, spreadPvc1, g2pp.getCorrelation(), rho23, a);
    return forwardAdjusted;
  }

  /**
   * Computes the different coefficients required to obtain the change of collateral convexity adjustment on IBOR forwards
   * in a hybrid multi-curve model with two pseudo-discounting curves.
   * 
   * @param transitionDate  the transition date
   * @param obsIbor  the IBOR observation
   * @param hw1  the first HW provider
   * @param hw2  the second HW provider
   * @return the coefficient required to compute the change of discounting convexity adjustment
   */
  public DoubleArray modelCoefficients(
      LocalDate transitionDate,
      IborIndexObservation obsIbor,
      G2ppPiecewiseConstantParameters g2pp) {
    
    double t = g2pp.relativeTime(transitionDate);
    double theta = g2pp.relativeTime(obsIbor.getFixingDate());
    double u = g2pp.relativeTime(obsIbor.getEffectiveDate());
    double v = g2pp.relativeTime(obsIbor.getMaturityDate());
    HullWhiteOneFactorPiecewiseConstantParameters parametersHw1 = HullWhiteOneFactorPiecewiseConstantParameters
        .of(g2pp.getKappa1(), g2pp.getVolatility1(), g2pp.getVolatilityTime().subArray(1, g2pp.getVolatilityTime().size()-1));
    HullWhiteOneFactorPiecewiseConstantParameters parametersHw2 = HullWhiteOneFactorPiecewiseConstantParameters
        .of(g2pp.getKappa2(), g2pp.getVolatility2(), g2pp.getVolatilityTime().subArray(1, g2pp.getVolatilityTime().size()-1));
    DoubleArray alphastthetav = FORMULAS_G2PP.alphaCollateralAccountDiscountFactor(g2pp, t, theta, v);
    double alpha1tthetav = alphastthetav.get(0); //^2
    double alpha2tthetav = alphastthetav.get(1); //^2
    double alphatthetav = alphastthetav.get(2); //^2
    double alpha10thetau = FORMULAS_HW.alphaCashAccount(parametersHw1, 0, theta, u); // no^2
    double alpha10thetav = FORMULAS_HW.alphaCashAccount(parametersHw1, 0, theta, v); // no^2
    double cross21tthetav0thetau = FORMULAS_HW.varianceCrossTermCashAccount(parametersHw2, parametersHw1, t, theta, v, u);
    double cross21tthetav0thetav = FORMULAS_HW.varianceCrossTermCashAccount(parametersHw2, parametersHw1, t, theta, v, v);
    double cross23tthetav0theta = FORMULAS_HW.varianceCrossTermConstantVolCashAccount(parametersHw2, t, theta, v);
    return DoubleArray.ofUnsafe(new double[] {alpha1tthetav, alpha2tthetav, alphatthetav, alpha10thetau, alpha10thetav,
        cross21tthetav0thetau, cross21tthetav0thetav, cross23tthetav0theta});
  }

  /**
   * The 2D-numerical integration of the convexity related expectation.
   * 
   * @param vol1  the first dimension volatility
   * @param vol2  the second dimension volatility
   * @param sigma  the covariance matrix
   * @return  the convexity coefficient
   */
  public double integrationConvexity(
      double vol1,
      double vol2,
      DoubleMatrix sigma) {

    DoubleMatrix sigma_1 = ALGEBRA.getInverse(sigma);
    BiFunction<Double, Double, Double> integrant =
        (x1, x2) -> Math.exp(
            -vol1 * x1 - 0.5 * vol1 * vol1 
            - vol2 * x2 - 0.5 * vol2 * vol2 
            -0.5 * x1 * x1 * sigma_1.get(0, 0) - x1 * x2 * sigma_1.get(0, 1) - 0.5 * x2 * x2 * sigma_1.get(1, 1));
    double det = ALGEBRA.getDeterminant(sigma);
    return integration2D(integrant) / (2 * Math.PI * Math.sqrt(det));
  }
  
  /**
   * Computes the adjusted IBOR forward rate for the collateral/discounting transition.
   * <p>
   * Computation using only doubles and not the financial description of the different elements.
   * 
   * @param c  the model coefficients
   * @param Pc1u  the discount factor in c1-discounting up to time u
   * @param Pc1v  the discount factor in c1-discounting up to time v
   * @param delta  the IBOR period accrual factor
   * @param theta  the IBOR coupon fixing time
   * @param spreadPvc1  the spread
   * @param rho12  the correlation between the two G2++ factors
   * @param rho23  the correlation between the second G2++ factor and the hybrid model spread factor
   * @return  the IBOR adjusted forward
   */
  public double adjustedForward(
      DoubleArray c,
      double Pc1u,
      double Pc1v,
      double delta,
      double theta,
      double spreadPvc1,
      double rho12,
      double rho23,
      double a) {
    
    /* part 1: Pc1u */
    double notAdj1 = Pc1u / delta;
    DoubleMatrix sigma1 = DoubleMatrix.ofUnsafe( // standard
        new double[][] {{1.0d, rho12 * c.get(5) / (Math.sqrt(c.get(1)) * c.get(3))},
            {rho12 * c.get(5) / (Math.sqrt(c.get(1)) * c.get(3)), 1.0d}});
    double adjustment1 = integrationConvexity(Math.sqrt(c.get(1)), c.get(3), sigma1);
    double convexityAdjusted1 = notAdj1 * adjustment1;
    
    /* part 2: Pc1v */ 
    double notAdj2 = -Pc1v / delta;
    DoubleMatrix sigma2 = DoubleMatrix.ofUnsafe( // standard
        new double[][] {{1.0d, rho12 * c.get(6) / (Math.sqrt(c.get(1)) * c.get(4))},
            {rho12 * c.get(6) / (Math.sqrt(c.get(1)) * c.get(4)), 1.0d}});
    double adjustment2 = integrationConvexity(Math.sqrt(c.get(1)), c.get(4), sigma2);
    double convexityAdjusted2 = notAdj2 * adjustment2;

    /* part 3: Sc1j */
    double sqtheta = Math.sqrt(theta);
    double notAdj3 = spreadPvc1;
    DoubleMatrix sigma3 = DoubleMatrix.ofUnsafe(
        new double[][] {{1.0d, a * rho23 * c.get(7) / (Math.sqrt(c.get(1)) * a * sqtheta)},
            {a * rho23 * c.get(7) / (Math.sqrt(c.get(1)) * a * sqtheta), 1.0d}});
    double adjustment3 = integrationConvexity(Math.sqrt(c.get(1)), a * sqtheta, sigma3);
    double convexityAdjusted3 = notAdj3 * adjustment3;
    
    /* aggregation */
    double forwardAdjusted = Math.exp(0.5 * c.get(0) + 0.5 * c.get(1) - 0.5 * c.get(2))
        * (convexityAdjusted1 + convexityAdjusted2 + convexityAdjusted3) / Pc1v;
    return forwardAdjusted;
  }
  
  // 2D-integration numerical integration between the fixed bounds
  private double integration2D(BiFunction<Double, Double, Double> g) {
    double integral = 0.0;
    try {
      integral = integrator2D.integrate(g, INTEGRAL_BOUNDS_LOWER, INTEGRAL_BOUNDS_UPPER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return integral;
  }
  
  //TODO: version with 1D integration analytically or fully analytical

}
