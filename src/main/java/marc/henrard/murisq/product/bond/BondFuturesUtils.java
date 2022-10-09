/**
 * Copyright (C) 2022 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.bond;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.value.Rounding;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.market.ValueType;
import com.opengamma.strata.market.curve.AddFixedCurve;
import com.opengamma.strata.market.curve.ConstantCurve;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveInfoType;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.DefaultCurveMetadata;
import com.opengamma.strata.market.curve.LegalEntityGroup;
import com.opengamma.strata.market.curve.RepoGroup;
import com.opengamma.strata.pricer.DiscountFactors;
import com.opengamma.strata.pricer.SimpleDiscountFactors;
import com.opengamma.strata.pricer.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.bond.ImmutableLegalEntityDiscountingProvider;
import com.opengamma.strata.pricer.bond.LegalEntityDiscountingProvider;
import com.opengamma.strata.product.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.product.bond.ResolvedBondFuture;
import com.opengamma.strata.product.bond.ResolvedFixedCouponBond;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.SwapPaymentEvent;

import marc.henrard.murisq.basics.index.sort.SortUtils;
import marc.henrard.murisq.market.curve.description.CombinedMapCurve;
import marc.henrard.murisq.pricer.bond.DiscountingFixedCouponBondProductPricer;
import marc.henrard.murisq.pricer.swap.CashFlowEquivalentCalculator;

/**
 * Utilities related to bond futures.
 * 
 * @author Marc Henrard
 */
public class BondFuturesUtils {

  /** The bond pricer */
  public static final DiscountingFixedCouponBondProductPricer PRICER_BOND =
      DiscountingFixedCouponBondProductPricer.DEFAULT;
  /* The rounding conventions */
  public static final Rounding ROUNDING_EUREX_DE = Rounding.ofDecimalPlaces(6);
  public static final Rounding ROUNDING_ICE_UK = Rounding.ofDecimalPlaces(7);
  public static final Rounding ROUNDING_CME_US = Rounding.ofDecimalPlaces(4);

  // Private constructor
  private BondFuturesUtils() {
  }

  /**
   * Returns the bond futures conversion factor for a given Germany bond.
   * 
   * @param bond  the bond
   * @param deliveryDate  the delivery date
   * @param notionalCoupon  the notional coupon for the futures; typically 6%
   * @return the factor
   */
  public static double conversionFactorEurexDE(
      ResolvedFixedCouponBond bond,
      LocalDate deliveryDate,
      double notionalCoupon) {

    double dirtyPrice = PRICER_BOND.dirtyPriceFromYield(bond, deliveryDate, notionalCoupon);
    double cleanPrice = PRICER_BOND.cleanPriceFromDirtyPrice(bond, deliveryDate, dirtyPrice);
    double factorRaw = cleanPrice;
    return ROUNDING_EUREX_DE.round(factorRaw);
  }

  /**
   * Returns the bond futures conversion factor for a given U.K. bond.
   * 
   * @param bond  the bond
   * @param deliveryDate  the delivery date
   * @param notionalCoupon  the notional coupon for the futures; typically 4%
   * @return the factor
   */
  public static double priceFactorIceUK(
      ResolvedFixedCouponBond bond,
      LocalDate deliveryDate,
      double notionalCoupon) {

    double dirtyPrice = PRICER_BOND.dirtyPriceFromYield(bond, deliveryDate, notionalCoupon);
    double cleanPrice = PRICER_BOND.cleanPriceFromDirtyPrice(bond, deliveryDate, dirtyPrice);
    double factorRaw = cleanPrice;
    return ROUNDING_ICE_UK.round(factorRaw);
  }
  
