/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.risq.model.calibration;

import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebraFactory;
import com.opengamma.strata.math.impl.minimization.NonLinearParameterTransforms;
import com.opengamma.strata.math.impl.minimization.NonLinearTransformFunction;
import com.opengamma.strata.math.impl.statistics.leastsquare.LeastSquareResults;
import com.opengamma.strata.math.impl.statistics.leastsquare.LeastSquareResultsWithTransform;
import com.opengamma.strata.math.impl.statistics.leastsquare.NonLinearLeastSquare;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorTrade;

import marc.henrard.risq.model.rationalmulticurve.RationalParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTemplate;
import marc.henrard.risq.pricer.capfloor.RationalCapFloorTradePricer;

/**
 * Calibration by least square of cap/floor price for the Interest Rate rational multi-curve model.
 * 
 * @author Marc Henrard
 */
public class RationalCapFloorLeastSquareCalibrator {
  
  /** The precision used in least-square search. */
  private static final double DEFAULT_PRECISION = 1.0E-15;
  /** The least-square implementation. */
  private final static NonLinearLeastSquare LS =
      new NonLinearLeastSquare(DecompositionFactory.SV_COMMONS, MatrixAlgebraFactory.OG_ALGEBRA, DEFAULT_PRECISION);

  /** The template generating {@link RationalParameters}. */
  private final RationalTemplate template;

  /**
   * Create an instance of the calibrator.
   * 
   * @param template  the rational model template
   * @return the instance
   */
  public static RationalCapFloorLeastSquareCalibrator of(RationalTemplate template) {
    return new RationalCapFloorLeastSquareCalibrator(template);
  }
  
  /**
   * Private constructor. 
   * @param template
   */
  private RationalCapFloorLeastSquareCalibrator(RationalTemplate template) {
    this.template = template;
  }

  /**
   * Calibrates the model parameters to a set of cap/floor trades by least-square appraoch.
   * 
   * @param trades  the cap/floor trades
   * @param multicurve  the multi-curve provider
   * @param pricer  the cap/floor trade pricer
   * @return  the model parameters
   */
  public RationalParameters calibrate(
      List<ResolvedIborCapFloorTrade> trades, 
      RatesProvider multicurve,
      RationalCapFloorTradePricer pricer) {
    DoubleArray observedValues = DoubleArray.filled(trades.size()); // premium included in trade
    DoubleArray sigma = DoubleArray.filled(trades.size(), 1.0); // Scaling of errors
    ModelPrice function = new ModelPrice(trades, multicurve, template, pricer);
    // Jacobian by finite difference: TODO: improve
    VectorFieldFirstOrderDifferentiator differentiator = new VectorFieldFirstOrderDifferentiator();
    Function<DoubleArray, DoubleMatrix>  jacobien = differentiator.differentiate(function);
    DoubleArray startingPosition = template.initialGuess();
    NonLinearParameterTransforms transform = template.getTransform();
    NonLinearTransformFunction transFunc = new NonLinearTransformFunction(function, jacobien, transform);
    LeastSquareResults results = LS.solve(observedValues, 
        sigma,
        transFunc.getFittingFunction(), 
        transFunc.getFittingJacobian(), 
        transform.transform(startingPosition),
        transform.transform(startingPosition).multipliedBy(0.05));
    LeastSquareResultsWithTransform results2 = new LeastSquareResultsWithTransform(results, transform);
    DoubleArray parametersFitted = results2.getModelParameters();
    return template.generate(parametersFitted);
  }

  /**
   * Inner class computing the model price.
   */
  static class ModelPrice implements Function<DoubleArray, DoubleArray> {

    private final List<ResolvedIborCapFloorTrade> trades;
    private final RatesProvider multicurve;
    private final RationalTemplate template;
    private final int nbSwaptions;
    private final RationalCapFloorTradePricer pricer;

    public ModelPrice(
        List<ResolvedIborCapFloorTrade> trades, 
        RatesProvider multicurve,
        RationalTemplate template, 
        RationalCapFloorTradePricer pricer) {
      this.trades = trades;
      this.multicurve = multicurve;
      this.template = template;
      nbSwaptions = trades.size();
      this.pricer = pricer;
    }

    @Override
    public DoubleArray apply(DoubleArray x) {
      RationalParameters model = template.generate(x);
      Currency ccy = model.getCurrency();
      double[] modelPrice = new double[nbSwaptions];
      for (int i = 0; i < nbSwaptions; i++) {
        modelPrice[i] = pricer.presentValue(trades.get(i), multicurve, model)
            .convertedTo(ccy, multicurve).getAmount();
      }
      return DoubleArray.ofUnsafe(modelPrice);
    }

  }

}
