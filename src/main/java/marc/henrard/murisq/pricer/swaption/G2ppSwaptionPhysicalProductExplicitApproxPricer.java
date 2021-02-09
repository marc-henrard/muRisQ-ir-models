/**
 * Copyright (C) 2011 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapPaymentEvent;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantFormulas;
import marc.henrard.murisq.model.g2pp.G2ppPiecewiseConstantParameters;
import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.pricer.swap.CashFlowEquivalentCalculator;
import marc.henrard.murisq.pricer.swap.CashFlowEquivalentUtils;

/**
 * Pricing of European physical settlement swaptions in the G2++ with deterministic multiplicative spread.
 * <p>
 * Pricing by efficient approximation. Implementation uses the "middle point" version.
 * Literature Reference: 
 * Henrard, M. (2010). Swaptions in Libor Market Model with local volatility. Wilmott Journal, 2010, 2, 135-154
 * Implementation reference:
 * Henrard, M. G2++, muRisQ Model description, September 2020.
 * 
 * @author Marc Henrard
 */
public class G2ppSwaptionPhysicalProductExplicitApproxPricer
    extends SingleCurrencyModelSwaptionPhysicalProductPricer {

  /**
   * Formulas for the G2++ model with piecewise constant volatility.
   */
  private static final G2ppPiecewiseConstantFormulas FORMULAS_G2PP = G2ppPiecewiseConstantFormulas.DEFAULT;

  /**
  * Default implementation.
  */
  public static final G2ppSwaptionPhysicalProductExplicitApproxPricer DEFAULT =
      new G2ppSwaptionPhysicalProductExplicitApproxPricer();

  /**
  * Creates an instance.
  */
  public G2ppSwaptionPhysicalProductExplicitApproxPricer() {
  }

  @Override
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    ArgChecker.isTrue(model instanceof G2ppPiecewiseConstantParameters,
        "Parameters must be of the type G2ppPiecewiseConstantParameters");
    G2ppPiecewiseConstantParameters g2pp = (G2ppPiecewiseConstantParameters) model;
    DiscountFactors dsc = multicurve.discountFactors(swaption.getCurrency());
    ResolvedSwapLeg cfe = CashFlowEquivalentCalculator
        .cashFlowEquivalentSwap(swaption.getUnderlying(), multicurve); // includes the spread adjusted notional
    cfe = CashFlowEquivalentUtils.sortCompress(cfe);
    ImmutableList<SwapPaymentEvent> cfePeriods = cfe.getPaymentEvents();
    int nbCf = cfePeriods.size();
    double[] cfa = new double[nbCf];
    double[] t = new double[nbCf];
    double sign = Math.signum(((NotionalExchange) cfePeriods.get(0)).getPaymentAmount().getAmount());
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      NotionalExchange cf = (NotionalExchange) cfePeriods.get(loopcf);
      cfa[loopcf] = -sign * cf.getPaymentAmount().getAmount();
      t[loopcf] = g2pp.getTimeMeasure().relativeTime(g2pp.getValuationDate(), cf.getPaymentDate());
    }
    double rhog2pp = g2pp.getCorrelation();
    double[][] ht0 = FORMULAS_G2PP.volatilityMaturityPartRatioDiscountFactors(g2pp, t[0], t);
    double[] dfswap = new double[nbCf];
    double[] p0 = new double[nbCf];
    double[] cP = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      dfswap[loopcf] = dsc.discountFactor(t[loopcf]);
      p0[loopcf] = dfswap[loopcf] / dfswap[0];
      cP[loopcf] = cfa[loopcf] * p0[loopcf];
    }
    double k = -cfa[0];
    double b0 = 0.0;
    for (int loopcf = 1; loopcf < nbCf; loopcf++) {
      b0 += cP[loopcf];
    }
    double[] alpha0 = new double[nbCf - 1];
    double[] beta0 = new double[2];
    for (int loopcf = 0; loopcf < nbCf - 1; loopcf++) {
      alpha0[loopcf] = cfa[loopcf + 1] * p0[loopcf + 1] / b0;
      beta0[0] += alpha0[loopcf] * ht0[0][loopcf + 1];
      beta0[1] += alpha0[loopcf] * ht0[1][loopcf + 1];
    }
    double[][] gamma = FORMULAS_G2PP.gammaRatioDiscountFactors(g2pp, 0, g2pp.relativeTime(swaption.getExpiry()));
    double[] tau = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      tau[loopcf] = gamma[0][0] * ht0[0][loopcf] * ht0[0][loopcf] + gamma[1][1] * ht0[1][loopcf] * ht0[1][loopcf] +
          2 * rhog2pp * gamma[0][1] * ht0[0][loopcf] * ht0[1][loopcf];
    }
    double xbarnum = 0.0;
    double xbarde = 0.0;
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      xbarnum += cP[loopcf] - cP[loopcf] * tau[loopcf] * tau[loopcf] / 2.0;
      xbarde += cP[loopcf] * tau[loopcf];
    }
    double xbar = xbarnum / xbarde;
    double[] pK = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      pK[loopcf] = p0[loopcf] * (1.0 - tau[loopcf] * xbar - tau[loopcf] * tau[loopcf] / 2.0);
    }
    double[] alphaK = new double[nbCf - 1];
    double[] betaK = new double[2];
    for (int loopcf = 0; loopcf < nbCf - 1; loopcf++) {
      alphaK[loopcf] = cfa[loopcf + 1] * pK[loopcf + 1] / k;
      betaK[0] += alphaK[loopcf] * ht0[0][loopcf + 1];
      betaK[1] += alphaK[loopcf] * ht0[1][loopcf + 1];
    }
    double[] betaBar = new double[] {(beta0[0] + betaK[0]) / 2.0, (beta0[1] + betaK[1]) / 2.0};
    double sigmaBar2 = gamma[0][0] * betaBar[0] * betaBar[0] + gamma[1][1] * betaBar[1] * betaBar[1] +
        2 * rhog2pp * gamma[0][1] * betaBar[0] * betaBar[1];
    double sigmaBar = Math.sqrt(sigmaBar2);
    double priceFwd = BlackFormulaRepository.price(b0, k, 1.0d, sigmaBar, sign < 0); // time embedded in sigmaBar
    return CurrencyAmount.of(swaption.getCurrency(), priceFwd * dfswap[0] * swaption.getLongShort().sign());
  }

}
