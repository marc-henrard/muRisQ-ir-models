/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.indexfutures;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;

import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;
import marc.henrard.murisq.product.futures.CompoundedOvernightFuturesResolved;

/**
 * Pricer of overnight futures in the Hull-White one-factor model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Henrard, Marc. (2018) Overnight based futures: convexity adjustment estimation
 * 
 * @author Marc Henrard
 */
public class HullWhiteOneFactorCompoundedOvernightFuturesProductPricer {

  /**
   * Default implementation.
   */
  public static final HullWhiteOneFactorCompoundedOvernightFuturesProductPricer DEFAULT = 
      new HullWhiteOneFactorCompoundedOvernightFuturesProductPricer();

  /** The model related formulas. */
  private static final HullWhiteOneFactorPiecewiseConstantFormulas FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;

  /**
   * Returns the price of the overnight futures.
   * 
   * @param futures  the overnight futures
   * @param multicurve  the multi-curve
   * @param hwProvider  the Hull-White one-factor parameters provider
   * @return  the price
   */
  public double price(
      CompoundedOvernightFuturesResolved futures,
      RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    Currency ccy = futures.getCurrency();
    List<LocalDate> onDates = futures.getOnDates();
    int nbOnDates = onDates.size();
    double delta = futures.getIndex().getDayCount()
        .yearFraction(futures.getStartAccrualDate(), futures.getEndAccrualDate()); // index AF
    double PcTs = ratesProvider.discountFactor(ccy, futures.getStartAccrualDate());
    double PcTe = ratesProvider.discountFactor(ccy, futures.getEndAccrualDate());
    List<Double> ti = new ArrayList<>();
    ti.add(0.0d);
    for (int i = 0; i < nbOnDates; i++) {
      ti.add(hwProvider.relativeTime(onDates.get(i)));
    }
    List<Double> gamma = new ArrayList<>();
    for (int i = 0; i < nbOnDates - 1; i++) {
      gamma.add(FORMULAS.futuresConvexityFactor(
          hwProvider.getParameters(), ti.get(i), ti.get(i + 1), ti.get(i + 1), ti.get(nbOnDates)));
    }
    double productGamma = 1.0;
    for (int i = 0; i < onDates.size() - 1; i++) {
      productGamma *= gamma.get(i);
    }
    return 1.0d - (PcTs / PcTe * productGamma - 1.0d) / delta;
  }
  
  /**
   * Returns the convexity adjustment associated to the overnight futures.
   * 
   * @param futures  the overnight futures
   * @param multicurve  the multi-curve
   * @param hwProvider  the Hull-White one-factor parameters provider
   * @return  the adjustment
   */
  public double convexityAdjustment(
      CompoundedOvernightFuturesResolved futures,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    Currency ccy = futures.getCurrency();
    List<Double> gamma = convexityAdjustmentGammas(futures, multicurve, hwProvider);
    double productGamma = 1.0;
    for (int i = 0; i < gamma.size() - 1; i++) {
      productGamma *= gamma.get(i);
    }
    double delta = futures.getAccrualFactor();
    double PcTs = multicurve.discountFactor(ccy, futures.getStartAccrualDate());
    double PcTe = multicurve.discountFactor(ccy, futures.getEndAccrualDate());
    return PcTs / PcTe * (productGamma - 1.0d) / delta;
  }
  
  /**
   * Returns the different gamma factors used in the convexity adjustment associated to the overnight futures.
   * <p>
   * See the literature reference for the exact definition of the factors.
   * 
   * @param futures  the overnight futures
   * @param multicurve  the multi-curve
   * @param hwProvider  the Hull-White one-factor parameters provider
   * @return  the factors
   */
  public List<Double> convexityAdjustmentGammas(
      CompoundedOvernightFuturesResolved futures,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    List<LocalDate> onDates = futures.getOnDates();
    int nbOnDates = onDates.size();
    List<Double> ti = new ArrayList<>();
    ti.add(0.0d);
    for (int i = 0; i < nbOnDates; i++) {
      ti.add(hwProvider.relativeTime(onDates.get(i)));
    }
    List<Double> gamma = new ArrayList<>();
    for (int i = 0; i < nbOnDates - 1; i++) {
      gamma.add(FORMULAS.futuresConvexityFactor(
          hwProvider.getParameters(), ti.get(i), ti.get(i + 1), ti.get(i + 1), ti.get(nbOnDates)));
    }
    return gamma;
  }

}
