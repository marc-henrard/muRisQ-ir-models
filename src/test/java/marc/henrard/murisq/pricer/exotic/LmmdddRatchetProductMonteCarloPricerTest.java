/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.exotic;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueAdjustment;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.basics.value.ValueStep;
import com.opengamma.strata.collect.array.DoubleArray;
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
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.product.capfloor.IborCapFloorLeg;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorLeg;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.CompoundingMethod;
import com.opengamma.strata.product.swap.IborRatchetRateCalculation;
import com.opengamma.strata.product.swap.IborRateCalculation;
import com.opengamma.strata.product.swap.NotionalSchedule;
import com.opengamma.strata.product.swap.PaymentSchedule;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLeg;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.model.lmm.LmmdddUtils;
import marc.henrard.murisq.pricer.capfloor.HullWhiteCapFloorLegPricer;

/**
 * Test {@link LmmdddRatchetProductMonteCarloPricer}.
 * 
 * @author Marc Henrard
 */
public class LmmdddRatchetProductMonteCarloPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  
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
  private static final double VOL2_LEVEL_1 = 0.06;
  private static final double VOL2_ANGLE = Math.PI * 0.5;
  private static final double VOL2_LEVEL_A = 0.04;
  private static final double DISPLACEMENT = 0.10; // 10% rate displacement
  private static final DayCount HW_DAYCOUNT = DayCounts.ACT_365F;
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERTION, DoubleArray.of(HW_SIGMA), DoubleArray.of());
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider
      .of(HW_PARAMETERS, HW_DAYCOUNT, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  
  /* Pricer */
  private static final DiscountingSwapLegPricer PRICER_LEG =
      DiscountingSwapLegPricer.DEFAULT;
  private static final HullWhiteCapFloorLegPricer PRICER_CAP_LEG_HW =
      HullWhiteCapFloorLegPricer.DEFAULT;
  
  /* Instrument description */
  private static final double NOTIONAL = 1_000_000.0d;
  private static final List<ValueSchedule> COEFFICIENTS = new ArrayList<>();
  static { // Start with IBOR then 50% previous + 50% current IBOR , floor at 1%, cap at 2*IBOR
    COEFFICIENTS.add(ValueSchedule.of(0, ImmutableList.of(ValueStep.of(1, ValueAdjustment.ofDeltaAmount(0.50))))); // main previous
    COEFFICIENTS.add(ValueSchedule.of(1.0, ImmutableList.of(ValueStep.of(1, ValueAdjustment.ofDeltaAmount(-0.50))))); // main Ibor
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // main fixed
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor previous
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor Ibor
    COEFFICIENTS.add(ValueSchedule.of(0.00)); // floor fixed
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap previous
    COEFFICIENTS.add(ValueSchedule.of(2.0)); // cap Ibor
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap fixed.
  }
  private static final double CAP_STRIKE = 0.02;
  private static final List<ValueSchedule> COEFFICIENTS_CAP = new ArrayList<>();
  static { // Current IBOR with a cap at 2% and no floor (floor set at -100%)
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_0); // main previous
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_1); // main Ibor             = Ibor
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_0); // main fixed
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_0); // floor previous
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_0); // floor Ibor
    COEFFICIENTS_CAP.add(ValueSchedule.of(-1.0)); // floor fixed             no floor
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_0); // cap previous
    COEFFICIENTS_CAP.add(ValueSchedule.ALWAYS_0); // cap Ibor
    COEFFICIENTS_CAP.add(ValueSchedule.of(CAP_STRIKE)); // cap fixed.        with cap at 2%
  }
  private static final List<ValueSchedule> COEFFICIENTS_IBOR = new ArrayList<>();
  static { // Current IBOR no floor, no cap (set at -100% and 100%)
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_0); // main previous
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_1); // main Ibor             = Ibor
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_0); // main fixed
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_0); // floor previous
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_0); // floor Ibor
    COEFFICIENTS_IBOR.add(ValueSchedule.of(-1.0)); // floor fixed             no floor (100%)
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_0); // cap previous
    COEFFICIENTS_IBOR.add(ValueSchedule.ALWAYS_0); // cap Ibor
    COEFFICIENTS_IBOR.add(ValueSchedule.of(1.0)); // cap fixed.               no cap (100%)
  }
  private static final LocalDate START_DATE = LocalDate.of(2020, 2, 28);
  private static final LocalDate END_DATE = LocalDate.of(2022, 2, 28);
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_MC_1 = within(1.0E-1);
  private static final Offset<Double> TOLERANCE_MC_2 = within(2.0E+3);

  /* Test Monte Carlo comparing Ibor leg with ratchet using trivial coefficients. */
  @Test
  public void hw_like_ratchet_ibor() {
//    long start, end;
//    start = System.currentTimeMillis();
    ResolvedSwapLeg ratchet = createRatchetSwapLeg(COEFFICIENTS_IBOR);
    ResolvedSwapLeg iborLeg = EUR_FIXED_1Y_EURIBOR_3M.getFloatingLeg()
        .toLeg(START_DATE, END_DATE, PayReceive.RECEIVE, NOTIONAL).resolve(REF_DATA);
    int nbPaths = 1_000;
    List<LocalDate> iborDates = new ArrayList<>();
    ImmutableList<SwapPaymentPeriod> ratchetPayments = ratchet.getPaymentPeriods();
    iborDates.add(ratchetPayments.get(0).getStartDate());
    for(SwapPaymentPeriod period: ratchetPayments) {
      iborDates.add(period.getEndDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = LmmdddUtils.
        lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
            VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LiborMarketModelMonteCarloEvolution evolution =
        LiborMarketModelMonteCarloEvolution.of(1.0d);
    LmmdddRatchetProductMonteCarloPricer pricerLmmMc =
        LmmdddRatchetProductMonteCarloPricer.builder()
            .model(lmmHw)
            .evolution(evolution)
            .nbPaths(nbPaths)
            .numberGenerator(rnd)
            .pathNumberBlock(10_000).build();
    double pvIborLeg = PRICER_LEG.presentValue(iborLeg, MULTICURVE_EUR).getAmount(); // 16499.39173511338
    double pvMc = pricerLmmMc.presentValueDouble(ResolvedSwap.of(ratchet), MULTICURVE_EUR, lmmHw);
    // Previous run: 1_000 paths: 17874.335430266827 (84 ms) / 10_000 paths: 17478.65182316717 (284 ms) 
    //   1_000_000 paths: 16421.2687672823 (8292 ms) / 5_000_000 paths: 16418.277071786775 (35660 ms)
//    end = System.currentTimeMillis();
//    System.out.println(pvIborLeg);
//    System.out.println(pvMc);
//    System.out.println("Computation time: " + (end-start) + " ms.");
    assertThat(pvMc).isEqualTo(pvIborLeg, TOLERANCE_MC_2);
    double pvPreviousRun = 17874.335430266827;
    assertThat(pvMc).isEqualTo(pvPreviousRun, TOLERANCE_MC_1);
  }

  /* Test Monte Carlo comparing cap with ratchet using cap coefficients. */
  @Test
  public void hw_like_ratchet_cap() {
    long start, end;
    start = System.currentTimeMillis();
    ResolvedSwapLeg ratchet = createRatchetSwapLeg(COEFFICIENTS_CAP);
    ResolvedIborCapFloorLeg cap = capFloor(CAP_STRIKE);
    ResolvedSwapLeg iborLeg = EUR_FIXED_1Y_EURIBOR_3M.getFloatingLeg()
        .toLeg(START_DATE, END_DATE, PayReceive.RECEIVE, NOTIONAL).resolve(REF_DATA);
    int nbPaths = 1_000;
    List<LocalDate> iborDates = new ArrayList<>();
    ImmutableList<SwapPaymentPeriod> ratchetPayments = ratchet.getPaymentPeriods();
    iborDates.add(ratchetPayments.get(0).getStartDate());
    for (SwapPaymentPeriod period : ratchetPayments) {
      iborDates.add(period.getEndDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw =
        LmmdddUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
            VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LiborMarketModelMonteCarloEvolution evolution =
        LiborMarketModelMonteCarloEvolution.of(1.0d);
    LmmdddRatchetProductMonteCarloPricer pricerLmmMc =
        LmmdddRatchetProductMonteCarloPricer.builder()
            .model(lmmHw)
            .evolution(evolution)
            .nbPaths(nbPaths)
            .numberGenerator(rnd)
            .pathNumberBlock(10_000).build();
    double pvCapHw = PRICER_CAP_LEG_HW.presentValue(cap, MULTICURVE_EUR, HW_PROVIDER).getAmount(); // 7874.5413972761
    System.out.println(pvCapHw);
    double pvIborLeg = PRICER_LEG.presentValue(iborLeg, MULTICURVE_EUR).getAmount(); // 16499.39173511338
    System.out.println(pvIborLeg);
    double pvMc = pricerLmmMc.presentValueDouble(ResolvedSwap.of(ratchet), MULTICURVE_EUR, lmmHw);
    // Previous run: 1_000 paths: 9532.046023651626 (68 ms) / 10_000 paths: 9128.209124585674 (287 ms) 
    //   1_000_000 paths: 8455.730716265343 (8628 ms) / 5_000_000 paths: 8460.653691070165 (35660 ms)
    System.out.println(pvMc);
    end = System.currentTimeMillis();
    System.out.println("Computation time: " + (end - start) + " ms.");
    assertThat(pvMc).isEqualTo(pvIborLeg - pvCapHw, TOLERANCE_MC_2); // 8624.850337837279
    double pvPreviousRun = 9532.046023651626;
    assertThat(pvMc).isEqualTo(pvPreviousRun, TOLERANCE_MC_1);
  }
  
  
  /* Test Monte Carlo vs previous run. */
  @Test
  public void hw_like_ratchet() {
    ResolvedSwapLeg ratchet = createRatchetSwapLeg(COEFFICIENTS);
    int nbPaths = 3;
    List<LocalDate> iborDates = new ArrayList<>();
    ImmutableList<SwapPaymentPeriod> ratchetPayments = ratchet.getPaymentPeriods();
    iborDates.add(ratchetPayments.get(0).getStartDate());
    for(SwapPaymentPeriod period: ratchetPayments) {
      iborDates.add(period.getEndDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = LmmdddUtils.
        lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
            VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LiborMarketModelMonteCarloEvolution evolution =
        LiborMarketModelMonteCarloEvolution.of(1.0d);
    LmmdddRatchetProductMonteCarloPricer pricerLmmMc =
        LmmdddRatchetProductMonteCarloPricer.builder()
        .model(lmmHw)
        .evolution(evolution)
        .nbPaths(nbPaths)
        .numberGenerator(rnd)
        .pathNumberBlock(10_000).build();
    double pvMc = pricerLmmMc.presentValueDouble(ResolvedSwap.of(ratchet), MULTICURVE_EUR, lmmHw);
    double pvMcPreviousRun = 20445.6449739844; // 1,000 paths: 20445.6449739844
    // 1,000,000 paths: 20445.644973785333 // 2,000,000 paths: 
    System.out.println(pvMc);
    assertThat(pvMc).isEqualTo(pvMcPreviousRun, TOLERANCE_MC_1);
  }
  
  private ResolvedIborCapFloorLeg capFloor(double strike) {
    IborRateCalculation iborCal = IborRateCalculation.of(EUR_EURIBOR_3M);
    PeriodicSchedule schedule = PeriodicSchedule.of(
        START_DATE,
        END_DATE,
        Frequency.P3M,
        BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA),
        StubConvention.NONE,
        RollConventions.NONE);
    return IborCapFloorLeg.builder()
        .calculation(iborCal)
        .capSchedule(ValueSchedule.of(strike))
        .currency(EUR)
        .notional(ValueSchedule.of(NOTIONAL))
        .paymentSchedule(schedule)
        .payReceive(PayReceive.RECEIVE).build().resolve(REF_DATA);
  }
      
  public ResolvedSwapLeg createRatchetSwapLeg(List<ValueSchedule> coefficients) {
    double notional = 1_000_000;
    IborRatchetRateCalculation ratchetCalculation = 
        IborRatchetRateCalculation.of(EUR_EURIBOR_3M, coefficients);
    Frequency frequency = Frequency.P3M;
    BusinessDayAdjustment businessDayAdjustment = BusinessDayAdjustment
        .of(BusinessDayConventions.MODIFIED_FOLLOWING, HolidayCalendarIds.EUTA);
    PeriodicSchedule accrualSchedule = PeriodicSchedule
        .of(START_DATE, END_DATE, frequency, businessDayAdjustment, StubConvention.NONE, RollConventions.NONE);
    SwapLeg leg = RateCalculationSwapLeg
        .builder()
        .payReceive(PayReceive.RECEIVE)
        .accrualSchedule(accrualSchedule)
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(frequency)
            .paymentDateOffset(DaysAdjustment.NONE)
            .compoundingMethod(CompoundingMethod.NONE)
            .build())
        .notionalSchedule(NotionalSchedule.builder()
            .currency(EUR_EURIBOR_3M.getCurrency())
            .finalExchange(false)
            .initialExchange(false)
            .amount(ValueSchedule.of(notional)).build())
        .calculation(ratchetCalculation)
        .build();
    return leg.resolve(REF_DATA);
  }

}
