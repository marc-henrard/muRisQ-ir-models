/**
 * Copyright (C) 2011 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
import com.opengamma.strata.pricer.impl.rate.model.HullWhiteOneFactorPiecewiseConstantInterestRateModel;
import com.opengamma.strata.pricer.impl.rate.swap.CashFlowEquivalentCalculator;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.RateComputation;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

/**
 * Pricing of cash-settled swaptions in the Hull-White model with third order approximation.
 * 
 * Reference: Henrard, M., Cash-Settled Swaptions: How Wrong are We? (November 2010). 
 * Available at SSRN: http://ssrn.com/abstract=1703846
 * 
 * @author Marc Henrard
 */
public class HullWhiteSwaptionCashParYieldProductApproxPricer 
    extends SingleCurrencyModelSwaptionCashParYieldProductPricer {

  /**
   * Formulas for the Hull-White one-factor model with piecewise constant volatility.
   */
  private static final HullWhiteOneFactorPiecewiseConstantInterestRateModel HW_MODEL =
      HullWhiteOneFactorPiecewiseConstantInterestRateModel.DEFAULT;

  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

  /**
  * Default implementation.
  */
  public static final HullWhiteSwaptionCashParYieldProductApproxPricer DEFAULT =
      new HullWhiteSwaptionCashParYieldProductApproxPricer();

  /**
  * Creates an instance.
  */
  public HullWhiteSwaptionCashParYieldProductApproxPricer() {
  }

  /**
   * Computes the present value of a swaption in the Hull-White one-factor model.
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the product to price
   * @param multicurve  the rates provider
   * @param hwProvider  the Hull-White model parameters
   * @return the present value of the swaption product
   */
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    Currency ccy = swaption.getCurrency();
    HullWhiteOneFactorPiecewiseConstantParameters parameters = hwProvider.getParameters();
    LocalDate expiryDate = swaption.getExpiry().toLocalDate();
    double expiryTime = hwProvider.relativeTime(expiryDate);
    ResolvedSwap swap = swaption.getUnderlying();
    ResolvedSwapLeg legFixed = swap.getLegs(SwapLegType.FIXED).get(0);
    double strike = calculateStrike(legFixed);
    int nbFixed = legFixed.getPaymentPeriods().size();
    double[] alphaFixed = new double[nbFixed];
    double[] dfFixed = new double[nbFixed];
    double[] discountedCashFlowFixed = new double[nbFixed];
    for (int loopcf = 0; loopcf < nbFixed; loopcf++) {
      RatePaymentPeriod period = (RatePaymentPeriod) legFixed.getPaymentPeriods().get(loopcf);
      double notional = period.getNotional();
      double accrualFactor = period.getAccrualPeriods().get(0).getYearFraction();
      LocalDate cfDate = period.getPaymentDate();
      double cfTime = hwProvider.relativeTime(cfDate);
      alphaFixed[loopcf] = HW_MODEL.alpha(parameters, 0.0, expiryTime, expiryTime, cfTime);
      dfFixed[loopcf] = multicurve.discountFactor(ccy, cfDate);
      discountedCashFlowFixed[loopcf] = dfFixed[loopcf] * notional * accrualFactor;
    }
    ResolvedSwapLeg legIbor = swap.getLegs(SwapLegType.IBOR).get(0);
    ResolvedSwapLeg cfeIbor = CashFlowEquivalentCalculator.cashFlowEquivalentIborLeg(legIbor, multicurve);
    int nbIbor = cfeIbor.getPaymentEvents().size();
    double[] alphaIbor = new double[nbIbor];
    double[] dfIbor = new double[nbIbor];
    double[] discountedCashFlowIbor = new double[nbIbor];
    for (int loopcf = 0; loopcf < nbIbor; loopcf++) {
      NotionalExchange cf = (NotionalExchange) cfeIbor.getPaymentEvents().get(loopcf);
      LocalDate cfDate = cf.getPaymentDate();
      double cfTime = hwProvider.relativeTime(cfDate);
      alphaIbor[loopcf] = HW_MODEL.alpha(parameters, 0.0, expiryTime, expiryTime, cfTime);
      dfIbor[loopcf] = multicurve.discountFactor(ccy, cfDate);
      discountedCashFlowIbor[loopcf] = dfIbor[loopcf] * cf.getPaymentAmount().getAmount();
    }
    ResolvedSwapLeg cfe = CashFlowEquivalentCalculator.cashFlowEquivalentSwap(swap, multicurve);
    int nbCf = cfe.getPaymentEvents().size();
    final double[] alpha = new double[nbCf];
    final double[] df = new double[nbCf];
    final double[] discountedCashFlow = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      NotionalExchange cf = (NotionalExchange) cfe.getPaymentEvents().get(loopcf);
      LocalDate cfDate = cf.getPaymentDate();
      double cfTime = hwProvider.relativeTime(cfDate);
      alpha[loopcf] = HW_MODEL.alpha(parameters, 0.0, expiryTime, expiryTime, cfTime);
      df[loopcf] = multicurve.discountFactor(ccy, cfDate);
      discountedCashFlow[loopcf] = df[loopcf] * cf.getPaymentAmount().getAmount();
    }
    double kappa = HW_MODEL.kappa(DoubleArray.ofUnsafe(discountedCashFlow), DoubleArray.ofUnsafe(alpha));
    int nbFixedPaymentYear = (int) Math.round(1.0 /
        ((RatePaymentPeriod) legFixed.getPaymentPeriods().get(0)).getAccrualPeriods().get(0).getYearFraction());
    double[] derivativesRate = new double[3];
    double[] derivativesAnnuity = new double[3];
    double x0 = 0.0;
    double rate = swapRate(x0, discountedCashFlowFixed, alphaFixed, discountedCashFlowIbor, alphaIbor, derivativesRate);
    double annuity = annuityCash(rate, nbFixedPaymentYear, nbFixed, derivativesAnnuity);
    double[] u = new double[4];
    u[0] = annuity * (strike - rate);
    u[1] = (strike - rate) * derivativesAnnuity[0] * derivativesRate[0] - derivativesRate[0] * annuity;
    u[2] = (strike - rate) *
        (derivativesAnnuity[0] * derivativesRate[1] + derivativesAnnuity[1] * derivativesRate[0] * derivativesRate[0]) -
        2 * derivativesAnnuity[0] * derivativesRate[0] * derivativesRate[0] - annuity * derivativesRate[1];
    u[3] = -3 * derivativesRate[0] *
        (derivativesAnnuity[0] * derivativesRate[1] + derivativesAnnuity[1] * derivativesRate[0] * derivativesRate[0]) -
        2 * derivativesAnnuity[0] * derivativesRate[0] * derivativesRate[1] +
        (strike - rate) * (derivativesAnnuity[0] * derivativesRate[2] +
            3 * derivativesAnnuity[1] * derivativesRate[0] * derivativesRate[1] +
            derivativesAnnuity[2] * derivativesRate[0] * derivativesRate[0] * derivativesRate[0]) -
        rate * derivativesRate[2];
    final double kappatilde = kappa + alphaIbor[0];
    final double alpha0tilde = alphaIbor[0] + x0;
    double pv;
    if (!legFixed.getPayReceive().equals(PayReceive.PAY)) {
      pv = (u[0] - u[1] * alpha0tilde + u[2] * (1 + alpha[0] * alpha[0]) / 2.0 -
          u[3] * (alpha0tilde * alpha0tilde * alpha0tilde + 3.0 * alpha0tilde) / 6.0) * NORMAL.getCDF(kappatilde) +
          (-u[1] - u[2] * (-2.0 * alpha0tilde + kappatilde) / 2.0 + u[3] *
              (-3 * alpha0tilde * alpha0tilde + 3.0 * kappatilde * alpha0tilde - kappatilde * kappatilde - 2.0) / 6.0) *
              NORMAL.getPDF(kappatilde);
    } else {
      pv = -(u[0] - u[1] * alpha0tilde + u[2] * (1 + alpha[0] * alpha[0]) / 2.0 -
          u[3] * (alpha0tilde * alpha0tilde * alpha0tilde + 3.0 * alpha0tilde) / 6.0) * NORMAL.getCDF(-kappatilde) +
          (-u[1] - u[2] * (-2.0 * alpha0tilde + kappatilde) / 2.0 + u[3] *
              (-3 * alpha0tilde * alpha0tilde + 3.0 * kappatilde * alpha0tilde - kappatilde * kappatilde - 2.0) / 6.0) *
              NORMAL.getPDF(kappatilde);
    }
    double notional = Math.abs(((RatePaymentPeriod) legFixed.getPaymentPeriods().get(0)).getNotional());
    return CurrencyAmount.of(ccy, pv * notional * dfIbor[0] * swaption.getLongShort().sign());
  }

  /**
   * Computation of the swap rate for a given random variable in the Hull-White one factor model.
   * @param x The random variable.
   * @param discountedCashFlowFixed The discounted cash flows.
   * @param alphaFixed The bond volatilities.
   * @param discountedCashFlowIbor The discounted cash flows.
   * @param alphaIbor The bond volatilities.
   * @param derivatives Array used to return the derivatives of the swap rate with respect to the random variable. 
   * The array is changed by the method. The values are [0] the first order derivative and [1] the second order derivative.
   * @return The swap rate.
   */
  private double swapRate(double x,
      double[] discountedCashFlowFixed,
      double[] alphaFixed,
      double[] discountedCashFlowIbor,
      double[] alphaIbor,
      double[] derivatives) {

    double[] f = new double[3];
    double y1;
    for (int loopcf = 0; loopcf < discountedCashFlowIbor.length; loopcf++) {
      y1 = -discountedCashFlowIbor[loopcf] *
          Math.exp(-alphaIbor[loopcf] * x - alphaIbor[loopcf] * alphaIbor[loopcf] / 2.0);
      f[0] += y1;
      f[1] += -alphaIbor[loopcf] * y1;
      f[2] += alphaIbor[loopcf] * alphaIbor[loopcf] * y1;
    }
    double[] g = new double[3];
    double y2;
    for (int loopcf = 0; loopcf < discountedCashFlowFixed.length; loopcf++) {
      y2 = discountedCashFlowFixed[loopcf] *
          Math.exp(-alphaFixed[loopcf] * x - alphaFixed[loopcf] * alphaFixed[loopcf] / 2.0);
      g[0] += y2;
      g[1] += -alphaFixed[loopcf] * y2;
      g[2] += alphaFixed[loopcf] * alphaFixed[loopcf] * y2;
    }
    double swapRate = f[0] / g[0];
    derivatives[0] = (f[1] * g[0] - f[0] * g[1]) / (g[0] * g[0]);
    derivatives[1] =
        (f[2] * g[0] - f[0] * g[2]) / (g[0] * g[0]) - (f[1] * g[0] - f[0] * g[1]) * 2 * g[1] / (g[0] * g[0] * g[0]);
    return swapRate;
  }

  /**
   * Computes the cash annuity from the swap rate and its derivatives.
   * @param swapRate The swap rate.
   * @param nbFixedPaymentYear The number of fixed payment per year.
   * @param nbFixedPeriod The total number of payments.
   * @param derivatives Array used to return the derivatives of the annuity with respect to the swap rate. The array is changed by the method.
   * The values are [0] the first order derivative, [1] the second order derivative and [2] the third order derivative.
   * @return The annuity
   */
  private double annuityCash(double swapRate,
      int nbFixedPaymentYear,
      int nbFixedPeriod,
      double[] derivatives) {

    final double invfact = 1 + swapRate / nbFixedPaymentYear;
    final double annuity = 1.0 / swapRate * (1.0 - 1.0 / Math.pow(invfact, nbFixedPeriod));
    derivatives[0] = 0.0;
    derivatives[1] = 0.0;
    derivatives[2] = 0.0;
    for (int looppay = 0; looppay < nbFixedPeriod; looppay++) {
      derivatives[0] += -(looppay + 1) * Math.pow(invfact, -looppay - 2) / (nbFixedPaymentYear * nbFixedPaymentYear);
      derivatives[1] += (looppay + 1) * (looppay + 2) * Math.pow(invfact, -looppay - 3) /
          (nbFixedPaymentYear * nbFixedPaymentYear * nbFixedPaymentYear);
      derivatives[2] += -(looppay + 1) * (looppay + 2) * (looppay + 3) * Math.pow(invfact, -looppay - 4) /
          (nbFixedPaymentYear * nbFixedPaymentYear * nbFixedPaymentYear * nbFixedPaymentYear);
    }
    return annuity;
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
