/* $RCSfile$
 * $Author: egonw $
 * $Date: 2005-11-10 09:52:44 -0600 (Thu, 10 Nov 2005) $
 * $Revision: 4255 $
 *
 * Copyright (C) 2003-2005  Miguel, Jmol Development, www.jmol.org
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

package org.jmol.symmetry;

import javajs.util.List;
import javajs.util.SB;



import org.jmol.api.SymmetryInterface;
import org.jmol.util.Escape;
import org.jmol.util.Logger;
import org.jmol.util.Measure;
import javajs.util.PT;
import org.jmol.util.Parser;

import javajs.util.M3;
import javajs.util.M4;
import javajs.util.Matrix;
import javajs.util.P3;
import javajs.util.P4;
import org.jmol.util.Quaternion;
import javajs.util.T3;
import javajs.util.V3;
import org.jmol.modelset.ModelSet;
import org.jmol.script.T;

/*
 * Bob Hanson 4/2006
 * 
 * references: International Tables for Crystallography Vol. A. (2002) 
 *
 * http://www.iucr.org/iucr-top/cif/cifdic_html/1/cif_core.dic/Ispace_group_symop_operation_xyz.html
 * http://www.iucr.org/iucr-top/cif/cifdic_html/1/cif_core.dic/Isymmetry_equiv_pos_as_xyz.html
 *
 * LATT : http://macxray.chem.upenn.edu/LATT.pdf thank you, Patrick Carroll
 * 
 * NEVER ACCESS THESE METHODS DIRECTLY! ONLY THROUGH CLASS Symmetry
 */


class SymmetryOperation extends M4 {
  String xyzOriginal;
  String xyz;
  private boolean doNormalize = true;
  boolean isFinalized;
  private int opId;

  private P3 atomTest;
  private P3 temp3 = new P3();
  private P3 temp3b;

  private String[] myLabels;
  int modDim;

// rsvs:
//    [ [(3+modDim)*x + 1]    
//      [(3+modDim)*x + 1]     [ Gamma_R   [0x0]   | Gamma_S
//      [(3+modDim)*x + 1]  ==    [0x0]    Gamma_e | Gamma_d 
//      ...                       [0]       [0]    |   1     ]
//      [0 0 0 0 0...   1] ]
  
  float[] linearRotTrans;
  
  Matrix rsvs;
  private boolean isBio;
  private M3 mComplex;
  private boolean isComplex;
  private Matrix sigma;
  int index;
  
  void setSigma(Matrix sigma) {
    this.sigma = sigma;
  }

  /**
   * @j2sIgnoreSuperConstructor
   * @j2sOverride
   * 
   * @param op
   * @param atoms
   * @param atomIndex
   * @param countOrId
   * @param doNormalize
   */
  SymmetryOperation(SymmetryOperation op, P3[] atoms,
                           int atomIndex, int countOrId, boolean doNormalize) {
    this.doNormalize = doNormalize;
    if (op == null) {
      opId = countOrId;
      return;
    }
    /*
     * externalizes and transforms an operation for use in atom reader
     * 
     */
    xyzOriginal = op.xyzOriginal;
    xyz = op.xyz;
    opId = op.opId;
    modDim = op.modDim;
    myLabels = op.myLabels;
    index = op.index;
    linearRotTrans = op.linearRotTrans;
    sigma = op.sigma;
    setMatrix(false);
    if (!op.isFinalized)
      doFinalize();
    if (doNormalize)
      setOffset(atoms, atomIndex, countOrId);
  }

  

