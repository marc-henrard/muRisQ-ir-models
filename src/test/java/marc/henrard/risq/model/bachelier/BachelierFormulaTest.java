/**
 * Copyright (C) 2016 - Marc Henrard.
 */
package marc.henrard.risq.model.bachelier;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.strata.pricer.impl.option.NormalFormulaRepository;
import com.opengamma.strata.product.common.PutCall;

/**
 * Test {@link BachelierFormula} implied volatility.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
@Test
public class BachelierFormulaTest {

  private static final double FORWARD = 123.4;
  private static final double DF = 0.95;
  private static final double T = 3.25;
  private static final int NB_TESTS = 10;
  private static final double[] PRICES_CALL = new double[NB_TESTS];
  private static final double[] PRICES_CALL_ATM = new double[NB_TESTS];
  private static final double[] PRICES_PUT = new double[NB_TESTS];
  private static final double[] PRICES_PUT_ATM = new double[NB_TESTS];
  private static final double[] STRIKES = new double[NB_TESTS];
  private static final double[] STRIKES_ATM = new double[NB_TESTS];
  private static final double[] SIGMA_BACHELIER = new double[NB_TESTS];
  static {
    for (int i = 0; i < NB_TESTS; i++) {
      STRIKES[i] = FORWARD + (-NB_TESTS / 2 + i) * 10;
      STRIKES_ATM[i] = FORWARD + (-0.5d * NB_TESTS + i) * 3.0E-10;
      SIGMA_BACHELIER[i] = FORWARD * (0.05 + 4.0 * i / 100.0);
      PRICES_CALL[i] = DF * NormalFormulaRepository.price(FORWARD, STRIKES[i], T, SIGMA_BACHELIER[i], PutCall.CALL);
      PRICES_CALL_ATM[i] = DF * NormalFormulaRepository.price(FORWARD, STRIKES_ATM[i], T, SIGMA_BACHELIER[i], PutCall.CALL);
      PRICES_PUT[i] = DF * NormalFormulaRepository.price(FORWARD, STRIKES[i], T, SIGMA_BACHELIER[i], PutCall.PUT);
      PRICES_PUT_ATM[i] = DF * NormalFormulaRepository.price(FORWARD, STRIKES_ATM[i], T, SIGMA_BACHELIER[i], PutCall.PUT);
    }
  }
  private static final double TOLERANCE_VOL = 1.0E-7;

  /* Test the implied volatility for calls. */
  public void implied_volatility_call() {
    for (int i = 0; i < NB_TESTS; i++) {
      double ivComputed = 
          BachelierFormula.impliedVolatilityApproxLfk4(PRICES_CALL[i], FORWARD, STRIKES[i], T, DF, PutCall.CALL);
      assertEquals("Strike: " + i, SIGMA_BACHELIER[i], ivComputed, TOLERANCE_VOL);
    }
  }

  /* Tests the implied volatility for calls close to ATM. There is a special treatment for strikes close to ATM. */
  public void implied_volatility_call_atm() {
    for (int i = 0; i < NB_TESTS; i++) {
      double ivComputed = 
          BachelierFormula.impliedVolatilityApproxLfk4(PRICES_CALL_ATM[i], FORWARD, STRIKES_ATM[i], T, DF, PutCall.CALL);
      assertEquals("Strike: " + i, SIGMA_BACHELIER[i], ivComputed, TOLERANCE_VOL);
    }
  }

  /* Test the implied volatility for puts. */
  public void implied_volatility_put() {
    for (int i = 0; i < NB_TESTS; i++) {
      double ivComputed = BachelierFormula.impliedVolatilityApproxLfk4(PRICES_PUT[i], FORWARD, STRIKES[i], T, DF,
          PutCall.PUT);
      assertEquals("Strike: " + i, SIGMA_BACHELIER[i], ivComputed, TOLERANCE_VOL);
    }
  }

  /* Tests the implied volatility for puts close to ATM. There is a special treatment for strikes close to ATM. */
  public void implied_volatility_put_atm() {
    for (int i = 0; i < NB_TESTS; i++) {
      double ivComputed = BachelierFormula.impliedVolatilityApproxLfk4(PRICES_PUT_ATM[i], FORWARD, STRIKES_ATM[i],
          T, DF, PutCall.CALL);
      assertEquals("Strike: " + i, SIGMA_BACHELIER[i], ivComputed, TOLERANCE_VOL);
    }
  }

  /* The prices must be above the intrinsic value. Call */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void wrong_price_call() {
    BachelierFormula.impliedVolatilityApproxLfk4(0.9 * DF, FORWARD, FORWARD - 1.0, T, DF, PutCall.CALL);
  }

  /* The prices must be above the intrinsic value. Put */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void wrong_price_put() {
    BachelierFormula.impliedVolatilityApproxLfk4(0.9 * DF, FORWARD, FORWARD + 1.0, T, DF, PutCall.PUT);
  }

}
