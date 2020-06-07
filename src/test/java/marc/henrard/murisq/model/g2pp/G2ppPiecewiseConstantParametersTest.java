/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.g2pp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.market.param.LabelParameterMetadata;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.basics.time.TimeMeasurement;

/**
 * Tests {@link G2ppPiecewiseConstantParameters}
 * 
 * @author Marc Henrard
 */
public class G2ppPiecewiseConstantParametersTest {

  private static final LocalDate VALUATION_DATE = LocalDate.of(2015, 11, 20);
  private static final LocalTime VALUATION_TIME = LocalTime.NOON;
  private static final ZoneId VALUATION_ZONE = ZoneId.of("America/New_York");
  private static final TimeMeasurement TIME_MEASUREMENT = ScaledSecondTime.DEFAULT;

  private static final Currency CURRENCY = Currency.USD;
  private static final double CORRELATION = -0.50;
  private static final double KAPPA_1 = 0.02;
  private static final double KAPPA_2 = 0.20;
  private static final DoubleArray VOLATILITY_1 = DoubleArray.of(0.01d, 0.012d, 0.011);
  private static final DoubleArray VOLATILITY_2 = DoubleArray.of(0.005d, 0.006d, 0.007);
  private static final DoubleArray VOLATILITY_TIME = DoubleArray.of(1.0d, 5.0d);
  
  private static final Offset<Double> TOLERANCE_TIME = Offset.offset(1.0E-8);

  private static final G2ppPiecewiseConstantParameters PARAMETERS = 
      G2ppPiecewiseConstantParameters.builder()
      .currency(CURRENCY)
      .correlation(CORRELATION)
      .kappa1(KAPPA_1)
      .kappa2(KAPPA_2)
      .volatility1(VOLATILITY_1)
      .volatility2(VOLATILITY_2)
      .volatilityTime(VOLATILITY_TIME)
      .valuationDate(VALUATION_DATE)
      .valuationTime(VALUATION_TIME)
      .valuationZone(VALUATION_ZONE)
      .timeMeasure(TIME_MEASUREMENT)
      .build();

  /* Tests the builder. */
  @Test
  public void builder() {
    assertThat(PARAMETERS.getCurrency()).isEqualTo(CURRENCY);
    assertThat(PARAMETERS.getCorrelation()).isEqualTo(CORRELATION);
    assertThat(PARAMETERS.getKappa1()).isEqualTo(KAPPA_1);
    assertThat(PARAMETERS.getKappa2()).isEqualTo(KAPPA_2);
    assertThat(PARAMETERS.getVolatility1()).isEqualTo(VOLATILITY_1);
    assertThat(PARAMETERS.getVolatility2()).isEqualTo(VOLATILITY_2);
    assertThat(PARAMETERS.getVolatilityTime().subArray(1, 3)).isEqualTo(VOLATILITY_TIME);
    assertThat(PARAMETERS.getValuationDate()).isEqualTo(VALUATION_DATE);
    assertThat(PARAMETERS.getValuationTime()).isEqualTo(VALUATION_TIME);
    assertThat(PARAMETERS.getValuationZone()).isEqualTo(VALUATION_ZONE);
    assertThat(PARAMETERS.getTimeMeasure()).isEqualTo(TIME_MEASUREMENT);
    assertThat(PARAMETERS.getParameterCount()).isEqualTo(3 + VOLATILITY_1.size() * 2);
    assertThat(PARAMETERS.getValuationDateTime())
        .isEqualTo(ZonedDateTime.of(VALUATION_DATE, VALUATION_TIME, VALUATION_ZONE));
  }
  
  /* Tests the relative time. */
  @Test
  public void relativeTime() {
    ZonedDateTime dateTime = ZonedDateTime
        .of(LocalDate.of(2018, 8, 18), LocalTime.of(15, 14), ZoneId.of("Europe/Brussels"));
    double relativeTimeComputed = PARAMETERS.relativeTime(dateTime);
    double relativeTimeExpected = TIME_MEASUREMENT
        .relativeTime(PARAMETERS.getValuationDateTime(), dateTime);
    assertThat(relativeTimeComputed).isEqualTo(relativeTimeExpected, TOLERANCE_TIME);
  }

  /* Tests getter on individual parameters. */
  @Test
  public void getParameter() {
    assertThat(PARAMETERS.getParameter(0)).isEqualTo(CORRELATION);
    assertThat(PARAMETERS.getParameter(1)).isEqualTo(KAPPA_1);
    assertThat(PARAMETERS.getParameter(2)).isEqualTo(KAPPA_2);
    for (int i = 0; i < VOLATILITY_1.size(); i++) {
      assertThat(PARAMETERS.getParameter(3 + i)).isEqualTo(VOLATILITY_1.get(i));
    }
    for (int i = 0; i < VOLATILITY_2.size(); i++) {
      assertThat(PARAMETERS.getParameter(3 + VOLATILITY_1.size() + i)).isEqualTo(VOLATILITY_2.get(i));
    }
  }

  /* Tests getter on individual parameters metadata. */
  @Test
  public void getParameterMetadata() {
    assertThat(PARAMETERS.getParameterMetadata(0))
        .isEqualTo(LabelParameterMetadata.of("correlation"));
    assertThat(PARAMETERS.getParameterMetadata(1))
        .isEqualTo(LabelParameterMetadata.of("kappa1"));
    assertThat(PARAMETERS.getParameterMetadata(2))
        .isEqualTo(LabelParameterMetadata.of("kappa2"));
    for (int i = 0; i < VOLATILITY_1.size(); i++) {
      assertThat(PARAMETERS.getParameterMetadata(3 + i))
          .isEqualTo(LabelParameterMetadata.of("volatility1-" + i));
    }
    for (int i = 0; i < VOLATILITY_2.size(); i++) {
      assertThat(PARAMETERS.getParameterMetadata(3 + VOLATILITY_1.size() + i))
          .isEqualTo(LabelParameterMetadata.of("volatility2-" + i));
    }
  }

}
