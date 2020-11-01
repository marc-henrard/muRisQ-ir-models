/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.util.OptionalDouble;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.product.cms.CmsPeriod;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapIndex;
import com.opengamma.strata.product.swap.SwapIndices;

/**
 * Tests {@link CmsPeriodResolved}.
 */
public class CmsPeriodResolvedTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final double NOTIONAL_1M = 1_000_000d;
  private static final LocalDate START_DATE = LocalDate.of(2020, 8, 18);
  private static final LocalDate END_DATE = LocalDate.of(2020, 10, 18);
  private static final double ACCRUAL_FACTOR = 0.25;
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2020, 10, 28);
  private static final LocalDate FIXING_DATE = LocalDate.of(2020, 8, 14);
  private static final Double CAPLET = 0.01;
  private static final Double FLOORLET = 0.02;
  private static final SwapIndex INDEX1 = SwapIndices.EUR_EURIBOR_1100_10Y;
  private static final ResolvedSwap SWAP_1 = INDEX1.getTemplate()
      .createTrade(FIXING_DATE, BuySell.SELL, 1.0d, 1.0d, REF_DATA)
      .getProduct()
      .resolve(REF_DATA);
  private static final CmsPeriodResolved CMS_PERIOD_CPN =
      CmsPeriodResolved.of(
          CmsPeriod.builder()
              .currency(Currency.EUR)
              .dayCount(DayCounts.ACT_360)
              .notional(NOTIONAL_1M)
              .startDate(START_DATE)
              .endDate(END_DATE)
              .yearFraction(ACCRUAL_FACTOR)
              .paymentDate(PAYMENT_DATE)
              .fixingDate(FIXING_DATE)
              .index(INDEX1)
              .underlyingSwap(SWAP_1)
              .build());
  private static final CmsPeriodResolved CMS_PERIOD_CAP =
      CmsPeriodResolved.of(
          CmsPeriod.builder()
              .currency(Currency.EUR)
              .dayCount(DayCounts.ACT_360)
              .notional(NOTIONAL_1M)
              .startDate(START_DATE)
              .endDate(END_DATE)
              .yearFraction(ACCRUAL_FACTOR)
              .paymentDate(PAYMENT_DATE)
              .fixingDate(FIXING_DATE)
              .index(INDEX1)
              .underlyingSwap(SWAP_1)
              .caplet(CAPLET)
              .build());
  private static final CmsPeriodResolved CMS_PERIOD_FLOOR =
      CmsPeriodResolved.of(
          CmsPeriod.builder()
              .currency(Currency.EUR)
              .dayCount(DayCounts.ACT_360)
              .notional(NOTIONAL_1M)
              .startDate(START_DATE)
              .endDate(END_DATE)
              .yearFraction(ACCRUAL_FACTOR)
              .paymentDate(PAYMENT_DATE)
              .fixingDate(FIXING_DATE)
              .index(INDEX1)
              .underlyingSwap(SWAP_1)
              .floorlet(FLOORLET)
              .build());
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_PAYOFF = offset(1.0E-8);
  
  @Test
  public void builder_cpn() {
    assertThat(CMS_PERIOD_CPN.getPeriod().getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(CMS_PERIOD_CPN.getPeriod().getStartDate()).isEqualTo(START_DATE);
    assertThat(CMS_PERIOD_CPN.getPeriod().getEndDate()).isEqualTo(END_DATE);
    assertThat(CMS_PERIOD_CPN.getPeriod().getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(CMS_PERIOD_CPN.getPeriod().getPaymentDate()).isEqualTo(PAYMENT_DATE);
    assertThat(CMS_PERIOD_CPN.getPeriod().getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(CMS_PERIOD_CPN.getPeriod().getIndex()).isEqualTo(INDEX1);
    assertThat(CMS_PERIOD_CPN.getPeriod().getUnderlyingSwap()).isEqualTo(SWAP_1);
    assertThat(CMS_PERIOD_CPN.getPeriod().getCaplet()).isEqualTo(OptionalDouble.empty());
    assertThat(CMS_PERIOD_CPN.getPeriod().getFloorlet()).isEqualTo(OptionalDouble.empty());
    assertThat(CMS_PERIOD_CPN.getPeriod().getCurrency()).isEqualTo(Currency.EUR);
  }

  @Test
  public void payoff_primitive_cpn() {
    double[] swapRate = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    for (int i = 0; i < swapRate.length; i++) {
      double payoffComputed = CMS_PERIOD_CPN.payoff(swapRate[i]);
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *swapRate[i];
      assertThat(payoffComputed).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_array_cpn() {
    double[] swapRate = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] payoffComputed = CMS_PERIOD_CPN.payoff(swapRate);
    for (int i = 0; i < swapRate.length; i++) {
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR * swapRate[i];
      assertThat(payoffComputed[i]).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_primitive_cap() {
    double[] swapRate = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    for (int i = 0; i < swapRate.length; i++) {
      double payoffComputed = CMS_PERIOD_CAP.payoff(swapRate[i]);
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR * Math.max(swapRate[i] - CAPLET, 0.0);
      assertThat(payoffComputed).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_array_cap() {
    double[] swapRate = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] payoffComputed = CMS_PERIOD_CAP.payoff(swapRate);
    for (int i = 0; i < swapRate.length; i++) {
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR * Math.max(swapRate[i] - CAPLET, 0.0);
      assertThat(payoffComputed[i]).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_primitive_floor() {
    double[] swapRate = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    for (int i = 0; i < swapRate.length; i++) {
      double payoffComputed = CMS_PERIOD_FLOOR.payoff(swapRate[i]);
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR * Math.max(FLOORLET - swapRate[i], 0.0);
      assertThat(payoffComputed).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_array_floor() {
    double[] swapRate = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] payoffComputed = CMS_PERIOD_FLOOR.payoff(swapRate);
    for (int i = 0; i < swapRate.length; i++) {
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR * Math.max(FLOORLET - swapRate[i] , 0.0);
      assertThat(payoffComputed[i]).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

}
