/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import java.time.LocalDate;
import java.util.function.BiFunction;

import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.integration.Integrator2D;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;

import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

public class DiscountingTransition2HWHybridConvexityCalculator {

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
  /* Matrix algebra */
  private static final MatrixAlgebra ALGEBRA = new CommonsMatrixAlgebra();

  /** The default instance */
  public static final DiscountingTransition2HWHybridConvexityCalculator DEFAULT = 
      new DiscountingTransition2HWHybridConvexityCalculator(INTEGRATOR_2D_DEFAULT);
  
  private final Integrator2D<Double, Double> integrator2D;
  
  /**
   * Creates an instance with a given 2D integrator.
   * 
   * @param integrator2D the integrator 
   */
  public DiscountingTransition2HWHybridConvexityCalculator(Integrator2D<Double, Double> integrator2D) {
    this.integrator2D = integrator2D;
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
      HullWhiteOneFactorPiecewiseConstantParametersProvider hw1,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hw2) {
    
    ArgChecker.isTrue(hw1.getDayCount().equals(hw2.getDayCount()), "both HW provider must have the same day-count");
    double t = hw1.relativeTime(transitionDate);
    double theta = hw1.relativeTime(obsIbor.getFixingDate());
    double u = hw1.relativeTime(obsIbor.getEffectiveDate());
    double v = hw1.relativeTime(obsIbor.getMaturityDate());
    HullWhiteOneFactorPiecewiseConstantParameters parameters1 = hw1.getParameters();
    HullWhiteOneFactorPiecewiseConstantParameters parameters2 = hw2.getParameters();
    double alpha1tv = FORMULAS_HW.alphaCashAccount(parameters1, 0, t, v);
    double alpha2tv = FORMULAS_HW.alphaCashAccount(parameters2, 0, t, v);
    double alpha2thetau = FORMULAS_HW.alphaCashAccount(parameters2, 0, theta, u);
    double alpha2thetav = FORMULAS_HW.alphaCashAccount(parameters2, 0, theta, v);
    double cross12tvv = FORMULAS_HW.varianceCrossTermCashAccount(parameters1, parameters2, 0, t, v, v);
    double cross12tvu = FORMULAS_HW.varianceCrossTermCashAccount(parameters1, parameters2, 0, t, v, u);
    double cross22tvu = FORMULAS_HW.varianceCrossTermCashAccount(parameters2, parameters2, 0, t, v, u);
    double cross22tvv = FORMULAS_HW.varianceCrossTermCashAccount(parameters2, parameters2, 0, t, v, v);
    double cross13tv = FORMULAS_HW.varianceCrossTermConstantVolCashAccount(parameters1, 0, t, v);
    double cross23tv = FORMULAS_HW.varianceCrossTermConstantVolCashAccount(parameters2, 0, t, v);
    return DoubleArray.ofUnsafe(new double[]{alpha1tv, alpha2tv, alpha2thetau, alpha2thetav, 
        cross12tvv, cross12tvu, cross22tvu, cross22tvv, cross13tv, cross23tv});
  }
  
  public double convexityAdjustmentNI(
      DoubleMatrix sigma,
      double vol1,
      double vol2,
      double vol3) {

    DoubleMatrix sigma_1 = ALGEBRA.getInverse(sigma);
    double detSigma1 = ALGEBRA.getDeterminant(sigma);
    double rho11 = sigma_1.get(0, 0);
    double rho21 = sigma_1.get(1, 0);
    double rho31 = sigma_1.get(2, 0);
    DoubleMatrix sigma1_1_reduction = DoubleMatrix.ofUnsafe(new double[][] 
        {{sigma_1.get(1, 1), sigma_1.get(1, 2)},
        {sigma_1.get(2, 1), sigma_1.get(2, 2)}});
    double omega = 1.0;
    double b = vol1;
    BiFunction<Double, Double, Double> c1fd =
        (x2, x3) -> Math.exp(-0.5 * 
            (x2 * x2 * sigma1_1_reduction.get(0, 0) 
                + 2 * x2 * x3 * sigma1_1_reduction.get(0, 1) 
                + x3 * x3 * sigma1_1_reduction.get(1, 1)
                + omega * b * b 
                - Math.pow(omega * b + rho21 * x2 + rho31 * x3, 2) / rho11)
            + x2 * vol2 + 0.5 * vol2 * vol2 - x3 * vol3 - 0.5 * vol3 * vol3
            );
    double adj1 = 1.0 / (Math.sqrt(detSigma1 * rho11) * 2 * Math.PI);
    double adj2 = integration2D(c1fd);
    double adjTot = adj1 * adj2;
    return adjTot;
  }
  
  private double integration2D(BiFunction<Double, Double, Double> g) {
    double integral = 0.0;
    try {
      integral = integrator2D.integrate(g, INTEGRAL_BOUNDS_LOWER, INTEGRAL_BOUNDS_UPPER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return integral;
  }

}
