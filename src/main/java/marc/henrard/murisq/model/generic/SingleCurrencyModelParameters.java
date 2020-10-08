/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.generic;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.ParameterizedData;

/**
 * Interest rate multi-curve model for a single currency.
 * 
 * @author Marc Henrard
 */
public interface SingleCurrencyModelParameters
    extends ParameterizedData {

  /**
   * The currency for which the model is valid.
   * @return the currency
   */
  public Currency getCurrency();
  
  /**
   * Returns the valuation date. 
   * <p>
   * All data items in this environment are calibrated for this date.
   * 
   * @return the date
   */
  public LocalDate getValuationDate();
  
  /**
   * Returns the valuation date and time. 
   * <p>
   * All data items in this environment are calibrated for this date.
   * 
   * @return the date and time
   */
  public ZonedDateTime getValuationDateTime();
  
  /**
   * Converts a time of day and date to a relative time. 
   * <p>
   * When the date is after the valuation date (and potentially time), the returned number is positive.
   * 
   * @param dateTime  the date to find the relative time of
   * @return the time
   */
  public double relativeTime(ZonedDateTime dateTime);

  /**
   * The model parameters.
   * 
   * @return the parameters
   */
  public default DoubleArray getParameters() {
    int nbParam = getParameterCount();
    double[] p = new double[nbParam];
    for (int i = 0; i < nbParam; i++) {
      p[i] = getParameter(i);
    }
    return DoubleArray.ofUnsafe(p);
  }

}
