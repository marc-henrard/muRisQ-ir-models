/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;

import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.pricer.swaption.VolatilitySwaptionPhysicalProductPricer;
import com.opengamma.strata.product.common.PutCall;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.Swap;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.bachelier.BachelierFormula;

/**
 * To replace the original Strata class {@link NormalSwaptionPhysicalProductPricer}.
 * There is a bug in the NormalFormulaRepository.impliedVolatility for negative or close to 0 forwards.
 */
public class NormalSwaptionPhysicalProductPricer2
    extends VolatilitySwaptionPhysicalProductPricer {

  /**
   * Default implementation.
   */
  public static final NormalSwaptionPhysicalProductPricer2 DEFAULT =
      new NormalSwaptionPhysicalProductPricer2(DiscountingSwapProductPricer.DEFAULT);

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public NormalSwaptionPhysicalProductPricer2(DiscountingSwapProductPricer swapPricer) {
    super(swapPricer);
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the implied normal volatility from the present value of a swaption.
   * <p>
   * The guess volatility for the start of the root-finding process is 1%.
   * 
   * @param swaption  the product
   * @param ratesProvider  the rates provider
   * @param dayCount  the day-count used to estimate the time between valuation date and swaption expiry
   * @param presentValue  the present value of the swaption product
   * @return the implied volatility associated with the present value
   */
  public double impliedVolatilityFromPresentValue(
      ResolvedSwaption swaption,
      RatesProvider ratesProvider,
      DayCount dayCount,
      double presentValue) {

    double sign = swaption.getLongShort().sign();
    ArgChecker.isTrue(presentValue * sign > 0, "Present value sign must be in line with the option Long/Short flag ");
    validateSwaption(swaption);
    LocalDate valuationDate = ratesProvider.getValuationDate();
    LocalDate expiryDate = swaption.getExpiryDate();
    ArgChecker.isTrue(expiryDate.isAfter(valuationDate),
        "Expiry must be after valuation date to compute an implied volatility");
    double expiry = dayCount.yearFraction(valuationDate, expiryDate);
    ResolvedSwap underlying = swaption.getUnderlying();
    ResolvedSwapLeg fixedLeg = fixedLeg(underlying);
    double forward = getSwapPricer().parRate(underlying, ratesProvider);
    double pvbp = getSwapPricer().getLegPricer().pvbp(fixedLeg, ratesProvider);
    double numeraire = Math.abs(pvbp);
    double strike = getSwapPricer().getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    PutCall putCall = PutCall.ofPut(fixedLeg.getPayReceive().isReceive());
    return BachelierFormula
        .impliedVolatilityApproxLfk4(Math.abs(presentValue), forward, strike, expiry, numeraire, putCall);
  }

}
