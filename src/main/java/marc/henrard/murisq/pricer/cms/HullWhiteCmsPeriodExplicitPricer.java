/**
 * Copyright (C) 2006 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.cms;

import java.time.LocalDate;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.ZeroRateSensitivity;
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
 * one-factor model with piecewise constant volatility. Pricing by an explicit approximated formula.
 * <p>
 * The approximation is obtained by a Taylor approximation of order 3. 
 * <p>
 * The swap underlying the CMS must have a fixed coupon of 1.
 * <p>
 * <i>Reference: </i>
 * Henrard, Marc. (2008) CMS swaps and caps in one-factor Gaussian models. SSRN Working Paper 985551.
 *    Available at <a href="http://ssrn.com/abstract=985551">http://ssrn.com/abstract=985551</a>
 * 
 * @author Marc Henrard
 */
public class HullWhiteCmsPeriodExplicitPricer {
  
  /** Normal distribution function. */
  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

  /**
   * Default implementation.
   */
  public static final HullWhiteCmsPeriodExplicitPricer DEFAULT =
      new HullWhiteCmsPeriodExplicitPricer(DiscountingPaymentPricer.DEFAULT);

  /**
   * Pricer for {@link Payment}.
   */
  private final DiscountingPaymentPricer paymentPricer;

  /**
   * Creates an instance.
   * 
   * @param paymentPricer  the pricer for {@link Payment}
   */
  public HullWhiteCmsPeriodExplicitPricer(DiscountingPaymentPricer paymentPricer) {
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "paymentPricer");
  }

  /**
   * Returns the present value of the CMS period.
   * 
   * @param cms
   * @param multicurve
   * @param hwProvider
   * @return the present value
   */
  public CurrencyAmount presentValue(
      CmsPeriod cms, 
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    Currency ccy = cms.getCurrency();
    if(multicurve.getValuationDate().isAfter(cms.getPaymentDate())) {
      return CurrencyAmount.of(ccy, 0.0); // Payment already took place
    }
    if(!multicurve.getValuationDate().isBefore(cms.getFixingDate())) { // Fixing took place, but not yet paid. Requires fixing.
      LocalDateDoubleTimeSeries tsSwapRate = multicurve.timeSeries(cms.getIndex());
      ArgChecker.isTrue(tsSwapRate.containsDate(cms.getFixingDate()), 
          "Fixing already took place, time series must contain index value at fixing date {}", cms.getFixingDate());
      double fixing = tsSwapRate.get(cms.getFixingDate()).getAsDouble();
      double payoff = fixing;
      if(cms.getCmsPeriodType().equals(CmsPeriodType.FLOORLET)) {
        payoff = Math.max(0, cms.getFloorlet().getAsDouble() - fixing);
      } else {
        payoff = Math.max(0, fixing - cms.getFloorlet().getAsDouble());
      }
      double dfPayment = multicurve.discountFactor(ccy, cms.getPaymentDate());
      double pv = cms.getNotional() * cms.getYearFraction() * payoff * dfPayment;
      return CurrencyAmount.of(ccy, pv);
    }
    // Fixing still to take place
    ResolvedSwap swap = cms.getUnderlyingSwap();
    List<ResolvedSwapLeg> legsFixed = swap.getLegs(SwapLegType.FIXED);
    ArgChecker.isTrue(legsFixed.size() == 1, "swap must have one fixed leg");
    ResolvedSwapLeg legFixed = legsFixed.get(0);
    List<ResolvedSwapLeg> legsIbor = swap.getLegs(SwapLegType.IBOR);
    ArgChecker.isTrue(legsIbor.size() == 1, "swap must have one Ibor leg");
    ResolvedSwapLeg legIbor = legsIbor.get(0);
    LocalDate fixingDate = cms.getFixingDate();
    LocalDate numeraireDate = fixingDate;
    LocalDate paymentDate = cms.getPaymentDate();
    
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
    double[] coefficientA = 
        coefficientsA(-alphap, alphaIbor, discountedCashFlowIbor, alphaFixed, discountedCashFlowFixed);
    // Code for higher order terms
    double shift = 1.0E-4; // Finite difference shift; could be obtained by AD
    double[] coefficientAP = coefficientsA(-alphap + shift, alphaIbor,
        discountedCashFlowIbor, alphaFixed, discountedCashFlowFixed);
    double[] coefficientAM = coefficientsA(-alphap - shift, alphaIbor,
        discountedCashFlowIbor, alphaFixed, discountedCashFlowFixed);
    double A3 = (coefficientAP[2] - coefficientAM[2]) / (2 * shift);
//    double A4 = (coefficientAP[2] + coefficientAM[2] - 2 * coefficientA[2]) / (shift * shift);

    if (cms.getCmsPeriodType().equals(CmsPeriodType.CAPLET)) { // Case caplet
      double strike = cms.getCaplet().getAsDouble();
      double kappa = (strike - coefficientA[0]) / coefficientA[1]; // Order 1 approximation
      // Code for kappa by equation solving
      //      DoubleArray alpha = DoubleArray.copyOf(alphaIbor).concat(alphaFixed);
      //      DoubleArray discountedCashFlow = DoubleArray.copyOf(discountedCashFlowIbor)
      //          .concat(DoubleArray.copyOf(discountedCashFlowFixed).multipliedBy(strike));
      //      double kappa = hwProvider.getModel().kappa(discountedCashFlow, alpha) + alphap;
      double term1 = (coefficientA[0] - strike + 0.5 * coefficientA[2]) * NORMAL.getCDF(-kappa);
      double term2 =
          (coefficientA[1] + coefficientA[2] * 0.5 * kappa 
              + A3 / 6.0 * (kappa * kappa + 2)  // Approximation order 3
          ) * Math.exp(-0.5 * kappa * kappa) / Math.sqrt(2.0 * Math.PI);
      double pv = cms.getNotional() * cms.getYearFraction() * dfPayment * (term1 + term2);
      return CurrencyAmount.of(ccy, pv);
    }
    if (cms.getCmsPeriodType().equals(CmsPeriodType.FLOORLET)) { // Case Floorlet
      double strike = cms.getFloorlet().getAsDouble();
      double kappa = (strike - coefficientA[0]) / coefficientA[1]; // Order 1 approximation
      double term1 = -(coefficientA[0] - strike + 0.5 * coefficientA[2]) * NORMAL.getCDF(kappa);
      double term2 = 
          (coefficientA[1] + coefficientA[2] * 0.5 * kappa
              + A3 / 6.0 * (kappa * kappa + 2)  // Approximation order 3
          ) * Math.exp(-0.5 * kappa * kappa) / Math.sqrt(2.0 * Math.PI);
      double pv = cms.getNotional() * cms.getYearFraction() * dfPayment * (term1 + term2);
      return CurrencyAmount.of(ccy, pv);
    }
    // Case coupon
    double pv = cms.getNotional() * cms.getYearFraction() * dfPayment * 
        (coefficientA[0] + 0.5 * coefficientA[2] // Approximation order 2
        //      + A4 / 8 // Approximation order 4
        );
    return CurrencyAmount.of(ccy, pv);
  }
  
  public PointSensitivityBuilder presentValueSensitivityRates(
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
    
    ResolvedSwapLeg cfeIbor = CashFlowEquivalentCalculator.cashFlowEquivalentIborLeg(legIbor, multicurve);
    int nbPaymentsIbor = cfeIbor.getPaymentEvents().size();
    double[] alphaIbor = new double[nbPaymentsIbor];
    double[] discountedCashFlowIbor = new double[nbPaymentsIbor];
    PointSensitivityBuilder[] discountedCashFlowIbordr = new PointSensitivityBuilder[nbPaymentsIbor];
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      NotionalExchange payment = (NotionalExchange) cfeIbor.getPaymentEvents().get(loopcf);
      LocalDate maturityDate = payment.getPaymentDate();
      alphaIbor[loopcf] = hwProvider.alpha(multicurve.getValuationDate(), fixingDate, numeraireDate, maturityDate);
      discountedCashFlowIbor[loopcf] = paymentPricer.presentValueAmount(payment.getPayment(), multicurve);
      discountedCashFlowIbordr[loopcf] = paymentPricer.presentValueSensitivity(payment.getPayment(), multicurve);
    }
    ResolvedSwapLeg cfeFixed = CashFlowEquivalentCalculator.cashFlowEquivalentFixedLeg(legFixed, multicurve);
    int nbPaymentsFixed = cfeFixed.getPaymentEvents().size();
    double[] alphaFixed = new double[nbPaymentsFixed];
    double[] discountedCashFlowFixed = new double[nbPaymentsFixed];
    PointSensitivityBuilder[] discountedCashFlowFixeddr = new PointSensitivityBuilder[nbPaymentsIbor];
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      NotionalExchange payment = (NotionalExchange) cfeFixed.getPaymentEvents().get(loopcf);
      LocalDate maturityDate = payment.getPaymentDate();
      alphaFixed[loopcf] = hwProvider.alpha(multicurve.getValuationDate(), fixingDate, numeraireDate, maturityDate);
      discountedCashFlowFixed[loopcf] = paymentPricer.presentValueAmount(payment.getPayment(), multicurve);
      discountedCashFlowFixeddr[loopcf] = paymentPricer.presentValueSensitivity(payment.getPayment(), multicurve);
    }
    double alphap = hwProvider.alpha(multicurve.getValuationDate(), fixingDate, numeraireDate, paymentDate);
    double dfPayment = multicurve.discountFactor(ccy, paymentDate);
    ZeroRateSensitivity dfPaymentdr = multicurve.discountFactors(ccy).zeroRatePointSensitivity(paymentDate);

    int nbA = 3;
    double[] coefficientA = coefficientsA(-alphap, alphaIbor,
        discountedCashFlowIbor, alphaFixed, discountedCashFlowFixed);

    // Backward sweep - common
    double[][] discountedCashFlowIborBar = new double[nbA][nbPaymentsIbor];
    double[][] discountedCashFlowFixedBar = new double[nbA][nbPaymentsFixed];
    double[] coefficientABar = new double[nbA];
    
    if (cms.getCmsPeriodType().equals(CmsPeriodType.CAPLET)) {
      double strike = cms.getCaplet().getAsDouble();
      double kappa = (strike - coefficientA[0]) / coefficientA[1]; // Order 1 approximation
      // Code for kappa by equation solving
      //      DoubleArray alpha = DoubleArray.copyOf(alphaIbor).concat(alphaFixed);
      //      DoubleArray discountedCashFlow = DoubleArray.copyOf(discountedCashFlowIbor)
      //          .concat(DoubleArray.copyOf(discountedCashFlowFixed).multipliedBy(strike));
      //      double kappa = hwProvider.getModel().kappa(discountedCashFlow, alpha) + alphap;
      double term1 = (coefficientA[0] - strike + 0.5 * coefficientA[2]) * NORMAL.getCDF(-kappa);
      double term2 = (coefficientA[1] 
              + coefficientA[2] * 0.5 * kappa 
      //              + A3 / 6.0 * (kappa * kappa + 2)  // Approximation order 3
          ) * Math.exp(-0.5 * kappa * kappa) / Math.sqrt(2.0 * Math.PI);
      double pv = cms.getNotional() * cms.getYearFraction() * dfPayment 
          * (term1 + term2);
//      return CurrencyAmount.of(ccy, pv);
    }
    if (cms.getCmsPeriodType().equals(CmsPeriodType.FLOORLET)) {
      double strike = cms.getFloorlet().getAsDouble();
      double kappa = (strike - coefficientA[0]) / coefficientA[1]; // Order 1 approximation
      double term1 = -(coefficientA[0] - strike + 0.5 * coefficientA[2]) * NORMAL.getCDF(kappa);
      double term2 = (coefficientA[1] 
          + coefficientA[2] * 0.5 * kappa
//        + A3 / 6.0 * (kappa * kappa + 2)  // Approximation order 3
      ) * Math.exp(-0.5 * kappa * kappa) / Math.sqrt(2.0 * Math.PI);
      double pv = cms.getNotional() * cms.getYearFraction() * dfPayment * (term1 + term2);
//      return CurrencyAmount.of(ccy, pv);
    }
    // Case coupon
