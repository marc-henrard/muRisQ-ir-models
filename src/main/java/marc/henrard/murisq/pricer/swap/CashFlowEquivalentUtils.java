/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.pricer.impl.rate.swap.CashFlowEquivalentCalculator;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapPaymentEvent;

/**
 * Utilities to manipulate results of {@link CashFlowEquivalentCalculator}
 * 
 * @author Marc Henrard
 */
public class CashFlowEquivalentUtils {

  /**
   * Sort by increasing dates and compress cash flows at the same date.
   * <p>
   * The input cash flow equivalent must be made of {@link NotionalExchange} {@link SwapPaymentEvent}.
   *  
   * @param cfe  the starting cash flow equivalent
   * @return  the sorted and compressed cash flow equivalent
   */
  public static ResolvedSwapLeg sortCompress(ResolvedSwapLeg cfe) {
    ImmutableList<SwapPaymentEvent> cfePeriods = cfe.getPaymentEvents();
    int nbCf = cfePeriods.size();
    // Sort
    List<Pair<LocalDate, Integer>> dateIndices = new ArrayList<>();
    for (int loopcf = 0; loopcf < nbCf; loopcf++) {
      ArgChecker.isTrue(cfePeriods.get(loopcf) instanceof NotionalExchange,
          "each period must be of the type NotionalExchange");
      dateIndices.add(Pair.of(cfePeriods.get(loopcf).getPaymentDate(), loopcf));
    }
    Collections.sort(dateIndices);
    // Compress duplicate dates
    List<SwapPaymentEvent> cfeCompressed = new ArrayList<>();
    cfeCompressed.add(cfePeriods.get(dateIndices.get(0).getSecond()));
    for (int loopcf = 1; loopcf < nbCf; loopcf++) {
      if (dateIndices.get(loopcf).getFirst().equals(dateIndices.get(loopcf - 1).getFirst())) {
        NotionalExchange cfPrevious = (NotionalExchange) cfeCompressed.get(cfeCompressed.size() - 1);
        NotionalExchange cf = (NotionalExchange) cfePeriods.get(dateIndices.get(loopcf).getSecond());
        NotionalExchange cfCompressed = NotionalExchange.of(
            Payment.of(cf.getCurrency(),
                cfPrevious.getPaymentAmount().getAmount() + cf.getPaymentAmount().getAmount(),
                cf.getPaymentDate()));
        cfeCompressed.set(cfeCompressed.size() - 1, cfCompressed);
      } else { // different date
        cfeCompressed.add(cfePeriods.get(dateIndices.get(loopcf).getSecond()));
      }
    }
    return ResolvedSwapLeg.builder()
        .paymentEvents(cfeCompressed)
        .type(cfe.getType())
        .payReceive(cfe.getPayReceive()).build();
  }

}
