/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.rate;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.array.DoubleArray;

/**
 * Tests {@link IborRatchetRateComputation}.
 * 
 * @author Marc Henrard
 */
public class IborRatchetRateComputationTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate FIXING_DATE = LocalDate.of(2015, 6, 15);

  private static final IborIndexObservation OBS = IborIndexObservation.of(EUR_EURIBOR_3M, FIXING_DATE, REF_DATA);
  private static final DoubleArray MAIN_COEFF = DoubleArray.of(0.5, 0.5, 0.01);
  private static final DoubleArray FLOOR_COEFF = DoubleArray.of(0.75, 0.0, 0.0);
  private static final DoubleArray CAP_COEFF = DoubleArray.of(1.0, 0.0, 0.02);
  
  @Test
  public void builder() {
    IborRatchetRateComputation test = IborRatchetRateComputation.builder()
        .observation(OBS)
        .mainCoefficients(MAIN_COEFF)
        .floorCoefficients(FLOOR_COEFF)
        .capCoefficients(CAP_COEFF).build();
    assertThat(test.getObservation()).isEqualTo(OBS);
    assertThat(test.getMainCoefficients()).isEqualTo(MAIN_COEFF);
    assertThat(test.getFloorCoefficients()).isEqualTo(FLOOR_COEFF);
    assertThat(test.getCapCoefficients()).isEqualTo(CAP_COEFF);
  }

  @Test
  public void of() {
    IborRatchetRateComputation test = IborRatchetRateComputation
        .of(OBS, MAIN_COEFF, FLOOR_COEFF, CAP_COEFF);
    assertThat(test.getObservation()).isEqualTo(OBS);
    assertThat(test.getMainCoefficients()).isEqualTo(MAIN_COEFF);
    assertThat(test.getFloorCoefficients()).isEqualTo(FLOOR_COEFF);
    assertThat(test.getCapCoefficients()).isEqualTo(CAP_COEFF);
  }

  @Test
  public void rate() {
    IborRatchetRateComputation ratchet = IborRatchetRateComputation
        .of(OBS, MAIN_COEFF, FLOOR_COEFF, CAP_COEFF);
    // 1. Main
    double previous1 = 0.01;
    double ibor1 = 0.02;
    double ratchetRate1Computed = ratchet.rate(previous1, ibor1);
    double main1 = MAIN_COEFF.get(0) * previous1 + MAIN_COEFF.get(1) * ibor1 + MAIN_COEFF.get(2);
    assertThat(ratchetRate1Computed).isEqualTo(main1);
    // 2. Floor
    double previous2 = 0.01;
    double ibor2 = -0.02;
    double ratchetRate2Computed = ratchet.rate(previous2, ibor2);
    double floor2 = FLOOR_COEFF.get(0) * previous2;
    assertThat(ratchetRate2Computed).isEqualTo(floor2);
    // 3. Cap
    double previous3 = 0.01;
    double ibor3 = 0.08;
    double ratchetRate3Computed = ratchet.rate(previous3, ibor3);
    double cap3 = CAP_COEFF.get(0) * previous3 + CAP_COEFF.get(1) * ibor3 + CAP_COEFF.get(2);
    assertThat(ratchetRate3Computed).isEqualTo(cap3);
  }

  @Test
  public void builder_null_observation() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateComputation.builder()
            .observation(null)
            .mainCoefficients(MAIN_COEFF)
            .floorCoefficients(FLOOR_COEFF)
            .capCoefficients(CAP_COEFF).build());
  }

  @Test
  public void builder_null_maincoeff() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateComputation.builder()
            .observation(OBS)
            .mainCoefficients(null)
            .floorCoefficients(FLOOR_COEFF)
            .capCoefficients(CAP_COEFF).build());
  }

  @Test
  public void builder_null_floorcoeff() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateComputation.builder()
            .observation(OBS)
            .mainCoefficients(MAIN_COEFF)
            .floorCoefficients(null)
            .capCoefficients(CAP_COEFF).build());
  }

  @Test
  public void builder_null_capcoeff() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IborRatchetRateComputation.builder()
            .observation(OBS)
            .mainCoefficients(MAIN_COEFF)
            .floorCoefficients(FLOOR_COEFF)
            .capCoefficients(null).build());
  }

}
