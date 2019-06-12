/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import static com.opengamma.strata.basics.date.Tenor.TENOR_10Y;
import static com.opengamma.strata.basics.date.Tenor.TENOR_2Y;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_SOFR;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.product.swap.OvernightAccrualMethod.COMPOUNDED;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swap.SwapTrade;
import com.opengamma.strata.product.swap.type.OvernightRateSwapLegConvention;

/**
 * Tests {@link OvernightOvernightSwapTemplate}.
 * 
 * @author Marc Henrard
 */
@Test
public class OvernightOvernightSwapTemplateTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final double NOTIONAL = 12_000_000d;

  private static final HolidayCalendarId USNY_GS_ID = 
      HolidayCalendarId.of("USNY+USGS");
  private static final BusinessDayAdjustment BDA_USNY_GS =
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, USNY_GS_ID);
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

  private static final OvernightOvernightSwapConvention CONV =
      OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M;
  private static final OvernightOvernightSwapConvention CONV_2 =
      OvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M;

  //-------------------------------------------------------------------------
  public void test_of() {
    OvernightOvernightSwapTemplate test = OvernightOvernightSwapTemplate.of(TENOR_10Y, CONV);
    assertEquals(test.getPeriodToStart(), Period.ZERO);
    assertEquals(test.getTenor(), TENOR_10Y);
    assertEquals(test.getConvention(), CONV);
  }

  public void test_of_period() {
    OvernightOvernightSwapTemplate test = OvernightOvernightSwapTemplate.of(Period.ofMonths(3), TENOR_10Y, CONV);
    assertEquals(test.getPeriodToStart(), Period.ofMonths(3));
    assertEquals(test.getTenor(), TENOR_10Y);
    assertEquals(test.getConvention(), CONV);
  }

  //-------------------------------------------------------------------------
  public void test_builder_notEnoughData() {
    assertThrowsIllegalArg(() -> OvernightOvernightSwapTemplate.builder()
        .tenor(TENOR_2Y)
        .build());
  }

  //-------------------------------------------------------------------------
  public void test_createTrade() {
    OvernightOvernightSwapTemplate base = OvernightOvernightSwapTemplate.of(Period.ofMonths(3), TENOR_10Y, CONV);
    LocalDate tradeDate = LocalDate.of(2015, 5, 5);
    LocalDate startDate = LocalDate.of(2015, 8, 7);
    LocalDate endDate = LocalDate.of(2025, 8, 7);
    SwapTrade test = base.createTrade(tradeDate, BuySell.BUY, NOTIONAL, -0.0001, REF_DATA);
    Swap expected = Swap.of(
        SOFR_LEG.toLeg(startDate, endDate, PayReceive.PAY, NOTIONAL, -0.0001),
        EFFR_LEG.toLeg(startDate, endDate, PayReceive.RECEIVE, NOTIONAL));
    assertEquals(test.getInfo().getTradeDate(), Optional.of(tradeDate));
    assertEquals(test.getProduct(), expected);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    OvernightOvernightSwapTemplate test = OvernightOvernightSwapTemplate.of(Period.ofMonths(3), TENOR_10Y, CONV);
    coverImmutableBean(test);
    OvernightOvernightSwapTemplate test2 = OvernightOvernightSwapTemplate.of(Period.ofMonths(2), TENOR_2Y, CONV_2);
    coverBeanEquals(test, test2);
  }

  public void test_serialization() {
    OvernightOvernightSwapTemplate test = OvernightOvernightSwapTemplate.of(Period.ofMonths(3), TENOR_10Y, CONV);
    assertSerialization(test);
  }
  
}
