/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap;

import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueAdjustment;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.basics.value.ValueStep;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.CompoundingMethod;
import com.opengamma.strata.product.swap.IborRatchetRateCalculation;
import com.opengamma.strata.product.swap.NotionalSchedule;
import com.opengamma.strata.product.swap.PaymentSchedule;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLeg;

/**
 * Tests {@link IborRatchetRateCalculation}.
 * 
 * @author Marc Henrard
 */
public class IborRatchetRateCalculationTest {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final List<ValueSchedule> COEFFICIENTS = new ArrayList<>();
  static { // Start with IBOR then 50% previous + 50% current IBOR , floor at 1%, cap at 2*IBOR
    COEFFICIENTS.add(ValueSchedule.of(0, ImmutableList.of(ValueStep.of(1, ValueAdjustment.ofDeltaAmount(0.50))))); // main previous
    COEFFICIENTS.add(ValueSchedule.of(1.0, ImmutableList.of(ValueStep.of(1, ValueAdjustment.ofDeltaAmount(-0.50))))); // main Ibor
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // main fixed
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor previous
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap Ibor
    COEFFICIENTS.add(ValueSchedule.of(0.01)); // floor fixed
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap previous
    COEFFICIENTS.add(ValueSchedule.of(2.0)); // cap Ibor
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap fixed.
  }
  
  private static final List<ValueSchedule> COEFFICIENTS_CAPPED = new ArrayList<>();
  static { // IBOR capped at 3% = IBOR - cap 3%
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // main previous 
    COEFFICIENTS.add(ValueSchedule.of(1.0)); // main Ibor = IBOR with weight 1
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // main fixed, 
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor previous, 
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // floor Ibor, 
    COEFFICIENTS.add(ValueSchedule.of(-100.0)); // floor fixed = no floor
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap pervious
    COEFFICIENTS.add(ValueSchedule.ALWAYS_0); // cap Ibor
    COEFFICIENTS.add(ValueSchedule.of(0.03)); // cap fixed.
  }
  
  @Test
  public void createAccrualPeriods() {
    LocalDate startDate = LocalDate.of(2020, 2, 28);
    LocalDate endDate = LocalDate.of(2022, 2, 28);
    Frequency frequency = Frequency.P3M;
    BusinessDayAdjustment businessDayAdjustment = BusinessDayAdjustment
        .of(BusinessDayConventions.MODIFIED_FOLLOWING, HolidayCalendarIds.USNY);
    Schedule accrualSchedule = PeriodicSchedule
        .of(startDate, endDate, frequency, businessDayAdjustment, StubConvention.NONE, RollConventions.NONE)
        .createSchedule(REF_DATA);
    IborRatchetRateCalculation ratchetCalculation = 
        IborRatchetRateCalculation.of(USD_LIBOR_3M, COEFFICIENTS);
    ImmutableList<RateAccrualPeriod> ratchetAccruals = 
        ratchetCalculation.createAccrualPeriods(accrualSchedule, null, REF_DATA);
    System.out.println(ratchetAccruals);
  }
  
  @Test
  public void createSwapLeg() {
    double notional = 1_000_000;
    IborRatchetRateCalculation ratchetCalculation = 
        IborRatchetRateCalculation.of(USD_LIBOR_3M, COEFFICIENTS);
    LocalDate startDate = LocalDate.of(2020, 2, 28);
    LocalDate endDate = LocalDate.of(2022, 2, 28);
    Frequency frequency = Frequency.P3M;
    BusinessDayAdjustment businessDayAdjustment = BusinessDayAdjustment
        .of(BusinessDayConventions.MODIFIED_FOLLOWING, HolidayCalendarIds.USNY);
    PeriodicSchedule accrualSchedule = PeriodicSchedule
        .of(startDate, endDate, frequency, businessDayAdjustment, StubConvention.NONE, RollConventions.NONE);
    SwapLeg leg = RateCalculationSwapLeg
        .builder()
        .payReceive(PayReceive.PAY)
        .accrualSchedule(accrualSchedule)
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(frequency)
            .paymentDateOffset(DaysAdjustment.NONE)
            .compoundingMethod(CompoundingMethod.NONE)
            .build())
        .notionalSchedule(NotionalSchedule.builder()
            .currency(USD_LIBOR_3M.getCurrency())
            .finalExchange(false)
            .initialExchange(false)
            .amount(ValueSchedule.of(notional)).build())
        .calculation(ratchetCalculation)
        .build();
    ResolvedSwapLeg legResolved = leg.resolve(REF_DATA);
    System.out.println(legResolved);
  }
  

}
