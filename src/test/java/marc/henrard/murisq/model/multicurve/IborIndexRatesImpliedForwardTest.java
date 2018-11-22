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
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.param.ParameterPerturbation;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.IborRateSensitivity;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.murisq.dataset.MulticurveStandardDataSet;

/**
 * Tests {@link IborIndexRatesImpliedForward}.
 * 
 * @author Marc Henrard
 */
@Test
public class IborIndexRatesImpliedForwardTest {

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
  private static final IborIndex INDEX = IborIndices.USD_LIBOR_3M;
  private static final LocalDate FORWARD_DATE = LocalDate.of(2018, 9, 28);
  private static final IborIndexRates UNDERLYING = 
      MULTICURVE.iborIndexRates(INDEX);
  private static final LocalDateDoubleTimeSeries FIXINGS =
      TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, INDEX, FORWARD_DATE, REF_DATA);
  private static final IborIndexRatesImpliedForward IBOR_INDEX_FORWARD =
      IborIndexRatesImpliedForward.of(UNDERLYING, FORWARD_DATE, FIXINGS);
  
  /* Tests */
  private static final double TOLERANCE_FWD = 1.0E-8;
  
  public void of() {
    assertEquals(IBOR_INDEX_FORWARD.getValuationDate(), FORWARD_DATE);
    assertEquals(IBOR_INDEX_FORWARD.getUnderlying(), UNDERLYING);
    assertEquals(IBOR_INDEX_FORWARD.getFixings(), FIXINGS);
  }

  // before initial valuation date
  public void rate_before() {
    LocalDate date1 = LocalDate.of(2018, 7, 31);
    IborIndexObservation obs1 = IborIndexObservation.of(INDEX, date1, REF_DATA);
    double rate1Expected = UNDERLYING.rate(obs1);
    double rate1Computed = IBOR_INDEX_FORWARD.rate(obs1);
    assertEquals(rate1Computed, rate1Expected, TOLERANCE_FWD);
  }

  // between initial valuation date and forward date
  public void rate_between() {
    LocalDate date = LocalDate.of(2018, 9, 14);
    IborIndexObservation obs = IborIndexObservation.of(INDEX, date, REF_DATA);
    double rateExpected = UNDERLYING.rateIgnoringFixings(obs);
    double rateComputed1 = IBOR_INDEX_FORWARD.rate(obs);
    assertEquals(rateComputed1, rateExpected, TOLERANCE_FWD);
    double rateComputed2 = IBOR_INDEX_FORWARD.rateIgnoringFixings(obs);
    assertEquals(rateComputed2, rateExpected, TOLERANCE_FWD);
  }

  // after forward date
  public void rate_after() {
    LocalDate date1 = LocalDate.of(2018, 10, 19);
    IborIndexObservation obs1 = IborIndexObservation.of(INDEX, date1, REF_DATA);
    double rate1Expected = UNDERLYING.rateIgnoringFixings(obs1);
    double rateComputed1 = IBOR_INDEX_FORWARD.rate(obs1);
    assertEquals(rateComputed1, rate1Expected, TOLERANCE_FWD);
    double rateComputed2 = IBOR_INDEX_FORWARD.rateIgnoringFixings(obs1);
    assertEquals(rateComputed2, rate1Expected, TOLERANCE_FWD);
  }

  // before initial valuation date
  public void point_sensitivity_before() {
    LocalDate date1 = LocalDate.of(2018, 7, 31);
    IborIndexObservation obs1 = IborIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = IBOR_INDEX_FORWARD.ratePointSensitivity(obs1);
    assertEquals(ptsComputed, PointSensitivityBuilder.none());
  }

  // between initial valuation date and forward date
  public void point_sensitivity_between() {
    LocalDate date1 = LocalDate.of(2018, 9, 14);
    IborIndexObservation obs1 = IborIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = IBOR_INDEX_FORWARD.ratePointSensitivity(obs1);
    assertEquals(ptsComputed, PointSensitivityBuilder.none());
  }

  // after forward date
  public void point_sensitivity_after() {
    LocalDate date1 = LocalDate.of(2018, 10, 19);
    IborIndexObservation obs = IborIndexObservation.of(INDEX, date1, REF_DATA);
    PointSensitivityBuilder ptsComputed = IBOR_INDEX_FORWARD.ratePointSensitivity(obs);
    assertEquals(ptsComputed, IborRateSensitivity.of(obs, 1d));
  }

  public void with_parameter() {
    double newValue = 0.01;
    int parameterIndex = 2;
    IborIndexRates test = IBOR_INDEX_FORWARD.withParameter(parameterIndex, newValue);
    IborIndexRates expected = IborIndexRatesImpliedForward
        .of(UNDERLYING.withParameter(parameterIndex, newValue), FORWARD_DATE, FIXINGS);
    assertEquals(test, expected);
  }

  public void with_perturbation() {
    ParameterPerturbation perturbation = (i, v, m) -> v + 1d;
    IborIndexRates test = IBOR_INDEX_FORWARD.withPerturbation(perturbation);
    IborIndexRates expected = IborIndexRatesImpliedForward
        .of(UNDERLYING.withPerturbation(perturbation), FORWARD_DATE, FIXINGS);
    assertEquals(test, expected);
  }
  
}
