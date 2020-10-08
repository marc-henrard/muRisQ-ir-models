/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.index.IborIndices;
import com.opengamma.strata.basics.index.OvernightIndices;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;

import marc.henrard.murisq.basics.time.ScaledSecondTime;

/**
 * Test {@link LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters}.
 * 
 * @author Marc Henrard
 */
public class LiborMarketModelDisplacedDiffusionDeterministicSpreadParametersTest {

  private static final double MEAN_REVERTION = 0.02;
  private static final double SIGMA = 0.01;
  private static final double DISPLACEMENT = 0.10; // 10% rate displacement
  private static final int NB_PERIODS = 12;
  private static final DoubleArray ACCRUAL_FACTORS = DoubleArray.of(NB_PERIODS, (i) -> 0.25);
  private static final DoubleArray DISPLACEMENTS = DoubleArray.of(NB_PERIODS, (i) -> DISPLACEMENT);
  private static final DoubleArray IBOR_TIMES = DoubleArray.of(NB_PERIODS + 1, (i) -> 0.25 + i * 0.25);
  private static final DoubleArray MULTIPLICATIVE_SPREADS = DoubleArray.of(NB_PERIODS, (i) -> (1.01 + i * 0.0001));
  private static final DoubleMatrix VOLATILITIES =
      DoubleMatrix.of(NB_PERIODS, 2, (j, i) -> SIGMA * (j + 10.0d) / 100.0d * (i + 11.0d) / 125.0d); 
  private static final LocalDate VALUATION_DATE = LocalDate.of(2020, 8, 18);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 12);
  private static final ZoneId ZONE = ZoneId.of("Europe/Brussels");
  
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters PARAMETERS =
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters.builder()
          .accrualFactors(ACCRUAL_FACTORS)
          .displacements(DISPLACEMENTS)
          .iborIndex(IborIndices.EUR_EURIBOR_3M)
          .overnightIndex(OvernightIndices.EUR_ESTR)
          .iborTimes(IBOR_TIMES)
          .meanReversion(MEAN_REVERTION)
          .multiplicativeSpreads(MULTIPLICATIVE_SPREADS)
          .timeMeasure(ScaledSecondTime.DEFAULT)
          .timeTolerance(0.001)
          .volatilities(VOLATILITIES)
          .valuationDate(VALUATION_DATE)
          .valuationTime(VALUATION_TIME)
          .valuationZone(ZONE)
          .build();
  
  /* Tests */
  private static final Offset<Double> TOLERANCE_DOUBLE = within(1.0E-12);

  /* Test the ibor time index. */
  @Test
  public void getIborTimeIndex() {
    double[] testTimes = {0.25d, 0.50d, 0.49999, 0.500001, 0.501, 0.70, 0.75, 1.25, 1.26};
    int[] indicesExpected = {0, 1, 1, 1, 1, 2, 2, 4, 5};
    int[] indicesComputed = PARAMETERS.getIborTimeIndex(testTimes);
    assertThat(indicesComputed).isEqualTo(indicesExpected);
  }

  /* Test parameters getter. */
  @Test
  public void getParameter() {
    for (int j = 0; j < NB_PERIODS; j++) {
      for (int i = 0; i < 2; i++) {
        double volExpected = SIGMA * (j + 10.0d) / 100.0d * (i + 11.0d) / 125.0d; 
        int index = 2 * j + i;
        double volComputed = PARAMETERS.getParameter(index);
        assertThat(volComputed).isEqualTo(volExpected, TOLERANCE_DOUBLE);
      }
    }
  }

  /* Test parameters getter. */
  @Test
  public void iborRateFromDscForwards() {
    double fwd = 0.0123;
    for(int i=0; i<NB_PERIODS; i++) {
      double iborRateComputed = PARAMETERS.iborRateFromDscForwards(fwd, i);
      double iborRateExpected = (MULTIPLICATIVE_SPREADS.get(i) * (1 + ACCRUAL_FACTORS.get(i) * fwd) - 1.0d)
          / ACCRUAL_FACTORS.get(i);
      assertThat(iborRateComputed).isEqualTo(iborRateExpected, TOLERANCE_DOUBLE);
    }
  }

}
