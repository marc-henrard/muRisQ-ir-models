/**
 * Copyright (C) 2017 - present by Marc Henrard.
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

import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;

/**
 * Tests {@link RationalTwoFactor2HWShapePlusCstParameters}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactor2HWShapePlusCstParametersTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final double A_1 = 0.75;
  private static final double A_2 = 0.50;
  private static final double CORRELATION = 0.10;
  private static final double B_0_0 = 0.50;
  private static final double ETA_1 = 0.01;
  private static final double KAPPA_1 = 0.03;
  private static final double ETA_2 = 0.02;
  private static final double KAPPA_2 = 0.20;
  private static final double C_1 = 0.00;
  private static final double C_2 = 0.02;
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
  
  private static final RationalTwoFactor2HWShapePlusCstParameters PARAMETERS_2HW =
      RationalTwoFactor2HWShapePlusCstParameters.builder()
      .a1(A_1).a2(A_2).correlation(CORRELATION).b00(B_0_0).eta1(ETA_1).kappa1(KAPPA_1)
      .eta2(ETA_2).kappa2(KAPPA_2).c1(C_1).c2(C_2)
      .timeMeasure(TIME_MEASURE).discountFactors(DF).valuationTime(VAL_DATE_TIME.toLocalTime())
      .valuationZone(VAL_DATE_TIME.getZone()).build();
  private static final LocalDate[] TEST_DATES = new LocalDate[]{
      LocalDate.of(2016, 8, 18), LocalDate.of(2016, 8, 19), LocalDate.of(2017, 8, 18), LocalDate.of(2021, 8, 18)};
  private static final int NB_TEST_DATES = TEST_DATES.length;

  private final static double TOLERANCE = 1.0E-10;

  public void builder() {
    assertEquals(PARAMETERS_2HW.getA1(), A_1);
    assertEquals(PARAMETERS_2HW.getA2(), A_2);
    assertEquals(PARAMETERS_2HW.getCorrelation(), CORRELATION);
    assertEquals(PARAMETERS_2HW.getB00(), B_0_0);
    assertEquals(PARAMETERS_2HW.getEta1(), ETA_1);
    assertEquals(PARAMETERS_2HW.getKappa1(), KAPPA_1);
    assertEquals(PARAMETERS_2HW.getEta2(), ETA_2);
    assertEquals(PARAMETERS_2HW.getKappa2(), KAPPA_2);
    assertEquals(PARAMETERS_2HW.getC1(), C_1);
    assertEquals(PARAMETERS_2HW.getC2(), C_2);
    assertEquals(PARAMETERS_2HW.getTimeMeasure(), TIME_MEASURE);
    assertEquals(PARAMETERS_2HW.getDiscountFactors(), DF);
    assertEquals(PARAMETERS_2HW.getValuationDate(), VAL_DATE);
    assertEquals(PARAMETERS_2HW.getValuationDateTime(), VAL_DATE_TIME);
  }
  
  public void of() {
    RationalTwoFactor2HWShapePlusCstParameters test = RationalTwoFactor2HWShapePlusCstParameters
        .of(DoubleArray.of(A_1, A_2, CORRELATION, B_0_0, ETA_1, KAPPA_1, ETA_2, KAPPA_2, C_1, C_2), 
            TIME_MEASURE, DF, VAL_DATE_TIME.toLocalTime(), VAL_DATE_TIME.getZone());
    assertEquals(test.getA1(), A_1);
    assertEquals(test.getA2(), A_2);
    assertEquals(test.getCorrelation(), CORRELATION);
    assertEquals(test.getB00(), B_0_0);
    assertEquals(test.getEta1(), ETA_1);
    assertEquals(test.getKappa1(), KAPPA_1);
    assertEquals(test.getEta2(), ETA_2);
    assertEquals(test.getKappa2(), KAPPA_2);
    assertEquals(test.getC1(), C_1);
    assertEquals(test.getC2(), C_2);
    assertEquals(test.getTimeMeasure(), TIME_MEASURE);
    assertEquals(test.getDiscountFactors(), DF);
    assertEquals(test.getValuationDate(), VAL_DATE);
    assertEquals(test.getValuationDateTime(), VAL_DATE_TIME);
  }

  /* Tests b0 with eta2=0 v RationalTwoFactorHWShapePlusCstParameters implementation. */
  public void b0_1hw() {
    RationalTwoFactor2HWShapePlusCstParameters param2hw =
        RationalTwoFactor2HWShapePlusCstParameters.builder()
            .a1(A_1).a2(A_2).correlation(CORRELATION).b00(B_0_0).eta1(ETA_1).kappa1(KAPPA_1)
            .eta2(0.0).kappa2(KAPPA_1).c1(C_1).c2(C_2)
            .timeMeasure(TIME_MEASURE).discountFactors(DF).valuationTime(VAL_DATE_TIME.toLocalTime())
            .valuationZone(VAL_DATE_TIME.getZone()).build();
    RationalTwoFactorHWShapePlusCstParameters param1hw =
        RationalTwoFactorHWShapePlusCstParameters.builder()
            .a1(A_1).a2(A_2).correlation(CORRELATION).b00(B_0_0).eta(ETA_1).kappa(KAPPA_1).c1(C_1).c2(C_2)
            .timeMeasure(TIME_MEASURE).discountFactors(DF).valuationTime(VAL_DATE_TIME.toLocalTime())
            .valuationZone(VAL_DATE_TIME.getZone()).build();
    for (int loopdate = 1; loopdate < NB_TEST_DATES; loopdate++) {
      double b0_2 = param2hw.b0(TEST_DATES[loopdate]);
      double b0_1 = param1hw.b0(TEST_DATES[loopdate]);
      assertEquals(b0_2, b0_1, TOLERANCE);
    }
  }

  /* Tests b0 with a local implementation of the formula. */
  public void b0() {
    assertEquals(PARAMETERS_2HW.b0(TEST_DATES[0]), B_0_0, TOLERANCE);
    for (int loopdate = 1; loopdate < NB_TEST_DATES; loopdate++) {
      double u = DF.relativeYearFraction(TEST_DATES[loopdate]);
      double pu = DF.discountFactor(u);
      double b0Expected = (B_0_0 
          - ETA_1 / (A_1 * KAPPA_1) * (1.0d - Math.exp(-KAPPA_1 * u))
          + ETA_2 / (A_1 * KAPPA_2) * (1.0d - Math.exp(-KAPPA_2 * u))) * pu;
      double b0Computed = PARAMETERS_2HW.b0(TEST_DATES[loopdate]);
      assertEquals(b0Computed, b0Expected, TOLERANCE);
    }
  }
  
  /* Tests b1 against b0. */
  public void b1() {
    for (int loopdate = 0; loopdate < NB_TEST_DATES; loopdate++) {
      IborIndexObservation obs = IborIndexObservation.of(EUR_EURIBOR_3M, TEST_DATES[loopdate], REF_DATA);
      double delta = EUR_EURIBOR_3M.getDayCount().yearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
      double b1Expected = 
          (PARAMETERS_2HW.b0(obs.getEffectiveDate()) - PARAMETERS_2HW.b0(obs.getMaturityDate())) / delta
          + C_1;
      double b1Computed = PARAMETERS_2HW.b1(obs);
      assertEquals(b1Computed, b1Expected, TOLERANCE);
    }
  }
  
  /* Tests b2. */
  public void b2() {
    for (int loopdate = 0; loopdate < NB_TEST_DATES; loopdate++) {
      IborIndexObservation obs = IborIndexObservation.of(EUR_EURIBOR_3M, TEST_DATES[loopdate], REF_DATA);
      double b2Expected = C_2;
      double b2Computed = PARAMETERS_2HW.b2(obs);
      assertEquals(b2Computed, b2Expected, TOLERANCE);
    }
  }
  
  /* Tests parameter features. */
  public void parameters_count() {
    assertEquals(PARAMETERS_2HW.getParameterCount(), 10);
  }
  
  public void parameters_values() {
    assertEquals(PARAMETERS_2HW.getParameter(0), A_1);
    assertEquals(PARAMETERS_2HW.getParameter(1), A_2);
    assertEquals(PARAMETERS_2HW.getParameter(2), CORRELATION);
    assertEquals(PARAMETERS_2HW.getParameter(3), B_0_0);
    assertEquals(PARAMETERS_2HW.getParameter(4), ETA_1);
    assertEquals(PARAMETERS_2HW.getParameter(5), KAPPA_1);
    assertEquals(PARAMETERS_2HW.getParameter(6), ETA_2);
    assertEquals(PARAMETERS_2HW.getParameter(7), KAPPA_2);
    assertEquals(PARAMETERS_2HW.getParameter(8), C_1);
    assertEquals(PARAMETERS_2HW.getParameter(9), C_2);
  }
  
  public void parameters_metadata() {
    assertEquals(PARAMETERS_2HW.getParameterMetadata(0), LabelParameterMetadata.of("a1"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(1), LabelParameterMetadata.of("a2"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(2), LabelParameterMetadata.of("correlation"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(3), LabelParameterMetadata.of("b_0_0"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(4), LabelParameterMetadata.of("eta1"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(5), LabelParameterMetadata.of("kappa1"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(6), LabelParameterMetadata.of("eta2"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(7), LabelParameterMetadata.of("kappa2"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(8), LabelParameterMetadata.of("c1"));
    assertEquals(PARAMETERS_2HW.getParameterMetadata(9), LabelParameterMetadata.of("c2"));
  }

  public void parameters_with() {
    double test = 0.123456;
    for (int i = 0; i < PARAMETERS_2HW.getParameterCount(); i++) {
      RationalTwoFactor2HWShapePlusCstParameters newParam = PARAMETERS_2HW.withParameter(i, test);
      for (int j = 0; j < PARAMETERS_2HW.getParameterCount(); j++) {
        assertEquals(newParam.getParameter(j), (i == j) ? test : PARAMETERS_2HW.getParameter(j));
      }
    }
  }
  
  public void serialization(){
    assertSerialization(PARAMETERS_2HW);
  }
  
}
