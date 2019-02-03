/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.Index;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.data.MarketData;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveDefinition;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveParameterSize;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.JacobianCalibrationMatrix;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.market.curve.RatesCurveGroupEntry;
import com.opengamma.strata.market.curve.interpolator.CurveExtrapolators;
import com.opengamma.strata.market.curve.interpolator.CurveInterpolators;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.curve.CalibrationMeasures;
import com.opengamma.strata.pricer.curve.ImmutableRatesProviderGenerator;
import com.opengamma.strata.pricer.curve.RatesProviderGenerator;
import com.opengamma.strata.pricer.curve.SyntheticRatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.DiscountIborIndexRates;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.ResolvedTrade;

/**
 * Multi-curve provider implied by an existing provider and a curve group definition.
 * 
 * @author Marc Henrard
 */
public class RatesProviderImpliedGroupDefinition {
  
  private static final SyntheticRatesCurveCalibrator SYNTHETIC_CALIBRATOR =
      SyntheticRatesCurveCalibrator.standard();
  /** The matrix algebra used for matrix inversion. */
  private static final MatrixAlgebra MATRIX_ALGEBRA = new CommonsMatrixAlgebra();
  /** The calibration measures. This is used to compute the Jacobian matrices. */
  private static final CalibrationMeasures MEASURES = CalibrationMeasures.PAR_SPREAD;

  /**
   * Generate the multi-curve from an existing multi-curve and the group definition.
   * <p>
   * The multi-curve has Jacobian matrices.
   * <p>
   * Restrictions:
   * - Only discounting and Ibor forward curves (no inflation)
   * - Generated curves should be zero rate or discount factor based
   * - IborIndexRates of the starting curves should be of the type DiscountIborIndexRates
   * 
   * @param group  the new group
   * @param multicurve  the initial multi-curve framework
   * @param refData  the reference data
   * @return the implied multi-curve
   */
  public static ImmutableRatesProvider generate(
      RatesCurveGroupDefinition group,
      RatesProvider multicurve,
      ReferenceData refData) {
    
    Map<Index, LocalDateDoubleTimeSeries> ts = ImmutableMap.of();
    RatesCurveGroupDefinition groupDefnBound =
        group.bindTimeSeries(multicurve.getValuationDate(), ts);
    ImmutableRatesProvider providerCombined = ImmutableRatesProvider.builder(multicurve.getValuationDate()).build();
    RatesProviderGenerator providerGenerator = ImmutableRatesProviderGenerator.of(providerCombined, groupDefnBound, refData);
    int nbParametersTotal = group.getTotalParameterCount();
    ImmutableRatesProvider impliedProvider0 = // Provider with parameters of value 0
        providerGenerator.generate(DoubleArray.filled(nbParametersTotal));
    // Generate the curves y values
    int nbDef = group.getCurveDefinitions().size();
    DoubleArray parameters = DoubleArray.EMPTY;
    for (int loopdef = 0; loopdef < nbDef; loopdef++) {
      CurveDefinition def = groupDefnBound.getCurveDefinitions().get(loopdef);
      RatesCurveGroupEntry entry = group.findEntry(def.getName()).get();
      ValueType yType = def.getYValueType();
      ArgChecker.isTrue(yType.equals(ValueType.DISCOUNT_FACTOR) || yType.equals(ValueType.ZERO_RATE),
          "Only discount factors and zero rates supported");
      DiscountFactors df = null;
      Set<Currency> currencies = entry.getDiscountCurrencies();
      ArgChecker.isTrue(currencies.size() <= 1, "one currency");
      Curve curve0 = impliedProvider0.getCurves().get(entry.getCurveName());
      ArgChecker.isTrue(curve0 instanceof InterpolatedNodalCurve, "interpolated curve");
      InterpolatedNodalCurve interpolatedCurve0 = (InterpolatedNodalCurve) curve0;
      DoubleArray times = interpolatedCurve0.getXValues();
      int nbTimes = times.size();
      if (!currencies.isEmpty()) {
        Currency ccy = currencies.iterator().next();
        df = multicurve.discountFactors(ccy);
      } else {
        ImmutableSet<Index> indices = entry.getIndices();
        Index index = indices.iterator().next();
        ArgChecker.isTrue(index instanceof IborIndex, "Only IBOR if not currency");
        IborIndex ibor = (IborIndex) index;
        IborIndexRates rates = multicurve.iborIndexRates(ibor);
        ArgChecker.isTrue(rates instanceof DiscountIborIndexRates, "Only discount Ibor rates");
        df = ((DiscountIborIndexRates) rates).getDiscountFactors();
      }
      double[] yValues = new double[nbTimes];
      for (int i = 0; i < nbTimes; i++) {
        if (yType.equals(ValueType.DISCOUNT_FACTOR)) {
          yValues[i] = df.discountFactor(times.get(i));
        } else {  // Zero-rate
          yValues[i] = df.zeroRate(times.get(i));
        }
      }
      parameters = parameters.concat(DoubleArray.ofUnsafe(yValues));
    }
    ImmutableRatesProvider multicurveNoJacobian = providerGenerator.generate(parameters);
    MarketData marketData = SYNTHETIC_CALIBRATOR.marketData(group, multicurveNoJacobian, refData);
    ImmutableList<ResolvedTrade> trades = groupDefnBound.resolvedTrades(marketData, refData);
    ImmutableList<CurveParameterSize> orderGroup = toOrder(groupDefnBound);
    ImmutableMap<CurveName, JacobianCalibrationMatrix> jacobians = 
        updateJacobiansForGroup(multicurveNoJacobian, trades, orderGroup, orderGroup);
    ImmutableRatesProvider multicurveImplied = 
        providerGenerator.generate(parameters, jacobians);
    return multicurveImplied;
  }

