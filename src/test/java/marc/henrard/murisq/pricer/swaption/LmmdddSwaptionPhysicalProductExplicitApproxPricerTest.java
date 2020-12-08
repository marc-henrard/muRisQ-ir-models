/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.HullWhiteSwaptionPhysicalProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LmmdddExamplesUtils;

/**
 * Tests {@link LmmdddSwaptionPhysicalProductExplicitApproxPricer}.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionPhysicalProductExplicitApproxPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());
  
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);

  /* Multi-curve */
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  
  /* LMM parameters (HW-like) */
  private static final double MEAN_REVERTION = 0.02;
  private static final double HW_SIGMA = 0.01;
  private static final DayCount HW_DAYCOUNT = DayCounts.ACT_365F;
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERTION, DoubleArray.of(HW_SIGMA), DoubleArray.of());
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider
      .of(HW_PARAMETERS, HW_DAYCOUNT, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  
  /* Swaption description */
  private static final double NOTIONAL = 1_000_000.0d;
  
  /* Pricer */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_SWAPTION_LMM_APPROX = 
      LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;
  private static final HullWhiteSwaptionPhysicalProductPricer PRICER_SWAPTION_HW =
      HullWhiteSwaptionPhysicalProductPricer.DEFAULT;
  private static final NormalSwaptionPhysicalProductPricer2 PRICER_SWAPTION_BACHELIER =
      NormalSwaptionPhysicalProductPricer2.DEFAULT;
  private static final RatesFiniteDifferenceSensitivityCalculator FD_CALC =
      new RatesFiniteDifferenceSensitivityCalculator(1.0E-7); // Better precision

  /* Tests */
  private static final Offset<Double> TOLERANCE_APPROX = within(1.5E+2);
  private static final Offset<Double> TOLERANCE_APPROX_IV = within(8.0E-6); // Implied volatility withing 0.06 bps
  private static final double TOLERANCE_PV01 = 5.0E+1;
  private static final boolean PRINT_DETAILS = false;
  
  /* Test v Hull-White model 
   * LMM dates are equal to underlying swap dates. */
  @Test
  public void hw_like() {
    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
    Tenor[] tenors = new Tenor[] {Tenor.TENOR_2Y, Tenor.TENOR_10Y, Tenor.TENOR_30Y};
    double[] moneyness = new double[] {-0.0100,-0.0050, 0, 0.0050, 0.0100};
    for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopexp]));
      ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_3M
          .createTrade(expiryDate, tenors[tenors.length - 1], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
      List<LocalDate> iborDates = new ArrayList<>();
      ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
      iborDates.add(iborLeg.get(0).getStartDate());
      for (SwapPaymentPeriod period : iborLeg) {
        iborDates.add(period.getEndDate());
      }
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = LmmdddExamplesUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates,
          EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
          VALUATION_ZONE, VALUATION_TIME, REF_DATA);

      for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
        for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
          SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
              .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, parRate + moneyness[loopmoney],
                  REF_DATA);
          Swaption swaption = Swaption.builder()
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
              .longShort(LongShort.LONG)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swap.getProduct()).build();
          ResolvedSwaption swaptionResolved = swaption.resolve(REF_DATA);
          CurrencyAmount pvApprox =
              PRICER_SWAPTION_LMM_APPROX.presentValue(swaptionResolved, MULTICURVE_EUR, lmmHw);
          CurrencyAmount pvHw =
              PRICER_SWAPTION_HW.presentValue(swaptionResolved, MULTICURVE_EUR, HW_PROVIDER);
          assertThat(pvApprox.getCurrency()).isEqualTo(pvHw.getCurrency());
          assertThat(pvApprox.getAmount()).isEqualTo(pvHw.getAmount(), TOLERANCE_APPROX);
