/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.description;

import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.AddFixedCurve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolator;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.param.LabelDateParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.UnitParameterSensitivity;

/**
 * Test {@link MultiplyFixedCurve}.
 * 
 * @author Marc Henrard
 */
public class MultiplyFixedCurveTest {

  private static final String NAME_FIXED = "FixedCurve";
  private static final String NAME_SPREAD = "SpreadCurve";
  private static final CurveName FIXED_CURVE_NAME = CurveName.of(NAME_FIXED);
  private static final CurveName SPREAD_CURVE_NAME = CurveName.of(NAME_SPREAD);
  private static final CurveMetadata METADATA_FIXED = Curves.discountFactors(FIXED_CURVE_NAME, ACT_365F);
  private static final String LABEL_1 = "Node1";
  private static final String LABEL_2 = "Node2";
  private static final String LABEL_3 = "Node3";
  private static final List<ParameterMetadata> PARAM_METADATA_SPREAD = new ArrayList<>();
  static {
    PARAM_METADATA_SPREAD.add(LabelDateParameterMetadata.of(LocalDate.of(2015, 1, 1), LABEL_1));
    PARAM_METADATA_SPREAD.add(LabelDateParameterMetadata.of(LocalDate.of(2015, 2, 1), LABEL_2));
    PARAM_METADATA_SPREAD.add(LabelDateParameterMetadata.of(LocalDate.of(2015, 3, 1), LABEL_3));
  }
  private static final CurveMetadata METADATA_SPREAD = DefaultCurveMetadata.builder()
      .curveName(SPREAD_CURVE_NAME)
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.DISCOUNT_FACTOR)
      .dayCount(ACT_365F)
      .parameterMetadata(PARAM_METADATA_SPREAD)
      .build();

  private static final double[] JUMP_LEVEL = {0.0010d, 0.0015d};
  private static final DoubleArray XVALUES_FIXED =
      DoubleArray.of(1.0, 1.0d + 1.0 / 365.0, 2.0d, 2.0d + 3.0 / 365.0);
  private static final DoubleArray YVALUES_FIXED =
      DoubleArray.of(1.0, 1.0 / (1.0 + 1.0 / 365.0 * JUMP_LEVEL[0]), 1.0 / (1.0 + 1.0 / 365.0 * JUMP_LEVEL[0]),
          1.0 / ((1.0 + 1.0 / 365.0 * JUMP_LEVEL[0]) * (1.0 + 3.0 / 365.0 * JUMP_LEVEL[1])));
  // 10bps 1d + 15bps 3 day
  private static final DoubleArray XVALUES_SPREAD = DoubleArray.of(1.5d, 2.5d, 4.5d);
  private static final DoubleArray YVALUES_SPREAD = DoubleArray.of(0.95, 0.90, 0.80);
  private static final CurveInterpolator INTERPOLATOR_FIXED = CurveInterpolators.LINEAR;
  private static final CurveExtrapolator EXTRAPOLATOR_FLAT = CurveExtrapolators.FLAT;
  private static final CurveInterpolator INTERPOLATOR_SPREAD = CurveInterpolators.LOG_LINEAR;
  private static final CurveExtrapolator EXTRAPOLATOR_EXP = CurveExtrapolators.EXPONENTIAL;
  private static final double[] X_SAMPLE = {0.5d, 1.0d, 1.5d, 1.75d, 10.0d};
  private static final int NB_X_SAMPLE = X_SAMPLE.length;
  private static final double[] X_SAMPLE_JUMP = {1.0d, 2.0d};
  private static final int[] X_SAMPLE_JUMP_DAYS = {1, 3};
  private static final int NB_X_SAMPLE_JUMP = X_SAMPLE_JUMP.length;

  private static final InterpolatedNodalCurve FIXED_CURVE =
      InterpolatedNodalCurve.of(METADATA_FIXED, XVALUES_FIXED, YVALUES_FIXED, INTERPOLATOR_FIXED,
          EXTRAPOLATOR_FLAT, EXTRAPOLATOR_FLAT);
  private static final InterpolatedNodalCurve SPREAD_CURVE =
      InterpolatedNodalCurve.of(METADATA_SPREAD, XVALUES_SPREAD, YVALUES_SPREAD, INTERPOLATOR_SPREAD,
          EXTRAPOLATOR_EXP, EXTRAPOLATOR_EXP);

  private static final MultiplyFixedCurve MULT_FIXED_CURVE = MultiplyFixedCurve.of(FIXED_CURVE, SPREAD_CURVE);

  private static final Offset<Double> TOLERANCE_Y = Offset.offset(1.0E-10);

  @Test
  public void test_invalid() {
    // null fixed
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MultiplyFixedCurve.of(null, SPREAD_CURVE));
    // null spread
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MultiplyFixedCurve.of(FIXED_CURVE, null));
  }

  @Test
  public void getter() {
    assertThat(MULT_FIXED_CURVE.getMetadata()).isEqualTo(METADATA_SPREAD);
    assertThat(MULT_FIXED_CURVE.getParameterCount()).isEqualTo(XVALUES_SPREAD.size());
    assertThat(MULT_FIXED_CURVE.getParameter(0)).isEqualTo(MULT_FIXED_CURVE.getSpreadCurve().getParameter(0));
    assertThat(MULT_FIXED_CURVE.getParameterMetadata(0))
        .isEqualTo(MULT_FIXED_CURVE.getSpreadCurve().getParameterMetadata(0));
    assertThat(MULT_FIXED_CURVE.withParameter(0, 9d))
        .isEqualTo(MultiplyFixedCurve.of(FIXED_CURVE, SPREAD_CURVE.withParameter(0, 9d)));
    assertThat(MULT_FIXED_CURVE.withPerturbation((i, v, m) -> v + 1d)).isEqualTo(
        MultiplyFixedCurve.of(FIXED_CURVE, SPREAD_CURVE.withPerturbation((i, v, m) -> v + 1d)));
    assertThat(MULT_FIXED_CURVE.withMetadata(METADATA_FIXED)).isEqualTo(
        MultiplyFixedCurve.of(FIXED_CURVE, SPREAD_CURVE.withMetadata(METADATA_FIXED)));
  }

  /* Check the jumps at the nodes of the fixed curve. */
  @Test
  public void yValueJump() {
    for (int i = 0; i < NB_X_SAMPLE_JUMP; i++) {
      double yStartBefore = MULT_FIXED_CURVE.yValue(X_SAMPLE_JUMP[i] - 0.01);
      double yEndBefore = MULT_FIXED_CURVE.yValue(X_SAMPLE_JUMP[i] - 0.01 + X_SAMPLE_JUMP_DAYS[i] / 365.0d);
      double rateBefore = 365.0d / X_SAMPLE_JUMP_DAYS[i] * (yStartBefore / yEndBefore - 1.0d);
      double yStart = MULT_FIXED_CURVE.yValue(X_SAMPLE_JUMP[i]);
      double yEnd = MULT_FIXED_CURVE.yValue(X_SAMPLE_JUMP[i] + X_SAMPLE_JUMP_DAYS[i] / 365.0d);
      double rate = 365.0d / X_SAMPLE_JUMP_DAYS[i] * (yStart / yEnd - 1.0d);
      double yStartAfter = MULT_FIXED_CURVE.yValue(X_SAMPLE_JUMP[i] + 0.01);
      double yEndAfter = MULT_FIXED_CURVE.yValue(X_SAMPLE_JUMP[i] + 0.01 + X_SAMPLE_JUMP_DAYS[i] / 365.0d);
      double rateAfter = 365.0d / X_SAMPLE_JUMP_DAYS[i] * (yStartAfter / yEndAfter - 1.0d);
      assertThat(rateBefore + JUMP_LEVEL[i] - 0.0001 < rate).isTrue(); // , "Jump before big enough " + i);
      assertThat(rateBefore + JUMP_LEVEL[i] + 0.0001 > rate).isTrue(); // "Jump before not too big " + i);
      assertThat(rateAfter + JUMP_LEVEL[i] - 0.0001 < rate).isTrue(); // "Jump after big enough " + i);
      assertThat(rateAfter + JUMP_LEVEL[i] + 0.0001 > rate).isTrue(); // "Jump after not too big " + i);
    }
  }

  @Test
  public void yValue() {
    for (int i = 0; i < NB_X_SAMPLE; i++) {
      double yComputed = MULT_FIXED_CURVE.yValue(X_SAMPLE[i]);
      double yExpected = FIXED_CURVE.yValue(X_SAMPLE[i]) * SPREAD_CURVE.yValue(X_SAMPLE[i]);
      assertThat(yComputed).isEqualTo(yExpected, TOLERANCE_Y);
    }
  }

  @Test
  public void firstDerivative() {
    for (int i = 0; i < NB_X_SAMPLE; i++) {
      double dComputed = MULT_FIXED_CURVE.firstDerivative(X_SAMPLE[i]);
      double dExpected = FIXED_CURVE.firstDerivative(X_SAMPLE[i]) * SPREAD_CURVE.yValue(X_SAMPLE[i]) 
          + FIXED_CURVE.yValue(X_SAMPLE[i]) * SPREAD_CURVE.firstDerivative(X_SAMPLE[i]);
      assertThat(dComputed).isEqualTo(dExpected, TOLERANCE_Y);
    }
  }

  @Test
  public void yParameterSensitivity() {
    for (int i = 0; i < X_SAMPLE.length; i++) {
      UnitParameterSensitivity dComputed = MULT_FIXED_CURVE.yValueParameterSensitivity(X_SAMPLE[i]);
      UnitParameterSensitivity dExpected = SPREAD_CURVE.yValueParameterSensitivity(X_SAMPLE[i]);
      assertThat(dComputed.compareKey(dExpected) == 0);
      assertThat(dComputed.getSensitivity().equalWithTolerance(dExpected.getSensitivity(), TOLERANCE_Y.value));
    }
  }

  @Test
  public void underlyingCurve() {
    assertThat(MULT_FIXED_CURVE.split()).isEqualTo(ImmutableList.of(FIXED_CURVE, SPREAD_CURVE));
    CurveMetadata metadata = DefaultCurveMetadata.builder()
        .curveName(CurveName.of("newCurve"))
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE)
        .dayCount(ACT_365F)
        .parameterMetadata(PARAM_METADATA_SPREAD)
        .build();
    InterpolatedNodalCurve newCurve = InterpolatedNodalCurve.of(
        metadata, XVALUES_SPREAD, YVALUES_SPREAD, INTERPOLATOR_SPREAD);
    assertThat(
        MULT_FIXED_CURVE.withUnderlyingCurve(0, newCurve)).isEqualTo(
        MultiplyFixedCurve.of(newCurve, SPREAD_CURVE));
    assertThat(
        MULT_FIXED_CURVE.withUnderlyingCurve(1, newCurve)).isEqualTo(
        MultiplyFixedCurve.of(FIXED_CURVE, newCurve));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> MULT_FIXED_CURVE.withUnderlyingCurve(2, newCurve));
  }

  //-------------------------------------------------------------------------
  @Test
  public void coverage() {
    coverImmutableBean(MULT_FIXED_CURVE);
    coverBeanEquals(MULT_FIXED_CURVE, AddFixedCurve.of(SPREAD_CURVE, FIXED_CURVE));
  }

}