  /**
   * rsvs is the superspace group rotation-translation matrix.
   * It is a (3 + modDim + 1) x (3 + modDim + 1) matrix from 
   * which we can extract all necessary parts;
   * @param isReverse 
   * 
   */
  private void setGamma(boolean isReverse) {
  // standard M4 (this)
  //
  //  [ [rot]   | [trans] 
  //     [0]    |   1     ]
  //
  // becomes for a superspace group
  //
  //  [ Gamma_R   [0x0]   | Gamma_S
  //    [0x0]     Gamma_e | Gamma_d 
  //     [0]       [0]    |   1     ]
    
    int n = 3 + modDim;
    double[][] a = (rsvs = new Matrix(null, n + 1, n + 1)).getArray();
    double[] t = new double[n];
    int pt = 0;
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        a[i][j] = linearRotTrans[pt++];
        if (i < 3 && j >= 3 && a[i][j] != 0)
          isComplex = true;
      }
      t[i] = (isReverse ? -1 : 1) * linearRotTrans[pt++];
    }
    a[n][n] = 1;
    if (isReverse)
      rsvs = rsvs.inverse();
    for (int i = 0; i < n; i++)
      a[i][n] = t[i];
    a = rsvs.getSubmatrix(0,  0,  3,  3).getArray();
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 4; j++)
        setElement(i,  j, (float) (j < 3 ? a[i][j] : t[i]));
    setElement(3,3,1);
  }

  void doFinalize() {
    m03 /= 12;
    m13 /= 12;
    m23 /= 12;
    if (modDim > 0) {
      double[][] a = rsvs.getArray();
      for (int i = a.length - 1; --i >= 0;)
        a[i][3 + modDim] /= 12;
    }
    isFinalized = true;
  }
  
  String getXyz(boolean normalized) {
    return (normalized && modDim == 0 || xyzOriginal == null ? xyz : xyzOriginal);
  }

  void newPoint(P3 atom1, P3 atom2, int transX, int transY, int transZ) {
    temp3.setT(atom1);
    if (isComplex) {
      if (mComplex == null) {
        mComplex = new M3();
        Matrix w = rsvs.getSubmatrix(0, 3, 3, modDim);
        w = w.mul(sigma);
        double[][] a = w.getArray();
        for (int i = 0; i < 3; i++)
          for (int j = 0; j < 3; j++)
            mComplex.setElement(i, j, (float) a[i][j]);
        temp3b = new P3();
      }
      temp3b.setT(atom1);
//      temp3b.x += transX;
//      temp3b.y += transY;
//      temp3b.z += transZ;
      mComplex.transform2(temp3b, temp3b);
//      temp3.x += transX;
//      temp3.y += transY;
//      temp3.z += transZ;
      transform2(temp3, temp3);
      temp3.x += temp3b.x;
      temp3.y += temp3b.y;
      temp3.z += temp3b.z;
//      atom2.set(temp3.x, temp3.y, temp3.z);
//      if (index==3) {
        System.out.println("op=" + index + " " + xyz + " " + transX + " " + transY + " " + transZ);
        //System.out.println(this);
        //System.out.println("rsvs=" + rsvs);
        //System.out.println("r3d=" + rsvs.getSubmatrix(0, 3, 3, modDim));
        //System.out.println("sigma=" + sigma);
        //System.out.println("rot2=" + mComplex);
//      System.out.println("atom1=" + ((org.jmol.adapter.smarter.Atom) atom1).atomName + " " + atom1);
//      System.out.println("atom2=" + atom2);
//      }
    } else {
      transform2(temp3, temp3);
    }
    atom2.set(temp3.x + transX, temp3.y + transY, temp3.z + transZ);
  }

  String dumpInfo() {
    return "\n" + xyz + "\ninternal matrix representation:\n"
        + toString();
  }

  final static String dumpSeitz(M4 s) {
    return new SB().append("{\t").appendI((int) s.m00).append("\t").appendI((int) s.m01)
        .append("\t").appendI((int) s.m02).append("\t").append(twelfthsOf(s.m03)).append("\t}\n")
        .append("{\t").appendI((int) s.m10).append("\t").appendI((int) s.m11).append("\t").appendI((int) s.m12)
        .append("\t").append(twelfthsOf(s.m13)).append("\t}\n")
        .append("{\t").appendI((int) s.m20).append("\t").appendI((int) s.m21).append("\t").appendI((int) s.m22)
        .append("\t").append(twelfthsOf(s.m23)).append("\t}\n").append("{\t0\t0\t0\t1\t}\n").toString();
  }
  
  final static String dumpCanonicalSeitz(M4 s) {
    return new SB().append("{\t").appendI((int) s.m00).append("\t").appendI((int) s.m01)
        .append("\t").appendI((int) s.m02).append("\t").append(twelfthsOf((s.m03+12)%12)).append("\t}\n")
        .append("{\t").appendI((int) s.m10).append("\t").appendI((int) s.m11).append("\t").appendI((int) s.m12)
        .append("\t").append(twelfthsOf((s.m13+12)%12)).append("\t}\n").append("{\t").appendI((int) s.m20)
        .append("\t").appendI((int) s.m21).append("\t")
        .appendI((int) s.m22).append("\t").append(twelfthsOf((s.m23+12)%12)).append("\t}\n")
        .append("{\t0\t0\t0\t1\t}\n").toString();
  }
  
  boolean setMatrixFromXYZ(String xyz, int modDim, boolean allowScaling) {
    /*
     * sets symmetry based on an operator string "x,-y,z+1/2", for example
     * 
     */
    if (xyz == null)
      return false;
    xyzOriginal = xyz;
    xyz = xyz.toLowerCase();
    int n = (modDim + 4) * (modDim + 4);
    this.modDim = modDim;
    if (modDim > 0)
      myLabels = labelsXn;
    linearRotTrans = new float[n];
    boolean isReverse = (xyz.startsWith("!"));
    if (isReverse)
      xyz = xyz.substring(1);
    if (xyz.indexOf("xyz matrix:") == 0) {
      /* note: these terms must in unit cell fractional coordinates!
       * CASTEP CML matrix is in fractional coordinates, but do not take into account
       * hexagonal systems. Thus, in wurtzite.cml, for P 6c 2'c:
       *
       * "transform3": 
       * 
       * -5.000000000000e-1  8.660254037844e-1  0.000000000000e0   0.000000000000e0 
       * -8.660254037844e-1 -5.000000000000e-1  0.000000000000e0   0.000000000000e0 
       *  0.000000000000e0   0.000000000000e0   1.000000000000e0   0.000000000000e0 
       *  0.000000000000e0   0.000000000000e0   0.000000000000e0   1.000000000000e0
       *
       * These are transformations of the STANDARD xyz axes, not the unit cell. 
       * But, then, what coordinate would you feed this? Fractional coordinates of what?
       * The real transform is something like x-y,x,z here.
       * 
       */
      this.xyz = xyz;
      Parser.parseStringInfestedFloatArray(xyz, null, linearRotTrans);        
      return setFromMatrix(null, isReverse);
    }
    if (xyz.indexOf("[[") == 0) {
      xyz = xyz.replace('[',' ').replace(']',' ').replace(',',' ');
      Parser.parseStringInfestedFloatArray(xyz, null, linearRotTrans);
      for (int i = 0; i < n; i++) {
        float v = linearRotTrans[i];
        if (Float.isNaN(v))
          return false;
      }
      setMatrix(isReverse);
      isFinalized = true;
      isBio = (xyz.indexOf("bio") >= 0);
      this.xyz = (isBio ? toString() : getXYZFromMatrix(this, false, false, false));
      return true;
    }
    String strOut = getMatrixFromString(this, xyz, linearRotTrans, allowScaling);
    if (strOut == null)
      return false;
    setMatrix(isReverse);
    this.xyz = (isReverse ? getXYZFromMatrix(this, true, false, false) : strOut);
    //System.out.println("testing " + xyz +  " == " + this.xyz + " " + this + "\n" + Escape.eAF(linearRotTrans));
    if (Logger.debugging)
      Logger.debug("" + this);
    return true;
  }


  private void setMatrix(boolean isReverse) {
    if (linearRotTrans.length > 16) {
      setGamma(isReverse);
    } else {
      setA(linearRotTrans, 0);
      if (isReverse)
        invertM(this);
    }
  }

  boolean setFromMatrix(float[] offset, boolean isReverse) {
    float v = 0;
    int pt = 0;
    myLabels = (modDim == 0 ? labelsXYZ : labelsXn);
    int rowPt = 0;
    int n = 3 + modDim;
    for (int i = 0; rowPt < n; i++) {
      if (Float.isNaN(linearRotTrans[i]))
        return false;
      v = linearRotTrans[i];
      if (Math.abs(v) < 0.00001f)
        v = 0;
      boolean isTrans = ((i + 1) % (n + 1) == 0);
      if (isTrans) {
        if (offset != null) {
          v /= 12;
          if (pt < offset.length)
            v += offset[pt++];
        }
        v = normalizeTwelfths((v < 0 ? -1 : 1) * Math.round(Math.abs(v * 12))
            / 12f, doNormalize);
        rowPt++;
      }
      linearRotTrans[i] = v;
    }
    linearRotTrans[linearRotTrans.length - 1] = 1;
    setMatrix(isReverse);
    isFinalized = (offset == null);
    xyz = getXYZFromMatrix(this, true, false, false);
    //System.out.println("testing " + xyz + " " + this + "\n" + Escape.eAF(linearRotTrans));
    return true;
  }

  /**
   * Convert the Jones-Faithful notation 
   *   "x, -z+1/2, y"  or "x1, x3-1/2, x2, x5+1/2, -x6+1/2, x7..."
   * to a linear array
   * 
   * @param op
   * @param xyz
   * @param linearRotTrans
   * @param allowScaling
   * @return canonized Jones-Faithful string
   */
  static String getMatrixFromString(SymmetryOperation op, String xyz,
                                    float[] linearRotTrans, boolean allowScaling) {
    boolean isDenominator = false;
    boolean isDecimal = false;
    boolean isNegative = false;
    int modDim = (op == null ? 0 : op.modDim);
    int nRows = 4 + modDim;
    boolean doNormalize = (op != null && op.doNormalize);
    linearRotTrans[linearRotTrans.length - 1] = 1;
    String[] myLabels = (op == null || modDim == 0 ? null : op.myLabels);
    if (myLabels == null)
      myLabels = labelsXYZ;
    xyz = xyz.toLowerCase();
    xyz += ",";
    if (modDim > 0)
      for (int i = modDim + 3; --i >= 0;)
        xyz = PT.simpleReplace(xyz, labelsXn[i], labelsXnSub[i]);
    int tpt0 = 0;
    int rowPt = 0;
    char ch;
    float iValue = 0;
    float decimalMultiplier = 1f;
    String strT = "";
    String strOut = "";
    for (int i = 0; i < xyz.length(); i++) {
      switch (ch = xyz.charAt(i)) {
      case '\'':
      case ' ':
      case '{':
      case '}':
      case '!':
        continue;
      case '-':
        isNegative = true;
        continue;
      case '+':
        isNegative = false;
        continue;
      case '/':
        isDenominator = true;
        continue;
      case 'x':
      case 'y':
      case 'z':
      case 'a':
      case 'b':
      case 'c':
      case 'd':
      case 'e':
      case 'f':
      case 'g':
      case 'h':
        int val = (isNegative ? -1 : 1);
        if (allowScaling && iValue != 0) {
          val = (int) iValue;
          iValue = 0;
        }
        tpt0 = rowPt * nRows;
        int ipt = (ch >= 'x' ? ch - 'x' :ch - 'a' + 3);
        linearRotTrans[tpt0 + ipt] = val; 
        strT += plusMinus(strT, val, myLabels[ipt]);
        break;
      case ',':
        // add translation in 12ths
        iValue = normalizeTwelfths(iValue, doNormalize);
        linearRotTrans[tpt0 + nRows - 1] = iValue;
        strT += xyzFraction(iValue, false, true);
        strOut += (strOut == "" ? "" : ",") + strT;
        if (rowPt == nRows - 2)
          return strOut;
        iValue = 0;
        strT = "";
        if (rowPt++ > 2 && modDim == 0) {
          Logger.warn("Symmetry Operation? " + xyz);
          return null;
        }
        break;
      case '.':
        isDecimal = true;
        decimalMultiplier = 1f;
        continue;
      case '0':
        if (!isDecimal && (isDenominator || !allowScaling))
          continue;
        //$FALL-THROUGH$
      default:
        //Logger.debug(isDecimal + " " + ch + " " + iValue);
        int ich = ch - '0';
        if (isDecimal && ich >= 0 && ich <= 9) {
          decimalMultiplier /= 10f;
          if (iValue < 0)
            isNegative = true;
          iValue += decimalMultiplier * ich * (isNegative ? -1 : 1);
          continue;
        }
        if (ich >= 0 && ich <= 9) {
          if (isDenominator) {
            iValue /= ich;
          } else {
            iValue = iValue * 10 + (isNegative ? -1 : 1) * ich;
            isNegative = false;
          }
        } else {
          Logger.warn("symmetry character?" + ch);
        }
      }
      isDecimal = isDenominator = isNegative = false;
    }
    return null;
  }

  private final static String xyzFraction(float n12ths, boolean allPositive, boolean halfOrLess) {
    n12ths = Math.round(n12ths);
    if (allPositive) {
      while (n12ths < 0)
        n12ths += 12f;
    } else if (halfOrLess && n12ths > 6f) {
      n12ths -= 12f;
    }
    String s = twelfthsOf(n12ths);
    return (s.charAt(0) == '0' ? "" : n12ths > 0 ? "+" + s : s);
  }

  private final static String twelfthsOf(float n12ths) {
    String str = "";
    int i12ths = Math.round(n12ths);
    if (i12ths == 12)
      return "1";
    if (i12ths == -12)
      return "-1";
    if (i12ths < 0) {
      i12ths = -i12ths;
      if (i12ths % 12 != 0)
        str = "-";
    }
    int n = i12ths / 12;
    if (n < 1)
      return str + twelfths[i12ths % 12];
    int m = 0;
    switch (i12ths % 12) {
    case 0:
      return str + n;
    case 1:
    case 5:
    case 7:
    case 11:
      m = 12;
      break;
    case 2:
    case 10:
      m = 6;
      break;
    case 3:
    case 9:
      m = 4;
      break;
    case 4:
    case 8:
      m = 3;
      break;
    case 6:
      m = 2;
      break;
    }
    return str + (i12ths * m / 12) + "/" + m;
  }

  private final static String[] twelfths = { "0", "1/12", "1/6", "1/4", "1/3",
  "5/12", "1/2", "7/12", "2/3", "3/4", "5/6", "11/12" };

  private static String plusMinus(String strT, float x, String sx) {
    return (x == 0 ? "" : (x < 0 ? "-" : strT.length() == 0 ? "" : "+") + (x == 1 || x == -1 ? "" : "" + (int) Math.abs(x)) + sx);
  }

  private static float normalizeTwelfths(float iValue, boolean doNormalize) {
    iValue *= 12f;
    if (doNormalize) {
      while (iValue > 6)
        iValue -= 12;
      while (iValue <= -6)
        iValue += 12;
    }
    return iValue;
  }

  final static String[] labelsXYZ = new String[] {"x", "y", "z"};

  final static String[] labelsXn = new String[] {"x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9", "x10", "x11", "x12", "x13"};
  final static String[] labelsXnSub = new String[] {"x", "y", "z", "a",  "b",  "c",  "d",  "e",  "f",  "g",   "h",   "i",   "j"};

  final static String getXYZFromMatrix(M4 mat, boolean is12ths,
                                       boolean allPositive, boolean halfOrLess) {
    String str = "";
    SymmetryOperation op = (mat instanceof SymmetryOperation ? (SymmetryOperation) mat
        : null);
    if (op != null && op.modDim > 0)
      return getXYZFromRsVs(op.rsvs.getRotation(), op.rsvs.getTranslation(), is12ths);
    float[] row = new float[4];
    for (int i = 0; i < 3; i++) {
      int lpt = (i < 3 ? 0 : 3);
      mat.getRow(i, row);
      String term = "";
      for (int j = 0; j < 3; j++)
        if (row[j] != 0)
          term += plusMinus(term, row[j], labelsXYZ[j + lpt]);
      term += xyzFraction((is12ths ? row[3] : row[3] * 12), allPositive,
          halfOrLess);
      str += "," + term;
    }
    return str.substring(1);
  }

  private void setOffset(P3[] atoms, int atomIndex, int count) {
    /*
     * the center of mass of the full set of atoms is moved into the cell with this
     *  
     */
    int i1 = atomIndex;
    int i2 = i1 + count;
    float x = 0;
    float y = 0;
    float z = 0;
    if (atomTest == null)
      atomTest = new P3();
    for (int i = i1; i < i2; i++) {
      newPoint(atoms[i], atomTest, 0, 0, 0);
      x += atomTest.x;
      y += atomTest.y;
      z += atomTest.z;
    }
    
    while (x < -0.001 || x >= count + 0.001) {
      m03 += (x < 0 ? 1 : -1);
      x += (x < 0 ? count : -count);
    }
    while (y < -0.001 || y >= count + 0.001) {
      m13 += (y < 0 ? 1 : -1);
      y += (y < 0 ? count : -count);
    }
    while (z < -0.001 || z >= count + 0.001) {
      m23 += (z < 0 ? 1 : -1);
      z += (z < 0 ? count : -count);
    }
  }

