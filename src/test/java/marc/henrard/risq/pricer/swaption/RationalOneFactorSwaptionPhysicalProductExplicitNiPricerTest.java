/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swaption;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

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
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;
import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;
import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorSimpleHWShapeParameters;

/**
 * Tests {@link RationalOneFactorSwaptionPhysicalProductExplicitPricer} and
 * {@link RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer}.
 * <p>
 * The tests are done with realistic EUR data.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorSwaptionPhysicalProductExplicitNiPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);

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
  private static final CurveGroupName GROUP_EUR = CurveGroupName.of("EUR-DSCONOIS-EURIBOR3MIRS-EURIBOR6MIRS");
  private static final MarketData MARKET_DATA;
  static {
    ResourceLocator quotesResource = ResourceLocator.of(FILE_QUOTES);
    ImmutableMap<QuoteId, Double> quotes = QuotesCsvLoader.load(VALUATION_DATE, quotesResource);
    MARKET_DATA = MarketData.of(VALUATION_DATE, quotes);
  }
  private static final CurveCalibrator CALIBRATOR = CurveCalibrator.standard();
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      CALIBRATOR.calibrate(GROUPS_CONFIG.get(GROUP_EUR), MARKET_DATA, REF_DATA);
  
  /* Rational model data: HW shaped b0 */
  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  private static final RationalOneFactorSimpleHWShapeParameters MODEL_SIMPLE = 
      RationalOneFactorSimpleHWShapeParameters.of(A, B_0_0, ETA, KAPPA, TIME_MEAS, MULTICURVE_EUR.discountFactors(EUR));

  /* Descriptions of swaptions */
  private static final Period[] EXPIRIES_PER = new Period[] {
    Period.ofMonths(3), Period.ofYears(2), Period.ofYears(10)};
  private static final int NB_EXPIRIES = EXPIRIES_PER.length;
  private static final Period[] TENORS_PER = new Period[] {
    Period.ofYears(1), Period.ofYears(5), Period.ofYears(10)};
  private static final int NB_TENORS = TENORS_PER.length;
  private static final double[] MONEYNESS = new double[] {-0.0025, 0.00, 0.0100};
  private static final int NB_MONEYNESS = MONEYNESS.length;
  private static final double NOTIONAL = 100_000_000.0d;
  
  /* Pricer */
  private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;
  private static final RationalOneFactorSwaptionPhysicalProductExplicitPricer PRICER_SWAPTION_RATIONAL_EXPLICIT =
      RationalOneFactorSwaptionPhysicalProductExplicitPricer.DEFAULT;
  private static final RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer PRICER_SWAPTION_RATIONAL_NI =
      RationalOneFactorSwaptionPhysicalProductNumericalIntegrationPricer.DEFAULT;
  
  /* Tolerance */
  private static final double TOLERANCE_PV_NI = 5.0E-2;
  private static final double TOLERANCE_PV_EXPL = 1.0E-2;
  
  /* Test explicit formula vs numerical integration. Simple model parameters. */
  public void present_value_numerical_integration_simple() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int j = 0; j < NB_TENORS; j++) {
        for (int k = 0; k < NB_MONEYNESS; k++) {
          SwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
              VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, 0, REF_DATA);
          ResolvedSwap swap0Resolved = swap0.getProduct().resolve(REF_DATA);
          double parRate = PRICER_SWAP.parRate(swap0Resolved, MULTICURVE_EUR);
          LocalDate expiryDate = EUR_EURIBOR_6M.calculateFixingFromEffective(swap0Resolved.getStartDate(), REF_DATA);
          SwapTrade swapPayer = EUR_FIXED_1Y_EURIBOR_6M.createTrade(VALUATION_DATE, 
              EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, parRate + MONEYNESS[k], REF_DATA);
          ResolvedSwaption swpt = Swaption.builder()
              .longShort(LongShort.LONG)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapPayer.getProduct()).build().resolve(REF_DATA);
          double pvExplicit = 
              PRICER_SWAPTION_RATIONAL_EXPLICIT.presentValue(swpt, MULTICURVE_EUR, MODEL_SIMPLE).getAmount();
          double pvNumInteg = 
              PRICER_SWAPTION_RATIONAL_NI.presentValue(swpt, MULTICURVE_EUR, MODEL_SIMPLE).getAmount();
          assertEquals(pvExplicit, pvNumInteg, TOLERANCE_PV_NI);
        }
      }
    }
  }

  /* Test buy/sell parity and put/call/forward parity. */
  public void present_value_longshort_putcall() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int j = 0; j < NB_TENORS; j++) {
        for (int k = 0; k < NB_MONEYNESS; k++) {
          double[][] pvSwpt = new double[2][2];
          double[] pvSwap = new double[2];
          for (int looppr = 0; looppr < 2; looppr++) {
            for (int loopls = 0; loopls < 2; loopls++) {
              SwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M.createTrade(VALUATION_DATE, EXPIRIES_PER[i], 
                  Tenor.of(TENORS_PER[j]), (looppr == 0) ? BuySell.BUY : BuySell.SELL, NOTIONAL, 0, REF_DATA);
              ResolvedSwap swap0Resolved = swap0.getProduct().resolve(REF_DATA);
              double parRate = PRICER_SWAP.parRate(swap0Resolved, MULTICURVE_EUR);
              LocalDate expiryDate = EUR_EURIBOR_6M.calculateFixingFromEffective(swap0Resolved.getStartDate(), REF_DATA);
              Swap swap = EUR_FIXED_1Y_EURIBOR_6M.createTrade(VALUATION_DATE,
                  EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), (looppr == 0) ? BuySell.BUY : BuySell.SELL,
                  NOTIONAL, parRate + MONEYNESS[k], REF_DATA).getProduct();
              ResolvedSwaption swpt = Swaption.builder()
                  .longShort((loopls == 0) ? LongShort.LONG : LongShort.SHORT)
                  .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
                  .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
                  .underlying(swap).build().resolve(REF_DATA);
              pvSwpt[looppr][loopls] =
                  PRICER_SWAPTION_RATIONAL_EXPLICIT.presentValue(swpt, MULTICURVE_EUR, MODEL_SIMPLE).getAmount();
              pvSwap[looppr] = 
                  PRICER_SWAP.presentValue(swap.resolve(REF_DATA), MULTICURVE_EUR).getAmount(EUR).getAmount();
            }
            assertEquals(pvSwpt[looppr][0], -pvSwpt[looppr][1], TOLERANCE_PV_EXPL);  // Long/Short parity
          }
          assertEquals(pvSwpt[1][0] - pvSwpt[0][0], pvSwap[1], TOLERANCE_PV_EXPL);  
          // Receiver swaption - Payer swaption = Receiver swap
        }
      }
    }
  }

}
