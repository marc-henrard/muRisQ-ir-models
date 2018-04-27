/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Measurement of time for options.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public interface TimeMeasurement {
  
  /**
   * Measure the time between two date/time. The result is positive is the end is after the start and
   * negative if the start is after the end.
   *  
   * @param dateTimeStart  the start date/time
   * @param dateTimeEnd  the end date/time
   * @return the measure of time distance between the two dates
   */
  public double relativeTime(ZonedDateTime dateTimeStart, ZonedDateTime dateTimeEnd);

  /**
   * Measure the time between a date/time and a date.
   * <p>
   * The notion of time between a date/time and a date can be in some cases a little bit arbitrary
   * but is required in some cases as the interest rate product recognize only dates and not time 
   * while option exercise has a time.
   *  
   * @param dateTimeStart  the start date/time
   * @param date  the end date
   * @return the measure of time distance between the two dates
   */
  public double relativeTime(ZonedDateTime dateTimeStart, LocalDate date);


  /**
   * Measure the time between two local dates.
   * <p>
   * The notion of time between two dates can be in some cases a little bit arbitrary
   * but is required in some cases as the interest rate product recognize only dates.
   *  
   * @param dateStart  the start date
   * @param date  the end date
   * @return the measure of time distance between the two dates
   */
  public double relativeTime(LocalDate dateStart, LocalDate date);

}
