/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.montecarlo;

import java.time.ZonedDateTime;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.random.RandomNumberGenerator;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.ResolvedProduct;

import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentSchedule;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;

/**
 * Generic Monte Carlo pricer for path dependent products in the LMM with displaced diffusion.
 *
 * @param <P> the type of product to be priced 
 * 
 * @author Marc Henrard
 */
public interface LmmdddMonteCarloMultiDatePricer<P extends ResolvedProduct>
    extends MonteCarloMultiDatesPricer<P, LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters>{
  
  /**
   * Returns the mechanism to compute the LMM rate evolution mechanism.
   * 
   * @return the evolution
   */
  abstract LiborMarketModelMonteCarloEvolution getEvolution();
  
  /**
   * Returns the random number generator.
   * 
   * @return the random number generator
   */
  abstract RandomNumberGenerator getNumberGenerator();
  
  @Override
  default public int getNbFactors() {
    return getModel().getFactorCount();
  }

  @Override
  default public double numeraireInitialValue(RatesProvider multicurve) {
    // The pseudo-numeraire is the pseudo-discount factor on the last model date.
    DoubleArray iborTimes = getModel().getIborTimes();
    double numeraireTime = iborTimes.get(iborTimes.size() - 1);
    // Curve and model time measure must be compatible
    return multicurve.discountFactors(getModel().getCurrency()).discountFactor(numeraireTime);
  }

  @Override
  default public MulticurveEquivalentValues initialValues(
      MulticurveEquivalentSchedule mce, 
      RatesProvider multicurve) {
    
    // Model is on dsc forward rate, i.e. DSC forward on LIBOR periods, not instrument dependent
    DoubleArray iborTimes = getModel().getIborTimes();
    Currency ccy = getModel().getCurrency();
    DiscountFactors dsc = multicurve.discountFactors(ccy);
    double[] fwdDsc = new double[iborTimes.size() - 1];
    for (int i = 0; i < iborTimes.size() - 1; i++) {
      fwdDsc[i] = (dsc.discountFactor(iborTimes.get(i)) / dsc.discountFactor(iborTimes.get(i + 1)) - 1.0d) /
          getModel().getAccrualFactors().get(i);
    }
    // The forward rates are stored in ON equivalent values
    return MulticurveEquivalentValues.builder().onRates(DoubleArray.ofUnsafe(fwdDsc)).build();
  }

  @Override
  default public List<List<MulticurveEquivalentValues>> evolve(
      MulticurveEquivalentValues initialValues,
      List<ZonedDateTime> expiries,
      int numberSample) {
    
    return getEvolution()
        .evolveMultiSteps(expiries, initialValues, getModel(), getNumberGenerator(), numberSample);
  }

  /**
   * Returns the numeraire rebased discount factors at the different LMM dates.
   * <p>
   * The numeraire is the discount factor at the last date, hence the last discounting is 1 and the 
   * one at other dates are above one for positive rates.
   * 
   * @param model  the interest rate model
   * @param valuesExpiry  the modeled values at expiry, dimension: paths x expiries
   * @return  the rebased discount factors, dimension: path x expiries x LMM dates
   */
  default double[][][] discounting(List<List<MulticurveEquivalentValues>> valuesExpiries) {

    int nbPaths = valuesExpiries.size();
    int nbExpiries = valuesExpiries.get(0).size();
    int nbFwdPeriods = getModel().getIborPeriodsCount();
    double[] delta = getModel().getAccrualFactors().toArrayUnsafe();
    double[][][] discounting = new double[nbPaths][nbExpiries][nbFwdPeriods + 1];
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      for (int loopexp = 0; loopexp < nbExpiries; loopexp++) {
        MulticurveEquivalentValues valuePath = valuesExpiries.get(looppath).get(loopexp);
        double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
        discounting[looppath][loopexp][nbFwdPeriods] = 1.0;
        for (int loopdsc = nbFwdPeriods - 1; loopdsc >= 0; loopdsc--) {
          discounting[looppath][loopexp][loopdsc] =
              discounting[looppath][loopexp][loopdsc + 1] * (1.0 + valueFwdPath[loopdsc] * delta[loopdsc]);
        }
      }
    }
    return discounting;
  }

}
