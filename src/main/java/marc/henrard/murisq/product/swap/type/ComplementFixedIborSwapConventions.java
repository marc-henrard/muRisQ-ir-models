/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;


import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_360;
import static com.opengamma.strata.basics.schedule.Frequency.P6M;
import static com.opengamma.strata.basics.schedule.Frequency.P12M;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.GBLO;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.USNY;

import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.IborRateSwapLegConvention;
import com.opengamma.strata.product.swap.type.ImmutableFixedIborSwapConvention;

public class ComplementFixedIborSwapConventions {

  private static final HolidayCalendarId GBLO_USNY = GBLO.combinedWith(USNY);
  
  /**
   * USD fixed (SA Bond) vs LIBOR 1M.
   */
  public static final FixedIborSwapConvention USD_FIXED_6M_LIBOR_1M =
      ImmutableFixedIborSwapConvention.of(
          "USD-FIXED-6M-LIBOR-1M",
          FixedRateSwapLegConvention.of(USD, ACT_360, P6M, BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO_USNY)),
          IborRateSwapLegConvention.of(IborIndices.USD_LIBOR_1M));
  
  /**
   * EUR ACT/360 v EURIBOR 6M - for reporting purposes.
   */
  public static final FixedIborSwapConvention EUR_FIXED_1Y_ACT360_EURIBOR_6M =
      ImmutableFixedIborSwapConvention.of(
          "EUR-FIXED-1Y-ACT360-EURIBOR-6M",
          FixedRateSwapLegConvention.of(EUR, ACT_360, P12M, BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA)),
          IborRateSwapLegConvention.of(IborIndices.EUR_EURIBOR_6M));
  
  /**
   * EUR ACT/360 v EURIBOR 3M - for reporting purposes.
   */
  public static final FixedIborSwapConvention EUR_FIXED_1Y_ACT360_EURIBOR_3M =
      ImmutableFixedIborSwapConvention.of(
          "EUR-FIXED-1Y-ACT360-EURIBOR-3M",
          FixedRateSwapLegConvention.of(EUR, ACT_360, P12M, BusinessDayAdjustment.of(MODIFIED_FOLLOWING, EUTA)),
          IborRateSwapLegConvention.of(IborIndices.EUR_EURIBOR_3M));

}
