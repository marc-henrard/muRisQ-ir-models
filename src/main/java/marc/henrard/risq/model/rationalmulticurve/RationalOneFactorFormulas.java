/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.risq.model.rationalmulticurve.RationalOneFactorParameters;

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
 * Formulas related to the one factor model.
 * 
 * @author Marc Henrard
 */
public class RationalOneFactorFormulas {
  
  /** The default instance of the formulas. */
  public final static RationalOneFactorFormulas DEFAULT = new RationalOneFactorFormulas();
  
  // Private constructor
  private RationalOneFactorFormulas(){
  }
  
  /**
   * In the rational one factors model, for the description of a swap dynamic, the constant and the coefficients of
   * exp(a_1 X(1) - ...).
   * 
   * @param swap  the swap
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 1-factor model
   * @return the coefficients
   */
  public double[] swapCoefficients(
      ResolvedSwap swap, 
      RatesProvider rates, 
      RationalOneFactorParameters model) {
    
    ResolvedSwapLeg fixedLeg = fixedLeg(swap);
    ResolvedSwapLeg iborLeg = iborLeg(swap);
    Currency ccy = fixedLeg.getCurrency();
    double[] c = new double[2];
    for(SwapPaymentPeriod period: fixedLeg.getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof FixedRateComputation, "fixed");
      FixedRateComputation obs = (FixedRateComputation) accrualPeriod.getRateComputation();
      c[0] += ratePeriod.getNotional() * obs.getRate() * accrualPeriod.getYearFraction() 
          * rates.discountFactor(ccy, ratePeriod.getPaymentDate());
      c[1] += ratePeriod.getNotional() * obs.getRate() * accrualPeriod.getYearFraction() *
          model.b0(ratePeriod.getPaymentDate());
    }
    for (SwapPaymentPeriod period : iborLeg.getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof IborRateComputation, "ibor");
      IborRateComputation obs = (IborRateComputation) accrualPeriod.getRateComputation();
      double df = rates.discountFactor(ccy, ratePeriod.getPaymentDate());
      c[0] += ratePeriod.getNotional() * accrualPeriod.getYearFraction()
          * rates.iborIndexRates(obs.getIndex()).rate(obs.getObservation()) * df;
      c[1] += ratePeriod.getNotional() * accrualPeriod.getYearFraction() *
          model.b1(obs.getObservation());
    }
    c[0] -= c[1];
    return c;
  }
  
  //-----------------------------------------------------------------------

  // check that one leg is fixed and return it
  private static ResolvedSwapLeg fixedLeg(ResolvedSwap swap) {
    // find fixed leg
    List<ResolvedSwapLeg> fixedLegs = swap.getLegs(SwapLegType.FIXED);
    if (fixedLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a fixed leg");
    }
    return fixedLegs.get(0);
  }

  // check that one leg is ibor and return it
  private static ResolvedSwapLeg iborLeg(ResolvedSwap swap) {
    // find ibor leg
    List<ResolvedSwapLeg> iborLegs = swap.getLegs(SwapLegType.IBOR);
    if (iborLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a ibor leg");
    }
    return iborLegs.get(0);
  }

}
