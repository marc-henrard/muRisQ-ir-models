/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import java.time.ZonedDateTime;

/**
 * Measurement of time for options.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public interface TimeMeasurement {
  
  /**
   * Measure the time between two date/time.
   *  
   * @param dateTimeStart  the start date/time
   * @param dateTimeEnd  the end date/time
   * @return the measure of time distance between the two dates
   */
  public double relativeTime(ZonedDateTime dateTimeStart, ZonedDateTime dateTimeEnd);

}
