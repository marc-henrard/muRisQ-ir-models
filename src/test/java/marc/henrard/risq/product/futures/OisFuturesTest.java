/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.product.futures;

import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_3M;
import static com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.product.SecurityId;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.Swap;

import marc.henrard.risq.product.futures.OisFutures;
import marc.henrard.risq.product.futures.OisFuturesResolved;

/**
 * Tests {@link OisFutures}.
 * 
 * @author Marc Henrard
 */
@Test
public class OisFuturesTest {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();

  /* OIS futures description. */
  private static final SecurityId SECURITY_ID = SecurityId.of("muRisQ", "ONF-3M-M8");
  private static final double ACCRUAL_FACTOR = 0.25;
  private static final double NOTIONAL = 1_000_000;
  private static final LocalDate LAST_TRADE_DATE = LocalDate.of(2018, 6, 20);
  private static final Period TENOR = Period.ofMonths(3);
  private static final Swap UNDERLYING = EUR_FIXED_1Y_EONIA_OIS
      .createTrade(LAST_TRADE_DATE, Tenor.of(TENOR), BuySell.BUY, 1.0, 0.0, REF_DATA).getProduct();
  private static final OisFutures OIS_FUT = OisFutures.builder()
      .securityId(SECURITY_ID)
      .underlying(UNDERLYING)
      .accrualFactor(ACCRUAL_FACTOR)
      .notional(NOTIONAL)
      .lastTradeDate(LAST_TRADE_DATE).build();
  
  public void builder() {
    assertEquals(OIS_FUT.getSecurityId(), SECURITY_ID);
    assertEquals(OIS_FUT.getUnderlying(), UNDERLYING);
    assertEquals(OIS_FUT.getAccrualFactor(), ACCRUAL_FACTOR);
    assertEquals(OIS_FUT.getNotional(), NOTIONAL);
    assertEquals(OIS_FUT.getLastTradeDate(), LAST_TRADE_DATE);
  }
  
  public void builder_wrong_swap_index() {
    Swap underlyingIbor = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(LAST_TRADE_DATE, Tenor.of(TENOR), BuySell.BUY, 1.0, 0.0, REF_DATA).getProduct();
    assertThrowsIllegalArg(() -> OisFutures.builder()
        .securityId(SECURITY_ID)
        .underlying(underlyingIbor)
        .accrualFactor(ACCRUAL_FACTOR)
        .notional(NOTIONAL)
        .lastTradeDate(LAST_TRADE_DATE).build());
  }
  
  public void builder_wrong_swap_date() {
    Swap underlying = EUR_FIXED_1Y_EURIBOR_3M
        .createTrade(LAST_TRADE_DATE.minusMonths(1), Tenor.of(TENOR), BuySell.BUY, 1.0, 0.0, REF_DATA).getProduct();
    assertThrowsIllegalArg(() -> OisFutures.builder()
        .securityId(SECURITY_ID)
        .underlying(underlying)
        .accrualFactor(ACCRUAL_FACTOR)
        .notional(NOTIONAL)
        .lastTradeDate(LAST_TRADE_DATE).build());
  }

  //-------------------------------------------------------------------------
  public void resolved() {
    OisFuturesResolved resolved = OIS_FUT.resolve(REF_DATA);
    OisFuturesResolved expected = OisFuturesResolved.builder()
        .securityId(SECURITY_ID)
        .underlying(UNDERLYING.resolve(REF_DATA))
        .accrualFactor(ACCRUAL_FACTOR)
        .notional(NOTIONAL)
        .lastTradeDate(LAST_TRADE_DATE).build();
    assertEquals(resolved, expected);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    coverImmutableBean(OIS_FUT);
    OisFutures oisFut2 = OisFutures.builder()
        .securityId(SecurityId.of("muRisQ", "XXX"))
        .underlying(EUR_FIXED_1Y_EONIA_OIS
            .createTrade(LAST_TRADE_DATE, Tenor.of(TENOR), BuySell.BUY, 1.0, 123.0, REF_DATA).getProduct())
        .accrualFactor(1.23d)
        .notional(123456.7d)
        .lastTradeDate(LAST_TRADE_DATE.plusDays(1)).build();
    coverBeanEquals(OIS_FUT, oisFut2);
  }

  public void test_serialization() {
    assertSerialization(OIS_FUT);
  }
  
}
