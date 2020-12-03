/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.capfloor;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.capfloor.IborCapFloorLeg;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorLeg;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.IborRateCalculation;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;

/**
 * Tests of {@link HullWhiteCapFloorLegPricer}.
 * 
 * @author Marc Henrard
 */
public class HullWhiteCapFloorLegPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");

  // Hull-White model parameters
  private static final double MEAN_REVERSION = 0.01;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.01, 0.011, 0.012, 0.013, 0.014);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(0.5, 1.0, 2.0, 5.0);
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
          MODEL_PARAMETERS, ACT_365F, VALUATION_DATE.atTime(VALUATION_TIME).atZone(VALUATION_ZONE));

  private static final HullWhiteCapletFloorletPeriodPricer PRICER_CAPLET_HW =
      HullWhiteCapletFloorletPeriodPricer.DEFAULT;
  private static final HullWhiteCapFloorLegPricer PRICER_CAP_LEG_HW =
      HullWhiteCapFloorLegPricer.DEFAULT;
  
  /* Descriptions of cap/floor */
  private static final Period[] EXPIRIES_PER = new Period[] {
      Period.ofYears(2), Period.ofYears(10)};
  private static final int NB_EXPIRIES = EXPIRIES_PER.length;
  private static final double[] STRIKES = new double[] {-0.0025, 0.0100, 0.0200};
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;

  /* Load and calibrate curves */
  private static final ImmutableRatesProvider MULTICURVE = MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  
  /* Constants */
  private static final Offset<Double> TOLERANCE_PV = offset(1.0E-2);
  private static final Double TOLERANCE_SENSI_RATE = 1.0E-2;

  /* Test cap/floor as sum of caplets/floorlets. Present value, currency exposure, present value sensitivity rates*/
  @Test
  public void present_value_leg_v_caplet() {
    IborRateCalculation iborCal = IborRateCalculation.of(EUR_EURIBOR_6M);
    for (int i = 0; i < NB_EXPIRIES; i++) {
      for (int k = 0; k < NB_STRIKES; k++) {
        PeriodicSchedule schedule = PeriodicSchedule.of(
            VALUATION_DATE.plus(EUR_EURIBOR_6M.getTenor()),
            VALUATION_DATE.plus(EXPIRIES_PER[i]),
            Frequency.P6M,
            BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA),
            StubConvention.NONE,
            RollConventions.NONE);
        ResolvedIborCapFloorLeg leg = IborCapFloorLeg.builder()
            .calculation(iborCal)
            .capSchedule(ValueSchedule.of(STRIKES[k]))
            .currency(EUR)
            .notional(ValueSchedule.of(NOTIONAL))
            .paymentSchedule(schedule)
            .payReceive(PayReceive.RECEIVE).build().resolve(REF_DATA);
        CurrencyAmount pvComputed = PRICER_CAP_LEG_HW.presentValue(leg, MULTICURVE, HW_PROVIDER);
        CurrencyAmount pvExpected = CurrencyAmount.of(EUR, 0.0);
        for(IborCapletFloorletPeriod period: leg.getCapletFloorletPeriods()) {
          pvExpected = pvExpected.plus(PRICER_CAPLET_HW.presentValue(period, MULTICURVE, HW_PROVIDER));
        }
        assertThat(pvComputed.getCurrency()).isEqualTo(EUR);
        assertThat(pvComputed.getAmount()).isCloseTo(pvExpected.getAmount(), TOLERANCE_PV);
        MultiCurrencyAmount ceComputed = PRICER_CAP_LEG_HW.currencyExposure(leg, MULTICURVE, HW_PROVIDER);
        assertThat(ceComputed.getCurrencies().size()).isEqualTo(1);
        assertThat(ceComputed.contains(EUR)).isTrue();
        assertThat(ceComputed.getAmount(EUR).getAmount()).isCloseTo(pvExpected.getAmount(), TOLERANCE_PV);
        PointSensitivityBuilder ptsComputed = PRICER_CAP_LEG_HW.presentValueSensitivityRates(leg, MULTICURVE, HW_PROVIDER);
        PointSensitivityBuilder ptsExpected = PointSensitivityBuilder.none();
        for(IborCapletFloorletPeriod period: leg.getCapletFloorletPeriods()) {
          ptsExpected = ptsExpected.combinedWith(PRICER_CAPLET_HW.presentValueSensitivityRates(period, MULTICURVE, HW_PROVIDER));
        }
        CurrencyParameterSensitivities psComputed = MULTICURVE.parameterSensitivity(ptsComputed.build());
        CurrencyParameterSensitivities psExpected = MULTICURVE.parameterSensitivity(ptsExpected.build());
        assertThat(psComputed.equalWithTolerance(psExpected, TOLERANCE_SENSI_RATE)).isTrue();
      }
    }
  }

}
