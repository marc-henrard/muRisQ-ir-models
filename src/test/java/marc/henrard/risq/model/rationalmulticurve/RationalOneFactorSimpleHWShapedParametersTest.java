/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.param.LabelParameterMetadata;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;

import marc.henrard.risq.model.generic.ParameterDateCurve;
import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;

/**
 * Tests {@link ScaledSecondTime}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorSimpleHWShapedParametersTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  
  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
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
  
  private static final RationalOneFactorSimpleHWShapedParameters PARAMETERS =
      RationalOneFactorSimpleHWShapedParameters.of(A, B_0_0, ETA, KAPPA, TIME_MEASURE, DF, VAL_DATE_TIME);
  
  private static final LocalDate[] TEST_DATES = new LocalDate[]{
      LocalDate.of(2016, 8, 18), LocalDate.of(2016, 8, 19), LocalDate.of(2017, 8, 18), LocalDate.of(2021, 8, 18)};
  private static final int NB_TEST_DATES = TEST_DATES.length;

  private final static double TOLERANCE = 1.0E-10;
  
  public void builder() {
    assertEquals(PARAMETERS.getA(), A);
    assertEquals(PARAMETERS.getB00(), B_0_0);
    assertEquals(PARAMETERS.getEta(), ETA);
    assertEquals(PARAMETERS.getKappa(), KAPPA);
    assertEquals(PARAMETERS.getTimeMeasure(), TIME_MEASURE);
    assertEquals(PARAMETERS.getDiscountFactors(), DF);
    assertEquals(PARAMETERS.getValuationDate(), VAL_DATE);
    assertEquals(PARAMETERS.getValuationDateTime(), VAL_DATE_TIME);
  }
  
  /* Tests b0 with a local implementation of the formula. */
  public void b0() {
    assertEquals(PARAMETERS.b0(TEST_DATES[0]), B_0_0, TOLERANCE);
    for (int loopdate = 1; loopdate < NB_TEST_DATES; loopdate++) {
      double u = DF.relativeYearFraction(TEST_DATES[loopdate]);
      double pu = DF.discountFactor(u);
      double b0Expected = (B_0_0 - ETA / (A * KAPPA) * (1.0d - Math.exp(-KAPPA * u))) * pu;
      double b0Computed = PARAMETERS.b0(TEST_DATES[loopdate]);
      assertEquals(b0Computed, b0Expected, TOLERANCE);
    }
  }

  /* Tests that the b0 method and the b0 ParameterDateCurve produce the same values. */
  public void b0_parameter_curve() {
    ParameterDateCurve b0 = PARAMETERS.b0();
    for (int loopdate = 1; loopdate < NB_TEST_DATES; loopdate++) {
      assertEquals(b0.parameterValue(TEST_DATES[loopdate]), PARAMETERS.b0(TEST_DATES[loopdate]), TOLERANCE);
    }
  }
  // TODO: Check derivatives of b0 curve wrt time and parameters
  
  /* Tests b1 against b0. */
  public void b1() {
    for (int loopdate = 0; loopdate < NB_TEST_DATES; loopdate++) {
      IborIndexObservation obs = IborIndexObservation.of(EUR_EURIBOR_3M, TEST_DATES[loopdate], REF_DATA);
      double delta = EUR_EURIBOR_3M.getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
      double b1Expected = (PARAMETERS.b0(obs.getEffectiveDate()) - PARAMETERS.b0(obs.getMaturityDate())) / delta;
      double b1Computed = PARAMETERS.b1(obs);
      assertEquals(b1Computed, b1Expected, TOLERANCE);
    }
  }
  
  /* Tests parameter features. */
  public void parameters_count() {
    assertEquals(PARAMETERS.getParameterCount(), 4);
  }
  
  public void parameters_values() {
    assertEquals(PARAMETERS.getParameter(0), A);
    assertEquals(PARAMETERS.getParameter(1), B_0_0);
    assertEquals(PARAMETERS.getParameter(2), ETA);
    assertEquals(PARAMETERS.getParameter(3), KAPPA);
  }
  
  public void parameters_metadata() {
    assertEquals(PARAMETERS.getParameterMetadata(0), LabelParameterMetadata.of("a"));
    assertEquals(PARAMETERS.getParameterMetadata(1), LabelParameterMetadata.of("b_0_0"));
    assertEquals(PARAMETERS.getParameterMetadata(2), LabelParameterMetadata.of("eta"));
    assertEquals(PARAMETERS.getParameterMetadata(3), LabelParameterMetadata.of("kappa"));
  }

  public void parameters_with() {
    double test = 0.123456;
    for (int i = 0; i < 4; i++) {
      RationalOneFactorSimpleHWShapedParameters newParam = PARAMETERS.withParameter(i, test);
      for (int j = 0; j < 4; j++) {
        assertEquals(newParam.getParameter(j), (i == j) ? test : PARAMETERS.getParameter(j));
      }
    }
  }
  
  public void serialization(){
    assertSerialization(PARAMETERS);
  }
  
}
