/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.model.hullwhite;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

public class HullWhiteOneFactorPiecewiseConstantMonteCarloUtils {

  /** Formulas related to the Hull-White one factor model. */
  private static final HullWhiteOneFactorPiecewiseConstantFormulas HW_FORMULAS = 
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  
  /**
   * Returns a set of paths for the zero-mean part of the short rate from a sample of normal distributions.
   * 
   * @param times  the sampling times
   * @param sample  standard normal distribution sample - dimensions: scenarios/times
   * @return the paths for the x variable - dimensions: scenarios/times
   */
  public static double[][] xPaths(
      double[] times,
      double[][] sample,
      HullWhiteOneFactorPiecewiseConstantParameters parameters) {

    int nbScenarios = sample.length; // TODO: check all samples have same size
    int nbTimes = sample[0].length;
    ArgChecker.isTrue(nbTimes == times.length, 
        "times length and inner dimension of sample must be equal");
    double[] times0 = new double[nbTimes + 1]; // Times extended with 0 at the start
    System.arraycopy(times, 0, times0, 1, nbTimes);
    double kappa = parameters.getMeanReversion();
    double[] expKappaTi = new double[nbTimes]; // exponential of time differences
    double[] stdSR = new double[nbTimes]; // standard deviation of short rate over time steps
    for (int looptime = 0; looptime < nbTimes; looptime++) {
      expKappaTi[looptime] = Math.exp(-kappa * (times0[looptime + 1] - times0[looptime]));
      stdSR[looptime] = Math.sqrt(
          HW_FORMULAS.shortRateVariance(parameters, times0[looptime], times0[looptime+1]));
    }
    double[][] xTmp = new double[nbScenarios][nbTimes + 1]; // The values of x with 0 value at time 0
    for (int loopsc = 0; loopsc < nbScenarios; loopsc++) {
      for (int looptime = 0; looptime < nbTimes; looptime++) {
        xTmp[loopsc][looptime + 1] =
            expKappaTi[looptime] * xTmp[loopsc][looptime] 
                + stdSR[looptime] * sample[loopsc][looptime];
      }
    }
    double[][] x = new double[nbScenarios][nbTimes]; // The values of x
    for (int loopsc = 0; loopsc < nbScenarios; loopsc++) {
      System.arraycopy(xTmp[loopsc], 1, x[loopsc], 0, nbTimes);
    }
    return x;
  }
  
}
