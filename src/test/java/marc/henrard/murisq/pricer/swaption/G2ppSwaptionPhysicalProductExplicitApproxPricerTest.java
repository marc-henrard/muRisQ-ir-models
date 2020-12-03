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
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
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
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantParameters;

/**
 * Tests {@link G2ppSwaptionPhysicalProductExplicitApproxPricer}.
 * 
 * @author Marc Henrard
 */
public class G2ppSwaptionPhysicalProductExplicitApproxPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());
  
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  
  /* G2++ parameters */
  private static final Currency CURRENCY = Currency.EUR;
  private static final double CORRELATION = -0.50;
  private static final double KAPPA_1 = 0.02;
  private static final double KAPPA_2 = 0.20;
  private static final DoubleArray VOLATILITY_1_CST = DoubleArray.of(0.01d);
  private static final DoubleArray VOLATILITY_2_CST = DoubleArray.of(0.005d);
  private static final DoubleArray VOLATILITY_TIME_CST = DoubleArray.of();
  private static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;
  private static final DayCount G2PP_DAYCOUNT = DayCounts.ACT_365F; // Used only for Implied Volatility computation
  private static final G2ppPiecewiseConstantParameters PARAMETERS = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1)
      .kappa2(KAPPA_2)
      .volatility1(VOLATILITY_1_CST)
      .volatility2(VOLATILITY_2_CST)
      .volatilityTime(VOLATILITY_TIME_CST)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();
  private static final G2ppPiecewiseConstantParameters PARAMETERS_G2PP_DEG_1 = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1)
      .kappa2(KAPPA_2)
      .volatility1(VOLATILITY_1_CST)
      .volatility2(DoubleArray.of(0.0d))
      .volatilityTime(VOLATILITY_TIME_CST)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();
  private static final G2ppPiecewiseConstantParameters PARAMETERS_G2PP_DEG_2 = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1)
      .kappa2(KAPPA_2)
      .volatility1(DoubleArray.of(0.0d))
      .volatility2(VOLATILITY_2_CST)
      .volatilityTime(VOLATILITY_TIME_CST)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();
  private static final DayCount HW_DAYCOUNT = DayCounts.ACT_365F;
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS_1 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_1, VOLATILITY_1_CST, DoubleArray.of());
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider PROVIDER_HW_1 =
      HullWhiteOneFactorPiecewiseConstantParametersProvider
      .of(HW_PARAMETERS_1, HW_DAYCOUNT, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS_2 =
      HullWhiteOneFactorPiecewiseConstantParameters.of(KAPPA_2, VOLATILITY_2_CST, DoubleArray.of());
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider PROVIDER_HW_2 =
      HullWhiteOneFactorPiecewiseConstantParametersProvider
      .of(HW_PARAMETERS_2, HW_DAYCOUNT, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);

  /* Swaption description */
  private static final double NOTIONAL = 1_000_000.0d;
  
  /* Pricers */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final G2ppSwaptionPhysicalProductExplicitApproxPricer PRICER_SWPT_G2PP_APPROX =
      G2ppSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;
  private static final G2ppSwaptionPhysicalProductNumericalIntegration2DPricer PRICER_SWPT_G2PP_NI =
      G2ppSwaptionPhysicalProductNumericalIntegration2DPricer.DEFAULT;
  private static final NormalSwaptionPhysicalProductPricer PRICER_SWAPTION_BACHELIER =
      NormalSwaptionPhysicalProductPricer.DEFAULT;
  private static final HullWhiteSwaptionPhysicalProductPricer PRICER_SWAPTION_HW =
      HullWhiteSwaptionPhysicalProductPricer.DEFAULT;
  
  private static final Offset<Double> TOLERANCE_IV = Offset.offset(1.0E-5);

  /* Compare efficient approximation to Numerical integration 2D */
  @Test
  public void pv_approx_v_ni() {
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
      for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
        for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
          for (int loopPayRec = 0; loopPayRec < 2; loopPayRec++) {
            SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
                .createTrade(expiryDate, tenors[looptenor],
                    (loopPayRec == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, parRate + moneyness[loopmoney],
                    REF_DATA);
            double[] ivApprox = new double[2];
            for (int looplongshort = 0; looplongshort < 2; looplongshort++) {
              Swaption swaptionPayLong = Swaption.builder()
                  .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
                  .longShort((looplongshort == 0) ? LongShort.LONG : LongShort.SHORT)
                  .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
                  .underlying(swap.getProduct()).build();
              ResolvedSwaption swaptionResolved = swaptionPayLong.resolve(REF_DATA);
              CurrencyAmount pvApprox =
                  PRICER_SWPT_G2PP_APPROX.presentValue(swaptionResolved, MULTICURVE_EUR, PARAMETERS);
              CurrencyAmount pvNi =
                  PRICER_SWPT_G2PP_NI.presentValue(swaptionResolved, MULTICURVE_EUR, PARAMETERS);
              assertThat(pvApprox.getCurrency()).isEqualTo(pvNi.getCurrency());
//              System.out
//                  .println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] + ", " + pvApprox +
//                      ", " + pvNi + ", " + (pvNi.getAmount() - pvApprox.getAmount()));
              ivApprox[looplongshort] = PRICER_SWAPTION_BACHELIER
                  .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, G2PP_DAYCOUNT,
                      pvApprox.getAmount());
              double ivNi = PRICER_SWAPTION_BACHELIER
                  .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, G2PP_DAYCOUNT, pvNi.getAmount());
//              System.out
//                  .println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//                      + ((loopPayRec == 0) ? BuySell.BUY : BuySell.SELL)
//                      + ((looplongshort == 0) ? LongShort.LONG : LongShort.SHORT)
//                      + ", " + ivApprox[looplongshort] +
//                      ", " + ivNi + ", " + (ivNi - ivApprox[looplongshort]));
              assertThat(ivApprox[looplongshort]).isEqualTo(ivNi, TOLERANCE_IV); // Compare implied volatilities
            } // end long/short
            assertThat(ivApprox[0]).isEqualTo(ivApprox[1], TOLERANCE_IV); // Long/short
          } // end pay/receive
        } // end loopmoney
      } // end looptenor
    } // end loopexp
  }
  
  /* Compare degenerated G2++ with Hull-White 1-factor */
  @Test
  public void pv_g2pp_v_hw() {
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
      for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenors[looptenor], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
        for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
          for (int loopPayRec = 0; loopPayRec < 2; loopPayRec++) {
            SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
                .createTrade(expiryDate, tenors[looptenor],
                    (loopPayRec == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, parRate + moneyness[loopmoney],
                    REF_DATA);
            double[] ivG2pp1 = new double[2];
            for (int looplongshort = 0; looplongshort < 2; looplongshort++) {
              Swaption swaptionPayLong = Swaption.builder()
                  .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
                  .longShort((looplongshort == 0) ? LongShort.LONG : LongShort.SHORT)
                  .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
                  .underlying(swap.getProduct()).build();
              ResolvedSwaption swaptionResolved = swaptionPayLong.resolve(REF_DATA);
              CurrencyAmount pvG2pp1 =
                  PRICER_SWPT_G2PP_APPROX.presentValue(swaptionResolved, MULTICURVE_EUR, PARAMETERS_G2PP_DEG_1);
              CurrencyAmount pvHw1 =
                  PRICER_SWAPTION_HW.presentValue(swaptionResolved, MULTICURVE_EUR, PROVIDER_HW_1);
              assertThat(pvG2pp1.getCurrency()).isEqualTo(pvHw1.getCurrency());
              ivG2pp1[looplongshort] = PRICER_SWAPTION_BACHELIER
                  .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, G2PP_DAYCOUNT,
                      pvG2pp1.getAmount());
              double ivHw1 = PRICER_SWAPTION_BACHELIER
                  .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, G2PP_DAYCOUNT, pvHw1.getAmount());
//              System.out
//                  .println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//                      + ((loopPayRec == 0) ? BuySell.BUY : BuySell.SELL)
//                      + ((looplongshort == 0) ? LongShort.LONG : LongShort.SHORT)
//                      + ", " + ivG2pp1[looplongshort] +
//                      ", " + ivHw1 + ", " + (ivHw1 - ivG2pp1[looplongshort]));
              assertThat(ivG2pp1[looplongshort]).isEqualTo(ivHw1, TOLERANCE_IV); // Compare implied volatilities
              CurrencyAmount pvG2pp2 =
                  PRICER_SWPT_G2PP_APPROX.presentValue(swaptionResolved, MULTICURVE_EUR, PARAMETERS_G2PP_DEG_2);
              CurrencyAmount pvHw2 =
                  PRICER_SWAPTION_HW.presentValue(swaptionResolved, MULTICURVE_EUR, PROVIDER_HW_2);
              assertThat(pvG2pp2.getCurrency()).isEqualTo(pvHw2.getCurrency());
              double ivG2pp2 = PRICER_SWAPTION_BACHELIER
                  .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, G2PP_DAYCOUNT, pvG2pp2.getAmount());
              double ivHw2 = PRICER_SWAPTION_BACHELIER
                  .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, G2PP_DAYCOUNT, pvHw2.getAmount());
              assertThat(ivG2pp2).isEqualTo(ivHw2, TOLERANCE_IV); // Compare implied volatilities
            } // end long/short
            assertThat(ivG2pp1[0]).isEqualTo(ivG2pp1[1], TOLERANCE_IV); // Long/short
          } // end pay/receive
        } // end loopmoney
      } // end looptenor
    } // end loopexp
  }
  
