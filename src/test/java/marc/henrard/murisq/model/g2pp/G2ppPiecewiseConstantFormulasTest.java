/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.g2pp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Tests {@link G2ppPiecewiseConstantFormulas}
 * 
 * @author Marc Henrard
 */
public class G2ppPiecewiseConstantFormulasTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final LocalTime VALUATION_TIME = LocalTime.NOON;
  private static final ZoneId VALUATION_ZONE = ZoneId.of("America/New_York");
  private static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;

  private static final Currency CURRENCY = Currency.USD;
  private static final double CORRELATION = -0.50;
  private static final double KAPPA_1 = 0.02;
  private static final double KAPPA_2 = 0.20;
  private static final DoubleArray VOLATILITY_1_CST = DoubleArray.of(0.01d);
  private static final DoubleArray VOLATILITY_2_CST = DoubleArray.of(0.005d);
  private static final DoubleArray VOLATILITY_TIME_CST = DoubleArray.of();
  private static final DoubleArray VOLATILITY_1 = DoubleArray.of(0.01d, 0.012d, 0.011);
  private static final DoubleArray VOLATILITY_2 = DoubleArray.of(0.005d, 0.006d, 0.007);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(1.0d, 3.0d);
  
  private static final Offset<Double> TOLERANCE_VAR = Offset.offset(1.0E-8);

  private static final G2ppPiecewiseConstantParameters PARAMETERS_CST = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1)
      .kappa2(KAPPA_2)
      .volatility1(VOLATILITY_1_CST)
      .volatility2(VOLATILITY_2_CST)
      .volatilityTime(VOLATILITY_TIME_CST)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();

  private static final G2ppPiecewiseConstantParameters PARAMETERS = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1)
      .kappa2(KAPPA_2)
      .volatility1(VOLATILITY_1)
      .volatility2(VOLATILITY_2)
      .volatilityTime(VOLATILITY_TIME)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();
  
  private static final G2ppPiecewiseConstantFormulas FORMULAS_G2PP = 
      G2ppPiecewiseConstantFormulas.DEFAULT;

  /* Tests the alphas for pseudo-discount factors rescaled by the collateral account . */
  @Test
  public void alphaCollateralAccount() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double maturity = 30.0d;
    DoubleArray alphas = FORMULAS_G2PP.alphaCollateralAccount(PARAMETERS_CST, startExpiry, endExpiry, maturity);
    assertThat(alphas.size()).isEqualTo(3);
    double alpha1Expected = (endExpiry - startExpiry);
    alpha1Expected -= 2.0d / KAPPA_1 * Math.exp(-KAPPA_1 * maturity) *
        (Math.exp(KAPPA_1 * endExpiry) - Math.exp(KAPPA_1 * startExpiry));
    alpha1Expected += 1.0d / (2 * KAPPA_1) * Math.exp(-2 * KAPPA_1 * maturity) *
        (Math.exp(2 * KAPPA_1 * endExpiry) - Math.exp(2 * KAPPA_1 * startExpiry));
    alpha1Expected *= VOLATILITY_1_CST.get(0) * VOLATILITY_1_CST.get(0) / (KAPPA_1 * KAPPA_1);
    assertThat(alphas.get(0)).isEqualTo(alpha1Expected, TOLERANCE_VAR);
    double alpha2Expected = (endExpiry - startExpiry);
    alpha2Expected -= 2.0d / KAPPA_2 * Math.exp(-KAPPA_2 * maturity) *
        (Math.exp(KAPPA_2 * endExpiry) - Math.exp(KAPPA_2 * startExpiry));
    alpha2Expected += 1.0d / (2 * KAPPA_2) * Math.exp(-2 * KAPPA_2 * maturity) *
        (Math.exp(2 * KAPPA_2 * endExpiry) - Math.exp(2 * KAPPA_2 * startExpiry));
    alpha2Expected *= VOLATILITY_2_CST.get(0) * VOLATILITY_2_CST.get(0) / (KAPPA_2 * KAPPA_2);
    assertThat(alphas.get(1)).isEqualTo(alpha2Expected, TOLERANCE_VAR);
    double alphaTotExpected = (endExpiry - startExpiry);
    alphaTotExpected -= 1.0d / KAPPA_1 * Math.exp(-KAPPA_1 * maturity) *
        (Math.exp(KAPPA_1 * endExpiry) - Math.exp(KAPPA_1 * startExpiry));
    alphaTotExpected -= 1.0d / KAPPA_2 * Math.exp(-KAPPA_2 * maturity) *
        (Math.exp(KAPPA_2 * endExpiry) - Math.exp(KAPPA_2 * startExpiry));
    alphaTotExpected += 1.0d / (KAPPA_1 + KAPPA_2) * Math.exp(-(KAPPA_1 + KAPPA_2) * maturity) *
        (Math.exp((KAPPA_1 + KAPPA_2) * endExpiry) - Math.exp((KAPPA_1 + KAPPA_2) * startExpiry));
    alphaTotExpected *= 2 * CORRELATION * VOLATILITY_1_CST.get(0) * VOLATILITY_2_CST.get(0) / (KAPPA_1 * KAPPA_2);
    alphaTotExpected += alpha1Expected + alpha2Expected;
    assertThat(alphas.get(2)).isEqualTo(alphaTotExpected, TOLERANCE_VAR);
  }

  /* Tests the alphas for pseudo-discount factors rescaled by the collateral account . 
   * Compare first 2 components to HW. */
  @Test
  public void alphaCollateralAccount_hw() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double maturity = 30.0d;
    DoubleArray alphas = FORMULAS_G2PP.alphaCollateralAccount(PARAMETERS, startExpiry, endExpiry, maturity);
    HullWhiteOneFactorPiecewiseConstantParameters hw1 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_1, VOLATILITY_1, VOLATILITY_TIME);
    HullWhiteOneFactorPiecewiseConstantParameters hw2 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_2, VOLATILITY_2, VOLATILITY_TIME);
    HullWhiteOneFactorPiecewiseConstantFormulas formulasHw = 
        HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
    double alpha1 = formulasHw.alphaCashAccount(hw1, startExpiry, endExpiry, maturity);
    double alpha2 = formulasHw.alphaCashAccount(hw2, startExpiry, endExpiry, maturity);
    assertThat(alphas.get(0)).isEqualTo(alpha1 * alpha1, TOLERANCE_VAR);
    assertThat(alphas.get(1)).isEqualTo(alpha2 * alpha2, TOLERANCE_VAR);
  }

}
