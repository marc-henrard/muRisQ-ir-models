/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.model.hullwhite;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

/**
 * Tests {@link HullWhiteOneFactorPiecewiseConstantMonteCarloUtils}.
 * 
 * @author Marc Henrard
 */
@Test
public class HullWhiteOneFactorPiecewiseConstantMonteCarloUtilsTest {

  private static final double MEAN_REVERSION = 0.03;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.015, 0.011, 0.012, 0.013, 0.014, 0.016);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(0.5, 1.0, 2.0, 4.0, 5.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  
  private static final HullWhiteOneFactorPiecewiseConstantFormulas HW_FORMULAS = 
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;

  /* Test xPath v a local implementation */
  public void xPaths_local() {
    double[] times = {1.0d, 1.5d, 2.0d, 5.0d, 10.0d};
    int nbTimes = times.length;
    double[][] sample = {{0.5, 0.5, 0.5, 0.5, 0.5},
        {-1.0, -0.5, 0.0, 0.5, 1.0},
        {-0.5, -1.0, 0.5, 0.0, -0.5}};
    int nbScenarios = sample.length;
    double[] times0 = {0.0d, 1.0d, 1.5d, 2.0d, 5.0d, 10.0d};
    double[][] xPathsComputed =
        HullWhiteOneFactorPiecewiseConstantMonteCarloUtils.xPaths(times, sample, MODEL_PARAMETERS);
    double[] expKappaTi = new double[nbTimes]; // exponential of time differences
    double[] stdSR = new double[nbTimes]; // standard deviation of short rate over time steps
    for (int looptime = 0; looptime < nbTimes; looptime++) {
      expKappaTi[looptime] = Math.exp(-MEAN_REVERSION * (times0[looptime + 1] - times0[looptime]));
      stdSR[looptime] = Math.sqrt(
          HW_FORMULAS.shortRateVariance(MODEL_PARAMETERS, times0[looptime], times0[looptime + 1]));
    }
    for (int loopsc = 0; loopsc < nbScenarios; loopsc++) {
      double[] x = new double[nbTimes + 1];
      for (int looptime = 0; looptime < nbTimes; looptime++) {
        x[looptime + 1] =
            expKappaTi[looptime] * x[looptime] + stdSR[looptime] * sample[loopsc][looptime];
        assertEquals(xPathsComputed[loopsc][looptime], x[looptime + 1]);
      }
    }
  }
  
}
