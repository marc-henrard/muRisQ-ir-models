/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.basics.index;

import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.collect.named.ExtendedEnum;

/**
 * Implementation of IBOR-like indices based on compounding.
 * 
 * @author Marc Henrard
 */
public class ComplementIborIndices {

  /**
   * The extended enum lookup from name to instance.
   */
  static final ExtendedEnum<IborIndex> ENUM_LOOKUP = ExtendedEnum.of(IborIndex.class);
  
  /** The 1-month SONIA-linked compounding. */
  public static final IborIndex GBP_SONIACMP_1M = IborIndex.of("GBP-SONIACMP-1M");
  /** The 3-month SONIA-linked compounding. */
  public static final IborIndex GBP_SONIACMP_3M = IborIndex.of("GBP-SONIACMP-3M");
  /** The 6-month SONIA-linked compounding. */
  public static final IborIndex GBP_SONIACMP_6M = IborIndex.of("GBP-SONIACMP-6M");
  
  /** The 1-month ESTER-linked compounding. */
  public static final IborIndex EUR_ESTERCMP_1M = IborIndex.of("EUR-ESTERCMP-1M");
  /** The 3-month ESTER-linked compounding. */
  public static final IborIndex EUR_ESTERCMP_3M = IborIndex.of("EUR-ESTERCMP-3M");
  /** The 6-month ESTER-linked compounding. */
  public static final IborIndex EUR_ESTERCMP_6M = IborIndex.of("EUR-ESTERCMP-6M");
  
  /** The 1-month SOFR-linked compounding. */
  public static final IborIndex USD_SOFRCMP_1M = IborIndex.of("USD-SOFRCMP-1M");
  /** The 3-month ESTER-linked compounding. */
  public static final IborIndex USD_SOFRCMP_3M = IborIndex.of("USD-SOFRCMP-3M");
  /** The 6-month ESTER-linked compounding. */
  public static final IborIndex USD_SOFRCMP_6M = IborIndex.of("USD-SOFRCMP-6M");
  
  /** The 1-month SOFR-linked compounding. */
  public static final IborIndex USD_FED_FUNDCMP_1M = IborIndex.of("USD-FED-FUNDCMP-1M");
  /** The 3-month ESTER-linked compounding. */
  public static final IborIndex USD_FED_FUNDCMP_3M = IborIndex.of("USD-FED-FUNDCMP-3M");
  /** The 6-month ESTER-linked compounding. */
  public static final IborIndex USD_FED_FUNDCMP_6M = IborIndex.of("USD-FED-FUNDCMP-6M");

}
