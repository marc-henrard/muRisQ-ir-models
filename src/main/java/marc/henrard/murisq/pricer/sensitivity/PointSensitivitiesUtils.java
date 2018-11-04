/**
 * Copyright (C) 2018 - present by Marc Henrard.
 */
package marc.henrard.murisq.pricer.sensitivity;

import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivity;
import com.opengamma.strata.pricer.ZeroRateSensitivity;
import com.opengamma.strata.pricer.rate.IborRateSensitivity;

/**
 * Utilities to organize point sensitivities.
 * 
 * @author Marc Henrard
 */
public class PointSensitivitiesUtils {
  
  /**
   * Split the underlying sensitivities by types {@link IborRateSensitivity} and {@link ZeroRateSensitivity}
   * and sort them by fixing and payment date.
   */
  public static Map<IborIndex, List<IborRateSensitivity>> sortCompress(PointSensitivities sensitivities) {
    
    /* Filter */
    Map<IborIndex, List<IborRateSensitivity>> iborSensitivities = new HashMap<>();
    Map<Currency, List<ZeroRateSensitivity>> zeroSensitivities = new HashMap<>();
    for(PointSensitivity s: sensitivities.getSensitivities()) {
      if(s instanceof IborRateSensitivity) {
        IborIndex index = ((IborRateSensitivity) s).getIndex();
        List<IborRateSensitivity> list = iborSensitivities.get(index);
        if(list == null) {
          list = new ArrayList<>();
          iborSensitivities.put(index, list);
        }
        list.add((IborRateSensitivity) s);
      } else {
        if(s instanceof ZeroRateSensitivity) {
          Currency ccy = ((ZeroRateSensitivity) s).getCurrency();
          List<ZeroRateSensitivity> list = zeroSensitivities.get(ccy);
          if(list == null) {
            list = new ArrayList<>();
            zeroSensitivities.put(ccy, list);
          }
          list.add((ZeroRateSensitivity) s);
        }
      }
    }
    /* Sort and compress */
    Map<IborIndex, List<IborRateSensitivity>> iborSensitivitiesSortedCompressed = new HashMap<>();
    for(Entry<IborIndex, List<IborRateSensitivity>> entry: iborSensitivities.entrySet()) {
      iborSensitivitiesSortedCompressed.put(entry.getKey(), sortCompress(entry.getValue()));
    }
    return iborSensitivitiesSortedCompressed;
  }
  
  /**
   * Sort by fixing date and compress the sensitivities.
   * <p>
   * All sensitivities should be with respect to the same index and expressed in the same currency.
   * The sorting is done on the fixing date.
   * The compression is done for sensitivities with the same fixing date where the sensitivities 
   * values are added.
   * 
   * @param raw  the raw list of {@link IborRateSensitivity}
   * @return the sorted and compressed list
   */
  public static List<IborRateSensitivity> sortCompress(List<IborRateSensitivity> raw) {
    if(raw.isEmpty()) {
      return new ArrayList<>();
    }
    /* Sort */
    List<IborRateSensitivity> sorted = new ArrayList<>(raw);
    Collections.sort(sorted,
        (s1, s2) -> s1.getObservation().getFixingDate().compareTo(s2.getObservation().getFixingDate()));
    /* Compress */
    Currency ccy = sorted.get(0).getCurrency();
    IborIndex index = sorted.get(0).getIndex();
    List<IborRateSensitivity> compressed = new ArrayList<>();
    int loops = 0;
    while (loops < sorted.size()) {
      LocalDate fixingDate = sorted.get(loops).getObservation().getFixingDate();
      double sensitivity = 0.0;
      while ((loops < sorted.size()) &&
          sorted.get(loops).getObservation().getFixingDate().equals(fixingDate)) {
        assertEquals(sorted.get(loops).getCurrency(), ccy);
        assertEquals(sorted.get(loops).getIndex(), index);
        sensitivity += sorted.get(loops).getSensitivity();
        loops++;
      }
      compressed.add(IborRateSensitivity.of(sorted.get(loops - 1).getObservation(), ccy, sensitivity));
    }
    return compressed;
  }

}
