/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.generic;

import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.coverEnum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link FallbackType}.
 * 
 * @author Marc Henrard
 */
public class FallbackTypeTest {

  //-------------------------------------------------------------------------
  public static Object[][] data_name() {
    return new Object[][] {
        {FallbackType.SPOT_OVERNIGHT, "SpotOvernight"},
        {FallbackType.COMPOUNDED_IN_ARREARS_CALCPERIOD, "CompoundedInArrearsCalcperiod"},
        {FallbackType.COMPOUNDED_IN_ADVANCE, "CompoundedInAdvance"},
        {FallbackType.OIS_BENCHMARK, "OisBenchmark"},
        {FallbackType.COMPOUNDED_IN_ARREARS_2DAYS_CALCPERIOD, "CompoundedInArrears2daysCalcperiod"},
        {FallbackType.COMPOUNDED_IN_ARREARS_2DAYS_IBORPERIOD, "CompoundedInArrears2daysIborperiod"},
        {FallbackType.COMPOUNDED_IN_ARREARS_2DAYS_TENOR, "CompoundedInArrears2daysTenor"},
    };
  }

  @ParameterizedTest
  @MethodSource("data_name")
  public void test_toString(FallbackType convention, String name) {
    assertThat(convention.toString()).isEqualTo(name);
  }

  @ParameterizedTest
  @MethodSource("data_name")
  public void test_of_lookup(FallbackType convention, String name) {
    assertThat(FallbackType.of(name)).isEqualTo(convention);
  }

  @Test
  public void test_of_lookup_notFound() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FallbackType.of("Rubbish"));
  }

  @Test
  public void test_of_lookup_null() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FallbackType.of(null));
  }

  //-------------------------------------------------------------------------
  @Test
  public void coverage() {
    coverEnum(FallbackType.class);
  }

  @Test
  public void test_serialization() {
    assertSerialization(FallbackType.COMPOUNDED_IN_ARREARS_2DAYS_TENOR);
  }
  
}
