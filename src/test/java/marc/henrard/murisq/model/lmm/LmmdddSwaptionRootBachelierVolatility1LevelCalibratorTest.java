/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

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
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
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
 * Tests {@link LmmdddSwaptionRootBachelierVolatility1LevelCalibrator}.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionRootBachelierVolatility1LevelCalibratorTest {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_3M.getFixingCalendar());
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;
  private static final LocalDate VALUATION_DATE = MULTICURVE_EUR.getValuationDate();
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  
  /* LMM one-factor */
  private static final double MEAN_REVERTION = 0.02;
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
  private static final double DISPLACEMENT = 0.05; // 5% rate displacement
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
  private static final double IV_TARGET = 0.0075;
  private static final LmmdddSwaptionRootBachelierVolatility1LevelCalibrator LMM1_CALIBRATOR_1F =
      LmmdddSwaptionRootBachelierVolatility1LevelCalibrator.of(LMM_HW_START);
  private static final LmmdddSwaptionRootBachelierVolatility1LevelCalibrator LMM1_CALIBRATOR_2F =
      LmmdddSwaptionRootBachelierVolatility1LevelCalibrator.of(LMM_2F_START);
  private static final Offset<Double> TOLERANCE_APPROX_IV = within(1.0E-8);

  /* Test calibration with one factor volatilities. */
  @Test
  public void calibration_hw_like() {
    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
    Tenor[] tenors = new Tenor[] {Tenor.TENOR_1Y, Tenor.TENOR_10Y};
    double[] moneyness = new double[] {-0.0050, 0, 0.0100};
    for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopexp]));
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
          LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersCalibrated =
              LMM1_CALIBRATOR_1F.calibrate(swaptionResolved, IV_TARGET, MULTICURVE_EUR);
          double ivAfterCalibration = PRICER_SWAPTION_LMM_APPROX
              .impliedVolatilityBachelier(swaptionResolved, MULTICURVE_EUR, parametersCalibrated);
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + ivAfterCalibration + ", " + IV_TARGET + ", " + (ivAfterCalibration-IV_TARGET));
          assertThat(ivAfterCalibration).isEqualTo(IV_TARGET, TOLERANCE_APPROX_IV); // Compare implied volatilities
        } // end loopmoney
      } // end looptenor
    } // end loopexp
  }

  /* Test calibration with two-factor volatilities. */
  @Test
  public void calibration_2factor() {
    Period[] expiries = new Period[] {Period.ofMonths(6), Period.ofMonths(12), Period.ofMonths(60)};
    Tenor[] tenors = new Tenor[] {Tenor.TENOR_1Y, Tenor.TENOR_10Y};
    double[] moneyness = new double[] {-0.0050, 0, 0.0100};
    for (int loopexp = 0; loopexp < expiries.length; loopexp++) {
      LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiries[loopexp]));
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
          LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersCalibrated =
              LMM1_CALIBRATOR_2F.calibrate(swaptionResolved, IV_TARGET, MULTICURVE_EUR);
          double ivAfterCalibration = PRICER_SWAPTION_LMM_APPROX
              .impliedVolatilityBachelier(swaptionResolved, MULTICURVE_EUR, parametersCalibrated);
//          System.out.println(expiries[loopexp].toString() + tenors[looptenor] + moneyness[loopmoney] 
//              + ", " + ivAfterCalibration + ", " + IV_TARGET + ", " + (ivAfterCalibration-IV_TARGET));
          assertThat(ivAfterCalibration).isEqualTo(IV_TARGET, TOLERANCE_APPROX_IV); // Compare implied volatilities
        } // end loopmoney
      } // end looptenor
    } // end loopexp
  }

}