  /**
   * Generate the multi-curve from an existing multi-curve and the group definition.
   * <p>
   * The multi-curve has Jacobian matrices.
   * <p>
   * Restrictions:
   * - Only discounting and Ibor forward curves (no inflation)
   * - Generated curves should be zero rate or discount factor based
   * - IborIndexRates are generated by pseudo-discount-factors through the accumulation of ibor rates
   * 
   * @param group  the new group
   * @param multicurve  the initial multi-curve framework
   * @param refData  the reference data
   * @return the implied multi-curve
   */
  public static ImmutableRatesProvider generateGeneric(
      RatesCurveGroupDefinition group,
      RatesProvider multicurve,
      Map<Index, LocalDateDoubleTimeSeries> ts,
      ReferenceData refData) {
    
    LocalDate valuationDate = multicurve.getValuationDate();
    RatesCurveGroupDefinition groupDefnBound = group.bindTimeSeries(multicurve.getValuationDate(), ts);
    ImmutableRatesProvider providerCombined = ImmutableRatesProvider.builder(multicurve.getValuationDate())
        .timeSeries(ts).build();
    RatesProviderGenerator providerGenerator = ImmutableRatesProviderGenerator.of(providerCombined, groupDefnBound, refData);
    int nbParametersTotal = group.getTotalParameterCount();
    ImmutableRatesProvider impliedProvider0 = // Provider with parameters of value 0
        providerGenerator.generate(DoubleArray.filled(nbParametersTotal));
    // Generate the curves y values
    int nbDef = group.getCurveDefinitions().size();
    DoubleArray parameters = DoubleArray.EMPTY;
    for (int loopdef = 0; loopdef < nbDef; loopdef++) {
      CurveDefinition def = groupDefnBound.getCurveDefinitions().get(loopdef);
      RatesCurveGroupEntry entry = group.findEntry(def.getName()).get();
      ValueType yType = def.getYValueType();
      ArgChecker.isTrue(yType.equals(ValueType.DISCOUNT_FACTOR) || yType.equals(ValueType.ZERO_RATE),
          "Only discount factors and zero rates supported");
      DiscountFactors df = null;
      Set<Currency> currencies = entry.getDiscountCurrencies();
      ArgChecker.isTrue(currencies.size() <= 1, "one currency");
      Curve curve0 = impliedProvider0.getCurves().get(entry.getCurveName());
      ArgChecker.isTrue(curve0 instanceof InterpolatedNodalCurve, "interpolated curve");
      InterpolatedNodalCurve interpolatedCurve0 = (InterpolatedNodalCurve) curve0;
      DoubleArray times = interpolatedCurve0.getXValues();
      int nbTimes = times.size();
      if (!currencies.isEmpty()) {
        Currency ccy = currencies.iterator().next();
        df = multicurve.discountFactors(ccy);
      } else {
        ImmutableSet<Index> indices = entry.getIndices();
        Index index = indices.iterator().next();
        ArgChecker.isTrue(index instanceof IborIndex, "Only IBOR if not currency");
        IborIndex ibor = (IborIndex) index;
        IborIndexRates rates = multicurve.iborIndexRates(ibor);
        // Generate pseudo discount factors at successive IBOR dates 
        DayCount dayCount = curve0.getMetadata().getInfo(CurveInfoType.DAY_COUNT);
        LocalDate fixingDate = multicurve.getValuationDate();
        double endTime = 0.0;
        List<Double> dfList = new ArrayList<>();
        dfList.add(1.0);
        List<Double> tList = new ArrayList<>();
        tList.add(dayCount.relativeYearFraction(valuationDate, ibor.calculateEffectiveFromFixing(fixingDate, refData)));
        double dfMaturity = 1.0d;
        while (endTime < times.get(nbTimes-1)) {
          IborIndexObservation obs = IborIndexObservation.of(ibor, fixingDate, refData);
          double rate = rates.rate(obs);
          endTime = dayCount.relativeYearFraction(valuationDate, obs.getMaturityDate());
          if(dfList.size() == 1) { // Need to adjust the first df to take the spot df into account
            double depositTime = dayCount.relativeYearFraction(obs.getEffectiveDate(), obs.getMaturityDate());
            dfMaturity /= Math.pow(1.0d + obs.getYearFraction() * rate, endTime / depositTime);
          } else {
            dfMaturity /= 1.0d + obs.getYearFraction() * rate; // pseudo discount factor not maturity
          }
          dfList.add(dfMaturity);
          tList.add(endTime);
          fixingDate = ibor.calculateFixingFromEffective(obs.getMaturityDate(), refData);
        }
        Curve curvedf = InterpolatedNodalCurve.of(DefaultCurveMetadata.builder()
            .curveName("IBOR")
            .xValueType(ValueType.YEAR_FRACTION)
            .yValueType(ValueType.DISCOUNT_FACTOR)
            .dayCount(dayCount).build(),
            DoubleArray.of(tList.size(), i -> tList.get(i)), 
            DoubleArray.of(dfList.size(), i -> dfList.get(i)), 
            CurveInterpolators.LINEAR,
            CurveExtrapolators.FLAT, // 1 before 
            CurveExtrapolators.EXPONENTIAL);
        df = DiscountFactors.of(ibor.getCurrency(), valuationDate, curvedf);
      }
      double[] yValues = new double[nbTimes];
      for (int i = 0; i < nbTimes; i++) {
        if (yType.equals(ValueType.DISCOUNT_FACTOR)) {
          yValues[i] = df.discountFactor(times.get(i));
        } else {  // Zero-rate
          yValues[i] = df.zeroRate(times.get(i));
        }
      }
      parameters = parameters.concat(DoubleArray.ofUnsafe(yValues));
    }
    ImmutableRatesProvider multicurveNoJacobian = providerGenerator.generate(parameters);
    MarketData marketData = SYNTHETIC_CALIBRATOR.marketData(group, multicurveNoJacobian, refData); 
    // Computation time could be improved by a local implementation of market data and trades resolved only once
    ImmutableList<ResolvedTrade> trades = groupDefnBound.resolvedTrades(marketData, refData); // 3
    ImmutableList<CurveParameterSize> orderGroup = toOrder(groupDefnBound);
    ImmutableMap<CurveName, JacobianCalibrationMatrix> jacobians = 
        updateJacobiansForGroup(multicurveNoJacobian, trades, orderGroup, orderGroup);  // 1
    ImmutableRatesProvider multicurveImplied = providerGenerator.generate(parameters, jacobians);
    return multicurveImplied;
  }
  
