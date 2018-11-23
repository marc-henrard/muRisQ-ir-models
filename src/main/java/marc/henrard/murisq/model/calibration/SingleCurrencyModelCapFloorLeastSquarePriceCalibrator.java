/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.calibration;

import java.util.BitSet;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebraFactory;
import com.opengamma.strata.math.impl.statistics.leastsquare.LeastSquareResults;
import com.opengamma.strata.math.impl.statistics.leastsquare.NonLinearLeastSquare;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorTrade;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.generic.SingleCurrencyModelTemplate;
import marc.henrard.murisq.pricer.capfloor.SingleCurrencyModelCapFloorTradePricer;

/**
 * Calibration by least square of cap/floor price for the Interest Rate single currency models.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelCapFloorLeastSquarePriceCalibrator {
  
  /** The precision used in least-square search. */
  private static final double DEFAULT_PRECISION = 1.0E-15;
  /** The least-square implementation. */
  private final static NonLinearLeastSquare LS =
      new NonLinearLeastSquare(DecompositionFactory.SV_COMMONS, MatrixAlgebraFactory.OG_ALGEBRA, DEFAULT_PRECISION);

  /** The template generating {@link RationalParameters}. */
  private final SingleCurrencyModelTemplate template;

  /**
   * Create an instance of the calibrator.
   * 
   * @param template  the rational model template
   * @return the instance
   */
  public static SingleCurrencyModelCapFloorLeastSquarePriceCalibrator of(SingleCurrencyModelTemplate template) {
    return new SingleCurrencyModelCapFloorLeastSquarePriceCalibrator(template);
  }
  
  /**
   * Private constructor. 
   * @param template
   */
  private SingleCurrencyModelCapFloorLeastSquarePriceCalibrator(SingleCurrencyModelTemplate template) {
    this.template = template;
  }
  
  /**
   * Calibrates the model parameters to a set of cap/floor trades by least-square approach.
   * <p>
   * The calibration uses explicit constraints and reduces the number of calibration parameters to 
   * the non-fixed parameters.
   * 
   * @param trades  the cap/floor trades
   * @param multicurve  the multi-curve provider
   * @param pricer  the cap/floor trade pricer
   * @return  the model parameters
   */
  public SingleCurrencyModelParameters calibrateConstraints(
      List<ResolvedIborCapFloorTrade> trades, 
      RatesProvider multicurve,
      SingleCurrencyModelCapFloorTradePricer pricer) {
    
    DoubleArray observedValues = DoubleArray.filled(trades.size()); // premium included in trade
    DoubleArray sigma = DoubleArray.filled(trades.size(), 1.0); // Scaling of errors
    ModelValuesConstraints function = new ModelValuesConstraints(trades, multicurve, template, pricer);
    // Jacobian by finite difference: TODO: improve
    VectorFieldFirstOrderDifferentiator differentiator = new VectorFieldFirstOrderDifferentiator();
    Function<DoubleArray, DoubleMatrix>  jacobien = differentiator.differentiate(function);
    
    DoubleArray startCalibratedParameters = initialGuessVariable(template.initialGuess(), template.getFixed());
    LeastSquareResults results = LS.solve(observedValues, 
        sigma,
        function,
        jacobien,
        startCalibratedParameters,
        template.getConstraints(),
        startCalibratedParameters.multipliedBy(0.05));
    DoubleArray parametersCalibrated = results.getFitParameters();
    return template.generate(allParametersFromCalibrated(
        parametersCalibrated, template.initialGuess(), template.getFixed()));
  }

  /**
   * Inner class computing the model price from the variable parameters.
   */
  static class ModelValuesConstraints implements Function<DoubleArray, DoubleArray> {

    /** The trades on which the model is calibrated. */ 
    private final List<ResolvedIborCapFloorTrade> trades;
    /** The multi-curve used to compute trade values. */
    private final RatesProvider multicurve;
    /** The model template. */
    private final SingleCurrencyModelTemplate template;
    /** The number of trades. */
    private final int nbTrades;
    /** The pricer for the trades. */
    private final SingleCurrencyModelCapFloorTradePricer pricer;

    // Constructor
    public ModelValuesConstraints(
        List<ResolvedIborCapFloorTrade> trades, 
        RatesProvider multicurve,
        SingleCurrencyModelTemplate template, 
        SingleCurrencyModelCapFloorTradePricer pricer) {
      this.trades = trades;
      this.multicurve = multicurve;
      this.template = template;
      nbTrades = trades.size();
      this.pricer = pricer;
    }

    /**
     * The parameters values only for the parameters to be calibrated; 
     * the fixed ones are extracted from the template initial guess.
     */
    @Override
    public DoubleArray apply(DoubleArray x) {
      int nbParam = template.parametersCount();
      int loopx = 0;
      double[] p = new double[nbParam];
      for (int i = 0; i < nbParam; i++) {
        if (template.getFixed().get(i)) {
          p[i] = template.initialGuess().get(i);
        } else {
          p[i] = x.get(loopx);
          loopx++;
        }
      }
      SingleCurrencyModelParameters model = template.generate(DoubleArray.ofUnsafe(p));
      Currency ccy = model.getCurrency();
      double[] modelPrice = new double[nbTrades];
      for (int i = 0; i < nbTrades; i++) {
        modelPrice[i] = pricer.presentValue(trades.get(i), multicurve, model)
            .convertedTo(ccy, multicurve).getAmount();
      }
      return DoubleArray.ofUnsafe(modelPrice);
    }
  }
  
  /**
   * Generates the array of initial guesses for the variable parameters to be calibrated.
   * 
   * @param initialGuess  the full initial guess vector, including the fixed parameters
   * @param fixed  the fixed parameters
   * @return  the initial guess array
   */
  private static DoubleArray initialGuessVariable(DoubleArray initialGuess, BitSet fixed) {
    double[] ig2 = new double[initialGuess.size() - fixed.cardinality()];
    int loopig = 0;
    for (int i = 0; i < initialGuess.size(); i++) {
      if (!fixed.get(i)) {
        ig2[loopig] = initialGuess.get(i);
        loopig++;
      }
    }
    return DoubleArray.ofUnsafe(ig2);
  }
  
  /**
   * Generates an array with all the parameters from the array with only the calibrated parameters.
   * 
   * @param calibrated  the calibrated parameters
   * @param initialGuess  the full initial guess vector, including the fixed parameters
   * @param fixed  the fixed parameters
   * @return  the full array of parameters
   */
  private static DoubleArray allParametersFromCalibrated(
      DoubleArray calibrated, 
      DoubleArray initialGuess, 
      BitSet fixed) {
    
    double[] p = new double[initialGuess.size()];
    int loopc = 0;
    for (int i = 0; i < initialGuess.size(); i++) {
      if (fixed.get(i)) {
        p[i] = initialGuess.get(i);
      } else {
        p[i] = calibrated.get(loopc);
        loopc++;
      }
    }
    return DoubleArray.ofUnsafe(p);
  }

}
