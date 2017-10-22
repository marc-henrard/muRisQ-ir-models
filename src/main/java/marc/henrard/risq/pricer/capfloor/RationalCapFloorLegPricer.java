/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorLeg;

import marc.henrard.risq.model.rationalmulticurve.RationalParameters;

/**
 * Price of cap/floor legs in a multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * 
 * @author Marc Henrard
 */
public class RationalCapFloorLegPricer {
  
  /** Pricer for {@link IborCapletFloorletPeriod} in the rational model. */
  private final RationalCapletFloorletPeriodPricer periodPricer;

  /**
   * Creates an instance.
   * 
   * @param periodPricer  the pricer for {@link IborCapletFloorletPeriod}.
   */
  public RationalCapFloorLegPricer(RationalCapletFloorletPeriodPricer periodPricer) {
    this.periodPricer = ArgChecker.notNull(periodPricer, "periodPricer");
  }
  
  /**
   * Calculates the present value of the Ibor cap/floor leg.
   * <p>
   * The result is expressed using the currency of the leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param model  the rational model parameters
   * @return the present value
   */
  public CurrencyAmount presentValue(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      RationalParameters model) {

    return capFloorLeg.getCapletFloorletPeriods()
        .stream()
        .map(period -> periodPricer.presentValue(period, multicurve, model))
        .reduce((c1, c2) -> c1.plus(c2))
        .get();
  }

}
