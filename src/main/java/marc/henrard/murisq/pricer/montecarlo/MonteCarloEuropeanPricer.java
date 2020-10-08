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
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalent;
import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;

/**
 * Generic Monte Carlo pricer for European options.
 * @author marc
 *
 * @param <P> the type of product to be priced 
 * @param <M> the single currency model
 */
public interface MonteCarloEuropeanPricer<P extends ResolvedProduct, M extends SingleCurrencyModelParameters>  {
  
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
   * Generate the multi-curve equivalent for the instrument.
   * @return the multi-curve equivalent
   */
  abstract MulticurveEquivalent multicurveEquivalent(P product);
  
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
   * 
   * @param multicurve the multi-curve
   * @return the numeraire value
   */
  abstract double numeraireInitialValue(RatesProvider multicurve);
  
  /**
   * The initial values relevant for the model and the instrument.
   * 
   * @param mce  the multi-curve equivalent representation of the instrument
   * @param multicurve  the multi-curve
   * @param model the model
   * @return the initial values
   */
  abstract MulticurveEquivalentValues initialValues(
      MulticurveEquivalent mce,
      RatesProvider multicurve, 
      M model);
  
  /**
   * Evolves the model up to the expiry date/time.
   * 
   * @param numeraireInitialValue  the initial numeraire value
   * @param initialValues  the initial values for the multi-curve equivalent
   * @param expiry  the option expiry date/time
   * @param numberPaths  the number of paths to use in the Monte Carlo
   * @return the evolved quantities, one element for each path
   */
  abstract List<MulticurveEquivalentValues> evolve(
      MulticurveEquivalentValues initialValues,
      ZonedDateTime expiry,
      int numberPaths);
  
  /**
   * Aggregate different quantity simulated into a value for each path.
   * <P>
   * The aggregation consists in applying the product specific quantities to the model quantities 
   * and multiplying by the numeraire. The starting numeraire is not applied to the results.
   * 
   * @param me  the multi-curve equivalent
   * @param valuesExpiry  the values at expiry for the model quantities; one value for each path
   * @param model  the interest rate model
   * @return the aggregated value
   */
  abstract DoubleArray aggregation(
      MulticurveEquivalent me,
      List<MulticurveEquivalentValues> valuesExpiry, 
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

    MulticurveEquivalent mce = multicurveEquivalent(product);
    MulticurveEquivalentValues initialValues = initialValues(mce, multicurve, model);
    Triple<Integer, Integer, Integer> decomposition = decomposition(); // fullblocks, path block, residual
    double pv = 0.0;
    for (int loopblock = 0; loopblock < decomposition.getFirst(); loopblock++) {
      List<MulticurveEquivalentValues> valuesExpiry =
          evolve(initialValues, mce.getDecisionTime(), decomposition.getSecond());
      pv += aggregation(mce, valuesExpiry, model).sum();
    }
    if (decomposition.getThird() > 0) { // Residual number of path if non zero.
      List<MulticurveEquivalentValues> valuesExpiryResidual =
          evolve(initialValues, mce.getDecisionTime(), decomposition.getThird());
      pv += aggregation(mce, valuesExpiryResidual, model).sum();
    }
    double initialNumeraireValue = numeraireInitialValue(multicurve);
    pv = pv /getNbPaths() * initialNumeraireValue;
    return pv;
  }
  
}