  /**
   * TU, 3YR, FV
   * With n the number of whole years from the first day of the delivery month to the maturity (or call) date of the bond or note.
   * The number of whole months between n-year after delivery and the maturity.
   * 
   * @param bond  the bond
   * @param deliveryDate  the delivery date
   * @param notionalCoupon  the notional coupon for the futures; typically 6%
   * @return the factor
   */
  public static double conversionFactorCmeUsShort(
      ResolvedFixedCouponBond bond,
      LocalDate deliveryDate,
      double notionalCoupon) {

    double factorOnPeriod = 1.0d / (1.0d + 0.5 * notionalCoupon);
    double coupon = bond.getFixedRate();
    LocalDate maturity = bond.getUnadjustedEndDate();
    long n = ChronoUnit.YEARS.between(deliveryDate, maturity);
    LocalDate referenceDate = deliveryDate.plusYears(n);
    long z = ChronoUnit.MONTHS.between(referenceDate, maturity);
    long v = (z < 7) ? z : z - 6;
    double a = Math.pow(factorOnPeriod, v / 6.0d);
    double b = coupon / 2.0d * (6.0d - v) / 6.0d;
    double c = (z < 7) ? Math.pow(factorOnPeriod, 2 * n) : Math.pow(factorOnPeriod, 2 * n + 1);
    double d = coupon / notionalCoupon * (1.0d - c);
    double factorRaw = a * (0.5 * coupon + c + d) - b;
    return ROUNDING_CME_US.round(factorRaw);
  }
  
  /**
   * US, TY
   * With n the number of whole years from the first day of the delivery month to the maturity (or call) date of the bond or note.
   * The number of whole months between n-year after delivery and the maturity rounded down to the nearest quarter.
   * 
   * @param bond  the bond
   * @param deliveryDate  the delivery date
   * @param notionalCoupon  the notional coupon for the futures; typically 6%
   * @return the factor
   */
  public static double conversionFactorCmeUsLong(
      ResolvedFixedCouponBond bond,
      LocalDate deliveryDate,
      double notionalCoupon) {

    double factorOnPeriod = 1.0d / (1.0d + 0.5 * notionalCoupon);
    double coupon = bond.getFixedRate();
    LocalDate maturity = bond.getUnadjustedEndDate();
    long n = ChronoUnit.YEARS.between(deliveryDate, maturity);
    LocalDate referenceDate = deliveryDate.plusYears(n);
    long z = (long) (Math.floor(ChronoUnit.MONTHS.between(referenceDate, maturity) / 3.0d) * 3l);
    long v = (z < 7) ? z : 3;
    double a = Math.pow(factorOnPeriod, v / 6.0d);
    double b = coupon / 2.0d * (6.0d - v) / 6.0d;
    double c = (z < 7) ? Math.pow(factorOnPeriod, 2 * n) : Math.pow(factorOnPeriod, 2 * n + 1);
    double d = coupon / notionalCoupon * (1.0d - c);
    double factorRaw = a * (0.5 * coupon + c + d) - b;
    return ROUNDING_CME_US.round(factorRaw);
  }

  /**
   * For a given futures and a given set of curves, find the smallest spread for which the CTD bond changes.
   * The spread is a parallel move of all the curves (repo and discounting). 
   * 
   * @param futures  the bond futures
   * @param provider  the discounting and repo curves provider
   * @param maxSpread  the maximum spread; will be check on both sides (above and below current curve)
   * @param spreadPrecision  the precision on the spread indicating when the search can stop
   * @return  the pair with the spread found if any and the flag indicating if a spread was found
   */
  public static Pair<Double, Boolean> findCheapestToDeliverChangeSpread(
      ResolvedBondFuture futures,
      ImmutableLegalEntityDiscountingProvider provider,
      double maxSpread,
      double spreadPrecision) {

    // Spread above current curve
    Pair<Double, Boolean> spreadPlus =
        findCheapestToDeliverChangeSpreadOneSided(futures, provider, 0.0d, maxSpread, spreadPrecision, true);
    // Spread below current curve
    Pair<Double, Boolean> spreadMinus =
        findCheapestToDeliverChangeSpreadOneSided(futures, provider, -maxSpread, 0.0d, spreadPrecision, false);
    if (spreadPlus.getSecond() && spreadMinus.getSecond()) { // found on both sides
      if (spreadPlus.getFirst() < -spreadMinus.getFirst()) {
        return spreadPlus;
      } else {
        return spreadMinus;
      }
    }
    if (spreadPlus.getSecond()) {
      return spreadPlus;
    }
    return spreadMinus;
  }

