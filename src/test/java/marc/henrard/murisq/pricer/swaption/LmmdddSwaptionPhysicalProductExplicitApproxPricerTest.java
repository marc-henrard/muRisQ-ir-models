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
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.HullWhiteSwaptionPhysicalProductPricer;
import com.opengamma.strata.pricer.swaption.NormalSwaptionPhysicalProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LmmdddUtils;

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

  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/test/resources/curve-config/";
  private static final String FILE_QUOTES = "src/test/resources/quotes/quotes-20151120-eur.csv";

  private static final ResourceLocator GROUPS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "groups-eur.csv");
  private static final ResourceLocator SETTINGS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "settings-eur.csv");
  private static final ResourceLocator NODES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "nodes-eur.csv");
  private static final ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> GROUPS_CONFIG =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, NODES_RESOURCE);
  private static final CurveGroupName GROUP_EUR = CurveGroupName.of("EUR-DSCONOIS-EURIBOR3MIRS-EURIBOR6MIRS");
  private static final MarketData MARKET_DATA;
  static {
    ResourceLocator quotesResource = ResourceLocator.of(FILE_QUOTES);
    ImmutableMap<QuoteId, Double> quotes = QuotesCsvLoader.load(VALUATION_DATE, quotesResource);
    MARKET_DATA = MarketData.of(VALUATION_DATE, quotes);
  }
  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      CALIBRATOR.calibrate(GROUPS_CONFIG.get(GROUP_EUR), MARKET_DATA, REF_DATA);
  
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
  private static final NormalSwaptionPhysicalProductPricer PRICER_SWAPTION_BACHELIER =
      NormalSwaptionPhysicalProductPricer.DEFAULT;

  /* Tests */
  private static final Offset<Double> TOLERANCE_APPROX = within(1.0E+1);
  private static final Offset<Double> TOLERANCE_APPROX_IV = within(2.0E-6); // Implied volatility withing 0.02 bps
  
  /* Test v Hull-White model 
   * LMM dates are equal to underlying swap dates. */
  @Test
  public void hw_like() {
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
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = LmmdddUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates,
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
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + pvApprox + ", " + pvHw + ", " + (pvHw.getAmount()-pvApprox.getAmount()));
          double ivApprox = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, HW_DAYCOUNT, pvApprox.getAmount());
          double ivHw = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, HW_DAYCOUNT, pvHw.getAmount());
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + ivApprox + ", " + ivHw + ", " + (ivHw-ivApprox));
          assertThat(ivApprox).isEqualTo(ivHw, TOLERANCE_APPROX_IV); // Compare implied volatilities
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
  
  // TODO: Compare to MC for LMM with different displacement and multi-factors
  // TODO: Compare to G2++

}
