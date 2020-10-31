/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
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
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.cern.MersenneTwister64;
import com.opengamma.strata.math.impl.cern.RandomEngine;
import com.opengamma.strata.math.impl.random.NormalRandomNumberGenerator;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.HullWhiteSwaptionPhysicalProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.model.lmm.LmmdddExamplesUtils;
import marc.henrard.murisq.pricer.decomposition.MulticurveDecisionScheduleCalculator;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;

/**
 * Tests {@link LmmdddSwaptionPhysicalProductMonteCarloPricer} and partly
 * {@link LiborMarketModelMonteCarloEvolution}.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionPhysicalProductMonteCarloPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());

  /* Pricer */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;

  /* Market Data */
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;

  /* Swaption description */
  private static final Period EXPIRY = Period.ofMonths(60);
  private static final LocalDate EXPIRY_DATE = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRY));
  private static final Tenor TENOR = Tenor.TENOR_10Y;
  private static final double MONEYNESS = 0.0050;
  private static final double NOTIONAL = 1_000_000.0d;
  private static final ResolvedSwapTrade SWAP_0 = EUR_FIXED_1Y_EURIBOR_3M
      .createTrade(EXPIRY_DATE, TENOR, BuySell.BUY, NOTIONAL, 0.0d, REF_DATA).resolve(REF_DATA);
  private static final double PAR_RATE = PRICER_SWAP.parRate(SWAP_0.getProduct(), MULTICURVE_EUR);
  private static final SwapTrade SWAP = EUR_FIXED_1Y_EURIBOR_3M
      .createTrade(EXPIRY_DATE, TENOR, BuySell.BUY, NOTIONAL, PAR_RATE + MONEYNESS, REF_DATA);
  private static final Swaption SWAPTION = Swaption.builder()
      .expiryDate(AdjustableDate.of(EXPIRY_DATE)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
      .longShort(LongShort.LONG)
      .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
      .underlying(SWAP.getProduct()).build();
  private static final ResolvedSwaption SWAPTION_RESOLVED = SWAPTION.resolve(REF_DATA);
  
  /* Model data */
  private static final double MEAN_REVERTION = 0.02;
  private static final double HW_SIGMA = 0.01;
  private static final List<LocalDate> IBOR_DATES = new ArrayList<>();
  static {
    ResolvedSwapLeg leg = SWAPTION_RESOLVED.getUnderlying().getLegs().get(1);
    IBOR_DATES.add(leg.getPaymentPeriods().get(0).getStartDate());
    for (int i = 0; i < leg.getPaymentPeriods().size(); i++) {
      IBOR_DATES.add(leg.getPaymentPeriods().get(i).getPaymentDate());
    }
  }
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMMHW = 
      LmmdddExamplesUtils.
      lmmHw(MEAN_REVERTION, HW_SIGMA, IBOR_DATES, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, 
          MULTICURVE_EUR, VALUATION_ZONE, VALUATION_TIME, REF_DATA);
  private static final double VOL2_LEVEL_1 = 0.09;
  private static final double VOL2_ANGLE = Math.PI * 0.5;
  private static final double VOL2_LEVEL_2 = 0.06;
  private static final double DISPLACEMENT = 0.05; // 5% rate displacement
  
  /* Monte carlo */
  private static final int NBPATHS = 10000; // Used only for performance analysis
  private static final int PATHSPERBLOCK = 1000;
  private static final RandomEngine ENGINE = new MersenneTwister64(0);
  private static final NormalRandomNumberGenerator RND = 
      new NormalRandomNumberGenerator(0.0d, 1.0d, ENGINE);
  private static final LiborMarketModelMonteCarloEvolution EVOLUTION =
      LiborMarketModelMonteCarloEvolution.DEFAULT;
  private static final LmmdddSwaptionPhysicalProductMonteCarloPricer PRICER_MC_1 = 
      LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
      .evolution(EVOLUTION)
      .model(LMMHW)
      .numberGenerator(RND)
      .nbPaths(NBPATHS)
      .pathNumberBlock(PATHSPERBLOCK)
      .build();

  private static final NormalSwaptionPhysicalProductPricer2 PRICER_SWAPTION_BACHELIER =
      NormalSwaptionPhysicalProductPricer2.DEFAULT;
  private static final HullWhiteSwaptionPhysicalProductPricer PRICER_SWAPTION_HW =
      HullWhiteSwaptionPhysicalProductPricer.DEFAULT;
  private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_SWAPTION_LMM_APPROX =
      LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;
  
  private static final Offset<Double> TOLERANCE_DF = within(1.0E-12);
  private static final Offset<Double> TOLERANCE_RATE = within(1.0E-12);
  private static final Offset<Double> TOLERANCE_PV_EXACT = within(1.0E-12);
  
  /* Multi-curve equivalent for the underlying swap */
  @Test
  public void multicurveEquivalent() {
    MulticurveEquivalent mceComputed = PRICER_MC_1.multicurveEquivalent(SWAPTION_RESOLVED);
    MulticurveEquivalent mceExpected = MulticurveDecisionScheduleCalculator
        .multicurveEquivalent(SWAPTION_RESOLVED.getUnderlying());
    mceExpected = mceExpected.toBuilder().decisionTime(SWAPTION_RESOLVED.getExpiry()).build();
    assertThat(mceComputed).isEqualTo(mceExpected);
  }
  
  /* Numeraire initial values */
  @Test
  public void numeraireInitialValue() {
    double numeraireExpected = PRICER_MC_1.numeraireInitialValue(MULTICURVE_EUR);
    LocalDate maturity = SWAPTION_RESOLVED.getUnderlying().getLegs().get(0).getEndDate();
    double numeraireComputed = MULTICURVE_EUR.discountFactor(Currency.EUR, maturity);
    assertThat(numeraireComputed).isEqualTo(numeraireExpected, TOLERANCE_DF);
  }
  
  /* Multi-curve equivalent initial values */
  @Test
  public void initialValues() {
    MulticurveEquivalent mce = PRICER_MC_1.multicurveEquivalent(SWAPTION_RESOLVED);
    MulticurveEquivalentValues mceValues = PRICER_MC_1.initialValues(mce, MULTICURVE_EUR, LMMHW);
    assertThat(mceValues.getDiscountFactors()).isEqualTo(null);
    assertThat(mceValues.getIborRates()).isEqualTo(null);
    assertThat(mceValues.getOnRates().size()).isEqualTo(IBOR_DATES.size() - 1);
    for(int i=0; i<IBOR_DATES.size()-1; i++) {
      OvernightIndexObservation obs = OvernightIndexObservation.of(EUR_EONIA, IBOR_DATES.get(i), REF_DATA);
      double fwd = MULTICURVE_EUR.overnightIndexRates(EUR_EONIA).periodRate(obs, IBOR_DATES.get(i+1));
      assertThat(mceValues.getOnRates().get(i)).isEqualTo(fwd, TOLERANCE_RATE);
    }
  }
  
  /* Evolution of values. Tests only that evolution is correctly applied. The evolution
   * class itself is tested in other unit tests. */
  @Test
  public void evolve() {
    int nbPaths = 100;
    MulticurveEquivalent mce = PRICER_MC_1.multicurveEquivalent(SWAPTION_RESOLVED);
    MulticurveEquivalentValues initialValues = PRICER_MC_1.initialValues(mce, MULTICURVE_EUR, LMMHW);
    RandomEngine engineComputed = new MersenneTwister64(0);
    NormalRandomNumberGenerator rndComputed =
        new NormalRandomNumberGenerator(0.0d, 1.0d, engineComputed);
    LmmdddSwaptionPhysicalProductMonteCarloPricer pricerComputed =
        LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
            .evolution(EVOLUTION)
            .model(LMMHW)
            .numberGenerator(rndComputed)
            .nbPaths(NBPATHS)
            .pathNumberBlock(PATHSPERBLOCK)
            .build();
    List<MulticurveEquivalentValues> valuesExpiryComputed =
        pricerComputed.evolve(initialValues, mce.getDecisionTime(), nbPaths);
    RandomEngine engineExpected = new MersenneTwister64(0); 
    // random seed fixed to have always the same results in the tests
    NormalRandomNumberGenerator rndExpected =
        new NormalRandomNumberGenerator(0.0d, 1.0d, engineExpected);
    List<MulticurveEquivalentValues> valuesExpiryExpected =
        EVOLUTION.evolveOneStep(SWAPTION_RESOLVED.getExpiry(), initialValues, LMMHW, rndExpected, nbPaths);
    assertThat(valuesExpiryComputed).isEqualTo(valuesExpiryExpected);
  }
  
  @Test
  public void discounting() {
    int nbPaths = 100;
    MulticurveEquivalent mce = PRICER_MC_1.multicurveEquivalent(SWAPTION_RESOLVED);
    MulticurveEquivalentValues initialValues = PRICER_MC_1.initialValues(mce, MULTICURVE_EUR, LMMHW);
    List<MulticurveEquivalentValues> valuesExpiry =
        PRICER_MC_1.evolve(initialValues, mce.getDecisionTime(), nbPaths);
    int nbFwdPeriods = LMMHW.getIborPeriodsCount();
    double[] delta = LMMHW.getAccrualFactors().toArrayUnsafe();
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
    double[][] discountingComputed = PRICER_MC_1.discounting(LMMHW, valuesExpiry);
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      for (int loopdsc = 0; loopdsc < nbFwdPeriods; loopdsc++) {
        assertThat(discountingComputed[looppath][loopdsc])
            .isEqualTo(discounting[looppath][loopdsc], TOLERANCE_PV_EXACT);
      }
    }
  }
  
  @Test
  public void aggregation() {
    int nbPaths = 100;
    MulticurveEquivalent me = PRICER_MC_1.multicurveEquivalent(SWAPTION_RESOLVED);
    MulticurveEquivalentValues initialValues = PRICER_MC_1.initialValues(me, MULTICURVE_EUR, LMMHW);
    List<MulticurveEquivalentValues> valuesExpiry =
        PRICER_MC_1.evolve(initialValues, me.getDecisionTime(), nbPaths);
    DoubleArray aggregationComputed = PRICER_MC_1.aggregation(SWAPTION_RESOLVED, me, valuesExpiry, LMMHW);
    // Local implementation
    double[] delta = LMMHW.getAccrualFactors().toArrayUnsafe();
    double[] beta = LMMHW.getMultiplicativeSpreads().toArrayUnsafe();
    // Indices fix payments
    int nbFix = me.getDiscountFactorPayments().size();
    double[] fixTimes = new double[nbFix];
    for (int i = 0; i < nbFix; i++) {
      fixTimes[i] = LMMHW.getTimeMeasure()
          .relativeTime(LMMHW.getValuationDate(), me.getDiscountFactorPayments().get(i).getPaymentDate());
    }
    int[] fixIndices = LMMHW.getIborTimeIndex(fixTimes);
    // LMMHW is adapted to swaption; swap 1Y fix; LMM 3M: indices from 4 every 4
    int nbYear = TENOR.getPeriod().getYears();
    assertThat(fixIndices.length).isEqualTo(nbYear);
    for (int i = 0; i < nbYear; i++) {
      assertThat(fixIndices[i]).isEqualTo(4 * (i + 1));
    }
    // Indices IBOR payments
    int nbIbor = me.getIborComputations().size();
    double[] iborPaymentTimes = new double[nbIbor]; // payment time
    double[] iborEffectiveTimes = new double[nbIbor]; // effective time, to find the right forward rate
    for (int i = 0; i < nbIbor; i++) {
      iborPaymentTimes[i] = LMMHW.getTimeMeasure()
          .relativeTime(LMMHW.getValuationDate(), me.getIborPayments().get(i).getPaymentDate());
      iborEffectiveTimes[i] = LMMHW.getTimeMeasure()
          .relativeTime(LMMHW.getValuationDate(), me.getIborComputations().get(i).getEffectiveDate());
    }
    int[] iborPaymentIndices = LMMHW.getIborTimeIndex(iborPaymentTimes);
    int[] iborEffectiveIndices = LMMHW.getIborTimeIndex(iborEffectiveTimes);
    assertThat(iborPaymentIndices.length).isEqualTo(4 * nbYear);
    for (int i = 0; i < iborPaymentIndices.length; i++) {
      assertThat(iborPaymentIndices[i]).isEqualTo(i + 1);
      assertThat(iborEffectiveIndices[i]).isEqualTo(i);
    }
    // Pricing
    double[][] discounting = PRICER_MC_1.discounting(LMMHW, valuesExpiry);
    double[] pv = new double[nbPaths];

    for (int looppath = 0; looppath < nbPaths; looppath++) {
      MulticurveEquivalentValues valuePath = valuesExpiry.get(looppath);
      double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
      double pvPath = 0.0;
      for (int loopfix = 0; loopfix < nbFix; loopfix++) {
        pvPath += me.getDiscountFactorPayments().get(loopfix).getPaymentAmount().getAmount() *
            discounting[looppath][fixIndices[loopfix]];
      }
      for (int loopibor = 0; loopibor < nbIbor; loopibor++) {
        int ipay = iborPaymentIndices[loopibor];
        int ifwd = iborEffectiveIndices[loopibor];
        double iborRate = (beta[ifwd] * (1 + delta[ifwd] * valueFwdPath[ifwd]) - 1.0d) / delta[ifwd];
        pvPath += me.getIborPayments().get(loopibor).getPaymentAmount().getAmount() *
            iborRate * discounting[looppath][ipay];
      }
      pv[looppath] = Math.max(0.0, pvPath);
      assertThat(aggregationComputed.get(looppath)).isEqualTo(pv[looppath]);
    }
  }

  /* Comparison with Hull-White implied volatilities; different maturities and expiries. 
   * Also serve as a Unit Test mechanism for LiborMarketModelMonteCarloEvolution. */
  @Test
  public void comparison_hw() {
    Offset<Double> toleranceIv = within(5.7E-4); 
    int nbPaths = 10_000;
    // nbPaths/error 10,000: ~5.7E-4 / 100,000: ~2.0E-4 / 500,000: ~1.2E-4 / 1,000,000: ~9.0E-5
    DayCount dayCountHw = DayCounts.ACT_365F;
    HullWhiteOneFactorPiecewiseConstantParameters parametersHw =
        HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERTION, DoubleArray.of(HW_SIGMA), DoubleArray.of());
    HullWhiteOneFactorPiecewiseConstantParametersProvider providerHw =
        HullWhiteOneFactorPiecewiseConstantParametersProvider
            .of(parametersHw, dayCountHw, VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
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
      RandomEngine engine = new MersenneTwister64(0); // To have same seed for each test
      NormalRandomNumberGenerator generator = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
      LmmdddSwaptionPhysicalProductMonteCarloPricer pricer =
          LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
              .evolution(EVOLUTION)
              .model(lmmHw)
              .numberGenerator(generator)
              .nbPaths(nbPaths)
              .pathNumberBlock(PATHSPERBLOCK)
              .build();
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
          double pvLmm = pricer.presentValueDouble(swaptionResolved, MULTICURVE_EUR, lmmHw);
          CurrencyAmount pvHw =
              PRICER_SWAPTION_HW.presentValue(swaptionResolved, MULTICURVE_EUR, providerHw);
          double ivLmm = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, dayCountHw, pvLmm);
          double ivHw = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, dayCountHw, pvHw.getAmount());
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + ivLmm + ", " + ivHw + ", " + (ivHw - ivLmm));
          assertThat(ivLmm).isEqualTo(ivHw, toleranceIv); // Compare implied volatilities
        } // end loopmoney
      } // end looptenor
    } // end loopexp

  }

  /* Comparison with a two-factor implied volatilities; different maturities and expiries. 
   * Also serve as a Unit Test mechanism for LiborMarketModelMonteCarloEvolution. */
  @Test
  public void comparison_2factor() {
    Offset<Double> toleranceIv = within(6.3E-4); 
    int nbPaths = 10_000;
    // nbPaths/error 10,000: ~6.3E-4 / 100,000: ~1.6E-4 / 1,000,000: ~8.3E-5
    DayCount dayCountHw = DayCounts.ACT_365F;
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
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm = LmmdddExamplesUtils.
          lmm2Angle(MEAN_REVERTION, VOL2_LEVEL_1, VOL2_ANGLE, VOL2_LEVEL_2, DISPLACEMENT,
              iborDates, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
              VALUATION_ZONE, VALUATION_TIME, REF_DATA);
      RandomEngine engine = new MersenneTwister64(0); // To have same seed for each test
      NormalRandomNumberGenerator generator = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
      LmmdddSwaptionPhysicalProductMonteCarloPricer pricer =
          LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
              .evolution(EVOLUTION)
              .model(lmm)
              .numberGenerator(generator)
              .nbPaths(nbPaths)
              .pathNumberBlock(PATHSPERBLOCK)
              .build();
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
          double pvLmm = pricer.presentValueDouble(swaptionResolved, MULTICURVE_EUR, lmm);
          CurrencyAmount pvApprox =
              PRICER_SWAPTION_LMM_APPROX.presentValue(swaptionResolved, MULTICURVE_EUR, lmm);
          double ivLmm = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, dayCountHw, pvLmm);
          double ivHw = PRICER_SWAPTION_BACHELIER
              .impliedVolatilityFromPresentValue(swaptionResolved, MULTICURVE_EUR, dayCountHw, pvApprox.getAmount());
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + ivLmm + ", " + ivHw + ", " + (ivHw - ivLmm));
          assertThat(ivLmm).isEqualTo(ivHw, toleranceIv); // Compare implied volatilities
        } // end loopmoney
      } // end looptenor
    } // end loopexp

  }

