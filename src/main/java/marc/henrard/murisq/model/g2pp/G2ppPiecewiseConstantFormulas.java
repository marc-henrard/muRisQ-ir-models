/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.g2pp;

import java.util.Arrays;

import com.opengamma.strata.collect.array.DoubleArray;

/**
 * Formulas related to the G2++ model with piecewise constant volatilities.
 * <i>Implementation Reference: </i>
 * <p>
 * Henrard, M. G2++ two-factor model. Model implementation documentation. muRisQ documentation. 
 *   First version: 28 December 2009; latest version: 5 June 2020.
 * 
 * @author Marc Henrard
 */
public class G2ppPiecewiseConstantFormulas {
  
  /** The default instance of the formulas. */
  public final static G2ppPiecewiseConstantFormulas DEFAULT = 
      new G2ppPiecewiseConstantFormulas();
  
  // Private constructor
  private G2ppPiecewiseConstantFormulas(){
  }

  /**
   * The maturity dependent part of the volatility (function called H in the implementation note).
   * 
   * @param parameters  the G2pp model parameters
   * @param u  the start time
   * @param v  the end time
   * @return the volatilities
   */
  public double[] volatilityMaturityPart(G2ppPiecewiseConstantParameters parameters, double u, double v) {
    double[] a = parameters.getMeanReversions();
    double[] result = new double[2];
    double expa0u = Math.exp(-a[0] * u);
    double expa1u = Math.exp(-a[1] * u);
    result[0] = (expa0u - Math.exp(-a[0] * v)) / a[0];
    result[1] = (expa1u - Math.exp(-a[1] * v)) / a[1];
    return result;
  }

  /**
   * The maturity dependent part of the volatility (function called H in the implementation note).
   * 
   * @param parameters  the G2pp model parameters
   * @param u  the start time
   * @param v  the end times
   * @return the volatilities, dimensions: model factors(2)/times
   */
  public double[][] volatilityMaturityPart(G2ppPiecewiseConstantParameters parameters, double u, double[] v) {
    double[] a = parameters.getMeanReversions();
    double[][] result = new double[2][v.length];
    double expa0u = Math.exp(-a[0] * u);
    double expa1u = Math.exp(-a[1] * u);
    for (int loopcf = 0; loopcf < v.length; loopcf++) {
      result[0][loopcf] = (expa0u - Math.exp(-a[0] * v[loopcf])) / a[0];
      result[1][loopcf] = (expa1u - Math.exp(-a[1] * v[loopcf])) / a[1];
    }
    return result;
  }

