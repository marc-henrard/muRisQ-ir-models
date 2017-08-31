/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.rationalmulticurve;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
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
   * Returns the evolved discount factor to a forward date for a given value of the underlying normal random variable.
   * <p>
   * Evolution in the M measure.
   * 
   * @param maturityDate  the maturity date of the discount factor to evolve
   * @param forwardDateTime  the forward date and time to which the discount factor will be evolved
   * @param startingDiscountFactors  the starting discount factors
   * @param x  the value of the standard normal distribution for the evolution
   * @param parameters  the rational model parameters
   * @return  the evolved discount factor
   */
  public double evolvedDiscountFactor(
      LocalDate maturityDate,
      ZonedDateTime forwardDateTime,
      DiscountFactors startingDiscountFactors,
      double x,
      RationalOneFactorParameters parameters){

    double t = parameters.relativeTime(forwardDateTime);
    ArgChecker.isTrue(t >=0, "the evolution time must be positive or zero");
    double a = parameters.a();
    double p0u = startingDiscountFactors.discountFactor(maturityDate);
    LocalDate forwardDate = forwardDateTime.toLocalDate();
    double p0t = startingDiscountFactors.discountFactor(forwardDate);
    double b0u = parameters.b0(maturityDate);
    double b0t = parameters.b0(forwardDate);
    double At = Math.exp(a * Math.sqrt(t) * x - 0.5 * a * a * t) - 1.0d;
    return (p0u + b0u * At) / (p0t + b0t * At);
  }
  
  /**
   * Returns the evolved discount factors to forward dates for a given value of the underlying normal random variable.
   * <p>
   * Evolution in the M measure.
   * 
   * @param maturityDates  the maturity dates of the discount factors to evolve
   * @param forwardDateTime  the forward date and time to which the discount factor will be evolved
   * @param startingDiscountFactors  the starting discount factors
   * @param x  the value of the standard normal distribution for the evolution
   * @param parameters  the rational model parameters
   * @return  the evolved discount factor
   */
  public DoubleArray evolvedDiscountFactor(
      List<LocalDate> maturityDates,
      ZonedDateTime forwardDateTime,
      DiscountFactors startingDiscountFactors,
      double x,
      RationalOneFactorParameters parameters) {

    int nbDates = maturityDates.size();
    double t = parameters.relativeTime(forwardDateTime);
    ArgChecker.isTrue(t >= 0, "the evolution time must be positive or zero");
    double a = parameters.a();
    double[] p0u = new double[nbDates];
    for (int loopdate = 0; loopdate < nbDates; loopdate++) {
      p0u[loopdate] = startingDiscountFactors.discountFactor(maturityDates.get(loopdate));
    }
    LocalDate forwardDate = forwardDateTime.toLocalDate();
    double p0t = startingDiscountFactors.discountFactor(forwardDate);
    double[] b0u = new double[nbDates];
    for (int loopdate = 0; loopdate < nbDates; loopdate++) {
      b0u[loopdate] = parameters.b0(maturityDates.get(loopdate));
    }
    double b0t = parameters.b0(forwardDate);
    double At = Math.exp(a * Math.sqrt(t) * x - 0.5 * a * a * t) - 1.0d;
    double[] df = new double[nbDates];
    double denominator =  1.0D / (p0t + b0t * At);;
    for (int loopdate = 0; loopdate < nbDates; loopdate++) {
      df[loopdate] = (p0u[loopdate] + b0u[loopdate] * At) * denominator;
    }
    return DoubleArray.ofUnsafe(df);
  }

  /**
   * Returns the evolved discount factors to a forward date for a given array of the underlying normal random variable.
   * <p>
   * Evolution in the M measure.
   * 
   * @param maturityDate  the maturity date of the discount factor to evolve
   * @param forwardDateTime  the forward date and time to which the discount factor will be evolved
   * @param startingDiscountFactors  the starting discount factors
   * @param x  the values of the standard normal distribution for the evolution
   * @param parameters  the model parameters
   * @return  the evolved discount factors
   */
  public DoubleArray evolvedDiscountFactor(
      LocalDate maturityDate,
      ZonedDateTime forwardDateTime,
      DiscountFactors startingDiscountFactors,
      DoubleArray x,
      RationalOneFactorParameters parameters) {

    double a = parameters.a();
    int nbX = x.size();
    double p0u = startingDiscountFactors.discountFactor(maturityDate);
    LocalDate forwardDate = forwardDateTime.toLocalDate();
    double p0t = startingDiscountFactors.discountFactor(forwardDate);
    double b0u = parameters.b0(maturityDate);
    double b0t = parameters.b0(forwardDate);
    double t = parameters.relativeTime(forwardDateTime);
    double[] ptu = new double[nbX];
    for (int loopx = 0; loopx < nbX; loopx++) {
      double At = Math.exp(a * Math.sqrt(t) * x.get(loopx) - 0.5 * a * a * t) - 1.0d;
      ptu[loopx] = (p0u + b0u * At) / (p0t + b0t * At);
    }
    return DoubleArray.ofUnsafe(ptu);
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
    
    ResolvedSwapLeg fixedLeg = RationalNFactorFormulas.fixedLeg(swap);
    ResolvedSwapLeg iborLeg = RationalNFactorFormulas.iborLeg(swap);
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

}
