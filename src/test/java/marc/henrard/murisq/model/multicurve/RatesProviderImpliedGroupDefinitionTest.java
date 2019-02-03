/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.multicurve;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Optional;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.collect.io.ResourceLocator;
import com.opengamma.strata.loader.csv.RatesCalibrationCsvLoader;
import com.opengamma.strata.market.curve.CurveGroupName;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.JacobianCalibrationMatrix;
import com.opengamma.strata.market.curve.RatesCurveGroupDefinition;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.curve.SyntheticRatesCurveCalibrator;
import com.opengamma.strata.pricer.rate.DiscountIborIndexRates;
import com.opengamma.strata.pricer.rate.IborIndexRates;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

import marc.henrard.murisq.dataset.MulticurveStandardDataSet;

@Test
public class RatesProviderImpliedGroupDefinitionTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2018, 8, 30);
  
  /* Curves description */
  private static final String INTERPOLATION = "zrlinear";
  private static final String SUFFIX_CSV = ".csv";
  private static final String CURVE_GROUP_NAME_STR = "USD-DSCONOIS-L3MIRS";
  private static final CurveGroupName CURVE_GROUP_NAME = CurveGroupName.of(CURVE_GROUP_NAME_STR);
  private static final String PATH_CONFIG = "src/test/resources/curve-config/USD-DSCONOIS-L3MIRS/";
  private static final ResourceLocator GROUP_FILE = 
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-group.csv");
  private static final ResourceLocator SETTINGS_FILE =
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-settings-" + INTERPOLATION + SUFFIX_CSV);
  private static final ResourceLocator NODES_FILE = 
      ResourceLocator.of(PATH_CONFIG + "USD-DSCONOIS-L3MIRS-nodes.csv");
  
  private static final String CURVE_GROUP_NAME_RWM_STR = "USD-DSCONOIS-IBOR3MIRS-RWM";
  private static final CurveGroupName CURVE_GROUP_NAME_RWM = CurveGroupName.of(CURVE_GROUP_NAME_RWM_STR);
  private static final String PATH_CONFIG_RWM = "src/test/resources/curve-config/USD-DSCONOIS-IBOR3MIRS-RWM/";
  private static final ResourceLocator GROUP_FILE_RWM = 
      ResourceLocator.of(PATH_CONFIG_RWM + "USD-DSCONOIS-IBOR3MIRS-RWM-group.csv");
  private static final ResourceLocator SETTINGS_FILE_RWM =
      ResourceLocator.of(PATH_CONFIG_RWM + "USD-DSCONOIS-IBOR3MIRS-RWM-settings-" + INTERPOLATION + SUFFIX_CSV);
  private static final ResourceLocator NODES_FILE_RWM = 
      ResourceLocator.of(PATH_CONFIG_RWM + "USD-DSCONOIS-IBOR3MIRS-RWM-nodes.csv");
  private static final RatesCurveGroupDefinition GROUP_DEFINITION_RWM = RatesCalibrationCsvLoader
      .load(GROUP_FILE_RWM, SETTINGS_FILE_RWM, NODES_FILE_RWM).get(CURVE_GROUP_NAME_RWM);
  
  private static final String PATH_QUOTES = "src/test/resources/quotes/";
  private static final String QUOTES_FILE = PATH_QUOTES + "MARKET-DATA-2018-08-30.csv";
  // TODO: add fixings
  
  private static final double TOLERANCE_DF = 7.0E-4;
  private static final double TOLERANCE_RATE = 6.0E-4;
  private static final double TOLERANCE_RATE_GENERIC = 6.0E-4;
  private static final double TOLERANCE_JAC = 2.0E-5;

  public void implied_noJacobian() {
    ImmutableRatesProvider multicurve = MulticurveStandardDataSet
        .multicurve(VALUATION_DATE, CURVE_GROUP_NAME,
            GROUP_FILE, SETTINGS_FILE, NODES_FILE, QUOTES_FILE, REF_DATA);
    ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
        .generateNoJacobian(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
    // Check discount factors 
    int nbDate = 60;
    for (int i = 0; i < nbDate; i++) {
      LocalDate date = VALUATION_DATE.plusMonths(6 * i);
      double dfExpected = multicurve.discountFactor(Currency.USD, date);
      double dfImplied = multicurveImplied.discountFactor(Currency.USD, date);
      assertEquals(dfImplied, dfExpected, TOLERANCE_DF, "Date: " + date);
//      System.out.println("Date: " + date 
//          + "," + dfExpected
//          + "," + dfImplied);
    }
    // Check IBOR rates
    IborIndex index = IborIndices.USD_LIBOR_3M;
    for (int i = 0; i < nbDate; i++) {
      LocalDate date = VALUATION_DATE.plusMonths(6 * i);
      IborIndexObservation obs = IborIndexObservation.of(index, date, REF_DATA);
      double rateExpected = multicurve.iborIndexRates(index).rate(obs);
      double rateImplied = multicurveImplied.iborIndexRates(index).rate(obs);
      assertEquals(rateImplied, rateExpected, TOLERANCE_RATE, "Date: " + date);
//      System.out.println("Date: " + date 
//          + "," + rateExpected
//          + "," + rateImplied);
    }
  }

  public void implied_values() {
    ImmutableRatesProvider multicurve = MulticurveStandardDataSet
        .multicurve(VALUATION_DATE, CURVE_GROUP_NAME,
            GROUP_FILE, SETTINGS_FILE, NODES_FILE, QUOTES_FILE, REF_DATA);
    ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
        .generate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
    // Check discount factors 
    int nbDate = 60;
    for (int i = 0; i < nbDate; i++) {
      LocalDate date = VALUATION_DATE.plusMonths(6 * i);
      double dfExpected = multicurve.discountFactor(Currency.USD, date);
      double dfImplied = multicurveImplied.discountFactor(Currency.USD, date);
      assertEquals(dfImplied, dfExpected, TOLERANCE_DF, "Date: " + date);
    }
    // Check IBOR rates
    IborIndex index = IborIndices.USD_LIBOR_3M;
    for (int i = 0; i < nbDate; i++) {
      LocalDate date = VALUATION_DATE.plusMonths(6 * i);
      IborIndexObservation obs = IborIndexObservation.of(index, date, REF_DATA);
      double rateExpected = multicurve.iborIndexRates(index).rate(obs);
      double rateImplied = multicurveImplied.iborIndexRates(index).rate(obs);
      assertEquals(rateImplied, rateExpected, TOLERANCE_RATE, "Date: " + date);
//    System.out.println("Date: " + date 
//    + "," + rateExpected
//    + "," + rateImplied);
    }
  }


  public void implied_values_generic() {
    ImmutableRatesProvider multicurve = MulticurveStandardDataSet
        .multicurve(VALUATION_DATE, CURVE_GROUP_NAME,
            GROUP_FILE, SETTINGS_FILE, NODES_FILE, QUOTES_FILE, REF_DATA);
    ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
        .generateGeneric(GROUP_DEFINITION_RWM, multicurve, ImmutableMap.of(), REF_DATA);
    // Check discount factors 
    int nbDate = 60;
    for (int i = 0; i < nbDate; i++) {
      LocalDate date = VALUATION_DATE.plusMonths(6 * i);
      double dfExpected = multicurve.discountFactor(Currency.USD, date);
      double dfImplied = multicurveImplied.discountFactor(Currency.USD, date);
      assertEquals(dfImplied, dfExpected, TOLERANCE_DF, "Date: " + date);
    }
    // Check IBOR rates
    IborIndex index = IborIndices.USD_LIBOR_3M;
    for (int i = 0; i < nbDate; i++) {
      LocalDate date = VALUATION_DATE.plusMonths(6 * i);
      IborIndexObservation obs = IborIndexObservation.of(index, date, REF_DATA);
      double rateExpected = multicurve.iborIndexRates(index).rate(obs);
      double rateImplied = multicurveImplied.iborIndexRates(index).rate(obs);
      assertEquals(rateImplied, rateExpected, TOLERANCE_RATE_GENERIC, "Date: " + date);
//    System.out.println("Date: " + date 
//    + "," + rateExpected
//    + "," + rateImplied);
    }
  }

  // Compare with synthetic
  public void implied_jacobian() {
    ImmutableRatesProvider multicurve = MulticurveStandardDataSet
        .multicurve(VALUATION_DATE, CURVE_GROUP_NAME,
            GROUP_FILE, SETTINGS_FILE, NODES_FILE, QUOTES_FILE, REF_DATA);
    ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
        .generate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
    ImmutableRatesProvider multicurveSynthetic = SyntheticRatesCurveCalibrator.standard()
        .calibrate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
    // Compare jacobians - Discount factors
    DiscountFactors dfImplied = multicurveImplied.discountFactors(Currency.USD);
    ZeroRateDiscountFactors dfZeroImplied = (ZeroRateDiscountFactors) dfImplied;
    Optional<JacobianCalibrationMatrix> dfJacImplied = 
        dfZeroImplied.getCurve().getMetadata().findInfo(CurveInfoType.JACOBIAN);
    assertTrue(dfJacImplied.isPresent());
    DiscountFactors dfSynthetic = multicurveSynthetic.discountFactors(Currency.USD);
    ZeroRateDiscountFactors dfZeroSynthetic = (ZeroRateDiscountFactors) dfSynthetic;
    Optional<JacobianCalibrationMatrix> dfJacSynthetic = 
        dfZeroSynthetic.getCurve().getMetadata().findInfo(CurveInfoType.JACOBIAN);
    assertTrue(dfJacImplied.get().getOrder().equals(dfJacSynthetic.get().getOrder()));
    DoubleMatrix matrixImplied = dfJacImplied.get().getJacobianMatrix();
    DoubleMatrix matrixSynthetic = dfJacSynthetic.get().getJacobianMatrix();
    assertEquals(matrixImplied.rowCount(), matrixSynthetic.rowCount());
    assertEquals(matrixImplied.columnCount(), matrixSynthetic.columnCount());
    for(int i=0; i<matrixImplied.rowCount(); i++) {
      for(int j=0; j<matrixImplied.columnCount(); j++) {
        assertEquals(matrixImplied.get(i, j), matrixSynthetic.get(i, j), TOLERANCE_JAC, 
            "Element:" + i + "," + j);
//        System.out.println("Element:" + i + "," + j + "," + matrixImplied.get(i, j)
//            + "," + matrixSynthetic.get(i, j)
//            + "," + (matrixImplied.get(i, j) - matrixSynthetic.get(i, j)));
      }
    }
    // Compare jacobians - Forward
    IborIndex index = IborIndices.USD_LIBOR_3M;
    IborIndexRates ratesImplied = multicurveImplied.iborIndexRates(index);
    DiscountFactors dfIborImplied = ((DiscountIborIndexRates) ratesImplied).getDiscountFactors();
    ZeroRateDiscountFactors dfIborZeroImplied = (ZeroRateDiscountFactors) dfIborImplied;
    Optional<JacobianCalibrationMatrix> iborJacImplied = 
        dfIborZeroImplied.getCurve().getMetadata().findInfo(CurveInfoType.JACOBIAN);
    assertTrue(iborJacImplied.isPresent());
    IborIndexRates ratesSynthetic = multicurveSynthetic.iborIndexRates(index);
    DiscountFactors dfIborSynthetic = ((DiscountIborIndexRates) ratesSynthetic).getDiscountFactors();
    ZeroRateDiscountFactors dfIborZeroSynthetic = (ZeroRateDiscountFactors) dfIborSynthetic;
    Optional<JacobianCalibrationMatrix> iborJacSynthetic = 
        dfIborZeroSynthetic.getCurve().getMetadata().findInfo(CurveInfoType.JACOBIAN);
    assertTrue(iborJacImplied.get().getOrder().equals(iborJacSynthetic.get().getOrder()));
    DoubleMatrix matrixIborImplied = iborJacImplied.get().getJacobianMatrix();
    DoubleMatrix matrixIborSynthetic = iborJacSynthetic.get().getJacobianMatrix();
    assertEquals(matrixIborImplied.rowCount(), matrixIborSynthetic.rowCount());
    assertEquals(matrixIborImplied.columnCount(), matrixIborSynthetic.columnCount());
    for(int i=0; i<matrixIborImplied.rowCount(); i++) {
      for(int j=0; j<matrixIborImplied.columnCount(); j++) {
        assertEquals(matrixIborImplied.get(i, j), matrixIborSynthetic.get(i, j), TOLERANCE_JAC, 
            "Element:" + i + "," + j);
//        System.out.println("Element:" + i + "," + j + "," + matrixImplied.get(i, j)
//            + "," + matrixSynthetic.get(i, j)
//            + "," + (matrixImplied.get(i, j) - matrixSynthetic.get(i, j)));
      }
    }
  }

  /* Compare performance with synthetic curve calibration. */
  @Test(enabled = false)
  public void performance() {
    long start, end;
    int nbWarmup = 100;
    int nbRep = 100;
    ImmutableRatesProvider multicurve = MulticurveStandardDataSet
        .multicurve(VALUATION_DATE, CURVE_GROUP_NAME,
            GROUP_FILE, SETTINGS_FILE, NODES_FILE, QUOTES_FILE, REF_DATA);
    double check = 0.0d;
    for (int i = 0; i < nbWarmup; i++) {
      ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
          .generate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
      ImmutableRatesProvider multicurveSynthetic = SyntheticRatesCurveCalibrator.standard()
          .calibrate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
      ImmutableRatesProvider multicurveImpliedG = RatesProviderImpliedGroupDefinition
          .generateGeneric(GROUP_DEFINITION_RWM, multicurve, ImmutableMap.of(), REF_DATA);
      check += multicurveImplied.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
      check += multicurveImpliedG.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
      check += multicurveSynthetic.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
    }
    check = 0.0d;
    start = System.currentTimeMillis();
    for (int i = 0; i < nbRep; i++) {
      ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
          .generateNoJacobian(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
      check += multicurveImplied.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
    }
    end = System.currentTimeMillis();
    System.out.println("Computation time implied (No Jacobian): " + (end-start) + " ms for " + nbRep + " repetitions. " + check);
    check = 0.0d;
    start = System.currentTimeMillis();
    for (int i = 0; i < nbRep; i++) {
      ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
          .generate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
      check += multicurveImplied.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
    }
    end = System.currentTimeMillis();
    System.out.println("Computation time implied: " + (end-start) + " ms for " + nbRep + " repetitions. " + check);
    check = 0.0d;
    start = System.currentTimeMillis();
    for (int i = 0; i < nbRep; i++) {
      ImmutableRatesProvider multicurveImplied = RatesProviderImpliedGroupDefinition
          .generateGeneric(GROUP_DEFINITION_RWM, multicurve, ImmutableMap.of(), REF_DATA);
      check += multicurveImplied.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
    }
    end = System.currentTimeMillis();
    System.out.println("Computation time implied (Generic): " + (end-start) + " ms for " + nbRep + " repetitions. " + check);
    check = 0.0d;
    start = System.currentTimeMillis();
    for (int i = 0; i < nbRep; i++) {
      ImmutableRatesProvider multicurveSynthetic = SyntheticRatesCurveCalibrator.standard()
        .calibrate(GROUP_DEFINITION_RWM, multicurve, REF_DATA);
      check += multicurveSynthetic.discountFactor(Currency.USD, VALUATION_DATE.plusYears(1));
    }
    end = System.currentTimeMillis();
    System.out.println("Computation time synthetic: " + (end-start) + " ms for " + nbRep + " repetitions. " + check);
  }
  //  Previous run on author laptop:
  //  Computation time implied (No Jacobian): 54 ms for 100 repetitions. 
  //  Computation time implied: 630 ms for 100 repetitions.
  //  Computation time synthetic: 1034 ms for 100 repetitions.
  
}
