/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;

import marc.henrard.risq.model.generic.ModelParameters;

/**
 * Interest rate multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * The martingales are A(1) = exp(a_1 X_t^(1) - 0.5 a_1^2 t) - 1, A(2) = exp(a_2 X_t^(2)  - 0.5 a_2^2 t) - 1.
 * The Libor process numerator is of the form L(0) + b_1 A(1) + b_2 A(2) 
 * The discount factor process numerator is of the form P(0,T) + b_0(T) A(1)
 * 
 * @author Marc Henrard
 */
public interface RationalTwoFactorParameters 
    extends ModelParameters {
  
  /**
   * The currency for which the model is valid.
   * @return the currency
   */
  public Currency getCurrency();
  
  /**
   * Returns the b0 parameter at a given date.
   * @param date  the date
   * @return  the parameter
   */
  public double b0(LocalDate date);

  /**
   * Returns the b1 parameter used in the Libor process evolution for a given index and Ibor observation.
   * <p>
   * The b1 function can be different for each index.
   * 
   * @param obs  the ibor index observation
   *  
   * @return  the parameter
   */
  public double b1(IborIndexObservation obs);

  /**
   * Returns the b2 parameter used in the Libor process evolution for a given index and Ibor observation.
   * <p>
   * The b2 function can be different for each index.
   * 
   * @param obs  the ibor index observation
   *  
   * @return  the parameter
   */
  public double b2(IborIndexObservation obs);
  
  /**
   * Returns the parameter of first the log-normal martingale.
   * @return the parameter
   */
  public double a1();
  
  /**
   * Returns the parameter of second the log-normal martingale.
   * @return the parameter
   */
  public double a2();
  
  /**
   * Returns the correlation between X_1 and X_2.
   * @return the correlation
   */
  public double getCorrelation();
  
  /**
   * Gets the valuation date. All data items in this environment are calibrated for this date.
   * @return the value of the property, not null
   */
  public LocalDate getValuationDate();
  
  /**
   * Gets the valuation date and time. All data items in this environment are calibrated for this date.
   * @return the value of the property, not null
   */
  public ZonedDateTime getValuationDateTime();

  /**
   * Converts a time of day and date to a relative time. 
   * <p>
   * When the date is after the valuation date (and potentially time), the returned number is negative.
   * 
   * @param dateTime  the date/time to find the relative year fraction of
   * @return the time
   */
  public double relativeTime(ZonedDateTime dateTime);

}
