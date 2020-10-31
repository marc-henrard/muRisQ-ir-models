/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.pricer.rate.RatesProvider;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;

/**
 * Utilities to create models parameters for simple implementations and testing.
 * 
 * @author Marc Henrard
 */
public class LmmdddExamplesUtils {
  
  private static final double TIME_TOLERANCE = 5.0d/350.0d; // To allow for long week-ends
  private static final double DEFAULT_NB_YEAR_ANGLE = 20.0D;
  
  /**
   * Create a Hull-White-one-factor-like LMM.
   * 
   * @param a  mean reversion
   * @param sigma  volatility
   * @param iborDates  the ibor dates
   * @param iborIndex  the underlying Ibor index
   * @param lmmTimeMeasure  the time measure for the times in the model
   * @param multicurve  the multi-curve used to compute beta values
   * @param valuationZone  the valuation zone
   * @param valuationTime  the valuation time
   * @param refData  the reference data with holidays
   * @return the model
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmmHw(
      double a,
      double sigma,
      List<LocalDate> iborDates,
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      TimeMeasurement lmmTimeMeasure,
      RatesProvider multicurve,
      ZoneId valuationZone,
      LocalTime valuationTime,
      ReferenceData refData) {

    LocalDate valuationDate = multicurve.getValuationDate();
    int nbDates = iborDates.size();
    double[] iborTimes = new double[nbDates];
    for (int i = 0; i < nbDates; i++) {
      iborTimes[i] = lmmTimeMeasure.relativeTime(valuationDate, iborDates.get(i));
    }
    double[] accrualFactors = new double[nbDates - 1];
    double[] displacements = new double[nbDates - 1];
    double[] multiplicativeSpreads = new double[nbDates - 1];
    double[][] volatilities = new double[nbDates - 1][1];
    for (int i = 0; i < nbDates - 1; i++) {
      accrualFactors[i] = iborIndex.getDayCount().relativeYearFraction(iborDates.get(i), iborDates.get(i + 1));
      displacements[i] = 1 / accrualFactors[i];
      LocalDate fixingDate = iborIndex.calculateFixingFromEffective(iborDates.get(i), refData);
      IborIndexObservation obs = IborIndexObservation.of(iborIndex, fixingDate, refData);
      double iborRate = multicurve.iborIndexRates(iborIndex).rate(obs);
      double dfStart = multicurve.discountFactor(iborIndex.getCurrency(), obs.getEffectiveDate());
      double dfEnd = multicurve.discountFactor(iborIndex.getCurrency(), obs.getMaturityDate());
      double fwdRatio = dfStart / dfEnd;
      multiplicativeSpreads[i] = (1.0 + accrualFactors[i] * iborRate) / fwdRatio;
      volatilities[i][0] = sigma / a * (Math.exp(-a * iborTimes[i]) - Math.exp(-a * iborTimes[i + 1]));
    }
    return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.builder()
        .valuationDate(valuationDate).valuationTime(valuationTime).valuationZone(valuationZone)
        .overnightIndex(overnightIndex)
        .iborIndex(iborIndex)
        .meanReversion(a)
        .accrualFactors(DoubleArray.ofUnsafe(accrualFactors))
        .iborTimes(DoubleArray.ofUnsafe(iborTimes))
        .displacements(DoubleArray.ofUnsafe(displacements))
        .multiplicativeSpreads(DoubleArray.ofUnsafe(multiplicativeSpreads))
        .timeMeasure(ScaledSecondTime.DEFAULT)
        .timeTolerance(TIME_TOLERANCE)
        .volatilities(DoubleMatrix.ofUnsafe(volatilities)).build();
  }

  /**
   * Generate a one-factor model with flat volatilities.
   * 
   * @param a  the mean reversion 
   * @param volLevel  the starting volatility level
   * @param displacement  the diffusion displacement
   * @param iborDates  the ibor dates
   * @param iborIndex  the underlying Ibor index
   * @param lmmTimeMeasure  the time measure for the times in the model
   * @param multicurve  the multi-curve used to compute beta values
   * @param valuationZone  the valuation zone
   * @param valuationTime  the valuation time
   * @param refData  the reference data with holidays
   * @return the model
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm1(
      double a,
      double volLevel,
      double displacement,
      List<LocalDate> iborDates,
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      TimeMeasurement lmmTimeMeasure,
      RatesProvider multicurve,
      ZoneId valuationZone,
      LocalTime valuationTime,
      ReferenceData refData) {

    LocalDate valuationDate = multicurve.getValuationDate();
    int nbDates = iborDates.size();
    double[] iborTimes = new double[nbDates];
    for (int i = 0; i < nbDates; i++) {
      iborTimes[i] = lmmTimeMeasure.relativeTime(valuationDate, iborDates.get(i));
    }
    double[] accrualFactors = new double[nbDates - 1];
    double[] displacements = new double[nbDates - 1];
    Arrays.fill(displacements, displacement);
    double[] multiplicativeSpreads = new double[nbDates - 1];
    double[][] volatilities = new double[nbDates - 1][1]; // 1-factor model
    for (int i = 0; i < nbDates - 1; i++) {
      accrualFactors[i] = iborIndex.getDayCount().relativeYearFraction(iborDates.get(i), iborDates.get(i + 1));
      LocalDate fixingDate = iborIndex.calculateFixingFromEffective(iborDates.get(i), refData);
      IborIndexObservation obs = IborIndexObservation.of(iborIndex, fixingDate, refData);
      double iborRate = multicurve.iborIndexRates(iborIndex).rate(obs);
      double dfStart = multicurve.discountFactor(iborIndex.getCurrency(), obs.getEffectiveDate());
      double dfEnd = multicurve.discountFactor(iborIndex.getCurrency(), obs.getMaturityDate());
      double fwdRatio = dfStart / dfEnd;
      multiplicativeSpreads[i] = (1.0 + accrualFactors[i] * iborRate) / fwdRatio;
      volatilities[i][0] = volLevel;
    }
    return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.builder()
        .valuationDate(valuationDate).valuationTime(valuationTime).valuationZone(valuationZone)
        .overnightIndex(overnightIndex)
        .iborIndex(iborIndex)
        .meanReversion(a)
        .accrualFactors(DoubleArray.ofUnsafe(accrualFactors))
        .iborTimes(DoubleArray.ofUnsafe(iborTimes))
        .displacements(DoubleArray.ofUnsafe(displacements))
        .multiplicativeSpreads(DoubleArray.ofUnsafe(multiplicativeSpreads))
        .timeMeasure(ScaledSecondTime.DEFAULT)
        .timeTolerance(TIME_TOLERANCE)
        .volatilities(DoubleMatrix.ofUnsafe(volatilities)).build();
  }

  /**
   * Generate a two factor model with the volatilities on the two factors defined with an "angle".
   * <p>
   * The angle between the factors: 
   *  - factor 1 weight is sin(angle*t/20) 
   *  - factor 2 weight is cos(angle*t/20).
   * For the angle = 0, there is only one factor. For angle = pi/2, the 0Y rate is independent of the 20Y rate.
   * 
   * @param a  the mean reversion 
   * @param volLevel  the starting volatility level
   * @param angle  the angle defining the two factor volatilities
   * @param volAngle  the volatility of the angle part
   * @param displacement  the diffusion displacement
   * @param iborDates  the ibor dates
   * @param iborIndex  the underlying Ibor index
   * @param lmmTimeMeasure  the time measure for the times in the model
   * @param multicurve  the multi-curve used to compute beta values
   * @param valuationZone  the valuation zone
   * @param valuationTime  the valuation time
   * @param refData  the reference data with holidays
   * @return the model
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm2Angle(
      double a,
      double volLevel,
      double angle,
      double volAngle,
      double displacement,
      List<LocalDate> iborDates,
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      TimeMeasurement lmmTimeMeasure,
      RatesProvider multicurve,
      ZoneId valuationZone,
      LocalTime valuationTime,
      ReferenceData refData) {
    
    return lmm2Angle(a, volLevel, angle, volAngle, DEFAULT_NB_YEAR_ANGLE, displacement, iborDates, 
        overnightIndex, iborIndex, lmmTimeMeasure, multicurve, valuationZone, valuationTime, refData);
  }

  /**
   * Generate a two factor model with the volatilities on the two factors defined with an "angle".
   * <p>
   * The angle between the factors: 
   *  - factor 1 weight is sin(angle*t/yearAngle) 
   *  - factor 2 weight is cos(angle*t/yearAngle).
   * For the angle = 0, there is only one factor. For angle = pi/2, the 0Y rate is independent of the yearAngle rate.
   * 
   * @param a  the mean reversion 
   * @param volLevel  the starting volatility level
   * @param angle  the angle defining the two factor volatilities
   * @param volAngle  the volatility of the angle part
   * @param yearAngle  the number of years after which the angles are attained 
   * @param displacement  the diffusion displacement
   * @param iborDates  the ibor dates
   * @param iborIndex  the underlying Ibor index
   * @param lmmTimeMeasure  the time measure for the times in the model
   * @param multicurve  the multi-curve used to compute beta values
   * @param valuationZone  the valuation zone
   * @param valuationTime  the valuation time
   * @param refData  the reference data with holidays
   * @return the model
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm2Angle(
      double a,
      double volLevel,
      double angle,
      double volAngle,
      double yearAngle,
      double displacement,
      List<LocalDate> iborDates,
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      TimeMeasurement lmmTimeMeasure,
      RatesProvider multicurve,
      ZoneId valuationZone,
      LocalTime valuationTime,
      ReferenceData refData) {

    return lmm2Angle(a, volLevel, angle, volAngle, 0.0d, yearAngle, 
        displacement, iborDates, overnightIndex, iborIndex, lmmTimeMeasure, 
        multicurve, valuationZone, valuationTime, refData);
  }

  /**
   * Generate a two factor model with the volatilities on the two factors defined with an "angle".
   * <p>
   * The angle between the factors: 
   *  - factor 1 weight is sin(angle*t/yearAngle) 
   *  - factor 2 weight is cos(angle*t/yearAngle).
   * For the angle = 0, there is only one factor. For angle = pi/2, the 0Y rate is independent of the yearAngle rate.
   * 
   * @param a  the mean reversion 
   * @param volLevel  the starting volatility level
   * @param angle  the angle defining the two factor volatilities
   * @param volAngle  the volatility of the angle part at 0
   * @param volAngleSlope  the volatility of the angle part at the last rate
   * @param yearAngle  the number of years after which the angles are attained 
   * @param displacement  the diffusion displacement
   * @param iborDates  the ibor dates
   * @param iborIndex  the underlying Ibor index
   * @param lmmTimeMeasure  the time measure for the times in the model
   * @param multicurve  the multi-curve used to compute beta values
   * @param valuationZone  the valuation zone
   * @param valuationTime  the valuation time
   * @param refData  the reference data with holidays
   * @return the model
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm2Angle(
      double a,
      double volLevel,
      double angle,
      double volAngle,
      double volAngleSlope,
      double yearAngle,
      double displacement,
      List<LocalDate> iborDates,
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      TimeMeasurement lmmTimeMeasure,
      RatesProvider multicurve,
      ZoneId valuationZone,
      LocalTime valuationTime,
      ReferenceData refData) {

    LocalDate valuationDate = multicurve.getValuationDate();
    int nbDates = iborDates.size();
    double[] iborTimes = new double[nbDates];
    for (int i = 0; i < nbDates; i++) {
      iborTimes[i] = lmmTimeMeasure.relativeTime(valuationDate, iborDates.get(i));
    }
    double[] accrualFactors = new double[nbDates - 1];
    double[] displacements = new double[nbDates - 1];
    Arrays.fill(displacements, displacement);
    double[] multiplicativeSpreads = new double[nbDates - 1];
    double[][] volatilities = new double[nbDates - 1][2]; // 2-factor model
    for (int i = 0; i < nbDates - 1; i++) {
      accrualFactors[i] = iborIndex.getDayCount().relativeYearFraction(iborDates.get(i), iborDates.get(i + 1));
      LocalDate fixingDate = iborIndex.calculateFixingFromEffective(iborDates.get(i), refData);
      IborIndexObservation obs = IborIndexObservation.of(iborIndex, fixingDate, refData);
      double iborRate = multicurve.iborIndexRates(iborIndex).rate(obs);
      double dfStart = multicurve.discountFactor(iborIndex.getCurrency(), obs.getEffectiveDate());
      double dfEnd = multicurve.discountFactor(iborIndex.getCurrency(), obs.getMaturityDate());
      double fwdRatio = dfStart / dfEnd;
      multiplicativeSpreads[i] = (1.0 + accrualFactors[i] * iborRate) / fwdRatio;
      double vol2 = volAngle + i * volAngleSlope / (nbDates - 2);
      volatilities[i][0] = volLevel + vol2 * Math.sin(iborTimes[i] / yearAngle * angle);
      volatilities[i][1] = volLevel + vol2 * Math.cos(iborTimes[i] / yearAngle * angle);
    }
    return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.builder()
        .valuationDate(valuationDate).valuationTime(valuationTime).valuationZone(valuationZone)
        .overnightIndex(overnightIndex)
        .iborIndex(iborIndex)
        .meanReversion(a)
        .accrualFactors(DoubleArray.ofUnsafe(accrualFactors))
        .iborTimes(DoubleArray.ofUnsafe(iborTimes))
        .displacements(DoubleArray.ofUnsafe(displacements))
        .multiplicativeSpreads(DoubleArray.ofUnsafe(multiplicativeSpreads))
        .timeMeasure(ScaledSecondTime.DEFAULT)
        .timeTolerance(TIME_TOLERANCE)
        .volatilities(DoubleMatrix.ofUnsafe(volatilities)).build();
  }
  
  /**
   * Create a 3-factor LMM model. The volatility level is the same for all forward rates.
   * The volatilities are created in the following way:
   *  - For each forward rate, two "angles" are created. The first one goes linearly from 0 to angleEnd1.
   *    The second one goes for 0 to angleEnd2 with the weights given by applying angle2Function to the
   *    linear load of the angles.
   *  - The loads of the 3 factors are
   *    . sin(angle1)
   *    . cos(angle1)* sin(angle2)
   *    . cos(angle1)* cos(angle2)
   * 
   * @param a  the mean reversion
   * @param volatility  the total volatility level
   * @param yearAngle  the number of years after which the angles are attained 
   * @param angleEnd1  the first end angle
   * @param angleEnd2  the second end angle
   * @param angle2Function  the function adjusting the loads of the second angle
   * @param displacement  the diffusion displacement
   * @param iborDates  the ibor dates
   * @param iborIndex  the underlying Ibor index
   * @param lmmTimeMeasure  the time measure for the times in the model
   * @param multicurve  the multi-curve used to compute beta values
   * @param valuationZone  the valuation zone
   * @param valuationTime  the valuation time
   * @param refData  the reference data with holidays
   * @return the model
   */
  public static LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm3Angle(
      double a,
      double volatility,
      double yearAngle,
      double angleEnd1,
      double angleEnd2,
      Function<Double, Double> angle2Function,
      double displacement,
      List<LocalDate> iborDates,
      OvernightIndex overnightIndex,
      IborIndex iborIndex,
      TimeMeasurement lmmTimeMeasure,
      RatesProvider multicurve,
      ZoneId valuationZone,
      LocalTime valuationTime,
      ReferenceData refData) {

    LocalDate valuationDate = multicurve.getValuationDate();
    int nbDates = iborDates.size();
    double[] iborTimes = new double[nbDates];
    for (int i = 0; i < nbDates; i++) {
      iborTimes[i] = lmmTimeMeasure.relativeTime(valuationDate, iborDates.get(i));
    }
    double[] accrualFactors = new double[nbDates - 1];
    double[] displacements = new double[nbDates - 1];
    Arrays.fill(displacements, displacement);
    double[] multiplicativeSpreads = new double[nbDates - 1];
    double[][] volatilities = new double[nbDates - 1][3]; // 3-factor model
    for (int i = 0; i < nbDates - 1; i++) {
      accrualFactors[i] = iborIndex.getDayCount().relativeYearFraction(iborDates.get(i), iborDates.get(i + 1));
      LocalDate fixingDate = iborIndex.calculateFixingFromEffective(iborDates.get(i), refData);
      IborIndexObservation obs = IborIndexObservation.of(iborIndex, fixingDate, refData);
      double iborRate = multicurve.iborIndexRates(iborIndex).rate(obs);
      double dfStart = multicurve.discountFactor(iborIndex.getCurrency(), obs.getEffectiveDate());
      double dfEnd = multicurve.discountFactor(iborIndex.getCurrency(), obs.getMaturityDate());
      double fwdRatio = dfStart / dfEnd;
      multiplicativeSpreads[i] = (1.0 + accrualFactors[i] * iborRate) / fwdRatio;
      double angleStep1 = (iborTimes[i]-iborTimes[0]) / yearAngle * angleEnd1;
      double angleStep2 = angle2Function.apply((iborTimes[i]-iborTimes[0]) / yearAngle) * angleEnd2;
      volatilities[i][0] = volatility * Math.sin(angleStep1);
      volatilities[i][1] = volatility * Math.cos(angleStep1) * Math.sin(angleStep2);
      volatilities[i][2] = volatility  * Math.cos(angleStep1) * Math.cos(angleStep2);
    }
    return LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.builder()
        .valuationDate(valuationDate).valuationTime(valuationTime).valuationZone(valuationZone)
        .overnightIndex(overnightIndex)
        .iborIndex(iborIndex)
        .meanReversion(a)
        .accrualFactors(DoubleArray.ofUnsafe(accrualFactors))
        .iborTimes(DoubleArray.ofUnsafe(iborTimes))
        .displacements(DoubleArray.ofUnsafe(displacements))
        .multiplicativeSpreads(DoubleArray.ofUnsafe(multiplicativeSpreads))
        .timeMeasure(ScaledSecondTime.DEFAULT)
        .timeTolerance(TIME_TOLERANCE)
        .volatilities(DoubleMatrix.ofUnsafe(volatilities)).build();
  }

}
