/**
 * Copyright (C) 2017 - Marc Henrard.
 */
package marc.henrard.murisq.dataset;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.data.ObservableId;
import com.opengamma.strata.loader.csv.FixingSeriesCsvLoader;
import com.opengamma.strata.loader.csv.QuotesCsvLoader;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.pricer.curve.RatesCurveCalibrator;
import com.opengamma.strata.pricer.curve.SyntheticRatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Generate a multi-curve with standard configuration and quotes from csv file.
 * 
 * @author Marc Henrard
 */
public class MulticurveStandardDataSet {

  private static final RatesCurveCalibrator CALIBRATOR = RatesCurveCalibrator.standard();
  private static final SyntheticRatesCurveCalibrator CALIBRATOR_SYNTHETIC = 
      SyntheticRatesCurveCalibrator.standard();

  /**
   * Returns a multi-curve provider calibrated using a given curve group defined in given resources.
   * 
   * @param calibrationDate  the calibration date
   * @param curveGroupName  the curve group name
   * @param groupFile  the file with the group definition
   * @param settingsFile  the file with the settings
   * @param nodesFile  the file with the curves' nodes
   * @param refData  the reference data
   * @return the calibrated curves
   */
  public static ImmutableRatesProvider multicurve(
      LocalDate calibrationDate,
      CurveGroupName curveGroupName,
      ResourceLocator groupFile,
      ResourceLocator settingsFile,
      ResourceLocator nodesFile,
      String fileQuotes,
      ReferenceData refData) {

    RatesCurveGroupDefinition groupDefinition = RatesCalibrationCsvLoader
        .load(groupFile, settingsFile, nodesFile).get(curveGroupName);
    Map<QuoteId, Double> quotes = QuotesCsvLoader.load(calibrationDate, ResourceLocator.of(fileQuotes));
    MarketData marketData = MarketData.of(calibrationDate, quotes);
    return CALIBRATOR.calibrate(groupDefinition, marketData, refData);
  }

  /**
   * Returns a multi-curve provider calibrated using a given curve group defined in given resources and 
   * with historical times series for fixings.
   * 
   * @param calibrationDate  the calibration date
   * @param curveGroupName  the curve group name
   * @param groupFile  the file with the group definition
   * @param settingsFile  the file with the settings
   * @param nodesFile  the file with the curves' nodes
   * @param fixingResources  the resource locators with the time series
   * @param refData  the reference data
   * @return the calibrated curves
   */
  public static ImmutableRatesProvider multicurve(
      LocalDate calibrationDate,
      CurveGroupName curveGroupName,
      ResourceLocator groupFile,
      ResourceLocator settingsFile,
      ResourceLocator nodesFile,
      String fileQuotes,
      List<ResourceLocator> fixingResources,
      ReferenceData refData) {
    
    Map<ObservableId, LocalDateDoubleTimeSeries> timeSeries =
        FixingSeriesCsvLoader.load(fixingResources);
    RatesCurveGroupDefinition groupDefinition = RatesCalibrationCsvLoader
        .load(groupFile, settingsFile, nodesFile).get(curveGroupName);
    MarketData marketData = MarketData
        .of(calibrationDate, 
            QuotesCsvLoader.load(calibrationDate, ResourceLocator.of(fileQuotes)),
            timeSeries);
    return CALIBRATOR.calibrate(groupDefinition, marketData, refData);
  }

  /**
   * Returns a multi-curve provider calibrated from a set of nodes with quotes build synthetically from
   * a given curve group defined in given resources.
   * 
   * @param calibrationDate  the calibration date
   * @param curveGroupName  the curve group name
   * @param groupFile  the file with the group definition
   * @param settingsFile  the file with the settings
   * @param nodesFile  the file with the curves' nodes
   * @param nodesSyntheticFile  the file with the nodes for the synthetic curves
   * @param refData  the reference data
   * @return the calibrated curves
   */
  public static ImmutableRatesProvider multicurveSynthetic(
      LocalDate calibrationDate,
      CurveGroupName curveGroupName,
      ResourceLocator groupFile,
      ResourceLocator settingsFile,
      ResourceLocator nodesFile,
      ResourceLocator nodesSyntheticFile,
      String fileQuotes,
      ReferenceData refData) {

    RatesCurveGroupDefinition groupDefinition = RatesCalibrationCsvLoader
        .load(groupFile, settingsFile, nodesFile).get(curveGroupName);
    MarketData marketData = MarketData
        .of(calibrationDate, QuotesCsvLoader.load(calibrationDate, ResourceLocator.of(fileQuotes)));
    ImmutableRatesProvider multicurve =  CALIBRATOR.calibrate(groupDefinition, marketData, refData);
    RatesCurveGroupDefinition groupDefinitionSynthetic = RatesCalibrationCsvLoader
        .load(groupFile, settingsFile, nodesSyntheticFile).get(curveGroupName);
    return CALIBRATOR_SYNTHETIC.calibrate(groupDefinitionSynthetic, multicurve, refData);
  }
  

}
