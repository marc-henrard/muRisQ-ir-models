/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.generic;

import java.time.LocalDate;
import java.util.OptionalDouble;

import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.pricer.PricingException;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;

/**
 * Utilities related to IBOR fallback options.
 * 
 * @author Marc Henrard
 */
public class FallbackUtils {
  
  /**
   * Computes the compounded in arrears rate for an overnight compounded rate from a time series of 
   * overnight rates.
   * <p>
   * Rate cut-off days are not taken into account.
   * Throws an exception if one of the fixing is not available in the time series.
   * 
   * @param timeSeries  the time series
   * @param computation  the overnight compounded computation
   * @return the compounded rate
   */
  // Note: rounding may be required for production
  public static double compoundedInArrears(
      LocalDateDoubleTimeSeries timeSeries,
      OvernightCompoundedRateComputation computation) {

    LocalDate currentFixingOn = computation.getStartDate();
    double compositionFactor = 1.0d;
    while (currentFixingOn.isBefore(computation.getEndDate())) {
      LocalDate effectiveDate = computation.calculateEffectiveFromFixing(currentFixingOn);
      LocalDate maturityDate = computation.calculateMaturityFromEffective(effectiveDate);
      double accrualFactorOn = 
          computation.getIndex().getDayCount().yearFraction(effectiveDate, maturityDate);
      compositionFactor *= 1.0d 
          + accrualFactorOn * checkedFixing(currentFixingOn, timeSeries, computation.getIndex());
      currentFixingOn = computation.getFixingCalendar().next(currentFixingOn);
    }
    double accrualFactorPeriod = computation.getIndex().getDayCount()
        .yearFraction(computation.getStartDate(), computation.getEndDate());
    return (compositionFactor - 1.0d) / accrualFactorPeriod;
  }
  
  /**
   * Check that the fixing is present. Throws an exception if not and return the rate as double if available.
   * 
   * @param fixingDate  the fixing date
   * @param timeSeries  the time series
   * @param index  the overnight index
   * @return  the rate
   */
  private static double checkedFixing(
      LocalDate fixingDate,
      LocalDateDoubleTimeSeries timeSeries,
      OvernightIndex index) {

    OptionalDouble fixedRate = timeSeries.get(fixingDate);
    return fixedRate.orElseThrow(() -> new PricingException(
        "Could not get fixing value of index " + index.getName() + " for date " + fixingDate));
  }

}
