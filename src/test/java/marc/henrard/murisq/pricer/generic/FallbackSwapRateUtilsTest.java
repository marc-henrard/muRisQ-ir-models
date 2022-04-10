/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.value.ValueDerivatives;

/**
 * Tests {@link FallbackSwapRateUtils}.
 * 
 * @author Marc Henrard
 */
public class FallbackSwapRateUtilsTest {

  private static final double USD_LIBOR_3M_SPREAD = 0.0026161;
  private static final double GBP_LIBOR_3M_SPREAD = 0.001193;
  private static final double GBP_LIBOR_6M_SPREAD = 0.002766;

  private static final double USD_ACCRUAL_FACTOR_RATIO = 365.25d / 360.00d;

  private static final Offset<Double> TOLERANCE_RATE = within(1.0E-8);
  private static final Offset<Double> TOLERANCE_D1 = within(1.0E-8);
  private static final Offset<Double> TOLERANCE_D2 = within(1.0E-7);
  private static final Offset<Double> TOLERANCE_D3 = within(1.0E-7);
  private static final double SHIFT_FD = 1.0E-6;

  @Test
  public void fallback_isr_usd() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateLiborComputed = FallbackSwapRateUtils.fallbackMechanismUsd(rateOis[looptest]);
      double rateLiborExpected = USD_ACCRUAL_FACTOR_RATIO * (2 * (Math.pow(1 + rateOis[looptest], 0.5) - 1) +
          USD_LIBOR_3M_SPREAD * 0.5d * (Math.pow(1 + rateOis[looptest], 0.25) + 1));
      assertThat(rateLiborComputed).isEqualTo(rateLiborExpected, TOLERANCE_RATE);
    }
  }

  @Test
  public void fallbackequivalent_isr_usd() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateLibor = FallbackSwapRateUtils.fallbackMechanismUsd(rateOis[looptest]);
      double rateOisComputed = FallbackSwapRateUtils.fallbackEquivalentRateUsd(rateLibor);
      assertThat(rateOisComputed).isEqualTo(rateOis[looptest], TOLERANCE_RATE);
    }
  }

  @Test
  public void fallback_isr_usd_AD() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateIrs = FallbackSwapRateUtils.fallbackMechanismUsd(rateOis[looptest]);
      ValueDerivatives rateIrsUp = FallbackSwapRateUtils.fallbackMechanismUsdAD(rateOis[looptest] + SHIFT_FD);
      ValueDerivatives rateIrsDown = FallbackSwapRateUtils.fallbackMechanismUsdAD(rateOis[looptest] - SHIFT_FD);
      ValueDerivatives rateIrsAd = FallbackSwapRateUtils.fallbackMechanismUsdAD(rateOis[looptest]);
      assertThat(rateIrs).isEqualTo(rateIrsAd.getValue(), TOLERANCE_RATE);
      assertThat((rateIrsUp.getValue() - rateIrsDown.getValue()) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(0), TOLERANCE_D1);
      assertThat((rateIrsUp.getDerivative(0) - rateIrsDown.getDerivative(0)) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(1), TOLERANCE_D2);
      assertThat((rateIrsUp.getDerivative(1) - rateIrsDown.getDerivative(1)) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(2), TOLERANCE_D3);
    }
  }

  @Test
  public void fallback_isr_gbp_1Y() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateIrsComputed = FallbackSwapRateUtils.fallbackMechanismGbp1Y(rateOis[looptest]);
      double rateIrsExpected = rateOis[looptest] +
          GBP_LIBOR_3M_SPREAD * 0.25d * (Math.pow(1 + rateOis[looptest], 0.25) + 1) *
              (Math.pow(1 + rateOis[looptest], 0.50) + 1);
      assertThat(rateIrsComputed).isEqualTo(rateIrsExpected, TOLERANCE_RATE);
    }
  }

  @Test
  public void fallbackequivalent_isr_gbp_1Y() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateIrs = FallbackSwapRateUtils.fallbackMechanismGbp1Y(rateOis[looptest]);
      double rateOisComputed = FallbackSwapRateUtils.fallbackEquivalentRateGbp1Y(rateIrs);
      assertThat(rateOisComputed).isEqualTo(rateOis[looptest], TOLERANCE_RATE);
    }
  }

  @Test
  public void fallback_isr_gbp_1Y_AD() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateIrs = FallbackSwapRateUtils.fallbackMechanismGbp1Y(rateOis[looptest]);
      ValueDerivatives rateIrsUp = FallbackSwapRateUtils.fallbackMechanismGbp1YAD(rateOis[looptest] + SHIFT_FD);
      ValueDerivatives rateIrsDown = FallbackSwapRateUtils.fallbackMechanismGbp1YAD(rateOis[looptest] - SHIFT_FD);
      ValueDerivatives rateIrsAd = FallbackSwapRateUtils.fallbackMechanismGbp1YAD(rateOis[looptest]);
      assertThat(rateIrs).isEqualTo(rateIrsAd.getValue(), TOLERANCE_RATE);
      assertThat((rateIrsUp.getValue() - rateIrsDown.getValue()) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(0), TOLERANCE_D1);
      assertThat((rateIrsUp.getDerivative(0) - rateIrsDown.getDerivative(0)) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(1), TOLERANCE_D2);
      assertThat((rateIrsUp.getDerivative(1) - rateIrsDown.getDerivative(1)) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(2), TOLERANCE_D3);
    }
  }

  @Test
  public void fallback_isr_gbp_Plus1Y() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateLiborComputed = FallbackSwapRateUtils.fallbackMechanismGbpPlus1Y(rateOis[looptest]);
      double rateLiborExpected = 2 * (Math.pow(1 + rateOis[looptest], 0.5) - 1) + GBP_LIBOR_6M_SPREAD;
      assertThat(rateLiborComputed).isEqualTo(rateLiborExpected, TOLERANCE_RATE);
    }
  }

  @Test
  public void fallbackequivalent_isr_gbp_Plus1Y() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateIrs = FallbackSwapRateUtils.fallbackMechanismGbpPlus1Y(rateOis[looptest]);
      double rateOisComputed = FallbackSwapRateUtils.fallbackEquivalentRateGbpPlus1Y(rateIrs);
      assertThat(rateOisComputed).isEqualTo(rateOis[looptest], TOLERANCE_RATE);
    }
  }

  @Test
  public void fallback_isr_gbp_Plus1Y_AD() {
    double[] rateOis = {-0.0025, 0.0, 0.0025, 0.0100, 0.0500, 0.2000};
    int nbTests = rateOis.length;
    for (int looptest = 0; looptest < nbTests; looptest++) {
      double rateIrs = FallbackSwapRateUtils.fallbackMechanismGbpPlus1Y(rateOis[looptest]);
      ValueDerivatives rateIrsUp = FallbackSwapRateUtils.fallbackMechanismGbpPlus1YAD(rateOis[looptest] + SHIFT_FD);
      ValueDerivatives rateIrsDown = FallbackSwapRateUtils.fallbackMechanismGbpPlus1YAD(rateOis[looptest] - SHIFT_FD);
      ValueDerivatives rateIrsAd = FallbackSwapRateUtils.fallbackMechanismGbpPlus1YAD(rateOis[looptest]);
      assertThat(rateIrs).isEqualTo(rateIrsAd.getValue(), TOLERANCE_RATE);
      assertThat((rateIrsUp.getValue() - rateIrsDown.getValue()) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(0), TOLERANCE_D1);
      assertThat((rateIrsUp.getDerivative(0) - rateIrsDown.getDerivative(0)) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(1), TOLERANCE_D2);
      assertThat((rateIrsUp.getDerivative(1) - rateIrsDown.getDerivative(1)) / (2 * SHIFT_FD))
          .isEqualTo(rateIrsAd.getDerivative(2), TOLERANCE_D3);
    }
  }

}
