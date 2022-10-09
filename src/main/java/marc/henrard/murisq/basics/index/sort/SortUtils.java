/**
 * Copyright (C) 2022 - present by Marc Henrard.
 */
package marc.henrard.murisq.basics.index.sort;

import com.opengamma.strata.collect.tuple.Pair;

/**
 * Utilities to sort data.
 * 
 * @author Marc Henrard
 */
public class SortUtils {
  
  /**
   * Returns the minimum of an array and the index of the minimum in the original array.
   * 
   * @param x  the array
   * @return the minimum and its index
   */
  public static Pair<Double, Integer> minIndex(double[] x){
    int nbX = x.length;
    int index = -1;
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < nbX; i++) {
      if (x[i] < min) {
        min = x[i];
        index = i;
      }
    }
    return Pair.of(min, index);
  }
  
}
