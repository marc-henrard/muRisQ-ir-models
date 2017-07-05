/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import static org.testng.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

/**
 * Tests {@link ScaledSecondTime}
 */
@Test
public class ScaledSecondTimeTest {

  private final static ScaledSecondTime TIME_MEASURE = ScaledSecondTime.DEFAULT;
  private final static double SECOND_BY_YEAR = 60 * 60 * 24 * 365; // On a 365 d/year basis
  private final static double TOLERANCE_TIME = 1.0E-9; // Less than 1 second
  
  public void relative_time_sec() {
    ZonedDateTime dateTimeStart = ZonedDateTime.of(2016, 8, 18, 11, 12, 13, 0, ZoneId.of("Europe/Brussels"));
    ZonedDateTime dateTimeEnd = ZonedDateTime.of(2016, 8, 18, 11, 13, 12, 0, ZoneId.of("Europe/Brussels"));
    double timeComputed = TIME_MEASURE.relativeTime(dateTimeStart, dateTimeEnd);
    assertEquals(timeComputed, 59.0 / SECOND_BY_YEAR , TOLERANCE_TIME);
  }
  
  public void relative_time_hour() {
    ZonedDateTime dateTimeStart = ZonedDateTime.of(2016, 8, 18, 11, 12, 13, 0, ZoneId.of("Europe/Brussels"));
    ZonedDateTime dateTimeEnd = ZonedDateTime.of(2016, 8, 18, 12, 14, 15, 0, ZoneId.of("Europe/Brussels"));
    double timeComputed = TIME_MEASURE.relativeTime(dateTimeStart, dateTimeEnd);
    assertEquals(timeComputed, (60*60 + 2*60 + 2) / SECOND_BY_YEAR , TOLERANCE_TIME);
  }
  
  public void relative_time_days() {
    ZonedDateTime dateTimeStart = ZonedDateTime.of(2016, 8, 18, 11, 12, 13, 0, ZoneId.of("Europe/Brussels"));
    ZonedDateTime dateTimeEnd = ZonedDateTime.of(2016, 9, 20, 12, 14, 15, 0, ZoneId.of("Europe/Brussels"));
    double timeComputed = TIME_MEASURE.relativeTime(dateTimeStart, dateTimeEnd);
    assertEquals(timeComputed, (60*(60*(24*(31+2) + 1) + 2) + 2) / SECOND_BY_YEAR , TOLERANCE_TIME);
  }
  
  public void relative_time_zone() {
    ZonedDateTime dateTimeStart = ZonedDateTime.of(2016, 8, 18, 11, 12, 13, 0, ZoneId.of("Europe/London"));
    ZonedDateTime dateTimeEnd = ZonedDateTime.of(2016, 9, 20, 16, 14, 15, 0, ZoneId.of("Europe/Brussels"));
    double timeComputed = TIME_MEASURE.relativeTime(dateTimeStart, dateTimeEnd);
    assertEquals(timeComputed, (60*(60*(24*(31+2) + 5 - 1) + 2) + 2) / SECOND_BY_YEAR , TOLERANCE_TIME);
  }
  
}
