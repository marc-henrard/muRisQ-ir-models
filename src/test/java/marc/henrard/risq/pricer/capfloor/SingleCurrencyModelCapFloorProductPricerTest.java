/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.product.capfloor.IborCapFloor;
import com.opengamma.strata.product.capfloor.IborCapFloorLeg;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloor;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.IborRateCalculation;
import com.opengamma.strata.product.swap.SwapTrade;

import marc.henrard.risq.model.dataset.RationalTwoFactorParameters20151120DataSet;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorGenericParameters;
import marc.henrard.risq.pricer.dataset.MulticurveEur20151120DataSet;

/**
 * Tests {@link SingleCurrencyModelCapFloorProductPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class SingleCurrencyModelCapFloorProductPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = MulticurveEur20151120DataSet.VALUATION_DATE;
  private static final BusinessDayAdjustment BUSINESS_ADJ = BusinessDayAdjustment.of(
      BusinessDayConventions.MODIFIED_FOLLOWING, EUTA);
  public static final ImmutableRatesProvider MULTICURVE = MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120;

  private static final DiscountingSwapLegPricer PRICER_SWAP_LEG = DiscountingSwapLegPricer.DEFAULT;
  private static final RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer PRICER_CAPLET_S_EX =
      RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer.DEFAULT;
  private static final SingleCurrencyModelCapFloorLegPricer PRICER_LEG_S_EX =
      new SingleCurrencyModelCapFloorLegPricer(PRICER_CAPLET_S_EX);
  private static final SingleCurrencyModelCapFloorProductPricer PRICER_CAP_S_EX =
      new SingleCurrencyModelCapFloorProductPricer(PRICER_LEG_S_EX, PRICER_SWAP_LEG);
  
  /* Descriptions of swaptions */
  private static final Period[] MATURITIES_PER = new Period[] {
      Period.ofYears(1), Period.ofYears(5), Period.ofYears(10)};
  private static final int NB_MATURITIES = MATURITIES_PER.length;
  private static final double[] STRIKES = new double[] {-0.0025, 0.0100, 0.0200};
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;
  
  /* Rational model data */
  public static final LocalTime LOCAL_TIME = LocalTime.of(9, 29);
  public static final ZoneId ZONE_ID = ZoneId.of("Europe/London");
  private static final RationalTwoFactorGenericParameters RATIONAL_2F = 
      RationalTwoFactorParameters20151120DataSet.rational2Factor(LOCAL_TIME, ZONE_ID);
  
  /* Constants */
  private static final double TOLERANCE_PV = 1.0E-1;

  /* Tests present value as sum of legs. */
  public void present_value_product() {
    LocalDate spot6M = EUR_EURIBOR_6M.calculateMaturityFromFixing(VALUATION_DATE, REF_DATA);
    for (int i = 0; i < NB_MATURITIES; i++) {
      LocalDate maturity = spot6M.plus(MATURITIES_PER[i]);
      for (int k = 0; k < NB_STRIKES; k++) {
        PeriodicSchedule paySchedule =
            PeriodicSchedule.of(spot6M, maturity, Frequency.P6M, BUSINESS_ADJ, StubConvention.NONE,
                RollConventions.NONE);
        IborCapFloorLeg leg = IborCapFloorLeg.builder()
            .currency(EUR)
            .calculation(IborRateCalculation.of(EUR_EURIBOR_6M))
            .capSchedule(ValueSchedule.of(STRIKES[k]))
            .notional(ValueSchedule.of(NOTIONAL))
            .paymentSchedule(paySchedule)
            .payReceive(PayReceive.PAY).build();
        SwapTrade swapTrade = EUR_FIXED_1Y_EURIBOR_6M
            .createTrade(VALUATION_DATE, Tenor.of(MATURITIES_PER[i]), BuySell.SELL, NOTIONAL, 0.01, REF_DATA);
        IborCapFloor cap = IborCapFloor.of(leg, swapTrade.getProduct().getLegs().get(0));
        ResolvedIborCapFloor resolvedCap = cap.resolve(REF_DATA);
        MultiCurrencyAmount pvComputed = PRICER_CAP_S_EX.presentValue(resolvedCap, MULTICURVE, RATIONAL_2F);
        assertEquals(pvComputed.getCurrencies(), ImmutableSet.of(EUR));
        double pvExpected =
            PRICER_LEG_S_EX.presentValue(resolvedCap.getCapFloorLeg(), MULTICURVE, RATIONAL_2F).getAmount() +
                PRICER_SWAP_LEG.presentValue(resolvedCap.getPayLeg().get(), MULTICURVE).getAmount();
        assertEquals(pvComputed.getAmount(EUR).getAmount(), pvExpected, TOLERANCE_PV);
      }
    }
  }
  
}
