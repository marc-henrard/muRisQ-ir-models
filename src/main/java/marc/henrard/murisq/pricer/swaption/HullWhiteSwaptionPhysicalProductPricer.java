/**
 * Copyright (C) 2011 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.LocalDate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.DoubleArrayMath;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.math.impl.statistics.distribution.NormalDistribution;
import com.opengamma.strata.math.impl.statistics.distribution.ProbabilityDistribution;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.SettlementType;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;
import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantModelParameters;
import marc.henrard.murisq.pricer.swap.CashFlowEquivalentCalculator;

/**
 * Pricing of European physical settlement swaptions in the Hull-White one-factor with deterministic multiplicative spread.
 * <p>
 * Literature Reference: 
 * Henrard, M. "The Irony in the derivatives discounting Part II: the crisis", Wilmott Journal, 2010, 2, 301-316
 * Implementation reference:
 * Henrard, M. Hull-White one factor model: results and implementation, muRisQ Model description, May 2020.
 * 
 * @author Marc Henrard
 */
public class HullWhiteSwaptionPhysicalProductPricer
    extends SingleCurrencyModelSwaptionPhysicalProductPricer {

  /** The Hull-White formulas. */
  public final static HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS = 
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  /** Normal distribution function. */
  private static final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

  /**
   * The small parameter.
   */
  private static final double SMALL = 1.0e-9;

  /**
   * Default implementation.
   */
  public static final HullWhiteSwaptionPhysicalProductPricer DEFAULT =
      new HullWhiteSwaptionPhysicalProductPricer(DiscountingPaymentPricer.DEFAULT);

  /**
   * Pricer for {@link Payment}.
   */
  private final DiscountingPaymentPricer paymentPricer;

  /**
   * Creates an instance.
   * 
   * @param paymentPricer  the pricer for {@link Payment}
   */
  public HullWhiteSwaptionPhysicalProductPricer(DiscountingPaymentPricer paymentPricer) {
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "paymentPricer");
  }

  @Override
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    ArgChecker.isTrue(model instanceof HullWhiteOneFactorPiecewiseConstantModelParameters,
        "Parameters must be of the type HullWhiteOneFactorPiecewiseConstantModelParameters");
    HullWhiteOneFactorPiecewiseConstantModelParameters hw = (HullWhiteOneFactorPiecewiseConstantModelParameters) model;
    validate(swaption, multicurve, hw);
    LocalDate valuationDate = multicurve.getValuationDate();
    ResolvedSwap swap = swaption.getUnderlying();
    LocalDate expiryDate = swaption.getExpiryDate();
    double expiryTime = hw.getTimeMeasure().relativeTime(valuationDate, expiryDate);
    if (expiryDate.isBefore(multicurve.getValuationDate())) { // Option has expired already
      return CurrencyAmount.of(swap.getLegs().get(0).getCurrency(), 0d);
    }
    ResolvedSwapLeg cashFlowEquiv = CashFlowEquivalentCalculator.cashFlowEquivalentSwap(swap, multicurve);
    int nbCf = cashFlowEquiv.getPaymentEvents().size();
    double[] alpha = new double[nbCf];
    double[] discountedCashFlow = new double[nbCf];
    double[] t = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      NotionalExchange payment = (NotionalExchange) cashFlowEquiv.getPaymentEvents().get(loopcf);
      LocalDate maturityDate = payment.getPaymentDate();
      t[loopcf] = hw.getTimeMeasure().relativeTime(valuationDate, maturityDate);
      alpha[loopcf] = FORMULAS.alphaRatioDiscountFactors(hw.getParametersStrata(), 0.0d, expiryTime, t[0], t[loopcf]);
      discountedCashFlow[loopcf] = paymentPricer.presentValueAmount(payment.getPayment(), multicurve);
    }
    double omega = (swap.getLegs(SwapLegType.FIXED).get(0).getPayReceive().isPay() ? -1d : 1d);
    double kappa = computeKappa(hw, discountedCashFlow, alpha, omega);
    double pv = 0.0;
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      pv += discountedCashFlow[loopcf] * NORMAL.getCDF(omega * (kappa + alpha[loopcf]));
    }
    return CurrencyAmount.of(cashFlowEquiv.getCurrency(), pv * (swaption.getLongShort().isLong() ? 1d : -1d));
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the currency exposure of the swaption product.
   * 
   * @param swaption  the product
   * @param ratesProvider  the rates provider
   * @param hwProvider  the Hull-White model parameter provider
   * @return the currency exposure
   */
  public MultiCurrencyAmount currencyExposure(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    return MultiCurrencyAmount.of(presentValue(swaption, multicurve, model));
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the swaption product.
   * <p>
   * The present value sensitivity of the product is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param swaption  the product
   * @param ratesProvider  the rates provider
   * @param hwProvider  the Hull-White model parameter provider
   * @return the point sensitivity to the rate curves
   */
  public PointSensitivityBuilder presentValueSensitivityRates(
      ResolvedSwaption swaption,
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    ArgChecker.isTrue(model instanceof HullWhiteOneFactorPiecewiseConstantModelParameters,
        "Parameters must be of the type HullWhiteOneFactorPiecewiseConstantModelParameters");
    HullWhiteOneFactorPiecewiseConstantModelParameters hw = (HullWhiteOneFactorPiecewiseConstantModelParameters) model;
    validate(swaption, multicurve, hw);
    LocalDate valuationDate = multicurve.getValuationDate();
    ResolvedSwap swap = swaption.getUnderlying();
    LocalDate expiryDate = swaption.getExpiryDate();
    double expiryTime = hw.getTimeMeasure().relativeTime(valuationDate, expiryDate);
    if (expiryDate.isBefore(multicurve.getValuationDate())) { // Option has expired already
      return PointSensitivityBuilder.none();
    }
    ImmutableMap<Payment, PointSensitivityBuilder> cashFlowEquivSensi =
        CashFlowEquivalentCalculator.cashFlowEquivalentAndSensitivitySwap(swap, multicurve);
    ImmutableList<Payment> list = cashFlowEquivSensi.keySet().asList();
    ImmutableList<PointSensitivityBuilder> listSensi = cashFlowEquivSensi.values().asList();
    int nbCf = list.size();
    double[] alpha = new double[nbCf];
    double[] discountedCashFlow = new double[nbCf];
    double[] t = new double[nbCf];
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      Payment payment = list.get(loopcf);
      t[loopcf] = hw.getTimeMeasure().relativeTime(valuationDate, payment.getDate());
      alpha[loopcf] = FORMULAS.alphaRatioDiscountFactors(hw.getParametersStrata(), 0.0d, expiryTime, t[0], t[loopcf]);
      discountedCashFlow[loopcf] = paymentPricer.presentValueAmount(payment, multicurve);
    }
    double omega = (swap.getLegs(SwapLegType.FIXED).get(0).getPayReceive().isPay() ? -1d : 1d);
    double kappa = computeKappa(hw, discountedCashFlow, alpha, omega);
    PointSensitivityBuilder point = PointSensitivityBuilder.none();
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      Payment payment = list.get(loopcf);
      double cdf = NORMAL.getCDF(omega * (kappa + alpha[loopcf]));
      point = point.combinedWith(paymentPricer.presentValueSensitivity(payment, multicurve).multipliedBy(cdf));
      if (!listSensi.get(loopcf).equals(PointSensitivityBuilder.none())) {
        point = point.combinedWith(listSensi.get(loopcf)
            .multipliedBy(cdf * multicurve.discountFactor(payment.getCurrency(), payment.getDate())));
      }
    }
    return swaption.getLongShort().isLong() ? point : point.multipliedBy(-1d);
  }

  //-------------------------------------------------------------------------
  // validate that the rates and volatilities providers are coherent
  private void validate(ResolvedSwaption swaption, RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantModelParameters hwProvider) {
    ArgChecker.isTrue(hwProvider.getValuationDateTime().toLocalDate().equals(ratesProvider.getValuationDate()),
        "Hull-White model data and rate data should be for the same date");
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), "underlying swap should be single currency");
    ArgChecker.isTrue(swaption.getSwaptionSettlement().getSettlementType().equals(SettlementType.PHYSICAL),
        "swaption should be physical settlement");
  }

  // handling short time to expiry
  private double computeKappa(
      HullWhiteOneFactorPiecewiseConstantModelParameters parameters,
      double[] discountedCashFlow, 
      double[] alpha, 
      double omega) {
    
    double kappa = 0d;
    if (DoubleArrayMath.fuzzyEqualsZero(alpha, SMALL)) { // threshold coherent to rootfinder in kappa computation
      double totalPv = DoubleArrayMath.sum(discountedCashFlow);
      kappa = totalPv * omega > 0d ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    } else {
      kappa = FORMULAS.kappa(DoubleArray.ofUnsafe(discountedCashFlow), DoubleArray.ofUnsafe(alpha));
    }
    return kappa;
  }

}