  /**
   * Generate the multi-curve from an existing multi-curve and the group definition.
   * <p>
   * The multi-curve does not have Jacobian matrices.
   * 
   * @param group  the new group
   * @param multicurve  the initial multi-curve framework
   * @param refData  the reference data
   * @return the implied multi-curve
   */
  public static ImmutableRatesProvider generateNoJacobian(
      RatesCurveGroupDefinition group,
      ImmutableRatesProvider multicurve,
      ReferenceData refData) {
    
    RatesCurveGroupDefinition groupDefnBound =
        group.bindTimeSeries(multicurve.getValuationDate(), multicurve.getTimeSeries());
    ImmutableRatesProvider providerCombined = ImmutableRatesProvider.builder(multicurve.getValuationDate()).build();
    RatesProviderGenerator providerGenerator = ImmutableRatesProviderGenerator.of(providerCombined, groupDefnBound, refData);
    int nbParametersTotal = group.getTotalParameterCount();
    ImmutableRatesProvider impliedProvider0 = // Provider with parameters of value 0
        providerGenerator.generate(DoubleArray.filled(nbParametersTotal));
    // Generate the curves y values
    int nbDef = group.getCurveDefinitions().size();
    DoubleArray parameters = DoubleArray.EMPTY;
    for (int loopdef = 0; loopdef < nbDef; loopdef++) {
      CurveDefinition def = groupDefnBound.getCurveDefinitions().get(loopdef);
      RatesCurveGroupEntry entry = group.findEntry(def.getName()).get();
      ValueType yType = def.getYValueType();
      ArgChecker.isTrue(yType.equals(ValueType.DISCOUNT_FACTOR) || yType.equals(ValueType.ZERO_RATE),
          "Only discount factors and zero rates supported");
      DiscountFactors df = null;
      Set<Currency> currencies = entry.getDiscountCurrencies();
      ArgChecker.isTrue(currencies.size() <= 1, "one currency");
      if (!currencies.isEmpty()) {
        Currency ccy = currencies.iterator().next();
        df = multicurve.discountFactors(ccy);
      } else {
        ImmutableSet<Index> indices = entry.getIndices();
        Index index = indices.iterator().next();
        ArgChecker.isTrue(index instanceof IborIndex, "Only IBOR if not currency");
        IborIndex ibor = (IborIndex) index;
        IborIndexRates rates = multicurve.iborIndexRates(ibor);
        ArgChecker.isTrue(rates instanceof DiscountIborIndexRates, "Only discount Ibor rates");
        df = ((DiscountIborIndexRates) rates).getDiscountFactors();
      }
      Curve curve0 = impliedProvider0.getCurves().get(entry.getCurveName());
      ArgChecker.isTrue(curve0 instanceof InterpolatedNodalCurve, "interpolated curve");
      DoubleArray times = ((InterpolatedNodalCurve) curve0).getXValues();
      int nbTimes = times.size();
      double[] yValues = new double[nbTimes];
      for (int i = 0; i < nbTimes; i++) {
        if (yType.equals(ValueType.DISCOUNT_FACTOR)) {
          yValues[i] = df.discountFactor(times.get(i));
        } else {  // Zero-rate
          yValues[i] = df.zeroRate(times.get(i));
        }
      }
      parameters = parameters.concat(DoubleArray.ofUnsafe(yValues));
    }
    ImmutableRatesProvider impliedProviderValues = providerGenerator.generate(parameters);
    return impliedProviderValues;
  }

