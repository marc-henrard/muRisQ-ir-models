/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.montecarlo;

import java.time.ZonedDateTime;
import java.util.List;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.ResolvedProduct;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentSchedule;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;

/**
 * Generic Monte Carlo pricer for European options.
 * @author marc
 *
 * @param <P> the type of product to be priced 
 * @param <M> the single currency model
 */
public interface MonteCarloMultiDatesPricer<P extends ResolvedProduct, M extends SingleCurrencyModelParameters>  {
  
  /**
   * The number of paths to use in the simulation.
   * @return
   */
  abstract int getNbPaths();

  /**
   * Gets the model number of factors.
   * @return the generator
   */
  abstract int getNbFactors();

  /**
   * Gets the number of paths in a block for computation.
   * @return the number of paths.
   */
  abstract int getPathNumberBlock();
  
  /**
   * Returns the model underlying the Monte Carlo pricer
   * @return the model
   */
  abstract M getModel();
  
  /**
   * Generate the multi-curve equivalent schedule for the instrument.
   * @return the equivalent
   */
  abstract MulticurveEquivalentSchedule multicurveEquivalent(P product);
  
  /**
   * Returns the decomposition of the number of paths into blocks. 
   * @return The decomposition as a {@link Triple} of integers with the number of full blocks, 
   * the number of paths in each block and the residual number of paths.
   */
  public default Triple<Integer, Integer, Integer> decomposition(){
    int fullBlock = getNbPaths() / getPathNumberBlock();
    int residual = getNbPaths() - getPathNumberBlock() * fullBlock;
    return Triple.of(fullBlock, getPathNumberBlock(), residual);
  }
  
  /**
   * Returns the initial value of the numeraire
   * @param multicurve the multi-curve
   * @return the numeraire value
   */
  abstract double getNumeraireValue(RatesProvider multicurve);
  
  /**
   * The initial values relevant for the model and the instrument.
   * 
   * @param mce  the multi-curve equivalent representation of the instrument
   * @param multicurve  the multi-curve
   * @param model the model
   * @return the initial values
   */
  abstract MulticurveEquivalentValues initialValues(
      MulticurveEquivalentSchedule mce,
      RatesProvider multicurve, 
      M model);
  
  /**
   * Evolves the model up to the expiry date/time.
   * 
   * @param numeraireInitialValue  the initial numeraire value
   * @param initialValues  the initial values for the multi-curve equivalent
   * @param expiries  the option expiry dates/times
   * @param numberSample  the number of sample to use in the Monte Carlo
   * @return the evolved quantities, dimensions: paths x expiry
   */
  abstract List<List<MulticurveEquivalentValues>> evolve(
      MulticurveEquivalentValues initialValues,
      List<ZonedDateTime> expiries,
      int numberSample);
  
  /**
   * Aggregate different simulations into a value.
   * <P>
   * The aggregation consists in applying the product specific quantities to the model quantities 
   * and multiplying by the numeraire. The starting numeraire is not applied to the pv.
   * 
   * @param me  the multi-curve equivalent
   * @param product 
   * @param valuesExpiries  the values at the expiries for the numeraire and the model quantities, dimensions: paths x expiry
   * @param model
   * @return the values for each path, dimensions path x cash flows
   */
  abstract double[][] aggregation(
      MulticurveEquivalentSchedule me, // TODO: do we need also the full product?
      P product, 
      List<List<MulticurveEquivalentValues>> valuesExpiries, 
      M model);
  
  /**
   * Present value as a double.
   * 
   * @param product  the financial product to price
   * @param multicurve  the underlying multi-curve framework
   * @param model  the interest rate model
   * @return the present value
   */
  default double presentValueDouble(
      P product, 
      RatesProvider multicurve,
      M model) {

    MulticurveEquivalentSchedule mce = multicurveEquivalent(product);
    MulticurveEquivalentValues initialValues = initialValues(mce, multicurve, model);
    Triple<Integer, Integer, Integer> decomposition = decomposition(); // fullblocks, path block, residual
    double pv = 0.0;
    for (int loopblock = 0; loopblock < decomposition.getFirst(); loopblock++) {
      List<List<MulticurveEquivalentValues>> valuesExpiry =
          evolve(initialValues, mce.getDecisionTimes(), decomposition.getSecond());
      double[][] aggregation = aggregation(mce, product, valuesExpiry, model);
      for (int looppath = 0; looppath < decomposition.getSecond(); looppath++) {
        pv += DoubleArray.ofUnsafe(aggregation[looppath]).sum();
      }
    }
    if (decomposition.getThird() > 0) { // Residual number of path if non zero.
      List<List<MulticurveEquivalentValues>> valuesExpiryResidual =
          evolve(initialValues, mce.getDecisionTimes(), decomposition.getThird());
      double[][] aggregation = aggregation(mce, product, valuesExpiryResidual, model);
      for (int looppath = 0; looppath < decomposition.getThird(); looppath++) {
        pv += DoubleArray.ofUnsafe(aggregation[looppath]).sum();
      }
    }
    double initialNumeraireValue = getNumeraireValue(multicurve);
    pv = pv /getNbPaths() * initialNumeraireValue;
    return pv;
  }
  
}
