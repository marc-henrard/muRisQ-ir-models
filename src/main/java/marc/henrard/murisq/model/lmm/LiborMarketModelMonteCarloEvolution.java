/**
 * Copyright (C) 2011 - present by Marc Henrard.
 */
package marc.henrard.murisq.model.lmm;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.joda.beans.ImmutableBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;

import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.array.DoubleMatrix;
import com.opengamma.strata.math.impl.matrix.CommonsMatrixAlgebra;
import com.opengamma.strata.math.impl.matrix.MatrixAlgebra;
import com.opengamma.strata.math.impl.random.RandomNumberGenerator;

import marc.henrard.murisq.pricer.decomposition.MulticurveEquivalentValues;
import java.util.Map;
import java.util.NoSuchElementException;
import org.joda.beans.Bean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.MetaProperty;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

/**
 * Method to generate Monte Carlo paths for a LMM with displaced diffusion and deterministic IBOR/collateral spreads.
 * <p>
 * See the details of the model parameters in {@link LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters}.
 * <p>
 * Implementation reference:
 * Henrard, M. Libor/Forward Market Model in the multi-curve framework, muRisQ Model description, September 2020.
 */
@BeanDefinition(factoryName = "of")
public final class LiborMarketModelMonteCarloEvolution
    implements ImmutableBean, Serializable {
  
  /** The default maximum length of a jump in the path generation. */
  private static final double MAX_JUMP_DEFAULT = 1.0;
  
  /** The maximum length of a jump in the path generation. */
  @PropertyDefinition
  private final double maxJump;
  
  /** Default instance */
  public static LiborMarketModelMonteCarloEvolution DEFAULT =
      LiborMarketModelMonteCarloEvolution.of(MAX_JUMP_DEFAULT);
  
  /**
   * Evolves according to a model starting values up to the decision date.
   * <p>
   * The pseudo-numeraire is implicitly the pseudo-discount factor associated to the last Ibor time.
   * <p>
   * The output consists on a certain number of scenarios and for each of them 
   * 
   * @param stepDateTime  the date and time of the step
   * @param startingValues  the initial values of the forward rates, must be compatible with the model
   * @param model  the model parameters
   * @param numberGenerator  the random number generator
   * @param nbPaths  the number of paths to be generated
   * @return the value evolved to the decision date described in the multi-curve equivalent
   */
  public List<MulticurveEquivalentValues> evolveOneStep(
      ZonedDateTime stepDateTime,
      MulticurveEquivalentValues initialValues,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      RandomNumberGenerator numberGenerator,
      int nbPaths) {

    double[] stepTimes = new double[] {model.relativeTime(stepDateTime)};
    int nbLmmPeriods = model.getDisplacements().size();
    double[][] initForwards = new double[nbPaths][nbLmmPeriods];
    DoubleArray initialValueOnRates = initialValues.getOnRates();
    for (int i = 0; i < nbPaths; i++) {
      initForwards[i] = initialValueOnRates.toArray().clone();
    }
    double[][][] pathsData = pathGeneratorForwards(stepTimes, initForwards, model, numberGenerator);
    List<MulticurveEquivalentValues> paths = new ArrayList<>();
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      double[] onRates = new double[nbLmmPeriods];
      for (int i = 0; i < nbLmmPeriods; i++) {
        onRates[i] = pathsData[0][i][looppath];
      }
      MulticurveEquivalentValues pathValues = MulticurveEquivalentValues.builder()
          .onRates(DoubleArray.ofUnsafe(onRates)).build();
      paths.add(pathValues);
    }
    return paths;
  }
  
  /**
   * 
   * @param stepDateTime
   * @param initialValues
   * @param model
   * @param numberGenerator
   * @param nbPaths
   * @return overnight rates, dimensions: path x lmm periods
   */
  public double[][] evolveOneStepFast(
      ZonedDateTime stepDateTime,
      MulticurveEquivalentValues initialValues,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      RandomNumberGenerator numberGenerator,
      int nbPaths) {

    double[] stepTimes = new double[] {model.relativeTime(stepDateTime)};
    int nbLmmPeriods = model.getDisplacements().size();
    double[][] initForwards = new double[nbPaths][nbLmmPeriods];
    DoubleArray initialValueOnRates = initialValues.getOnRates();
    for (int i = 0; i < nbPaths; i++) {
      initForwards[i] = initialValueOnRates.toArray().clone();
    }
    double[][][] pathsData = pathGeneratorForwards(stepTimes, initForwards, model, numberGenerator);
    return pathsData[0];
  }
  
  /**
   * Evolves according to a model starting values up to the different decision dates.
   * 
   * @param stepDateTimes  the dates and times of each step
   * @param startingValues  the initial values of the forward rates, must be compatible with the model
   * @param model  the model parameters
   * @param numberGenerator  the random number generator
   * @param nbPaths  the number of paths to be generated
   * @return the value evolved to the decision date described in the multi-curve equivalent, dimensions nbPaths x nbSteps
   */
  public List<List<MulticurveEquivalentValues>> evolveMultiSteps(
      List<ZonedDateTime> stepDateTimes,
      MulticurveEquivalentValues initialValues,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters model,
      RandomNumberGenerator numberGenerator,
      int nbPaths) {
    
    int nbSteps = stepDateTimes.size();
    double[] stepTimes = new double[nbSteps];
    for (int i = 0; i < nbSteps; i++) {
      stepTimes[i] = model.relativeTime(stepDateTimes.get(i));
    }
    int nbLmmPeriods = model.getDisplacements().size();
    double[][] initForwards = new double[nbLmmPeriods][nbPaths];
    DoubleArray initialValueOnRates = initialValues.getOnRates(); // dsc forwards
    for (int i = 0; i < nbLmmPeriods; i++) {
      Arrays.fill(initForwards[i], initialValueOnRates.get(i));
    }
    double[][][] pathsData = pathGeneratorForwards(stepTimes, initForwards, model, numberGenerator);
    List<List<MulticurveEquivalentValues>> paths = new ArrayList<>();
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      List<MulticurveEquivalentValues> steps = new ArrayList<>();
      for (int loopstep = 0; loopstep < nbSteps; loopstep++) {
        double[] forwardRates = new double[nbLmmPeriods];
        for (int i = 0; i < nbLmmPeriods; i++) {
          forwardRates[i] = pathsData[loopstep][i][looppath];
        }
        MulticurveEquivalentValues pathValues = MulticurveEquivalentValues.builder()
            .onRates(DoubleArray.ofUnsafe(forwardRates)).build();
        steps.add(pathValues);
      }
      paths.add(steps);
    }
    return paths;
  }

  /**
   * Generates multi-steps for the path in the model.
   * 
   * @param stepTimes  the required step times, today is represented by 0, the times must be positive and in 
   *  increasing order
   * @param initForwards  the initial forward rates, dimensions:  paths x LMM periods, the number of paths must be the
   *  same for each rate
   * @return the forward rates at each step, dimensions: steps x paths x LMM periods
   */
  public double[][][] pathGeneratorForwards(
      double[] stepTimes,
      double[][] initForwards,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm,
      RandomNumberGenerator numberGenerator) {

    final int nbPaths = initForwards.length;
    final int nbPeriods = initForwards[0].length;
    final int nbJump = stepTimes.length;
    double[][] initTmp = new double[nbPaths][nbPeriods]; // modified at each step
    for (int looppath = 0; looppath < nbPaths; looppath++) {
      initTmp[looppath] = initForwards[looppath].clone();
    }
    final double[] jumpTimeAugmented = new double[nbJump + 1];
    jumpTimeAugmented[0] = 0;
    System.arraycopy(stepTimes, 0, jumpTimeAugmented, 1, nbJump); // Add 0 in the steps to facilitate algorithm
    final double[][][] result = new double[nbJump][nbPaths][nbPeriods];
    for (int loopjump = 0; loopjump < nbJump; loopjump++) { // Long jump start
      // Intermediary jumps; intermediary values are not exported
      double[] jumpIn;
      if (jumpTimeAugmented[loopjump + 1] - jumpTimeAugmented[loopjump] < maxJump) {
        jumpIn = new double[] {jumpTimeAugmented[loopjump], jumpTimeAugmented[loopjump + 1]};
      } else {
        double jump = jumpTimeAugmented[loopjump + 1] - jumpTimeAugmented[loopjump];
        int nbJumpIn = (int) Math.ceil(jump / maxJump);
        jumpIn = new double[nbJumpIn + 1];
        jumpIn[0] = jumpTimeAugmented[loopjump];
        for (int loopJumpIn = 1; loopJumpIn <= nbJumpIn; loopJumpIn++) {
          jumpIn[loopJumpIn] = jumpTimeAugmented[loopjump] + loopJumpIn * jump / nbJumpIn;
        }
      }
      initTmp = stepPredictorCorrector(jumpIn, initTmp, lmm, numberGenerator);
      for (int looppath = 0; looppath < nbPaths; looppath++) {
        result[loopjump][looppath] = initTmp[looppath].clone();
      }
    } // Long jump end
    return result;
  }

  /**
   * Create one step in the LMM diffusion. 
   * <p>
   * The step is done through several intermediary jump times. The diffusion is approximated with a 
   * predictor-corrector approach at each jump. Only the one step results are in the output, not the intermediary steps.
   * <p>
   * At each jump, only the rates after the jump time are evolved, the other rates are unchanged from their start
   * of intermediary jump period values.
   * <p>
   * The implementation uses the efficient one step implementation that is described in Section 5.3 of the reference.
   * 
   * @param jumpTimes  the intermediary jump times, the start step is the first time in the array and 
   *   the last jump time is the long step time
   * @param initForwards  the initial forward rates, dimensions:  paths x periodsLMM, the number of path must be the
   *   same for each rate
   * @param lmm  the model parameters
   * @return the forward rates at the end of the step; dimensions:  paths x periodsLMM
   */
  public double[][] stepPredictorCorrector(
      double[] jumpTimes,
      double[][] initForwards,
      LiborMarketModelDisplacedDiffusionDeterministicSpreadParameters lmm,
      RandomNumberGenerator numberGenerator) {
    
    double amr = lmm.getMeanReversion();
    double[] iborTimes = lmm.getIborTimes().toArrayUnsafe();
    double[] almm = lmm.getDisplacements().toArrayUnsafe();
    double[] deltalmm = lmm.getAccrualFactors().toArrayUnsafe();
    int nbPeriodLMM = lmm.getIborPeriodsCount();
    int nbFactorLMM = lmm.getFactorCount();
    double timeTolerance = lmm.getTimeTolerance();
    DoubleMatrix gammaLMM = lmm.getVolatilities();
    MatrixAlgebra algebra = new CommonsMatrixAlgebra();
    DoubleMatrix s = (DoubleMatrix) algebra.multiply(gammaLMM, algebra.getTranspose(gammaLMM));
    int nbJump = jumpTimes.length - 1;
    int nbPath = initForwards.length;
    double[] dt = new double[nbJump];
    double[] alpha = new double[nbJump];
    double[] alpha2 = new double[nbJump];
    for (int loopjump = 0; loopjump < nbJump; loopjump++) {
      dt[loopjump] = jumpTimes[loopjump + 1] - jumpTimes[loopjump];
      alpha[loopjump] = Math.exp(amr * jumpTimes[loopjump + 1]);
      alpha2[loopjump] = alpha[loopjump] * alpha[loopjump];
    }

    double[][] f = initForwards.clone();
    for (int loopjump = 0; loopjump < nbJump; loopjump++) {
      double sqrtDt = Math.sqrt(dt[loopjump]);
      int index = Arrays.binarySearch(iborTimes, jumpTimes[loopjump + 1] - timeTolerance);
      // index: The index from which the rate should be evolved, the others are unchanged.
      if (index < 0) { // not exact match
        index = -index - 1;
      }
      int nbIndices = nbPeriodLMM - index; // the number of rates evolved
      double[] deltaI = new double[nbIndices];
      for (int loopn = 0; loopn < nbIndices; loopn++) {
        deltaI[loopn] = 1.0 / deltalmm[index + loopn];
      }
      double[][] salpha2Array = new double[nbIndices][nbIndices];
      for (int loopn1 = 0; loopn1 < nbIndices; loopn1++) {
        for (int loopn2 = 0; loopn2 < nbIndices; loopn2++) {
          salpha2Array[loopn1][loopn2] = s.get(index + loopn1, index + loopn2) * alpha2[loopjump];
        }
      }
      DoubleMatrix salpha2 = DoubleMatrix.ofUnsafe(salpha2Array);
      double[][] dw = getNormalArray(nbFactorLMM, nbPath, numberGenerator); // Random seed normal
      // Common figures (without state dependent drift)
      double[] drift1 = new double[nbIndices];
      for (int loopn = 0; loopn < nbIndices; loopn++) {
        drift1[loopn] = -0.5 * salpha2.get(loopn, loopn) * dt[loopjump];
      }
      final double[][] cc = new double[nbIndices][nbPath];
      for (int loopn = 0; loopn < nbIndices; loopn++) {
        for (int looppath = 0; looppath < nbPath; looppath++) {
          for (int loopfact = 0; loopfact < nbFactorLMM; loopfact++) {
            cc[loopn][looppath] +=
                gammaLMM.get(index + loopn, loopfact) * dw[loopfact][looppath] * sqrtDt * alpha[loopjump];
          }
          cc[loopn][looppath] += drift1[loopn];
        }
      }
      // Unique step: predictor and corrector
      final double[][] muPredict = new double[nbIndices][nbPath];
      final double[][] muCorrect = new double[nbIndices][nbPath];
      final double[][] coefPredict = new double[nbPath][nbIndices - 1];
      final double[][] coefCorrect = new double[nbIndices][nbPath];
      for (int looppath = 0; looppath < nbPath; looppath++) {
        for (int loopn = 0; loopn < nbIndices - 1; loopn++) {
          coefPredict[looppath][loopn] = (f[looppath][index + loopn + 1] + almm[index + loopn + 1]) /
              (f[looppath][index + loopn + 1] + deltaI[loopn + 1]);
        }
      }
      for (int loopdrift = nbIndices - 1; loopdrift >= 0; loopdrift--) {
        if (loopdrift < nbIndices - 1) {
          for (int looppath = 0; looppath < nbPath; looppath++) {
            coefCorrect[loopdrift + 1][looppath] = 
                (f[looppath][index + loopdrift + 1] + almm[index + loopdrift + 1])
                  / (f[looppath][index + loopdrift + 1] + deltaI[loopdrift + 1]); // Note: f has already been updated
            for (int loop = loopdrift + 1; loop < nbIndices; loop++) {
              muPredict[loopdrift][looppath] += salpha2.get(loop, loopdrift) * coefPredict[looppath][loop - 1];
              muCorrect[loopdrift][looppath] += salpha2.get(loop, loopdrift) * coefCorrect[loop][looppath];
            }
          }
          for (int looppath = 0; looppath < nbPath; looppath++) {
            f[looppath][loopdrift + index] = (f[looppath][loopdrift + index] + almm[index + loopdrift]) 
                * Math.exp(-0.5 * (muPredict[loopdrift][looppath] + muCorrect[loopdrift][looppath]) * dt[loopjump] + cc[loopdrift][looppath]) 
                - almm[index + loopdrift];
          }
        } else { // Last forward rate does not have state dependent drift
          for (int looppath = 0; looppath < nbPath; looppath++) {
            f[looppath][loopdrift + index] =
                (f[looppath][loopdrift + index] + almm[index + loopdrift]) * Math.exp(cc[loopdrift][looppath]) 
                - almm[index + loopdrift];
          }
        }
      }
    }
    return f;
  }

  /**
   * Gets a 2D-array of independent normally distributed variables.
   * @param nbJump The number of jumps.
   * @param nbPath The number of paths.
   * @return The array of variables.
   */
  private double[][] getNormalArray(
      int nbJump, 
      int nbPath,
      RandomNumberGenerator numberGenerator) {
    final double[][] result = new double[nbJump][nbPath];
    for (int loopjump = 0; loopjump < nbJump; loopjump++) {
      result[loopjump] = numberGenerator.getVector(nbPath);
    }
    return result;
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code LiborMarketModelMonteCarloEvolution}.
   * @return the meta-bean, not null
   */
  public static LiborMarketModelMonteCarloEvolution.Meta meta() {
    return LiborMarketModelMonteCarloEvolution.Meta.INSTANCE;
  }

  static {
    MetaBean.register(LiborMarketModelMonteCarloEvolution.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Obtains an instance.
   * @param maxJump  the value of the property
   * @return the instance
   */
  public static LiborMarketModelMonteCarloEvolution of(
      double maxJump) {
    return new LiborMarketModelMonteCarloEvolution(
      maxJump);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static LiborMarketModelMonteCarloEvolution.Builder builder() {
    return new LiborMarketModelMonteCarloEvolution.Builder();
  }

  private LiborMarketModelMonteCarloEvolution(
      double maxJump) {
    this.maxJump = maxJump;
  }

  @Override
  public LiborMarketModelMonteCarloEvolution.Meta metaBean() {
    return LiborMarketModelMonteCarloEvolution.Meta.INSTANCE;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the maximum length of a jump in the path generation.
   * @return the value of the property
   */
  public double getMaxJump() {
    return maxJump;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      LiborMarketModelMonteCarloEvolution other = (LiborMarketModelMonteCarloEvolution) obj;
      return JodaBeanUtils.equal(maxJump, other.maxJump);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(maxJump);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(64);
    buf.append("LiborMarketModelMonteCarloEvolution{");
    buf.append("maxJump").append('=').append(JodaBeanUtils.toString(maxJump));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code LiborMarketModelMonteCarloEvolution}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code maxJump} property.
     */
    private final MetaProperty<Double> maxJump = DirectMetaProperty.ofImmutable(
        this, "maxJump", LiborMarketModelMonteCarloEvolution.class, Double.TYPE);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "maxJump");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 843824050:  // maxJump
          return maxJump;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public LiborMarketModelMonteCarloEvolution.Builder builder() {
      return new LiborMarketModelMonteCarloEvolution.Builder();
    }

    @Override
    public Class<? extends LiborMarketModelMonteCarloEvolution> beanType() {
      return LiborMarketModelMonteCarloEvolution.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code maxJump} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> maxJump() {
      return maxJump;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 843824050:  // maxJump
          return ((LiborMarketModelMonteCarloEvolution) bean).getMaxJump();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code LiborMarketModelMonteCarloEvolution}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<LiborMarketModelMonteCarloEvolution> {

    private double maxJump;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(LiborMarketModelMonteCarloEvolution beanToCopy) {
      this.maxJump = beanToCopy.getMaxJump();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 843824050:  // maxJump
          return maxJump;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 843824050:  // maxJump
          this.maxJump = (Double) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public LiborMarketModelMonteCarloEvolution build() {
      return new LiborMarketModelMonteCarloEvolution(
          maxJump);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the maximum length of a jump in the path generation.
     * @param maxJump  the new value
     * @return this, for chaining, not null
     */
    public Builder maxJump(double maxJump) {
      this.maxJump = maxJump;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(64);
      buf.append("LiborMarketModelMonteCarloEvolution.Builder{");
      buf.append("maxJump").append('=').append(JodaBeanUtils.toString(maxJump));
      buf.append('}');
      return buf.toString();
    }

  }

  //-------------------------- AUTOGENERATED END --------------------------
}
