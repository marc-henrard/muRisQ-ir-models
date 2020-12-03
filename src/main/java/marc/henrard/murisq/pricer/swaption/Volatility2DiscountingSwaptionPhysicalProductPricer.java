/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SwaptionVolatilities;
import com.opengamma.strata.product.common.PutCall;
import com.opengamma.strata.product.common.SettlementType;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

/**
 * Pricer for swaption with physical settlement based on volatilities.
 * <p>
 * The price is computed for a swaption in which the collateral to a switch date is different to the collateral after,
 * including the collateral of the delivered swaps. Two different discounting curves are used for the different periods.
 * <p>
 * Reference: Henrard, M. (2020) Option pricing with two collateral rates. Forthcoming.
 */
public class Volatility2DiscountingSwaptionPhysicalProductPricer {

  /**
   * Default implementation.
   */
  public static final Volatility2DiscountingSwaptionPhysicalProductPricer DEFAULT =
      new Volatility2DiscountingSwaptionPhysicalProductPricer(DiscountingSwapProductPricer.DEFAULT);

  /**
   * Pricer for {@link SwapProduct}. 
   */
  private final DiscountingSwapProductPricer swapPricer;

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public Volatility2DiscountingSwaptionPhysicalProductPricer(DiscountingSwapProductPricer swapPricer) {
    this.swapPricer = ArgChecker.notNull(swapPricer, "swapPricer");
  }
  
  /**
   * Returns the swap pricer.
   * 
   * @return the swap pricer
   */
  protected DiscountingSwapProductPricer getSwapPricer() {
    return swapPricer;
  }

  /**
   * Calculates the present value of the swaption.
   * <p>
   * Approximation with ratio of discounting but no convexity adjustments.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider used to compute the value at expiry
   * @param switchDate  the date for the collateral rate switch
   * @param discountFactors  the discount factors used to compute discounting from expiry to today
   * @param swaptionVolatilities  the volatilities
   * @return the present value
   */
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider ratesProvider,
      LocalDate switchDate,
      DiscountFactors discountFactors,
      SwaptionVolatilities swaptionVolatilities) {

    validate(swaption, ratesProvider, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(swaption.getExpiry());
    ResolvedSwap underlying = swaption.getUnderlying();
    ResolvedSwapLeg fixedLeg = fixedLeg(underlying);
    Currency ccy = fixedLeg.getCurrency();
    if (expiry < 0d) { // Option has expired already
      return CurrencyAmount.of(ccy, 0d);
    }
    double forward = swapPricer.parRate(underlying, ratesProvider);
    double pvbp = swapPricer.getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double price = Math.abs(pvbp) * swaptionVolatilities.price(expiry, tenor, putCall, strike, forward, volatility);
    double df1 = discountFactors.discountFactor(switchDate);
    double df2 = ratesProvider.discountFactor(ccy, switchDate);
    return CurrencyAmount.of(ccy, df1 / df2 * price * swaption.getLongShort().sign());
  }
  

  /**
   * Checks that there is exactly one fixed leg and returns it.
   * 
   * @param swap  the swap
   * @return the fixed leg
   */
  protected ResolvedSwapLeg fixedLeg(ResolvedSwap swap) {
    ArgChecker.isFalse(swap.isCrossCurrency(), "Swap must be single currency");
    // find fixed leg
    List<ResolvedSwapLeg> fixedLegs = swap.getLegs(SwapLegType.FIXED);
    if (fixedLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a fixed leg");
    }
    return fixedLegs.get(0);
  }

  /**
   * Validates that the rates and volatilities providers are coherent
   * and that the swaption is single currency physical.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   */
  protected void validate(
      ResolvedSwaption swaption,
      RatesProvider ratesProvider,
      SwaptionVolatilities swaptionVolatilities) {

    ArgChecker.isTrue(swaptionVolatilities.getValuationDate().equals(ratesProvider.getValuationDate()),
        "Volatility and rate data must be for the same date");
    validateSwaption(swaption);
  }

  /**
   * Validates that the swaption is single currency physical.
   * 
   * @param swaption  the swaption
   */
  protected void validateSwaption(ResolvedSwaption swaption) {
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), "Underlying swap must be single currency");
    ArgChecker.isTrue(swaption.getSwaptionSettlement().getSettlementType().equals(SettlementType.PHYSICAL),
        "Swaption must be physical settlement");
  }

}
