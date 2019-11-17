/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.generic;

import org.joda.convert.FromString;
import org.joda.convert.ToString;

import com.opengamma.strata.collect.named.EnumNames;
import com.opengamma.strata.collect.named.NamedEnum;

/**
 * The types of a IBOR fallback.
 */
public enum FallbackType implements NamedEnum {

  /**
   * The Spot Overnight Rate option.
   */
  SPOT_OVERNIGHT,
  /**
   * The Compounded Setting in Arrears Rate option, based on calculation period.
   */
  COMPOUNDED_IN_ARREARS_CALCPERIOD,
  /**
   * The Compounded Setting in Advance Rate option.
   */
  COMPOUNDED_IN_ADVANCE,
  /**
   * The OIS Benchmark options.
   */
  OIS_BENCHMARK,
  /**
   * The Compounded Setting in Arrears Rate, shifted by 2 business days, based on IBOR period.
   */
  COMPOUNDED_IN_ARREARS_2DAYS_IBORPERIOD,
  /**
   * The Compounded Setting in Arrears Rate, shifted by 2 business days, based on calculation period.
   */
  COMPOUNDED_IN_ARREARS_2DAYS_CALCPERIOD;

  private static final EnumNames<FallbackType> NAMES = EnumNames.of(FallbackType.class);

  /**
   * Obtains an instance from the specified name.
   * <p>
   * Parsing handles the mixed case form produced by {@link #toString()} and
   * the upper and lower case variants of the enum constant name.
   * 
   * @param name  the name to parse
   * @return the fallback type
   * @throws IllegalArgumentException if the name is not known
   */
  @FromString
  public static FallbackType of(String name) {
    return NAMES.parse(name);
  }

  /**
   * Returns the formatted string with the type's name.
   * 
   * @return the string
   */
  @ToString
  @Override
  public String toString() {
    return NAMES.format(this);
  }

}