//  // action of this method depends upon setting of unitcell
//  private void transformCartesian(UnitCell unitcell, P3 pt) {
//    unitcell.toFractional(pt, false);
//    transform(pt);
//    unitcell.toCartesian(pt, false);
//
//  }
  
  V3[] rotateAxes(V3[] vectors, UnitCell unitcell, P3 ptTemp, M3 mTemp) {
    V3[] vRot = new V3[3];
    getRotationScale(mTemp);    
    for (int i = vectors.length; --i >=0;) {
      ptTemp.setT(vectors[i]);
      unitcell.toFractional(ptTemp, true);
      mTemp.transform(ptTemp);
      unitcell.toCartesian(ptTemp, true);
      vRot[i] = V3.newV(ptTemp);
    }
    return vRot;
  }
  
  /**
   * 
   * @param modelSet
   *        TODO
   * @param uc
   * @param pt00
   * @param ptTarget
   * @param id
   * @return Object[] containing: [0] xyz (Jones-Faithful calculated from
   *         matrix) [1] xyzOriginal (Provided by calling method) [2] info
   *         ("C2 axis", for example) [3] draw commands [4] translation vector
   *         (fractional) [5] translation vector (Cartesian) [6] inversion point
   *         [7] axis point [8] axis vector (defines plane if angle = 0 [9]
   *         angle of rotation [10] matrix representation
   */
  Object[] getDescription(ModelSet modelSet, SymmetryInterface uc,
                                 P3 pt00, P3 ptTarget, String id) {
    if (!isFinalized)
      doFinalize();
    V3 vtemp = new V3();
    P3 ptemp = new P3();
    P3 pt01 = new P3();
    P3 pt02 = new P3();
    P3 pt03 = new P3();
    V3 ftrans = new V3();
    V3 vtrans = new V3();
    String xyz = (isBio ? xyzOriginal : getXYZFromMatrix(this, false, false,
        false));
    boolean typeOnly = (id == null);
    if (pt00 == null || Float.isNaN(pt00.x))
      pt00 = new P3();
    if (ptTarget != null) {
      // Check to see if the two points only differ by
      // a translation after transformation.
      // If so, add that difference to the matrix transformation 
      pt01.setT(pt00);
      pt02.setT(ptTarget);
      uc.toUnitCell(pt01, ptemp);
      uc.toUnitCell(pt02, ptemp);
      uc.toFractional(pt01, false);
      transform(pt01);
      uc.toCartesian(pt01, false);
      uc.toUnitCell(pt01, ptemp);
      if (pt01.distance(pt02) > 0.1f)
        return null;
      pt01.setT(pt00);
      pt02.setT(ptTarget);
      uc.toFractional(pt01, false);
      uc.toFractional(pt02, false);
      transform(pt01);
      vtrans.sub2(pt02, pt01);
      pt01.set(0, 0, 0);
      pt02.set(0, 0, 0);
    }
    pt01.x = pt02.y = pt03.z = 1;
    pt01.add(pt00);
    pt02.add(pt00);
    pt03.add(pt00);

    P3 p0 = P3.newP(pt00);
    P3 p1 = P3.newP(pt01);
    P3 p2 = P3.newP(pt02);
    P3 p3 = P3.newP(pt03);

    uc.toFractional(p0, false);
    uc.toFractional(p1, false);
    uc.toFractional(p2, false);
    uc.toFractional(p3, false);
    transform2(p0, p0);
    transform2(p1, p1);
    transform2(p2, p2);
    transform2(p3, p3);
    p0.add(vtrans);
    p1.add(vtrans);
    p2.add(vtrans);
    p3.add(vtrans);
    approx(vtrans);
    uc.toCartesian(p0, false);
    uc.toCartesian(p1, false);
    uc.toCartesian(p2, false);
    uc.toCartesian(p3, false);

    V3 v01 = new V3();
    v01.sub2(p1, p0);
    V3 v02 = new V3();
    v02.sub2(p2, p0);
    V3 v03 = new V3();
    v03.sub2(p3, p0);

    vtemp.cross(v01, v02);
    boolean haveinversion = (vtemp.dot(v03) < 0);

    // The first trick is to check cross products to see if we still have a
    // right-hand axis.

    if (haveinversion) {

      // undo inversion for quaternion analysis (requires proper rotations only)

      p1.scaleAdd2(-2, v01, p1);
      p2.scaleAdd2(-2, v02, p2);
      p3.scaleAdd2(-2, v03, p3);

    }

    // The second trick is to use quaternions. Each of the three faces of the
    // frame (xy, yz, and zx)
    // is checked. The helix() function will return data about the local helical
    // axis, and the
    // symop(sym,{0 0 0}) function will return the overall translation.

    Object[] info;
    info = (Object[]) Measure.computeHelicalAxis(null, T.array, pt00, p0,
        Quaternion.getQuaternionFrame(p0, p1, p2).div(
            Quaternion.getQuaternionFrame(pt00, pt01, pt02)));
    P3 pa1 = (P3) info[0];
    V3 ax1 = (V3) info[1];
    int ang1 = (int) Math.abs(PT.approx(((P3) info[3]).x, 1));
    float pitch1 = approxF(((P3) info[3]).y);

    if (haveinversion) {

      // redo inversion

      p1.scaleAdd2(2, v01, p1);
      p2.scaleAdd2(2, v02, p2);
      p3.scaleAdd2(2, v03, p3);

    }

    V3 trans = V3.newVsub(p0, pt00);
    if (trans.length() < 0.1f)
      trans = null;

    // ////////// determination of type of operation from first principles

    P3 ptinv = null; // inverted point for translucent frame
    P3 ipt = null; // inversion center
    P3 pt0 = null; // reflection center

    boolean istranslation = (ang1 == 0);
    boolean isrotation = !istranslation;
    boolean isinversion = false;
    boolean ismirrorplane = false;

    if (isrotation || haveinversion)
      trans = null;

    // handle inversion

    if (haveinversion && istranslation) {

      // simple inversion operation

      ipt = P3.newP(pt00);
      ipt.add(p0);
      ipt.scale(0.5f);
      ptinv = p0;
      isinversion = true;
    } else if (haveinversion) {

      /*
       * 
       * We must convert simple rotations to rotation-inversions; 2-screws to
       * planes and glide planes.
       * 
       * The idea here is that there is a relationship between the axis for a
       * simple or screw rotation of an inverted frame and one for a
       * rotation-inversion. The relationship involves two adjacent equilateral
       * triangles:
       * 
       * 
       *       o 
       *      / \
       *     /   \    i'
       *    /     \ 
       *   /   i   \
       * A/_________\A' 
       *  \         / 
       *   \   j   / 
       *    \     / 
       *     \   / 
       *      \ / 
       *       x
       *      
       * Points i and j are at the centers of the triangles. Points A and A' are
       * the frame centers; an operation at point i, j, x, or o is taking A to
       * A'. Point i is 2/3 of the way from x to o. In addition, point j is half
       * way between i and x.
       * 
       * The question is this: Say you have an rotation/inversion taking A to
       * A'. The relationships are:
       * 
       * 6-fold screw x for inverted frame corresponds to 6-bar at i for actual
       * frame 3-fold screw i for inverted frame corresponds to 3-bar at x for
       * actual frame
       * 
       * The proof of this follows. Consider point x. Point x can transform A to
       * A' as a clockwise 6-fold screw axis. So, say we get that result for the
       * inverted frame. What we need for the real frame is a 6-bar axis
       * instead. Remember, though, that we inverted the frame at A to get this
       * result. The real frame isn't inverted. The 6-bar must do that inversion
       * AND also get the frame to point A' with the same (clockwise) rotation.
       * The key is to see that there is another axis -- at point i -- that does
       * the trick.
       * 
       * Take a look at the angles and distances that arise when you project A
       * through point i. The result is a frame at i'. Since the distance i-i'
       * is the same as i-A (and thus i-A') and the angle i'-i-A' is 60 degrees,
       * point i is also a 6-bar axis transforming A to A'.
       * 
       * Note that both the 6-fold screw axis at x and the 6-bar axis at i are
       * both clockwise.
       * 
       * Similar analysis shows that the 3-fold screw i corresponds to the 3-bar
       * axis at x.
       * 
       * So in each case we just calculate the vector i-j or x-o and then factor
       * appropriately.
       * 
       * The 4-fold case is simpler -- just a parallelogram.
       */

      V3 d = (pitch1 == 0 ? new V3() : ax1);
      float f = 0;
      switch (ang1) {
      case 60: // 6_1 at x to 6-bar at i
        f = 2f / 3f;
        break;
      case 120: // 3_1 at i to 3-bar at x
        f = 2;
        break;
      case 90: // 4_1 to 4-bar at opposite corner
        f = 1;
        break;
      case 180: // 2_1 to mirror plane
        // C2 with inversion is a mirror plane -- but could have a glide
        // component.
        pt0 = P3.newP(pt00);
        pt0.add(d);
        pa1.scaleAdd2(0.5f, d, pt00);
        if (pt0.distance(p0) > 0.1f) {
          trans = V3.newVsub(p0, pt0);
          ptemp.setT(trans);
          uc.toFractional(ptemp, false);
          ftrans.setT(ptemp);
        } else {
          trans = null;
        }
        isrotation = false;
        haveinversion = false;
        ismirrorplane = true;
      }
      if (f != 0) {
        // pa1 = pa1 + ((pt00 - pa1) + (p0 - (pa1 + d))) * f

        vtemp.sub2(pt00, pa1);
        vtemp.add(p0);
        vtemp.sub(pa1);
        vtemp.sub(d);
        vtemp.scale(f);
        pa1.add(vtemp);
        ipt = new P3();
        ipt.scaleAdd2(0.5f, d, pa1);
        ptinv = new P3();
        ptinv.scaleAdd2(-2, ipt, pt00);
        ptinv.scale(-1);
      }

    } else if (trans != null) {

      // get rid of unnecessary translations added to keep most operations
      // within cell 555

      ptemp.setT(trans);
      uc.toFractional(ptemp, false);
      if (approxF(ptemp.x) == 1) {
        ptemp.x = 0;
      }
      if (approxF(ptemp.y) == 1) {
        ptemp.y = 0;
      }
      if (approxF(ptemp.z) == 1) {
        ptemp.z = 0;
      }
      ftrans.setT(ptemp);
      uc.toCartesian(ptemp, false);
      trans.setT(ptemp);
    }

    // fix angle based on direction of axis

    int ang = ang1;
    approx0(ax1);

    if (isrotation) {

      P3 pt1 = new P3();

      vtemp.setT(ax1);

      // draw the lines associated with a rotation

      int ang2 = ang1;
      if (haveinversion) {
        pt1.add2(pa1, vtemp);
        ang2 = Math.round(Measure.computeTorsion(ptinv, pa1, pt1, p0, true));
      } else if (pitch1 == 0) {
        pt1.setT(pa1);
        ptemp.scaleAdd2(1, pt1, vtemp);
        ang2 = Math.round(Measure.computeTorsion(pt00, pa1, ptemp, p0, true));
      } else {
        ptemp.add2(pa1, vtemp);
        pt1.scaleAdd2(0.5f, vtemp, pa1);
        ang2 = Math.round(Measure.computeTorsion(pt00, pa1, ptemp, p0, true));
      }

      if (ang2 != 0)
        ang1 = ang2;
    }

    if (isrotation && !haveinversion && pitch1 == 0) {
      if (ax1.z < 0 || ax1.z == 0 && (ax1.y < 0 || ax1.y == 0 && ax1.x < 0)) {
        ax1.scale(-1);
        ang1 = -ang1;
      }
    }

    // time to get the description

    String info1 = "identity";
    SB draw1 = new SB();
    String drawid;

    if (isinversion) {
      ptemp.setT(ipt);
      uc.toFractional(ptemp, false);
      info1 = "inversion center|" + coord(ptemp);
    } else if (isrotation) {
      if (haveinversion) {
        info1 = "" + (360 / ang) + "-bar axis";
      } else if (pitch1 != 0) {
        info1 = "" + (360 / ang) + "-fold screw axis";
        ptemp.setT(ax1);
        uc.toFractional(ptemp, false);
        info1 += "|translation: " + coord(ptemp);
      } else {
        info1 = "C" + (360 / ang) + " axis";
      }
    } else if (trans != null) {
      String s = " " + coord(ftrans);
      if (istranslation) {
        info1 = "translation:" + s;
      } else if (ismirrorplane) {
        float fx = approxF(ftrans.x);
        float fy = approxF(ftrans.y);
        float fz = approxF(ftrans.z);
        s = " " + coord(ftrans);
        if (fx != 0 && fy != 0 && fz != 0)
          info1 = "d-";
        else if (fx != 0 && fy != 0 || fy != 0 && fz != 0 || fz != 0 && fx != 0)
          info1 = "n-";
        else if (fx != 0)
          info1 = "a-";
        else if (fy != 0)
          info1 = "b-";
        else
          info1 = "c-";
        info1 += "glide plane |translation:" + s;
      }
    } else if (ismirrorplane) {
      info1 = "mirror plane";
    }

    if (haveinversion && !isinversion) {
      ptemp.setT(ipt);
      uc.toFractional(ptemp, false);
      info1 += "|inversion center at " + coord(ptemp);
    }

    String cmds = null;
    if (!typeOnly) {
      drawid = "\ndraw ID " + id + "_";

      // delete previous elements of this user-settable ID

      draw1 = new SB();
      draw1.append(
          ("// " + xyzOriginal + "|" + xyz + "|" + info1).replace('\n', ' '))
          .append("\n");
      draw1.append(drawid).append("* delete");

      // draw the initial frame

      drawLine(draw1, drawid + "frame1X", 0.15f, pt00, pt01, "red");
      drawLine(draw1, drawid + "frame1Y", 0.15f, pt00, pt02, "green");
      drawLine(draw1, drawid + "frame1Z", 0.15f, pt00, pt03, "blue");

      // draw the final frame just a bit fatter and shorter, in case they
      // overlap

      ptemp.sub2(p1, p0);
      ptemp.scaleAdd2(0.9f, ptemp, p0);
      drawLine(draw1, drawid + "frame2X", 0.2f, p0, ptemp, "red");
      ptemp.sub2(p2, p0);
      ptemp.scaleAdd2(0.9f, ptemp, p0);
      drawLine(draw1, drawid + "frame2Y", 0.2f, p0, ptemp, "green");
      ptemp.sub2(p3, p0);
      ptemp.scaleAdd2(0.9f, ptemp, p0);
      drawLine(draw1, drawid + "frame2Z", 0.2f, p0, ptemp, "purple");

      String color;

      if (isrotation) {

        P3 pt1 = new P3();

        color = "red";

        ang = ang1;
        float scale = 1.0f;
        vtemp.setT(ax1);

        // draw the lines associated with a rotation

        if (haveinversion) {
          pt1.add2(pa1, vtemp);
          if (pitch1 == 0) {
            pt1.setT(ipt);
            vtemp.scale(3);
            ptemp.scaleAdd2(-1, vtemp, pa1);
            draw1.append(drawid).append("rotVector2 diameter 0.1 ").append(
                Escape.eP(pa1)).append(Escape.eP(ptemp)).append(" color red");
          }
          scale = p0.distance(pt1);
          draw1.append(drawid).append("rotLine1 ").append(Escape.eP(pt1))
              .append(Escape.eP(ptinv)).append(" color red");
          draw1.append(drawid).append("rotLine2 ").append(Escape.eP(pt1))
              .append(Escape.eP(p0)).append(" color red");
        } else if (pitch1 == 0) {
          boolean isSpecial = (pt00.distance(p0) < 0.2f);
          if (!isSpecial) {
            draw1.append(drawid).append("rotLine1 ").append(Escape.eP(pt00))
                .append(Escape.eP(pa1)).append(" color red");
            draw1.append(drawid).append("rotLine2 ").append(Escape.eP(p0))
                .append(Escape.eP(pa1)).append(" color red");
          }
          vtemp.scale(3);
          ptemp.scaleAdd2(-1, vtemp, pa1);
          draw1.append(drawid).append("rotVector2 diameter 0.1 ").append(
              Escape.eP(pa1)).append(Escape.eP(ptemp)).append(" color red");
          pt1.setT(pa1);
          if (pitch1 == 0 && pt00.distance(p0) < 0.2)
            pt1.scaleAdd2(0.5f, pt1, vtemp);
        } else {
          // screw
          color = "orange";
          draw1.append(drawid).append("rotLine1 ").append(Escape.eP(pt00))
              .append(Escape.eP(pa1)).append(" color red");
          ptemp.add2(pa1, vtemp);
          draw1.append(drawid).append("rotLine2 ").append(Escape.eP(p0))
              .append(Escape.eP(ptemp)).append(" color red");
          pt1.scaleAdd2(0.5f, vtemp, pa1);
        }

        // draw arc arrow

        ptemp.add2(pt1, vtemp);
        if (haveinversion && pitch1 != 0) {
          draw1.append(drawid).append("rotRotLine1").append(Escape.eP(pt1))
              .append(Escape.eP(ptinv)).append(" color red");
          draw1.append(drawid).append("rotRotLine2").append(Escape.eP(pt1))
              .append(Escape.eP(p0)).append(" color red");
        }
        draw1.append(drawid).append(
            "rotRotArrow arrow width 0.10 scale " + scale + " arc ").append(
            Escape.eP(pt1)).append(Escape.eP(ptemp));
        ptemp.setT(haveinversion ? ptinv : pt00);
        if (ptemp.distance(p0) < 0.1f)
          ptemp.set((float) Math.random(), (float) Math.random(), (float) Math
              .random());
        draw1.append(Escape.eP(ptemp));
        ptemp.set(0, ang, 0);
        draw1.append(Escape.eP(ptemp)).append(" color red");
        // draw the main vector

        draw1.append(drawid).append("rotVector1 vector diameter 0.1 ").append(
            Escape.eP(pa1)).append(Escape.eP(vtemp)).append("color ").append(
            color);
      }

      if (ismirrorplane) {

        // indigo arrow across plane from pt00 to pt0

        if (pt00.distance(pt0) > 0.2)
          draw1.append(drawid).append("planeVector arrow ").append(
              Escape.eP(pt00)).append(Escape.eP(pt0)).append(" color indigo");

        // faint inverted frame if trans is not null

        if (trans != null) {
          ptemp.scaleAdd2(-1, p0, p1);
          ptemp.add(pt0);
          drawLine(draw1, drawid + "planeFrameX", 0.15f, pt0, ptemp,
              "translucent red");
          ptemp.scaleAdd2(-1, p0, p2);
          ptemp.add(pt0);
          drawLine(draw1, drawid + "planeFrameY", 0.15f, pt0, ptemp,
              "translucent green");
          ptemp.scaleAdd2(-1, p0, p3);
          ptemp.add(pt0);
          drawLine(draw1, drawid + "planeFrameZ", 0.15f, pt0, ptemp,
              "translucent blue");
        }

        color = (trans == null ? "green" : "blue");

        // ok, now HERE's a good trick. We use the Marching Cubes
        // algorithm to find the intersection points of a plane and the unit
        // cell.
        // We expand the unit cell by 5% in all directions just so we are
        // guaranteed to get cutoffs.

        vtemp.setT(ax1);
        vtemp.normalize();
        // ax + by + cz + d = 0
        // so if a point is in the plane, then N dot X = -d
        float w = -vtemp.x * pa1.x - vtemp.y * pa1.y - vtemp.z * pa1.z;
        P4 plane = P4.new4(vtemp.x, vtemp.y, vtemp.z, w);
        List<Object> v = new List<Object>();
        v.addLast(uc.getCanonicalCopy(1.05f, false));
        modelSet.intersectPlane(plane, v, 3);

        // returns triangles and lines
        for (int i = v.size(); --i >= 0;) {
          P3[] pts = (P3[]) v.get(i);
          draw1.append(drawid).append("planep").appendI(i).append(" ").append(
              Escape.eP(pts[0])).append(Escape.eP(pts[1]));
          if (pts.length == 3)
            draw1.append(Escape.eP(pts[2]));
          draw1.append(" color translucent ").append(color);
        }

        // and JUST in case that does not work, at least draw a circle

        if (v.size() == 0) {
          ptemp.add2(pa1, ax1);
          draw1.append(drawid).append("planeCircle scale 2.0 circle ").append(
              Escape.eP(pa1)).append(Escape.eP(ptemp)).append(
              " color translucent ").append(color).append(" mesh fill");
        }
      }

      if (haveinversion) {

        // draw a faint frame showing the inversion

        draw1.append(drawid).append("invPoint diameter 0.4 ").append(
            Escape.eP(ipt));
        draw1.append(drawid).append("invArrow arrow ").append(Escape.eP(pt00))
            .append(Escape.eP(ptinv)).append(" color indigo");
        if (!isinversion) {
          ptemp.add2(ptinv, pt00);
          ptemp.sub(pt01);
          drawLine(draw1, drawid + "invFrameX", 0.15f, ptinv, ptemp,
              "translucent red");
          ptemp.add2(ptinv, pt00);
          ptemp.sub(pt02);
          drawLine(draw1, drawid + "invFrameY", 0.15f, ptinv, ptemp,
              "translucent green");
          ptemp.add2(ptinv, pt00);
          ptemp.sub(pt03);
          drawLine(draw1, drawid + "invFrameZ", 0.15f, ptinv, ptemp,
              "translucent blue");
        }
      }

      // and display translation if still not {0 0 0}

      if (trans != null) {
        if (pt0 == null)
          pt0 = P3.newP(pt00);
        draw1.append(drawid).append("transVector vector ").append(
            Escape.eP(pt0)).append(Escape.eP(trans));
      }

      // color the targeted atoms opaque and add another frame if necessary

      draw1.append("\nvar pt00 = " + Escape.eP(pt00));
      draw1.append("\nvar p0 = " + Escape.eP(p0));
      draw1.append("\nif (within(0.2,p0).length == 0) {");
      draw1.append("\nvar set2 = within(0.2,p0.uxyz.xyz)");
      draw1.append("\nif (set2) {");
      draw1.append(drawid)
          .append("cellOffsetVector arrow @p0 @set2 color grey");
      draw1.append(drawid).append(
          "offsetFrameX diameter 0.20 @{set2.xyz} @{set2.xyz + ").append(
          Escape.eP(v01)).append("*0.9} color red");
      draw1.append(drawid).append(
          "offsetFrameY diameter 0.20 @{set2.xyz} @{set2.xyz + ").append(
          Escape.eP(v02)).append("*0.9} color green");
      draw1.append(drawid).append(
          "offsetFrameZ diameter 0.20 @{set2.xyz} @{set2.xyz + ").append(
          Escape.eP(v03)).append("*0.9} color purple");
      draw1.append("\n}}\n");

      cmds = draw1.toString();
      draw1 = null;
      drawid = null;
    }
    if (trans == null)
      ftrans = null;
    if (isrotation) {
      if (haveinversion) {
      } else if (pitch1 == 0) {
      } else {
        // screw
        trans = V3.newV(ax1);
        ptemp.setT(trans);
        uc.toFractional(ptemp, false);
        ftrans = V3.newV(ptemp);
      }
    }
    if (ismirrorplane) {
      ang1 = 0;
    }
    if (haveinversion) {
      if (isinversion) {
        pa1 = null;
        ax1 = null;
        trans = null;
        ftrans = null;
      }
    } else if (istranslation) {
      pa1 = null;
      ax1 = null;
    }

    // and display translation if still not {0 0 0}
    if (ax1 != null)
      ax1.normalize();
    M4 m2 = null;
    m2 = M4.newM(this);
    if (vtrans.length() != 0) {
      m2.m03 += vtrans.x;
      m2.m13 += vtrans.y;
      m2.m23 += vtrans.z;
    }
    xyz = (isBio ? m2.toString() : getXYZFromMatrix(m2, false, false, false));
    return new Object[] { xyz, xyzOriginal, info1, cmds, approx0(ftrans),
        approx0(trans), approx0(ipt), approx0(pa1), approx0(ax1),
        Integer.valueOf(ang1), m2, vtrans };
  }

  private String coord(T3 p) {
    approx0(p);
    return (isBio ? p.x + " " + p.y + " " + p.z : fcoord(p));
  }

  private static void drawLine(SB s, String id, float diameter, P3 pt0, P3 pt1,
                        String color) {
    s.append(id).append(" diameter ").appendF(diameter)
        .append(Escape.eP(pt0)).append(Escape.eP(pt1))
        .append(" color ").append(color);
  }

  static String fcoord(T3 p) {
    return fc(p.x) + " " + fc(p.y) + " " + fc(p.z);
  }

  private static String fc(float x) {
    float xabs = Math.abs(x);
    int x24 = (int) approxF(xabs * 24);
    String m = (x < 0 ? "-" : "");
    if (x24%8 != 0)
      return m + twelfthsOf(x24 >> 1);
    return (x24 == 0 ? "0" : x24 == 24 ? m + "1" : m + (x24/8) + "/3");
  }

  private static T3 approx0(T3 pt) {
    if (pt != null) {
      if (Math.abs(pt.x) < 0.0001f)
        pt.x = 0;
      if (Math.abs(pt.y) < 0.0001f)
        pt.y = 0;
      if (Math.abs(pt.z) < 0.0001f)
        pt.z = 0;
    }
    return pt;
  }
  
  private static T3 approx(T3 pt) {
    if (pt != null) {
      pt.x = approxF(pt.x);
      pt.y = approxF(pt.y);
      pt.z = approxF(pt.z);
    }
    return pt;
  }
  
  private static float approxF(float f) {
    return PT.approx(f, 100);
  }

  public static void normalizeTranslation(M4 operation) {
    operation.m03 = ((int)operation.m03 + 12) % 12;
    operation.m13 = ((int)operation.m13 + 12) % 12;
    operation.m23 = ((int)operation.m23 + 12) % 12;    
  }

  public static String getXYZFromRsVs(Matrix rs, Matrix vs, boolean is12ths) {
    double[][] ra = rs.getArray();
    double[][] va = vs.getArray();
    int d = ra.length;
    String s = "";
    for (int i = 0; i < d; i++) {
      s += ",";
      for (int j = 0; j < d; j++) {
        double r = ra[i][j];
        if (r != 0) {
          s += (r < 0 ? "-" : s.endsWith(",") ? "" : "+") + (Math.abs(r) == 1 ? "" : "" + (int) Math.abs(r)) + "x" + (j + 1);
        }
      }
      s += xyzFraction((int) (va[i][0] * (is12ths ? 1 : 12)), false, true);
    }
    return PT.simpleReplace(s.substring(1), ",+", ",");
  }

  @Override
  public String toString() {
    return (rsvs == null ? super.toString() : super.toString() + " " + rsvs.toString());
  }
}
