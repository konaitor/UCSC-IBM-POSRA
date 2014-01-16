package org.jmol.api;


import javajs.util.P3;
import javajs.util.P3i;
import javajs.util.P4;
import javajs.util.V3;

public interface VolumeDataInterface {

  public abstract void setVoxelDataAsArray(float[][][] voxelData);

  public abstract float[][][] getVoxelData();

  public abstract int setVoxelCounts(int nPointsX, int nPointsY, int nPointsZ);

  public abstract int[] getVoxelCounts();

  public abstract void setVolumetricVector(int i, float x, float y, float z);

  public abstract float[] getVolumetricVectorLengths();

  public abstract void setVolumetricOrigin(float x, float y, float z);

  public abstract float[] getOriginFloat();

  public abstract void setDataDistanceToPlane(P4 plane);

  public abstract void setPlaneParameters(P4 plane);

  public abstract float calcVoxelPlaneDistance(int x, int y, int z);

  public abstract float distancePointToPlane(P3 pt);

  public abstract void transform(V3 v1, V3 v2);

  public abstract void voxelPtToXYZ(int x, int y, int z, P3 pt);

  public abstract void xyzToVoxelPt(float x, float y, float z, P3i pt3i);

  public abstract float lookupInterpolatedVoxelValue(P3 point, boolean getSource);

  public abstract void filterData(boolean isSquared, float invertCutoff);

  public abstract void capData(P4 plane, float cutoff);

}
