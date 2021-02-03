/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrParametersSwaptionVolatilities;
import com.opengamma.strata.pricer.swaption.SabrSwaptionCashParYieldProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.CashSwaptionSettlement;
import com.opengamma.strata.product.swaption.CashSwaptionSettlementMethod;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.dataset.SabrSwaptionEurDataSet;

/**
 * Tests {@link Volatility2DiscountingSwaptionCashParYieldProductPricer} 
 * and {@link Sabr2DiscountingSwaptionCashParYieldProductPricer}.
 * 
 * @author Marc Henrard
 */
public class Sabr2DiscountingSwaptionCashParYieldProductPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());

  private static final Currency CCY = Currency.EUR;
  private static final Pair<ImmutableRatesProvider, SabrParametersSwaptionVolatilities> MARKET_DATA =
      SabrSwaptionEurDataSet.sabrParameters();
  private static final ImmutableRatesProvider MULTICURVE_ESTR = MARKET_DATA.getFirst();
  private static final ImmutableRatesProvider MULTICURVE_EONIA =
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  private static final DiscountFactors DISCOUNTING_BEFORE_SWITCH =
      MULTICURVE_EONIA.discountFactors(CCY);
  private static final SabrParametersSwaptionVolatilities SABR = MARKET_DATA.getSecond();
  private static final LocalDate VALUATION_DATE = MULTICURVE_EONIA.getValuationDate();

  /* Swaption description */
  private static final double NOTIONAL = 1_000_000.0d;
  private static final LocalTime EXERCISE_TIME = LocalTime.of(11, 0);
  private static final ZoneId EXERCISE_ZONE = ZoneId.of("Europe/Brussels");

  /* Pricers */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final Sabr2DiscountingSwaptionCashParYieldProductPricer PRICER_SWPT_SABR_2 =
      Sabr2DiscountingSwaptionCashParYieldProductPricer.DEFAULT;
  private static final SabrSwaptionCashParYieldProductPricer PRICER_SWPT_SABR_1 =
      SabrSwaptionCashParYieldProductPricer.DEFAULT;
  private static final RatesFiniteDifferenceSensitivityCalculator FD_CALC =
      new RatesFiniteDifferenceSensitivityCalculator(1.0E-7); // Better precision

  private static final Offset<Double> TOLERANCE_PV = Offset.offset(1.0E-2);
  private static final double TOLERANCE_PV01 = 1.0E+2;

  /* Compare the pricing with 2 discounting V 1 discounting and local adjustment */
  @Test
  public void pv_v_1dsc() {
    Period switchPeriod = Period.ofMonths(12);
    Period expiryPeriod = Period.ofMonths(18);
    Tenor tenor = Tenor.TENOR_10Y;
    double moneyness = 0.0010;
    LocalDate switchDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(switchPeriod));
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiryPeriod));
    ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
    double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EONIA);
    for (int loopPayRec = 0; loopPayRec < 2; loopPayRec++) {
      CurrencyAmount[] pv2Computed = new CurrencyAmount[2];
      for (int loopShortLong = 0; loopShortLong < 2; loopShortLong++) {
        SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenor,
                (loopPayRec == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, parRate + moneyness,
                REF_DATA);

        Swaption swaptionPayLong = Swaption.builder()
            .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(EXERCISE_TIME).expiryZone(EXERCISE_ZONE)
            .longShort((loopShortLong == 0) ? LongShort.LONG : LongShort.SHORT)
            .swaptionSettlement(
                CashSwaptionSettlement.of(swap.getProduct().getStartDate().getUnadjusted(),
                    CashSwaptionSettlementMethod.PAR_YIELD))
            .underlying(swap.getProduct()).build();
        ResolvedSwaption swaptionResolved = swaptionPayLong.resolve(REF_DATA);
        CurrencyAmount pv1 = PRICER_SWPT_SABR_1
            .presentValue(swaptionResolved, MULTICURVE_ESTR, SABR);
        double df1 = DISCOUNTING_BEFORE_SWITCH.discountFactor(switchDate);
        double df2 = MULTICURVE_ESTR.discountFactor(CCY, switchDate);
        CurrencyAmount pv2Expected = pv1.multipliedBy(df1 / df2);
        pv2Computed[loopShortLong] = PRICER_SWPT_SABR_2
            .presentValue(swaptionResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, MULTICURVE_ESTR, SABR);
        assertThat(pv2Computed[loopShortLong].getCurrency()).isEqualTo(CCY);
        assertThat(pv2Computed[loopShortLong].getAmount()).isEqualTo(pv2Expected.getAmount(), TOLERANCE_PV);
      }
      assertThat(pv2Computed[0].getAmount()).isEqualTo(-pv2Computed[1].getAmount(), TOLERANCE_PV);
    }
  }

  /* Test PV01 against finite difference */
  @Test
  public void pv01() {
    Period switchPeriod = Period.ofMonths(12);
    Period expiryPeriod = Period.ofMonths(18);
    Tenor tenor = Tenor.TENOR_10Y;
    double moneyness = 0.0010;
    LocalDate switchDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(switchPeriod));
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiryPeriod));
    ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
    double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EONIA);
    for (int loopPayRec = 0; loopPayRec < 2; loopPayRec++) {
      for (int loopShortLong = 0; loopShortLong < 2; loopShortLong++) {
        SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenor,
                (loopPayRec == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, parRate + moneyness,
                REF_DATA);
        Swaption swaptionPayLong = Swaption.builder()
            .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(EXERCISE_TIME).expiryZone(EXERCISE_ZONE)
            .longShort((loopShortLong == 0) ? LongShort.LONG : LongShort.SHORT)
            .swaptionSettlement(
                CashSwaptionSettlement.of(swap.getProduct().getStartDate().getUnadjusted(),
                    CashSwaptionSettlementMethod.PAR_YIELD))
            .underlying(swap.getProduct()).build();
        ResolvedSwaption swaptionResolved = swaptionPayLong.resolve(REF_DATA);
        CurrencyAmount pv = PRICER_SWPT_SABR_2
            .presentValue(swaptionResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, MULTICURVE_ESTR, SABR);
        Triple<CurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder> pvPv01 = PRICER_SWPT_SABR_2
            .presentValueSensitivityRatesStickyModel(
                swaptionResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, MULTICURVE_ESTR, SABR);
        CurrencyAmount pvFromSensi = pvPv01.getFirst();
        assertThat(pv.getCurrency()).isEqualTo(pvFromSensi.getCurrency());
        assertThat(pvFromSensi.getAmount()).isEqualTo(pv.getAmount(), TOLERANCE_PV);
        PointSensitivities sensitivitiesRateBefore = pvPv01.getSecond().build();
        PointSensitivities sensitivitiesRateAfter = pvPv01.getThird().build();
        CurrencyParameterSensitivities sensiEoniaComputed =
            MULTICURVE_EONIA.parameterSensitivity(sensitivitiesRateBefore);
        CurrencyParameterSensitivities sensiEstrComputed =
            MULTICURVE_ESTR.parameterSensitivity(sensitivitiesRateAfter);
        CurrencyParameterSensitivities sensiComputed = sensiEoniaComputed.combinedWith(sensiEstrComputed);
        CurrencyParameterSensitivities sensiEstrFd =
            FD_CALC.sensitivity(MULTICURVE_ESTR, m -> PRICER_SWPT_SABR_2
                .presentValue(swaptionResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, m, SABR));
        CurrencyParameterSensitivities sensiEoniaFd =
            FD_CALC.sensitivity(MULTICURVE_EONIA, m -> PRICER_SWPT_SABR_2
                .presentValue(swaptionResolved, m.discountFactors(CCY), switchDate, MULTICURVE_ESTR, SABR));
        CurrencyParameterSensitivities sensiFd = sensiEoniaFd.combinedWith(sensiEstrFd);
        assertThat(sensiComputed.equalWithTolerance(sensiFd, TOLERANCE_PV01)).isTrue();
      }
    }
  }

}
