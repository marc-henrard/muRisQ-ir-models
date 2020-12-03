/**
 * Copyright (C) 2017 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.fra;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.math.impl.integration.IntegratorRepeated2D;
import com.opengamma.strata.math.impl.integration.RungeKuttaIntegrator1D;
import com.opengamma.strata.pricer.fra.DiscountingFraProductPricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.fra.ResolvedFra;
import com.opengamma.strata.product.fra.type.FraConventions;
import com.opengamma.strata.product.rate.IborRateComputation;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.dataset.RationalTwoFactorParameters20151120DataSet;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorGenericParameters;
import marc.henrard.murisq.model.rationalmulticurve.RationalTwoFactorHWShapePlusCstParameters;
import marc.henrard.murisq.pricer.fra.RationalFraProductNumericalIntegrationPricer;
import marc.henrard.murisq.pricer.fra.RationalFraProductNumericalIntegrationPricer.PriceIntegrant2;

/**
 * Tests {@link RationalFraProductNumericalIntegrationPricer}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalFraProductNumericalIntegrationPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);

  /* Load and calibrate curves */
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;

  /* Rational model data */
  private static final RationalTwoFactorGenericParameters RATIONAL_2F =
      RationalTwoFactorParameters20151120DataSet.RATIONAL_2F;
  private static final RationalTwoFactorHWShapePlusCstParameters RATIONAL_HW_CST =
      RationalTwoFactorParameters20151120DataSet.rational2FactorHwCst(
          MULTICURVE_EUR.discountFactors(EUR), RATIONAL_2F.getValuationTime(), RATIONAL_2F.getValuationZone());
  private static final RationalTwoFactorHWShapePlusCstParameters PARAM_0 =
      RationalTwoFactorHWShapePlusCstParameters
          .of(DoubleArray.of(0.50, 0.50, 0.00, 0.00, 0.00, 0.01, 0.00, 0.00),
              RATIONAL_2F.getTimeMeasure(), MULTICURVE_EUR.discountFactors(EUR), RATIONAL_2F.getValuationTime(),
              RATIONAL_2F.getValuationZone());

  /* Pricer */
  private static final DiscountingFraProductPricer PRICER_FRA_PRODUCT_DSC =
      DiscountingFraProductPricer.DEFAULT;
  private static final RationalFraProductNumericalIntegrationPricer PRICER_FRA_PRODUCT_RAT =
      RationalFraProductNumericalIntegrationPricer.DEFAULT;

  /* FRA description */
  private static final double NOTIONAL = 100_000_000;
  private static final double FIXED_RATE = 0.01;

  private static final double LIMIT_INT = 12.0; // Equivalent to + infinity in normal integrals
  private static final double TOL_ABS = 1.0E-8;
  private static final double TOL_REL = 1.0E-6;

  /* Testing */
  private static final double TOLERANCE_PV = 1.0E-1;
  private static final double TOLERANCE_PV_NOCONV = 1.0E+3;
  private static final double TOLERANCE_RATE = 1.0E-8;
  private static final double TOLERANCE_RATE_NOCONV = 1.0E-4;

	/* Present value: compare rational model price with zero volatility with discounted value. */
  public void fra_rat_pv_0() {
    int nbStart = 12; // 12 quarters
    for (int loopstart = 0; loopstart < nbStart; loopstart++) {
      ResolvedFra fra = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
          Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, FIXED_RATE, REF_DATA).resolve(REF_DATA)
          .getProduct();
      CurrencyAmount pvDsc = PRICER_FRA_PRODUCT_DSC.presentValue(fra, MULTICURVE_EUR);
      CurrencyAmount pvRat0 = PRICER_FRA_PRODUCT_RAT.presentValue(fra, MULTICURVE_EUR, PARAM_0);
      assertEquals(pvDsc.getAmount(), pvRat0.getAmount(), TOLERANCE_PV);
      CurrencyAmount pvRat = PRICER_FRA_PRODUCT_RAT.presentValue(fra, MULTICURVE_EUR, RATIONAL_HW_CST);
      assertEquals(pvDsc.getAmount(), pvRat.getAmount(), TOLERANCE_PV_NOCONV);
    }
  }

  /* Par rate: compare rational model price with zero volatility with discounted value. */
  public void fra_rat_rate_0() {
    int nbStart = 12; // 12 quarters
    for (int loopstart = 0; loopstart < nbStart; loopstart++) {
      ResolvedFra fra = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
          Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, FIXED_RATE, REF_DATA).resolve(REF_DATA)
          .getProduct();
      double parRateDsc = PRICER_FRA_PRODUCT_DSC.parRate(fra, MULTICURVE_EUR);
      double parRateRat0 = PRICER_FRA_PRODUCT_RAT.parRate(fra, MULTICURVE_EUR, PARAM_0);
      assertEquals(parRateDsc, parRateRat0, TOLERANCE_RATE);
      double parRateRat = PRICER_FRA_PRODUCT_RAT.parRate(fra, MULTICURVE_EUR, RATIONAL_HW_CST);
      assertEquals(parRateDsc, parRateRat, TOLERANCE_RATE_NOCONV);
    }
  }

  /* Present value v par rate: check pv and par rate are consistent */
  public void fra_rat_pv_parrate() {
    int nbStart = 12; // 12 quarters
    for (int loopstart = 0; loopstart < nbStart; loopstart++) {
      ResolvedFra fra = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
          Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, 0.00, REF_DATA).resolve(REF_DATA)
          .getProduct();
      double parRateRat = PRICER_FRA_PRODUCT_RAT.parRate(fra, MULTICURVE_EUR, RATIONAL_HW_CST);
      ResolvedFra fra0 = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
          Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, parRateRat, REF_DATA).resolve(REF_DATA)
          .getProduct();
      CurrencyAmount pv0 = PRICER_FRA_PRODUCT_RAT.presentValue(fra0, MULTICURVE_EUR, RATIONAL_HW_CST);
      assertEquals(pv0.getAmount(), 0.0d, TOLERANCE_PV);
    }
  }

  /* Present value: compare rational model price to local implementation using the expectation computation */
  public void fra_rat_pv_formula() {
    int nbStart = 12; // 12 quarters
    for (int loopstart = 0; loopstart < nbStart; loopstart++) {
      ResolvedFra fra = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
          Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, FIXED_RATE, REF_DATA).resolve(REF_DATA)
          .getProduct();
      CurrencyAmount pvRatComputed = PRICER_FRA_PRODUCT_RAT.presentValue(fra, MULTICURVE_EUR, RATIONAL_HW_CST);
      double expectation = PRICER_FRA_PRODUCT_RAT.expectation(fra, MULTICURVE_EUR, RATIONAL_HW_CST);
      Currency ccy = fra.getCurrency();
      LocalDate u = fra.getStartDate();
      double p0u = MULTICURVE_EUR.discountFactor(ccy, u);
      double pvRatExpected = p0u - (1.0d + fra.getYearFraction() * FIXED_RATE) * expectation;
      assertEquals(pvRatExpected * NOTIONAL, pvRatComputed.getAmount(), TOLERANCE_PV);
    }
  }

  /* Present value: compare rational model expectation v local formula implementation */
  public void fra_rat_expectation() {
    int nbStart = 12; // 12 quarters
    for (int loopstart = 0; loopstart < nbStart; loopstart++) {
      ResolvedFra fra = FraConventions.of(EUR_EURIBOR_6M).createTrade(VALUATION_DATE,
          Period.ofMonths(3 * (1 + loopstart)), BuySell.BUY, NOTIONAL, FIXED_RATE, REF_DATA).resolve(REF_DATA)
          .getProduct();
      double expectationComputed = PRICER_FRA_PRODUCT_RAT.expectation(fra, MULTICURVE_EUR, RATIONAL_HW_CST);
      IborRateComputation rateComputation = (IborRateComputation) fra.getFloatingRate();
      LocalDate u = fra.getStartDate();
      LocalDate v = fra.getEndDate();
      double delta = fra.getYearFraction();
      double p0u = MULTICURVE_EUR.discountFactor(EUR, u);
      double p0v = MULTICURVE_EUR.discountFactor(EUR, v);
      double l0theta =
          MULTICURVE_EUR.iborIndexRates(rateComputation.getIndex()).rate(rateComputation.getObservation()) * p0v;
      double b0u = RATIONAL_HW_CST.b0(u);
      double b0v = RATIONAL_HW_CST.b0(v);
      double b1theta = RATIONAL_HW_CST.b1(rateComputation.getObservation());
      double b2theta = RATIONAL_HW_CST.b2(rateComputation.getObservation());
      double rho = RATIONAL_HW_CST.getCorrelation();
      ZonedDateTime fixingTime =
          rateComputation.getIndex().calculateFixingDateTime(rateComputation.getObservation().getFixingDate());
      double theta = RATIONAL_HW_CST.relativeTime(fixingTime);
      double[] c = {delta, p0u, b0u, l0theta, b1theta, b2theta, p0v, b0v}; // delta, p0u, b0u, L0theta, b1theta, b2theta, p0v, b0v
      final PriceIntegrant2 integrant =
          new PriceIntegrant2(new double[] {RATIONAL_HW_CST.a1(), RATIONAL_HW_CST.a2()}, rho, c, theta);
      final RungeKuttaIntegrator1D integrator1D =
          new RungeKuttaIntegrator1D(TOL_ABS, TOL_REL, 100);
      final IntegratorRepeated2D integrator2D = new IntegratorRepeated2D(integrator1D);
      double expectationExpected = 0.0;
      try {
        expectationExpected = 1.0 / (2.0 * Math.PI * Math.sqrt(1 - rho * rho)) *
            integrator2D.integrate(integrant, new Double[] {-LIMIT_INT, -LIMIT_INT},
                new Double[] {LIMIT_INT, LIMIT_INT});
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
      assertEquals(expectationComputed, expectationExpected, TOLERANCE_PV);
    }
  }

}
