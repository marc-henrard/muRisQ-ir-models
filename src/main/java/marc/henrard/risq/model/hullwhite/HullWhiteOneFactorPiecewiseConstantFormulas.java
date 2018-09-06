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

  /**
   * Returns the short rate variance between two times.
   * 
   * @param data  the Hull-White model data
   * @param startTime the start time
   * @param endTime  the end time
   * @return the short rate variance
   */
  public double shortRateVariance(
      HullWhiteOneFactorPiecewiseConstantParameters data, 
      double startTime, 
      double endTime) {
    
    double factor1 = Math.exp(- 2 * data.getMeanReversion() * endTime);
    double numerator = 2 * data.getMeanReversion();
    int indexStart = 1; // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    while (startTime > data.getVolatilityTime().get(indexStart)) {
      indexStart++;
    }
    int indexEnd = indexStart; // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    while (endTime > data.getVolatilityTime().get(indexEnd)) {
      indexEnd++;
    }
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startTime;
    System.arraycopy(data.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endTime;
    double factor2 = 0.0;
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      factor2 += data.getVolatility().get(loopperiod + indexStart - 1) *
          data.getVolatility().get(loopperiod + indexStart - 1) *
          (Math.exp(2 * data.getMeanReversion() * s[loopperiod + 1]) 
              - Math.exp(2 * data.getMeanReversion() * s[loopperiod]));
    }
    return factor1 * factor2 / numerator;
  }

  /**
   * Returns the model part of the short rate mean up to a given time.
   * <p>
   * This does not include the 
   * instantaneous forward (which is market data curve dependent only).
   * 
   * @param data  the Hull-White model data
   * @param endTime  the end time
   * @return the short rate mean (model part)
   */
  public double shortRateMeanModelPart(
      HullWhiteOneFactorPiecewiseConstantParameters data, 
      double endTime) {
    
    double kappa = data.getMeanReversion();
    double numerator = 2 * kappa * kappa;
    int indexStart = 1; // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = indexStart; // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    while (endTime > data.getVolatilityTime().get(indexEnd)) {
      indexEnd++;
    }
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = 0.0d;
    System.arraycopy(data.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endTime;
    double factor = 0.0;
    double[] exp2 = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      exp2[loopperiod] = 1.0 - Math.exp(-kappa * (endTime - s[loopperiod]));
      exp2[loopperiod] *= exp2[loopperiod]; // Square
    }
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      factor += data.getVolatility().get(loopperiod + indexStart - 1) *
          data.getVolatility().get(loopperiod + indexStart - 1) *
          (exp2[loopperiod] - exp2[loopperiod + 1]);
    }
    return factor / numerator;
  }

}
