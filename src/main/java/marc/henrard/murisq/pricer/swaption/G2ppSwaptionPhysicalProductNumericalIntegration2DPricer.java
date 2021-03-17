/**
 * Copyright (C) 2011 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.util.function.BiFunction;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.pricer.DiscountFactors;
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
 * Pricing of European physical settlement swaptions in the G2++ with deterministic multiplicative spread
 * between LIBOR and discounting.
 * <p>
 * Based on cash flow equivalent, works for LIBOR and OIS swaptions.
 * <p>
 * Pricing by numerical integration.
 * Implementation reference:
 * Henrard, M. G2++, muRisQ Model description, September 2020.
 * 
 * @author Marc Henrard
 */
public class G2ppSwaptionPhysicalProductNumericalIntegration2DPricer
    extends SingleCurrencyModelSwaptionPhysicalProductPricer {

  /**
   * Default minimal number of integration steps in the integration.
   */
  private static final int NB_INTEGRATION_DEFAULT = 50;
  /** Value related to the numerical integration. */
  private static final double LIMIT_INT = 12.0; // Equivalent to + infinity in normal integrals
  private static final double TOL_ABS = 1.0E-1;
  private static final double TOL_REL = 1.0E-6;

  /**
   * Formulas for the G2++ model with piecewise constant volatility.
   */
  private static final G2ppPiecewiseConstantFormulas FORMULAS_G2PP = G2ppPiecewiseConstantFormulas.DEFAULT;
  
  /**
   * Minimal number of integration steps in the integration.
   */
  private final int nbSteps;
  
  /**
  * Default implementation.
  */
  public static final G2ppSwaptionPhysicalProductNumericalIntegration2DPricer DEFAULT =
      new G2ppSwaptionPhysicalProductNumericalIntegration2DPricer(NB_INTEGRATION_DEFAULT);

  /**
  * Creates an instance.
  */
  public G2ppSwaptionPhysicalProductNumericalIntegration2DPricer(int nbSteps) {
    this.nbSteps = nbSteps;
  }

  /**
   * Computes the present value of the Physical delivery swaption through approximation..
   * @param swaption The swaption.
   * @param g2Data The G2++ parameters and the curves.
   * @return The present value.
   */
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    ArgChecker.isTrue(model instanceof G2ppPiecewiseConstantParameters,
        "Parameters must be of the type G2ppPiecewiseConstantParameters");
    G2ppPiecewiseConstantParameters g2pp = (G2ppPiecewiseConstantParameters) model;
    Currency ccy = swaption.getCurrency();
    DiscountFactors dsc = multicurve.discountFactors(ccy);
    ResolvedSwapLeg cfe = CashFlowEquivalentCalculator
        .cashFlowEquivalentSwap(swaption.getUnderlying(), multicurve); // includes the spread adjusted notional
    cfe = CashFlowEquivalentUtils.sortCompress(cfe);
    ImmutableList<SwapPaymentEvent> cfePeriods = cfe.getPaymentEvents();
    int nbCf = cfePeriods.size();
    double theta = g2pp.relativeTime(swaption.getExpiry());
    final double[] t = new double[nbCf];
    final double[] df = new double[nbCf];
    final double[] discountedCashFlow = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      NotionalExchange cf = (NotionalExchange) cfePeriods.get(loopcf);
      t[loopcf] = g2pp.getTimeMeasure().relativeTime(g2pp.getValuationDate(), cf.getPaymentDate());
      df[loopcf] = dsc.discountFactor(t[loopcf]);
      discountedCashFlow[loopcf] = df[loopcf] * cf.getPaymentAmount().getAmount();
    }
    double rhog2pp = g2pp.getCorrelation();
    double[][] htheta = FORMULAS_G2PP.volatilityMaturityPartRatioDiscountFactors(g2pp, theta, t);
    double[][] gamma = FORMULAS_G2PP.gammaRatioDiscountFactors(g2pp, 0, theta);
    double[][] alpha = new double[2][nbCf];
    double[] tau2 = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      alpha[0][loopcf] = Math.sqrt(gamma[0][0]) * htheta[0][loopcf];
      alpha[1][loopcf] = Math.sqrt(gamma[1][1]) * htheta[1][loopcf];
      tau2[loopcf] = alpha[0][loopcf] * alpha[0][loopcf] + alpha[1][loopcf] * alpha[1][loopcf] + 2 * rhog2pp * gamma[0][1] * htheta[0][loopcf] * htheta[1][loopcf];
    }
    double rhobar = rhog2pp * gamma[0][1] / Math.sqrt(gamma[0][0] * gamma[1][1]);
    SwaptionIntegrant integrant = new SwaptionIntegrant(discountedCashFlow, alpha, tau2, rhobar);
    RungeKuttaIntegrator1D integrator1D = 
        new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbSteps);
    IntegratorRepeated2D integrator2D = new IntegratorRepeated2D(integrator1D);
    double pv = 0.0;
    try {
      pv = 1.0 / (2.0 * Math.PI * Math.sqrt(1 - rhobar * rhobar)) * 
          integrator2D.integrate(integrant, new Double[] {-LIMIT_INT, -LIMIT_INT}, new Double[] {LIMIT_INT, LIMIT_INT});
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return CurrencyAmount.of(ccy, pv * swaption.getLongShort().sign());
  }

  /**
   * Inner class to implement the integration used in price replication.
   */
  private static final class SwaptionIntegrant implements BiFunction<Double, Double, Double> {

    private final double[] _discountedCashFlow;
    private final double[][] _alpha;
    private final double[] _tau2;
    private final double _rhobar;

    /**
     * Constructor to the integrant function.
     * @param discountedCashFlow The discounted cash flows.
     * @param alpha The bond volatilities.
     */
    public SwaptionIntegrant(final double[] discountedCashFlow, final double[][] alpha, final double[] tau2, final double rhobar) {
      _discountedCashFlow = discountedCashFlow;
      _alpha = alpha;
      _tau2 = tau2;
      _rhobar = rhobar;
    }

    @Override
    public Double apply(final Double x0, final Double x1) {
      double result = 0.0;
      final double densityPart = -(x0 * x0 + x1 * x1 - 2 * _rhobar * x0 * x1) / (2.0 * (1 - _rhobar * _rhobar));
      for (int loopcf = 0; loopcf < _discountedCashFlow.length; loopcf++) {
        result += _discountedCashFlow[loopcf] * Math.exp(-_alpha[0][loopcf] * x0 - _alpha[1][loopcf] * x1 - _tau2[loopcf] / 2.0 + densityPart);
      }
      return Math.max(result, 0.0);
    }
  }
  
}
