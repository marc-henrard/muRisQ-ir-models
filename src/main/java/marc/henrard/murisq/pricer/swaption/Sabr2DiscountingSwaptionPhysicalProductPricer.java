/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.value.ValueDerivatives;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrSwaptionVolatilities;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

/**
 * Pricer for swaption with physical settlement in SABR model on the swap rate.
 * <p>
 * The price is computed for a swaption in which the collateral to a switch date is different to the collateral after,
 * including the collateral of the delivered swaps. Two different discounting curves are used for the different periods.
 * <p>
 * Discounting reference: Henrard, M. (2020) Option pricing with two collateral rates. Forthcoming.
 * SABR reference: Hagan, P., Kumar, D., Lesniewski, A., and Woodward, D. (2002). Managing smile risk. 
 *     Wilmott Magazine, Sep:84–108.
 */
public class Sabr2DiscountingSwaptionPhysicalProductPricer
    extends Volatility2DiscountingSwaptionPhysicalProductPricer {

  /**
  * Default implementation.
  */
  public static final Sabr2DiscountingSwaptionPhysicalProductPricer DEFAULT =
      new Sabr2DiscountingSwaptionPhysicalProductPricer(DiscountingSwapProductPricer.DEFAULT);

  /**
  * Creates an instance.
  * 
  * @param swapPricer  the pricer for {@link Swap}
  */
  public Sabr2DiscountingSwaptionPhysicalProductPricer(DiscountingSwapProductPricer swapPricer) {
    super(swapPricer);
  }
  /**
   * Calculates the present value and present value sensitivity of the swaption product to the rate curves.
   * <p>
   * The present value sensitivity is computed in a "sticky model parameter" style, i.e. the sensitivity to the 
   * curve nodes with the SABR model parameters unchanged. This sensitivity does not include a potential 
   * re-calibration of the model parameters to the raw market data.
   * <p>
   * Approximation with ratio of discounting but no convexity adjustments.
   * <p>
   * The sensitivity result is divided in two parts, one with the sensitivity to the discount factors before the switch 
   * and one with the sensitivity to the multi-curve after the switch.
   * 
   * @param swaption  the swaption
   * @param discountFactorsBeforeSwitch  the discount factors used to compute discounting from today to switch
   * @param switchDate  the date for the collateral rate switch
   * @param ratesProviderAfterSwitch  the rates provider used to compute the value at expiry
   * @param swaptionVolatilities  the SABR volatilities
   * @return the present value and present value sensitivities
   */
  public Triple<CurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder> presentValueSensitivityRatesStickyModel(
      ResolvedSwaption swaption,
      DiscountFactors discountFactorsBeforeSwitch,
      LocalDate switchDate,
      RatesProvider ratesProviderAfterSwitch,
      SabrSwaptionVolatilities swaptionVolatilities) {

    validate(swaption, ratesProviderAfterSwitch, swaptionVolatilities);
    ZonedDateTime expiryDateTime = swaption.getExpiry();
    ArgChecker.isTrue(!switchDate.isAfter(expiryDateTime.toLocalDate()), "switch date must be before expiry date");
    double expiry = swaptionVolatilities.relativeTime(expiryDateTime);
    ResolvedSwap underlying = swaption.getUnderlying();
    ResolvedSwapLeg fixedLeg = fixedLeg(underlying);
    Currency ccy = fixedLeg.getCurrency();
    if (expiry < 0d) { // Option has expired already
      return Triple.of(CurrencyAmount.of(ccy, 0.0d), PointSensitivityBuilder.none(), PointSensitivityBuilder.none());
    }
    double forward = getSwapPricer().parRate(underlying, ratesProviderAfterSwitch);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProviderAfterSwitch);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProviderAfterSwitch, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double shift = swaptionVolatilities.shift(expiry, tenor);
    ValueDerivatives volatilityAdj = swaptionVolatilities.volatilityAdjoint(expiry, tenor, strike, forward);
    double shiftedForward = forward + shift;
    double shiftedStrike = strike + shift;
    boolean isCall = fixedLeg.getPayReceive().isPay();
    double price = BlackFormulaRepository.price(shiftedForward, shiftedStrike, expiry, volatilityAdj.getValue(), isCall);
    double sign = swaption.getLongShort().sign();
    double dfBefore = discountFactorsBeforeSwitch.discountFactor(switchDate);
    double df2 = ratesProviderAfterSwitch.discountFactor(ccy, switchDate);
    CurrencyAmount pv =  CurrencyAmount.of(ccy, dfBefore / df2 * price * Math.abs(pvbp) * sign);
    // Backward sweep
    PointSensitivityBuilder pvbpDr = getSwapPricer().getLegPricer().pvbpSensitivity(fixedLeg, ratesProviderAfterSwitch);
    PointSensitivityBuilder forwardDr = getSwapPricer().parRateSensitivity(underlying, ratesProviderAfterSwitch);
    double delta =
        BlackFormulaRepository.delta(shiftedForward, shiftedStrike, expiry, volatilityAdj.getValue(), isCall);
    double vega = BlackFormulaRepository.vega(shiftedForward, shiftedStrike, expiry, volatilityAdj.getValue());
    PointSensitivityBuilder sensitivity2 = pvbpDr.multipliedBy(price * sign * Math.signum(pvbp))
        .combinedWith(forwardDr.multipliedBy((delta + vega * volatilityAdj.getDerivative(0)) * Math.abs(pvbp) * sign));
    PointSensitivityBuilder sensitivityDfBefore =
        discountFactorsBeforeSwitch.zeroRatePointSensitivity(switchDate).multipliedBy(1 / df2);
    PointSensitivityBuilder sensitivityDfAfter =
        ratesProviderAfterSwitch.discountFactors(ccy).zeroRatePointSensitivity(switchDate)
            .multipliedBy(-dfBefore / (df2 * df2));
    PointSensitivityBuilder sensitivityRatesAfter = sensitivity2.multipliedBy(dfBefore / df2)
        .combinedWith(sensitivityDfAfter.multipliedBy(pv.getAmount()));
    PointSensitivityBuilder sensitivityRatesBefore = sensitivityDfBefore.multipliedBy(pv.getAmount());
    return Triple.of(pv, sensitivityRatesBefore, sensitivityRatesAfter);
  }

}
