/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.model.shortrate;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.market.curve.interpolator.CurveExtrapolators.EXPONENTIAL;
import static com.opengamma.strata.market.curve.interpolator.CurveExtrapolators.FLAT;
import static com.opengamma.strata.market.curve.interpolator.CurveInterpolators.LOG_NATURAL_SPLINE_MONOTONE_CUBIC;
import static com.opengamma.strata.market.curve.interpolator.CurveInterpolators.NATURAL_CUBIC_SPLINE;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.SimpleDiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;

/**
 * Tests {@link ShortRateModelsUtils}.
 * 
 * @author Marc Henrard
 */
@Test
public class ShortRateModelsUtilsTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2018, 8, 20);
  private static final DayCount DC = DayCounts.ACT_365F;
  private static final CurveMetadata METADATA_DF = DefaultCurveMetadata.builder()
      .curveName("DF")
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.DISCOUNT_FACTOR)
      .dayCount(DC).build();
  private static final DoubleArray TIME_DF = DoubleArray.of(0.1, 1.0, 2.0, 5.0, 10.0);
  private static final DoubleArray VALUE_DF = DoubleArray.of(0.999, 0.99, 0.98, 0.95, 0.91);
  private static final Curve CURVE_DF = InterpolatedNodalCurve.of(METADATA_DF, TIME_DF, VALUE_DF,
      LOG_NATURAL_SPLINE_MONOTONE_CUBIC, EXPONENTIAL, EXPONENTIAL);

  private static final CurveMetadata metadataZr = DefaultCurveMetadata.builder()
      .curveName("ZR")
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.ZERO_RATE)
      .dayCount(DC).build();
  private static final DoubleArray TIME_ZR = DoubleArray.of(0.1, 1.0, 2.0, 5.0, 10.0);
  private static final DoubleArray VALUE_ZR = DoubleArray.of(0.01, 0.011, 0.01, 0.011, 0.012);
  private static final Curve CURVE_ZR = InterpolatedNodalCurve.of(metadataZr, TIME_ZR, VALUE_ZR,
      NATURAL_CUBIC_SPLINE, FLAT, FLAT);
  private static final DiscountFactors DF_SIMPLE =
      SimpleDiscountFactors.of(USD, VALUATION_DATE, CURVE_DF);
  private static final DiscountFactors DF_ZERO_RATE =
      ZeroRateDiscountFactors.of(USD, VALUATION_DATE, CURVE_ZR);

  private static final List<LocalDate> DATE_TESTS = ImmutableList.of(
      LocalDate.of(2018, 8, 27), LocalDate.of(2018, 9, 20), LocalDate.of(2019, 2, 20),
      LocalDate.of(2019, 8, 27), LocalDate.of(2021, 8, 27), LocalDate.of(2020, 8, 27));
  private static final int NB_TESTS = DATE_TESTS.size();
  
  private static final double TOLERANCE_D = 1.0E-8;

  /* Test the instantaneous forward rate using explicit formula with derivatives for 
   * discount factors described directly by an interpolated discount factor curve. */
  public void simple() {
    Function<LocalDate, Double> instFwd = ShortRateModelsUtils.instantaneousForward(DF_SIMPLE);
    for (int looptest = 0; looptest < NB_TESTS; looptest++) {
      double fwdComputed = instFwd.apply(DATE_TESTS.get(looptest));
      double fwdExpected =
          -CURVE_DF.firstDerivative(DC.relativeYearFraction(VALUATION_DATE, DATE_TESTS.get(looptest))) /
              DF_SIMPLE.discountFactor(DATE_TESTS.get(looptest));
      assertEquals(fwdComputed, fwdExpected, TOLERANCE_D);
    }
  }

  /* Test the instantaneous forward rate using explicit formula with derivatives for 
   * discount factors described by an interpolated curve with zero rates. */
  public void zeroRate() {
    Function<LocalDate, Double> instFwd = ShortRateModelsUtils.instantaneousForward(DF_ZERO_RATE);
    for (int looptest = 0; looptest < NB_TESTS; looptest++) {
      double fwdComputed = instFwd.apply(DATE_TESTS.get(looptest));
      double t = DC.relativeYearFraction(VALUATION_DATE, DATE_TESTS.get(looptest));
      double fwdExpected = CURVE_ZR.firstDerivative(t) * t + CURVE_ZR.yValue(t);
      assertEquals(fwdComputed, fwdExpected, TOLERANCE_D);
    }
  }

}
