/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.capfloor;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.pricer.swap.SwapPaymentPeriodPricer;
import com.opengamma.strata.pricer.swaption.HullWhiteSwaptionPhysicalProductPricer;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod.Builder;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.IborRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableFixedIborSwapConvention;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;

/**
 * Tests of {@link HullWhiteCapletFloorletPeriodPricer}.
 * 
 * @author Marc Henrard
 */
public class HullWhiteCapletFloorletPeriodPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.findValue(EUTA).get();
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");

  // Hull-White model parameters
  private static final double MEAN_REVERSION = 0.01;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.01, 0.011, 0.012, 0.013, 0.014);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(0.5, 1.0, 2.0, 5.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
          MODEL_PARAMETERS, ACT_365F, VALUATION_DATE.atTime(VALUATION_TIME).atZone(VALUATION_ZONE));

  private static final HullWhiteCapletFloorletPeriodPricer PRICER_CAP_HW =
      HullWhiteCapletFloorletPeriodPricer.DEFAULT;
  private static final HullWhiteSwaptionPhysicalProductPricer PRICER_SWPT_HW =
      HullWhiteSwaptionPhysicalProductPricer.DEFAULT;
  
  /* Descriptions of caplets/floorlets */
  private static final Period[] EXPIRIES_PER = new Period[] {
      Period.ofMonths(3), Period.ofYears(2), Period.ofYears(10)};
  private static final int NB_EXPIRIES = EXPIRIES_PER.length;
  private static final double[] STRIKES = new double[] {-0.0025, 0.0100, 0.0200};
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;

  /* Load and calibrate curves */
  private static final ImmutableRatesProvider MULTICURVE = MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;
  private static final RatesFiniteDifferenceSensitivityCalculator FDC = 
      new RatesFiniteDifferenceSensitivityCalculator(1.0E-6);
  
  /* Constants */
  private static final Offset<Double> TOLERANCE_PV_PARITY = offset(1.0E-2);
  private static final Double TOLERANCE_SENSI_RATE = 1.0E-2;
  private static final Double TOLERANCE_SENSI_RATE_FD = 1.0E+4;
  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

  /* Test payer/receiver parity. */
  @Test
  public void present_value_payer_receiver_parity() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true);
        IborCapletFloorletPeriod floorletShort = capletFloorlet(-NOTIONAL, comp, STRIKES[k], false);
        double pvUnderlying = MULTICURVE.discountFactor(EUR, comp.getMaturityDate()) 
             * (MULTICURVE.iborIndexRates(EUR_EURIBOR_6M).rate(comp.getObservation()) - STRIKES[k])
             * capletLong.getYearFraction() * NOTIONAL;
        double pvSemiExplPayLong =
            PRICER_CAP_HW.presentValue(capletLong, MULTICURVE, HW_PROVIDER).getAmount();
        double pvSemiExplRecShort =
            PRICER_CAP_HW.presentValue(floorletShort, MULTICURVE, HW_PROVIDER).getAmount();
        assertThat(pvSemiExplPayLong + pvSemiExplRecShort)
            .isCloseTo(pvUnderlying, TOLERANCE_PV_PARITY);
      }
    }
  }

  /* Test pv and currency exposure of caplet v one period swaption. */
  @Test
  public void present_value_caplet_swaption() {
    FixedRateSwapLegConvention fixedLegConvention =
        FixedRateSwapLegConvention.of(EUR, DayCounts.ACT_360, Frequency.P6M,
            BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA));
    FixedIborSwapConvention swapConvention = ImmutableFixedIborSwapConvention.of("TEST",
        fixedLegConvention, IborRateSwapLegConvention.of(EUR_EURIBOR_6M));
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true);
        SwapTrade swapPayer = swapConvention.createTrade(
            fixingDate, Tenor.TENOR_6M, BuySell.BUY, NOTIONAL, STRIKES[k], REF_DATA);
        Swaption swptPayerLong = Swaption.builder()
            .longShort(LongShort.LONG)
            .expiryDate(AdjustableDate.of(fixingDate)).expiryTime(LocalTime.of(11, 00))
            .expiryZone(ZoneId.of("Europe/Brussels"))
            .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
            .underlying(swapPayer.getProduct()).build();
        CurrencyAmount pvCaplet = PRICER_CAP_HW.presentValue(capletLong, MULTICURVE, HW_PROVIDER);
        double pvSwpt =
            PRICER_SWPT_HW.presentValue(swptPayerLong.resolve(REF_DATA), MULTICURVE, HW_PROVIDER).getAmount();
        MultiCurrencyAmount ceCaplet = PRICER_CAP_HW.currencyExposure(capletLong, MULTICURVE, HW_PROVIDER);
