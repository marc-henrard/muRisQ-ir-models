/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_ESTR;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;
import static com.opengamma.strata.product.swap.OvernightAccrualMethod.COMPOUNDED;

import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.product.swap.type.OvernightRateSwapLegConvention;

/**
 * Market standard Overnight-Overnight swap conventions.
 */
public final class StandardOvernightOvernightSwapConventions {

  private static final HolidayCalendarId USNY_GS_ID = 
      HolidayCalendarId.of("USNY+USGS");
  private static final BusinessDayAdjustment BDA_USNY_GS =
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, USNY_GS_ID);
  private static final DaysAdjustment T_PLUS_TWO = DaysAdjustment.ofBusinessDays(2, USNY_GS_ID);
  private static final OvernightRateSwapLegConvention SOFR_LEG =
      OvernightRateSwapLegConvention.builder()
          .index(USD_SOFR)
          .accrualMethod(COMPOUNDED)
          .accrualFrequency(Frequency.P3M)
          .paymentFrequency(Frequency.P3M)
          .stubConvention(StubConvention.SMART_INITIAL)
          .startDateBusinessDayAdjustment(BDA_USNY_GS)
          .endDateBusinessDayAdjustment(BDA_USNY_GS)
          .accrualBusinessDayAdjustment(BDA_USNY_GS)
          .build();
  private static final OvernightRateSwapLegConvention EFFR_LEG =
      OvernightRateSwapLegConvention.builder()
          .index(USD_FED_FUND)
          .accrualMethod(COMPOUNDED)
          .accrualFrequency(Frequency.P3M)
          .paymentFrequency(Frequency.P3M)
          .stubConvention(StubConvention.SMART_INITIAL)
          .startDateBusinessDayAdjustment(BDA_USNY_GS)
          .endDateBusinessDayAdjustment(BDA_USNY_GS)
          .accrualBusinessDayAdjustment(BDA_USNY_GS)
          .build();
  
  /**
   * USD SOFR v EFFR.
   * <p>
   * Both legs use day count 'Act/360'. The payment frequency is quarterly.
   * The spot date offset is 2 days. No rate cut-off. The spread is on the SOFR leg. The calendar is
   * the combination of USNY and USGS.
   */
  public static final OvernightOvernightSwapConvention USD_SOFR_3M_FED_FUND_3M =
      ImmutableOvernightOvernightSwapConvention
      .of("USD-SOFR-3M-FED-FUND-3M", SOFR_LEG, EFFR_LEG, T_PLUS_TWO);

  //-------------------------------------------------------------------------
  private static final BusinessDayAdjustment BDA_EUTA =
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, EUTA);
  private static final DaysAdjustment T_PLUS_TWO_EUTA = DaysAdjustment.ofBusinessDays(2, EUTA);
  private static final OvernightRateSwapLegConvention EONIA_LEG =
      OvernightRateSwapLegConvention.builder()
          .index(EUR_EONIA)
          .accrualMethod(COMPOUNDED)
          .accrualFrequency(Frequency.P3M)
          .paymentFrequency(Frequency.P3M)
          .stubConvention(StubConvention.SMART_INITIAL)
          .startDateBusinessDayAdjustment(BDA_EUTA)
          .endDateBusinessDayAdjustment(BDA_EUTA)
          .accrualBusinessDayAdjustment(BDA_EUTA)
          .build();
  private static final OvernightRateSwapLegConvention ESTER_LEG =
      OvernightRateSwapLegConvention.builder()
          .index(EUR_ESTR)
          .accrualMethod(COMPOUNDED)
          .accrualFrequency(Frequency.P3M)
          .paymentFrequency(Frequency.P3M)
          .stubConvention(StubConvention.SMART_INITIAL)
          .startDateBusinessDayAdjustment(BDA_EUTA)
          .endDateBusinessDayAdjustment(BDA_EUTA)
          .accrualBusinessDayAdjustment(BDA_EUTA)
          .build();
  
  /**
   * EUR EONIA v ESTER.
   * <p>
   * Both legs use day count 'Act/360'. The payment frequency is quarterly.
   * The spot date offset is 2 days. No rate cut-off. The spread is on the EONIA leg. The calendar is EUTA.
   */
  public static final OvernightOvernightSwapConvention EUR_EONIA_3M_ESTER_3M =
      ImmutableOvernightOvernightSwapConvention
      .of("EUR-EONIA-3M-ESTER-3M", EONIA_LEG, ESTER_LEG, T_PLUS_TWO_EUTA);

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   */
  private StandardOvernightOvernightSwapConventions() {
  }

}
