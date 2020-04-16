/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.fra;

import static com.opengamma.strata.basics.currency.Currency.AUD;
import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.currency.Currency.NZD;
import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_360;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.GBLO;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.SAT_SUN;
import static com.opengamma.strata.basics.date.Tenor.TENOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_3M;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.date;
import static com.opengamma.strata.product.common.BuySell.BUY;
import static com.opengamma.strata.product.fra.FraDiscountingMethod.AFMA;
import static com.opengamma.strata.product.fra.FraDiscountingMethod.ISDA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.AdjustableDate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.TenorAdjustment;
import com.opengamma.strata.basics.index.ImmutableIborIndex;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightRateComputation;
import com.opengamma.strata.product.swap.OvernightAccrualMethod;

/**
 * This is a modified version of the original Strata FraTest to fit with IBOR Fallback. 
 */
/**
 * Tests {@link Fra}.
 */
public class FraTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final double NOTIONAL_1M = 1_000_000d;
  private static final double FIXED_RATE = 0.025d;
  private static final double SPREAD = 0.0010d;
  private static final BusinessDayAdjustment BDA_MOD_FOLLOW = BusinessDayAdjustment.of(MODIFIED_FOLLOWING, GBLO);
  private static final DaysAdjustment PLUS_TWO_DAYS = DaysAdjustment.ofBusinessDays(2, GBLO);
  private static final DaysAdjustment MINUS_TWO_DAYS = DaysAdjustment.ofBusinessDays(-2, GBLO);
  private static final LocalDate START_DATE = date(2015, 6, 15);

  //-------------------------------------------------------------------------
  @Test
  public void test_builder_ibor() {
    Fra test = sut();
    assertThat(test.getBuySell()).isEqualTo(BUY);
    assertThat(test.getCurrency()).isEqualTo(GBP);  // defaulted
    assertThat(test.getNotional()).isCloseTo(NOTIONAL_1M, offset(0d));
    assertThat(test.getStartDate()).isEqualTo(date(2015, 6, 15));
    assertThat(test.getEndDate()).isEqualTo(date(2015, 9, 15));
    assertThat(test.getBusinessDayAdjustment()).isEqualTo(Optional.empty());
    assertThat(test.getPaymentDate()).isEqualTo(AdjustableDate.of(date(2015, 6, 15)));
    assertThat(test.getFixedRate()).isCloseTo(FIXED_RATE, offset(0d));
    assertThat(test.getSpread()).isCloseTo(SPREAD, offset(0d));
    assertThat(test.getIndex()).isEqualTo(GBP_LIBOR_3M);
    assertThat(test.getFixingDateOffset()).isEqualTo(GBP_LIBOR_3M.getFixingDateOffset());  // defaulted
    assertThat(test.getDayCount()).isEqualTo(ACT_365F);  // defaulted
    assertThat(test.getDiscounting()).isEqualTo(ISDA);  // defaulted
    assertThat(test.isCrossCurrency()).isFalse();
    assertThat(test.allPaymentCurrencies()).containsOnly(GBP);
    assertThat(test.allCurrencies()).containsOnly(GBP);
  }
  
  @Test
  public void test_builder_on() {
    Fra test = Fra.builder()
            .buySell(BUY)
            .notional(NOTIONAL_1M)
            .startDate(date(2015, 6, 15))
            .endDate(date(2015, 9, 15))
            .fixedRate(FIXED_RATE)
            .spread(SPREAD)
            .index(GBP_SONIA)
            .endCompositionDate(date(2015, 9, 17))
            .build();
    assertThat(test.getBuySell()).isEqualTo(BUY);
    assertThat(test.getCurrency()).isEqualTo(GBP);  // defaulted
    assertThat(test.getNotional()).isCloseTo(NOTIONAL_1M, offset(0d));
    assertThat(test.getStartDate()).isEqualTo(date(2015, 6, 15));
    assertThat(test.getEndDate()).isEqualTo(date(2015, 9, 15));
    assertThat(test.getEndCompositionDate().isPresent()).isTrue();
    assertThat(test.getEndCompositionDate().get()).isEqualTo(date(2015, 9, 17));
    assertThat(test.getBusinessDayAdjustment()).isEqualTo(Optional.empty());
    assertThat(test.getPaymentDate()).isEqualTo(AdjustableDate.of(date(2015, 6, 15)));
    assertThat(test.getFixedRate()).isCloseTo(FIXED_RATE, offset(0d));
    assertThat(test.getSpread()).isCloseTo(SPREAD, offset(0d));
    assertThat(test.getIndex()).isEqualTo(GBP_SONIA);
    assertThat(test.getFixingDateOffset()).isEqualTo(DaysAdjustment.NONE);  // defaulted
    assertThat(test.getDayCount()).isEqualTo(ACT_365F);  // defaulted
    assertThat(test.getDiscounting()).isEqualTo(ISDA);  // defaulted
    assertThat(test.isCrossCurrency()).isFalse();
    assertThat(test.allPaymentCurrencies()).containsOnly(GBP);
    assertThat(test.allCurrencies()).containsOnly(GBP);
  }

  @Test
  public void test_builder_AUD() {
    ImmutableIborIndex dummyIndex = ImmutableIborIndex.builder()
        .name("AUD-INDEX-3M")
        .currency(AUD)
        .dayCount(ACT_360)
        .fixingDateOffset(MINUS_TWO_DAYS)
        .effectiveDateOffset(PLUS_TWO_DAYS)
        .maturityDateOffset(TenorAdjustment.ofLastDay(TENOR_3M, BDA_MOD_FOLLOW))
        .fixingCalendar(SAT_SUN)
        .fixingTime(LocalTime.NOON)
        .fixingZone(ZoneId.of("Australia/Sydney"))
        .build();
    Fra test = Fra.builder()
        .buySell(BUY)
        .notional(NOTIONAL_1M)
        .startDate(date(2015, 6, 15))
        .endDate(date(2015, 9, 15))
        .paymentDate(AdjustableDate.of(date(2015, 6, 16)))
        .fixedRate(FIXED_RATE)
        .index(dummyIndex)
        .fixingDateOffset(MINUS_TWO_DAYS)
        .build();
    assertThat(test.getBuySell()).isEqualTo(BUY);
    assertThat(test.getCurrency()).isEqualTo(AUD);  // defaulted
    assertThat(test.getNotional()).isCloseTo(NOTIONAL_1M, offset(0d));
    assertThat(test.getStartDate()).isEqualTo(date(2015, 6, 15));
    assertThat(test.getEndDate()).isEqualTo(date(2015, 9, 15));
    assertThat(test.getBusinessDayAdjustment()).isEqualTo(Optional.empty());
    assertThat(test.getPaymentDate()).isEqualTo(AdjustableDate.of(date(2015, 6, 16)));
    assertThat(test.getFixedRate()).isCloseTo(FIXED_RATE, offset(0d));
    assertThat(test.getIndex()).isEqualTo(dummyIndex);
    assertThat(test.getFixingDateOffset()).isEqualTo(MINUS_TWO_DAYS);
    assertThat(test.getDayCount()).isEqualTo(ACT_360);  // defaulted
    assertThat(test.getDiscounting()).isEqualTo(AFMA);  // defaulted
  }

  @Test
  public void test_builder_NZD() {
    ImmutableIborIndex dummyIndex = ImmutableIborIndex.builder()
        .name("NZD-INDEX-3M")
        .currency(NZD)
        .dayCount(ACT_360)
        .fixingDateOffset(MINUS_TWO_DAYS)
        .effectiveDateOffset(PLUS_TWO_DAYS)
        .maturityDateOffset(TenorAdjustment.ofLastDay(TENOR_3M, BDA_MOD_FOLLOW))
        .fixingCalendar(SAT_SUN)
        .fixingTime(LocalTime.NOON)
        .fixingZone(ZoneId.of("NZ"))
        .build();
    Fra test = Fra.builder()
        .buySell(BUY)
        .notional(NOTIONAL_1M)
        .startDate(date(2015, 6, 15))
        .endDate(date(2015, 9, 15))
        .paymentDate(AdjustableDate.of(date(2015, 6, 16)))
        .fixedRate(FIXED_RATE)
        .index(dummyIndex)
        .fixingDateOffset(MINUS_TWO_DAYS)
        .build();
    assertThat(test.getBuySell()).isEqualTo(BUY);
    assertThat(test.getCurrency()).isEqualTo(NZD);  // defaulted
    assertThat(test.getNotional()).isCloseTo(NOTIONAL_1M, offset(0d));
    assertThat(test.getStartDate()).isEqualTo(date(2015, 6, 15));
    assertThat(test.getEndDate()).isEqualTo(date(2015, 9, 15));
    assertThat(test.getBusinessDayAdjustment()).isEqualTo(Optional.empty());
    assertThat(test.getPaymentDate()).isEqualTo(AdjustableDate.of(date(2015, 6, 16)));
    assertThat(test.getFixedRate()).isCloseTo(FIXED_RATE, offset(0d));
    assertThat(test.getIndex()).isEqualTo(dummyIndex);
    assertThat(test.getFixingDateOffset()).isEqualTo(MINUS_TWO_DAYS);
    assertThat(test.getDayCount()).isEqualTo(ACT_360);  // defaulted
    assertThat(test.getDiscounting()).isEqualTo(AFMA);  // defaulted
  }

  @Test
  public void test_builder_datesInOrder() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Fra.builder()
            .buySell(BUY)
            .notional(NOTIONAL_1M)
            .startDate(date(2015, 6, 15))
            .endDate(date(2015, 6, 14))
            .fixedRate(FIXED_RATE)
            .index(GBP_LIBOR_3M)
            .build());
  }

  @Test
  public void test_builder_noIndex() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Fra.builder()
            .buySell(BUY)
            .notional(NOTIONAL_1M)
            .startDate(date(2015, 6, 15))
            .endDate(date(2015, 9, 15))
            .fixedRate(FIXED_RATE)
            .build());
  }

  @Test
  public void test_builder_noDates() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Fra.builder()
            .buySell(BUY)
            .notional(NOTIONAL_1M)
            .endDate(date(2015, 9, 15))
            .fixedRate(FIXED_RATE)
            .index(GBP_LIBOR_3M)
            .build());
  }

  //-------------------------------------------------------------------------
  @Test
  public void test_resolve_Ibor() {
    Fra fra = Fra.builder()
        .buySell(BUY)
        .notional(NOTIONAL_1M)
        .startDate(date(2015, 6, 15))
        .endDate(date(2015, 9, 15))
        .paymentDate(AdjustableDate.of(date(2015, 6, 20), BDA_MOD_FOLLOW))
        .fixedRate(FIXED_RATE)
        .spread(SPREAD)
        .index(GBP_LIBOR_3M)
        .fixingDateOffset(MINUS_TWO_DAYS)
        .build();
    ResolvedFra test = fra.resolve(REF_DATA);
    assertThat(test.getCurrency()).isEqualTo(GBP);
    assertThat(test.getNotional()).isCloseTo(NOTIONAL_1M, offset(0d));
    assertThat(test.getStartDate()).isEqualTo(date(2015, 6, 15));
    assertThat(test.getEndDate()).isEqualTo(date(2015, 9, 15));
    assertThat(test.getPaymentDate()).isEqualTo(date(2015, 6, 22));
    assertThat(test.getFixedRate()).isCloseTo(FIXED_RATE, offset(0d));
    assertThat(test.getSpread()).isCloseTo(SPREAD, offset(0d));
    assertThat(test.getFloatingRate()).isEqualTo(IborRateComputation.of(GBP_LIBOR_3M, date(2015, 6, 11), REF_DATA));
    assertThat(test.getYearFraction()).isCloseTo(ACT_365F.yearFraction(date(2015, 6, 15), date(2015, 9, 15)), offset(0d));
    assertThat(test.getDiscounting()).isEqualTo(ISDA);
  }
  
  @Test
  public void test_resolve_On() {
    Fra fra = Fra.builder()
        .buySell(BUY)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(date(2015, 9, 15))
        .fixedRate(FIXED_RATE)
        .spread(SPREAD)
        .index(GBP_SONIA)
        .endCompositionDate(date(2015, 9, 17))
        .build();
    ResolvedFra test = fra.resolve(REF_DATA);
    assertThat(test.getCurrency()).isEqualTo(GBP);
    assertThat(test.getNotional()).isCloseTo(NOTIONAL_1M, offset(0d));
    assertThat(test.getStartDate()).isEqualTo(START_DATE);
    assertThat(test.getEndDate()).isEqualTo(date(2015, 9, 15));
    assertThat(test.getPaymentDate()).isEqualTo(START_DATE);
    assertThat(test.getFixedRate()).isCloseTo(FIXED_RATE, offset(0d));
    assertThat(test.getSpread()).isCloseTo(SPREAD, offset(0d));
    assertThat(test.getFloatingRate())
    .isEqualTo(OvernightRateComputation.of(GBP_SONIA, START_DATE, date(2015, 9, 17), 
        0, OvernightAccrualMethod.COMPOUNDED, REF_DATA));
    assertThat(test.getYearFraction()).isCloseTo(ACT_365F.yearFraction(date(2015, 6, 15), date(2015, 9, 15)), offset(0d));
    assertThat(test.getDiscounting()).isEqualTo(ISDA);
  }

  //-------------------------------------------------------------------------

  @Test
  public void test_serialization() {
    assertSerialization(sut());
  }

  //-------------------------------------------------------------------------
  static Fra sut() {
    return Fra.builder()
        .buySell(BUY)
        .notional(NOTIONAL_1M)
        .startDate(date(2015, 6, 15))
        .endDate(date(2015, 9, 15))
        .fixedRate(FIXED_RATE)
        .spread(SPREAD)
        .index(GBP_LIBOR_3M)
        .build();
  }

}
