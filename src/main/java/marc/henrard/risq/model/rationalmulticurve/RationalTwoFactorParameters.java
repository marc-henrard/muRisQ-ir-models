/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.time.LocalDate;

import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;

import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;

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
    extends SingleCurrencyModelParameters {
  
  /**
   * Returns the b0 parameter at a given date.
   * <p>
   * The date must be after the model valuation date.
   * 
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
   * Validate that a date used is on or after the valuation date.
   * 
   * @param date  the date
   */
  default public void validateDate(LocalDate date) {
    ArgChecker.isFalse(date.isBefore(getValuationDate()), "date should be on or after valuation date");
  }
  
  /**
   * Validate that an Ibor observation has not fixed yet.
   * 
   * @param date  the date
   */
  default public void validateObservation(IborIndexObservation obs) {
    ArgChecker.isFalse(obs.getFixingDate().isBefore(getValuationDate()), 
        "fixing date should be on or after valuation date");
  }

}