//  @Ignore 
//  @Test
//  public void performance_hw1() {
//    long start, end;
//    int nbRep = 5;
//    int nbRep2 = 3;
//    for (int j = 0; j < nbRep2; j++) {
//      start = System.currentTimeMillis();
//      double testLmm = 0.0;
//      for (int i = 0; i < nbRep; i++) {
//        testLmm += PRICER_MC_1.presentValueDouble(SWAPTION_RESOLVED, MULTICURVE_EUR, LMMHW);
//      }
//      end = System.currentTimeMillis();
//      System.out.println("LMM computation time: " + (end - start) + " ms (" + nbRep + " repetitions with " + NBPATHS +
//          " paths). " + testLmm);
//    }
//  }
  

//  /* Many runs with a given number of paths to analyze the distribution of PV */
//  @Ignore 
//  @Test
//  public void distribution_pv_hw1() {
//    int nbPaths = 10_000;
//    int nbRep = 100;
//    long start, end;
//    LmmdddSwaptionPhysicalProductMonteCarloPricer pricer = 
//        LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
//        .evolution(EVOLUTION)
//        .model(LMMHW)
//        .numberGenerator(RND)
//        .nbPaths(nbPaths)
//        .pathNumberBlock(PATHSPERBLOCK)
//        .build();
//    start = System.currentTimeMillis();
//    for (int i = 0; i < nbRep; i++) {
//      double pv = pricer.presentValueDouble(SWAPTION_RESOLVED, MULTICURVE_EUR, LMMHW);
//      double iv = PRICER_SWAPTION_BACHELIER
//          .impliedVolatilityFromPresentValue(SWAPTION_RESOLVED, MULTICURVE_EUR, DayCounts.ACT_365F, pv);
//      System.out.println(pv + ", " + iv);
//    }
//    end = System.currentTimeMillis();
//    System.out.println("LMM computation time: " + (end - start) + " ms.");
//  }

}
