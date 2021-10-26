/**
 * Copyright (C) 2021 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.generic;

import java.util.function.Function;

import com.opengamma.strata.basics.value.ValueDerivatives;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.rootfinding.BracketRoot;
import com.opengamma.strata.math.impl.rootfinding.BrentSingleRootFinder;
import com.opengamma.strata.math.impl.rootfinding.RealSingleRootFinder;

/**
 * Utilities related to ICE Swap Rate fallback as proposed by WGSRFRR and ARRC.
 * 
 * @author Marc Henrard
 */
public class FallbackIsrUtils {
  /**
   * The ISDA fallback spread for USD-LIBOR-3M.
   */
  private static final double USD_LIBOR_3M_SPREAD = 0.0026161;
  /**
   * The ISDA fallback spread for USD-LIBOR-3M.
   */
  private static final double GBP_LIBOR_3M_SPREAD = 0.001193;
  /**
   * The ISDA fallback spread for USD-LIBOR-3M.
   */
  private static final double GBP_LIBOR_6M_SPREAD = 0.002766;
  /**
   * Root finder for solving the OIS rate equivalent by fallback to original IRS rate.
   */
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();
  private static final BracketRoot BRACKET_ROOT = new BracketRoot();

  /** Range around a very naive root approximation. */
  private static final double RANGE = 0.0050;

  /**
   * ARRC proposed fallback mechanism for ICE Swap Rate in USD.
   * 
   * @param rateOis  the OIS-linked benchmark rate
   * @return the fallback rate
   */
  public static double fallbackMechanismUsd(double rateOis) {
    return 365.25d / 360.00d *
        (2 * (Math.sqrt(1 + rateOis) - 1) + USD_LIBOR_3M_SPREAD * 0.5 * (Math.pow(1 + rateOis, 0.25) + 1));
  }

  /**
   * WGSRFRR proposed fallback mechanism for ICE Swap Rate in GBP (1Y Tenor).
   * 
   * @param rateOis  the OIS-linked benchmark rate
   * @return the fallback rate
   */
  public static double fallbackMechanismGbp1Y(double rateOis) {
    return rateOis +
        GBP_LIBOR_3M_SPREAD * 0.25d * (Math.pow(1 + rateOis, 0.25) + 1) * (Math.sqrt(1 + rateOis) + 1);
  }

  /**
   * WGSRFRR proposed fallback mechanism for ICE Swap Rate in GBP (Tenor > 1Y).
   * 
   * @param rateOis  the OIS-linked benchmark rate
   * @return the fallback rate
   */
  public static double fallbackMechanismGbpPlus1Y(double rateOis) {
    return 2.0d * (Math.sqrt(1 + rateOis) - 1) + GBP_LIBOR_6M_SPREAD;
  }

  /**
   * ARRC proposed fallback mechanism for ICE Swap Rate in USD and the derivative of the fallback function.
   * 
   * @param rateOis  the OIS-linked benchmark rate
   * @return the fallback rate and its derivatives of order 1 to 3 at the given OIS rate.
   */
  public static ValueDerivatives fallbackMechanismUsdAD(double rateOis) {
    double r12 = Math.sqrt(1 + rateOis);
    double part1 = 2 * (r12 - 1);
    double part2 = USD_LIBOR_3M_SPREAD * 0.5 * (Math.pow(1 + rateOis, 0.25) + 1);
    double factor = 365.25d / 360.00d;
    double f = factor * (part1 + part2);
    double part1p = 1.0d / r12;
    double part2p = USD_LIBOR_3M_SPREAD * 0.125d * Math.pow(1 + rateOis, -0.75);
    double fp = factor * (part1p + part2p);
    double part1pp = -0.5d / (r12 * r12 * r12);
    double part2pp = -USD_LIBOR_3M_SPREAD * 3.0d / 32.0d * Math.pow(1 + rateOis, -1.75);
    double fpp = factor * (part1pp + part2pp);
    double part1ppp = 0.75d * Math.pow(1 + rateOis, -2.5);
    double part2ppp = -1.75 * part2pp / (1 + rateOis);
    double fppp = factor * (part1ppp + part2ppp);
    return ValueDerivatives.of(f, DoubleArray.of(fp, fpp, fppp));
  }

