/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;

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
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;
import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorSimpleHWShapedParameters;

/**
 * Tests {@link RationalOneFactorFormulas}
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorFormulasTest {

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
  
  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
  private static final ZonedDateTime VAL_DATE_TIME = ZonedDateTime.of(2016, 8, 18, 11, 12, 13, 0, ZoneId.of("Europe/Brussels"));
  private static final LocalDate VAL_DATE = VAL_DATE_TIME.toLocalDate();
  
  private static final TimeMeasurement TIME_MEASURE = ScaledSecondTime.DEFAULT;
  
  private static final CurveInterpolator INTERPOLATOR_LINEAR = CurveInterpolators.LINEAR;
  private static final CurveName CURVE_NAME = CurveName.of("EUR-DSC");
  private static final CurveMetadata METADATA = Curves.zeroRates(CURVE_NAME, ACT_365F);
  private static final DoubleArray TIMES_ZR = DoubleArray.of(0, 0.25, 0.50, 1, 2.01, 5.02, 10.00);
  private static final DoubleArray ZR = DoubleArray.of(-0.0020, -0.0010, 0.0000, 0.0015, 0.0100, 0.0090, 0.0150);
  private static final InterpolatedNodalCurve ZERO_RATES = InterpolatedNodalCurve.of(METADATA, TIMES_ZR, ZR, INTERPOLATOR_LINEAR);
  private static final DiscountFactors DF = ZeroRateDiscountFactors.of(EUR, VAL_DATE, ZERO_RATES);
  
  private static final RationalOneFactorSimpleHWShapedParameters PARAMETERS =
      RationalOneFactorSimpleHWShapedParameters.of(A, B_0_0, ETA, KAPPA, TIME_MEASURE, DF, VAL_DATE_TIME);
  private static final RationalOneFactorFormulas FORMULAS = 
      RationalOneFactorFormulas.DEFAULT;

  private final static double TOLERANCE = 1.0E-10;

  public void swap_coefficients() {
    double fixedRate = 0.02d;
    double notional = 123456.78d;
    ResolvedSwap swap = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VAL_DATE, Period.ofYears(1), Tenor.TENOR_2Y, BuySell.BUY, notional, fixedRate, REF_DATA)
        .resolve(REF_DATA).getProduct();
    double[] cComputed = FORMULAS.swapCoefficients(swap, MULTICURVE_EUR, PARAMETERS);
    double[] cExpeted = new double[2];
    for(SwapPaymentPeriod period: swap.getLegs().get(0).getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      cExpeted[0] += -notional * fixedRate * accrualPeriod.getYearFraction() 
          * MULTICURVE_EUR.discountFactor(EUR, ratePeriod.getPaymentDate());
      cExpeted[1] += -notional * fixedRate * accrualPeriod.getYearFraction() *
          PARAMETERS.b0(ratePeriod.getPaymentDate());
    }
    for (SwapPaymentPeriod period : swap.getLegs().get(1).getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof IborRateComputation, "ibor");
      IborRateComputation obs = (IborRateComputation) accrualPeriod.getRateComputation();
      double df = MULTICURVE_EUR.discountFactor(EUR, ratePeriod.getPaymentDate());
      cExpeted[0] += notional * accrualPeriod.getYearFraction()
          * MULTICURVE_EUR.iborIndexRates(obs.getIndex()).rate(obs.getObservation()) * df;
      cExpeted[1] += notional * accrualPeriod.getYearFraction() *
          PARAMETERS.b1(obs.getObservation());
    }
    cExpeted[0] -= cExpeted[1];
    ArrayAsserts.assertArrayEquals(cExpeted, cComputed, TOLERANCE);
  }
  
}
