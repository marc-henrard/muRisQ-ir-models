/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.shortrate;

import java.time.LocalDate;
import java.util.function.Function;

import com.opengamma.strata.pricer.DiscountFactors;

/**
 * Utilities for short rate models.
 * 
 * @author Marc Henrard
 */
public class ShortRateModelsUtils {
  
  /**
   * Returns a function describing the instantaneous forward rates (-D_t ln (P(t))) 
   * for a discount factor curve.
   * 
   * @param discountFactors  the discount factors
   * @return the instantaneous forward rate function
   */
  public static Function<LocalDate, Double> instantaneousForward(DiscountFactors discountFactors) {
    return (d) -> {
      double t = discountFactors.relativeYearFraction(d);
      return -discountFactors.discountFactorTimeDerivative(t) / discountFactors.discountFactor(t);
    };
  }

}
