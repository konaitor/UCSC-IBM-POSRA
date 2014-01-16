package org.jmol.util;

import java.util.Map;

import org.jmol.api.SymmetryInterface;

import javajs.util.T3;
import javajs.util.V3;

/**
 * A class to allow for more complex vibrations and associated 
 * phenomena, such as modulated crystals.
 * 
 * @author Bob Hanson hansonr@stolaf.edu
 * 
 */

public class Vibration extends V3 {

  protected final static double twoPI = 2 * Math.PI;

  /**
   * @param pt 
   * @param t456 
   * @param scale 
   * @param modulationScale 
   */
  public void setTempPoint(T3 pt, T3 t456, float scale, float modulationScale) {
    pt.scaleAdd2((float) (Math.cos(t456.x * twoPI) * scale), this, pt); 
  }

  public void getInfo(Map<String, Object> info) {
    info.put("vibVector", V3.newV(this));
  }

  public SymmetryInterface getUnitCell() {
    // ModulationSet only
    return null;
  }

}