  /**
   * For a given futures and a given set of curves, find the smallest spread for which the CTD bond changes, 
   * taking into account the WI bonds.
   * The spread is a parallel move of all the curves (repo and discounting).
   * For each curve shift, the WI bond with forward coupon for that new curve is included in the delivery basket. This 
   * means that different curves will produce different baskets (same maturity but different coupon for the last bond).
   * 
   * @param futures  the bond futures
   * @param provider  the discounting and repo curves provider
   * @param maxSpread  the maximum spread; will be check on both sides (above and below current curve)
   * @param spreadPrecision  the precision on the spread indicating when the search can stop
   * @param conversionFactorFunction  the function to compute the conversion factor associated to a bond
   * @param bondWiTemplate  the when-issued bond template with a unit coupon (i.e. 100%)
   * @param wiCouponSetDate  the date when the when-issued bond coupon is set
   * @param couponStep  the minimal step between two coupons at issuance
   * @return  the pair with the spread found if any and the flag indicating if a spread was found
   */
  public static Pair<Double, Boolean> findCheapestToDeliverChangeSpreadWi(
      ResolvedBondFuture futures,
      ImmutableLegalEntityDiscountingProvider provider,
      double maxSpread,
      double spreadPrecision,
      Function<Pair<ResolvedFixedCouponBond, LocalDate>, Double> conversionFactorFunction,
      ResolvedFixedCouponBond bondWiTemplate,
      LocalDate wiCouponSetDate,
      double couponStep) {

    // Spread above current curve
    Pair<Double, Boolean> spreadPlus =
        findCheapestToDeliverChangeSpreadWiOneSided(futures, provider, 0.0d, maxSpread, spreadPrecision, true, 
            conversionFactorFunction, bondWiTemplate, wiCouponSetDate, couponStep);
    // Spread below current curve
    Pair<Double, Boolean> spreadMinus =
        findCheapestToDeliverChangeSpreadWiOneSided(futures, provider, -maxSpread, 0.0d, spreadPrecision, false, 
            conversionFactorFunction, bondWiTemplate, wiCouponSetDate, couponStep);
    if (spreadPlus.getSecond() && spreadMinus.getSecond()) { // found on both sides
      if (spreadPlus.getFirst() < -spreadMinus.getFirst()) {
        return spreadPlus;
      } else {
        return spreadMinus;
      }
    }
    if (spreadPlus.getSecond()) {
      return spreadPlus;
    }
    return spreadMinus;
  }

  /**
   * For a given futures and a given set of curves, find the smallest spread for which the CTD bond changes.
   * The spread is a parallel move of all the curves (repo and discounting).
   * Change on one side of the current curve.
   * 
   * @param futures  the bond futures
   * @param provider  the discounting and repo curves provider
   * @param minSpread  the minimum spread
   * @param maxSpread  the maximum spread
   * @param spreadPrecision  the precision on the spread indicating when the search can stop
   * @param lowPartPriority  flag indicating if the low part (or the high part) should be used in priority 
   *     when searching where the CTD changes
   * @return  the pair with the spread found if any and the flag indicating if a spread was found
   */
  public static Pair<Double, Boolean> findCheapestToDeliverChangeSpreadOneSided(
      ResolvedBondFuture futures,
      ImmutableLegalEntityDiscountingProvider provider,
      double minSpread,
      double maxSpread,
      double spreadPrecision,
      boolean lowPartPriority) {

    boolean found = false;
    double bestSpread = 0.0d;
    double lowSpread = minSpread;
    double highSpread = maxSpread;
    LegalEntityDiscountingProvider providerLow = providerSpread(provider, lowSpread);
    Pair<Double, Integer> priceCtdLow = priceCtd(futures, providerLow);
    LegalEntityDiscountingProvider providerHigh = providerSpread(provider, highSpread);
    Pair<Double, Integer> priceCtdHigh = priceCtd(futures, providerHigh);
    if (priceCtdLow.getSecond() != priceCtdHigh.getSecond()) {
      found = true;
      while ((highSpread - lowSpread) > spreadPrecision) {
        double testSpread = 0.5 * (lowSpread + highSpread);
        LegalEntityDiscountingProvider providerTest = providerSpread(provider, testSpread);
        Pair<Double, Integer> priceCtdTest = priceCtd(futures, providerTest);
        if (lowPartPriority) {
          if (priceCtdTest.getSecond() != priceCtdLow.getSecond()) { // Change in the low part
            highSpread = testSpread;
            priceCtdHigh = priceCtdTest;
          } else {
            lowSpread = testSpread;
            priceCtdLow = priceCtdTest;
          }
        } else {
          if (priceCtdTest.getSecond() != priceCtdHigh.getSecond()) { // Change in the high part
            lowSpread = testSpread;
            priceCtdLow = priceCtdTest;
          } else {
            highSpread = testSpread;
            priceCtdHigh = priceCtdTest;
          }
        }
      }
      bestSpread = 0.5 * (lowSpread + highSpread);
    }
    return Pair.of(bestSpread, found);
  }

