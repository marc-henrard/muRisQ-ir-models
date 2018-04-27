/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.risq.model.bachelier;

import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.common.PutCall;

/**
 * Methods related to the Bachelier/normal formula for option pricing.
 * 
 * @author Marc Henrard <a href="http://multi-curve-framework.blogspot.com/">Multi-curve Framework</a>
 */
public class BachelierFormula {

  /** Constants related to the approximated implied volatility formula implemented in 'impliedVolatilityApproxLfk4'. */
  /** Below this simple moneyness, the option is considered ATM and a simple approximation is used. */
  private static final double LFK4_CUTOFF_ATM = 1.0E-10;
  /** The first cutoff value for the value of -C(x)/x */
  private static final double LFK4_CUTOFF_1 = 0.15;
  /** The first cutoff value for the value of \tilde \eta */
  private static final double LFK4_CUTOFF_2 = 0.0091;
  /** The second cutoff value for the value of \tilde \eta */
  private static final double LFK4_CUTOFF_3 = 0.088;
  /** Rational functions coefficients */
  private static final double[] A_LFK4 = { 0.06155371425063157, 2.723711658728403, 10.83806891491789, 301.0827907126612,
      1082.864564205999, 790.7079667603721, 109.330638190985, 0.1515726686825187, 1.436062756519326,
      118.6674859663193, 441.1914221318738, 313.4771127147156, 40.90187645954703 };
  private static final double[] B_LFK4 = { 0.6409168551974357, 788.5769356915809, 445231.8217873989, 149904950.4316367,
      32696572166.83277, 4679633190389.852, 420159669603232.9, 2.053009222143781e+16, 3.434507977627372e+17,
      2.012931197707014e+16, 644.3895239520736, 211503.4461395385, 42017301.42101825, 5311468782.258145,
      411727826816.0715, 17013504968737.03, 247411313213747.3 };
  private static final double[] C_LFK4 = { 0.6421106629595358, 654.5620600001645, 291531.4455893533, 69009535.38571493,
      9248876215.120627, 479057753706.175, 9209341680288.471, 61502442378981.76, 107544991866857.5,
      63146430757.94501, 437.9924136164148, 90735.89146171122, 9217405.224889684, 400973228.1961834,
      7020390994.356452, 44654661587.93606, 76248508709.85633 };
  private static final double[] D_LFK4 = { 0.936024443848096, 328.5399326371301, 177612.3643595535, 8192571.038267588,
      110475347.0617102, 545792367.0681282, 1033254933.287134, 695066365.5403566, 123629089.1036043,
      756.3653755877336, 173.9755977685531, 6591.71234898389, 82796.56941455391, 396398.9698566103,
      739196.7396982114, 493626.035952601, 87510.31231623856 };
  
  private static final double VERY_SMALL_VOL = 1.0E-16;

