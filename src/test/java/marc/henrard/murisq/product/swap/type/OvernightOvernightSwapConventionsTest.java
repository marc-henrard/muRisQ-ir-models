/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.basics.schedule.Frequency;

/**
 * Tests {@link OvernightOvernightSwapConventions}.
 * 
 * @author Marc Henrard
 */
@Test
public class OvernightOvernightSwapConventionsTest {

  @DataProvider(name = "spotLag")
  public static Object[][] data_spot_lag() {
    return new Object[][] {
        {OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M, 2},
        {OvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M, 2},
    };
  }

  @Test(dataProvider = "spotLag")
  public void test_spot_lag(ImmutableOvernightOvernightSwapConvention convention, int lag) {
    assertEquals(convention.getSpotDateOffset().getDays(), lag);
  }

  //-------------------------------------------------------------------------
  @DataProvider(name = "periodOn")
  public static Object[][] data_period_on() {
    return new Object[][] {
        {OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M, Frequency.P3M},
        {OvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M, Frequency.P3M},
    };
  }

  @Test(dataProvider = "periodOn")
  public void test_accrualPeriod_on(OvernightOvernightSwapConvention convention, Frequency frequency) {
    assertEquals(convention.getOvernightLeg1().getAccrualFrequency(), frequency);
    assertEquals(convention.getOvernightLeg2().getAccrualFrequency(), frequency);
  }

  @Test(dataProvider = "periodOn")
  public void test_paymentPeriod_on(OvernightOvernightSwapConvention convention, Frequency frequency) {
    assertEquals(convention.getOvernightLeg1().getPaymentFrequency(), frequency);
    assertEquals(convention.getOvernightLeg2().getPaymentFrequency(), frequency);
  }

  //-------------------------------------------------------------------------
  @DataProvider(name = "dayCount")
  public static Object[][] data_day_count() {
    return new Object[][] {
        {OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M, DayCounts.ACT_360},
        {OvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M, DayCounts.ACT_360},
    };
  }

  @Test(dataProvider = "dayCount")
  public void test_day_count(OvernightOvernightSwapConvention convention, DayCount dayCount) {
    assertEquals(convention.getOvernightLeg1().getDayCount(), dayCount);
    assertEquals(convention.getOvernightLeg2().getDayCount(), dayCount);
  }

  //-------------------------------------------------------------------------
  @DataProvider(name = "onIndex1")
  public static Object[][] data_float_leg1() {
    return new Object[][] {
        {OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M, OvernightIndices.USD_SOFR},
        {OvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M, OvernightIndices.EUR_EONIA},
    };
  }

  @Test(dataProvider = "onIndex1")
  public void test_float_leg1(OvernightOvernightSwapConvention convention, OvernightIndex floatLeg) {
    assertEquals(convention.getOvernightLeg1().getIndex(), floatLeg);
  }

  //-------------------------------------------------------------------------
  @DataProvider(name = "onIndex2")
  public static Object[][] data_float_leg2() {
    return new Object[][] {
        {OvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M, OvernightIndices.USD_FED_FUND},
        {OvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M, OvernightIndices.EUR_ESTER},
    };
  }

  @Test(dataProvider = "onIndex2")
  public void test_float_leg2(OvernightOvernightSwapConvention convention, OvernightIndex floatLeg) {
    assertEquals(convention.getOvernightLeg2().getIndex(), floatLeg);
  }
  
}
