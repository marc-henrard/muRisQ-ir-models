package marc.henrard.murisq.pricer.indexfutures;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.index.ResolvedOvernightFuture;

import marc.henrard.murisq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Pricer of overnight futures on compounded in arrears rates in the Hull-White one-factor model.
 * <p>
 * <i>Reference: </i>
 * <p>
 * Henrard, Marc. (2018) Overnight based futures: convexity adjustment estimation
 * 
 * @author Marc Henrard
 */
public class HullWhiteOneFactorOvernightFuturesProductPricer {

  /**
   * Default implementation.
   */
  public static final HullWhiteOneFactorOvernightFuturesProductPricer DEFAULT = 
      new HullWhiteOneFactorOvernightFuturesProductPricer();

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
      ResolvedOvernightFuture futures,
      RatesProvider ratesProvider,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    Currency ccy = futures.getCurrency();
    List<LocalDate> onDates = overnightDates(futures);
    LocalDate startDate = futures.getOvernightRate().getStartDate();
    LocalDate endDate = futures.getOvernightRate().getEndDate();
    int nbOnDates = onDates.size();
    double delta = futures.getIndex().getDayCount()
        .yearFraction(startDate, endDate); // index AF
    double PcTs = ratesProvider.discountFactor(ccy, startDate);
    double PcTe = ratesProvider.discountFactor(ccy, endDate);
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
      ResolvedOvernightFuture futures,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    Currency ccy = futures.getCurrency();
    List<Double> gamma = convexityAdjustmentGammas(futures, multicurve, hwProvider);
    double productGamma = 1.0;
    for (int i = 0; i < gamma.size() - 1; i++) {
      productGamma *= gamma.get(i);
    }
    LocalDate startDate = futures.getOvernightRate().getStartDate();
    LocalDate endDate = futures.getOvernightRate().getEndDate();
    double delta = futures.getIndex().getDayCount().yearFraction(startDate, endDate);
    double PcTs = multicurve.discountFactor(ccy, startDate);
    double PcTe = multicurve.discountFactor(ccy, endDate);
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
      ResolvedOvernightFuture futures,
      RatesProvider multicurve,
      HullWhiteOneFactorPiecewiseConstantParametersProvider hwProvider) {

    List<LocalDate> onDates = overnightDates(futures);
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
  
  /**
   * Returns the overnight dates associated to a given futures.
   * 
   * @param futures  the overnight futures
   * @return the dates
   */
  public List<LocalDate> overnightDates(ResolvedOvernightFuture futures){
    
    LocalDate startDate = futures.getOvernightRate().getStartDate();
    LocalDate endDate = futures.getOvernightRate().getEndDate();
    HolidayCalendar calendar = futures.getOvernightRate().getFixingCalendar();
    List<LocalDate> onDates = new ArrayList<>();
    LocalDate currentDate = startDate;
    onDates.add(startDate);
    while(currentDate.isBefore(endDate)) {
      currentDate = calendar.next(currentDate);
      onDates.add(currentDate);
    }
    return onDates;
  }

}