  /**
   * For a given futures and a given set of curves, find the smallest spread for which the CTD bond changes, 
   * taking into account the WI bonds. Change on one side of the current curve.
   * The spread is a parallel move of all the curves (repo and discounting).
   * For each curve shift, the WI bond with forward coupon for that new curve is included in the delivery basket. This 
   * means that different curves will produce different baskets (same maturity but different coupon for the last bond).
   * 
   * @param futures  the bond futures
   * @param provider  the discounting and repo curves provider
   * @param minSpread  the minimum spread
   * @param maxSpread  the maximum spread
   * @param spreadPrecision  the precision on the spread indicating when the search can stop
   * @param lowPartPriority  flag indicating if the low part (or the high part) should be used in priority 
   *     when searching where the CTD changes
   * @param conversionFactorFunction  the function to compute the conversion factor associated to a bond
   * @param bondWiTemplate  the when-issued bond template with a unit coupon (i.e. 100%)
   * @param wiCouponSetDate  the date when the when-issued bond coupon is set
   * @param couponStep  the minimal step between two coupons at issuance
   * @return  the pair with the spread found if any and the flag indicating if a spread was found
   */
  public static Pair<Double, Boolean> findCheapestToDeliverChangeSpreadWiOneSided(
      ResolvedBondFuture futures,
      ImmutableLegalEntityDiscountingProvider provider,
      double minSpread,
      double maxSpread,
      double spreadPrecision,
      boolean lowPartPriority,
      Function<Pair<ResolvedFixedCouponBond, LocalDate>, Double> conversionFactorFunction,
      ResolvedFixedCouponBond bondWiTemplate,
      LocalDate wiCouponSetDate,
      double couponStep) {

    boolean found = false;
    double bestSpread = 0.0d;
    double lowSpread = minSpread;
    double highSpread = maxSpread;
    LegalEntityDiscountingProvider providerLow = providerSpread(provider, lowSpread);
    ResolvedBondFuture futuresLow = 
        addForwardWi(futures, providerLow, conversionFactorFunction, bondWiTemplate, wiCouponSetDate, couponStep);
    Pair<Double, Integer> priceCtdLow = priceCtd(futuresLow, providerLow);
    LegalEntityDiscountingProvider providerHigh = providerSpread(provider, highSpread);
    ResolvedBondFuture futuresHigh = 
        addForwardWi(futures, providerHigh, conversionFactorFunction, bondWiTemplate, wiCouponSetDate, couponStep);
    Pair<Double, Integer> priceCtdHigh = priceCtd(futuresHigh, providerHigh);
    if (priceCtdLow.getSecond() != priceCtdHigh.getSecond()) {
      found = true;
      while ((highSpread - lowSpread) > spreadPrecision) {
        double testSpread = 0.5 * (lowSpread + highSpread);
        LegalEntityDiscountingProvider providerTest = providerSpread(provider, testSpread);
        ResolvedBondFuture futuresTest = 
            addForwardWi(futures, providerTest, conversionFactorFunction, bondWiTemplate, wiCouponSetDate, couponStep);
        Pair<Double, Integer> priceCtdTest = priceCtd(futuresTest, providerTest);
        if (lowPartPriority) {
          if (priceCtdTest.getSecond() != priceCtdLow.getSecond()) { // Change in the low part
            highSpread = testSpread;
            priceCtdHigh = priceCtdTest;
          } else {
            lowSpread = testSpread;
            priceCtdLow = priceCtdTest;
          }
        } else {
          if (priceCtdTest.getSecond() != priceCtdHigh.getSecond()) { // Change in the high part
            lowSpread = testSpread;
            priceCtdLow = priceCtdTest;
          } else {
            highSpread = testSpread;
            priceCtdHigh = priceCtdTest;
          }
        }
      }
      bestSpread = 0.5 * (lowSpread + highSpread);
    }
    return Pair.of(bestSpread, found);
  }

