/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.time.LocalDate;

import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;

import marc.henrard.risq.model.generic.ParameterDateCurve;
import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;

/**
 * Interest rate multi-curve rational model with one factor.
 * <p>
 * <i>References: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public interface RationalOneFactorParameters
    extends SingleCurrencyModelParameters {
  
  /**
   * Returns the parameter of the log-normal martingale.
   * @return the parameter
   */
  public double a();
  
  /**
   * Returns the b0 parameter used in the numeraire and discount factor dynamic at a given date.
   * 
   * @param date  the date
   * @return  the parameter
   */
  public double b0(LocalDate date);

  /**
   * Returns the b0 parameter curve used in the numeraire and discount factor dynamic.
   * 
   * @param date  the date
   * @return  the parameter
   */
  public ParameterDateCurve b0();
  
  /**
   * Returns the b1 parameter used in the Libor process evolution for a given index and Ibor observation.
   * <p>
   * The b1 function can be different for each index.
   * @param obs  the ibor index observation
   *  
   * @return  the parameter
   */
  public double b1(IborIndexObservation obs);
  
  /**
   * Returns the b0 parameter sensitivity to the interest rate curves.
   * <p>
   * This is used in case the b0 coefficient depends explicitly on P^D(0,.).
   * 
   * @param date  the date
   * @return  the parameter sensitivity to rates
   */
  public PointSensitivityBuilder b0Sensitivity(LocalDate date);
  
  /**
   * Returns the b1 parameter sensitivity to the interest rate curves.
   * <p>
   * This is used in case the b1 coefficient depends explicitly on P^D(0,.).
   * 
   * @param obs  the ibor index observation
   * @return  the parameter sensitivity to rates
   */
  public PointSensitivityBuilder b1Sensitivity(IborIndexObservation obs);
  
  // TODO: add sensitivity to parameters
  //  public ValueDerivatives b0ParameterSensitivity(LocalDate date);
  //  public ValueDerivatives b1ParameterSensitivity(IborIndexObservation obs);

}