  /**
   * Approximated Bachelier implied volatility from the option price.
   * <p>
   * Approximation of the implied volatility by rational functions. The version implemented in this method 
   * is the "LFK-4 representation with four rational functions" (section 4.2.2 of the paper in the reference). The
   * coefficients are the ones of the Matlab code reproduced in the paper.
   * <p>
   * Reference: Fabien Le Floc’h, "Fast and Accurate Analytic Basis Point Volatility", v3.1 released June 2016
   * 
   * @param optionPrice  the option price
   * @param forward  the forward price/rate
   * @param strike  the strike
   * @param timeToExpiry  the time to expiry
   * @param numeraire  the numeraire; used to re-scale the price
   * @param putCall  the put/call flag
   * @return the implied Bachelier volatility for the given price
   */
  public static double impliedVolatilityApproxLfk4(double optionPrice, double forward, double strike,
      double timeToExpiry, double numeraire, PutCall putCall) {

    ArgChecker.isTrue(numeraire > 0, "numeraire must be greater than 0");
    double price = optionPrice / numeraire;
    double sign = (putCall.equals(PutCall.CALL)) ? 1.0d : -1.0d;
    double intrinsic = Math.max((forward - strike) * sign, 0);
    if(Math.abs(price - intrinsic) < Math.sqrt(timeToExpiry) * VERY_SMALL_VOL) {
      return 0.0d; // To accommodate rounding error in the price when model has finite range.
    }
    ArgChecker.isTrue(price > intrinsic,
        "optionPrice must be greater than intrinsic value. Have price=" + price + " and intrinsic=" + intrinsic);

    double betaStart = -Math.log(LFK4_CUTOFF_1);
    double betaEnd = -Math.log(Double.MIN_NORMAL);
    double x = (forward - strike) * sign; // Intrinsic value
    if (Math.abs(x) < LFK4_CUTOFF_ATM) {
      return price * Math.sqrt(2 * Math.PI / timeToExpiry);
    }
    double z;
    if (x > 0.0d) {
      z = (price - x) / x;
    } else {
      z = -price / x;
    }
    if (z < LFK4_CUTOFF_1) {
      ArgChecker.isTrue(z > 0, "z must be positive");
      double u = -(Math.log(z) + betaStart) / (betaEnd - betaStart);
      double hz = 0.0;
      if (u < LFK4_CUTOFF_2) {
        double num = B_LFK4[0] + u * (B_LFK4[1] + u * (B_LFK4[2] + u * (B_LFK4[3] + u * (B_LFK4[4]
            + u * (B_LFK4[5] + u * (B_LFK4[6] + u * (B_LFK4[7] + u * (B_LFK4[8] + u * (B_LFK4[9])))))))));
        double den = 1.0d + u * (B_LFK4[10] + u * (B_LFK4[11]
            + u * (B_LFK4[12] + u * (B_LFK4[13] + u * (B_LFK4[14] + u * (B_LFK4[15] + u * B_LFK4[16]))))));
        hz = num / den;
      } else if (u < LFK4_CUTOFF_3) {
        double num = C_LFK4[0] + u * (C_LFK4[1] + u * (C_LFK4[2] + u * (C_LFK4[3] + u * (C_LFK4[4]
            + u * (C_LFK4[5] + u * (C_LFK4[6] + u * (C_LFK4[7] + u * (C_LFK4[8] + u * (C_LFK4[9])))))))));
        double den = 1.0d + u * (C_LFK4[10] + u * (C_LFK4[11]
            + u * (C_LFK4[12] + u * (C_LFK4[13] + u * (C_LFK4[14] + u * (C_LFK4[15] + u * C_LFK4[16]))))));
        hz = num / den;
      } else {
        double num = D_LFK4[0] + u * (D_LFK4[1] + u * (D_LFK4[2] + u * (D_LFK4[3] + u * (D_LFK4[4]
            + u * (D_LFK4[5] + u * (D_LFK4[6] + u * (D_LFK4[7] + u * (D_LFK4[8] + u * (D_LFK4[9])))))))));
        double den = 1.0d + u * (D_LFK4[10] + u * (D_LFK4[11]
            + u * (D_LFK4[12] + u * (D_LFK4[13] + u * (D_LFK4[14] + u * (D_LFK4[15] + u * D_LFK4[16]))))));
        hz = num / den;
      }
      return Math.abs(x) / Math.sqrt(hz * timeToExpiry);
    }
    if (x < 0) {
      price = price - x;
    }
    z = Math.abs(x) / price;
    double u = eta(z);
    double num = A_LFK4[0] + u * (A_LFK4[1]
        + u * (A_LFK4[2] + u * (A_LFK4[3] + u * (A_LFK4[4] + u * (A_LFK4[5] + u * (A_LFK4[6] + u * (A_LFK4[7])))))));
    double den = 1.0d + u * (A_LFK4[8] + u * (A_LFK4[9] + u * (A_LFK4[10] + u * (A_LFK4[11] + u * A_LFK4[12]))));
    return price * num / (den * Math.sqrt(timeToExpiry));
  }

  /**
   * Internal function for the value of eta (Formula (11) in the paper).
   * 
   * @param z  the z-parameter
   * @return the eta value
   */
  private static double eta(double z) {
    if (z < 1.0E-2) { // Case close to 0, to avoid 0/0, expansion order 7
      return 1.0d - z * (0.5d + z * (1.0d / 12.0d + z * (1.0d / 24.0d + z
          * (19.0d / 720.0d + z * (3.0d / 160.0d + z * (863.0d / 60_480.0d + z * (275.0d / 24_192.0d)))))));
    }
    return -z / Math.log(1.0d - z);
  }

}
