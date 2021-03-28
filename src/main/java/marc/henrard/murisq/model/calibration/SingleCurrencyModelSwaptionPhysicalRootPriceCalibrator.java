/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.calibration;

import java.util.BitSet;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swaption.ResolvedSwaptionTrade;

import marc.henrard.murisq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.murisq.model.generic.SingleCurrencyModelTemplate;
import marc.henrard.murisq.pricer.swaption.SingleCurrencyModelSwaptionPhysicalTradePricer;

/**
 * Exact calibration by root-finding of swaption price for the Interest Rate single currency models.
 * <p>
 * The calibration is done on the trade premium.
 * 
 * @author Marc Henrard
 */
public class SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator {
  
  /** The precision used in root-finding search. */
  private static final double TOLERANCE_ABS = 1.0E-1;  // Adapt to notional?
  private static final double TOLERANCE_REL = 1.0E-4;
  private static final int STEP_MAX = 250;
  /** The root-finder implementation. */
  private final static BroydenVectorRootFinder ROOT_FINDER = new BroydenVectorRootFinder(
      TOLERANCE_ABS,
      TOLERANCE_REL,
      STEP_MAX,
      DecompositionFactory.SV_COMMONS);

  /** The template generating {@link SingleCurrencyModelParameters}. */
  private final SingleCurrencyModelTemplate template;

  /**
   * Create an instance of the calibrator.
   * 
   * @param template  the rational model template
   * @return the instance
   */
  public static SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator of(SingleCurrencyModelTemplate template) {
    return new SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator(template);
  }
  
  /**
   * Private constructor. 
   * @param template
   */
  private SingleCurrencyModelSwaptionPhysicalRootPriceCalibrator(SingleCurrencyModelTemplate template) {
    this.template = template;
  }
  
  /**
   * Calibrates the model parameters to a set of swaption trades by exact root finding approach.
   * <p>
   * The calibration uses explicit constraints and reduces the number of calibration parameters to 
   * the non-fixed parameters.
   * 
   * @param trades  the swaption trades
   * @param multicurve  the multi-curve provider
   * @param pricer  the swaption trade pricer
   * @return  the model parameters
   */
  public SingleCurrencyModelParameters calibrateConstraints(
      List<ResolvedSwaptionTrade> trades, 
      RatesProvider multicurve,
      SingleCurrencyModelSwaptionPhysicalTradePricer pricer) {
    
    ModelValues function = new ModelValues(trades, multicurve, template, pricer);
    // Jacobian by finite difference: TODO: improve
    VectorFieldFirstOrderDifferentiator differentiator = new VectorFieldFirstOrderDifferentiator();
    Function<DoubleArray, DoubleMatrix>  jacobian = differentiator.differentiate(function);
    DoubleArray startCalibratedParameters = initialGuessVariable(template.initialGuess(), template.getFixed());
    DoubleArray parametersCalibrated = ROOT_FINDER.findRoot(function, jacobian, startCalibratedParameters);
    return template.generate(allParametersFromCalibrated(
        parametersCalibrated, template.initialGuess(), template.getFixed()));
  }

  /**
   * Inner class computing the model price from the variable parameters.
   */
  static class ModelValues implements Function<DoubleArray, DoubleArray> {

    /** The trades on which the model is calibrated. */ 
    private final List<ResolvedSwaptionTrade> trades;
    /** The multi-curve used to compute trade values. */
    private final RatesProvider multicurve;
    /** The model template. */
    private final SingleCurrencyModelTemplate template;
    /** The number of trades. */
    private final int nbTrades;
    /** The pricer for the trades. */
    private final SingleCurrencyModelSwaptionPhysicalTradePricer pricer;

    // Constructor
    public ModelValues(
        List<ResolvedSwaptionTrade> trades, 
        RatesProvider multicurve,
        SingleCurrencyModelTemplate template, 
        SingleCurrencyModelSwaptionPhysicalTradePricer pricer) {
      
      this.trades = trades;
      this.multicurve = multicurve;
      this.template = template;
      nbTrades = trades.size();
      this.pricer = pricer;
      ArgChecker.isTrue(nbTrades == template.parametersVariableCount(), 
          "number of variable parameters should be equal to the number of trades");
    }

    /**
     * The parameters values only for the parameters to be calibrated; 
     * the fixed ones are extracted from the template initial guess.
     */
    @Override
    public DoubleArray apply(DoubleArray x) {
      ArgChecker.isTrue(x.size() == nbTrades, "number of parameters should be equal to number of trades");
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
