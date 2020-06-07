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
