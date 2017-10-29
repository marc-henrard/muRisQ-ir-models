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
import java.time.ZoneOffset;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.ConstantCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.param.LabelParameterMetadata;

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
  private static final Curve CURVE_1 = ConstantCurve.of("C1", VAL_1);
  private static final Curve CURVE_2 = ConstantCurve.of("C2", VAL_2);
  private static final Curve CURVE_3 = 
      InterpolatedNodalCurve.of(DefaultCurveMetadata.of("C3"), 
          DoubleArray.of(0.0d, 10.0D), DoubleArray.of(3.0d, 4.0d), CurveInterpolators.LINEAR);
  
  private static final ParameterDateCurve B0 = 
      GenericParameterDateCurve.of(ScaledSecondTime.DEFAULT, CURVE_1, VALUATION_DATE);
  private static final ParameterDateCurve B1_1 =
      GenericParameterDateCurve.of(ScaledSecondTime.DEFAULT, CURVE_2, VALUATION_DATE);
  private static final ParameterDateCurve B1_2 =
      GenericParameterDateCurve.of(ScaledSecondTime.DEFAULT, CURVE_3, VALUATION_DATE);
  private static final List<IborIndex> B1_INDICES =
      ImmutableList.of(EUR_EURIBOR_3M, EUR_EURIBOR_6M);
  private static final List<ParameterDateCurve> B1_CURVES =
      ImmutableList.of(B1_1, B1_2);
  
  private static final RationalOneFactorGenericParameters PARAMETERS = RationalOneFactorGenericParameters
      .of(EUR, A, B0, B1_INDICES, B1_CURVES, TIME_MEASUREMENT, VALUATION_DATE);

  public void of() {
    assertEquals(PARAMETERS.getCurrency(), EUR);
    assertEquals(PARAMETERS.getA(), A);
    assertEquals(PARAMETERS.getTimeMeasure(), TIME_MEASUREMENT);
    assertEquals(PARAMETERS.getValuationDate(), VALUATION_DATE);
  }
  
  public void parameters() {
    RationalOneFactorGenericParameters test = RationalOneFactorGenericParameters
        .of(EUR, A, B0, B1_INDICES, B1_CURVES, TIME_MEASUREMENT, VALUATION_DATE);
    assertEquals(test.b0(VALUATION_DATE.plusYears(1)), VAL_1);
    IborIndexObservation obs3 = 
        IborIndexObservation.of(EUR_EURIBOR_3M, VALUATION_DATE.plusYears(2), REF_DATA);
    assertEquals(test.b1(obs3), VAL_2);
    LocalDate fixingDate6 = VALUATION_DATE.plusYears(3);
    IborIndexObservation obs6 = 
        IborIndexObservation.of(EUR_EURIBOR_6M, fixingDate6, REF_DATA);
    assertEquals(test.b1(obs6), B1_2.parameterValue(fixingDate6));
  }

  /* Tests parameter features. */
  public void parameters_count() {
    assertEquals(PARAMETERS.getParameterCount(), 
        1 + B0.getParameterCount() + B1_1.getParameterCount() + B1_2.getParameterCount());
  }

  public void parameters_values() {
    int loopparam = 0;
    assertEquals(PARAMETERS.getParameter(loopparam), A);
    loopparam++;
    for (int i = 0; i < B0.getParameterCount(); i++) {
      assertEquals(PARAMETERS.getParameter(loopparam), B0.getParameter(i));
      loopparam++;
    }
    for (int loopindex = 0; loopindex < B1_INDICES.size(); loopindex++) {
      for (int i = 0; i < B1_CURVES.get(loopindex).getParameterCount(); i++) {
        assertEquals(PARAMETERS.getParameter(loopparam), B1_CURVES.get(loopindex).getParameter(i));
        loopparam++;
      }
    }
  }
  
  public void parameters_metadata() {
    int loopparam = 0;
    assertEquals(PARAMETERS.getParameterMetadata(loopparam), LabelParameterMetadata.of("a"));
    loopparam++;
    for (int i = 0; i < B0.getParameterCount(); i++) {
      assertEquals(PARAMETERS.getParameterMetadata(loopparam), B0.getParameterMetadata(i));
      loopparam++;
    }
    for (int loopindex = 0; loopindex < B1_INDICES.size(); loopindex++) {
      for (int i = 0; i < B1_CURVES.get(loopindex).getParameterCount(); i++) {
        assertEquals(PARAMETERS.getParameterMetadata(loopparam), B1_CURVES.get(loopindex).getParameterMetadata(i));
        loopparam++;
      }
    }
  }
  
  public void parameters_with() {
    int nbParameters = PARAMETERS.getParameterCount();
    double test = 0.123456;
    int loopparam = 0;
    RationalOneFactorGenericParameters newParam = PARAMETERS.withParameter(loopparam, test);
    for (int j = 0; j < nbParameters; j++) {
      assertEquals(newParam.getParameter(j), (loopparam == j) ? test : PARAMETERS.getParameter(j));
    }
    loopparam++;
    for (int i = 0; i < B0.getParameterCount(); i++) {
      newParam = PARAMETERS.withParameter(loopparam, test);
      for (int j = 0; j < nbParameters; j++) {
        assertEquals(newParam.getParameter(j), (loopparam == j) ? test : PARAMETERS.getParameter(j));
      }
      loopparam++;
    }
    for (int loopindex = 0; loopindex < B1_INDICES.size(); loopindex++) {
      for (int i = 0; i < B1_CURVES.get(loopindex).getParameterCount(); i++) {
        newParam = PARAMETERS.withParameter(loopparam, test);
        for (int j = 0; j < nbParameters; j++) {
          assertEquals(newParam.getParameter(j), (loopparam == j) ? test : PARAMETERS.getParameter(j));
        }
        loopparam++;
      }
    }
  }
  
  public void serialization(){
    RationalOneFactorGenericParameters test = RationalOneFactorGenericParameters
        .of(EUR, A, B0, B1_INDICES, B1_CURVES, TIME_MEASUREMENT, VALUATION_DATE);
    assertSerialization(test);
  }
  
}
