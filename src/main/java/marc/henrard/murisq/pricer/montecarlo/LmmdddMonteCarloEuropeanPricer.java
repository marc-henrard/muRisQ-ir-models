/**
 * Copyright (C) 2015 - present by Marc Henrard.
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
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;

/**
 * Generic Monte Carlo pricer for European options in the LMM with displaced diffusion.
 *
 * @param <P> the type of product to be priced 
 * 
 * @author Marc Henrard
 */
public interface LmmdddMonteCarloEuropeanPricer<P extends ResolvedProduct>
  extends MonteCarloEuropeanPricer<P, LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters>{
  
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
      MulticurveEquivalent mce,
      RatesProvider multicurve) {

    // Model is on dsc forward rate, i.e. DSC forward on LIBOR periods
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
  public default List<MulticurveEquivalentValues> evolve(
      MulticurveEquivalentValues initialValues,
      ZonedDateTime expiry,
      int numberPaths) {

    return getEvolution()
        .evolveOneStep(expiry, initialValues, getModel(), getNumberGenerator(), numberPaths);
  }

  /**
   * Returns the numeraire rebased discount factors at the different LMM dates.
   * <p>
   * The numeraire is the discount factor at the last date, hence the last discounting is 1 and the 
   * one at other dates are above one for positive rates.
   * 
   * @param model  the interest rate model
   * @param valuesExpiry  the modeled values at expiry
   * @return  the rebased discount factors, dimension: path x dates
   */
  default double[][] discounting(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      List<MulticurveEquivalentValues> valuesExpiry) {

    int nbFwdPeriods = model.getIborPeriodsCount();
    int nbPathsDsc = valuesExpiry.size();
    double[] delta = model.getAccrualFactors().toArrayUnsafe();
    double[][] discounting = new double[nbPathsDsc][nbFwdPeriods + 1];
    for (int looppath = 0; looppath < nbPathsDsc; looppath++) {
      MulticurveEquivalentValues valuePath = valuesExpiry.get(looppath);
      double[] valueFwdPath = valuePath.getOnRates().toArrayUnsafe();
      discounting[looppath][nbFwdPeriods] = 1.0;
      for (int loopdsc = nbFwdPeriods - 1; loopdsc >= 0; loopdsc--) {
        discounting[looppath][loopdsc] =
            discounting[looppath][loopdsc + 1] * (1.0 + valueFwdPath[loopdsc] * delta[loopdsc]);
      }
    }
    return discounting;
  }
  
  /**
   * 
   * @param model
   * @param valuesExpiry  overnight rates, dimensions: paths x LMM periods
   * @return
   */
  default double[][] discountingFast(double[][] valuesExpiry) {
    int nbFwdPeriods = getModel().getIborPeriodsCount();
    int nbPathsDsc = valuesExpiry.length;
    double[] delta = getModel().getAccrualFactors().toArrayUnsafe();
    double[][] discounting = new double[nbPathsDsc][nbFwdPeriods + 1];
    for (int looppath = 0; looppath < nbPathsDsc; looppath++) {
      double[] valueFwdPath = valuesExpiry[looppath];
      discounting[looppath][nbFwdPeriods] = 1.0;
      for (int loopdsc = nbFwdPeriods - 1; loopdsc >= 0; loopdsc--) {
        discounting[looppath][loopdsc] =
            discounting[looppath][loopdsc + 1] * (1.0 + valueFwdPath[loopdsc] * delta[loopdsc]);
      }
    }
    return discounting;
  }

}
