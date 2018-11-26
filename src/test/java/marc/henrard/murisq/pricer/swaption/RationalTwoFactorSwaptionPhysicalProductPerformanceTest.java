/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.dataset.RationalTwoFactorParameters20151120DataSet;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorGenericParameters;
import marc.henrard.murisq.pricer.swaption.RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer;
import marc.henrard.murisq.pricer.swaption.RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer;

/**
 * Tests of {@link RationalTwoFactorSwaptionPhysicalProductPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactorSwaptionPhysicalProductPerformanceTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);

  private static final double NOTIONAL = 1_000_000.0d;

  private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;

  private static final RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer PRICER_SWPT_2_NI =
      RationalTwoFactorSwaptionPhysicalProductNumericalIntegrationPricer.DEFAULT;
  private static final RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer PRICER_SWPT_S_EX =
      RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer.DEFAULT;

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
  private static final ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> GROUPS_CONFIG =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, NODES_RESOURCE);
  private static final MarketData MARKET_DATA =
      MarketData.of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));

  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final String GROUP_NAME = "EUR-DSCONOIS-EURIBOR3MIRS-EURIBOR6MIRS";
  public static final ImmutableRatesProvider MULTICURVE =
      CALIBRATOR.calibrate(GROUPS_CONFIG.get(CurveGroupName.of(GROUP_NAME)), MARKET_DATA, REF_DATA);

  /* Rational model data */
  private static final RationalTwoFactorGenericParameters RATIONAL_2F =
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F;

  @Test(enabled = false)
  public void present_value_performance() {
    long startTime, endTime;
    int rep = 2;
    int nbTests = 10;

    ResolvedSwaption[][][] swpt = new ResolvedSwaption[NB_EXPIRIES][NB_TENORS][NB_MONEYNESS];
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
          swpt[i][j][k] = Swaption.builder()
              .longShort(LongShort.LONG)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapPayer.getProduct()).build().resolve(REF_DATA);
        }
      }
    }
    
    for (int looprep = 0; looprep < rep; looprep++) { // Repetitions - start

      startTime = System.currentTimeMillis();
      double pvNi = 0.0d;
      for (int looptest = 0; looptest < nbTests; looptest++) {
        for (int i = 0; i < NB_EXPIRIES; i++) {
          for (int j = 0; j < NB_TENORS; j++) {
            for (int k = 0; k < NB_MONEYNESS; k++) {
              pvNi += PRICER_SWPT_2_NI.presentValue(swpt[i][j][k], MULTICURVE, RATIONAL_2F).getAmount();
            }
          }
        }
      }
      endTime = System.currentTimeMillis();
      System.out
          .println("Numerical integration time for " + nbTests + " tests (" + (NB_EXPIRIES * NB_TENORS * NB_MONEYNESS) +
              " types) : " + (endTime - startTime) + " ms - " + pvNi);

      startTime = System.currentTimeMillis();
      double pvSe = 0.0d;
      for (int looptest = 0; looptest < nbTests; looptest++) {
        for (int i = 0; i < NB_EXPIRIES; i++) {
          for (int j = 0; j < NB_TENORS; j++) {
            for (int k = 0; k < NB_MONEYNESS; k++) {
              pvSe += PRICER_SWPT_S_EX.presentValue(swpt[i][j][k], MULTICURVE, RATIONAL_2F).getAmount();
            }
          }
        }
      }
      endTime = System.currentTimeMillis();
      System.out
          .println("Semi-explicit formula time for " + nbTests + " tests (" + (NB_EXPIRIES * NB_TENORS * NB_MONEYNESS) +
              " types) : " + (endTime - startTime) + " ms - " + pvSe);
    } // Repetitions - end
  }

}
