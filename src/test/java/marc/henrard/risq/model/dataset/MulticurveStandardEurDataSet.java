/**
 * Copyright (C) 2017 - Marc Henrard.
 */
package marc.henrard.risq.model.dataset;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupDefinition;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.pricer.curve.CurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Generate a multi-curve in EUR with standard configuration and quotes from csv file.
 * 
 * @author Marc Henrard
 */
public class MulticurveStandardEurDataSet {

  private static final String PATH_CONFIG = "src/test/resources/curve-config/";
  private static final String PATH_QUOTES = "src/test/resources/quotes/";

  private static final String CURVE_GROUP_STD_NAME_STR = "EUR-DSCONOIS-E3MIRS-E6MIRS";
  private static final CurveGroupName CURVE_GROUP_STD_NAME = CurveGroupName.of(CURVE_GROUP_STD_NAME_STR);
  private static final ResourceLocator GROUPS_STD_FILE = ResourceLocator.of( 
      PATH_CONFIG + CURVE_GROUP_STD_NAME_STR +"/" + CURVE_GROUP_STD_NAME_STR + "-group.csv");
  private static final ResourceLocator SETTINGS_STD_FILE =ResourceLocator.of( 
      PATH_CONFIG + CURVE_GROUP_STD_NAME_STR +"/" + CURVE_GROUP_STD_NAME_STR + "-settings-linear.csv");
  private static final ResourceLocator NODES_STD_FILE =ResourceLocator.of( 
      PATH_CONFIG + CURVE_GROUP_STD_NAME_STR +"/" + CURVE_GROUP_STD_NAME_STR + "-nodes-standard.csv");
  private static final CurveGroupDefinition GROUP_STD_DEFINITION = RatesCalibrationCsvLoader
      .load(GROUPS_STD_FILE, SETTINGS_STD_FILE, NODES_STD_FILE).get(CURVE_GROUP_STD_NAME);

  private static final CurveCalibrator CALIBRATOR = CurveCalibrator.standard();

  public static ImmutableRatesProvider multicurve(LocalDate calibrationDate, ReferenceData refData) {
    String fileQuotes = PATH_QUOTES + "MARKET-QUOTES-EUR-Standard-" 
        + calibrationDate.format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
    MarketData marketData = MarketData
        .of(calibrationDate, QuotesCsvLoader.load(calibrationDate, ResourceLocator.of(fileQuotes)));
    return CALIBRATOR.calibrate(GROUP_STD_DEFINITION, marketData, refData);
  }

}
