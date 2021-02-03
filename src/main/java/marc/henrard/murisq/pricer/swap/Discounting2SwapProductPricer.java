/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.swap.ResolvedSwap;

/**
 * Pricer for swaps.
 * <p>
 * The price is computed for a swap in which the collateral to a switch date is different to the collateral after. 
 * Two different discounting curves are used for the different periods. The swap effective date must be after the
 * switch date.
 * <p>
 * Reference: Henrard, M. (2021) Option pricing with two collateral rates. Forthcoming.
 */
public class Discounting2SwapProductPricer {

  /**
   * Default implementation.
   */
  public static final Discounting2SwapProductPricer DEFAULT =
      new Discounting2SwapProductPricer(DiscountingSwapProductPricer.DEFAULT);

  /**
   * Pricer for {@link SwapProduct}. 
   */
  private final DiscountingSwapProductPricer swapPricer;

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public Discounting2SwapProductPricer(DiscountingSwapProductPricer swapPricer) {
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
   * Calculates the present value of the swap.
   * <p>
   * Approximation with ratio of discounting but no convexity adjustments. The currency of the discounting before the 
   * switch must be the currency of the swap.
   * 
   * @param swap  the swap
   * @param discountFactorsBeforeSwitch  the discount factors used to compute discounting from today to switch
   * @param switchDate  the date for the collateral rate switch
   * @param multicurveAfterSwitch  the multi-curve used to compute the value after the switch
   * @return the present value
   */
  public MultiCurrencyAmount presentValue(
      ResolvedSwap swap,
      DiscountFactors discountFactorsBeforeSwitch,
      LocalDate switchDate,
      RatesProvider multicurveAfterSwitch) {

    ArgChecker.isFalse(swap.getStartDate().isBefore(switchDate),
        "Swap start date must be on or after switch date, have start on {} and switch on ", swap.getStartDate(),
        switchDate);
    ArgChecker.isTrue(swap.allPaymentCurrencies().size() == 1, "Swap must be single currency");
    Currency ccy = discountFactorsBeforeSwitch.getCurrency();
    ArgChecker.isTrue(swap.allPaymentCurrencies().contains(ccy), "Discounting must be in the same currency as swap");
    MultiCurrencyAmount pvDsc2 = swapPricer.presentValue(swap, multicurveAfterSwitch);
    double df1 = discountFactorsBeforeSwitch.discountFactor(switchDate);
    double df2 = multicurveAfterSwitch.discountFactor(ccy, switchDate);
    return pvDsc2.multipliedBy(df1 / df2);
  }

  /**
   * Calculates the present value  and present value sensitivity of the swap product to the rate curves.
   * <p>
   * The sensitivity is divided into the sensitivity to the first discounting and 
   * the sensitivity to the second discounting and forward curves.
   * 
   * @param swap  the swap
   * @param discountFactorsBeforeSwitch  the discount factors used to compute discounting from today to switch
   * @param switchDate  the date for the collateral rate switch
   * @param multicurveAfterSwitch  the multi-curve used to compute the value after the switch
   * @return the present value and the two sensitivities
   */
  public Triple<MultiCurrencyAmount, PointSensitivityBuilder, PointSensitivityBuilder>
      presentValueSensitivityRatesStickyModel(
          ResolvedSwap swap,
          DiscountFactors discountFactorsBeforeSwitch,
          LocalDate switchDate,
          RatesProvider multicurveAfterSwitch) {

    ArgChecker.isFalse(swap.getStartDate().isBefore(switchDate),
        "Swap start date must be on or after switch date, have start on {} and switch on ", swap.getStartDate(),
        switchDate);
    ArgChecker.isTrue(swap.allPaymentCurrencies().size() == 1, "Swap must be single currency");
    Currency ccy = discountFactorsBeforeSwitch.getCurrency();
    DiscountFactors discountFactorsAfterSwitch = multicurveAfterSwitch.discountFactors(ccy);
    MultiCurrencyAmount pvDscAfter = swapPricer.presentValue(swap, multicurveAfterSwitch);
    double pvDscAfterCcy = pvDscAfter.getAmount(ccy).getAmount();
    double dfBefore = discountFactorsBeforeSwitch.discountFactor(switchDate);
    double dfAfter = discountFactorsAfterSwitch.discountFactor(switchDate);
    MultiCurrencyAmount pv = pvDscAfter.multipliedBy(dfBefore / dfAfter);
    // Backward sweep
    double dfBeforeBar = pvDscAfterCcy / dfAfter;
    PointSensitivityBuilder sensitivityDfBefore =
        discountFactorsBeforeSwitch.zeroRatePointSensitivity(switchDate);
    PointSensitivityBuilder sensitivityRatesBefore = sensitivityDfBefore.multipliedBy(dfBeforeBar);
    double dfAfterBar = pvDscAfterCcy * -dfBefore / (dfAfter * dfAfter);
    PointSensitivityBuilder sensitivityDfAfter =
        discountFactorsAfterSwitch.zeroRatePointSensitivity(switchDate);
    PointSensitivityBuilder pvDscAfterDr = swapPricer.presentValueSensitivity(swap, multicurveAfterSwitch);
    PointSensitivityBuilder sensitivityRatesAfter = sensitivityDfAfter.multipliedBy(dfAfterBar)
        .combinedWith(pvDscAfterDr.multipliedBy(dfBefore / dfAfter));
    return Triple.of(pv, sensitivityRatesBefore, sensitivityRatesAfter);
  }

}
