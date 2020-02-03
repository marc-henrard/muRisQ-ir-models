/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.curve;

import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.CombinedCurve;
import com.opengamma.strata.market.curve.CurveParameterSize;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.pricer.curve.TradeCalibrationMeasure;
import com.opengamma.strata.pricer.datasets.ImmutableRatesProviderSimpleData;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.SwapDummyData;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.deposit.ResolvedIborFixingDepositTrade;
import com.opengamma.strata.product.deposit.ResolvedTermDepositTrade;
import com.opengamma.strata.product.fra.ResolvedFraTrade;
import com.opengamma.strata.product.fx.ResolvedFxSwapTrade;
import com.opengamma.strata.product.index.ResolvedIborFutureTrade;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions;

/**
 * Test {@link CalibrationSplitMeasures}.
 */
public class CalibrationSplitMeasuresTest {

  //-------------------------------------------------------------------------
  @Test
  public void test_PAR_SPREAD() {
    assertThat(CalibrationSplitMeasures.PAR_SPREAD.getName()).isEqualTo("ParSpread");
    assertThat(CalibrationSplitMeasures.PAR_SPREAD.getTradeTypes()).contains(
        ResolvedFraTrade.class,
        ResolvedFxSwapTrade.class,
        ResolvedIborFixingDepositTrade.class,
        ResolvedIborFutureTrade.class,
        ResolvedSwapTrade.class,
        ResolvedTermDepositTrade.class);
  }

  @Test
  public void test_MARKET_QUOTE() {
    assertThat(CalibrationSplitMeasures.MARKET_QUOTE.getName()).isEqualTo("MarketQuote");
    assertThat(CalibrationSplitMeasures.MARKET_QUOTE.getTradeTypes()).contains(
        ResolvedFraTrade.class,
        ResolvedIborFixingDepositTrade.class,
        ResolvedIborFutureTrade.class,
        ResolvedSwapTrade.class,
        ResolvedTermDepositTrade.class);
  }

  //-------------------------------------------------------------------------

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VAL_DATE = LocalDate.of(2014, 1, 16);  
  private static final CurveInterpolator INTERP = CurveInterpolators.LINEAR;
  private static final DoubleArray TIME_1 = DoubleArray.of(0.0, 0.1, 0.25, 0.5, 0.75, 1.0, 2.0);
  private static final DoubleArray RATE_1 = DoubleArray.of(0.0160, 0.0165, 0.0155, 0.0155, 0.0155, 0.0150, 0.0140);
  private static final DoubleArray TIME_2 = DoubleArray.of(0.0, 1.0, 2.0, 3.0);
  private static final DoubleArray RATE_2 = DoubleArray.of(0.0010, 0.0012, 0.0010, 0.0011);
  private static final InterpolatedNodalCurve CURVE_1 =
      InterpolatedNodalCurve.of(Curves.zeroRates("C1", ACT_365F), TIME_1, RATE_1, INTERP);
  private static final InterpolatedNodalCurve CURVE_2 =
      InterpolatedNodalCurve.of(Curves.zeroRates("C2", ACT_365F), TIME_2, RATE_2, INTERP);
  
  /* Tests derivatives with parameter split */
  @Test
  public void test_derivatives() {
    CombinedCurve combined = CombinedCurve.of(CURVE_1, CURVE_2);
    ImmutableRatesProvider provider = ImmutableRatesProvider.builder(VAL_DATE)
        .discountCurve(Currency.EUR, combined)
        .overnightIndexCurve(OvernightIndices.EUR_EONIA, combined)
        .build();
    List<CurveParameterSize> curveOrder1 = ImmutableList.of(
        CurveParameterSize.of(CURVE_1.getName(), CURVE_1.getParameterCount()),
        CurveParameterSize.of(CURVE_2.getName(), CURVE_2.getParameterCount()));
    List<CurveParameterSize> curveOrder2 = ImmutableList.of(
        CurveParameterSize.of(CURVE_2.getName(), CURVE_2.getParameterCount()),
        CurveParameterSize.of(CURVE_1.getName(), CURVE_1.getParameterCount()));
    ResolvedSwapTrade trade = FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS
        .createTrade(VAL_DATE, Tenor.TENOR_18M, BuySell.BUY, 1_000, 0.015, REF_DATA)
        .resolve(REF_DATA);
    DoubleArray sensitivityArray1 =
        CalibrationSplitMeasures.PAR_SPREAD.derivative(trade, provider, curveOrder1);
    DoubleArray sensitivityArray2 =
        CalibrationSplitMeasures.PAR_SPREAD.derivative(trade, provider, curveOrder2);
    assertThat(sensitivityArray1.subArray(0, CURVE_1.getParameterCount()))
        .isEqualTo(sensitivityArray2.subArray(CURVE_2.getParameterCount(),
            CURVE_2.getParameterCount() + CURVE_1.getParameterCount()));
    assertThat(sensitivityArray2.subArray(0, CURVE_2.getParameterCount()))
        .isEqualTo(sensitivityArray1.subArray(CURVE_1.getParameterCount(),
            CURVE_1.getParameterCount() + CURVE_2.getParameterCount()));
  }

  //-------------------------------------------------------------------------
  @Test
  public void test_of_array() {
    CalibrationSplitMeasures test = CalibrationSplitMeasures.of(
        "Test",
        TradeCalibrationMeasure.FRA_PAR_SPREAD,
        TradeCalibrationMeasure.SWAP_PAR_SPREAD);
    assertThat(test.getName()).isEqualTo("Test");
    assertThat(test.getTradeTypes()).containsOnly(ResolvedFraTrade.class, ResolvedSwapTrade.class);
    assertThat(test.toString()).isEqualTo("Test");
  }

  @Test
  public void test_of_list() {
    CalibrationSplitMeasures test = CalibrationSplitMeasures.of(
        "Test",
        ImmutableList.of(TradeCalibrationMeasure.FRA_PAR_SPREAD, TradeCalibrationMeasure.SWAP_PAR_SPREAD));
    assertThat(test.getName()).isEqualTo("Test");
    assertThat(test.getTradeTypes()).containsOnly(ResolvedFraTrade.class, ResolvedSwapTrade.class);
    assertThat(test.toString()).isEqualTo("Test");
  }

  @Test
  public void test_of_duplicate() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CalibrationSplitMeasures.of(
            "Test", TradeCalibrationMeasure.FRA_PAR_SPREAD, TradeCalibrationMeasure.FRA_PAR_SPREAD));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CalibrationSplitMeasures.of(
            "Test", ImmutableList.of(TradeCalibrationMeasure.FRA_PAR_SPREAD, TradeCalibrationMeasure.FRA_PAR_SPREAD)));
  }

  @Test
  public void test_measureNotKnown() {
    CalibrationSplitMeasures test = CalibrationSplitMeasures.of("Test", TradeCalibrationMeasure.FRA_PAR_SPREAD);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> test.value(SwapDummyData.SWAP_TRADE, ImmutableRatesProviderSimpleData.IMM_PROV_EUR_FIX))
        .withMessage("Trade type 'ResolvedSwapTrade' is not supported for calibration");
  }

}
