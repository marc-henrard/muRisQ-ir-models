/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.capfloor;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorLeg;

/**
 * Pricer for IBOR cap/floor legs in Hull-White one factor model with piecewise constant volatility
 * and deterministic multiplicative basis between discounting and IBOR forwards.
 * <p>
 * The payment date can be different from the IBOR end date.
 * <p>
 * Reference for deterministic multiplicative basis: 
 *   Henrard, M. "The Irony in the derivatives discounting Part II: the crisis", Wilmott Journal, 2010, 2, 301-316
 */
public class HullWhiteCapFloorLegPricer {
  
  /**
   * Default implementation.
   */
  public static final HullWhiteCapFloorLegPricer DEFAULT = new HullWhiteCapFloorLegPricer();

  /**
   * Creates an instance.
   */
  public HullWhiteCapFloorLegPricer() {
  }
  
  /** Period pricer */
  private static final HullWhiteCapletFloorletPeriodPricer PERIOD_PRICER =
      HullWhiteCapletFloorletPeriodPricer.DEFAULT;
  
  /**
   * Calculates the present value of the Ibor cap/floor leg.
   * <p>
   * The result is expressed using the currency of the leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param hwProvider  the Hull-White model parameter provider
   * @return the present value
   */
  public CurrencyAmount presentValue(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    return capFloorLeg.getCapletFloorletPeriods()
        .stream()
        .map(period -> PERIOD_PRICER.presentValue(period, multicurve, hwProvider))
        .reduce((c1, c2) -> c1.plus(c2))
        .get();
  }
  
  /**
   * Calculates the currency exposure of the Ibor cap/floor leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param hwProvider  the Hull-White model parameter provider
   * @return the currency exposure
   */
  public MultiCurrencyAmount currencyExposure(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    return capFloorLeg.getCapletFloorletPeriods()
        .stream().map(period -> PERIOD_PRICER.currencyExposure(period, multicurve, hwProvider))
        .reduce((c1, c2) -> c1.plus(c2))
        .get();
  }
  
  /**
   * Calculates the rate sensitivity of the Ibor cap/floor leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param hwProvider  the Hull-White model parameter provider
   * @return the sensitivity to rates
   */
  public PointSensitivityBuilder presentValueSensitivityRates(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    return capFloorLeg.getCapletFloorletPeriods()
        .stream().map(period -> PERIOD_PRICER.presentValueSensitivityRates(period, multicurve, hwProvider))
        .reduce((c1, c2) -> c1.combinedWith(c2))
        .get();
  }

}
