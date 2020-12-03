/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_6M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.product.swap.type.FixedIborSwapConventions.EUR_FIXED_1Y_EURIBOR_6M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.swap.DiscountingSwapProductPricer;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.common.LongShort;
import com.opengamma.strata.product.swap.ResolvedSwap;
import com.opengamma.strata.product.swap.ResolvedSwapLeg;
import com.opengamma.strata.product.swap.ResolvedSwapTrade;
import com.opengamma.strata.product.swap.SwapLegType;
import com.opengamma.strata.product.swap.SwapPaymentPeriod;
import com.opengamma.strata.product.swaption.PhysicalSwaptionSettlement;
import com.opengamma.strata.product.swaption.ResolvedSwaption;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LmmdddExamplesUtils;
import marc.henrard.murisq.model.lmm.LmmdddUtils;
import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductExplicitApproxPricer;

/**
 * Tests {@link LmmdddUtils} .
 * 
 * @author Marc Henrard
 */
public class LmmdddUtilsTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();
  private static final HolidayCalendar EUTA_IMPL = REF_DATA.getValue(EUR_EURIBOR_6M.getFixingCalendar());

  /* Market Data */
  private static final ImmutableRatesProvider MULTICURVE_EUR = 
      MulticurveEur20151120DataSet.MULTICURVE_EUR_EONIA_20151120;
  private static final LocalDate VALUATION_DATE = MULTICURVE_EUR.getValuationDate();
  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/Brussels");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);

  /* LMM two-factor */
  private static final double MEAN_REVERTION = 0.02;
  private static final double DISPLACEMENT = 0.05;
  private static final double VOL2_LEVEL_1 = 0.02;
  private static final double VOL2_ANGLE = Math.PI * 0.5;
  private static final double VOL2_LEVEL_2 = 0.12;
  private static final List<LocalDate> IBOR_DATES = new ArrayList<>();
  static {
    ResolvedSwapTrade swapMax = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(VALUATION_DATE, Tenor.TENOR_30Y, BuySell.BUY, 1.0, 0.0d, REF_DATA).resolve(REF_DATA);
    ImmutableList<SwapPaymentPeriod> iborLeg = swapMax.getProduct().getLegs().get(1).getPaymentPeriods();
    IBOR_DATES.add(iborLeg.get(0).getStartDate());
    for (SwapPaymentPeriod period : iborLeg) {
      IBOR_DATES.add(period.getEndDate());
    }
  }
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMM_2F =
      LmmdddExamplesUtils.lmm2Angle(MEAN_REVERTION, VOL2_LEVEL_1, VOL2_ANGLE, VOL2_LEVEL_2, DISPLACEMENT,
          IBOR_DATES, EUR_EONIA, EUR_EURIBOR_6M, ScaledSecondTime.DEFAULT, MULTICURVE_EUR,
          VALUATION_ZONE, VALUATION_TIME, REF_DATA);
  
  /* Pricers */
  private static final DiscountingSwapProductPricer PRICER_SWAP = DiscountingSwapProductPricer.DEFAULT;
  private static final LmmdddSwaptionPhysicalProductExplicitApproxPricer PRICER_LMM_APPROX =
      LmmdddSwaptionPhysicalProductExplicitApproxPricer.DEFAULT;

  /* Tests */
  private static final Offset<Double> TOLERANCE_APPROX_IV = within(5.0E-4); // Implied volatility within 0.02 bps
  private static final boolean PRINT_DETAILS = false;

  /* Compare swaption pricing from approximate dynamic to pricing from predictor corrector. 
   * The swap dynamic relies on the model independent weights and the model dependent weights.
   * The other methods are thus indirectly tested.*/
  @Test
  public void swap_dynamic_pricing() {
    Period expiry = Period.ofYears(5);
    LocalDate expiryDate = EUTA_IMPL.nextOrSame(VALUATION_DATE.plus(expiry)); // Make sure compatible with LMM
    Tenor tenor = Tenor.TENOR_2Y;
    double notional = 1_000_000.0d;
    double expiryTime = LMM_2F.getTimeMeasure().relativeTime(VALUATION_DATE, expiryDate);
    double volFactor = Math.sqrt((Math.exp(2 * MEAN_REVERTION * expiryTime) - 1) / (2 * MEAN_REVERTION * expiryTime));
    double[] moneyness = {-0.0100, -0.0050, 0.0, 0.0050, 0.0100};
    ResolvedSwap swap0 = EUR_FIXED_1Y_EURIBOR_6M
        .createTrade(expiryDate, tenor, BuySell.BUY, notional, 0.0d, REF_DATA)
        .getProduct()
        .resolve(REF_DATA);
    ResolvedSwapLeg fixedLeg = swap0.getLegs(SwapLegType.FIXED).get(0);
    double annuity = Math.abs(PRICER_SWAP.getLegPricer().pvbp(fixedLeg, MULTICURVE_EUR));
    double swapRate = PRICER_SWAP.parRate(swap0, MULTICURVE_EUR);

    if (PRINT_DETAILS) {
      System.out.println("Moneyness, PredictorCorrector, Dynamic, Difference");
    }
    for (int loopmoney = 0; loopmoney < moneyness.length; loopmoney++) {
      if (PRINT_DETAILS) {
        System.out.print(moneyness[loopmoney]);
      }
      ResolvedSwap swap = EUR_FIXED_1Y_EURIBOR_6M
          .createTrade(expiryDate, tenor, BuySell.BUY, notional, swapRate + moneyness[loopmoney], REF_DATA)
          .getProduct()
          .resolve(REF_DATA);
      ResolvedSwaption swaption = ResolvedSwaption.builder()
          .underlying(swap)
          .expiry(expiryDate.atTime(VALUATION_TIME).atZone(VALUATION_ZONE))
          .longShort(LongShort.LONG)
          .swaptionSettlement(PhysicalSwaptionSettlement.DEFAULT).build();
      double ivPredCor = PRICER_LMM_APPROX.impliedVolatilityBachelier(swaption, MULTICURVE_EUR, LMM_2F);
      DoubleArray gammaS = LmmdddUtils.swapDynamicInitialFreezeVolatilities(swap, MULTICURVE_EUR, LMM_2F);
      double volatilityDd = volFactor * Math.sqrt(gammaS.multipliedBy(gammaS).sum());
      double price = annuity * BlackFormulaRepository
          .price(swapRate + DISPLACEMENT, swapRate + moneyness[loopmoney] + DISPLACEMENT, expiryTime, volatilityDd,
              true);
      double ivDynamic = PRICER_LMM_APPROX.impliedVolatilityBachelier(swaption, MULTICURVE_EUR, price, expiryTime);
      assertThat(ivPredCor).isEqualTo(ivDynamic, TOLERANCE_APPROX_IV); // Compare implied volatilities
      if (PRINT_DETAILS) {
        System.out.println(", " + ivPredCor + ", " + ivDynamic + ", " + (ivPredCor - ivDynamic));
      }
    }
  }
}
