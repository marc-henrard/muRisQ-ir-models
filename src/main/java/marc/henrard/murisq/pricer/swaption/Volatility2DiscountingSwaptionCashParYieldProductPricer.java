/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.value.ValueDerivatives;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.ZeroRateSensitivity;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.SabrSwaptionVolatilities;
import com.opengamma.strata.pricer.swaption.SwaptionVolatilities;
import com.opengamma.strata.product.common.PutCall;
import com.opengamma.strata.product.common.SettlementType;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swaption.CashSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

/**
 * Pricer for swaption with par yield cash settlement based on volatilities.
 * <p>
 * The price is computed for a swaption in which the collateral to a switch date is different to the collateral after,
 * including the collateral of the delivered swaps. Two different discounting curves are used for the different periods.
 * <p>
 * Reference: Henrard, M. (2021) Option pricing with two collateral rates. Forthcoming.
 */
public class Volatility2DiscountingSwaptionCashParYieldProductPricer {

  /**
   * Default implementation.
   */
  public static final Volatility2DiscountingSwaptionCashParYieldProductPricer DEFAULT =
      new Volatility2DiscountingSwaptionCashParYieldProductPricer(DiscountingSwapProductPricer.DEFAULT);

  /**
   * Pricer for {@link SwapProduct}. 
   */
  private final DiscountingSwapProductPricer swapPricer;

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public Volatility2DiscountingSwaptionCashParYieldProductPricer(DiscountingSwapProductPricer swapPricer) {
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
   * @param discountFactorsBeforeSwitch  the discount factors used to compute discounting from today to switch
   * @param switchDate  the date for the collateral rate switch
   * @param ratesProviderAfterSwitch  the rates provider used to compute the value at expiry
   * @param swaptionVolatilities  the volatilities
   * @return the present value
   */
  public CurrencyAmount presentValue(
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
      return CurrencyAmount.of(ccy, 0d);
    }
    double forward = swapPricer.parRate(underlying, ratesProviderAfterSwitch);
    double numeraire = numeraire(swaption, fixedLeg, forward, ratesProviderAfterSwitch);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProviderAfterSwitch);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProviderAfterSwitch, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    double price = numeraire * swaptionVolatilities.price(expiry, tenor, putCall, strike, forward, volatility);
    double df1 = discountFactorsBeforeSwitch.discountFactor(switchDate);
    double df2 = ratesProviderAfterSwitch.discountFactor(ccy, switchDate);
    return CurrencyAmount.of(ccy, df1 / df2 * price * swaption.getLongShort().sign());    
  }
  
  /**
   * Computes the implied volatility of the swaption.
   * 
   * @param swaption  the swaption
   * @param ratesProvider  the rates provider
   * @param swaptionVolatilities  the volatilities
   * @return the implied volatility associated with the swaption
   */
  public double impliedVolatility(
      ResolvedSwaption swaption,
      RatesProvider ratesProviderAfterSwitch,
      SabrSwaptionVolatilities swaptionVolatilities) {

    validate(swaption, ratesProviderAfterSwitch, swaptionVolatilities);
    double expiry = swaptionVolatilities.relativeTime(swaption.getExpiry());
    ResolvedSwap underlying = swaption.getUnderlying();
    ResolvedSwapLeg fixedLeg = fixedLeg(underlying);
    ArgChecker.isTrue(expiry >= 0d, "Option must be before expiry to compute an implied volatility");
    double forward = swapPricer.parRate(underlying, ratesProviderAfterSwitch);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProviderAfterSwitch);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProviderAfterSwitch, pvbp);
    double tenor = swaptionVolatilities.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double volatility = swaptionVolatilities.volatility(expiry, tenor, strike, forward);
    return volatility;
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
    ArgChecker.isTrue(swaption.getSwaptionSettlement().getSettlementType().equals(SettlementType.CASH),
        "Swaption must be physical settlement");
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the numeraire, used to multiply the results.
   * 
   * @param swaption  the swap
   * @param fixedLeg  the fixed leg
   * @param forward  the forward rate
   * @param multicurve  the rates provider
   * @return the numeraire
   */
  public double numeraire(
      ResolvedSwaption swaption,
      ResolvedSwapLeg fixedLeg,
      double forward,
      RatesProvider multicurve) {

    double annuityCash = swapPricer.getLegPricer().annuityCash(fixedLeg, forward);
    CashSwaptionSettlement cashSettlement = (CashSwaptionSettlement) swaption.getSwaptionSettlement();
    double discountSettlement =
        multicurve.discountFactor(fixedLeg.getCurrency(), cashSettlement.getSettlementDate());
    return Math.abs(annuityCash * discountSettlement);
  }

  /**
   * Calculate the numeraire and its sensitivity to curves.
   * 
   * @param swaption  the swap
   * @param fixedLeg  the fixed leg
   * @param forward  the forward rate
   * @param forwardSensitivities  the sensitivities of the forward to the curves
   * @param multicurve  the rates provider
   * @return the numeraire and its sensitivities
   */
  public Pair<Double, PointSensitivityBuilder> numeraireSensitivity(
      ResolvedSwaption swaption,
      ResolvedSwapLeg fixedLeg,
      double forward,
      PointSensitivityBuilder forwardSensitivities,
      RatesProvider multicurve) {

    DiscountFactors discountFactors = multicurve.discountFactors(fixedLeg.getCurrency());
    ValueDerivatives annuityCashDerivatives = swapPricer.getLegPricer().annuityCashDerivative(fixedLeg, forward);
    double annuityCash = annuityCashDerivatives.getValue();
    CashSwaptionSettlement cashSettlement = (CashSwaptionSettlement) swaption.getSwaptionSettlement();
    double discountSettlement = discountFactors.discountFactor(cashSettlement.getSettlementDate());
    double numeraire = Math.abs(annuityCash * discountSettlement);
    // Backward sweep
    double sign = Math.signum(annuityCash * discountSettlement);
    double annuityCashBar = discountSettlement * sign;
    double discountSettlementBar = annuityCash * sign;
    ZeroRateSensitivity discountSettlementDr =
        discountFactors.zeroRatePointSensitivity(cashSettlement.getSettlementDate());
    PointSensitivityBuilder sensitivity = discountSettlementDr.multipliedBy(discountSettlementBar);
    sensitivity = sensitivity.combinedWith(
        forwardSensitivities.multipliedBy(annuityCashDerivatives.getDerivative(0) * annuityCashBar));
    return Pair.of(numeraire, sensitivity);
  }

}
