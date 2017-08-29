/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;

import marc.henrard.risq.model.dataset.RationalTwoFactorParameters20151120DataSet;
import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;
import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorGenericParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorGenericParameters;
import marc.henrard.risq.pricer.swaption.RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer;
import marc.henrard.risq.pricer.swaption.RationalTwoFactorSwaptionPhysicalProductPricer;
import marc.henrard.risq.pricer.swaption.RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

/**
 * Tests of {@link RationalTwoFactorSwaptionPhysicalProductPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactorSwaptionPhysicalProductSemiExplicitNiPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  
  private static final double NOTIONAL = 1_000_000.0d;
  
  private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;

  private static final RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer PRICER_SWPT_2_NI =
      RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer.DEFAULT; 
  private static final RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer PRICER_SWPT_S_EX =
      RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer.DEFAULT; 
  private static final int NB_STEP_HIGH = 50; // Can be increased to 100 to improve TOLERANCE_PV_NI
  private static final RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer PRICER_SWPT_2_NI_HIGH =
      new RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer(NB_STEP_HIGH); // High precision for verification
  private static final RationalOneFactorSwaptionPhysicalProductExplicitPricer PRICER_SWAPT_1_EX =
      RationalOneFactorSwaptionPhysicalProductExplicitPricer.DEFAULT;

  /* Descriptions of swaptions */
  private static final Period[] EXPIRIES_PER = new Period[] {
    Period.ofYears(1), Period.ofYears(2), Period.ofYears(3), Period.ofYears(4), Period.ofYears(5), 
    Period.ofYears(7), Period.ofYears(10)};
  private static final int NB_EXPIRIES = EXPIRIES_PER.length;
  private static final Period[] TENORS_PER = new Period[] {
    Period.ofYears(1), Period.ofYears(2), Period.ofYears(3), Period.ofYears(4), Period.ofYears(5), 
    Period.ofYears(7), Period.ofYears(10)};
  private static final int NB_TENORS = TENORS_PER.length;
  private static final double[] MONEYNESS = new double[] {-0.0025, 0.00, 0.0100}; 
  private static final int NB_MONEYNESS = MONEYNESS.length;

  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/test/resources/curve-config/";
  private static final String FILE_QUOTES = "src/test/resources/quotes/quotes-20151120-eur.csv";

  private static final ResourceLocator GROUPS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "groups-eur.csv");
  private static final ResourceLocator SETTINGS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "settings-eur.csv");
  private static final ResourceLocator NODES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "nodes-eur.csv");
  private static final ImmutableMap<CurveGroupName, CurveGroupDefinition> GROUPS_CONFIG =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, NODES_RESOURCE);
  private static final MarketData MARKET_DATA = 
      MarketData.of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));

  private static final CurveCalibrator CALIBRATOR = CurveCalibrator.standard();
  private static final String GROUP_NAME = "EUR-DSCONOIS-EURIBOR3MIRS-EURIBOR6MIRS";
  public static final ImmutableRatesProvider MULTICURVE = 
      CALIBRATOR.calibrate(GROUPS_CONFIG.get(CurveGroupName.of(GROUP_NAME)), MARKET_DATA, REF_DATA);
  
  /* Rational model data */
  private static final RationalTwoFactorGenericParameters RATIONAL_2F = 
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F;
  private static final RationalTwoFactorGenericParameters RATIONAL_2F_REDUCED_1 = 
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F_REDUCED_1;
  private static final RationalTwoFactorGenericParameters RATIONAL_2F_REDUCED_0 = 
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F_REDUCED_0;
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  private static final RationalOneFactorGenericParameters RATIONAL_1F = 
      RationalOneFactorGenericParameters.of(EUR, RATIONAL_2F_REDUCED_0.a1(), 
          RATIONAL_2F_REDUCED_0.getB0(), RATIONAL_2F_REDUCED_0.getB1(), TIME_MEAS, VALUATION_DATE);
  
  /* Constants */
