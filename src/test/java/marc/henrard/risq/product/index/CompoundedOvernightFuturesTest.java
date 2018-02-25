/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.product.index;

import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.product.SecurityId;

/**
 * Tests {@link CompoundedOvernightFutures}.
 * 
 * @author Marc Henrard
 */
@Test
public class CompoundedOvernightFuturesTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  
  private static final SecurityId ID = SecurityId.of(StandardId.of("muRisQ", "Fut"));
  private static final double NOTIONAL = 500_000;
  private static final LocalDate START_ACCRUAL_DATE = LocalDate.of(2018, 6, 20);
  private static final LocalDate END_ACCRUAL_DATE = LocalDate.of(2018, 9, 19);
  private static final OvernightIndex INDEX = GBP_SONIA;
  
  private static final double TOLERANCE_AF = 1.0E-8;

  /* Test the defaulting of the accrual factor. */
  public void accrualFactorDefault() {
    CompoundedOvernightFutures test = CompoundedOvernightFutures.builder()
        .securityId(ID)
        .notional(NOTIONAL)
        .startAccrualDate(START_ACCRUAL_DATE)
        .endAccrualDate(END_ACCRUAL_DATE)
        .index(INDEX)
        .currency(GBP).build();
    double af = INDEX.getDayCount().relativeYearFraction(START_ACCRUAL_DATE, END_ACCRUAL_DATE);
    assertEquals(test.getAccrualFactor(), af, TOLERANCE_AF);
  }

  /* Test the defaulting of the currency. */
  public void currencyDefault() {
    CompoundedOvernightFutures test = CompoundedOvernightFutures.builder()
        .securityId(ID)
        .notional(NOTIONAL)
        .accrualFactor(0.25)
        .startAccrualDate(START_ACCRUAL_DATE)
        .endAccrualDate(END_ACCRUAL_DATE)
        .index(INDEX).build();
    assertEquals(test.getCurrency(), GBP);
  }

  /* Test the resolution of the futures. */
  public void resolved() {
    CompoundedOvernightFutures test = CompoundedOvernightFutures.builder()
        .securityId(ID)
        .notional(NOTIONAL)
        .startAccrualDate(START_ACCRUAL_DATE)
        .endAccrualDate(END_ACCRUAL_DATE)
        .index(INDEX).build();
    CompoundedOvernightFuturesResolved resolved = test.resolve(REF_DATA);
    assertEquals(test.getSecurityId(), resolved.getSecurityId());
    assertEquals(test.getCurrency(), resolved.getCurrency());
    assertEquals(test.getNotional(), resolved.getNotional());
    assertEquals(test.getAccrualFactor(), resolved.getAccrualFactor());
    assertEquals(test.getStartAccrualDate(), resolved.getStartAccrualDate());
    assertEquals(test.getEndAccrualDate(), resolved.getEndAccrualDate());
    assertEquals(test.getIndex(), resolved.getIndex());
    HolidayCalendar calendar = REF_DATA.getValue(GBP_SONIA.getFixingCalendar());
    List<LocalDate> onDates = new ArrayList<>();
    LocalDate currentDate = START_ACCRUAL_DATE;
    while (!currentDate.isAfter(END_ACCRUAL_DATE)) {
      onDates.add(currentDate);
      currentDate = calendar.next(currentDate);
    }
    int nbOnDates = onDates.size();
    assertEquals(onDates.get(0), START_ACCRUAL_DATE);
    assertEquals(onDates.get(nbOnDates - 1), END_ACCRUAL_DATE);
    assertEquals(resolved.getOnDates(), onDates);
    double delta = 0.0;
    for (int i = 0; i < onDates.size() - 1; i++) {
      double deltai = GBP_SONIA.getDayCount().relativeYearFraction(onDates.get(i), onDates.get(i + 1));
      assertEquals(resolved.getOnAccruals().get(i), deltai, TOLERANCE_AF);
      delta += deltai;
    }
    assertEquals(resolved.getAccrualFactor(), delta, TOLERANCE_AF);
  }
  
}
