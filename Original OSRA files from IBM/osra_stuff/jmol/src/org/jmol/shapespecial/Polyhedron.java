package org.jmol.shapespecial;

import javajs.util.P3;

import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.util.Escape;

public class Polyhedron {
  /**
   * 
   */

  int modelIndex;
  public final Atom centralAtom;
  public final P3[] vertices;
  int ptCenter;
  boolean visible;
  public final short[] normixes;
  public byte[] planes;
  //int planeCount;
  public int visibilityFlags = 0;
  boolean collapsed = false;
  float faceCenterOffset, distanceFactor;
  boolean isFullyLit;

  Polyhedron(Atom centralAtom, int ptCenter, int nPoints, int planeCount,
      P3[] otherAtoms, short[] normixes, byte[] planes, boolean collapsed, float faceCenterOffset, float distanceFactor) {
    this.centralAtom = centralAtom;
    modelIndex = centralAtom.getModelIndex();
    this.ptCenter = ptCenter;
    this.vertices = new P3[nPoints];
    this.visible = true;
    this.normixes = new short[planeCount];
    //this.planeCount = planeCount;
    this.planes = new byte[planeCount * 3];
    for (int i = nPoints; --i >= 0;)
      vertices[i] = otherAtoms[i];
    for (int i = planeCount; --i >= 0;)
      this.normixes[i] = normixes[i];
    for (int i = planeCount * 3; --i >= 0;)
      this.planes[i] = planes[i];
    this.collapsed = collapsed;
    this.faceCenterOffset = faceCenterOffset;
    this.distanceFactor = distanceFactor;
  }

  String getState() {
    BS bs = new BS();
    for (int i = 0; i < ptCenter; i++)
      bs.set(((Atom) vertices[i]).getIndex());
    return "  polyhedra ({" + centralAtom.getIndex() + "}) to "
    + Escape.eBS(bs) + (collapsed ? " collapsed" : "") 
    +  " distanceFactor " + distanceFactor
    +  " faceCenterOffset " + faceCenterOffset 
    + (isFullyLit ? " fullyLit" : "" ) + ";"
    + (visible ? "" : "polyhedra off;") + "\n";
  }
}