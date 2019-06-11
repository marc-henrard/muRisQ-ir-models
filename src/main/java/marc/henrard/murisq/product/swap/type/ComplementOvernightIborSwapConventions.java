/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.basics.schedule.Frequency.P3M;
import static com.opengamma.strata.product.swap.OvernightAccrualMethod.COMPOUNDED;

import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.product.swap.OvernightAccrualMethod;
import com.opengamma.strata.product.swap.type.IborRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableOvernightIborSwapConvention;
import com.opengamma.strata.product.swap.type.OvernightIborSwapConvention;
import com.opengamma.strata.product.swap.type.OvernightRateSwapLegConvention;

/**
 * Market standard Overnight-Ibor swap conventions.
 */
public final class ComplementOvernightIborSwapConventions {

  /**
   * GBP SONIA compounded 3M v LIBOR 3M .
   * <p>
   * Both legs use day count 'Act/365F'.
   * The spot date offset is 0 days and payment offset is 0 days.
   */
  public static final OvernightIborSwapConvention GBP_SONIA_OIS_1Y_LIBOR_3M =
      makeConvention("GBP-SONIA-OIS-3M-LIBOR-3M", GBP_SONIA, GBP_LIBOR_3M, P3M, 0, 0, COMPOUNDED);

  /**
   * Generate the convention from details.
   * 
   * @param name  the name
   * @param onIndex  the overnight index
   * @param iborIndex  the ibor index
   * @param frequency  the frequency of the overnight leg payments
   * @param paymentLag  the lag between end accrual and payment
   * @param cutOffDays  the number of cut-off days
   * @param accrual  the accrual method
   * @return  the convention
   */
  private static OvernightIborSwapConvention makeConvention(
      String name,
      OvernightIndex onIndex,
      IborIndex iborIndex,
      Frequency frequency,
      int paymentLag,
      int cutOffDays,
      OvernightAccrualMethod accrual) {

    HolidayCalendarId calendarOn = onIndex.getFixingCalendar();
    DaysAdjustment paymentDateOffset = DaysAdjustment.ofBusinessDays(paymentLag, calendarOn);
    return ImmutableOvernightIborSwapConvention.of(
        name,
        OvernightRateSwapLegConvention.builder()
            .index(onIndex)
            .accrualMethod(accrual)
            .accrualFrequency(frequency)
            .paymentFrequency(frequency)
            .paymentDateOffset(paymentDateOffset)
            .stubConvention(StubConvention.SMART_INITIAL)
            .rateCutOffDays(cutOffDays)
            .build(),
        IborRateSwapLegConvention.of(iborIndex));
  }

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   */
  private ComplementOvernightIborSwapConventions() {
  }

}
