/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.hullwhite;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.math.impl.rootfinding.BracketRoot;
import com.opengamma.strata.math.impl.rootfinding.RidderSingleRootFinder;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

/**
 * Formulas related to the Hull-White one-factor/extended Vasicek model with piecewise constant volatility.
 * 
 * @author Marc Henrard
 */
public class HullWhiteOneFactorPiecewiseConstantFormulas {
  
  /** The default instance of the formulas. */
  public final static HullWhiteOneFactorPiecewiseConstantFormulas DEFAULT = 
      new HullWhiteOneFactorPiecewiseConstantFormulas();
  
  // Private constructor
  private HullWhiteOneFactorPiecewiseConstantFormulas(){
  }

  /**
   * Calculates the volatility of the (zero-coupon) bond scaled by the collateral account
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
   * Calculates the volatility of the (zero-coupon) bond scaled by another (zero-coupon) bond, 
   * i.e. alpha, for a given period.
   * 
   * @param parameters  the Hull-White model parameters
   * @param startExpiry the start time of the expiry period
   * @param endExpiry  the end time of the expiry period
   * @param denominatorBondMaturity  the time to maturity for the bond at the denominator, often the numeraire
   * @param numeratorBondMaturity the time to maturity for the bond
   * @return the re-based bond volatility
   */
  public double alphaRatioDiscountFactors(
      HullWhiteOneFactorPiecewiseConstantParameters parameters,
      double startExpiry,
      double endExpiry,
      double denominatorBondMaturity,
      double numeratorBondMaturity) {

    double factor1 = Math.exp(-parameters.getMeanReversion() * denominatorBondMaturity) -
        Math.exp(-parameters.getMeanReversion() * numeratorBondMaturity);
    double numerator = 2 * parameters.getMeanReversion() * parameters.getMeanReversion() * parameters.getMeanReversion();
    int indexStart = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), startExpiry) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), endExpiry) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startExpiry;
    System.arraycopy(parameters.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endExpiry;
    double factor2 = 0d;
    double[] exp2as = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      exp2as[loopperiod] = Math.exp(2 * parameters.getMeanReversion() * s[loopperiod]);
    }
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      factor2 += parameters.getVolatility().get(loopperiod + indexStart - 1) *
          parameters.getVolatility().get(loopperiod + indexStart - 1) * (exp2as[loopperiod + 1] - exp2as[loopperiod]);
    }
    return factor1 * Math.sqrt(factor2 / numerator);
  }

  /**
   * Calculates the exercise boundary for swaptions.
   * <p>
   * Reference: Henrard, M. (2003). "Explicit bond option and swaption formula in Heath-Jarrow-Morton one-factor model". 
   * International Journal of Theoretical and Applied Finance, 6(1):57--72.
   * 
   * @param discountedCashFlow  the cash flow equivalent discounted to today
   * @param alpha  the zero-coupon bond volatilities
   * @return the exercise boundary
   */
  public double kappa(DoubleArray discountedCashFlow, DoubleArray alpha) {
    final Function<Double, Double> swapValue = new Function<Double, Double>() {
      @Override
      public Double apply(Double x) {
        double error = 0.0;
        for (int loopcf = 0; loopcf < alpha.size(); loopcf++) {
          error += discountedCashFlow.get(loopcf) *
              Math.exp(-0.5 * alpha.get(loopcf) * alpha.get(loopcf) - (alpha.get(loopcf) - alpha.get(0)) * x);
        }
        return error;
      }
    };
    BracketRoot bracketer = new BracketRoot();
    double accuracy = 1.0E-8;
    RidderSingleRootFinder rootFinder = new RidderSingleRootFinder(accuracy);
    double[] range = bracketer.getBracketedPoints(swapValue, -2.0, 2.0);
    return rootFinder.getRoot(swapValue, range[0], range[1]);
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

  /**
   * Computes the covariances for rates on different periods and different model parameters. 
   * Does not include the correlation between Brownian motions.
   * <p>
   * The volatility times must be the same for each parameters. This condition is not checked to avoid slow computations.
   * The method {@link HullWhiteOneFactorPiecewiseConstantFormulas#parametersCommonTimes} can be used to produced the 
   * required parameters sets.
   * <p>
   * Correspond to the computation of the integral
   *    \int_t0^t1 \nu_1(s,u_1) \nu_2(s,u_2) ds
   * where t0 = startIntegralTime, t1 = endIntegralTime
   * 
   * @param parameters  the Hull-White model parameters
   * @param startIntegralTime the end time for the first factor diffusion
   * @param endIntegralTime the end time for the second factor diffusion
   * @param u1 the maturity of the first factor
   * @param u2 the maturity of the second factor
   * @return the covariance
   */
  public double varianceCrossTermCashAccount(
      HullWhiteOneFactorPiecewiseConstantParameters parameters1, 
      HullWhiteOneFactorPiecewiseConstantParameters parameters2, 
      double startIntegralTime,
      double endIntegralTime,
      double u1,
      double u2) {
    
    double kappa1 = parameters1.getMeanReversion();
    double kappa2 = parameters2.getMeanReversion();
    int indexStart = Math.abs(Arrays.binarySearch(parameters1.getVolatilityTime().toArray(), startIntegralTime) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters1.getVolatilityTime().toArray(), endIntegralTime) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startIntegralTime;
    System.arraycopy(parameters1.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endIntegralTime;
    double[] expa1s = new double[sLen + 1];
    double[] expa2s = new double[sLen + 1];
    double[] expa1a2s = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      expa1s[loopperiod] = Math.exp(kappa1 * s[loopperiod]);
      expa2s[loopperiod] = Math.exp(kappa2 * s[loopperiod]);
      expa1a2s[loopperiod] = Math.exp((kappa1 + kappa2) * s[loopperiod]);
    }
    double term1 = 0.0d;
    double term2 = 0.0d;
    double term3 = 0.0d;
    double term4 = 0.0d;
    double factor1 = 1.0d / (kappa1 * kappa2);
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      double eta12 = parameters1.getVolatility().get(loopperiod + indexStart - 1) 
          * parameters2.getVolatility().get(loopperiod + indexStart - 1);
      term1 += eta12 * (s[loopperiod + 1] - s[loopperiod]);
      term2 += eta12 * (expa1s[loopperiod + 1] - expa1s[loopperiod]);
      term3 += eta12 * (expa2s[loopperiod + 1] - expa2s[loopperiod]);
      term4 += eta12 * (expa1a2s[loopperiod + 1] - expa1a2s[loopperiod]);
    }
    term2 *= -Math.exp(-kappa1 * u1) / kappa1;
    term3 *= -Math.exp(-kappa2 * u2) / kappa2;
    term4 *= Math.exp(-kappa1 * u1 - kappa2 * u2) / (kappa1 + kappa2);
    return factor1 * (term1 + term2 + term3 + term4);
  }
  
  /**
   * Computes the covariances for rates on with a HW model parameters and a constant volatility factor.
   * Does not include the correlation between Brownian motions.
   * <p>
   * Correspond to the computation of the integral
   *    \int_0^t \nu_1(s,u_1) ds
   * where t = endIntegralTime
   * 
   * @param parameters  the Hull-White model parameters
   * @param endIntegralTime the end time 
   * @param u1 the maturity 
   * @return the covariance
   */
  public double varianceCrossTermConstantVolCashAccount(
      HullWhiteOneFactorPiecewiseConstantParameters parameters,
      double startIntegralTime,
      double endIntegralTime,
      double u1) {
    
    double kappa = parameters.getMeanReversion();
    int indexStart = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), startIntegralTime) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), endIntegralTime) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startIntegralTime;
    System.arraycopy(parameters.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endIntegralTime;
    double[] expas = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      expas[loopperiod] = Math.exp(kappa * s[loopperiod]);
    }
    double term1 = 0.0d;
    double term2 = 0.0d;
    double factor1 = 1.0d / kappa;
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      double eta = parameters.getVolatility().get(loopperiod + indexStart - 1);
      term1 += eta * (s[loopperiod + 1] - s[loopperiod]);
      term2 += eta * (expas[loopperiod + 1] - expas[loopperiod]);
    }
    term2 *= -Math.exp(-kappa * u1) / kappa;
    return factor1 * (term1 + term2);
  }

  /**
   * From two sets of Hull-White parameters, returns equivalent sets but with common volatility times.
   * The volatility times of the outputs are the joint times of the inputs.
   * 
   * @param parameters1  the first Hull-White parameters
   * @param parameters2  the second Hull-White parameters
   * @return  the pair of equivalent models
   */
  public Pair<HullWhiteOneFactorPiecewiseConstantParameters, HullWhiteOneFactorPiecewiseConstantParameters>
      parametersCommonTimes(
          HullWhiteOneFactorPiecewiseConstantParameters parameters1,
          HullWhiteOneFactorPiecewiseConstantParameters parameters2) {

    double eps = 1.0E-4;
    double[] vol1 = parameters1.getVolatility().toArrayUnsafe();
    double[] vol2 = parameters2.getVolatility().toArrayUnsafe();
    DoubleArray time1 = parameters1.getVolatilityTime(); // Including 0 and 1000
    DoubleArray time2 = parameters2.getVolatilityTime(); // Including 0 and 1000
    DoubleArray timesJointSorted = time1.concat(time2.subArray(1, time2.size() - 1));
    timesJointSorted = timesJointSorted.sorted(); // sorting
    List<Double> timesJointSortedWithoutDuplicates = timesJointSorted
        .stream().distinct().boxed().collect(Collectors.toList());
    int nbTimes = timesJointSortedWithoutDuplicates.size();
    double[] vol1JointTimes = new double[nbTimes - 1];
    double[] vol2JointTimes = new double[nbTimes - 1];
    for (int loopt = 0; loopt < nbTimes - 1; loopt++) {
      int i1 = Math.abs(Arrays.binarySearch(time1.toArrayUnsafe(),
          timesJointSortedWithoutDuplicates.get(loopt + 1) - eps)) - 2;
      vol1JointTimes[loopt] = vol1[i1];
      int i2 = Math.abs(Arrays.binarySearch(time2.toArrayUnsafe(),
          timesJointSortedWithoutDuplicates.get(loopt + 1) - eps)) - 2;
      vol2JointTimes[loopt] = vol2[i2];
    }
    DoubleArray times = DoubleArray.copyOf(timesJointSortedWithoutDuplicates)
        .subArray(1, timesJointSortedWithoutDuplicates.size() - 1);
    HullWhiteOneFactorPiecewiseConstantParameters parameters1Equivalent =
        HullWhiteOneFactorPiecewiseConstantParameters.of(parameters1.getMeanReversion(),
            DoubleArray.ofUnsafe(vol1JointTimes), times);
    HullWhiteOneFactorPiecewiseConstantParameters parameters2Equivalent =
        HullWhiteOneFactorPiecewiseConstantParameters.of(parameters2.getMeanReversion(),
            DoubleArray.ofUnsafe(vol2JointTimes), times);
    return Pair.of(parameters1Equivalent, parameters2Equivalent);
  }

}
