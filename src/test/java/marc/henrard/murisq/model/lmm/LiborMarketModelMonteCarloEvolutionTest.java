/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

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
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.math.impl.cern.MersenneTwister64;
import com.opengamma.strata.math.impl.cern.RandomEngine;
import com.opengamma.strata.math.impl.random.NormalRandomNumberGenerator;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.IborRateCalculation;
import com.opengamma.strata.product.swap.NotionalSchedule;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductMonteCarloPricer;

/**
 * Test {@link LiborMarketModelMonteCarloEvolution}.
 * 
 * @author Marc Henrard
 */
public class LiborMarketModelMonteCarloEvolutionTest {

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
  private static final double VOL2_LEVEL_1 = 0.02;
  private static final double VOL2_ANGLE = Math.PI * 0.5;
  private static final double VOL2_LEVEL_A = 0.08;
  private static final double DISPLACEMENT = 0.10; // 10% rate displacement
  
  /* Pricer */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  
  /* Instrument description */
  private static final double NOTIONAL = 1_000_000.0d;
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_MC_1 = within(1.0E-1);
  private static final Offset<Double> TOLERANCE_MC_2 = within(1.0E+5);
  private static final boolean PRINT_DETAILS = false;
  
  /* Test Monte Carlo with single fixed cash-flow. */
  @Test
  public void hw_like_european_1cf() {
    int nbPaths = 100; // one cf: independent of number of paths
    Period expiry = Period.ofMonths(60);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry));
    Tenor tenor = Tenor.TENOR_1Y;
    SwapTrade swap1 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.SELL, NOTIONAL, 1.0d, REF_DATA);
    // Ibor leg with 0 notional
    RateCalculationSwapLeg iborLeg = (RateCalculationSwapLeg) swap1.getProduct().getLegs().get(1);
    iborLeg = iborLeg.toBuilder().notionalSchedule(NotionalSchedule.of(iborLeg.getCurrency(), 0)).build(); 
    Swap swap2 = Swap.of(swap1.getProduct().getLegs().get(0), iborLeg);
    MultiCurrencyAmount pvDsc = PRICER_SWAP.presentValue(swap2.resolve(REF_DATA), MULTICURVE_EUR);
    double pvMc = hw_like_european(expiryDate, swap2, nbPaths);
    assertThat(pvMc).isEqualTo(pvDsc.getAmount(EUR_FIXED_1Y_EURIBOR_3M.getFixedLeg().getCurrency()).getAmount(), TOLERANCE_MC_1);
  }
  
  /* Test Monte Carlo with multiple fixed cash-flow. */
  @Test
  public void hw_like_european_mcf() {
    int nbPaths = 1_000;
    Period expiry = Period.ofMonths(60);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry));
    Tenor tenor = Tenor.TENOR_10Y;
    SwapTrade swap1 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.SELL, NOTIONAL, 1.0d, REF_DATA);
    // Ibor leg with 0 notional
    RateCalculationSwapLeg iborLeg = (RateCalculationSwapLeg) swap1.getProduct().getLegs().get(1);
    iborLeg = iborLeg.toBuilder().notionalSchedule(NotionalSchedule.of(iborLeg.getCurrency(), 0)).build(); 
    Swap swap2 = Swap.of(swap1.getProduct().getLegs().get(0), iborLeg);
    
    MultiCurrencyAmount pvDsc = PRICER_SWAP.presentValue(swap2.resolve(REF_DATA), MULTICURVE_EUR);
    double pvMc = hw_like_european(expiryDate, swap2, nbPaths);
    double pvMcPreviousRun = 9326349.14320495; 
    // pvDsc: 9300123.148441346
    // Paths/pv: 1,000/9326349.14320495 ; 10,000/9322005.10370701 ; 100,000/9298060.25406535
    //           1,000,000/9299729.974091204
    assertThat(pvMc).isEqualTo(pvMcPreviousRun, TOLERANCE_MC_1);
    Offset<Double> toleranceMc = within(3.0E+4);
    assertThat(pvMc).isEqualTo(pvDsc.getAmount(EUR_FIXED_1Y_EURIBOR_3M.getFixedLeg().getCurrency()).getAmount(), toleranceMc);
  }
  
  /* Test Monte Carlo with multiple Ibor cash-flow. */
  @Test
  public void hw_like_european_mcIbor() {
    int nbPaths = 1_000;
    Period expiry = Period.ofMonths(60);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry));
    Tenor tenor = Tenor.TENOR_10Y;
    SwapTrade swap1 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA); // Fixed leg with 0 rate
    // Add large spread
    RateCalculationSwapLeg iborLeg = (RateCalculationSwapLeg) swap1.getProduct().getLegs().get(1);
    IborRateCalculation iborCalculation = (IborRateCalculation) iborLeg.getCalculation();
    iborLeg = iborLeg.toBuilder()
        .calculation(iborCalculation.toBuilder().spread(ValueSchedule.of(0.10d)).build()).build();
    Swap swap2 = Swap.of(swap1.getProduct().getLegs().get(0), iborLeg);
    
    MultiCurrencyAmount pvDsc = PRICER_SWAP.presentValue(swap2.resolve(REF_DATA), MULTICURVE_EUR);
    double pvMc = hw_like_european(expiryDate, swap2, nbPaths);
    double pvMcPreviousRun = 1119112.8970316479;
    // pvDsc: 1110607.0301221774
    // Paths/pv: 1,000 paths/1119112.8970316479 ; 10,000/1117528.5511773005 ; 100,000/1109113.5727566544
    //           1,000,000/1109659.369704706 ; 10,000,000/ 1109795.0610369984
    assertThat(pvMc).isEqualTo(pvMcPreviousRun, TOLERANCE_MC_1);
    Offset<Double> toleranceMc = within(1.0E+4);
    assertThat(pvMc).isEqualTo(pvDsc.getAmount(EUR_FIXED_1Y_EURIBOR_3M.getFixedLeg().getCurrency()).getAmount(), toleranceMc);
  }
  
  /**
   * Test MC with for a given set of fixed or IBOR cash flows.
   * 
   * @param expiryDate  the expiry date for the simulation
   * @param swap  the swap (fixed cash flows only)
   * @param nbPath  the number of path for the simulation
   */
  private double hw_like_european(LocalDate expiryDate, Swap swap, int nbPath) {
    List<LocalDate> iborDates = new ArrayList<>();
    ImmutableList<SwapPaymentPeriod> iborLegPayments = 
        swap.resolve(REF_DATA).getLegs().get(1).getPaymentPeriods();
    iborDates.add(iborLegPayments.get(0).getStartDate());
    for(SwapPaymentPeriod period: iborLegPayments) {
      iborDates.add(period.getEndDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = LmmdddExamplesUtils.
        lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
            VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LiborMarketModelMonteCarloEvolution evolution =
        LiborMarketModelMonteCarloEvolution.of(1.0d);
    LmmdddSwaptionPhysicalProductMonteCarloPricer priceSwaptionLmmMc =
        LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
        .model(lmmHw)
        .evolution(evolution)
        .nbPaths(nbPath)
        .numberGenerator(rnd)
        .pathNumberBlock(1_000).build();
    Swaption swaption = Swaption.builder()
        .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
        .longShort(LongShort.LONG)
        .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
        .underlying(swap).build();
    double pvMc = priceSwaptionLmmMc.presentValueDouble(swaption.resolve(REF_DATA), MULTICURVE_EUR);
    return pvMc;
  }
  
  /* Test Monte Carlo with single fixed cash-flow. */
  @Test
  public void lmm_angle2_european_1cf() {
    int nbPaths = 100; // one cf: independent of number of paths
    Period expiry = Period.ofMonths(60);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry));
    Tenor tenor = Tenor.TENOR_1Y;
    SwapTrade swap1 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.SELL, NOTIONAL, 1.0d, REF_DATA);
    // Ibor leg with 0 notional
    RateCalculationSwapLeg iborLeg = (RateCalculationSwapLeg) swap1.getProduct().getLegs().get(1);
    iborLeg = iborLeg.toBuilder().notionalSchedule(NotionalSchedule.of(iborLeg.getCurrency(), 0)).build(); 
    Swap swap2 = Swap.of(swap1.getProduct().getLegs().get(0), iborLeg);
    MultiCurrencyAmount pvDsc = PRICER_SWAP.presentValue(swap2.resolve(REF_DATA), MULTICURVE_EUR);
    double pvMc = lmm_angle2_european(expiryDate, swap2, nbPaths);