//@Ignore // Test performance. Does not run in the standard unit test.
//  @Test
//  public void performance() {
//    // Previous run results (1,800 swaptions): Approximation 50 ms / NI-2D 115,000 ms
//    long start, end;
//    int nbRep = 100;
//    int nbRep2 = 5;
//    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
//    Tenor[] tenors = new Tenor[] {Tenor.TENOR_1Y, Tenor.TENOR_10Y};
//    double[] moneyness = new double[] {-0.0050, 0, 0.0100};
//    // Create instruments
//    ResolvedSwaption[][][] swaptionsResolved = new ResolvedSwaption[expiries.length][tenors.length][moneyness.length];
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
//    // Approx
//    for (int j = 0; j < nbRep2; j++) {
//      start = System.currentTimeMillis();
//      double testLmm = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//          for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//            for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//              CurrencyAmount pvApprox = PRICER_SWPT_G2PP_APPROX
//                  .presentValue(swaptionsResolved[loopexp][looptenor][loopmoney], MULTICURVE_EUR, PARAMETERS);
//              testLmm += pvApprox.getAmount();
//            } // end loopmoney
//          } // end looptenor
//        } // end loopexp
//      }
//      end = System.currentTimeMillis();
//      System.out.println("G2++ approx computation time: " + (end - start) + " ms. (" +
//          (nbRep * expiries.length * tenors.length * moneyness.length) + " computations) " + testLmm);
//
//      // NI-2D
//      start = System.currentTimeMillis();
//      double testHW = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
//          for (int looptenor = 0; looptenor < tenors.length; looptenor++) {
//            for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
//              CurrencyAmount pvHw = PRICER_SWPT_G2PP_NI
//                  .presentValue(swaptionsResolved[loopexp][looptenor][loopmoney], MULTICURVE_EUR, PARAMETERS);
//              testHW += pvHw.getAmount();
//            } // end loopmoney
//          } // end looptenor
//        } // end loopexp
//      }
//      end = System.currentTimeMillis();
//      System.out.println("G2++ NI-2D computation time: " + (end - start) + " ms. (" +
//          (nbRep * expiries.length * tenors.length * moneyness.length) + " computations) " + testHW);
//    }
//  }

}