//          if(PRINT_DETAILS) {
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + pvApprox + ", " + pvHw + ", " + (pvHw.getAmount()-pvApprox.getAmount()));
//          }
          double ivApprox = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, HW_DAYCOUNT, pvApprox.getAmount());
          double ivHw = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, HW_DAYCOUNT, pvHw.getAmount());
          if(PRINT_DETAILS) {
          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
              + ", " + ivApprox + ", " + ivHw + ", " + (ivHw-ivApprox));
          }
          assertThat(ivApprox).isEqualTo(ivHw, TOLERANCE_APPROX_IV); // Compare implied volatilities
        } // end loopmoney
      } // end looptenor
    } // end loopexp
  }

  /* Test AD rate sensitivities V Finite difference. */
  @Test
  public void sensitivity_hw_like() {
    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
    Tenor[] tenors = new Tenor[] {Tenor.TENOR_1Y, Tenor.TENOR_10Y};
    double[] moneyness = new double[] {-0.0050, 0, 0.0100};
    for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopexp]));
      ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_3M
          .createTrade(expiryDate, tenors[tenors.length - 1], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
      List<LocalDate> iborDates = new ArrayList<>();
      ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
      iborDates.add(iborLeg.get(0).getStartDate());
      for (SwapPaymentPeriod period : iborLeg) {
        iborDates.add(period.getEndDate());
      }
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw =
          LmmdddExamplesUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates,
              EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
              VALUATION_ZONE, VALUATION_TIME, REF_DATA);
      for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
        for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
          SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
              .createTrade(expiryDate,
                  tenors[looptenor],
                  (loopmoney == 0) ? BuySell.BUY : BuySell.SELL,
                  NOTIONAL,
                  parRate + moneyness[loopmoney],
                  REF_DATA);
          Swaption swaption = Swaption.builder()
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
              .longShort((loopexp == 1) ? LongShort.LONG : LongShort.SHORT)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swap.getProduct()).build();
          ResolvedSwaption swaptionResolved = swaption.resolve(REF_DATA);
          Pair<CurrencyAmount, PointSensitivityBuilder> pvPtsComputed = PRICER_SWAPTION_LMM_APPROX
              .presentValueSensitivityRatesStickyModel(swaptionResolved, MULTICURVE_EUR, lmmHw);
          CurrencyAmount pvComputed = pvPtsComputed.getFirst();
          CurrencyAmount pvApprox =
              PRICER_SWAPTION_LMM_APPROX.presentValue(swaptionResolved, MULTICURVE_EUR, lmmHw);
          assertThat(pvApprox.getCurrency()).isEqualTo(pvComputed.getCurrency());
          assertThat(pvApprox.getAmount()).isEqualTo(pvComputed.getAmount(), TOLERANCE_APPROX);

          CurrencyParameterSensitivities psAd = MULTICURVE_EUR.parameterSensitivity(pvPtsComputed.getSecond().build());
          CurrencyParameterSensitivities psFd = FD_CALC.sensitivity(MULTICURVE_EUR,
              (m) -> PRICER_SWAPTION_LMM_APPROX.presentValue(swaptionResolved, m, lmmHw));
          if (PRINT_DETAILS) {
            System.out.println("AD: " + psAd);
            System.out.println(psFd);
          }
          assertThat(psAd.equalWithTolerance(psFd, TOLERANCE_PV01)).isTrue();
        } // end loopmoney
      } // end looptenor
    } // end loopexp
  }
  
//  @Ignore // Test performance. Does not run in the standard unit test.
//  @Test
//  public void performance() {
//    long start, end;
//    int nbRep = 100;
//    int nbRep2 = 10;
//    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
//    Tenor[] tenors = new Tenor[] {Tenor.TENOR_1Y, Tenor.TENOR_10Y};
//    double[] moneyness = new double[] {-0.0050, 0, 0.0100};
//    // Create instruments
//    ResolvedSwaption[][][] swaptionsResolved = new ResolvedSwaption[expiries.length][tenors.length][moneyness.length];
//    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters[] lmmHw = 
//        new LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters[expiries.length];
//    for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopexp]));
//      ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_3M
//          .createTrade(expiryDate, tenors[tenors.length - 1], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
//      List<LocalDate> iborDates = new ArrayList<>();
//      ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
//      iborDates.add(iborLeg.get(0).getStartDate());
//      for (SwapPaymentPeriod period : iborLeg) {
//        iborDates.add(period.getEndDate());
//      }
//      lmmHw[loopexp] = LmmdddUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates,
//          EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
//          VALUATION_ZONE, VALUATION_TIME, REF_DATA);
//      for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
//            .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
//        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
//        for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//          SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
//              .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, parRate + moneyness[loopmoney],
//                  REF_DATA);
//          Swaption swaption = Swaption.builder()
//              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
//              .longShort(LongShort.LONG)
//              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
//              .underlying(swap.getProduct()).build();
//          swaptionsResolved[loopexp][looptenor][loopmoney] = swaption.resolve(REF_DATA);
//        } // end loopmoney
//      } // end looptenor
//    } // end loopexp
//
//    // LMM
//    for (int j = 0; j < nbRep2; j++) {
//      start = System.currentTimeMillis();
//      double testLmm = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//          for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//            for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//              CurrencyAmount pvApprox = PRICER_SWAPTION_LMM_APPROX
//                  .presentValue(swaptionsResolved[loopexp][looptenor][loopmoney], MULTICURVE_EUR, lmmHw[loopexp]);
//              testLmm += pvApprox.getAmount();
//            } // end loopmoney
//          } // end looptenor
//        } // end loopexp
//      }
//      end = System.currentTimeMillis();
//      System.out.println("LMM computation time: " + (end - start) + " ms. (" +
//          (nbRep * expiries.length * tenors.length * moneyness.length) + " computations) " + testLmm);
//
//      // HW
//      start = System.currentTimeMillis();
//      double testHW = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//          for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//            for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//              CurrencyAmount pvHw = PRICER_SWAPTION_HW
//                  .presentValue(swaptionsResolved[loopexp][looptenor][loopmoney], MULTICURVE_EUR, HW_PROVIDER);
//              testHW += pvHw.getAmount();
//            } // end loopmoney
//          } // end looptenor
//        } // end loopexp
//      }
//      end = System.currentTimeMillis();
//      System.out.println("HW computation time: " + (end - start) + " ms. (" +
//          (nbRep * expiries.length * tenors.length * moneyness.length) + " computations) " + testHW);
//    }
//  }

