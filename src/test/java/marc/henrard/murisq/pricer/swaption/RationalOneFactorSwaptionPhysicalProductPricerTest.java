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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.surface.ConstantSurface;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.BlackSwaptionExpiryTenorVolatilities;
import com.opengamma.strata.pricer.swaption.BlackSwaptionPhysicalProductPricer;
import com.opengamma.strata.pricer.swaption.NormalSwaptionExpiryTenorVolatilities;
import com.opengamma.strata.pricer.swaption.NormalSwaptionPhysicalProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.rationalmulticurve.RationalOneFactorSimpleHWShapeParameters;
import marc.henrard.murisq.pricer.swaption.RationalOneFactorSwaptionPhysicalProductExplicitPricer;
import marc.henrard.murisq.pricer.swaption.SingleCurrencyModelSwaptionPhysicalProductPricer;

/**
 * Tests {@link SingleCurrencyModelSwaptionPhysicalProductPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalOneFactorSwaptionPhysicalProductPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  private static final ZonedDateTime VALUATION_DATE_TIME = 
      VALUATION_DATE.atTime(VALUATION_TIME).atZone(VALUATION_ZONE);
  private static final DayCount DAYCOUNT_DEFAULT = DayCounts.ACT_365F;

  /* Load and calibrate curves */
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  private static final ImmutableRatesProvider MULTICURVE_EUR_POS = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_POS_20151120;
  
  /* Rational model data: HW shaped b0 */
  private static final double A = 0.75;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  private static final RationalOneFactorSimpleHWShapeParameters MODEL_SIMPLE = 
      RationalOneFactorSimpleHWShapeParameters.of(A, B_0_0, ETA, KAPPA, TIME_MEAS, MULTICURVE_EUR.discountFactors(EUR));

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
  private static final BlackSwaptionPhysicalProductPricer PRICER_SWAPTION_BLACK = 
      BlackSwaptionPhysicalProductPricer.DEFAULT;
  private static final NormalSwaptionPhysicalProductPricer PRICER_SWAPTION_BACHELIER = 
      NormalSwaptionPhysicalProductPricer.DEFAULT;
  
  private static final double TOLERANCE_PV = 1.0E-2;
  private static final double TOLERANCE_IV = 1.0E-6;
  
  /* Test the implied volatility for the Black/log-normal model. 
   * Data is artificially high to have positive strikes and forwards. */
  public void black() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int j = 0; j < NB_TENORS; j++) {
        for (int k = 0; k < NB_MONEYNESS; k++) {
          SwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_6M.createTrade(
              VALUATION_DATE, EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, 0, REF_DATA);
          ResolvedSwap swap0Resolved = swap0.getProduct().resolve(REF_DATA);
          double parRate = PRICER_SWAP.parRate(swap0Resolved, MULTICURVE_EUR_POS);
          LocalDate expiryDate = EUR_EURIBOR_6M.calculateFixingFromEffective(swap0Resolved.getStartDate(), REF_DATA);
          SwapTrade swapPayer = EUR_FIXED_1Y_EURIBOR_6M.createTrade(VALUATION_DATE, 
              EXPIRIES_PER[i], Tenor.of(TENORS_PER[j]), BuySell.BUY, NOTIONAL, parRate + MONEYNESS[k], REF_DATA);
          ResolvedSwaption swpt = Swaption.builder()
              .longShort(LongShort.LONG)
              .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(LocalTime.NOON).expiryZone(ZoneOffset.UTC)
              .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
              .underlying(swapPayer.getProduct()).build().resolve(REF_DATA);
          double pvRational = 
              PRICER_SWAPTION_RATIONAL_EXPLICIT.presentValue(swpt, MULTICURVE_EUR_POS, MODEL_SIMPLE).getAmount();
          double iv =
              PRICER_SWAPTION_RATIONAL_EXPLICIT.impliedVolatilityBlack(swpt, MULTICURVE_EUR_POS, MODEL_SIMPLE);
          BlackSwaptionExpiryTenorVolatilities volatilities =
              BlackSwaptionExpiryTenorVolatilities.of(
                  EUR_FIXED_1Y_EURIBOR_6M,
                  VALUATION_DATE_TIME,
                  ConstantSurface.of(DefaultSurfaceMetadata.builder()
                      .surfaceName("Black-vol")
                      .xValueType(ValueType.YEAR_FRACTION)
                      .yValueType(ValueType.YEAR_FRACTION)
                      .zValueType(ValueType.BLACK_VOLATILITY)
                      .dayCount(DAYCOUNT_DEFAULT).build(),
                      iv));
          double pvBlack = 
              PRICER_SWAPTION_BLACK.presentValue(swpt, MULTICURVE_EUR_POS, volatilities).getAmount();
          assertEquals(pvRational, pvBlack, TOLERANCE_PV);
          /* Short swaption. */
          ResolvedSwaption swpt2 = swpt.toBuilder().longShort(LongShort.SHORT).build();
          double iv2 = PRICER_SWAPTION_RATIONAL_EXPLICIT
              .impliedVolatilityBlack(swpt2, MULTICURVE_EUR_POS, MODEL_SIMPLE);
          assertEquals(iv, iv2, TOLERANCE_IV);
        }
      }
    }
  }
  
  /* Test the implied volatility for the Bachelier/normal model. 
   * Data is artificially high to have positive strikes and forwards. */
  public void bachelier() {
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
          double pvRational = PRICER_SWAPTION_RATIONAL_EXPLICIT
              .presentValue(swpt, MULTICURVE_EUR, MODEL_SIMPLE).getAmount();
          double iv = PRICER_SWAPTION_RATIONAL_EXPLICIT
              .impliedVolatilityBachelier(swpt, MULTICURVE_EUR, MODEL_SIMPLE);
          NormalSwaptionExpiryTenorVolatilities volatilities =
              NormalSwaptionExpiryTenorVolatilities.of(
                  EUR_FIXED_1Y_EURIBOR_6M,
                  VALUATION_DATE_TIME,
                  ConstantSurface.of(DefaultSurfaceMetadata.builder()
                      .surfaceName("Bachelier-vol")
                      .xValueType(ValueType.YEAR_FRACTION)
                      .yValueType(ValueType.YEAR_FRACTION)
                      .zValueType(ValueType.NORMAL_VOLATILITY)
                      .dayCount(DAYCOUNT_DEFAULT).build(),
                      iv));
          double pvBachelier = 
              PRICER_SWAPTION_BACHELIER.presentValue(swpt, MULTICURVE_EUR, volatilities).getAmount();
          assertEquals(pvRational, pvBachelier, TOLERANCE_PV);
          /* Short swaption. */
          ResolvedSwaption swpt2 = swpt.toBuilder().longShort(LongShort.SHORT).build();
          double iv2 = PRICER_SWAPTION_RATIONAL_EXPLICIT
              .impliedVolatilityBachelier(swpt2, MULTICURVE_EUR, MODEL_SIMPLE);
          assertEquals(iv, iv2, TOLERANCE_IV);
        }
      }
    }
  }
  
}
