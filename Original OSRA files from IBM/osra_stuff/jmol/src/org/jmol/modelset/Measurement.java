/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-12-12 09:50:32 -0600 (Thu, 12 Dec 2013) $
 * $Revision: 19087 $
 *
 * Copyright (C) 2002-2005  The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.modelset;

import java.util.Map;

import org.jmol.util.Escape;
import org.jmol.util.Point3fi;

import javajs.util.A4;
import javajs.util.P3;

import org.jmol.util.Measure;
import org.jmol.atomdata.RadiusData;
import org.jmol.atomdata.RadiusData.EnumType;
import org.jmol.constant.EnumVdw;
import org.jmol.modelset.TickInfo;

import org.jmol.viewer.JC;
import org.jmol.viewer.Viewer;

import javajs.util.List;
import javajs.util.SB;

public class Measurement {

  /*
   * a class to contain a single measurement.
   * 
   */
  
  public String thisID;
  public ModelSet modelSet;
  public int index;
  public boolean isVisible = true;
  public boolean isHidden = false;
  public boolean isTrajectory = false;
  public boolean isValid = true;
  public short colix;
  public short labelColix = -1; // use colix
  public int mad;
  public TickInfo tickInfo;
  public int traceX = Integer.MIN_VALUE, traceY;

  public int count;
  public int[] countPlusIndices = new int[5];
  public Point3fi[] pts;
  public float value;
  
  public String strFormat;
  public Text text;

  private Viewer viewer;
  private String strMeasurement;
  private String type;

  // next three are used by MeaurementRenderer
  
  private boolean tainted;
  public A4 renderAxis;
  public P3 renderArc;
  
  public boolean isTainted() {
    return (tainted && !(tainted = false));
  }
  
  public Measurement setM(ModelSet modelSet, Measurement m, float value, short colix,
                          String strFormat, int index) {
    //value Float.isNaN ==> pending
    this.modelSet = modelSet;
    this.index = index;
    this.viewer = modelSet.viewer;
    this.colix = colix;
    this.strFormat = strFormat;
    if (m != null) {
      tickInfo = m.tickInfo;
      pts = m.pts;
      mad = m.mad;
      thisID = m.thisID;
      text = m.text;
      if (thisID != null && text != null)
        labelColix = text.colix;
    }
    if (pts == null)
      pts = new Point3fi[4];
    int[] indices = (m == null ? null : m.countPlusIndices);
    count = (indices == null ? 0 : indices[0]);
    if (count > 0) {
      System.arraycopy(indices, 0, countPlusIndices, 0, count + 1);
      isTrajectory = modelSet.isTrajectoryMeasurement(countPlusIndices);
    }
    this.value = (Float.isNaN(value) || isTrajectory ? getMeasurement(null) : value);
    formatMeasurement(null);
    return this;
  }

  public Measurement setPoints(ModelSet modelSet, int[] indices, Point3fi[] points,
      TickInfo tickInfo) {
    // temporary holding structure only; -- no viewer
    this.modelSet = modelSet;
    countPlusIndices = indices;
    count = indices[0];
    this.pts = (points == null ? new Point3fi[4] : points);
    viewer = modelSet.viewer;
    this.tickInfo = tickInfo;
    return this;
  }

  public void setCount(int count) {
    setCountM(count);
  }

  protected void setCountM(int count) {
    this.count = countPlusIndices[0] = count;
  }

  public int getAtomIndex(int n) {
    return (n > 0 && n <= count ? countPlusIndices[n] : -1);
  }

  public Point3fi getAtom(int n) {
    int pt = countPlusIndices[n];
    return (pt < -1 ? pts[-2 - pt] : modelSet.atoms[pt]);
  }

  public int getLastIndex() {
    return (count > 0 ? countPlusIndices[count] : -1);
  }

  public String getString() {
    return strMeasurement;
  }

  public String getStringUsing(Viewer viewer, String strFormat, String units) {
    this.viewer = viewer;
    value = getMeasurement(null);
    formatMeasurementAs(strFormat, units, true);
    if (strFormat == null)
      return getInfoAsString(units);
    return strMeasurement;
  }

