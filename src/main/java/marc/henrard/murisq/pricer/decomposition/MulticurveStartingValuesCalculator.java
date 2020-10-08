/**
 * Copyright (C) 2015 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.decomposition;

import java.util.ArrayList;
import java.util.List;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;

/**
 * Computes the starting values of multi-curve equivalent for a given rates provider.
 * Used mainly for Monte Carlo pricing.
 */
public class MulticurveStartingValuesCalculator {

  /**
   * Computes the starting value associated to a decision schedule from a rates provider.
   * 
   * @param decisionSchedule  the decision schedule
   * @param multicurve  the rates provider
   * @return the values
   */
  public static List<MulticurveEquivalentValues> startingValuesRates(
      MulticurveEquivalentSchedule decisionSchedule,
      RatesProvider multicurve){
    List<MulticurveEquivalent> meList = decisionSchedule.getSchedules();
    List<MulticurveEquivalentValues> v = new ArrayList<>(meList.size());
    for(MulticurveEquivalent me: meList) {
      v.add(startingValuesRates(me, multicurve));
    }
    return v;
  }
  
  /**
   * Computes the starting value of a multi-curve equivalent from a rates provider.
   * <p>
   * The values associated to the Libor payments are Libor forward rates. 
   * For Libor present values, use {@link MulticurveStartingValuesCalculator#startingValuesProcess}.
   * 
   * @param multicurveEquivalent  the multi-curve equivalent
   * @param multicurve  the rates provider
   * @return the values
   */
  public static MulticurveEquivalentValues startingValuesRates(
      MulticurveEquivalent multicurveEquivalent,
      RatesProvider multicurve) {
    
    int dfSize = multicurveEquivalent.getDiscountFactorPayments().size();
    double[] df = new double[dfSize];
    for (int i = 0; i < dfSize; i++) {
      df[i] = multicurve.discountFactor(
          multicurveEquivalent.getDiscountFactorPayments().get(i).getCurrency(),
          multicurveEquivalent.getDiscountFactorPayments().get(i).getPaymentDate());
    }
    
    int nbIbor = multicurveEquivalent.getIborComputations().size();
    double[] l = new double[nbIbor];
    List<IborRateComputation> ibor = multicurveEquivalent.getIborComputations();
    for (int i = 0; i < dfSize; i++) {
      l[i] = multicurve.iborIndexRates(ibor.get(i).getIndex()).rate(ibor.get(i).getObservation());
    }
    
    int nbOn = multicurveEquivalent.getOnComputations().size();
    double[] on = new double[nbOn];
    List<OvernightCompoundedRateComputation> o = multicurveEquivalent.getOnComputations();
    for (int i = 0; i < dfSize; i++) {
      on[i] = multicurve.overnightIndexRates(o.get(i).getIndex())
          .periodRate(o.get(i).observeOn(o.get(i).getStartDate()), o.get(i).getEndDate());
    }
    return MulticurveEquivalentValues.builder()
        .discountFactors(DoubleArray.ofUnsafe(df))
        .iborRates(DoubleArray.ofUnsafe(l))
        .onRates(DoubleArray.ofUnsafe(on))
        .build();
  }

}
