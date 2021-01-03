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
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.pricer.impl.rate.model.HullWhiteOneFactorPiecewiseConstantInterestRateModel;
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
  private static final double KAPPA_1_1 = 0.02;
  private static final double KAPPA_1_2 = 0.20;
  private static final DoubleArray VOLATILITY_1_1_CST = DoubleArray.of(0.01d);
  private static final DoubleArray VOLATILITY_1_2_CST = DoubleArray.of(0.005d);
  private static final DoubleArray VOLATILITY_TIME_CST = DoubleArray.of();
  private static final DoubleArray VOLATILITY_1 = DoubleArray.of(0.01d, 0.012d, 0.011);
  private static final DoubleArray VOLATILITY_2 = DoubleArray.of(0.005d, 0.006d, 0.007);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(1.0d, 3.0d);
  private static final double KAPPA_2_1 = 0.03;
  private static final double KAPPA_2_2 = 0.15;
  private static final DoubleArray VOLATILITY_2_1_CST = DoubleArray.of(0.009d);
  private static final DoubleArray VOLATILITY_2_2_CST = DoubleArray.of(0.004d);
  
  private static final Offset<Double> TOLERANCE_VAR = Offset.offset(1.0E-8);

  private static final G2ppPiecewiseConstantParameters PARAMETERS_CST_1 = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1_1)
      .kappa2(KAPPA_1_2)
      .volatility1(VOLATILITY_1_1_CST)
      .volatility2(VOLATILITY_1_2_CST)
      .volatilityTime(VOLATILITY_TIME_CST)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();

  private static final G2ppPiecewiseConstantParameters PARAMETERS_CST_2 = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_2_1)
      .kappa2(KAPPA_2_2)
      .volatility1(VOLATILITY_2_1_CST)
      .volatility2(VOLATILITY_2_2_CST)
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
      .kappa1(KAPPA_1_1)
      .kappa2(KAPPA_1_2)
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
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS_HW = 
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  private static final HullWhiteOneFactorPiecewiseConstantInterestRateModel FORMULAS_HW_STRATA = 
      HullWhiteOneFactorPiecewiseConstantInterestRateModel.DEFAULT;

  /* Tests the alphas for pseudo-discount factors rescaled by the collateral account . 
   * Constant volatility parameters */
  @Test
  public void alphaCollateralAccountDiscountFactor_cst() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double maturity = 30.0d;
    DoubleArray alphas = FORMULAS_G2PP.alphaCollateralAccountDiscountFactor(PARAMETERS_CST_1, startExpiry, endExpiry, maturity);
    assertThat(alphas.size()).isEqualTo(3);
    double alpha1Expected = (endExpiry - startExpiry);
    alpha1Expected -= 2.0d / KAPPA_1_1 * Math.exp(-KAPPA_1_1 * maturity) *
        (Math.exp(KAPPA_1_1 * endExpiry) - Math.exp(KAPPA_1_1 * startExpiry));
    alpha1Expected += 1.0d / (2 * KAPPA_1_1) * Math.exp(-2 * KAPPA_1_1 * maturity) *
        (Math.exp(2 * KAPPA_1_1 * endExpiry) - Math.exp(2 * KAPPA_1_1 * startExpiry));
    alpha1Expected *= VOLATILITY_1_1_CST.get(0) * VOLATILITY_1_1_CST.get(0) / (KAPPA_1_1 * KAPPA_1_1);
    assertThat(alphas.get(0)).isEqualTo(alpha1Expected, TOLERANCE_VAR);
    double alpha2Expected = (endExpiry - startExpiry);
    alpha2Expected -= 2.0d / KAPPA_1_2 * Math.exp(-KAPPA_1_2 * maturity) *
        (Math.exp(KAPPA_1_2 * endExpiry) - Math.exp(KAPPA_1_2 * startExpiry));
    alpha2Expected += 1.0d / (2 * KAPPA_1_2) * Math.exp(-2 * KAPPA_1_2 * maturity) *
        (Math.exp(2 * KAPPA_1_2 * endExpiry) - Math.exp(2 * KAPPA_1_2 * startExpiry));
    alpha2Expected *= VOLATILITY_1_2_CST.get(0) * VOLATILITY_1_2_CST.get(0) / (KAPPA_1_2 * KAPPA_1_2);
    assertThat(alphas.get(1)).isEqualTo(alpha2Expected, TOLERANCE_VAR);
    double alphaTotExpected = (endExpiry - startExpiry);
    alphaTotExpected -= 1.0d / KAPPA_1_1 * Math.exp(-KAPPA_1_1 * maturity) *
        (Math.exp(KAPPA_1_1 * endExpiry) - Math.exp(KAPPA_1_1 * startExpiry));
    alphaTotExpected -= 1.0d / KAPPA_1_2 * Math.exp(-KAPPA_1_2 * maturity) *
        (Math.exp(KAPPA_1_2 * endExpiry) - Math.exp(KAPPA_1_2 * startExpiry));
    alphaTotExpected += 1.0d / (KAPPA_1_1 + KAPPA_1_2) * Math.exp(-(KAPPA_1_1 + KAPPA_1_2) * maturity) *
        (Math.exp((KAPPA_1_1 + KAPPA_1_2) * endExpiry) - Math.exp((KAPPA_1_1 + KAPPA_1_2) * startExpiry));
    alphaTotExpected *= 2 * CORRELATION * VOLATILITY_1_1_CST.get(0) * VOLATILITY_1_2_CST.get(0) / (KAPPA_1_1 * KAPPA_1_2);
    alphaTotExpected += alpha1Expected + alpha2Expected;
    assertThat(alphas.get(2)).isEqualTo(alphaTotExpected, TOLERANCE_VAR);
  }

  /* Tests the alphas for pseudo-discount factors rescaled by the collateral account . 
   * Compare first 2 components to HW. */
  @Test
  public void alphaCollateralAccountDiscountFactor_hw() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double maturity = 30.0d;
    DoubleArray alphas = FORMULAS_G2PP.alphaCollateralAccountDiscountFactor(PARAMETERS, startExpiry, endExpiry, maturity);
    HullWhiteOneFactorPiecewiseConstantParameters hw1 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_1_1, VOLATILITY_1, VOLATILITY_TIME);
    HullWhiteOneFactorPiecewiseConstantParameters hw2 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_1_2, VOLATILITY_2, VOLATILITY_TIME);
    
    double alpha1 = FORMULAS_HW.alphaCashAccount(hw1, startExpiry, endExpiry, maturity);
    double alpha2 = FORMULAS_HW.alphaCashAccount(hw2, startExpiry, endExpiry, maturity);
    assertThat(alphas.get(0)).isEqualTo(alpha1 * alpha1, TOLERANCE_VAR);
    assertThat(alphas.get(1)).isEqualTo(alpha2 * alpha2, TOLERANCE_VAR);
  }

  /* Tests the alphas for pseudo-discount factors rescaled by a forward bond. 
   * Constant volatility parameters */
  @Test
  public void alphaRatioDiscountFactors_cst() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double scalingBondMaturity = 30.0d;
    double dynamicBondMaturity = 20.0d;
    DoubleArray alphas = FORMULAS_G2PP
        .alphaRatioDiscountFactors(PARAMETERS_CST_1, startExpiry, endExpiry, scalingBondMaturity, dynamicBondMaturity);
    assertThat(alphas.size()).isEqualTo(3);
    double alpha1Expected = 
        (Math.exp(-KAPPA_1_1 * scalingBondMaturity) - Math.exp(-KAPPA_1_1 * dynamicBondMaturity)) / KAPPA_1_1;
    alpha1Expected *= 
        (Math.exp(-KAPPA_1_1 * scalingBondMaturity) - Math.exp(-KAPPA_1_1 * dynamicBondMaturity)) / KAPPA_1_1;
    alpha1Expected *= 0.5d / KAPPA_1_1 * VOLATILITY_1_1_CST.get(0) * VOLATILITY_1_1_CST.get(0) *
        (Math.exp(2 * KAPPA_1_1 * endExpiry) - Math.exp(2 * KAPPA_1_1 * startExpiry));
    assertThat(alphas.get(0)).isEqualTo(alpha1Expected, TOLERANCE_VAR);
    double alpha2Expected = 
        (Math.exp(-KAPPA_1_2 * scalingBondMaturity) - Math.exp(-KAPPA_1_2 * dynamicBondMaturity)) / KAPPA_1_2;
    alpha2Expected *= 
        (Math.exp(-KAPPA_1_2 * scalingBondMaturity) - Math.exp(-KAPPA_1_2 * dynamicBondMaturity)) / KAPPA_1_2;
    alpha2Expected *= 0.5d / KAPPA_1_2 * VOLATILITY_1_2_CST.get(0) * VOLATILITY_1_2_CST.get(0) *
        (Math.exp(2 * KAPPA_1_2 * endExpiry) - Math.exp(2 * KAPPA_1_2 * startExpiry));
    assertThat(alphas.get(1)).isEqualTo(alpha2Expected, TOLERANCE_VAR);
    double t1 = 2 * CORRELATION;
    double t2 = 
        (Math.exp(-KAPPA_1_1 * scalingBondMaturity) - Math.exp(-KAPPA_1_1 * dynamicBondMaturity)) / KAPPA_1_1;
    double t3 = 
        (Math.exp(-KAPPA_1_2 * scalingBondMaturity) - Math.exp(-KAPPA_1_2 * dynamicBondMaturity)) / KAPPA_1_2;
    double t4 = 1.0d / (KAPPA_1_1 + KAPPA_1_2) * VOLATILITY_1_1_CST.get(0) * VOLATILITY_1_2_CST.get(0) *
        (Math.exp((KAPPA_1_1 + KAPPA_1_2) * endExpiry) - Math.exp((KAPPA_1_1 + KAPPA_1_2) * startExpiry));
    double alphaTotExpected = t1 * t2 * t3 * t4;
    alphaTotExpected += alpha1Expected + alpha2Expected;
    assertThat(alphas.get(2)).isEqualTo(alphaTotExpected, TOLERANCE_VAR);
  }

  /* Tests the alphas for pseudo-discount factors rescaled by a forward bond. 
   * Compare first 2 components to HW. */
  @Test
  public void alphaRatioDiscountFactors_hw() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double scalingBondMaturity = 30.0d;
    double dynamicBondMaturity = 20.0d;
    DoubleArray alphas = FORMULAS_G2PP
        .alphaRatioDiscountFactors(PARAMETERS, startExpiry, endExpiry, scalingBondMaturity, dynamicBondMaturity);
    HullWhiteOneFactorPiecewiseConstantParameters hw1 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_1_1, VOLATILITY_1, VOLATILITY_TIME);
    HullWhiteOneFactorPiecewiseConstantParameters hw2 = 
        HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_1_2, VOLATILITY_2, VOLATILITY_TIME);
    double alpha1 = FORMULAS_HW_STRATA.alpha(hw1, startExpiry, endExpiry, scalingBondMaturity, dynamicBondMaturity);
    double alpha2 = FORMULAS_HW_STRATA.alpha(hw2, startExpiry, endExpiry, scalingBondMaturity, dynamicBondMaturity);
    assertThat(alphas.get(0)).isEqualTo(alpha1 * alpha1, TOLERANCE_VAR);
    assertThat(alphas.get(1)).isEqualTo(alpha2 * alpha2, TOLERANCE_VAR);
  }

  /* Tests the alphas for pseudo-discount factors rescaled by a forward bond. 
   * Compare first 2 components to HW. */
  @Test
  public void covarianceDiscountFactors_cst() {
    double startExpiry = 1.0d;
    double endExpiry = 5.0d;
    double maturity1 = 30.0d;
    double maturity2 = 20.0d;
    DoubleMatrix corarianceComputed = FORMULAS_G2PP
        .covarianceDiscountFactors(PARAMETERS_CST_1, PARAMETERS_CST_2, startExpiry, endExpiry, maturity1, maturity2);

    double corariance11 = endExpiry - startExpiry;
    corariance11 += -(Math.exp(KAPPA_1_1 * endExpiry) - Math.exp(KAPPA_1_1 * startExpiry)) / KAPPA_1_1 *
        Math.exp(-KAPPA_1_1 * maturity1);
    corariance11 += -(Math.exp(KAPPA_2_1 * endExpiry) - Math.exp(KAPPA_2_1 * startExpiry)) / KAPPA_2_1 *
        Math.exp(-KAPPA_2_1 * maturity2);
    corariance11 += (Math.exp((KAPPA_1_1 + KAPPA_2_1) * endExpiry) - Math.exp((KAPPA_1_1 + KAPPA_2_1) * startExpiry)) /
        (KAPPA_1_1 + KAPPA_2_1) * Math.exp(-KAPPA_1_1 * maturity1 - KAPPA_2_1 * maturity2);
    corariance11 /= (KAPPA_1_1 * KAPPA_2_1);
    corariance11 *= VOLATILITY_1_1_CST.get(0) * VOLATILITY_2_1_CST.get(0);
    assertThat(corarianceComputed.get(0, 0)).isEqualTo(corariance11, TOLERANCE_VAR);

    double corariance12 = endExpiry - startExpiry;
    corariance12 += -(Math.exp(KAPPA_1_1 * endExpiry) - Math.exp(KAPPA_1_1 * startExpiry)) / KAPPA_1_1 *
        Math.exp(-KAPPA_1_1 * maturity1);
    corariance12 += -(Math.exp(KAPPA_2_2 * endExpiry) - Math.exp(KAPPA_2_2 * startExpiry)) / KAPPA_2_2 *
        Math.exp(-KAPPA_2_2 * maturity2);
    corariance12 += (Math.exp((KAPPA_1_1 + KAPPA_2_2) * endExpiry) - Math.exp((KAPPA_1_1 + KAPPA_2_2) * startExpiry)) /
        (KAPPA_1_1 + KAPPA_2_2) * Math.exp(-KAPPA_1_1 * maturity1 - KAPPA_2_2 * maturity2);
    corariance12 /= (KAPPA_1_1 * KAPPA_2_2);
    corariance12 *= VOLATILITY_1_1_CST.get(0) * VOLATILITY_2_2_CST.get(0) * CORRELATION;
    assertThat(corarianceComputed.get(0, 1)).isEqualTo(corariance12, TOLERANCE_VAR);

    double corariance21 = endExpiry - startExpiry;
    corariance21 += -(Math.exp(KAPPA_1_2 * endExpiry) - Math.exp(KAPPA_1_2 * startExpiry)) / KAPPA_1_2 *
        Math.exp(-KAPPA_1_2 * maturity1);
    corariance21 += -(Math.exp(KAPPA_2_1 * endExpiry) - Math.exp(KAPPA_2_1 * startExpiry)) / KAPPA_2_1 *
        Math.exp(-KAPPA_2_1 * maturity2);
    corariance21 += (Math.exp((KAPPA_1_2 + KAPPA_2_1) * endExpiry) - Math.exp((KAPPA_1_2 + KAPPA_2_1) * startExpiry)) /
        (KAPPA_1_2 + KAPPA_2_1) * Math.exp(-KAPPA_1_2 * maturity1 - KAPPA_2_1 * maturity2);
    corariance21 /= (KAPPA_1_2 * KAPPA_2_1);
    corariance21 *= VOLATILITY_1_2_CST.get(0) * VOLATILITY_2_1_CST.get(0) * CORRELATION;
    assertThat(corarianceComputed.get(1, 0)).isEqualTo(corariance21, TOLERANCE_VAR);
    
    double corariance22 = endExpiry - startExpiry;
    corariance22 += -(Math.exp(KAPPA_1_2 * endExpiry) - Math.exp(KAPPA_1_2 * startExpiry)) / KAPPA_1_2 *
        Math.exp(-KAPPA_1_2 * maturity1);
    corariance22 += -(Math.exp(KAPPA_2_2 * endExpiry) - Math.exp(KAPPA_2_2 * startExpiry)) / KAPPA_2_2 *
        Math.exp(-KAPPA_2_2 * maturity2);
    corariance22 += (Math.exp((KAPPA_1_2 + KAPPA_2_2) * endExpiry) - Math.exp((KAPPA_1_2 + KAPPA_2_2) * startExpiry)) /
        (KAPPA_1_2 + KAPPA_2_2) * Math.exp(-KAPPA_1_2 * maturity1 - KAPPA_2_2 * maturity2);
    corariance22 /= (KAPPA_1_2 * KAPPA_2_2);
    corariance22 *= VOLATILITY_1_2_CST.get(0) * VOLATILITY_2_2_CST.get(0);
    assertThat(corarianceComputed.get(1, 1)).isEqualTo(corariance22, TOLERANCE_VAR);
    
  }
  
}
