/**
 * Copyright (C) 2020 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.montecarlo;

import static com.opengamma.strata.basics.index.IborIndices.EUR_EURIBOR_3M;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.collect.tuple.Triple;
import com.opengamma.strata.math.impl.cern.MersenneTwister64;
import com.opengamma.strata.math.impl.cern.RandomEngine;
import com.opengamma.strata.math.impl.random.NormalRandomNumberGenerator;

import marc.henrard.murisq.basics.time.ScaledSecondTime;
import marc.henrard.murisq.dataset.MulticurveEur20151120DataSet;
import marc.henrard.murisq.model.lmm.LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters;
import marc.henrard.murisq.model.lmm.LiborMarketModelMonteCarloEvolution;
import marc.henrard.murisq.model.lmm.LmmdddUtils;
import marc.henrard.murisq.pricer.swaption.LmmdddSwaptionPhysicalProductMonteCarloPricer;

/**
 * Tests {@link MonteCarloEuropeanPricer}.
 * Interface; tests only some default methods.
 * discounting method tested in {@link LmmdddCmsPeriodMonteCarloPricerTest} and 
 * {@link LmmdddSwaptionPhysicalProductMonteCarloPricerTest}.
 * 
 * @author Marc Henrard
 */
public class MonteCarloEuropeanPricerTest {

  private static final ReferenceData REF_DATA = ReferenceData.standard();

  private static final ZoneId VALUATION_ZONE = ZoneId.of("Europe/London");
  private static final LocalTime VALUATION_TIME = LocalTime.of(10, 29);
  private static final double MEAN_REVERTION = 0.02;
  private static final double HW_SIGMA = 0.01;
  private static final List<LocalDate> IBOR_DATES = 
      ImmutableList.of(LocalDate.of(2016, 11, 20), LocalDate.of(2020, 2, 20));
  private static final LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters LMMHW = 
      LmmdddUtils.
      lmmHw(MEAN_REVERTION, HW_SIGMA, IBOR_DATES, EUR_EONIA, EUR_EURIBOR_3M, ScaledSecondTime.DEFAULT, 
          MulticurveEur20151120DataSet.MULTICURVE_EUR_20151120,
          VALUATION_ZONE, VALUATION_TIME, REF_DATA);
  private static final RandomEngine ENGINE = new MersenneTwister64(0);
  private static final NormalRandomNumberGenerator RND = 
      new NormalRandomNumberGenerator(0.0d, 1.0d, ENGINE);
  private static final LiborMarketModelMonteCarloEvolution EVOLUTION =
      LiborMarketModelMonteCarloEvolution.DEFAULT;
  
  @Test
  public void decomposition1() {
    int blocks = 3;
    int pathPerBlock = 100;
    int extra = 23;
    checkDecomposition(blocks, pathPerBlock, extra);
  }
  
  @Test
  public void decomposition2() {
    int blocks = 10;
    int pathPerBlock = 110;
    int extra = 0;
    checkDecomposition(blocks, pathPerBlock, extra);
  }
  
  @Test
  public void decomposition3() {
    int blocks = 0;
    int pathPerBlock = 100;
    int extra = 25;
    checkDecomposition(blocks, pathPerBlock, extra);
  }
  
  private void checkDecomposition(int blocks, int pathPerBlock, int extra) {
    int nbPaths = blocks * pathPerBlock + extra;
    LmmdddSwaptionPhysicalProductMonteCarloPricer pricer = 
        LmmdddSwaptionPhysicalProductMonteCarloPricer.builder()
        .evolution(EVOLUTION)
        .model(LMMHW)
        .numberGenerator(RND)
        .nbPaths(nbPaths)
        .pathNumberBlock(pathPerBlock)
        .build();
    Triple<Integer, Integer, Integer> dec = pricer.decomposition();
    assertThat(dec.getFirst()).isEqualTo(blocks);
    assertThat(dec.getSecond()).isEqualTo(pathPerBlock);
    assertThat(dec.getThird()).isEqualTo(extra);
  }

}
