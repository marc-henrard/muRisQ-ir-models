/**
 * Copyright (C) 2010 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.value.ValueDerivatives;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.impl.rate.swap.CashFlowEquivalentCalculator;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;

/**
 * Pricing of European physical settlement swaptions in the Libor Market Model with deterministic multiplicative spread.
 * <p>
 * Pricing by efficient approximation. Implementation uses the "middle point" version.
 * Literature Reference: 
 * Henrard, M. (2010). Swaptions in Libor Market Model with local volatility. Wilmott Journal, 2010, 2, 135-154
 * Implementation reference:
 * Henrard, M. Libor/Forward Market Model in the multi-curve framework, muRisQ Model description, September 2020.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionPhysicalProductExplicitApproxPricer
   extends SingleCurrencyModelSwaptionPhysicalProductPricer {
  
  /**
   * Default implementation.
   */
  public static final LmmdddSwaptionPhysicalProductExplicitApproxPricer DEFAULT = 
      new LmmdddSwaptionPhysicalProductExplicitApproxPricer();
  /**
   * Creates an instance.
   */
  public LmmdddSwaptionPhysicalProductExplicitApproxPricer() {
  }

  @Override
  public CurrencyAmount presentValue(
      ResolvedSwaption swaption, 
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    // LMM and multi-curve times measurement must be compatible; instrument times must be close to model times
    ArgChecker.isTrue(model instanceof LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters);
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm = (LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) model;
    DiscountFactors dsc = multicurve.discountFactors(swaption.getCurrency());
    double amr = lmm.getMeanReversion();
    // 1. Swaption CFE preparation
    ResolvedSwapLeg cfe = CashFlowEquivalentCalculator
        .cashFlowEquivalentSwap(swaption.getUnderlying(), multicurve); // includes the spread adjusted notional
    int nbCfInit = cfe.getPaymentEvents().size();
    double[] cfTimesInit = new double[nbCfInit]; // times, not sorted
    double[] cfAmountsInit = new double[nbCfInit]; 
    for (int loopcf = 0; loopcf < nbCfInit; loopcf++) {
      cfTimesInit[loopcf] = lmm.getTimeMeasure().relativeTime(
          lmm.getValuationDate(),
          ((NotionalExchange)cfe.getPaymentEvents().get(loopcf)).getPaymentDate());
      cfAmountsInit[loopcf] = 
          ((NotionalExchange)cfe.getPaymentEvents().get(loopcf)).getPaymentAmount().getAmount();
    }
    ZonedDateTime expiry = swaption.getExpiry();
    double timeToExpiry = lmm.relativeTime(expiry);
    // 2. Model data
    int nbFactor = lmm.getFactorCount();
    final double[][] volLMM = lmm.getVolatilities().toArrayUnsafe();
    final double[] timeLmm = lmm.getIborTimes().toArrayUnsafe();
    // 3. Link cfe dates to lmm
    int[] indexCfDates = lmm.getIborTimeIndex(cfTimesInit);
    int indStart = Arrays.stream(indexCfDates).min().getAsInt();
    int indEnd = Arrays.stream(indexCfDates).max().getAsInt();
    
    int nbCfDatesLmm = indEnd - indStart + 1;
    double[] cfAmounts = new double[nbCfDatesLmm]; // add amounts at potentially missing intermediary dates and aggregate same dates
    for (int loopcf = 0; loopcf < nbCfInit; loopcf++) {
      cfAmounts[indexCfDates[loopcf] - indStart] += cfAmountsInit[loopcf];
    }
    double amount0 = cfAmounts[0];
    if (amount0 > 0.0d) { // Change sign to have standard call
      for (int i = 0; i < nbCfDatesLmm; i++) {
        cfAmounts[i] *= -1.0d;
      }
    }
    boolean isCall = (amount0 < 0);
    final double[] cfTimes = new double[nbCfDatesLmm];
    System.arraycopy(timeLmm, indStart, cfTimes, 0, nbCfDatesLmm);

    final double[] dfLmm = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      dfLmm[loopcf] = dsc.discountFactor(cfTimes[loopcf]);
    }
    final double[][] gammaLMM = new double[nbCfDatesLmm - 1][nbFactor];
    final double[] deltaSwap = new double[nbCfDatesLmm - 1];
    final double[] deltaModel = lmm.getAccrualFactors().toArrayUnsafe();
    System.arraycopy(deltaModel, indStart, deltaSwap, 0, nbCfDatesLmm - 1);
    final double[] aSwap = new double[nbCfDatesLmm - 1];
    final double[] aModel = lmm.getDisplacements().toArrayUnsafe();
    System.arraycopy(aModel, indStart, aSwap, 0, nbCfDatesLmm - 1);
    final double[] forwardLmm = new double[nbCfDatesLmm - 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      gammaLMM[loopcf] = volLMM[indStart + loopcf];
      forwardLmm[loopcf] = (dfLmm[loopcf] / dfLmm[loopcf + 1] - 1.0d) / deltaSwap[loopcf];
    }
    // 4. cfe modification (for roller coasters) - not implemented
    final double[] cfaMod = new double[nbCfDatesLmm + 1];
    final double cfaMod0 = cfAmounts[0];
    cfaMod[0] = cfaMod0; // modified strike
    cfaMod[1] = 0.0;
    System.arraycopy(cfAmounts, 1, cfaMod, 2, nbCfDatesLmm - 1);
    // 5. Pricing algorithm
    final double[] p0 = new double[nbCfDatesLmm];
    final double[] dP = new double[nbCfDatesLmm];
    double b0 = 0;
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      p0[loopcf] = dfLmm[loopcf] / dfLmm[0];
      dP[loopcf] = cfaMod[loopcf + 1] * p0[loopcf];
      b0 += dP[loopcf];
    }
    final double bK = -cfaMod0; // strike
    final double bM = (b0 + bK) / 2.0d;
    final double meanReversionImpact = Math.abs(amr) < 1.0E-6 ? 
        timeToExpiry : (Math.exp(2.0d * amr * timeToExpiry) - 1.0d) / (2.0d * amr); // To handle 0 mean reversion.
    final double[] rate0Ratio = new double[nbCfDatesLmm - 1];
    final double[][] mu0 = new double[nbCfDatesLmm - 1][nbFactor];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      rate0Ratio[loopcf] = (forwardLmm[loopcf] + aSwap[loopcf]) / (forwardLmm[loopcf] + 1 / deltaSwap[loopcf]);
    }
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      mu0[0][loopfact] = rate0Ratio[0] * gammaLMM[0][loopfact];
    }
    for (int loopcf = 1; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        mu0[loopcf][loopfact] = mu0[loopcf - 1][loopfact] + rate0Ratio[loopcf] * gammaLMM[loopcf][loopfact];
      }
    }
    final double[] tau = new double[nbCfDatesLmm];
    final double[] tau2 = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        tau2[loopcf + 1] += mu0[loopcf][loopfact] * mu0[loopcf][loopfact];
      }
      tau2[loopcf + 1] = tau2[loopcf + 1] * meanReversionImpact;
      tau[loopcf + 1] = Math.sqrt(tau2[loopcf + 1]);
    }
    double sumNum = -bM;
    double sumDen = 0;
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      sumNum += dP[loopcf] - dP[loopcf] * tau2[loopcf] / 2.0;
      sumDen += dP[loopcf] * tau[loopcf];
    }
    final double xBar = sumNum / sumDen;
    final double[] pM = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      pM[loopcf] = p0[loopcf] * (1 - xBar * tau[loopcf] - tau2[loopcf] / 2.0);
    }
    final double[] liborM = new double[nbCfDatesLmm - 1];
    final double[] alphaM = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      liborM[loopcf] = (pM[loopcf] / pM[loopcf + 1] - 1.0d) / deltaSwap[loopcf];
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      alphaM[loopcf] = cfaMod[loopcf + 1] * pM[loopcf] / bM;
    }
    final double[] rateMRatio = new double[nbCfDatesLmm - 1];
    final double[][] muM = new double[nbCfDatesLmm - 1][nbFactor];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      rateMRatio[loopcf] = (liborM[loopcf] + aSwap[loopcf]) / (liborM[loopcf] + 1 / deltaSwap[loopcf]);
    }
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      muM[0][loopfact] = rateMRatio[0] * gammaLMM[0][loopfact];
    }
    for (int loopcf = 1; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        muM[loopcf][loopfact] = muM[loopcf - 1][loopfact] + rateMRatio[loopcf] * gammaLMM[loopcf][loopfact];
      }
    }
    double normSigmaM = 0;
    final double[] sigmaM = new double[nbFactor];
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
        sigmaM[loopfact] += alphaM[loopcf + 1] * muM[loopcf][loopfact];
      }
      normSigmaM += sigmaM[loopfact] * sigmaM[loopfact];
    }
    double impliedBlackVol = Math.sqrt(normSigmaM * meanReversionImpact);
    double pv = dfLmm[0] * BlackFormulaRepository.price(b0, bK, 1.0d, impliedBlackVol, isCall);
    return CurrencyAmount.of(swaption.getCurrency(), 
        pv * (swaption.getLongShort().equals(LongShort.LONG) ? 1.0 : -1.0));
  }
  
  /**
   * Computes the present value and its sensitivity to interest rate for a swaption the LMM displace-diffusion model.
   * <p>
   * The sensitivity is computed with the model parameters constant. 
   * <p>
   * The result is expressed using the currency of the swaption.
   * 
   * @param swaption  the product to price
   * @param multicurve  the rates provider
   * @param model  the rational model parameters
   * @return the present value and the sensitivity
   */
  public Pair<CurrencyAmount, PointSensitivityBuilder> presentValueSensitivityRatesStickyModel(
      ResolvedSwaption swaption, 
      RatesProvider multicurve,
      SingleCurrencyModelParameters model) {

    // LMM and multi-curve times measurement must be compatible; instrument times must be close to model times
    ArgChecker.isTrue(model instanceof LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters);
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm =
        (LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters) model;
    Currency ccy = swaption.getCurrency();
    ResolvedSwap swap = swaption.getUnderlying();
    DiscountFactors dsc = multicurve.discountFactors(swaption.getCurrency());
    double amr = lmm.getMeanReversion();
    // 1. Swaption CFE preparation
    ImmutableMap<Payment, PointSensitivityBuilder> cfe =
        CashFlowEquivalentCalculator.cashFlowEquivalentAndSensitivitySwap(swap, multicurve);
    int nbCfInit = cfe.size();
    double[] cfTimesInit = new double[nbCfInit]; // times, not sorted
    double[] cfAmountsInit = new double[nbCfInit];
    List<PointSensitivityBuilder> psCfe = new ArrayList<>();
    int loopcfe = 0;
    for (Entry<Payment, PointSensitivityBuilder> entry : cfe.entrySet()) {
      cfTimesInit[loopcfe] = lmm.getTimeMeasure().relativeTime(lmm.getValuationDate(), entry.getKey().getDate());
      cfAmountsInit[loopcfe] = entry.getKey().getAmount();
      psCfe.add(entry.getValue());
      loopcfe++;
    }
    ZonedDateTime expiry = swaption.getExpiry();
    double timeToExpiry = lmm.relativeTime(expiry);
    // 2. Model data
    int nbFactor = lmm.getFactorCount();
    final double[][] volLMM = lmm.getVolatilities().toArrayUnsafe();
    final double[] timeLmm = lmm.getIborTimes().toArrayUnsafe();
    // 3. Link cfe dates to lmm
    int[] indexCfDates = lmm.getIborTimeIndex(cfTimesInit);
    int indStart = Arrays.stream(indexCfDates).min().getAsInt();
    int indEnd = Arrays.stream(indexCfDates).max().getAsInt();
    
    int nbCfDatesLmm = indEnd - indStart + 1;
    double[] cfAmounts = new double[nbCfDatesLmm]; // add amounts at potentially missing intermediary dates and aggregate same dates
    for (int loopcf = 0; loopcf < nbCfInit; loopcf++) {
      cfAmounts[indexCfDates[loopcf] - indStart] += cfAmountsInit[loopcf];
    }
    double amount0 = cfAmounts[0];
    if (amount0 > 0.0d) { // Change sign to have standard call
      for (int i = 0; i < nbCfDatesLmm; i++) {
        cfAmounts[i] *= -1.0d;
      }
    }
    boolean isCall = (amount0 < 0);
    final double[] cfTimes = new double[nbCfDatesLmm];
    System.arraycopy(timeLmm, indStart, cfTimes, 0, nbCfDatesLmm);
    final double[] dfLmm = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      dfLmm[loopcf] = dsc.discountFactor(cfTimes[loopcf]);
    }
    final double[][] gammaLMM = new double[nbCfDatesLmm - 1][nbFactor];
    final double[] deltaSwap = new double[nbCfDatesLmm - 1];
    final double[] deltaModel = lmm.getAccrualFactors().toArrayUnsafe();
    System.arraycopy(deltaModel, indStart, deltaSwap, 0, nbCfDatesLmm - 1);
    final double[] aSwap = new double[nbCfDatesLmm - 1];
    final double[] aModel = lmm.getDisplacements().toArrayUnsafe();
    System.arraycopy(aModel, indStart, aSwap, 0, nbCfDatesLmm - 1);
    double[] forwardLmm = new double[nbCfDatesLmm - 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      gammaLMM[loopcf] = volLMM[indStart + loopcf];
      forwardLmm[loopcf] = (dfLmm[loopcf] / dfLmm[loopcf + 1] - 1.0d) / deltaSwap[loopcf];
    }
    // 4. cfe modification (for roller coasters) - not implemented
    double[] cfaMod = new double[nbCfDatesLmm + 1];
    double cfaMod0 = cfAmounts[0];
    cfaMod[0] = cfaMod0; // modified strike
    cfaMod[1] = 0.0;
    System.arraycopy(cfAmounts, 1, cfaMod, 2, nbCfDatesLmm - 1);
    // 5. Pricing algorithm
    final double[] p0 = new double[nbCfDatesLmm];
    final double[] dP = new double[nbCfDatesLmm];
    double b0 = 0;
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      p0[loopcf] = dfLmm[loopcf] / dfLmm[0];
      dP[loopcf] = cfaMod[loopcf + 1] * p0[loopcf];
      b0 += dP[loopcf];
    }
    final double bK = -cfaMod[0]; // strike
    final double bM = (b0 + bK) / 2.0d;
    final double meanReversionImpact = Math.abs(amr) < 1.0E-6 ? 
        timeToExpiry : (Math.exp(2.0d * amr * timeToExpiry) - 1.0d) / (2.0d * amr); // To handle 0 mean reversion.
    final double[] rate0Ratio = new double[nbCfDatesLmm - 1];
    final double[][] mu0 = new double[nbCfDatesLmm - 1][nbFactor];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      rate0Ratio[loopcf] = (forwardLmm[loopcf] + aSwap[loopcf]) / (forwardLmm[loopcf] + 1 / deltaSwap[loopcf]);
    }
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      mu0[0][loopfact] = rate0Ratio[0] * gammaLMM[0][loopfact];
    }
    for (int loopcf = 1; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        mu0[loopcf][loopfact] = mu0[loopcf - 1][loopfact] + rate0Ratio[loopcf] * gammaLMM[loopcf][loopfact];
      }
    }
    final double[] tau = new double[nbCfDatesLmm];
    final double[] tau2 = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        tau2[loopcf + 1] += mu0[loopcf][loopfact] * mu0[loopcf][loopfact];
      }
      tau2[loopcf + 1] = tau2[loopcf + 1] * meanReversionImpact;
      tau[loopcf + 1] = Math.sqrt(tau2[loopcf + 1]);
    }
    double sumNum = -bM;
    double sumDen = 0;
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      sumNum += dP[loopcf] - dP[loopcf] * tau2[loopcf] / 2.0;
      sumDen += dP[loopcf] * tau[loopcf];
    }
    final double xBar = sumNum / sumDen;
    final double[] pM = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      pM[loopcf] = p0[loopcf] * (1 - xBar * tau[loopcf] - tau2[loopcf] / 2.0);
    }
    final double[] liborM = new double[nbCfDatesLmm - 1];
    final double[] alphaM = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      liborM[loopcf] = (pM[loopcf] / pM[loopcf + 1] - 1.0d) / deltaSwap[loopcf];
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      alphaM[loopcf] = cfaMod[loopcf + 1] * pM[loopcf] / bM;
    }
    final double[] rateMRatio = new double[nbCfDatesLmm - 1];
    final double[][] muM = new double[nbCfDatesLmm - 1][nbFactor];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      rateMRatio[loopcf] = (liborM[loopcf] + aSwap[loopcf]) / (liborM[loopcf] + 1 / deltaSwap[loopcf]);
    }
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      muM[0][loopfact] = rateMRatio[0] * gammaLMM[0][loopfact];
    }
    for (int loopcf = 1; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        muM[loopcf][loopfact] = muM[loopcf - 1][loopfact] + rateMRatio[loopcf] * gammaLMM[loopcf][loopfact];
      }
    }
    double normSigmaM = 0;
    final double[] sigmaM = new double[nbFactor];
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
        sigmaM[loopfact] += alphaM[loopcf + 1] * muM[loopcf][loopfact];
      }
      normSigmaM += sigmaM[loopfact] * sigmaM[loopfact];
    }
    double impliedBlackVol = Math.sqrt(normSigmaM * meanReversionImpact);
    ValueDerivatives black = BlackFormulaRepository.priceAdjoint(b0, bK, 1.0d, impliedBlackVol, isCall);
    double sign = (swaption.getLongShort().equals(LongShort.LONG) ? 1.0 : -1.0);
    double pvDouble = dfLmm[0] * black.getValue() * sign;
    CurrencyAmount pv = CurrencyAmount.of(ccy, pvDouble);
    // Backward sweep
    double pvBar = 1.0;
    double impliedBlackVolBar = dfLmm[0] * black.getDerivative(3) * sign * pvBar;
    double normSigmaMBar = meanReversionImpact / (2.0 * impliedBlackVol) * impliedBlackVolBar;
    double[] sigmaMBar = new double[nbFactor];
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      sigmaMBar[loopfact] = 2 * sigmaM[loopfact] * normSigmaMBar;
    }
    double[][] muMBar = new double[nbCfDatesLmm - 1][nbFactor];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        muMBar[loopcf][loopfact] = alphaM[loopcf + 1] * sigmaMBar[loopfact];
      }
    }
    for (int loopcf = nbCfDatesLmm - 3; loopcf >= 0; loopcf--) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        muMBar[loopcf][loopfact] += muMBar[loopcf + 1][loopfact];
      }
    }
    double[] rateMRatioBar = new double[nbCfDatesLmm - 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        rateMRatioBar[loopcf] += gammaLMM[loopcf][loopfact] * muMBar[loopcf][loopfact];
      }
    }
    double[] alphaMBar = new double[nbCfDatesLmm];
    for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
      for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
        alphaMBar[loopcf + 1] += muM[loopcf][loopfact] * sigmaMBar[loopfact];
      }
    }
    double[] liborMBar = new double[nbCfDatesLmm - 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      liborMBar[loopcf] = ((liborM[loopcf] + 1 / deltaModel[loopcf]) - (liborM[loopcf] + aSwap[loopcf])) /
          ((liborM[loopcf] + 1 / deltaModel[loopcf]) * (liborM[loopcf] + 1 / deltaModel[loopcf])) *
          rateMRatioBar[loopcf];
    }
    double[] pMBar = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      pMBar[loopcf] += 1.0 / pM[loopcf + 1] / deltaModel[loopcf] * liborMBar[loopcf];
      pMBar[loopcf + 1] += -pM[loopcf] / (pM[loopcf + 1] * pM[loopcf + 1]) / deltaModel[loopcf] * liborMBar[loopcf];
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      pMBar[loopcf] += cfaMod[loopcf + 1] / bM * alphaMBar[loopcf];
    }
    double xBarBar = 0.0;
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      xBarBar += -p0[loopcf] * tau[loopcf] * pMBar[loopcf];
    }
    double sumNumBar = 1.0 / sumDen * xBarBar;
    double sumDenBar = -sumNum / (sumDen * sumDen) * xBarBar;
    double[] tauBar = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      tauBar[loopcf] = -p0[loopcf] * xBar * pMBar[loopcf];
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      tauBar[loopcf] += dP[loopcf] * sumDenBar;
    }
    final double[] tau2Bar = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      tau2Bar[loopcf] = -p0[loopcf] / 2.0 * pMBar[loopcf];
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      tau2Bar[loopcf] += -dP[loopcf] / 2.0 * sumNumBar;
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      tau2Bar[loopcf + 1] += 1.0 / tau[loopcf + 1] / 2.0 * tauBar[loopcf + 1];
    }
    double[][] mu0Bar = new double[nbCfDatesLmm - 1][nbFactor];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        mu0Bar[loopcf][loopfact] = 2.0 * mu0[loopcf][loopfact] * meanReversionImpact * tau2Bar[loopcf + 1];
      }
    }
    for (int loopcf = nbCfDatesLmm - 3; loopcf >= 0; loopcf--) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        mu0Bar[loopcf][loopfact] += mu0Bar[loopcf + 1][loopfact];
      }
    }
    double[] rate0RatioBar = new double[nbCfDatesLmm - 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      for (int loopfact = 0; loopfact < nbFactor; loopfact++) {
        rate0RatioBar[loopcf] += gammaLMM[loopcf][loopfact] * mu0Bar[loopcf][loopfact];
      }
    }
    double bMBar = -sumNumBar;
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      bMBar += -cfaMod[loopcf + 1] * pM[loopcf] / (bM * bM) * alphaMBar[loopcf];
    }
    double bKBar = bMBar / 2.0;
    bKBar += dfLmm[0] * black.getDerivative(1) * sign * pvBar;
    double b0Bar = bMBar / 2.0;
    b0Bar += dfLmm[0] * black.getDerivative(0) * sign * pvBar;
    double[] dPBar = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      dPBar[loopcf] = b0Bar + tau[loopcf] * sumDenBar + (1.0 - tau2[loopcf] / 2.0) * sumNumBar;
    }
    double[] p0Bar = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      p0Bar[loopcf] =
          cfaMod[loopcf + 1] * dPBar[loopcf] + (1 - xBar * tau[loopcf] - tau2[loopcf] / 2.0) * pMBar[loopcf];
    }
    double[] cfaModBar = new double[nbCfDatesLmm + 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      cfaModBar[loopcf + 1] = p0[loopcf] * dPBar[loopcf] + pM[loopcf] / bM * alphaMBar[loopcf];
    }
    cfaModBar[0] += -bKBar;

    double[] forwardLmmBar = new double[nbCfDatesLmm - 1];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      forwardLmmBar[loopcf] =
          (1.0 / (forwardLmm[loopcf] + 1 / deltaModel[loopcf]) - (forwardLmm[loopcf] + aSwap[loopcf]) /
              ((forwardLmm[loopcf] + 1 / deltaModel[loopcf]) * (forwardLmm[loopcf] + 1 / deltaModel[loopcf]))) *
              rate0RatioBar[loopcf];
    }
    double[] dfLmmBar = new double[nbCfDatesLmm];
    for (int loopcf = 0; loopcf < nbCfDatesLmm - 1; loopcf++) {
      dfLmmBar[loopcf] += (1.0 / dfLmm[loopcf + 1]) / deltaModel[loopcf] * forwardLmmBar[loopcf];
      dfLmmBar[loopcf + 1] +=
          -dfLmm[loopcf] / (dfLmm[loopcf + 1] * dfLmm[loopcf + 1]) / deltaModel[loopcf] * forwardLmmBar[loopcf];
    }
    for (int loopcf = 1; loopcf < nbCfDatesLmm; loopcf++) {
      dfLmmBar[loopcf] += 1.0 / dfLmm[0] * p0Bar[loopcf];
      dfLmmBar[0] += -dfLmm[loopcf] / (dfLmm[0] * dfLmm[0]) * p0Bar[loopcf];
    }
    dfLmmBar[0] += black.getValue() * sign * pvBar;
    double[] cfAmountsBar = new double[nbCfDatesLmm];
    cfAmountsBar[0] = cfaModBar[0];
    System.arraycopy(cfaModBar, 2, cfAmountsBar, 1, nbCfDatesLmm - 1);

    if (amount0 > 0.0d) { // Change sign to have standard call
      for (int i = 0; i < nbCfDatesLmm; i++) {
        cfAmountsBar[i] *= -1.0d;
      }
    }
    double[] cfAmountsInitBar = new double[nbCfInit];
    for (int loopcf = 0; loopcf < nbCfInit; loopcf++) {
      cfAmountsInitBar[loopcf] = cfAmountsBar[indexCfDates[loopcf] - indStart];
    }
    PointSensitivityBuilder ps = PointSensitivityBuilder.none();
    for (int loopcf = 0; loopcf < nbCfInit; loopcf++) {
      ps = ps.combinedWith(psCfe.get(loopcf).multipliedBy(cfAmountsInitBar[loopcf]));
    }
    for (int loopcf = 0; loopcf < nbCfDatesLmm; loopcf++) {
      ps = ps.combinedWith(dsc.zeroRatePointSensitivity(cfTimes[loopcf]).multipliedBy(dfLmmBar[loopcf]));
    }
    return Pair.of(pv, ps);
  }

}
