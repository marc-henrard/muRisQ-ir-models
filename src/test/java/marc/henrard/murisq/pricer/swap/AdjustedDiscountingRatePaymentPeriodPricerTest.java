/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.function.Function;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.impl.swap.DiscountingRatePaymentPeriodPricer;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;

import marc.henrard.murisq.dataset.MulticurveStandardEurDataSet;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;
import marc.henrard.murisq.pricer.swap.AdjustedDiscountingRatePaymentPeriodPricer;

/**
 * Tests {@link AdjustedDiscountingRatePaymentPeriodPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class AdjustedDiscountingRatePaymentPeriodPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 9, 6);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");

  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardEurDataSet.multicurve(VALUATION_DATE, REF_DATA);
  public static final ImmutableRatesProvider MULTICURVE_TS =
      MulticurveStandardEurDataSet.multicurveWithFixing(VALUATION_DATE, REF_DATA);
  
  private static final double MEAN_REVERSION = 0.03;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.0065);
  private static final DoubleArray VOLATILITY_0 = DoubleArray.of(0.0d);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS_0 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY_0, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAMETERS, DayCounts.ACT_365F, 
          VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER_0 =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAMETERS_0, DayCounts.ACT_365F, 
          VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  private static final AdjustedDiscountingRatePaymentPeriodPricer PRICER_PERIOD_ADJ =
      new AdjustedDiscountingRatePaymentPeriodPricer(HW_PROVIDER);
  private static final AdjustedDiscountingRatePaymentPeriodPricer PRICER_PERIOD_ADJ_0 =
      new AdjustedDiscountingRatePaymentPeriodPricer(HW_PROVIDER_0);
  public static final DiscountingRatePaymentPeriodPricer PRICER_PERIOD_DISCOUNTING = 
      DiscountingRatePaymentPeriodPricer.DEFAULT;
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  
  private static final RatesFiniteDifferenceSensitivityCalculator FD =
      RatesFiniteDifferenceSensitivityCalculator.DEFAULT;
  
  /* Period */
  private static final double NOTIONAL = 100_000_000.0d;
  private static final LocalDate START_DATE = LocalDate.of(2028, 2, 18);
  private static final LocalDate END_DATE = LocalDate.of(2028, 2, 21);
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2028, 8, 18);
  private static final OvernightIndex INDEX = OvernightIndices.EUR_EONIA;
  private static final OvernightCompoundedRateComputation ON_COMPUTATION = OvernightCompoundedRateComputation
      .of(INDEX, START_DATE, END_DATE, REF_DATA);
  private static final RateAccrualPeriod ACCRUAL = RateAccrualPeriod.builder()
      .startDate(START_DATE)
      .endDate(END_DATE)
      .yearFraction(INDEX.getDayCount().relativeYearFraction(START_DATE, PAYMENT_DATE))
      .rateComputation(ON_COMPUTATION).build();
  private static final double SPREAD = 0.0010;
  private static final RateAccrualPeriod ACCRUAL_SPREAD = RateAccrualPeriod.builder()
      .startDate(START_DATE)
      .endDate(END_DATE)
      .yearFraction(INDEX.getDayCount().relativeYearFraction(START_DATE, PAYMENT_DATE))
      .rateComputation(ON_COMPUTATION)
      .spread(SPREAD).build();
  private static final RatePaymentPeriod PERIOD = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE)
      .accrualPeriods(ACCRUAL)
      .dayCount(INDEX.getDayCount())
      .notional(NOTIONAL)
      .currency(INDEX.getCurrency()).build();
  private static final RatePaymentPeriod PERIOD_SPREAD = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE)
      .accrualPeriods(ACCRUAL_SPREAD)
      .dayCount(INDEX.getDayCount())
      .notional(NOTIONAL)
      .currency(INDEX.getCurrency()).build();
  
  private static final LocalDate START_DATE_ON_FIXED = LocalDate.of(2017, 8, 18);
  private static final LocalDate END_DATE_ON_FIXED = LocalDate.of(2017, 8, 21);
  private static final LocalDate START_DATE_ACCRUAL_FIXED = LocalDate.of(2017, 8, 21);
  private static final LocalDate PAYMENT_DATE_FIXED = LocalDate.of(2017, 11, 22);
  private static final OvernightCompoundedRateComputation ON_COMPUTATION_FIXED = OvernightCompoundedRateComputation
      .of(INDEX, START_DATE_ON_FIXED, END_DATE_ON_FIXED, REF_DATA);
  private static final RateAccrualPeriod ACCRUAL_FIXED = RateAccrualPeriod.builder()
      .startDate(START_DATE_ACCRUAL_FIXED)
      .endDate(PAYMENT_DATE_FIXED)
      .yearFraction(INDEX.getDayCount().relativeYearFraction(START_DATE_ACCRUAL_FIXED, PAYMENT_DATE_FIXED))
      .rateComputation(ON_COMPUTATION_FIXED).build();
  private static final RatePaymentPeriod PERIOD_FIXED = RatePaymentPeriod.builder()
      .paymentDate(PAYMENT_DATE_FIXED)
      .accrualPeriods(ACCRUAL_FIXED)
      .dayCount(INDEX.getDayCount())
      .notional(NOTIONAL)
      .currency(INDEX.getCurrency()).build();
  
  /* Test */
  private static final double TOLERANCE_PV_0 = 1.0E-6;
  private static final double TOLERANCE_DELTA = 1.0E+4; // 1 EUR/bps

  /* The forecast value v a local implementation */
  public void forecast_value() {
    double forecastComputed = PRICER_PERIOD_ADJ.forecastValue(PERIOD, MULTICURVE);
    double onFwd = MULTICURVE.overnightIndexRates(INDEX)
        .periodRate(OvernightIndexObservation.of(INDEX, START_DATE, REF_DATA), END_DATE);
    double v = HW_PROVIDER.relativeTime(PAYMENT_DATE);
    double t0 = HW_PROVIDER.relativeTime(START_DATE);
    double p0t0 = MULTICURVE.discountFactor(EUR, START_DATE);
    double t1 = HW_PROVIDER.relativeTime(END_DATE);
    double p0t1 = MULTICURVE.discountFactor(EUR, END_DATE);
    double expGamma = FORMULAS.timingAdjustmentFactor(HW_PARAMETERS, t0, t1, v);
    double delta = INDEX.getDayCount().relativeYearFraction(START_DATE, END_DATE);
    double fwdAdjusted = onFwd + p0t0 / p0t1 * (expGamma - 1.0d) / delta;
    double forecastValue = fwdAdjusted * ACCRUAL.getYearFraction() * NOTIONAL;
    assertEquals(forecastComputed, forecastValue, TOLERANCE_PV_0);
  }

  /* The forecast value v a local implementation. Value fixed but not paid yet. */
  public void forecast_value_fixed() {
    double forecastComputed = PRICER_PERIOD_ADJ.forecastValue(PERIOD_FIXED, MULTICURVE_TS);
    double onFwd = MULTICURVE_TS.overnightIndexRates(INDEX)
        .rate(OvernightIndexObservation.of(INDEX, START_DATE_ON_FIXED, REF_DATA));
    double forecastValue = onFwd * ACCRUAL_FIXED.getYearFraction() * NOTIONAL;
    assertEquals(forecastComputed, forecastValue, TOLERANCE_PV_0);
  }

  /* The forecast value v a local implementation */
  public void forecast_value_spread() {
    double forecastComputed = PRICER_PERIOD_ADJ.forecastValue(PERIOD_SPREAD, MULTICURVE);
    double onFwd = MULTICURVE.overnightIndexRates(INDEX)
        .periodRate(OvernightIndexObservation.of(INDEX, START_DATE, REF_DATA), END_DATE);
    double v = HW_PROVIDER.relativeTime(PAYMENT_DATE);
    double t0 = HW_PROVIDER.relativeTime(START_DATE);
    double p0t0 = MULTICURVE.discountFactor(EUR, START_DATE);
    double t1 = HW_PROVIDER.relativeTime(END_DATE);
    double p0t1 = MULTICURVE.discountFactor(EUR, END_DATE);
    double expGamma = FORMULAS.timingAdjustmentFactor(HW_PARAMETERS, t0, t1, v);
    double delta = INDEX.getDayCount().relativeYearFraction(START_DATE, END_DATE);
    double fwdAdjusted = onFwd + p0t0 / p0t1 * (expGamma - 1.0d) / delta;
    double forecastValue = (fwdAdjusted + SPREAD) * ACCRUAL.getYearFraction() * NOTIONAL;
    assertEquals(forecastComputed, forecastValue, TOLERANCE_PV_0);
  }

  /* The present value v a forecast value */
  public void present_value() {
    double forecastComputed = PRICER_PERIOD_ADJ.forecastValue(PERIOD, MULTICURVE);
    double pvComputed = PRICER_PERIOD_ADJ.presentValue(PERIOD, MULTICURVE);
    double pvExpected = MULTICURVE.discountFactor(EUR, PAYMENT_DATE) * forecastComputed;
    assertEquals(pvComputed, pvExpected, TOLERANCE_PV_0);
  }

  /* PV adjusted with 0 volatility v PV discounting */
  public void pv_0_vol() {
    double pvComputed = PRICER_PERIOD_ADJ_0.presentValue(PERIOD, MULTICURVE);
    double pvDiscounting = PRICER_PERIOD_DISCOUNTING.presentValue(PERIOD, MULTICURVE);
    assertEquals(pvComputed, pvDiscounting, TOLERANCE_PV_0);
  }

  /* PV sensitivity by finite difference */
  public void pv_sensitivity() {
    PointSensitivityBuilder ptsComputed = PRICER_PERIOD_ADJ.presentValueSensitivity(PERIOD, MULTICURVE);
    CurrencyParameterSensitivities ps = MULTICURVE.parameterSensitivity(ptsComputed.build());
    Function<ImmutableRatesProvider, CurrencyAmount> valueFn =
        (p) -> CurrencyAmount.of(EUR, PRICER_PERIOD_ADJ.presentValue(PERIOD, p));
    CurrencyParameterSensitivities psFd = FD.sensitivity(MULTICURVE, valueFn);
    assertTrue(ps.equalWithTolerance(psFd, TOLERANCE_DELTA));
  }

  /* forecast sensitivity by finite difference */
  public void forecast_value_sensitivity() {
    PointSensitivityBuilder ptsComputed = PRICER_PERIOD_ADJ.forecastValueSensitivity(PERIOD, MULTICURVE);
    CurrencyParameterSensitivities ps = MULTICURVE.parameterSensitivity(ptsComputed.build());
    Function<ImmutableRatesProvider, CurrencyAmount> valueFn =
        (p) -> CurrencyAmount.of(EUR, PRICER_PERIOD_ADJ.forecastValue(PERIOD, p));
    CurrencyParameterSensitivities psFd = FD.sensitivity(MULTICURVE, valueFn);
    assertTrue(ps.equalWithTolerance(psFd, TOLERANCE_DELTA));
  }

  /* PVBP same as for discounting */
  public void pvbp() {
    double pvbpComputed = PRICER_PERIOD_ADJ_0.pvbp(PERIOD, MULTICURVE);
    double pvbpDiscounting = PRICER_PERIOD_DISCOUNTING.pvbp(PERIOD, MULTICURVE);
    assertEquals(pvbpComputed, pvbpDiscounting, TOLERANCE_PV_0);
  }
  
}
