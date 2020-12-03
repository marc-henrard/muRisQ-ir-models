/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.dataset;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.market.surface.ConstantSurface;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.Surface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.Surfaces;
import com.opengamma.strata.market.surface.interpolator.GridSurfaceInterpolator;
import com.opengamma.strata.market.surface.interpolator.SurfaceInterpolator;
import com.opengamma.strata.pricer.model.SabrInterestRateParameters;
import com.opengamma.strata.pricer.model.SabrVolatilityFormula;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swaption.NormalSwaptionExpiryTenorVolatilities;
import com.opengamma.strata.pricer.swaption.SabrParametersSwaptionVolatilities;
import com.opengamma.strata.pricer.swaption.SabrSwaptionCalibrator;
import com.opengamma.strata.pricer.swaption.SwaptionVolatilities;
import com.opengamma.strata.pricer.swaption.SwaptionVolatilitiesName;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

public class SabrSwaptionEurDataSet {

  public static final LocalDate VALUATION_DATE = MulticurveEur20151120DataSet.VALUATION_DATE;
  public static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  public static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");
  private static final ZonedDateTime VALUATION_DATE_TIME = 
      ZonedDateTime.of(VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE);
  
  private static final SurfaceInterpolator INTERPOLATOR_2D = GridSurfaceInterpolator
      .of(CurveInterpolators.LINEAR, CurveExtrapolators.FLAT, CurveInterpolators.LINEAR, CurveExtrapolators.FLAT);
  private static final SurfaceMetadata METADATA_ATM = 
      DefaultSurfaceMetadata.builder().surfaceName("ATM")
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.YEAR_FRACTION)
      .zValueType(ValueType.NORMAL_VOLATILITY)
      .dayCount(DayCounts.ACT_365F).build();
  private static final SurfaceMetadata METADATA_ALPHA = 
      Surfaces.sabrParameterByExpiryTenor("Alpha", DayCounts.ACT_365F, ValueType.SABR_ALPHA);
  private static final SurfaceMetadata METADATA_BETA = 
      Surfaces.sabrParameterByExpiryTenor("Beta", DayCounts.ACT_365F, ValueType.SABR_BETA);
  private static final SurfaceMetadata METADATA_RHO = 
      Surfaces.sabrParameterByExpiryTenor("Rho", DayCounts.ACT_365F, ValueType.SABR_RHO);
  private static final SurfaceMetadata METADATA_NU = 
      Surfaces.sabrParameterByExpiryTenor("Nu", DayCounts.ACT_365F, ValueType.SABR_NU);
  private static final SurfaceMetadata METADATA_SHIFT = DefaultSurfaceMetadata.of("Shift");

  private static final List<Period> EXPIRIES_PERIOD = ImmutableList.of(Period.ofMonths(6), Period.ofYears(1),
      Period.ofYears(2), Period.ofYears(5), Period.ofYears(10));
  private static final List<Tenor> TENORS_PERIOD = ImmutableList.of(
      Tenor.TENOR_2Y, Tenor.TENOR_5Y, Tenor.TENOR_10Y, Tenor.TENOR_30Y);
  
  private static final DoubleArray EXPIRIES_TIMES =
      DoubleArray.of(0.5, 0.5, 0.5, 0.5, 1, 1, 1, 1, 2, 2, 2, 2, 5, 5, 5, 5, 10, 10, 10, 10);
  private static final DoubleArray TENORS_TIMES =
      DoubleArray.of(2, 5, 10, 30, 2, 5, 10, 30, 2, 5, 10, 30, 2, 5, 10, 30, 2, 5, 10, 30);
  private static final DoubleArray ATM_VOL = DoubleArray.of(
      0.001700, 0.002715, 0.003849, 0.004999, // 6M
      0.002070, 0.003067, 0.004130, 0.005112, // 1Y
      0.002673, 0.003576, 0.004504, 0.005187, // 2Y
      0.004241, 0.004641, 0.005142, 0.005195, // 5Y
      0.005241, 0.005322, 0.005394, 0.004957); // 10Y
  private static final DoubleArray RHO_DATA = DoubleArray.of(
      -0.2150, -0.2437,  0.3989,   0.3989, 
      -0.2494,  -0.2778,  -0.2750,  -0.2631, 
      -0.2958,  -0.30,  -0.3091,  -0.28, // -0.2958,  -0.3601,  -0.3091,  -0.3066
      0.0000,   -0.0085,  -0.0191,  -0.0390, 
      -0.3397,  -0.3324,  -0.3441,  -0.3620);
  private static final DoubleArray NU_DATA = DoubleArray.of(
      0.6385, 0.6844, 0.0050, 0.0050, 
      0.5109, 0.5550, 0.5899, 0.5983, 
      0.4433, 0.5447, 0.5122, 0.5243, 
      0.4198, 0.4288, 0.4402, 0.4450, 
      0.3211, 0.3181, 0.3273, 0.3227);
  private static final Surface ATM = InterpolatedNodalSurface.of(METADATA_ATM, EXPIRIES_TIMES, TENORS_TIMES,
      ATM_VOL, INTERPOLATOR_2D);
  private static final Surface ALPHA = InterpolatedNodalSurface.of(METADATA_ALPHA, EXPIRIES_TIMES, TENORS_TIMES,
      DoubleArray.of(20, i -> 0.05), INTERPOLATOR_2D);
  private static final Surface BETA = ConstantSurface.of(METADATA_BETA, 0.50d);
  private static final Surface SHIFT = ConstantSurface.of(METADATA_SHIFT, 0.02d);
  private static final Surface RHO = InterpolatedNodalSurface.of(METADATA_RHO, EXPIRIES_TIMES, TENORS_TIMES,
      RHO_DATA, INTERPOLATOR_2D);
  private static final Surface NU = InterpolatedNodalSurface.of(METADATA_NU, EXPIRIES_TIMES, TENORS_TIMES,
      NU_DATA, INTERPOLATOR_2D);
  private static final SabrInterestRateParameters STARTING_SABR_PARAMETERS = SabrInterestRateParameters
      .of(ALPHA, BETA, RHO, NU, SHIFT, SabrVolatilityFormula.hagan());
  private static final FixedIborSwapConvention CONVENTION = FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
  private static final SabrParametersSwaptionVolatilities STARTING_SABR = 
      SabrParametersSwaptionVolatilities.of(SwaptionVolatilitiesName.of("SABR"), CONVENTION, 
          VALUATION_DATE_TIME, STARTING_SABR_PARAMETERS);
  
  private static final SwaptionVolatilities ATM_SWPT = NormalSwaptionExpiryTenorVolatilities
      .of(CONVENTION, VALUATION_DATE_TIME, ATM);
  
  private static final SabrSwaptionCalibrator CALIBRATOR_SABR = SabrSwaptionCalibrator.DEFAULT;

  /**
   * Returns the rates provider and the SABR parameters for EUR swaptions v EURIBOR-6M as of 2020-10-16.
   * 
   * @return the rates provider and the SABR parameters
   */
  public static Pair<ImmutableRatesProvider, SabrParametersSwaptionVolatilities> sabrParameters() {
    ImmutableRatesProvider multicurve = MulticurveEur20151120DataSet.MULTICURVE_EUR_ESTR_20151120;
    SabrParametersSwaptionVolatilities sabrCalibrated =
        CALIBRATOR_SABR.calibrateAlphaWithAtm(SwaptionVolatilitiesName.of("SABR-cal"),
            STARTING_SABR, multicurve, ATM_SWPT,
            TENORS_PERIOD, EXPIRIES_PERIOD, INTERPOLATOR_2D);
    return Pair.of(multicurve, sabrCalibrated);
  }

}
