/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.indexfutures;

import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.StandardId;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.basics.value.Rounding;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.SecurityId;
import com.opengamma.strata.product.index.ResolvedOvernightFuture;
import com.opengamma.strata.product.rate.OvernightRateComputation;
import com.opengamma.strata.product.swap.OvernightAccrualMethod;

import marc.henrard.murisq.dataset.MulticurveStandardGbpDataSet;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Tests {@link HullWhiteOneFactorOvernightFuturesProductPricer}
 * 
 * @author Marc Henrard
 */
public class HullWhiteOneFactorOvernightFuturesProductPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 12, 29);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");

  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardGbpDataSet.multicurve(VALUATION_DATE, REF_DATA);
  
  private static final double MEAN_REVERSION = 0.03;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.0065);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters HW_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(HW_PARAMETERS, DayCounts.ACT_365F, 
          VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  
  private static final HullWhiteOneFactorOvernightFuturesProductPricer PRICER_FUT =
      HullWhiteOneFactorOvernightFuturesProductPricer.DEFAULT;

  private static final SecurityId ID = SecurityId.of(StandardId.of("muRisQ", "Fut"));
  private static final double NOTIONAL = 500_000;
  private static final LocalDate START_ACCRUAL_DATE = LocalDate.of(2021, 6, 16);
  private static final LocalDate END_ACCRUAL_DATE = LocalDate.of(2021, 9, 15);
  private static final OvernightIndex INDEX = GBP_SONIA;
  
  private static final ResolvedOvernightFuture ON_FUTURES = ResolvedOvernightFuture.builder()
      .accrualFactor(0.25)
      .currency(GBP)
      .lastTradeDate(START_ACCRUAL_DATE)
      .notional(NOTIONAL)
      .overnightRate(OvernightRateComputation
          .of(INDEX, START_ACCRUAL_DATE, END_ACCRUAL_DATE, 0, OvernightAccrualMethod.COMPOUNDED, REF_DATA))
      .securityId(ID)
      .rounding(Rounding.none())
      .build();
  
  private static final double TOLERANCE_GAMMA = 1.0E-8;
  private static final double TOLERANCE_PRICE = 1.0E-8;

  /* Tests gamma factors v local implementation */
  @Test
  public void gammas() {
    List<LocalDate> onDates = PRICER_FUT.overnightDates(ON_FUTURES);
    int nbOnDates = onDates.size();
    List<Double> gammasComputed = PRICER_FUT.convexityAdjustmentGammas(ON_FUTURES, MULTICURVE, HW_PROVIDER);
    assertEquals(gammasComputed.size(), nbOnDates - 1);
    List<Double> ti = new ArrayList<>();
    ti.add(0.0d);
    for (int i = 0; i < nbOnDates; i++) {
      ti.add(HW_PROVIDER.relativeTime(onDates.get(i)));
    }
    for (int i = 0; i < nbOnDates - 1; i++) {
      double gammaExpected = FORMULAS.futuresConvexityFactor(
          HW_PROVIDER.getParameters(), ti.get(i), ti.get(i + 1), ti.get(i + 1), ti.get(nbOnDates));
      assertEquals(gammasComputed.get(i), gammaExpected, TOLERANCE_GAMMA);
    }
  }

  /* Tests convexity v local implementation */
  @Test
  public void convexityAdjustment() {
    List<Double> gammas = PRICER_FUT.convexityAdjustmentGammas(ON_FUTURES, MULTICURVE, HW_PROVIDER);
    Currency ccy = GBP_SONIA.getCurrency();
    double delta = GBP_SONIA.getDayCount().yearFraction(START_ACCRUAL_DATE, END_ACCRUAL_DATE);
    double PcTs = MULTICURVE.discountFactor(ccy, START_ACCRUAL_DATE);
    double PcTe = MULTICURVE.discountFactor(ccy, END_ACCRUAL_DATE);
    double productGamma = 1.0;
    for (int i = 0; i < gammas.size(); i++) {
      productGamma *= gammas.get(i);
    }
    double caExpected = PcTs / PcTe * (productGamma - 1.0d) / delta;
    double caComputed = PRICER_FUT.convexityAdjustment(ON_FUTURES, MULTICURVE, HW_PROVIDER);
    assertEquals(caComputed, caExpected, TOLERANCE_PRICE);
  }

  /* Tests price v local implementation */
  @Test
  public void price() {
    double adj = PRICER_FUT.convexityAdjustment(ON_FUTURES, MULTICURVE, HW_PROVIDER);
    double fwd = MULTICURVE.overnightIndexRates(INDEX)
        .periodRate(OvernightIndexObservation.of(INDEX, START_ACCRUAL_DATE, REF_DATA), END_ACCRUAL_DATE);
    double priceExpected = 1 - (fwd + adj);
    double priceComputed = PRICER_FUT.price(ON_FUTURES, MULTICURVE, HW_PROVIDER);
    assertEquals(priceComputed, priceExpected, TOLERANCE_PRICE);
  }

  /* Tests overnight dates */
  @Test
  public void onDates() {
    List<LocalDate> onDatesComputed = PRICER_FUT.overnightDates(ON_FUTURES);
    HolidayCalendar calendar = REF_DATA.getValue(GBP_SONIA.getFixingCalendar());
    List<LocalDate> onDatesExpected = new ArrayList<>();
    LocalDate currentDate = START_ACCRUAL_DATE;
    onDatesExpected.add(currentDate);
    while (currentDate.isBefore(END_ACCRUAL_DATE)) {
      currentDate = calendar.next(currentDate);
      onDatesExpected.add(currentDate);
    }
    assertEquals(onDatesComputed.size(), onDatesExpected.size());
    for (int i = 0; i < onDatesComputed.size(); i++) {
      assertEquals(onDatesComputed.get(i), onDatesExpected.get(i));
    }
  }

}
