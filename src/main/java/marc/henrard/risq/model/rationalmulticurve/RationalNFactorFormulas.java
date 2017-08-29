/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.util.List;

import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;

/**
 * Interest rate multi-curve rational model.
 * <p>
 * <i>References: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * Helpers related to N-factor formulas.
 * 
 * @author Marc Henrard
 */
public class RationalNFactorFormulas {

  // check that one leg is fixed and return it
  public static ResolvedSwapLeg fixedLeg(ResolvedSwap swap) {
    // find fixed leg
    List<ResolvedSwapLeg> fixedLegs = swap.getLegs(SwapLegType.FIXED);
    if (fixedLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a fixed leg");
    }
    return fixedLegs.get(0);
  }

  // check that one leg is ibor and return it
  public static ResolvedSwapLeg iborLeg(ResolvedSwap swap) {
    // find ibor leg
    List<ResolvedSwapLeg> iborLegs = swap.getLegs(SwapLegType.IBOR);
    if (iborLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a ibor leg");
    }
    return iborLegs.get(0);
  }

}
