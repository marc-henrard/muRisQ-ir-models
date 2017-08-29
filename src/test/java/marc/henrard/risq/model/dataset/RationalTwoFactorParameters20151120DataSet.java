/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.dataset;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.curve.ConstantCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolator;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;

import marc.henrard.risq.model.generic.GenericParameterDateCurve;
import marc.henrard.risq.model.generic.ParameterDateCurve;
import marc.henrard.risq.model.generic.ScaledSecondTime;
import marc.henrard.risq.model.generic.TimeMeasurement;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorGenericParameters;

/**
 * Examples of data sets for the Rational Two-Factor model.
 * Used for tests.
 * 
 * @author Marc Henrard
 */
public class RationalTwoFactorParameters20151120DataSet {

  public static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  
  private static final CurveInterpolator INTERPOLATOR_LINEAR = CurveInterpolators.LINEAR;
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  private static final double A1 = 0.50;
  private static final double A2 = 0.40;
  private static final double RHO = 0.30;
  private static final double ALPHA = 0.10;
  private static final double BETA = 0.01;
  private static final double[] TIMES = new double[]{0.0d, 100.0d};
  private static final Curve B0_CURVE = InterpolatedNodalCurve.of(DefaultCurveMetadata.of("B1"), 
      DoubleArray.copyOf(TIMES), 
      DoubleArray.copyOf(new double[]{ALPHA, ALPHA + BETA * TIMES[1]}), INTERPOLATOR_LINEAR);
  private static final ParameterDateCurve B0 = GenericParameterDateCurve.of(
      DayCounts.ACT_365F, B0_CURVE, VALUATION_DATE);
  private static final Map<IborIndex, ParameterDateCurve> B1 = new HashMap<>();
  static {
    ParameterDateCurve b2_6 = 
        GenericParameterDateCurve.of(
            DayCounts.ACT_365F, 
            ConstantCurve.of(DefaultCurveMetadata.of("B1"), 0.010), 
            VALUATION_DATE);
    B1.put(EUR_EURIBOR_6M, b2_6);
  }
  private static final Map<IborIndex, ParameterDateCurve> B2 = new HashMap<>();
  static {
    ParameterDateCurve b3_6 = 
        GenericParameterDateCurve.of(
            DayCounts.ACT_365F, 
            ConstantCurve.of(DefaultCurveMetadata.of("B2"), 0.001), 
            VALUATION_DATE);
    B2.put(EUR_EURIBOR_6M, b3_6);
  }
  private static final Map<IborIndex, ParameterDateCurve> ZERO = new HashMap<>();
  static {
    ParameterDateCurve b3_0 = 
        GenericParameterDateCurve.of(
            DayCounts.ACT_365F, 
            ConstantCurve.of(DefaultCurveMetadata.of("ZERO"), 0.0d), 
            VALUATION_DATE);
    ZERO.put(EUR_EURIBOR_6M, b3_0);
  }
  public static final LocalTime LOCAL_TIME = LocalTime.NOON;
  public static final ZoneId ZONE_ID = ZoneId.of("Europe/London");
  public static final RationalTwoFactorGenericParameters RATIONAL_2F = 
      RationalTwoFactorGenericParameters.of(EUR, A1, A2, RHO, B0, B1, B2, TIME_MEAS, VALUATION_DATE, LOCAL_TIME, ZONE_ID);
  public static final RationalTwoFactorGenericParameters RATIONAL_2F_REDUCED_1 = 
      RationalTwoFactorGenericParameters.of(EUR, A1, A2, 0.9, B0, B1, ZERO, TIME_MEAS, VALUATION_DATE, LOCAL_TIME, ZONE_ID);
  public static final RationalTwoFactorGenericParameters RATIONAL_2F_REDUCED_0 = 
      RationalTwoFactorGenericParameters.of(EUR, A1, A2, 0.0, B0, B1, ZERO, TIME_MEAS, VALUATION_DATE, LOCAL_TIME, ZONE_ID);
  
}
