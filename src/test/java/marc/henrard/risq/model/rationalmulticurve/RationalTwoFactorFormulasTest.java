/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.risq.model.dataset.RationalTwoFactorParameters20151120DataSet;

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

  private static final RationalTwoFactorGenericParameters PARAMETERS =
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F;
  private static final RationalTwoFactorFormulas FORMULAS = 
      RationalTwoFactorFormulas.DEFAULT;

  private final static double TOLERANCE = 1.0E-10;

  public void swap_coefficients() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    ResolvedSwap swap = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VALUATION_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA).getProduct();
    double[] cComputed = FORMULAS.swapCoefficients(swap, MULTICURVE_EUR, PARAMETERS);
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
  
}
