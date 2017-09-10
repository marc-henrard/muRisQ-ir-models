/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;


import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static org.testng.Assert.assertEquals;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
 * Tests {@link RationalOneFactorGenericParameters}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorGenericParametersTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  public static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  public static final LocalTime LOCAL_TIME = LocalTime.NOON;
  public static final ZoneOffset ZONE_OFFSET = ZoneOffset.UTC;
  public static final double A = 0.01;
  public static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;
  public static final DayCount DAY_COUNT = DayCounts.ACT_365F;
  private static final double VAL_1 = 1.0d;
  private static final double VAL_2 = 2.0d;
  private static final double VAL_3 = 3.0d;
  private static final Curve CURVE_1 = ConstantCurve.of("C1", VAL_1);
  private static final Curve CURVE_2 = ConstantCurve.of("C2", VAL_2);
  private static final Curve CURVE_3 = ConstantCurve.of("C3", VAL_3);
  
  private static final ParameterDateCurve B0 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_1, VALUATION_DATE);
  private static final ParameterDateCurve B1_1 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_2, VALUATION_DATE);
  private static final ParameterDateCurve B1_2 = 
      GenericParameterDateCurve.of(DAY_COUNT, CURVE_3, VALUATION_DATE);
  private static final Map<IborIndex, ParameterDateCurve> B1 =
      ImmutableMap.of(EUR_EURIBOR_3M, B1_1, EUR_EURIBOR_6M, B1_2);
  
  public void of() {
    RationalOneFactorGenericParameters test = RationalOneFactorGenericParameters
        .of(EUR, A, B0, B1, TIME_MEASUREMENT, VALUATION_DATE);
    assertEquals(test.getCurrency(), EUR);
    assertEquals(test.getA(), A);
    assertEquals(test.getTimeMeasure(), TIME_MEASUREMENT);
    assertEquals(test.getValuationDate(), VALUATION_DATE);
  }
  
  public void parameters() {
    RationalOneFactorGenericParameters test = RationalOneFactorGenericParameters
        .of(EUR, A, B0, B1, TIME_MEASUREMENT, VALUATION_DATE);
    assertEquals(test.b0(VALUATION_DATE.plusYears(1)), VAL_1);
    IborIndexObservation obs3 = 
        IborIndexObservation.of(EUR_EURIBOR_3M, VALUATION_DATE.plusYears(2), REF_DATA);
    assertEquals(test.b1(obs3), VAL_2);
    IborIndexObservation obs6 = 
        IborIndexObservation.of(EUR_EURIBOR_6M, VALUATION_DATE.plusYears(3), REF_DATA);
    assertEquals(test.b1(obs6), VAL_3);
  }
  
  public void serialization(){
    RationalOneFactorGenericParameters test = RationalOneFactorGenericParameters
        .of(EUR, A, B0, B1, TIME_MEASUREMENT, VALUATION_DATE);
    assertSerialization(test);
  }
  
}
