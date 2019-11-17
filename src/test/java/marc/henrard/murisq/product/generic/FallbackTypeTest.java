/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.generic;

import static com.opengamma.strata.collect.TestHelper.assertThrows;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests {@link FallbackType}.
 * 
 * @author Marc Henrard
 */
@Test
public class FallbackTypeTest {

  @DataProvider(name = "name")
  public static Object[][] data_name() {
    return new Object[][] {
        {FallbackType.SPOT_OVERNIGHT, "SpotOvernight"},
        {FallbackType.COMPOUNDED_IN_ARREARS_CALCPERIOD, "CompoundedInArrearsCalcperiod"},
        {FallbackType.COMPOUNDED_IN_ADVANCE, "CompoundedInAdvance"},
        {FallbackType.OIS_BENCHMARK, "OisBenchmark"},
        {FallbackType.COMPOUNDED_IN_ARREARS_2DAYS_CALCPERIOD, "CompoundedInArrears2daysCalcperiod"},
        {FallbackType.COMPOUNDED_IN_ARREARS_2DAYS_IBORPERIOD, "CompoundedInArrears2daysIborperiod"},
    };
  }

  @Test(dataProvider = "name")
  public void toString(FallbackType type, String name) {
    assertEquals(type.toString(), name);
  }

  @Test(dataProvider = "name")
  public void ofString(FallbackType type, String name) {
    assertEquals(FallbackType.of(name), type);
  }

  public void ofNotFound() {
    assertThrows(() -> FallbackType.of("Unknown"), IllegalArgumentException.class);
  }

  public void ofNull() {
    assertThrows(() -> FallbackType.of(null), IllegalArgumentException.class);
  }
}
