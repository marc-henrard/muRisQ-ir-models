/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import java.time.LocalDate;

import com.google.common.collect.ImmutableMap;
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
 * Common definition of EUR curves for testing purposes.
 * 
 * @author Marc Henrard
 */
public class CurveEurDataSet {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  public static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);

  /* Load and calibrate curves */
  private static final String PATH_CONFIG = "src/test/resources/curve-config/";
  private static final String FILE_QUOTES = "src/test/resources/quotes/quotes-20151120-eur.csv";

  private static final ResourceLocator GROUPS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "groups-eur.csv");
  private static final ResourceLocator SETTINGS_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "settings-eur.csv");
  private static final ResourceLocator NODES_RESOURCE =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + "nodes-eur.csv");
  private static final ImmutableMap<CurveGroupName, CurveGroupDefinition> GROUPS_CONFIG =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE, SETTINGS_RESOURCE, NODES_RESOURCE);
  private static final MarketData MARKET_DATA = 
      MarketData.of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));

  private static final CurveCalibrator CALIBRATOR = CurveCalibrator.standard();
  private static final String GROUP_NAME = "EUR-DSCONOIS-EURIBOR3MIRS-EURIBOR6MIRS";
  public static final ImmutableRatesProvider MULTICURVE_EUR_20151120 = 
      CALIBRATOR.calibrate(GROUPS_CONFIG.get(CurveGroupName.of(GROUP_NAME)), MARKET_DATA, REF_DATA);

}