//  private static final double TOLERANCE_PV_HC = 1.0E-6;
  private static final double TOLERANCE_PV_PARITY = 1.0E-1;
  private static final double TOLERANCE_PV_NI = 1.5;

  /* Test payer/receiver parity. */
  public void present_value_payer_receiver_parity() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int j = 0; j < NB_TENORS; j++) {
        SwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
            VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, 0, REF_DATA);
        ResolvedSwap swap0Resolved = swap0.getProduct().resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0Resolved, MULTICURVE);
        LocalDate expiryDate = EUR_EURIBOR_6M.calculateFixingFromEffective(swap0Resolved.getStartDate(), REF_DATA);
        for (int k = 0; k < NB_MONEYNESS; k++) {
          SwapTrade swapPayer = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
              VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, parRate + MONEYNESS[k], REF_DATA);
          SwapTrade swapReceiver = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
              VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.SELL, NOTIONAL, parRate + MONEYNESS[k], REF_DATA);
          Swaption swptPayerLong = Swaption.builder()
              .longShort(LongShort.LONG)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapPayer.getProduct()).build();
          Swaption swptReceiverShort = Swaption.builder()
              .longShort(LongShort.SHORT)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapReceiver.getProduct()).build();
          double pvSwapPayer = PRICER_SWAP.presentValue(swapPayer.getProduct().resolve(REF_DATA), MULTICURVE).getAmount(Currency.EUR).getAmount();
          double pvNumIntegPayLong = PRICER_SWPT_2_NI.presentValue(swptPayerLong.resolve(REF_DATA), MULTICURVE, RATIONAL_2F).getAmount();
          double pvNumIntegRecShort = PRICER_SWPT_2_NI.presentValue(swptReceiverShort.resolve(REF_DATA), MULTICURVE, RATIONAL_2F).getAmount();
          assertEquals(pvNumIntegPayLong + pvNumIntegRecShort, pvSwapPayer, TOLERANCE_PV_PARITY,
              "Payer/receiver parity: " + EXPIRIES_PER[i] + TENORS_PER[j] + MONEYNESS[k]);
          double pvSemiExplPayLong = PRICER_SWPT_S_EX.presentValue(swptPayerLong.resolve(REF_DATA), MULTICURVE, RATIONAL_2F).getAmount();
          double pvSemiExplRecShort = PRICER_SWPT_S_EX.presentValue(swptReceiverShort.resolve(REF_DATA), MULTICURVE, RATIONAL_2F).getAmount();
          assertEquals(pvSemiExplPayLong + pvSemiExplRecShort, pvSwapPayer, TOLERANCE_PV_PARITY,
              "Payer/receiver parity: " + EXPIRIES_PER[i] + TENORS_PER[j] + MONEYNESS[k]);
        }
      }
    }
  }

  /* Test 2 factors reduced v 1 factor. */
  // For more precise test, use NB_STEP_HIGH=100 and TOLERANCE_PV_NI=1.0E-1;
  public void present_value_reduced() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int j = 0; j < NB_TENORS; j++) {
        SwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
            VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, 0, REF_DATA);
        ResolvedSwap swap0Resolved = swap0.getProduct().resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0Resolved, MULTICURVE);
        LocalDate expiryDate = EUR_EURIBOR_6M.calculateFixingFromEffective(swap0Resolved.getStartDate(), REF_DATA);
        for (int k = 0; k < NB_MONEYNESS; k++) {
          SwapTrade swapPayer = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
              VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, parRate + MONEYNESS[k], REF_DATA);
          ResolvedSwaption swptPayerLong = Swaption.builder()
              .longShort(LongShort.LONG)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapPayer.getProduct()).build().resolve(REF_DATA);
          double pvNumInteg2PayLong0 = PRICER_SWPT_2_NI_HIGH.presentValue(swptPayerLong, MULTICURVE, RATIONAL_2F_REDUCED_0).getAmount();
          double pvNumInteg2PayLong1 = PRICER_SWPT_2_NI_HIGH.presentValue(swptPayerLong, MULTICURVE, RATIONAL_2F_REDUCED_1).getAmount();
          double pvExplicit1PayLong = PRICER_SWAPT_1_EX.presentValue(swptPayerLong, MULTICURVE, RATIONAL_1F).getAmount();
          assertEquals(pvNumInteg2PayLong0, pvExplicit1PayLong, TOLERANCE_PV_NI,
              "2F reduced v 1F parity: " + EXPIRIES_PER[i] + TENORS_PER[j] + MONEYNESS[k]);
          assertEquals(pvNumInteg2PayLong1, pvExplicit1PayLong, TOLERANCE_PV_NI,
              "2F reduced v 1F parity: " + EXPIRIES_PER[i] + TENORS_PER[j] + MONEYNESS[k]);
        }
      }
    }
  }

  /* Test 2 factors NI v semi-explicit. */
  public void present_value_semi_explicit() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int j = 0; j < NB_TENORS; j++) {
        SwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
            VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, 0, REF_DATA);
        ResolvedSwap swap0Resolved = swap0.getProduct().resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0Resolved, MULTICURVE);
        LocalDate expiryDate = EUR_EURIBOR_6M.calculateFixingFromEffective(swap0Resolved.getStartDate(), REF_DATA);
        for (int k = 0; k < NB_MONEYNESS; k++) {
          SwapTrade swapPayer = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
              VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, parRate + MONEYNESS[k], REF_DATA);
          ResolvedSwaption swptPayerLong = Swaption.builder()
              .longShort(LongShort.LONG)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapPayer.getProduct()).build().resolve(REF_DATA);
          double pvNumInteg2PayLong = PRICER_SWPT_2_NI_HIGH.presentValue(swptPayerLong, MULTICURVE, RATIONAL_2F).getAmount();
          double pvSemiExpliPayLong = PRICER_SWPT_S_EX.presentValue(swptPayerLong, MULTICURVE, RATIONAL_2F).getAmount();
          assertEquals(pvNumInteg2PayLong, pvSemiExpliPayLong, TOLERANCE_PV_NI,
              "2F NI v 2F Semi-explicit: " + EXPIRIES_PER[i] + TENORS_PER[j] + MONEYNESS[k]);
        }
      }
    }
  }
  
}
