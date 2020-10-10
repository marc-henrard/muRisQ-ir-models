/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap;

import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.Period;
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
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueAdjustment;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.basics.value.ValueStep;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.CompoundingMethod;
import com.opengamma.strata.product.swap.IborRatchetRateCalculation;
import com.opengamma.strata.product.swap.NotionalSchedule;
import com.opengamma.strata.product.swap.PaymentSchedule;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.murisq.product.rate.IborRatchetRateComputation;

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
  
//  private static final List<ValueSchedule> COEFFICIENTS_CAPPED = new ArrayList<>();
//  static { // IBOR capped at 3% = IBOR - cap 3%
//    COEFFICIENTS_CAPPED.add(ValueSchedule.ALWAYS_0); // main previous 
//    COEFFICIENTS_CAPPED.add(ValueSchedule.of(1.0)); // main Ibor = IBOR with weight 1
//    COEFFICIENTS_CAPPED.add(ValueSchedule.ALWAYS_0); // main fixed, 
//    COEFFICIENTS_CAPPED.add(ValueSchedule.ALWAYS_0); // floor previous, 
//    COEFFICIENTS_CAPPED.add(ValueSchedule.ALWAYS_0); // floor Ibor, 
//    COEFFICIENTS_CAPPED.add(ValueSchedule.of(-100.0)); // floor fixed = no floor
//    COEFFICIENTS_CAPPED.add(ValueSchedule.ALWAYS_0); // cap previous
//    COEFFICIENTS_CAPPED.add(ValueSchedule.ALWAYS_0); // cap Ibor
//    COEFFICIENTS_CAPPED.add(ValueSchedule.of(0.03)); // cap fixed.
//  }
  
  @Test
  public void createAccrualPeriods() {
    LocalDate startDate = LocalDate.of(2020, 2, 28);
    int nbYear = 2;
    Period tenor = Period.ofYears(nbYear);
    LocalDate endDate = startDate.plus(tenor);
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
    assertThat(ratchetAccruals.size()).isEqualTo(nbYear * 4); // quarterly
    for (int loopperiod = 0; loopperiod < ratchetAccruals.size(); loopperiod++) {
      RateAccrualPeriod periodRatchet = ratchetAccruals.get(loopperiod);
      SchedulePeriod periodSchedule = accrualSchedule.getPeriod(loopperiod);
      assertThat(periodRatchet.getYearFraction())
          .isEqualTo(periodSchedule.yearFraction(ratchetCalculation.getDayCount(), accrualSchedule));
      assertThat(periodRatchet.getStartDate()).isEqualTo(periodSchedule.getStartDate());
      assertThat(periodRatchet.getEndDate()).isEqualTo(periodSchedule.getEndDate());
      assertThat(periodRatchet.getGearing()).isEqualTo(1.0);
      assertThat(periodRatchet.getSpread()).isEqualTo(0.0);
      assertThat(periodRatchet.getRateComputation() instanceof IborRatchetRateComputation)
          .isTrue();
      IborRatchetRateComputation computationRatchet = (IborRatchetRateComputation) periodRatchet.getRateComputation();
      assertThat(computationRatchet.getIndex()).isEqualTo(USD_LIBOR_3M);
      if (loopperiod == 0) {
        assertThat(computationRatchet.getMainCoefficients()).isEqualTo(DoubleArray.of(0.0, 1.0, 0.0));
        assertThat(computationRatchet.getFloorCoefficients()).isEqualTo(DoubleArray.of(0.0, 0.0, 0.01));
        assertThat(computationRatchet.getCapCoefficients()).isEqualTo(DoubleArray.of(0.0, 2.0, 0.0));
      } else {
        assertThat(computationRatchet.getMainCoefficients()).isEqualTo(DoubleArray.of(0.50, 0.50, 0.0));
        assertThat(computationRatchet.getFloorCoefficients()).isEqualTo(DoubleArray.of(0.0, 0.0, 0.01));
        assertThat(computationRatchet.getCapCoefficients()).isEqualTo(DoubleArray.of(0.0, 2.0, 0.0));
      }
    }
  }

  @Test
  public void createSwapLeg() {
    double notional = 1_000_000;
    IborRatchetRateCalculation ratchetCalculation =
        IborRatchetRateCalculation.of(USD_LIBOR_3M, COEFFICIENTS);
    LocalDate startDate = LocalDate.of(2020, 2, 28);
    int nbYear = 2;
    Period tenor = Period.ofYears(nbYear);
    LocalDate endDate = startDate.plus(tenor);
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
    assertThat(legResolved.getPaymentPeriods().size()).isEqualTo(nbYear * 4); // quarterly
    assertThat(legResolved.getPaymentEvents().size()).isEqualTo(0);
    assertThat(legResolved.getType()).isEqualTo(SwapLegType.OTHER);
    assertThat(legResolved.getPayReceive()).isEqualTo(PayReceive.PAY);
    ImmutableList<RateAccrualPeriod> ratchetAccruals =
        ratchetCalculation.createAccrualPeriods(accrualSchedule.createSchedule(REF_DATA), null, REF_DATA);
    for (int loopperiod = 0; loopperiod < legResolved.getPaymentPeriods().size(); loopperiod++) {
      SwapPaymentPeriod swapPeriod = legResolved.getPaymentPeriods().get(loopperiod);
      assertThat(swapPeriod instanceof RatePaymentPeriod).isTrue();
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) swapPeriod;
      assertThat(ratePeriod.getAccrualPeriods().size()).isEqualTo(1);
      assertThat(ratePeriod.getAccrualPeriods().get(0)).isEqualTo(ratchetAccruals.get(loopperiod));
    }
  }


  @Test
  public void builder_null_index() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateCalculation
            .of(null, COEFFICIENTS));
  }
  
  @Test
  public void builder_null_coeff() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateCalculation
            .of(USD_LIBOR_3M, null));
  }
  
  @Test
  public void builder_length_coeff() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateCalculation
            .of(USD_LIBOR_3M, COEFFICIENTS.subList(0, 5)));
  }

  @Test
  public void builder_initial_value0_main() {
    List<ValueSchedule> coefficients = new ArrayList<>();
    coefficients.add(ValueSchedule.of(1.0)); // main previous
    coefficients.add(ValueSchedule.of(1.0)); // main Ibor
    coefficients.add(ValueSchedule.ALWAYS_0); // main fixed
    coefficients.add(ValueSchedule.ALWAYS_0); // floor previous
    coefficients.add(ValueSchedule.ALWAYS_0); // cap Ibor
    coefficients.add(ValueSchedule.of(0.01)); // floor fixed
    coefficients.add(ValueSchedule.ALWAYS_0); // cap previous
    coefficients.add(ValueSchedule.of(2.0)); // cap Ibor
    coefficients.add(ValueSchedule.ALWAYS_0); // cap fixed.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateCalculation.of(USD_LIBOR_3M, coefficients));
  }

  @Test
  public void builder_initial_value0_floor() {
    List<ValueSchedule> coefficients = new ArrayList<>();
    coefficients.add(ValueSchedule.ALWAYS_0); // main previous
    coefficients.add(ValueSchedule.of(1.0)); // main Ibor
    coefficients.add(ValueSchedule.ALWAYS_0); // main fixed
    coefficients.add(ValueSchedule.ALWAYS_1); // floor previous
    coefficients.add(ValueSchedule.ALWAYS_0); // cap Ibor
    coefficients.add(ValueSchedule.of(0.01)); // floor fixed
    coefficients.add(ValueSchedule.ALWAYS_0); // cap previous
    coefficients.add(ValueSchedule.of(2.0)); // cap Ibor
    coefficients.add(ValueSchedule.ALWAYS_0); // cap fixed.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateCalculation.of(USD_LIBOR_3M, coefficients));
  }

  @Test
  public void builder_initial_value0_cap() {
    List<ValueSchedule> coefficients = new ArrayList<>();
    coefficients.add(ValueSchedule.ALWAYS_0); // main previous
    coefficients.add(ValueSchedule.of(1.0)); // main Ibor
    coefficients.add(ValueSchedule.ALWAYS_0); // main fixed
    coefficients.add(ValueSchedule.ALWAYS_0); // floor previous
    coefficients.add(ValueSchedule.ALWAYS_0); // cap Ibor
    coefficients.add(ValueSchedule.of(0.01)); // floor fixed
    coefficients.add(ValueSchedule.ALWAYS_1); // cap previous
    coefficients.add(ValueSchedule.of(2.0)); // cap Ibor
    coefficients.add(ValueSchedule.ALWAYS_0); // cap fixed.
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateCalculation.of(USD_LIBOR_3M, coefficients));
  }

}
