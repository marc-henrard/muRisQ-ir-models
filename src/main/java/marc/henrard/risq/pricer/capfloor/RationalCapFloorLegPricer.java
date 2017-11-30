/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.capfloor;

import java.time.ZonedDateTime;
import java.util.function.Function;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.surface.ConstantSurface;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.math.impl.rootfinding.BracketRoot;
import com.opengamma.strata.math.impl.rootfinding.BrentSingleRootFinder;
import com.opengamma.strata.pricer.capfloor.NormalIborCapFloorLegPricer;
import com.opengamma.strata.pricer.capfloor.NormalIborCapletFloorletExpiryStrikeVolatilities;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.capfloor.IborCapletFloorletPeriod;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorLeg;

import marc.henrard.risq.model.rationalmulticurve.RationalParameters;

/**
 * Measures for cap/floor legs in a multi-curve rational model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Theoretical description: Crepey, S., Macrina, A., Nguyen, T.~M., and Skovmand, D. (2016).
 * Rational multi-curve models with counterparty-risk valuation adjustments. <i>Quantitative Finance</i>, 16(6): 847-866.
 * 
 * @author Marc Henrard
 */
public class RationalCapFloorLegPricer {
  
  /** Pricer used to estimate the Bachelier implied volatility. */
  private static final NormalIborCapFloorLegPricer PRICER_LEG_BACHELIER =
      NormalIborCapFloorLegPricer.DEFAULT;
  /** Tolerance for the implied volatility. */
  private static final double TOLERANCE_ABS = 1.0E-8;
  
  /** Pricer for {@link IborCapletFloorletPeriod} in the rational model. */
  private final RationalCapletFloorletPeriodPricer periodPricer;

  /**
   * Creates an instance.
   * 
   * @param periodPricer  the pricer for {@link IborCapletFloorletPeriod}.
   */
  public RationalCapFloorLegPricer(RationalCapletFloorletPeriodPricer periodPricer) {
    this.periodPricer = ArgChecker.notNull(periodPricer, "periodPricer");
  }
  
  /**
   * Calculates the present value of the Ibor cap/floor leg.
   * <p>
   * The result is expressed using the currency of the leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param model  the rational model parameters
   * @return the present value
   */
  public CurrencyAmount presentValue(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      RationalParameters model) {

    return capFloorLeg.getCapletFloorletPeriods()
        .stream()
        .map(period -> periodPricer.presentValue(period, multicurve, model))
        .reduce((c1, c2) -> c1.plus(c2))
        .get();
  }


  /**
   * Computes the implied volatility in the Bachelier model.
   * <p>
   * The cap/floor price is computed in the rational model and the implied volatility for that price is computed.
   * The implied volatility is the constant volatility for all caplets/floorlets composing the leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param model  the rational model parameters
   * @return the implied volatility in the Bachelier model
   */
  public double impliedVolatilityBachelier(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      RationalParameters model) {

    double pvRational = presentValue(capFloorLeg, multicurve, model).getAmount();
    return impliedVolatilityBachelier(capFloorLeg, multicurve, pvRational, model.getValuationDateTime());
  }

  /**
   * Computes the implied volatility in the Bachelier model.
   * <p>
   * The leg present value is passed and the implied volatility for that price is computed.
   * The implied volatility is the constant volatility for all caplets/floorlets composing the leg.
   * 
   * @param capFloorLeg  the Ibor cap/floor leg
   * @param multicurve  the rates provider 
   * @param pv  the leg's present value
   * @param valuationDateTime  the valuation date and time
   * @return the implied volatility in the Bachelier model
   */
  public double impliedVolatilityBachelier(
      ResolvedIborCapFloorLeg capFloorLeg,
      RatesProvider multicurve,
      double pv,
      ZonedDateTime valuationDateTime) {
    
    IborIndex index = capFloorLeg.getIndex();
    BracketRoot bracket = new BracketRoot();
    BrentSingleRootFinder rootFinder = new BrentSingleRootFinder(TOLERANCE_ABS);
    double notional = notional(capFloorLeg);
    if (Math.abs(pv) < notional * 1.0E-8) {  // price is 0 for practical purposes
      return 0.0d; // Return 0.0 implied volatility - possible as rates are bounded in the model
    }
    Function<Double, Double> error = x -> {
      NormalIborCapletFloorletExpiryStrikeVolatilities volatilities =
          NormalIborCapletFloorletExpiryStrikeVolatilities.of(index, valuationDateTime,
              ConstantSurface.of(DefaultSurfaceMetadata.builder()
                  .surfaceName("Bachelier-vol")
                  .xValueType(ValueType.YEAR_FRACTION)
                  .yValueType(ValueType.STRIKE)
                  .zValueType(ValueType.NORMAL_VOLATILITY)
                  .dayCount(DayCounts.ACT_365F).build(),
                  x));
      double pvBachelier = PRICER_LEG_BACHELIER.presentValue(capFloorLeg, multicurve, volatilities).getAmount();
      return pv - pvBachelier;
    };
    double ivLower = 0.0001;
    double ivUpper = 0.05;
    double[] ivBracket = bracket.getBracketedPoints(error, ivLower, ivUpper);
    double impliedVolatility = rootFinder.getRoot(error, ivBracket[0], ivBracket[1]);
    return impliedVolatility;
  }
  
  private double notional(ResolvedIborCapFloorLeg capFloorLeg) {
    double n = 0.0;
    for(IborCapletFloorletPeriod p: capFloorLeg.getCapletFloorletPeriods()) {
      n = Math.max(n, Math.abs(p.getNotional()));
    }
    return n;
  }

}
