/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.cms;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
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
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.math.impl.cern.MersenneTwister64;
import com.opengamma.strata.math.impl.cern.RandomEngine;
import com.opengamma.strata.math.impl.random.NormalRandomNumberGenerator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.cms.CmsPeriod;
import com.opengamma.strata.product.cms.CmsPeriodType;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapIndex;
import com.opengamma.strata.product.swap.SwapIndices;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.model.lmm.LmmdddExamplesUtils;
import marc.henrard.murisq.product.cms.CmsPeriodResolved;
import marc.henrard.murisq.product.cms.CmsSpreadPeriod;
import marc.henrard.murisq.product.cms.CmsSpreadPeriodResolved;

/**
 * Tests {@link LmmdddCmsSpreadPeriodMonteCarloPricer}.
 * 
 * @author Marc Henrard
 */
public class LmmdddCmsSpreadPeriodMonteCarloPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_6M.getFixingCalendar());

  /* Market Data */
  private static final ImmutableRatesProvider MULTICURVE_EUR = MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;
  
  /* Descriptions of caplets/floorlets */
  private static final CmsPeriodType[] TYPES = 
      new CmsPeriodType[] {CmsPeriodType.COUPON, CmsPeriodType.CAPLET, CmsPeriodType.FLOORLET};
  private static final SwapIndex[] INDICES = 
      new SwapIndex[] {SwapIndices.EUR_EURIBOR_1100_10Y, SwapIndices.EUR_EURIBOR_1100_2Y};
  private static final Period[] EXPIRIES = new Period[] {Period.ofYears(5), Period.ofYears(20)};
  private static final Period[] PAYMENT_LAG = new Period[] {Period.ZERO, Period.ofMonths(12)};
  private static final int NB_INDICES = INDICES.length;
  private static final int NB_EXPIRIES = EXPIRIES.length;
  private static final int NB_PAY_LAG = PAYMENT_LAG.length;
  private static final double NOTIONAL = 100_000_000.0d;
  private static final double[] WEIGHTS = {1.50d, 0.75d};
  private static final double ACCRUAL_FACTOR = 0.50;
  private static final double STRIKE = 0.02;
  private static final BusinessDayAdjustment ADJUSTMENT = 
      BusinessDayAdjustment.of(BusinessDayConventions.MODIFIED_FOLLOWING, HolidayCalendarIds.EUTA);
  private static final CmsPeriod[][][][] CMS_PERIODS = 
      new CmsPeriod[3][INDICES.length][EXPIRIES.length][PAYMENT_LAG.length];
  private static final CmsSpreadPeriodResolved[][][] CMS_SPREAD_PERIODS = 
      new CmsSpreadPeriodResolved[3][EXPIRIES.length][PAYMENT_LAG.length];
  private static final CmsSpreadPeriodResolved[][][] CMS_SPREAD_PERIODS_0 = 
      new CmsSpreadPeriodResolved[3][EXPIRIES.length][PAYMENT_LAG.length]; // Second index with weight 0.
  static {
    FixedIborSwapConvention convention = FixedIborSwapConventions.EUR_FIXED_1Y_LIBOR_6M;
    for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
      for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
        LocalDate startDate = convention.calculateSpotDateFromTradeDate(fixingDate, REF_DATA);
        LocalDate endDate = EUTA_IMPL.nextOrSame(startDate.plusMonths(6));
        LocalDate paymentDate = EUTA_IMPL.nextOrSame(startDate.plus(PAYMENT_LAG[looplag]));
        for (int loopindex = 0; loopindex < NB_INDICES; loopindex++) {
          ResolvedSwap underlyingSwap =
              INDICES[loopindex].getTemplate().createTrade(fixingDate, BuySell.BUY, 1.0d, 1.0d, REF_DATA)
                  .resolve(REF_DATA)
                  .getProduct();
          for (int looptype = 0; looptype < 3; looptype++) {
            CMS_PERIODS[looptype][loopindex][loopexp][looplag] =
                cmsPeriod(fixingDate, startDate, endDate, paymentDate, INDICES[loopindex], underlyingSwap,
                    TYPES[looptype], STRIKE);
          }
        }
        for (int looptype = 0; looptype < 3; looptype++) {
          CmsSpreadPeriod.Builder builder = CmsSpreadPeriod.builder()
              .notional(NOTIONAL)
              .endDate(AdjustableDate.of(endDate, ADJUSTMENT))
              .startDate(AdjustableDate.of(startDate, ADJUSTMENT))
              .paymentDate(AdjustableDate.of(paymentDate, ADJUSTMENT))
              .yearFraction(ACCRUAL_FACTOR)
              .fixingDate(fixingDate)
              .index1(INDICES[0])
              .weight1(WEIGHTS[0])
              .index2(INDICES[1])
              .weight2(WEIGHTS[1]);
          CmsSpreadPeriod.Builder builder0 = CmsSpreadPeriod.builder()
              .notional(NOTIONAL)
              .endDate(AdjustableDate.of(endDate, ADJUSTMENT))
              .startDate(AdjustableDate.of(startDate, ADJUSTMENT))
              .paymentDate(AdjustableDate.of(paymentDate, ADJUSTMENT))
              .yearFraction(ACCRUAL_FACTOR)
              .fixingDate(fixingDate)
              .index1(INDICES[0])
              .index2(INDICES[1])
              .weight2(0.0d);
          if (looptype == 1) {
            builder.caplet(STRIKE);
            builder0.caplet(STRIKE);
          }
          if (looptype == 2) {
            builder.floorlet(STRIKE);
            builder0.floorlet(STRIKE);
          }
          CMS_SPREAD_PERIODS[looptype][loopexp][looplag] = builder.build()
              .resolve(REF_DATA);
          CMS_SPREAD_PERIODS_0[looptype][loopexp][looplag] =
              builder0.build().resolve(REF_DATA);
        }
      }
    }
  }
  
  /* Model data 1-factor*/
  private static final double MEAN_REVERTION = 0.02;
  private static final double HW_SIGMA = 0.01;
  
  /* Model data 2-factor*/
  private static final double DISPLACEMENT = 0.05;
  private static final double VOL2_LEVEL = 0.08;
  private static final double ANGLE = Math.PI * 0.5;
  private static final double YEAR_ANGLE = 20.0D;
  
  private static final List<LocalDate> IBOR_DATES = new ArrayList<>();
  static {
    ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VALUATION_DATE, Tenor.TENOR_40Y, BuySell.BUY, 1.0, 0.0d, REF_DATA).resolve(REF_DATA);
    ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
    IBOR_DATES.add(iborLeg.get(0).getStartDate());
    for (SwapPaymentPeriod period : iborLeg) {
      IBOR_DATES.add(period.getEndDate());
    }
  }
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMM_HW = 
          LmmdddExamplesUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, IBOR_DATES, EUR_EONIA, EUR_EURIBOR_6M, 
              ScaledSecondTime.DEFAULT, MULTICURVE_EUR, VALUATION_ZONE, VALUATION_TIME, REF_DATA);
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMM_2F = 
      LmmdddExamplesUtils.lmm2Angle(MEAN_REVERTION, 0.0d, ANGLE, VOL2_LEVEL, YEAR_ANGLE, 
          DISPLACEMENT, IBOR_DATES, EUR_EONIA, EUR_EURIBOR_6M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
          VALUATION_ZONE, VALUATION_TIME, REF_DATA);
  
  /* Monte carlo */
  private static final int PATHSPERBLOCK = 500;
  private static final LiborMarketModelMonteCarloEvolution EVOLUTION =
      LiborMarketModelMonteCarloEvolution.DEFAULT;

  private static final boolean PRINT_DETAILS = false;

  /* Compare a CMS spread coupon to two CMS. Weight on index 1 is 1.50, weight on index 2 is 0.75.
   * Use HW-like LMM and 2-factor model. */
  @Test
  public void cms_spread_cpn_V_2_cms_cpn() {
    Offset<Double> toleranceRate = within(3.2E-3); // Adjusted to 2,000 paths
    int nbPaths = 2_000;
    long start, end;
    start = System.currentTimeMillis();
    // nbPaths/error 2,000: ~3.2E-3 (7s) / 10,000: ~2.0E-3 (34s) / 100,000: ~4.7E-4 (365s) / 200,000: ~1.3E-4 (660s)
    double maxError = 0.0;
    for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
      for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
        if (PRINT_DETAILS) {
          System.out.println(EXPIRIES[loopexp] + " - " + PAYMENT_LAG[looplag]);
        }
        RandomEngine engine = new MersenneTwister64(0); // To have same seed for each test
        NormalRandomNumberGenerator generator = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
        LmmdddCmsPeriodMonteCarloPricer pricerCmsLmmHw = cmsPricer(generator, LMM_HW, nbPaths);
        LmmdddCmsSpreadPeriodMonteCarloPricer pricerCmsSpreadLmmHw = cmsSpreadPricer(generator, LMM_HW, nbPaths);
        LmmdddCmsPeriodMonteCarloPricer pricerCmsLmm2F = cmsPricer(generator, LMM_2F, nbPaths);
        LmmdddCmsSpreadPeriodMonteCarloPricer pricerCmsSpreadLmm2F = cmsSpreadPricer(generator, LMM_2F, nbPaths);
        double[] pvCmsLmmHw = new double[2];
        double[] pvCmsLmm2F = new double[2];
        double[] fwdCmsLmmHw = new double[2];
        double[] fwdCmsLmm2F = new double[2];
        for (int loopindex = 0; loopindex < NB_INDICES; loopindex++) {
          CmsPeriodResolved cms =
              CmsPeriodResolved.of(CMS_PERIODS[0][loopindex][loopexp][looplag]);
          pvCmsLmmHw[loopindex] = pricerCmsLmmHw.presentValueDouble(cms, MULTICURVE_EUR);
          pvCmsLmm2F[loopindex] = pricerCmsLmm2F.presentValueDouble(cms, MULTICURVE_EUR);
          fwdCmsLmmHw[loopindex] = pvCmsLmmHw[loopindex] / (NOTIONAL * ACCRUAL_FACTOR);
          fwdCmsLmm2F[loopindex] = pvCmsLmm2F[loopindex] / (NOTIONAL * ACCRUAL_FACTOR);
        }
        double pvSpreadLmmHw =
            pricerCmsSpreadLmmHw.presentValueDouble(CMS_SPREAD_PERIODS[0][loopexp][looplag], MULTICURVE_EUR);
        double pvSpreadLmm2F =
            pricerCmsSpreadLmm2F.presentValueDouble(CMS_SPREAD_PERIODS[0][loopexp][looplag], MULTICURVE_EUR);
        double fwdSpreadLmmHw = pvSpreadLmmHw / (NOTIONAL * ACCRUAL_FACTOR);
        double fwdSpreadLmm2F = pvSpreadLmm2F / (NOTIONAL * ACCRUAL_FACTOR);
        double fwd2CmsHw = (WEIGHTS[0] * pvCmsLmmHw[0] - WEIGHTS[1] * pvCmsLmmHw[1]) / (NOTIONAL * ACCRUAL_FACTOR);
        double fwd2Cms2F = (WEIGHTS[0] * pvCmsLmm2F[0] - WEIGHTS[1] * pvCmsLmm2F[1]) / (NOTIONAL * ACCRUAL_FACTOR);
        double fwdDiffHw = fwdSpreadLmmHw - fwd2CmsHw;
        double fwdDiff2F = fwdSpreadLmm2F - fwd2Cms2F;
        if (PRINT_DETAILS) {
          System.out.println("HW: " + fwdSpreadLmmHw + ", " + fwd2CmsHw + ", " + fwdCmsLmmHw[0] + ", " +
              fwdCmsLmmHw[1] + ", " + fwdDiffHw);
          System.out.println("2F: " + fwdSpreadLmm2F + ", " + fwd2Cms2F + ", " + fwdCmsLmm2F[0] + ", " +
              fwdCmsLmm2F[1] + ", " + fwdDiff2F);
        }
        assertThat(fwdSpreadLmmHw).isEqualTo(fwd2CmsHw, toleranceRate); // Compare forward rates
        assertThat(fwdSpreadLmm2F).isEqualTo(fwd2Cms2F, toleranceRate); // Compare forward rates
        maxError = Math.max(Math.abs(fwdDiff2F), Math.max(Math.abs(fwdDiffHw), maxError));
      }
    }
    end = System.currentTimeMillis();
    if (PRINT_DETAILS) {
      System.out.println("Paths: " + nbPaths + " in " + (end - start) + " ms. Max error: " + maxError);
    }
  }

  /* Compare a CMS spread cap with second weight 0 to a CMS.
   * Use HW-like LMM and 2-factor model. */
  @Test
  public void cms_spread_0_cap_V_cms_can() {
    Offset<Double> toleranceRate = within(3.5E-3); // Adjusted to 2,000 paths
    int nbPaths = 2_000;
    long start, end;
    start = System.currentTimeMillis();
    // nbPaths/error 2,000: ~3.5E-3 (13s) / 10,000: ~1.9E-3 (75s) / 100,000: ~4.1E-4 (699s) / 200,000: ~1.8E-4 (1400s)
    double maxError = 0.0;
    for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
      for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
        if (PRINT_DETAILS) {
          System.out.println(EXPIRIES[loopexp] + " - " + PAYMENT_LAG[looplag]);
        }
        RandomEngine engine = new MersenneTwister64(0); // To have same seed for each test
        NormalRandomNumberGenerator generator = new NormalRandomNumberGenerator(0.0d, 1.0d, engine);
        LmmdddCmsPeriodMonteCarloPricer pricerCmsLmmHw = cmsPricer(generator, LMM_HW, nbPaths);
        LmmdddCmsSpreadPeriodMonteCarloPricer pricerCmsSpreadLmmHw = cmsSpreadPricer(generator, LMM_HW, nbPaths);
        LmmdddCmsPeriodMonteCarloPricer pricerCmsLmm2F = cmsPricer(generator, LMM_2F, nbPaths);
        LmmdddCmsSpreadPeriodMonteCarloPricer pricerCmsSpreadLmm2F = cmsSpreadPricer(generator, LMM_2F, nbPaths);
        for (int looptype = 0; looptype < 3; looptype++) {
          CmsPeriodResolved cms =
              CmsPeriodResolved.of(CMS_PERIODS[looptype][0][loopexp][looplag]);
          double pvCmsLmmHw = pricerCmsLmmHw.presentValueDouble(cms, MULTICURVE_EUR);
          double pvCmsLmm2F = pricerCmsLmm2F.presentValueDouble(cms, MULTICURVE_EUR);
          double fwdCmsLmmHw = pvCmsLmmHw / (NOTIONAL * ACCRUAL_FACTOR);
          double fwdCmsLmm2F = pvCmsLmm2F / (NOTIONAL * ACCRUAL_FACTOR);
          double pvSpreadLmmHw =
              pricerCmsSpreadLmmHw.presentValueDouble(CMS_SPREAD_PERIODS_0[looptype][loopexp][looplag], MULTICURVE_EUR);
          double pvSpreadLmm2F =
              pricerCmsSpreadLmm2F.presentValueDouble(CMS_SPREAD_PERIODS_0[looptype][loopexp][looplag], MULTICURVE_EUR);
          double fwdSpreadLmmHw = pvSpreadLmmHw / (NOTIONAL * ACCRUAL_FACTOR);
          double fwdSpreadLmm2F = pvSpreadLmm2F / (NOTIONAL * ACCRUAL_FACTOR);
          double fwdCmsHw = pvCmsLmmHw / (NOTIONAL * ACCRUAL_FACTOR);
          double fwdCms2F = pvCmsLmm2F / (NOTIONAL * ACCRUAL_FACTOR);
          double fwdDiffHw = fwdSpreadLmmHw - fwdCmsHw;
          double fwdDiff2F = fwdSpreadLmm2F - fwdCms2F;
          if (PRINT_DETAILS) {
            System.out.println("HW: " + fwdSpreadLmmHw + ", " + fwdCmsHw + ", " + fwdCmsLmmHw + ", " + fwdDiffHw);
            System.out.println("2F: " + fwdSpreadLmm2F + ", " + fwdCms2F + ", " + fwdCmsLmm2F + ", " + fwdDiff2F);
          }
          assertThat(fwdSpreadLmmHw).isEqualTo(fwdCmsHw, toleranceRate); // Compare forward rates
          assertThat(fwdSpreadLmm2F).isEqualTo(fwdCms2F, toleranceRate); // Compare forward rates
          maxError = Math.max(Math.abs(fwdDiff2F), Math.max(Math.abs(fwdDiffHw), maxError));
        }
      }
    }
    end = System.currentTimeMillis();
    if (PRINT_DETAILS) {
      System.out.println("Paths: " + nbPaths + " in " + (end - start) + " ms. Max error: " + maxError);
    }
  }

  /* Compare a CMS spread cap - floors to CMS spread coupon.
   * Use HW-like LMM and 2-factor model. */
  @Test
  public void cms_spread_put_call_parity() {
    Offset<Double> toleranceRate = within(6.1E-4); // Adjusted to 2,000 paths
    int nbPaths = 2_000;
    long start, end;
    start = System.currentTimeMillis();
    // nbPaths/error 2,000: ~6.1E-4 (8s) / 10,000: ~2.8E-4 (33s) / 50,000: ~2.9E-5 (172s) / 200,000: ~4.5E-5 (648s)
    double maxError = 0.0;
    for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
      for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
        if (PRINT_DETAILS) {
          System.out.println(EXPIRIES[loopexp] + " - " + PAYMENT_LAG[looplag]);
        }
        double[] pvSpreadLmmHw = new double[3];
        double[] pvSpreadLmm2F = new double[3];
        double[] fwdSpreadLmmHw = new double[3];
        double[] fwdSpreadLmm2F = new double[3];
        for (int looptype = 0; looptype < 3; looptype++) {
          RandomEngine engineHw = new MersenneTwister64(0); // To have same seed for each test
          NormalRandomNumberGenerator generatorHw = new NormalRandomNumberGenerator(0.0d, 1.0d, engineHw);
          LmmdddCmsSpreadPeriodMonteCarloPricer pricerCmsSpreadLmmHw = cmsSpreadPricer(generatorHw, LMM_HW, nbPaths);
          pvSpreadLmmHw[looptype] =
              pricerCmsSpreadLmmHw.presentValueDouble(CMS_SPREAD_PERIODS[looptype][loopexp][looplag], MULTICURVE_EUR);
          RandomEngine engine2F = new MersenneTwister64(0); // To have same seed for each test
          NormalRandomNumberGenerator generator2F = new NormalRandomNumberGenerator(0.0d, 1.0d, engine2F);
          LmmdddCmsSpreadPeriodMonteCarloPricer pricerCmsSpreadLmm2F = cmsSpreadPricer(generator2F, LMM_2F, nbPaths);
          pvSpreadLmm2F[looptype] =
              pricerCmsSpreadLmm2F.presentValueDouble(CMS_SPREAD_PERIODS[looptype][loopexp][looplag], MULTICURVE_EUR);
          fwdSpreadLmmHw[looptype] = pvSpreadLmmHw[looptype] / (NOTIONAL * ACCRUAL_FACTOR);
          fwdSpreadLmm2F[looptype] = pvSpreadLmm2F[looptype] / (NOTIONAL * ACCRUAL_FACTOR);
        }

        double dfPay = MULTICURVE_EUR.discountFactor(EUR, CMS_SPREAD_PERIODS[0][loopexp][looplag].getPaymentDate());
        double pcParityHw = fwdSpreadLmmHw[1] - fwdSpreadLmmHw[2] -
            (fwdSpreadLmmHw[0] - dfPay * STRIKE);
        double pcParity2F = fwdSpreadLmm2F[1] - fwdSpreadLmm2F[2] -
            (fwdSpreadLmm2F[0] - dfPay * STRIKE);
        if (PRINT_DETAILS) {
          System.out.println("HW: " + fwdSpreadLmmHw[0] + ", " + fwdSpreadLmmHw[1] + ", " + fwdSpreadLmmHw[2] + ", " + pcParityHw);
          System.out.println("2F: " + fwdSpreadLmm2F[0] + ", " + fwdSpreadLmm2F[1] + ", " + fwdSpreadLmm2F[2] + ", " + pcParity2F);
        }
        assertThat(pcParityHw).isEqualTo(0.0d, toleranceRate); // Compare forward rates
        assertThat(pcParity2F).isEqualTo(0.0d, toleranceRate); // Compare forward rates
        maxError = Math.max(Math.abs(pcParity2F), Math.max(Math.abs(pcParityHw), maxError));
      }
    }
    end = System.currentTimeMillis();
    if (PRINT_DETAILS) {
      System.out.println("Paths: " + nbPaths + " in " + (end - start) + " ms. Max error: " + maxError);
    }
  }
  
  // Spread options with approximated dynamic?

  private static LmmdddCmsPeriodMonteCarloPricer cmsPricer(
      NormalRandomNumberGenerator generator,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      int nbPaths) {
    
    return LmmdddCmsPeriodMonteCarloPricer.builder()
        .evolution(EVOLUTION)
        .model(model)
        .numberGenerator(generator)
        .nbPaths(nbPaths)
        .pathNumberBlock(PATHSPERBLOCK)
        .build();
  }
  
  private static LmmdddCmsSpreadPeriodMonteCarloPricer cmsSpreadPricer(
      NormalRandomNumberGenerator generator,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      int nbPaths) {
    
    return LmmdddCmsSpreadPeriodMonteCarloPricer.builder()
        .evolution(EVOLUTION)
        .model(model)
        .numberGenerator(generator)
        .nbPaths(nbPaths)
        .pathNumberBlock(PATHSPERBLOCK)
        .build();
  }
      
  private static CmsPeriod cmsPeriod(LocalDate fixingDate, LocalDate startDate, LocalDate endDate, LocalDate paymentDate,
      SwapIndex index, ResolvedSwap underlyingSwap, CmsPeriodType type, double strike) {

    CmsPeriod.Builder cmsBuilder = CmsPeriod.builder()
        .fixingDate(fixingDate)
        .startDate(startDate)
        .endDate(endDate)
        .paymentDate(paymentDate)
        .dayCount(DayCounts.ACT_360)
        .yearFraction(ACCRUAL_FACTOR)
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
