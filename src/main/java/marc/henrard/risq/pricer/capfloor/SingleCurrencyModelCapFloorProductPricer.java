/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloor;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorLeg;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;

import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;

/**
 * Price of cap/floor product in a multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelCapFloorProductPricer {
  
  /** The pricer for the {@link ResolvedIborCapFloorLeg}. */
  private final SingleCurrencyModelCapFloorLegPricer capFloorLegPricer;
  /** The pricer for the {@link ResolvedSwapLeg}. */
  private final DiscountingSwapLegPricer vanillaLegPricer;
  
  /**
   * Creates and instance of the leg pricer.
   * 
   * @param capFloorLegPricer  the pricer for the {@link ResolvedIborCapFloorLeg}
   * @param vanillaLegPricer  the pricer for the {@link ResolvedSwapLeg
   */
  public SingleCurrencyModelCapFloorProductPricer(
      SingleCurrencyModelCapFloorLegPricer capFloorLegPricer,
      DiscountingSwapLegPricer vanillaLegPricer) {
    this.capFloorLegPricer = capFloorLegPricer;
    this.vanillaLegPricer = vanillaLegPricer;
  }

  /**
   * Calculates the present value of the Ibor cap/floor product.
   * <p>
   * The result is expressed using the currency of each leg.
   * 
   * @param capFloor  the Ibor cap/floor product
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the present value
   */
  public MultiCurrencyAmount presentValue(
      ResolvedIborCapFloor capFloor,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    CurrencyAmount pvCapFloorLeg =
        capFloorLegPricer.presentValue(capFloor.getCapFloorLeg(), multicurve, model);
    if (!capFloor.getPayLeg().isPresent()) {
      return MultiCurrencyAmount.of(pvCapFloorLeg);
    }
    CurrencyAmount pvPayLeg = vanillaLegPricer.presentValue(capFloor.getPayLeg().get(), multicurve);
    return MultiCurrencyAmount.of(pvCapFloorLeg).plus(pvPayLeg);
  }
  
}
