/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Measure the time by the number of seconds and rescaled to a year,
 * for a 365 day year and ignoring leap seconds (multiplied by 31_536_000.0).
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public class ScaledSecondTime 
    implements TimeMeasurement, Serializable {

  private static final long serialVersionUID = 1L;

  /** The number of second in a year, for a 365 day year and ignoring leap seconds. */
  private final static double SECOND_BY_YEAR = 31_536_000.0d;
  
  /** The default instance of the time measurement. */
  public final static ScaledSecondTime DEFAULT = new ScaledSecondTime();
  
  // private constructor
  public ScaledSecondTime(){
  }

  @Override
  public double relativeTime(ZonedDateTime dateTimeStart, ZonedDateTime dateTimeEnd) {
    return (dateTimeEnd.toEpochSecond() - dateTimeStart.toEpochSecond()) / SECOND_BY_YEAR;
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
