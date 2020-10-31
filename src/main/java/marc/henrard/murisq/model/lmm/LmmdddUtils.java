/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;

/**
 * Utilities related to the Libor Market Model with displaced diffusion and deterministic spread.
 * 
 * @author Marc Henrard
 */
public class LmmdddUtils {
  
  private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;

  /**
   * The weights of different forward rates in a forward swap rate.
   * <p>
   * The weights are model independent and represent \partial S / \partial F_j. 
   * They are denoted \bar w^S_j in implementation notes
   * 
   * @param swap  the swap
   * @param multicurve  the multi-curve framework
   * @return the weights
   */
  public static DoubleArray swapDynamicInitialFreezeWeightsForward (
      ResolvedSwap swap,
      RatesProvider multicurve) {

    ArgChecker.isTrue(swap.getLegs(SwapLegType.FIXED).size() == 1, "swap must have one fixed leg");
    ArgChecker.isTrue(swap.getLegs(SwapLegType.IBOR).size() == 1, "swap must have one Ibor leg");
    ResolvedSwapLeg fixedLeg = swap.getLegs(SwapLegType.FIXED).get(0);
    ResolvedSwapLeg iborLeg = swap.getLegs(SwapLegType.IBOR).get(0);
    ArgChecker.isTrue(fixedLeg.getCurrency().equals(iborLeg.getCurrency()), "both legs must be inthe same currency");
    Currency ccy = fixedLeg.getCurrency();
    DiscountFactors dsc = multicurve.discountFactors(ccy);
    ArgChecker.isTrue(iborLeg.getPaymentEvents().size() == 0, "swap Ibor leg must have no payment events");
    double swapRate = PRICER_SWAP.parRate(swap, multicurve);
    double annuity = Math.abs(PRICER_SWAP.getLegPricer().pvbp(fixedLeg, multicurve)
        / fixedLeg.findNotional(fixedLeg.getStartDate()).get().getAmount());
    // floating leg figures
    List<double[]> iborLegFigures = iborLegFigures(dsc, multicurve, iborLeg);
    double[] forwardRatesDsc = iborLegFigures.get(0); // Discounting forward rate on accrual dates
    double[] beta = iborLegFigures.get(1); // Ibor dsc forward spread
    double[] deltaIbor = iborLegFigures.get(2); 
    double[] dscFactorsIbor = iborLegFigures.get(3); 
    double[] timeIbor = iborLegFigures.get(4); 
    // Fixed leg figures
    Triple<double[], double[], double[]> fixedLegFigures = fixegLegFigures(dsc, fixedLeg);
    double[] deltaFixed = fixedLegFigures.getFirst(); 
    double[] timeFixed = fixedLegFigures.getSecond(); 
    double[] dscFactorsFixed = fixedLegFigures.getThird(); 
    return weights(annuity, swapRate, 
        forwardRatesDsc, beta, deltaIbor, dscFactorsIbor, timeIbor, 
        deltaFixed, timeFixed, dscFactorsFixed);
  }

  /**
   * The weights of different forward rates in a forward swap rate adjusted by the model dynamic.
   * <p>
   * The weights are model dependent and represent  \phi(F_j) / \phi(S) * \partial S / \partial F_j. 
   * They are denoted w^S_j in implementation notes
   * 
   * @param swap  the swap
   * @param multicurve  the multi-curve framework
   * @param lmm  the LMM displaced diffusion model, all displacements must be equal
   * @return the weights
   */
  public static DoubleArray swapDynamicInitialFreezeWeightsForwardModel (
      ResolvedSwap swap,
      RatesProvider multicurve,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm) {

    DoubleArray displacements = lmm.getDisplacements();
    for (int i = 1; i < displacements.size(); i++) {
      ArgChecker.isTrue(displacements.get(i) == displacements.get(0),
          "all displacements must be the same");
    }
    Function<Double, Double> phi = x -> x + displacements.get(0);  // Make sure compatible with LMM
    ArgChecker.isTrue(swap.getLegs(SwapLegType.FIXED).size() == 1, "swap must have one fixed leg");
    ArgChecker.isTrue(swap.getLegs(SwapLegType.IBOR).size() == 1, "swap must have one Ibor leg");
    ResolvedSwapLeg fixedLeg = swap.getLegs(SwapLegType.FIXED).get(0);
    ResolvedSwapLeg iborLeg = swap.getLegs(SwapLegType.IBOR).get(0);
    int nbIbor = iborLeg.getPaymentPeriods().size();
    ArgChecker.isTrue(fixedLeg.getCurrency().equals(iborLeg.getCurrency()), "both legs must be inthe same currency");
    Currency ccy = fixedLeg.getCurrency();
    DiscountFactors dsc = multicurve.discountFactors(ccy);
    ArgChecker.isTrue(iborLeg.getPaymentEvents().size() == 0, "swap Ibor leg must have no payment events");
    double swapRate = PRICER_SWAP.parRate(swap, multicurve);
    double annuity = Math.abs(PRICER_SWAP.getLegPricer().pvbp(fixedLeg, multicurve) /
        fixedLeg.findNotional(fixedLeg.getStartDate()).get().getAmount());
    // floating leg figures
    List<double[]> iborLegFigures = iborLegFigures(dsc, multicurve, iborLeg);
    double[] forwardRatesDsc = iborLegFigures.get(0); // Discounting forward rate on accrual dates
    double[] beta = iborLegFigures.get(1); // Ibor dsc forward spread
    double[] deltaIbor = iborLegFigures.get(2);
    double[] dscFactorsIbor = iborLegFigures.get(3);
    double[] timeIbor = iborLegFigures.get(4);
    // Fixed leg figures
    Triple<double[], double[], double[]> fixedLegFigures = fixegLegFigures(dsc, fixedLeg);
    double[] deltaFixed = fixedLegFigures.getFirst();
    double[] timeFixed = fixedLegFigures.getSecond();
    double[] dscFactorsFixed = fixedLegFigures.getThird();
    // Swap volatilities
    DoubleArray weightSBar = weights(annuity, swapRate,
        forwardRatesDsc, beta, deltaIbor, dscFactorsIbor, timeIbor,
        deltaFixed, timeFixed, dscFactorsFixed);
    double[] weightS = new double[nbIbor];
    for (int loopibor = 0; loopibor < nbIbor; loopibor++) {
      weightS[loopibor] =
          phi.apply(forwardRatesDsc[loopibor]) / phi.apply(swapRate) * weightSBar.get(loopibor);
    }
    return DoubleArray.ofUnsafe(weightS);
  }
  
