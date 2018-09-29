/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.product.futures;

import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.product.swap.type.FixedOvernightSwapConventions.EUR_FIXED_1Y_EONIA_OIS;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.product.SecurityId;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.Swap;

/**
 * Tests {@link OisFuturesTradeResolved}.
 * 
 * @author Marc Henrard
 */
@Test
public class OisFuturesTradeResolvedTest {
  
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
  private static final OisFuturesResolved OIS_FUT_RESOLVED = OIS_FUT.resolve(REF_DATA);
  private static final LocalDate TRADE_DATE = LocalDate.of(2018, 5, 21);
  private static final TradeInfo INFO = TradeInfo.of(TRADE_DATE);
  private static final double TRADE_PRICE = 0.0125;
  private static final double QUANTITY = 1234;
  private static final OisFuturesTradeResolved TRADE_RESOLVED = OisFuturesTradeResolved.builder()
      .info(INFO)
      .product(OIS_FUT_RESOLVED)
      .price(TRADE_PRICE)
      .quantity(QUANTITY).build();
  
  public void builder() {
    assertEquals(TRADE_RESOLVED.getInfo(), INFO);
    assertEquals(TRADE_RESOLVED.getProduct(), OIS_FUT_RESOLVED);
    assertEquals(TRADE_RESOLVED.getPrice(), TRADE_PRICE);
    assertEquals(TRADE_RESOLVED.getQuantity(), QUANTITY);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    coverImmutableBean(TRADE_RESOLVED);
    OisFuturesResolved oisFut2 = OisFutures.builder()
        .securityId(SecurityId.of("muRisQ", "XXX"))
        .underlying(EUR_FIXED_1Y_EONIA_OIS
            .createTrade(LAST_TRADE_DATE, Tenor.of(TENOR), BuySell.BUY, 1.0, 123.0, REF_DATA).getProduct())
        .accrualFactor(1.23d)
        .notional(123456.7d)
        .lastTradeDate(LAST_TRADE_DATE.plusDays(1)).build().resolve(REF_DATA);
    OisFuturesTradeResolved trade2 = OisFuturesTradeResolved.builder()
        .info(TradeInfo.of(TRADE_DATE.minusDays(1)))
        .product(oisFut2)
        .price(TRADE_PRICE / 2)
        .quantity(QUANTITY * 2).build();
    coverBeanEquals(TRADE_RESOLVED, trade2);
  }

  public void test_serialization() {
    assertSerialization(TRADE_RESOLVED);
  }
  
}
