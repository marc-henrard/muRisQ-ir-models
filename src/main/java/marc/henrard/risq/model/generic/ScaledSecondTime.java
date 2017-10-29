/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Measure the time between two temporal indications.
 * <p>
 * For two {@link ZonedDateTime}, the time is measured by the number of seconds and rescaled to a year 
 * considering a 365 day year and ignoring leap seconds (divided by 31_536_000.0).
 * <P>
 * If at least one is a {@link LocalDate}, the time is measured by the number of days and rescaled to a year 
 * considering a 365 day year (divided by 365.0). 
 * 
 * @author Marc Henrard
 */
public class ScaledSecondTime 
    implements TimeMeasurement, Serializable {

  private static final long serialVersionUID = 1L;

  /** The number of second in a year, for a 365 day year and ignoring leap seconds. */
  private final static double SECONDS_BY_YEAR = 31_536_000.0d;
  private final static double DAYS_BY_YEAR = 365.0d;
  
  /** The default instance of the time measurement. */
  public final static ScaledSecondTime DEFAULT = new ScaledSecondTime();
  
  // private constructor
  public ScaledSecondTime(){
  }

  @Override
  public double relativeTime(ZonedDateTime dateTimeStart, ZonedDateTime dateTimeEnd) {
    return (dateTimeEnd.toEpochSecond() - dateTimeStart.toEpochSecond()) / SECONDS_BY_YEAR;
  }

  @Override
  public double relativeTime(ZonedDateTime dateTimeStart, LocalDate date) {
    return ChronoUnit.DAYS.between(dateTimeStart.toLocalDate(), date) / DAYS_BY_YEAR;
  }

  @Override
  public double relativeTime(LocalDate dateStart, LocalDate date) {
    return ChronoUnit.DAYS.between(dateStart, date) / DAYS_BY_YEAR;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    return prime;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    return true;
  }

}
