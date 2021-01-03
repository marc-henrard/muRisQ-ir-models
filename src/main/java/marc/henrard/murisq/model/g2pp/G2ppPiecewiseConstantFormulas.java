/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.g2pp;

import java.util.Arrays;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;

import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Formulas related to the G2++ model with piecewise constant volatilities.
 * <i>Implementation Reference: </i>
 * <p>
 * Henrard, M. G2++ two-factor model. Model implementation documentation. muRisQ documentation. 
 *   First version: 28 December 2009; latest version: 2 January 2021.
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
   * <p>
   * The two results provided are H_i(v) - H_i(u)
   * 
   * @param parameters  the G2pp model parameters
   * @param u  the start time
   * @param v  the end time
   * @return the volatilities
   */
  public double[] volatilityMaturityPartRatioDiscountFactors(G2ppPiecewiseConstantParameters parameters, double u, double v) {
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
   * <p>
   * The two results provided are H_i(v) - H_i(u)
   * 
   * @param parameters  the G2pp model parameters
   * @param u  the start time
   * @param v  the end times
   * @return the volatilities, dimensions: model factors(2)/times
   */
  public double[][] volatilityMaturityPartRatioDiscountFactors(G2ppPiecewiseConstantParameters parameters, double u, double[] v) {
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
   * The expiry time dependent part of the volatility of a ratio of discount factors.
   * <p>
   * The numbers returned correspond to the quantities \gamma_{i,j} in Section 4.1 in the reference implementation document.
   * 
   * @param parameters  the G2pp model parameters
   * @param startExpiry  the start expiry time
   * @param endExpiry  the end expiry time
   * @return the volatilities
   */
  public double[][] gammaRatioDiscountFactors(
      G2ppPiecewiseConstantParameters parameters, 
      double startExpiry, 
      double endExpiry) {
    
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
   * Calculates the volatility of the ratio of pseudo-discount factors, i.e. alphas in the implementation reference, 
   * on a given period.
   * <p>
   * The results is a array with 3 items: alpha_{1}^2(s,t,u,v), alpha_{2}^2(s,t,u,v), alpha^2(s,t,u,v) 
   * from Section 4.1 in the reference implementation document.
   * 
   * @param parameters  the G2pp model parameters
   * @param startExpiry the start time of the expiry period
   * @param endExpiry  the end time of the expiry period
   * @param denominatorBondMaturity the time to maturity for the denominator pseudo-discount factor
   * @param numeratorBondMaturity the time to maturity for the numerator pseudo-discount factor
   * @return the ratio of pseudo-discount factors volatility
   */
  public DoubleArray alphaRatioDiscountFactors(
      G2ppPiecewiseConstantParameters parameters,
      double startExpiry,
      double endExpiry,
      double denominatorBondMaturity, // u
      double numeratorBondMaturity) { // v

    double[] h = volatilityMaturityPartRatioDiscountFactors(parameters, denominatorBondMaturity, numeratorBondMaturity);
    double[][] gamma = gammaRatioDiscountFactors(parameters, startExpiry, endExpiry);
    double alpha12 = h[0] * h[0] * gamma[0][0];
    double alpha22 = h[1] * h[1] * gamma[1][1];
    double tau2 = alpha12 + alpha22 + 2 * parameters.getCorrelation() * gamma[0][1] * h[0] * h[1];
    return DoubleArray.of(alpha12, alpha22, tau2);
  }
  
  /**
   * Calculates the volatility of the (zero-coupon) bond scaled by the collateral account,
   *  i.e. alphas, for a given period.
   * <p>
   * The results is a array with 3 items: bar alpha_{1}^2(s,t,u), bar alpha_{2}^2(s,t,u), bar alpha^2(s,t,u) 
   * from Section 4.2 in the reference implementation document.
   * 
   * @param parameters  the G2pp model parameters
   * @param startExpiry the start time of the expiry period
   * @param endExpiry  the end time of the expiry period
   * @param maturity the time to maturity for the pseudo-discount factor
   * @return the re-based pseudo-discount factor volatility
   */
  public DoubleArray alphaCollateralAccountDiscountFactor(
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
  
  /**
   * The covariation coefficients between the random variables resulting from two G2++ models.
   * 
   * \rho_{k_1,k_2} \int_a^b \nu_{1,k_1}(s, u1) \nu_{2,k_2}(s, u2) ds
   * 
   * The underlying factors are the same for the two G2++ models, only the weights of those factors differ according
   * to the parameters associated to each of them.
   * <p>
   * The volatility times must be the same for each parameters. This condition is not checked to avoid slow computations.
   * The method {@link HullWhiteOneFactorPiecewiseConstantFormulas#parametersCommonTimes} can be used to produced the 
   * required parameters sets.
   * 
   * @param parameters1  the first G2pp model parameters
   * @param parameters2  the second G2pp model parameters
   * @param startExpiry the start time of the expiry period - a
   * @param endExpiry  the end time of the expiry period - b
   * @param maturity1  the maturity of the first discount factor - u1
   * @param maturity2  the maturity of the second discount factor - u2
   * @return the covariance matrix between the different random variables
   */
  public DoubleMatrix covarianceDiscountFactors(
      G2ppPiecewiseConstantParameters parameters1,
      G2ppPiecewiseConstantParameters parameters2,
      double startExpiry,
      double endExpiry,
      double maturity1,
      double maturity2) {

    ArgChecker.isTrue(parameters1.getCorrelation() == parameters2.getCorrelation(), 
        "G2++ models must be based on same factors with same correlation");
    double rho = parameters1.getCorrelation();
    double[][] kappa = {{parameters1.getKappa1(), parameters1.getKappa2()},
        {parameters2.getKappa1(), parameters2.getKappa2()}};
    // indices
    int indexStart = Math.abs(Arrays.binarySearch(parameters1.getVolatilityTime().toArray(), startExpiry) + 1);
    // Period in which the time startExpiry is; volatilityTime.get(i-1) <= startExpiry < volatilityTime.get(i);
    int indexEnd = Math.abs(Arrays.binarySearch(parameters1.getVolatilityTime().toArray(), endExpiry) + 1);
    // Period in which the time endExpiry is; volatilityTime.get(i-1) <= endExpiry < volatilityTime.get(i);
    int sLen = indexEnd - indexStart + 1;
    // common factors
    double[] s = new double[sLen + 1];
    s[0] = startExpiry;
    System.arraycopy(parameters1.getVolatilityTime().toArray(), indexStart, s, 1, sLen - 1);
    s[sLen] = endExpiry;
    double[][][] expaijs = new double[sLen + 1][2][2];
    double[][][] expa1ia2js = new double[sLen + 1][2][2];
    for (int loopperiod = 0; loopperiod < sLen + 1; loopperiod++) {
      for (int i = 0; i < 2; i++) { // model
        for (int j = 0; j < 2; j++) { // factor
          expaijs[loopperiod][i][j] = Math.exp(kappa[i][j] * s[loopperiod]);
          expa1ia2js[loopperiod][i][j] = Math.exp((kappa[0][i] + kappa[1][j]) * s[loopperiod]);
        }
      }
    }
    double[] expa1jt1 = new double[2];
    double[] expa2jt2 = new double[2];
    for (int j = 0; j < 2; j++) { // factor
      expa1jt1[j] = Math.exp(-kappa[0][j] * maturity1);
      expa2jt2[j] = Math.exp(-kappa[1][j] * maturity2);
    }
    // Covariance terms
    double[][] covariance = new double[2][2];
    DoubleArray[] eta1 = new DoubleArray[] {parameters1.getVolatility1(), parameters1.getVolatility2()};
    DoubleArray[] eta2 = new DoubleArray[] {parameters2.getVolatility1(), parameters2.getVolatility2()};
    for (int k1 = 0; k1 < 2; k1++) { // model1
      for (int k2 = 0; k2 < 2; k2++) { // model2
        double factor1 = 1.0d / (kappa[0][k1] * kappa[1][k2]);
        double term1 = 0.0d;
        double term2 = 0.0d;
        double term3 = 0.0d;
        double term4 = 0.0d;
        for (int loopperiod = 0; loopperiod < indexEnd; loopperiod++) {
          double eta12 = eta1[k1].get(loopperiod) * eta2[k2].get(loopperiod);
          term1 += eta12 * (s[loopperiod + 1] - s[loopperiod]);
          term2 += eta12 * (expaijs[loopperiod + 1][0][k1] - expaijs[loopperiod][0][k1]);
          term3 += eta12 * (expaijs[loopperiod + 1][1][k2] - expaijs[loopperiod][1][k2]);
          term4 += eta12 * (expa1ia2js[loopperiod + 1][k1][k2] - expa1ia2js[loopperiod][k1][k2]);
        }
        term2 *= -Math.exp(-kappa[0][k1] * maturity1) / kappa[0][k1];
        term3 *= -Math.exp(-kappa[1][k2] * maturity2) / kappa[1][k2];
        term4 *= Math.exp(-kappa[0][k1] * maturity1 - kappa[1][k2] * maturity2) / (kappa[0][k1] + kappa[1][k2]);
        covariance[k1][k2] = factor1 * (term1 + term2 + term3 + term4);
        if(k1 != k2) {
          covariance[k1][k2] *= rho;
        }
      }
    }
    return DoubleMatrix.ofUnsafe(covariance);
  }

}
