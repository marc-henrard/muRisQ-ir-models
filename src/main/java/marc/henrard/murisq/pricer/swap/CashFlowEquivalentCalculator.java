/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.swap;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.IborIndexObservation;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.rate.RateComputation;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentEvent;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;

/**
 * Computes cash flow equivalent of products.
 * <p>
 * Reference: Henrard, M. (2018) Hybrid model: a dynamic multi-curve framework, working paper.
 */
public class CashFlowEquivalentCalculator {
  
  /**
   * Computes cash flow equivalent of swap.
   * <p>
   * The return type is {@code ResolvedSwapLeg} in which individual payments are
   * represented in terms of {@code NotionalExchange}.
   * 
   * @param swap  the swap product
   * @param multicurve  the multi-curve rates provider
   * @return the cash flow equivalent
   */
  public static ResolvedSwapLeg cashFlowEquivalentSwap(ResolvedSwap swap, RatesProvider multicurve) {
    
    List<SwapPaymentEvent> cfEquivalent = new ArrayList<>();
    for(ResolvedSwapLeg leg: swap.getLegs()) {
      if(leg.getType().equals(SwapLegType.FIXED)) {
        cfEquivalent.addAll(cashFlowEquivalentFixedLeg(leg, multicurve).getPaymentEvents());
      } else if(leg.getType().equals(SwapLegType.IBOR)) {
        cfEquivalent.addAll(cashFlowEquivalentIborLeg(leg, multicurve).getPaymentEvents());
      } else {
        cfEquivalent.addAll(cashFlowEquivalentOnLeg(leg, multicurve).getPaymentEvents());
      }
    }
    ResolvedSwapLeg leg = ResolvedSwapLeg.builder()
        .paymentEvents(cfEquivalent)
        .payReceive(PayReceive.RECEIVE)
        .type(SwapLegType.OTHER)
        .build();
    return leg;
  }
  
  /**
   * Computes cash flow equivalent of fixed leg.
   * <p>
   * The return type is {@code ResolvedSwapLeg} in which individual payments are
   * represented in terms of {@code NotionalExchange}.
   * 
   * @param fixedLeg  the fixed leg
   * @param multicurve  the multi-curve rates provider
   * @return the cash flow equivalent
   */
  public static ResolvedSwapLeg cashFlowEquivalentFixedLeg(ResolvedSwapLeg fixedLeg, RatesProvider multicurve) {

    Currency ccy = fixedLeg.getCurrency();
    ArgChecker.isTrue(fixedLeg.getType().equals(SwapLegType.FIXED), "Leg type should be FIXED");
    ArgChecker.isTrue(fixedLeg.getPaymentEvents().isEmpty(), "PaymentEvent should be empty");
    List<NotionalExchange> paymentEvents = new ArrayList<NotionalExchange>();
    for (SwapPaymentPeriod paymentPeriod : fixedLeg.getPaymentPeriods()) {
      ArgChecker.isTrue(paymentPeriod instanceof RatePaymentPeriod, "rate payment should be RatePaymentPeriod");
      RatePaymentPeriod ratePaymentPeriod = (RatePaymentPeriod) paymentPeriod;
      ArgChecker.isTrue(ratePaymentPeriod.getAccrualPeriods().size() == 1, "rate payment should not be compounding");
      RateAccrualPeriod rateAccrualPeriod = ratePaymentPeriod.getAccrualPeriods().get(0);
      double factor = rateAccrualPeriod.getYearFraction() *
          ((FixedRateComputation) rateAccrualPeriod.getRateComputation()).getRate();
      CurrencyAmount notional = ratePaymentPeriod.getNotionalAmount().multipliedBy(factor);
      LocalDate endDate = rateAccrualPeriod.getEndDate();
      LocalDate paymentDate = ratePaymentPeriod.getPaymentDate();
      double payDateRatio = 
          multicurve.discountFactor(ccy, paymentDate) / multicurve.discountFactor(ccy, endDate);
      NotionalExchange pay = NotionalExchange.of(notional.multipliedBy(payDateRatio), endDate);
      paymentEvents.add(pay);
    }
    ResolvedSwapLeg leg = ResolvedSwapLeg.builder()
        .paymentEvents(paymentEvents)
        .payReceive(PayReceive.RECEIVE)
        .type(SwapLegType.OTHER)
        .build();
    return leg;
  }
  