//        System.out.println("Caplet v Swaption: " + EXPIRIES_PER[i] + STRIKES[k]);
        assertThat(pvCaplet.getAmount()).isCloseTo(pvSwpt, TOLERANCE_PV_PARITY);
        assertThat(pvCaplet.getCurrency()).isEqualTo(EUR);
        assertThat(ceCaplet.getCurrencies().size()).isEqualTo(1);
        assertThat(pvCaplet.getAmount()).isCloseTo(ceCaplet.getAmount(EUR).getAmount(), TOLERANCE_PV_PARITY);
      }
    }
  }

  /* Test pv of caplet with payment delay. */
  // Note: cap/floor parity is to a delayed payment swap (i.e. with convexity adjustment). Not checked here
  @Test
  public void present_value_caplet_delay() {
    HolidayCalendar cal = REF_DATA.getValue(EUR_EURIBOR_6M.getFixingCalendar());
    IborIndexRates iborRates = MULTICURVE.iborIndexRates(EUR_EURIBOR_6M);
    DiscountFactors discountFactors = MULTICURVE.discountFactors(EUR);
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborIndexObservation obs = comp.getObservation();
        double delta = obs.getYearFraction();
        LocalDate paymentDate = cal.nextOrSame(comp.getMaturityDate().plusMonths(1));
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true, paymentDate);
        double pvCaplet = PRICER_CAP_HW.presentValue(capletLong, MULTICURVE, HW_PROVIDER).getAmount();
        double investmentFactorIbor = 1.0 + delta * iborRates.rate(obs);
        double onePlusDeltaK = 1.0 + delta * STRIKES[k];
        LocalDate[] paymentDates = new LocalDate[2];
        paymentDates[0] = obs.getEffectiveDate();
        paymentDates[1] = paymentDate;
        double[] alpha = new double[2];
        for (int loopcf = 0; loopcf < 2; loopcf++) {
          alpha[loopcf] = HW_PROVIDER
              .alpha(VALUATION_DATE, fixingDate, paymentDates[loopcf], obs.getMaturityDate());
        }
        double discountFactorPayment = discountFactors.discountFactor(paymentDate);
        double kappa = ( Math.log(investmentFactorIbor / onePlusDeltaK) - 0.5 * alpha[0] * alpha[0]) / alpha[0];
        double pvExpected = investmentFactorIbor * NORMAL.getCDF(kappa + alpha[0] + alpha[1]) * Math.exp(alpha[0] * alpha[1]) 
              - onePlusDeltaK * NORMAL.getCDF(kappa + alpha[1]);
        pvExpected *= NOTIONAL * discountFactorPayment;
        assertThat(pvCaplet).isCloseTo(pvExpected, TOLERANCE_PV_PARITY);
      }
    }
  }
  
  /* Test sensitivity by FD and payer/receiver parity. */
  @Test
  public void present_value_sensitivity() {
    SwapPaymentPeriodPricer<SwapPaymentPeriod> paymentPeriodPricer = SwapPaymentPeriodPricer.standard();
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true);
        IborCapletFloorletPeriod floorletShort = capletFloorlet(-NOTIONAL, comp, STRIKES[k], false);
        RateAccrualPeriod iborPeriod = RateAccrualPeriod.builder()
            .startDate(comp.getEffectiveDate())
            .endDate(comp.getMaturityDate())
            .yearFraction(comp.getYearFraction())
            .rateComputation(comp).spread(-STRIKES[k]).build();
        RatePaymentPeriod paymentPeriod = RatePaymentPeriod.builder()
            .accrualPeriods(iborPeriod)
            .currency(EUR)
            .dayCount(EUR_EURIBOR_6M.getDayCount())
            .notional(NOTIONAL)
            .paymentDate(comp.getMaturityDate())
            .build();
        double pvUnderlying = paymentPeriodPricer.presentValue(paymentPeriod, MULTICURVE);
        double pvPayLong =
            PRICER_CAP_HW.presentValue(capletLong, MULTICURVE, HW_PROVIDER).getAmount();
        double pvRecShort =
            PRICER_CAP_HW.presentValue(floorletShort, MULTICURVE, HW_PROVIDER).getAmount();
        assertThat(pvPayLong + pvRecShort)
            .isCloseTo(pvUnderlying, TOLERANCE_PV_PARITY);
        PointSensitivities ptsUnderlying = paymentPeriodPricer.presentValueSensitivity(paymentPeriod, MULTICURVE).build();
        CurrencyParameterSensitivities psUnderlying = MULTICURVE.parameterSensitivity(ptsUnderlying);
        PointSensitivities ptsPayLong = PRICER_CAP_HW.presentValueSensitivityRates(capletLong, MULTICURVE, HW_PROVIDER).build();
        CurrencyParameterSensitivities psPayLong = MULTICURVE.parameterSensitivity(ptsPayLong);
        PointSensitivities ptsRecShort = PRICER_CAP_HW.presentValueSensitivityRates(floorletShort, MULTICURVE, HW_PROVIDER).build();
        CurrencyParameterSensitivities psRecShort = MULTICURVE.parameterSensitivity(ptsRecShort);
        assertThat(psUnderlying.equalWithTolerance(psPayLong.combinedWith(psRecShort), TOLERANCE_SENSI_RATE)).isTrue();
        CurrencyParameterSensitivities psPayLongFd = 
            FDC.sensitivity(MULTICURVE, r -> PRICER_CAP_HW.presentValue(capletLong, r, HW_PROVIDER));
        assertThat(psPayLongFd.equalWithTolerance(psPayLong, TOLERANCE_SENSI_RATE_FD)).isTrue();
      }
    }
  }
  
  private IborCapletFloorletPeriod capletFloorlet(
      double notional, IborRateComputation comp, double strike, boolean isCap) {
    Builder builder = IborCapletFloorletPeriod.builder()
        .currency(EUR)
        .notional(notional)
        .startDate(comp.getEffectiveDate())
        .endDate(comp.getMaturityDate())
        .paymentDate(comp.getMaturityDate())
        .yearFraction(comp.getYearFraction())
        .iborRate(comp);
    if(isCap) {
      builder.caplet(strike);
    } else {
      builder.floorlet(strike);
    }
    return builder.build();
  }
  
  private IborCapletFloorletPeriod capletFloorlet(
      double notional, IborRateComputation comp, double strike, boolean isCap, LocalDate paymentDate) {
    Builder builder = IborCapletFloorletPeriod.builder()
        .currency(EUR)
        .notional(notional)
        .startDate(comp.getEffectiveDate())
        .endDate(comp.getMaturityDate())
        .paymentDate(paymentDate)
        .yearFraction(comp.getYearFraction())
        .iborRate(comp);
    if(isCap) {
      builder.caplet(strike);
    } else {
      builder.floorlet(strike);
    }
    return builder.build();
  }
  
}