  public String getStringDetail() {
    return (count == 2 ? "Distance" : count == 3 ? "Angle" : "Torsion")
        + getMeasurementScript(" - ", false) + " : " + value;
  }

  public void refresh(Point3fi[] pts) {
    value = getMeasurement(pts);
    isTrajectory = modelSet.isTrajectoryMeasurement(countPlusIndices);
    formatMeasurement(null);
  }

  /**
   * Used by MouseManager and Picking Manager to build the script
   * 
   * @param sep
   * @param withModelIndex
   * @return measure (atomIndex=1) (atomIndex=2)....
   */
  public String getMeasurementScript(String sep, boolean withModelIndex) {
    String str = "";
    // extra () are needed because of the possible context  symop({1}) ({2})
    boolean asScript = (sep.equals(" "));
    for (int i = 1; i <= count; i++)
      str += (i > 1 ? sep : " ") + getLabel(i, asScript, withModelIndex);
    return str;
  }

  public void formatMeasurementAs(String strFormat, String units,
                                  boolean useDefault) {
    if (strFormat != null && strFormat.length() == 0)
      strFormat = null;
    if (!useDefault && strFormat != null
        && strFormat.indexOf(countPlusIndices[0] + ":") != 0)
      return;
    this.strFormat = strFormat;
    formatMeasurement(units);
  }

  protected void formatMeasurement(String units) {
    tainted = true;
    switch (Float.isNaN(value) ? 0 : count) {
    default:
      strMeasurement = null;
      return;
    case 2:
      strMeasurement = formatDistance(units);
      return;
    case 3:
    case 4:
      strMeasurement = formatAngle(value);
      return;
    }
  }

  public void reformatDistanceIfSelected() {
    if (count != 2)
      return;
    if (viewer.isSelected(countPlusIndices[1])
        && viewer.isSelected(countPlusIndices[2]))
      formatMeasurement(null);
  }

  private String formatDistance(String units) {
    String label = getLabelString();
    if (label == null)
      return "";
    if (units == null) {
      int pt = strFormat.indexOf("//");
      units = (pt >= 0 ? strFormat.substring(pt + 2) : null);
      if (units == null) {
        units = viewer.getMeasureDistanceUnits();
        strFormat += "//" + units;
      }
    }
    units = fixUnits(units);
    int pt = label.indexOf("//");
    if (pt >= 0) {
      label = label.substring(0, pt);
      if (label.length() == 0)
        label = "%VALUE";
    }
    float f = fixValue(units, (label.indexOf("%V") >= 0));
    return formatString(f, units, label);
  }

  private static String fixUnits(String units) {
    if (units.equals("nanometers"))
      return "nm";
    else if (units.equals("picometers"))
      return "pm";
    else if (units.equals("angstroms"))
      return "\u00C5";
    else if (units.equals("vanderwaals") || units.equals("vdw"))
      return "%";
    return units;
  }

  public float fixValue(String units, boolean andRound) {
    if (count != 2)
      return value;
    float dist = value;
    if (units != null) {
      boolean isPercent = units.equals("%");
      if (isPercent || units.endsWith("hz")) {
        int i1 = getAtomIndex(1);
        int i2 = getAtomIndex(2);
        if (i1 >= 0 && i2 >= 0) {
          Atom a1 = (Atom) getAtom(1);
          Atom a2 = (Atom) getAtom(2);
          boolean isDC = (!isPercent && nmrType(units) == NMR_DC);
          type = (isPercent ? "percent" : isDC ? "dipoleCouplingConstant"
              : "J-CouplingConstant");
          dist = (isPercent ? dist
              / (a1.getVanderwaalsRadiusFloat(viewer, EnumVdw.AUTO)
              + a2.getVanderwaalsRadiusFloat(viewer, EnumVdw.AUTO))
              : isDC ? viewer.getNMRCalculation().getDipolarConstantHz(a1, a2)
                  : viewer.getNMRCalculation().getIsoOrAnisoHz(true, a1, a2, units,
                      null));
          isValid = !Float.isNaN(dist);
          if (isPercent)
            units = "pm";
        }
      }
      if (units.equals("nm"))
        return (andRound ? Math.round(dist * 100) / 1000f : dist / 10);
      if (units.equals("pm"))
        return (andRound ? Math.round(dist * 1000) / 10f : dist * 100);
      if (units.equals("au"))
        return (andRound ? Math.round(dist / JC.ANGSTROMS_PER_BOHR * 1000) / 1000f
            : dist / JC.ANGSTROMS_PER_BOHR);
      if (units.endsWith("khz"))
        return (andRound ? Math.round(dist / 10) / 100f : dist / 1000);
    }
    return (andRound ? Math.round(dist * 100) / 100f : dist);
  }

