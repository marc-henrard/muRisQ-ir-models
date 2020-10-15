/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
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
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolator;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductExplicitApproxPricer;

/**
 * Tests {@link LmmdddSwaptionRootBachelierVolatilityNCalibrator}.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionRootBachelierVolatilityNCalibratorTest {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;
  private static final LocalDate VALUATION_DATE = MULTICURVE_EUR.getValuationDate();
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  
  /* LMM one-factor */
  private static final double MEAN_REVERTION = 0.01;
  private static final double HW_SIGMA = 0.01;
  private static final List<LocalDate> IBOR_DATES = new ArrayList<>();
  static {
    ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(VALUATION_DATE, Tenor.TENOR_30Y, BuySell.BUY, 1.0, 0.0d, REF_DATA).resolve(REF_DATA);
    ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
    IBOR_DATES.add(iborLeg.get(0).getStartDate());
    for (SwapPaymentPeriod period : iborLeg) {
      IBOR_DATES.add(period.getEndDate());
    }
  }
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMM_HW_START = 
      LmmdddUtils.lmmHw(MEAN_REVERTION, HW_SIGMA, IBOR_DATES,
      EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
      VALUATION_ZONE, VALUATION_TIME, REF_DATA);

  /* LMM two-factor */
  private static final double VOL2_LEVEL_1 = 0.09;
  private static final double VOL2_ANGLE = Math.PI * 0.5;
  private static final double VOL2_LEVEL_2 = 0.06;
  private static final double DISPLACEMENT = 0.06; // 5% rate displacement
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMM_2F_START =
      LmmdddUtils.lmm2Angle(MEAN_REVERTION, VOL2_LEVEL_1, VOL2_ANGLE, VOL2_LEVEL_2, DISPLACEMENT,
          IBOR_DATES, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
          VALUATION_ZONE, VALUATION_TIME, REF_DATA);

  /* Pricer */
  private static final DiscountingSwapProductPricer PRICER_SWAP =
      DiscountingSwapProductPricer.DEFAULT;
  private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_SWAPTION_LMM_APPROX =
      LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;

  /* Swaption description */
  private static final double NOTIONAL = 1_000_000.0d;

  /* Calibration */
  private static final CurveInterpolator INTERPOLATOR = CurveInterpolators.LINEAR;
  private static final CurveExtrapolator EXTRAPOLATOR_LEFT = CurveExtrapolators.FLAT;
  private static final CurveExtrapolator EXTRAPOLATOR_RIGHT = CurveExtrapolators.FLAT;
  private static final LmmdddSwaptionRootBachelierVolatilityNCalibrator LMM_CALIBRATOR_1F =
      LmmdddSwaptionRootBachelierVolatilityNCalibrator.of(LMM_HW_START, INTERPOLATOR, EXTRAPOLATOR_LEFT, EXTRAPOLATOR_RIGHT);
  private static final LmmdddSwaptionRootBachelierVolatilityNCalibrator LMM_CALIBRATOR_2F =
      LmmdddSwaptionRootBachelierVolatilityNCalibrator.of(LMM_2F_START, INTERPOLATOR, EXTRAPOLATOR_LEFT, EXTRAPOLATOR_RIGHT);
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_APPROX_IV = within(1.0E-8);
  private static final boolean PRINT_DETAILS = false;

  /* Test calibration with one factor volatilities. ATM swaptions */
  @Test
  public void calibration_hw_like() {
    if (PRINT_DETAILS) {
      System.out.println("Calibration 1-factor");
    }
    calibration(LMM_CALIBRATOR_1F);
  }

  /* Test calibration with two-factor volatilities. */
  @Test
  public void calibration_2factor() {
    if (PRINT_DETAILS) {
      System.out.println("Calibration 2-factor");
    }
    calibration(LMM_CALIBRATOR_2F);
  }
  
  private void calibration(LmmdddSwaptionRootBachelierVolatilityNCalibrator calibrator) {
    Period[][] expiries = // dimension tests-calibration set
        new Period[][] {
            {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)},
            {Period.ofMonths(12), Period.ofMonths(36), Period.ofMonths(60), Period.ofMonths(120)}
        };
    Tenor[][] tenors = new Tenor[][] {
        {Tenor.TENOR_2Y, Tenor.TENOR_10Y, Tenor.TENOR_15Y},
        {Tenor.TENOR_10Y, Tenor.TENOR_10Y, Tenor.TENOR_10Y, Tenor.TENOR_10Y}
    };
    double[][] impliedVolatilities = {
        {0.0100, 0.0090, 0.0080},
        {0.0099, 0.0095, 0.0092, 0.0085}
    };
    for (int loopcal = 0; loopcal < expiries.length; loopcal++) {
      List<ResolvedSwaption> swaptions = new ArrayList<>();
      for (int loopswpt = 0; loopswpt < expiries[loopcal].length; loopswpt++) {
        LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopcal][loopswpt]));
        ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenors[loopcal][loopswpt], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA)
            .resolve(REF_DATA);
        double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
        SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
            .createTrade(expiryDate, tenors[loopcal][loopswpt], BuySell.BUY, NOTIONAL, parRate, REF_DATA);
        Swaption swaption = Swaption.builder()
            .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
            .longShort(LongShort.LONG)
            .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
            .underlying(swap.getProduct()).build();
        swaptions.add(swaption.resolve(REF_DATA));
      } // end loopswpt
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersCalibrated =
          calibrator.calibrate(swaptions, DoubleArray.ofUnsafe(impliedVolatilities[loopcal]), MULTICURVE_EUR);
      for (int loopswpt = 0; loopswpt < expiries[loopcal].length; loopswpt++) {
        double ivAfterCalibration = PRICER_SWAPTION_LMM_APPROX
            .impliedVolatilityBachelier(swaptions.get(loopswpt), MULTICURVE_EUR, parametersCalibrated);
        if (PRINT_DETAILS) {
            System.out.println(expiries[loopcal][loopswpt].toString() + tenors[loopcal][loopswpt] 
                + ", " + ivAfterCalibration + ", " + impliedVolatilities[loopcal][loopswpt] 
                + ", " + (ivAfterCalibration - impliedVolatilities[loopcal][loopswpt]));
        }
        assertThat(ivAfterCalibration).isEqualTo(impliedVolatilities[loopcal][loopswpt], TOLERANCE_APPROX_IV); // Compare implied volatilities
      }
    } // end loopcal
    
  }

  /* Test exception for swaptions in wrong order. */
  @Test
  public void calibration_swaption_order() {
    Period[] expiries = // dimension tests-calibration set
        new Period[]{Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
    Tenor[] tenors = new Tenor[]{Tenor.TENOR_15Y, Tenor.TENOR_2Y, Tenor.TENOR_10Y, };
    double[] impliedVolatilities = {0.0100, 0.0090, 0.0080};
    List<ResolvedSwaption> swaptions = new ArrayList<>();
    for (int loopswpt = 0; loopswpt < expiries.length; loopswpt++) {
      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopswpt]));
      ResolvedSwapTrade swap0 = EUR_FIXED_1Y_EURIBOR_3M
          .createTrade(expiryDate, tenors[loopswpt], BuySell.BUY, NOTIONAL, 0.0d, REF_DATA)
          .resolve(REF_DATA);
      double parRate = PRICER_SWAP.parRate(swap0.getProduct(), MULTICURVE_EUR);
      SwapTrade swap = EUR_FIXED_1Y_EURIBOR_3M
          .createTrade(expiryDate, tenors[loopswpt], BuySell.BUY, NOTIONAL, parRate, REF_DATA);
      Swaption swaption = Swaption.builder()
          .expiryDate(AdjustableDate.of(expiryDate)).expiryTime(VALUATION_TIME).expiryZone(VALUATION_ZONE)
          .longShort(LongShort.LONG)
          .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
          .underlying(swap.getProduct()).build();
      swaptions.add(swaption.resolve(REF_DATA));
    } // end loopswpt
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> LMM_CALIBRATOR_1F.calibrate(swaptions, DoubleArray.ofUnsafe(impliedVolatilities), MULTICURVE_EUR));
  }

}
