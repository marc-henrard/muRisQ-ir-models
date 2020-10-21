/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductExplicitApproxPricer;

/**
 * Exact calibration by root-finding of two swaptions for the LMM displaced diffusion.
 * <p>
 * The start volatilities and the skew are multiplied each by a factor to achieve calibration.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionRootBachelierVolatility2SkewCalibrator {
  
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
  public static LmmdddSwaptionRootBachelierVolatility2SkewCalibrator of(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters) {
    return new LmmdddSwaptionRootBachelierVolatility2SkewCalibrator(startingParameters);
  }
  
  /**
   * Private constructor. 
   * 
   * @param startingParameters  the starting parameters to be adjusted
   */
  private LmmdddSwaptionRootBachelierVolatility2SkewCalibrator(
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
      List<ResolvedSwaption> swaptions, 
      DoubleArray impliedVolatilities,
      RatesProvider multicurve) {
    
    ArgChecker.isTrue(swaptions.size() == 2, 
        "there must be exactly two swaptions in the calibration set");
    ModelValues function = new ModelValues(swaptions, impliedVolatilities, multicurve, startingParameters);
    VectorFieldFirstOrderDifferentiator differentiator = new VectorFieldFirstOrderDifferentiator();
    Function<DoubleArray, DoubleMatrix>  jacobian = differentiator.differentiate(function);
    DoubleArray parametersCalibrated = 
        ROOT_FINDER.findRoot(function, jacobian, DoubleArray.of(1.0d, 1.0d));
    DoubleMatrix volatilityUpdated = 
        startingParameters.getVolatilities().multipliedBy(parametersCalibrated.get(0));
    DoubleArray displacementUpdated = 
        startingParameters.getDisplacements().multipliedBy(parametersCalibrated.get(1));
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersUpdated = 
        startingParameters.toBuilder()
        .volatilities(volatilityUpdated)
        .displacements(displacementUpdated).build();
    return parametersUpdated;
  }

  /**
   * Inner class computing the model price from the variable parameters.
   */
  static class ModelValues implements Function<DoubleArray, DoubleArray> {

    private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_SWAPTION_LMM_APPROX = 
        LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;
    
    private final List<ResolvedSwaption> swaptions;
    private final DoubleArray ivMarket;
    private final RatesProvider multicurve;
    private final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters;

    // Constructor
    public ModelValues(
        List<ResolvedSwaption> swaptions, 
        DoubleArray impliedVolatilities,
        RatesProvider multicurve, 
        LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters) {
      
      this.swaptions = swaptions;
      this.ivMarket = impliedVolatilities;
      this.multicurve = multicurve;
      this.startingParameters = startingParameters;
    }

    @Override
    public DoubleArray apply(DoubleArray x) {
      DoubleMatrix volatilityUpdated = startingParameters.getVolatilities().multipliedBy(x.get(0));
      DoubleArray displacementUpdated = startingParameters.getDisplacements().multipliedBy(x.get(1));
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersUpdated =
          startingParameters.toBuilder()
          .volatilities(volatilityUpdated)
          .displacements(displacementUpdated).build();
      double[] ivModel = new double[2];
      for (int i = 0; i < 2; i++) {
        ivModel[i] = PRICER_SWAPTION_LMM_APPROX
            .impliedVolatilityBachelier(swaptions.get(i), multicurve, parametersUpdated);
      }
      return ivMarket.minus(DoubleArray.ofUnsafe(ivModel));
    }
    
  }

}
