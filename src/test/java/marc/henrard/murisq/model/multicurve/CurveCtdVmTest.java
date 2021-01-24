/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.USNY;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.param.CurrencyParameterSensitivity;
import com.opengamma.strata.market.param.ParameterSize;
import com.opengamma.strata.market.param.ParameterizedDataCombiner;
import com.opengamma.strata.market.param.UnitParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.SimpleDiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.rate.DiscountOvernightIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;

/**
 * Tests {@link CurveCtdVm}.
 */
public class CurveCtdVmTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2018, 4, 9);

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar USNY_IMPL = REF_DATA.getValue(HolidayCalendarIds.USNY);
  private static final DayCount DC_DF = DayCounts.ACT_365F;

  private static final DoubleArray[] TIMES = new DoubleArray[] {
      DoubleArray.of(0.25, 0.5, 1.0, 2.0, 5.0, 10.0),
      DoubleArray.of(0.25, 0.5, 1.0, 2.0, 5.0, 10.0),
      DoubleArray.of(0.25, 0.5, 0.75, 1.1, 2.1, 5.1, 9.9)
  };
  private static final int NB_CURVES = TIMES.length;

  private static final DoubleArray PARAM[] = new DoubleArray[] {
      DoubleArray.of(0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
      DoubleArray.of(0.007, 0.009, 0.010, 0.012, 0.0115, 0.0121),
      DoubleArray.of(0.99875, 0.9930, 0.99325, 0.986, 0.97, 0.94, 0.90)
  };

  private static final DefaultCurveMetadata META_TEMPLATE =
      DefaultCurveMetadata.builder().xValueType(ValueType.YEAR_FRACTION)
          .yValueType(ValueType.ZERO_RATE).dayCount(DC_DF).curveName(CurveName.of("tmp")).build();

  private static final String[] CURVE_NAMES = new String[NB_CURVES];
  private static final List<Curve> CURVES = new ArrayList<>(NB_CURVES);
  private static final DiscountFactors[] DF = new DiscountFactors[NB_CURVES];
  static {
    for (int i = 0; i < NB_CURVES; i++) {
      CURVE_NAMES[i] = "On" + i;
      if (i != 2) {
        CURVES.add(InterpolatedNodalCurve.of(META_TEMPLATE.toBuilder().curveName(CurveName.of(CURVE_NAMES[i])).build(),
            TIMES[i], PARAM[i], CurveInterpolators.LINEAR));
        DF[i] = ZeroRateDiscountFactors.of(USD, VALUATION_DATE, CURVES.get(i));
      } else {
        CURVES.add(InterpolatedNodalCurve.of(META_TEMPLATE.toBuilder()
            .curveName(CurveName.of(CURVE_NAMES[i])).yValueType(ValueType.DISCOUNT_FACTOR).build(),
            TIMES[i], PARAM[i], CurveInterpolators.LOG_LINEAR, CurveExtrapolators.EXPONENTIAL, CurveExtrapolators.EXPONENTIAL));
        DF[i] = SimpleDiscountFactors.of(USD, VALUATION_DATE, CURVES.get(i));
      }
    }
  }
  private static final Curve CURVE_FLAT_2 =
      InterpolatedNodalCurve
          .of(META_TEMPLATE.toBuilder().curveName(CurveName.of("FLAT2")).build(),
              TIMES[0], PARAM[0].minus(0.0025), CurveInterpolators.DOUBLE_QUADRATIC);
  private static final CurveName CURVE_NAME = CurveName.of("CTD");

  private static final LocalDate FINAL_DATE = VALUATION_DATE.plus(Period.ofYears(12));
  private static final CurveCtdVm CURVE_CROSSING =
      CurveCtdVm.of(CURVE_NAME, FINAL_DATE, USNY_IMPL, VALUATION_DATE, DC_DF, USD, CURVES);

  private static final Offset<Double> TOLERANCE_FWD = Offset.offset(1.0E-10);

  /* Test the approach when there is only one curve, i.e. the CTD is always the unique input. To check the hedge cases. */
  @Test
  public void single_curve() {
    LocalDate finalDate = VALUATION_DATE.plus(Period.ofYears(5));
    Curve curveSingle =
        CurveCtdVm.of(CURVE_NAME, finalDate, USNY_IMPL, VALUATION_DATE, DC_DF, USD, ImmutableList.of(CURVES.get(0)));
    List<LocalDate> testingDates = testingDates(finalDate, USNY);
    OvernightIndexRates overnightRates1 = DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, DF[0]);
    OvernightIndexRates overnightRates2 =
        DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, DiscountFactors.of(USD, VALUATION_DATE, curveSingle));
    for (int i = 0; i < testingDates.size(); i++) {
      OvernightIndexObservation obs =
          OvernightIndexObservation.of(OvernightIndices.USD_FED_FUND, testingDates.get(i), REF_DATA);
      double fwd1 = overnightRates1.rate(obs);
      double fwd2 = overnightRates2.rate(obs);
      assertThat(fwd1).isEqualTo(fwd2, TOLERANCE_FWD);
    }
  }

  /* Test the approach when there are two curves but they forward do not cross, i.e. the CTD is always the same curve. To check the hedge cases. */
  @Test
  public void no_crossing() {
    LocalDate finalDate = VALUATION_DATE.plus(Period.ofYears(5));
    Curve cuNoCrossing = CurveCtdVm.of(
        CURVE_NAME,
        finalDate,
        USNY_IMPL,
        VALUATION_DATE,
        DC_DF,
        USD,
        ImmutableList.of(CURVES.get(0), CURVE_FLAT_2));
    List<LocalDate> testingDates = testingDates(finalDate, USNY);
    OvernightIndexRates overnightRates1 = DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, DF[0]);
    OvernightIndexRates overnightRates2 =
        DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, DiscountFactors.of(USD, VALUATION_DATE, cuNoCrossing));
    for (int i = 0; i < testingDates.size(); i++) {
      OvernightIndexObservation obs = OvernightIndexObservation.of(OvernightIndices.USD_FED_FUND, testingDates.get(i), REF_DATA);
      double fwd1 = overnightRates1.rate(obs);
      double fwd2 = overnightRates2.rate(obs);
      assertThat(fwd1).isEqualTo(fwd2, TOLERANCE_FWD);
    }
  }

  /* Test the approach when there several curves and they cross multiple times. */
  @Test
  public void multiple_crossing() {
    List<LocalDate> testingDates = testingDates(FINAL_DATE, USNY);
    for (int i = 0; i < testingDates.size(); i++) {
      OvernightIndexObservation obs = OvernightIndexObservation.of(OvernightIndices.USD_FED_FUND, testingDates.get(i), REF_DATA);
      OvernightIndexRates overnightRatesCtd =
          DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, DiscountFactors.of(USD, VALUATION_DATE, CURVE_CROSSING));
      double fwdCtd = overnightRatesCtd.rate(obs);
      double[] fwd = new double[NB_CURVES];
      for (int loopcurve = 0; loopcurve < NB_CURVES; loopcurve++) {
        OvernightIndexRates overnightRates1 = DiscountOvernightIndexRates.of(OvernightIndices.USD_FED_FUND, DF[loopcurve]);
        fwd[loopcurve] = overnightRates1.rate(obs);
      }
      double fwdMax = fwd[argmax(fwd)];
      assertThat(fwdCtd).isEqualTo(fwdMax, TOLERANCE_FWD);
    }
  }

  @Test
  public void valuation_date() {
    assertThat(CURVE_CROSSING.getValuationDate()).isEqualTo(VALUATION_DATE);
  }

  @Test
  public void parameter_count() {
    int nbParam = 0;
    for (int i = 0; i < DF.length; i++) {
      nbParam += DF[i].getParameterCount();
    }
    assertThat(CURVE_CROSSING.getParameterCount()).isEqualTo(nbParam);
  }

  @Test
  public void parameter() {
    ParameterizedDataCombiner paramCombiner = ParameterizedDataCombiner.of(DF);
    for (int i = 0; i < CURVE_CROSSING.getParameterCount(); i++) {
      double paramComputed = CURVE_CROSSING.getParameter(i);
      double paramExpected = paramCombiner.getParameter(i);
      assertThat(paramComputed).isEqualTo(paramExpected);
    }
  }

  /* This method tests a combination of zeroRatePointSensitivity, parameterSensitivity, createParameterSensitivities 
   * and withParameter. The sensitivities are tested against finite differences. */
  @Test
  public void parameter_sensitivity() {
    DiscountingPaymentPricer PRICER = DiscountingPaymentPricer.DEFAULT;
    RatesFiniteDifferenceSensitivityCalculator FD = new RatesFiniteDifferenceSensitivityCalculator(1.0E-6);
    LocalDate testDate = USNY_IMPL.nextOrSame(VALUATION_DATE.plusYears(6));
    double x = DC_DF.relativeYearFraction(VALUATION_DATE, testDate);
    UnitParameterSensitivity yDp = CURVE_CROSSING.yValueParameterSensitivity(x);
    assertThat(yDp.getParameterSplit().isPresent()).isTrue();
    List<ParameterSize> paramSize = yDp.getParameterSplit().get();
    assertThat(paramSize.size()).isEqualTo(CURVES.size());
    for (int i = 0; i < CURVES.size(); i++) {
      assertThat(paramSize.get(i).getName()).isEqualTo(CurveName.of(CURVE_NAMES[i]));
      assertThat(paramSize.get(i).getParameterCount()).isEqualTo(CURVES.get(i).getParameterCount());
    }
    Payment payment = Payment.of(CurrencyAmount.of(USD, 12_345.67), testDate);
    ImmutableRatesProvider provider = ImmutableRatesProvider.builder(VALUATION_DATE)
        .discountCurve(USD, CURVE_CROSSING)
        .build();
    PointSensitivityBuilder pts = PRICER.presentValueSensitivity(payment, provider);
    CurrencyParameterSensitivities ps = provider.parameterSensitivity(pts.build());
    CurrencyParameterSensitivities psFD = FD.sensitivity(provider, (p) -> PRICER.presentValue(payment, p));
    DoubleArray combinedSensitivityFd = psFD.getSensitivity(CURVE_NAME, USD).getSensitivity();
    DoubleArray combinedSensitivityAd = ps.getSensitivity(CURVE_NAME, USD).getSensitivity();
    assertThat(combinedSensitivityAd.equalWithTolerance(combinedSensitivityFd, 2.0E-1)).isTrue();
  }

  /* Tests that the split of the unique sensitivity to the aggregated curve 
   * can be split in the sensitivities with respect to the underlying original curves.*/
  @Test
  public void parameter_sensitivity_split() {
    DiscountingPaymentPricer PRICER = DiscountingPaymentPricer.DEFAULT;
    LocalDate testDate = USNY_IMPL.nextOrSame(VALUATION_DATE.plusYears(6));
    Payment payment = Payment.of(CurrencyAmount.of(USD, 12_345.67), testDate);
    ImmutableRatesProvider provider = ImmutableRatesProvider.builder(VALUATION_DATE)
        .discountCurve(USD, CURVE_CROSSING)
        .build();
    PointSensitivityBuilder pts = PRICER.presentValueSensitivity(payment, provider);
    CurrencyParameterSensitivities ps = provider.parameterSensitivity(pts.build());
    CurrencyParameterSensitivity psCtd = ps.getSensitivity(CURVE_NAME, USD);
    List<CurrencyParameterSensitivity> split = psCtd.split();
    assertThat(split.size()).isEqualTo(CURVES.size());
    for (int i = 0; i < CURVES.size(); i++) {
      assertThat(split.get(i).getMarketDataName()).isEqualTo(CurveName.of(CURVE_NAMES[i]));
      assertThat(split.get(i).getParameterCount()).isEqualTo(CURVES.get(i).getParameterCount());
    }
  }

  @Test
  public void with_metadata() {
    CurveMetadata metadata = DefaultCurveMetadata.builder()
        .curveName("test")
        .dayCount(DayCounts.ACT_365F)
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.DISCOUNT_FACTOR)
        .build();
    CurveCtdVm with = CURVE_CROSSING.withMetadata(metadata);
    assertThat(with.getMetadata()).isEqualTo(metadata);
  }

  /**
   * List of dates for testing: every date for 3M, weekly for 2Y and monthly up to final date.
   * 
   * @param finalDate  the date up to which the forward comparison should be carried
   * @param calendar  the calendar ID to create dates on good business dates
   * @return the dates
   */
  private List<LocalDate> testingDates(LocalDate finalDate, HolidayCalendarId calendar) {
    HolidayCalendar baseCalendar = REF_DATA.getValue(calendar);
    LocalDate finalDateDaily = VALUATION_DATE.plus(Period.ofMonths(3));
    LocalDate finalDateWeekly = VALUATION_DATE.plus(Period.ofYears(2));
    List<LocalDate> dates = new ArrayList<>();
    LocalDate currentDate = VALUATION_DATE;
    while (currentDate.isBefore(finalDateDaily)) {
      dates.add(currentDate);
      currentDate = baseCalendar.next(currentDate);
    }
    while (currentDate.isBefore(finalDateWeekly)) {
      currentDate = baseCalendar.nextOrSame(currentDate.plus(Period.ofDays(7)));
      dates.add(currentDate);
    }
    while (currentDate.isBefore(finalDate)) {
      currentDate = baseCalendar.nextOrSame(currentDate.plus(Period.ofMonths(1)));
      dates.add(currentDate);
    }
    return dates;
  }

  /* Returns the index for which an array of double has its maximum value */
  private static int argmax(double[] values) {
    int argmax = 0;
    double maxValue = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < values.length; i++) {
      if (values[i] > maxValue) {
        maxValue = values[i];
        argmax = i;
      }
    }
    return argmax;
  }

  public void test_serialization() {
    assertSerialization(CURVE_CROSSING);
  }

}
