package org.jmol.api;





import org.jmol.java.BS;

import javajs.util.List;
import javajs.util.P3;


public interface MOCalculationInterface {

  public abstract boolean setupCalculation(VolumeDataInterface volumeData, BS bsSelected,
                                 BS bsExclude,
                                 BS[] bsMolecules,
                                 String calculationType, P3[] atomCoordAngstroms,
                                 int firstAtomOffset, List<int[]> shells,
                                 float[][] gaussians,
                                 int[][] dfCoefMaps, 
                                 Object slaters, float[] moCoefficients,
                                 float[] linearCombination, boolean isSquaredLinear, float[][] coefs, float[] partialCharges, boolean doNormalize, P3[] points, float[] parameters, int testFlags);
  
  public abstract void createCube();
  public abstract float processPt(P3 pt);
}
