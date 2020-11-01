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
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapIndex;
import com.opengamma.strata.product.swap.SwapIndices;

/**
 * Tests {@link CmsSpreadPeriodResolved}.
 */
public class CmsSpreadPeriodResolvedTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final double NOTIONAL_1M = 1_000_000d;
  private static final LocalDate START_DATE = LocalDate.of(2020, 8, 18);
  private static final LocalDate END_DATE = LocalDate.of(2020, 10, 18);
  private static final double ACCRUAL_FACTOR = 0.25;
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2020, 10, 28);
  private static final LocalDate FIXING_DATE = LocalDate.of(2020, 8, 14);
  private static final Double CAPLET = 0.01;
  private static final Double FLOORLET = 0.02;
  private static final double WEIGHT1 = 1.00;
  private static final SwapIndex INDEX1 = SwapIndices.EUR_EURIBOR_1100_10Y;
  private static final ResolvedSwap SWAP_1 = INDEX1.getTemplate()
      .createTrade(FIXING_DATE, BuySell.SELL, 1.0d, 1.0d, REF_DATA)
      .getProduct()
      .resolve(REF_DATA);
  private static final double WEIGHT2 = 1.50;
  private static final SwapIndex INDEX2 = SwapIndices.EUR_EURIBOR_1100_2Y;
  private static final ResolvedSwap SWAP_2 = INDEX2.getTemplate()
      .createTrade(FIXING_DATE, BuySell.SELL, 1.0d, 1.0d, REF_DATA)
      .getProduct()
      .resolve(REF_DATA);
  private static final CmsSpreadPeriodResolved CMS_SPREAD_PERIOD_CPN = 
      CmsSpreadPeriodResolved.builder()
      .notional(NOTIONAL_1M)
      .startDate(START_DATE)
      .endDate(END_DATE)
      .yearFraction(ACCRUAL_FACTOR)
      .paymentDate(PAYMENT_DATE)
      .fixingDate(FIXING_DATE)
      .weight1(WEIGHT1)
      .index1(INDEX1)
      .underlyingSwap1(SWAP_1)
      .weight2(WEIGHT2)
      .index2(INDEX2)
      .underlyingSwap2(SWAP_2)
      .build();
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_PAYOFF = offset(1.0E-8);
  
  @Test
  public void builder_cpn() {
    assertThat(CMS_SPREAD_PERIOD_CPN.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(CMS_SPREAD_PERIOD_CPN.getStartDate()).isEqualTo(START_DATE);
    assertThat(CMS_SPREAD_PERIOD_CPN.getEndDate()).isEqualTo(END_DATE);
    assertThat(CMS_SPREAD_PERIOD_CPN.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(CMS_SPREAD_PERIOD_CPN.getPaymentDate()).isEqualTo(PAYMENT_DATE);
    assertThat(CMS_SPREAD_PERIOD_CPN.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(CMS_SPREAD_PERIOD_CPN.getWeight1()).isEqualTo(WEIGHT1);
    assertThat(CMS_SPREAD_PERIOD_CPN.getIndex1()).isEqualTo(INDEX1);
    assertThat(CMS_SPREAD_PERIOD_CPN.getUnderlyingSwap1()).isEqualTo(SWAP_1);
    assertThat(CMS_SPREAD_PERIOD_CPN.getWeight2()).isEqualTo(WEIGHT2);
    assertThat(CMS_SPREAD_PERIOD_CPN.getIndex2()).isEqualTo(INDEX2);
    assertThat(CMS_SPREAD_PERIOD_CPN.getUnderlyingSwap2()).isEqualTo(SWAP_2);
    assertThat(CMS_SPREAD_PERIOD_CPN.getCaplet()).isEqualTo(OptionalDouble.empty());
    assertThat(CMS_SPREAD_PERIOD_CPN.getFloorlet()).isEqualTo(OptionalDouble.empty());
    assertThat(CMS_SPREAD_PERIOD_CPN.getCurrency()).isEqualTo(Currency.EUR);
  }

  @Test
  public void builder_cap() {
    CmsSpreadPeriodResolved cap =
        CmsSpreadPeriodResolved.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .underlyingSwap1(SWAP_1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .underlyingSwap2(SWAP_2)
            .caplet(CAPLET)
            .build();
    assertThat(cap.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(cap.getStartDate()).isEqualTo(START_DATE);
    assertThat(cap.getEndDate()).isEqualTo(END_DATE);
    assertThat(cap.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(cap.getPaymentDate()).isEqualTo(PAYMENT_DATE);
    assertThat(cap.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(cap.getWeight1()).isEqualTo(WEIGHT1);
    assertThat(cap.getIndex1()).isEqualTo(INDEX1);
    assertThat(cap.getWeight2()).isEqualTo(WEIGHT2);
    assertThat(cap.getIndex2()).isEqualTo(INDEX2);
    assertThat(cap.getCaplet().getAsDouble()).isEqualTo(CAPLET);
    assertThat(cap.getFloorlet()).isEqualTo(OptionalDouble.empty());
  }

  @Test
  public void builder_floor() {
    CmsSpreadPeriodResolved floor =
        CmsSpreadPeriodResolved.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .underlyingSwap1(SWAP_1)
            .index2(INDEX2)
            .floorlet(FLOORLET)
            .underlyingSwap2(SWAP_2)
            .build();
    assertThat(floor.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(floor.getStartDate()).isEqualTo(START_DATE);
    assertThat(floor.getEndDate()).isEqualTo(END_DATE);
    assertThat(floor.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(floor.getPaymentDate()).isEqualTo(PAYMENT_DATE);
    assertThat(floor.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(floor.getWeight1()).isEqualTo(WEIGHT1);
    assertThat(floor.getIndex1()).isEqualTo(INDEX1);
    assertThat(floor.getWeight2()).isEqualTo(WEIGHT2);
    assertThat(floor.getIndex2()).isEqualTo(INDEX2);
    assertThat(floor.getFloorlet().getAsDouble()).isEqualTo(FLOORLET);
    assertThat(floor.getCaplet()).isEqualTo(OptionalDouble.empty());
  }

  @Test
  public void payoff_primitive_cpn() {
    double[] swapRate1 = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] swapRate2 = {0.0050d, 0.0100, 0.0200, 0.0150, 0.0250, -0.0100d, 0.0d};
    for (int i = 0; i < swapRate1.length; i++) {
      double payoffComputed = CMS_SPREAD_PERIOD_CPN.payoff(swapRate1[i], swapRate2[i]);
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *
          (WEIGHT1 * swapRate1[i] - WEIGHT2 * swapRate2[i]);
      assertThat(payoffComputed).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_array_cpn() {
    double[] swapRate1 = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] swapRate2 = {0.0050d, 0.0100, 0.0200, 0.0150, 0.0250, -0.0100d, 0.0d};
    double[] payoffComputed = CMS_SPREAD_PERIOD_CPN.payoff(swapRate1, swapRate2);
    for (int i = 0; i < swapRate1.length; i++) {
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *
          (WEIGHT1 * swapRate1[i] - WEIGHT2 * swapRate2[i]);
      assertThat(payoffComputed[i]).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_primitive_cap() {
    CmsSpreadPeriodResolved cap =
        CmsSpreadPeriodResolved.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .underlyingSwap1(SWAP_1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .underlyingSwap2(SWAP_2)
            .caplet(CAPLET)
            .build();
    double[] swapRate1 = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] swapRate2 = {0.0050d, 0.0100, 0.0200, 0.0150, 0.0250, -0.0100d, 0.0d};
    for (int i = 0; i < swapRate1.length; i++) {
      double payoffComputed = cap.payoff(swapRate1[i], swapRate2[i]);
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *
          Math.max(WEIGHT1 * swapRate1[i] - WEIGHT2 * swapRate2[i] - CAPLET, 0.0);
      assertThat(payoffComputed).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_array_cap() {
    CmsSpreadPeriodResolved cap =
        CmsSpreadPeriodResolved.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .underlyingSwap1(SWAP_1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .underlyingSwap2(SWAP_2)
            .caplet(CAPLET)
            .build();
    double[] swapRate1 = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] swapRate2 = {0.0050d, 0.0100, 0.0200, 0.0150, 0.0250, -0.0100d, 0.0d};
    double[] payoffComputed = cap.payoff(swapRate1, swapRate2);
    for (int i = 0; i < swapRate1.length; i++) {
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *
          Math.max(WEIGHT1 * swapRate1[i] - WEIGHT2 * swapRate2[i] - CAPLET, 0.0);
      assertThat(payoffComputed[i]).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_primitive_floor() {
    CmsSpreadPeriodResolved floor =
        CmsSpreadPeriodResolved.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .underlyingSwap1(SWAP_1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .underlyingSwap2(SWAP_2)
            .floorlet(FLOORLET)
            .build();
    double[] swapRate1 = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] swapRate2 = {0.0050d, 0.0100, 0.0200, 0.0150, 0.0250, -0.0100d, 0.0d};
    for (int i = 0; i < swapRate1.length; i++) {
      double payoffComputed = floor.payoff(swapRate1[i], swapRate2[i]);
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *
          Math.max(FLOORLET - (WEIGHT1 * swapRate1[i] - WEIGHT2 * swapRate2[i]), 0.0);
      assertThat(payoffComputed).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

  @Test
  public void payoff_array_floor() {
    CmsSpreadPeriodResolved floor =
        CmsSpreadPeriodResolved.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .underlyingSwap1(SWAP_1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .underlyingSwap2(SWAP_2)
            .floorlet(FLOORLET)
            .build();
    double[] swapRate1 = {-0.0100d, 0.0d, 0.0050d, 0.0100, 0.0150, 0.0200, 0.0250};
    double[] swapRate2 = {0.0050d, 0.0100, 0.0200, 0.0150, 0.0250, -0.0100d, 0.0d};
    double[] payoffComputed = floor.payoff(swapRate1, swapRate2);
    for (int i = 0; i < swapRate1.length; i++) {
      double payoffExpected = NOTIONAL_1M * ACCRUAL_FACTOR *
          Math.max(FLOORLET - (WEIGHT1 * swapRate1[i] - WEIGHT2 * swapRate2[i]), 0.0);
      assertThat(payoffComputed[i]).isEqualTo(payoffExpected, TOLERANCE_PAYOFF);
    }
  }

}
