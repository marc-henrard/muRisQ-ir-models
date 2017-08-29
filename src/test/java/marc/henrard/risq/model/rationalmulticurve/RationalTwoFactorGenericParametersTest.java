/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.market.curve.ConstantCurve;
import com.opengamma.strata.market.curve.Curve;

import marc.henrard.risq.model.generic.GenericParameterDateCurve;
import marc.henrard.risq.model.generic.ParameterDateCurve;
import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;

/**
 * Tests {@link RationalTwoFactorGenericParameters}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactorGenericParametersTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  public static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  public static final ZoneId ZONE_ID = ZoneId.of("Europe/London");
  public static final LocalTime VALUATION_TIME = LocalTime.NOON;
  public static final double A1 = 0.01;
  public static final double A2 = 0.02;
  public static final double RHO = 0.50;
  public static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;
  public static final DayCount DAY_COUNT = DayCounts.ACT_365F;
  private static final double VAL_1 = 1.0d;
  private static final double VAL_2 = 2.0d;
  private static final double VAL_3 = 3.0d;
  private static final double VAL_4 = 4.0d;
  private static final Curve CURVE_1 = ConstantCurve.of("C1", VAL_1);
  private static final Curve CURVE_2 = ConstantCurve.of("C2", VAL_2);
  private static final Curve CURVE_3 = ConstantCurve.of("C3", VAL_3);
  private static final Curve CURVE_4 = ConstantCurve.of("C4", VAL_4);
  
  private static final ParameterDateCurve B0 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_1, VALUATION_DATE);
  private static final ParameterDateCurve B1_1 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_2, VALUATION_DATE);
  private static final ParameterDateCurve B1_2 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_3, VALUATION_DATE);
  private static final ParameterDateCurve B2_1 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_3, VALUATION_DATE);
  private static final ParameterDateCurve B2_2 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_4, VALUATION_DATE);
  private static final Map<IborIndex, ParameterDateCurve> B1 =
      ImmutableMap.of(EUR_EURIBOR_3M, B1_1, EUR_EURIBOR_6M, B1_2);
  private static final Map<IborIndex, ParameterDateCurve> B2 =
      ImmutableMap.of(EUR_EURIBOR_3M, B2_1, EUR_EURIBOR_6M, B2_2);
  
  public void of() {
    RationalTwoFactorGenericParameters test = RationalTwoFactorGenericParameters
        .of(EUR, A1, A2, RHO, B0, B1, B2, TIME_MEASUREMENT, VALUATION_DATE, VALUATION_TIME, ZONE_ID);
    assertEquals(test.getCurrency(), EUR);
    assertEquals(test.a1(), A1);
    assertEquals(test.a2(), A2);
    assertEquals(test.getCorrelation(), RHO);
    assertEquals(test.getTimeMeasure(), TIME_MEASUREMENT);
    assertEquals(test.getValuationDate(), VALUATION_DATE);
    assertEquals(test.getValuationTime(), VALUATION_TIME);
    assertEquals(test.getValuationZone(), ZONE_ID);
  }
  
  public void parameters() {
    RationalTwoFactorGenericParameters test = RationalTwoFactorGenericParameters
        .of(EUR, A1, A2, RHO, B0, B1, B2, TIME_MEASUREMENT, VALUATION_DATE, VALUATION_TIME, ZONE_ID);
    assertEquals(test.b0(VALUATION_DATE.plusYears(1)), VAL_1);
    IborIndexObservation obs3 = 
        IborIndexObservation.of(EUR_EURIBOR_3M, VALUATION_DATE.plusYears(2), REF_DATA);
    assertEquals(test.b1(obs3), VAL_2);
    assertEquals(test.b2(obs3), VAL_3);
    IborIndexObservation obs6 = 
        IborIndexObservation.of(EUR_EURIBOR_6M, VALUATION_DATE.plusYears(3), REF_DATA);
    assertEquals(test.b1(obs6), VAL_3);
    assertEquals(test.b2(obs6), VAL_4);
  }
  
  public void serialization(){
    RationalTwoFactorGenericParameters test = RationalTwoFactorGenericParameters
        .of(EUR, A1, A2, RHO, B0, B1, B2, TIME_MEASUREMENT, VALUATION_DATE, VALUATION_TIME, ZONE_ID);
    assertSerialization(test);
  }
  
}
