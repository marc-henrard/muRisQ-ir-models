/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.risq.model.calibration;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.AdjustablePayment;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.RollConventions;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapLegPricer;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.capfloor.IborCapFloor;
import com.opengamma.strata.product.capfloor.IborCapFloorLeg;
import com.opengamma.strata.product.capfloor.IborCapFloorTrade;
import com.opengamma.strata.product.capfloor.ResolvedIborCapFloorTrade;
import com.opengamma.strata.product.common.PayReceive;
import com.opengamma.strata.product.swap.IborRateCalculation;

import marc.henrard.risq.model.dataset.MulticurveStandardEurDataSet;
import marc.henrard.risq.model.dataset.RationalParametersDataSet;
import marc.henrard.risq.model.generic.SingleCurrencyModelParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorHWShapePlusCstParameters;
import marc.henrard.risq.model.rationalmulticurve.RationalTwoFactorHWShapePlusCstTemplate;
import marc.henrard.risq.pricer.capfloor.RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer;
import marc.henrard.risq.pricer.capfloor.SingleCurrencyModelCapFloorLegPricer;
import marc.henrard.risq.pricer.capfloor.SingleCurrencyModelCapFloorProductPricer;
import marc.henrard.risq.pricer.capfloor.SingleCurrencyModelCapFloorTradePricer;

/**
 * Tests {@link SingleCurrencyModelCapFloorLeastSquarePriceCalibrator}.
 * 
 * @author Marc Henrard
 */
@Test
public class RationalCapFloorRootPriceCalibratorTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2017, 9, 6);
  private static final LocalTime VALUATION_TIME = LocalTime.of(11, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");
  private static final BusinessDayAdjustment BUSINESS_ADJ = BusinessDayAdjustment.of(
      BusinessDayConventions.MODIFIED_FOLLOWING, EUTA);

  /* Curve and model data */
  public static final ImmutableRatesProvider MULTICURVE =
      MulticurveStandardEurDataSet.multicurve(VALUATION_DATE, REF_DATA);
  private static final RationalTwoFactorHWShapePlusCstParameters RATIONAL_2F = RationalParametersDataSet
      .twoFactorHWShaped(VALUATION_TIME, VALUATION_ZONE, MULTICURVE.discountFactors(EUR));
  
  /* Pricers */
  private static final DiscountingSwapLegPricer PRICER_SWAP_LEG = DiscountingSwapLegPricer.DEFAULT;
  private static final DiscountingPaymentPricer PRICER_PAYMENT = DiscountingPaymentPricer.DEFAULT;
  private static final RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer PRICER_CAPLET_S_EX =
      RationalTwoFactorCapletFloorletPeriodSemiExplicitPricer.DEFAULT;
  private static final SingleCurrencyModelCapFloorLegPricer PRICER_LEG_S_EX =
      new SingleCurrencyModelCapFloorLegPricer(PRICER_CAPLET_S_EX);
  private static final SingleCurrencyModelCapFloorProductPricer PRICER_PRODUCT =
      new SingleCurrencyModelCapFloorProductPricer(PRICER_LEG_S_EX, PRICER_SWAP_LEG);
  private static final SingleCurrencyModelCapFloorTradePricer PRICER_TRADE =
      new SingleCurrencyModelCapFloorTradePricer(PRICER_PRODUCT, PRICER_PAYMENT);
  
  /* Descriptions of cap/floor */
  private static final Period[] MATURITIES_PER = new Period[] {
      Period.ofYears(2), Period.ofYears(10)};
  private static final int NB_MATURITIES = MATURITIES_PER.length;
  private static final double STRIKE = 0.0050;
  private static final double NOTIONAL = 1_000_000.0d;
  
  private static final double TOL_ROOT = 1.0E-5;

  /* Calibration exact of eta and kappa to a term structure of 2 prices. */
  public void two_factor_ts() {
    LocalDate spot = EUR_EURIBOR_6M.calculateEffectiveFromFixing(VALUATION_DATE, REF_DATA);
    List<ResolvedIborCapFloorTrade> trades = new ArrayList<>();
    for (int i = 0; i < NB_MATURITIES; i++) {
      LocalDate maturity = spot.plus(MATURITIES_PER[i]);
      IborCapFloor cap = cap(spot, maturity, STRIKE);
      MultiCurrencyAmount pvLeg = PRICER_PRODUCT.presentValue(cap.resolve(REF_DATA), MULTICURVE, RATIONAL_2F);
      AdjustablePayment premium = AdjustablePayment.of(pvLeg.getAmount(EUR).multipliedBy(-1.0), VALUATION_DATE);
      IborCapFloorTrade capTrade = IborCapFloorTrade.builder()
          .product(cap)
          .premium(premium)
          .info(TradeInfo.of(VALUATION_DATE)).build();
      trades.add(capTrade.resolve(REF_DATA));
    }
    BitSet fixed = new BitSet(8);
    fixed.set(0); // a1
    fixed.set(1); // a2
    fixed.set(2); // correlation
    fixed.set(3); // b00
//    fixed.set(4); // eta
//    fixed.set(5); // kappa
    fixed.set(6); // c1
    fixed.set(7); // c2
    RationalTwoFactorHWShapePlusCstTemplate template =
        RationalTwoFactorHWShapePlusCstTemplate
            .of(RATIONAL_2F.getTimeMeasure(), RATIONAL_2F.getDiscountFactors(),
                RATIONAL_2F.getValuationTime(), RATIONAL_2F.getValuationZone(),
                DoubleArray.of(RATIONAL_2F.a1(), RATIONAL_2F.a2(), RATIONAL_2F.getCorrelation(),
                    RATIONAL_2F.getB00(), RATIONAL_2F.getEta() + 0.0002, RATIONAL_2F.getKappa() + 0.0001,
                    RATIONAL_2F.getC1(), RATIONAL_2F.getC2()),
                fixed);
    assertEquals(template.parametersCount(), RATIONAL_2F.getParameterCount());
    assertEquals(template.parametersVariableCount(), 2);
    SingleCurrencyModelCapFloorRootPriceCalibrator calibrator =
        SingleCurrencyModelCapFloorRootPriceCalibrator.of(template);
    SingleCurrencyModelParameters calibrated = 
        calibrator.calibrateConstraints(trades, MULTICURVE, PRICER_TRADE);
    assertTrue(calibrated.getParameters().equalWithTolerance(RATIONAL_2F.getParameters(), TOL_ROOT));
  }
  
  private static IborCapFloor cap(LocalDate spot, LocalDate maturity, double strike) {
    PeriodicSchedule paySchedule =
        PeriodicSchedule.of(spot, maturity, Frequency.P6M, BUSINESS_ADJ, StubConvention.NONE,
            RollConventions.NONE);
    IborCapFloorLeg leg = IborCapFloorLeg.builder()
        .currency(EUR)
        .calculation(IborRateCalculation.of(EUR_EURIBOR_6M))
        .capSchedule(ValueSchedule.of(strike))
        .notional(ValueSchedule.of(NOTIONAL))
        .paymentSchedule(paySchedule)
        .payReceive(PayReceive.RECEIVE).build();
    return IborCapFloor.of(leg);
  }
  
}
