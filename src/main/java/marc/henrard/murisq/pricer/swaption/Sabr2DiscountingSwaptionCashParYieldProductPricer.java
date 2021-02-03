/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.value.ValueDerivatives;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrSwaptionVolatilities;
import com.opengamma.strata.product.common.PutCall;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

/**
 * Pricer for swaption with par yield cash settlement in SABR model on the swap rate.
 * <p>
 * The price is computed for a swaption in which the collateral to a switch date is different to the collateral after,
 * including the collateral of the delivered swaps. Two different discounting curves are used for the different periods.
 * <p>
 * Discounting reference: Henrard, M. (2021) Option pricing with two collateral rates. Forthcoming.
 * SABR reference: Hagan, P., Kumar, D., Lesniewski, A., and Woodward, D. (2002). Managing smile risk. 
 *     Wilmott Magazine, Sep:84–108.
 */
public class Sabr2DiscountingSwaptionCashParYieldProductPricer
    extends Volatility2DiscountingSwaptionCashParYieldProductPricer {

  /**
   * Default implementation.
   */
  public static final Sabr2DiscountingSwaptionCashParYieldProductPricer DEFAULT =
      new Sabr2DiscountingSwaptionCashParYieldProductPricer(DiscountingSwapProductPricer.DEFAULT);

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public Sabr2DiscountingSwaptionCashParYieldProductPricer(DiscountingSwapProductPricer swapPricer) {
    super(swapPricer);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the swaption product to the rate curves.
   * <p>
   * The present value sensitivity is computed in a "sticky model parameter" style, i.e. the sensitivity to the 
   * curve nodes with the SABR model parameters unchanged. This sensitivity does not include a potential 
   * re-calibration of the model parameters to the raw market data.
   * <p>
   * The sensitivity is divided into the sensitivity to the first discounting and 
   * the sensitivity to the second discounting and forward curves.
   * 
   * @param swaption  the swaption product
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the present value and the two sensitivities
   */
  public Triple<CurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder>
      presentValueSensitivityRatesStickyModel(
          ResolvedSwaption swaption,
          DiscountFactors discountFactorsBeforeSwitch,
          LocalDate switchDate,
          RatesProvider ratesProviderAfterSwitch,
          SabrSwaptionVolatilities swaptionVolatilities) {

    validate(swaption, ratesProviderAfterSwitch, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(swaption.getExpiry());
    ResolvedSwap underlying = swaption.getUnderlying();
    ResolvedSwapLeg fixedLeg = fixedLeg(underlying);
    Currency ccy = fixedLeg.getCurrency();
    if (expiry < 0d) { // Option has expired already
      return Triple.of(CurrencyAmount.of(ccy, 0.0d), PointSensitivityBuilder.none(), PointSensitivityBuilder.none());
    }
    double forward = getSwapPricer().parRate(underlying, ratesProviderAfterSwitch);
    PointSensitivityBuilder forwardDr = getSwapPricer().parRateSensitivity(underlying, ratesProviderAfterSwitch);
    Pair<Double, PointSensitivityBuilder> numeraireDerivatives =
        numeraireSensitivity(swaption, fixedLeg, forward, forwardDr.cloned(), ratesProviderAfterSwitch);
    double numeraire = numeraireDerivatives.getFirst();
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProviderAfterSwitch);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProviderAfterSwitch, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    ValueDerivatives volatilityAdj = swaptionVolatilities.volatilityAdjoint(expiry, tenor, strike, forward);
    double volatility = volatilityAdj.getValue();
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double price = swaptionVolatilities.price(expiry, tenor, putCall, strike, forward, volatility);
    double dfBefore = discountFactorsBeforeSwitch.discountFactor(switchDate);
    double dfAfter = ratesProviderAfterSwitch.discountFactor(ccy, switchDate);
    double sign = swaption.getLongShort().sign();
    CurrencyAmount pv = CurrencyAmount.of(ccy, numeraire * dfBefore / dfAfter * price * sign);
    // Backward sweep
    PointSensitivityBuilder annuityCashDr = numeraireDerivatives.getSecond();
    double volatilityDFwd = volatilityAdj.getDerivative(0);
    double delta = swaptionVolatilities.priceDelta(expiry, tenor, putCall, strike, forward, volatility);
    double vega = swaptionVolatilities.priceVega(expiry, tenor, putCall, strike, forward, volatility);
    PointSensitivityBuilder priceDr = forwardDr.multipliedBy(delta + vega * volatilityDFwd);
    PointSensitivityBuilder sensitivityDfAfter =
        ratesProviderAfterSwitch.discountFactors(ccy).zeroRatePointSensitivity(switchDate)
            .multipliedBy(-numeraire * dfBefore / (dfAfter * dfAfter) * price * sign);
    PointSensitivityBuilder sensitivityDprice = priceDr.multipliedBy(numeraire * dfBefore / dfAfter * sign);
    PointSensitivityBuilder sensitivityDAnnuity = annuityCashDr.multipliedBy(dfBefore / dfAfter * price * sign);
    PointSensitivityBuilder sensitivityRatesAfter =
        sensitivityDfAfter.combinedWith(sensitivityDprice).combinedWith(sensitivityDAnnuity);
    PointSensitivityBuilder sensitivityDfBefore =
        discountFactorsBeforeSwitch.zeroRatePointSensitivity(switchDate)
            .multipliedBy(numeraire * price * sign / dfAfter);
    return Triple.of(pv, sensitivityDfBefore, sensitivityRatesAfter);
  }

}
