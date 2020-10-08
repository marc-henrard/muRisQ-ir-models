/**
 * Copyright (C) 2016 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.decomposition;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.product.rate.FixedRateComputation;
import com.opengamma.strata.product.rate.IborRateComputation;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

/**
 * Calculator of decision schedule in the multi-curve framework for different instruments.
 */
public class MulticurveDecisionScheduleCalculator {
  
  /**
   * Constructs the multi-curve decision schedule for a swaption.
   * 
   * @param swaption  the swaption
   * @return the decision schedule
   */
  public static MulticurveEquivalentSchedule decisionSchedule(ResolvedSwaption swaption) {
    MulticurveEquivalent multicurveEquivalent = multicurveEquivalent(swaption.getUnderlying());
    multicurveEquivalent = multicurveEquivalent.toBuilder().decisionTime(swaption.getExpiry()).build();
    List<MulticurveEquivalent> schedules = new ArrayList<>();
    schedules.add(multicurveEquivalent);
    return MulticurveEquivalentSchedule.builder().schedules(schedules).build();
  }
  
  /**
   * Constructs the multi-curve equivalent to a swap. 
   * <p>
   * The swap must have only fixed and ibor legs. The Ibor rate payments must have only one accrual period.
   * All legs must have no payment events.
   * 
   * @param swap  the swap
   * @return the multi-curve equivalent
   */
  public static MulticurveEquivalent multicurveEquivalent(ResolvedSwap swap) {
    ImmutableList<ResolvedSwapLeg> iborLegs = swap.getLegs(SwapLegType.IBOR);
    ImmutableList<ResolvedSwapLeg> fixedLegs = swap.getLegs(SwapLegType.FIXED);
    ArgChecker.isTrue(swap.getLegs().size() == iborLegs.size() + fixedLegs.size(), 
        "All legs must be fixed or ibor");
    MulticurveEquivalent result = MulticurveEquivalent.empty();
    for(ResolvedSwapLeg iborLeg: iborLegs) {
      MulticurveEquivalent mceIbor = multicurveEquivalentIborLeg(iborLeg);
      result = result.combinedWith(mceIbor);
    }
    List<NotionalExchange> payments = new ArrayList<>();
    for(ResolvedSwapLeg fixedLeg: fixedLegs) {
      payments.addAll(paymentEquivalentFixedLeg(fixedLeg));
    }
    payments.addAll(result.getDiscountFactorPayments());
    return result.toBuilder()
        .discountFactorPayments(payments)
        .build();
  }
  
  /**
   * Constructs the list of payments equivalent to a swap fixed leg.
   * @param fixedLeg  the swap fixed leg
   * @return the payments
   */
  private static List<NotionalExchange> paymentEquivalentFixedLeg(ResolvedSwapLeg fixedLeg) {
    ArgChecker.isTrue(fixedLeg.getType().equals(SwapLegType.FIXED), "Leg type should be FIXED");
    ArgChecker.isTrue(fixedLeg.getPaymentEvents().isEmpty(), "PaymentEvent should be empty");
    ImmutableList<SwapPaymentPeriod> paymentPeriods = fixedLeg.getPaymentPeriods();
    List<NotionalExchange> payments = new ArrayList<>(paymentPeriods.size());
    for(int i=0; i< paymentPeriods.size(); i++) {
      ArgChecker.isTrue(paymentPeriods.get(i) instanceof RatePaymentPeriod, "rate payment should be RatePaymentPeriod");
      RatePaymentPeriod ratePaymentPeriod = (RatePaymentPeriod) paymentPeriods.get(i);
      ArgChecker.isTrue(ratePaymentPeriod.getAccrualPeriods().size() == 1, "rate payment should not be compounding");
      RateAccrualPeriod rateAccrualPeriod = ratePaymentPeriod.getAccrualPeriods().get(0);
      double factor = rateAccrualPeriod.getYearFraction() *
          ((FixedRateComputation) rateAccrualPeriod.getRateComputation()).getRate();
      CurrencyAmount notional = ratePaymentPeriod.getNotionalAmount().multipliedBy(factor);
      payments.add(NotionalExchange.of(notional, ratePaymentPeriod.getPaymentDate()));
    }
    return payments;
  }

  /**
   * Constructs the multi-curve equivalent to the swap Ibor leg.  
   * @param iborLeg  the Ibor leg
   * @return the multi-curve equivalent
   */
  private static MulticurveEquivalent multicurveEquivalentIborLeg(ResolvedSwapLeg iborLeg) {
    ArgChecker.isTrue(iborLeg.getType().equals(SwapLegType.IBOR), "Leg type should be IBOR");
    ArgChecker.isTrue(iborLeg.getPaymentEvents().isEmpty(), "PaymentEvent should be empty");
    ImmutableList<SwapPaymentPeriod> paymentPeriods = iborLeg.getPaymentPeriods();
    List<IborRateComputation> ibors = new ArrayList<>(paymentPeriods.size());
    List<NotionalExchange> iborPayments = new ArrayList<>(paymentPeriods.size());
    List<NotionalExchange> payments = new ArrayList<>(paymentPeriods.size());
    for (int i = 0; i < paymentPeriods.size(); i++) {
      ArgChecker.isTrue(paymentPeriods.get(i) instanceof RatePaymentPeriod,
          "rate payment should be RatePaymentPeriod");
      RatePaymentPeriod ratePaymentPeriod = (RatePaymentPeriod) paymentPeriods.get(i);
      ArgChecker.isTrue(ratePaymentPeriod.getAccrualPeriods().size() == 1,
          "rate payment should not be compounding");
      RateAccrualPeriod rateAccrualPeriod = ratePaymentPeriod.getAccrualPeriods().get(0);
      ArgChecker.isTrue(rateAccrualPeriod.getRateComputation() instanceof IborRateComputation,
          "rate observation should be Ibor");
      ibors.add((IborRateComputation) rateAccrualPeriod.getRateComputation());
      double gearing = rateAccrualPeriod.getGearing();
      iborPayments.add(NotionalExchange.of(
          ratePaymentPeriod.getNotionalAmount().multipliedBy(gearing * rateAccrualPeriod.getYearFraction()),
          ratePaymentPeriod.getPaymentDate()));
      double spread = rateAccrualPeriod.getSpread();
      if (spread != 0.0) {
        double factor = rateAccrualPeriod.getYearFraction() * spread;
        CurrencyAmount notional = ratePaymentPeriod.getNotionalAmount().multipliedBy(factor);
        payments.add(NotionalExchange.of(notional, ratePaymentPeriod.getPaymentDate()));
      }
    }
    return MulticurveEquivalent.builder()
        .decisionTime(null)
        .discountFactorPayments(payments)
        .iborPayments(iborPayments)
        .iborComputations(ibors)
        .build();
  }
  
}