  /**
   * Creates a provider with all the curves shifted by a common flat spread.
   * All the discounting factors in the provider must be of type {@link ZeroRateDiscountFactors} 
   * or {@link SimpleDiscountFactors}.
   * 
   * @param provider  the starting provider
   * @param spread  the spread
   * @return the provider with spread
   */
  public static ImmutableLegalEntityDiscountingProvider providerSpread(
      ImmutableLegalEntityDiscountingProvider provider,
      double spread) {

    Map<Pair<RepoGroup, Currency>, DiscountFactors> repoCurves = provider.getRepoCurves();
    Map<Pair<RepoGroup, Currency>, DiscountFactors> repoCurvesSpread =
        new HashMap<Pair<RepoGroup, Currency>, DiscountFactors>();
    CurveMetadata spreadZeroMetadata = DefaultCurveMetadata.builder()
        .curveName("spread")
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.ZERO_RATE)
        .dayCount(DayCounts.ACT_365F) // Not used as spread is constant
        .build();
    DefaultCurveMetadata combinedDiscountFactorMetadata = DefaultCurveMetadata.builder()
        .curveName("combined")
        .xValueType(ValueType.YEAR_FRACTION)
        .yValueType(ValueType.DISCOUNT_FACTOR)
        .build();
    Function<Triple<Double, Double, Double>, Double> mappingDfZero =
        (t) -> t.getSecond() * Math.exp(-t.getFirst() * t.getThird());
    for (Entry<Pair<RepoGroup, Currency>, DiscountFactors> entry : repoCurves.entrySet()) {
      Curve spreadZeroCurve = ConstantCurve.of(spreadZeroMetadata, spread);
      if (entry.getValue() instanceof ZeroRateDiscountFactors) {
        ZeroRateDiscountFactors df = (ZeroRateDiscountFactors) entry.getValue();
        Curve curve = df.getCurve();
        Curve curveWithSpread = AddFixedCurve.of(curve, spreadZeroCurve);
        repoCurvesSpread.put(entry.getKey(),
            ZeroRateDiscountFactors.of(df.getCurrency(), df.getValuationDate(), curveWithSpread));
      } else {
        if (entry.getValue() instanceof SimpleDiscountFactors) {
          SimpleDiscountFactors df = (SimpleDiscountFactors) entry.getValue();
          Curve curve = df.getCurve();
          Curve curveWithSpread = CombinedMapCurve.of(curve, spreadZeroCurve, mappingDfZero,
              combinedDiscountFactorMetadata.toBuilder().dayCount(curve.getMetadata().getInfo(CurveInfoType.DAY_COUNT))
                  .build());
          repoCurvesSpread.put(entry.getKey(),
              SimpleDiscountFactors.of(df.getCurrency(), df.getValuationDate(), curveWithSpread));
        } else {
          throw new IllegalArgumentException(
              "DiscountFactors must be of type ZeroRateDiscountFactors or SimpleDiscountFactors");
        }
      }
    }
    Map<Pair<LegalEntityGroup, Currency>, DiscountFactors> issuerCurves = provider.getIssuerCurves();
    Map<Pair<LegalEntityGroup, Currency>, DiscountFactors> issuerCurvesSpread =
        new HashMap<Pair<LegalEntityGroup, Currency>, DiscountFactors>();
    for (Entry<Pair<LegalEntityGroup, Currency>, DiscountFactors> entry : issuerCurves.entrySet()) {
      Curve spreadZeroCurve = ConstantCurve.of(spreadZeroMetadata, spread);
      if (entry.getValue() instanceof ZeroRateDiscountFactors) {
        ZeroRateDiscountFactors df = (ZeroRateDiscountFactors) entry.getValue();
        Curve curve = df.getCurve();
        Curve curveWithSpread = AddFixedCurve.of(curve, spreadZeroCurve);
        issuerCurvesSpread.put(entry.getKey(),
            ZeroRateDiscountFactors.of(df.getCurrency(), df.getValuationDate(), curveWithSpread));
      } else {
        if (entry.getValue() instanceof SimpleDiscountFactors) {
          SimpleDiscountFactors df = (SimpleDiscountFactors) entry.getValue();
          Curve curve = df.getCurve();
          Curve curveWithSpread = CombinedMapCurve.of(curve, spreadZeroCurve, mappingDfZero,
              combinedDiscountFactorMetadata.toBuilder().dayCount(curve.getMetadata().getInfo(CurveInfoType.DAY_COUNT))
                  .build());
          issuerCurvesSpread.put(entry.getKey(),
              SimpleDiscountFactors.of(df.getCurrency(), df.getValuationDate(), curveWithSpread));
        } else {
          throw new IllegalArgumentException(
              "DiscountFactors must be of type ZeroRateDiscountFactors or SimpleDiscountFactors");
        }
      }
    }
    return provider.toBuilder()
        .repoCurves(repoCurvesSpread)
        .issuerCurves(issuerCurvesSpread).build();
  }

  /**
   * Returns the price in the CTD approach and the index of the CTD bond.
   * 
   * @param future  the futures
   * @param discountingProvider  the discounting curves
   * @return  the price with the CTD and its index in the delivery basket
   */
  public static Pair<Double, Integer> priceCtd(
      ResolvedBondFuture futures,
      LegalEntityDiscountingProvider discountingProvider) {

    ImmutableList<ResolvedFixedCouponBond> basket = futures.getDeliveryBasket();
    int size = basket.size();
    double[] priceBonds = new double[size];
    for (int i = 0; i < size; ++i) {
      ResolvedFixedCouponBond bond = basket.get(i);
      double dirtyPrice = PRICER_BOND.dirtyPriceFromCurves(bond, discountingProvider, futures.getLastDeliveryDate());
      priceBonds[i] = PRICER_BOND.cleanPriceFromDirtyPrice(
          bond, futures.getLastDeliveryDate(), dirtyPrice) / futures.getConversionFactors().get(i);
    }
    return SortUtils.minIndex(priceBonds);
  }

  /**
   * Create a new bond based on a template by changing only the coupon rate.
   * 
   * @param template  the bond template
   * @param coupon  the new coupon value
   * @return  the bond
   */
  public static ResolvedFixedCouponBond withCoupon(ResolvedFixedCouponBond template, double coupon) {
    ImmutableList<FixedCouponBondPaymentPeriod> paymentsTemplate = template.getPeriodicPayments();
    List<FixedCouponBondPaymentPeriod> paymentsCoupon = new ArrayList<>();
    for (FixedCouponBondPaymentPeriod p : paymentsTemplate) {
      paymentsCoupon.add(p.toBuilder().fixedRate(coupon).build());
    }
    return template.toBuilder().periodicPayments(paymentsCoupon).fixedRate(coupon).build();
  }

  /**
   * The function to compute the bond coupon rate (without rounding).
   * 
   * @param bondWiUnit  the bond description with a coupon of 1
   * @param deliveryDate  the WI delivery date at which par is measured
   * @return  the bond coupon for which it would trade at a (dirty) par price at delivery date
   */
  public static Function<double[], Double> pathDependencyYield(
      ResolvedFixedCouponBond bondWiUnit,
      LocalDate deliveryDate) {

    ImmutableList<SwapPaymentEvent> cfeBondWi1 =
        CashFlowEquivalentCalculator.cashFlowEquivalent(bondWiUnit, deliveryDate).getPaymentEvents();
    int nbCfsWi = cfeBondWi1.size(); // must be same size as df!
    double[] accrualFactorsWi = new double[nbCfsWi - 1]; // Final notional not a coupon
    for (int i = 0; i < nbCfsWi - 1; i++) {
      accrualFactorsWi[i] = ((NotionalExchange) cfeBondWi1.get(i)).getPayment().getAmount() /
          ((NotionalExchange) cfeBondWi1.get(nbCfsWi - 1)).getPayment().getAmount(); // Final notional
    }
    Function<double[], Double> functionCoupon =
        (df) -> {
          double pvbp = 0.0;
          for (int i = 0; i < nbCfsWi - 1; i++) {
            pvbp += df[i] * accrualFactorsWi[i];
          }
          double numerator = 1.0 - df[nbCfsWi - 1];
          return numerator / pvbp;
        };
    return functionCoupon;
  }

  /**
   * Create a new bond futures where an extra bond has been added to the delivery basket. The new bond correspond 
   * to a when-issued bond with a coupon set at the level of the forward.
   * 
   * @param futures  the bond futures
   * @param provider  the provider of bond discounting curves
   * @param conversionFactorFunction  the function to compute the conversion factor associated to a bond
   * @param bondWiTemplate  the when-issued bond template with a unit coupon (i.e. 100%)
   * @param wiCouponSetDate  the date when the when-issued bond coupon is set
   * @return the new bond futures
   */
  public static ResolvedBondFuture addForwardWi(
      ResolvedBondFuture futures,
      LegalEntityDiscountingProvider provider,
      Function<Pair<ResolvedFixedCouponBond, LocalDate>, Double> conversionFactorFunction,
      ResolvedFixedCouponBond bondWiTemplate,
      LocalDate wiCouponSetDate,
      double couponStep) {

    LocalDate wiFirstSettleDate = bondWiTemplate.getStartDate();
    int nbPeriodsBond = bondWiTemplate.getPeriodicPayments().size();
    double[] forwardDiscountFactorsWiBond = new double[nbPeriodsBond + 1];
    double dfWiSettleDate = provider.repoCurveDiscountFactors(bondWiTemplate.getSecurityId(),
        bondWiTemplate.getLegalEntityId(), bondWiTemplate.getCurrency()).discountFactor(wiFirstSettleDate);
    for (int i = 0; i < nbPeriodsBond; i++) {
      forwardDiscountFactorsWiBond[i] =
          provider.issuerCurveDiscountFactors(bondWiTemplate.getLegalEntityId(), bondWiTemplate.getCurrency())
              .discountFactor(bondWiTemplate.getPeriodicPayments().get(i).getPaymentDate()) / dfWiSettleDate;
    }
    forwardDiscountFactorsWiBond[nbPeriodsBond] =
        provider.issuerCurveDiscountFactors(bondWiTemplate.getLegalEntityId(), bondWiTemplate.getCurrency())
            .discountFactor(bondWiTemplate.getNominalPayment().getDate()) / dfWiSettleDate;
    Function<double[], Double> yieldFunction = pathDependencyYield(bondWiTemplate, wiFirstSettleDate);
    double currentParYield = yieldFunction.apply(forwardDiscountFactorsWiBond);
    double couponWi = Math.round(currentParYield / couponStep) * couponStep;
    ResolvedFixedCouponBond bondWi = withCoupon(bondWiTemplate, couponWi);
    List<ResolvedFixedCouponBond> basketWithWi = new ArrayList<>(futures.getDeliveryBasket());
    basketWithWi.add(bondWi);
    List<Double> conversionFactorsWithWi = new ArrayList<>(futures.getConversionFactors());
    conversionFactorsWithWi.add(conversionFactorFunction.apply(Pair.of(bondWi, futures.getLastDeliveryDate())));
    return futures.toBuilder()
        .deliveryBasket(basketWithWi)
        .conversionFactors(conversionFactorsWithWi).build();
  }

}
