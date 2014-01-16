package org.jmol.api;



import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.viewer.Viewer;

import javajs.util.P3;


public interface MepCalculationInterface {

  public abstract void calculate(VolumeDataInterface volumeData, BS bsSelected,
                                 P3[] atomCoordAngstroms, float[] charges, int calcType);

  public abstract void assignPotentials(Atom[] atoms, float[] potentials, BS bsAromatic, BS bsCarbonyl, BS bsIgnore, String data);

  public abstract float valueFor(float x, float d2, int distanceMode);

  public abstract void set(Viewer viewer);

}
