/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.generic;

import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.market.observable.IndexQuoteId;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;

/**
 * Tests {@link FallbackIborUtils}.
 * 
 * @author Marc Henrard
 */
@Test
public class FallbackIborUtilsTest {
  
  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final OvernightIndex ON_INDEX = OvernightIndices.GBP_SONIA;
  private static final LocalDate START_DATE = LocalDate.of(2018, 8, 17);
  private static final LocalDate END_DATE = LocalDate.of(2018, 9, 17);
  
  /* Fixings */
  private static final List<ResourceLocator> FIXING_RESOURCES = ImmutableList.of(
      ResourceLocator.of("src/test/resources/fixing/GBP-SONIA-FAKE.csv"));
  private static final Map<ObservableId, LocalDateDoubleTimeSeries> TIME_SERIES =
      FixingSeriesCsvLoader.load(FIXING_RESOURCES);
  private static final LocalDateDoubleTimeSeries ON_TS = TIME_SERIES.get(IndexQuoteId.of(ON_INDEX));
  
  private static final double TOLERANCE_RATE = 1.0E-6;

  public void compoundedInArrears() {
    OvernightCompoundedRateComputation computation = 
        OvernightCompoundedRateComputation.of(ON_INDEX, START_DATE, END_DATE, REF_DATA);
    double rateComputed = FallbackIborUtils.compoundedInArrears(ON_TS, computation);
    LocalDate currentFixingOn = START_DATE;
    double compositionFactor = 1.0d;
    while (currentFixingOn.isBefore(computation.getEndDate())) {
      LocalDate effectiveDate = currentFixingOn;
      LocalDate maturityDate = computation.getFixingCalendar().next(currentFixingOn);
      double accrualFactorOn = 
          computation.getIndex().getDayCount().yearFraction(effectiveDate, maturityDate);
      compositionFactor *= 1.0d 
          + accrualFactorOn * ON_TS.get(currentFixingOn).getAsDouble();
      currentFixingOn = maturityDate;
    }
    double accrualFactorPeriod = computation.getIndex().getDayCount()
        .yearFraction(computation.getStartDate(), computation.getEndDate());
    double rateExpected =  (compositionFactor - 1.0d) / accrualFactorPeriod;
    assertEquals(rateComputed, rateExpected, TOLERANCE_RATE);
  }
  
}