//    double pv = cms.getNotional() * cms.getYearFraction() * dfPayment * 
//        (coefficientA[0] + 0.5 * coefficientA[2]);
    // Backward sweep
    double pvBar = 1.0;
    double dfPaymentBar =
        cms.getNotional() * cms.getYearFraction() * (coefficientA[0] + 0.5 * coefficientA[2]) * pvBar;
    PointSensitivityBuilder sensitivity = PointSensitivityBuilder.none();
    sensitivity = sensitivity.combinedWith(dfPaymentdr.multipliedBy(dfPaymentBar));

    coefficientABar[0] += cms.getNotional() * cms.getYearFraction() * dfPayment * pvBar;
    coefficientABar[2] += cms.getNotional() * cms.getYearFraction() * dfPayment * 0.5 * pvBar;
    coefficientA = coefficientsABar(-alphap, alphaIbor,
        discountedCashFlowIbor, alphaFixed, discountedCashFlowFixed,
        discountedCashFlowIborBar, discountedCashFlowFixedBar, coefficientABar);
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      sensitivity = sensitivity.combinedWith(
          discountedCashFlowIbordr[loopcf].multipliedBy(discountedCashFlowIborBar[0][loopcf]));
    }
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      sensitivity = sensitivity.combinedWith(
          discountedCashFlowFixeddr[loopcf].multipliedBy(discountedCashFlowFixedBar[0][loopcf]));
    }
    
    // To use: CashFlowEquivalentCalculator cashFlowEquivalentAndSensitivityIborLeg
    // Map to List?
    ImmutableMap<Payment, PointSensitivityBuilder> mapFix = 
        CashFlowEquivalentCalculator.cashFlowEquivalentAndSensitivityFixedLeg(legFixed, multicurve);
    mapFix.entrySet().asList();
    
    return sensitivity;
  }
  
  // TODO: rate sensitivity
  // TODO: parameters sensitivity
  
  /*
   * Computation of the Taylor expansion coefficients for the swap rate
   */
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
    // Add A3?
    return new double[] {A0, A1, A2};
  }
  
  private double[] coefficientsABar(
      double x, 
      double[] alphaIbor,
      double[] discountedCashFlowIbor,
      double[] alphaFixed,
      double[] discountedCashFlowFixed,
      double[][] discountedCashFlowIborBar, // Ax - df
      double[][] discountedCashFlowFixedBar,
      double[] ABar) {
    
    int nbPaymentsIbor = discountedCashFlowIbor.length;
    int nbPaymentsFixed = discountedCashFlowFixed.length;
    double b = 0;

    double[] eaxIbor = new double[nbPaymentsIbor];
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      eaxIbor[loopcf] = Math.exp(-alphaIbor[loopcf] * x - 0.5 * alphaIbor[loopcf] * alphaIbor[loopcf]);
    }
    double[] eaxFixed = new double[nbPaymentsFixed];
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      eaxFixed[loopcf] = Math.exp(-alphaFixed[loopcf] * x - 0.5 * alphaFixed[loopcf] * alphaFixed[loopcf]);
    }
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      b += discountedCashFlowIbor[loopcf] * eaxIbor[loopcf];
    }
    double c = 0;
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      c -= discountedCashFlowFixed[loopcf] * eaxFixed[loopcf];
    }
    double bp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      bp += -discountedCashFlowIbor[loopcf] * alphaIbor[loopcf] * eaxIbor[loopcf];
    }
    double cp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      cp -= -discountedCashFlowFixed[loopcf] * alphaFixed[loopcf] * eaxFixed[loopcf];
    }
    double bpp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      bpp += discountedCashFlowIbor[loopcf] * alphaIbor[loopcf] * alphaIbor[loopcf] * eaxIbor[loopcf];
    }
    double cpp = 0;
    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      cpp -= discountedCashFlowFixed[loopcf] * alphaFixed[loopcf] * alphaFixed[loopcf] * eaxFixed[loopcf];
    }
    double c2 = c * c;
    double c3 = c2 * c;
    double A0 = b / c;
    double A1 = bp / c - b * cp / c2;
    double A2 = bpp / c - (2 * bp * cp + b * cpp) / c2 + 2 * b * cp * cp / c3;
    
    // Backward sweep
    int nbA = 3;
    double[] bBar = new double[nbA];
    double[] cBar = new double[nbA];
    double[] c2Bar = new double[nbA];
    double[] c3Bar = new double[nbA];
    double[] bpBar = new double[nbA];
    double[] cpBar = new double[nbA];
    double[] bppBar = new double[nbA];
    double[] cppBar = new double[nbA];
    bppBar[2] += 1.0d / c * ABar[2];
    cppBar[2] += -b / c2 * ABar[2];
    bpBar[2] += -2 * cp / c2 * ABar[2];
    cpBar[2] += (-2 * bp / c2 + 4 * b * cp / c3) * ABar[2];
    bBar[2] += (-cpp / c2 + 2 * cp * cp / c3) * ABar[2];
    cBar[2] += -bpp / c2 * ABar[2];
    bBar[2] += (-cpp / c2 + 2 * cp * cp / c3) * ABar[2];
    c2Bar[2] += (2 * bp * cp + b * cpp) / (c2 * c2) * ABar[2];
    c3Bar[2] += -2 * b * cp * cp / (c3 * c3) * ABar[2];

    bpBar[1] += 1 / c * ABar[1];
    cpBar[1] += b / c2 * ABar[1];
    bBar[1] += -cp / c2 * ABar[1];
    cBar[1] += -bp / c2 * ABar[1];
    c2Bar[1] += b * cp / (c2 * c2) * ABar[1];

    bBar[0] += 1.0d / c * ABar[0];
    cBar[0] += b * ABar[0];

    for (int i = 0; i < nbA; i++) {
      c2Bar[i] += c * c3Bar[i];
      cBar[i] += c2 * c3Bar[i];
      cBar[i] = 2 * c * c2Bar[i];
    }

    for (int loopcf = 0; loopcf < nbPaymentsIbor; loopcf++) {
      for (int i = 0; i < nbA; i++) {
        discountedCashFlowIborBar[i][loopcf] += eaxIbor[loopcf] * bBar[0];
        discountedCashFlowIborBar[i][loopcf] += alphaIbor[loopcf] * eaxIbor[loopcf] * bpBar[0];
        discountedCashFlowIborBar[i][loopcf] += alphaIbor[loopcf] * alphaIbor[loopcf] * eaxIbor[loopcf] * bppBar[0];
      }
    }

    for (int loopcf = 0; loopcf < nbPaymentsFixed; loopcf++) {
      for (int i = 0; i < nbA; i++) {
        discountedCashFlowFixedBar[i][loopcf] += eaxFixed[loopcf] * bBar[0];
        discountedCashFlowFixedBar[i][loopcf] += alphaFixed[loopcf] * eaxFixed[loopcf] * bpBar[0];
        discountedCashFlowFixedBar[i][loopcf] += alphaFixed[loopcf] * alphaFixed[loopcf] * eaxFixed[loopcf] * bppBar[0];
      }
    }
    
    return new double[] {A0, A1, A2}; // 
  }

}
