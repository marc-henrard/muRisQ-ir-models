/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.rationalmultiurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.swap.OvernightRateCalculation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.dataset.RationalTwoFactorParameters20151120DataSet;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorFormulas;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorGenericParameters;

/**
 * Tests {@link RationalTwoFactorFormulas}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactorFormulasTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final ZonedDateTime VALUATION_DATE_TIME = 
      ZonedDateTime.of(2015, 11, 20, 11, 12, 13, 0, ZoneId.of("Europe/Brussels"));
  private static final LocalDate VALUATION_DATE = VALUATION_DATE_TIME.toLocalDate();

  /* Load and calibrate curves */
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;

  private static final RationalTwoFactorGenericParameters PARAMETERS =
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F;
  private static final RationalTwoFactorFormulas FORMULAS = 
      RationalTwoFactorFormulas.DEFAULT;

  private final static double TOLERANCE = 1.0E-10;

  /* Tests the swap coefficients. */
  public void swap_coefficients() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    ResolvedSwap swap = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VALUATION_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA).getProduct();
    double[] cComputed = FORMULAS.swapCoefficients(swap, MULTICURVE_EUR, PARAMETERS);
    assertEquals(cComputed.length, 3);
    double[] cFixed = FORMULAS.legFixedCoefficients(swap.getLegs().get(0), MULTICURVE_EUR, PARAMETERS);
    double[] cIbor = FORMULAS.legIborCoefficients(swap.getLegs().get(1), MULTICURVE_EUR, PARAMETERS);
    double[] cExpected = new double[3];
    for (int i = 0; i < 3; i++) {
      cExpected[i] = cFixed[i] + cIbor[i];
    }
    ArrayAsserts.assertArrayEquals(cExpected, cComputed, TOLERANCE);
  }

  /* Tests the swap coefficients for a fixed leg. */
  public void swap_coefficients_fixed() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    ResolvedSwap swap = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VALUATION_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA).getProduct();
    double[] cComputed = FORMULAS.legFixedCoefficients(swap.getLegs().get(0), MULTICURVE_EUR, PARAMETERS);
    assertEquals(cComputed.length, 3);
    double[] cExpected = new double[3];
    for (SwapPaymentPeriod period : swap.getLegs().get(0).getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      cExpected[0] += -notional * fixedRate * accrualPeriod.getYearFraction() *
          MULTICURVE_EUR.discountFactor(EUR, ratePeriod.getPaymentDate());
      cExpected[1] += -notional * fixedRate * accrualPeriod.getYearFraction() *
          PARAMETERS.b0(ratePeriod.getPaymentDate());
    }
    cExpected[0] -= cExpected[1] + cExpected[2];
    ArrayAsserts.assertArrayEquals(cExpected, cComputed, TOLERANCE);
  }
  
  /* Tests the swap coefficients for a Ibor leg. */
  public void swap_coefficients_ibor() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    ResolvedSwap swap = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VALUATION_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA).getProduct();
    double[] cComputed = FORMULAS.legIborCoefficients(swap.getLegs().get(1), MULTICURVE_EUR, PARAMETERS);
    assertEquals(cComputed.length, 3);
    double[] cExpected = new double[3];
    for (SwapPaymentPeriod period : swap.getLegs().get(1).getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof IborRateComputation, "ibor");
      IborRateComputation obs = (IborRateComputation) accrualPeriod.getRateComputation();
      double df = MULTICURVE_EUR.discountFactor(EUR, ratePeriod.getPaymentDate());
      cExpected[0] += notional * accrualPeriod.getYearFraction() *
          MULTICURVE_EUR.iborIndexRates(obs.getIndex()).rate(obs.getObservation()) * df;
      cExpected[1] += notional * accrualPeriod.getYearFraction() *
          PARAMETERS.b1(obs.getObservation());
      cExpected[2] += notional * accrualPeriod.getYearFraction() *
          PARAMETERS.b2(obs.getObservation());
    }
    cExpected[0] -= cExpected[1] + cExpected[2];
    ArrayAsserts.assertArrayEquals(cExpected, cComputed, TOLERANCE);
  }
  
  /* Tests the swap coefficients for a OIS leg. */
  public void swap_coefficients_ois() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    ResolvedSwap swap = EUR_FIXED_1Y_EONIA_OIS
        .createTrade(VALUATION_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA).getProduct();
    double[] cComputed = FORMULAS.legOisCoefficients(swap.getLegs().get(1), MULTICURVE_EUR, PARAMETERS);
    assertEquals(cComputed.length, 3);
    double[] cExpected = new double[3];
    for (SwapPaymentPeriod period : swap.getLegs().get(1).getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof OvernightCompoundedRateComputation,
          "overnight compounded");
      OvernightCompoundedRateComputation obs =
          (OvernightCompoundedRateComputation) accrualPeriod.getRateComputation();
      double dfPayment = MULTICURVE_EUR.discountFactor(EUR, ratePeriod.getPaymentDate());
      double dfStart = MULTICURVE_EUR.discountFactor(EUR, obs.getStartDate());
      double dfEnd = MULTICURVE_EUR.discountFactor(EUR, obs.getEndDate());
      cExpected[0] += ratePeriod.getNotional() * (dfStart * dfPayment / dfEnd - dfPayment);
      cExpected[1] += ratePeriod.getNotional() *
          (dfPayment / dfEnd * PARAMETERS.b0(obs.getStartDate()) - PARAMETERS.b0(ratePeriod.getPaymentDate()));
    }
    cExpected[0] -= cExpected[1] + cExpected[2];
    ArrayAsserts.assertArrayEquals(cExpected, cComputed, TOLERANCE);
  }
  
  /* Tests the swap coefficients for a OIS leg + spread. */
  public void swap_coefficients_ois_spread() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    double spread = 0.0010;
    Swap swap0 = EUR_FIXED_1Y_EONIA_OIS
        .createTrade(VALUATION_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .getProduct();
    RateCalculationSwapLeg legOis0 = (RateCalculationSwapLeg) swap0.getLegs().get(1);
    OvernightRateCalculation onCal0 = (OvernightRateCalculation) legOis0.getCalculation();
    RateCalculationSwapLeg legOisSpread = 
        legOis0.toBuilder().calculation(onCal0.toBuilder().spread(ValueSchedule.of(spread)).build()).build();
    ResolvedSwap swap = Swap.of(swap0.getLegs().get(0), legOisSpread).resolve(REF_DATA);
    double[] cComputed = FORMULAS.legOisCoefficients(swap.getLegs().get(1), MULTICURVE_EUR, PARAMETERS);
    assertEquals(cComputed.length, 3);
    double[] cExpected = new double[3];
    for (SwapPaymentPeriod period : swap.getLegs().get(1).getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof OvernightCompoundedRateComputation,
          "overnight compounded");
      OvernightCompoundedRateComputation obs =
          (OvernightCompoundedRateComputation) accrualPeriod.getRateComputation();
      double dfPayment = MULTICURVE_EUR.discountFactor(EUR, ratePeriod.getPaymentDate());
      double dfStart = MULTICURVE_EUR.discountFactor(EUR, obs.getStartDate());
      double dfEnd = MULTICURVE_EUR.discountFactor(EUR, obs.getEndDate());
      double af = accrualPeriod.getYearFraction();
      cExpected[0] += ratePeriod.getNotional() * (dfStart * dfPayment / dfEnd - (1 - af * spread) * dfPayment);
      cExpected[1] += ratePeriod.getNotional() *
          (dfPayment / dfEnd * PARAMETERS.b0(obs.getStartDate()) -
              (1 - af * spread) * PARAMETERS.b0(ratePeriod.getPaymentDate()));
    }
    cExpected[0] -= cExpected[1] + cExpected[2];
    ArrayAsserts.assertArrayEquals(cExpected, cComputed, TOLERANCE);
  }

  /* Tests the caplet/floorlet coefficients. */
  public void caplet_coefficients() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    double accrualFactor = 0.25;
    LocalDate fixingDate = LocalDate.of(2020, 8, 14);
    LocalDate startDate = LocalDate.of(2020, 8, 18);
    LocalDate endDate = LocalDate.of(2021, 2, 18);
    IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
    IborCapletFloorletPeriod caplet = IborCapletFloorletPeriod.builder()
        .currency(EUR)
        .notional(notional)
        .startDate(startDate)
        .endDate(endDate)
        .paymentDate(endDate)
        .yearFraction(accrualFactor)
        .caplet(fixedRate)
        .iborRate(comp)
        .build();
    double[] cComputed = FORMULAS.capletCoefficients(caplet, MULTICURVE_EUR, PARAMETERS);
    assertEquals(cComputed.length, 3);
    double[] cExpected = new double[3];
    cExpected[1] = (PARAMETERS.b1(comp.getObservation()) - fixedRate * PARAMETERS.b0(endDate));
    cExpected[2] = PARAMETERS.b2(comp.getObservation());
    cExpected[0] = (MULTICURVE_EUR.iborIndexRates(EUR_EURIBOR_6M).rate(comp.getObservation())
        - fixedRate) * MULTICURVE_EUR.discountFactor(EUR, endDate) - (cExpected[1] + cExpected[2]);
    cExpected[0] *= notional * accrualFactor;
    cExpected[1] *= notional * accrualFactor;
    cExpected[2] *= notional * accrualFactor;
    ArrayAsserts.assertArrayEquals(cExpected, cComputed, TOLERANCE);
  }
  
  /* Tests the semi-explicit integral value for edge cases. */
  public void semi_explicit_t_0() {
    double pvP = FORMULAS.pvSemiExplicit(new double[] {1.0d, 2.0d, 3.0d}, 0.0, 0.5, 0.5, 0.1, 10);
    assertEquals(pvP, 1.0d + 2.0d + 3.0d, TOLERANCE);
    double pvN = FORMULAS.pvSemiExplicit(new double[] {-1.0d, -2.0d, -3.0d}, 0.0, 0.5, 0.5, 0.1, 10);
    assertEquals(pvN, 0.0d, TOLERANCE);
  }
  
}
