/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import java.time.ZonedDateTime;

/**
 * Measure the time by the number of seconds and rescaled to a year,
 * for a 365 day year and ignoring leap seconds (multiplied by 31_536_000.0).
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public class ScaledSecondTime 
    implements TimeMeasurement {

  /** The number of second in a year, for a 365 day year and ignoring leap seconds. */
  private final static double SECOND_BY_YEAR = 31_536_000.0d;
  
  /** The default instance of the time measurement. */
  public final static ScaledSecondTime DEFAULT = new ScaledSecondTime();
  
  // private constructor
  private ScaledSecondTime(){
  }

  @Override
  public double relativeTime(ZonedDateTime dateTimeStart, ZonedDateTime dateTimeEnd) {
    return (dateTimeEnd.toEpochSecond() - dateTimeStart.toEpochSecond()) / SECOND_BY_YEAR;
  }

}
