/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import static java.time.temporal.ChronoUnit.DAYS;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.common.PutCall;

import marc.henrard.risq.model.bachelier.BachelierFormula;
import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;

/**
 * Price of caplet/floorlet in the single currency model.
 * 
 * @author Marc Henrard
 */
public abstract class SingleCurrencyModelCapletFloorletPeriodPricer {
  
  /**
   * Computes the present value of a caplet/floorlet in the rational model.
   * <p>
   * The result is expressed using the currency of the caplet/floorlet.
   * <p>
   * The pricer accept only in fixing in advance, paying in arrears caplet/floorlet with payment date
   * equal to the maturity date of the underlying Ibor rate.
   * 
   * @param caplet  the caplet/floorlet period to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the present value of the caplet/floorlet
   */
  public abstract CurrencyAmount presentValue(
      IborCapletFloorletPeriod caplet,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model);
  
  /**
   * Computes the implied volatility in the Black model.
   * <p>
   * The swaption price is computed in the rational model and the implied volatility for that price is computed. 
   * The implied volatility may failed if the model price is outside the Black possible prices.
   * 
   * @param caplet  the caplet/floorlet period to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the implied volatility in the Black model
   */
  public double impliedVolatilityBlack(
      IborCapletFloorletPeriod caplet, 
      RatesProvider multicurve, 
      SingleCurrencyModelParameters model) {
    
    ArgChecker.notNegative(caplet.getStrike(), "for Black implied volatility, strike must be non-negative");
    double price = presentValue(caplet, multicurve, model).getAmount();
    double forwardRate = 
        multicurve.iborIndexRates(caplet.getIndex()).rate(caplet.getIborRate().getObservation());
    ArgChecker.notNegative(forwardRate, "for Black implied volatility, forward rate must be non-negative");
    double timeToEpiry = model.relativeTime(caplet.getFixingDateTime());
    double strike = caplet.getStrike();
    double numeraire = multicurve.discountFactor(caplet.getCurrency(), caplet.getPaymentDate()) 
        * caplet.getYearFraction() * caplet.getNotional(); // DF * AF * notional
    return BlackFormulaRepository.impliedVolatility(
        Math.abs(price / numeraire), forwardRate, strike, timeToEpiry, caplet.getPutCall().equals(PutCall.CALL));
  }

  /**
   * Computes the implied volatility in the Bachelier model from a model.
   * <p>
   * The caplet/floorlet price is computed in the rational model and the implied volatility for that price is computed.
   * The Bachelier formula inversion is done using {@link BachelierFormula#impliedVolatilityApproxLfk4}.
   * 
   * @param caplet  the caplet/floorlet period to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the implied volatility in the Bachelier model
   */
  public double impliedVolatilityBachelier(
      IborCapletFloorletPeriod caplet, 
      RatesProvider multicurve, 
      SingleCurrencyModelParameters model) {
    
    double price = presentValue(caplet, multicurve, model).getAmount();
    double timeToEpiry = model.relativeTime(caplet.getFixingDateTime());
    return impliedVolatilityBachelier(caplet, multicurve, price, timeToEpiry);
  }

  /**
   * Computes the implied volatility in the Bachelier model from a price.
   * <p>
   * The Bachelier formula inversion is done using {@link BachelierFormula#impliedVolatilityApproxLfk4}.
   * 
   * @param caplet  the caplet/floorlet period to price
   * @param multicurve  the rates provider
   * @param price  the swaption price
   * @param timeToEpiry  the time to expiry as computed by the model
   * @return the implied volatility in the Bachelier model
   */
  public double impliedVolatilityBachelier(
      IborCapletFloorletPeriod caplet, 
      RatesProvider multicurve, 
      double price,
      double timeToEpiry) {
    
    double forwardRate = 
        multicurve.iborIndexRates(caplet.getIndex()).rate(caplet.getIborRate().getObservation());
    double strike = caplet.getStrike();
    double numeraire = multicurve.discountFactor(caplet.getCurrency(), caplet.getPaymentDate()) 
        * caplet.getYearFraction() * caplet.getNotional(); // DF * AF * notional
    return BachelierFormula.impliedVolatilityApproxLfk4(
        Math.abs(price), forwardRate, strike, timeToEpiry, Math.abs(numeraire), caplet.getPutCall());
  }

  /**
   * Validates that the rates, caplet and volatilities providers are coherent.
   * 
   * @param rates  the rate provider
   * @param caplet  the caplet/floorlet period to price
   * @param model  the rational one-factor model
   */
  protected void validate(
      RatesProvider rates, 
      IborCapletFloorletPeriod caplet, 
      SingleCurrencyModelParameters model) {
    
    ArgChecker.isTrue(model.getValuationDate().equals(rates.getValuationDate()),
        "volatility and rate data should be for the same date, have {} and {}",
        model.getValuationDate(), rates.getValuationDate());
    ArgChecker.isTrue(caplet.getCurrency().equals(model.getCurrency()), 
        "underlying caplet/floorlet should be in the currency of the model, have {} and {}", 
        caplet.getCurrency(), model.getCurrency());
    ArgChecker.isTrue(caplet.getEndDate().equals(caplet.getPaymentDate()),
        "caplet end date and payment date should be for the same date, have {} and {}",
        caplet.getEndDate(), caplet.getPaymentDate());
    caplet.getUnadjustedEndDate();
    ArgChecker.isTrue(DAYS.between(caplet.getEndDate(), caplet.getIborRate().getObservation().getMaturityDate()) < 7,
        "caplet end date and Ibor maturity date should be less than 7 days appart, have {} and {}",
        caplet.getEndDate(), caplet.getIborRate().getObservation().getMaturityDate());
  }

}
