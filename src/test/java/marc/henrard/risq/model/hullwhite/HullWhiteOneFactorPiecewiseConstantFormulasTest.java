/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.model.hullwhite;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.impl.rate.model.HullWhiteOneFactorPiecewiseConstantInterestRateModel;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

/**
 * Tests {@link HullWhiteOneFactorPiecewiseConstantFormulas}.
 * 
 * @author Marc Henrard
 */
@Test
public class HullWhiteOneFactorPiecewiseConstantFormulasTest {

  private static final double MEAN_REVERSION = 0.03;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.015, 0.011, 0.012, 0.013, 0.014, 0.016);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(0.5, 1.0, 2.0, 4.0, 5.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  private static final HullWhiteOneFactorPiecewiseConstantInterestRateModel MODEL_STD =
      HullWhiteOneFactorPiecewiseConstantInterestRateModel.DEFAULT;

  private static final double TOLERANCE_CONVEXITY_FACTOR = 1E-8;

  /* Start in 0, end before first pillar. */
  public void futuresConvexityFactorSimpleStart() {
    double s = 0.0;
    double t = 0.20; // Before first pillar
    double u = 2.00;
    double v = 2.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double factorExpected1 = MODEL_STD.futuresConvexityFactor(MODEL_PARAMETERS, t, u, v);
    assertEquals(factorComputed, factorExpected1, TOLERANCE_CONVEXITY_FACTOR);
    double a = MODEL_PARAMETERS.getMeanReversion();
    double factor1 = Math.exp(-a * u) - Math.exp(-a * v);
    double numerator = 2 * a * a * a;
    double factor2 = VOLATILITY.get(0) * VOLATILITY.get(0) *
        (Math.exp(a * t) - Math.exp(a * s)) *
        (2 - Math.exp(-a * (v - t)) - Math.exp(-a * (v - s)));
    double factorExpected2 = Math.exp(factor1 / numerator * factor2);
    assertEquals(factorComputed, factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Start and end in the same volatility period. */
  public void futuresConvexityFactorSimpleMiddle() {
    double s = 1.10;
    double t = 1.50;
    double u = 2.00;
    double v = 2.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double a = MODEL_PARAMETERS.getMeanReversion();
    double factor1 = Math.exp(-a * u) - Math.exp(-a * v);
    double numerator = 2 * a * a * a;
    double factor2 = VOLATILITY.get(2) * VOLATILITY.get(2) *
        (Math.exp(a * t) - Math.exp(a * s)) *
        (2 - Math.exp(-a * (v - t)) - Math.exp(-a * (v - s)));
    double factorExpected2 = Math.exp(factor1 / numerator * factor2);
    assertEquals(factorComputed, factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Adjustment period covering several volatility periods, starting in 0. */
  public void futuresConvexityFactorMultipleStart() {
    double s = 0.0;
    double t = 2.50;
    double u = 3.00;
    double v = 3.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double factorExpected1 = MODEL_STD.futuresConvexityFactor(MODEL_PARAMETERS, t, u, v);
    assertEquals(factorComputed, factorExpected1, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Adjustment period covering several volatility periods, starting at an intermediary date. */
  public void futuresConvexityFactorMultipleMiddle() {
    double s = 1.10;
    double t = 4.50;  // After last pillar
    double u = 5.00;
    double v = 5.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double a = MODEL_PARAMETERS.getMeanReversion();
    double factor1 = Math.exp(-a * u) - Math.exp(-a * v);
    double numerator = 2 * a * a * a;
    double factor2 = 0.0;
    double[] q = {s, 2.0, 4.0, t};
    for (int loopperiod = 0; loopperiod < 3; loopperiod++) {
      factor2 += VOLATILITY.get(loopperiod + 2) * VOLATILITY.get(loopperiod + 2) *
          (Math.exp(a * q[loopperiod + 1]) - Math.exp(a * q[loopperiod])) *
          (2 - Math.exp(-a * (v - q[loopperiod + 1])) - Math.exp(-a * (v - q[loopperiod])));
    }
    double factorExpected2 = Math.exp(factor1 / numerator * factor2);
    assertEquals(factorComputed, factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Adjustment period covering several volatility periods, starting at an intermediary date 
   * and end after the last volatility pillar */
  public void futuresConvexityFactorMultipleEnd() {
    double s = 1.10;
    double t = 5.50; // Before first pillar
    double u = 6.00;
    double v = 6.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double a = MODEL_PARAMETERS.getMeanReversion();
    double factor1 = Math.exp(-a * u) - Math.exp(-a * v);
    double numerator = 2 * a * a * a;
    double factor2 = 0.0;
    double[] q = {s, 2.0, 4.0, 5.0, t};
    for (int loopperiod = 0; loopperiod < 4; loopperiod++) {
      factor2 += VOLATILITY.get(loopperiod + 2) * VOLATILITY.get(loopperiod + 2) *
          (Math.exp(a * q[loopperiod + 1]) - Math.exp(a * q[loopperiod])) *
          (2 - Math.exp(-a * (v - q[loopperiod + 1])) - Math.exp(-a * (v - q[loopperiod])));
    }
    double factorExpected2 = Math.exp(factor1 / numerator * factor2);
    assertEquals(factorComputed, factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }
  
}
