/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.BitSet;

import org.testng.annotations.Test;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;

import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;

/**
 * Tests {@link RationalTwoFactorHWShapePlusCstTemplate}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactorHWShapePlusCstTemplateTest {

  private static final ZonedDateTime VAL_DATE_TIME = ZonedDateTime.of(2016, 8, 18, 11, 12, 13, 0, ZoneId.of("Europe/Brussels"));
  private static final LocalDate VAL_DATE = VAL_DATE_TIME.toLocalDate();

  private static final TimeMeasurement TIME_MEASURE = ScaledSecondTime.DEFAULT;
  private static final CurveInterpolator INTERPOLATOR_LINEAR = CurveInterpolators.LINEAR;
  private static final CurveName CURVE_NAME = CurveName.of("EUR-DSC");
  private static final CurveMetadata METADATA = Curves.zeroRates(CURVE_NAME, ACT_365F);
  private static final DoubleArray TIMES_ZR = DoubleArray.of(0, 0.25, 0.50, 1, 2.01, 5.02, 10.00);
  private static final DoubleArray ZR = DoubleArray.of(-0.0020, -0.0010, 0.0000, 0.0015, 0.0100, 0.0090, 0.0150);
  private static final InterpolatedNodalCurve ZERO_RATES = InterpolatedNodalCurve.of(METADATA, TIMES_ZR, ZR, INTERPOLATOR_LINEAR);
  private static final DiscountFactors DF = ZeroRateDiscountFactors.of(EUR, VAL_DATE, ZERO_RATES);

  private static final double A_1 = 0.75;
  private static final double A_2 = 0.50;
  private static final double RHO = 0.10;
  private static final double B_0_0 = 0.50;
  private static final double ETA_1 = 0.01;
  private static final double KAPPA_1 = 0.03;
  private static final double C_1 = 1.10;
  private static final double C_2 = 0.25;
  private static final DoubleArray GUESS = DoubleArray.of(0.1, 0.2, 0.3, 0.4, 0.7, 0.8, 0.9, 1.0);
  private static final BitSet FIXED = new BitSet(8);
  static {
    FIXED.set(2);
    FIXED.set(7);
  }
  
  public void of() {
    RationalTwoFactorHWShapePlusCstTemplate test = RationalTwoFactorHWShapePlusCstTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), GUESS, FIXED);
    assertEquals(test.getTimeMeasure(), TIME_MEASURE);
    assertEquals(test.getDiscountFactors(), DF);
    assertEquals(test.getValuationTime(), VAL_DATE_TIME.toLocalTime());
    assertEquals(test.getValuationZone(), VAL_DATE_TIME.getZone());
    assertEquals(test.getInitialGuess(), GUESS);
  }

  public void generate() {
    RationalTwoFactorHWShapePlusCstTemplate test = RationalTwoFactorHWShapePlusCstTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), GUESS, FIXED);
    DoubleArray parameters = DoubleArray.of(A_1, A_2, RHO, B_0_0, ETA_1, KAPPA_1, C_1, C_2);
    RationalTwoFactorHWShapePlusCstParameters modelComputed = test.generate(parameters);
    RationalTwoFactorHWShapePlusCstParameters modelExpected =
        RationalTwoFactorHWShapePlusCstParameters.of(
            DoubleArray.of(A_1, A_2, RHO, B_0_0, ETA_1, KAPPA_1, C_1, C_2), 
            TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone());
    assertEquals(modelComputed, modelExpected);
  }

  public void incorrectInput() {
    BitSet fixed = new BitSet(10);
    fixed.set(11);
    assertThrowsIllegalArg(() -> RationalTwoFactorHWShapePlusCstTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), GUESS, fixed));
    DoubleArray guess11 = DoubleArray.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.8, 0.9, 1.0, 1.1);
    assertThrowsIllegalArg(() -> RationalTwoFactorHWShapePlusCstTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), guess11, FIXED));
    DoubleArray guess9 = DoubleArray.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.8, 0.9);
    assertThrowsIllegalArg(() -> RationalTwoFactorHWShapePlusCstTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), guess9, FIXED));
  }
  
}