  /**
   * The swap rate volatilities in the dynamic of the model.
   * <p>
   * Denoted \bar \gamma_S_j in implementation notes. 
   * The vector of volatilities represents the load of the different factors.
   * 
   * @param swap  the swap
   * @param multicurve  the multi-curve framework
   * @param lmm  the LMM displaced diffusion model, all displacements must be equal
   * @return  the volatilities
   */
  public static DoubleArray swapDynamicInitialFreezeVolatilities (
      ResolvedSwap swap,
      RatesProvider multicurve,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm) {

    DoubleArray displacements = lmm.getDisplacements();
    for (int i = 1; i < displacements.size(); i++) {
      ArgChecker.isTrue(displacements.get(i) == displacements.get(0),
          "all displacements must be the same");
    }
    Function<Double, Double> phi = x -> x + displacements.get(0);  // Make sure compatible with LMM
    ArgChecker.isTrue(swap.getLegs(SwapLegType.FIXED).size() == 1, "swap must have one fixed leg");
    ArgChecker.isTrue(swap.getLegs(SwapLegType.IBOR).size() == 1, "swap must have one Ibor leg");
    ResolvedSwapLeg fixedLeg = swap.getLegs(SwapLegType.FIXED).get(0);
    ResolvedSwapLeg iborLeg = swap.getLegs(SwapLegType.IBOR).get(0);
    int nbIbor = iborLeg.getPaymentPeriods().size();
    ArgChecker.isTrue(fixedLeg.getCurrency().equals(iborLeg.getCurrency()), "both legs must be inthe same currency");
    Currency ccy = fixedLeg.getCurrency();
    DiscountFactors dsc = multicurve.discountFactors(ccy);
    ArgChecker.isTrue(iborLeg.getPaymentEvents().size() == 0, "swap Ibor leg must have no payment events");
    double swapRate = PRICER_SWAP.parRate(swap, multicurve);
    double annuity = Math.abs(PRICER_SWAP.getLegPricer().pvbp(fixedLeg, multicurve) /
        fixedLeg.findNotional(fixedLeg.getStartDate()).get().getAmount());
    // floating leg figures
    List<double[]> iborLegFigures = iborLegFigures(dsc, multicurve, iborLeg);
    double[] forwardRatesDsc = iborLegFigures.get(0); // Discounting forward rate on accrual dates
    double[] beta = iborLegFigures.get(1); // Ibor dsc forward spread
    double[] deltaIbor = iborLegFigures.get(2);
    double[] dscFactorsIbor = iborLegFigures.get(3);
    double[] timeIbor = iborLegFigures.get(4);
    // Fixed leg figures
    Triple<double[], double[], double[]> fixedLegFigures = fixegLegFigures(dsc, fixedLeg);
    double[] deltaFixed = fixedLegFigures.getFirst();
    double[] timeFixed = fixedLegFigures.getSecond();
    double[] dscFactorsFixed = fixedLegFigures.getThird();
    // Swap volatilities
    DoubleArray weightSBar = weights(annuity, swapRate,
        forwardRatesDsc, beta, deltaIbor, dscFactorsIbor, timeIbor,
        deltaFixed, timeFixed, dscFactorsFixed);
    double[] weightS = new double[nbIbor];
    for (int loopibor = 0; loopibor < nbIbor; loopibor++) {
      weightS[loopibor] =
          phi.apply(forwardRatesDsc[loopibor]) / phi.apply(swapRate) * weightSBar.get(loopibor);
    }
    int[] iborIndices = lmm.getIborTimeIndex(timeIbor);
    double[] gammaS = new double[lmm.getFactorCount()]; // 2-factor model
    DoubleMatrix volatilityLmm = lmm.getVolatilities();
    for (int loopibor = 0; loopibor < nbIbor; loopibor++) {
      for (int i = 0; i < lmm.getFactorCount(); i++) {
        gammaS[i] += weightS[loopibor] * volatilityLmm.get(iborIndices[loopibor] - 1, i);
      }
    }
    return DoubleArray.ofUnsafe(gammaS);
  }
  
