package org.jmol.api;

import javajs.util.SB;

import org.jmol.constant.EnumVdw;
import org.jmol.java.BS;
import org.jmol.viewer.JmolStateCreator;
import org.jmol.viewer.Viewer;

public interface JmolDataManager {

  JmolDataManager set(Viewer viewer);

  boolean getDataState(JmolStateCreator stateCreator, SB commands);

  void clear();

  Object[] getData(String type);

  float[][] getDataFloat2D(String label);

  float[][][] getDataFloat3D(String label);

  float getDataFloat(String label, int atomIndex);

  float[] getDataFloatA(String label);

  String getDefaultVdwNameOrData(EnumVdw type, BS bs);

  void deleteModelAtoms(int firstAtomIndex, int nAtoms, BS bsDeleted);

  void setData(String type, Object[] data, int arrayCount, int atomCount,
               int matchField, int matchFieldColumnCount, int field,
               int fieldColumnCount);

}