////Test performance. Does not run in the standard unit test.
//  @Test
//  public void performance_sensi() {
//    long start, end;
//    int nbRep = 250;
//    int nbRep2 = 10;
//    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
//    Tenor[] tenors = new Tenor[] {Tenor.TENOR_1Y, Tenor.TENOR_10Y};
//    double[] moneyness = new double[] {-0.0050, 0, 0.0100};
//    // Create instruments
//    ResolvedSwaption[][][] swaptionsResolved = new ResolvedSwaption[expiries.length][tenors.length][moneyness.length];
//    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters[] lmmHw =
//        new LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters[expiries.length];
//    for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopexp]));
//      ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_3M
//          .createTrade(expiryDate, tenors[tenors.length - 1], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
//      List<LocalDate> iborDates = new ArrayList<>();
//      ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
//      iborDates.add(iborLeg.get(0).getStartDate());
//      for (SwapPaymentPeriod period : iborLeg) {
//        iborDates.add(period.getEndDate());
//      }
//      lmmHw[loopexp] = LmmdddExamplesUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates,
//          EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
//          VALUATION_ZONE, VALUATION_TIME, REF_DATA);
//      for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
//            .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
//        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
//        for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//          SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
//              .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, parRate + moneyness[loopmoney],
//                  REF_DATA);
//          Swaption swaption = Swaption.builder()
//              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
//              .longShort(LongShort.LONG)
//              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
//              .underlying(swap.getProduct()).build();
//          swaptionsResolved[loopexp][looptenor][loopmoney] = swaption.resolve(REF_DATA);
//        } // end loopmoney
//      } // end looptenor
//    } // end loopexp
//
//    // PV
//    for (int j = 0; j < nbRep2; j++) {
//      start = System.currentTimeMillis();
//      double testLmm = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//          for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//            for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//              CurrencyAmount pvApprox = PRICER_SWAPTION_LMM_APPROX
//                  .presentValue(swaptionsResolved[loopexp][looptenor][loopmoney], MULTICURVE_EUR, lmmHw[loopexp]);
//              testLmm += pvApprox.getAmount();
//            } // end loopmoney
//          } // end looptenor
//        } // end loopexp
//      }
//      end = System.currentTimeMillis();
//      System.out.println("LMM PV computation time: " + (end - start) + " ms. (" +
//          (nbRep * expiries.length * tenors.length * moneyness.length) + " computations) " + testLmm);
//
//      // PV + sensi
//      start = System.currentTimeMillis();
//      double testHW = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//          for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//            for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//              Pair<CurrencyAmount, PointSensitivityBuilder> pvPts = PRICER_SWAPTION_LMM_APPROX
//                  .presentValueSensitivityRatesStickyModel(
//                      swaptionsResolved[loopexp][looptenor][loopmoney], MULTICURVE_EUR, lmmHw[loopexp]);
//              CurrencyParameterSensitivities psAd = 
//                  MULTICURVE_EUR.parameterSensitivity(pvPts.getSecond().build());
//              testHW += psAd.getSensitivities().get(0).getSensitivity().get(0);
//            } // end loopmoney
//          } // end looptenor
//        } // end loopexp
//      }
//      end = System.currentTimeMillis();
//      System.out.println("LMM PV + PV01 computation time: " + (end - start) + " ms. (" +
//          (nbRep * expiries.length * tenors.length * moneyness.length) + " computations) " + testHW);
//    }
//    // Ratio Time(PV + PV01) / Time(PV) ~ 3.75 to 4.00
//  }
  
  // TODO: Compare to MC for LMM with different displacement and multi-factors

}
