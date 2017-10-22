/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod.Builder;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.IborRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableFixedIborSwapConvention;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.Swaption;

import marc.henrard.risq.model.dataset.RationalTwoFactorParameters20151120DataSet;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorGenericParameters;
import marc.henrard.risq.pricer.dataset.MulticurveEur20151120DataSet;
import marc.henrard.risq.pricer.swaption.RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer;

/**
 * Tests of {@link RationalTwoFactorSwaptionPhysicalProductPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalTwoFactorCapletFloorletPeriodSemiExplicitNiPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.findValue(EUTA).get();

  private static final RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer PRICER_CAP_2_NI =
      RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer.DEFAULT; 
  private static final RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer PRICER_CAP_S_EX =
      RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer.DEFAULT; 
  private static final int NB_STEP_HIGH = 50; // Can be increased to 100 to improve TOLERANCE_PV_NI
  private static final RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer PRICER_CAP_2_NI_HIGH =
      new RationalTwoFactorCapletFloorletPeriodNumericalIntegrationPricer(NB_STEP_HIGH); // High precision for verification
  private static final RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer PRICER_SWPT_S_EX =
      RationalTwoFactorSwaptionPhysicalProductSemiExplicitPricer.DEFAULT; 

  
  /* Descriptions of swaptions */
  private static final Period[] EXPIRIES_PER = new Period[] {
      Period.ofMonths(3), Period.ofYears(2), Period.ofYears(10)};
  private static final int NB_EXPIRIES = EXPIRIES_PER.length;
  private static final double[] STRIKES = new double[] {-0.0025, 0.0100, 0.0200};
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;

  /* Load and calibrate curves */
  public static final ImmutableRatesProvider MULTICURVE = MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;
  
  /* Rational model data */
  public static final LocalTime LOCAL_TIME = LocalTime.of(9, 29);
  public static final ZoneId ZONE_ID = ZoneId.of("Europe/London");
  private static final RationalTwoFactorGenericParameters RATIONAL_2F = 
      RationalTwoFactorParameters20151120DataSet.rational2Factor(LOCAL_TIME, ZONE_ID);
  
  /* Constants */
  private static final double TOLERANCE_PV_PARITY = 1.0E-1;
  private static final double TOLERANCE_PV_NI = 1.5;

  /* Test payer/receiver parity. */
  public void present_value_payer_receiver_parity() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true);
        IborCapletFloorletPeriod floorletShort = capletFloorlet(-NOTIONAL, comp, STRIKES[k], false);
        double pvUnderlying = MULTICURVE.discountFactor(EUR, comp.getMaturityDate()) 
             * (MULTICURVE.iborIndexRates(EUR_EURIBOR_6M).rate(comp.getObservation()) - STRIKES[k])
             * capletLong.getYearFraction() * NOTIONAL;
        double pvNumIntegPayLong =
            PRICER_CAP_2_NI.presentValue(capletLong, MULTICURVE, RATIONAL_2F).getAmount();
        double pvNumIntegRecShort =
            PRICER_CAP_2_NI.presentValue(floorletShort, MULTICURVE, RATIONAL_2F).getAmount();
        assertEquals(pvNumIntegPayLong + pvNumIntegRecShort, pvUnderlying, TOLERANCE_PV_PARITY,
            "Payer/receiver parity: " + EXPIRIES_PER[i] + STRIKES[k]);
        double pvSemiExplPayLong =
            PRICER_CAP_S_EX.presentValue(capletLong, MULTICURVE, RATIONAL_2F).getAmount();
        double pvSemiExplRecShort =
            PRICER_CAP_S_EX.presentValue(floorletShort, MULTICURVE, RATIONAL_2F).getAmount();
        assertEquals(pvSemiExplPayLong + pvSemiExplRecShort, pvUnderlying, TOLERANCE_PV_PARITY,
            "Payer/receiver parity: " + EXPIRIES_PER[i] +  STRIKES[k]);
      }
    }
  }

  /* Test 2 factors NI v semi-explicit. */
  public void present_value_semi_explicit_v_numerical_integration() {
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true);
        double pvNumInteg2PayLong = PRICER_CAP_2_NI_HIGH.presentValue(capletLong, MULTICURVE, RATIONAL_2F).getAmount();
        double pvSemiExpliPayLong = PRICER_CAP_S_EX.presentValue(capletLong, MULTICURVE, RATIONAL_2F).getAmount();
        assertEquals(pvNumInteg2PayLong, pvSemiExpliPayLong, TOLERANCE_PV_NI,
            "2F NI v 2F Semi-explicit: " + EXPIRIES_PER[i] + STRIKES[k]);
      }
    }
  }

  /* Test pv of caplet v one period swaption. */
  public void present_value_caplet_swaption() {
    FixedRateSwapLegConvention fixedLegConvention =
        FixedRateSwapLegConvention.of(EUR, DayCounts.ACT_360, Frequency.P6M,
            BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA));
    FixedIborSwapConvention swapConvention = ImmutableFixedIborSwapConvention.of("TEST",
        fixedLegConvention, IborRateSwapLegConvention.of(EUR_EURIBOR_6M));
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES_PER[i]));
        IborRateComputation comp = IborRateComputation.of(EUR_EURIBOR_6M, fixingDate, REF_DATA);
        IborCapletFloorletPeriod capletLong = capletFloorlet(NOTIONAL, comp, STRIKES[k], true);
        SwapTrade swapPayer = swapConvention.createTrade(
            fixingDate, Tenor.TENOR_6M, BuySell.BUY, NOTIONAL, STRIKES[k], REF_DATA);
        Swaption swptPayerLong = Swaption.builder()
            .longShort(LongShort.LONG)
            .expiryDate(AdjustableDate.of(fixingDate)).expiryTime(LocalTime.of(11, 00))
            .expiryZone(ZoneId.of("Europe/Brussels"))
            .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT)
            .underlying(swapPayer.getProduct()).build();
        double pvCaplet = PRICER_CAP_S_EX.presentValue(capletLong, MULTICURVE, RATIONAL_2F).getAmount();
        double pvSwpt =
            PRICER_SWPT_S_EX.presentValue(swptPayerLong.resolve(REF_DATA), MULTICURVE, RATIONAL_2F).getAmount();
        assertEquals(pvCaplet, pvSwpt, TOLERANCE_PV_NI,
            "Caplet v Swaption: " + EXPIRIES_PER[i] + STRIKES[k]);
      }
    }
  }
  
  private IborCapletFloorletPeriod capletFloorlet(
      double notional, IborRateComputation comp, double strike, boolean isCap) {
    Builder builder = IborCapletFloorletPeriod.builder()
        .currency(EUR)
        .notional(notional)
        .startDate(comp.getEffectiveDate())
        .endDate(comp.getMaturityDate())
        .paymentDate(comp.getMaturityDate())
        .yearFraction(comp.getYearFraction())
        .iborRate(comp);
    if(isCap) {
      builder.caplet(strike);
    } else {
      builder.floorlet(strike);
    }
    return builder.build();
  }
  
}