  // --------------------------------------------------------------------------------------------------------------------
  // Methods below copied from RatesCurveCalibrator in Strata 2.1.0 as those methods are private in the original version.
  
  // converts a definition to the curve order list
  private static ImmutableList<CurveParameterSize> toOrder(RatesCurveGroupDefinition groupDefn) {
    return groupDefn.getCurveDefinitions().stream().map(def -> def.toCurveParameterSize()).collect(toImmutableList());
  }

  // calculates the Jacobians
  private static ImmutableMap<CurveName, JacobianCalibrationMatrix> updateJacobiansForGroup(
      ImmutableRatesProvider provider,
      ImmutableList<ResolvedTrade> trades,
      ImmutableList<CurveParameterSize> orderGroup,
      ImmutableList<CurveParameterSize> orderAll) {

    // sensitivity to all parameters in the stated order
    int totalParamsAll = orderAll.stream().mapToInt(e -> e.getParameterCount()).sum();
    DoubleMatrix res = derivatives(trades, provider, orderAll, totalParamsAll); // Slow part

    // jacobian direct
    int totalParamsGroup = orderGroup.stream().mapToInt(e -> e.getParameterCount()).sum();
    int totalParamsPrevious = totalParamsAll - totalParamsGroup;
    DoubleMatrix pDmCurrentMatrix = MATRIX_ALGEBRA.getInverse(res);
    
    // add to the map of jacobians, one entry for each curve in this group
    ImmutableMap.Builder<CurveName, JacobianCalibrationMatrix> jacobianBuilder = ImmutableMap.builder();
    int startIndex = 0;
    for (CurveParameterSize order : orderGroup) {
      int paramCount = order.getParameterCount();
      double[][] pDmCurveArray = new double[paramCount][totalParamsAll];
      // copy data for this group
      for (int p = 0; p < paramCount; p++) {
        System.arraycopy(pDmCurrentMatrix.rowArray(startIndex + p), 0, pDmCurveArray[p], totalParamsPrevious, totalParamsGroup);
      }
      // build final Jacobian matrix
      DoubleMatrix pDmCurveMatrix = DoubleMatrix.ofUnsafe(pDmCurveArray);
      jacobianBuilder.put(order.getName(), JacobianCalibrationMatrix.of(orderAll, pDmCurveMatrix));
      startIndex += paramCount;
    }
    return jacobianBuilder.build();
  }

  // calculate the derivatives
  private static DoubleMatrix derivatives(
      ImmutableList<ResolvedTrade> trades,
      ImmutableRatesProvider provider,
      ImmutableList<CurveParameterSize> orderAll,
      int totalParamsAll) {

    return DoubleMatrix.ofArrayObjects(
        trades.size(),
        totalParamsAll,
        i -> MEASURES.derivative(trades.get(i), provider, orderAll));
  }

}
