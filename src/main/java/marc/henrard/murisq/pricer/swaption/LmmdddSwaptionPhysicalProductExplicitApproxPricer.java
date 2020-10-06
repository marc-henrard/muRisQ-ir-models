/**
 * Copyright (C) 2010 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swaption;

import java.time.ZonedDateTime;
import java.util.Arrays;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.impl.rate.swap.CashFlowEquivalentCalculator;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.NotionalExchange;
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
    int nbCFInit = cfe.getPaymentEvents().size();
    double[] cfTimesInit = new double[nbCFInit]; // times, not sorted
    double[] cfAmountsInit = new double[nbCFInit]; 
    for (int loopcf = 0; loopcf < nbCFInit; loopcf++) {
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
    for (int loopcf = 0; loopcf < nbCFInit; loopcf++) {
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

}
