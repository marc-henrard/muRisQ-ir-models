/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.calibration;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.ResolvedSwaptionTrade;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.dataset.MulticurveStandardEurDataSet;
import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantModelParameters;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantTemplate;
import marc.henrard.murisq.pricer.swaption.HullWhiteSwaptionPhysicalProductPricer;
import marc.henrard.murisq.pricer.swaption.SingleCurrencyModelSwaptionPhysicalTradePricer;

/**
 * Tests {@link SingleCurrencyModelCapFloorRootPriceCalibrator} applied to 
 * {@link HullWhiteSwaptionPhysicalProductPricer}.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelSwaptionPhysicalRootPriceCalibratorHullWhiteTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 9, 6);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");

  /* Curve and model data */
  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardEurDataSet.multicurve(VALUATION_DATE, REF_DATA);
  
  /* Pricers */
  private static final DiscountingPaymentPricer PRICER_PAYMENT = DiscountingPaymentPricer.DEFAULT;
  private static final HullWhiteSwaptionPhysicalProductPricer PRICER_HW_SWPT_PRODUCT =
      HullWhiteSwaptionPhysicalProductPricer.DEFAULT;
  private static final SingleCurrencyModelSwaptionPhysicalTradePricer PRICER_HW_SWPT_TRADE =
      new SingleCurrencyModelSwaptionPhysicalTradePricer(PRICER_HW_SWPT_PRODUCT, PRICER_PAYMENT);

  /* Cap/floor details */
  private static final Period[] EXPIRIES_PERIODS = new Period[] {
      Period.ofYears(2), Period.ofYears(5), Period.ofYears(10)};
  private static final Tenor TENOR = Tenor.TENOR_10Y;
  private static final int NB_EXPIRIES = EXPIRIES_PERIODS.length;
  private static final double STRIKE = 0.0050;
  private static final double NOTIONAL = 1_000_000.0d;

  /* Model starting values */
  private static final TimeMeasurement TIME_MEASURE = ScaledSecondTime.DEFAULT;
  private static final double MEAN_REVERSION = 0.05;
  private static final int NB_MAIN_PARAMETERS = 1;
  
  private static final DoubleArray VOL_START_3 = DoubleArray.of(0.01, 0.012, 0.011);
  private static final DoubleArray VOL_TIME_3 = DoubleArray.of(2.0, 5.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters PARAMETERS_STRATA_3 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOL_START_3, VOL_TIME_3);
  private static final HullWhiteOneFactorPiecewiseConstantModelParameters HW_PARAMETERS_3 =
      HullWhiteOneFactorPiecewiseConstantModelParameters
      .of(EUR, PARAMETERS_STRATA_3, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE, TIME_MEASURE);
  
  private static final DoubleArray VOL_START_1 = DoubleArray.of(0.01);
  private static final DoubleArray VOL_TIME_1 = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters PARAMETERS_STRATA_1 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOL_START_1, VOL_TIME_1);
  private static final HullWhiteOneFactorPiecewiseConstantModelParameters HW_PARAMETERS_1 =
      HullWhiteOneFactorPiecewiseConstantModelParameters
      .of(EUR, PARAMETERS_STRATA_1, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE, TIME_MEASURE);

  /* Testing constants */
  private static final double TOL_PV = 1.0E-0;
  private static final boolean PRINT_RESULTS = false;

  /* Exact calibration by root-finding. Calibration of the time dependent parameters. Calibration to the prices. */
  @Test
  public void calibration_price_3vol() {
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    List<ResolvedSwaptionTrade> trades = new ArrayList<>();
    for (int i = 0; i < EXPIRIES_PERIODS.length; i++) {
      LocalDate expiry = spot.plus(EXPIRIES_PERIODS[i]);
      Swaption swaption = swaption(spot, expiry, STRIKE);
      ResolvedSwaption swaptionResolved = swaption.resolve(REF_DATA);
      CurrencyAmount pv = PRICER_HW_SWPT_PRODUCT.presentValue(swaptionResolved, MULTICURVE, HW_PARAMETERS_3);
      Payment premium = Payment.of(pv.multipliedBy(-1.0), VALUATION_DATE);
      ResolvedSwaptionTrade trade = ResolvedSwaptionTrade.of(TradeInfo.empty(), swaptionResolved, premium);
      trades.add(trade);
    }
    BitSet fixed = new BitSet(NB_MAIN_PARAMETERS + NB_EXPIRIES);
    for (int i = 0; i < NB_MAIN_PARAMETERS; i++) {
      fixed.set(i);
    }
    HullWhiteOneFactorPiecewiseConstantTemplate template =
        HullWhiteOneFactorPiecewiseConstantTemplate
            .of(EUR, TIME_MEASURE, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE, VOL_TIME_3,
                DoubleArray.of(MEAN_REVERSION, 0.0075, 0.0075, 0.0075), fixed);
    SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator calibrator =
        SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator.of(template);
    SingleCurrencyModelParameters calibrated =
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_HW_SWPT_TRADE);
    if (PRINT_RESULTS) {
      System.out.println(calibrated);
    }
    for (int i = 0; i < EXPIRIES_PERIODS.length; i++) {
      assertEquals(PRICER_HW_SWPT_TRADE.presentValue(trades.get(i), MULTICURVE, calibrated).getAmount(),
          0, TOL_PV);
    }
  }

  /* Calibration of one volatility on one swaption. */
  @Test
  public void calibration_price_1vol() {
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    List<ResolvedSwaptionTrade> trades = new ArrayList<>();
    LocalDate expiry = spot.plus(EXPIRIES_PERIODS[1]); // 5Y
    Swaption swaption = swaption(spot, expiry, STRIKE);
    ResolvedSwaption swaptionResolved = swaption.resolve(REF_DATA);
    CurrencyAmount pv = PRICER_HW_SWPT_PRODUCT.presentValue(swaptionResolved, MULTICURVE, HW_PARAMETERS_1);
    Payment premium = Payment.of(pv.multipliedBy(-1.0), VALUATION_DATE);
    ResolvedSwaptionTrade trade = ResolvedSwaptionTrade.of(TradeInfo.empty(), swaptionResolved, premium);
    trades.add(trade);
    BitSet fixed = new BitSet(NB_MAIN_PARAMETERS + 1);
    for (int i = 0; i < NB_MAIN_PARAMETERS; i++) {
      fixed.set(i);
    }
    HullWhiteOneFactorPiecewiseConstantTemplate template =
        HullWhiteOneFactorPiecewiseConstantTemplate
            .of(EUR, TIME_MEASURE, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE, VOL_TIME_1,
                DoubleArray.of(MEAN_REVERSION, 0.0125), fixed);
    SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator calibrator =
        SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator.of(template);
    SingleCurrencyModelParameters calibrated =
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_HW_SWPT_TRADE);
    if (PRINT_RESULTS) {
      System.out.println(calibrated);
    }
    assertEquals(PRICER_HW_SWPT_TRADE.presentValue(trades.get(0), MULTICURVE, calibrated).getAmount(), 0.0d, TOL_PV);
  }

  /* Calibration of mean reversion and volatility on two swaptions. */
  @Test
  public void calibration_price_mr_1vol() {
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    List<ResolvedSwaptionTrade> trades = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      LocalDate expiry = spot.plus(EXPIRIES_PERIODS[i]);
      Swaption swaption = swaption(spot, expiry, STRIKE);
      ResolvedSwaption swaptionResolved = swaption.resolve(REF_DATA);
      CurrencyAmount pv = PRICER_HW_SWPT_PRODUCT.presentValue(swaptionResolved, MULTICURVE, HW_PARAMETERS_1);
      Payment premium = Payment.of(pv.multipliedBy(-1.0), VALUATION_DATE);
      ResolvedSwaptionTrade trade = ResolvedSwaptionTrade.of(TradeInfo.empty(), swaptionResolved, premium);
      trades.add(trade);
    }
    BitSet fixed = new BitSet(NB_MAIN_PARAMETERS + 1); // None fixed
    HullWhiteOneFactorPiecewiseConstantTemplate template =
        HullWhiteOneFactorPiecewiseConstantTemplate
        .of(EUR, TIME_MEASURE, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE, VOL_TIME_1, 
            DoubleArray.of(0.04, 0.0080), fixed);
    SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator calibrator =
        SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator.of(template);
    SingleCurrencyModelParameters calibrated = 
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_HW_SWPT_TRADE);
    if (PRINT_RESULTS) {
      System.out.println(calibrated);
    }
    for (int i = 0; i < 2; i++) {
      assertEquals(PRICER_HW_SWPT_TRADE.presentValue(trades.get(i), MULTICURVE, calibrated).getAmount(), 0.0d, TOL_PV);
    }
  }

  private static Swaption swaption(LocalDate spot, LocalDate expiry, double strike) {
    Swap underlying = FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(expiry, TENOR, BuySell.BUY, NOTIONAL, strike, REF_DATA)
        .getProduct();
    Swaption swaption = Swaption.builder()
        .underlying(underlying)
        .expiryDate(AdjustableDate.of(expiry))
        .expiryTime(VALUATION_TIME)
        .expiryZone(VALUATION_ZONE)
        .longShort(LongShort.LONG)
        .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
        .build();
    return swaption;
  }
}
