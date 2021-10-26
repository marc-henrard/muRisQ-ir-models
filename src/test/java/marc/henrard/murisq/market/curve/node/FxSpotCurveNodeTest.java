/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.node;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.USNY;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.data.FxRateId;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.MarketDataId;
import com.opengamma.strata.data.MarketDataNotFoundException;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.market.param.DatedParameterMetadata;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.fx.FxSingle;
import com.opengamma.strata.product.fx.FxSingleTrade;

/**
 * Tests {@link FxSpotCurveNode}.
 */
public class FxSpotCurveNodeTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VAL_DATE = date(2015, 6, 30);
  private static final CurrencyPair EUR_USD = CurrencyPair.of(Currency.EUR, Currency.USD);
  private static final HolidayCalendarId EUTA_USNY = EUTA.combinedWith(USNY);
  private static final DaysAdjustment SPOT_ADJ = DaysAdjustment.ofBusinessDays(2, EUTA_USNY);

  private static final FxRateId FX_RATE_ID = FxRateId.of(EUR_USD);
  
  private static final String LABEL = "Label";

  private static final QuoteId QUOTE_ID_PTS_ON = QuoteId.of(StandardId.of("muRisQ-Ticker", "EUR_USD_ON"));
  private static final FxRate SPOT_RATE = FxRate.of(EUR_USD, 1.50d);
  private static final double FX_RATE_PTS_ON = 0.0001d;
  private static final MarketData MARKET_DATA = ImmutableMarketData.builder(VAL_DATE)
      .addValue(FX_RATE_ID, SPOT_RATE)
      .addValue(QUOTE_ID_PTS_ON, FX_RATE_PTS_ON)
      .build();
  
  @Test
  public void test_builder() {
    FxSpotCurveNode test = FxSpotCurveNode.builder()
        .spotDateOffset(SPOT_ADJ)
        .fxRateId(FX_RATE_ID)
        .label(LABEL)
        .build();
    assertThat(test.getLabel()).isEqualTo(LABEL);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getSpotDateOffset()).isEqualTo(SPOT_ADJ);
  }
  
  @Test
  public void test_of() {
    FxSpotCurveNode test = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    assertThat(test.getLabel()).isEqualTo(LABEL);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getSpotDateOffset()).isEqualTo(SPOT_ADJ);
  }

  @Test
  public void test_builder_noOffset() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FxSpotCurveNode.builder()
            .fxRateId(FX_RATE_ID)
            .label(LABEL)
            .build());
  }

  @Test
  public void test_builder_noFxId() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FxSpotCurveNode.builder()
            .spotDateOffset(SPOT_ADJ)
            .label(LABEL)
            .build());
  }
  
  @Test
  public void test_requirements() {
    FxSpotCurveNode test = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    Set<? extends MarketDataId<?>> set = test.requirements();
    assertThat(set.size()).isEqualTo(1);
  }

  @Test
  public void test_trade() {
    FxSpotCurveNode node = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    FxSingleTrade trade = node.trade(1d, MARKET_DATA, REF_DATA);
    LocalDate spotDate = SPOT_ADJ.adjust(VAL_DATE, REF_DATA);
    FxSingle fx = FxSingle.of(CurrencyAmount.of(EUR, 1.0d), SPOT_RATE, spotDate);
    FxSingleTrade expected = FxSingleTrade.of(TradeInfo.of(VAL_DATE), fx);
    assertThat(trade).isEqualTo(expected);
    assertThat(node.resolvedTrade(1d, MARKET_DATA, REF_DATA)).isEqualTo(trade.resolve(REF_DATA));
  }

  @Test
  public void test_trade_noMarketData() {
    FxSpotCurveNode node = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    MarketData marketData = MarketData.empty(VAL_DATE);
    assertThatExceptionOfType(MarketDataNotFoundException.class)
        .isThrownBy(() -> node.trade(1d, marketData, REF_DATA));
  }

  @Test
  public void test_initialGuess() {
    FxSpotCurveNode node = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    assertThat(node.initialGuess(MARKET_DATA, ValueType.ZERO_RATE)).isEqualTo(SPOT_RATE.fxRate(EUR_USD));
    assertThat(node.initialGuess(MARKET_DATA, ValueType.DISCOUNT_FACTOR)).isEqualTo(SPOT_RATE.fxRate(EUR_USD));
  }

  @Test
  public void test_metadata_end() {
    FxSpotCurveNode node = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    DatedParameterMetadata metadata = node.metadata(valuationDate, REF_DATA);
    assertThat(metadata.getDate()).isEqualTo(LocalDate.MIN);
    assertThat(metadata.getLabel()).isEqualTo(LABEL);
  }

  //-------------------------------------------------------------------------
  @Test
  public void coverage() {
    FxSpotCurveNode test = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    coverImmutableBean(test);
    FxSpotCurveNode test2 = FxSpotCurveNode
        .of(DaysAdjustment.ofBusinessDays(0, EUTA_USNY), FxRateId.of(CurrencyPair.of(EUR, Currency.ARS)), "Blah");
    coverBeanEquals(test, test2);
  }

  @Test
  public void test_serialization() {
    FxSpotCurveNode test = FxSpotCurveNode.of(SPOT_ADJ, FX_RATE_ID, LABEL);
    assertSerialization(test);
  }
  
}