  /**
   * Computes cash flow equivalent of overnight leg.
   * <p>
   * The return type is {@code ResolvedSwapLeg} in which individual payments are
   * represented in terms of {@code NotionalExchange}.
   * 
   * @param onLeg  the overnight leg
   * @param multicurve  the multi-curve rates provider
   * @return the cash flow equivalent
   */
  public static ResolvedSwapLeg cashFlowEquivalentOnLeg(
      ResolvedSwapLeg onLeg, 
      RatesProvider multicurve) {
    
    Currency ccy = onLeg.getCurrency();
    ArgChecker.isTrue(onLeg.getType().equals(SwapLegType.OVERNIGHT), "Leg type should be OVERNIGHT");
    ArgChecker.isTrue(onLeg.getPaymentEvents().isEmpty(), "PaymentEvent should be empty");
    List<NotionalExchange> paymentEvents = new ArrayList<NotionalExchange>();
    for (SwapPaymentPeriod paymentPeriod : onLeg.getPaymentPeriods()) {
      ArgChecker.isTrue(paymentPeriod instanceof RatePaymentPeriod, 
          "rate payment should be RatePaymentPeriod");
      RatePaymentPeriod ratePaymentPeriod = (RatePaymentPeriod) paymentPeriod;
      ArgChecker.isTrue(ratePaymentPeriod.getAccrualPeriods().size() == 1, 
          "rate payment should not be compounding");
      RateAccrualPeriod rateAccrualPeriod = ratePaymentPeriod.getAccrualPeriods().get(0);
      CurrencyAmount notional = ratePaymentPeriod.getNotionalAmount();
      RateComputation rateComputation = rateAccrualPeriod.getRateComputation();
      ArgChecker.isTrue(rateComputation instanceof OvernightCompoundedRateComputation, 
          "RateComputation should be of type OvernightCompoundedRateComputation");
      LocalDate startDate = rateAccrualPeriod.getStartDate();
      LocalDate endDate = rateAccrualPeriod.getEndDate();
      LocalDate paymentDate = ratePaymentPeriod.getPaymentDate();
      double payDateRatio =
          multicurve.discountFactor(ccy, paymentDate) / multicurve.discountFactor(ccy, endDate);
      NotionalExchange payStart = NotionalExchange.of(notional.multipliedBy(payDateRatio), startDate);
      double spread = rateAccrualPeriod.getSpread();
      double af = rateAccrualPeriod.getYearFraction();
      NotionalExchange payEnd =
          NotionalExchange.of(notional.multipliedBy(-payDateRatio * (1 - spread * af)), endDate);
      paymentEvents.add(payStart);
      paymentEvents.add(payEnd);
    }
    ResolvedSwapLeg leg = ResolvedSwapLeg.builder()
        .paymentEvents(paymentEvents)
        .payReceive(PayReceive.RECEIVE)
        .type(SwapLegType.OTHER)
        .build();
    return leg;
  }
  
  /**
   * Computes cash flow equivalent of Ibor leg.
   * <p>
   * The return type is {@code ResolvedSwapLeg} in which individual payments are
   * represented in terms of {@code NotionalExchange}.
   * 
   * @param iborLeg  the Ibor leg
   * @param ratesProvider  the rates provider
   * @return the cash flow equivalent
   */
  public static ResolvedSwapLeg cashFlowEquivalentIborLeg(ResolvedSwapLeg iborLeg, RatesProvider ratesProvider) {
    ArgChecker.isTrue(iborLeg.getType().equals(SwapLegType.IBOR), "Leg type should be IBOR");
    ArgChecker.isTrue(iborLeg.getPaymentEvents().isEmpty(), "PaymentEvent should be empty");
    List<NotionalExchange> paymentEvents = new ArrayList<NotionalExchange>();
    for (SwapPaymentPeriod paymentPeriod : iborLeg.getPaymentPeriods()) {
      ArgChecker.isTrue(paymentPeriod instanceof RatePaymentPeriod, "rate payment should be RatePaymentPeriod");
      RatePaymentPeriod ratePaymentPeriod = (RatePaymentPeriod) paymentPeriod;
      ArgChecker.isTrue(ratePaymentPeriod.getAccrualPeriods().size() == 1, "rate payment should not be compounding");
      RateAccrualPeriod rateAccrualPeriod = ratePaymentPeriod.getAccrualPeriods().get(0);
      CurrencyAmount notional = ratePaymentPeriod.getNotionalAmount();
      LocalDate paymentDate = ratePaymentPeriod.getPaymentDate();
      RateComputation rateComputation = rateAccrualPeriod.getRateComputation();
      ArgChecker.isTrue(rateComputation instanceof IborRateComputation);
      IborIndexObservation obs = ((IborRateComputation) rateComputation).getObservation();
      IborIndex index = obs.getIndex();
      LocalDate fixingStartDate = obs.getEffectiveDate();
      double fixingYearFraction = obs.getYearFraction();
      double beta = (1d + fixingYearFraction * ratesProvider.iborIndexRates(index).rate(obs)) *
          ratesProvider.discountFactor(paymentPeriod.getCurrency(), paymentPeriod.getPaymentDate()) /
          ratesProvider.discountFactor(paymentPeriod.getCurrency(), fixingStartDate);
      double ycRatio = rateAccrualPeriod.getYearFraction() / fixingYearFraction;
      NotionalExchange payStart = NotionalExchange.of(notional.multipliedBy(beta * ycRatio), fixingStartDate);
      NotionalExchange payEnd = NotionalExchange.of(notional.multipliedBy(-ycRatio), paymentDate);
      paymentEvents.add(payStart);
      paymentEvents.add(payEnd);
    }
    ResolvedSwapLeg leg = ResolvedSwapLeg.builder()
        .paymentEvents(paymentEvents)
        .payReceive(PayReceive.RECEIVE)
        .type(SwapLegType.OTHER)
        .build();
    return leg;
  }

  /**
   * Generate a new list with the dates sorted and the amounts of elements with same payment date compressed.
   * <p>
   * The original list is unchanged.
   * 
   * @param input  the starting list
   * @return the normalized list
   */
  public static List<NotionalExchange> normalize(List<NotionalExchange> input) {
    List<NotionalExchange> sorted = new ArrayList<>(input); // copy for sorting
    Collections.sort(sorted, (a, b) -> (int) (a.getPaymentDate().toEpochDay() - b.getPaymentDate().toEpochDay()));

    NotionalExchange previous = sorted.get(0);
    for (int i = 1; i < sorted.size(); i++) {
      NotionalExchange current = sorted.get(i);
      if (current.getPaymentDate().equals(previous.getPaymentDate()) &&
          current.getCurrency().equals(previous.getCurrency())) {
        current = NotionalExchange.of(
            CurrencyAmount.of(current.getCurrency(),
                current.getPaymentAmount().getAmount() + previous.getPaymentAmount().getAmount()),
            current.getPaymentDate());
        sorted.set(i - 1, current);
        sorted.remove(i);
        i--;
      }
      previous = current;
    }
    return sorted;
  }
 
}
