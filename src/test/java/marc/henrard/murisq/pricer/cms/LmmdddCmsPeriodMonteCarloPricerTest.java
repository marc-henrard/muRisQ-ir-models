/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.cms;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
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

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.cern.MersenneTwister64;
import com.opengamma.strata.math.impl.cern.RandomEngine;
import com.opengamma.strata.math.impl.random.NormalRandomNumberGenerator;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.cms.CmsPeriod;
import com.opengamma.strata.product.cms.CmsPeriodType;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapIndex;
import com.opengamma.strata.product.swap.SwapIndices;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.model.lmm.LmmdddExamplesUtils;
import marc.henrard.murisq.pricer.decomposition.MulticurveDecisionScheduleCalculator;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;
import marc.henrard.murisq.product.cms.CmsPeriodResolved;

/**
 * Tests {@link LmmdddCmsPeriodMonteCarloPricer} and partly
 * {@link LiborMarketModelMonteCarloEvolution}.
 * 
 * @author Marc Henrard
 */
public class LmmdddCmsPeriodMonteCarloPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_6M.getFixingCalendar());

  /* Pricer */
  private static final HullWhiteCmsPeriodExplicitPricer PRICER_CMS_HW =
      HullWhiteCmsPeriodExplicitPricer.DEFAULT;

  /* Market Data */
  private static final ImmutableRatesProvider MULTICURVE_EUR = MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  
  /* Descriptions of caplets/floorlets */
  private static final CmsPeriodType[] TYPES = 
      new CmsPeriodType[] {CmsPeriodType.COUPON, CmsPeriodType.CAPLET, CmsPeriodType.FLOORLET};
  private static final SwapIndex[] INDICES = 
      new SwapIndex[] {SwapIndices.EUR_EURIBOR_1100_2Y, SwapIndices.EUR_EURIBOR_1100_10Y};
  private static final Period[] EXPIRIES = new Period[] {Period.ofYears(5), Period.ofYears(20)};
  private static final Period[] PAYMENT_LAG = new Period[] {Period.ZERO, Period.ofMonths(12)};
  private static final double[] STRIKES = new double[] {0.0050, 0.0250};
  private static final int NB_INDICES = INDICES.length;
  private static final int NB_EXPIRIES = EXPIRIES.length;
  private static final int NB_PAY_LAG = PAYMENT_LAG.length;
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;
  private static final CmsPeriod[][][][][] CMS_PERIODS = 
      new CmsPeriod[3][INDICES.length][EXPIRIES.length][PAYMENT_LAG.length][STRIKES.length];
  static {
    FixedIborSwapConvention convention = FixedIborSwapConventions.EUR_FIXED_1Y_LIBOR_6M;
    for (int looptype = 0; looptype < 3; looptype++) {
      for (int loopindex = 0; loopindex < NB_INDICES; loopindex++) {
        for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
          for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
            for (int loopstrike = 0; loopstrike < NB_STRIKES; loopstrike++) {
              LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
              LocalDate startDate = convention.calculateSpotDateFromTradeDate(fixingDate, REF_DATA);
              LocalDate endDate = EUTA_IMPL.nextOrSame(startDate.plusMonths(6));
              LocalDate paymentDate = EUTA_IMPL.nextOrSame(startDate.plus(PAYMENT_LAG[looplag]));
              ResolvedSwap underlyingSwap =
                  INDICES[loopindex].getTemplate().createTrade(fixingDate, BuySell.BUY, 1.0d, 1.0d, REF_DATA)
                      .resolve(REF_DATA)
                      .getProduct();
              CMS_PERIODS[looptype][loopindex][loopexp][looplag][loopstrike] =
                  cmsPeriod(fixingDate, startDate, endDate, paymentDate, INDICES[loopindex], underlyingSwap,
                      TYPES[looptype], STRIKES[loopstrike]);
            }
          }
        }
      }
    }
  }
  
  /* Model data */
  private static final double MEAN_REVERTION = 0.02;
  private static final double HW_SIGMA = 0.01;
  
  /* Monte carlo */
  private static final int NBPATHS = 10000; // Used only for performance analysis
  private static final int PATHSPERBLOCK = 1000;
  private static final RandomEngine ENGINE = new MersenneTwister64(0);
  private static final NormalRandomNumberGenerator RND = 
      new NormalRandomNumberGenerator(0.0d, 1.0d, ENGINE);
  private static final LiborMarketModelMonteCarloEvolution EVOLUTION =
      LiborMarketModelMonteCarloEvolution.DEFAULT;

  
  private static final Offset<Double> TOLERANCE_DF = within(1.0E-12);
  private static final Offset<Double> TOLERANCE_RATE = within(1.0E-12);
  private static final Offset<Double> TOLERANCE_PV_EXACT = within(1.0E-12);
  private static final boolean PRINT_DETAILS = false;
  
  /* Multi-curve equivalent, only one case */
  @Test
  public void multicurveEquivalent() {
    CmsPeriod period = CMS_PERIODS[0][1][1][1][1];
    ResolvedSwap swap = period.getUnderlyingSwap();
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
    LmmdddCmsPeriodMonteCarloPricer pricer = 
      LmmdddCmsPeriodMonteCarloPricer.builder()
      .evolution(EVOLUTION)
      .model(lmmHw)
      .numberGenerator(RND)
      .nbPaths(NBPATHS)
      .pathNumberBlock(PATHSPERBLOCK)
      .build();
    CmsPeriodResolved cms = CmsPeriodResolved.of(period);
    MulticurveEquivalent mceComputed = pricer.multicurveEquivalent(cms);
    MulticurveEquivalent mceExpected = MulticurveDecisionScheduleCalculator
        .multicurveEquivalent(swap);
    // add payment date
    LocalDate paymentDate = period.getPaymentDate();
    Payment paymentCms = Payment.of(EUR, NOTIONAL * period.getYearFraction(), paymentDate);
    List<NotionalExchange> df = new ArrayList<>(mceExpected.getDiscountFactorPayments());
    df.add(NotionalExchange.of(paymentCms));
    mceExpected = mceExpected.toBuilder()
        .discountFactorPayments(df)
        .decisionTime(INDICES[1].calculateFixingDateTime(CMS_PERIODS[0][1][1][1][1].getFixingDate())).build();
    assertThat(mceComputed).isEqualTo(mceExpected);
  }
  
  /* Numeraire initial values */
  @Test
  public void numeraireInitialValue() {
    CmsPeriod period = CMS_PERIODS[0][1][1][1][1];
    ResolvedSwap swap = period.getUnderlyingSwap();
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
    LmmdddCmsPeriodMonteCarloPricer pricer = 
      LmmdddCmsPeriodMonteCarloPricer.builder()
      .evolution(EVOLUTION)
      .model(lmmHw)
      .numberGenerator(RND)
      .nbPaths(NBPATHS)
      .pathNumberBlock(PATHSPERBLOCK)
      .build();
    double numeraireExpected = pricer.numeraireInitialValue(MULTICURVE_EUR);
    LocalDate maturity = period.getUnderlyingSwap().getLegs().get(0).getEndDate();
    double numeraireComputed = MULTICURVE_EUR.discountFactor(Currency.EUR, maturity);
    assertThat(numeraireComputed).isEqualTo(numeraireExpected, TOLERANCE_DF);
  }
  
  /* Multi-curve equivalent initial values */
  @Test
  public void initialValues() {
    CmsPeriod period = CMS_PERIODS[0][1][1][1][1];
    CmsPeriodResolved cms = CmsPeriodResolved.of(period);
    ResolvedSwap swap = period.getUnderlyingSwap();
    List<LocalDate> iborDates = new ArrayList<>();
    ResolvedSwapLeg leg = swap.getLegs().get(1);
    iborDates.add(leg.getPaymentPeriods().get(0).getStartDate());
    for (int i = 0; i < leg.getPaymentPeriods().size(); i++) {
      iborDates.add(leg.getPaymentPeriods().get(i).getPaymentDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
    LmmdddCmsPeriodMonteCarloPricer pricer = 
      LmmdddCmsPeriodMonteCarloPricer.builder()
      .evolution(EVOLUTION)
      .model(lmmHw)
      .numberGenerator(RND)
      .nbPaths(NBPATHS)
      .pathNumberBlock(PATHSPERBLOCK)
      .build();
    MulticurveEquivalent mce = pricer.multicurveEquivalent(cms);
    MulticurveEquivalentValues mceValues = pricer.initialValues(mce, MULTICURVE_EUR);
    assertThat(mceValues.getDiscountFactors()).isEqualTo(null);
    assertThat(mceValues.getIborRates()).isEqualTo(null);
    assertThat(mceValues.getOnRates().size()).isEqualTo(lmmHw.getIborTimes().size() - 1);
    for (int i = 0; i < iborDates.size() - 1; i++) {
      OvernightIndexObservation obs = OvernightIndexObservation.of(EUR_EONIA, iborDates.get(i), REF_DATA);
      double fwd = MULTICURVE_EUR.overnightIndexRates(EUR_EONIA).periodRate(obs, iborDates.get(i + 1));
      assertThat(mceValues.getOnRates().get(i)).isEqualTo(fwd, TOLERANCE_RATE);
    }
  }
  
  /* Evolution of values. Tests only that evolution is correctly applied. The evolution
   * class itself is tested in other unit tests. */
  @Test
  public void evolve() {
    CmsPeriod period = CMS_PERIODS[0][1][1][1][1];
    CmsPeriodResolved cms = CmsPeriodResolved.of(period);
    ResolvedSwap swap = period.getUnderlyingSwap();
    List<LocalDate> iborDates = new ArrayList<>();
    ResolvedSwapLeg leg = swap.getLegs().get(1);
    iborDates.add(leg.getPaymentPeriods().get(0).getStartDate());
    for (int i = 0; i < leg.getPaymentPeriods().size(); i++) {
      iborDates.add(leg.getPaymentPeriods().get(i).getPaymentDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
    int nbPaths = 100;
    RandomEngine engineComputed = new MersenneTwister64(0);
    NormalRandomNumberGenerator rndComputed =
        new NormalRandomNumberGenerator(0.0d, 1.0d, engineComputed);
    LmmdddCmsPeriodMonteCarloPricer pricerComputed =
        LmmdddCmsPeriodMonteCarloPricer.builder()
            .evolution(EVOLUTION)
            .model(lmmHw)
            .numberGenerator(rndComputed)
            .nbPaths(NBPATHS)
            .pathNumberBlock(PATHSPERBLOCK)
            .build();
    MulticurveEquivalent mce = pricerComputed.multicurveEquivalent(cms);
    MulticurveEquivalentValues initialValues = pricerComputed.initialValues(mce, MULTICURVE_EUR);
    List<MulticurveEquivalentValues> valuesExpiryComputed =
        pricerComputed.evolve(initialValues, mce.getDecisionTime(), nbPaths);
    RandomEngine engineExpected = new MersenneTwister64(0);
    // random seed fixed to have always the same results in the tests
    NormalRandomNumberGenerator rndExpected =
        new NormalRandomNumberGenerator(0.0d, 1.0d, engineExpected);
    List<MulticurveEquivalentValues> valuesExpiryExpected =
        EVOLUTION.evolveOneStep(period.getIndex().calculateFixingDateTime(period.getFixingDate()),
            initialValues, lmmHw, rndExpected, nbPaths);
    assertThat(valuesExpiryComputed).isEqualTo(valuesExpiryExpected);
  }
  
  @Test
  public void discounting() {
    CmsPeriod period = CMS_PERIODS[0][1][1][1][1];
    CmsPeriodResolved cms = CmsPeriodResolved.of(period);
    ResolvedSwap swap = period.getUnderlyingSwap();
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
    LmmdddCmsPeriodMonteCarloPricer pricer = 
      LmmdddCmsPeriodMonteCarloPricer.builder()
      .evolution(EVOLUTION)
      .model(lmmHw)
      .numberGenerator(RND)
      .nbPaths(NBPATHS)
      .pathNumberBlock(PATHSPERBLOCK)
      .build();
    int nbPaths = 100;
    MulticurveEquivalent mce = pricer.multicurveEquivalent(cms);
    MulticurveEquivalentValues initialValues = pricer.initialValues(mce, MULTICURVE_EUR);
    List<MulticurveEquivalentValues> valuesExpiry =
        pricer.evolve(initialValues, mce.getDecisionTime(), nbPaths);
    int nbFwdPeriods = lmmHw.getIborPeriodsCount();
    double[] delta = lmmHw.getAccrualFactors().toArrayUnsafe();
    double[][] discounting = new double[nbPaths][nbFwdPeriods + 1];
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      MulticurveEquivalentValues valuePath = valuesExpiry.get(looppath);
      double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
      discounting[looppath][nbFwdPeriods] = 1.0;
      for (int loopdsc = nbFwdPeriods - 1; loopdsc >= 0; loopdsc--) {
        discounting[looppath][loopdsc] = 
            discounting[looppath][loopdsc + 1] * (1.0 + valueFwdPath[loopdsc] * delta[loopdsc]);
      }
    }
    double[][] discountingComputed = pricer.discounting(lmmHw, valuesExpiry);
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      for (int loopdsc = 0; loopdsc < nbFwdPeriods; loopdsc++) {
        assertThat(discountingComputed[looppath][loopdsc])
            .isEqualTo(discounting[looppath][loopdsc], TOLERANCE_PV_EXACT);
      }
    }
  }
  
  @Test
  public void aggregation() {
    CmsPeriod period = CMS_PERIODS[0][1][1][1][1];
    CmsPeriodResolved cms = CmsPeriodResolved.of(period);
    ResolvedSwap swap = period.getUnderlyingSwap();
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
    LmmdddCmsPeriodMonteCarloPricer pricer =
        LmmdddCmsPeriodMonteCarloPricer.builder()
            .evolution(EVOLUTION)
            .model(lmmHw)
            .numberGenerator(RND)
            .nbPaths(NBPATHS)
            .pathNumberBlock(PATHSPERBLOCK)
            .build();

    int nbPaths = 100;
    MulticurveEquivalent me = pricer.multicurveEquivalent(cms);
    MulticurveEquivalentValues initialValues = pricer.initialValues(me, MULTICURVE_EUR);
    List<MulticurveEquivalentValues> valuesExpiry =
        pricer.evolve(initialValues, me.getDecisionTime(), nbPaths);
    DoubleArray aggregationComputed = pricer.aggregation(cms, me, valuesExpiry);
    // Local implementation
    double[] delta = lmmHw.getAccrualFactors().toArrayUnsafe();
    double[] beta = lmmHw.getMultiplicativeSpreads().toArrayUnsafe();
    // Indices DF
    int nbDf = me.getDiscountFactorPayments().size();
    double[] dfTimes = new double[nbDf];
    for (int i = 0; i < nbDf; i++) {
      dfTimes[i] = lmmHw.getTimeMeasure()
          .relativeTime(lmmHw.getValuationDate(), me.getDiscountFactorPayments().get(i).getPaymentDate());
    }
    int[] dfIndices = lmmHw.getIborTimeIndex(dfTimes);
    // LMMHW is adapted to swap; swap 1Y fix; LMM 6M: indices from 2 every 2
    int nbYear = INDICES[1].getTemplate().getTenor().getPeriod().getYears();
    assertThat(dfIndices.length).isEqualTo(nbYear + 1);
    for (int i = 0; i < nbYear; i++) {
      assertThat(dfIndices[i]).isEqualTo(2 * (i + 1));
    }
    // Indices IBOR payments
    int nbIbor = me.getIborComputations().size();
    double[] iborPaymentTimes = new double[nbIbor]; // payment time
    double[] iborEffectiveTimes = new double[nbIbor]; // effective time, to find the right forward rate
    for (int i = 0; i < nbIbor; i++) {
      iborPaymentTimes[i] = lmmHw.getTimeMeasure()
          .relativeTime(lmmHw.getValuationDate(), me.getIborPayments().get(i).getPaymentDate());
      iborEffectiveTimes[i] = lmmHw.getTimeMeasure()
          .relativeTime(lmmHw.getValuationDate(), me.getIborComputations().get(i).getEffectiveDate());
    }
    int[] iborPaymentIndices = lmmHw.getIborTimeIndex(iborPaymentTimes);
    int[] iborEffectiveIndices = lmmHw.getIborTimeIndex(iborEffectiveTimes);
    assertThat(iborPaymentIndices.length).isEqualTo(2 * nbYear);
    for (int i = 0; i < iborPaymentIndices.length; i++) {
      assertThat(iborPaymentIndices[i]).isEqualTo(i + 1);
      assertThat(iborEffectiveIndices[i]).isEqualTo(i);
    }
    // Swap Rate
    double[][] discounting = pricer.discounting(lmmHw, valuesExpiry);
    double[] swapRate = new double[nbPaths];
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      MulticurveEquivalentValues valuePath = valuesExpiry.get(looppath);
      double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
      double pvbp = 0.0;
      for (int loopfix = 0; loopfix < nbDf-1; loopfix++) {
        pvbp += me.getDiscountFactorPayments().get(loopfix).getPaymentAmount().getAmount() *
            discounting[looppath][dfIndices[loopfix]];
      }
      double pvIborLeg = 0.0;
      for (int loopibor = 0; loopibor < nbIbor; loopibor++) {
        int ipay = iborPaymentIndices[loopibor];
        int ifwd = iborEffectiveIndices[loopibor];
        double iborRate = (beta[ifwd] * (1 + delta[ifwd] * valueFwdPath[ifwd]) - 1.0d) / delta[ifwd];
        pvIborLeg += me.getIborPayments().get(loopibor).getPaymentAmount().getAmount() *
            iborRate * discounting[looppath][ipay];
      }
      swapRate[looppath] = -pvIborLeg / pvbp;
    }
    double[] payoffs = cms.payoff(swapRate);
    double[] pv = new double[nbPaths];
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      pv[looppath] = discounting[looppath][dfIndices[nbDf - 1]] * payoffs[looppath];
      assertThat(aggregationComputed.get(looppath)).isEqualTo(pv[looppath], TOLERANCE_PV_EXACT);
    }

  }

  /* Comparison with Hull-White forward rates; different maturities and expiries. 
   * Also serve as a Unit Test mechanism for LiborMarketModelMonteCarloEvolution. */
  @Test
  public void comparison_hw() {
    Offset<Double> toleranceRate = within(7.5E-4);
    int nbPaths = 10_000;
    long start, end;
    start = System.currentTimeMillis();
    // nbPaths/error 10,000: ~7.5E-4 / 100,000: ~3.0E-4 (90s) / 500,000: ~2.3E-4 (428s) / 1,000,000: ~2.0E-4 (748s)
    DayCount dayCountHw = DayCounts.ACT_365F;
    HullWhiteOneFactorPiecewiseConstantParameters parametersHw =
        HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERTION, DoubleArray.of(HW_SIGMA), DoubleArray.of());
    HullWhiteOneFactorPiecewiseConstantParametersProvider providerHw =
        HullWhiteOneFactorPiecewiseConstantParametersProvider
            .of(parametersHw, dayCountHw, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
    double maxError = 0.0;
    for (int loopindex = 0; loopindex < NB_INDICES; loopindex++) {
      for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
        CmsPeriod period = CMS_PERIODS[0][loopindex][loopexp][1][1];
        ResolvedSwap swap = period.getUnderlyingSwap();
        LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw = lmmHw(swap);
        RandomEngine engine = new MersenneTwister64(0); // To have same seed for each test
        NormalRandomNumberGenerator generator = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
        LmmdddCmsPeriodMonteCarloPricer pricer =
            LmmdddCmsPeriodMonteCarloPricer.builder()
                .evolution(EVOLUTION)
                .model(lmmHw)
                .numberGenerator(generator)
                .nbPaths(nbPaths)
                .pathNumberBlock(PATHSPERBLOCK)
                .build();
        for (int looptype = 0; looptype < 3; looptype++) {
          for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
            for (int loopstrike = 0; loopstrike < NB_STRIKES; loopstrike++) {
              CmsPeriodResolved cms =
                  CmsPeriodResolved.of(CMS_PERIODS[looptype][loopindex][loopexp][looplag][loopstrike]);
              double pvLmm = pricer.presentValueDouble(cms, MULTICURVE_EUR);
              double fwdLmm = pvLmm / (period.getNotional() * period.getYearFraction());
              CurrencyAmount pvHw =
                  PRICER_CMS_HW.presentValue(CMS_PERIODS[looptype][loopindex][loopexp][looplag][loopstrike],
                      MULTICURVE_EUR, providerHw);
              double fwdHw = pvHw.getAmount() / (period.getNotional() * period.getYearFraction());
              if(PRINT_DETAILS) {
              System.out.println(INDICES[loopindex] + EXPIRIES[loopexp].toString() + TYPES[looptype] +
                  PAYMENT_LAG[looplag] + STRIKES[loopstrike] + ", " + fwdLmm + ", " + fwdHw + ", " + (fwdHw - fwdLmm));
              }
              assertThat(fwdLmm).isEqualTo(fwdHw, toleranceRate); // Compare forward rates
              maxError = Math.max(Math.abs(fwdLmm - fwdHw), maxError);
            }
          }
        }
      }
    }
    end = System.currentTimeMillis();
    if(PRINT_DETAILS) {
    System.out.println("Paths: " + nbPaths + " in " + (end - start) + " ms. Max error: " + maxError);
    }
  }
  
  // TODO: CMS cap low strike v CMS coupon + strike
  
  private static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw(ResolvedSwap swap) {
    List<LocalDate> iborDates = new ArrayList<>();
    ResolvedSwapLeg leg = swap.getLegs().get(1);
    iborDates.add(leg.getPaymentPeriods().get(0).getStartDate());
    for (int i = 0; i < leg.getPaymentPeriods().size(); i++) {
      iborDates.add(leg.getPaymentPeriods().get(i).getPaymentDate());
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw =
        LmmdddExamplesUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, iborDates, EUR_EONIA, EUR_EURIBOR_6M, ScaledSecondTime.DEFAULT,
            MULTICURVE_EUR, VALUATION_ZONE, VALUATION_TIME, REF_DATA);
    return lmmHw;
  }
  
  private static CmsPeriod cmsPeriod(LocalDate fixingDate, LocalDate startDate, LocalDate endDate, LocalDate paymentDate,
      SwapIndex index, ResolvedSwap underlyingSwap, CmsPeriodType type, double strike) {

    CmsPeriod.Builder cmsBuilder = CmsPeriod.builder()
        .fixingDate(fixingDate)
        .startDate(startDate)
        .endDate(endDate)
        .paymentDate(paymentDate)
        .dayCount(DayCounts.ACT_360)
        .yearFraction(0.5)
        .notional(NOTIONAL)
        .index(index)
        .underlyingSwap(underlyingSwap)
        .currency(EUR);
    if(type.equals(CmsPeriodType.CAPLET)) {
      cmsBuilder = cmsBuilder.caplet(strike);
    }
    if(type.equals(CmsPeriodType.FLOORLET)) {
      cmsBuilder = cmsBuilder.floorlet(strike);
    }
    return cmsBuilder.build();
  }

}
