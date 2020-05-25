/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.hullwhite;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.pricer.impl.rate.model.HullWhiteOneFactorPiecewiseConstantInterestRateModel;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

/**
 * Tests {@link HullWhiteOneFactorPiecewiseConstantFormulas}.
 * 
 * @author Marc Henrard
 */
public class HullWhiteOneFactorPiecewiseConstantFormulasTest {

  private static final double MEAN_REVERSION = 0.03;
  private static final double MEAN_REVERSION_2 = 0.01;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.015, 0.011, 0.012, 0.013, 0.014, 0.016);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(0.5, 1.0, 2.0, 4.0, 5.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final DoubleArray VOLATILITY_CST = DoubleArray.of(0.01);
  private static final DoubleArray VOLATILITY_CST_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_CST_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY_CST, VOLATILITY_CST_TIME);
  private static final DoubleArray VOLATILITY_CST_2 = DoubleArray.of(0.0125);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_CST_2_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION_2, VOLATILITY_CST_2, VOLATILITY_CST_TIME);
  private static final DoubleArray VOLATILITY_CST_STEP = DoubleArray.of(0.01, 0.01, 0.01, 0.01);
  private static final DoubleArray VOLATILITY_CST_STEP_TIME = DoubleArray.of(1.0, 2.0, 3.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_CST_STEP_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY_CST_STEP, VOLATILITY_CST_STEP_TIME);
  
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  private static final HullWhiteOneFactorPiecewiseConstantInterestRateModel MODEL_STD =
      HullWhiteOneFactorPiecewiseConstantInterestRateModel.DEFAULT;

  private static final Offset<Double>  TOLERANCE_CONVEXITY_FACTOR = Offset.offset(1E-8);
  private static final Offset<Double> TOLERANCE_VARIANCE = Offset.offset(1E-8);

  /* Tests the timing adjustment factor. */
  @Test
  public void timing_adjustment_factor() {
    double s = 4.40;
    double t = 4.50;
    double v = 5.25;
    double adjustmentComputed = FORMULAS.timingAdjustmentFactor(MODEL_PARAMETERS, s, t, v);
    double gamma = 1.0d / (MEAN_REVERSION * MEAN_REVERSION)
        * (Math.exp(-MEAN_REVERSION * s) - Math.exp(-MEAN_REVERSION * t))
        * (Math.exp(-MEAN_REVERSION * v) - Math.exp(-MEAN_REVERSION * t))
        * FORMULAS.alpha2ForwardGPart(MODEL_PARAMETERS, 0, s);
    assertThat(adjustmentComputed).isEqualTo(Math.exp(gamma), TOLERANCE_VARIANCE);
  }
  
  /* Check the G part of alpha^2 versus the full alpha forward. */
  @Test
  public void alpha2ForwardGPart_v_alphaTotal() {
    double s = 1.10;
    double t = 4.50;
    double u = 5.00;
    double v = 5.25;
    double alpha2GComputed = FORMULAS.alpha2ForwardGPart(MODEL_PARAMETERS, s, t);
    double alphaComputed = MODEL_STD.alpha(MODEL_PARAMETERS, s, t, u, v);
    double alphaExpected = (Math.exp(-MEAN_REVERSION * u) - Math.exp(-MEAN_REVERSION * v)) / MEAN_REVERSION *
        Math.sqrt(alpha2GComputed);
    assertThat(alphaComputed).isEqualTo(alphaExpected, TOLERANCE_VARIANCE);
  }
  
  /* Check the G part of alpha^2 versus the full alpha forward. Date after last pillar. */
  @Test
  public void alpha2ForwardGPart_v_alphaTotal_long() {
    double s = 8.10;
    double t = 8.50;
    double u = 9.00;
    double v = 9.25;
    double alpha2GComputed = FORMULAS.alpha2ForwardGPart(MODEL_PARAMETERS, s, t);
    double alphaComputed = MODEL_STD.alpha(MODEL_PARAMETERS, s, t, u, v);
    double alphaExpected = (Math.exp(-MEAN_REVERSION * u) - Math.exp(-MEAN_REVERSION * v)) / MEAN_REVERSION *
        Math.sqrt(alpha2GComputed);
    assertThat(alphaComputed).isEqualTo(alphaExpected, TOLERANCE_VARIANCE);
  }
  
  /* Start in 0, constant volatility. */
  @Test
  public void alphaCashAccountConstantVol() {
    double s = 0.0;
    double t = 4.00;
    double u = 6.00;
    double alphaComputed = FORMULAS.alphaCashAccount(MODEL_CST_PARAMETERS, s, t, u);
    double alphaExpected = t 
        - 2.0/MEAN_REVERSION * Math.exp(-MEAN_REVERSION*u) * (Math.exp(MEAN_REVERSION * t) - 1.0)
        + 1.0d / (2*MEAN_REVERSION) * Math.exp(-2*MEAN_REVERSION*u) * (Math.exp(2.0 * MEAN_REVERSION * t) - 1.0);
    alphaExpected = VOLATILITY_CST.get(0) / MEAN_REVERSION * Math.sqrt(alphaExpected);
    assertThat(alphaComputed).isEqualTo(alphaExpected, TOLERANCE_VARIANCE);
  }
  
  /* Start in 0, constant volatility v piecewise constant same vol. */
  @Test
  public void alphaCashAccountPiecewiseConstVol() {
    double s = 0.0;
    double t = 4.00; // Before first pillar
    double u = 6.00;
    double alphaComputed = FORMULAS.alphaCashAccount(MODEL_CST_STEP_PARAMETERS, s, t, u);
    double alphaExpected = FORMULAS.alphaCashAccount(MODEL_CST_PARAMETERS, s, t, u);
    assertThat(alphaComputed).isEqualTo(alphaExpected, TOLERANCE_VARIANCE);
  }

  /* Start in 0, end before first pillar. */
  @Test
  public void futuresConvexityFactorSimpleStart() {
    double s = 0.0;
    double t = 0.20; // Before first pillar
    double u = 2.00;
    double v = 2.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double factorExpected1 = MODEL_STD.futuresConvexityFactor(MODEL_PARAMETERS, t, u, v);
    assertThat(factorComputed).isEqualTo(factorExpected1, TOLERANCE_CONVEXITY_FACTOR);
    double a = MODEL_PARAMETERS.getMeanReversion();
    double factor1 = Math.exp(-a * u) - Math.exp(-a * v);
    double numerator = 2 * a * a * a;
    double factor2 = VOLATILITY.get(0) * VOLATILITY.get(0) *
        (Math.exp(a * t) - Math.exp(a * s)) *
        (2 - Math.exp(-a * (v - t)) - Math.exp(-a * (v - s)));
    double factorExpected2 = Math.exp(factor1 / numerator * factor2);
    assertThat(factorComputed).isEqualTo(factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Start and end in the same volatility period. */
  @Test
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
    assertThat(factorComputed).isEqualTo(factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Adjustment period covering several volatility periods, starting in 0. */
  @Test
  public void futuresConvexityFactorMultipleStart() {
    double s = 0.0;
    double t = 2.50;
    double u = 3.00;
    double v = 3.25;
    double factorComputed = FORMULAS.futuresConvexityFactor(MODEL_PARAMETERS, s, t, u, v);
    double factorExpected1 = MODEL_STD.futuresConvexityFactor(MODEL_PARAMETERS, t, u, v);
    assertThat(factorComputed).isEqualTo(factorExpected1, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Adjustment period covering several volatility periods, starting at an intermediary date. */
  @Test
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
    assertThat(factorComputed).isEqualTo(factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Adjustment period covering several volatility periods, starting at an intermediary date 
   * and end after the last volatility pillar */
  @Test
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
    assertThat(factorComputed).isEqualTo(factorExpected2, TOLERANCE_CONVEXITY_FACTOR);
  }

  /* Variance of the short rate for a constant volatility. */
  @Test
  public void shortRateVarianceConstantVol() {
    double t = 5.50;
    double sigma = VOLATILITY_CST.get(0);
    double factor1 = Math.exp(-2 * MEAN_REVERSION * t);
    double numerator = 2 * MEAN_REVERSION;
    double factor2 = sigma * sigma * (Math.exp(2 * MEAN_REVERSION * t) - 1);
    double varExpected = factor1 * factor2 / numerator;
    double varComputed = FORMULAS.shortRateVariance(MODEL_CST_PARAMETERS, 0.0d, t);
    assertThat(varComputed).isEqualTo(varExpected, TOLERANCE_VARIANCE);
  }

  /* Variance of the short rate for a piecewise-constant volatility. */
  @Test
  public void shortRateVariancePiecewiseConst() {
    double startTime = 0.75;
    double endTime = 4.5;
    double[] ti = {startTime, 1.0, 2.0, 4.0, endTime};
    double[] etai = {0.011, 0.012, 0.013, 0.014};
    double factor1 = Math.exp(-2 * MEAN_REVERSION * endTime);
    double numerator = 2 * MEAN_REVERSION;
    double factor2 = 0;
    for(int i=0; i<4; i++) {
      factor2 += etai[i] * etai[i] * 
          (Math.exp(2 * MEAN_REVERSION * ti[i+1]) - Math.exp(2 * MEAN_REVERSION * ti[i]));
    }
    double varExpected = factor1 * factor2 / numerator;
    double varComputed = FORMULAS.shortRateVariance(MODEL_PARAMETERS, startTime, endTime);
    assertThat(varComputed).isEqualTo(varExpected, TOLERANCE_VARIANCE);
  }

  /* Mean of the short rate (model dependent part) for a constant volatility. */
  @Test
  public void shortRateMeanModelPartConstantVol() {
    double t = 5.50;
    double sigma = VOLATILITY_CST.get(0);
    double numerator = 2 * MEAN_REVERSION * MEAN_REVERSION;
    double factor2 = sigma * sigma * Math.pow(1 - Math.exp(- MEAN_REVERSION * t), 2);
    double varExpected = factor2 / numerator;
    double varComputed = FORMULAS.shortRateMeanModelPart(MODEL_CST_PARAMETERS, t);
    assertThat(varComputed).isEqualTo(varExpected, TOLERANCE_VARIANCE);
  }

  /* Mean of the short rate (model dependent part) for a piecewise-constant volatility. */
  @Test
  public void shortRateMeanModelPartPiecewiseConst() {
    double endTime = 4.5;
    double[] ti = {0.0, 0.5, 1.0, 2.0, 4.0, endTime};
    double[] etai = {0.015, 0.011, 0.012, 0.013, 0.014};
    double numerator = 2 * MEAN_REVERSION * MEAN_REVERSION;
    double factor2 = 0;
    for (int i = 0; i < 5; i++) {
      factor2 += etai[i] * etai[i] *
          (Math.pow(1 - Math.exp(-MEAN_REVERSION * (endTime - ti[i])), 2) -
              Math.pow(1 - Math.exp(-MEAN_REVERSION * (endTime - ti[i + 1])), 2));
    }
    double varExpected = factor2 / numerator;
    double varComputed = FORMULAS.shortRateMeanModelPart(MODEL_PARAMETERS, endTime);
    assertThat(varComputed).isEqualTo(varExpected, TOLERANCE_VARIANCE);
  }

  /* Cross terms for variance related to rates on 2 different periods. */
  @Test
  public void varianceCrossTerm_ConstVol() {
    double endTime = 4.5;
    double t1 = 5.0d;
    double t2 = 5.25d;
    double t3 = 6.0d;
    double t4 = 6.25d;
    double sigma = VOLATILITY_CST.get(0);
    double numerator = 2 * Math.pow(MEAN_REVERSION, 3);
    double factor2 = (Math.exp(-MEAN_REVERSION * t1) - Math.exp(-MEAN_REVERSION * t2)) *
        (Math.exp(-MEAN_REVERSION * t3) - Math.exp(-MEAN_REVERSION * t4));
    double factor1 = sigma * sigma *( Math.exp(2 * MEAN_REVERSION * endTime) - 1.0d);
    double varianceExpected = factor1 * factor2 / numerator;
    double varianceComputed = FORMULAS.varianceCrossTerm(MODEL_CST_PARAMETERS, endTime, t1, t2, t3, t4);
    assertThat(varianceComputed).isEqualTo(varianceExpected, TOLERANCE_VARIANCE);
  }

  @Test
  public void varianceCrossTerm_PiecewiseConstVol() {
    double endTime = 4.5;
    double[] ti = {0.0, 0.5, 1.0, 2.0, 4.0, endTime};
    double[] etai = {0.015, 0.011, 0.012, 0.013, 0.014};
    double t1 = 5.0d;
    double t2 = 5.25d;
    double t3 = 6.0d;
    double t4 = 6.25d;
    double numerator = 2 * Math.pow(MEAN_REVERSION, 3);
    double factor2 = (Math.exp(-MEAN_REVERSION * t1) - Math.exp(-MEAN_REVERSION * t2)) *
        (Math.exp(-MEAN_REVERSION * t3) - Math.exp(-MEAN_REVERSION * t4));
    double factor1 = 0;
    for (int i = 0; i < etai.length; i++) {
      factor1 += etai[i] * etai[i] *
          ( Math.exp(2 * MEAN_REVERSION * ti[i+1]) - Math.exp(2 * MEAN_REVERSION * ti[i]));
    }
    double varianceExpected = factor1 * factor2 / numerator;
    double varianceComputed = FORMULAS.varianceCrossTerm(MODEL_PARAMETERS, endTime, t1, t2, t3, t4);
    assertThat(varianceComputed).isEqualTo(varianceExpected, TOLERANCE_VARIANCE);
  }

  /* Tests that the parameters generated by parametersCommonTimes are equivalent. */
  @Test
  public void parametersCommonTimes() {
    double[] t1 = {0.5, 1.0, 2.0, 4.0, 10.0};
    double[] eta1 = {0.015, 0.011, 0.012, 0.013, 0.014, 0.015};
    double[] t2 = {0.25, 1.5, 2.0, 4.5, 9.0, 9.5};
    double[] eta2 = {0.025, 0.021, 0.022, 0.023, 0.024, 0.025, 0.026};
    double a1 = 0.01;
    double a2 = 0.02;
    HullWhiteOneFactorPiecewiseConstantParameters hw1 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(a1, DoubleArray.ofUnsafe(eta1), DoubleArray.ofUnsafe(t1));
    HullWhiteOneFactorPiecewiseConstantParameters hw2 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(a2, DoubleArray.ofUnsafe(eta2), DoubleArray.ofUnsafe(t2));
    Pair<HullWhiteOneFactorPiecewiseConstantParameters, HullWhiteOneFactorPiecewiseConstantParameters> joint = 
        FORMULAS.parametersCommonTimes(hw1, hw2);

    HullWhiteOneFactorPiecewiseConstantParameters hw1Equivalent = joint.getFirst();
    HullWhiteOneFactorPiecewiseConstantParameters hw2Equivalent = joint.getSecond();
    assertThat(hw1.getMeanReversion()).isEqualTo(hw1Equivalent.getMeanReversion(), TOLERANCE_VARIANCE);
    assertThat(hw1Equivalent.getVolatility().size()).isEqualTo(hw2Equivalent.getVolatility().size());
    assertThat(hw1.getVolatilityTime()).isEqualTo(hw1.getVolatilityTime());
    double[] testTimes = {0.0, 0.10, 0.249, 0.25, 0.261, 0.499, 0.50, 0.501, 2.5, 9.4, 9.9, 10.0, 10.5};
    for(int i=0; i<testTimes.length; i++) {
      double alpha1Computed = FORMULAS.alpha2ForwardGPart(hw1Equivalent, 0, testTimes[i]);
      double alpha1Expected = FORMULAS.alpha2ForwardGPart(hw1, 0, testTimes[i]);
      assertThat(alpha1Computed).isEqualTo(alpha1Expected, TOLERANCE_VARIANCE);
      double alpha2Computed = FORMULAS.alpha2ForwardGPart(hw2Equivalent, 0, testTimes[i]);
      double alpha2Expected = FORMULAS.alpha2ForwardGPart(hw2, 0, testTimes[i]);
      assertThat(alpha2Computed).isEqualTo(alpha2Expected, TOLERANCE_VARIANCE);
    }
  }

  /* Cross terms for variance compare to alpha. */
  @Test
  public void varianceCrossTermCashAccount_alpha() {
    double endIntegralTime1 = 3.0;
    double endIntegralTime2 = 5.0;
    double u1 = 10.0;
    double alphaComputed = FORMULAS
        .alphaCashAccount(MODEL_PARAMETERS, 0, Math.min(endIntegralTime1, endIntegralTime2), u1);
    double crossComputed = FORMULAS
        .varianceCrossTermCashAccount(MODEL_PARAMETERS, MODEL_PARAMETERS, endIntegralTime1, endIntegralTime2, u1, u1);
    assertThat(alphaComputed * alphaComputed).isEqualTo(crossComputed, TOLERANCE_VARIANCE);
  }

  /* Cross terms for variance with constant vol. Local formula. */
  @Test
  public void varianceCrossTermCashAccount_HWConstVol() {
    double endIntegralTime1 = 3.0;
    double endIntegralTime2 = 5.0;
    double minEndIntegralTime = Math.min(endIntegralTime1, endIntegralTime2);
    double u1 = 10.0;
    double u2 = 12.0;
    double crossComputed = FORMULAS
        .varianceCrossTermCashAccount(MODEL_CST_PARAMETERS, MODEL_CST_2_PARAMETERS, endIntegralTime1, endIntegralTime2,
            u1, u2);
    double factor1 = 1.0 / (MEAN_REVERSION * MEAN_REVERSION_2);
    double volProduct = VOLATILITY_CST.get(0) * VOLATILITY_CST_2.get(0);
    double term1 = volProduct * minEndIntegralTime;
    double term2 = -1.0 / MEAN_REVERSION * Math.exp(-MEAN_REVERSION * u1) * volProduct *
        (Math.exp(MEAN_REVERSION * minEndIntegralTime) - 1);
    double term3 = -1.0 / MEAN_REVERSION_2 * Math.exp(-MEAN_REVERSION_2 * u2) * volProduct *
        (Math.exp(MEAN_REVERSION_2 * minEndIntegralTime) - 1);
    double term4 = 1/(MEAN_REVERSION + MEAN_REVERSION_2) * 
        Math.exp(-MEAN_REVERSION * u1 -MEAN_REVERSION_2 * u2) * volProduct *
        (Math.exp((MEAN_REVERSION + MEAN_REVERSION_2) * minEndIntegralTime) - 1);
    double crossExpected = factor1 * (term1 + term2 + term3 + term4);
    assertThat(crossExpected).isEqualTo(crossComputed, TOLERANCE_VARIANCE);
  }

  /* Cross terms for variance between HW model and constant volatility model. Local formula. */
  @Test
  public void varianceCrossTermContantVolCashAccount_HWConstVol() {
    double endIntegralTime = 3.0;
    double u1 = 10.0;
    double crossComputed = FORMULAS
        .varianceCrossTermConstantVolCashAccount(MODEL_CST_PARAMETERS, endIntegralTime, u1);
    double factor1 = 1.0 / MEAN_REVERSION ;
    double volProduct = VOLATILITY_CST.get(0);
    double term1 = volProduct * endIntegralTime;
    double term2 = -1.0 / MEAN_REVERSION * Math.exp(-MEAN_REVERSION * u1) * volProduct *
        (Math.exp(MEAN_REVERSION * endIntegralTime) - 1);
    double crossExpected = factor1 * (term1 + term2);
    assertThat(crossExpected).isEqualTo(crossComputed, TOLERANCE_VARIANCE);
  }
  
}
