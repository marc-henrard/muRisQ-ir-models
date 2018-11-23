/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.dataset;

import java.time.LocalTime;
import java.time.ZoneId;

import com.opengamma.strata.pricer.DiscountFactors;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;
import marc.henrard.murisq.model.rationalmulticurve.RationalOneFactorSimpleHWShapeParameters;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactor2HWShapePlusCstParameters;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorHWShapePlusCstParameters;

/**
 * Generates rational multi-curve model parameters for analysis purposes.
 * 
 * @author Marc Henrard
 */
public class RationalParametersDataSet {
  
  /* Rational model data: HW shaped b0 */
  private static final double A_1 = 0.75;
  private static final double A_2 = 0.50;
  private static final double CORRELATION = 0.00;
  private static final double B_0_0 = 0.50;
  private static final double ETA = 0.01;
  private static final double KAPPA = 0.03;
  private static final double C_1 = 0.00;
  private static final double C_2 = 0.0020;
  
  private static final TimeMeasurement TIME_MEAS = ScaledSecondTime.DEFAULT;
  
  /**
   * Creates a one-factor rational model parameter set with a=0.75, b00=0.50, eta=0.01 and kappa =0.03.
   * 
   * @param time  the parameters time
   * @param zone  the parameters zone
   * @param discountFactors  the discount factors
   * @return  the parameters
   */
  public static RationalOneFactorSimpleHWShapeParameters oneFactorHWShaped(
      LocalTime time,
      ZoneId zone,
      DiscountFactors discountFactors) {
    
    return RationalOneFactorSimpleHWShapeParameters.builder()
        .a(A_1)
        .b00(B_0_0)
        .eta(ETA)
        .kappa(KAPPA)
        .timeMeasure(TIME_MEAS)
        .discountFactors(discountFactors)
        .valuationTime(time)
        .valuationZone(zone)
        .build();
  }
  
  /**
   * Creates a two-factor rational model parameter set with a1=0.75, a2=0.50, correlation=0.0, 
   * b00=0.50, eta=0.01 and kappa =0.03. The two additive spreads are c1=0 and c2=0.0020.
   * 
   * @param time  the parameters time
   * @param zone  the parameters zone
   * @param discountFactors  the discount factors
   * @return  the parameters
   */
  public static RationalTwoFactorHWShapePlusCstParameters twoFactorHWShaped(
  LocalTime time,
  ZoneId zone,
  DiscountFactors discountFactors) {
    
    return RationalTwoFactorHWShapePlusCstParameters.builder()
        .a1(A_1)
        .a2(A_2)
        .correlation(CORRELATION)
        .b00(B_0_0)
        .eta(ETA)
        .kappa(KAPPA)
        .c1(C_1)
        .c2(C_2)
        .timeMeasure(TIME_MEAS)
        .discountFactors(discountFactors)
        .valuationTime(time)
        .valuationZone(zone).build();
  }
  
  /**
   * Creates a two-factor rational model parameter set with a1=0.75, a2=0.50, correlation=0.0, 
   * b00=0.50, eta=0.01 and kappa =0.03. The two additive spreads c1 and c2 are 0.
   * 
   * @param time  the parameters time
   * @param zone  the parameters zone
   * @param discountFactors  the discount factors
   * @return  the parameters
   */
  public static RationalTwoFactor2HWShapePlusCstParameters twoFactor2HWShaped(
  LocalTime time,
  ZoneId zone,
  DiscountFactors discountFactors) {
    
    return RationalTwoFactor2HWShapePlusCstParameters.builder()
        .a1(A_1)
        .a2(A_2)
        .correlation(0.0d)
        .b00(B_0_0)
        .eta1(0.02)
        .kappa1(0.01)
        .eta2(0.015)
        .kappa2(0.20)
        .c1(0.0d)
        .c2(0.0d)
        .timeMeasure(TIME_MEAS)
        .discountFactors(discountFactors)
        .valuationTime(time)
        .valuationZone(zone).build();
  }

}
