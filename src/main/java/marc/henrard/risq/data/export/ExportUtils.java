/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.data.export;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.io.CsvOutput;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.observable.QuoteId;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.param.CurrencyParameterSensitivity;
import com.opengamma.strata.market.param.ParameterMetadata;

/**
 * Utilities to export objects (typically in csv files or in the console).
 * 
 * @author Marc Henrard
 */
public class ExportUtils {
  
  /**
   * Append a map of market quotes to a csv-like destination in the standard Strata format.
   * 
   * @param date  the date for which the quotes are valid
   * @param quotes  the quotes as a map
   * @param destination  the destination to which the csv-like string is appended
   * @throws IOException 
   */
  public static void exportMarketQuotes(
      LocalDate date,
      Map<QuoteId, Double> quotes,
      Appendable destination,
      boolean exportHeader) throws IOException {
    if (exportHeader) {
      String header = "Valuation Date, Symbology, Ticker, Field Name, Value\n";
      destination.append(header);
    }
    String dateStr = date.toString();
    for (Entry<QuoteId, Double> entry : quotes.entrySet()) {
      String row = dateStr + ", ";
      row = row + entry.getKey().getStandardId().getScheme() + ", ";
      row = row + entry.getKey().getStandardId().getValue() + ", ";
      row = row + entry.getKey().getFieldName().toString() + ", ";
      row = row + entry.getValue() + "\n";
      destination.append(row);
    }
  }

  /**
   * Append the csv-like representation of a time series at the end of an appendable.
   * 
   * @param name  the name of the time serie exported
   * @param values  the values
   * @param destination  the destination to which the csv-like string is appended
   * @throws IOException
   */
  public static void exportTimeSeries(
      String name,
      LocalDateDoubleTimeSeries values,
      Appendable destination) throws IOException {

    String header = "Reference, Date, Value\n";
    destination.append(header);
    StringBuilder builder = new StringBuilder();
    values.stream()
        .forEach(p -> {
          String row = name + "," + p.getDate().format(DateTimeFormatter.ISO_DATE) + "," + p.getValue() + "\n";
          builder.append(row);
        });
    destination.append(builder.toString());
  }

  /**
   * Append a set of values in an array to a csv-like destination.
   * 
   * @param headers  the header of each column
   * @param values  the values in array format
   * @param destination  the destination to which the csv-like string is appended
   * @throws IOException 
   */
  public static void exportArray(
      String[] headers,
      double[][] values,
      Appendable destination) throws IOException {
    int nbColumns = headers.length;
    int nbRows = values.length;
    destination.append(headers[0]);
    for (int c = 1; c < nbColumns; c++) {
      destination.append(", " + headers[c]);
    }
    destination.append("\n");
    for (int r = 0; r < nbRows; r++) {
      destination.append("" + values[r][0]);
      for (int c = 1; c < values[r].length; c++) {
        destination.append("," + values[r][c]);
      }
      destination.append("\n");
    }
  }

  /**
   * Append a set of values in an array to a csv-like destination.
   * 
   * @param headers  the header of each column
   * @param values  the values in array format
   * @param destination  the destination to which the csv-like string is appended
   * @throws IOException 
   */
  public static void exportArray(
      double[][] values,
      Appendable destination) throws IOException {
    int nbRows = values.length;
    for (int r = 0; r < nbRows; r++) {
      destination.append("" + values[r][0]);
      for (int c = 1; c < values[r].length; c++) {
        destination.append("," + values[r][c]);
      }
      destination.append("\n");
    }
  }
  
  /**
   * Append a {@link CurrencyParameterSensitivities} to a csv-like destination.
   * <p>
   * The sensitivities for each curve are exported one below the other in two columns. One indicating
   * the metadata and the other one the value. All the values are multiplied by a common factors. This is
   * to allow the scaling of the internal representation, which is typically as sensitivity to 1, to the
   * representation used externally, which is typically as sensitivity to 1 basis point (0.0001).
   * <p>
   * The metadata should be present for all curves.
   * 
   * @param sensitivities  the sensitivities
   * @param scale  the scaling factor
   * @param destination  the destination to which the csv-like string is appended
   */
  public static void exportCurrencyParameterSensitivities(
      CurrencyParameterSensitivities sensitivities,
      double scale,
      Appendable destination) {

    CsvOutput csv = new CsvOutput(destination);
    List<CurrencyParameterSensitivity> sensitivitiesAsList = sensitivities.getSensitivities();
    csv.writeLine(ImmutableList.of("Label", "Value"));
    for (CurrencyParameterSensitivity sensitivity : sensitivitiesAsList) {
      csv.writeLine(ImmutableList.of(sensitivity.getMarketDataName().toString(), sensitivity.getCurrency().toString()));
      ArgChecker.isFalse(sensitivity.getParameterMetadata().isEmpty(), "Parameters metadata required");
      DoubleArray sensitivityValues = sensitivity.getSensitivity();
      List<ParameterMetadata> sensitivityMetadata = sensitivity.getParameterMetadata();
      for (int loopnode = 0; loopnode < sensitivityValues.size(); loopnode++) {
        csv.writeLine(ImmutableList.of(
            sensitivityMetadata.get(loopnode).getLabel(),
            Double.toString((sensitivityValues.get(loopnode) * scale))));
      }
    }
  }

  /**
   * Exports a string to a file. Useful in particular for CSV, XML and beans.
   * 
   * @param string  the string to export
   * @param fileName  the name of the file
   */
  public static void exportString(
      String string,
      String fileName) {
    try (FileWriter writer = new FileWriter(fileName)) {
      writer.append(string);
      writer.close();
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

}