//    System.out.println(pvDsc + "," + pvMc);
    assertThat(pvMc).isEqualTo(pvDsc.getAmount(EUR_FIXED_1Y_EURIBOR_3M.getFixedLeg().getCurrency()).getAmount(), TOLERANCE_MC_1);
  }
  
  /* Test Monte Carlo with multiple fixed cash-flow. */
  @Test
  public void lmm_angle2_european_mcf() {
    int nbPaths = 1_000;
    Period expiry = Period.ofMonths(60);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry));
    Tenor tenor = Tenor.TENOR_10Y;
    SwapTrade swap1 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.SELL, NOTIONAL, 1.0d, REF_DATA);
    // Ibor leg with 0 notional
    RateCalculationSwapLeg iborLeg = (RateCalculationSwapLeg) swap1.getProduct().getLegs().get(1);
    iborLeg = iborLeg.toBuilder().notionalSchedule(NotionalSchedule.of(iborLeg.getCurrency(), 0)).build(); 
    Swap swap2 = Swap.of(swap1.getProduct().getLegs().get(0), iborLeg);
    
    MultiCurrencyAmount pvDsc = PRICER_SWAP.presentValue(swap2.resolve(REF_DATA), MULTICURVE_EUR);
    double pvMc = lmm_angle2_european(expiryDate, swap2, nbPaths);
    if (PRINT_DETAILS) {
      System.out.println(pvDsc + "," + pvMc);
    }
    double pvMcPreviousRun = 9361333.3796; // 1,000 paths: 9361333.3796
    // Dsc: 9300123.1484 - MC 1,000,000 paths: 9300506.8394 
    assertThat(pvMc).isEqualTo(pvMcPreviousRun, TOLERANCE_MC_1);
    assertThat(pvMc).isEqualTo(pvDsc.getAmount(EUR_FIXED_1Y_EURIBOR_3M.getFixedLeg().getCurrency()).getAmount(), TOLERANCE_MC_2);
  }
  
  /* Test Monte Carlo with multiple Ibor cash-flow. */
  @Test
  public void lmm_angle2_european_mcIbor() {
    int nbPaths = 1_000;
    Period expiry = Period.ofMonths(60);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry));
    Tenor tenor = Tenor.TENOR_10Y;
    SwapTrade swap1 = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(expiryDate, tenor, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA); // Fixed leg with 0 rate
    // Add large spread
    RateCalculationSwapLeg iborLeg = (RateCalculationSwapLeg) swap1.getProduct().getLegs().get(1);
    IborRateCalculation iborCalculation = (IborRateCalculation) iborLeg.getCalculation();
    iborLeg = iborLeg.toBuilder()
        .calculation(iborCalculation.toBuilder().spread(ValueSchedule.of(0.10d)).build()).build();
    Swap swap2 = Swap.of(swap1.getProduct().getLegs().get(0), iborLeg);
    MultiCurrencyAmount pvDsc = PRICER_SWAP.presentValue(swap2.resolve(REF_DATA), MULTICURVE_EUR);
    double pvMc = lmm_angle2_european(expiryDate, swap2, nbPaths);
    if (PRINT_DETAILS) {
      System.out.println(pvDsc + "," + pvMc);
    }
    double pvMcPreviousRun = 1129934.2599; // 1,000 paths: 1129934.2599
    // Dsc: 1110607.0301 - MC 1,000,000 paths: 1109913.19897611
    assertThat(pvMc).isEqualTo(pvMcPreviousRun, TOLERANCE_MC_1);
    Offset<Double> toleranceMc = within(2.5E+4);
    assertThat(pvMc)
        .isEqualTo(pvDsc.getAmount(EUR_FIXED_1Y_EURIBOR_3M.getFixedLeg().getCurrency()).getAmount(), toleranceMc);
  }
  
  /**
   * Test MC with for a given set of fixed or IBOR cash flows.
   * 
   * @param expiryDate  the expiry date for the simulation
   * @param swap  the swap (fixed cash flows only)
   * @param nbPath  the number of path for the simulation
   */
  private double lmm_angle2_european(LocalDate expiryDate, Swap swap, int nbPath) {
    List<LocalDate> iborDates = new ArrayList<>();
    ImmutableList<SwapPaymentPeriod> iborLegPayments = 
        swap.resolve(REF_DATA).getLegs().get(1).getPaymentPeriods();
    iborDates.add(iborLegPayments.get(0).getStartDate());
    for(SwapPaymentPeriod period: iborLegPayments) {
      iborDates.add(period.getEndDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmAngle = LmmdddExamplesUtils.
        lmm2Angle(MEAN_REVERTION, VOL2_LEVEL_1, VOL2_ANGLE, VOL2_LEVEL_A, DISPLACEMENT,
            iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
            VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LiborMarketModelMonteCarloEvolution evolution =
        LiborMarketModelMonteCarloEvolution.of(1.0d);
    LmmdddSwaptionPhysicalProductMonteCarloPricer priceSwaptionLmmMc =
        LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
        .model(lmmAngle)
        .evolution(evolution)
        .nbPaths(nbPath)
        .numberGenerator(rnd)
        .pathNumberBlock(10_000).build();
    Swaption swaption = Swaption.builder()
        .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
        .longShort(LongShort.LONG)
        .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
        .underlying(swap).build();
    double pvMc = priceSwaptionLmmMc.presentValueDouble(swaption.resolve(REF_DATA), MULTICURVE_EUR);
    return pvMc;
  }

}
