/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;
import static com.opengamma.strata.product.swap.OvernightAccrualMethod.COMPOUNDED;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.OvernightRateSwapLegConvention;

@Test
public class ImmutableOvernightOvernightSwapConventionTest {
  
  private static final HolidayCalendarId USNY_GS_ID = 
      HolidayCalendarId.of("USNY+USGS");
  private static final BusinessDayAdjustment BDA_USNY_GS =
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, USNY_GS_ID);
  private static final DaysAdjustment T_PLUS_TWO = DaysAdjustment.ofBusinessDays(2, USNY_GS_ID);

  private static final String NAME = "USD-EFFR-SOFR";
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
  
  /* Trade */
  private static final double NOTIONAL = 12_000_000d;
  private static final double SPREAD = -0.0001;
  private static final LocalDate TRADE_DATE = LocalDate.of(2018, 7 , 18);
  private static final LocalDate START_DATE = LocalDate.of(2018, 7 , 22);
  private static final LocalDate END_DATE = LocalDate.of(2019, 7 , 22);

  public void of() {
    ImmutableOvernightOvernightSwapConvention test =
        ImmutableOvernightOvernightSwapConvention.of(NAME, SOFR_LEG, EFFR_LEG, T_PLUS_TWO);
    assertEquals(test.getName(), NAME);
    assertEquals(test.getOvernightLeg1(), SOFR_LEG);
    assertEquals(test.getOvernightLeg2(), EFFR_LEG);
    assertEquals(test.getSpotDateOffset(), T_PLUS_TWO);
  }

  public void builder() {
    ImmutableOvernightOvernightSwapConvention test =
        ImmutableOvernightOvernightSwapConvention.builder()
        .name(NAME)
        .overnightLeg1(SOFR_LEG)
        .overnightLeg2(EFFR_LEG)
        .spotDateOffset(T_PLUS_TWO).build();
    assertEquals(test.getName(), NAME);
    assertEquals(test.getOvernightLeg1(), SOFR_LEG);
    assertEquals(test.getOvernightLeg2(), EFFR_LEG);
    assertEquals(test.getSpotDateOffset(), T_PLUS_TWO);
  }

  public void toTrade() {
    ImmutableOvernightOvernightSwapConvention test =
        ImmutableOvernightOvernightSwapConvention.of(NAME, SOFR_LEG, EFFR_LEG, T_PLUS_TWO);
    SwapTrade trade = test.toTrade(TradeInfo.of(TRADE_DATE), START_DATE, END_DATE, 
        BuySell.BUY, NOTIONAL, SPREAD);
    assertEquals(trade.getInfo(), TradeInfo.of(TRADE_DATE));
    assertEquals(trade.getProduct().getStartDate(), AdjustableDate.of(START_DATE, BDA_USNY_GS));
    assertEquals(trade.getProduct().getEndDate(), AdjustableDate.of(END_DATE, BDA_USNY_GS));
    Swap product = Swap.of(
        SOFR_LEG.toLeg(START_DATE, END_DATE, PayReceive.PAY, NOTIONAL, SPREAD),
        EFFR_LEG.toLeg(START_DATE, END_DATE, PayReceive.RECEIVE, NOTIONAL));
    assertEquals(trade.getProduct(), product);
  }
  
}
