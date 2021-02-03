/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapTrade;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;

/**
 * Tests {@link Discounting2SwapProductPricer}.
 * 
 * @author Marc Henrard
 */
public class Discounting2SwapProductPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());

  private static final Currency CCY = Currency.EUR;
  private static final ImmutableRatesProvider MULTICURVE_ESTR =
      MulticurveEur20151120DataSet.MULTICURVE_EUR_ESTR_20151120;
  private static final ImmutableRatesProvider MULTICURVE_EONIA =
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  private static final DiscountFactors DISCOUNTING_BEFORE_SWITCH =
      MULTICURVE_EONIA.discountFactors(CCY);
  private static final LocalDate VALUATION_DATE = MULTICURVE_EONIA.getValuationDate();

  /* Swaption description */
  private static final double NOTIONAL = 1_000_000.0d;

  /* Pricers */
  private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;
  private static final Discounting2SwapProductPricer PRICER_SWAP_2DSC = Discounting2SwapProductPricer.DEFAULT;
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
    MultiCurrencyAmount[] pv2Computed = new MultiCurrencyAmount[2];
    for (int loopPayRec = 0; loopPayRec < 2; loopPayRec++) {
      SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
          .createTrade(expiryDate, tenor,
              (loopPayRec == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, parRate + moneyness,
              REF_DATA);
      ResolvedSwap swapResolved = swap.resolve(REF_DATA).getProduct();
      MultiCurrencyAmount pv1 = PRICER_SWAP.presentValue(swapResolved, MULTICURVE_ESTR);
      double df1 = DISCOUNTING_BEFORE_SWITCH.discountFactor(switchDate);
      double df2 = MULTICURVE_ESTR.discountFactor(CCY, switchDate);
      MultiCurrencyAmount pv2Expected = pv1.multipliedBy(df1 / df2);
      pv2Computed[loopPayRec] = PRICER_SWAP_2DSC
          .presentValue(swapResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, MULTICURVE_ESTR);
      assertThat(pv2Computed[loopPayRec].size()).isEqualTo(1);
      assertThat(pv2Computed[loopPayRec].contains(CCY)).isTrue();
      assertThat(pv2Computed[loopPayRec].getAmount(CCY).getAmount())
          .isEqualTo(pv2Expected.getAmount(CCY).getAmount(), TOLERANCE_PV);
    }
    assertThat(pv2Computed[0].getAmount(CCY).getAmount())
        .isEqualTo(-pv2Computed[1].getAmount(CCY).getAmount(), TOLERANCE_PV);
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
      SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
          .createTrade(expiryDate, tenor,
              (loopPayRec == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, parRate + moneyness,
              REF_DATA);
      ResolvedSwap swapResolved = swap.resolve(REF_DATA).getProduct();
      MultiCurrencyAmount pv = PRICER_SWAP_2DSC
          .presentValue(swapResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, MULTICURVE_ESTR);
      Triple<MultiCurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder> pvPv01 = PRICER_SWAP_2DSC
          .presentValueSensitivityRatesStickyModel(
              swapResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, MULTICURVE_ESTR);
      CurrencyAmount pvFromSensi = pvPv01.getFirst().getAmount(CCY);
      assertThat(pv.getAmount(CCY).getCurrency()).isEqualTo(pvFromSensi.getCurrency());
      assertThat(pvFromSensi.getAmount()).isEqualTo(pv.getAmount(CCY).getAmount(), TOLERANCE_PV);
      PointSensitivities sensitivitiesRateBefore = pvPv01.getSecond().build();
      PointSensitivities sensitivitiesRateAfter = pvPv01.getThird().build();
      CurrencyParameterSensitivities sensiEoniaComputed =
          MULTICURVE_EONIA.parameterSensitivity(sensitivitiesRateBefore);
      CurrencyParameterSensitivities sensiEstrComputed =
          MULTICURVE_ESTR.parameterSensitivity(sensitivitiesRateAfter);
      CurrencyParameterSensitivities sensiComputed = sensiEoniaComputed.combinedWith(sensiEstrComputed);
      CurrencyParameterSensitivities sensiEstrFd =
          FD_CALC.sensitivity(MULTICURVE_ESTR, m -> PRICER_SWAP_2DSC
              .presentValue(swapResolved, DISCOUNTING_BEFORE_SWITCH, switchDate, m).getAmount(CCY));
      CurrencyParameterSensitivities sensiEoniaFd =
          FD_CALC.sensitivity(MULTICURVE_EONIA, m -> PRICER_SWAP_2DSC
              .presentValue(swapResolved, m.discountFactors(CCY), switchDate, MULTICURVE_ESTR).getAmount(CCY));
      CurrencyParameterSensitivities sensiFd = sensiEoniaFd.combinedWith(sensiEstrFd);
      assertThat(sensiComputed.equalWithTolerance(sensiFd, TOLERANCE_PV01)).isTrue();
    }
  }

}
