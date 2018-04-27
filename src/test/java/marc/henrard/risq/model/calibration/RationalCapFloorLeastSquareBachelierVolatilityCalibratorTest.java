/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.calibration;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.AdjustablePayment;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.capfloor.IborCapFloor;
import com.opengamma.strata.product.capfloor.IborCapFloorLeg;
import com.opengamma.strata.product.capfloor.IborCapFloorTrade;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorTrade;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.IborRateCalculation;

import marc.henrard.risq.model.dataset.MulticurveStandardEurDataSet;
import marc.henrard.risq.model.dataset.RationalParametersDataSet;
import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorHWShapePlusCstParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorHWShapePlusCstTemplate;
import marc.henrard.risq.pricer.capfloor.RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer;
import marc.henrard.risq.pricer.capfloor.SingleCurrencyModelCapFloorLegPricer;
import marc.henrard.risq.pricer.capfloor.SingleCurrencyModelCapFloorProductPricer;
import marc.henrard.risq.pricer.capfloor.SingleCurrencyModelCapFloorTradePricer;

/**
 * Tests {@link RationalCapFloorLeastSquareBachelierVolatilityCalibrator}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalCapFloorLeastSquareBachelierVolatilityCalibratorTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 9, 6);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");
  private static final BusinessDayAdjustment BUSINESS_ADJ = BusinessDayAdjustment.of(
      BusinessDayConventions.MODIFIED_FOLLOWING, EUTA);

  /* Curve and model data */
  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardEurDataSet.multicurve(VALUATION_DATE, REF_DATA);
  private static final RationalTwoFactorHWShapePlusCstParameters RATIONAL_2F = RationalParametersDataSet
      .twoFactorHWShaped(VALUATION_TIME, VALUATION_ZONE, MULTICURVE.discountFactors(EUR));
  
  /* Pricers */
  private static final DiscountingSwapLegPricer PRICER_SWAP_LEG = DiscountingSwapLegPricer.DEFAULT;
  private static final DiscountingPaymentPricer PRICER_PAYMENT = DiscountingPaymentPricer.DEFAULT;
  private static final RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer PRICER_CAPLET_S_EX =
      RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer.DEFAULT;
  private static final SingleCurrencyModelCapFloorLegPricer PRICER_LEG_S_EX =
      new SingleCurrencyModelCapFloorLegPricer(PRICER_CAPLET_S_EX);
  private static final SingleCurrencyModelCapFloorProductPricer PRICER_PRODUCT =
      new SingleCurrencyModelCapFloorProductPricer(PRICER_LEG_S_EX, PRICER_SWAP_LEG);
  private static final SingleCurrencyModelCapFloorTradePricer PRICER_TRADE =
      new SingleCurrencyModelCapFloorTradePricer(PRICER_PRODUCT, PRICER_PAYMENT);
  
  /* Descriptions of cap/floor */
  private static final Period[] MATURITIES_PER = new Period[] {
      Period.ofYears(1), Period.ofYears(2), Period.ofYears(3), Period.ofYears(4), Period.ofYears(5),
      Period.ofYears(6), Period.ofYears(7), Period.ofYears(8), Period.ofYears(9), Period.ofYears(10)};
  private static final int NB_MATURITIES = MATURITIES_PER.length;
  private static final double[] STRIKES = new double[] 
      {-0.0025, 0.0000, 0.0050, 0.0100, 0.0150, 0.0200};
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;
  

  private static final double TOL_LS = 1.0E-6;

  /* Calibration at best of parameters b_0(0) and eta with a smile. Recover a rational model smile. 
   * Method with explicit constraints: calibrateConstraints. */
  public void two_factor_smile_rat() {
    int maturityIndex = 4;
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    LocalDate maturity = spot.plus(MATURITIES_PER[maturityIndex]);
    List<ResolvedIborCapFloorTrade> trades = new ArrayList<>();
    for (int k = 0; k < NB_STRIKES; k++) {
      IborCapFloor cap = cap(spot, maturity, STRIKES[k]);
      MultiCurrencyAmount pvLeg = PRICER_PRODUCT.presentValue(cap.resolve(REF_DATA), MULTICURVE, RATIONAL_2F);
      AdjustablePayment premium = AdjustablePayment.of(pvLeg.getAmount(EUR).multipliedBy(-1.0), VALUATION_DATE);
      IborCapFloorTrade capTrade = IborCapFloorTrade.builder()
          .product(cap)
          .premium(premium)
          .info(TradeInfo.of(VALUATION_DATE)).build();
      trades.add(capTrade.resolve(REF_DATA));
    }
    BitSet fixed = new BitSet(8);
    fixed.set(0); // a1
    fixed.set(1); // a2
    fixed.set(2); // correlation
    fixed.set(5); // kappa
    fixed.set(6); // c1
    fixed.set(7); // c2
    RationalTwoFactorHWShapePlusCstTemplate template =
        RationalTwoFactorHWShapePlusCstTemplate
        .of(RATIONAL_2F.getTimeMeasure(), RATIONAL_2F.getDiscountFactors(),
            RATIONAL_2F.getValuationTime(), RATIONAL_2F.getValuationZone(), 
            DoubleArray.of(0.75, 0.50, 0.00, 0.45, 0.012, 0.03, 0.00, 0.0020), fixed);
    SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator calibrator = 
            SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator.of(template);
    SingleCurrencyModelParameters calibrated = 
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_TRADE);
    assertTrue(calibrated.getParameters().equalWithTolerance(RATIONAL_2F.getParameters(), TOL_LS));
  }

  /* Calibration at best of parameters a1 and kappa with a term structure. Recover a rational model smile.
   * Method with explicit constraints: calibrateConstraints. */
  public void two_factor_ts() {
    int strikeIndex = 3;
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    List<ResolvedIborCapFloorTrade> trades = new ArrayList<>();
    for (int i = 0; i < NB_MATURITIES; i++) {
      LocalDate maturity = spot.plus(MATURITIES_PER[i]);
      IborCapFloor cap = cap(spot, maturity, STRIKES[strikeIndex]);
      MultiCurrencyAmount pvLeg = PRICER_PRODUCT.presentValue(cap.resolve(REF_DATA), MULTICURVE, RATIONAL_2F);
      AdjustablePayment premium = AdjustablePayment.of(pvLeg.getAmount(EUR).multipliedBy(-1.0), VALUATION_DATE);
      IborCapFloorTrade capTrade = IborCapFloorTrade.builder()
          .product(cap)
          .premium(premium)
          .info(TradeInfo.of(VALUATION_DATE)).build();
      trades.add(capTrade.resolve(REF_DATA));
    }
    BitSet fixed = new BitSet(8);
//    fixed.set(0); // a1
    fixed.set(1); // a2
    fixed.set(2); // correlation
    fixed.set(3); // b00
    fixed.set(4); // eta
//    fixed.set(5); // kappa
    fixed.set(6); // c1
    fixed.set(7); // c2
    RationalTwoFactorHWShapePlusCstTemplate template =
        RationalTwoFactorHWShapePlusCstTemplate
        .of(RATIONAL_2F.getTimeMeasure(), RATIONAL_2F.getDiscountFactors(),
            RATIONAL_2F.getValuationTime(), RATIONAL_2F.getValuationZone(), 
            DoubleArray.of(0.80, 0.50, 0.00, 0.50, 0.01, 0.05, 0.00, 0.0020), fixed);
    SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator calibrator = 
        SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator.of(template);
    SingleCurrencyModelParameters calibrated = 
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_TRADE);
    assertTrue(calibrated.getParameters().equalWithTolerance(RATIONAL_2F.getParameters(), TOL_LS));
  }

  /* Calibration at best of parameters b_0(0) and c2 with a smile. Recover a arbitrary prices.
   * Method with explicit constraints: calibrateConstraints. */
  public void two_factor_smile_ext() {
    int maturityIndex = 4;
    double[] pvAddOn = new double[] 
        {-1000, -1000, -50, -500, -100, -1000};
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    LocalDate maturity = spot.plus(MATURITIES_PER[maturityIndex]);
    List<ResolvedIborCapFloorTrade> trades = new ArrayList<>();
    for (int k = 0; k < NB_STRIKES; k++) {
      IborCapFloor cap = cap(spot, maturity, STRIKES[k]);
      MultiCurrencyAmount pvLeg = PRICER_PRODUCT.presentValue(cap.resolve(REF_DATA), MULTICURVE, RATIONAL_2F);
      AdjustablePayment premium = 
          AdjustablePayment.of(pvLeg.getAmount(EUR).multipliedBy(-1.0).plus(pvAddOn[k]), VALUATION_DATE);
      IborCapFloorTrade capTrade = IborCapFloorTrade.builder()
          .product(cap)
          .premium(premium)
          .info(TradeInfo.of(VALUATION_DATE)).build();
      trades.add(capTrade.resolve(REF_DATA));
    }
    BitSet fixed = new BitSet(8);
    List<Integer> paramPerturbed = ImmutableList.of(3, 7);
    for (int i = 0; i < 8; i++) {
      if (!paramPerturbed.contains(i)) {
        fixed.set(i);
      }
    }
    RationalTwoFactorHWShapePlusCstTemplate template =
        RationalTwoFactorHWShapePlusCstTemplate
        .of(RATIONAL_2F.getTimeMeasure(), RATIONAL_2F.getDiscountFactors(),
            RATIONAL_2F.getValuationTime(), RATIONAL_2F.getValuationZone(), 
            DoubleArray.of(0.75, 0.50, 0.00, 0.50, 0.0099, 0.03, 0.00, 0.0020), fixed);
    SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator calibrator = 
        SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator.of(template);
    SingleCurrencyModelParameters calibrated = 
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_TRADE);
    checkLeastSquare(trades, calibrated, paramPerturbed);
  }

  /* Calibration at best of parameters eta and kappa with a smile. Recover a arbitrary prices.
   * Method with explicit constraints: calibrateConstraints. */
  public void two_factor_smile_ts_ext() {
    int[] maturityIndex = {4, 9};
    double[][] pvAddOn = new double[] []
        {{500, 100, -100, 100, 100, 500},
      {-3000, -5000, -9000, -9000, -5000, -3000}};
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    List<ResolvedIborCapFloorTrade> trades = new ArrayList<>();
    for (int i = 0; i < maturityIndex.length; i++) {
      LocalDate maturity = spot.plus(MATURITIES_PER[maturityIndex[i]]);
      for (int k = 0; k < NB_STRIKES; k++) {
        IborCapFloor cap = cap(spot, maturity, STRIKES[k]);
        MultiCurrencyAmount pvLeg = PRICER_PRODUCT.presentValue(cap.resolve(REF_DATA), MULTICURVE, RATIONAL_2F);
        AdjustablePayment premium = 
            AdjustablePayment.of(pvLeg.getAmount(EUR).multipliedBy(-1.0).plus(-pvAddOn[i][k]), VALUATION_DATE);
        IborCapFloorTrade capTrade = IborCapFloorTrade.builder()
            .product(cap)
            .premium(premium)
            .info(TradeInfo.of(VALUATION_DATE)).build();
        trades.add(capTrade.resolve(REF_DATA));
      }
    }
    BitSet fixed = new BitSet(8);
    List<Integer> paramPerturbed = ImmutableList.of(4, 5);
    for (int i = 0; i < 8; i++) {
      if (!paramPerturbed.contains(i)) {
        fixed.set(i);
      }
    }
    RationalTwoFactorHWShapePlusCstTemplate template =
        RationalTwoFactorHWShapePlusCstTemplate
        .of(RATIONAL_2F.getTimeMeasure(), RATIONAL_2F.getDiscountFactors(),
            RATIONAL_2F.getValuationTime(), RATIONAL_2F.getValuationZone(), 
            DoubleArray.of(0.75, 0.50, 0.00, 0.55, 0.012, 0.031, 0.00, 0.002), fixed);
    SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator calibrator = 
        SingleCurrencyModelCapFloorLeastSquareBachelierVolatilityCalibrator.of(template);
    SingleCurrencyModelParameters calibrated = 
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_TRADE);
    checkLeastSquare(trades, calibrated, paramPerturbed);
  }

  // Check squared error for small perturbations
  private static void checkLeastSquare(
      List<ResolvedIborCapFloorTrade> trades,
      SingleCurrencyModelParameters calibrated,
      List<Integer> paramPerturbed) {

    double shift = 1.0E-4;
    double[] ivMarket = new double[trades.size()];
    for (int looptrade = 0; looptrade < trades.size(); looptrade++) {
      double pvPremium = PRICER_PAYMENT.presentValue(trades.get(looptrade).getPremium().get(), MULTICURVE).getAmount();
      ivMarket[looptrade] = PRICER_LEG_S_EX.impliedVolatilityBachelier(
          trades.get(looptrade).getProduct().getCapFloorLeg(), MULTICURVE, -pvPremium,
          calibrated.getValuationDateTime());
    }
    double sCalibrated = 0.0;
    for (int looptrade = 0; looptrade < trades.size(); looptrade++) {
      double ivCheck = PRICER_LEG_S_EX.impliedVolatilityBachelier(
          trades.get(looptrade).getProduct().getCapFloorLeg(), MULTICURVE, calibrated);
      sCalibrated += (ivCheck - ivMarket[looptrade]) * (ivCheck - ivMarket[looptrade]);
    }
    for (int i = 0; i < paramPerturbed.size(); i++) {
      for (int loopside = 0; loopside < 2; loopside++) {
        RationalTwoFactorHWShapePlusCstParameters perturbed =
            (RationalTwoFactorHWShapePlusCstParameters) calibrated.withParameter(paramPerturbed.get(i),
                calibrated.getParameter(paramPerturbed.get(i)) + (2 * loopside - 1) * shift);
        double sPerturbed = 0.0;
        for (int looptrade = 0; looptrade < trades.size(); looptrade++) {
          double ivCheck = PRICER_LEG_S_EX.impliedVolatilityBachelier(
              trades.get(looptrade).getProduct().getCapFloorLeg(), MULTICURVE, perturbed);
          sPerturbed += (ivCheck - ivMarket[looptrade]) * (ivCheck - ivMarket[looptrade]);
        }
        assertTrue(sPerturbed > sCalibrated,
            "Check parameter " + i + " side " + loopside + ": " + sPerturbed + " < " + sCalibrated);
      }
    }
  }
  
  private static IborCapFloor cap(LocalDate spot, LocalDate maturity, double strike) {
    PeriodicSchedule paySchedule =
        PeriodicSchedule.of(spot, maturity, Frequency.P6M, BUSINESS_ADJ, StubConvention.NONE,
            RollConventions.NONE);
    IborCapFloorLeg leg = IborCapFloorLeg.builder()
        .currency(EUR)
        .calculation(IborRateCalculation.of(EUR_EURIBOR_6M))
        .capSchedule(ValueSchedule.of(strike))
        .notional(ValueSchedule.of(NOTIONAL))
        .paymentSchedule(paySchedule)
        .payReceive(PayReceive.RECEIVE).build();
    return IborCapFloor.of(leg);
  }
  
}
