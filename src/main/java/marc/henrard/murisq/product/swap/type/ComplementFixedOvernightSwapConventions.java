/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_360;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_ESTR;
import static com.opengamma.strata.basics.schedule.Frequency.P12M;
import static com.opengamma.strata.basics.schedule.Frequency.TERM;
import static com.opengamma.strata.product.swap.OvernightAccrualMethod.COMPOUNDED;

import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.product.swap.type.FixedOvernightSwapConvention;
import com.opengamma.strata.product.swap.type.FixedRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableFixedOvernightSwapConvention;
import com.opengamma.strata.product.swap.type.OvernightRateSwapLegConvention;

/**
 * Market standard Fixed-Overnight swap conventions.
 * <p>
 * https://developers.opengamma.com/quantitative-research/Interest-Rate-Instruments-and-Market-Conventions.pdf
 */
public final class ComplementFixedOvernightSwapConventions {
  
  /**
   * EUR fixed vs ESTR OIS swap for terms less than or equal to one year.
   * <p>
   * Both legs pay once at the end and use day count 'Act/360'.
   * The spot date offset is 2 days and the payment date offset is 1 day.
   */
  public static final FixedOvernightSwapConvention EUR_FIXED_TERM_ESTR_OIS =
      makeConvention("EUR-FIXED-TERM-ESTR-OIS", EUR_ESTR, ACT_360, TERM, 1, 2);

  /**
   * EUR fixed vs ESTR OIS swap for terms greater than one year.
   * <p>
   * Both legs pay annually and use day count 'Act/360'.
   * The spot date offset is 2 days and the payment date offset is 1 day.
   */
  public static final FixedOvernightSwapConvention EUR_FIXED_1Y_ESTR_OIS =
      makeConvention("EUR-FIXED-1Y-ESTR-OIS", EUR_ESTR, ACT_360, P12M, 1, 2);


  //-------------------------------------------------------------------------
  // build conventions
  private static FixedOvernightSwapConvention makeConvention(
      String name,
      OvernightIndex index,
      DayCount dayCount,
      Frequency frequency,
      int paymentLag,
      int spotLag) {

    HolidayCalendarId calendar = index.getFixingCalendar();
    DaysAdjustment paymentDateOffset = DaysAdjustment.ofBusinessDays(paymentLag, calendar);
    DaysAdjustment spotDateOffset = DaysAdjustment.ofBusinessDays(spotLag, calendar);
    return ImmutableFixedOvernightSwapConvention.of(
        name,
        FixedRateSwapLegConvention.builder()
            .currency(index.getCurrency())
            .dayCount(dayCount)
            .accrualFrequency(frequency)
            .accrualBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, calendar))
            .paymentFrequency(frequency)
            .paymentDateOffset(paymentDateOffset)
            .stubConvention(StubConvention.SMART_INITIAL)
            .build(),
        OvernightRateSwapLegConvention.builder()
            .index(index)
            .accrualMethod(COMPOUNDED)
            .accrualFrequency(frequency)
            .paymentFrequency(frequency)
            .paymentDateOffset(paymentDateOffset)
            .stubConvention(StubConvention.SMART_INITIAL)
            .build(),
        spotDateOffset);
  }
  
  /**
   * Restricted constructor.
   */
  private ComplementFixedOvernightSwapConventions() {
  }

}
