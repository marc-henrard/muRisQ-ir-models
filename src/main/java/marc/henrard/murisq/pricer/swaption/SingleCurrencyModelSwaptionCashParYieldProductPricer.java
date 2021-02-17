/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import org.apache.poi.ss.formula.eval.NotImplementedException;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.common.PutCall;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.RateComputation;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swaption.CashSwaptionSettlement;
import com.opengamma.strata.product.swaption.CashSwaptionSettlementMethod;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.bachelier.BachelierFormula;
import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;

/**
 * Price of cash settlement par yield European swaptions in single currency models.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelSwaptionCashParYieldProductPricer {

  /** The pricer used for swap measures. */
  private static final DiscountingSwapProductPricer PRICER_SWAP = 
      DiscountingSwapProductPricer.DEFAULT;
  
  /**
   * Computes the present value of a swaption in a given model.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the product to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the present value of the swaption product
   */
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {
    
    throw new NotImplementedException("present value is implemented in model specific extensions");
  }

  /**
   * Computes the implied volatility in the Bachelier model for the "standard market model".
   * <p>
   * The implied volatility for the given price is computed.
   * The Bachelier formula inversion is done using {@link BachelierFormula#impliedVolatilityApproxLfk4}.
   * 
   * @param swaption  the product to price
   * @param multicurve  the rates provider
   * @param price  the swaption price
   * @param timeToEpiry  the time to expiry as computed by the model
   * @return the implied volatility in the Bachelier model
   */
  public double impliedVolatilityBachelier(
      ResolvedSwaption swaption, 
      RatesProvider multicurve, 
      double price,
      double timeToEpiry) {

    validate(swaption);
    double parRate = PRICER_SWAP.parRate(swaption.getUnderlying(), multicurve);
    ResolvedSwapLeg legFixed = swaption.getUnderlying().getLegs(SwapLegType.FIXED).get(0);
    double strike = calculateStrike(legFixed);
    double numeraire = calculateNumeraire(swaption, legFixed, parRate, multicurve);
    return BachelierFormula.impliedVolatilityApproxLfk4(
        Math.abs(price), parRate, strike, timeToEpiry, Math.abs(numeraire),
        isReceiver(swaption) ? PutCall.PUT : PutCall.CALL);
  }

  /**
   * Validates that the rates and volatilities providers are coherent and that the swaption is acceptable.
   * 
   * @param rates  the rate provider
   * @param swaption  the swaption
   * @param model  the rational one-factor model
   */
  protected void validate(
      RatesProvider rates, 
      ResolvedSwaption swaption, 
      SingleCurrencyModelParameters model) {
    
    ArgChecker.isTrue(model.getValuationDate().equals(rates.getValuationDate()),
        "volatility and rate data should be for the same date");
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), 
        "underlying swap should be single currency");
    ArgChecker.isTrue((swaption.getSwaptionSettlement() instanceof CashSwaptionSettlement) 
        || (((CashSwaptionSettlement)swaption.getSwaptionSettlement()).getMethod().equals(CashSwaptionSettlementMethod.PAR_YIELD)), 
        "swaption should be cash par yield settlement");
  }

  /**
   * Validates that the rates and volatilities providers are coherent and that the swaption is acceptable.
   * 
   * @param swaption  the swaption
   */
  protected void validate(ResolvedSwaption swaption) {
    
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), 
        "underlying swap should be single currency");
    ArgChecker.isTrue((swaption.getSwaptionSettlement() instanceof CashSwaptionSettlement) 
        || (((CashSwaptionSettlement)swaption.getSwaptionSettlement()).getMethod().equals(CashSwaptionSettlementMethod.PAR_YIELD)), 
        "swaption should be cash par yield settlement");
  }
  
  /**
   * Returns true is the underlying swap is a receiver and false otherwise.
   * 
   * @param swaption  the swaption
   * @return the receiver flag
   */
  private boolean isReceiver(ResolvedSwaption swaption) {
    return swaption.getUnderlying().getLegs(SwapLegType.FIXED).get(0).getPayReceive()
        .equals(PayReceive.RECEIVE);
  }

  /**
   * Calculates the numeraire, used to multiply the results.
   * 
   * @param swaption  the swap
   * @param fixedLeg  the fixed leg
   * @param forward  the forward rate
   * @param ratesProvider  the rates provider
   * @return the numeraire
   */
  protected double calculateNumeraire(
      ResolvedSwaption swaption,
      ResolvedSwapLeg fixedLeg,
      double forward,
      RatesProvider ratesProvider) {

    double annuityCash = PRICER_SWAP.getLegPricer().annuityCash(fixedLeg, forward);
    CashSwaptionSettlement cashSettlement = (CashSwaptionSettlement) swaption.getSwaptionSettlement();
    double discountSettle = ratesProvider.discountFactor(fixedLeg.getCurrency(), cashSettlement.getSettlementDate());
    return Math.abs(annuityCash * discountSettle);
  }

  /**
   * Calculates the strike.
   * 
   * @param fixedLeg  the fixed leg
   * @return the strike
   */
  protected double calculateStrike(ResolvedSwapLeg fixedLeg) {
    SwapPaymentPeriod paymentPeriod = fixedLeg.getPaymentPeriods().get(0);
    ArgChecker.isTrue(paymentPeriod instanceof RatePaymentPeriod, "Payment period must be RatePaymentPeriod");
    RatePaymentPeriod ratePaymentPeriod = (RatePaymentPeriod) paymentPeriod;
    // compounding is caught when par rate is computed
    RateComputation rateComputation = ratePaymentPeriod.getAccrualPeriods().get(0).getRateComputation();
    ArgChecker.isTrue(rateComputation instanceof FixedRateComputation, "Swap leg must be fixed leg");
    return ((FixedRateComputation) rateComputation).getRate();
  }

}
