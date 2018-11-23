/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.param.ParameterPerturbation;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;
import com.opengamma.strata.pricer.rate.OvernightRateSensitivity;

import marc.henrard.murisq.dataset.MulticurveStandardDataSet;

/**
 * Tests {@link OvernightIndexRatesImpliedForward}.
 * 
 * @author Marc Henrard
 */
@Test
public class OvernightIndexRatesImpliedForwardTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2018, 8, 30);

  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/test/resources/curve-config/USD-DSCONOIS-L3MIRS/";
  private static final List<ResourceLocator> FIXING_RESOURCES = ImmutableList.of(
      ResourceLocator.of("src/test/resources/fixing/USD-FED-FUND.csv"),
      ResourceLocator.of("src/test/resources/fixing/USD-LIBOR-3M.csv"));
  private static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardDataSet.multicurve(VALUATION_DATE,
          CurveGroupName.of("USD-DSCONOIS-L3MIRS"),
          ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-group.csv"),
          ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-settings-zrlinear.csv"),
          ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-nodes.csv"), 
          "src/test/resources/quotes/MARKET-DATA-2018-08-30.csv", 
          FIXING_RESOURCES,
          REF_DATA);
  
  /* Forward */
  private static final OvernightIndex INDEX = OvernightIndices.USD_FED_FUND;
  private static final LocalDate FORWARD_DATE = LocalDate.of(2018, 9, 28);
  private static final OvernightIndexRates UNDERLYING = 
      MULTICURVE.overnightIndexRates(INDEX);
  private static final LocalDateDoubleTimeSeries FIXINGS =
      TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, INDEX, FORWARD_DATE, REF_DATA);
  private static final OvernightIndexRatesImpliedForward ON_INDEX_FORWARD =
      OvernightIndexRatesImpliedForward.of(UNDERLYING, FORWARD_DATE, FIXINGS);
  
  /* Tests */
  private static final double TOLERANCE_FWD = 1.0E-8;
  
  public void of() {
    assertEquals(ON_INDEX_FORWARD.getValuationDate(), FORWARD_DATE);
    assertEquals(ON_INDEX_FORWARD.getUnderlying(), UNDERLYING);
    assertEquals(ON_INDEX_FORWARD.getFixings(), FIXINGS);
  }

  // before initial valuation date
  public void rate_before() {
    LocalDate date1 = LocalDate.of(2018, 7, 31);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    double rate1Expected = UNDERLYING.rate(obs1);
    double rate1Computed = ON_INDEX_FORWARD.rate(obs1);
    assertEquals(rate1Computed, rate1Expected, TOLERANCE_FWD);
  }

  // between initial valuation date and forward date
  public void rate_between() {
    LocalDate date = LocalDate.of(2018, 9, 14);
    OvernightIndexObservation obs = OvernightIndexObservation.of(INDEX, date, REF_DATA);
    double rateExpected = UNDERLYING.rateIgnoringFixings(obs);
    double rateComputed1 = ON_INDEX_FORWARD.rate(obs);
    assertEquals(rateComputed1, rateExpected, TOLERANCE_FWD);
    double rateComputed2 = ON_INDEX_FORWARD.rateIgnoringFixings(obs);
    assertEquals(rateComputed2, rateExpected, TOLERANCE_FWD);
  }

  // after forward date
  public void rate_after() {
    LocalDate date1 = LocalDate.of(2018, 10, 19);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    double rate1Expected = UNDERLYING.rateIgnoringFixings(obs1);
    double rateComputed1 = ON_INDEX_FORWARD.rate(obs1);
    assertEquals(rateComputed1, rate1Expected, TOLERANCE_FWD);
    double rateComputed2 = ON_INDEX_FORWARD.rateIgnoringFixings(obs1);
    assertEquals(rateComputed2, rate1Expected, TOLERANCE_FWD);
  }

  // before initial valuation date
  public void point_sensitivity_before() {
    LocalDate date1 = LocalDate.of(2018, 7, 31);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = ON_INDEX_FORWARD.ratePointSensitivity(obs1);
    assertEquals(ptsComputed, PointSensitivityBuilder.none());
  }
  
  public void point_sensitivity_period_before() {
    LocalDate date1 = LocalDate.of(2018, 7, 31);
    LocalDate date2 = LocalDate.of(2018, 8, 15);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = 
        ON_INDEX_FORWARD.periodRatePointSensitivity(obs1, date2);
    PointSensitivityBuilder ptsExpected =
        OvernightRateSensitivity.ofPeriod(obs1, date2, 1d);
    assertEquals(ptsComputed, ptsExpected);
  }

  // between initial valuation date and forward date
  public void point_sensitivity_between() {
    LocalDate date1 = LocalDate.of(2018, 9, 14);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = ON_INDEX_FORWARD.ratePointSensitivity(obs1);
    assertEquals(ptsComputed, PointSensitivityBuilder.none());
  }
  
  public void point_sensitivity_period_between() {
    LocalDate date1 = LocalDate.of(2018, 9, 14);
    LocalDate date2 = LocalDate.of(2018, 9, 28);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = 
        ON_INDEX_FORWARD.periodRatePointSensitivity(obs1, date2);
    PointSensitivityBuilder ptsExpected =
        OvernightRateSensitivity.ofPeriod(obs1, date2, 1d);
    assertEquals(ptsComputed, ptsExpected);
  }

  // after forward date
  public void point_sensitivity_after() {
    LocalDate date1 = LocalDate.of(2018, 10, 19);
    OvernightIndexObservation obs = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = ON_INDEX_FORWARD.ratePointSensitivity(obs);
    assertEquals(ptsComputed, OvernightRateSensitivity.of(obs, 1d));
  }
  
  public void point_sensitivity_period_after() {
    LocalDate date1 = LocalDate.of(2018, 10, 19);
    LocalDate date2 = LocalDate.of(2018, 11, 2);
    OvernightIndexObservation obs1 = OvernightIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = 
        ON_INDEX_FORWARD.periodRatePointSensitivity(obs1, date2);
    PointSensitivityBuilder ptsExpected =
        OvernightRateSensitivity.ofPeriod(obs1, date2, 1d);
    assertEquals(ptsComputed, ptsExpected);
  }

  public void with_parameter() {
    double newValue = 0.01;
    int parameterIndex = 2;
    OvernightIndexRates test = ON_INDEX_FORWARD.withParameter(parameterIndex, newValue);
    OvernightIndexRates expected = OvernightIndexRatesImpliedForward
        .of(UNDERLYING.withParameter(parameterIndex, newValue), FORWARD_DATE, FIXINGS);
    assertEquals(test, expected);
  }

  public void with_perturbation() {
    ParameterPerturbation perturbation = (i, v, m) -> v + 1d;
    OvernightIndexRates test = ON_INDEX_FORWARD.withPerturbation(perturbation);
    OvernightIndexRates expected = OvernightIndexRatesImpliedForward
        .of(UNDERLYING.withPerturbation(perturbation), FORWARD_DATE, FIXINGS);
    assertEquals(test, expected);
  }
  
}
