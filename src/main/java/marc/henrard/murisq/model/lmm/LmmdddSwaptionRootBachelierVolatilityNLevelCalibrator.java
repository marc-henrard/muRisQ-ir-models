/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.math.impl.differentiation.VectorFieldFirstOrderDifferentiator;
import com.opengamma.strata.math.impl.linearalgebra.DecompositionFactory;
import com.opengamma.strata.math.impl.rootfinding.newton.BroydenVectorRootFinder;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductExplicitApproxPricer;

/**
 * Exact calibration by root-finding of swaptions for the LMM displaced diffusion.
 * <p>
 * The start volatilities a multiplied by factors to achieve calibration. The factors are interpolated.
 * <p>
 * The calibrating swaptions must be have underlying swaps with increasing start dates and end dates.
 * <p>
 * One node of the factor curve is selected for each swaption. The first node correspond to the first
 * forward rate of the first swaption and the last node to the last rate of the last swaption. The scaling
 * factors are interpolated between those nodes.
 * 
 * @author Marc Henrard
 */
public class LmmdddSwaptionRootBachelierVolatilityNLevelCalibrator {
  
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
  private final static CurveMetadata ADJ_METADATA = DefaultCurveMetadata.of("Adjustment");

  /** Starting parameters. */
  private final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters;
  /** Interpolator and extrapolators. */ 
  private final CurveInterpolator interpolator;
  private final CurveExtrapolator extrapolatorLeft;
  private final CurveExtrapolator extrapolatorRight;

  /**
   * Create an instance of the calibrator.
   * <p>
   * The extrapolators do not impact the pricing of the swaptions in the calibration basket but may but
   * may impact other instruments with earlier or later dates.
   * 
   * @param startingParameters  the starting parameters to be adjusted
   * @param interpolator  the interpolator for the scaling factors
   * @param extrapolatorLeft  the left extrapolator for the scaling factors
   * @param extrapolatorRight  the right extrapolator for the scaling factors
   * @return the instance
   */
  public static LmmdddSwaptionRootBachelierVolatilityNLevelCalibrator of(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters,
      CurveInterpolator interpolator,
      CurveExtrapolator extrapolatorLeft,
      CurveExtrapolator extrapolatorRight) {
    return new LmmdddSwaptionRootBachelierVolatilityNLevelCalibrator(startingParameters, interpolator, 
        extrapolatorLeft, extrapolatorRight);
  }
  
  /**
   * Private constructor. 
   * 
   * @param startingParameters  the starting parameters to be adjusted
   * @param interpolator  the interpolator for the scaling factors
   * @param extrapolatorLeft  the left extrapolator for the scaling factors
   * @param extrapolatorRight  the right extrapolator for the scaling factors
   */
  private LmmdddSwaptionRootBachelierVolatilityNLevelCalibrator(
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters,
      CurveInterpolator interpolator,
      CurveExtrapolator extrapolatorLeft,
      CurveExtrapolator extrapolatorRight) {

    this.startingParameters = startingParameters;
    this.interpolator = interpolator;
    this.extrapolatorLeft = extrapolatorLeft;
    this.extrapolatorRight = extrapolatorRight;
  }

