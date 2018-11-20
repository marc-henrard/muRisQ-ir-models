/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendarIds;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.murisq.dataset.MulticurveStandardDataSet;

/**
 * Tests {@link TimeSeriesImpliedForward}.
 * 
 * @author Marc Henrard
 */
@Test
public class TimeSeriesImpliedForwardTest {

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
  
  /* Tests */
  private static final double TOLERANCE_FWD = 1.0E-8;

  public void implied_time_series_one_index_ibor() {
    HolidayCalendar gblo_impl = REF_DATA.getValue(HolidayCalendarIds.GBLO);
    LocalDate maximumDate = LocalDate.of(2019, 12, 19);
    IborIndex index = IborIndices.USD_LIBOR_3M;
    LocalDateDoubleTimeSeries tsGenerated =
        TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, index, maximumDate, REF_DATA);
    assertEquals(tsGenerated.getLatestDate(), maximumDate);
    LocalDate currentTestDate = LocalDate.of(2018, 7, 2);
    while(!currentTestDate.isAfter(maximumDate)) {
      assertTrue(tsGenerated.containsDate(currentTestDate), "Date: " + currentTestDate.toString());
      IborIndexObservation obs = IborIndexObservation.of(index, currentTestDate, REF_DATA);
      double fwdExpected = 0.0;
      if(!currentTestDate.isAfter(VALUATION_DATE)) {
        fwdExpected = MULTICURVE.timeSeries(index).get(currentTestDate).getAsDouble();
      } else {
        fwdExpected = MULTICURVE.iborIndexRates(index).rate(obs);
      }
      assertEquals(tsGenerated.get(currentTestDate).getAsDouble(), fwdExpected, TOLERANCE_FWD, 
          "Date: " + currentTestDate.toString());
      currentTestDate = gblo_impl.next(currentTestDate);
    }
  }

  public void implied_time_series_one_index_on() {
    HolidayCalendar usny_impl = REF_DATA.getValue(HolidayCalendarIds.USNY);
    LocalDate maximumDate = LocalDate.of(2019, 12, 19);
    OvernightIndex index = OvernightIndices.USD_FED_FUND;
    LocalDateDoubleTimeSeries tsGenerated =
        TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, index, maximumDate, REF_DATA);
    assertEquals(tsGenerated.getLatestDate(), maximumDate);
    LocalDate currentTestDate = LocalDate.of(2018, 7, 2);
    while(!currentTestDate.isAfter(maximumDate)) {
      assertTrue(tsGenerated.containsDate(currentTestDate), "Date: " + currentTestDate.toString());
      OvernightIndexObservation obs = OvernightIndexObservation.of(index, currentTestDate, REF_DATA);
      double fwdExpected = 0.0;
      if(!currentTestDate.isAfter(VALUATION_DATE)) {
        fwdExpected = MULTICURVE.timeSeries(index).get(currentTestDate).getAsDouble();
      } else {
        fwdExpected = MULTICURVE.overnightIndexRates(index).rate(obs);
      }
      assertEquals(tsGenerated.get(currentTestDate).getAsDouble(), fwdExpected, TOLERANCE_FWD, 
          "Date: " + currentTestDate.toString());
      currentTestDate = usny_impl.next(currentTestDate);
    }
  }

  public void implied_time_series_all_indices() {
    LocalDate maximumDate = LocalDate.of(2019, 12, 19);
    Set<Index> indices = ImmutableSet.of(OvernightIndices.USD_FED_FUND, IborIndices.USD_LIBOR_3M);
    Map<Index, LocalDateDoubleTimeSeries> impliedTimeSeries =
        TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, indices, maximumDate, REF_DATA);
    assertEquals(impliedTimeSeries.size(), 2);
    OvernightIndex indexOn = OvernightIndices.USD_FED_FUND;
    LocalDateDoubleTimeSeries tsGeneratedOn =
        TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, indexOn, maximumDate, REF_DATA);
    assertEquals(impliedTimeSeries.get(indexOn), tsGeneratedOn);
    IborIndex indexIbor = IborIndices.USD_LIBOR_3M;
    LocalDateDoubleTimeSeries tsGeneratedIbor =
        TimeSeriesImpliedForward.impliedTimeSeries(MULTICURVE, indexIbor, maximumDate, REF_DATA);
    assertEquals(impliedTimeSeries.get(indexIbor), tsGeneratedIbor);
  }
  
}
