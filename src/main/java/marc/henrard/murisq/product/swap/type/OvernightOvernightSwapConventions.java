/**
 * Copyright (C) 2019 - present by Marc Henrard.
 */
package marc.henrard.murisq.product.swap.type;

import com.opengamma.strata.collect.named.ExtendedEnum;

/**
 * Market standard Overnight-Overnight swap conventions.
 */
public final class OvernightOvernightSwapConventions {

  /**
   * The extended enum lookup from name to instance.
   */
  static final ExtendedEnum<OvernightOvernightSwapConvention> ENUM_LOOKUP = ExtendedEnum.of(OvernightOvernightSwapConvention.class);

  //-------------------------------------------------------------------------
  /**
   * The 'USD-SOFR-3M-FED-FUND-3M' swap convention.
   * <p>
   * USD SOFR 3M plus spread v EFFR 3M swap.
   * Both legs use day count 'Act/360'.
   */
  public static final OvernightOvernightSwapConvention USD_SOFR_3M_FED_FUND_3M =
      OvernightOvernightSwapConvention.of(StandardOvernightOvernightSwapConventions.USD_SOFR_3M_FED_FUND_3M.getName());
  /**
   * The 'EUR-EONIA-3M-ESTER-3M' swap convention.
   * <p>
   * EUR EONIA 3M plus spread v ESTER 3M swap.
   * Both legs use day count 'Act/360'.
   */
  public static final OvernightOvernightSwapConvention EUR_EONIA_3M_ESTER_3M =
      OvernightOvernightSwapConvention.of(StandardOvernightOvernightSwapConventions.EUR_EONIA_3M_ESTER_3M.getName());

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   */
  private OvernightOvernightSwapConventions() {
  }

}
