/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.model.hullwhite;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

/**
 * Formulas related to the Hull-White one-factor/extended Vasicek model with piecewise constant volatility.
 */
public class HullWhiteOneFactorPiecewiseConstantFormulas {
  
  /** The default instance of the formulas. */
  public final static HullWhiteOneFactorPiecewiseConstantFormulas DEFAULT = 
      new HullWhiteOneFactorPiecewiseConstantFormulas();
  
  // Private constructor
  private HullWhiteOneFactorPiecewiseConstantFormulas(){
  }

  /**
   * Calculates the future convexity factor used in future pricing.
   * <p>
   * The factor is called gamma in the reference: 
   * Henrard, M. "The Irony in the derivatives discounting Part II: the crisis", Wilmott Journal, 2010, 2, 301-316
   * The factor is defined as 
   *   gamma(s,t,u,v) = exp( \int_s^t nu(x,v)(nu(x,v)-nu(x,u)) dx )
   * 
   * @param parameters  the Hull-White model parameters
   * @param s  the start integration time
   * @param t  the end integration time
   * @param u  the start period time
   * @param v  the end period time
   * @return the factor
   */
  public double futuresConvexityFactor(
      HullWhiteOneFactorPiecewiseConstantParameters parameters,
      double s,
      double t,
      double u,
      double v) {

    ArgChecker.isTrue(s <= t, "start integration time must be before end integration time");
    double a = parameters.getMeanReversion();
    double factor1 = Math.exp(- a * u) - Math.exp(-a * v);
    double numerator = 2 * a * a * a;
    int indexS = 1; // Period in which the time s is; volatilityTime[i-1] <= s < volatilityTime[i];
    while (s > parameters.getVolatilityTime().get(indexS)) {
      indexS++;
    }
    int indexT = 1; // Period in which the time t is; volatilityTime[i-1] <= t < volatilityTime[i];
    while (t > parameters.getVolatilityTime().get(indexT)) {
      indexT++;
    }
    double[] q = new double[indexT - indexS + 2];
    q[0] = s;
    System.arraycopy(parameters.getVolatilityTime().toArray(), indexS, q, 1, indexT - indexS);
    q[indexT - indexS + 1] = t;
    double factor2 = 0.0;
    for (int loopperiod = 0; loopperiod < indexT - indexS + 1; loopperiod++) {
      factor2 += parameters.getVolatility().get(loopperiod + indexS - 1) 
          * parameters.getVolatility().get(loopperiod + indexS - 1) *
          (Math.exp(a * q[loopperiod + 1]) - Math.exp(a * q[loopperiod])) *
          (2 - Math.exp(-a * (v - q[loopperiod + 1])) - Math.exp(-a * (v - q[loopperiod])));
    }
    return Math.exp(factor1 / numerator * factor2);
  }

}
