/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.pricer.swap;

import java.time.LocalDate;
import java.util.List;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.market.explain.ExplainMapBuilder;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.impl.swap.DiscountingRatePaymentPeriodPricer;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.swap.SwapPaymentPeriodPricer;
import com.opengamma.strata.product.rate.OvernightCompoundedRateComputation;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RatePaymentPeriod;

import marc.henrard.risq.model.hullwhite.HullWhiteOneFactorPiecewiseConstantFormulas;

/**
 * Pricer for rate payment periods where some specific periods are priced with
 * timing adjustment. The adjustment is computed with the Hull-White one-factor model.
 * <p>
 * The period types adjusted are:
 * - Payment period with one accrual period for which the rate computation is of 
 * type {@link OvernightCompoundedRateComputation} and the computation end date is
 * different from the payment date. Collateral at overnight index rate.
 * 
 * @author Marc Henrard
 *
 */
public class AdjustedDiscountingRatePaymentPeriodPricer
    implements SwapPaymentPeriodPricer<RatePaymentPeriod> {
  
  /** The pricer used for all periods except the special one described in the intro. */
  public static final DiscountingRatePaymentPeriodPricer DISCOUNTING = 
      DiscountingRatePaymentPeriodPricer.DEFAULT;
  private static final HullWhiteOneFactorPiecewiseConstantFormulas HW_FORMULAS =
      HullWhiteOneFactorPiecewiseConstantFormulas.DEFAULT;
  
  private final HullWhiteOneFactorPiecewiseConstantParametersProvider parameters;
  
  /**
   * Creates an instance with a given set of Hull-White parameters.
   * 
   * @param parameters the parameters 
   */
  public AdjustedDiscountingRatePaymentPeriodPricer(
      HullWhiteOneFactorPiecewiseConstantParametersProvider parameters) {
    
    this.parameters = parameters;
  }

  @Override
  public double presentValue(RatePaymentPeriod period, RatesProvider provider) {
    double df = provider.discountFactor(period.getCurrency(), period.getPaymentDate());
    return forecastValue(period, provider) * df;
  }

  @Override
  public double forecastValue(RatePaymentPeriod period, RatesProvider provider) {
    if (isOnTiming(period)) {
      RateAccrualPeriod accrual = period.getAccrualPeriods().get(0);
      OvernightCompoundedRateComputation onComputation =
          (OvernightCompoundedRateComputation) accrual.getRateComputation();
      LocalDate startDate = onComputation.getStartDate();
      if (startDate.isBefore(provider.getValuationDate())) { // Fixing already started: no adjustment needed
        return DISCOUNTING.forecastValue(period, provider);
        // This is a small approximation, we should sill have an adjustment if fixing has started but not finished
      }
      LocalDate endDate = onComputation.getEndDate();
      double onFwd = provider.overnightIndexRates(onComputation.getIndex())
          .periodRate(onComputation.observeOn(startDate), endDate);
      double deltaOnComputation = onComputation.getIndex().getDayCount()
          .relativeYearFraction(startDate, endDate);
      double v = parameters.relativeTime(period.getPaymentDate());
      double s = parameters.relativeTime(startDate);
      double p0s = provider.discountFactor(period.getCurrency(), startDate);
      double t = parameters.relativeTime(endDate);
      double p0t = provider.discountFactor(period.getCurrency(), endDate);
      double expGamma = HW_FORMULAS.timingAdjustmentFactor(parameters.getParameters(), s, t, v);
      double fwdAdj = onFwd + p0s / p0t * (expGamma - 1.0d) / deltaOnComputation;
      double rateGearingSpread = fwdAdj * accrual.getGearing() + accrual.getSpread();
      return rateGearingSpread * accrual.getYearFraction() * period.getNotional();
    }
    return DISCOUNTING.forecastValue(period, provider);
  }

  @Override
  public PointSensitivityBuilder presentValueSensitivity(RatePaymentPeriod period, RatesProvider provider) {
    Currency ccy = period.getCurrency();
    DiscountFactors discountFactors = provider.discountFactors(ccy);
    LocalDate paymentDate = period.getPaymentDate();
    double df = discountFactors.discountFactor(paymentDate);
    PointSensitivityBuilder forecastSensitivity = forecastValueSensitivity(period, provider);
    forecastSensitivity = forecastSensitivity.multipliedBy(df);
    double forecastValue = forecastValue(period, provider);
    PointSensitivityBuilder dscSensitivity = discountFactors.zeroRatePointSensitivity(paymentDate);
    dscSensitivity = dscSensitivity.multipliedBy(forecastValue);
    return forecastSensitivity.combinedWith(dscSensitivity);
  }

  @Override
  public PointSensitivityBuilder forecastValueSensitivity(RatePaymentPeriod period, RatesProvider provider) {
    if(isOnTiming(period)) {
      RateAccrualPeriod accrual = period.getAccrualPeriods().get(0);
      OvernightCompoundedRateComputation onComputation = 
          (OvernightCompoundedRateComputation) accrual.getRateComputation();
      LocalDate startDate = onComputation.getStartDate();
      if (startDate.isBefore(provider.getValuationDate())) { // Fixing already known
        return DISCOUNTING.forecastValueSensitivity(period, provider);
      } else {
        LocalDate endDate = onComputation.getEndDate();
        double deltaOnComputation = onComputation.getIndex().getDayCount()
            .relativeYearFraction(startDate, endDate);
        double v = parameters.relativeTime(period.getPaymentDate());
        double t0 = parameters.relativeTime(startDate);
        double p0t0 = provider.discountFactor(period.getCurrency(), startDate);
        double t1 = parameters.relativeTime(endDate);
        double p0t1 = provider.discountFactor(period.getCurrency(), endDate);
        double expGamma = HW_FORMULAS.timingAdjustmentFactor(parameters.getParameters(), t0, t1, v);
        double valueBar = 1.0d;
        double treatedRateBar = valueBar * accrual.getYearFraction() * period.getNotional();
        double fwdAdjBar = accrual.getGearing() * treatedRateBar;
        double p0t1Bar = -p0t0 / (p0t1 * p0t1) * (expGamma - 1.0d) / deltaOnComputation * fwdAdjBar;
        double p0t0Bar = 1.0d / p0t1 * (expGamma - 1.0d) / deltaOnComputation * fwdAdjBar;
        double onFwdBar = fwdAdjBar;
        PointSensitivityBuilder sensi =
            provider.discountFactors(period.getCurrency()).zeroRatePointSensitivity(startDate)
                .multipliedBy(p0t0Bar)
                .combinedWith(
                    provider.discountFactors(period.getCurrency()).zeroRatePointSensitivity(endDate)
                        .multipliedBy(p0t1Bar));
        sensi = sensi.combinedWith(
            provider.overnightIndexRates(onComputation.getIndex())
                .periodRatePointSensitivity(onComputation.observeOn(startDate), endDate)
                .multipliedBy(onFwdBar));
        return sensi;
      }
    }
    return DISCOUNTING.forecastValueSensitivity(period, provider);
  }

  @Override
  public MultiCurrencyAmount currencyExposure(RatePaymentPeriod period, RatesProvider provider) {
    if(isOnTiming(period)) {
      return MultiCurrencyAmount.of(period.getCurrency(), presentValue(period, provider));
    }
    return DISCOUNTING.currencyExposure(period, provider);
  }

  @Override
  public double currentCash(RatePaymentPeriod period, RatesProvider provider) {
    return DISCOUNTING.currentCash(period, provider);
  }

  @Override
  public double pvbp(RatePaymentPeriod period, RatesProvider provider) {
    return DISCOUNTING.pvbp(period, provider); // PVBP depends on spread and is not impacted by adjustment
  }

  @Override
  public PointSensitivityBuilder pvbpSensitivity(RatePaymentPeriod period, RatesProvider provider) {
    return DISCOUNTING.pvbpSensitivity(period, provider); // PVBP depends on spread and is not impacted by adjustment
  }

  @Override
  public double accruedInterest(RatePaymentPeriod period, RatesProvider provider) {
    throw new UnsupportedOperationException("Accrued interest not supported for adjusted pricer.");
  }

  @Override
  public void explainPresentValue(RatePaymentPeriod period, RatesProvider provider, ExplainMapBuilder builder) {
    throw new UnsupportedOperationException("Explain not supported for adjusted pricer.");
  }
  
  /**
   * Flags an overnight period that has to be priced with timing adjustment.
   * 
   * @param period  the period
   * @return the flag
   */
  private boolean isOnTiming(RatePaymentPeriod period) {
    List<RateAccrualPeriod> accruals = period.getAccrualPeriods();
    if(accruals.size() > 1) {
      return false;
    }
    if(!(accruals.get(0).getRateComputation() instanceof OvernightCompoundedRateComputation)) {
      return false;
    }
    OvernightCompoundedRateComputation onComputation = 
        (OvernightCompoundedRateComputation) accruals.get(0).getRateComputation();
    if(!period.getCurrency().equals(onComputation.getIndex().getCurrency())) {
      return false;
    }
    LocalDate paymentDate = period.getPaymentDate();
    if(onComputation.getEndDate().equals(paymentDate)) {
      return false;
    }
    return true;
  }

}
