/**
 * Copyright (C) 2006 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.cms;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.impl.rate.swap.CashFlowEquivalentCalculator;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.cms.CmsPeriod;
import com.opengamma.strata.product.cms.CmsPeriodType;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;

/**
 * Computes the price of a CMS period (coupon, cap or floor) in the Hull-White/extended Vasicek
 * one-factor model with piecewise constant volatility by numerical integration.
 * <p>
 * Pricer used mainly for testing purposes. For an explicit (approximated) implementation,
 * see the pricer {@link HullWhiteCmsPeriodExplicitPricer}.
 * 
 * @author Marc Henrard
 */
public class HullWhiteCmsPeriodNumericalIntegrationPricer {

  /** Value related to the numerical integration. */
  private static final double LIMIT_INT = 12.0; // Equivalent to + infinity in normal integrals
  private static final double TOL_ABS = 1.0E-2;
  private static final double TOL_REL = 1.0E-8;
  
  /** Minimal number of integration steps in the integration. Default value. */
  private static final int NB_INTEGRATION_STEPS_DEFAULT = 10;
  
  /** Number of integration steps in the integration. */
  private final int nbSteps;

  /** Pricer for {@link Payment}.$*/
  private final DiscountingPaymentPricer paymentPricer;

  /**
   * Creates an instance.
   */
  public HullWhiteCmsPeriodNumericalIntegrationPricer(
      int nbIntegrationSteps, 
      DiscountingPaymentPricer paymentPricer) {
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "paymentPricer");
    this.nbSteps = nbIntegrationSteps;
  }
  
  /** Default implementation. */
  public static final HullWhiteCmsPeriodNumericalIntegrationPricer DEFAULT =
      new HullWhiteCmsPeriodNumericalIntegrationPricer(
          NB_INTEGRATION_STEPS_DEFAULT,
          DiscountingPaymentPricer.DEFAULT);

  public CurrencyAmount presentValue(
      CmsPeriod cms,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    ResolvedSwap swap = cms.getUnderlyingSwap();
    Currency ccy = cms.getCurrency();
    List<ResolvedSwapLeg> legsFixed = swap.getLegs(SwapLegType.FIXED);
    ArgChecker.isTrue(legsFixed.size() == 1, "swap must have one fixed leg");
    ResolvedSwapLeg legFixed = legsFixed.get(0);
    List<ResolvedSwapLeg> legsIbor = swap.getLegs(SwapLegType.IBOR);
    ArgChecker.isTrue(legsIbor.size() == 1, "swap must have one Ibor leg");
    ResolvedSwapLeg legIbor = legsIbor.get(0);
    LocalDate fixingDate = cms.getFixingDate();
    LocalDate numeraireDate = fixingDate;
    LocalDate paymentDate = cms.getPaymentDate();
    // Coefficients
    ResolvedSwapLeg cfeIbor = CashFlowEquivalentCalculator.cashFlowEquivalentIborLeg(legIbor, multicurve);
    int nbPaymentsIbor = cfeIbor.getPaymentEvents().size();
    double[] alphaIbor = new double[nbPaymentsIbor];
    double[] discountedCashFlowIbor = new double[nbPaymentsIbor];
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      NotionalExchange payment = (NotionalExchange) cfeIbor.getPaymentEvents().get(loopcf);
      LocalDate maturityDate = payment.getPaymentDate();
      alphaIbor[loopcf] = hwProvider.alpha(multicurve.getValuationDate(), fixingDate, numeraireDate, maturityDate);
      discountedCashFlowIbor[loopcf] = paymentPricer.presentValueAmount(payment.getPayment(), multicurve);
    }
    ResolvedSwapLeg cfeFixed = CashFlowEquivalentCalculator.cashFlowEquivalentFixedLeg(legFixed, multicurve);
    int nbPaymentsFixed = cfeFixed.getPaymentEvents().size();
    double[] alphaFixed = new double[nbPaymentsFixed];
    double[] discountedCashFlowFixed = new double[nbPaymentsFixed];
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      NotionalExchange payment = (NotionalExchange) cfeFixed.getPaymentEvents().get(loopcf);
      LocalDate maturityDate = payment.getPaymentDate();
      alphaFixed[loopcf] = hwProvider.alpha(multicurve.getValuationDate(), fixingDate, numeraireDate, maturityDate);
      discountedCashFlowFixed[loopcf] = paymentPricer.presentValueAmount(payment.getPayment(), multicurve);
    }
    double alphap = hwProvider.alpha(multicurve.getValuationDate(), fixingDate, numeraireDate, paymentDate);
    double dfPayment = multicurve.discountFactor(ccy, paymentDate);

    // Integration
    double limiteInf = -LIMIT_INT;
    Function<Double, Double> payoffFunction = null;
    if (cms.getCmsPeriodType().equals(CmsPeriodType.COUPON)) {
      payoffFunction = x -> x;
    }
    if (cms.getCmsPeriodType().equals(CmsPeriodType.CAPLET)) {
      double strike = cms.getCaplet().getAsDouble();
      payoffFunction = x -> Math.max(x - strike, 0.0d);
      
      double[] coefficientA = coefficientsA(-alphap, alphaIbor,
          discountedCashFlowIbor, alphaFixed, discountedCashFlowFixed);
      double kappa = (strike - coefficientA[0]) / coefficientA[1] - alphap; // Order 1 approximation
      limiteInf = kappa;
    }
    if (cms.getCmsPeriodType().equals(CmsPeriodType.FLOORLET)) {
      double strike = cms.getFloorlet().getAsDouble();
      payoffFunction = x -> Math.max(strike - x, 0.0d);
    }
    PriceIntegrantGeneric integrant =new PriceIntegrantGeneric
        (alphaIbor, alphaFixed, discountedCashFlowIbor, discountedCashFlowFixed, alphap, payoffFunction);
    RungeKuttaIntegrator1D integrator1D =
        new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, nbSteps);
    double pv = 0.0;
    try {
      pv = 1.0 / Math.sqrt(2.0 * Math.PI) *
          integrator1D.integrate(integrant, limiteInf, LIMIT_INT);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return CurrencyAmount.of(ccy, cms.getNotional() * cms.getYearFraction() * dfPayment * pv);
  }

  /** Inner class to implement the 1-dimensional integration used in price replication. */
  private static final class PriceIntegrantGeneric 
    implements Function<Double, Double> {
    
    private final double[] alphaIbor;
    private final double[] alphaFixed;
    private final double[] dfIbor;
    private final double[] dfFixed;
    private final double alphap;
    private final Function<Double, Double> payoffFunction;
    private final int nbPayIbor;
    private final int nbPayFixed;

    /**
     * Constructor to the integrant function.
     */
    public PriceIntegrantGeneric(double[] alphadfIbor, double[] alphadfFixed, double[] dfIbor, double[] dfFixed,
        double alphap, Function<Double, Double>  payoffFunction) {
      this.alphaIbor = alphadfIbor;
      this.alphaFixed = alphadfFixed;
      this.dfIbor = dfIbor;
      this.dfFixed = dfFixed;
      this.alphap = alphap;
      this.payoffFunction = payoffFunction;
      this.nbPayIbor = alphadfIbor.length;
      this.nbPayFixed = alphadfFixed.length;
    }

    @Override
    public Double apply(Double x) {
      double b = 0;
      for (int loopcf = 0; loopcf < nbPayIbor; loopcf++) {
        b += dfIbor[loopcf] *
            Math.exp(-alphaIbor[loopcf] * x - 0.5 * alphaIbor[loopcf] * alphaIbor[loopcf]);
      }
      double c = 0;
      for (int loopcf = 0; loopcf < nbPayFixed; loopcf++) {
        c -= dfFixed[loopcf] *
            Math.exp(-alphaFixed[loopcf] * x - 0.5 * alphaFixed[loopcf] * alphaFixed[loopcf]);
      }
      double payoff = payoffFunction.apply(b / c) * Math.exp(-alphap * x - 0.5 * alphap * alphap);
      double density = Math.exp(-0.5 * x * x);
      return payoff * density;
    }
        
  }
  
  private double[] coefficientsA(
      double x, 
      double[] alphaIbor,
      double[] discountedCashFlowIbor,
      double[] alphaFixed,
      double[] discountedCashFlowFixed) {
    
    int nbPaymentsIbor = discountedCashFlowIbor.length;
    int nbPaymentsFixed = discountedCashFlowFixed.length;
    double b = 0;
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      b += discountedCashFlowIbor[loopcf] *
          Math.exp(-alphaIbor[loopcf] * x - 0.5 * alphaIbor[loopcf] * alphaIbor[loopcf]);
    }
    double c = 0;
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      c -= discountedCashFlowFixed[loopcf] *
          Math.exp(-alphaFixed[loopcf] * x - 0.5 * alphaFixed[loopcf] * alphaFixed[loopcf]);
    }
    double bp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      bp += -discountedCashFlowIbor[loopcf] * alphaIbor[loopcf] *
          Math.exp(-alphaIbor[loopcf] * x - 0.5 * alphaIbor[loopcf] * alphaIbor[loopcf]);
    }
    double cp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      cp -= -discountedCashFlowFixed[loopcf] * alphaFixed[loopcf] *
          Math.exp(-alphaFixed[loopcf] * x - 0.5 * alphaFixed[loopcf] * alphaFixed[loopcf]);
    }
    double bpp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      bpp += discountedCashFlowIbor[loopcf] * alphaIbor[loopcf] * alphaIbor[loopcf] *
          Math.exp(-alphaIbor[loopcf] * x - 0.5 * alphaIbor[loopcf] * alphaIbor[loopcf]);
    }
    double cpp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      cpp -= discountedCashFlowFixed[loopcf] * alphaFixed[loopcf] * alphaFixed[loopcf] *
          Math.exp(-alphaFixed[loopcf] * x - 0.5 * alphaFixed[loopcf] * alphaFixed[loopcf]);
    }
    double A0 = b / c;
    double A1 = bp / c - b * cp / (c * c);
    double A2 = bpp / c - (2 * bp * cp + b * cpp) / (c * c) + 2 * b * cp * cp / (c * c * c);
    
    return new double[] {A0, A1, A2}; // 
  }


}