  /**
   * The expiry time dependent part of the volatility.
   * 
   * @param parameters  the G2pp model parameters
   * @param startExpiry  the start expiry time
   * @param endExpiry  the end expiry time
   * @return the volatilities
   */
  public double[][] gamma(G2ppPiecewiseConstantParameters parameters, double startExpiry, double endExpiry) {
    double[] a = parameters.getMeanReversions();
    double[] volTimes = parameters.getVolatilityTime().toArrayUnsafe();

    double[][] sigma = new double[2][]; // dimensions: factor/time
    sigma[0] = parameters.getVolatility1().toArrayUnsafe();
    sigma[1] = parameters.getVolatility2().toArrayUnsafe();

    // indices
    int indexStart = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), startExpiry) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), endExpiry) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    double[] s = new double[sLen + 1];
    s[0] = startExpiry;
    System.arraycopy(volTimes, indexStart, s, 1, sLen - 1);
    s[sLen] = endExpiry;
    double[] gammaii = new double[2];
    double gamma12 = 0.0;
    double[][] exp2ais = new double[sLen + 1][2]; // dimension: periods/factors
    double[] expa0a1s = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      for (int loopfactor = 0; loopfactor < 2; loopfactor++) {
        exp2ais[loopperiod][loopfactor] = Math.exp(2 * a[loopfactor] * s[loopperiod]);
      }
      expa0a1s[loopperiod] = Math.exp((a[0] + a[1]) * s[loopperiod]);
    }
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      for (int loopfactor = 0; loopfactor < 2; loopfactor++) {
        gammaii[loopfactor] += sigma[loopfactor][indexStart - 1 + loopperiod] 
            * sigma[loopfactor][indexStart - 1 + loopperiod] 
                * (exp2ais[loopperiod + 1][loopfactor] - exp2ais[loopperiod][loopfactor]);
      }
      gamma12 += sigma[0][indexStart - 1 + loopperiod] 
          * sigma[1][indexStart - 1 + loopperiod] 
              * (expa0a1s[loopperiod + 1] - expa0a1s[loopperiod]);
    }
    double[][] result = new double[2][2];
    result[0][0] = gammaii[0] / (2 * a[0]);
    result[1][1] = gammaii[1] / (2 * a[1]);
    result[1][0] = gamma12 / (a[0] + a[1]);
    result[0][1] = result[1][0];
    return result;
  }
  
  /**
   * Calculates the volatility of the (zero-coupon) bond scaled by the collateral account,
   *  i.e. alphas, for a given period.
   * <p>
   * The results is a array with 3 items: alpha_{N,1}^2(s,t,u), alpha_{N,2}^2(s,t,u), alpha_{N}^2(s,t,u) 
   * 
   * @param parameters  the G2pp model parameters
   * @param startExpiry the start time of the expiry period
   * @param endExpiry  the end time of the expiry period
   * @param maturity the time to maturity for the pseudo-discount factor
   * @return the re-based pseudo-discount factor volatility
   */
  public DoubleArray alphaCollateralAccount(
      G2ppPiecewiseConstantParameters parameters,
      double startExpiry,
      double endExpiry,
      double maturity) {

    double[] kappa = {parameters.getKappa1(), parameters.getKappa2()};
    double rho = parameters.getCorrelation();
    // indices
    int indexStart = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), startExpiry) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters.getVolatilityTime().toArray(), endExpiry) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    // common factors
    double[] s = new double[sLen + 1];
    s[0] = startExpiry;
    System.arraycopy(parameters.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endExpiry;
    double[][] expais = new double[sLen + 1][2];
    double[][] exp2ais = new double[sLen + 1][2];
    double[] expa1a2s = new double[sLen + 1];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      for (int i = 0; i < 2; i++) {
        expais[loopperiod][i] = Math.exp(kappa[i] * s[loopperiod]);
        exp2ais[loopperiod][i] = expais[loopperiod][i] * expais[loopperiod][i];
      }
      expa1a2s[loopperiod] = expais[loopperiod][0] * expais[loopperiod][1];
    }
    double[] expaiu = new double[2];
    double[] exp2aiu = new double[2];
    for (int i = 0; i < 2; i++) {
      expaiu[i] = Math.exp(-kappa[i] * maturity);
      exp2aiu[i] = expaiu[i] * expaiu[i];
    }
    double expa1a2u = expaiu[0] * expaiu[1];
    // square terms
    double[] termii_1 = new double[2];
    double[] termii_2 = new double[2];
    double[] termii_3 = new double[2];
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      double[] etaii2 = new double[2];
      etaii2[0] = parameters.getVolatility1().get(loopperiod + indexStart - 1) *
          parameters.getVolatility1().get(loopperiod + indexStart - 1);
      etaii2[1] = parameters.getVolatility2().get(loopperiod + indexStart - 1) *
          parameters.getVolatility2().get(loopperiod + indexStart - 1);
      for (int i = 0; i < 2; i++) {
        termii_1[i] += etaii2[i] * (s[loopperiod + 1] - s[loopperiod]);
        termii_2[i] += etaii2[i] * (expais[loopperiod + 1][i] - expais[loopperiod][i]);
        termii_3[i] += etaii2[i] * (exp2ais[loopperiod + 1][i] - exp2ais[loopperiod][i]);
      }
    }
    double[] alphaij = new double[3];
    for (int i = 0; i < 2; i++) {
      alphaij[i] = 1.0d / (kappa[i] * kappa[i]) *
          (termii_1[i] - 2.0 / kappa[i] * expaiu[i] * termii_2[i] + exp2aiu[i] * termii_3[i] / (2.0 * kappa[i]));
    }
    // cross term
    alphaij[2] = alphaij[0] + alphaij[1];
    double term12_1 = 0.0d;
    double term12_2 = 0.0d;
    double term12_3 = 0.0d;
    double term12_4 = 0.0d;
    for (int loopperiod = 0; loopperiod < sLen; loopperiod++) {
      double eta12 = parameters.getVolatility1().get(loopperiod + indexStart - 1) *
          parameters.getVolatility2().get(loopperiod + indexStart - 1);
      term12_1 += eta12 * (s[loopperiod + 1] - s[loopperiod]);
      term12_2 += eta12 * (expais[loopperiod + 1][0] - expais[loopperiod][0]);
      term12_3 += eta12 * (expais[loopperiod + 1][1] - expais[loopperiod][1]);
      term12_4 += eta12 * (expa1a2s[loopperiod + 1] - expa1a2s[loopperiod]);
    }
    alphaij[2] += 2.0d * rho / (kappa[0] * kappa[1]) *
        (term12_1 - expaiu[0] / kappa[0] * term12_2 - expaiu[1] / kappa[1] * term12_3 
            + expa1a2u / (kappa[0] + kappa[1]) * term12_4);
    return DoubleArray.ofUnsafe(alphaij);
  }

}
