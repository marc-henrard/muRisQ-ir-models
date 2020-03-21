/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.capfloor;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
import com.opengamma.strata.pricer.ZeroRateSensitivity;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;

/**
 * Pricer for IBOR caplet/floorlet in Hull-White one factor model with piecewise constant volatility
 * and deterministic multiplicative basis between discounting and IBOR forwards.
 * <p>
 * The payment date can be different from the IBOR end date.
 * <p>
 * Reference for deterministic multiplicative basis: 
 *   Henrard, M. "The Irony in the derivatives discounting Part II: the crisis", Wilmott Journal, 2010, 2, 301-316
 */
public class HullWhiteCapletFloorletPeriodPricer{

  /**
   * Normal distribution function.
   */
  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

  /**
   * Default implementation.
   */
  public static final HullWhiteCapletFloorletPeriodPricer DEFAULT = new HullWhiteCapletFloorletPeriodPricer();

  /**
   * Creates an instance.
   */
  public HullWhiteCapletFloorletPeriodPricer() {
  }

  /**
   * Calculates the present value of the caplet/floorlet period.
   * <p>
   * The result is expressed using the currency of the caplet/floorlet.
   * 
   * @param caplet  the product
   * @param ratesProvider  the rates provider
   * @param hwProvider  the Hull-White model parameter provider
   * @return the present value
   */
  public CurrencyAmount presentValue(
      IborCapletFloorletPeriod caplet, 
      RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    validate(ratesProvider, hwProvider);
    Currency ccy = caplet.getCurrency();
    LocalDate expiryDate = caplet.getFixingDate();
    if (expiryDate.isBefore(ratesProvider.getValuationDate())) { // Option has expired already
      return CurrencyAmount.of(ccy, 0d);
    }
    double deltaIbor = caplet.getIborRate().getYearFraction();
    double deltaPay = caplet.getYearFraction();
    IborIndexObservation obsIbor = caplet.getIborRate().getObservation();
    double investmentFactorIbor = 1.0 + deltaIbor * ratesProvider.iborIndexRates(caplet.getIndex()).rate(obsIbor);
    double onePlusDeltaK = 1.0 + deltaIbor * caplet.getStrike();
    LocalDate[] paymentDates = new LocalDate[2];
    paymentDates[0] = obsIbor.getEffectiveDate();
    paymentDates[1] = caplet.getPaymentDate();
    double[] alpha = new double[2];
    for (int loopcf = 0; loopcf < 2; loopcf++) {
      alpha[loopcf] = hwProvider
          .alpha(ratesProvider.getValuationDate(), expiryDate, paymentDates[loopcf], obsIbor.getMaturityDate());
    }
    double discountFactorPayment = ratesProvider.discountFactor(ccy, caplet.getPaymentDate());
    double kappa = ( Math.log(investmentFactorIbor / onePlusDeltaK) - 0.5 * alpha[0] * alpha[0]) / alpha[0];
    double pv = 0.0;
    if (caplet.getPutCall().isCall()) {
      pv = investmentFactorIbor * NORMAL.getCDF(kappa + alpha[0] + alpha[1]) * Math.exp(alpha[0] * alpha[1]) 
          - onePlusDeltaK * NORMAL.getCDF(kappa + alpha[1]);
    } else {
      pv = onePlusDeltaK * NORMAL.getCDF(- kappa - alpha[1])
          - investmentFactorIbor * NORMAL.getCDF(- kappa - alpha[0] - alpha[1]) * Math.exp(alpha[0] * alpha[1]);
    }
    pv *= discountFactorPayment * deltaPay / deltaIbor; // adjustment for accrual factor
    return CurrencyAmount.of(ccy, pv * caplet.getNotional());
    // Note: The formula could be simplified if caplet.getPaymentDate() == caplet.getEndDate()
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the currency exposure of the caplet/floorlet period.
   * 
   * @param caplet  the product
   * @param ratesProvider  the rates provider
   * @param hwProvider  the Hull-White model parameter provider
   * @return the currency exposure
   */
  public MultiCurrencyAmount currencyExposure(
      IborCapletFloorletPeriod caplet, 
      RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    return MultiCurrencyAmount.of(presentValue(caplet, ratesProvider, hwProvider));
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the caplet/floorlet period.
   * <p>
   * The present value sensitivity of the product is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param caplet  the product
   * @param ratesProvider  the rates provider
   * @param hwProvider  the Hull-White model parameter provider
   * @return the point sensitivity to the rate curves
   */
  public PointSensitivityBuilder presentValueSensitivityRates(
      IborCapletFloorletPeriod caplet, 
      RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    validate(ratesProvider, hwProvider);
    Currency ccy = caplet.getCurrency();
    LocalDate expiryDate = caplet.getFixingDate();
    if (expiryDate.isBefore(ratesProvider.getValuationDate())) { // Option has expired already
      return PointSensitivityBuilder.none();
    }
    double deltaIbor = caplet.getIborRate().getYearFraction();
    double deltaPay = caplet.getYearFraction();
    IborIndexObservation obsIbor = caplet.getIborRate().getObservation();
    double investmentFactorIbor = 1.0 + deltaIbor * ratesProvider.iborIndexRates(caplet.getIndex()).rate(obsIbor);
    double onePlusDeltaK = 1.0 + deltaIbor * caplet.getStrike();
    LocalDate[] paymentDates = new LocalDate[2];
    paymentDates[0] = obsIbor.getEffectiveDate();
    paymentDates[1] = caplet.getPaymentDate();
    double[] alpha = new double[2];
    for (int loopcf = 0; loopcf < 2; loopcf++) {
      alpha[loopcf] = hwProvider
          .alpha(ratesProvider.getValuationDate(), expiryDate, paymentDates[loopcf], obsIbor.getMaturityDate());
    }
    double discountFactorPayment = ratesProvider.discountFactor(ccy, caplet.getPaymentDate());
    double kappa = ( Math.log(investmentFactorIbor / onePlusDeltaK) - 0.5 * alpha[0] * alpha[0]) / alpha[0];
    double expalpha01 = Math.exp(alpha[0] * alpha[1]);
    double normalKappaAlpha0Alpha1 = NORMAL.getCDF(kappa + alpha[0] + alpha[1]);
    double normalKappaAlpha1 = NORMAL.getCDF(kappa + alpha[1]);
    double pv = 0.0;
    if (caplet.getPutCall().isCall()) {
      pv = investmentFactorIbor * normalKappaAlpha0Alpha1 * expalpha01 - onePlusDeltaK * normalKappaAlpha1;
    } else {
      pv = onePlusDeltaK * (1 - normalKappaAlpha1) - investmentFactorIbor * (1 - normalKappaAlpha0Alpha1) * expalpha01;
    }
    // Backward sweep
    PointSensitivityBuilder sensitivity = PointSensitivityBuilder.none();
    double pv2Bar = 1.0;
    double discountFactorPaymentBar = pv * caplet.getNotional() * deltaPay / deltaIbor;
    ZeroRateSensitivity discountFactorPaymentDr = 
        ratesProvider.discountFactors(ccy).zeroRatePointSensitivity(caplet.getPaymentDate());
    sensitivity = sensitivity.combinedWith(discountFactorPaymentDr.multipliedBy(discountFactorPaymentBar));
    double pvBar = caplet.getNotional() * discountFactorPayment * deltaPay / deltaIbor * pv2Bar;
    double investmentFactorIborBar = 0.0;
    // Note kappaBar = 0 as kappa optimal.
    if (caplet.getPutCall().isCall()) {
      investmentFactorIborBar = normalKappaAlpha0Alpha1 * expalpha01 * pvBar;
    } else {
      investmentFactorIborBar = - (1-normalKappaAlpha0Alpha1) * expalpha01 * pvBar;
    }
    PointSensitivityBuilder investmentFactorIborDr = 
        ratesProvider.iborIndexRates(caplet.getIndex()).ratePointSensitivity(obsIbor);
    sensitivity = sensitivity.combinedWith(investmentFactorIborDr.multipliedBy(deltaIbor * investmentFactorIborBar));
    return sensitivity;
  }

  //-------------------------------------------------------------------------
  // validate that the rates and volatilities providers are coherent
  private void validate(
      RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {
    ArgChecker.isTrue(hwProvider.getValuationDateTime().toLocalDate().equals(ratesProvider.getValuationDate()),
        "Hull-White model data and rate data should be for the same date");
  }

}
