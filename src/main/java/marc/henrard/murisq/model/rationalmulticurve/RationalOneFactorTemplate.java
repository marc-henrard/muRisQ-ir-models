/**
 * Copyright (C) 2015 - Marc Henrard.
 */
package marc.henrard.murisq.model.rationalmulticurve;

import com.opengamma.strata.collect.array.DoubleArray;

/**
 * Template for one factor rational model. 
 * <p>
 * Used in model calibration. The main method is the one generating a model from a set of parameters.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public interface RationalOneFactorTemplate {
  
  /**
   * Returns the number of parameter expected by the template.
   * 
   * @return the number
   */
  public abstract int parametersCount();
  
  /**
   * Returns the initial guess for the parameters.
   * 
   * @return the guess
   */
  public abstract DoubleArray initialGuess();
  
  /**
   * Generates a rational one factor model from a set of parameters.
   * 
   * @param parameters  the parameters
   * @return the model
   */
  public abstract RationalOneFactorParameters generate(DoubleArray parameters);

}
