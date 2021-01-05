/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.cms;

import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.date.HolidayCalendarIds.EUTA;
import static com.opengamma.strata.basics.currency.Currency.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.CurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParameters;
import com.opengamma.strata.pricer.model.HullWhiteOneFactorPiecewiseConstantParametersProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.cms.CmsPeriod;
import com.opengamma.strata.product.cms.CmsPeriodType;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.SwapIndex;
import com.opengamma.strata.product.swap.SwapIndices;
import com.opengamma.strata.product.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.product.swap.type.FixedIborSwapConventions;

import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;

/**
 * Tests of {@link HullWhiteCmsPeriodExplicitPricer} and {@link HullWhiteCmsPeriodNumericalIntegrationPricer}.
 * 
 * @author Marc Henrard
 */
public class HullWhiteCmsPeriodExplicitPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 0);
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.findValue(EUTA).get();

  // Hull-White model parameters
  private static final double MEAN_REVERSION = 0.01;
  private static final DoubleArray VOLATILITY = DoubleArray.of(0.01);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of();
  private static final HullWhiteOneFactorPiecewiseConstantParameters MODEL_PARAMETERS =
      HullWhiteOneFactorPiecewiseConstantParameters.of(MEAN_REVERSION, VOLATILITY, VOLATILITY_TIME);
  private static final HullWhiteOneFactorPiecewiseConstantParametersProvider HW_PROVIDER =
      HullWhiteOneFactorPiecewiseConstantParametersProvider.of(
          MODEL_PARAMETERS, ACT_365F, VALUATION_DATE.atTime(VALUATION_TIME).atZone(VALUATION_ZONE));

  private static final HullWhiteCmsPeriodExplicitPricer PRICER_CMS_EXPL =
      HullWhiteCmsPeriodExplicitPricer.DEFAULT;
  private static final int NB_STEPS = 250;
  private static final HullWhiteCmsPeriodNumericalIntegrationPricer PRICER_CMS_NI_PRECISION =
      new HullWhiteCmsPeriodNumericalIntegrationPricer(NB_STEPS, DiscountingPaymentPricer.DEFAULT);
  
  /* Descriptions of caplets/floorlets */
  private static final SwapIndex[] INDICES = 
      new SwapIndex[] {SwapIndices.EUR_EURIBOR_1100_2Y, SwapIndices.EUR_EURIBOR_1100_10Y};
  private static final Period[] EXPIRIES = new Period[] {Period.ofYears(5), Period.ofYears(20)};
  private static final Period[] PAYMENT_LAG = new Period[] {Period.ZERO, Period.ofMonths(12)};
  private static final double[] STRIKES = new double[] {0.0050, 0.0250};
  private static final int NB_INDICES = INDICES.length;
  private static final int NB_EXPIRIES = EXPIRIES.length;
  private static final int NB_PAY_LAG = PAYMENT_LAG.length;
  private static final int NB_STRIKES = STRIKES.length;
  private static final double NOTIONAL = 100_000_000.0d;

  /* Load and calibrate curves */
  private static final ImmutableRatesProvider MULTICURVE = MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  
  /* Constants */
  private static final Offset<Double> TOLERANCE_PV = offset(2.0E+1); // Approximation order 3
  private static final Offset<Double> TOLERANCE_PV_CAP = offset(3.5E+1); // Approximation order 3
  
  /* Coupon:  present value versus numerical integration. */
  @Test
  public void coupon_present_value_v_ni() {
    FixedIborSwapConvention convention = FixedIborSwapConventions.EUR_FIXED_1Y_LIBOR_6M;
    for(int loopindex = 0; loopindex<NB_INDICES; loopindex++) {
      for(int loopexp=0; loopexp<NB_EXPIRIES; loopexp++) {
        for(int looplag = 0; looplag<NB_PAY_LAG; looplag++) {
          LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
          LocalDate startDate = convention.calculateSpotDateFromTradeDate(fixingDate, REF_DATA);
          LocalDate endDate = EUTA_IMPL.nextOrSame(startDate.plusMonths(6));
          LocalDate paymentDate = EUTA_IMPL.nextOrSame(startDate.plus(PAYMENT_LAG[looplag]));
          ResolvedSwap underlyingSwap = 
              INDICES[loopindex].getTemplate().createTrade(fixingDate, BuySell.BUY, 1.0d, 1.0d, REF_DATA)
              .resolve(REF_DATA)
              .getProduct();
          CmsPeriod cms = cmsPeriod(fixingDate, startDate, endDate, paymentDate, INDICES[loopindex], underlyingSwap, 
              CmsPeriodType.COUPON, 0.0);
          CurrencyAmount pvExp = PRICER_CMS_EXPL.presentValue(cms, MULTICURVE, HW_PROVIDER);
          CurrencyAmount pvNIn = PRICER_CMS_NI_PRECISION.presentValue(cms, MULTICURVE, HW_PROVIDER);
          assertThat(pvExp.getCurrency()).isEqualTo(EUR);
          assertThat(pvNIn.getCurrency()).isEqualTo(EUR);
          assertThat(pvExp.getAmount()).isCloseTo(pvNIn.getAmount(), TOLERANCE_PV);
//          System.out.println(INDICES[loopindex].toString() + EXPIRIES[loopexp] + PAYMENT_LAG[looplag] 
//              + " Explicit, " + pvExp + ", Diff, " + (pvExp.minus(pvNIn)));
        }
      }
    }
  }

  /* Caplet: present value versus numerical integration. */
  @Test
  public void cap_present_value_v_ni() {
    FixedIborSwapConvention convention = FixedIborSwapConventions.EUR_FIXED_1Y_LIBOR_6M;
    for (int loopindex = 0; loopindex < NB_INDICES; loopindex++) {
      for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
        for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
          for (int loopstrike = 0; loopstrike < NB_STRIKES; loopstrike++) {
            LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
            LocalDate startDate = convention.calculateSpotDateFromTradeDate(fixingDate, REF_DATA);
            LocalDate endDate = EUTA_IMPL.nextOrSame(startDate.plusMonths(6));
            LocalDate paymentDate = EUTA_IMPL.nextOrSame(startDate.plus(PAYMENT_LAG[looplag]));
            ResolvedSwap underlyingSwap =
                INDICES[loopindex].getTemplate().createTrade(fixingDate, BuySell.BUY, 1.0d, 1.0d, REF_DATA)
                    .resolve(REF_DATA)
                    .getProduct();
            CmsPeriod cmsCap = cmsPeriod(fixingDate, startDate, endDate, paymentDate, INDICES[loopindex], underlyingSwap, 
                CmsPeriodType.CAPLET, STRIKES[loopstrike]);
            CurrencyAmount pvExp = PRICER_CMS_EXPL.presentValue(cmsCap, MULTICURVE, HW_PROVIDER);
            CurrencyAmount pvNIn = PRICER_CMS_NI_PRECISION.presentValue(cmsCap, MULTICURVE, HW_PROVIDER);
            assertThat(pvExp.getCurrency()).isEqualTo(EUR);
            assertThat(pvNIn.getCurrency()).isEqualTo(EUR);
            assertThat(pvExp.getAmount()).isCloseTo(pvNIn.getAmount(),TOLERANCE_PV_CAP);
//            System.out.println(INDICES[loopindex].toString() + EXPIRIES[loopexp] + PAYMENT_LAG[looplag] +
//                " Explicit, " + pvExp.getAmount() + ", NI, " + pvNIn.getAmount() 
//                + ", " + (pvExp.getAmount() - pvNIn.getAmount()));

          }
        }
      }
    }
  }

  /* Floorlet: present value versus numerical integration. */
  @Test
  public void floor_present_value_v_ni() {
    FixedIborSwapConvention convention = FixedIborSwapConventions.EUR_FIXED_1Y_LIBOR_6M;
    for (int loopindex = 0; loopindex < NB_INDICES; loopindex++) {
      for (int loopexp = 0; loopexp < NB_EXPIRIES; loopexp++) {
        for (int looplag = 0; looplag < NB_PAY_LAG; looplag++) {
          for (int loopstrike = 0; loopstrike < NB_STRIKES; loopstrike++) {
            LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
            LocalDate startDate = convention.calculateSpotDateFromTradeDate(fixingDate, REF_DATA);
            LocalDate endDate = EUTA_IMPL.nextOrSame(startDate.plusMonths(6));
            LocalDate paymentDate = EUTA_IMPL.nextOrSame(startDate.plus(PAYMENT_LAG[looplag]));
            ResolvedSwap underlyingSwap =
                INDICES[loopindex].getTemplate().createTrade(fixingDate, BuySell.BUY, 1.0d, 1.0d, REF_DATA)
                    .resolve(REF_DATA)
                    .getProduct();
            CmsPeriod cmsFloor = cmsPeriod(fixingDate, startDate, endDate, paymentDate, INDICES[loopindex], underlyingSwap, 
                CmsPeriodType.FLOORLET, STRIKES[loopstrike]);
            CurrencyAmount pvExp = PRICER_CMS_EXPL.presentValue(cmsFloor, MULTICURVE, HW_PROVIDER);
            CurrencyAmount pvNIn = PRICER_CMS_NI_PRECISION.presentValue(cmsFloor, MULTICURVE, HW_PROVIDER);
            assertThat(pvExp.getCurrency()).isEqualTo(EUR);
            assertThat(pvNIn.getCurrency()).isEqualTo(EUR);
              assertThat(pvExp.getAmount()).isCloseTo(pvNIn.getAmount(), TOLERANCE_PV_CAP);
//            System.out.println(INDICES[loopindex].toString() + EXPIRIES[loopexp] + PAYMENT_LAG[looplag] +
//                " Explicit, " + pvExp + ", Diff, " + (pvExp.minus(pvNIn)));

          }
        }
      }
    }
  }
  
  /* Coupon: present value sensitivity versus finite difference. */
  @Test
  public void coupon_sensitivity_v_fd() {
    
    RatesFiniteDifferenceSensitivityCalculator fdCalculator = RatesFiniteDifferenceSensitivityCalculator.DEFAULT;
    FixedIborSwapConvention convention = FixedIborSwapConventions.EUR_FIXED_1Y_LIBOR_6M;
    for(int loopindex = 0; loopindex<NB_INDICES; loopindex++) {
      for(int loopexp=0; loopexp<NB_EXPIRIES; loopexp++) {
        for(int looplag = 0; looplag<NB_PAY_LAG; looplag++) {
          LocalDate fixingDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(EXPIRIES[loopexp]));
          LocalDate startDate = convention.calculateSpotDateFromTradeDate(fixingDate, REF_DATA);
          LocalDate endDate = EUTA_IMPL.nextOrSame(startDate.plusMonths(6));
          LocalDate paymentDate = EUTA_IMPL.nextOrSame(startDate.plus(PAYMENT_LAG[looplag]));
          ResolvedSwap underlyingSwap = 
              INDICES[loopindex].getTemplate().createTrade(fixingDate, BuySell.BUY, 1.0d, 1.0d, REF_DATA)
              .resolve(REF_DATA)
              .getProduct();
          CmsPeriod cms = cmsPeriod(fixingDate, startDate, endDate, paymentDate, INDICES[loopindex], underlyingSwap, 
              CmsPeriodType.COUPON, 0.0);
          
//          CurrencyAmount pvApprox = PRICER_CMS_EXPL.presentValue(cms, MULTICURVE, HW_PROVIDER);
          PointSensitivityBuilder pts = PRICER_CMS_EXPL.presentValueSensitivityRates(cms, MULTICURVE, HW_PROVIDER);
          CurrencyParameterSensitivities psApprox = MULTICURVE.parameterSensitivity(pts.build());
          CurrencyParameterSensitivities psFd = 
              fdCalculator.sensitivity(MULTICURVE, (p) -> PRICER_CMS_EXPL.presentValue(cms, p, HW_PROVIDER));
          System.out.println(psApprox);
          System.out.println(psFd);
          assertThat(psApprox.equalWithTolerance(psFd, 1.0E+4)).isTrue();
//          System.out.println(INDICES[loopindex].toString() + EXPIRIES[loopexp] + PAYMENT_LAG[looplag] 
//              + " Explicit, " + pvExp + ", Diff, " + (pvExp.minus(pvNIn)));
        }
      }
    }
    
  }
  
  private CmsPeriod cmsPeriod(LocalDate fixingDate, LocalDate startDate, LocalDate endDate, LocalDate paymentDate,
      SwapIndex index, ResolvedSwap underlyingSwap, CmsPeriodType type, double strike) {

    CmsPeriod.Builder cmsBuilder = CmsPeriod.builder()
        .fixingDate(fixingDate)
        .startDate(startDate)
        .endDate(endDate)
        .paymentDate(paymentDate)
        .dayCount(DayCounts.ACT_360)
        .yearFraction(0.5)
        .notional(NOTIONAL)
        .index(index)
        .underlyingSwap(underlyingSwap)
        .currency(EUR);
    if(type.equals(CmsPeriodType.CAPLET)) {
      cmsBuilder = cmsBuilder.caplet(strike);
    }
    if(type.equals(CmsPeriodType.FLOORLET)) {
      cmsBuilder = cmsBuilder.floorlet(strike);
    }
    return cmsBuilder.build();
  }

}
