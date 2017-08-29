/**
 * Copyright (C) 2017 - Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
 * Tests {@link RationalOneFactorSimpleHWShapedTemplate}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorSimpleHWShapedTemplateTest {

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

  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
  private static final DoubleArray GUESS = DoubleArray.of(0.1, 0.2, 0.3, 0.4);
  
  public void of() {
    RationalOneFactorSimpleHWShapedTemplate test = RationalOneFactorSimpleHWShapedTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), GUESS);
    assertEquals(test.getTimeMeasure(), TIME_MEASURE);
    assertEquals(test.getDiscountFactors(), DF);
    assertEquals(test.getValuationTime(), VAL_DATE_TIME.toLocalTime());
    assertEquals(test.getValuationZone(), VAL_DATE_TIME.getZone());
    assertEquals(test.getInitialGuess(), GUESS);
  }

  public void generate() {
    RationalOneFactorSimpleHWShapedTemplate test = RationalOneFactorSimpleHWShapedTemplate
        .of(TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone(), GUESS);
    DoubleArray parameters = DoubleArray.of(A, B_0_0, ETA, KAPPA);
    RationalOneFactorSimpleHWShapedParameters modelComputed = test.generate(parameters);
    RationalOneFactorSimpleHWShapedParameters modelExpected =
        RationalOneFactorSimpleHWShapedParameters.of(A, B_0_0, ETA, KAPPA, TIME_MEASURE, DF, VAL_DATE_TIME);
    assertEquals(modelComputed, modelExpected);
  }
  
}
