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
import com.opengamma.strata.math.impl.cern.MersenneTwister64;
import com.opengamma.strata.math.impl.cern.RandomEngine;
import com.opengamma.strata.math.impl.random.NormalRandomNumberGenerator;
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
import marc.henrard.murisq.pricer.montecarlo.MonteCarloDataSet;

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
  
  private static final ImmutableRatesProvider MULTICURVE_EUR = MonteCarloDataSet.MULTICURVE_EUR;
  
  /* LMM parameters (HW-like) */
  private static final double MEAN_REVERTION = 0.02;
  private static final double HW_SIGMA = 0.01;
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
  private static final LiborMarketModelMonteCarloEvolution LMM_EVOLUTION =
      LiborMarketModelMonteCarloEvolution.DEFAULT;
  
  /* Instrument description */
  private static final double NOTIONAL = 1_000_000.0d;
  private static final List<ValueSchedule> COEFFICIENTS = new ArrayList<>();
  static { // Start with IBOR then 50% previous + 50% current IBOR , floor at 1%, cap at 2*IBOR
    COEFFICIENTS.add(ValueSchedule.of(0, ImmutableList.of(ValueStep.of(1, ValueAdjustment.ofDeltaAmount(0.50))))); // main previous
    COEFFICIENTS.add(ValueSchedule.of(1.0, ImmutableList.of(ValueStep.of(1, ValueAdjustment.ofDeltaAmount(-0.50))))); // main Ibor
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // main fixed
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor previous
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor Ibor
    COEFFICIENTS.add(ValueSchedule.of(0.01)); // floor fixed
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

  /* Test Monte Carlo comparing Ibor leg with ratchet using trivial coefficients.
   * LMM model with Hull-White-like parameters */
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
    LmmdddRatchetProductMonteCarloPricer pricerLmmMc =
        LmmdddRatchetProductMonteCarloPricer.builder()
            .model(lmmHw)
            .evolution(LMM_EVOLUTION)
            .nbPaths(nbPaths)
            .numberGenerator(rnd)
            .pathNumberBlock(10_000).build();
    double pvDsc = PRICER_LEG.presentValue(iborLeg, MULTICURVE_EUR).getAmount(); // 16499.39173511338
    double pvMc = pricerLmmMc.presentValueDouble(ResolvedSwap.of(ratchet), MULTICURVE_EUR, lmmHw);
    // pvDsc: 16499.39173511338
    // Paths/pv: 1,000/17874.34 (99ms); 10,000/17478.65 (281ms) ; 100,000/16463.94 (1516ms)
    //           1,000,000/16421.27 (10491ms)
//    end = System.currentTimeMillis();
//    System.out.println(pvDsc);
//    System.out.println(pvMc);
//    System.out.println("Computation time: " + (end-start) + " ms.");
    double pvPreviousRun = 17874.335430266827; // 1,000 paths
    assertThat(pvMc).isEqualTo(pvPreviousRun, TOLERANCE_MC_1);
    Offset<Double> toleranceMc = within(1.5E+4);
    assertThat(pvMc).isEqualTo(pvDsc, toleranceMc);
  }

  /* Test Monte Carlo comparing cap with ratchet using cap coefficients.
   * LMM model with Hull-White-like parameters */
  @Test
  public void hw_like_ratchet_cap() {
//    long start, end;
//    start = System.currentTimeMillis();
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
        LmmdddUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_3M, 
            ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
            VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LmmdddRatchetProductMonteCarloPricer pricerLmmMc =
        LmmdddRatchetProductMonteCarloPricer.builder()
            .model(lmmHw)
            .evolution(LMM_EVOLUTION)
            .nbPaths(nbPaths)
            .numberGenerator(rnd)
            .pathNumberBlock(10_000).build();
    double pvCapHw = PRICER_CAP_LEG_HW.presentValue(cap, MULTICURVE_EUR, HW_PROVIDER).getAmount(); // 7874.5413972761
    double pvIborLeg = PRICER_LEG.presentValue(iborLeg, MULTICURVE_EUR).getAmount(); // 16499.39173511338
    double pvCappedLeg = pvIborLeg - pvCapHw;
    double pvMc = pricerLmmMc.presentValueDouble(ResolvedSwap.of(ratchet), MULTICURVE_EUR, lmmHw);
    // pvCappedLeg: 8624.850337837279
    // Paths/pv: 1,000/9532.05 (129ms); 10,000/9128.21 (319ms) ; 100,000/8515.52 (1512ms)
    //           1,000,000/8455.73 (7919ms)
//    System.out.println(pvCappedLeg);
//    System.out.println(pvMc);
//    end = System.currentTimeMillis();
//    System.out.println("Computation time: " + (end - start) + " ms.");
    double pvPreviousRun = 9532.0460; //
    assertThat(pvMc).isEqualTo(pvPreviousRun, TOLERANCE_MC_1);
    Offset<Double> toleranceMc = within(1.0E+4);
    assertThat(pvMc).isEqualTo(pvCappedLeg, toleranceMc);
  }
  
  
  /* Test Monte Carlo vs previous run. */
  @Test
  public void hw_like_ratchet() {
    ResolvedSwapLeg ratchet = createRatchetSwapLeg(COEFFICIENTS);
    int nbPaths = 1_000;
    List<LocalDate> iborDates = new ArrayList<>();
    ImmutableList<SwapPaymentPeriod> ratchetPayments = ratchet.getPaymentPeriods();
    iborDates.add(ratchetPayments.get(0).getStartDate());
    for (SwapPaymentPeriod period : ratchetPayments) {
      iborDates.add(period.getEndDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw =
        LmmdddUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT,
            MULTICURVE_EUR, VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    RandomEngine engine = new MersenneTwister64(0);
    NormalRandomNumberGenerator rnd = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
    LmmdddRatchetProductMonteCarloPricer pricerLmmMc =
        LmmdddRatchetProductMonteCarloPricer.builder()
            .model(lmmHw)
            .evolution(LMM_EVOLUTION)
            .nbPaths(nbPaths)
            .numberGenerator(rnd)
            .pathNumberBlock(10_000).build();
    double pvMc = pricerLmmMc.presentValueDouble(ResolvedSwap.of(ratchet), MULTICURVE_EUR, lmmHw);
    double pvMcPreviousRun = 7590.671659282815;
    // Paths/pv: 1,000/7590.67 ; 10,000/7299.39 ; 100,000/6039.52
    //           1,000,000/6008.84
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