  /**
   * Calibrates the model parameters to set of swaptions by exact root finding approach.
   * <p>
   * The calibration is done to the Bachelier/normal implied volatilities.
   * 
   * @param swaptions  the swaptions
   * @param impliedVolatilities  the Bachelier/normal model implied volatilities
   * @param multicurve  the multi-curve provider
   * @return  the calibrated model parameters
   */
  public LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters calibrate(
      List<ResolvedSwaption> swaptions,
      DoubleArray impliedVolatilities,
      RatesProvider multicurve) {

    ArgChecker.isTrue(swaptions.size() == impliedVolatilities.size(),
        "the number of swaptions must be equal to the number of implied volatilities");
    // indices start/end
    int nbSwaptions = swaptions.size();
    LocalDate valuationDate = multicurve.getValuationDate();
    double[] startTimes = new double[nbSwaptions];
    double[] endTimes = new double[nbSwaptions];
    for (int loopswpt = 0; loopswpt < nbSwaptions; loopswpt++) {
      ResolvedSwap swap = swaptions.get(loopswpt).getUnderlying();
      ResolvedSwapLeg legIbor = swap.getLegs(SwapLegType.IBOR).get(0);
      startTimes[loopswpt] = startingParameters.getTimeMeasure().relativeTime(valuationDate, legIbor.getStartDate());
      endTimes[loopswpt] = startingParameters.getTimeMeasure().relativeTime(valuationDate, legIbor.getEndDate());
    }
    int[] startIndices = startingParameters.getIborTimeIndex(startTimes);
    int[] endIndices = startingParameters.getIborTimeIndex(endTimes);
    // Check order
    for (int loopswpt = 0; loopswpt < nbSwaptions-1; loopswpt++) {
      ArgChecker.isTrue(startIndices[loopswpt] < startIndices[loopswpt+1], 
          "swaptions must be in strictly increasing start date order");
      ArgChecker.isTrue(endIndices[loopswpt] < endIndices[loopswpt+1], 
          "swaptions must be in strictly increasing end date order");
    }
    // indices nodes
    double[] nodeIndices = new double[nbSwaptions]; // double, used for interpolation; LIBOR indices are always int
    for (int loopswpt = 0; loopswpt < nbSwaptions; loopswpt++) { // mid points
      double weightEnd = loopswpt * 1.0d / (nbSwaptions - 1);
      nodeIndices[loopswpt] = (1.0d - weightEnd) * startIndices[loopswpt] + weightEnd * (endIndices[loopswpt] - 1);
    }
    DoubleArray xValues = DoubleArray.ofUnsafe(nodeIndices);
    DoubleArray yValuesStart = DoubleArray.of(nbSwaptions, i -> 1.0d);
    // Root finding
    ModelValues function = new ModelValues(swaptions, impliedVolatilities, multicurve, 
        startingParameters, xValues, interpolator, extrapolatorLeft, extrapolatorRight);
    VectorFieldFirstOrderDifferentiator differentiator = new VectorFieldFirstOrderDifferentiator();
    Function<DoubleArray, DoubleMatrix>  jacobian = differentiator.differentiate(function);
    DoubleArray parametersCalibrated = ROOT_FINDER.findRoot(function, jacobian, yValuesStart);
    // Resulting parameters
    InterpolatedNodalCurve curve = InterpolatedNodalCurve.of(ADJ_METADATA, xValues, parametersCalibrated, interpolator);
    double[][] volatilityUpdatedArray = startingParameters.getVolatilities().toArray();
    for (int i = 0; i < startingParameters.getVolatilities().rowCount(); i++) {
      double volAdj = curve.yValue(i);
      for (int j = 0; j < startingParameters.getVolatilities().columnCount(); j++) {
        volatilityUpdatedArray[i][j] *= volAdj;
      }
    }
    LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersUpdated =
        startingParameters.toBuilder().volatilities(DoubleMatrix.ofUnsafe(volatilityUpdatedArray)).build();
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
    private final int nbVol;
    private final int nbFactors;
    private final int nbSwaptions;
    private final DoubleArray xValues;
    private final CurveInterpolator interpolator;
    private final CurveExtrapolator extrapolatorLeft;
    private final CurveExtrapolator extrapolatorRight;

    // Constructor
    public ModelValues(
        List<ResolvedSwaption> swaptions, 
        DoubleArray impliedVolatility,
        RatesProvider multicurve, 
        LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters startingParameters,
        DoubleArray xValues,
        CurveInterpolator interpolator,
        CurveExtrapolator extrapolatorLeft,
        CurveExtrapolator extrapolatorRight) {
      
      this.swaptions = swaptions;
      this.ivMarket = impliedVolatility;
      this.multicurve = multicurve;
      this.startingParameters = startingParameters;
      this.nbVol = startingParameters.getVolatilities().rowCount();
      this.nbFactors = startingParameters.getVolatilities().columnCount();
      this.nbSwaptions = swaptions.size();
      this.xValues = xValues;
      this.interpolator = interpolator;
      this.extrapolatorLeft = extrapolatorLeft;
      this.extrapolatorRight = extrapolatorRight;
    }

    @Override
    public DoubleArray apply(DoubleArray yValues) {
      InterpolatedNodalCurve curve = InterpolatedNodalCurve.builder()
          .metadata(ADJ_METADATA)
          .xValues(xValues)
          .yValues(yValues)
          .interpolator(interpolator)
          .extrapolatorLeft(extrapolatorLeft)
          .extrapolatorRight(extrapolatorRight).build();
      double[][] volatilityUpdatedArray = startingParameters.getVolatilities().toArray();
      for (int i = 0; i < nbVol; i++) {
        double volAdj = curve.yValue(i);
        for (int j = 0; j < nbFactors; j++) {
          volatilityUpdatedArray[i][j] *= volAdj;
        }
      }
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters parametersUpdated =
          startingParameters.toBuilder().volatilities(DoubleMatrix.ofUnsafe(volatilityUpdatedArray)).build();
      double[] ivModel = new double[nbSwaptions];
      for (int i = 0; i < nbSwaptions; i++) {
        ivModel[i] =
            PRICER_SWAPTION_LMM_APPROX.impliedVolatilityBachelier(swaptions.get(i), multicurve, parametersUpdated);
      }
      return ivMarket.minus(DoubleArray.ofUnsafe(ivModel));
    }

  }

}
