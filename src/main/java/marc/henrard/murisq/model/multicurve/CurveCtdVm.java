/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.joda.beans.Bean;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.ImmutableConstructor;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.ImmutableOvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.OvernightIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.CurveParameterSize;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.JacobianCalibrationMatrix;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.param.CurrencyParameterSensitivity;
import com.opengamma.strata.market.param.ParameterMetadata;
import com.opengamma.strata.market.param.ParameterSize;
import com.opengamma.strata.market.param.ParameterizedDataCombiner;
import com.opengamma.strata.market.param.UnitParameterSensitivity;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.ZeroRateSensitivity;
import com.opengamma.strata.pricer.rate.DiscountOvernightIndexRates;
import com.opengamma.strata.pricer.rate.OvernightIndexRates;

/**
 * Curve constructed as the intrinsic cheapest-to-deliver collateral (variation margin) pseudo-discount curve 
 * from a list of existing collateral pseudo-discounting curves.
 * <p>
 * The CTD curve is build by checking the overnight forward rates of each curve and storing the periods on which
 * one curve is the cheapest (highest rate). The discount factors between those periods are the discount factors
 * of the individual curves adjusted for the previous periods.
 */
@BeanDefinition
public class CurveCtdVm
    implements Curve, ImmutableBean, Serializable {

  /** Year fraction used as an effective zero. */
  private static final double EFFECTIVE_ZERO = 1e-10;
  /** Internal. Only the 'Actual' part of the day count is important. */
  private static final DayCount DC_ON = DayCounts.ACT_360;

  /** The underlying curves. */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<Curve> underlyingCurves;
  /** The common valuation date for all discounting factors. */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate valuationDate;
  /** The final date. */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate finalDate;
  /** The common currency of all the discount factors. */
  @PropertyDefinition(validate = "notNull")
  private final Currency currency;
  /** The day count */
  @PropertyDefinition(validate = "notNull")
  private final DayCount dayCount;
  /** The dates immediately after a CTD curve change, i.e. index i is valid for fixing in [t_i, t_{i+1}) */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<LocalDate> bounds;
  /** The index of the cheapest to deliver discount factor on the period delimited by the bounds.
   * The i-th index is valid for fixing in the period [t_i, t_{i+1}) */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<Integer> indexCurve;
  /** The discount factor for the bound dates. */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<Double> discountFactorsAtBounds;
  /** The discount factor for the curve in the next period at the bound dates. */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<Double> discountFactorsNextPeriodAtBounds;
  /** The sensitivity of the discount factors at the bound dates. */
  // This data can be viewed as the building blocks of the Jacobian matrix. 
  // If the curves are used only for PV purposes, the construction of this object could be skipped.
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<UnitParameterSensitivity> dfSensitivitiesAtBounds;
  /** The sensitivity of the discount factors at the bound dates. */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableList<UnitParameterSensitivity> dfSensitivitiesNextPeriodAtBounds;
  /** The calendar used to select the forward periods. */
  @PropertyDefinition(validate = "notNull")
  private final HolidayCalendar baseCalendar;
  /** The cheapest-to-delivery curve metadata. */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final CurveMetadata metadata;
  /** The doubles associated to the bounds by the relative time method. */
  private final transient double[] boundsDouble;  // cached, not a property
  /** The underlying discount factors. */
  private final transient DiscountFactors[] underlyingDiscountFactors;  // cached, not a property
  /** The parameter combiner. */
  private final transient ParameterizedDataCombiner paramCombiner;  // cached, not a property
  /** The split between the curves. */
  private final transient List<ParameterSize> parameterSplit;  // cached, not a property

  /**
   * Create a cheapest-to-deliver (CTD) curve from underlying curves.
   * <p>
   * The forwards rates are computed for each business days. The calendar used to select business days is provided. 
   * For each business day period, the curve associated to the CTD rate, i.e. the highest rate, is stored. 
   * The consecutive period with the same CTD curve are aggregated together in a single period. The resulting
   * curve glues together the different discounting factors on the periods of distinct CTD.
   * 
   * @param name  the name of the aggregated curve
   * @param finalDate  the date up to which the forward comparison between forward rates should be carried
   * @param baseCalendar  the calendar used to select the forward periods
   * @param valuationDate  the valuation date of the curve to be constructed
   * @param dayCount  the day count used for time measurement in the CTD curve
   * @param currency  the currency for the CTD curve
   * @param underlyingCurves  the underlying curves from which the CTD will be constructed
   * @return the CTD curve
   */
  public static CurveCtdVm of(
      CurveName name,
      LocalDate finalDate,
      HolidayCalendar baseCalendar,
      LocalDate valuationDate,
      DayCount dayCount,
      Currency currency,
      List<Curve> underlyingCurves) {

    ArgChecker.isTrue(underlyingCurves.size() > 0, "must have at least one discount factor");
    int nbCurves = underlyingCurves.size();
    ArgChecker.notNull(underlyingCurves, "curves");
    DiscountFactors[] underlyingDiscountFactors = new DiscountFactors[nbCurves];
    for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
      underlyingDiscountFactors[loopcurve] = DiscountFactors.of(currency, valuationDate, underlyingCurves.get(loopcurve));
    }
    List<ParameterSize> parameterSplit = new ArrayList<>();
    int nbParameters = 0;
    int nbDependencies = 0;
    List<CurveParameterSize> orderTotal = new ArrayList<>();
    for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
      parameterSplit.add(ParameterSize.of(underlyingCurves.get(loopcurve).getName(), underlyingCurves.get(loopcurve).getParameterCount()));
      nbParameters += underlyingCurves.get(loopcurve).getParameterCount();
      JacobianCalibrationMatrix m = underlyingCurves.get(loopcurve).getMetadata().getInfo(CurveInfoType.JACOBIAN);
      orderTotal.addAll(m.getOrder());
      nbDependencies += m.getTotalParameterCount();
    }
    /* Collect the metadata of the CTD curve. */
    List<ParameterMetadata> parameterMetadata = new ArrayList<>();
    for (int i = 0; i < underlyingDiscountFactors.length; i++) {
      for (int j = 0; j < underlyingDiscountFactors[i].getParameterCount(); j++) {
        parameterMetadata.add(underlyingDiscountFactors[i].getParameterMetadata(j));
      }
    }
    /* Jacobian */
    double[][] matrixTotal = new double[nbParameters][nbDependencies];
    int rowsFilled = 0;
    int columnsFilled = 0;
    for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
      JacobianCalibrationMatrix jac = underlyingCurves.get(loopcurve).getMetadata().getInfo(CurveInfoType.JACOBIAN);
      double[][] mat = jac.getJacobianMatrix().toArrayUnsafe();
      int nbParam = mat.length;
      int nbQuotes = jac.getTotalParameterCount(); 
      for (int looprow = 0; looprow < nbParam; looprow++) {
        System.arraycopy(mat[looprow], 0, matrixTotal[rowsFilled + looprow], columnsFilled, nbQuotes);
      }
      rowsFilled += nbParam;
      columnsFilled += nbQuotes;
    }
    JacobianCalibrationMatrix jacTotal = JacobianCalibrationMatrix.of(orderTotal, DoubleMatrix.ofUnsafe(matrixTotal));
    CurveMetadata metadata = DefaultCurveMetadata.builder()
        .curveName(name)
        .dayCount(dayCount)
        .parameterMetadata(parameterMetadata)
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.DISCOUNT_FACTOR)
        .jacobian(jacTotal)
        .build();
    /* Forwards */
    ImmutableOvernightIndex baseIndex = ImmutableOvernightIndex.builder()
        .currency(currency)
        .fixingCalendar(baseCalendar.getId())
        .dayCount(DC_ON)
        .name("OnTmp").build(); // Used to compute the forwards
    OvernightIndex[] syntheticOnIndices = new OvernightIndex[nbCurves];
    OvernightIndexRates[] overnightRates = new OvernightIndexRates[nbCurves];
    for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
      syntheticOnIndices[loopcurve] = baseIndex.toBuilder().name("SyntheticIndex" + loopcurve).build();
      overnightRates[loopcurve] =
          DiscountOvernightIndexRates.of(syntheticOnIndices[loopcurve], underlyingDiscountFactors[loopcurve]);
    }
    List<Integer> indexCurve = new ArrayList<>();
    List<LocalDate> bounds = new ArrayList<>();
    // The dates immediately after a curve change, i.e. index i is valid for fixing in [t_i, t_{i+1})
    bounds.add(valuationDate);
    int indexStep = 0;
    int runningIndex = 0;
    LocalDate currentDate = valuationDate;
    LocalDate currentDateP1 = baseCalendar.next(currentDate);
    { // Initial values start
      double[] fwd = new double[nbCurves];
      for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
        OvernightIndexObservation obs = onObservation(currentDate, currentDateP1, syntheticOnIndices[loopcurve]);
        fwd[loopcurve] = overnightRates[loopcurve].rate(obs);
      }
      runningIndex = argmax(fwd);
      indexCurve.add(runningIndex);
      currentDate = currentDateP1;
      currentDateP1 = baseCalendar.next(currentDate);
    } // Initial values end
    List<Double> discountFactorsAtBounds = new ArrayList<>();
    List<Double> discountFactorsNextPeriodAtBounds = new ArrayList<>();
    List<UnitParameterSensitivity> dfSensitivitiesAtBounds = new ArrayList<>();
    List<UnitParameterSensitivity> dfSensitivitiesNextPeriodAtBounds = new ArrayList<>();
    discountFactorsAtBounds.add(1.0d); // Df at valuation date is 1.0
    discountFactorsNextPeriodAtBounds.add(1.0d); // Df at valuation date is 1.0
    dfSensitivitiesAtBounds.add(
        UnitParameterSensitivity.of(name, parameterMetadata, DoubleArray.filled(nbParameters), parameterSplit));
    dfSensitivitiesNextPeriodAtBounds.add(
        UnitParameterSensitivity.of(name, parameterMetadata, DoubleArray.filled(nbParameters), parameterSplit));
    while (currentDate.isBefore(finalDate)) {  // Loop on all business dates up to the final date
      double[] fwd = new double[nbCurves];
      for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
        OvernightIndexObservation obs = onObservation(currentDate, currentDateP1, syntheticOnIndices[loopcurve]);
        fwd[loopcurve] = overnightRates[loopcurve].rate(obs);
      }
      int currentIndex = argmax(fwd);
      if (currentIndex != runningIndex) { // Change of the CTD
        bounds.add(currentDate);
        indexCurve.add(currentIndex);
        boundDetailsUpdate(
            discountFactorsAtBounds,
            dfSensitivitiesAtBounds,
            underlyingDiscountFactors[runningIndex],
            indexStep,
            currentDate,
            bounds.get(indexStep),
            underlyingCurves,
            metadata,
            parameterSplit);

        double dfNextCurrent = underlyingDiscountFactors[currentIndex].discountFactor(currentDate);
        discountFactorsNextPeriodAtBounds.add(dfNextCurrent);

        ZeroRateSensitivity startPtsSensi = underlyingDiscountFactors[currentIndex]
            .zeroRatePointSensitivity(currentDate);
        CurrencyParameterSensitivities startParamSensi =
            underlyingDiscountFactors[currentIndex].parameterSensitivity(startPtsSensi);
        UnitParameterSensitivity startUnitSensi =
            unitSensitivity(startParamSensi.getSensitivities().get(0), underlyingCurves, metadata, parameterSplit);
        dfSensitivitiesNextPeriodAtBounds.add(startUnitSensi);

        runningIndex = currentIndex;
        indexStep++;
      }
      currentDate = currentDateP1;
      currentDateP1 = baseCalendar.next(currentDate);
    } // End while - Loop on all business dates up to the final date
    return new CurveCtdVm(
        underlyingCurves,
        valuationDate,
        finalDate,
        currency,
        dayCount,
        bounds,
        indexCurve,
        discountFactorsAtBounds,
        discountFactorsNextPeriodAtBounds,
        dfSensitivitiesAtBounds,
        dfSensitivitiesNextPeriodAtBounds,
        baseCalendar,
        metadata);
  }

  @ImmutableConstructor
  private CurveCtdVm(
      List<Curve> underlyingCurves,
      LocalDate valuationDate,
      LocalDate finalDate,
      Currency currency,
      DayCount dayCount,
      List<LocalDate> bounds,
      List<Integer> indexCurve,
      List<Double> discountFactorsAtBounds,
      List<Double> discountFactorsNextPeriodAtBounds,
      List<UnitParameterSensitivity> dfSensitivitiesAtBounds,
      List<UnitParameterSensitivity> dfSensitivitiesNextPeriodAtBounds,
      HolidayCalendar baseCalendar,
      CurveMetadata metadata) {

    this.underlyingCurves = ImmutableList.copyOf(ArgChecker.notNull(underlyingCurves, "curves"));
    int nbCurves = underlyingCurves.size();
    this.underlyingDiscountFactors = new DiscountFactors[nbCurves];
    for (int loopcurve = 0; loopcurve < nbCurves; loopcurve++) {
      underlyingDiscountFactors[loopcurve] = DiscountFactors.of(currency, valuationDate, underlyingCurves.get(loopcurve));
    }
    this.valuationDate = valuationDate;
    this.finalDate = finalDate;
    this.currency = currency;
    this.bounds = ImmutableList.copyOf(bounds);
    this.boundsDouble = new double[bounds.size()];
    for (int loopbound = 0; loopbound < bounds.size(); loopbound++) {
      boundsDouble[loopbound] = underlyingDiscountFactors[0].relativeYearFraction(bounds.get(loopbound));
    }
    this.indexCurve = ImmutableList.copyOf(indexCurve);
    this.discountFactorsAtBounds = ImmutableList.copyOf(discountFactorsAtBounds);
    this.discountFactorsNextPeriodAtBounds = ImmutableList.copyOf(discountFactorsNextPeriodAtBounds);
    this.dfSensitivitiesAtBounds = ImmutableList.copyOf(dfSensitivitiesAtBounds);
    this.dfSensitivitiesNextPeriodAtBounds = ImmutableList.copyOf(dfSensitivitiesNextPeriodAtBounds);
    this.baseCalendar = baseCalendar;
    this.paramCombiner = ParameterizedDataCombiner.of(this.underlyingCurves);
    this.metadata = metadata;
    this.parameterSplit = new ArrayList<>();
    for (int i = 0; i < underlyingCurves.size(); i++) {
      parameterSplit.add(ParameterSize.of(underlyingCurves.get(i).getName(), underlyingCurves.get(i).getParameterCount()));
    }
    this.dayCount = dayCount;
  }

  // ensure standard constructor is invoked
  private Object readResolve() {
    return new CurveCtdVm(
        underlyingCurves,
        valuationDate,
        finalDate,
        currency,
        dayCount,
        bounds,
        indexCurve,
        discountFactorsAtBounds,
        discountFactorsNextPeriodAtBounds,
        dfSensitivitiesAtBounds,
        dfSensitivitiesNextPeriodAtBounds,
        baseCalendar,
        metadata);
  }

  // Returns the index for which an array of double has its maximum value
  private static int argmax(double[] values) {
    int argmax = 0;
    double maxValue = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < values.length; i++) {
      if (values[i] > maxValue) {
        maxValue = values[i];
        argmax = i;
      }
    }
    return argmax;
  }

  // Create an overnight observation with the synthetic index
  private static OvernightIndexObservation onObservation(
      LocalDate currentDate,
      LocalDate currentDateP1,
      OvernightIndex index) {

    return OvernightIndexObservation.builder()
        .fixingDate(currentDate)
        .publicationDate(currentDate)
        .effectiveDate(currentDate)
        .maturityDate(currentDateP1)
        .index(index)
        .yearFraction(DC_ON.relativeYearFraction(currentDate, currentDateP1)).build();
  }

  /**
   * Update the details related to the CTD bounds. The method is only used in the constructor. The lists of
   * discount factors and their sensitivities are updated, one new element is added at the end of each list.
   * 
   * @param discountFactorsAtBounds  the list of discount factors at the previous bounds
   * @param dfSensitivitiesAtBounds  the list of discount factors sensitivities at the previous bounds
   * @param discountFactor  the discount factor object used for the new values
   * @param indexStep  the index of the last element of the lists
   * @param currentDate  the date used for the new bounds
   * @param lowerBound  the previous bound
   */
  private static void boundDetailsUpdate(
      List<Double> discountFactorsAtBounds,
      List<UnitParameterSensitivity> dfSensitivitiesAtBounds,
      DiscountFactors discountFactor,
      int indexStep,
      LocalDate currentDate,
      LocalDate lowerBound,
      List<Curve> underlyingCurves,
      CurveMetadata metadata,
      List<ParameterSize> parameterSplit) {

    double dfStart = discountFactor.discountFactor(lowerBound);
    double dfCurrent = discountFactor.discountFactor(currentDate);
    double dfStep = dfCurrent / dfStart;
    discountFactorsAtBounds.add(discountFactorsAtBounds.get(indexStep) * dfStep);
    ZeroRateSensitivity startPtsSensi =
        discountFactor.zeroRatePointSensitivity(currentDate).multipliedBy(1.0d / dfStart);
    CurrencyParameterSensitivities startParamSensi = discountFactor.parameterSensitivity(startPtsSensi);
    UnitParameterSensitivity startUnitSensi =
        unitSensitivity(startParamSensi.getSensitivities().get(0), underlyingCurves, metadata, parameterSplit);
    ZeroRateSensitivity endPtsSensi = discountFactor
        .zeroRatePointSensitivity(lowerBound).multipliedBy(-dfCurrent / (dfStart * dfStart));
    CurrencyParameterSensitivities endParamSensi = discountFactor.parameterSensitivity(endPtsSensi);
    UnitParameterSensitivity endUnitSensi =
        unitSensitivity(endParamSensi.getSensitivities().get(0), underlyingCurves, metadata, parameterSplit);
    UnitParameterSensitivity stepUnitSensi = startUnitSensi.plus(endUnitSensi);
    dfSensitivitiesAtBounds.add(dfSensitivitiesAtBounds.get(indexStep).multipliedBy(dfStep)
        .plus(stepUnitSensi.multipliedBy(discountFactorsAtBounds.get(indexStep))));
  }

  // From a curve name, retrieve the index of the associated curve
  private static int indexByName(CurveName name, List<Curve> underlyingCurves) {
    for (int i = 0; i < underlyingCurves.size(); i++) {
      if (underlyingCurves.get(i).getName().equals(name)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Unable to find curve with name: " + name);
  }

  @Override
  public int getParameterCount() {
    return paramCombiner.getParameterCount();
  }

  @Override
  public double getParameter(int parameterIndex) {
    return paramCombiner.getParameter(parameterIndex);
  }

  @Override
  public ParameterMetadata getParameterMetadata(int parameterIndex) {
    return paramCombiner.getParameterMetadata(parameterIndex);
  }

  @Override
  public CurveCtdVm withParameter(int parameterIndex, double newValue) {
    List<Curve> perturbed = paramCombiner.withParameter(Curve.class, parameterIndex, newValue);
    return of(metadata.getCurveName(), finalDate, baseCalendar, valuationDate, dayCount, currency, perturbed);
  }

  @Override
  public double yValue(double x) {
    int stepIndex = Arrays.binarySearch(boundsDouble, x);
    if (stepIndex == 0) { // On valuation date
      return 1.0d;
    }
    if (stepIndex < 0) {
      stepIndex = -stepIndex - 1;
    } else {
      stepIndex = stepIndex + 1;
    }
    return discountFactorsAtBounds.get(stepIndex - 1) *
        underlyingDiscountFactors[indexCurve.get(stepIndex - 1)].discountFactor(x) /
        discountFactorsNextPeriodAtBounds.get(stepIndex - 1);
  }

  @Override
  public UnitParameterSensitivity yValueParameterSensitivity(double x) {
    if (x < EFFECTIVE_ZERO) {
      return UnitParameterSensitivity.of(metadata.getCurveName(), metadata.getParameterMetadata().get(),
          DoubleArray.filled(getParameterCount()), parameterSplit);
    }
    int stepIndex = Arrays.binarySearch(boundsDouble, x);
    if (stepIndex < 0) {
      stepIndex = -stepIndex - 1;
    } else {
      stepIndex = stepIndex + 1;
    }
    double dfComp = discountFactorsAtBounds.get(stepIndex - 1);
    double dfEnd = underlyingDiscountFactors[indexCurve.get(stepIndex - 1)].discountFactor(x);
    double dfStart = discountFactorsNextPeriodAtBounds.get(stepIndex - 1);
    UnitParameterSensitivity startUnitSensi =
        dfSensitivitiesNextPeriodAtBounds.get(stepIndex - 1).multipliedBy(-dfComp * dfEnd / (dfStart * dfStart));
    ZeroRateSensitivity endPtsSensi = underlyingDiscountFactors[indexCurve.get(stepIndex - 1)]
        .zeroRatePointSensitivity(x).multipliedBy(dfComp / dfStart);
    CurrencyParameterSensitivities endParamSensi =
        underlyingDiscountFactors[indexCurve.get(stepIndex - 1)].parameterSensitivity(endPtsSensi);
    // Only one part in the sensitivities as DF is by construction made of only one curve.
    UnitParameterSensitivity endUnitSensi =
        unitSensitivity(endParamSensi.getSensitivities().get(0), underlyingCurves, metadata, parameterSplit);
    UnitParameterSensitivity compUnitSensi =
        dfSensitivitiesAtBounds.get(stepIndex - 1).multipliedBy(dfEnd / dfStart);
    UnitParameterSensitivity totalUnitSensi = startUnitSensi.plus(endUnitSensi).plus(compUnitSensi);
    return totalUnitSensi;
  }

  // From the sensitivity to the parameters of one underlying curve, create the sensitivity vector to all curves.
  private static UnitParameterSensitivity unitSensitivity(
      CurrencyParameterSensitivity sensi,
      List<Curve> underlyingCurves,
      CurveMetadata metadata,
      List<ParameterSize> parameterSplit) {

    DoubleArray unitSensi = DoubleArray.EMPTY;
    int indexCurve = indexByName((CurveName) sensi.getMarketDataName(), underlyingCurves);
    for (int i = 0; i < underlyingCurves.size(); i++) {
      if (i == indexCurve) {
        unitSensi = unitSensi.concat(sensi.getSensitivity());
      } else {
        unitSensi = unitSensi.concat(DoubleArray.filled(underlyingCurves.get(i).getParameterCount()));
      }
    }
    return UnitParameterSensitivity
        .of(metadata.getCurveName(), metadata.getParameterMetadata().get(), unitSensi, parameterSplit);
  }

  @Override
  public double firstDerivative(double x) {
    int stepIndex = Arrays.binarySearch(boundsDouble, x);
    if (stepIndex == 0) { // On valuation date
      return 1.0d;
    }
    if (stepIndex < 0) {
      stepIndex = -stepIndex - 1;
    } else {
      stepIndex = stepIndex + 1;
    }
    double factor = discountFactorsAtBounds.get(stepIndex - 1) /
        discountFactorsNextPeriodAtBounds.get(stepIndex - 1);
    DiscountFactors df = underlyingDiscountFactors[indexCurve.get(stepIndex - 1)];
    return factor * df.discountFactorTimeDerivative(x);
  }

  @Override
  public CurveCtdVm withMetadata(CurveMetadata metadata) {
    return new CurveCtdVm(
        underlyingCurves,
        valuationDate,
        finalDate,
        currency,
        dayCount,
        bounds,
        indexCurve,
        discountFactorsAtBounds,
        discountFactorsNextPeriodAtBounds,
        dfSensitivitiesAtBounds,
        dfSensitivitiesNextPeriodAtBounds,
        baseCalendar,
        metadata);
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code CurveCtdVm}.
   * @return the meta-bean, not null
   */
  public static CurveCtdVm.Meta meta() {
    return CurveCtdVm.Meta.INSTANCE;
  }

  static {
    MetaBean.register(CurveCtdVm.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static CurveCtdVm.Builder builder() {
    return new CurveCtdVm.Builder();
  }

  @Override
  public CurveCtdVm.Meta metaBean() {
    return CurveCtdVm.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the underlying curves.
   * @return the value of the property, not null
   */
  public ImmutableList<Curve> getUnderlyingCurves() {
    return underlyingCurves;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the common valuation date for all discounting factors.
   * @return the value of the property, not null
   */
  public LocalDate getValuationDate() {
    return valuationDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the final date.
   * @return the value of the property, not null
   */
  public LocalDate getFinalDate() {
    return finalDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the common currency of all the discount factors.
   * @return the value of the property, not null
   */
  public Currency getCurrency() {
    return currency;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day count
   * @return the value of the property, not null
   */
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the dates immediately after a CTD curve change, i.e. index i is valid for fixing in [t_i, t_{i+1})
   * @return the value of the property, not null
   */
  public ImmutableList<LocalDate> getBounds() {
    return bounds;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the indexCurve.
   * @return the value of the property, not null
   */
  public ImmutableList<Integer> getIndexCurve() {
    return indexCurve;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the discount factor for the bound dates.
   * @return the value of the property, not null
   */
  public ImmutableList<Double> getDiscountFactorsAtBounds() {
    return discountFactorsAtBounds;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the discount factor for the curve in the next period at the bound dates.
   * @return the value of the property, not null
   */
  public ImmutableList<Double> getDiscountFactorsNextPeriodAtBounds() {
    return discountFactorsNextPeriodAtBounds;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the dfSensitivitiesAtBounds.
   * @return the value of the property, not null
   */
  public ImmutableList<UnitParameterSensitivity> getDfSensitivitiesAtBounds() {
    return dfSensitivitiesAtBounds;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the sensitivity of the discount factors at the bound dates.
   * @return the value of the property, not null
   */
  public ImmutableList<UnitParameterSensitivity> getDfSensitivitiesNextPeriodAtBounds() {
    return dfSensitivitiesNextPeriodAtBounds;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the calendar used to select the forward periods.
   * @return the value of the property, not null
   */
  public HolidayCalendar getBaseCalendar() {
    return baseCalendar;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the cheapest-to-delivery curve metadata.
   * @return the value of the property, not null
   */
  @Override
  public CurveMetadata getMetadata() {
    return metadata;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      CurveCtdVm other = (CurveCtdVm) obj;
      return JodaBeanUtils.equal(underlyingCurves, other.underlyingCurves) &&
          JodaBeanUtils.equal(valuationDate, other.valuationDate) &&
          JodaBeanUtils.equal(finalDate, other.finalDate) &&
          JodaBeanUtils.equal(currency, other.currency) &&
          JodaBeanUtils.equal(dayCount, other.dayCount) &&
          JodaBeanUtils.equal(bounds, other.bounds) &&
          JodaBeanUtils.equal(indexCurve, other.indexCurve) &&
          JodaBeanUtils.equal(discountFactorsAtBounds, other.discountFactorsAtBounds) &&
          JodaBeanUtils.equal(discountFactorsNextPeriodAtBounds, other.discountFactorsNextPeriodAtBounds) &&
          JodaBeanUtils.equal(dfSensitivitiesAtBounds, other.dfSensitivitiesAtBounds) &&
          JodaBeanUtils.equal(dfSensitivitiesNextPeriodAtBounds, other.dfSensitivitiesNextPeriodAtBounds) &&
          JodaBeanUtils.equal(baseCalendar, other.baseCalendar) &&
          JodaBeanUtils.equal(metadata, other.metadata);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(underlyingCurves);
    hash = hash * 31 + JodaBeanUtils.hashCode(valuationDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(finalDate);
    hash = hash * 31 + JodaBeanUtils.hashCode(currency);
    hash = hash * 31 + JodaBeanUtils.hashCode(dayCount);
    hash = hash * 31 + JodaBeanUtils.hashCode(bounds);
    hash = hash * 31 + JodaBeanUtils.hashCode(indexCurve);
    hash = hash * 31 + JodaBeanUtils.hashCode(discountFactorsAtBounds);
    hash = hash * 31 + JodaBeanUtils.hashCode(discountFactorsNextPeriodAtBounds);
    hash = hash * 31 + JodaBeanUtils.hashCode(dfSensitivitiesAtBounds);
    hash = hash * 31 + JodaBeanUtils.hashCode(dfSensitivitiesNextPeriodAtBounds);
    hash = hash * 31 + JodaBeanUtils.hashCode(baseCalendar);
    hash = hash * 31 + JodaBeanUtils.hashCode(metadata);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(448);
    buf.append("CurveCtdVm{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("underlyingCurves").append('=').append(JodaBeanUtils.toString(underlyingCurves)).append(',').append(' ');
    buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
    buf.append("finalDate").append('=').append(JodaBeanUtils.toString(finalDate)).append(',').append(' ');
    buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
    buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
    buf.append("bounds").append('=').append(JodaBeanUtils.toString(bounds)).append(',').append(' ');
    buf.append("indexCurve").append('=').append(JodaBeanUtils.toString(indexCurve)).append(',').append(' ');
    buf.append("discountFactorsAtBounds").append('=').append(JodaBeanUtils.toString(discountFactorsAtBounds)).append(',').append(' ');
    buf.append("discountFactorsNextPeriodAtBounds").append('=').append(JodaBeanUtils.toString(discountFactorsNextPeriodAtBounds)).append(',').append(' ');
    buf.append("dfSensitivitiesAtBounds").append('=').append(JodaBeanUtils.toString(dfSensitivitiesAtBounds)).append(',').append(' ');
    buf.append("dfSensitivitiesNextPeriodAtBounds").append('=').append(JodaBeanUtils.toString(dfSensitivitiesNextPeriodAtBounds)).append(',').append(' ');
    buf.append("baseCalendar").append('=').append(JodaBeanUtils.toString(baseCalendar)).append(',').append(' ');
    buf.append("metadata").append('=').append(JodaBeanUtils.toString(metadata)).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code CurveCtdVm}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code underlyingCurves} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<Curve>> underlyingCurves = DirectMetaProperty.ofImmutable(
        this, "underlyingCurves", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code valuationDate} property.
     */
    private final MetaProperty<LocalDate> valuationDate = DirectMetaProperty.ofImmutable(
        this, "valuationDate", CurveCtdVm.class, LocalDate.class);
    /**
     * The meta-property for the {@code finalDate} property.
     */
    private final MetaProperty<LocalDate> finalDate = DirectMetaProperty.ofImmutable(
        this, "finalDate", CurveCtdVm.class, LocalDate.class);
    /**
     * The meta-property for the {@code currency} property.
     */
    private final MetaProperty<Currency> currency = DirectMetaProperty.ofImmutable(
        this, "currency", CurveCtdVm.class, Currency.class);
    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", CurveCtdVm.class, DayCount.class);
    /**
     * The meta-property for the {@code bounds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<LocalDate>> bounds = DirectMetaProperty.ofImmutable(
        this, "bounds", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code indexCurve} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<Integer>> indexCurve = DirectMetaProperty.ofImmutable(
        this, "indexCurve", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code discountFactorsAtBounds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<Double>> discountFactorsAtBounds = DirectMetaProperty.ofImmutable(
        this, "discountFactorsAtBounds", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code discountFactorsNextPeriodAtBounds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<Double>> discountFactorsNextPeriodAtBounds = DirectMetaProperty.ofImmutable(
        this, "discountFactorsNextPeriodAtBounds", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code dfSensitivitiesAtBounds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<UnitParameterSensitivity>> dfSensitivitiesAtBounds = DirectMetaProperty.ofImmutable(
        this, "dfSensitivitiesAtBounds", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code dfSensitivitiesNextPeriodAtBounds} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<UnitParameterSensitivity>> dfSensitivitiesNextPeriodAtBounds = DirectMetaProperty.ofImmutable(
        this, "dfSensitivitiesNextPeriodAtBounds", CurveCtdVm.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code baseCalendar} property.
     */
    private final MetaProperty<HolidayCalendar> baseCalendar = DirectMetaProperty.ofImmutable(
        this, "baseCalendar", CurveCtdVm.class, HolidayCalendar.class);
    /**
     * The meta-property for the {@code metadata} property.
     */
    private final MetaProperty<CurveMetadata> metadata = DirectMetaProperty.ofImmutable(
        this, "metadata", CurveCtdVm.class, CurveMetadata.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "underlyingCurves",
        "valuationDate",
        "finalDate",
        "currency",
        "dayCount",
        "bounds",
        "indexCurve",
        "discountFactorsAtBounds",
        "discountFactorsNextPeriodAtBounds",
        "dfSensitivitiesAtBounds",
        "dfSensitivitiesNextPeriodAtBounds",
        "baseCalendar",
        "metadata");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -251422943:  // underlyingCurves
          return underlyingCurves;
        case 113107279:  // valuationDate
          return valuationDate;
        case 354777668:  // finalDate
          return finalDate;
        case 575402001:  // currency
          return currency;
        case 1905311443:  // dayCount
          return dayCount;
        case -1383205195:  // bounds
          return bounds;
        case 721328957:  // indexCurve
          return indexCurve;
        case -378993045:  // discountFactorsAtBounds
          return discountFactorsAtBounds;
        case 1654051167:  // discountFactorsNextPeriodAtBounds
          return discountFactorsNextPeriodAtBounds;
        case 705273859:  // dfSensitivitiesAtBounds
          return dfSensitivitiesAtBounds;
        case 1909869815:  // dfSensitivitiesNextPeriodAtBounds
          return dfSensitivitiesNextPeriodAtBounds;
        case -1847589585:  // baseCalendar
          return baseCalendar;
        case -450004177:  // metadata
          return metadata;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public CurveCtdVm.Builder builder() {
      return new CurveCtdVm.Builder();
    }

    @Override
    public Class<? extends CurveCtdVm> beanType() {
      return CurveCtdVm.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code underlyingCurves} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<Curve>> underlyingCurves() {
      return underlyingCurves;
    }

    /**
     * The meta-property for the {@code valuationDate} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LocalDate> valuationDate() {
      return valuationDate;
    }

    /**
     * The meta-property for the {@code finalDate} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<LocalDate> finalDate() {
      return finalDate;
    }

    /**
     * The meta-property for the {@code currency} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Currency> currency() {
      return currency;
    }

    /**
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    /**
     * The meta-property for the {@code bounds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<LocalDate>> bounds() {
      return bounds;
    }

    /**
     * The meta-property for the {@code indexCurve} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<Integer>> indexCurve() {
      return indexCurve;
    }

    /**
     * The meta-property for the {@code discountFactorsAtBounds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<Double>> discountFactorsAtBounds() {
      return discountFactorsAtBounds;
    }

    /**
     * The meta-property for the {@code discountFactorsNextPeriodAtBounds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<Double>> discountFactorsNextPeriodAtBounds() {
      return discountFactorsNextPeriodAtBounds;
    }

    /**
     * The meta-property for the {@code dfSensitivitiesAtBounds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<UnitParameterSensitivity>> dfSensitivitiesAtBounds() {
      return dfSensitivitiesAtBounds;
    }

    /**
     * The meta-property for the {@code dfSensitivitiesNextPeriodAtBounds} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableList<UnitParameterSensitivity>> dfSensitivitiesNextPeriodAtBounds() {
      return dfSensitivitiesNextPeriodAtBounds;
    }

    /**
     * The meta-property for the {@code baseCalendar} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<HolidayCalendar> baseCalendar() {
      return baseCalendar;
    }

    /**
     * The meta-property for the {@code metadata} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<CurveMetadata> metadata() {
      return metadata;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -251422943:  // underlyingCurves
          return ((CurveCtdVm) bean).getUnderlyingCurves();
        case 113107279:  // valuationDate
          return ((CurveCtdVm) bean).getValuationDate();
        case 354777668:  // finalDate
          return ((CurveCtdVm) bean).getFinalDate();
        case 575402001:  // currency
          return ((CurveCtdVm) bean).getCurrency();
        case 1905311443:  // dayCount
          return ((CurveCtdVm) bean).getDayCount();
        case -1383205195:  // bounds
          return ((CurveCtdVm) bean).getBounds();
        case 721328957:  // indexCurve
          return ((CurveCtdVm) bean).getIndexCurve();
        case -378993045:  // discountFactorsAtBounds
          return ((CurveCtdVm) bean).getDiscountFactorsAtBounds();
        case 1654051167:  // discountFactorsNextPeriodAtBounds
          return ((CurveCtdVm) bean).getDiscountFactorsNextPeriodAtBounds();
        case 705273859:  // dfSensitivitiesAtBounds
          return ((CurveCtdVm) bean).getDfSensitivitiesAtBounds();
        case 1909869815:  // dfSensitivitiesNextPeriodAtBounds
          return ((CurveCtdVm) bean).getDfSensitivitiesNextPeriodAtBounds();
        case -1847589585:  // baseCalendar
          return ((CurveCtdVm) bean).getBaseCalendar();
        case -450004177:  // metadata
          return ((CurveCtdVm) bean).getMetadata();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code CurveCtdVm}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<CurveCtdVm> {

    private List<Curve> underlyingCurves = ImmutableList.of();
    private LocalDate valuationDate;
    private LocalDate finalDate;
    private Currency currency;
    private DayCount dayCount;
    private List<LocalDate> bounds = ImmutableList.of();
    private List<Integer> indexCurve = ImmutableList.of();
    private List<Double> discountFactorsAtBounds = ImmutableList.of();
    private List<Double> discountFactorsNextPeriodAtBounds = ImmutableList.of();
    private List<UnitParameterSensitivity> dfSensitivitiesAtBounds = ImmutableList.of();
    private List<UnitParameterSensitivity> dfSensitivitiesNextPeriodAtBounds = ImmutableList.of();
    private HolidayCalendar baseCalendar;
    private CurveMetadata metadata;

    /**
     * Restricted constructor.
     */
    protected Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(CurveCtdVm beanToCopy) {
      this.underlyingCurves = beanToCopy.getUnderlyingCurves();
      this.valuationDate = beanToCopy.getValuationDate();
      this.finalDate = beanToCopy.getFinalDate();
      this.currency = beanToCopy.getCurrency();
      this.dayCount = beanToCopy.getDayCount();
      this.bounds = beanToCopy.getBounds();
      this.indexCurve = beanToCopy.getIndexCurve();
      this.discountFactorsAtBounds = beanToCopy.getDiscountFactorsAtBounds();
      this.discountFactorsNextPeriodAtBounds = beanToCopy.getDiscountFactorsNextPeriodAtBounds();
      this.dfSensitivitiesAtBounds = beanToCopy.getDfSensitivitiesAtBounds();
      this.dfSensitivitiesNextPeriodAtBounds = beanToCopy.getDfSensitivitiesNextPeriodAtBounds();
      this.baseCalendar = beanToCopy.getBaseCalendar();
      this.metadata = beanToCopy.getMetadata();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -251422943:  // underlyingCurves
          return underlyingCurves;
        case 113107279:  // valuationDate
          return valuationDate;
        case 354777668:  // finalDate
          return finalDate;
        case 575402001:  // currency
          return currency;
        case 1905311443:  // dayCount
          return dayCount;
        case -1383205195:  // bounds
          return bounds;
        case 721328957:  // indexCurve
          return indexCurve;
        case -378993045:  // discountFactorsAtBounds
          return discountFactorsAtBounds;
        case 1654051167:  // discountFactorsNextPeriodAtBounds
          return discountFactorsNextPeriodAtBounds;
        case 705273859:  // dfSensitivitiesAtBounds
          return dfSensitivitiesAtBounds;
        case 1909869815:  // dfSensitivitiesNextPeriodAtBounds
          return dfSensitivitiesNextPeriodAtBounds;
        case -1847589585:  // baseCalendar
          return baseCalendar;
        case -450004177:  // metadata
          return metadata;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -251422943:  // underlyingCurves
          this.underlyingCurves = (List<Curve>) newValue;
          break;
        case 113107279:  // valuationDate
          this.valuationDate = (LocalDate) newValue;
          break;
        case 354777668:  // finalDate
          this.finalDate = (LocalDate) newValue;
          break;
        case 575402001:  // currency
          this.currency = (Currency) newValue;
          break;
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        case -1383205195:  // bounds
          this.bounds = (List<LocalDate>) newValue;
          break;
        case 721328957:  // indexCurve
          this.indexCurve = (List<Integer>) newValue;
          break;
        case -378993045:  // discountFactorsAtBounds
          this.discountFactorsAtBounds = (List<Double>) newValue;
          break;
        case 1654051167:  // discountFactorsNextPeriodAtBounds
          this.discountFactorsNextPeriodAtBounds = (List<Double>) newValue;
          break;
        case 705273859:  // dfSensitivitiesAtBounds
          this.dfSensitivitiesAtBounds = (List<UnitParameterSensitivity>) newValue;
          break;
        case 1909869815:  // dfSensitivitiesNextPeriodAtBounds
          this.dfSensitivitiesNextPeriodAtBounds = (List<UnitParameterSensitivity>) newValue;
          break;
        case -1847589585:  // baseCalendar
          this.baseCalendar = (HolidayCalendar) newValue;
          break;
        case -450004177:  // metadata
          this.metadata = (CurveMetadata) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public CurveCtdVm build() {
      return new CurveCtdVm(
          underlyingCurves,
          valuationDate,
          finalDate,
          currency,
          dayCount,
          bounds,
          indexCurve,
          discountFactorsAtBounds,
          discountFactorsNextPeriodAtBounds,
          dfSensitivitiesAtBounds,
          dfSensitivitiesNextPeriodAtBounds,
          baseCalendar,
          metadata);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the underlying curves.
     * @param underlyingCurves  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder underlyingCurves(List<Curve> underlyingCurves) {
      JodaBeanUtils.notNull(underlyingCurves, "underlyingCurves");
      this.underlyingCurves = underlyingCurves;
      return this;
    }

    /**
     * Sets the {@code underlyingCurves} property in the builder
     * from an array of objects.
     * @param underlyingCurves  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder underlyingCurves(Curve... underlyingCurves) {
      return underlyingCurves(ImmutableList.copyOf(underlyingCurves));
    }

    /**
     * Sets the common valuation date for all discounting factors.
     * @param valuationDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder valuationDate(LocalDate valuationDate) {
      JodaBeanUtils.notNull(valuationDate, "valuationDate");
      this.valuationDate = valuationDate;
      return this;
    }

    /**
     * Sets the final date.
     * @param finalDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder finalDate(LocalDate finalDate) {
      JodaBeanUtils.notNull(finalDate, "finalDate");
      this.finalDate = finalDate;
      return this;
    }

    /**
     * Sets the common currency of all the discount factors.
     * @param currency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder currency(Currency currency) {
      JodaBeanUtils.notNull(currency, "currency");
      this.currency = currency;
      return this;
    }

    /**
     * Sets the day count
     * @param dayCount  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCount(DayCount dayCount) {
      JodaBeanUtils.notNull(dayCount, "dayCount");
      this.dayCount = dayCount;
      return this;
    }

    /**
     * Sets the dates immediately after a CTD curve change, i.e. index i is valid for fixing in [t_i, t_{i+1})
     * @param bounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder bounds(List<LocalDate> bounds) {
      JodaBeanUtils.notNull(bounds, "bounds");
      this.bounds = bounds;
      return this;
    }

    /**
     * Sets the {@code bounds} property in the builder
     * from an array of objects.
     * @param bounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder bounds(LocalDate... bounds) {
      return bounds(ImmutableList.copyOf(bounds));
    }

    /**
     * Sets the indexCurve.
     * @param indexCurve  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder indexCurve(List<Integer> indexCurve) {
      JodaBeanUtils.notNull(indexCurve, "indexCurve");
      this.indexCurve = indexCurve;
      return this;
    }

    /**
     * Sets the {@code indexCurve} property in the builder
     * from an array of objects.
     * @param indexCurve  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder indexCurve(Integer... indexCurve) {
      return indexCurve(ImmutableList.copyOf(indexCurve));
    }

    /**
     * Sets the discount factor for the bound dates.
     * @param discountFactorsAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactorsAtBounds(List<Double> discountFactorsAtBounds) {
      JodaBeanUtils.notNull(discountFactorsAtBounds, "discountFactorsAtBounds");
      this.discountFactorsAtBounds = discountFactorsAtBounds;
      return this;
    }

    /**
     * Sets the {@code discountFactorsAtBounds} property in the builder
     * from an array of objects.
     * @param discountFactorsAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactorsAtBounds(Double... discountFactorsAtBounds) {
      return discountFactorsAtBounds(ImmutableList.copyOf(discountFactorsAtBounds));
    }

    /**
     * Sets the discount factor for the curve in the next period at the bound dates.
     * @param discountFactorsNextPeriodAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactorsNextPeriodAtBounds(List<Double> discountFactorsNextPeriodAtBounds) {
      JodaBeanUtils.notNull(discountFactorsNextPeriodAtBounds, "discountFactorsNextPeriodAtBounds");
      this.discountFactorsNextPeriodAtBounds = discountFactorsNextPeriodAtBounds;
      return this;
    }

    /**
     * Sets the {@code discountFactorsNextPeriodAtBounds} property in the builder
     * from an array of objects.
     * @param discountFactorsNextPeriodAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder discountFactorsNextPeriodAtBounds(Double... discountFactorsNextPeriodAtBounds) {
      return discountFactorsNextPeriodAtBounds(ImmutableList.copyOf(discountFactorsNextPeriodAtBounds));
    }

    /**
     * Sets the dfSensitivitiesAtBounds.
     * @param dfSensitivitiesAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dfSensitivitiesAtBounds(List<UnitParameterSensitivity> dfSensitivitiesAtBounds) {
      JodaBeanUtils.notNull(dfSensitivitiesAtBounds, "dfSensitivitiesAtBounds");
      this.dfSensitivitiesAtBounds = dfSensitivitiesAtBounds;
      return this;
    }

    /**
     * Sets the {@code dfSensitivitiesAtBounds} property in the builder
     * from an array of objects.
     * @param dfSensitivitiesAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dfSensitivitiesAtBounds(UnitParameterSensitivity... dfSensitivitiesAtBounds) {
      return dfSensitivitiesAtBounds(ImmutableList.copyOf(dfSensitivitiesAtBounds));
    }

    /**
     * Sets the sensitivity of the discount factors at the bound dates.
     * @param dfSensitivitiesNextPeriodAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dfSensitivitiesNextPeriodAtBounds(List<UnitParameterSensitivity> dfSensitivitiesNextPeriodAtBounds) {
      JodaBeanUtils.notNull(dfSensitivitiesNextPeriodAtBounds, "dfSensitivitiesNextPeriodAtBounds");
      this.dfSensitivitiesNextPeriodAtBounds = dfSensitivitiesNextPeriodAtBounds;
      return this;
    }

    /**
     * Sets the {@code dfSensitivitiesNextPeriodAtBounds} property in the builder
     * from an array of objects.
     * @param dfSensitivitiesNextPeriodAtBounds  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dfSensitivitiesNextPeriodAtBounds(UnitParameterSensitivity... dfSensitivitiesNextPeriodAtBounds) {
      return dfSensitivitiesNextPeriodAtBounds(ImmutableList.copyOf(dfSensitivitiesNextPeriodAtBounds));
    }

    /**
     * Sets the calendar used to select the forward periods.
     * @param baseCalendar  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder baseCalendar(HolidayCalendar baseCalendar) {
      JodaBeanUtils.notNull(baseCalendar, "baseCalendar");
      this.baseCalendar = baseCalendar;
      return this;
    }

    /**
     * Sets the cheapest-to-delivery curve metadata.
     * @param metadata  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder metadata(CurveMetadata metadata) {
      JodaBeanUtils.notNull(metadata, "metadata");
      this.metadata = metadata;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(448);
      buf.append("CurveCtdVm.Builder{");
      int len = buf.length();
      toString(buf);
      if (buf.length() > len) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }

    protected void toString(StringBuilder buf) {
      buf.append("underlyingCurves").append('=').append(JodaBeanUtils.toString(underlyingCurves)).append(',').append(' ');
      buf.append("valuationDate").append('=').append(JodaBeanUtils.toString(valuationDate)).append(',').append(' ');
      buf.append("finalDate").append('=').append(JodaBeanUtils.toString(finalDate)).append(',').append(' ');
      buf.append("currency").append('=').append(JodaBeanUtils.toString(currency)).append(',').append(' ');
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("bounds").append('=').append(JodaBeanUtils.toString(bounds)).append(',').append(' ');
      buf.append("indexCurve").append('=').append(JodaBeanUtils.toString(indexCurve)).append(',').append(' ');
      buf.append("discountFactorsAtBounds").append('=').append(JodaBeanUtils.toString(discountFactorsAtBounds)).append(',').append(' ');
      buf.append("discountFactorsNextPeriodAtBounds").append('=').append(JodaBeanUtils.toString(discountFactorsNextPeriodAtBounds)).append(',').append(' ');
      buf.append("dfSensitivitiesAtBounds").append('=').append(JodaBeanUtils.toString(dfSensitivitiesAtBounds)).append(',').append(' ');
      buf.append("dfSensitivitiesNextPeriodAtBounds").append('=').append(JodaBeanUtils.toString(dfSensitivitiesNextPeriodAtBounds)).append(',').append(' ');
      buf.append("baseCalendar").append('=').append(JodaBeanUtils.toString(baseCalendar)).append(',').append(' ');
      buf.append("metadata").append('=').append(JodaBeanUtils.toString(metadata)).append(',').append(' ');
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
