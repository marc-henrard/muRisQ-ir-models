/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.util.OptionalDouble;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapIndex;
import com.opengamma.strata.product.swap.SwapIndices;


/**
 * Tests {@link CmsSpreadPeriod}.
 */
public class CmsSpreadPeriodTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final double NOTIONAL_1M = 1_000_000d;
  private static final BusinessDayAdjustment ADJUSTMENT = 
      BusinessDayAdjustment.of(BusinessDayConventions.MODIFIED_FOLLOWING, HolidayCalendarIds.EUTA);
  private static final LocalDate START_DATE = LocalDate.of(2020, 8, 18);
  private static final AdjustableDate START_DATE_ADJ = AdjustableDate.of(START_DATE, ADJUSTMENT);
  private static final LocalDate END_DATE = LocalDate.of(2020, 10, 18);
  private static final AdjustableDate END_DATE_ADJ = AdjustableDate.of(END_DATE, ADJUSTMENT);
  private static final double ACCRUAL_FACTOR = 0.25;
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2020, 10, 28);
  private static final AdjustableDate PAYMENT_DATE_ADJ = AdjustableDate.of(PAYMENT_DATE, ADJUSTMENT);
  private static final LocalDate FIXING_DATE = LocalDate.of(2020, 8, 14);
  private static final Double CAPLET = 0.01;
  private static final Double FLOORLET = 0.02;
  private static final double WEIGHT1 = 1.00;
  private static final SwapIndex INDEX1 = SwapIndices.EUR_EURIBOR_1100_10Y;
  private static final double WEIGHT2 = 1.50;
  private static final SwapIndex INDEX2 = SwapIndices.EUR_EURIBOR_1100_2Y;
  private static final CmsSpreadPeriod CMS_SPREAD_PERIOD_CPN = 
      CmsSpreadPeriod.builder()
      .notional(NOTIONAL_1M)
      .startDate(START_DATE_ADJ)
      .endDate(END_DATE_ADJ)
      .yearFraction(ACCRUAL_FACTOR)
      .paymentDate(PAYMENT_DATE_ADJ)
      .fixingDate(FIXING_DATE)
      .weight1(WEIGHT1)
      .index1(INDEX1)
      .weight2(WEIGHT2)
      .index2(INDEX2)
      .build();
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_PAYOFF = offset(1.0E-8);

  @Test
  public void builder_cpn() {
    assertThat(CMS_SPREAD_PERIOD_CPN.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(CMS_SPREAD_PERIOD_CPN.getStartDate()).isEqualTo(START_DATE_ADJ);
    assertThat(CMS_SPREAD_PERIOD_CPN.getEndDate()).isEqualTo(END_DATE_ADJ);
    assertThat(CMS_SPREAD_PERIOD_CPN.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(CMS_SPREAD_PERIOD_CPN.getPaymentDate()).isEqualTo(PAYMENT_DATE_ADJ);
    assertThat(CMS_SPREAD_PERIOD_CPN.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(CMS_SPREAD_PERIOD_CPN.getWeight1()).isEqualTo(WEIGHT1);
    assertThat(CMS_SPREAD_PERIOD_CPN.getIndex1()).isEqualTo(INDEX1);
    assertThat(CMS_SPREAD_PERIOD_CPN.getWeight2()).isEqualTo(WEIGHT2);
    assertThat(CMS_SPREAD_PERIOD_CPN.getIndex2()).isEqualTo(INDEX2);
    assertThat(CMS_SPREAD_PERIOD_CPN.getCaplet()).isEqualTo(OptionalDouble.empty());
    assertThat(CMS_SPREAD_PERIOD_CPN.getFloorlet()).isEqualTo(OptionalDouble.empty());
    assertThat(CMS_SPREAD_PERIOD_CPN.getCurrency()).isEqualTo(Currency.EUR);
  }

  @Test
  public void builder_cap() {
    CmsSpreadPeriod cap =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .caplet(CAPLET)
            .build();
    assertThat(cap.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(cap.getStartDate()).isEqualTo(START_DATE_ADJ);
    assertThat(cap.getEndDate()).isEqualTo(END_DATE_ADJ);
    assertThat(cap.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(cap.getPaymentDate()).isEqualTo(PAYMENT_DATE_ADJ);
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
    CmsSpreadPeriod floor =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
            .floorlet(FLOORLET)
            .build();
    assertThat(floor.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(floor.getStartDate()).isEqualTo(START_DATE_ADJ);
    assertThat(floor.getEndDate()).isEqualTo(END_DATE_ADJ);
    assertThat(floor.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(floor.getPaymentDate()).isEqualTo(PAYMENT_DATE_ADJ);
    assertThat(floor.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(floor.getWeight1()).isEqualTo(WEIGHT1);
    assertThat(floor.getIndex1()).isEqualTo(INDEX1);
    assertThat(floor.getWeight2()).isEqualTo(WEIGHT2);
    assertThat(floor.getIndex2()).isEqualTo(INDEX2);
    assertThat(floor.getFloorlet().getAsDouble()).isEqualTo(FLOORLET);
    assertThat(floor.getCaplet()).isEqualTo(OptionalDouble.empty());
  }

  @Test
  public void builder_cpn_default_weights() {
    CmsSpreadPeriod cpn =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .index1(INDEX1)
            .index2(INDEX2)
            .build();
    assertThat(cpn.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(cpn.getStartDate()).isEqualTo(START_DATE_ADJ);
    assertThat(cpn.getEndDate()).isEqualTo(END_DATE_ADJ);
    assertThat(cpn.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(cpn.getPaymentDate()).isEqualTo(PAYMENT_DATE_ADJ);
    assertThat(cpn.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(cpn.getWeight1()).isEqualTo(1.0d);
    assertThat(cpn.getIndex1()).isEqualTo(INDEX1);
    assertThat(cpn.getWeight2()).isEqualTo(1.0d);
    assertThat(cpn.getIndex2()).isEqualTo(INDEX2);
    assertThat(cpn.getCaplet()).isEqualTo(OptionalDouble.empty());
    assertThat(cpn.getFloorlet()).isEqualTo(OptionalDouble.empty());
  }

  @Test
  public void builder_cpn_default_paymentdate() {
    CmsSpreadPeriod cpn =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .fixingDate(FIXING_DATE)
            .index1(INDEX1)
            .index2(INDEX2)
            .build();
    assertThat(cpn.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(cpn.getStartDate()).isEqualTo(START_DATE_ADJ);
    assertThat(cpn.getEndDate()).isEqualTo(END_DATE_ADJ);
    assertThat(cpn.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(cpn.getPaymentDate()).isEqualTo(END_DATE_ADJ);
    assertThat(cpn.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(cpn.getWeight1()).isEqualTo(1.0d);
    assertThat(cpn.getIndex1()).isEqualTo(INDEX1);
    assertThat(cpn.getWeight2()).isEqualTo(1.0d);
    assertThat(cpn.getIndex2()).isEqualTo(INDEX2);
    assertThat(cpn.getCaplet()).isEqualTo(OptionalDouble.empty());
    assertThat(cpn.getFloorlet()).isEqualTo(OptionalDouble.empty());
  }

  @Test
  public void builder_different_currencies() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .index1(INDEX1)
            .index2(SwapIndices.USD_LIBOR_1100_10Y)
            .build());
  }

  @Test
  public void builder_wrong_date_order() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(END_DATE_ADJ)
            .endDate(START_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .index1(INDEX1)
            .index2(INDEX2)
            .build());
  }
  
  @Test
  public void resolve() {
    CmsSpreadPeriodResolved resolved = CMS_SPREAD_PERIOD_CPN.resolve(REF_DATA);
    assertThat(resolved.getNotional()).isEqualTo(NOTIONAL_1M);
    assertThat(resolved.getStartDate()).isEqualTo(START_DATE);
    assertThat(resolved.getEndDate()).isEqualTo(ADJUSTMENT.adjust(END_DATE,REF_DATA));
    assertThat(resolved.getYearFraction()).isEqualTo(ACCRUAL_FACTOR);
    assertThat(resolved.getPaymentDate()).isEqualTo(PAYMENT_DATE);
    assertThat(resolved.getFixingDate()).isEqualTo(FIXING_DATE);
    assertThat(resolved.getWeight1()).isEqualTo(WEIGHT1);
    assertThat(resolved.getIndex1()).isEqualTo(INDEX1);
    assertThat(resolved.getWeight2()).isEqualTo(WEIGHT2);
    assertThat(resolved.getIndex2()).isEqualTo(INDEX2);
    assertThat(resolved.getCaplet()).isEqualTo(OptionalDouble.empty());
    assertThat(resolved.getFloorlet()).isEqualTo(OptionalDouble.empty());
    assertThat(resolved.getCurrency()).isEqualTo(Currency.EUR);
    ResolvedSwap underlyingSwap1 =
        INDEX1.getTemplate().createTrade(FIXING_DATE, BuySell.SELL, 1.0d, 1.0d, REF_DATA)
            .getProduct()
            .resolve(REF_DATA);
    ResolvedSwap underlyingSwap2 =
        INDEX2.getTemplate().createTrade(FIXING_DATE, BuySell.SELL, 1.0d, 1.0d, REF_DATA)
            .getProduct()
            .resolve(REF_DATA);
    assertThat(resolved.getUnderlyingSwap1()).isEqualTo(underlyingSwap1);
    assertThat(resolved.getUnderlyingSwap2()).isEqualTo(underlyingSwap2);
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
    CmsSpreadPeriod cap =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
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
    CmsSpreadPeriod cap =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
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
    CmsSpreadPeriod floor =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
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
    CmsSpreadPeriod floor =
        CmsSpreadPeriod.builder()
            .notional(NOTIONAL_1M)
            .startDate(START_DATE_ADJ)
            .endDate(END_DATE_ADJ)
            .yearFraction(ACCRUAL_FACTOR)
            .paymentDate(PAYMENT_DATE_ADJ)
            .fixingDate(FIXING_DATE)
            .weight1(WEIGHT1)
            .index1(INDEX1)
            .weight2(WEIGHT2)
            .index2(INDEX2)
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
