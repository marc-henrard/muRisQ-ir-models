/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.index.PriceIndexObservation;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeriesBuilder;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;
import com.opengamma.strata.pricer.rate.PriceIndexValues;
import com.opengamma.strata.pricer.rate.RatesProvider;

/**
 * Utilities to create implied time series for Index fixings.
 */
public class TimeSeriesImpliedForward {

  /**
   * Returns the implied time series of forward fixing from a multi-curve provider.
   * <p>
   * The time series in the rates provider are part of the fixing time series returned.
   * 
   * @param multicurve  the multi-curve rates provider used to compute the forward fixings
   * @param indices  the indices for which the time series should be computed
   * @param maximumDate  the maximum date for which the fixing should be computed (maximum date inclusive)
   * @param refData  the reference data
   * @return  the time series
   */
  public static Map<Index, LocalDateDoubleTimeSeries> impliedTimeSeries(
      RatesProvider multicurve,
      Set<Index> indices,
      LocalDate maximumDate,
      ReferenceData refData) {

    Map<Index, LocalDateDoubleTimeSeries> ts = new HashMap<>();
    for (Index index : indices) {
      ts.put(index, impliedTimeSeries(multicurve, index, maximumDate, refData));
    }
    return ts;
  }

  /**
   * Returns the implied time series of forward fixing for a given index.
   * <p>
   * The time series in the rates provider are part of the fixing time series returned.
   * 
   * @param multicurve  the multi-curve rates provider used to compute the forward fixings
   * @param index  the index for which the time series should be computed
   * @param maximumDate  the maximum date for which the fixing should be computed (maximum date inclusive)
   * @param refData  the reference data
   * @return  the time series
   */
  public static LocalDateDoubleTimeSeries impliedTimeSeries(
      RatesProvider multicurve,
      Index index,
      LocalDate maximumDate,
      ReferenceData refData) {

    LocalDateDoubleTimeSeriesBuilder builder = multicurve.timeSeries(index).toBuilder();
    LocalDate currentDate = multicurve.getValuationDate();
    if (index instanceof IborIndex) {
      IborIndex iborIndex = (IborIndex) index;
      HolidayCalendar cal = refData.getValue(iborIndex.getFixingCalendar());
      if (builder.get(currentDate).isPresent()) { // Skipping valuation date if already present
        currentDate = cal.next(currentDate);
      }
      while (!currentDate.isAfter(maximumDate)) {
        IborIndexRates rates = multicurve.iborIndexRates(iborIndex);
        double rate = rates.rate(IborIndexObservation.of(iborIndex, currentDate, refData));
        builder.put(currentDate, rate);
        currentDate = cal.next(currentDate);
      }
    } else if (index instanceof OvernightIndex) {
      OvernightIndex onIndex = (OvernightIndex) index;
      HolidayCalendar cal = refData.getValue(onIndex.getFixingCalendar());
      if (builder.get(currentDate).isPresent()) { // Skipping valuation date if already present
        currentDate = cal.next(currentDate);
      }
      while (!currentDate.isAfter(maximumDate)) {
        OvernightIndexRates rates = multicurve.overnightIndexRates((OvernightIndex) index);
        double rate = rates.rate(OvernightIndexObservation.of((OvernightIndex) index, currentDate, refData));
        builder.put(currentDate, rate);
        currentDate = cal.next(currentDate);
      }
    } else if (index instanceof PriceIndex) {
      PriceIndex priceIndex = (PriceIndex) index;
      YearMonth currentMonth = YearMonth.from(currentDate);
      YearMonth maximumMonth = YearMonth.from(maximumDate);
      while (!currentMonth.isAfter(maximumMonth)) {
        PriceIndexValues rates = multicurve.priceIndexValues(priceIndex);
        double value = rates.value(PriceIndexObservation.of(priceIndex, currentMonth));
        builder.put(currentMonth.atEndOfMonth(), value);
        currentMonth = currentMonth.plusMonths(1);
      }
    }
    return builder.build();
  }

}
