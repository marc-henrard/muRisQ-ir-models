/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.util.BitSet;
import java.util.function.Function;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.minimization.NonLinearParameterTransforms;

/**
 * Template for rational model. 
 * <p>
 * Used in model calibration. The main method is the one generating a model from a set of parameters.
 * 
 * @author Marc Henrard
 */
public interface RationalTemplate {
  
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
   * Generates a rational from a set of parameters.
   * 
   * @param parameters  the parameters
   * @return the model
   */
  public abstract RationalParameters generate(DoubleArray parameters);
  
  /**
   * Returns the parameter transform to take the parameters bounds into account.
   * 
   * @param fixed  the fixed parameters which are not calibrated but constant at their initial guess
   * @return  the transform
   */
  public abstract NonLinearParameterTransforms getTransform();
  
  /**
   * Returns the set of fixed parameters.
   * 
   * @return the fixed parameters
   */
  public abstract BitSet getFixed();
  
  /**
   * Creates a function that returns true if the trial point is within the constraints of the model.
   * 
   * @return the function
   */
  public abstract Function<DoubleArray, Boolean> getConstraints();

}
