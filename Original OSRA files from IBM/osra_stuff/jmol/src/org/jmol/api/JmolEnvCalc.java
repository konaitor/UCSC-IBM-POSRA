package org.jmol.api;

import org.jmol.atomdata.AtomDataServer;
import org.jmol.atomdata.RadiusData;
import org.jmol.java.BS;

import javajs.util.P3;

public interface JmolEnvCalc {

  JmolEnvCalc set(AtomDataServer viewer, int atomCount, short[] mads);

  P3[] getPoints();

  BS getBsSurfaceClone();

  void calculate(RadiusData rd, float maxRadius, BS bsSelected,
                 BS bsIgnore, boolean disregardNeighbors,
                 boolean onlySelectedDots, boolean isSurface,
                 boolean multiModel);
}
