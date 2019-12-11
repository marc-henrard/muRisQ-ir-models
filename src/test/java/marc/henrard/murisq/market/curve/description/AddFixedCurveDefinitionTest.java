/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.description;

import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_1M;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.AddFixedCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.InterpolatedNodalCurveDefinition;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.curve.node.FraCurveNode;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.TenorDateParameterMetadata;
import com.opengamma.strata.product.fra.type.FraTemplate;

/**
 * Tests {@link AddFixedCurveDefinition}.
 * 
 * @author Marc Henrard
 */
public class AddFixedCurveDefinitionTest {

  private static final LocalDate VAL_DATE = date(2015, 9, 9);
  private static final CurveName CURVE_NAME = CurveName.of("Test");
  private static final QuoteId TICKER_1 = QuoteId.of(StandardId.of("muRisQId", "Ticker1"));
  private static final QuoteId TICKER_2 = QuoteId.of(StandardId.of("muRisQId", "Ticker2"));
  private static final QuoteId TICKER_3 = QuoteId.of(StandardId.of("muRisQId", "Ticker3"));
  private static final ImmutableList<FraCurveNode> NODES = ImmutableList.of(
      FraCurveNode.of(FraTemplate.of(Period.ofMonths(3), GBP_LIBOR_1M), TICKER_1),
      FraCurveNode.of(FraTemplate.of(Period.ofMonths(6), GBP_LIBOR_1M), TICKER_2),
      FraCurveNode.of(FraTemplate.of(Period.ofMonths(9), GBP_LIBOR_1M), TICKER_3));
  private static final InterpolatedNodalCurveDefinition UNDERLYING_DEF =
      InterpolatedNodalCurveDefinition.builder()
          .name(CURVE_NAME)
          .xValueType(ValueType.YEAR_FRACTION)
          .yValueType(ValueType.DISCOUNT_FACTOR)
          .dayCount(ACT_365F)
          .nodes(NODES)
          .interpolator(CurveInterpolators.LOG_LINEAR)
          .extrapolatorLeft(CurveExtrapolators.EXPONENTIAL)
          .extrapolatorRight(CurveExtrapolators.EXPONENTIAL)
          .build();

  private final DoubleArray X_ADJ = DoubleArray.of(30.0/365.0, 31.0/365.0);
  private final DoubleArray Y_ADJ = DoubleArray.of(1.0, 1.0/(1.0+1.0/365.0*0.0010)); // 10bps 1 day
  private final Curve FIXED_CURVE = InterpolatedNodalCurve.builder()
      .xValues(X_ADJ)
      .yValues(Y_ADJ)
      .metadata(DefaultCurveMetadata.builder().curveName("MESpikes").xValueType(ValueType.YEAR_FRACTION)
          .yValueType(ValueType.DISCOUNT_FACTOR).build())
      .extrapolatorLeft(CurveExtrapolators.FLAT)
      .extrapolatorRight(CurveExtrapolators.FLAT)
      .interpolator(CurveInterpolators.LINEAR)
      .build();

  @Test
  public void builder() {
    AddFixedCurveDefinition test = AddFixedCurveDefinition.builder()
        .fixedCurve(FIXED_CURVE)
        .spreadCurveDefinition(UNDERLYING_DEF).build();
    assertThat(test.getFixedCurve()).isEqualTo(FIXED_CURVE);
    assertThat(test.getSpreadCurveDefinition()).isEqualTo(UNDERLYING_DEF);
  }

  @Test
  public void generate_curve() {
    AddFixedCurveDefinition test = AddFixedCurveDefinition.builder()
        .fixedCurve(FIXED_CURVE)
        .spreadCurveDefinition(UNDERLYING_DEF).build();
    ParameterMetadata pMeta1 = TenorDateParameterMetadata.of(LocalDate.of(2015, 10, 9), Tenor.TENOR_1M, "1M");
    ParameterMetadata pMeta2 = TenorDateParameterMetadata.of(LocalDate.of(2015, 12, 9), Tenor.TENOR_3M, "3M");
    ParameterMetadata pMeta3 = TenorDateParameterMetadata.of(LocalDate.of(2016, 3, 9), Tenor.TENOR_6M, "6M");
    CurveMetadata metadata = DefaultCurveMetadata.builder().curveName("meta-test")
        .xValueType(ValueType.YEAR_FRACTION).yValueType(ValueType.DISCOUNT_FACTOR)
        .parameterMetadata(pMeta1, pMeta2, pMeta3).build();
    Curve generatedCurve = test.curve(VAL_DATE, metadata, DoubleArray.of(1.00, 1.00, 1.00));
    assertThat(generatedCurve instanceof AddFixedCurve).isTrue();
  }
  
}
