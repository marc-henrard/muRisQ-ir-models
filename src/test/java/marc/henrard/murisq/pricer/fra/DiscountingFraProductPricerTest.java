/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.fra;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.fra.FraDiscountingMethod;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;

import marc.henrard.murisq.dataset.MulticurveStandardDataSet;
import marc.henrard.murisq.product.fra.ResolvedFra;

/**
 * Tests {@link DiscountingFraProductPricer}.
 */
public class DiscountingFraProductPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2018, 8, 30);

  private static final double NOTIONAL_1M = 1_000_000d;
  private static final double FIXED_RATE = 0.025d;
  private static final double SPREAD = 0.0010d;
  private static final double ACCRUAL_FACTOR = 0.25;
  private static final LocalDate START_DATE = LocalDate.of(2019, 9, 3);
  private static final LocalDate END_DATE_ACCRUED = LocalDate.of(2019, 12, 3);
  private static final LocalDate END_DATE_RATE = LocalDate.of(2019, 12, 5);
  
  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/test/resources/curve-config/USD-DSCONOIS-L3MIRS/";
  private static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardDataSet.multicurve(VALUATION_DATE,
          CurveGroupName.of("USD-DSCONOIS-L3MIRS"),
          ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-group.csv"),
          ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-settings-zrlinear.csv"),
          ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-nodes.csv"), 
          "src/test/resources/quotes/standalone/MARKET-DATA-2018-08-30.csv", REF_DATA);
  
  /* Pricer */
  private static final DiscountingFraProductPricer PRICER_FRA = DiscountingFraProductPricer.DEFAULT;
  private static final RatesFiniteDifferenceSensitivityCalculator FD_CALC = 
      new RatesFiniteDifferenceSensitivityCalculator(1.0E-6);
  
  private static final double TOLERANCE_RATE = 1.0E-10;
  private static final double TOLERANCE_PV = 1.0E-6;
  private static final double TOLERANCE_DELTA = 1.0E-1;
  
  /* Note: during FRA period does not work properly as it is not always coherent (but its is still ISDA proposal). */
  
  /* Par rate with ISDA discounting, payment at start date. Ibor FRA*/
  @Test
  public void parRate_ISDA_Ibor_start() {
    ResolvedFra fra = fraIborIsdaPayAtStart();
    parRateIbor(fra);
  }
  
  /* Par rate with ISDA discounting, payment at end date. Ibor FRA*/
  @Test
  public void parRate_ISDA_Ibor_end() {
    ResolvedFra fra = fraIborIsdaPayAtEnd();
    parRateIbor(fra);
  }
  
  /* Par rate with NONE discounting, payment at start date. Ibor FRA*/
  @Test
  public void parRate_None_Ibor_start() {
    ResolvedFra fra = fraIborNonePayAtStart();
    parRateIbor(fra);
  }
  
  /* Par rate with NONE discounting, payment at end date. Ibor FRA*/
  @Test
  public void parRate_None_Ibor_end() {
    ResolvedFra fra = fraIborNonePayAtEnd();
    parRateIbor(fra);
  }

  /* Par rate for all Ibor cases. */
  private void parRateIbor(ResolvedFra fra) {
    double parRate = PRICER_FRA.parRate(fra, MULTICURVE);
    IborRateComputation comput = (IborRateComputation) fra.getFloatingRate();
    double curveRate = MULTICURVE.iborIndexRates(USD_LIBOR_3M).rate(comput.getObservation());
    assertThat(parRate).isCloseTo(curveRate + SPREAD, offset(TOLERANCE_RATE));
  }
  
  /* Present value with ISDA discounting, payment at start date. Ibor FRA*/
  @Test
  public void presentValue_ISDA_Ibor_start() {
    ResolvedFra fra = fraIborIsdaPayAtStart();
    presentValueIborIsda(fra);
  }
  
  /* Present value with ISDA discounting, payment at end date. Ibor FRA*/
  @Test
  public void presentValue_ISDA_Ibor_end() {
    ResolvedFra fra = fraIborIsdaPayAtEnd();
    presentValueIborIsda(fra);
  }

  /* Present value for all Ibor cases. */
  private void presentValueIborIsda(ResolvedFra fra) {
    CurrencyAmount pv = PRICER_FRA.presentValue(fra, MULTICURVE);
    IborRateComputation comput = (IborRateComputation) fra.getFloatingRate();
    double curveRate = MULTICURVE.iborIndexRates(USD_LIBOR_3M).rate(comput.getObservation());
    double paymentAmount = (curveRate + SPREAD - FIXED_RATE) * ACCRUAL_FACTOR * NOTIONAL_1M; // BUY
    double df = MULTICURVE.discountFactor(USD, fra.getPaymentDate());
    double pvExpected = df * paymentAmount / (1 + ACCRUAL_FACTOR * curveRate);
    assertThat(pv.getAmount()).isCloseTo(pvExpected, offset(TOLERANCE_PV));
    MultiCurrencyAmount ce = PRICER_FRA.currencyExposure(fra, MULTICURVE);
    assertThat(ce.getCurrencies().size()).isEqualTo(1);
    assertThat(ce.getAmount(USD).getAmount()).isCloseTo(pv.getAmount(), offset(TOLERANCE_PV));
  }
  
  /* Present value with ISDA discounting, payment at start date. Ibor FRA*/
  @Test
  public void presentValue_NONE_Ibor_start() {
    ResolvedFra fra = fraIborNonePayAtStart();
    presentValueIborNone(fra);
  }
  
  /* Present value with ISDA discounting, payment at end date. Ibor FRA*/
  @Test
  public void presentValue_NONE_Ibor_end() {
    ResolvedFra fra = fraIborNonePayAtEnd();
    presentValueIborNone(fra);
  }

  /* Par rate for all Ibor cases. */
  private void presentValueIborNone(ResolvedFra fra) {
    CurrencyAmount pv = PRICER_FRA.presentValue(fra, MULTICURVE);
    IborRateComputation comput = (IborRateComputation) fra.getFloatingRate();
    double curveRate = MULTICURVE.iborIndexRates(USD_LIBOR_3M).rate(comput.getObservation());
    double paymentAmount = (curveRate + SPREAD - FIXED_RATE); // BUY
    double df = MULTICURVE.discountFactor(USD, fra.getPaymentDate());
    double pvExpected = df * paymentAmount * ACCRUAL_FACTOR * NOTIONAL_1M;
    assertThat(pv.getAmount()).isCloseTo(pvExpected, offset(TOLERANCE_PV));
    MultiCurrencyAmount ce = PRICER_FRA.currencyExposure(fra, MULTICURVE);
    assertThat(ce.getCurrencies().size()).isEqualTo(1);
    assertThat(ce.getAmount(USD).getAmount()).isCloseTo(pv.getAmount(), offset(TOLERANCE_PV));
  }
  
  /* Par rate with ISDA discounting, payment at start date. ON FRA*/
  @Test
  public void parRate_ISDA_On_start() {
    ResolvedFra fra = fraOnIsdaPayAtStart();
    parRateOn(fra);
  }
  
  /* Par rate with ISDA discounting, payment at end date. ON FRA*/
  @Test
  public void parRate_ISDA_On_end() {
    ResolvedFra fra = fraOnIsdaPayAtEnd();
    parRateOn(fra);
  }
  
  /* Par rate with NONE discounting, payment at start date. ON FRA*/
  @Test
  public void parRate_NONE_On_start() {
    ResolvedFra fra = fraOnNonePayAtStart();
    parRateOn(fra);
  }
  
  /* Par rate with NONE discounting, payment at end date. ON FRA*/
  @Test
  public void parRate_NONE_On_end() {
    ResolvedFra fra = fraOnNonePayAtEnd();
    parRateOn(fra);
  }
  
  /* Par rate ON FRA*/
 private void parRateOn(ResolvedFra fra) {
    double parRate = PRICER_FRA.parRate(fra, MULTICURVE);
    double curveRate = MULTICURVE.overnightIndexRates(USD_FED_FUND)
        .periodRate(OvernightIndexObservation.of(USD_FED_FUND, START_DATE, REF_DATA), END_DATE_RATE);
    assertThat(parRate).isCloseTo(curveRate + SPREAD, offset(TOLERANCE_RATE));
  }
 
 /* Present value with ISDA discounting, payment at start date. ON FRA*/
 @Test
 public void presentValue_ISDA_On_start() {
   ResolvedFra fra = fraOnIsdaPayAtStart();
   presentValueOnIsda(fra);
 }
 
 /* Present value with ISDA discounting, payment at end date. ON FRA*/
 @Test
 public void presentValue_ISDA_On_end() {
   ResolvedFra fra = fraOnIsdaPayAtEnd();
   presentValueOnIsda(fra);
 }

 /* Present value for all Ibor cases. */
 private void presentValueOnIsda(ResolvedFra fra) {
   CurrencyAmount pv = PRICER_FRA.presentValue(fra, MULTICURVE);
   double curveRate = MULTICURVE.overnightIndexRates(USD_FED_FUND)
       .periodRate(OvernightIndexObservation.of(USD_FED_FUND, START_DATE, REF_DATA), END_DATE_RATE);
   double paymentAmount = (curveRate + SPREAD - FIXED_RATE) * ACCRUAL_FACTOR * NOTIONAL_1M; // BUY
   double df = MULTICURVE.discountFactor(USD, fra.getPaymentDate());
   double pvExpected = df * paymentAmount / (1 + ACCRUAL_FACTOR * curveRate);
   assertThat(pv.getAmount()).isCloseTo(pvExpected, offset(TOLERANCE_PV));
   MultiCurrencyAmount ce = PRICER_FRA.currencyExposure(fra, MULTICURVE);
   assertThat(ce.getCurrencies().size()).isEqualTo(1);
   assertThat(ce.getAmount(USD).getAmount()).isCloseTo(pv.getAmount(), offset(TOLERANCE_PV));
 }
 
 /* Present value with NONE discounting, payment at start date. ON FRA*/
 @Test
 public void presentValue_NONE_On_start() {
   ResolvedFra fra = fraOnNonePayAtStart();
   presentValueOnNone(fra);
 }
 
 /* Present value with NONE discounting, payment at end date. ON FRA*/
 @Test
 public void presentValue_NONE_On_end() {
   ResolvedFra fra = fraOnNonePayAtEnd();
   presentValueOnNone(fra);
 }

 /* Present value for all Ibor cases. */
 private void presentValueOnNone(ResolvedFra fra) {
   CurrencyAmount pv = PRICER_FRA.presentValue(fra, MULTICURVE);
   double curveRate = MULTICURVE.overnightIndexRates(USD_FED_FUND)
       .periodRate(OvernightIndexObservation.of(USD_FED_FUND, START_DATE, REF_DATA), END_DATE_RATE);
   double paymentAmount = (curveRate + SPREAD - FIXED_RATE) * ACCRUAL_FACTOR * NOTIONAL_1M; // BUY
   double df = MULTICURVE.discountFactor(USD, fra.getPaymentDate());
   double pvExpected = df * paymentAmount;
   assertThat(pv.getAmount()).isCloseTo(pvExpected, offset(TOLERANCE_PV));
   MultiCurrencyAmount ce = PRICER_FRA.currencyExposure(fra, MULTICURVE);
   assertThat(ce.getCurrencies().size()).isEqualTo(1);
   assertThat(ce.getAmount(USD).getAmount()).isCloseTo(pv.getAmount(), offset(TOLERANCE_PV));
 }
 
 /* Present value sensitivity with ISDA discounting, payment at start date. Ibor FRA*/
 @Test
 public void presentValueSensitivity_ISDA_Ibor_start() {
   ResolvedFra fra = fraIborIsdaPayAtStart();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with ISDA discounting, payment at end date. Ibor FRA*/
 @Test
 public void presentValueSensitivity_ISDA_Ibor_end() {
   ResolvedFra fra = fraIborIsdaPayAtEnd();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with NONE discounting, payment at start date. Ibor FRA*/
 @Test
 public void presentValueSensitivity_NONE_Ibor_start() {
   ResolvedFra fra = fraIborNonePayAtStart();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with NONE discounting, payment at end date. Ibor FRA*/
 @Test
 public void presentValueSensitivity_NONE_Ibor_end() {
   ResolvedFra fra = fraIborNonePayAtEnd();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with ISDA discounting, payment at start date. ON FRA*/
 @Test
 public void presentValueSensitivity_ISDA_On_start() {
   ResolvedFra fra = fraOnIsdaPayAtStart();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with ISDA discounting, payment at end date. ON FRA*/
 @Test
 public void presentValueSensitivity_ISDA_On_end() {
   ResolvedFra fra = fraOnIsdaPayAtEnd();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with NONE discounting, payment at start date. ON FRA*/
 @Test
 public void presentValueSensitivity_NONE_On_start() {
   ResolvedFra fra = fraOnNonePayAtStart();
   presentValueSensitivity(fra);
 }
 
 /* Present value sensitivity with NONE discounting, payment at end date. ON FRA*/
 @Test
 public void presentValueSensitivity_NONE_On_end() {
   ResolvedFra fra = fraOnNonePayAtEnd();
   presentValueSensitivity(fra);
 }

 /* Present value for all Ibor cases. */
 private void presentValueSensitivity(ResolvedFra fra) {
   PointSensitivities pts = PRICER_FRA.presentValueSensitivity(fra, MULTICURVE);
   CurrencyParameterSensitivities psAd = MULTICURVE.parameterSensitivity(pts);
   CurrencyParameterSensitivities psFd = 
       FD_CALC.sensitivity(MULTICURVE, m -> PRICER_FRA.presentValue(fra, m));
   assertThat(psAd.equalWithTolerance(psFd, TOLERANCE_DELTA)).isTrue();
 }
  
  private ResolvedFra fraIborIsdaPayAtStart() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(START_DATE)
        .yearFraction(ACCRUAL_FACTOR)
        .fixedRate(FIXED_RATE)
        .floatingRate(IborRateComputation.of(USD_LIBOR_3M, USD_LIBOR_3M.calculateFixingFromEffective(START_DATE, REF_DATA), REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.ISDA)
        .build();
  }
  
  private ResolvedFra fraOnIsdaPayAtStart() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(START_DATE)
        .yearFraction(ACCRUAL_FACTOR)
        .fixedRate(FIXED_RATE)
        .floatingRate(OvernightCompoundedRateComputation.of(USD_FED_FUND, START_DATE, END_DATE_RATE, REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.ISDA)
        .build();
  }
  
  private ResolvedFra fraIborIsdaPayAtEnd() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(END_DATE_ACCRUED)
        .yearFraction(ACCRUAL_FACTOR)
        .fixedRate(FIXED_RATE)
        .floatingRate(IborRateComputation.of(USD_LIBOR_3M, USD_LIBOR_3M.calculateFixingFromEffective(START_DATE, REF_DATA), REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.ISDA)
        .build();
  }
  
  private ResolvedFra fraOnIsdaPayAtEnd() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(END_DATE_ACCRUED)
        .yearFraction(0.25)
        .fixedRate(FIXED_RATE)
        .floatingRate(OvernightCompoundedRateComputation.of(USD_FED_FUND, START_DATE, END_DATE_RATE, REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.ISDA)
        .build();
  }

  
  private ResolvedFra fraIborNonePayAtStart() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(START_DATE)
        .yearFraction(ACCRUAL_FACTOR)
        .fixedRate(FIXED_RATE)
        .floatingRate(IborRateComputation.of(USD_LIBOR_3M, USD_LIBOR_3M.calculateFixingFromEffective(START_DATE, REF_DATA), REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.NONE)
        .build();
  }
  
  private ResolvedFra fraOnNonePayAtStart() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(START_DATE)
        .yearFraction(0.25)
        .fixedRate(FIXED_RATE)
        .floatingRate(OvernightCompoundedRateComputation.of(USD_FED_FUND, START_DATE, END_DATE_RATE, REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.NONE)
        .build();
  }
  
  private ResolvedFra fraIborNonePayAtEnd() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(END_DATE_ACCRUED)
        .yearFraction(ACCRUAL_FACTOR)
        .fixedRate(FIXED_RATE)
        .floatingRate(IborRateComputation.of(USD_LIBOR_3M, USD_LIBOR_3M.calculateFixingFromEffective(START_DATE, REF_DATA), REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.NONE)
        .build();
  }
  
  private ResolvedFra fraOnNonePayAtEnd() {
    return ResolvedFra.builder()
        .currency(USD)
        .notional(NOTIONAL_1M)
        .startDate(START_DATE)
        .endDate(END_DATE_ACCRUED)
        .paymentDate(END_DATE_ACCRUED)
        .yearFraction(0.25)
        .fixedRate(FIXED_RATE)
        .floatingRate(OvernightCompoundedRateComputation.of(USD_FED_FUND, START_DATE, END_DATE_RATE, REF_DATA))
        .spread(SPREAD)
        .discounting(FraDiscountingMethod.NONE)
        .build();
  }

}
