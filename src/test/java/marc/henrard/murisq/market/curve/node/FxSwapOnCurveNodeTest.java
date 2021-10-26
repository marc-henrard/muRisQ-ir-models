/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.market.curve.node;

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
import java.time.Period;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.data.FxRateId;
import com.opengamma.strata.data.ImmutableMarketData;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.MarketDataId;
import com.opengamma.strata.data.MarketDataNotFoundException;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.CurveNodeDate;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.market.param.DatedParameterMetadata;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.TenorDateParameterMetadata;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.fx.FxSwapTrade;
import com.opengamma.strata.product.fx.ResolvedFxSwapTrade;
import com.opengamma.strata.product.fx.type.FxSwapTemplate;
import com.opengamma.strata.product.fx.type.ImmutableFxSwapConvention;

/**
 * Tests {@link FxSwapOnCurveNode}.
 */
public class FxSwapOnCurveNodeTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VAL_DATE = date(2015, 6, 30);
  private static final CurrencyPair EUR_USD = CurrencyPair.of(Currency.EUR, Currency.USD);
  private static final HolidayCalendarId EUTA_USNY = EUTA.combinedWith(USNY);
  private static final DaysAdjustment ON_ADJ = DaysAdjustment.ofBusinessDays(0, EUTA_USNY);
  private static final DaysAdjustment TN_ADJ = DaysAdjustment.ofBusinessDays(1, EUTA_USNY);
  private static final ImmutableFxSwapConvention CONVENTION_ON = ImmutableFxSwapConvention.of(EUR_USD, ON_ADJ);
  private static final ImmutableFxSwapConvention CONVENTION_TN = ImmutableFxSwapConvention.of(EUR_USD, TN_ADJ);
  private static final Period NEAR_PERIOD = Period.ZERO;
  private static final Period FAR_PERIOD = Period.ofDays(1);
  private static final FxSwapTemplate TEMPLATE_ON = FxSwapTemplate.of(NEAR_PERIOD, FAR_PERIOD, CONVENTION_ON);
  private static final FxSwapTemplate TEMPLATE_TN = FxSwapTemplate.of(NEAR_PERIOD, FAR_PERIOD, CONVENTION_TN);

  private static final FxRateId FX_RATE_ID = FxRateId.of(EUR_USD);
  private static final QuoteId QUOTE_ID_PTS_ON = QuoteId.of(StandardId.of("muRisQ-Ticker", "EUR_USD_ON"));
  private static final QuoteId QUOTE_ID_PTS_TN = QuoteId.of(StandardId.of("muRisQ-Ticker", "EUR_USD_TN"));
  private static final FxRate SPOT_RATE = FxRate.of(EUR_USD, 1.50d);
  private static final double FX_RATE_PTS_ON = 0.0001d;
  private static final double FX_RATE_PTS_TN = 0.0002d;
  private static final String LABEL = "Label";
  private static final String LABEL_AUTO = "1D";
  private static final MarketData MARKET_DATA = ImmutableMarketData.builder(VAL_DATE)
      .addValue(FX_RATE_ID, SPOT_RATE)
      .addValue(QUOTE_ID_PTS_ON, FX_RATE_PTS_ON)
      .addValue(QUOTE_ID_PTS_TN, FX_RATE_PTS_TN)
      .build();

  //-------------------------------------------------------------------------
  @Test
  public void test_builder_tn() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.builder()
        .label(LABEL)
        .template(TEMPLATE_TN)
        .fxRateId(FX_RATE_ID)
        .nearForwardPointsIds(ImmutableList.of(QUOTE_ID_PTS_TN))
        .date(CurveNodeDate.LAST_FIXING)
        .build();
    assertThat(test.getLabel()).isEqualTo(LABEL);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getNearForwardPointsIds()).isEqualTo(ImmutableList.of(QUOTE_ID_PTS_TN));
    assertThat(test.getFarForwardPointsId().isPresent()).isFalse();
    assertThat(test.getTemplate()).isEqualTo(TEMPLATE_TN);
    assertThat(test.getDate()).isEqualTo(CurveNodeDate.LAST_FIXING);
  }

  @Test
  public void test_builder_on() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.builder()
        .label(LABEL)
        .template(TEMPLATE_ON)
        .fxRateId(FX_RATE_ID)
        .nearForwardPointsIds(ImmutableList.of(QUOTE_ID_PTS_ON, QUOTE_ID_PTS_TN))
        .farForwardPointsId(QUOTE_ID_PTS_TN)
        .date(CurveNodeDate.LAST_FIXING)
        .build();
    assertThat(test.getLabel()).isEqualTo(LABEL);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getNearForwardPointsIds()).isEqualTo(ImmutableList.of(QUOTE_ID_PTS_ON, QUOTE_ID_PTS_TN));
    assertThat(test.getFarForwardPointsId().isPresent()).isTrue();
    assertThat(test.getFarForwardPointsId().get()).isEqualTo(QUOTE_ID_PTS_TN);
    assertThat(test.getTemplate()).isEqualTo(TEMPLATE_ON);
    assertThat(test.getDate()).isEqualTo(CurveNodeDate.LAST_FIXING);
  }

  @Test
  public void test_builder_defaults() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.builder()
        .template(TEMPLATE_TN)
        .nearForwardPointsIds(ImmutableList.of(QUOTE_ID_PTS_TN))
        .build();
    assertThat(test.getLabel()).isEqualTo(LABEL_AUTO);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getNearForwardPointsIds()).isEqualTo(ImmutableList.of(QUOTE_ID_PTS_TN));
    assertThat(test.getTemplate()).isEqualTo(TEMPLATE_TN);
    assertThat(test.getDate()).isEqualTo(CurveNodeDate.END);
  }

  @Test
  public void test_builder_noTemplate() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FxSwapOnCurveNode.builder()
            .label(LABEL).nearForwardPointsIds(ImmutableList.of(QUOTE_ID_PTS_TN)).build());
  }

  @Test
  public void test_ofOn() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.ofOn(TEMPLATE_ON, QUOTE_ID_PTS_ON, QUOTE_ID_PTS_TN);
    assertThat(test.getLabel()).isEqualTo(LABEL_AUTO);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getNearForwardPointsIds()).isEqualTo(ImmutableList.of(QUOTE_ID_PTS_ON, QUOTE_ID_PTS_TN));
    assertThat(test.getFarForwardPointsId().isPresent()).isTrue();
    assertThat(test.getFarForwardPointsId().get()).isEqualTo(QUOTE_ID_PTS_TN);
    assertThat(test.getTemplate()).isEqualTo(TEMPLATE_ON);
  }

  @Test
  public void test_ofTn() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.ofTn(TEMPLATE_ON, QUOTE_ID_PTS_TN);
    assertThat(test.getLabel()).isEqualTo(LABEL_AUTO);
    assertThat(test.getFxRateId()).isEqualTo(FX_RATE_ID);
    assertThat(test.getNearForwardPointsIds()).isEqualTo(ImmutableList.of(QUOTE_ID_PTS_TN));
    assertThat(test.getFarForwardPointsId().isPresent()).isFalse();
    assertThat(test.getTemplate()).isEqualTo(TEMPLATE_ON);
  }

  @Test
  public void test_requirements_on() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.ofOn(TEMPLATE_ON, QUOTE_ID_PTS_ON, QUOTE_ID_PTS_TN);
    Set<? extends MarketDataId<?>> set = test.requirements();
    assertThat(set.size()).isEqualTo(3);
    assertThat(set.contains(FX_RATE_ID)).isTrue();
    assertThat(set.contains(QUOTE_ID_PTS_ON)).isTrue();
    assertThat(set.contains(QUOTE_ID_PTS_TN)).isTrue();
  }

  @Test
  public void test_requirements_tn() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.ofTn(TEMPLATE_ON, QUOTE_ID_PTS_TN);
    Set<? extends MarketDataId<?>> set = test.requirements();
    assertThat(set.size()).isEqualTo(2);
    assertThat(set.contains(FX_RATE_ID)).isTrue();
    assertThat(set.contains(QUOTE_ID_PTS_TN)).isTrue();
  }

  @Test
  public void test_trade_on() {
    FxSwapOnCurveNode node = FxSwapOnCurveNode.ofOn(TEMPLATE_ON, QUOTE_ID_PTS_ON, QUOTE_ID_PTS_TN);
    FxSwapTrade trade = node.trade(1d, MARKET_DATA, REF_DATA);
    double spotRate = SPOT_RATE.fxRate(EUR_USD);
    double nearRate = spotRate - (FX_RATE_PTS_ON + FX_RATE_PTS_TN);
    FxSwapTrade expected = TEMPLATE_ON.createTrade(VAL_DATE, BuySell.BUY, 1.0, nearRate, FX_RATE_PTS_TN, REF_DATA);
    assertThat(trade).isEqualTo(expected);
    assertThat(node.resolvedTrade(1d, MARKET_DATA, REF_DATA)).isEqualTo(trade.resolve(REF_DATA));
  }

  @Test
  public void test_trade_tn() {
    FxSwapOnCurveNode node = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    FxSwapTrade trade = node.trade(1d, MARKET_DATA, REF_DATA);
    double spotRate = SPOT_RATE.fxRate(EUR_USD);
    double nearRate = spotRate - FX_RATE_PTS_TN;
    FxSwapTrade expected = TEMPLATE_TN.createTrade(VAL_DATE, BuySell.BUY, 1.0, nearRate, FX_RATE_PTS_TN, REF_DATA);
    assertThat(trade).isEqualTo(expected);
    assertThat(node.resolvedTrade(1d, MARKET_DATA, REF_DATA)).isEqualTo(trade.resolve(REF_DATA));
  }

  @Test
  public void test_trade_noMarketData() {
    FxSwapOnCurveNode node = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    MarketData marketData = MarketData.empty(VAL_DATE);
    assertThatExceptionOfType(MarketDataNotFoundException.class)
        .isThrownBy(() -> node.trade(1d, marketData, REF_DATA));
  }

  @Test
  public void test_sampleResolvedTrade() {
    FxSwapOnCurveNode node = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    ResolvedFxSwapTrade trade = node.sampleResolvedTrade(valuationDate, SPOT_RATE, REF_DATA);
    ResolvedFxSwapTrade expected = TEMPLATE_TN
        .createTrade(valuationDate, BuySell.BUY, 1d, SPOT_RATE.fxRate(EUR_USD), 0d, REF_DATA).resolve(REF_DATA);
    assertThat(trade).isEqualTo(expected);
  }

  @Test
  public void test_initialGuess() {
    FxSwapOnCurveNode node = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    assertThat(node.initialGuess(MARKET_DATA, ValueType.ZERO_RATE)).isEqualTo(0.0d);
    assertThat(node.initialGuess(MARKET_DATA, ValueType.DISCOUNT_FACTOR)).isEqualTo(1.0d);
  }

  @Test
  public void test_metadata_end() {
    FxSwapOnCurveNode node = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    LocalDate endDate = CONVENTION_TN.getBusinessDayAdjustment()
        .adjust(CONVENTION_TN.getSpotDateOffset().adjust(valuationDate, REF_DATA).plus(FAR_PERIOD), REF_DATA);
    ParameterMetadata metadata = node.metadata(valuationDate, REF_DATA);
    assertThat(((TenorDateParameterMetadata) metadata).getDate()).isEqualTo(endDate);
    assertThat(((TenorDateParameterMetadata) metadata).getTenor()).isEqualTo(Tenor.of(FAR_PERIOD));
  }

  @Test
  public void test_metadata_fixed() {
    LocalDate nodeDate = VAL_DATE.plusMonths(1);
    FxSwapOnCurveNode node = FxSwapOnCurveNode.builder()
        .label(LABEL)
        .template(TEMPLATE_TN)
        .fxRateId(FX_RATE_ID)
        .nearForwardPointsIds(ImmutableList.of(QUOTE_ID_PTS_TN))
        .date(CurveNodeDate.of(nodeDate))
        .build();
    LocalDate valuationDate = LocalDate.of(2015, 1, 22);
    DatedParameterMetadata metadata = node.metadata(valuationDate, REF_DATA);
    assertThat(metadata.getDate()).isEqualTo(nodeDate);
    assertThat(metadata.getLabel()).isEqualTo(node.getLabel());
  }

  //-------------------------------------------------------------------------
  @Test
  public void coverage() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    coverImmutableBean(test);
    FxSwapOnCurveNode test2 = FxSwapOnCurveNode.builder()
        .label(LABEL)
        .template(FxSwapTemplate.of(Period.ofMonths(1), Period.ofMonths(2),
            ImmutableFxSwapConvention.of(CurrencyPair.of(Currency.EUR, Currency.ARS), ON_ADJ)))
        .fxRateId(FxRateId.of(CurrencyPair.of(Currency.EUR, Currency.ARS)))
        .farForwardPointsId(QuoteId.of(StandardId.of("muRisQ-Ticker", "blah-blah")))
        .date(CurveNodeDate.LAST_FIXING)
        .build();
    coverBeanEquals(test, test2);
  }

  @Test
  public void test_serialization() {
    FxSwapOnCurveNode test = FxSwapOnCurveNode.ofTn(TEMPLATE_TN, QUOTE_ID_PTS_TN);
    assertSerialization(test);
  }

}