  public final static int NMR_NOT = 0;
  public final static int NMR_DC = 1;
  public final static int NMR_JC = 2;
  
  public static int nmrType(String units) {
    return (units.indexOf("hz") < 0 ? NMR_NOT : units.startsWith("dc_") || units.equals("khz") ? NMR_DC : NMR_JC);
  }

  private String formatAngle(float angle) {
    String label = getLabelString();
    if (label.indexOf("%V") >= 0)
      angle = Math.round(angle * 10) / 10f;
    return formatString(angle, "\u00B0", label);
  }

  private String getLabelString() {
    String s = countPlusIndices[0] + ":";
    String label = null;
    if (strFormat != null) {
      if (strFormat.length() == 0)
        return null;
      label = (strFormat.length() > 2 && strFormat.indexOf(s) == 0 ? strFormat
          : null);
    }
    if (label == null) {
      strFormat = null;
      label = viewer.getDefaultMeasurementLabel(countPlusIndices[0]);
    }
    if (label.indexOf(s) == 0)
      label = label.substring(2);
    if (strFormat == null)
      strFormat = s + label;
    return label;
  }

  private String formatString(float value, String units, String label) {
    return LabelToken.formatLabelMeasure(viewer, this, label, value, units);
  }

  public boolean sameAsPoints(int[] indices, Point3fi[] points) {
    if (count != indices[0])
      return false;
    boolean isSame = true;
    for (int i = 1; i <= count && isSame; i++)
      isSame = (countPlusIndices[i] == indices[i]);
    if (isSame)
      for (int i = 0; i < count && isSame; i++) {
        if (points[i] != null)
          isSame = (this.pts[i].distance(points[i]) < 0.01);
      }
    if (isSame)
      return true;
    switch (count) {
    default:
      return true;
    case 2:
      return sameAsIJ(indices, points, 1, 2) && sameAsIJ(indices, points, 2, 1);
    case 3:
      return sameAsIJ(indices, points, 1, 3) && sameAsIJ(indices, points, 2, 2)
          && sameAsIJ(indices, points, 3, 1);
    case 4:
      return sameAsIJ(indices, points, 1, 4) && sameAsIJ(indices, points, 2, 3)
          && sameAsIJ(indices, points, 3, 2) && sameAsIJ(indices, points, 4, 1);
    }
  }

  private boolean sameAsIJ(int[] atoms, Point3fi[] points, int i, int j) {
    int ipt = countPlusIndices[i];
    int jpt = atoms[j];
    return (ipt >= 0 || jpt >= 0 ? ipt == jpt : this.pts[-2 - ipt]
        .distance(points[-2 - jpt]) < 0.01);
  }

  public boolean sameAs(int i, int j) {
    return sameAsIJ(countPlusIndices, pts, i, j);
  }

  public List<String> toVector(boolean asBitSet) {
    List<String> V = new List<String>();
    for (int i = 1; i <= count; i++)
      V.addLast(getLabel(i, asBitSet, false));
    V.addLast(strMeasurement);
    return V;
  }

  
  public float getMeasurement(Point3fi[] pts) {
    if (countPlusIndices == null)
      return Float.NaN;
    if (count < 2)
      return Float.NaN;
    for (int i = count; --i >= 0;)
      if (countPlusIndices[i + 1] == -1) {
        return Float.NaN;
      }
    Point3fi ptA = (pts == null ? getAtom(1) : pts[0]);
    Point3fi ptB = (pts == null ? getAtom(2) : pts[1]);
    Point3fi ptC;
    switch (count) {
    case 2:
      return ptA.distance(ptB);
    case 3:
      ptC = (pts == null ? getAtom(3) : pts[2]);
      return Measure.computeAngleABC(ptA, ptB, ptC, true);
    case 4:
      ptC = (pts == null ? getAtom(3) : pts[2]);
      Point3fi ptD = (pts == null ? getAtom(4) : pts[3]);
      return Measure.computeTorsion(ptA, ptB, ptC, ptD, true);
    default:
      return Float.NaN;
    }
  }