  private static DoubleArray weights(
      double annuity,
      double swapRate,
      double[] forwardRatesDsc,
      double[] beta,
      double[] deltaIbor,
      double[] dscFactorsIbor,
      double[] timeIbor,
      double[] deltaFixed,
      double[] timeFixed,
      double[] dscFactorsFixed) {

    int nbIborPeriods = forwardRatesDsc.length;
    int nbFixedPeriods = deltaFixed.length;
    double[] weights = new double[nbIborPeriods];
    for (int loopIbor = 0; loopIbor < nbIborPeriods; loopIbor++) {
      int ktilde = 0;
      while (timeFixed[ktilde] < timeIbor[loopIbor]) {
        ktilde++;
      }
      double factor1 = -deltaIbor[loopIbor] / (annuity * (1 + deltaIbor[loopIbor] * forwardRatesDsc[loopIbor]));
      double factor2 = -dscFactorsIbor[loopIbor + 1];
      for (int j = loopIbor + 2; j <= nbIborPeriods; j++) {
        factor2 += beta[j - 1] * dscFactorsIbor[j - 1] - dscFactorsIbor[j];
      }
      for (int j = ktilde; j < nbFixedPeriods; j++) {
        factor2 += swapRate * deltaFixed[j] * dscFactorsFixed[j];
      }
      weights[loopIbor] = factor1 * factor2;
    }
    return DoubleArray.ofUnsafe(weights);
  }
  
  private static List<double[]> iborLegFigures(
      DiscountFactors dsc,
      RatesProvider multicurve,
      ResolvedSwapLeg iborLeg) {

    int nbIborPeriods = iborLeg.getPaymentPeriods().size();
    double[] forwardRatesDsc = new double[nbIborPeriods]; // Discounting forward rate on accrual dates
    double[] beta = new double[nbIborPeriods]; // Ibor dsc forward spread
    double[] deltaIbor = new double[nbIborPeriods];
    double[] dscFactorsIbor = new double[nbIborPeriods + 1];
    double[] timeIbor = new double[nbIborPeriods + 1];
    timeIbor[0] = dsc.relativeYearFraction(iborLeg.getStartDate());
    dscFactorsIbor[0] = dsc.discountFactor(timeIbor[0]);
    for (int loopibor = 0; loopibor < nbIborPeriods; loopibor++) {
      RatePaymentPeriod periodPayment = (RatePaymentPeriod) iborLeg.getPaymentPeriods().get(loopibor);
      timeIbor[loopibor + 1] = dsc.relativeYearFraction(periodPayment.getEndDate());
      dscFactorsIbor[loopibor + 1] = dsc.discountFactor(timeIbor[loopibor + 1]);
      RateAccrualPeriod periodAccrual = periodPayment.getAccrualPeriods().get(0);
      IborIndexObservation obs = ((IborRateComputation) periodAccrual.getRateComputation()).getObservation();
      deltaIbor[loopibor] = periodAccrual.getYearFraction();
      forwardRatesDsc[loopibor] = (dscFactorsIbor[loopibor] / dscFactorsIbor[loopibor + 1] - 1.0) / deltaIbor[loopibor];
      double iborRate = multicurve.iborIndexRates(obs.getIndex()).rate(obs);
      beta[loopibor] = (1 + deltaIbor[loopibor] * iborRate) / (1 + deltaIbor[loopibor] * forwardRatesDsc[loopibor]);
    }
    List<double[]> result = new ArrayList<>();
    result.add(forwardRatesDsc);
    result.add(beta);
    result.add(deltaIbor);
    result.add(dscFactorsIbor);
    result.add(timeIbor);
    return result;
  }

  private static Triple<double[], double[], double[]> fixegLegFigures(
      DiscountFactors dsc,
      ResolvedSwapLeg fixedLeg) {

    int nbFixedPeriods = fixedLeg.getPaymentPeriods().size();
    double[] deltaFixed = new double[nbFixedPeriods];
    double[] timeFixed = new double[nbFixedPeriods];
    double[] dscFactorsFixed = new double[nbFixedPeriods];
    for (int loopfixed = 0; loopfixed < nbFixedPeriods; loopfixed++) {
      RatePaymentPeriod periodPayment = (RatePaymentPeriod) fixedLeg.getPaymentPeriods().get(loopfixed);
      timeFixed[loopfixed] = dsc.relativeYearFraction(periodPayment.getEndDate());
      dscFactorsFixed[loopfixed] = dsc.discountFactor(timeFixed[loopfixed]);
      RateAccrualPeriod periodAccrual = periodPayment.getAccrualPeriods().get(0);
      deltaFixed[loopfixed] = periodAccrual.getYearFraction();
    }
    return Triple.of(deltaFixed, timeFixed, dscFactorsFixed);
  }

}
