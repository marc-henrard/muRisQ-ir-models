/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.util.function.Function;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductExplicitApproxPricer;

/**
 * Exact calibration by root-finding of one swaption for the LMM displaced diffusion.
 * <p>
 * The start volatilities are multiplied by a common factor to achieve calibration.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionRootBachelierVolatility1LevelCalibrator {
  
  /** The precision used in root-finding search. */
  private static final double TOLERANCE_ABS = 1.0E-9;
  private static final double TOLERANCE_REL = 1.0E-4;
  private static final int STEP_MAX = 250;
  /** The root-finder implementation. */
  private final static BroydenVectorRootFinder ROOT_FINDER = new BroydenVectorRootFinder(
      TOLERANCE_ABS,
      TOLERANCE_REL,
      STEP_MAX,
      DecompositionFactory.SV_COMMONS);

  /** Starting parameters. */
  private final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters;

  /**
   * Create an instance of the calibrator.
   * 
   * @param startingParameters  the starting parameters to be adjusted
   * @return the instance
   */
  public static LmmdddSwaptionRootBachelierVolatility1LevelCalibrator of(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters) {
    return new LmmdddSwaptionRootBachelierVolatility1LevelCalibrator(startingParameters);
  }
  
  /**
   * Private constructor. 
   * 
   * @param startingParameters  the starting parameters to be adjusted
   */
  private LmmdddSwaptionRootBachelierVolatility1LevelCalibrator(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters) {
    this.startingParameters = startingParameters;
  }

  /**
   * Calibrates the model parameters to a swaption by exact root finding approach.
   * <p>
   * The calibration is done to the Bachelier/normal implied volatility.
   * 
   * @param swaption  the swaption product
   * @param impliedVolatility  the Bachelier/normal model implied volatility
   * @param multicurve  the multi-curve provider
   * @return  the calibrated model parameters
   */
  public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters calibrate(
      ResolvedSwaption swaption, 
      double impliedVolatility,
      RatesProvider multicurve) {
    
    ModelValues function = new ModelValues(swaption, impliedVolatility, multicurve, startingParameters);
    VectorFieldFirstOrderDifferentiator differentiator = new VectorFieldFirstOrderDifferentiator();
    Function<DoubleArray, DoubleMatrix>  jacobian = differentiator.differentiate(function);
    DoubleArray parametersCalibrated = ROOT_FINDER.findRoot(function, jacobian, DoubleArray.of(1.0d));
    DoubleMatrix volatilityUpdated = 
        startingParameters.getVolatilities().multipliedBy(parametersCalibrated.get(0));
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersUpdated = 
        startingParameters.toBuilder().volatilities(volatilityUpdated).build();
    return parametersUpdated;
  }

  /**
   * Inner class computing the model price from the variable parameters.
   */
  static class ModelValues implements Function<DoubleArray, DoubleArray> {

    private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_SWAPTION_LMM_APPROX = 
        LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;
    
    private final ResolvedSwaption swaption;
    private final double ivMarket;
    private final RatesProvider multicurve;
    private final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters;

    // Constructor
    public ModelValues(
        ResolvedSwaption swaption, 
        double impliedVolatility,
        RatesProvider multicurve, 
        LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters) {
      
      this.swaption = swaption;
      this.ivMarket = impliedVolatility;
      this.multicurve = multicurve;
      this.startingParameters = startingParameters;
    }

    @Override
    public DoubleArray apply(DoubleArray x) {
      DoubleMatrix volatilityUpdated = startingParameters.getVolatilities().multipliedBy(x.get(0));
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersUpdated = 
          startingParameters.toBuilder().volatilities(volatilityUpdated).build();
      double ivModel = PRICER_SWAPTION_LMM_APPROX.impliedVolatilityBachelier(swaption, multicurve, parametersUpdated);
      return DoubleArray.of(ivMarket - ivModel);
    }
    
  }

}