  /**
   * WGSRFRR proposed fallback mechanism for ICE Swap Rate in GBP (Tenor 1Y) 
   * and the derivative of the fallback function.
   * 
   * @param rateOis  the OIS-linked benchmark rate
   * @return the fallback rate and its derivatives of order 1 to 3 at the given OIS rate.
   */
  public static ValueDerivatives fallbackMechanismGbp1YAD(double rateOis) {
    double r12 = Math.sqrt(1 + rateOis);
    double r14 = Math.pow(1 + rateOis, 0.25);
    double f = rateOis + GBP_LIBOR_3M_SPREAD * 0.25d * (r14 + 1) * (r12 + 1);
    double r12p = 0.5d / r12;
    double r14p = 0.25d * r14 / (1 + rateOis);
    double fp = 1 + GBP_LIBOR_3M_SPREAD * 0.25d * (r14p * (r12 + 1) + (r14 + 1) * r12p);
    double r12pp = -0.5d * r12p / (1 + rateOis);
    double r14pp = -0.75 * r14p / (1 + rateOis);
    double fpp = GBP_LIBOR_3M_SPREAD * 0.25d *
        (r14pp * (r12 + 1) + 2 * r14p * r12p + (r14 + 1) * r12pp);
    double r12ppp = -3.0d / 2.0d * r12pp / (1 + rateOis);
    double r14ppp = -1.75 * r14pp / (1 + rateOis);
    double fppp = GBP_LIBOR_3M_SPREAD * 0.25d *
        (r14ppp * (r12 + 1) + 3 * (r14pp * r12p + r14p * r12pp) + (r14 + 1) * r12ppp);
    return ValueDerivatives.of(f, DoubleArray.of(fp, fpp, fppp));
  }

  /**
   * WGSRFRR proposed fallback mechanism for ICE Swap Rate in GBP (Tenor > 1Y) 
   * and the derivative of the fallback function.
   * 
   * @param rateOis  the OIS-linked benchmark rate
   * @return the fallback rate and its derivatives of order 1 to 3 at the given OIS rate.
   */
  public static ValueDerivatives fallbackMechanismGbpPlus1YAD(double rateOis) {
    double r12 = Math.sqrt(1 + rateOis);
    double f = 2.0d * (r12 - 1) + GBP_LIBOR_6M_SPREAD;
    double r12p = 0.5d / r12;
    double fp = 2.0d * r12p;
    double r12pp = -0.5d * r12p / (1 + rateOis);
    double fpp = 2.0d * r12pp;
    double r12ppp = -3.0d / 2.0d * r12pp / (1 + rateOis);
    double fppp = 2.0d * r12ppp;
    return ValueDerivatives.of(f, DoubleArray.of(fp, fpp, fppp));
  }

  /**
   * Returns the OIS rate equivalent by fallback to original IRS rate (USD).
   * 
   * @param rateIrs  the IRS rate
   * @return the adjusted strike
   */
  public static double fallbackEquivalentRateUsd(double rateIrs) {
    Function<Double, Double> f =
        (x) -> fallbackMechanismUsd(x) - rateIrs;
    double xLower = rateIrs - USD_LIBOR_3M_SPREAD - RANGE;
    double xUpper = rateIrs - USD_LIBOR_3M_SPREAD + RANGE;
    double[] bracket = BRACKET_ROOT.getBracketedPoints(f, xLower, xUpper);
    double adjusted = ROOT_FINDER.getRoot(f, bracket[0], bracket[1]);
    return adjusted;
  }

  /**
   * Returns the OIS rate equivalent by fallback to original IRS rate (GBP Tenor 1Y).
   * 
   * @param rateIrs  the IRS rate
   * @return the adjusted strike
   */
  public static double fallbackEquivalentRateGbp1Y(double rateIrs) {
    Function<Double, Double> f =
        (x) -> fallbackMechanismGbp1Y(x) - rateIrs;
    double xLower = rateIrs - GBP_LIBOR_3M_SPREAD - RANGE;
    double xUpper = rateIrs - GBP_LIBOR_3M_SPREAD + RANGE;
    double[] bracket = BRACKET_ROOT.getBracketedPoints(f, xLower, xUpper);
    double adjusted = ROOT_FINDER.getRoot(f, bracket[0], bracket[1]);
    return adjusted;
  }

  /**
   * Returns the OIS rate equivalent by fallback to original IRS rate (GBP Tenor > 1Y).
   * 
   * @param rateIrs  the IRS rate
   * @return the adjusted strike
   */
  public static double fallbackEquivalentRateGbpPlus1Y(double rateIrs) {
    Function<Double, Double> f =
        (x) -> fallbackMechanismGbpPlus1Y(x) - rateIrs;
    double xLower = rateIrs - GBP_LIBOR_6M_SPREAD - RANGE;
    double xUpper = rateIrs - GBP_LIBOR_6M_SPREAD + RANGE;
    double[] bracket = BRACKET_ROOT.getBracketedPoints(f, xLower, xUpper);
    double adjusted = ROOT_FINDER.getRoot(f, bracket[0], bracket[1]);
    return adjusted;
  }

}