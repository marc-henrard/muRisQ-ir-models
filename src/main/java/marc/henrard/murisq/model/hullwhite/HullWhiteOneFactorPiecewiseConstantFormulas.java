/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.hullwhite;

import java.util.Arrays;

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
   * Calculates the volatility of the (zero-coupon) bond scaled by the cash account
   * numeraire, i.e. alpha, for a given period.
   * 
   * @param parameters  the Hull-White model parameters
   * @param startExpiry the start time of the expiry period
   * @param endExpiry  the end time of the expiry period
   * @param bondMaturity the time to maturity for the bond
   * @return the re-based bond volatility
   */
  public double alphaCashAccount(
      HullWhiteOneFactorPiecewiseConstantParameters parameters,
      double startExpiry,
      double endExpiry,
      double bondMaturity) {

    double kappa = parameters.getMeanReversion();
    double factor1_1 = Math.exp(-2 * kappa * bondMaturity) / (2 * kappa);
    double factor2_1 = -2 * Math.exp(-kappa * bondMaturity) / kappa;
    int indexStart = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), startExpiry) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), endExpiry) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startExpiry;
    System.arraycopy(parameters.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endExpiry;
    double[] expas = new double[sLen + 1];
    double[] exp2as = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      expas[loopperiod] = Math.exp(kappa * s[loopperiod]);
      exp2as[loopperiod] = expas[loopperiod] * expas[loopperiod];
    }
    double factor1_2 = 0d;
    double factor2_2 = 0d;
    double factor3_2 = 0d;
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      double eta2 = parameters.getVolatility().get(loopperiod + indexStart - 1) *
          parameters.getVolatility().get(loopperiod + indexStart - 1);
      factor1_2 += eta2 * (exp2as[loopperiod + 1] - exp2as[loopperiod]);
      factor2_2 += eta2 * (expas[loopperiod + 1] - expas[loopperiod]);
      factor3_2 += eta2 * (s[loopperiod + 1] - s[loopperiod]);
    }
    return Math.sqrt(factor1_1 * factor1_2 + factor2_1 * factor2_2 + factor3_2) / kappa;
  }
  
  /**
   * Correspond to \int_s^e g^2(s) ds = \int_s^e \eta^2(s) exp(2\kappa s) ds
   * 
   * @param data  the Hull-White model data
   * @param startExpiry the start time of the expiry period
   * @param endExpiry  the end time of the expiry period
   * @return  the integral value
   */
  public double alpha2ForwardGPart(
      HullWhiteOneFactorPiecewiseConstantParameters parameters,
      double startExpiry,
      double endExpiry) {
    
    double kappa = parameters.getMeanReversion();
    int indexStart = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), startExpiry) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), endExpiry) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startExpiry;
    System.arraycopy(parameters.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endExpiry;
    double[] exp2as = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      double expas = Math.exp(kappa * s[loopperiod]);
      exp2as[loopperiod] = expas * expas;
    }
    double alphaGPart = 0d;
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      double eta2 = parameters.getVolatility().get(loopperiod + indexStart - 1) *
          parameters.getVolatility().get(loopperiod + indexStart - 1);
      alphaGPart += eta2 * (exp2as[loopperiod + 1] - exp2as[loopperiod]);
    }
    return alphaGPart / (2 * kappa);
  }
  
  /**
   * Calculates the adjustment factor to be used for timing adjustment.
   * <p>
   * This is the factor for the payment of the rate for the period [s,t] paid in v.
   * <p>
   * The factor is called gamma in the reference: 
   * Henrard, M. "A quant perspective on IBOR fallback proposals", 
   *   Market Infrastructure Analysis, muRisQ Advisory, September 2018
   * The factor is defined as 
   *   gamma(s,t,v) = 1/\kappa^2 (\exp(-\kappa s)-\exp(-\kappa t)) (\exp(-\kappa v)-\exp(-\kappa t)) \int_x^{s} g^2(u)du
   * 
   * @param parameters  the Hull-White model parameters
   * @param s  the start time of the period on which the rate is measured
   * @param t  the end time of the period on which the rate is measured
   * @param v  the time when the rate is paid
   * @return the factor
   */
  public double timingAdjustmentFactor(
      HullWhiteOneFactorPiecewiseConstantParameters parameters,
      double s,
      double t,
      double v) {
    
    double kappa = parameters.getMeanReversion();
    double gamma = (Math.exp(-kappa * s) - Math.exp(-kappa * t)) 
        * (Math.exp(-kappa * v) - Math.exp(-kappa * t)) 
        / (kappa * kappa) 
        * alpha2ForwardGPart(parameters, 0, s);
    return Math.exp(gamma);
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
   * <p>
   * This is the variance of x(endTime) as a function of x(startTime) with 
   * r(t) = \Theta(t) + x(t).
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
  
  /**
   * Computes the crosses component of the variance used for rates on different periods.
   * <p>
   * Correspond to the computation of the integral
   *    \int_0^t (\nu(s,t_2)-\nu(s,t_1))(\nu(s,t_4)-\nu(s,t_3)) ds
   * where t = endIntegralTime
   * 
   * @param parameters  the Hull-White model parameters
   * @param endIntegralTime the end time for the integration period
   * @param t1 the start time for the first cross
   * @param t2 the end time for the first cross
   * @param t3 the start time for the second cross
   * @param t4 the end time for the second cross
   * @return the variance contribution
   */
  public double varianceCrossTerm(
      HullWhiteOneFactorPiecewiseConstantParameters parameters, 
      double endIntegralTime,
      double t1,
      double t2,
      double t3,
      double t4) {

    double kappa = parameters.getMeanReversion();
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArrayUnsafe(), endIntegralTime) + 1);
    // Period in which the time endIntegralTime is; volatilityTime.get(i-1) <= endIntegralTime < volatilityTime.get(i);
    double[] s = new double[indexEnd + 1];
    System.arraycopy(parameters.getVolatilityTime().toArrayUnsafe(), 0, s, 0, indexEnd);
    s[indexEnd] = endIntegralTime;
    double[] exp2as = new double[indexEnd + 1];
    for (int loopperiod = 0; loopperiod < indexEnd + 1; loopperiod++) {
      exp2as[loopperiod] = Math.exp(2 * kappa * s[loopperiod]);
    }
    double factor1 = 0.0d;
    for (int loopperiod = 0; loopperiod < indexEnd; loopperiod++) {
      double eta2 = parameters.getVolatility().get(loopperiod) * parameters.getVolatility().get(loopperiod);
      factor1 += eta2 * (exp2as[loopperiod + 1] - exp2as[loopperiod]);
    }
    double factor2 = (Math.exp(-kappa * t1) - Math.exp(-kappa * t2)) 
        * (Math.exp(-kappa * t3) - Math.exp(-kappa * t4)) / (2 * kappa * kappa * kappa);
    return factor1 * factor2;
  }

}
