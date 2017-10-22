/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.generic;

import java.time.LocalDate;

import com.opengamma.strata.market.param.ParameterizedData;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;

/**
 * Interface to a parameter curve where the value are given on dates. 
 * 
 * @author Marc Henrard
 */
public interface ParameterDateCurve
    extends ParameterizedData {

  /**
   * Returns the value of the parameter at the given date.
   * 
   * @param date  the date
   * @return the parameter
   */
  public double parameterValue(LocalDate date);
  
  /**
   * Returns the sensitivity of the parameter value to the underlying interest rate curves.
   * 
   * @param date  the date
   * @return the sensitivity
   */
  public PointSensitivityBuilder parameterValueCurveSensitivity(LocalDate date);
  
}