  public String getLabel(int i, boolean asBitSet, boolean withModelIndex) {
    int atomIndex = countPlusIndices[i];
    // double parens here because of situations like
    //  draw symop({3}), which the compiler will interpret as symop()
    return (atomIndex < 0 ? (withModelIndex ? "modelIndex "
        + getAtom(i).modelIndex + " " : "")
        + Escape.eP(getAtom(i)) : asBitSet ? "(({" + atomIndex + "}))" : viewer
        .getAtomInfo(atomIndex));
  }

  public void setModelIndex(short modelIndex) {
    if (pts == null)
      return;
    for (int i = 0; i < count; i++) {
      if (pts[i] != null)
        pts[i].modelIndex = modelIndex;
    }
  }

  public boolean isValid() {
    // valid: no A-A, A-B-A, A-B-C-B
    return !(sameAs(1, 2) || count > 2 && sameAs(1, 3) || count == 4
        && sameAs(2, 4));
  }

  public static int find(List<Measurement> measurements, Measurement m) {
    int[] indices = m.countPlusIndices;
    Point3fi[] points = m.pts;
    for (int i = measurements.size(); --i >= 0;)
      if (measurements.get(i).sameAsPoints(indices, points))
        return i;
    return -1;
  }

  public boolean isConnected(Atom[] atoms, int count) {
    int atomIndexLast = -1;
    for (int i = 1; i <= count; i++) {
      int atomIndex = getAtomIndex(i);
      if (atomIndex < 0)
        continue;
      if (atomIndexLast >= 0
          && !atoms[atomIndex].isBonded(atoms[atomIndexLast]))
        return false;
      atomIndexLast = atomIndex;
    }
    return true;
  }

  public String getInfoAsString(String units) {
    float f = fixValue(units, true);
    SB sb = new SB();
    sb.append(count == 2 ? (type == null ? "distance" : type) : count == 3 ? "angle" : "dihedral");
    sb.append(" \t").appendF(f);
    sb.append(" \t").append(Escape.eS(strMeasurement));
    for (int i = 1; i <= count; i++)
      sb.append(" \t").append(getLabel(i, false, false));
    if (thisID != null)
      sb.append(" \t").append(thisID);
    return sb.toString();
  }

  public boolean isInRange(RadiusData radiusData, float value) {
    if (radiusData.factorType == EnumType.FACTOR) {
      Atom atom1 = (Atom) getAtom(1);
      Atom atom2 = (Atom) getAtom(2);
      float d = (atom1.getVanderwaalsRadiusFloat(viewer, radiusData.vdwType) + atom2
          .getVanderwaalsRadiusFloat(viewer, radiusData.vdwType))
          * radiusData.value;
      return (value <= d);
    }
    return (radiusData.values[0] == Float.MAX_VALUE || value >= radiusData.values[0]
        && value <= radiusData.values[1]);
  }

  public boolean isIntramolecular(Atom[] atoms, int count) {
    int molecule = -1;
    for (int i = 1; i <= count; i++) {
      int atomIndex = getAtomIndex(i);
      if (atomIndex < 0)
        continue;
      int m = atoms[atomIndex].getMoleculeNumber(false);
      if (molecule < 0)
        molecule = m;
      else if (m != molecule)
        return false;
    }
    return true;
  }

  public boolean isMin(Map<String, Integer> htMin) {
    Atom a1 = (Atom) getAtom(1);
    Atom a2 = (Atom) getAtom(2);
    int d = (int) (a2.distanceSquared(a1)*100);
    String n1 = a1.getAtomName();
    String n2 = a2.getAtomName();
    String key = (n1.compareTo(n2) < 0 ? n1 + n2 : n2 + n1);
    Integer min = htMin.get(key);
    return (min != null && d == min.intValue());
  }

}
