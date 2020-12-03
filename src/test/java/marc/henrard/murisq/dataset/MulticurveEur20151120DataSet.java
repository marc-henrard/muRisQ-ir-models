/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.dataset;

import java.time.LocalDate;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Common definition of EUR curves for testing purposes.
 * 
 * @author Marc Henrard
 */
public class MulticurveEur20151120DataSet {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  public static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);

  /* Load and calibrate curves */
  private static final String GROUP_NAME_EONIA = "EUR-DSCEONIAOIS-E3MIRS-E6MIRS";
  private static final String GROUP_NAME_ESTR = "EUR-DSCESTROIS-E3MIRS-E6MIRS";
  private static final String PATH_CONFIG = "src/test/resources/curve-config/";
  private static final String FILE_QUOTES = "src/test/resources/quotes/quotes-20151120-eur.csv";
  private static final String FILE_QUOTES_POS = "src/test/resources/quotes/quotes-20151120-eur-positif.csv";

  private static final ResourceLocator GROUPS_RESOURCE_EONIA =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + GROUP_NAME_EONIA + "/" 
          + GROUP_NAME_EONIA + "-group.csv");
  private static final ResourceLocator SETTINGS_RESOURCE_EONIA =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + GROUP_NAME_EONIA + "/" 
          + GROUP_NAME_EONIA + "-settings-linear.csv");
  private static final ResourceLocator NODES_RESOURCE_EONIA =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + GROUP_NAME_EONIA + "/" 
          + GROUP_NAME_EONIA + "-nodes-standard.csv");
  private static final ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> GROUPS_CONFIG_EONIA =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE_EONIA, SETTINGS_RESOURCE_EONIA, NODES_RESOURCE_EONIA);


  private static final ResourceLocator GROUPS_RESOURCE_ESTR =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + GROUP_NAME_ESTR + "/" 
          + GROUP_NAME_ESTR + "-group.csv");
  private static final ResourceLocator SETTINGS_RESOURCE_ESTR =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + GROUP_NAME_ESTR + "/" 
          + GROUP_NAME_ESTR + "-settings-linear.csv");
  private static final ResourceLocator NODES_RESOURCE_ESTR =
      ResourceLocator.of(ResourceLocator.FILE_URL_PREFIX + PATH_CONFIG + GROUP_NAME_ESTR + "/" 
          + GROUP_NAME_ESTR + "-nodes-standard.csv");
  private static final ImmutableMap<CurveGroupName, RatesCurveGroupDefinition> GROUPS_CONFIG_ESTR =
      RatesCalibrationCsvLoader.load(GROUPS_RESOURCE_ESTR, SETTINGS_RESOURCE_ESTR, NODES_RESOURCE_ESTR);
  
  private static final MarketData MARKET_DATA = 
      MarketData.of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES)));
  private static final MarketData MARKET_DATA_POS = 
      MarketData.of(VALUATION_DATE, QuotesCsvLoader.load(VALUATION_DATE, ResourceLocator.of(FILE_QUOTES_POS)));

  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  public static final ImmutableRatesProvider MULTICURVE_EUR_EONIA_20151120 = 
      CALIBRATOR.calibrate(GROUPS_CONFIG_EONIA.get(CurveGroupName.of(GROUP_NAME_EONIA)), MARKET_DATA, REF_DATA);
  public static final ImmutableRatesProvider MULTICURVE_EUR_EONIA_POS_20151120 = 
      CALIBRATOR.calibrate(GROUPS_CONFIG_EONIA.get(CurveGroupName.of(GROUP_NAME_EONIA)), MARKET_DATA_POS, REF_DATA);
  public static final ImmutableRatesProvider MULTICURVE_EUR_ESTR_20151120 = 
      CALIBRATOR.calibrate(GROUPS_CONFIG_ESTR.get(CurveGroupName.of(GROUP_NAME_ESTR)), MARKET_DATA, REF_DATA);

}
