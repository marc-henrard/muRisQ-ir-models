/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.time.LocalDate;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorParameters;

/**
 * Interest rate multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * <p>
 * Implementation: Henrard, Marc. (2016) Rational multi-curve interest rate model: pricing of liquid instruments.
 * <p>
 * The martingales are A(1) = exp(a_1 X_t^(1) - 0.5 a_1^2 t) - 1, A(2) = exp(a_2 X_t^(2)  - 0.5 a_2^2 t) - 1.
 * The Libor process numerator is of the form L(0) + b_1 A(1) + b_2 A(2) 
 * The discount factor process numerator is of the form P(0,T) + b_0(T) A(1)
 * 
 * @author Marc Henrard
 */
public class RationalTwoFactorFormulas {
  
  /** The default instance of the formulas. */
  public final static RationalTwoFactorFormulas DEFAULT = new RationalTwoFactorFormulas();
  
  // Private constructor
  private RationalTwoFactorFormulas(){
  }
  
  /**
   * In the rational two-factor model, for the description of a swap dynamic, the constant, the coefficients of
   * exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...)
   * 
   * @param swap  the swap
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[] swapCoefficients(
      ResolvedSwap swap, 
      RatesProvider rates,
      RationalTwoFactorParameters model) {
    
    double[] c = new double[3];
    ResolvedSwapLeg fixedLeg = RationalNFactorFormulas.fixedLeg(swap);
    ResolvedSwapLeg iborLeg = RationalNFactorFormulas.iborLeg(swap);
    Currency ccy = fixedLeg.getCurrency();
    for (SwapPaymentPeriod period : fixedLeg.getPaymentPeriods()) {
      RatePaymentPeriod ratePeriod = (RatePaymentPeriod) period;
      ImmutableList<RateAccrualPeriod> accrualPeriods = ratePeriod.getAccrualPeriods();
      ArgChecker.isTrue(accrualPeriods.size() == 1, "only one accrual period per payment period supported");
      RateAccrualPeriod accrualPeriod = accrualPeriods.get(0);
      ArgChecker.isTrue(accrualPeriod.getRateComputation() instanceof FixedRateComputation, "fixed");
      FixedRateComputation obs = (FixedRateComputation) accrualPeriod.getRateComputation();
      c[0] += ratePeriod.getNotional() * obs.getRate() * accrualPeriod.getYearFraction()
          * rates.discountFactor(ccy, ratePeriod.getPaymentDate());
      c[1] += ratePeriod.getNotional() * obs.getRate() * accrualPeriod.getYearFraction() 
          * model.b0(ratePeriod.getPaymentDate());
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
      c[2] += ratePeriod.getNotional() * accrualPeriod.getYearFraction()  * 
          model.b2(obs.getObservation());
    }
    c[0] -= c[1] + c[2];
    return c;
  }
  
  /**
   * In the rational two-factor model, for the description of a caplet dynamic, the constant, the coefficients of
   * exp(a_1 X(1) - ...) and the coefficients of exp(a_2 X(2) - ...)
   * <p>
   * From the working paper on implementation:
   * c[1] = b_1(\theta) - K b_0(v)
   * c[2] = b_2(\theta)
   * c[0] = L^j(0; \theta) - K P^D(0,v) - (c[1] + c[2])
   * All to be multiplied by notional and accrual factor
   * 
   * @param caplet  the caplet/floorlet period
   * @param rates  the rates/multi-curve provider
   * @param model  the rational 2-factor model
   * @return the coefficients
   */
  public double[] capletCoefficients(
      IborCapletFloorletPeriod caplet,
      RatesProvider rates,
      RationalTwoFactorParameters model) {

    double strike = caplet.getStrike();
    double factor = Math.abs(caplet.getNotional()) * caplet.getYearFraction();
    IborIndexObservation obs = caplet.getIborRate().getObservation();
    LocalDate maturity = obs.getMaturityDate();
    double[] c = new double[3];
    c[1] = (model.b1(obs) - strike * model.b0(maturity)) * factor;
    c[2] = model.b2(obs) * factor;
    c[0] = (rates.iborIndexRates(obs.getIndex()).rate(obs) -
        strike) * rates.discountFactor(caplet.getCurrency(), maturity) * factor;
    c[0] -= c[1] + c[2];
    return c;
  }
  
}
