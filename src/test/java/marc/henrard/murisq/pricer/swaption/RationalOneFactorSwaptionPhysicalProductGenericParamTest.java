/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.ParameterizedFunctionalCurve;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.ParameterizedData;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.generic.GenericParameterDateCurve;
import marc.henrard.murisq.model.generic.ParameterDateCurve;
import marc.henrard.murisq.model.rationalmulticurve.RationalOneFactorGenericParameters;
import marc.henrard.murisq.model.rationalmulticurve.RationalOneFactorSimpleHWShapeParameters;
import marc.henrard.murisq.pricer.swaption.RationalOneFactorSwaptionPhysicalProductExplicitPricer;

/**
 * Tests {@link RationalOneFactorSwaptionPhysicalProductExplicitPricer} 
 * with specific and generic parameter descriptions.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorSwaptionPhysicalProductGenericParamTest {

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
  
  /* Rational model data: HW shaped b0 */
  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  private static final DiscountFactors DF = MULTICURVE_EUR.discountFactors(EUR);
  private static final RationalOneFactorSimpleHWShapeParameters MODEL_HWSHAPED = 
      RationalOneFactorSimpleHWShapeParameters.of(A, B_0_0, ETA, KAPPA, TIME_MEAS, DF);

  private static final Curve CURVE_B0 =
      ParameterizedFunctionalCurve.of(DefaultCurveMetadata.of("B0"),
          DoubleArray.of(A, B_0_0, ETA, KAPPA),
          (p, x) -> (p.get(1) - p.get(2) / (p.get(0) * p.get(3)) 
              * (1.0d - Math.exp(-p.get(3) * x))) * DF.discountFactor(x),
          (a, x) -> 0.0d, (a, x) -> DoubleArray.EMPTY);
  private static final ParameterDateCurve B0 =
      GenericParameterDateCurve.of(ScaledSecondTime.DEFAULT, CURVE_B0, VALUATION_DATE);
  private static final ParameterDateCurve B1_1 =
      new TestParameterDateCurve(EUR_EURIBOR_6M, REF_DATA, B0);
  private static final List<IborIndex> B1_INDICES =
      ImmutableList.of(EUR_EURIBOR_6M);
  private static final List<ParameterDateCurve> B1_CURVES =
      ImmutableList.of(B1_1);
  private static final RationalOneFactorGenericParameters MODEL_GENERIC =
      RationalOneFactorGenericParameters.of(EUR, A, B0, B1_INDICES, B1_CURVES, TIME_MEAS, VALUATION_DATE);
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
  
  /* Tolerance */
  private static final double TOLERANCE_PV = 5.0E-2;

  public void present_value() {
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
              PRICER_SWAPTION_RATIONAL_EXPLICIT.presentValue(swpt, MULTICURVE_EUR, MODEL_HWSHAPED).getAmount();
          double pvGeneric = 
              PRICER_SWAPTION_RATIONAL_EXPLICIT.presentValue(swpt, MULTICURVE_EUR, MODEL_GENERIC).getAmount();
          assertEquals(pvExplicit, pvGeneric, TOLERANCE_PV);
        }
      }
    }
  }
  
}

/*
 * Custom made curve for tests.
 */
class TestParameterDateCurve implements ParameterDateCurve{
  
  private final IborIndex index;
  private final ReferenceData ref;
  private final ParameterDateCurve b0;
  
  public TestParameterDateCurve(IborIndex index, ReferenceData ref, ParameterDateCurve b0) {
    this.index = index;
    this.ref = ref;
    this.b0 = b0;
  }

  @Override
  public double parameterValue(LocalDate date) {
    LocalDate effectiveDate = index.calculateEffectiveFromFixing(date, ref);
    LocalDate maturityDate = index.calculateMaturityFromEffective(effectiveDate, ref);
    double delta = index.getDayCount().yearFraction(effectiveDate, maturityDate);
    return (b0.parameterValue(effectiveDate) - b0.parameterValue(maturityDate)) / delta;
  }

  @Override
  public PointSensitivityBuilder parameterValueCurveSensitivity(LocalDate date) {
    throw new IllegalArgumentException("not implemented");
  }

  @Override
  public int getParameterCount() {
    return b0.getParameterCount();
  }

  @Override
  public double getParameter(int parameterIndex) {
    return b0.getParameter(parameterIndex);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    return b0.getParameterMetadata(parameterIndex);
  }

  @Override
  public ParameterizedData withParameter(int parameterIndex, double newValue) {
    throw new IllegalArgumentException("not implemented");
  }
  
}
