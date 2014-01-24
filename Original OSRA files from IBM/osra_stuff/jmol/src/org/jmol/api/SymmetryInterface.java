package org.jmol.api;

import java.util.Map;


import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.ModelSet;
import javajs.util.List;
import javajs.util.P3;

import org.jmol.util.Tensor;

import javajs.util.M3;
import javajs.util.M4;
import javajs.util.Matrix;
import javajs.util.P3i;
import javajs.util.T3;
import javajs.util.V3;

public interface SymmetryInterface {

  public SymmetryInterface setPointGroup(
                                     SymmetryInterface pointGroupPrevious,
                                     Atom[] atomset, BS bsAtoms,
                                     boolean haveVibration,
                                     float distanceTolerance,
                                     float linearTolerance);

  public String getPointGroupName();

  public Object getPointGroupInfo(int modelIndex, boolean asDraw,
                                           boolean asInfo, String type,
                                           int index, float scale);

  public void setSpaceGroup(boolean doNormalize);

  public int addSpaceGroupOperation(String xyz, int opId);

  /**
   * set symmetry lattice type using Hall rotations
   * 
   * @param latt SHELX index or character lattice character P I R F A B C S T or \0
   * 
   */
  public void setLattice(int latt);

  public String getSpaceGroupName();

  public Object getSpaceGroup();

  public void setSpaceGroupS(SymmetryInterface symmetry);

  public boolean createSpaceGroup(int desiredSpaceGroupIndex,
                                           String name,
                                           Object object);

  public String getSpaceGroupInfo(String name, SymmetryInterface cellInfo);

  public Object getLatticeDesignation();

  public void setFinalOperations(String name, P3[] atoms,
                                          int iAtomFirst,
                                          int noSymmetryCount, boolean doNormalize);

  public int getSpaceGroupOperationCount();

  public M4 getSpaceGroupOperation(int i);

  public String getSpaceGroupXyz(int i, boolean doNormalize);

  public void newSpaceGroupPoint(int i, P3 atom1, P3 atom2,
                                          int transX, int transY, int transZ);

  public V3[] rotateAxes(int iop, V3[] axes, P3 ptTemp, M3 mTemp);

  public void setUnitCellAllFractionalRelative(boolean TF);
  
  public void setUnitCell(float[] notionalUnitCell);

  public void toCartesian(T3 pt, boolean asAbsolue);

  public Tensor getTensor(float[] parBorU);

  public void toFractional(T3 pt, boolean isAbsolute);

  public P3[] getUnitCellVertices();

  public P3[] getCanonicalCopy(float scale, boolean withOffset);

  public P3 getCartesianOffset();

  public float[] getNotionalUnitCell();

  public float[] getUnitCellAsArray(boolean vectorsOnly);

  public void toUnitCell(P3 pt, P3 offset);

  public void setOffsetPt(P3 pt);

  public void setOffset(int nnn);

  public P3 getUnitCellMultiplier();

  public float getUnitCellInfoType(int infoType);

  public boolean getCoordinatesAreFractional();

  public int[] getCellRange();

  public String getSymmetryInfoString();

  public String[] getSymmetryOperations();

  public boolean haveUnitCell();

  public String getUnitCellInfo();

  public boolean isPeriodic();

  public void setSymmetryInfo(int modelIndex, Map<String, Object> modelAuxiliaryInfo);

  public boolean isBio();
  
  public boolean isPolymer();

  public boolean isSlab();

  public int addBioMoleculeOperation(M4 mat, boolean isReverse);

  public void setMinMaxLatticeParameters(P3i minXYZ, P3i maxXYZ);

  public void setUnitCellOrientation(M3 matUnitCellOrientation);

  public String getMatrixFromString(String xyz, float[] temp, boolean allowScaling, int modDim);

  public boolean checkDistance(P3 f1, P3 f2, float distance, 
                                        float dx, int iRange, int jRange, int kRange, P3 ptOffset);

  public P3 getFractionalOffset();

  public String fcoord(T3 p);

  public void setCartesianOffset(T3 origin);

  public V3[] getUnitCellVectors();

  public SymmetryInterface getUnitCell(T3[] points, boolean setRelative);

  public P3 toSupercell(P3 fpt);

  public boolean isSupercell();

  public String getSymmetryInfoString(Map<String, Object> sginfo, int symOp, String drawID, boolean labelOnly);

  public Map<String, Object> getSpaceGroupInfo(ModelSet modelSet, int modelIndex,
                                               String spaceGroup, int symOp,
                                               P3 pt1, P3 pt2,
                                               String drawID);

  public Object getSymmetryInfo(ModelSet modelSet, int iModel, int iAtom, SymmetryInterface uc, String xyz, int op,
                                P3 pt, P3 pt2, String id, int type);

  public BS notInCentroid(ModelSet modelSet, BS bsAtoms,
                          int[] minmax);

  public boolean checkUnitCell(SymmetryInterface uc, P3 cell, P3 ptTemp, boolean isAbsolute);

  public boolean unitCellEquals(SymmetryInterface uc2);

  public void unitize(P3 ptFrac);

  public void addLatticeVectors(List<float[]> lattvecs);

  public int getLatticeOp();

  public Matrix getOperationRsVs(int iop);

  public int getSiteMultiplicity(P3 a);

  public String addOp(Matrix rs, Matrix vs, Matrix sigma);

  public String getUnitCellState();

}
