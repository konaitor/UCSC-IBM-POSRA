/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2006-03-05 12:22:08 -0600 (Sun, 05 Mar 2006) $
 * $Revision: 4545 $
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

package org.jmol.scriptext;

import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jmol.api.Interface;
import org.jmol.api.JmolNMRInterface;
import org.jmol.api.JmolPatternMatcher;
import org.jmol.api.MepCalculationInterface;
import org.jmol.api.MinimizerInterface;
import org.jmol.api.SymmetryInterface;
import org.jmol.atomdata.RadiusData;
import org.jmol.atomdata.RadiusData.EnumType;
import org.jmol.constant.EnumVdw;
import org.jmol.i18n.GT;
import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.AtomCollection;
import org.jmol.modelset.Bond;
import org.jmol.modelset.BondSet;
import org.jmol.modelset.LabelToken;
import org.jmol.modelset.MeasurementData;
import org.jmol.modelset.ModelSet;
import org.jmol.modelset.StateScript;
import org.jmol.modelset.Text;
import org.jmol.modelset.TickInfo;
import org.jmol.script.JmolScriptExtension;
import org.jmol.script.SV;
import org.jmol.script.ScriptCompiler;
import org.jmol.script.ScriptContext;
import org.jmol.script.ScriptEvaluator;
import org.jmol.script.ScriptException;
import org.jmol.script.ScriptInterruption;
import org.jmol.script.ScriptMathProcessor;
import org.jmol.script.T;
import org.jmol.shape.MeshCollection;
import org.jmol.util.BSUtil;
import org.jmol.util.BoxInfo;
import org.jmol.util.C;
import org.jmol.util.ColorEncoder;
import org.jmol.util.Elements;
import org.jmol.util.Escape;
import org.jmol.util.JmolEdge;
import org.jmol.util.Parser;
import org.jmol.util.Point3fi;

import javajs.awt.Font;
import javajs.util.AU;
import javajs.util.List;
import javajs.util.SB;

import org.jmol.util.JmolMolecule;
import org.jmol.util.Logger;
import org.jmol.util.Measure;

import javajs.util.CU;
import javajs.util.M3;
import javajs.util.M4;
import javajs.util.P3;
import javajs.util.P4;
import javajs.util.PT;
import javajs.util.V3;

import org.jmol.util.Quaternion;
import org.jmol.util.SimpleUnitCell;
import org.jmol.util.TempArray;
import org.jmol.util.Txt;
import org.jmol.viewer.FileManager;
import org.jmol.viewer.JC;
import org.jmol.viewer.ShapeManager;
import org.jmol.viewer.StateManager;
import org.jmol.viewer.Viewer;
import org.jmol.viewer.Viewer.ACCESS;

public class ScriptExt implements JmolScriptExtension {
  private Viewer viewer;
  private ScriptEvaluator eval;
  private ShapeManager sm;
  private boolean chk;
  private String fullCommand;
  private String thisCommand;
  private T[] st;
  private int slen;

  final static int ERROR_invalidArgument = 22;

  public ScriptExt() {
    // used by Reflection
  }

  @Override
  public JmolScriptExtension init(Object se) {
    eval = (ScriptEvaluator) se;
    viewer = eval.viewer;
    sm = eval.sm;
    return this;
  }

  @Override
  public boolean dispatch(int iTok, boolean b, T[] st) throws ScriptException {
    chk = eval.chk;
    fullCommand = eval.fullCommand;
    thisCommand = eval.thisCommand;
    slen = eval.slen;
    this.st = st;
    switch (iTok) {
    case T.calculate:
      calculate();
      break;
    case T.capture:
      capture();
      break;
    case T.compare:
      compare();
      break;
    case T.configuration:
      configuration();
      break;
    case T.mapProperty:
      mapProperty();
      break;
    case T.minimize:
      minimize();
      break;
    case T.modulation:
      modulation();
      break;
    case T.plot:
    case T.quaternion:
    case T.ramachandran:
      plot(st);
      break;
    case T.navigate:
      navigate();
      break;
    case T.data:
      data();
      break;
    case T.show:
      show();
      break;
    case T.write:
      write(null);
      break;
    case JC.SHAPE_CGO:
      return cgo();
    case JC.SHAPE_CONTACT:
      return contact();
    case JC.SHAPE_DIPOLES:
      return dipole();
    case JC.SHAPE_DRAW:
      return draw();
    case JC.SHAPE_ISOSURFACE:
    case JC.SHAPE_PLOT3D:
    case JC.SHAPE_PMESH:
      return isosurface(iTok);
    case JC.SHAPE_LCAOCARTOON:
      return lcaoCartoon();
    case JC.SHAPE_MEASURES:
      measure();
      return true;
    case JC.SHAPE_MO:
      return mo(b);
    case JC.SHAPE_POLYHEDRA:
      return polyhedra();
    case JC.SHAPE_STRUTS:
      return struts();
    }
    return false;
  }

  private BS atomExpressionAt(int i) throws ScriptException {
    return eval.atomExpressionAt(i);
  }

  private void error(int err) throws ScriptException {
    eval.error(err);
  }

  private void invArg() throws ScriptException {
    error(ScriptEvaluator.ERROR_invalidArgument);
  }

  private void invPO() throws ScriptException {
    error(ScriptEvaluator.ERROR_invalidParameterOrder);
  }
  
  private Object getShapeProperty(int shapeType, String propertyName) {
    return eval.getShapeProperty(shapeType, propertyName);
  }

  private String parameterAsString(int i) throws ScriptException {
    return eval.parameterAsString(i);
  }

  private P3 centerParameter(int i) throws ScriptException {
    return eval.centerParameter(i);
  }

  private float floatParameter(int i) throws ScriptException {
    return eval.floatParameter(i);
  }

  private P3 getPoint3f(int i, boolean allowFractional)
  throws ScriptException {
    return eval.getPoint3f(i, allowFractional);
  }

  private P4 getPoint4f(int i) throws ScriptException {
    return eval.getPoint4f(i);
  }

  private int intParameter(int index) throws ScriptException {
    return eval.intParameter(index);
  }

  private boolean isFloatParameter(int index) {
    return eval.isFloatParameter(index);
  }

  private String setShapeId(int iShape, int i, boolean idSeen)
      throws ScriptException {
    return eval.setShapeId(iShape, i, idSeen);
  }

  private void setShapeProperty(int shapeType, String propertyName,
                                Object propertyValue) {
    eval.setShapeProperty(shapeType, propertyName, propertyValue);
  }

  private String stringParameter(int index) throws ScriptException {
    return  eval.stringParameter(index);  
  }
  
  private T getToken(int i) throws ScriptException {
    return eval.getToken(i);
  }
  
  private int tokAt(int i) {
    return eval.tokAt(i);
  }

  private boolean cgo() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    sm.loadShape(JC.SHAPE_CGO);
    if (tokAt(1) == T.list && listIsosurface(JC.SHAPE_CGO))
      return false;
    int iptDisplayProperty = 0;
    String thisId = initIsosurface(JC.SHAPE_CGO);
    boolean idSeen = (thisId != null);
    boolean isWild = (idSeen && getShapeProperty(JC.SHAPE_CGO, "ID") == null);
    boolean isInitialized = false;
    List<Object> data = null;
    float translucentLevel = Float.MAX_VALUE;
    eval.colorArgb[0] = Integer.MIN_VALUE;
    int intScale = 0;
    for (int i = eval.iToken; i < slen; ++i) {
      String propertyName = null;
      Object propertyValue = null;
      switch (getToken(i).tok) {
      case T.varray:
      case T.leftsquare:
      case T.spacebeforesquare:
        if (data != null || isWild)
          invArg();
        data = eval.listParameter(i, 2, Integer.MAX_VALUE);
        i = eval.iToken;
        continue;
      case T.scale:
        if (++i >= slen)
          error(ScriptEvaluator.ERROR_numberExpected);
        switch (getToken(i).tok) {
        case T.integer:
          intScale = intParameter(i);
          continue;
        case T.decimal:
          intScale = Math.round(floatParameter(i) * 100);
          continue;
        }
        error(ScriptEvaluator.ERROR_numberExpected);
        break;
      case T.color:
      case T.translucent:
      case T.opaque:
        translucentLevel = eval.getColorTrans(i, false);
        i = eval.iToken;
        idSeen = true;
        continue;
      case T.id:
        thisId = setShapeId(JC.SHAPE_CGO, ++i, idSeen);
        isWild = (getShapeProperty(JC.SHAPE_CGO, "ID") == null);
        i = eval.iToken;
        break;
      default:
        if (!eval.setMeshDisplayProperty(JC.SHAPE_CGO, 0, eval.theTok)) {
          if (eval.theTok == T.times || T.tokAttr(eval.theTok, T.identifier)) {
            thisId = setShapeId(JC.SHAPE_CGO, i, idSeen);
            i = eval.iToken;
            break;
          }
          invArg();
        }
        if (iptDisplayProperty == 0)
          iptDisplayProperty = i;
        i = eval.iToken;
        continue;
      }
      idSeen = (eval.theTok != T.delete);
      if (data != null && !isInitialized) {
        propertyName = "points";
        propertyValue = Integer.valueOf(intScale);
        isInitialized = true;
        intScale = 0;
      }
      if (propertyName != null)
        setShapeProperty(JC.SHAPE_CGO, propertyName, propertyValue);
    }
    eval.finalizeObject(JC.SHAPE_CGO, eval.colorArgb[0], translucentLevel, intScale,
        data != null, data, iptDisplayProperty, null);
    return true;
  }

  private boolean contact() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    sm.loadShape(JC.SHAPE_CONTACT);
    if (tokAt(1) == T.list && listIsosurface(JC.SHAPE_CONTACT))
      return false;
    int iptDisplayProperty = 0;
    eval.iToken = 1;
    String thisId = initIsosurface(JC.SHAPE_CONTACT);
    boolean idSeen = (thisId != null);
    boolean isWild = (idSeen && getShapeProperty(JC.SHAPE_CONTACT, "ID") == null);
    BS bsA = null;
    BS bsB = null;
    BS bs = null;
    RadiusData rd = null;
    float[] params = null;
    boolean colorDensity = false;
    SB sbCommand = new SB();
    int minSet = Integer.MAX_VALUE;
    int displayType = T.plane;
    int contactType = T.nada;
    float distance = Float.NaN;
    float saProbeRadius = Float.NaN;
    boolean localOnly = true;
    Boolean intramolecular = null;
    Object userSlabObject = null;
    int colorpt = 0;
    boolean colorByType = false;
    int tok;
    boolean okNoAtoms = (eval.iToken > 1);
    for (int i = eval.iToken; i < slen; ++i) {
      switch (tok = getToken(i).tok) {
      // these first do not need atoms defined
      default:
        okNoAtoms = true;
        if (!eval.setMeshDisplayProperty(JC.SHAPE_CONTACT, 0, eval.theTok)) {
          if (eval.theTok != T.times && !T.tokAttr(eval.theTok, T.identifier))
            invArg();
          thisId = setShapeId(JC.SHAPE_CONTACT, i, idSeen);
          i = eval.iToken;
          break;
        }
        if (iptDisplayProperty == 0)
          iptDisplayProperty = i;
        i = eval.iToken;
        continue;
      case T.id:
        okNoAtoms = true;
        setShapeId(JC.SHAPE_CONTACT, ++i, idSeen);
        isWild = (getShapeProperty(JC.SHAPE_CONTACT, "ID") == null);
        i = eval.iToken;
        break;
      case T.color:
        switch (tokAt(i + 1)) {
        case T.density:
          tok = T.nada;
          colorDensity = true;
          sbCommand.append(" color density");
          i++;
          break;
        case T.type:
          tok = T.nada;
          colorByType = true;
          sbCommand.append(" color type");
          i++;
          break;
        }
        if (tok == T.nada)
          break;
        //$FALL-THROUGH$ to translucent
      case T.translucent:
      case T.opaque:
        okNoAtoms = true;
        if (colorpt == 0)
          colorpt = i;
        eval.setMeshDisplayProperty(JC.SHAPE_CONTACT, i, eval.theTok);
        i = eval.iToken;
        break;
      case T.slab:
        okNoAtoms = true;
        userSlabObject = getCapSlabObject(i, false);
        setShapeProperty(JC.SHAPE_CONTACT, "slab", userSlabObject);
        i = eval.iToken;
        break;

      // now after this you need atoms

      case T.density:
        colorDensity = true;
        sbCommand.append(" density");
        if (isFloatParameter(i + 1)) {
          if (params == null)
            params = new float[1];
          params[0] = -Math.abs(floatParameter(++i));
          sbCommand.append(" " + -params[0]);
        }
        break;
      case T.resolution:
        float resolution = floatParameter(++i);
        if (resolution > 0) {
          sbCommand.append(" resolution ").appendF(resolution);
          setShapeProperty(JC.SHAPE_CONTACT, "resolution", Float
              .valueOf(resolution));
        }
        break;
      case T.within:
      case T.distance:
        distance = floatParameter(++i);
        sbCommand.append(" within ").appendF(distance);
        break;
      case T.plus:
      case T.integer:
      case T.decimal:
        rd = eval.encodeRadiusParameter(i, false, false);
        sbCommand.append(" ").appendO(rd);
        i = eval.iToken;
        break;
      case T.intermolecular:
      case T.intramolecular:
        intramolecular = (tok == T.intramolecular ? Boolean.TRUE
            : Boolean.FALSE);
        sbCommand.append(" ").appendO(eval.theToken.value);
        break;
      case T.minset:
        minSet = intParameter(++i);
        break;
      case T.hbond:
      case T.clash:
      case T.vanderwaals:
        contactType = tok;
        sbCommand.append(" ").appendO(eval.theToken.value);
        break;
      case T.sasurface:
        if (isFloatParameter(i + 1))
          saProbeRadius = floatParameter(++i);
        //$FALL-THROUGH$
      case T.cap:
      case T.nci:
      case T.surface:
        localOnly = false;
        //$FALL-THROUGH$
      case T.trim:
      case T.full:
      case T.plane:
      case T.connect:
        displayType = tok;
        sbCommand.append(" ").appendO(eval.theToken.value);
        if (tok == T.sasurface)
          sbCommand.append(" ").appendF(saProbeRadius);
        break;
      case T.parameters:
        params = eval.floatParameterSet(++i, 1, 10);
        i = eval.iToken;
        break;
      case T.bitset:
      case T.expressionBegin:
        if (isWild || bsB != null)
          invArg();
        bs = BSUtil.copy(atomExpressionAt(i));
        i = eval.iToken;
        if (bsA == null)
          bsA = bs;
        else
          bsB = bs;
        sbCommand.append(" ").append(Escape.eBS(bs));
        break;
      }
      idSeen = (eval.theTok != T.delete);
    }
    if (!okNoAtoms && bsA == null)
      error(ScriptEvaluator.ERROR_endOfStatementUnexpected);
    if (chk)
      return false;

    if (bsA != null) {
      // bond mode, intramolec set here
      if (contactType == T.vanderwaals && rd == null)
        rd = new RadiusData(null, 0, EnumType.OFFSET, EnumVdw.AUTO);
      RadiusData rd1 = (rd == null ? new RadiusData(null, 0.26f,
          EnumType.OFFSET, EnumVdw.AUTO) : rd);
      if (displayType == T.nci && bsB == null && intramolecular != null
          && intramolecular.booleanValue())
        bsB = bsA;
      else
        bsB = setContactBitSets(bsA, bsB, localOnly, distance, rd1, true);
      switch (displayType) {
      case T.cap:
      case T.sasurface:
        BS bsSolvent = eval.lookupIdentifierValue("solvent");
        bsA.andNot(bsSolvent);
        bsB.andNot(bsSolvent);
        bsB.andNot(bsA);
        break;
      case T.surface:
        bsB.andNot(bsA);
        break;
      case T.nci:
        if (minSet == Integer.MAX_VALUE)
          minSet = 100;
        setShapeProperty(JC.SHAPE_CONTACT, "minset", Integer.valueOf(minSet));
        sbCommand.append(" minSet ").appendI(minSet);
        if (params == null)
          params = new float[] { 0.5f, 2 };
      }

      if (intramolecular != null) {
        params = (params == null ? new float[2] : AU.ensureLengthA(
            params, 2));
        params[1] = (intramolecular.booleanValue() ? 1 : 2);
      }

      if (params != null)
        sbCommand.append(" parameters ").append(Escape.eAF(params));

      // now adjust for type -- HBOND or HYDROPHOBIC or MISC
      // these are just "standard shortcuts" they are not necessary at all
      setShapeProperty(JC.SHAPE_CONTACT, "set", new Object[] {
          Integer.valueOf(contactType), Integer.valueOf(displayType),
          Boolean.valueOf(colorDensity), Boolean.valueOf(colorByType), bsA,
          bsB, rd, Float.valueOf(saProbeRadius), params, sbCommand.toString() });
      if (colorpt > 0)
        eval.setMeshDisplayProperty(JC.SHAPE_CONTACT, colorpt, 0);
    }
    if (iptDisplayProperty > 0) {
      if (!eval.setMeshDisplayProperty(JC.SHAPE_CONTACT, iptDisplayProperty, 0))
        invArg();
    }
    if (userSlabObject != null && bsA != null)
      setShapeProperty(JC.SHAPE_CONTACT, "slab", userSlabObject);
    if (bsA != null && (displayType == T.nci || localOnly)) {
      Object volume = getShapeProperty(JC.SHAPE_CONTACT, "volume");
      if (PT.isAD(volume)) {
        double[] vs = (double[]) volume;
        double v = 0;
        for (int i = 0; i < vs.length; i++)
          v += Math.abs(vs[i]);
        volume = Float.valueOf((float) v);
      }
      int nsets = ((Integer) getShapeProperty(JC.SHAPE_CONTACT, "nSets"))
          .intValue();

      if (colorDensity || displayType != T.trim) {
        showString((nsets == 0 ? "" : nsets + " contacts with ")
            + "net volume " + volume + " A^3");
      }
    }
    return true;
  }

  private boolean dipole() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    // dipole intWidth floatMagnitude OFFSET floatOffset {atom1} {atom2}
    String propertyName = null;
    Object propertyValue = null;
    boolean iHaveAtoms = false;
    boolean iHaveCoord = false;
    boolean idSeen = false;

    sm.loadShape(JC.SHAPE_DIPOLES);
    if (tokAt(1) == T.list && listIsosurface(JC.SHAPE_DIPOLES))
      return false;
    setShapeProperty(JC.SHAPE_DIPOLES, "init", null);
    if (slen == 1) {
      setShapeProperty(JC.SHAPE_DIPOLES, "thisID", null);
      return false;
    }
    for (int i = 1; i < slen; ++i) {
      propertyName = null;
      propertyValue = null;
      switch (getToken(i).tok) {
      case T.on:
        propertyName = "on";
        break;
      case T.off:
        propertyName = "off";
        break;
      case T.delete:
        propertyName = "delete";
        break;
      case T.integer:
      case T.decimal:
        propertyName = "value";
        propertyValue = Float.valueOf(floatParameter(i));
        break;
      case T.bitset:
        propertyName = "atomBitset";
        //$FALL-THROUGH$
      case T.expressionBegin:
        if (propertyName == null)
          propertyName = (iHaveAtoms || iHaveCoord ? "endSet" : "startSet");
        propertyValue = atomExpressionAt(i);
        i = eval.iToken;
        iHaveAtoms = true;
        break;
      case T.leftbrace:
      case T.point3f:
        // {X, Y, Z}
        P3 pt = getPoint3f(i, true);
        i = eval.iToken;
        propertyName = (iHaveAtoms || iHaveCoord ? "endCoord" : "startCoord");
        propertyValue = pt;
        iHaveCoord = true;
        break;
      case T.bonds:
        propertyName = "bonds";
        break;
      case T.calculate:
        propertyName = "calculate";
        break;
      case T.id:
        setShapeId(JC.SHAPE_DIPOLES, ++i, idSeen);
        i = eval.iToken;
        break;
      case T.cross:
        propertyName = "cross";
        propertyValue = Boolean.TRUE;
        break;
      case T.nocross:
        propertyName = "cross";
        propertyValue = Boolean.FALSE;
        break;
      case T.offset:
        float v = floatParameter(++i);
        if (eval.theTok == T.integer) {
          propertyName = "offsetPercent";
          propertyValue = Integer.valueOf((int) v);
        } else {
          propertyName = "offset";
          propertyValue = Float.valueOf(v);
        }
        break;
      case T.offsetside:
        propertyName = "offsetSide";
        propertyValue = Float.valueOf(floatParameter(++i));
        break;

      case T.val:
        propertyName = "value";
        propertyValue = Float.valueOf(floatParameter(++i));
        break;
      case T.width:
        propertyName = "width";
        propertyValue = Float.valueOf(floatParameter(++i));
        break;
      default:
        if (eval.theTok == T.times || T.tokAttr(eval.theTok, T.identifier)) {
          setShapeId(JC.SHAPE_DIPOLES, i, idSeen);
          i = eval.iToken;
          break;
        }
        invArg();
      }
      idSeen = (eval.theTok != T.delete && eval.theTok != T.calculate);
      if (propertyName != null)
        setShapeProperty(JC.SHAPE_DIPOLES, propertyName, propertyValue);
    }
    if (iHaveCoord || iHaveAtoms)
      setShapeProperty(JC.SHAPE_DIPOLES, "set", null);
    return true;
  }

  private boolean draw() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    sm.loadShape(JC.SHAPE_DRAW);
    switch (tokAt(1)) {
    case T.list:
      if (listIsosurface(JC.SHAPE_DRAW))
        return false;
      break;
    case T.pointgroup:
      pointGroup();
      return false;
    case T.helix:
    case T.quaternion:
    case T.ramachandran:
      plot(st);
      return false;
    }
    boolean havePoints = false;
    boolean isInitialized = false;
    boolean isSavedState = false;
    boolean isIntersect = false;
    boolean isFrame = false;
    P4 plane;
    int tokIntersect = 0;
    float translucentLevel = Float.MAX_VALUE;
    eval.colorArgb[0] = Integer.MIN_VALUE;
    int intScale = 0;
    String swidth = "";
    int iptDisplayProperty = 0;
    P3 center = null;
    String thisId = initIsosurface(JC.SHAPE_DRAW);
    boolean idSeen = (thisId != null);
    boolean isWild = (idSeen && getShapeProperty(JC.SHAPE_DRAW, "ID") == null);
    int[] connections = null;
    int iConnect = 0;
    for (int i = eval.iToken; i < slen; ++i) {
      String propertyName = null;
      Object propertyValue = null;
      switch (getToken(i).tok) {
      case T.unitcell:
      case T.boundbox:
        if (chk)
          break;
        List<Object> vp = viewer.getPlaneIntersection(eval.theTok, null,
            intScale / 100f, 0);
        intScale = 0;
        propertyName = "polygon";
        propertyValue = vp;
        havePoints = true;
        break;
      case T.connect:
        connections = new int[4];
        iConnect = 4;
        float[] farray = eval.floatParameterSet(++i, 4, 4);
        i = eval.iToken;
        for (int j = 0; j < 4; j++)
          connections[j] = (int) farray[j];
        havePoints = true;
        break;
      case T.bonds:
      case T.atoms:
        if (connections == null
            || iConnect > (eval.theTok == T.bondcount ? 2 : 3)) {
          iConnect = 0;
          connections = new int[] { -1, -1, -1, -1 };
        }
        connections[iConnect++] = atomExpressionAt(++i).nextSetBit(0);
        i = eval.iToken;
        connections[iConnect++] = (eval.theTok == T.bonds ? atomExpressionAt(
            ++i).nextSetBit(0) : -1);
        i = eval.iToken;
        havePoints = true;
        break;
      case T.slab:
        switch (getToken(++i).tok) {
        case T.dollarsign:
          propertyName = "slab";
          propertyValue = eval.objectNameParameter(++i);
          i = eval.iToken;
          havePoints = true;
          break;
        default:
          invArg();
        }
        break;
      case T.intersection:
        switch (getToken(++i).tok) {
        case T.unitcell:
        case T.boundbox:
          tokIntersect = eval.theTok;
          isIntersect = true;
          continue;
        case T.dollarsign:
          propertyName = "intersect";
          propertyValue = eval.objectNameParameter(++i);
          i = eval.iToken;
          isIntersect = true;
          havePoints = true;
          break;
        default:
          invArg();
        }
        break;
      case T.polygon:
        propertyName = "polygon";
        havePoints = true;
        List<Object> v = new List<Object>();
        int nVertices = 0;
        int nTriangles = 0;
        P3[] points = null;
        List<SV> vpolygons = null;
        if (eval.isArrayParameter(++i)) {
          points = eval.getPointArray(i, -1);
          nVertices = points.length;
        } else {
          nVertices = Math.max(0, intParameter(i));
          points = new P3[nVertices];
          for (int j = 0; j < nVertices; j++)
            points[j] = centerParameter(++eval.iToken);
        }
        switch (getToken(++eval.iToken).tok) {
        case T.matrix3f:
        case T.matrix4f:
          SV sv = SV.newT(eval.theToken);
          sv.toArray();
          vpolygons = sv.getList();
          nTriangles = vpolygons.size();
          break;
        case T.varray:
          vpolygons = ((SV) eval.theToken).getList();
          nTriangles = vpolygons.size();
          break;
        default:
          nTriangles = Math.max(0, intParameter(eval.iToken));
        }
        int[][] polygons = AU.newInt2(nTriangles);
        for (int j = 0; j < nTriangles; j++) {
          float[] f = (vpolygons == null ? eval.floatParameterSet(++eval.iToken, 3,
              4) : SV.flistValue(vpolygons.get(j), 0));
          if (f.length < 3 || f.length > 4)
            invArg();
          polygons[j] = new int[] { (int) f[0], (int) f[1], (int) f[2],
              (f.length == 3 ? 7 : (int) f[3]) };
        }
        if (nVertices > 0) {
          v.addLast(points);
          v.addLast(polygons);
        } else {
          v = null;
        }
        propertyValue = v;
        i = eval.iToken;
        break;
      case T.symop:
        String xyz = null;
        int iSym = 0;
        plane = null;
        P3 target = null;
        switch (tokAt(++i)) {
        case T.string:
          xyz = stringParameter(i);
          break;
        case T.matrix4f:
          xyz = SV.sValue(getToken(i));
          break;
        case T.integer:
        default:
          if (!eval.isCenterParameter(i))
            iSym = intParameter(i++);
          if (eval.isCenterParameter(i))
            center = centerParameter(i);
          if (eval.isCenterParameter(eval.iToken + 1))
            target = centerParameter(++eval.iToken);
          if (chk)
            return false;
          i = eval.iToken;
        }
        BS bsAtoms = null;
        if (center == null && i + 1 < slen) {
          center = centerParameter(++i);
          // draw ID xxx symop [n or "x,-y,-z"] [optional {center}]
          // so we also check here for the atom set to get the right model
          bsAtoms = (tokAt(i) == T.bitset || tokAt(i) == T.expressionBegin ? atomExpressionAt(i)
              : null);
          i = eval.iToken + 1;
        }
        eval.checkLast(eval.iToken);
        if (!chk)
          eval.runScript((String) viewer.getSymmetryInfo(bsAtoms, xyz, iSym, center,
              target, thisId, T.draw));
        return false;
      case T.frame:
        isFrame = true;
        // draw ID xxx frame {center} {q1 q2 q3 q4}
        continue;
      case T.leftbrace:
      case T.point4f:
      case T.point3f:
        // {X, Y, Z}
        if (eval.theTok == T.point4f || !eval.isPoint3f(i)) {
          propertyValue = getPoint4f(i);
          if (isFrame) {
            eval.checkLast(eval.iToken);
            if (!chk)
              eval.runScript((Quaternion.newP4((P4) propertyValue)).draw(
                  (thisId == null ? "frame" : thisId), " " + swidth,
                  (center == null ? new P3() : center), intScale / 100f));
            return false;
          }
          propertyName = "planedef";
        } else {
          propertyValue = center = getPoint3f(i, true);
          propertyName = "coord";
        }
        i = eval.iToken;
        havePoints = true;
        break;
      case T.hkl:
      case T.plane:
        if (!havePoints && !isIntersect && tokIntersect == 0
            && eval.theTok != T.hkl) {
          propertyName = "plane";
          break;
        }
        if (eval.theTok == T.plane) {
          plane = eval.planeParameter(++i);
        } else {
          plane = eval.hklParameter(++i);
        }
        i = eval.iToken;
        if (tokIntersect != 0) {
          if (chk)
            break;
          List<Object> vpc = viewer.getPlaneIntersection(tokIntersect,
              plane, intScale / 100f, 0);
          intScale = 0;
          propertyName = "polygon";
          propertyValue = vpc;
        } else {
          propertyValue = plane;
          propertyName = "planedef";
        }
        havePoints = true;
        break;
      case T.linedata:
        propertyName = "lineData";
        propertyValue = eval.floatParameterSet(++i, 0, Integer.MAX_VALUE);
        i = eval.iToken;
        havePoints = true;
        break;
      case T.bitset:
      case T.expressionBegin:
        propertyName = "atomSet";
        propertyValue = atomExpressionAt(i);
        if (isFrame)
          center = centerParameter(i);
        i = eval.iToken;
        havePoints = true;
        break;
      case T.varray:
        propertyName = "modelBasedPoints";
        propertyValue = SV.listValue(eval.theToken);
        havePoints = true;
        break;
      case T.spacebeforesquare:
      case T.comma:
        break;
      case T.leftsquare:
        // [x y] or [x y %]
        propertyValue = eval.xypParameter(i);
        if (propertyValue != null) {
          i = eval.iToken;
          propertyName = "coord";
          havePoints = true;
          break;
        }
        if (isSavedState)
          invArg();
        isSavedState = true;
        break;
      case T.rightsquare:
        if (!isSavedState)
          invArg();
        isSavedState = false;
        break;
      case T.reverse:
        propertyName = "reverse";
        break;
      case T.string:
        propertyValue = stringParameter(i);
        propertyName = "title";
        break;
      case T.vector:
        propertyName = "vector";
        break;
      case T.length:
        propertyValue = Float.valueOf(floatParameter(++i));
        propertyName = "length";
        break;
      case T.decimal:
        // $drawObject
        propertyValue = Float.valueOf(floatParameter(i));
        propertyName = "length";
        break;
      case T.modelindex:
        propertyName = "modelIndex";
        propertyValue = Integer.valueOf(intParameter(++i));
        break;
      case T.integer:
        if (isSavedState) {
          propertyName = "modelIndex";
          propertyValue = Integer.valueOf(intParameter(i));
        } else {
          intScale = intParameter(i);
        }
        break;
      case T.scale:
        if (++i >= slen)
          error(ScriptEvaluator.ERROR_numberExpected);
        switch (getToken(i).tok) {
        case T.integer:
          intScale = intParameter(i);
          continue;
        case T.decimal:
          intScale = Math.round(floatParameter(i) * 100);
          continue;
        }
        error(ScriptEvaluator.ERROR_numberExpected);
        break;
      case T.id:
        thisId = setShapeId(JC.SHAPE_DRAW, ++i, idSeen);
        isWild = (getShapeProperty(JC.SHAPE_DRAW, "ID") == null);
        i = eval.iToken;
        break;
      case T.modelbased:
        propertyName = "fixed";
        propertyValue = Boolean.FALSE;
        break;
      case T.fixed:
        propertyName = "fixed";
        propertyValue = Boolean.TRUE;
        break;
      case T.offset:
        P3 pt = getPoint3f(++i, true);
        i = eval.iToken;
        propertyName = "offset";
        propertyValue = pt;
        break;
      case T.crossed:
        propertyName = "crossed";
        break;
      case T.width:
        propertyValue = Float.valueOf(floatParameter(++i));
        propertyName = "width";
        swidth = propertyName + " " + propertyValue;
        break;
      case T.line:
        propertyName = "line";
        propertyValue = Boolean.TRUE;
        break;
      case T.curve:
        propertyName = "curve";
        break;
      case T.arc:
        propertyName = "arc";
        break;
      case T.arrow:
        propertyName = "arrow";
        break;
      case T.circle:
        propertyName = "circle";
        break;
      case T.cylinder:
        propertyName = "cylinder";
        break;
      case T.vertices:
        propertyName = "vertices";
        break;
      case T.nohead:
        propertyName = "nohead";
        break;
      case T.barb:
        propertyName = "isbarb";
        break;
      case T.rotate45:
        propertyName = "rotate45";
        break;
      case T.perpendicular:
        propertyName = "perp";
        break;
      case T.radius:
      case T.diameter:
        boolean isRadius = (eval.theTok == T.radius);
        float f = floatParameter(++i);
        if (isRadius)
          f *= 2;
        propertyValue = Float.valueOf(f);
        propertyName = (isRadius || tokAt(i) == T.decimal ? "width"
            : "diameter");
        swidth = propertyName
            + (tokAt(i) == T.decimal ? " " + f : " " + ((int) f));
        break;
      case T.dollarsign:
        // $drawObject[m]
        if ((tokAt(i + 2) == T.leftsquare || isFrame)) {
          P3 pto = center = centerParameter(i);
          i = eval.iToken;
          propertyName = "coord";
          propertyValue = pto;
          havePoints = true;
          break;
        }
        // $drawObject
        propertyValue = eval.objectNameParameter(++i);
        propertyName = "identifier";
        havePoints = true;
        break;
      case T.color:
      case T.translucent:
      case T.opaque:
        idSeen = true;
        translucentLevel = eval.getColorTrans(i, false);
        i = eval.iToken;
        continue;
      default:
        if (!eval.setMeshDisplayProperty(JC.SHAPE_DRAW, 0, eval.theTok)) {
          if (eval.theTok == T.times || T.tokAttr(eval.theTok, T.identifier)) {
            thisId = setShapeId(JC.SHAPE_DRAW, i, idSeen);
            i = eval.iToken;
            break;
          }
          invArg();
        }
        if (iptDisplayProperty == 0)
          iptDisplayProperty = i;
        i = eval.iToken;
        continue;
      }
      idSeen = (eval.theTok != T.delete);
      if (havePoints && !isInitialized && !isFrame) {
        setShapeProperty(JC.SHAPE_DRAW, "points", Integer.valueOf(intScale));
        isInitialized = true;
        intScale = 0;
      }
      if (havePoints && isWild)
        invArg();
      if (propertyName != null)
        setShapeProperty(JC.SHAPE_DRAW, propertyName, propertyValue);
    }
    eval.finalizeObject(JC.SHAPE_DRAW, eval.colorArgb[0], translucentLevel, intScale,
        havePoints, connections, iptDisplayProperty, null);
    return true;
  }

  private boolean isosurface(int iShape) throws ScriptException {
    // also called by lcaoCartoon
    ScriptEvaluator eval = this.eval;
    sm.loadShape(iShape);
    if (tokAt(1) == T.list && listIsosurface(iShape))
      return false;
    int iptDisplayProperty = 0;
    boolean isIsosurface = (iShape == JC.SHAPE_ISOSURFACE);
    boolean isPmesh = (iShape == JC.SHAPE_PMESH);
    boolean isPlot3d = (iShape == JC.SHAPE_PLOT3D);
    boolean isLcaoCartoon = (iShape == JC.SHAPE_LCAOCARTOON);
    boolean surfaceObjectSeen = false;
    boolean planeSeen = false;
    boolean isMapped = false;
    boolean isBicolor = false;
    boolean isPhased = false;
    boolean doCalcArea = false;
    boolean doCalcVolume = false;
    boolean isCavity = false;
    boolean haveRadius = false;
    boolean toCache = false;
    boolean isFxy = false;
    boolean haveSlab = false;
    boolean haveIntersection = false;
    boolean isFrontOnly = false;
    float[] data = null;
    String cmd = null;
    int thisSetNumber = Integer.MIN_VALUE;
    int nFiles = 0;
    int nX, nY, nZ, ptX, ptY;
    float sigma = Float.NaN;
    float cutoff = Float.NaN;
    int ptWithin = 0;
    Boolean smoothing = null;
    int smoothingPower = Integer.MAX_VALUE;
    BS bs = null;
    BS bsSelect = null;
    BS bsIgnore = null;
    SB sbCommand = new SB();
    P3 pt;
    P4 plane = null;
    P3 lattice = null;
    P3[] pts;
    int color = 0;
    String str = null;
    int modelIndex = (chk ? 0 : Integer.MIN_VALUE);
    eval.setCursorWait(true);
    boolean idSeen = (initIsosurface(iShape) != null);
    boolean isWild = (idSeen && getShapeProperty(iShape, "ID") == null);
    boolean isColorSchemeTranslucent = false;
    boolean isInline = false;
    Object onlyOneModel = null;
    String translucency = null;
    String colorScheme = null;
    String mepOrMlp = null;
    M4[] symops = null;
    short[] discreteColixes = null;
    List<Object[]> propertyList = new List<Object[]>();
    boolean defaultMesh = false;
    if (isPmesh || isPlot3d)
      addShapeProperty(propertyList, "fileType", "Pmesh");

    for (int i = eval.iToken; i < slen; ++i) {
      String propertyName = null;
      Object propertyValue = null;
      getToken(i);
      if (eval.theTok == T.identifier)
        str = parameterAsString(i);
      switch (eval.theTok) {
      // settings only
      case T.isosurfacepropertysmoothing:
        smoothing = (getToken(++i).tok == T.on ? Boolean.TRUE
            : eval.theTok == T.off ? Boolean.FALSE : null);
        if (smoothing == null)
          invArg();
        continue;
      case T.isosurfacepropertysmoothingpower:
        smoothingPower = intParameter(++i);
        continue;
        // offset, rotate, and scale3d don't need to be saved in sbCommand
        // because they are display properties
      case T.move: // Jmol 13.0.RC2 -- required for state saving after coordinate-based translate/rotate
        propertyName = "moveIsosurface";
        if (tokAt(++i) != T.matrix4f)
          invArg();
        propertyValue = getToken(i++).value;
        break;
      case T.symop:
        float[][] ff = floatArraySet(i + 2, intParameter(i + 1), 16);
        symops = new M4[ff.length];
        for (int j = symops.length; --j >= 0;)
          symops[j] = M4.newA(ff[j]);
        i = eval.iToken;
        break;
      case T.symmetry:
        if (modelIndex < 0)
          modelIndex = Math.min(viewer.getCurrentModelIndex(), 0);
        boolean needIgnore = (bsIgnore == null);
        if (bsSelect == null)
          bsSelect = BSUtil.copy(viewer.getSelectionSet(false));
        // and in symop=1
        bsSelect.and(viewer.getAtomBits(T.symop, Integer.valueOf(1)));
        if (!needIgnore)
          bsSelect.andNot(bsIgnore);
        addShapeProperty(propertyList, "select", bsSelect);
        if (needIgnore) {
          bsIgnore = BSUtil.copy(bsSelect);
          BSUtil.invertInPlace(bsIgnore, viewer.getAtomCount());
          isFrontOnly = true;
          addShapeProperty(propertyList, "ignore", bsIgnore);
          sbCommand.append(" ignore ").append(Escape.eBS(bsIgnore));
        }
        sbCommand.append(" symmetry");
        if (color == 0)
          addShapeProperty(propertyList, "colorRGB", Integer.valueOf(T.symop));
        symops = viewer.modelSet.getSymMatrices(modelIndex);
        break;
      case T.offset:
        propertyName = "offset";
        propertyValue = centerParameter(++i);
        i = eval.iToken;
        break;
      case T.rotate:
        propertyName = "rotate";
        propertyValue = (tokAt(eval.iToken = ++i) == T.none ? null
            : getPoint4f(i));
        i = eval.iToken;
        break;
      case T.scale3d:
        propertyName = "scale3d";
        propertyValue = Float.valueOf(floatParameter(++i));
        break;
      case T.period:
        sbCommand.append(" periodic");
        propertyName = "periodic";
        break;
      case T.origin:
      case T.step:
      case T.point:
        propertyName = eval.theToken.value.toString();
        sbCommand.append(" ").appendO(eval.theToken.value);
        propertyValue = centerParameter(++i);
        sbCommand.append(" ").append(Escape.eP((P3) propertyValue));
        i = eval.iToken;
        break;
      case T.boundbox:
        if (fullCommand.indexOf("# BBOX=") >= 0) {
          String[] bbox = PT.split(
              PT.getQuotedAttribute(fullCommand, "# BBOX"), ",");
          pts = new P3[] { (P3) Escape.uP(bbox[0]), (P3) Escape.uP(bbox[1]) };
        } else if (eval.isCenterParameter(i + 1)) {
          pts = new P3[] { getPoint3f(i + 1, true),
              getPoint3f(eval.iToken + 1, true) };
          i = eval.iToken;
        } else {
          pts = viewer.getBoundBoxVertices();
        }
        sbCommand.append(" boundBox " + Escape.eP(pts[0]) + " "
            + Escape.eP(pts[pts.length - 1]));
        propertyName = "boundingBox";
        propertyValue = pts;
        break;
      case T.pmesh:
        isPmesh = true;
        sbCommand.append(" pmesh");
        propertyName = "fileType";
        propertyValue = "Pmesh";
        break;
      case T.intersection:
        // isosurface intersection {A} {B} VDW....
        // isosurface intersection {A} {B} function "a-b" VDW....
        bsSelect = atomExpressionAt(++i);
        if (chk) {
          bs = new BS();
        } else if (tokAt(eval.iToken + 1) == T.expressionBegin
            || tokAt(eval.iToken + 1) == T.bitset) {
          bs = atomExpressionAt(++eval.iToken);
          bs.and(viewer.getAtomsWithinRadius(5.0f, bsSelect, false, null));
        } else {
          // default is "within(5.0, selected) and not within(molecule,selected)"
          bs = viewer.getAtomsWithinRadius(5.0f, bsSelect, true, null);
          bs.andNot(viewer.getAtomBits(T.molecule, bsSelect));
        }
        bs.andNot(bsSelect);
        sbCommand.append(" intersection ").append(Escape.eBS(bsSelect)).append(
            " ").append(Escape.eBS(bs));
        i = eval.iToken;
        if (tokAt(i + 1) == T.function) {
          i++;
          String f = (String) getToken(++i).value;
          sbCommand.append(" function ").append(Escape.eS(f));
          if (!chk)
            addShapeProperty(propertyList, "func", (f.equals("a+b")
                || f.equals("a-b") ? f : createFunction("__iso__", "a,b", f)));
        } else {
          haveIntersection = true;
        }
        propertyName = "intersection";
        propertyValue = new BS[] { bsSelect, bs };
        break;
      case T.display:
      case T.within:
        boolean isDisplay = (eval.theTok == T.display);
        if (isDisplay) {
          sbCommand.append(" display");
          iptDisplayProperty = i;
          int tok = tokAt(i + 1);
          if (tok == T.nada)
            continue;
          i++;
          addShapeProperty(propertyList, "token", Integer.valueOf(T.on));
          if (tok == T.bitset || tok == T.all) {
            propertyName = "bsDisplay";
            if (tok == T.all) {
              sbCommand.append(" all");
            } else {
              propertyValue = st[i].value;
              sbCommand.append(" ").append(Escape.eBS((BS) propertyValue));
            }
            eval.checkLast(i);
            break;
          } else if (tok != T.within) {
            eval.iToken = i;
            invArg();
          }
        } else {
          ptWithin = i;
        }
        float distance;
        P3 ptc = null;
        bs = null;
        boolean havePt = false;
        if (tokAt(i + 1) == T.expressionBegin) {
          // within ( x.x , .... )
          distance = floatParameter(i + 3);
          if (eval.isPoint3f(i + 4)) {
            ptc = centerParameter(i + 4);
            havePt = true;
            eval.iToken = eval.iToken + 2;
          } else if (eval.isPoint3f(i + 5)) {
            ptc = centerParameter(i + 5);
            havePt = true;
            eval.iToken = eval.iToken + 2;
          } else {
            bs = eval.atomExpression(st, i + 5, slen, true, false, false, true);
            if (bs == null)
              invArg();
          }
        } else {
          distance = floatParameter(++i);
          ptc = centerParameter(++i);
        }
        if (isDisplay)
          eval.checkLast(eval.iToken);
        i = eval.iToken;
        if (fullCommand.indexOf("# WITHIN=") >= 0)
          bs = Escape.uB(PT.getQuotedAttribute(fullCommand, "# WITHIN"));
        else if (!havePt)
          bs = (eval.expressionResult instanceof BS ? (BS) eval.expressionResult
              : null);
        if (!chk) {
          if (bs != null && modelIndex >= 0) {
            bs.and(viewer.getModelUndeletedAtomsBitSet(modelIndex));
          }
          if (ptc == null)
            ptc = viewer.getAtomSetCenter(bs);

          getWithinDistanceVector(propertyList, distance, ptc, bs, isDisplay);
          sbCommand.append(" within ").appendF(distance).append(" ").append(
              bs == null ? Escape.eP(ptc) : Escape.eBS(bs));
        }
        continue;
      case T.parameters:
        propertyName = "parameters";
        // if > 1 parameter, then first is assumed to be the cutoff. 
        float[] fparams = eval.floatParameterSet(++i, 1, 10);
        i = eval.iToken;
        propertyValue = fparams;
        sbCommand.append(" parameters ").append(Escape.eAF(fparams));
        break;
      case T.property:
      case T.variable:
        onlyOneModel = eval.theToken.value;
        boolean isVariable = (eval.theTok == T.variable);
        int tokProperty = tokAt(i + 1);
        if (mepOrMlp == null) { // not mlp or mep
          if (!surfaceObjectSeen && !isMapped && !planeSeen) {
            addShapeProperty(propertyList, "sasurface", Float.valueOf(0));
            //if (surfaceObjectSeen)
            sbCommand.append(" vdw");
            surfaceObjectSeen = true;
          }
          propertyName = "property";
          if (smoothing == null) {
            boolean allowSmoothing = T.tokAttr(tokProperty, T.floatproperty);
            smoothing = (allowSmoothing
                && viewer.getIsosurfacePropertySmoothing(false) == 1 ? Boolean.TRUE
                : Boolean.FALSE);
          }
          addShapeProperty(propertyList, "propertySmoothing", smoothing);
          sbCommand.append(" isosurfacePropertySmoothing " + smoothing);
          if (smoothing == Boolean.TRUE) {
            if (smoothingPower == Integer.MAX_VALUE)
              smoothingPower = viewer.getIsosurfacePropertySmoothing(true);
            addShapeProperty(propertyList, "propertySmoothingPower", Integer
                .valueOf(smoothingPower));
            sbCommand.append(" isosurfacePropertySmoothingPower "
                + smoothingPower);
          }
          if (viewer.global.rangeSelected)
            addShapeProperty(propertyList, "rangeSelected", Boolean.TRUE);
        } else {
          propertyName = mepOrMlp;
        }
        str = parameterAsString(i);
        //        if (surfaceObjectSeen)
        sbCommand.append(" ").append(str);

        if (str.toLowerCase().indexOf("property_") == 0) {
          data = new float[viewer.getAtomCount()];
          if (chk)
            continue;
          data = viewer.getDataFloat(str);
          if (data == null)
            invArg();
          addShapeProperty(propertyList, propertyName, data);
          continue;
        }

        int atomCount = viewer.getAtomCount();
        data = new float[atomCount];

        if (isVariable) {
          String vname = parameterAsString(++i);
          if (vname.length() == 0) {
            data = eval.floatParameterSet(i, atomCount, atomCount);
          } else {
            data = new float[atomCount];
            if (!chk)
              Parser.parseStringInfestedFloatArray(""
                  + eval.getParameter(vname, T.string), null, data);
          }
          if (!chk/* && (surfaceObjectSeen)*/)
            sbCommand.append(" \"\" ").append(Escape.eAF(data));
        } else {
          getToken(++i);
          if (!chk) {
            sbCommand.append(" " + eval.theToken.value);
            Atom[] atoms = viewer.modelSet.atoms;
            viewer.autoCalculate(tokProperty);
            if (tokProperty != T.color)
              for (int iAtom = atomCount; --iAtom >= 0;)
                data[iAtom] = Atom.atomPropertyFloat(viewer, atoms[iAtom],
                    tokProperty);
          }
          if (tokProperty == T.color)
            colorScheme = "inherit";
          if (tokAt(i + 1) == T.within) {
            float d = floatParameter(i = i + 2);
            sbCommand.append(" within " + d);
            addShapeProperty(propertyList, "propertyDistanceMax", Float
                .valueOf(d));
          }
        }
        propertyValue = data;
        break;
      case T.modelindex:
      case T.model:
        if (surfaceObjectSeen)
          invArg();
        modelIndex = (eval.theTok == T.modelindex ? intParameter(++i) : eval
            .modelNumberParameter(++i));
        sbCommand.append(" modelIndex " + modelIndex);
        if (modelIndex < 0) {
          propertyName = "fixed";
          propertyValue = Boolean.TRUE;
          break;
        }
        propertyName = "modelIndex";
        propertyValue = Integer.valueOf(modelIndex);
        break;
      case T.select:
        // in general, viewer.getCurrentSelection() is used, but we may
        // override that here. But we have to be careful that
        // we PREPEND the selection to the command if no surface object
        // has been seen yet, and APPEND it if it has.
        propertyName = "select";
        BS bs1 = atomExpressionAt(++i);
        propertyValue = bs1;
        i = eval.iToken;
        boolean isOnly = (tokAt(i + 1) == T.only);
        if (isOnly) {
          i++;
          bsIgnore = BSUtil.copy(bs1);
          BSUtil.invertInPlace(bsIgnore, viewer.getAtomCount());
          addShapeProperty(propertyList, "ignore", bsIgnore);
          sbCommand.append(" ignore ").append(Escape.eBS(bsIgnore));
          isFrontOnly = true;
        }
        if (surfaceObjectSeen || isMapped) {
          sbCommand.append(" select " + Escape.eBS(bs1));
        } else {
          bsSelect = (BS) propertyValue;
          if (modelIndex < 0 && bsSelect.nextSetBit(0) >= 0)
            modelIndex = viewer.getAtomModelIndex(bsSelect.nextSetBit(0));
        }
        break;
      case T.set:
        thisSetNumber = intParameter(++i);
        break;
      case T.center:
        propertyName = "center";
        propertyValue = centerParameter(++i);
        sbCommand.append(" center " + Escape.eP((P3) propertyValue));
        i = eval.iToken;
        break;
      case T.sign:
      case T.color:
        idSeen = true;
        boolean isSign = (eval.theTok == T.sign);
        if (isSign) {
          sbCommand.append(" sign");
          addShapeProperty(propertyList, "sign", Boolean.TRUE);
        } else {
          if (tokAt(i + 1) == T.density) {
            i++;
            propertyName = "colorDensity";
            sbCommand.append(" color density");
            if (isFloatParameter(i + 1)) {
              float ptSize = floatParameter(++i);
              sbCommand.append(" " + ptSize);
              propertyValue = Float.valueOf(ptSize);
            }
            break;
          }
          /*
           * "color" now is just used as an equivalent to "sign" and as an
           * introduction to "absolute" any other use is superfluous; it has
           * been replaced with MAP for indicating "use the current surface"
           * because the term COLOR is too general.
           */

          if (getToken(i + 1).tok == T.string) {
            colorScheme = parameterAsString(++i);
            if (colorScheme.indexOf(" ") > 0) {
              discreteColixes = C.getColixArray(colorScheme);
              if (discreteColixes == null)
                error(ScriptEvaluator.ERROR_badRGBColor);
            }
          } else if (eval.theTok == T.mesh) {
            i++;
            sbCommand.append(" color mesh");
            color = eval.getArgbParam(++i);
            addShapeProperty(propertyList, "meshcolor", Integer.valueOf(color));
            sbCommand.append(" ").append(Escape.escapeColor(color));
            i = eval.iToken;
            continue;
          }
          if ((eval.theTok = tokAt(i + 1)) == T.translucent
              || eval.theTok == T.opaque) {
            sbCommand.append(" color");
            translucency = setColorOptions(sbCommand, i + 1,
                JC.SHAPE_ISOSURFACE, -2);
            i = eval.iToken;
            continue;
          }
          switch (tokAt(i + 1)) {
          case T.absolute:
          case T.range:
            getToken(++i);
            sbCommand.append(" color range");
            addShapeProperty(propertyList, "rangeAll", null);
            if (tokAt(i + 1) == T.all) {
              i++;
              sbCommand.append(" all");
              continue;
            }
            float min = floatParameter(++i);
            float max = floatParameter(++i);
            addShapeProperty(propertyList, "red", Float.valueOf(min));
            addShapeProperty(propertyList, "blue", Float.valueOf(max));
            sbCommand.append(" ").appendF(min).append(" ").appendF(max);
            continue;
          }
          if (eval.isColorParam(i + 1)) {
            color = eval.getArgbParam(i + 1);
            if (tokAt(i + 2) == T.to) {
              colorScheme = eval.getColorRange(i + 1);
              i = eval.iToken;
              break;
            }
          }
          sbCommand.append(" color");
        }
        if (eval.isColorParam(i + 1)) {
          color = eval.getArgbParam(++i);
          sbCommand.append(" ").append(Escape.escapeColor(color));
          i = eval.iToken;
          addShapeProperty(propertyList, "colorRGB", Integer.valueOf(color));
          idSeen = true;
          if (eval.isColorParam(i + 1)) {
            color = eval.getArgbParam(++i);
            i = eval.iToken;
            addShapeProperty(propertyList, "colorRGB", Integer.valueOf(color));
            sbCommand.append(" ").append(Escape.escapeColor(color));
            isBicolor = true;
          } else if (isSign) {
            invPO();
          }
        } else if (!isSign && discreteColixes == null) {
          invPO();
        }
        continue;
      case T.cache:
        if (!isIsosurface)
          invArg();
        toCache = !chk;
        continue;
      case T.file:
        if (tokAt(i + 1) != T.string)
          invPO();
        continue;
      case T.ionic:
      case T.vanderwaals:
        //if (surfaceObjectSeen)
        sbCommand.append(" ").appendO(eval.theToken.value);
        RadiusData rd = eval.encodeRadiusParameter(i, false, true);
        //if (surfaceObjectSeen)
        sbCommand.append(" ").appendO(rd);
        if (Float.isNaN(rd.value))
          rd.value = 100;
        propertyValue = rd;
        propertyName = "radius";
        haveRadius = true;
        if (isMapped)
          surfaceObjectSeen = false;
        i = eval.iToken;
        break;
      case T.plane:
        // plane {X, Y, Z, W}
        planeSeen = true;
        propertyName = "plane";
        propertyValue = eval.planeParameter(++i);
        i = eval.iToken;
        //if (surfaceObjectSeen)
        sbCommand.append(" plane ").append(Escape.eP4((P4) propertyValue));
        break;
      case T.scale:
        propertyName = "scale";
        propertyValue = Float.valueOf(floatParameter(++i));
        sbCommand.append(" scale ").appendO(propertyValue);
        break;
      case T.all:
        if (idSeen)
          invArg();
        propertyName = "thisID";
        break;
      case T.ellipsoid:
        // ellipsoid {xc yc zc f} where a = b and f = a/c
        // NOT OR ellipsoid {u11 u22 u33 u12 u13 u23}
        surfaceObjectSeen = true;
        ++i;
        //        ignoreError = true;
        //      try {
        propertyValue = getPoint4f(i);
        propertyName = "ellipsoid";
        i = eval.iToken;
        sbCommand.append(" ellipsoid ").append(Escape.eP4((P4) propertyValue));
        break;
      //        } catch (Exception e) {
      //        }
      //        try {
      //          propertyName = "ellipsoid";
      //          propertyValue = eval.floatParameterSet(i, 6, 6);
      //          i = eval.iToken;
      //          sbCommand.append(" ellipsoid ").append(
      //              Escape.eAF((float[]) propertyValue));
      //          break;
      //        } catch (Exception e) {
      //        }
      //        ignoreError = false;
      //        bs = atomExpressionAt(i);
      //        sbCommand.append(" ellipsoid ").append(Escape.eBS(bs));
      //        int iAtom = bs.nextSetBit(0);
      //        if (iAtom < 0)
      //          return;
      //        Atom[] atoms = viewer.modelSet.atoms;
      //        Tensor[] tensors = atoms[iAtom].getTensors();
      //        if (tensors == null || tensors.length < 1 || tensors[0] == null
      //            || (propertyValue = viewer.getQuadricForTensor(tensors[0], null)) == null)
      //          return;
      //        i = eval.iToken;
      //        propertyName = "ellipsoid";
      //        if (!chk)
      //          addShapeProperty(propertyList, "center", viewer.getAtomPoint3f(iAtom));
      //        break;
      case T.hkl:
        // miller indices hkl
        planeSeen = true;
        propertyName = "plane";
        propertyValue = eval.hklParameter(++i);
        i = eval.iToken;
        sbCommand.append(" plane ").append(Escape.eP4((P4) propertyValue));
        break;
      case T.lcaocartoon:
        surfaceObjectSeen = true;
        String lcaoType = parameterAsString(++i);
        addShapeProperty(propertyList, "lcaoType", lcaoType);
        sbCommand.append(" lcaocartoon ").append(Escape.eS(lcaoType));
        switch (getToken(++i).tok) {
        case T.bitset:
        case T.expressionBegin:
          // automatically selects just the model of the first atom in the set.
          propertyName = "lcaoCartoon";
          bs = atomExpressionAt(i);
          i = eval.iToken;
          if (chk)
            continue;
          int atomIndex = bs.nextSetBit(0);
          if (atomIndex < 0)
            error(ScriptEvaluator.ERROR_expressionExpected);
          sbCommand.append(" ({").appendI(atomIndex).append("})");
          modelIndex = viewer.getAtomModelIndex(atomIndex);
          addShapeProperty(propertyList, "modelIndex", Integer
              .valueOf(modelIndex));
          V3[] axes = { new V3(), new V3(),
              V3.newV(viewer.getAtomPoint3f(atomIndex)), new V3() };
          if (!lcaoType.equalsIgnoreCase("s")
              && viewer.getHybridizationAndAxes(atomIndex, axes[0], axes[1],
                  lcaoType) == null)
            return false;
          propertyValue = axes;
          break;
        default:
          error(ScriptEvaluator.ERROR_expressionExpected);
        }
        break;
      case T.mo:
        // mo 1-based-index
        int moNumber = Integer.MAX_VALUE;
        int offset = Integer.MAX_VALUE;
        boolean isNegOffset = (tokAt(i + 1) == T.minus);
        if (isNegOffset)
          i++;
        float[] linearCombination = null;
        switch (tokAt(++i)) {
        case T.nada:
          error(ScriptEvaluator.ERROR_badArgumentCount);
          break;
        case T.density:
          sbCommand.append("mo [1] squared ");
          addShapeProperty(propertyList, "squareLinear", Boolean.TRUE);
          linearCombination = new float[] { 1 };
          offset = moNumber = 0;
          i++;
          break;
        case T.homo:
        case T.lumo:
          offset = moOffset(i);
          moNumber = 0;
          i = eval.iToken;
          //if (surfaceObjectSeen) {
          sbCommand.append(" mo " + (isNegOffset ? "-" : "") + "HOMO ");
          if (offset > 0)
            sbCommand.append("+");
          if (offset != 0)
            sbCommand.appendI(offset);
          //}
          break;
        case T.integer:
          moNumber = intParameter(i);
          //if (surfaceObjectSeen)
          sbCommand.append(" mo ").appendI(moNumber);
          break;
        default:
          if (eval.isArrayParameter(i)) {
            linearCombination = eval.floatParameterSet(i, 1, Integer.MAX_VALUE);
            i = eval.iToken;
          }
        }
        boolean squared = (tokAt(i + 1) == T.squared);
        if (squared) {
          addShapeProperty(propertyList, "squareLinear", Boolean.TRUE);
          sbCommand.append(" squared");
          if (linearCombination == null)
            linearCombination = new float[0];
        } else if (tokAt(i + 1) == T.point) {
          ++i;
          int monteCarloCount = intParameter(++i);
          int seed = (tokAt(i + 1) == T.integer ? intParameter(++i)
              : ((int) -System.currentTimeMillis()) % 10000);
          addShapeProperty(propertyList, "monteCarloCount", Integer
              .valueOf(monteCarloCount));
          addShapeProperty(propertyList, "randomSeed", Integer.valueOf(seed));
          sbCommand.append(" points ").appendI(monteCarloCount).appendC(' ')
              .appendI(seed);
        }
        setMoData(propertyList, moNumber, linearCombination, offset,
            isNegOffset, modelIndex, null);
        surfaceObjectSeen = true;
        continue;
      case T.nci:
        propertyName = "nci";
        //if (surfaceObjectSeen)
        sbCommand.append(" " + propertyName);
        int tok = tokAt(i + 1);
        boolean isPromolecular = (tok != T.file && tok != T.string && tok != T.mrc);
        propertyValue = Boolean.valueOf(isPromolecular);
        if (isPromolecular)
          surfaceObjectSeen = true;
        break;
      case T.mep:
      case T.mlp:
        boolean isMep = (eval.theTok == T.mep);
        propertyName = (isMep ? "mep" : "mlp");
        //if (surfaceObjectSeen)
        sbCommand.append(" " + propertyName);
        String fname = null;
        int calcType = -1;
        surfaceObjectSeen = true;
        if (tokAt(i + 1) == T.integer) {
          calcType = intParameter(++i);
          sbCommand.append(" " + calcType);
          addShapeProperty(propertyList, "mepCalcType", Integer
              .valueOf(calcType));
        }
        if (tokAt(i + 1) == T.string) {
          fname = stringParameter(++i);
          //if (surfaceObjectSeen)
          sbCommand.append(" /*file*/" + Escape.eS(fname));
        } else if (tokAt(i + 1) == T.property) {
          mepOrMlp = propertyName;
          continue;
        }
        if (!chk)
          try {
            data = (fname == null && isMep ? viewer.getPartialCharges()
                : getAtomicPotentials(bsSelect, bsIgnore, fname));
          } catch (Exception e) {
            // ignore
          }
        if (!chk && data == null)
          error(ScriptEvaluator.ERROR_noPartialCharges);
        propertyValue = data;
        break;
      case T.volume:
        doCalcVolume = !chk;
        sbCommand.append(" volume");
        break;
      case T.id:
        setShapeId(iShape, ++i, idSeen);
        isWild = (getShapeProperty(iShape, "ID") == null);
        i = eval.iToken;
        break;
      case T.colorscheme:
        // either order NOT OK -- documented for TRANSLUCENT "rwb"
        if (tokAt(i + 1) == T.translucent) {
          isColorSchemeTranslucent = true;
          i++;
        }
        colorScheme = parameterAsString(++i).toLowerCase();
        if (colorScheme.equals("sets")) {
          sbCommand.append(" colorScheme \"sets\"");
        } else if (eval.isColorParam(i)) {
          colorScheme = eval.getColorRange(i);
          i = eval.iToken;
        }
        break;
      case T.addhydrogens:
        propertyName = "addHydrogens";
        propertyValue = Boolean.TRUE;
        sbCommand.append(" mp.addHydrogens");
        break;
      case T.angstroms:
        propertyName = "angstroms";
        sbCommand.append(" angstroms");
        break;
      case T.anisotropy:
        propertyName = "anisotropy";
        propertyValue = getPoint3f(++i, false);
        sbCommand.append(" anisotropy").append(Escape.eP((P3) propertyValue));
        i = eval.iToken;
        break;
      case T.area:
        doCalcArea = !chk;
        sbCommand.append(" area");
        break;
      case T.atomicorbital:
      case T.orbital:
        surfaceObjectSeen = true;
        if (isBicolor && !isPhased) {
          sbCommand.append(" phase \"_orb\"");
          addShapeProperty(propertyList, "phase", "_orb");
        }
        float[] nlmZprs = new float[7];
        nlmZprs[0] = intParameter(++i);
        nlmZprs[1] = intParameter(++i);
        nlmZprs[2] = intParameter(++i);
        nlmZprs[3] = (isFloatParameter(i + 1) ? floatParameter(++i) : 6f);
        //if (surfaceObjectSeen)
        sbCommand.append(" atomicOrbital ").appendI((int) nlmZprs[0]).append(
            " ").appendI((int) nlmZprs[1]).append(" ")
            .appendI((int) nlmZprs[2]).append(" ").appendF(nlmZprs[3]);
        if (tokAt(i + 1) == T.point) {
          i += 2;
          nlmZprs[4] = intParameter(i);
          nlmZprs[5] = (tokAt(i + 1) == T.decimal ? floatParameter(++i) : 0);
          nlmZprs[6] = (tokAt(i + 1) == T.integer ? intParameter(++i)
              : ((int) -System.currentTimeMillis()) % 10000);
          //if (surfaceObjectSeen)
          sbCommand.append(" points ").appendI((int) nlmZprs[4]).appendC(' ')
              .appendF(nlmZprs[5]).appendC(' ').appendI((int) nlmZprs[6]);
        }
        propertyName = "hydrogenOrbital";
        propertyValue = nlmZprs;
        break;
      case T.binary:
        sbCommand.append(" binary");
        // for PMESH, specifically
        // ignore for now
        continue;
      case T.blockdata:
        sbCommand.append(" blockData");
        propertyName = "blockData";
        propertyValue = Boolean.TRUE;
        break;
      case T.cap:
      case T.slab:
        haveSlab = true;
        propertyName = (String) eval.theToken.value;
        propertyValue = getCapSlabObject(i, false);
        i = eval.iToken;
        break;
      case T.cavity:
        if (!isIsosurface)
          invArg();
        isCavity = true;
        if (chk)
          continue;
        float cavityRadius = (isFloatParameter(i + 1) ? floatParameter(++i)
            : 1.2f);
        float envelopeRadius = (isFloatParameter(i + 1) ? floatParameter(++i)
            : 10f);
        if (envelopeRadius > 10f)
          eval.integerOutOfRange(0, 10);
        sbCommand.append(" cavity ").appendF(cavityRadius).append(" ").appendF(
            envelopeRadius);
        addShapeProperty(propertyList, "envelopeRadius", Float
            .valueOf(envelopeRadius));
        addShapeProperty(propertyList, "cavityRadius", Float
            .valueOf(cavityRadius));
        propertyName = "cavity";
        break;
      case T.contour:
      case T.contours:
        propertyName = "contour";
        sbCommand.append(" contour");
        switch (tokAt(i + 1)) {
        case T.discrete:
          propertyValue = eval.floatParameterSet(i + 2, 1, Integer.MAX_VALUE);
          sbCommand.append(" discrete ").append(
              Escape.eAF((float[]) propertyValue));
          i = eval.iToken;
          break;
        case T.increment:
          pt = getPoint3f(i + 2, false);
          if (pt.z <= 0 || pt.y < pt.x)
            invArg(); // from to step
          if (pt.z == (int) pt.z && pt.z > (pt.y - pt.x))
            pt.z = (pt.y - pt.x) / pt.z;
          propertyValue = pt;
          i = eval.iToken;
          sbCommand.append(" increment ").append(Escape.eP(pt));
          break;
        default:
          propertyValue = Integer
              .valueOf(tokAt(i + 1) == T.integer ? intParameter(++i) : 0);
          sbCommand.append(" ").appendO(propertyValue);
        }
        break;
      case T.decimal:
      case T.integer:
      case T.plus:
      case T.cutoff:
        sbCommand.append(" cutoff ");
        if (eval.theTok == T.cutoff)
          i++;
        if (tokAt(i) == T.plus) {
          propertyName = "cutoffPositive";
          propertyValue = Float.valueOf(cutoff = floatParameter(++i));
          sbCommand.append("+").appendO(propertyValue);
        } else if (isFloatParameter(i)) {
          propertyName = "cutoff";
          propertyValue = Float.valueOf(cutoff = floatParameter(i));
          sbCommand.appendO(propertyValue);
        } else {
          propertyName = "cutoffRange";
          propertyValue = eval.floatParameterSet(i, 2, 2);
          addShapeProperty(propertyList, "cutoff", Float.valueOf(0));
          sbCommand.append(Escape.eAF((float[]) propertyValue));
          i = eval.iToken;
        }
        break;
      case T.downsample:
        propertyName = "downsample";
        propertyValue = Integer.valueOf(intParameter(++i));
        //if (surfaceObjectSeen)
        sbCommand.append(" downsample ").appendO(propertyValue);
        break;
      case T.eccentricity:
        propertyName = "eccentricity";
        propertyValue = getPoint4f(++i);
        //if (surfaceObjectSeen)
        sbCommand.append(" eccentricity ").append(
            Escape.eP4((P4) propertyValue));
        i = eval.iToken;
        break;
      case T.ed:
        sbCommand.append(" ed");
        // electron density - never documented
        setMoData(propertyList, -1, null, 0, false, modelIndex, null);
        surfaceObjectSeen = true;
        continue;
      case T.debug:
      case T.nodebug:
        sbCommand.append(" ").appendO(eval.theToken.value);
        propertyName = "debug";
        propertyValue = (eval.theTok == T.debug ? Boolean.TRUE : Boolean.FALSE);
        break;
      case T.fixed:
        sbCommand.append(" fixed");
        propertyName = "fixed";
        propertyValue = Boolean.TRUE;
        break;
      case T.fullplane:
        sbCommand.append(" fullPlane");
        propertyName = "fullPlane";
        propertyValue = Boolean.TRUE;
        break;
      case T.functionxy:
      case T.functionxyz:
        // isosurface functionXY "functionName"|"data2d_xxxxx"
        // isosurface functionXYZ "functionName"|"data3d_xxxxx"
        // {origin} {ni ix iy iz} {nj jx jy jz} {nk kx ky kz}
        // or
        // isosurface origin.. step... count... functionXY[Z] = "x + y + z"
        boolean isFxyz = (eval.theTok == T.functionxyz);
        propertyName = "" + eval.theToken.value;
        List<Object> vxy = new List<Object>();
        propertyValue = vxy;
        isFxy = surfaceObjectSeen = true;
        //if (surfaceObjectSeen)
        sbCommand.append(" ").append(propertyName);
        String name = parameterAsString(++i);
        if (name.equals("=")) {
          //if (surfaceObjectSeen)
          sbCommand.append(" =");
          name = parameterAsString(++i);
          //if (surfaceObjectSeen)
          sbCommand.append(" ").append(Escape.eS(name));
          vxy.addLast(name);
          if (!chk)
            addShapeProperty(propertyList, "func", createFunction("__iso__",
                "x,y,z", name));
          //surfaceObjectSeen = true;
          break;
        }
        // override of function or data name when saved as a state
        String dName = PT.getQuotedAttribute(fullCommand, "# DATA"
            + (isFxy ? "2" : ""));
        if (dName == null)
          dName = "inline";
        else
          name = dName;
        boolean isXYZ = (name.indexOf("data2d_") == 0);
        boolean isXYZV = (name.indexOf("data3d_") == 0);
        isInline = name.equals("inline");
        //if (!surfaceObjectSeen)
        sbCommand.append(" inline");
        vxy.addLast(name); // (0) = name
        P3 pt3 = getPoint3f(++i, false);
        //if (!surfaceObjectSeen)
        sbCommand.append(" ").append(Escape.eP(pt3));
        vxy.addLast(pt3); // (1) = {origin}
        P4 pt4;
        ptX = ++eval.iToken;
        vxy.addLast(pt4 = getPoint4f(ptX)); // (2) = {ni ix iy iz}
        //if (!surfaceObjectSeen)
        sbCommand.append(" ").append(Escape.eP4(pt4));
        nX = (int) pt4.x;
        ptY = ++eval.iToken;
        vxy.addLast(pt4 = getPoint4f(ptY)); // (3) = {nj jx jy jz}
        //if (!surfaceObjectSeen)
        sbCommand.append(" ").append(Escape.eP4(pt4));
        nY = (int) pt4.x;
        vxy.addLast(pt4 = getPoint4f(++eval.iToken)); // (4) = {nk kx ky kz}
        //if (!surfaceObjectSeen)
        sbCommand.append(" ").append(Escape.eP4(pt4));
        nZ = (int) pt4.x;

        if (nX == 0 || nY == 0 || nZ == 0)
          invArg();
        if (!chk) {
          float[][] fdata = null;
          float[][][] xyzdata = null;
          if (isFxyz) {
            if (isInline) {
              nX = Math.abs(nX);
              nY = Math.abs(nY);
              nZ = Math.abs(nZ);
              xyzdata = floatArraySetXYZ(++eval.iToken, nX, nY, nZ);
            } else if (isXYZV) {
              xyzdata = viewer.getDataFloat3D(name);
            } else {
              xyzdata = viewer.functionXYZ(name, nX, nY, nZ);
            }
            nX = Math.abs(nX);
            nY = Math.abs(nY);
            nZ = Math.abs(nZ);
            if (xyzdata == null) {
              eval.iToken = ptX;
              eval.errorStr(ScriptEvaluator.ERROR_what, "xyzdata is null.");
            }
            if (xyzdata.length != nX || xyzdata[0].length != nY
                || xyzdata[0][0].length != nZ) {
              eval.iToken = ptX;
              eval.errorStr(ScriptEvaluator.ERROR_what, "xyzdata["
                  + xyzdata.length + "][" + xyzdata[0].length + "]["
                  + xyzdata[0][0].length + "] is not of size [" + nX + "]["
                  + nY + "][" + nZ + "]");
            }
            vxy.addLast(xyzdata); // (5) = float[][][] data
            //if (!surfaceObjectSeen)
            sbCommand.append(" ").append(Escape.e(xyzdata));
          } else {
            if (isInline) {
              nX = Math.abs(nX);
              nY = Math.abs(nY);
              fdata = floatArraySet(++eval.iToken, nX, nY);
            } else if (isXYZ) {
              fdata = viewer.getDataFloat2D(name);
              nX = (fdata == null ? 0 : fdata.length);
              nY = 3;
            } else {
              fdata = viewer.functionXY(name, nX, nY);
              nX = Math.abs(nX);
              nY = Math.abs(nY);
            }
            if (fdata == null) {
              eval.iToken = ptX;
              eval.errorStr(ScriptEvaluator.ERROR_what, "fdata is null.");
            }
            if (fdata.length != nX && !isXYZ) {
              eval.iToken = ptX;
              eval.errorStr(ScriptEvaluator.ERROR_what,
                  "fdata length is not correct: " + fdata.length + " " + nX
                      + ".");
            }
            for (int j = 0; j < nX; j++) {
              if (fdata[j] == null) {
                eval.iToken = ptY;
                eval.errorStr(ScriptEvaluator.ERROR_what, "fdata[" + j
                    + "] is null.");
              }
              if (fdata[j].length != nY) {
                eval.iToken = ptY;
                eval.errorStr(ScriptEvaluator.ERROR_what, "fdata[" + j
                    + "] is not the right length: " + fdata[j].length + " "
                    + nY + ".");
              }
            }
            vxy.addLast(fdata); // (5) = float[][] data
            //if (!surfaceObjectSeen)
            sbCommand.append(" ").append(Escape.e(fdata));
          }
        }
        i = eval.iToken;
        break;
      case T.gridpoints:
        propertyName = "gridPoints";
        sbCommand.append(" gridPoints");
        break;
      case T.ignore:
        propertyName = "ignore";
        propertyValue = bsIgnore = atomExpressionAt(++i);
        sbCommand.append(" ignore ").append(Escape.eBS(bsIgnore));
        i = eval.iToken;
        break;
      case T.insideout:
        propertyName = "insideOut";
        sbCommand.append(" insideout");
        break;
      case T.internal:
      case T.interior:
      case T.pocket:
        //if (!surfaceObjectSeen)
        sbCommand.append(" ").appendO(eval.theToken.value);
        propertyName = "pocket";
        propertyValue = (eval.theTok == T.pocket ? Boolean.TRUE : Boolean.FALSE);
        break;
      case T.lobe:
        // lobe {eccentricity}
        propertyName = "lobe";
        propertyValue = getPoint4f(++i);
        i = eval.iToken;
        //if (!surfaceObjectSeen)
        sbCommand.append(" lobe ").append(Escape.eP4((P4) propertyValue));
        surfaceObjectSeen = true;
        break;
      case T.lonepair:
      case T.lp:
        // lp {eccentricity}
        propertyName = "lp";
        propertyValue = getPoint4f(++i);
        i = eval.iToken;
        //if (!surfaceObjectSeen)
        sbCommand.append(" lp ").append(Escape.eP4((P4) propertyValue));
        surfaceObjectSeen = true;
        break;
      case T.mapProperty:
        if (isMapped || slen == i + 1)
          invArg();
        isMapped = true;
        if ((isCavity || haveRadius || haveIntersection) && !surfaceObjectSeen) {
          surfaceObjectSeen = true;
          addShapeProperty(propertyList, "bsSolvent", (haveRadius
              || haveIntersection ? new BS() : eval
              .lookupIdentifierValue("solvent")));
          addShapeProperty(propertyList, "sasurface", Float.valueOf(0));
        }
        if (sbCommand.length() == 0) {
          plane = (P4) getShapeProperty(JC.SHAPE_ISOSURFACE, "plane");
          if (plane == null) {
            if (getShapeProperty(JC.SHAPE_ISOSURFACE, "contours") != null) {
              addShapeProperty(propertyList, "nocontour", null);
            }
          } else {
            addShapeProperty(propertyList, "plane", plane);
            sbCommand.append("plane ").append(Escape.eP4(plane));
            planeSeen = true;
            plane = null;
          }
        } else if (!surfaceObjectSeen && !planeSeen) {
          invArg();
        }
        sbCommand.append("; isosurface map");
        addShapeProperty(propertyList, "map", (surfaceObjectSeen ? Boolean.TRUE
            : Boolean.FALSE));
        break;
      case T.maxset:
        propertyName = "maxset";
        propertyValue = Integer.valueOf(intParameter(++i));
        sbCommand.append(" maxSet ").appendO(propertyValue);
        break;
      case T.minset:
        propertyName = "minset";
        propertyValue = Integer.valueOf(intParameter(++i));
        sbCommand.append(" minSet ").appendO(propertyValue);
        break;
      case T.radical:
        // rad {eccentricity}
        surfaceObjectSeen = true;
        propertyName = "rad";
        propertyValue = getPoint4f(++i);
        i = eval.iToken;
        //if (!surfaceObjectSeen)
        sbCommand.append(" radical ").append(Escape.eP4((P4) propertyValue));
        break;
      case T.modelbased:
        propertyName = "fixed";
        propertyValue = Boolean.FALSE;
        sbCommand.append(" modelBased");
        break;
      case T.molecular:
      case T.sasurface:
      case T.solvent:
        onlyOneModel = eval.theToken.value;
        float radius;
        if (eval.theTok == T.molecular) {
          propertyName = "molecular";
          sbCommand.append(" molecular");
          radius = (isFloatParameter(i + 1) ? floatParameter(++i) : 1.4f);
        } else {
          addShapeProperty(propertyList, "bsSolvent", eval
              .lookupIdentifierValue("solvent"));
          propertyName = (eval.theTok == T.sasurface ? "sasurface" : "solvent");
          sbCommand.append(" ").appendO(eval.theToken.value);
          radius = (isFloatParameter(i + 1) ? floatParameter(++i) : viewer
              .getFloat(T.solventproberadius));
        }
        sbCommand.append(" ").appendF(radius);
        propertyValue = Float.valueOf(radius);
        if (tokAt(i + 1) == T.full) {
          addShapeProperty(propertyList, "doFullMolecular", null);
          //if (!surfaceObjectSeen)
          sbCommand.append(" full");
          i++;
        }
        surfaceObjectSeen = true;
        break;
      case T.mrc:
        addShapeProperty(propertyList, "fileType", "Mrc");
        sbCommand.append(" mrc");
        continue;
      case T.object:
      case T.obj:
        addShapeProperty(propertyList, "fileType", "Obj");
        sbCommand.append(" obj");
        continue;
      case T.msms:
        addShapeProperty(propertyList, "fileType", "Msms");
        sbCommand.append(" msms");
        continue;
      case T.phase:
        if (surfaceObjectSeen)
          invArg();
        propertyName = "phase";
        isPhased = true;
        propertyValue = (tokAt(i + 1) == T.string ? stringParameter(++i)
            : "_orb");
        sbCommand.append(" phase ").append(Escape.eS((String) propertyValue));
        break;
      case T.pointsperangstrom:
      case T.resolution:
        propertyName = "resolution";
        propertyValue = Float.valueOf(floatParameter(++i));
        sbCommand.append(" resolution ").appendO(propertyValue);
        break;
      case T.reversecolor:
        propertyName = "reverseColor";
        propertyValue = Boolean.TRUE;
        sbCommand.append(" reversecolor");
        break;
      case T.sigma:
        propertyName = "sigma";
        propertyValue = Float.valueOf(sigma = floatParameter(++i));
        sbCommand.append(" sigma ").appendO(propertyValue);
        break;
      case T.geosurface:
        // geosurface [radius]
        propertyName = "geodesic";
        propertyValue = Float.valueOf(floatParameter(++i));
        //if (!surfaceObjectSeen)
        sbCommand.append(" geosurface ").appendO(propertyValue);
        surfaceObjectSeen = true;
        break;
      case T.sphere:
        // sphere [radius]
        propertyName = "sphere";
        propertyValue = Float.valueOf(floatParameter(++i));
        //if (!surfaceObjectSeen)
        sbCommand.append(" sphere ").appendO(propertyValue);
        surfaceObjectSeen = true;
        break;
      case T.squared:
        propertyName = "squareData";
        propertyValue = Boolean.TRUE;
        sbCommand.append(" squared");
        break;
      case T.inline:
        propertyName = (!surfaceObjectSeen && !planeSeen && !isMapped ? "readFile"
            : "mapColor");
        str = stringParameter(++i);
        if (str == null)
          invArg();
        // inline PMESH data
        if (isPmesh)
          str = PT.replaceAllCharacter(str, "{,}|", ' ');
        if (eval.logMessages)
          Logger.debug("pmesh inline data:\n" + str);
        propertyValue = (chk ? null : str);
        addShapeProperty(propertyList, "fileName", "");
        sbCommand.append(" INLINE ").append(Escape.eS(str));
        surfaceObjectSeen = true;
        break;
      case T.string:
        boolean firstPass = (!surfaceObjectSeen && !planeSeen);
        propertyName = (firstPass && !isMapped ? "readFile" : "mapColor");
        String filename = parameterAsString(i);
        /*
         * A file name, optionally followed by a calculation type and/or an integer file index.
         * Or =xxxx, an EDM from Uppsala Electron Density Server
         * If the model auxiliary info has "jmolSufaceInfo", we use that.
         */
        if (filename.startsWith("=") && filename.length() > 1) {
          String[] info = (String[]) viewer.setLoadFormat(filename, '_', false);
          filename = info[0];
          String strCutoff = (!firstPass || !Float.isNaN(cutoff) ? null
              : info[1]);
          if (strCutoff != null && !chk) {
            cutoff = SV.fValue(SV.getVariable(viewer
                .evaluateExpression(strCutoff)));
            if (cutoff > 0) {
              if (!Float.isNaN(sigma)) {
                cutoff *= sigma;
                sigma = Float.NaN;
                addShapeProperty(propertyList, "sigma", Float.valueOf(sigma));
              }
              addShapeProperty(propertyList, "cutoff", Float.valueOf(cutoff));
              sbCommand.append(" cutoff ").appendF(cutoff);
            }
          }
          if (ptWithin == 0) {
            onlyOneModel = "=xxxx";
            if (modelIndex < 0)
              modelIndex = viewer.getCurrentModelIndex();
            bs = viewer.getModelUndeletedAtomsBitSet(modelIndex);
            getWithinDistanceVector(propertyList, 2.0f, null, bs, false);
            sbCommand.append(" within 2.0 ").append(Escape.eBS(bs));
          }
          if (firstPass)
            defaultMesh = true;
        }

        if (firstPass && viewer.getParameter("_fileType").equals("Pdb")
            && Float.isNaN(sigma) && Float.isNaN(cutoff)) {
          // negative sigma just indicates that 
          addShapeProperty(propertyList, "sigma", Float.valueOf(-1));
          sbCommand.append(" sigma -1.0");
        }
        if (filename.length() == 0) {
          if (modelIndex < 0)
            modelIndex = viewer.getCurrentModelIndex();
          filename = eval.getFullPathName();
          propertyValue = viewer.getModelAuxiliaryInfoValue(modelIndex,
              "jmolSurfaceInfo");
        }
        int fileIndex = -1;
        if (propertyValue == null && tokAt(i + 1) == T.integer)
          addShapeProperty(propertyList, "fileIndex", Integer
              .valueOf(fileIndex = intParameter(++i)));
        String stype = (tokAt(i + 1) == T.string ? stringParameter(++i) : null);
        // done reading parameters
        surfaceObjectSeen = true;
        if (chk) {
          break;
        }
        String[] fullPathNameOrError;
        String localName = null;
        if (propertyValue == null) {
          if (fullCommand.indexOf("# FILE" + nFiles + "=") >= 0) {
            // old way, abandoned
            filename = PT.getQuotedAttribute(fullCommand, "# FILE" + nFiles);
            if (tokAt(i + 1) == T.as)
              i += 2; // skip that
          } else if (tokAt(i + 1) == T.as) {
            localName = viewer.getFilePath(
                stringParameter(eval.iToken = (i = i + 2)), false);
            fullPathNameOrError = viewer.getFullPathNameOrError(localName);
            localName = fullPathNameOrError[0];
            if (viewer.getPathForAllFiles() != "") {
              // we use the LOCAL name when reading from a local path only (in the case of JMOL files)
              filename = localName;
              localName = null;
            } else {
              addShapeProperty(propertyList, "localName", localName);
            }
          }
        }
        // just checking here, and getting the full path name
        if (!filename.startsWith("cache://") && stype == null) {
          fullPathNameOrError = viewer.getFullPathNameOrError(filename);
          filename = fullPathNameOrError[0];
          if (fullPathNameOrError[1] != null)
            eval.errorStr(ScriptEvaluator.ERROR_fileNotFoundException, filename
                + ":" + fullPathNameOrError[1]);
        }
        Logger.info("reading isosurface data from " + filename);

        if (stype != null) {
          propertyValue = viewer.cacheGet(filename);
          addShapeProperty(propertyList, "calculationType", stype);
        }
        if (propertyValue == null) {
          addShapeProperty(propertyList, "fileName", filename);
          if (localName != null)
            filename = localName;
          if (fileIndex >= 0)
            sbCommand.append(" ").appendI(fileIndex);
        }
        sbCommand.append(" /*file*/").append(Escape.eS(filename));
        if (stype != null)
          sbCommand.append(" ").append(Escape.eS(stype));
        break;
      case T.connect:
        propertyName = "connections";
        switch (tokAt(++i)) {
        case T.bitset:
        case T.expressionBegin:
          propertyValue = new int[] { atomExpressionAt(i).nextSetBit(0) };
          break;
        default:
          propertyValue = new int[] { (int) eval.floatParameterSet(i, 1, 1)[0] };
          break;
        }
        i = eval.iToken;
        break;
      case T.atomindex:
        propertyName = "atomIndex";
        propertyValue = Integer.valueOf(intParameter(++i));
        break;
      case T.link:
        propertyName = "link";
        sbCommand.append(" link");
        break;
      case T.lattice:
        if (iShape != JC.SHAPE_ISOSURFACE)
          invArg();
        pt = getPoint3f(eval.iToken + 1, false);
        i = eval.iToken;
        if (pt.x <= 0 || pt.y <= 0 || pt.z <= 0)
          break;
        pt.x = (int) pt.x;
        pt.y = (int) pt.y;
        pt.z = (int) pt.z;
        sbCommand.append(" lattice ").append(Escape.eP(pt));
        if (isMapped) {
          propertyName = "mapLattice";
          propertyValue = pt;
        } else {
          lattice = pt;
        }
        break;
      default:
        if (eval.theTok == T.identifier) {
          propertyName = "thisID";
          propertyValue = str;
        }
        /* I have no idea why this is here....
        if (planeSeen && !surfaceObjectSeen) {
          addShapeProperty(propertyList, "nomap", Float.valueOf(0));
          surfaceObjectSeen = true;
        }
        */
        if (!eval.setMeshDisplayProperty(iShape, 0, eval.theTok)) {
          if (T.tokAttr(eval.theTok, T.identifier) && !idSeen) {
            setShapeId(iShape, i, idSeen);
            i = eval.iToken;
            break;
          }
          invArg();
        }
        if (iptDisplayProperty == 0)
          iptDisplayProperty = i;
        i = slen - 1;
        break;
      }
      idSeen = (eval.theTok != T.delete);
      if (isWild && surfaceObjectSeen)
        invArg();
      if (propertyName != null)
        addShapeProperty(propertyList, propertyName, propertyValue);
    }

    // OK, now send them all

    if (!chk) {
      if ((isCavity || haveRadius) && !surfaceObjectSeen) {
        surfaceObjectSeen = true;
        addShapeProperty(propertyList, "bsSolvent", (haveRadius ? new BS()
            : eval.lookupIdentifierValue("solvent")));
        addShapeProperty(propertyList, "sasurface", Float.valueOf(0));
      }
      if (planeSeen && !surfaceObjectSeen && !isMapped) {
        // !isMapped mp.added 6/14/2012 12.3.30
        // because it was preventing planes from being mapped properly
        addShapeProperty(propertyList, "nomap", Float.valueOf(0));
        surfaceObjectSeen = true;
      }
      if (thisSetNumber >= -1)
        addShapeProperty(propertyList, "getSurfaceSets", Integer
            .valueOf(thisSetNumber - 1));
      if (discreteColixes != null) {
        addShapeProperty(propertyList, "colorDiscrete", discreteColixes);
      } else if ("sets".equals(colorScheme)) {
        addShapeProperty(propertyList, "setColorScheme", null);
      } else if (colorScheme != null) {
        ColorEncoder ce = viewer.getColorEncoder(colorScheme);
        if (ce != null) {
          ce.isTranslucent = isColorSchemeTranslucent;
          ce.hi = Float.MAX_VALUE;
          addShapeProperty(propertyList, "remapColor", ce);
        }
      }
      if (surfaceObjectSeen && !isLcaoCartoon && sbCommand.indexOf(";") != 0) {
        propertyList.add(0, new Object[] { "newObject", null });
        boolean needSelect = (bsSelect == null);
        if (needSelect)
          bsSelect = BSUtil.copy(viewer.getSelectionSet(false));
        if (modelIndex < 0)
          modelIndex = viewer.getCurrentModelIndex();
        bsSelect.and(viewer.getModelUndeletedAtomsBitSet(modelIndex));
        if (onlyOneModel != null) {
          BS bsModels = viewer.getModelBitSet(bsSelect, false);
          if (bsModels.cardinality() != 1)
            eval.errorStr(ScriptEvaluator.ERROR_multipleModelsDisplayedNotOK,
                "ISOSURFACE " + onlyOneModel);
          if (needSelect) {
            propertyList.add(0, new Object[] { "select", bsSelect });
            if (sbCommand.indexOf("; isosurface map") == 0) {
              sbCommand = new SB().append("; isosurface map select ").append(
                  Escape.eBS(bsSelect)).append(sbCommand.substring(16));
            }
          }
        }
      }
      if (haveIntersection && !haveSlab) {
        if (!surfaceObjectSeen)
          addShapeProperty(propertyList, "sasurface", Float.valueOf(0));
        if (!isMapped) {
          addShapeProperty(propertyList, "map", Boolean.TRUE);
          addShapeProperty(propertyList, "select", bs);
          addShapeProperty(propertyList, "sasurface", Float.valueOf(0));
        }
        addShapeProperty(propertyList, "slab", getCapSlabObject(-100, false));
      }

      boolean timeMsg = (surfaceObjectSeen && viewer.getBoolean(T.showtiming));
      if (timeMsg)
        Logger.startTimer("isosurface");
      setShapeProperty(iShape, "setProperties", propertyList);
      if (timeMsg)
        showString(Logger.getTimerMsg("isosurface", 0));
      if (defaultMesh) {
        setShapeProperty(iShape, "token", Integer.valueOf(T.mesh));
        setShapeProperty(iShape, "token", Integer.valueOf(T.nofill));
        isFrontOnly = true;
        sbCommand.append(" mesh nofill frontOnly");
      }
    }
    if (lattice != null) // before MAP, this is a display option
      setShapeProperty(iShape, "lattice", lattice);
    if (symops != null) // before MAP, this is a display option
      setShapeProperty(iShape, "symops", symops);
    if (isFrontOnly)
      setShapeProperty(iShape, "token", Integer.valueOf(T.frontonly));
    if (iptDisplayProperty > 0) {
      if (!eval.setMeshDisplayProperty(iShape, iptDisplayProperty, 0))
        invArg();
    }
    if (chk)
      return false;
    Object area = null;
    Object volume = null;
    if (doCalcArea) {
      area = getShapeProperty(iShape, "area");
      if (area instanceof Float)
        viewer.setFloatProperty("isosurfaceArea", ((Float) area).floatValue());
      else
        viewer.setUserVariable("isosurfaceArea", SV
            .getVariableAD((double[]) area));
    }
    if (doCalcVolume) {
      volume = (doCalcVolume ? getShapeProperty(iShape, "volume") : null);
      if (volume instanceof Float)
        viewer.setFloatProperty("isosurfaceVolume", ((Float) volume)
            .floatValue());
      else
        viewer.setUserVariable("isosurfaceVolume", SV
            .getVariableAD((double[]) volume));
    }
    if (!isLcaoCartoon) {
      String s = null;
      if (isMapped && !surfaceObjectSeen) {
        setShapeProperty(iShape, "finalize", sbCommand.toString());
      } else if (surfaceObjectSeen) {
        cmd = sbCommand.toString();
        setShapeProperty(iShape, "finalize",
            (cmd.indexOf("; isosurface map") == 0 ? "" : " select "
                + Escape.eBS(bsSelect) + " ")
                + cmd);
        s = (String) getShapeProperty(iShape, "ID");
        if (s != null && !eval.tQuiet) {
          cutoff = ((Float) getShapeProperty(iShape, "cutoff")).floatValue();
          if (Float.isNaN(cutoff) && !Float.isNaN(sigma)) {
            Logger.error("sigma not supported");
          }
          s += " created";
          if (isIsosurface)
            s += " with cutoff=" + cutoff;
          float[] minMax = (float[]) getShapeProperty(iShape, "minMaxInfo");
          if (minMax[0] != Float.MAX_VALUE)
            s += " min=" + minMax[0] + " max=" + minMax[1];
          s += "; " + JC.shapeClassBases[iShape].toLowerCase() + " count: "
              + getShapeProperty(iShape, "count");
          s += eval.getIsosurfaceDataRange(iShape, "\n");
        }
      }
      String sarea, svol;
      if (doCalcArea || doCalcVolume) {
        sarea = (doCalcArea ? "isosurfaceArea = "
            + (area instanceof Float ? "" + area : Escape.eAD((double[]) area))
            : null);
        svol = (doCalcVolume ? "isosurfaceVolume = "
            + (volume instanceof Float ? "" + volume : Escape
                .eAD((double[]) volume)) : null);
        if (s == null) {
          if (doCalcArea)
            showString(sarea);
          if (doCalcVolume)
            showString(svol);
        } else {
          if (doCalcArea)
            s += "\n" + sarea;
          if (doCalcVolume)
            s += "\n" + svol;
        }
      }
      if (s != null)
        showString(s);
    }
    if (translucency != null)
      setShapeProperty(iShape, "translucency", translucency);
    setShapeProperty(iShape, "clear", null);
    if (toCache)
      setShapeProperty(iShape, "cache", null);
    listIsosurface(iShape);
    return true;
  }

  /**
   * 
   * @param bsSelected
   * @param bsIgnore
   * @param fileName
   * @return calculated atom potentials
   */
  private float[] getAtomicPotentials(BS bsSelected, BS bsIgnore,
                                     String fileName) {
    float[] potentials = new float[viewer.getAtomCount()];
    MepCalculationInterface m = (MepCalculationInterface) Interface
        .getOptionInterface("quantum.MlpCalculation");
    m.set(viewer);
    String data = (fileName == null ? null : viewer.getFileAsString(fileName));
    m.assignPotentials(viewer.modelSet.atoms, potentials, viewer.getSmartsMatch("a",
        bsSelected), viewer.getSmartsMatch("/noAromatic/[$(C=O),$(O=C),$(NC=O)]",
        bsSelected), bsIgnore, data);
    return potentials;
  }

  private boolean lcaoCartoon() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    sm.loadShape(JC.SHAPE_LCAOCARTOON);
    if (tokAt(1) == T.list && listIsosurface(JC.SHAPE_LCAOCARTOON))
      return false;
    setShapeProperty(JC.SHAPE_LCAOCARTOON, "init", fullCommand);
    if (slen == 1) {
      setShapeProperty(JC.SHAPE_LCAOCARTOON, "lcaoID", null);
      return false;
    }
    boolean idSeen = false;
    String translucency = null;
    for (int i = 1; i < slen; i++) {
      String propertyName = null;
      Object propertyValue = null;
      switch (getToken(i).tok) {
      case T.cap:
      case T.slab:
        propertyName = (String) eval.theToken.value;
        if (tokAt(i + 1) == T.off)
          eval.iToken = i + 1;
        propertyValue = getCapSlabObject(i, true);
        i = eval.iToken;
        break;
      case T.center:
        // serialized lcaoCartoon in isosurface format
        isosurface(JC.SHAPE_LCAOCARTOON);
        return false;
      case T.rotate:
        float degx = 0;
        float degy = 0;
        float degz = 0;
        switch (getToken(++i).tok) {
        case T.x:
          degx = floatParameter(++i) * JC.radiansPerDegree;
          break;
        case T.y:
          degy = floatParameter(++i) * JC.radiansPerDegree;
          break;
        case T.z:
          degz = floatParameter(++i) * JC.radiansPerDegree;
          break;
        default:
          invArg();
        }
        propertyName = "rotationAxis";
        propertyValue = V3.new3(degx, degy, degz);
        break;
      case T.on:
      case T.display:
      case T.displayed:
        propertyName = "on";
        break;
      case T.off:
      case T.hide:
      case T.hidden:
        propertyName = "off";
        break;
      case T.delete:
        propertyName = "delete";
        break;
      case T.bitset:
      case T.expressionBegin:
        propertyName = "select";
        propertyValue = atomExpressionAt(i);
        i = eval.iToken;
        break;
      case T.color:
        translucency = setColorOptions(null, i + 1, JC.SHAPE_LCAOCARTOON, -2);
        if (translucency != null)
          setShapeProperty(JC.SHAPE_LCAOCARTOON, "settranslucency",
              translucency);
        i = eval.iToken;
        idSeen = true;
        continue;
      case T.translucent:
      case T.opaque:
        eval.setMeshDisplayProperty(JC.SHAPE_LCAOCARTOON, i, eval.theTok);
        i = eval.iToken;
        idSeen = true;
        continue;
      case T.spacefill:
      case T.string:
        propertyValue = parameterAsString(i).toLowerCase();
        if (propertyValue.equals("spacefill"))
          propertyValue = "cpk";
        propertyName = "create";
        if (eval.optParameterAsString(i + 1).equalsIgnoreCase("molecular")) {
          i++;
          propertyName = "molecular";
        }
        break;
      case T.select:
        if (tokAt(i + 1) == T.bitset || tokAt(i + 1) == T.expressionBegin) {
          propertyName = "select";
          propertyValue = atomExpressionAt(i + 1);
          i = eval.iToken;
        } else {
          propertyName = "selectType";
          propertyValue = parameterAsString(++i);
          if (propertyValue.equals("spacefill"))
            propertyValue = "cpk";
        }
        break;
      case T.scale:
        propertyName = "scale";
        propertyValue = Float.valueOf(floatParameter(++i));
        break;
      case T.lonepair:
      case T.lp:
        propertyName = "lonePair";
        break;
      case T.radical:
      case T.rad:
        propertyName = "radical";
        break;
      case T.molecular:
        propertyName = "molecular";
        break;
      case T.create:
        propertyValue = parameterAsString(++i);
        propertyName = "create";
        if (eval.optParameterAsString(i + 1).equalsIgnoreCase("molecular")) {
          i++;
          propertyName = "molecular";
        }
        break;
      case T.id:
        propertyValue = eval.getShapeNameParameter(++i);
        i = eval.iToken;
        if (idSeen)
          invArg();
        propertyName = "lcaoID";
        break;
      default:
        if (eval.theTok == T.times || T.tokAttr(eval.theTok, T.identifier)) {
          if (eval.theTok != T.times)
            propertyValue = parameterAsString(i);
          if (idSeen)
            invArg();
          propertyName = "lcaoID";
          break;
        }
        break;
      }
      if (eval.theTok != T.delete)
        idSeen = true;
      if (propertyName == null)
        invArg();
      setShapeProperty(JC.SHAPE_LCAOCARTOON, propertyName, propertyValue);
    }
    setShapeProperty(JC.SHAPE_LCAOCARTOON, "clear", null);
    return true;
  }

  private Object getCapSlabObject(int i, boolean isLcaoCartoon)
      throws ScriptException {
    if (i < 0) {
      // standard range -100 to 0
      return TempArray.getSlabWithinRange(i, 0);
    }
    ScriptEvaluator eval = this.eval;
    Object data = null;
    int tok0 = tokAt(i);
    boolean isSlab = (tok0 == T.slab);
    int tok = tokAt(i + 1);
    P4 plane = null;
    P3[] pts = null;
    float d, d2;
    BS bs = null;
    Short slabColix = null;
    Integer slabMeshType = null;
    if (tok == T.translucent) {
      float slabTranslucency = (isFloatParameter(++i + 1) ? floatParameter(++i)
          : 0.5f);
      if (eval.isColorParam(i + 1)) {
        slabColix = Short.valueOf(C.getColixTranslucent3(C.getColix(eval
            .getArgbParam(i + 1)), slabTranslucency != 0, slabTranslucency));
        i = eval.iToken;
      } else {
        slabColix = Short.valueOf(C.getColixTranslucent3(C.INHERIT_COLOR,
            slabTranslucency != 0, slabTranslucency));
      }
      switch (tok = tokAt(i + 1)) {
      case T.mesh:
      case T.fill:
        slabMeshType = Integer.valueOf(tok);
        tok = tokAt(++i + 1);
        break;
      default:
        slabMeshType = Integer.valueOf(T.fill);
        break;
      }
    }
    //TODO: check for compatibility with LCAOCARTOONS
    switch (tok) {
    case T.bitset:
    case T.expressionBegin:
      data = atomExpressionAt(i + 1);
      tok = T.decimal;
      eval.iToken++;
      break;
    case T.off:
      eval.iToken = i + 1;
      return Integer.valueOf(Integer.MIN_VALUE);
    case T.none:
      eval.iToken = i + 1;
      break;
    case T.dollarsign:
      // do we need distance here? "-" here?
      i++;
      data = new Object[] { Float.valueOf(1), parameterAsString(++i) };
      tok = T.mesh;
      break;
    case T.within:
      // isosurface SLAB WITHIN RANGE f1 f2
      i++;
      if (tokAt(++i) == T.range) {
        d = floatParameter(++i);
        d2 = floatParameter(++i);
        data = new Object[] { Float.valueOf(d), Float.valueOf(d2) };
        tok = T.range;
      } else if (isFloatParameter(i)) {
        // isosurface SLAB WITHIN distance {atomExpression}|[point array]
        d = floatParameter(i);
        if (eval.isCenterParameter(++i)) {
          P3 pt = centerParameter(i);
          if (chk || !(eval.expressionResult instanceof BS)) {
            pts = new P3[] { pt };
          } else {
            Atom[] atoms = viewer.modelSet.atoms;
            bs = (BS) eval.expressionResult;
            pts = new P3[bs.cardinality()];
            for (int k = 0, j = bs.nextSetBit(0); j >= 0; j = bs
                .nextSetBit(j + 1), k++)
              pts[k] = atoms[j];
          }
        } else {
          pts = eval.getPointArray(i, -1);
        }
        if (pts.length == 0) {
          eval.iToken = i;
          invArg();
        }
        data = new Object[] { Float.valueOf(d), pts, bs };
      } else {
        data = eval.getPointArray(i, 4);
        tok = T.boundbox;
      }
      break;
    case T.boundbox:
      eval.iToken = i + 1;
      data = BoxInfo.getCriticalPoints(viewer.getBoundBoxVertices(), null);
      break;
    //case Token.slicebox:
    // data = BoxInfo.getCriticalPoints(((JmolViewer)(viewer)).slicer.getSliceVert(), null);
    //eval.iToken = i + 1;
    //break;  
    case T.brillouin:
    case T.unitcell:
      eval.iToken = i + 1;
      SymmetryInterface unitCell = viewer.getCurrentUnitCell();
      if (unitCell == null) {
        if (tok == T.unitcell)
          invArg();
      } else {
        pts = BoxInfo.getCriticalPoints(unitCell.getUnitCellVertices(),
            unitCell.getCartesianOffset());
        int iType = (int) unitCell
            .getUnitCellInfoType(SimpleUnitCell.INFO_DIMENSIONS);
        V3 v1 = null;
        V3 v2 = null;
        switch (iType) {
        case 3:
          break;
        case 1: // polymer
          v2 = V3.newVsub(pts[2], pts[0]);
          v2.scale(1000f);
          //$FALL-THROUGH$
        case 2: // slab
          // "a b c" is really "z y x"
          v1 = V3.newVsub(pts[1], pts[0]);
          v1.scale(1000f);
          pts[0].sub(v1);
          pts[1].scale(2000f);
          if (iType == 1) {
            pts[0].sub(v2);
            pts[2].scale(2000f);
          }
          break;
        }
        data = pts;
      }
      break;
    default:
      // isosurface SLAB n
      // isosurface SLAB -100. 0.  as "within range" 
      if (!isLcaoCartoon && isSlab && isFloatParameter(i + 1)) {
        d = floatParameter(++i);
        if (!isFloatParameter(i + 1))
          return Integer.valueOf((int) d);
        d2 = floatParameter(++i);
        data = new Object[] { Float.valueOf(d), Float.valueOf(d2) };
        tok = T.range;
        break;
      }
      // isosurface SLAB [plane]
      plane = eval.planeParameter(++i);
      float off = (isFloatParameter(eval.iToken + 1) ? floatParameter(++eval.iToken)
          : Float.NaN);
      if (!Float.isNaN(off))
        plane.w -= off;
      data = plane;
      tok = T.plane;
    }
    Object colorData = (slabMeshType == null ? null : new Object[] {
        slabMeshType, slabColix });
    return TempArray.getSlabObjectType(tok, data, !isSlab, colorData);
  }

  private boolean mo(boolean isInitOnly) throws ScriptException {
    ScriptEvaluator eval = this.eval;
    int offset = Integer.MAX_VALUE;
    boolean isNegOffset = false;
    BS bsModels = viewer.getVisibleFramesBitSet();
    List<Object[]> propertyList = new List<Object[]>();
    int i0 = 1;
    if (tokAt(1) == T.model || tokAt(1) == T.frame) {
      i0 = eval.modelNumberParameter(2);
      if (i0 < 0)
        invArg();
      bsModels.clearAll();
      bsModels.set(i0);
      i0 = 3;
    }
    for (int iModel = bsModels.nextSetBit(0); iModel >= 0; iModel = bsModels
        .nextSetBit(iModel + 1)) {
      sm.loadShape(JC.SHAPE_MO);
      int i = i0;
      if (tokAt(i) == T.list && listIsosurface(JC.SHAPE_MO))
        return true;
      setShapeProperty(JC.SHAPE_MO, "init", Integer.valueOf(iModel));
      String title = null;
      int moNumber = ((Integer) getShapeProperty(JC.SHAPE_MO, "moNumber"))
          .intValue();
      float[] linearCombination = (float[]) getShapeProperty(JC.SHAPE_MO,
          "moLinearCombination");
      if (isInitOnly)
        return true;// (moNumber != 0);
      if (moNumber == 0)
        moNumber = Integer.MAX_VALUE;
      String propertyName = null;
      Object propertyValue = null;

      switch (getToken(i).tok) {
      case T.cap:
      case T.slab:
        propertyName = (String) eval.theToken.value;
        propertyValue = getCapSlabObject(i, false);
        i = eval.iToken;
        break;
      case T.density:
        propertyName = "squareLinear";
        propertyValue = Boolean.TRUE;
        linearCombination = new float[] { 1 };
        offset = moNumber = 0;
        break;
      case T.integer:
        moNumber = intParameter(i);
        linearCombination = moCombo(propertyList);
        if (linearCombination == null && moNumber < 0)
          linearCombination = new float[] { -100, -moNumber };
        break;
      case T.minus:
        switch (tokAt(++i)) {
        case T.homo:
        case T.lumo:
          break;
        default:
          invArg();
        }
        isNegOffset = true;
        //$FALL-THROUGH$
      case T.homo:
      case T.lumo:
        if ((offset = moOffset(i)) == Integer.MAX_VALUE)
          invArg();
        moNumber = 0;
        linearCombination = moCombo(propertyList);
        break;
      case T.next:
        moNumber = T.next;
        linearCombination = moCombo(propertyList);
        break;
      case T.prev:
        moNumber = T.prev;
        linearCombination = moCombo(propertyList);
        break;
      case T.color:
        setColorOptions(null, i + 1, JC.SHAPE_MO, 2);
        break;
      case T.plane:
        // plane {X, Y, Z, W}
        propertyName = "plane";
        propertyValue = eval.planeParameter(i + 1);
        break;
      case T.point:
        addShapeProperty(propertyList, "randomSeed",
            tokAt(i + 2) == T.integer ? Integer.valueOf(intParameter(i + 2))
                : null);
        propertyName = "monteCarloCount";
        propertyValue = Integer.valueOf(intParameter(i + 1));
        break;
      case T.scale:
        propertyName = "scale";
        propertyValue = Float.valueOf(floatParameter(i + 1));
        break;
      case T.cutoff:
        if (tokAt(i + 1) == T.plus) {
          propertyName = "cutoffPositive";
          propertyValue = Float.valueOf(floatParameter(i + 2));
        } else {
          propertyName = "cutoff";
          propertyValue = Float.valueOf(floatParameter(i + 1));
        }
        break;
      case T.debug:
        propertyName = "debug";
        break;
      case T.noplane:
        propertyName = "plane";
        break;
      case T.pointsperangstrom:
      case T.resolution:
        propertyName = "resolution";
        propertyValue = Float.valueOf(floatParameter(i + 1));
        break;
      case T.squared:
        propertyName = "squareData";
        propertyValue = Boolean.TRUE;
        break;
      case T.titleformat:
        if (i + 1 < slen && tokAt(i + 1) == T.string) {
          propertyName = "titleFormat";
          propertyValue = parameterAsString(i + 1);
        }
        break;
      case T.identifier:
        invArg();
        break;
      default:
        if (eval.isArrayParameter(i)) {
          linearCombination = eval.floatParameterSet(i, 1, Integer.MAX_VALUE);
          if (tokAt(eval.iToken + 1) == T.squared) {
            addShapeProperty(propertyList, "squareLinear", Boolean.TRUE);
            eval.iToken++;
          }
          break;
        }
        int ipt = eval.iToken;
        if (!eval.setMeshDisplayProperty(JC.SHAPE_MO, 0, eval.theTok))
          invArg();
        setShapeProperty(JC.SHAPE_MO, "setProperties", propertyList);
        eval.setMeshDisplayProperty(JC.SHAPE_MO, ipt, tokAt(ipt));
        return true;
      }
      if (propertyName != null)
        addShapeProperty(propertyList, propertyName, propertyValue);
      if (moNumber != Integer.MAX_VALUE || linearCombination != null) {
        if (tokAt(eval.iToken + 1) == T.string)
          title = parameterAsString(++eval.iToken);
        eval.setCursorWait(true);
        setMoData(propertyList, moNumber, linearCombination, offset,
            isNegOffset, iModel, title);
        addShapeProperty(propertyList, "finalize", null);
      }
      if (propertyList.size() > 0)
        setShapeProperty(JC.SHAPE_MO, "setProperties", propertyList);
      propertyList.clear();
    }
    return true;
  }

  private float[] moCombo(List<Object[]> propertyList) {
    if (tokAt(eval.iToken + 1) != T.squared)
      return null;
    addShapeProperty(propertyList, "squareLinear", Boolean.TRUE);
    eval.iToken++;
    return new float[0];
  }

  private int moOffset(int index) throws ScriptException {
    boolean isHomo = (getToken(index).tok == T.homo);
    int offset = (isHomo ? 0 : 1);
    int tok = tokAt(++index);
    if (tok == T.integer && intParameter(index) < 0)
      offset += intParameter(index);
    else if (tok == T.plus)
      offset += intParameter(++index);
    else if (tok == T.minus)
      offset -= intParameter(++index);
    return offset;
  }

  @SuppressWarnings("unchecked")
  private void setMoData(List<Object[]> propertyList, int moNumber,
                         float[] lc, int offset, boolean isNegOffset,
                         int modelIndex, String title) throws ScriptException {
    ScriptEvaluator eval = this.eval;
    if (chk)
      return;
    if (modelIndex < 0) {
      modelIndex = viewer.getCurrentModelIndex();
      if (modelIndex < 0)
        eval.errorStr(ScriptEvaluator.ERROR_multipleModelsDisplayedNotOK, "MO isosurfaces");
    }
    Map<String, Object> moData = (Map<String, Object>) viewer.getModelAuxiliaryInfoValue(modelIndex, "moData");
    List<Map<String, Object>> mos = null;
    Map<String, Object> mo;
    Float f;
    int nOrb = 0;
    if (lc == null || lc.length < 2) {
      if (lc != null && lc.length == 1)
        offset = 0;
      if (moData == null)
        error(ScriptEvaluator.ERROR_moModelError);
      int lastMoNumber = (moData.containsKey("lastMoNumber") ? ((Integer) moData
          .get("lastMoNumber")).intValue()
          : 0);
      int lastMoCount = (moData.containsKey("lastMoCount") ? ((Integer) moData
          .get("lastMoCount")).intValue() : 1);
      if (moNumber == T.prev)
        moNumber = lastMoNumber - 1;
      else if (moNumber == T.next)
        moNumber = lastMoNumber + lastMoCount;
      mos = (List<Map<String, Object>>) (moData.get("mos"));
      nOrb = (mos == null ? 0 : mos.size());
      if (nOrb == 0)
        error(ScriptEvaluator.ERROR_moCoefficients);
      if (nOrb == 1 && moNumber > 1)
        error(ScriptEvaluator.ERROR_moOnlyOne);
      if (offset != Integer.MAX_VALUE) {
        // 0: HOMO;
        if (moData.containsKey("HOMO")) {
          moNumber = ((Integer) moData.get("HOMO")).intValue() + offset;
        } else {
          moNumber = -1;
          for (int i = 0; i < nOrb; i++) {
            mo = mos.get(i);
            if ((f = (Float) mo.get("occupancy")) != null) {
              if (f.floatValue() < 0.5f) {
                // go for LUMO = first unoccupied
                moNumber = i;
                break;
              }
              continue;
            } else if ((f = (Float) mo.get("energy")) != null) {
              if (f.floatValue() > 0) {
                // go for LUMO = first positive
                moNumber = i;
                break;
              }
              continue;
            }
            break;
          }
          if (moNumber < 0)
            error(ScriptEvaluator.ERROR_moOccupancy);
          moNumber += offset;
        }
        Logger.info("MO " + moNumber);
      }
      if (moNumber < 1 || moNumber > nOrb)
        eval.errorStr(ScriptEvaluator.ERROR_moIndex, "" + nOrb);
    }
    moNumber = Math.abs(moNumber);
    moData.put("lastMoNumber", Integer.valueOf(moNumber));
    moData.put("lastMoCount", Integer.valueOf(1));
    if (isNegOffset && lc == null)
      lc = new float[] { -100, moNumber };
    if (lc != null && lc.length < 2) {
      mo = mos.get(moNumber - 1);
      if ((f = (Float) mo.get("energy")) == null) {
        lc = new float[] { 100, moNumber };
      } else {

        // constuct set of equivalent energies and square this

        float energy = f.floatValue();
        BS bs = BS.newN(nOrb);
        int n = 0;
        boolean isAllElectrons = (lc.length == 1 && lc[0] == 1);
        for (int i = 0; i < nOrb; i++) {
          if ((f = (Float) mos.get(i).get("energy")) == null)
            continue;
          float e = f.floatValue();
          if (isAllElectrons ? e <= energy : e == energy) {
            bs.set(i + 1);
            n += 2;
          }
        }
        lc = new float[n];
        for (int i = 0, pt = 0; i < n; i += 2) {
          lc[i] = 1;
          lc[i + 1] = (pt = bs.nextSetBit(pt + 1));
        }
        moData.put("lastMoNumber", Integer.valueOf(bs.nextSetBit(0)));
        moData.put("lastMoCount", Integer.valueOf(n / 2));
      }
      addShapeProperty(propertyList, "squareLinear", Boolean.TRUE);
    }
    addShapeProperty(propertyList, "moData", moData);
    if (title != null)
      addShapeProperty(propertyList, "title", title);
    addShapeProperty(propertyList, "molecularOrbital", lc != null ? lc
        : Integer.valueOf(Math.abs(moNumber)));
    addShapeProperty(propertyList, "clear", null);
  }

  @Override
  public String plot(T[] args) throws ScriptException {
    st = eval.st;
    chk = eval.chk;
    // also used for draw [quaternion, helix, ramachandran] 
    // and write quaternion, ramachandran, plot, ....
    // and plot property propertyX, propertyY, propertyZ //
    int modelIndex = viewer.getCurrentModelIndex();
    if (modelIndex < 0)
      eval.errorStr(ScriptEvaluator.ERROR_multipleModelsDisplayedNotOK, "plot");
    modelIndex = viewer.getJmolDataSourceFrame(modelIndex);
    int pt = args.length - 1;
    boolean isReturnOnly = (args != st);
    T[] statementSave = st;
    if (isReturnOnly)
      eval.st = st = args;
    int tokCmd = (isReturnOnly ? T.show : args[0].tok);
    int pt0 = (isReturnOnly || tokCmd == T.quaternion
        || tokCmd == T.ramachandran ? 0 : 1);
    String filename = null;
    boolean makeNewFrame = true;
    boolean isDraw = false;
    switch (tokCmd) {
    case T.plot:
    case T.quaternion:
    case T.ramachandran:
      break;
    case T.draw:
      makeNewFrame = false;
      isDraw = true;
      break;
    case T.show:
      makeNewFrame = false;
      break;
    case T.write:
      makeNewFrame = false;
      if (tokAtArray(pt, args) == T.string) {
        filename = stringParameter(pt--);
      } else if (tokAtArray(pt - 1, args) == T.per) {
        filename = parameterAsString(pt - 2) + "." + parameterAsString(pt);
        pt -= 3;
      } else {
        eval.st = st = statementSave;
        eval.iToken = st.length;
        error(ScriptEvaluator.ERROR_endOfStatementUnexpected);
      }
      break;
    }
    String qFrame = "";
    Object[] parameters = null;
    String stateScript = "";
    boolean isQuaternion = false;
    boolean isDerivative = false;
    boolean isSecondDerivative = false;
    boolean isRamachandranRelative = false;
    int propertyX = 0, propertyY = 0, propertyZ = 0;
    BS bs = BSUtil.copy(viewer.getSelectionSet(false));
    String preSelected = "; select " + Escape.eBS(bs) + ";\n ";
    String type = eval.optParameterAsString(pt).toLowerCase();
    P3 minXYZ = null;
    P3 maxXYZ = null;
    int tok = tokAtArray(pt0, args);
    if (tok == T.string)
      tok = T.getTokFromName((String) args[pt0].value);
    switch (tok) {
    default:
      eval.iToken = 1;
      invArg();
      break;
    case T.data:
      eval.iToken = 1;
      type = "data";
      preSelected = "";
      break;
    case T.property:
      eval.iToken = pt0 + 1;
      if (!T.tokAttr(propertyX = tokAt(eval.iToken++), T.atomproperty)
          || !T.tokAttr(propertyY = tokAt(eval.iToken++), T.atomproperty))
        invArg();
      if (T.tokAttr(propertyZ = tokAt(eval.iToken), T.atomproperty))
        eval.iToken++;
      else
        propertyZ = 0;
      if (tokAt(eval.iToken) == T.min) {
        minXYZ = getPoint3f(++eval.iToken, false);
        eval.iToken++;
      }
      if (tokAt(eval.iToken) == T.max) {
        maxXYZ = getPoint3f(++eval.iToken, false);
        eval.iToken++;
      }
      type = "property " + T.nameOf(propertyX) + " "
          + T.nameOf(propertyY)
          + (propertyZ == 0 ? "" : " " + T.nameOf(propertyZ));
      if (bs.nextSetBit(0) < 0)
        bs = viewer.getModelUndeletedAtomsBitSet(modelIndex);
      stateScript = "select " + Escape.eBS(bs) + ";\n ";
      break;
    case T.ramachandran:
      if (type.equalsIgnoreCase("draw")) {
        isDraw = true;
        type = eval.optParameterAsString(--pt).toLowerCase();
      }
      isRamachandranRelative = (pt > pt0 && type.startsWith("r"));
      type = "ramachandran" + (isRamachandranRelative ? " r" : "")
          + (tokCmd == T.draw ? " draw" : "");
      break;
    case T.quaternion:
    case T.helix:
      qFrame = " \"" + viewer.getQuaternionFrame() + "\"";
      stateScript = "set quaternionFrame" + qFrame + ";\n  ";
      isQuaternion = true;
      // working backward this time:
      if (type.equalsIgnoreCase("draw")) {
        isDraw = true;
        type = eval.optParameterAsString(--pt).toLowerCase();
      }
      isDerivative = (type.startsWith("deriv") || type.startsWith("diff"));
      isSecondDerivative = (isDerivative && type.indexOf("2") > 0);
      if (isDerivative)
        pt--;
      if (type.equalsIgnoreCase("helix") || type.equalsIgnoreCase("axis")) {
        isDraw = true;
        isDerivative = true;
        pt = -1;
      }
      type = ((pt <= pt0 ? "" : eval.optParameterAsString(pt)) + "w")
          .substring(0, 1);
      if (type.equals("a") || type.equals("r"))
        isDerivative = true;
      if (!PT.isOneOf(type, ";w;x;y;z;r;a;")) // a absolute; r relative
        eval.evalError("QUATERNION [w,x,y,z,a,r] [difference][2]", null);
      type = "quaternion " + type + (isDerivative ? " difference" : "")
          + (isSecondDerivative ? "2" : "") + (isDraw ? " draw" : "");
      break;
    }
    st = statementSave;
    if (chk) // just in case we later mp.add parameter options to this
      return "";

    // if not just drawing check to see if there is already a plot of this type

    if (makeNewFrame) {
      stateScript += "plot " + type;
      int ptDataFrame = viewer.getJmolDataFrameIndex(modelIndex, stateScript);
      if (ptDataFrame > 0 && tokCmd != T.write && tokCmd != T.show) {
        // no -- this is that way we switch frames. viewer.deleteAtoms(viewer.getModelUndeletedAtomsBitSet(ptDataFrame), true);
        // data frame can't be 0.
        viewer.setCurrentModelIndexClear(ptDataFrame, true);
        // BitSet bs2 = viewer.getModelAtomBitSet(ptDataFrame);
        // bs2.and(bs);
        // need to be able to set data directly as well.
        // viewer.display(BitSetUtil.setAll(viewer.getAtomCount()), bs2, tQuiet);
        return "";
      }
    }

    // prepare data for property plotting

    float[] dataX = null, dataY = null, dataZ = null;
    P3 factors = P3.new3(1, 1, 1);
    if (tok == T.property) {
      dataX = eval.getBitsetPropertyFloat(bs, propertyX | T.selectedfloat,
          (minXYZ == null ? Float.NaN : minXYZ.x), (maxXYZ == null ? Float.NaN
              : maxXYZ.x));
      dataY = eval.getBitsetPropertyFloat(bs, propertyY | T.selectedfloat,
          (minXYZ == null ? Float.NaN : minXYZ.y), (maxXYZ == null ? Float.NaN
              : maxXYZ.y));
      if (propertyZ != 0)
        dataZ = eval.getBitsetPropertyFloat(bs, propertyZ | T.selectedfloat,
            (minXYZ == null ? Float.NaN : minXYZ.z),
            (maxXYZ == null ? Float.NaN : maxXYZ.z));
      if (minXYZ == null)
        minXYZ = P3.new3(getPlotMinMax(dataX, false, propertyX), getPlotMinMax(
            dataY, false, propertyY), getPlotMinMax(dataZ, false, propertyZ));
      if (maxXYZ == null)
        maxXYZ = P3.new3(getPlotMinMax(dataX, true, propertyX), getPlotMinMax(
            dataY, true, propertyY), getPlotMinMax(dataZ, true, propertyZ));
      Logger.info("plot min/max: " + minXYZ + " " + maxXYZ);
      P3 center = new P3();
      center.ave(maxXYZ, minXYZ);
      factors.sub2(maxXYZ, minXYZ);
      factors.set(factors.x / 200, factors.y / 200, factors.z / 200);
      if (T.tokAttr(propertyX, T.intproperty)) {
        factors.x = 1;
        center.x = 0;
      } else if (factors.x > 0.1 && factors.x <= 10) {
        factors.x = 1;
      }
      if (T.tokAttr(propertyY, T.intproperty)) {
        factors.y = 1;
        center.y = 0;
      } else if (factors.y > 0.1 && factors.y <= 10) {
        factors.y = 1;
      }
      if (T.tokAttr(propertyZ, T.intproperty)) {
        factors.z = 1;
        center.z = 0;
      } else if (factors.z > 0.1 && factors.z <= 10) {
        factors.z = 1;
      }
      if (propertyZ == 0)
        center.z = minXYZ.z = maxXYZ.z = factors.z = 0;
      for (int i = 0; i < dataX.length; i++)
        dataX[i] = (dataX[i] - center.x) / factors.x;
      for (int i = 0; i < dataY.length; i++)
        dataY[i] = (dataY[i] - center.y) / factors.y;
      if (propertyZ != 0)
        for (int i = 0; i < dataZ.length; i++)
          dataZ[i] = (dataZ[i] - center.z) / factors.z;
      parameters = new Object[] { bs, dataX, dataY, dataZ, minXYZ, maxXYZ,
          factors, center };
    }

    // all set...

    if (tokCmd == T.write)
      return viewer.writeFileData(filename, "PLOT_" + type, modelIndex,
          parameters);
    
    String data = (type.equals("data") ? "1 0 H 0 0 0 # Jmol PDB-encoded data" : viewer.getPdbData(modelIndex, type, parameters));
    
    if (tokCmd == T.show)
      return data;

    if (Logger.debugging)
      Logger.debug(data);

    if (tokCmd == T.draw) {
      eval.runScript(data);
      return "";
    }

    // create the new model

    String[] savedFileInfo = viewer.getFileInfo();
    boolean oldAppendNew = viewer.getBoolean(T.appendnew);
    viewer.setAppendNew(true);
    boolean isOK = (data != null && viewer.openStringInlineParamsAppend(data, null, true) == null);
    viewer.setAppendNew(oldAppendNew);
    viewer.setFileInfo(savedFileInfo);
    if (!isOK)
      return "";
    int modelCount = viewer.getModelCount();
    viewer.setJmolDataFrame(stateScript, modelIndex, modelCount - 1);
    if (tok != T.property)
      stateScript += ";\n" + preSelected;
    StateScript ss = viewer.addStateScript(stateScript, true, false);

    // get post-processing script

    float radius = 150;
    String script;
    switch (tok) {
    default:
      script = "frame 0.0; frame last; reset;select visible;wireframe only;";
      radius = 10;
      break;
    case T.property:
      viewer.setFrameTitle(modelCount - 1, type + " plot for model "
          + viewer.getModelNumberDotted(modelIndex));
      float f = 3;
      script = "frame 0.0; frame last; reset;" + "select visible; spacefill "
          + f + "; wireframe 0;" + "draw plotAxisX" + modelCount
          + " {100 -100 -100} {-100 -100 -100} \"" + T.nameOf(propertyX)
          + "\";" + "draw plotAxisY" + modelCount
          + " {-100 100 -100} {-100 -100 -100} \"" + T.nameOf(propertyY)
          + "\";";
      if (propertyZ != 0)
        script += "draw plotAxisZ" + modelCount
            + " {-100 -100 100} {-100 -100 -100} \"" + T.nameOf(propertyZ)
            + "\";";
      break;
    case T.ramachandran:
      viewer.setFrameTitle(modelCount - 1, "ramachandran plot for model "
          + viewer.getModelNumberDotted(modelIndex));
      script = "frame 0.0; frame last; reset;"
          + "select visible; color structure; spacefill 3.0; wireframe 0;"
          + "draw ramaAxisX" + modelCount + " {100 0 0} {-100 0 0} \"phi\";"
          + "draw ramaAxisY" + modelCount + " {0 100 0} {0 -100 0} \"psi\";";
      break;
    case T.quaternion:
    case T.helix:
      viewer.setFrameTitle(modelCount - 1, type.replace('w', ' ') + qFrame
          + " for model " + viewer.getModelNumberDotted(modelIndex));
      String color = (C
          .getHexCode(viewer.getColixBackgroundContrast()));
      script = "frame 0.0; frame last; reset;"
          + "select visible; wireframe 0; spacefill 3.0; "
          + "isosurface quatSphere" + modelCount + " color " + color
          + " sphere 100.0 mesh nofill frontonly translucent 0.8;"
          + "draw quatAxis" + modelCount
          + "X {100 0 0} {-100 0 0} color red \"x\";" + "draw quatAxis"
          + modelCount + "Y {0 100 0} {0 -100 0} color green \"y\";"
          + "draw quatAxis" + modelCount
          + "Z {0 0 100} {0 0 -100} color blue \"z\";" + "color structure;"
          + "draw quatCenter" + modelCount + "{0 0 0} scale 0.02;";
      break;
    }

    // run the post-processing script and set rotation radius and display frame title
    eval.runScript(script + preSelected);
    ss.setModelIndex(viewer.getCurrentModelIndex());
    viewer.setRotationRadius(radius, true);
    sm.loadShape(JC.SHAPE_ECHO);
    showString("frame " + viewer.getModelNumberDotted(modelCount - 1)
        + (type.length() > 0 ? " created: " + type + (isQuaternion ? qFrame : "") : ""));
    return "";
  }

  private float getPlotMinMax(float[] data, boolean isMax, int tok) {
    if (data == null)
      return 0;
    switch (tok) {
    case T.omega:
    case T.phi:
    case T.psi:
      return (isMax ? 180 : -180);
    case T.eta:
    case T.theta:
      return (isMax ? 360 : 0);
    case T.straightness:
      return (isMax ? 1 : -1);
    }
    float fmax = (isMax ? -1E10f : 1E10f);
    for (int i = data.length; --i >= 0;) {
      float f = data[i];
      if (Float.isNaN(f))
        continue;
      if (isMax == (f > fmax))
        fmax = f;
    }
    return fmax;
  }

  private boolean polyhedra() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    /*
     * needsGenerating:
     * 
     * polyhedra [number of vertices and/or basis] [at most two selection sets]
     * [optional type and/or edge] [optional design parameters]
     * 
     * OR else:
     * 
     * polyhedra [at most one selection set] [type-and/or-edge or on/off/delete]
     */
    boolean needsGenerating = false;
    boolean onOffDelete = false;
    boolean typeSeen = false;
    boolean edgeParameterSeen = false;
    boolean isDesignParameter = false;
    int lighting = 0;
    int nAtomSets = 0;
    sm.loadShape(JC.SHAPE_POLYHEDRA);
    setShapeProperty(JC.SHAPE_POLYHEDRA, "init", Boolean.TRUE);
    String setPropertyName = "centers";
    String decimalPropertyName = "radius_";
    float translucentLevel = Float.MAX_VALUE;
    eval.colorArgb[0] = Integer.MIN_VALUE;
    for (int i = 1; i < slen; ++i) {
      String propertyName = null;
      Object propertyValue = null;
      switch (getToken(i).tok) {
      case T.delete:
      case T.on:
      case T.off:
        if (i + 1 != slen || needsGenerating || nAtomSets > 1
            || nAtomSets == 0 && "to".equals(setPropertyName))
          error(ScriptEvaluator.ERROR_incompatibleArguments);
        propertyName = (eval.theTok == T.off ? "off" : eval.theTok == T.on ? "on"
            : "delete");
        onOffDelete = true;
        break;
      case T.opEQ:
      case T.comma:
        continue;
      case T.bonds:
        if (nAtomSets > 0)
          invPO();
        needsGenerating = true;
        propertyName = "bonds";
        break;
      case T.radius:
        decimalPropertyName = "radius";
        continue;
      case T.integer:
      case T.decimal:
        if (nAtomSets > 0 && !isDesignParameter)
          invPO();
        if (eval.theTok == T.integer) {
          if (decimalPropertyName == "radius_") {
            propertyName = "nVertices";
            propertyValue = Integer.valueOf(intParameter(i));
            needsGenerating = true;
            break;
          }
        }
        propertyName = (decimalPropertyName == "radius_" ? "radius"
            : decimalPropertyName);
        propertyValue = Float.valueOf(floatParameter(i));
        decimalPropertyName = "radius_";
        isDesignParameter = false;
        needsGenerating = true;
        break;
      case T.bitset:
      case T.expressionBegin:
        if (typeSeen)
          invPO();
        if (++nAtomSets > 2)
          error(ScriptEvaluator.ERROR_badArgumentCount);
        if ("to".equals(setPropertyName))
          needsGenerating = true;
        propertyName = setPropertyName;
        setPropertyName = "to";
        propertyValue = atomExpressionAt(i);
        i =eval.iToken;
        break;
      case T.to:
        if (nAtomSets > 1)
          invPO();
        if (tokAt(i + 1) == T.bitset 
            || tokAt(i + 1) == T.expressionBegin && !needsGenerating) {
          propertyName = "toBitSet";
          propertyValue = atomExpressionAt(++i);
          i = eval.iToken;
          needsGenerating = true;
          break;
        } else if (!needsGenerating) {
          error(ScriptEvaluator.ERROR_insufficientArguments);
        }
        setPropertyName = "to";
        continue;
      case T.facecenteroffset:
        if (!needsGenerating)
          error(ScriptEvaluator.ERROR_insufficientArguments);
        decimalPropertyName = "faceCenterOffset";
        isDesignParameter = true;
        continue;
      case T.distancefactor:
        if (nAtomSets == 0)
          error(ScriptEvaluator.ERROR_insufficientArguments);
        decimalPropertyName = "distanceFactor";
        isDesignParameter = true;
        continue;
      case T.color:
      case T.translucent:
      case T.opaque:
        translucentLevel = eval.getColorTrans(i, true);
        i = eval.iToken;
        continue;
      case T.collapsed:
      case T.flat:
        propertyName = "collapsed";
        propertyValue = (eval.theTok == T.collapsed ? Boolean.TRUE
            : Boolean.FALSE);
        if (typeSeen)
          error(ScriptEvaluator.ERROR_incompatibleArguments);
        typeSeen = true;
        break;
      case T.noedges:
      case T.edges:
      case T.frontedges:
        if (edgeParameterSeen)
          error(ScriptEvaluator.ERROR_incompatibleArguments);
        propertyName = parameterAsString(i);
        edgeParameterSeen = true;
        break;
      case T.fullylit:
        lighting = eval.theTok;
        continue;
      default:
        if (eval.isColorParam(i)) {
          eval.colorArgb[0] = eval.getArgbParam(i);
          i = eval.iToken;
          continue;
        }
        invArg();
      }
      setShapeProperty(JC.SHAPE_POLYHEDRA, propertyName,
          propertyValue);
      if (onOffDelete)
        return false;
    }
    if (!needsGenerating && !typeSeen && !edgeParameterSeen && lighting == 0)
      error(ScriptEvaluator.ERROR_insufficientArguments);
    if (needsGenerating)
      setShapeProperty(JC.SHAPE_POLYHEDRA, "generate", null);
    if (eval.colorArgb[0] != Integer.MIN_VALUE)
      setShapeProperty(JC.SHAPE_POLYHEDRA, "colorThis", Integer
          .valueOf(eval.colorArgb[0]));
    if (translucentLevel != Float.MAX_VALUE)
      eval.setShapeTranslucency(JC.SHAPE_POLYHEDRA, "", "translucentThis",
          translucentLevel, null);
    if (lighting != 0)
      setShapeProperty(JC.SHAPE_POLYHEDRA, "token", Integer.valueOf(lighting));
    setShapeProperty(JC.SHAPE_POLYHEDRA, "init", Boolean.FALSE);
    return true;
  }

  private boolean struts() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    boolean defOn = (tokAt(1) == T.only || tokAt(1) == T.on || slen == 1);
    int mad = eval.getMadParameter();
    if (defOn)
      mad = Math.round (viewer.getFloat(T.strutdefaultradius) * 2000f);
    setShapeProperty(JC.SHAPE_STICKS, "type", Integer
        .valueOf(JmolEdge.BOND_STRUT));
    eval.setShapeSizeBs(JC.SHAPE_STICKS, mad, null);
    setShapeProperty(JC.SHAPE_STICKS, "type", Integer
        .valueOf(JmolEdge.BOND_COVALENT_MASK));
    return true;
  }

  private String initIsosurface(int iShape) throws ScriptException {

    // handle isosurface/mo/pmesh delete and id delete here

    ScriptEvaluator eval = this.eval;
    setShapeProperty(iShape, "init", fullCommand);
    eval.iToken = 0;
    int tok1 = tokAt(1);
    int tok2 = tokAt(2);
    if (tok1 == T.delete || tok2 == T.delete && tokAt(++eval.iToken) == T.all) {
      setShapeProperty(iShape, "delete", null);
      eval.iToken += 2;
      if (slen > eval.iToken) {
        setShapeProperty(iShape, "init", fullCommand);
        setShapeProperty(iShape, "thisID", MeshCollection.PREVIOUS_MESH_ID);
      }
      return null;
    }
    eval.iToken = 1;
    if (!eval.setMeshDisplayProperty(iShape, 0, tok1)) {
      setShapeProperty(iShape, "thisID", MeshCollection.PREVIOUS_MESH_ID);
      if (iShape != JC.SHAPE_DRAW)
        setShapeProperty(iShape, "title", new String[] { thisCommand });
      if (tok1 != T.id
          && (tok2 == T.times || tok1 == T.times
              && eval.setMeshDisplayProperty(iShape, 0, tok2))) {
        String id = setShapeId(iShape, 1, false);
        eval.iToken++;
        return id;
      }
    }
    return null;
  }

  private void getWithinDistanceVector(List<Object[]> propertyList,
                                       float distance, P3 ptc, BS bs,
                                       boolean isShow) {
    List<P3> v = new List<P3>();
    P3[] pts = new P3[2];
    if (bs == null) {
      P3 pt1 = P3.new3(distance, distance, distance);
      P3 pt0 = P3.newP(ptc);
      pt0.sub(pt1);
      pt1.add(ptc);
      pts[0] = pt0;
      pts[1] = pt1;
      v.addLast(ptc);
    } else {
      BoxInfo bbox = viewer.getBoxInfo(bs, -Math.abs(distance));
      pts[0] = bbox.getBoundBoxVertices()[0];
      pts[1] = bbox.getBoundBoxVertices()[7];
      if (bs.cardinality() == 1)
        v.addLast(viewer.getAtomPoint3f(bs.nextSetBit(0)));
    }
    if (v.size() == 1 && !isShow) {
      addShapeProperty(propertyList, "withinDistance", Float.valueOf(distance));
      addShapeProperty(propertyList, "withinPoint", v.get(0));
    }
    addShapeProperty(propertyList, (isShow ? "displayWithin" : "withinPoints"),
        new Object[] { Float.valueOf(distance), pts, bs, v });
  }

  private String setColorOptions(SB sb, int index, int iShape, int nAllowed)
      throws ScriptException {
    ScriptEvaluator eval = this.eval;
    getToken(index);
    String translucency = "opaque";
    if (eval.theTok == T.translucent) {
      translucency = "translucent";
      if (nAllowed < 0) {
        float value = (isFloatParameter(index + 1) ? floatParameter(++index)
            : Float.MAX_VALUE);
        eval.setShapeTranslucency(iShape, null, "translucent", value, null);
        if (sb != null) {
          sb.append(" translucent");
          if (value != Float.MAX_VALUE)
            sb.append(" ").appendF(value);
        }
      } else {
        eval.setMeshDisplayProperty(iShape, index, eval.theTok);
      }
    } else if (eval.theTok == T.opaque) {
      if (nAllowed >= 0)
        eval.setMeshDisplayProperty(iShape, index, eval.theTok);
    } else {
      eval.iToken--;
    }
    nAllowed = Math.abs(nAllowed);
    for (int i = 0; i < nAllowed; i++) {
      if (eval.isColorParam(eval.iToken + 1)) {
        int color = eval.getArgbParam(++eval.iToken);
        setShapeProperty(iShape, "colorRGB", Integer.valueOf(color));
        if (sb != null)
          sb.append(" ").append(Escape.escapeColor(color));
      } else if (eval.iToken < index) {
        invArg();
      } else {
        break;
      }
    }
    return translucency;
  }

  private void addShapeProperty(List<Object[]> propertyList, String key,
                                Object value) {
    if (chk)
      return;
    propertyList.addLast(new Object[] { key, value });
  }

  /**
   * for the ISOSURFACE command
   * 
   * @param fname
   * @param xyz
   * @param ret
   * @return [ ScriptFunction, Params ]
   */
  private Object[] createFunction(String fname, String xyz, String ret) {
    ScriptEvaluator e = (new ScriptEvaluator());
    e.setViewer(viewer);
    try {
      e.compileScript(null, "function " + fname + "(" + xyz + ") { return "
          + ret + "}", false);
      List<SV> params = new List<SV>();
      for (int i = 0; i < xyz.length(); i += 2)
        params.addLast(SV.newV(T.decimal, Float.valueOf(0f)).setName(
            xyz.substring(i, i + 1)));
      return new Object[] { e.aatoken[0][1].value, params };
    } catch (Exception ex) {
      return null;
    }
  }

  private float[][] floatArraySet(int i, int nX, int nY) throws ScriptException {
    int tok = tokAt(i++);
    if (tok == T.spacebeforesquare)
      tok = tokAt(i++);
    if (tok != T.leftsquare)
      invArg();
    float[][] fparams = AU.newFloat2(nX);
    int n = 0;
    while (tok != T.rightsquare) {
      tok = getToken(i).tok;
      switch (tok) {
      case T.spacebeforesquare:
      case T.rightsquare:
        continue;
      case T.comma:
        i++;
        break;
      case T.leftsquare:
        i++;
        float[] f = new float[nY];
        fparams[n++] = f;
        for (int j = 0; j < nY; j++) {
          f[j] = floatParameter(i++);
          if (tokAt(i) == T.comma)
            i++;
        }
        if (tokAt(i++) != T.rightsquare)
          invArg();
        tok = T.nada;
        if (n == nX && tokAt(i) != T.rightsquare)
          invArg();
        break;
      default:
        invArg();
      }
    }
    return fparams;
  }

  private float[][][] floatArraySetXYZ(int i, int nX, int nY, int nZ)
      throws ScriptException {
    ScriptEvaluator eval = this.eval;
    int tok = tokAt(i++);
    if (tok == T.spacebeforesquare)
      tok = tokAt(i++);
    if (tok != T.leftsquare || nX <= 0)
      invArg();
    float[][][] fparams = AU.newFloat3(nX, -1);
    int n = 0;
    while (tok != T.rightsquare) {
      tok = getToken(i).tok;
      switch (tok) {
      case T.spacebeforesquare:
      case T.rightsquare:
        continue;
      case T.comma:
        i++;
        break;
      case T.leftsquare:
        fparams[n++] = floatArraySet(i, nY, nZ);
        i = ++eval.iToken;
        tok = T.nada;
        if (n == nX && tokAt(i) != T.rightsquare)
          invArg();
        break;
      default:
        invArg();
      }
    }
    return fparams;
  }

  private boolean listIsosurface(int iShape) throws ScriptException {
    String s = (slen > 3 ? "0" : tokAt(2) == T.nada ? "" : " " + getToken(2).value);
    if (!chk)
      showString((String) getShapeProperty(iShape, "list"
          + s));
    return true;
  }

  @Override
  @SuppressWarnings("static-access")
  public Object getBitsetIdent(BS bs, String label, Object tokenValue,
                        boolean useAtomMap, int index, boolean isExplicitlyAll) {
    boolean isAtoms = !(tokenValue instanceof BondSet);
    if (isAtoms) {
      if (label == null)
        label = viewer.getStandardLabelFormat(0);
      else if (label.length() == 0)
        label = "%[label]";
    }
    int pt = (label == null ? -1 : label.indexOf("%"));
    boolean haveIndex = (index != Integer.MAX_VALUE);
    if (bs == null || chk || isAtoms && pt < 0) {
      if (label == null)
        label = "";
      return isExplicitlyAll ? new String[] { label } : (Object) label;
    }
    ModelSet modelSet = viewer.modelSet;
    int n = 0;
    LabelToken labeler = modelSet.getLabeler();
    int[] indices = (isAtoms || !useAtomMap ? null : ((BondSet) tokenValue)
        .getAssociatedAtoms());
    if (indices == null && label != null && label.indexOf("%D") > 0)
      indices = viewer.getAtomIndices(bs);
    boolean asIdentity = (label == null || label.length() == 0);
    Map<String, Object> htValues = (isAtoms || asIdentity ? null : LabelToken
        .getBondLabelValues());
    LabelToken[] tokens = (asIdentity ? null : isAtoms ? labeler.compile(
        viewer, label, '\0', null) : labeler.compile(viewer, label, '\1',
        htValues));
    int nmax = (haveIndex ? 1 : BSUtil.cardinalityOf(bs));
    String[] sout = new String[nmax];
    for (int j = (haveIndex ? index : bs.nextSetBit(0)); j >= 0; j = bs
        .nextSetBit(j + 1)) {
      String str;
      if (isAtoms) {
        if (asIdentity)
          str = modelSet.atoms[j].getInfo();
        else
          str = labeler.formatLabelAtomArray(viewer, modelSet.atoms[j],
              tokens, '\0', indices);
      } else {
        Bond bond = modelSet.getBondAt(j);
        if (asIdentity)
          str = bond.getIdentity();
        else
          str = labeler.formatLabelBond(viewer, bond, tokens, htValues,
              indices);
      }
      str = Txt.formatStringI(str, "#", (n + 1));
      sout[n++] = str;
      if (haveIndex)
        break;
    }
    return nmax == 1 && !isExplicitlyAll ? sout[0] : (Object) sout;
  }

  private Object[] data;

  public void data() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    String dataString = null;
    String dataLabel = null;
    boolean isOneValue = false;
    int i;
    switch (eval.iToken = slen) {
    case 5:
      // parameters 3 and 4 are just for the ride: [end] and ["key"]
      dataString = parameterAsString(2);
      //$FALL-THROUGH$
    case 4:
    case 2:
      dataLabel = parameterAsString(1);
      if (dataLabel.equalsIgnoreCase("clear")) {
        if (!chk)
          viewer.setData(null, null, 0, 0, 0, 0, 0);
        return;
      }
      if ((i = dataLabel.indexOf("@")) >= 0) {
        dataString = "" + eval.getParameter(dataLabel.substring(i + 1), T.string);
        dataLabel = dataLabel.substring(0, i).trim();
      } else if (dataString == null && (i = dataLabel.indexOf(" ")) >= 0) {
        dataString = dataLabel.substring(i + 1).trim();
        dataLabel = dataLabel.substring(0, i).trim();
        isOneValue = true;
      }
      break;
    default:
      error(ScriptEvaluator.ERROR_badArgumentCount);
    }
    String dataType = dataLabel + " ";
    dataType = dataType.substring(0, dataType.indexOf(" ")).toLowerCase();
    if (dataType.equals("model") || dataType.equals("append")) {
      eval.load();
      return;
    }
    if (chk)
      return;
    boolean isDefault = (dataLabel.toLowerCase().indexOf("(default)") >= 0);
    if (dataType.equals("connect_atoms")) {
      viewer.connect(Parser.parseFloatArray2d(dataString));
      return;
    }
    if (dataType.indexOf("ligand_") == 0) {
      // ligand structure for pdbAddHydrogen
      viewer.setLigandModel(dataLabel.substring(7).toUpperCase() + "_data",
          dataString.trim());
      return;
    }
    if (dataType.indexOf("file_") == 0) {
      // ligand structure for pdbAddHydrogen
      viewer.setLigandModel(dataLabel.substring(5).toUpperCase() + "_file",
          dataString.trim());
      return;
    }
    data = new Object[4];
    // not saving this data in the state?
    if (dataType.equals("element_vdw")) {
      // vdw for now
      data[0] = dataType;
      data[1] = dataString.replace(';', '\n');
      int n = Elements.elementNumberMax;
      int[] eArray = new int[n + 1];
      for (int ie = 1; ie <= n; ie++)
        eArray[ie] = ie;
      data[2] = eArray;
      data[3] = Integer.valueOf(0);
      viewer.setData("element_vdw", data, n, 0, 0, 0, 0);
      return;
    }
    if (dataType.indexOf("data2d_") == 0) {
      // data2d_someName
      data[0] = dataLabel;
      data[1] = Parser.parseFloatArray2d(dataString);
      data[3] = Integer.valueOf(2);
      viewer.setData(dataLabel, data, 0, 0, 0, 0, 0);
      return;
    }
    if (dataType.indexOf("data3d_") == 0) {
      // data3d_someName
      data[0] = dataLabel;
      data[1] = Parser.parseFloatArray3d(dataString);
      data[3] = Integer.valueOf(3);
      viewer.setData(dataLabel, data, 0, 0, 0, 0, 0);
      return;
    }
    String[] tokens = PT.getTokens(dataLabel);
    if (dataType.indexOf("property_") == 0
        && !(tokens.length == 2 && tokens[1].equals("set"))) {
      BS bs = viewer.getSelectionSet(false);
      data[0] = dataType;
      int atomNumberField = (isOneValue ? 0 : ((Integer) viewer
          .getParameter("propertyAtomNumberField")).intValue());
      int atomNumberFieldColumnCount = (isOneValue ? 0 : ((Integer) viewer
          .getParameter("propertyAtomNumberColumnCount")).intValue());
      int propertyField = (isOneValue ? Integer.MIN_VALUE : ((Integer) viewer
          .getParameter("propertyDataField")).intValue());
      int propertyFieldColumnCount = (isOneValue ? 0 : ((Integer) viewer
          .getParameter("propertyDataColumnCount")).intValue());
      if (!isOneValue && dataLabel.indexOf(" ") >= 0) {
        if (tokens.length == 3) {
          // DATA "property_whatever [atomField] [propertyField]"
          dataLabel = tokens[0];
          atomNumberField = PT.parseInt(tokens[1]);
          propertyField = PT.parseInt(tokens[2]);
        }
        if (tokens.length == 5) {
          // DATA
          // "property_whatever [atomField] [atomFieldColumnCount] [propertyField] [propertyDataColumnCount]"
          dataLabel = tokens[0];
          atomNumberField = PT.parseInt(tokens[1]);
          atomNumberFieldColumnCount = PT.parseInt(tokens[2]);
          propertyField = PT.parseInt(tokens[3]);
          propertyFieldColumnCount = PT.parseInt(tokens[4]);
        }
      }
      if (atomNumberField < 0)
        atomNumberField = 0;
      if (propertyField < 0)
        propertyField = 0;
      int atomCount = viewer.getAtomCount();
      int[] atomMap = null;
      BS bsTemp = BSUtil.newBitSet(atomCount);
      if (atomNumberField > 0) {
        atomMap = new int[atomCount + 2];
        for (int j = 0; j <= atomCount; j++)
          atomMap[j] = -1;
        for (int j = bs.nextSetBit(0); j >= 0; j = bs.nextSetBit(j + 1)) {
          int atomNo = viewer.getAtomNumber(j);
          if (atomNo > atomCount + 1 || atomNo < 0 || bsTemp.get(atomNo))
            continue;
          bsTemp.set(atomNo);
          atomMap[atomNo] = j;
        }
        data[2] = atomMap;
      } else {
        data[2] = BSUtil.copy(bs);
      }
      data[1] = dataString;
      data[3] = Integer.valueOf(0);
      viewer.setData(dataType, data, atomCount, atomNumberField,
          atomNumberFieldColumnCount, propertyField, propertyFieldColumnCount);
      return;
    }
    int userType = AtomCollection.getUserSettableType(dataType);
    if (userType >= 0) {
      // this is a known settable type or "property_xxxx"
      viewer.setAtomData(userType, dataType, dataString, isDefault);
      return;
    }
    // this is just information to be stored.
    data[0] = dataLabel;
    data[1] = dataString;
    data[3] = Integer.valueOf(0);
    viewer.setData(dataType, data, 0, 0, 0, 0, 0);
  }

  public void navigate() throws ScriptException {
    /*
     * navigation on/off navigation depth p # would be as a depth value, like
     * slab, in percent, but could be negative navigation nSec translate X Y #
     * could be percentages navigation nSec translate $object # could be a draw
     * object navigation nSec translate (atom selection) #average of values
     * navigation nSec center {x y z} navigation nSec center $object navigation
     * nSec center (atom selection) navigation nSec path $object navigation nSec
     * path {x y z theta} {x y z theta}{x y z theta}{x y z theta}... navigation
     * nSec trace (atom selection)
     */
    ScriptEvaluator eval = this.eval;
    if (slen == 1) {
      eval.setBooleanProperty("navigationMode", true);
      return;
    }
    V3 rotAxis = V3.new3(0, 1, 0);
    List<Object[]> list = new List<Object[]>();
    P3 pt;
    if (slen == 2) {
      switch (getToken(1).tok) {
      case T.on:
      case T.off:
        if (chk)
          return;
        eval.setObjectMad(JC.SHAPE_AXES, "axes", 1);
        setShapeProperty(JC.SHAPE_AXES, "position", P3.new3(50, 50,
            Float.MAX_VALUE));
        eval.setBooleanProperty("navigationMode", true);
        viewer.setNavOn(eval.theTok == T.on);
        return;
      case T.stop:
        if (!chk)
          viewer.setNavXYZ(0, 0, 0);
        return;
      case T.point3f:
      case T.trace:
        break;
      default:
        invArg();
      }
    }
    if (!chk && !viewer.getBoolean(T.navigationmode))
      eval.setBooleanProperty("navigationMode", true);
    for (int i = 1; i < slen; i++) {
      float timeSec = (isFloatParameter(i) ? floatParameter(i++) : 2f);
      if (timeSec < 0)
        invArg();
      if (!chk && timeSec > 0)
        eval.refresh();
      switch (getToken(i).tok) {
      case T.point3f:
      case T.leftbrace:
        // navigate {x y z}
        pt = getPoint3f(i, true);
        eval.iToken++;
        if (eval.iToken != slen)
          invArg();
        if (!chk)
          viewer.setNavXYZ(pt.x, pt.y, pt.z);
        return;
      case T.depth:
        float depth = floatParameter(++i);
        if (!chk)
          list.addLast(new Object[] { Integer.valueOf(T.depth),
              Float.valueOf(timeSec), Float.valueOf(depth) });
        //viewer.setNavigationDepthPercent(timeSec, depth);
        continue;
      case T.center:
        pt = centerParameter(++i);
        i = eval.iToken;
        if (!chk)
          list.addLast(new Object[] { Integer.valueOf(T.point),
              Float.valueOf(timeSec), pt });
        //viewer.navigatePt(timeSec, pt);
        continue;
      case T.rotate:
        switch (getToken(++i).tok) {
        case T.x:
          rotAxis.set(1, 0, 0);
          i++;
          break;
        case T.y:
          rotAxis.set(0, 1, 0);
          i++;
          break;
        case T.z:
          rotAxis.set(0, 0, 1);
          i++;
          break;
        case T.point3f:
        case T.leftbrace:
          rotAxis.setT(getPoint3f(i, true));
          i = eval.iToken + 1;
          break;
        case T.identifier:
          invArg(); // for now
          break;
        }
        float degrees = floatParameter(i);
        if (!chk)
          list.addLast(new Object[] { Integer.valueOf(T.rotate),
              Float.valueOf(timeSec), rotAxis, Float.valueOf(degrees) });
        //          viewer.navigateAxis(timeSec, rotAxis, degrees);
        continue;
      case T.translate:
        float x = Float.NaN;
        float y = Float.NaN;
        if (isFloatParameter(++i)) {
          x = floatParameter(i);
          y = floatParameter(++i);
        } else {
          switch (tokAt(i)) {
          case T.x:
            x = floatParameter(++i);
            break;
          case T.y:
            y = floatParameter(++i);
            break;
          default:
            pt = centerParameter(i);
            i = eval.iToken;
            if (!chk)
              list.addLast(new Object[] { Integer.valueOf(T.translate),
                  Float.valueOf(timeSec), pt });
            //viewer.navTranslate(timeSec, pt);
            continue;
          }
        }
        if (!chk)
          list.addLast(new Object[] { Integer.valueOf(T.percent),
              Float.valueOf(timeSec), Float.valueOf(x), Float.valueOf(y) });
        //viewer.navTranslatePercent(timeSec, x, y);
        continue;
      case T.divide:
        continue;
      case T.trace:
        P3[][] pathGuide;
        List<P3[]> vp = new List<P3[]>();
        BS bs;
        if (tokAt(i + 1) == T.bitset || tokAt(i + 1) ==  T.expressionBegin) {
          bs = atomExpressionAt(++i);
          i = eval.iToken;
        } else {
          bs = viewer.getSelectionSet(false);
        }
        if (chk)
          return;
        viewer.getPolymerPointsAndVectors(bs, vp);
        int n;
        if ((n = vp.size()) > 0) {
          pathGuide = new P3[n][];
          for (int j = 0; j < n; j++) {
            pathGuide[j] = vp.get(j);
          }
          list.addLast(new Object[] { Integer.valueOf(T.trace),
              Float.valueOf(timeSec), pathGuide });
          //viewer.navigateGuide(timeSec, pathGuide);
          continue;
        }
        break;
      case T.path:
        P3[] path;
        float[] theta = null; // orientation; null for now
        if (getToken(i + 1).tok == T.dollarsign) {
          i++;
          // navigate timeSeconds path $id indexStart indexEnd
          String pathID = eval.objectNameParameter(++i);
          if (chk)
            return;
          setShapeProperty(JC.SHAPE_DRAW, "thisID", pathID);
          path = (P3[]) getShapeProperty(JC.SHAPE_DRAW, "vertices");
          eval.refresh();
          if (path == null)
            invArg();
          int indexStart = (int) (isFloatParameter(i + 1) ? floatParameter(++i)
              : 0);
          int indexEnd = (int) (isFloatParameter(i + 1) ? floatParameter(++i)
              : Integer.MAX_VALUE);
          list.addLast(new Object[] { Integer.valueOf(T.path),
              Float.valueOf(timeSec), path, theta,
              new int[] { indexStart, indexEnd } });
          //viewer.navigatePath(timeSec, path, theta, indexStart, indexEnd);
          continue;
        }
        List<P3> v = new List<P3>();
        while (eval.isCenterParameter(i + 1)) {
          v.addLast(centerParameter(++i));
          i = eval.iToken;
        }
        if (v.size() > 0) {
          path = v.toArray(new P3[v.size()]);
          if (!chk)
            list.addLast(new Object[] { Integer.valueOf(T.path),
                Float.valueOf(timeSec), path, theta,
                new int[] { 0, Integer.MAX_VALUE } });
          //viewer.navigatePath(timeSec, path, theta, 0, Integer.MAX_VALUE);
          continue;
        }
        //$FALL-THROUGH$
      default:
        invArg();
      }
    }
    if (!chk)
      viewer.navigateList(eval, list);
  }

  /**
   * used for TRY command
   * 
   * @param context
   * @param shapeManager
   * @return true if successful; false if not
   */
  @Override
  public boolean evaluateParallel(ScriptContext context,
                                  ShapeManager shapeManager) {
    ScriptEvaluator e = new ScriptEvaluator();
    e.setViewer(viewer);
    e.historyDisabled = true;
    e.compiler = new ScriptCompiler(viewer);
    e.sm = shapeManager;
    try {
      e.restoreScriptContext(context, true, false, false);
      // TODO: This will disallow some motion commands
      //       within a TRY/CATCH block in JavaScript, and
      //       the code will block. 
      e.allowJSThreads = false;
      e.dispatchCommands(false, false);
    } catch (Exception ex) {
      eval.viewer.setStringProperty("_errormessage", "" + ex);
      if (e.thisContext == null) {
        Logger.error("Error evaluating context " + ex);
        if (!viewer.isJS)
          ex.printStackTrace();
      }
      return false;
    }
    return true;
  }

  @Override
  public String write(T[] args) throws ScriptException {
    int pt = 0, pt0 = 0;
    boolean isCommand, isShow;
    if (args == null) {
      args = st;
      pt = pt0 = 1;
      isCommand = true;
      isShow = (viewer.isApplet() && !viewer.isSignedApplet()
          || !viewer.haveAccess(ACCESS.ALL) || viewer.getPathForAllFiles()
          .length() > 0);
    } else {
      isCommand = false;
      isShow = true;
    }
    int argCount = (isCommand ? slen : args.length);
    int len = 0;
    int nVibes = 0;
    int width = -1;
    int height = -1;
    int quality = Integer.MIN_VALUE;
    boolean timeMsg = viewer.getBoolean(T.showtiming);
    String driverList = viewer.getExportDriverList();
    String sceneType = "PNGJ";
    String data = null;
    String type2 = "";
    String fileName = null;
    String localPath = null;
    String remotePath = null;
    String val = null;
    String msg = null;
    String[] fullPath = new String[1];
    boolean isCoord = false;
    boolean isExport = false;
    boolean isImage = false;
    BS bsFrames = null;
    String[] scripts = null;
    Map<String, Object> params;
    String type = "SPT";
    int tok = (isCommand && args.length == 1 ? T.clipboard : tokAtArray(pt,
        args));
    switch (tok) {
    case T.nada:
      break;
    case T.script:
      // would fail in write() command.
      if (eval.isArrayParameter(pt + 1)) {
        scripts = eval.stringParameterSet(++pt);
        localPath = ".";
        remotePath = ".";
        pt0 = pt = eval.iToken + 1;
        tok = tokAt(pt);
      }
      break;
    default:
      type = SV.sValue(tokenAt(pt, args)).toUpperCase();
    }
    switch (tok) {
    case T.nada:
      break;
    case T.quaternion:
    case T.ramachandran:
    case T.property:
      msg = plot(args);
      if (!isCommand)
        return msg;
      break;
    case T.inline:
      type = "INLINE";
      data = SV.sValue(tokenAt(++pt, args));
      pt++;
      break;
    case T.pointgroup:
      type = "PGRP";
      pt++;
      type2 = SV.sValue(tokenAt(pt, args)).toLowerCase();
      if (type2.equals("draw"))
        pt++;
      break;
    case T.coord:
      pt++;
      isCoord = true;
      break;
    case T.state:
    case T.script:
      val = SV.sValue(tokenAt(++pt, args)).toLowerCase();
      while (val.equals("localpath") || val.equals("remotepath")) {
        if (val.equals("localpath"))
          localPath = SV.sValue(tokenAt(++pt, args));
        else
          remotePath = SV.sValue(tokenAt(++pt, args));
        val = SV.sValue(tokenAt(++pt, args)).toLowerCase();
      }
      type = "SPT";
      break;
    case T.file:
    case T.function:
    case T.history:
    case T.isosurface:
    case T.menu:
    case T.mesh:
    case T.mo:
    case T.pmesh:
      pt++;
      break;
    case T.jmol:
      type = "ZIPALL";
      pt++;
      break;
    case T.var:
      type = "VAR";
      pt += 2;
      break;
    case T.frame:
    case T.identifier:
    case T.image:
    case T.scene:
    case T.string:
    case T.vibration:
      switch (tok) {
      case T.image:
        pt++;
        break;
      case T.vibration:
        nVibes = eval.intParameterRange(++pt, 1, 10);
        if (!chk) {
          viewer.setVibrationOff();
          if (!eval.isJS)
            eval.delayScript(100);
        }
        pt++;
        break;
      case T.frame:
        BS bsAtoms;
        if (pt + 1 < argCount && args[++pt].tok == T.expressionBegin
            || args[pt].tok == T.bitset) {
          bsAtoms = eval.atomExpression(args, pt, 0, true, false, true, true);
          pt = eval.iToken + 1;
        } else {
          bsAtoms = viewer.getAllAtoms();
        }
        if (!chk)
          bsFrames = viewer.getModelBitSet(bsAtoms, true);
        break;
      case T.scene:
        val = SV.sValue(tokenAt(++pt, args)).toUpperCase();
        if (PT.isOneOf(val, ";PNG;PNGJ;")) {
          sceneType = val;
          pt++;
        }
        break;
      default:
        tok = T.image;
        break;
      }
      if (tok == T.image) {
        T t = T.getTokenFromName(SV.sValue(args[pt]).toLowerCase());
        if (t != null)
          type = SV.sValue(t).toUpperCase();
        if (PT.isOneOf(type, driverList.toUpperCase())) {
          // povray, maya, vrml, idtf
          pt++;
          type = type.substring(0, 1).toUpperCase()
              + type.substring(1).toLowerCase();
          // Povray, Maya, Vrml, Idtf
          isExport = true;
          if (isCommand)
            fileName = "Jmol." + type.toLowerCase();
          break;
        } else if (PT.isOneOf(type, ";ZIP;ZIPALL;SPT;STATE;")) {
          pt++;
          break;
        } else {
          type = "(image)";
        }
      }
      if (tokAtArray(pt, args) == T.integer) {
        width = SV.iValue(tokenAt(pt++, args));
        height = SV.iValue(tokenAt(pt++, args));
      }
      break;
    }

    if (msg == null) {
      val = SV.sValue(tokenAt(pt, args));
      if (val.equalsIgnoreCase("clipboard")) {
        if (chk)
          return "";
        // if (isApplet)
        // evalError(GT._("The {0} command is not available for the applet.",
        // "WRITE CLIPBOARD"));
      } else if (PT.isOneOf(val.toLowerCase(), JC.IMAGE_TYPES)) {
        if (tokAtArray(pt + 1, args) == T.integer
            && tokAtArray(pt + 2, args) == T.integer) {
          width = SV.iValue(tokenAt(++pt, args));
          height = SV.iValue(tokenAt(++pt, args));
        }
        if (tokAtArray(pt + 1, args) == T.integer)
          quality = SV.iValue(tokenAt(++pt, args));
      } else if (PT.isOneOf(val.toLowerCase(),
          ";xyz;xyzrn;xyzvib;mol;sdf;v2000;v3000;json;pdb;pqr;cml;")) {
        type = val.toUpperCase();
        if (pt + 1 == argCount)
          pt++;
      }

      // write [image|history|state] clipboard

      // write [optional image|history|state] [JPG quality|JPEG quality|JPG64
      // quality|PNG|PPM|SPT] "filename"
      // write script "filename"
      // write isosurface t.jvxl

      if (type.equals("(image)")
          && PT.isOneOf(val.toLowerCase(), JC.IMAGE_OR_SCENE)) {
        type = val.toUpperCase();
        pt++;
      }

      if (pt + 2 == argCount) {
        String s = SV.sValue(tokenAt(++pt, args));
        if (s.length() > 0 && s.charAt(0) != '.')
          type = val.toUpperCase();
      }
      switch (tokAtArray(pt, args)) {
      case T.nada:
        isShow = true;
        break;
      case T.clipboard:
        break;
      case T.identifier:
      case T.string:
        fileName = SV.sValue(tokenAt(pt, args));
        if (pt == argCount - 3 && tokAtArray(pt + 1, args) == T.per) {
          // write filename.xxx gets separated as filename .spt
          // write isosurface filename.xxx also
          fileName += "." + SV.sValue(tokenAt(pt + 2, args));
        }
        if (type != "VAR" && pt == pt0)
          type = "IMAGE";
        else if (fileName.length() > 0 && fileName.charAt(0) == '.'
            && (pt == pt0 + 1 || pt == pt0 + 2)) {
          fileName = SV.sValue(tokenAt(pt - 1, args)) + fileName;
          if (type != "VAR" && pt == pt0 + 1)
            type = "IMAGE";
        }
        if (fileName.equalsIgnoreCase("clipboard")
            || !viewer.haveAccess(ACCESS.ALL))
          fileName = null;
        break;
      default:
        invArg();
      }
      if (type.equals("IMAGE") || type.equals("(image)")
          || type.equals("FRAME") || type.equals("VIBRATION")) {
        type = (fileName != null && fileName.indexOf(".") >= 0 ? fileName
            .substring(fileName.lastIndexOf(".") + 1).toUpperCase() : "JPG");
      }
      if (type.equals("MNU")) {
        type = "MENU";
      } else if (type.equals("WRL") || type.equals("VRML")) {
        type = "Vrml";
        isExport = true;
      } else if (type.equals("X3D")) {
        type = "X3d";
        isExport = true;
      } else if (type.equals("IDTF")) {
        type = "Idtf";
        isExport = true;
      } else if (type.equals("MA")) {
        type = "Maya";
        isExport = true;
      } else if (type.equals("JS")) {
        type = "Js";
        isExport = true;
      } else if (type.equals("OBJ")) {
        type = "Obj";
        isExport = true;
      } else if (type.equals("JVXL")) {
        type = "ISOSURFACE";
      } else if (type.equals("XJVXL")) {
        type = "ISOSURFACE";
      } else if (type.equals("JMOL")) {
        type = "ZIPALL";
      } else if (type.equals("HIS")) {
        type = "HISTORY";
      }
      if (type.equals("COORD"))
        type = (fileName != null && fileName.indexOf(".") >= 0 ? fileName
            .substring(fileName.lastIndexOf(".") + 1).toUpperCase() : "XYZ");
      isImage = PT.isOneOf(type.toLowerCase(), JC.IMAGE_OR_SCENE);
      if (scripts != null) {
        if (type.equals("PNG"))
          type = "PNGJ";
        if (!type.equals("PNGJ") && !type.equals("ZIPALL"))
          invArg();
      }
      if (!isImage
          && !isExport
          && !PT
              .isOneOf(
                  type,
                  ";SCENE;JMOL;ZIP;ZIPALL;SPT;HISTORY;MO;ISOSURFACE;MESH;PMESH;VAR;FILE;FUNCTION;CML;JSON;XYZ;XYZRN;XYZVIB;MENU;MOL;PDB;PGRP;PQR;QUAT;RAMA;SDF;V2000;V3000;INLINE;"))
        eval
            .errorStr2(
                ScriptEvaluator.ERROR_writeWhat,
                "COORDS|FILE|FUNCTIONS|HISTORY|IMAGE|INLINE|ISOSURFACE|JMOL|MENU|MO|POINTGROUP|QUATERNION [w,x,y,z] [derivative]"
                    + "|RAMACHANDRAN|SPT|STATE|VAR x|ZIP|ZIPALL  CLIPBOARD",
                "CML|GIF|JPG|JPG64|JMOL|JVXL|MESH|MOL|PDB|PMESH|PNG|PNGJ|PNGT|PPM|PQR|SDF|CD|JSON|V2000|V3000|SPT|XJVXL|XYZ|XYZRN|XYZVIB|ZIP"
                    + driverList.toUpperCase().replace(';', '|'));
      if (chk)
        return "";
      Object bytes = null;
      boolean doDefer = false;
      if (data == null || isExport) {
        data = type.intern();
        if (isExport) {
          if (timeMsg)
            Logger.startTimer("export");
          Map<String, Object> eparams = new Hashtable<String, Object>();
          eparams.put("type", data);
          if (fileName != null)
            eparams.put("fileName", fileName);
          if (isCommand || fileName != null)
            eparams.put("fullPath", fullPath);
          eparams.put("width", Integer.valueOf(width));
          eparams.put("height", Integer.valueOf(height));
          data = viewer.generateOutputForExport(eparams);
          if (data == null || data.length() == 0)
            return "";
          if (!isCommand)
            return data;
          if ((type.equals("Povray") || type.equals("Idtf"))
              && fullPath[0] != null) {
            String ext = (type.equals("Idtf") ? ".tex" : ".ini");
            fileName = fullPath[0] + ext;
            params = new Hashtable<String, Object>();
            params.put("fileName", fileName);
            params.put("type", ext);
            params.put("text", data);
            params.put("fullPath", fullPath);
            msg = viewer.processWriteOrCapture(params);
            if (type.equals("Idtf"))
              data = data.substring(0, data.indexOf("\\begin{comment}"));
            data = "Created " + fullPath[0] + ":\n\n" + data;
            if (timeMsg)
              showString(Logger.getTimerMsg("export", 0));
          } else {
            msg = data;
          }
          if (msg != null) {
            if (!msg.startsWith("OK"))
              eval.evalError(msg, null);
            eval.scriptStatusOrBuffer(data);
          }
          return "";
        } else if (data == "MENU") {
          data = viewer.getMenu("");
        } else if (data == "PGRP") {
          data = viewer.getPointGroupAsString(type2.equals("draw"), null, 0,
              1.0f);
        } else if (data == "PDB" || data == "PQR") {
          if (isShow) {
            data = viewer.getPdbAtomData(null, null);
          } else {
            doDefer = true;
            /*
             * OutputStream os = viewer.getOutputStream(fileName, fullPath); msg =
             * viewer.getPdbData(null, new BufferedOutputStream(os)); if (msg !=
             * null) msg = "OK " + msg + " " + fullPath[0]; try { os.close(); }
             * catch (IOException e) { // TODO }
             */
          }
        } else if (data == "FILE") {
          if (isShow)
            data = viewer.getCurrentFileAsString();
          else
            doDefer = true;
          if ("?".equals(fileName))
            fileName = "?Jmol." + viewer.getParameter("_fileType");
        } else if ((data == "SDF" || data == "MOL" || data == "V2000"
            || data == "V3000" || data == "CD"  || data == "JSON")
            && isCoord) {
          data = viewer.getModelExtract("selected", true, false, data);
          if (data.startsWith("ERROR:"))
            bytes = data;
        } else if (data == "XYZ" || data == "XYZRN" || data == "XYZVIB"
            || data == "MOL" || data == "SDF" || data == "V2000"
            || data == "V3000" || data == "CML" || data == "CD" || data == "JSON") {
          data = viewer.getData("selected", data);
          if (data.startsWith("ERROR:"))
            bytes = data;
        } else if (data == "FUNCTION") {
          data = viewer.getFunctionCalls(null);
          type = "TXT";
        } else if (data == "VAR") {
          data = ((SV) eval.getParameter(SV.sValue(tokenAt(isCommand ? 2 : 1,
              args)), T.variable)).asString();
          type = "TXT";
        } else if (data == "SPT") {
          if (isCoord) {
            BS tainted = viewer.getTaintedAtoms(AtomCollection.TAINT_COORD);
            viewer.setAtomCoordsRelative(P3.new3(0, 0, 0), null);
            data = viewer.getStateInfo();
            viewer.setTaintedAtoms(tainted, AtomCollection.TAINT_COORD);
          } else {
            data = viewer.getStateInfo();
            if (localPath != null || remotePath != null)
              data = FileManager.setScriptFileReferences(data, localPath,
                  remotePath, null);
          }
        } else if (data == "ZIP" || data == "ZIPALL") {
          if (fileName != null
              && (bytes = data = viewer.createZip(fileName, type, scripts)) == null)
            eval.evalError("#CANCELED#", null);
        } else if (data == "HISTORY") {
          data = viewer.getSetHistory(Integer.MAX_VALUE);
          type = "SPT";
        } else if (data == "MO") {
          data = getMoJvxl(Integer.MAX_VALUE);
          type = "XJVXL";
        } else if (data == "PMESH") {
          if ((data = getIsosurfaceJvxl(true, JC.SHAPE_PMESH)) == null)
            error(ScriptEvaluator.ERROR_noData);
          type = "XJVXL";
        } else if (data == "ISOSURFACE" || data == "MESH") {
          if ((data = getIsosurfaceJvxl(data == "MESH", JC.SHAPE_ISOSURFACE)) == null)
            error(ScriptEvaluator.ERROR_noData);
          type = (data.indexOf("<?xml") >= 0 ? "XJVXL" : "JVXL");
          if (!isShow)
            showString((String) getShapeProperty(JC.SHAPE_ISOSURFACE,
                "jvxlFileInfo"));
        } else {
          // image
          len = -1;
          if (quality < 0)
            quality = -1;
        }
        if (data == null && !doDefer)
          data = "";
        if (len == 0 && !doDefer)
          len = (bytes == null ? data.length()
              : bytes instanceof String ? ((String) bytes).length()
                  : ((byte[]) bytes).length);
        if (isImage) {
          eval.refresh();
          if (width < 0)
            width = viewer.getScreenWidth();
          if (height < 0)
            height = viewer.getScreenHeight();
        }
      }
      if (!isCommand)
        return data;
      if (isShow) {
        eval.showStringPrint(data, true);
        return "";
      }
      if (bytes != null && bytes instanceof String) {
        // load error or completion message here
        /**
         * @j2sNative
         * 
         *            if (bytes.indexOf("OK") != 0)alert(bytes);
         * 
         */
        {
        }
        eval.scriptStatusOrBuffer((String) bytes);
        return (String) bytes;
      }
      if (type.equals("SCENE"))
        bytes = sceneType;
      else if (bytes == null && (!isImage || fileName != null))
        bytes = data;
      if (timeMsg)
        Logger.startTimer("write");
      if (doDefer) {
        msg = viewer.writeFileData(fileName, type, 0, null);
      } else {
        params = new Hashtable<String, Object>();
        if (fileName != null)
          params.put("fileName", fileName);
        params.put("type", type);
        if (bytes instanceof String && quality == Integer.MIN_VALUE)
          params.put("text", bytes);
        else if (bytes instanceof byte[])
          params.put("bytes", bytes);
        if (scripts != null)
          params.put("scripts", scripts);
        if (bsFrames != null)
          params.put("bsFrames", bsFrames);
        params.put("fullPath", fullPath);
        params.put("quality", Integer.valueOf(quality));
        params.put("width", Integer.valueOf(width));
        params.put("height", Integer.valueOf(height));
        params.put("nVibes", Integer.valueOf(nVibes));
        msg = viewer.processWriteOrCapture(params);
        //? (byte[]) bytes : null), scripts,  quality, width, height, bsFrames, nVibes, fullPath);
      }
      if (timeMsg)
        showString(Logger.getTimerMsg("write", 0));
    }
    if (!chk && msg != null) {
      if (!msg.startsWith("OK")) {
        eval.evalError(msg, null);
        /**
         * @j2sNative
         * 
         *            alert(msg);
         */
        {
        }
      }
      eval.scriptStatusOrBuffer(msg
          + (isImage ? "; width=" + width + "; height=" + height : ""));
      return msg;
    }
    return "";
  }

  private void show() throws ScriptException {
    String value = null;
    String str = parameterAsString(1);
    String msg = null;
    String name = null;
    int len = 2;
    T token = getToken(1);
    int tok = (token instanceof SV ? T.nada : token.tok);
    if (tok == T.string) {
      token = T.getTokenFromName(str.toLowerCase());
      if (token != null)
        tok = token.tok;
    }
    if (tok != T.symop && tok != T.state)
      checkLength(-3);
    if (slen == 2 && str.indexOf("?") >= 0) {
      showString(viewer.getAllSettings(str.substring(0, str.indexOf("?"))));
      return;
    }
    switch (tok) {
    case T.nada:
      if (!chk)
        msg = ((SV) eval.theToken).escape();
      break;
    case T.cache:
      if (!chk)
        msg = Escape.e(viewer.cacheList());
      break;
    case T.dssp:
      checkLength(2);
      if (!chk)
        msg = viewer.calculateStructures(null, true, false);
      break;
    case T.pathforallfiles:
      checkLength(2);
      if (!chk)
        msg = viewer.getPathForAllFiles();
      break;
    case T.nmr:
      if (eval.optParameterAsString(2).equalsIgnoreCase("1H")) {
        len = 3;
        if (!chk)
          msg = viewer.getNMRPredict(false);
        break;
      }
      if (!chk)
        viewer.getNMRPredict(true);
      return;
    case T.smiles:
    case T.drawing:
    case T.chemical:
      checkLength(tok == T.chemical ? 3 : 2);
      if (chk)
        return;
      msg = viewer.getSmiles(0, 0, viewer.getSelectionSet(false), false, true,
          false, false);
      switch (tok) {
      case T.drawing:
        if (msg.length() > 0) {
          viewer.show2D(msg);
          return;
        }
        msg = "Could not show drawing -- Either insufficient atoms are selected or the model is a PDB file.";
        break;
      case T.chemical:
        len = 3;
        String info = null;
        if (msg.length() > 0) {
          char type = '/';
          switch (getToken(2).tok) {
          case T.inchi:
            type = 'I';
            break;
          case T.inchikey:
            type = 'K';
            break;
          case T.name:
            type = 'N';
            break;
          default:
            info = parameterAsString(2);
          }
          msg = viewer.getChemicalInfo(msg, type, info);
          if (msg.indexOf("FileNotFound") >= 0)
            msg = "?";
        } else {
          msg = "Could not show name -- Either insufficient atoms are selected or the model is a PDB file.";
        }
      }
      break;
    case T.symop:
      if (slen > 3) {
        P3 pt1 = centerParameter(2);
        P3 pt2 = centerParameter(++eval.iToken);
        if (!chk)
          msg = viewer.getSymmetryOperation(null, 0, pt1, pt2, false);
        len = ++eval.iToken;
      } else {
        int iop = (eval.checkLength23() == 2 ? 0 : intParameter(2));
        if (!chk)
          msg = viewer.getSymmetryOperation(null, iop, null, null, false);
        len = -3;
      }
      break;
    case T.vanderwaals:
      EnumVdw vdwType = null;
      if (slen > 2) {
        vdwType = EnumVdw.getVdwType(parameterAsString(2));
        if (vdwType == null)
          invArg();
      }
      if (!chk)
        showString(viewer.getDefaultVdwNameOrData(0, vdwType, null));
      return;
    case T.function:
      eval.checkLength23();
      if (!chk)
        showString(viewer.getFunctionCalls(eval.optParameterAsString(2)));
      return;
    case T.set:
      checkLength(2);
      if (!chk)
        showString(viewer.getAllSettings(null));
      return;
    case T.url:
      // in a new window
      if ((len = slen) == 2) {
        if (!chk)
          viewer.showUrl(eval.getFullPathName());
        return;
      }
      name = parameterAsString(2);
      if (!chk)
        viewer.showUrl(name);
      return;
    case T.color:
      str = "defaultColorScheme";
      break;
    case T.scale3d:
      str = "scaleAngstromsPerInch";
      break;
    case T.quaternion:
    case T.ramachandran:
      if (chk)
        return;
      int modelIndex = viewer.getCurrentModelIndex();
      if (modelIndex < 0)
        eval.errorStr(ScriptEvaluator.ERROR_multipleModelsDisplayedNotOK, "show " + eval.theToken.value);
      msg = plot(st);
      len = slen;
      break;
    case T.trace:
      if (!chk)
        msg = getContext(false);
      break;
    case T.colorscheme:
      name = eval.optParameterAsString(2);
      if (name.length() > 0)
        len = 3;
      if (!chk)
        value = viewer.getColorSchemeList(name);
      break;
    case T.variables:
      if (!chk)
        msg = viewer.getAtomDefs(eval.definedAtomSets) + viewer.getVariableList()
            + getContext(true);
      break;
    case T.trajectory:
      if (!chk)
        msg = viewer.getTrajectoryState();
      break;
    case T.historylevel:
      value = "" + eval.commandHistoryLevelMax;
      break;
    case T.loglevel:
      value = "" + Logger.getLogLevel();
      break;
    case T.debugscript:
      value = "" + viewer.getBoolean(T.debugscript);
      break;
    case T.strandcount:
      msg = "set strandCountForStrands "
          + viewer.getStrandCount(JC.SHAPE_STRANDS)
          + "; set strandCountForMeshRibbon "
          + viewer.getStrandCount(JC.SHAPE_MESHRIBBON);
      break;
    case T.timeout:
      msg = viewer.showTimeout((len = slen) == 2 ? null : parameterAsString(2));
      break;
    case T.defaultlattice:
      value = Escape.eP(viewer.getDefaultLattice());
      break;
    case T.minimize:
      if (!chk)
        msg = viewer.getMinimizationInfo();
      break;
    case T.axes:
      switch (viewer.getAxesMode()) {
      case UNITCELL:
        msg = "set axesUnitcell";
        break;
      case BOUNDBOX:
        msg = "set axesWindow";
        break;
      default:
        msg = "set axesMolecular";
      }
      break;
    case T.bondmode:
      msg = "set bondMode " + (viewer.getBoolean(T.bondmodeor) ? "OR" : "AND");
      break;
    case T.strands:
      if (!chk)
        msg = "set strandCountForStrands "
            + viewer.getStrandCount(JC.SHAPE_STRANDS)
            + "; set strandCountForMeshRibbon "
            + viewer.getStrandCount(JC.SHAPE_MESHRIBBON);
      break;
    case T.hbond:
      msg = "set hbondsBackbone " + viewer.getBoolean(T.hbondsbackbone)
          + ";set hbondsSolid " + viewer.getBoolean(T.hbondssolid);
      break;
    case T.spin:
      if (!chk)
        msg = viewer.getSpinState();
      break;
    case T.ssbond:
      msg = "set ssbondsBackbone " + viewer.getBoolean(T.ssbondsbackbone);
      break;
    case T.display:// deprecated
    case T.selectionhalos:
      msg = "selectionHalos "
          + (viewer.getSelectionHaloEnabled(false) ? "ON" : "OFF");
      break;
    case T.hetero:
      msg = "set selectHetero " + viewer.getBoolean(T.hetero);
      break;
    case T.addhydrogens:
      msg = Escape.eAP(viewer.getAdditionalHydrogens(null, true, true, null));
      break;
    case T.hydrogen:
      msg = "set selectHydrogens " + viewer.getBoolean(T.hydrogen);
      break;
    case T.ambientpercent:
    case T.diffusepercent:
    case T.specular:
    case T.specularpower:
    case T.specularexponent:
    case T.lighting:
      if (!chk)
        msg = viewer.getSpecularState();
      break;
    case T.save:
      if (!chk)
        msg = viewer.listSavedStates();
      break;
    case T.unitcell:
      if (!chk)
        msg = viewer.getUnitCellInfoText();
      break;
    case T.coord:
      if ((len = slen) == 2) {
        if (!chk)
          msg = viewer.getCoordinateState(viewer.getSelectionSet(false));
        break;
      }
      String nameC = parameterAsString(2);
      if (!chk)
        msg = viewer.getSavedCoordinates(nameC);
      break;
    case T.state:
      if (!chk)
        viewer.clearConsole();
      if ((len = slen) == 2) {
        if (!chk)
          msg = viewer.getStateInfo();
        break;
      }
      name = parameterAsString(2);
      if (name.equals("/") && (len = slen) == 4) {
        name = parameterAsString(3).toLowerCase();
        if (!chk) {
          String[] info = PT.split(viewer.getStateInfo(), "\n");
          SB sb = new SB();
          for (int i = 0; i < info.length; i++)
            if (info[i].toLowerCase().indexOf(name) >= 0)
              sb.append(info[i]).appendC('\n');
          msg = sb.toString();
        }
        break;
      } else if (tokAt(2) == T.file && (len = slen) == 4) {
        if (!chk)
          msg = viewer.getEmbeddedFileState(parameterAsString(3));
        break;
      }
      len = 3;
      if (!chk)
        msg = viewer.getSavedState(name);
      break;
    case T.structure:
      if ((len = slen) == 2) {
        if (!chk)
          msg = viewer.getProteinStructureState();
        break;
      }
      String shape = parameterAsString(2);
      if (!chk)
        msg = viewer.getSavedStructure(shape);
      break;
    case T.data:
      String type = ((len = slen) == 3 ? parameterAsString(2) : null);
      if (!chk) {
        Object[] data = (type == null ? this.data : viewer.getData(type));
        msg = (data == null ? "no data" : Escape.encapsulateData(
            (String) data[0], data[1], ((Integer) data[3]).intValue()));
      }
      break;
    case T.spacegroup:
      Map<String, Object> info = null;
      if ((len = slen) == 2) {
        if (!chk) {
          info = viewer.getSpaceGroupInfo(null);
        }
      } else {
        String sg = parameterAsString(2);
        if (!chk)
          info = viewer.getSpaceGroupInfo(PT.simpleReplace(sg, "''",
              "\""));
      }
      if (info != null)
        msg = "" + info.get("spaceGroupInfo") + info.get("symmetryInfo");
      break;
    case T.dollarsign:
      len = 3;
      msg = eval.setObjectProperty();
      break;
    case T.boundbox:
      if (!chk) {
        msg = viewer.getBoundBoxCommand(true);
      }
      break;
    case T.center:
      if (!chk)
        msg = "center " + Escape.eP(viewer.getRotationCenter());
      break;
    case T.draw:
      if (!chk)
        msg = (String) getShapeProperty(JC.SHAPE_DRAW, "command");
      break;
    case T.file:
      // as a string
      if (!chk)
        viewer.clearConsole();
      if (slen == 2) {
        if (!chk)
          msg = viewer.getCurrentFileAsString();
        if (msg == null)
          msg = "<unavailable>";
        break;
      }
      len = 3;
      value = parameterAsString(2);
      if (!chk)
        msg = viewer.getFileAsString(value);
      break;
    case T.frame:
      if (tokAt(2) == T.all && (len = 3) > 0)
        msg = viewer.getModelFileInfoAll();
      else
        msg = viewer.getModelFileInfo();
      break;
    case T.history:
      int n = ((len = slen) == 2 ? Integer.MAX_VALUE : intParameter(2));
      if (n < 1)
        invArg();
      if (!chk) {
        viewer.clearConsole();
        if (eval.scriptLevel == 0)
          viewer.removeCommand();
        msg = viewer.getSetHistory(n);
      }
      break;
    case T.isosurface:
      if (!chk)
        msg = (String) getShapeProperty(JC.SHAPE_ISOSURFACE, "jvxlDataXml");
      break;
    case T.mo:
      if (eval.optParameterAsString(2).equalsIgnoreCase("list")) {
        msg = viewer.getMoInfo(-1);
        len = 3;
      } else {
        int ptMO = ((len = slen) == 2 ? Integer.MIN_VALUE : intParameter(2));
        if (!chk)
          msg = getMoJvxl(ptMO);
      }
      break;
    case T.model:
      if (!chk)
        msg = viewer.getModelInfoAsString();
      break;
    case T.measurements:
      if (!chk)
        msg = viewer.getMeasurementInfoAsString();
      break;
    case T.best:
      len = 3;
      if (!chk && slen == len)
        msg = viewer.getOrientationText(tokAt(2), null);
      break;
    case T.rotation:
      tok = tokAt(2);
      if (tok == T.nada)
        tok = T.rotation;
      else
        len = 3;
      //$FALL-THROUGH$
    case T.translation:
    case T.moveto:
      if (!chk)
        msg = viewer.getOrientationText(tok, null);
      break;
    case T.orientation:
      len = 2;
      if (slen > 3)
        break;
      switch (tok = tokAt(2)) {
      case T.translation:
      case T.rotation:
      case T.moveto:
      case T.nada:
        if (!chk)
          msg = viewer.getOrientationText(tok, null);
        break;
      default:
        name = eval.optParameterAsString(2);
        msg = viewer.getOrientationText(T.name, name);
      }
      len = slen;
      break;
    case T.pdbheader:
      if (!chk)
        msg = viewer.getPDBHeader();
      break;
    case T.pointgroup:
      pointGroup();
      return;
    case T.symmetry:
      if (!chk)
        msg = viewer.getSymmetryInfoAsString();
      break;
    case T.transform:
      if (!chk)
        msg = "transform:\n" + viewer.getTransformText();
      break;
    case T.zoom:
      msg = "zoom "
          + (viewer.getZoomEnabled() ? ("" + viewer.getZoomSetting()) : "off");
      break;
    case T.frank:
      msg = (viewer.getShowFrank() ? "frank ON" : "frank OFF");
      break;
    case T.radius:
      str = "solventProbeRadius";
      break;
    // Chime related
    case T.basepair:
    case T.chain:
    case T.sequence:
    case T.residue:
    case T.selected:
    case T.group:
    case T.atoms:
    case T.info:
      //case T.bonds: // ?? was this ever implemented? in Chime?
      msg = viewer.getChimeInfo(tok);
      break;
    // not implemented
    case T.echo:
    case T.fontsize:
    case T.property: // huh? why?
    case T.help:
    case T.solvent:
      value = "?";
      break;
    case T.identifier:
      if (str.equalsIgnoreCase("fileHeader")) {
        if (!chk)
          msg = viewer.getPDBHeader();
      } else if (str.equalsIgnoreCase("menu")) {
        if (!chk)
          value = viewer.getMenu("");
      } else if (str.equalsIgnoreCase("mouse")) {
        String qualifiers = ((len = slen) == 2 ? null : parameterAsString(2));
        if (!chk)
          msg = viewer.getBindingInfo(qualifiers);
      }
      break;
    }
    checkLength(len);
    if (chk)
      return;
    if (msg != null)
      showString(msg);
    else if (value != null)
      showString(str + " = " + value);
    else if (str != null) {
      if (str.indexOf(" ") >= 0)
        showString(str);
      else
        showString(str + " = " + getParameterEscaped(str));
    }
  }

  private void showString(String s) {
    eval.showString(s);
  }

  private void checkLength(int i) throws ScriptException {
    eval.checkLength(i);
  }

  private String getIsosurfaceJvxl(boolean asMesh, int iShape) {
    if (chk)
      return "";
    return (String) getShapeProperty(iShape, asMesh ? "jvxlMeshX"
        : "jvxlDataXml");
  }

  @SuppressWarnings("unchecked")
  private String getMoJvxl(int ptMO) throws ScriptException {
    // 0: all; Integer.MAX_VALUE: current;
    sm.loadShape(JC.SHAPE_MO);
    int modelIndex = viewer.getCurrentModelIndex();
    if (modelIndex < 0)
      eval.errorStr(ScriptEvaluator.ERROR_multipleModelsDisplayedNotOK, "MO isosurfaces");
    Map<String, Object> moData = (Map<String, Object>) viewer
        .getModelAuxiliaryInfoValue(modelIndex, "moData");
    if (moData == null)
      error(ScriptEvaluator.ERROR_moModelError);
    Integer n = (Integer) getShapeProperty(JC.SHAPE_MO, "moNumber");
    if (n == null || n.intValue() == 0) {
      setShapeProperty(JC.SHAPE_MO, "init", Integer.valueOf(modelIndex));
      //} else if (ptMO == Integer.MAX_VALUE) {
    }
    setShapeProperty(JC.SHAPE_MO, "moData", moData);
    return (String) getShapePropertyIndex(JC.SHAPE_MO, "showMO", ptMO);
  }

  private String getParameterEscaped(String var) {
    SV v = eval.getContextVariableAsVariable(var);
    return (v == null ? "" + viewer.getParameterEscaped(var) : v.escape());
  }

  private String getContext(boolean withVariables) {
    SB sb = new SB();
    ScriptContext context = eval.thisContext;
    while (context != null) {
      if (withVariables) {
        if (context.contextVariables != null) {
          sb.append(getScriptID(context));
          sb.append(StateManager.getVariableList(context.contextVariables, 80,
              true, false));
        }
      } else {
        sb.append(ScriptEvaluator.getErrorLineMessage(context.functionName,
            context.scriptFileName, eval.getLinenumber(context), context.pc,
            ScriptEvaluator.statementAsString(viewer, context.statement, -9999, eval.logMessages)));
      }
      context = context.parentContext;
    }
    if (withVariables) {
      if (eval.contextVariables != null) {
        sb.append(getScriptID(null));
        sb.append(StateManager.getVariableList(eval.contextVariables, 80, true,
            false));
      }
    } else {
      sb.append(eval.getErrorLineMessage2());
    }

    return sb.toString();
  }

  private String getScriptID(ScriptContext context) {
    String fuName = (context == null ? eval.functionName : "function "
        + context.functionName);
    String fiName = (context == null ? eval.scriptFileName : context.scriptFileName);
    return "\n# " + fuName + " (file " + fiName
        + (context == null ? "" : " context " + context.id) + ")\n";
  }

  private Object getShapePropertyIndex(int shapeType, String propertyName,
                                       int index) {
    return sm.getShapePropertyIndex(shapeType, propertyName, index);
  }

  private T tokenAt(int i, T[] args) {
    return (i < args.length ? args[i] : null);
  }

  private static int tokAtArray(int i, T[] args) {
    return (i < args.length && args[i] != null ? args[i].tok : T.nada);
  }

  private void calculate() throws ScriptException {
    boolean isSurface = false;
    boolean asDSSP = false;
    BS bs1 = null;
    BS bs2 = null;
    int n = Integer.MIN_VALUE;
    if ((eval.iToken = eval.slen) >= 2) {
      eval.clearDefinedVariableAtomSets();
      switch (getToken(1).tok) {
      case T.identifier:
        checkLength(2);
        break;
      case T.formalcharge:
        checkLength(2);
        if (chk)
          return;
        n = viewer.calculateFormalCharges(null);
        showString(GT.i(GT._("{0} charges modified"), n));
        return;
      case T.aromatic:
        checkLength(2);
        if (!chk)
          viewer.assignAromaticBonds();
        return;
      case T.hbond:
        if (eval.slen != 2) {
          // calculate hbonds STRUCTURE -- only the DSSP structurally-defining H bonds
          asDSSP = (tokAt(++eval.iToken) == T.structure);
          if (asDSSP)
            bs1 = viewer.getSelectionSet(false);
          else
            bs1 = atomExpressionAt(eval.iToken);
          if (!asDSSP && !(asDSSP = (tokAt(++eval.iToken) == T.structure)))
            bs2 = atomExpressionAt(eval.iToken);
        }
        if (chk)
          return;
        n = viewer.autoHbond(bs1, bs2, false);
        if (n != Integer.MIN_VALUE)
          eval.scriptStatusOrBuffer(GT.i(GT._("{0} hydrogen bonds"), Math.abs(n)));
        return;
      case T.hydrogen:
        bs1 = (slen == 2 ? null : atomExpressionAt(2));
        eval.checkLast(eval.iToken);
        if (!chk)
          viewer.addHydrogens(bs1, false, false);
        return;
      case T.partialcharge:
        eval.iToken = 1;
        bs1 = (slen == 2 ? null : atomExpressionAt(2));
        eval.checkLast(eval.iToken);
        if (!chk)
          viewer.calculatePartialCharges(bs1);
        return;
      case T.pointgroup:
        pointGroup();
        return;
      case T.straightness:
        checkLength(2);
        if (!chk) {
          viewer.calculateStraightness();
          viewer.addStateScript("set quaternionFrame '"
              + viewer.getQuaternionFrame() + "'; calculate straightness",
              false, true);
        }
        return;
      case T.structure:
        bs1 = (slen < 4 ? null : atomExpressionAt(2));
        switch (tokAt(++eval.iToken)) {
        case T.ramachandran:
          break;
        case T.dssp:
          asDSSP = true;
          break;
        case T.nada:
          asDSSP = viewer.getBoolean(T.defaultstructuredssp);
          break;
        default:
          invArg();
        }
        if (!chk)
          showString(viewer.calculateStructures(bs1, asDSSP, true));
        return;
      case T.struts:
        bs1 = (eval.iToken + 1 < slen ? atomExpressionAt(++eval.iToken) : null);
        bs2 = (eval.iToken + 1 < slen ? atomExpressionAt(++eval.iToken) : null);
        checkLength(++eval.iToken);
        if (!chk) {
          n = viewer.calculateStruts(bs1, bs2);
          if (n > 0) {
            setShapeProperty(JC.SHAPE_STICKS, "type", Integer
                .valueOf(JmolEdge.BOND_STRUT));
            eval.setShapePropertyBs(JC.SHAPE_STICKS, "color", Integer
                .valueOf(0x0FFFFFF), null);
            eval.setShapeTranslucency(JC.SHAPE_STICKS, "", "translucent", 0.5f, null);
            setShapeProperty(JC.SHAPE_STICKS, "type", Integer
                .valueOf(JmolEdge.BOND_COVALENT_MASK));
          }
          showString(GT.i(GT._("{0} struts mp.added"), n));
        }
        return;
      case T.surface:
        isSurface = true;
        // deprecated
        //$FALL-THROUGH$
      case T.surfacedistance:
        // preferred
        // calculate surfaceDistance FROM {...}
        // calculate surfaceDistance WITHIN {...}
        boolean isFrom = false;
        switch (tokAt(2)) {
        case T.within:
          eval.iToken++;
          break;
        case T.nada:
          isFrom = !isSurface;
          break;
        case T.from:
          isFrom = true;
          eval.iToken++;
          break;
        default:
          isFrom = true;
        }
        bs1 = (eval.iToken + 1 < slen ? atomExpressionAt(++eval.iToken) : viewer
            .getSelectionSet(false));
        checkLength(++eval.iToken);
        if (!chk)
          viewer.calculateSurface(bs1, (isFrom ? Float.MAX_VALUE : -1));
        return;
      }
    }
    eval.errorStr2(
        ScriptEvaluator.ERROR_what,
        "CALCULATE",
        "aromatic? hbonds? hydrogen? formalCharge? partialCharge? pointgroup? straightness? structure? struts? surfaceDistance FROM? surfaceDistance WITHIN?");
  }

  private void pointGroup() throws ScriptException {
    switch (tokAt(0)) {
    case T.calculate:
      if (!chk)
        showString(viewer.calculatePointGroup());
      return;
    case T.show:
      if (!chk)
        showString(viewer.getPointGroupAsString(false, null, 0, 0));
      return;
    }
    // draw pointgroup [C2|C3|Cs|Ci|etc.] [n] [scale x]
    int pt = 2;
    String type = (tokAt(pt) == T.scale ? "" : eval.optParameterAsString(pt));
    if (type.equals("chemicalShift"))
      type = "cs";
    float scale = 1;
    int index = 0;
    if (type.length() > 0) {
      if (isFloatParameter(++pt))
        index = intParameter(pt++);
    }
    if (tokAt(pt) == T.scale)
      scale = floatParameter(++pt);
    if (!chk)
      eval.runScript(viewer.getPointGroupAsString(true, type, index, scale));
  }

  private void mapProperty() throws ScriptException {
    // map {1.1}.straightness  {2.1}.property_x resno
    BS bsFrom, bsTo;
    String property1, property2, mapKey;
    int tokProp1 = 0;
    int tokProp2 = 0;
    int tokKey = 0;
    while (true) {
      if (tokAt(1) == T.selected) {
        bsFrom = viewer.getSelectionSet(false);
        bsTo = atomExpressionAt(2);
        property1 = property2 = "selected";
      } else {
        bsFrom = atomExpressionAt(1);
        if (tokAt(++eval.iToken) != T.per
            || !T.tokAttr(tokProp1 = tokAt(++eval.iToken), T.atomproperty))
          break;
        property1 = parameterAsString(eval.iToken);
        bsTo = atomExpressionAt(++eval.iToken);
        if (tokAt(++eval.iToken) != T.per
            || !T.tokAttr(tokProp2 = tokAt(++eval.iToken), T.settable))
          break;
        property2 = parameterAsString(eval.iToken);
      }
      if (T.tokAttr(tokKey = tokAt(eval.iToken + 1), T.atomproperty))
        mapKey = parameterAsString(++eval.iToken);
      else
        mapKey = T.nameOf(tokKey = T.atomno);
      eval.checkLast(eval.iToken);
      if (chk)
        return;
      BS bsOut = null;
      showString("mapping " + property1.toUpperCase() + " for "
          + bsFrom.cardinality() + " atoms to " + property2.toUpperCase()
          + " for " + bsTo.cardinality() + " atoms using "
          + mapKey.toUpperCase());
      if (T.tokAttrOr(tokProp1, T.intproperty, T.floatproperty)
          && T.tokAttrOr(tokProp2, T.intproperty, T.floatproperty)
          && T.tokAttrOr(tokKey, T.intproperty, T.floatproperty)) {
        float[] data1 = eval.getBitsetPropertyFloat(bsFrom, tokProp1
            | T.selectedfloat, Float.NaN, Float.NaN);
        float[] data2 = eval.getBitsetPropertyFloat(bsFrom,
            tokKey | T.selectedfloat, Float.NaN, Float.NaN);
        float[] data3 = eval.getBitsetPropertyFloat(bsTo, tokKey | T.selectedfloat,
            Float.NaN, Float.NaN);
        boolean isProperty = (tokProp2 == T.property);
        float[] dataOut = new float[isProperty ? viewer.getAtomCount()
            : data3.length];
        bsOut = new BS();
        if (data1.length == data2.length) {
          Map<Float, Float> ht = new Hashtable<Float, Float>();
          for (int i = 0; i < data1.length; i++) {
            ht.put(Float.valueOf(data2[i]), Float.valueOf(data1[i]));
          }
          int pt = -1;
          int nOut = 0;
          for (int i = 0; i < data3.length; i++) {
            pt = bsTo.nextSetBit(pt + 1);
            Float F = ht.get(Float.valueOf(data3[i]));
            if (F == null)
              continue;
            bsOut.set(pt);
            dataOut[(isProperty ? pt : nOut)] = F.floatValue();
            nOut++;
          }
          if (isProperty)
            viewer.setData(property2, new Object[] { property2, dataOut, bsOut,
                Integer.valueOf(0), Boolean.TRUE }, viewer.getAtomCount(), 0, 0,
                Integer.MAX_VALUE, 0);
          else
            viewer.setAtomProperty(bsOut, tokProp2, 0, 0, null, dataOut, null);
        }
      }
      if (bsOut == null) {
        String format = "{" + mapKey + "=%[" + mapKey + "]}." + property2
            + " = %[" + property1 + "]";
        String[] data = (String[]) getBitsetIdent(bsFrom, format, null, false,
            Integer.MAX_VALUE, false);
        SB sb = new SB();
        for (int i = 0; i < data.length; i++)
          if (data[i].indexOf("null") < 0)
            sb.append(data[i]).appendC('\n');
        if (Logger.debugging)
          Logger.debug(sb.toString());
        BS bsSubset = BSUtil.copy(viewer.getSelectionSubset());
        viewer.setSelectionSubset(bsTo);
        try {
          eval.runScript(sb.toString());
        } catch (Exception e) {
          viewer.setSelectionSubset(bsSubset);
          eval.errorStr(-1, "Error: " + e.toString());
        } catch (Error er) {
          viewer.setSelectionSubset(bsSubset);
          eval.errorStr(-1, "Error: " + er.toString());
        }
        viewer.setSelectionSubset(bsSubset);
      }
      showString("DONE");
      return;
    }
    invArg();
  }

  private void minimize() throws ScriptException {
    BS bsSelected = null;
    int steps = Integer.MAX_VALUE;
    float crit = 0;
    boolean addHydrogen = false;
    boolean isSilent = false;
    BS bsFixed = null;
    boolean isOnly = false;
    MinimizerInterface minimizer = viewer.getMinimizer(false);
    // may be null
    for (int i = 1; i < slen; i++)
      switch (getToken(i).tok) {
      case T.addhydrogens:
        addHydrogen = true;
        continue;
      case T.cancel:
      case T.stop:
        checkLength(2);
        if (chk || minimizer == null)
          return;
        minimizer.setProperty(parameterAsString(i), null);
        return;
      case T.clear:
        checkLength(2);
        if (chk || minimizer == null)
          return;
        minimizer.setProperty("clear", null);
        return;
      case T.constraint:
        if (i != 1)
          invArg();
        int n = 0;
        float targetValue = 0;
        int[] aList = new int[5];
        if (tokAt(++i) == T.clear) {
          checkLength(3);
        } else {
          while (n < 4 && !isFloatParameter(i)) {
            aList[++n] = atomExpressionAt(i).nextSetBit(0);
            i = eval.iToken + 1;
          }
          aList[0] = n;
          if (n == 1)
            invArg();
          targetValue = floatParameter(eval.checkLast(i));
        }
        if (!chk)
          viewer.getMinimizer(true).setProperty("constraint",
              new Object[] { aList, new int[n], Float.valueOf(targetValue) });
        return;
      case T.criterion:
        crit = floatParameter(++i);
        continue;
      case T.energy:
        steps = 0;
        continue;
      case T.fixed:
        if (i != 1)
          invArg();
        bsFixed = atomExpressionAt(++i);
        if (bsFixed.nextSetBit(0) < 0)
          bsFixed = null;
        i = eval.iToken;
        if (!chk)
          viewer.getMinimizer(true).setProperty("fixed", bsFixed);
        if (i + 1 == slen)
          return;
        continue;
      case T.bitset:
      case T.expressionBegin:
        isOnly = true;
        //$FALL-THROUGH$
      case T.select:
        if (eval.theTok == T.select)
          i++;
        bsSelected = atomExpressionAt(i);
        i = eval.iToken;
        if (tokAt(i + 1) == T.only) {
          i++;
          isOnly = true;
        }
        continue;
      case T.silent:
        isSilent = true;
        break;
      case T.step:
        steps = intParameter(++i);
        continue;
      default:
        invArg();
        break;
      }
    if (!chk)
      viewer.minimize(steps, crit, bsSelected, bsFixed, 0, addHydrogen, isOnly,
          isSilent, false);
  }

  /**
   * Allows for setting one or more specific t-values
   * as well as full unit-cell shifts (multiples of q).
   * 
   * @throws ScriptException
   */
  private void modulation() throws ScriptException {
    
    // modulation on/off  (all atoms)
    // moduation {atom set} on/off
    // modulation int  q-offset
    // modulation x.x  t-offset
    // modulation {t1 t2 t3} 
    // modulation {q1 q2 q3} TRUE 
    P3 qtOffset = null;
//    int frameN = Integer.MAX_VALUE;
    boolean mod = true;
    boolean isQ = false;
    BS bs = null;
    switch (getToken(1).tok) {
    case T.off:
      mod = false;
      //$FALL-THROUGH$
    case T.nada:
    case T.on:
      break;
    case T.bitset:
    case T.expressionBegin:
      bs = atomExpressionAt(1);
      switch (tokAt(eval.iToken + 1)) {
      case T.nada:
        break;
      case T.off:
        mod = false;
        //$FALL-THROUGH$
      case T.on:
        eval.iToken++;
        break;
      }
      eval.checkLast(eval.iToken);
      break;
    case T.leftbrace:
    case T.point3f:
      qtOffset = eval.getPoint3f(1, false);
      isQ = (tokAt(eval.iToken + 1) == T.on);
      break;
    case T.decimal:
      float t1 = floatParameter(1);
      qtOffset = P3.new3(t1, t1, t1);
      break;
    case T.integer:
      int t = intParameter(1);
      qtOffset = P3.new3(t, t, t);
      isQ = true;
      break;
    case T.scale:
      float scale = floatParameter(2);
      if (!chk)
        viewer.setFloatProperty("modulationScale", scale);
      return;
//    case T.fps:
//      float f = floatParameter(2);
//      if (!chk)
//        viewer.setModulationFps(f);
//      return;
//    case T.play:
//      int t0 = intParameter(2);
//      frameN = intParameter(3);
//      qtOffset = P3.new3(t0, t0, t0);
//      isQ = true;
//      break;
    default:
      invArg();
    }
    if (!chk)
      viewer.setModulation(bs, mod, qtOffset, isQ);

  }

  private BS setContactBitSets(BS bsA, BS bsB, boolean localOnly,
                              float distance, RadiusData rd,
                              boolean warnMultiModel) {
    boolean withinAllModels;
    BS bs;
    if (bsB == null) {
      // default is within just one model when {B} is missing
      bsB = BSUtil.setAll(viewer.getAtomCount());
      BSUtil.andNot(bsB, viewer.getDeletedAtoms());
      bsB.andNot(bsA);
      withinAllModels = false;
    } else {
      // two atom sets specified; within ALL MODELS here
      bs = BSUtil.copy(bsA);
      bs.or(bsB);
      int nModels = viewer.getModelBitSet(bs, false).cardinality();
      withinAllModels = (nModels > 1);
      if (warnMultiModel && nModels > 1 && !eval.tQuiet)
        showString(GT
            ._("Note: More than one model is involved in this contact!"));
    }
    // B always within some possibly extended VDW of A or just A itself
    if (!bsA.equals(bsB)) {
      boolean setBfirst = (!localOnly || bsA.cardinality() < bsB.cardinality());
      if (setBfirst) {
        bs = viewer.getAtomsWithinRadius(distance, bsA, withinAllModels, (Float
            .isNaN(distance) ? rd : null));
        bsB.and(bs);
      }
      if (localOnly) {
        // we can just get the near atoms for A as well.
        bs = viewer.getAtomsWithinRadius(distance, bsB, withinAllModels, (Float
            .isNaN(distance) ? rd : null));
        bsA.and(bs);
        if (!setBfirst) {
          bs = viewer.getAtomsWithinRadius(distance, bsA, withinAllModels,
              (Float.isNaN(distance) ? rd : null));
          bsB.and(bs);
        }
        // If the two sets are not the same,
        // we AND them and see if that is A. 
        // If so, then the smaller set is
        // removed from the larger set.
        bs = BSUtil.copy(bsB);
        bs.and(bsA);
        if (bs.equals(bsA))
          bsB.andNot(bsA);
        else if (bs.equals(bsB))
          bsA.andNot(bsB);
      }
    }
    return bsB;
  }

  private void compare() throws ScriptException {
    // compare {model1} {model2} 
    // compare {model1} {model2} ATOMS {bsAtoms1} {bsAtoms2}
    // compare {model1} {model2} ORIENTATIONS
    // compare {model1} {model2} ORIENTATIONS {bsAtoms1} {bsAtoms2}
    // compare {model1} {model2} ORIENTATIONS [quaternionList1] [quaternionList2]
    // compare {model1} {model2} SMILES "....."
    // compare {model1} {model2} SMARTS "....."
    // compare {model1} {model2} FRAMES
    // compare {model1} ATOMS {bsAtoms1} [coords]
    // compare {model1} [coords] ATOMS {bsAtoms1} [coords]
    // compare {model1} {model2} BONDS "....."   /// flexible fit
    // compare {model1} {model2} BONDS SMILES   /// flexible fit

    boolean isQuaternion = false;
    boolean doRotate = false;
    boolean doTranslate = false;
    boolean doAnimate = false;
    boolean isFlexFit = false;
    Quaternion[] data1 = null, data2 = null;
    BS bsAtoms1 = null, bsAtoms2 = null;
    List<Object[]> vAtomSets = null;
    List<Object[]> vQuatSets = null;
    eval.iToken = 0;
    float nSeconds = (isFloatParameter(1) ? floatParameter(++eval.iToken)
        : Float.NaN);
    ///BS bsFrom = (tokAt(++iToken) == T.subset ? null : atomExpressionAt(iToken));
    //BS bsTo = (tokAt(++iToken) == T.subset ? null : atomExpressionAt(iToken));
    //if (bsFrom == null || bsTo == null)
    ///invArg();
    BS bsFrom = atomExpressionAt(++eval.iToken);
    P3[] coordTo = null;
    BS bsTo = null;
    if (eval.isArrayParameter(++eval.iToken)) {
      coordTo = eval.getPointArray(eval.iToken, -1);
    } else if (tokAt(eval.iToken) != T.atoms) {
      bsTo = atomExpressionAt(eval.iToken);
    }
    BS bsSubset = null;
    boolean isSmiles = false;
    String strSmiles = null;
    BS bs = BSUtil.copy(bsFrom);
    if (bsTo != null)
      bs.or(bsTo);
    boolean isToSubsetOfFrom = (coordTo == null && bsTo != null && bs
        .equals(bsFrom));
    boolean isFrames = isToSubsetOfFrom;
    for (int i = eval.iToken + 1; i < slen; ++i) {
      switch (getToken(i).tok) {
      case T.frame:
        isFrames = true;
        break;
      case T.smiles:
        isSmiles = true;
        //$FALL-THROUGH$
      case T.search: // SMARTS
        strSmiles = stringParameter(++i);
        break;
      case T.bonds:
        isFlexFit = true;
        doRotate = true;
        strSmiles = parameterAsString(++i);
        if (strSmiles.equalsIgnoreCase("SMILES")) {
          isSmiles = true;
          strSmiles = viewer.getSmiles(0, 0, bsFrom, false, false, false, false);
        }
        break;
      case T.decimal:
      case T.integer:
        nSeconds = Math.abs(floatParameter(i));
        if (nSeconds > 0)
          doAnimate = true;
        break;
      case T.comma:
        break;
      case T.subset:
        bsSubset = atomExpressionAt(++i);
        i = eval.iToken;
        break;
      case T.bitset:
      case T.expressionBegin:
        if (vQuatSets != null)
          invArg();
        bsAtoms1 = atomExpressionAt(eval.iToken);
        int tok = (isToSubsetOfFrom ? 0 : tokAt(eval.iToken + 1));
        bsAtoms2 = (coordTo == null && eval.isArrayParameter(eval.iToken + 1) ? null
            : (tok == T.bitset || tok == T.expressionBegin ? atomExpressionAt(++eval.iToken)
                : BSUtil.copy(bsAtoms1)));
        if (bsSubset != null) {
          bsAtoms1.and(bsSubset);
          if (bsAtoms2 != null)
            bsAtoms2.and(bsSubset);
        }

        if (bsAtoms2 == null)
          coordTo = eval.getPointArray(++eval.iToken, -1);
        else
          bsAtoms2.and(bsTo);
        if (vAtomSets == null)
          vAtomSets = new List<Object[]>();
        vAtomSets.addLast(new BS[] { bsAtoms1, bsAtoms2 });
        i = eval.iToken;
        break;
      case T.varray:
        if (vAtomSets != null)
          invArg();
        isQuaternion = true;
        data1 = getQuaternionArray(((SV) eval.theToken)
            .getList(), T.list);
        getToken(++i);
        data2 = getQuaternionArray(((SV) eval.theToken)
            .getList(), T.list);
        if (vQuatSets == null)
          vQuatSets = new List<Object[]>();
        vQuatSets.addLast(new Object[] { data1, data2 });
        break;
      case T.orientation:
        isQuaternion = true;
        break;
      case T.point:
      case T.atoms:
        isQuaternion = false;
        break;
      case T.rotate:
        doRotate = true;
        break;
      case T.translate:
        doTranslate = true;
        break;
      default:
        invArg();
      }
    }
    if (chk)
      return;

    // processing
    if (isFrames)
      nSeconds = 0;
    if (Float.isNaN(nSeconds) || nSeconds < 0)
      nSeconds = 1;
    else if (!doRotate && !doTranslate)
      doRotate = doTranslate = true;
    doAnimate = (nSeconds != 0);

    boolean isAtoms = (!isQuaternion && strSmiles == null || coordTo != null);
    if (vAtomSets == null && vQuatSets == null) {
      if (bsSubset == null) {
        bsAtoms1 = (isAtoms ? viewer.getAtomBitSet("spine") : new BS());
        if (bsAtoms1.nextSetBit(0) < 0) {
          bsAtoms1 = bsFrom;
          bsAtoms2 = bsTo;
        } else {
          bsAtoms2 = BSUtil.copy(bsAtoms1);
          bsAtoms1.and(bsFrom);
          bsAtoms2.and(bsTo);
        }
      } else {
        bsAtoms1 = BSUtil.copy(bsFrom);
        bsAtoms2 = BSUtil.copy(bsTo);
        bsAtoms1.and(bsSubset);
        bsAtoms2.and(bsSubset);
        bsAtoms1.and(bsFrom);
        bsAtoms2.and(bsTo);
      }
      vAtomSets = new List<Object[]>();
      vAtomSets.addLast(new BS[] { bsAtoms1, bsAtoms2 });
    }

    BS[] bsFrames;
    if (isFrames) {
      BS bsModels = viewer.getModelBitSet(bsFrom, false);
      bsFrames = new BS[bsModels.cardinality()];
      for (int i = 0, iModel = bsModels.nextSetBit(0); iModel >= 0; iModel = bsModels
          .nextSetBit(iModel + 1), i++)
        bsFrames[i] = viewer.getModelUndeletedAtomsBitSet(iModel);
    } else {
      bsFrames = new BS[] { bsFrom };
    }
    for (int iFrame = 0; iFrame < bsFrames.length; iFrame++) {
      bsFrom = bsFrames[iFrame];
      float[] retStddev = new float[2]; // [0] final, [1] initial for atoms
      Quaternion q = null;
      List<Quaternion> vQ = new List<Quaternion>();
      P3[][] centerAndPoints = null;
      List<Object[]> vAtomSets2 = (isFrames ? new List<Object[]>()
          : vAtomSets);
      for (int i = 0; i < vAtomSets.size(); ++i) {
        BS[] bss = (BS[]) vAtomSets.get(i);
        if (isFrames)
          vAtomSets2.addLast(bss = new BS[] { BSUtil.copy(bss[0]), bss[1] });
        bss[0].and(bsFrom);
      }
      P3 center = null;
      V3 translation = null;
      if (isAtoms) {
        if (coordTo != null) {
          vAtomSets2.clear();
          vAtomSets2.addLast(new Object[] { bsAtoms1, coordTo });
        }
        try {
          centerAndPoints = viewer.getCenterAndPoints(vAtomSets2, true);
        } catch (Exception e) {
          invArg();
        }
        q = Measure.calculateQuaternionRotation(centerAndPoints, retStddev,
            true);
        float r0 = (Float.isNaN(retStddev[1]) ? Float.NaN : Math
            .round(retStddev[0] * 100) / 100f);
        float r1 = (Float.isNaN(retStddev[1]) ? Float.NaN : Math
            .round(retStddev[1] * 100) / 100f);
        showString("RMSD " + r0 + " --> " + r1 + " Angstroms");
      } else if (isQuaternion) {
        if (vQuatSets == null) {
          for (int i = 0; i < vAtomSets2.size(); i++) {
            BS[] bss = (BS[]) vAtomSets2.get(i);
            data1 = viewer.getAtomGroupQuaternions(bss[0], Integer.MAX_VALUE);
            data2 = viewer.getAtomGroupQuaternions(bss[1], Integer.MAX_VALUE);
            for (int j = 0; j < data1.length && j < data2.length; j++) {
              vQ.addLast(data2[j].div(data1[j]));
            }
          }
        } else {
          for (int j = 0; j < data1.length && j < data2.length; j++) {
            vQ.addLast(data2[j].div(data1[j]));
          }
        }
        retStddev[0] = 0;
        data1 = vQ.toArray(new Quaternion[vQ.size()]);
        q = Quaternion.sphereMean(data1, retStddev, 0.0001f);
        showString("RMSD = " + retStddev[0] + " degrees");
      } else {
        // SMILES
        /* not sure why this was like this:
        if (vAtomSets == null) {
          vAtomSets = new  List<BitSet[]>();
        }
        bsAtoms1 = BitSetUtil.copy(bsFrom);
        bsAtoms2 = BitSetUtil.copy(bsTo);
        vAtomSets.add(new BitSet[] { bsAtoms1, bsAtoms2 });
        */

        M4 m4 = new M4();
        center = new P3();
        if (isFlexFit) {
          float[] list;
          if (bsFrom == null || bsTo == null || (list = getFlexFitList(bsFrom, bsTo, strSmiles, !isSmiles)) == null)
            return;
          viewer.setDihedrals(list, null, 1);
        }
        float stddev = getSmilesCorrelation(bsFrom, bsTo, strSmiles, null,
            null, m4, null, !isSmiles, false, null, center);
        if (Float.isNaN(stddev))
          invArg();
        if (doTranslate) {
          translation = new V3();
          m4.get(translation);
        }
        if (doRotate) {
          M3 m3 = new M3();
          m4.getRotationScale(m3);
          q = Quaternion.newM(m3);
        }
        showString("RMSD = " + stddev + " Angstroms");
      }
      if (centerAndPoints != null)
        center = centerAndPoints[0][0];
      if (center == null) {
        centerAndPoints = viewer.getCenterAndPoints(vAtomSets2, true);
        center = centerAndPoints[0][0];
      }
      P3 pt1 = new P3();
      float endDegrees = Float.NaN;
      if (doTranslate) {
        if (translation == null)
          translation = V3.newVsub(centerAndPoints[1][0], center);
        endDegrees = 1e10f;
      }
      if (doRotate) {
        if (q == null)
          eval.evalError("option not implemented", null);
        pt1.add2(center, q.getNormal());
        endDegrees = q.getTheta();
        if (endDegrees == 0 && doTranslate) {
          if (translation.length() > 0.01f)
            endDegrees= 1e10f;
          else
            doRotate = doTranslate = doAnimate = false;
        }
      }
      if (Float.isNaN(endDegrees) || Float.isNaN(pt1.x))
        continue;
      List<P3> ptsB = null;
      if (doRotate && doTranslate && nSeconds != 0) {
        List<P3> ptsA = viewer.getAtomPointVector(bsFrom);
        M4 m4 = ScriptMathProcessor.getMatrix4f(q.getMatrix(),
            translation);
        ptsB = Measure.transformPoints(ptsA, m4, center);
      }
      if (!eval.useThreads())
        doAnimate = false;
      if (viewer.rotateAboutPointsInternal(eval, center, pt1, endDegrees
          / nSeconds, endDegrees, doAnimate, bsFrom, translation, ptsB, null)
          && doAnimate && eval.isJS)
        throw new ScriptInterruption(eval, "compare", 1);
    }
  }

  private void configuration() throws ScriptException {
    // if (!chk && viewer.getDisplayModelIndex() <= -2)
    // error(ERROR_backgroundModelError, "\"CONFIGURATION\"");
    BS bsAtoms;
    if (slen == 1) {
      bsAtoms = viewer.setConformation();
      viewer.addStateScriptRet("select", null, viewer.getSelectionSet(false),
          null, "configuration", true, false);
    } else {
      int n = intParameter(eval.checkLast(1));
      if (chk)
        return;
      bsAtoms = viewer.getConformation(viewer.getCurrentModelIndex(), n - 1,
          true);
      viewer.addStateScript("configuration " + n + ";", true, false);
    }
    if (chk)
      return;
    setShapeProperty(JC.SHAPE_STICKS, "type", Integer
        .valueOf(JmolEdge.BOND_HYDROGEN_MASK));
    eval.setShapeSizeBs(JC.SHAPE_STICKS, 0, bsAtoms);
    viewer.autoHbond(bsAtoms, bsAtoms, true);
    viewer.select(bsAtoms, false, 0, eval.tQuiet);
  }

  @SuppressWarnings("static-access")
  private void measure() throws ScriptException {
    ScriptEvaluator eval = this.eval;
    String id = null;
    int pt = 1;
    short colix = 0;
    float[] offset = null;
    if (slen == 2)
      switch (tokAt(1)) {
      case T.off:
        setShapeProperty(JC.SHAPE_MEASURES, "hideAll", Boolean.TRUE);
        return;
      case T.delete:
        if (!chk)
          viewer.clearAllMeasurements();
        return;
      }
    viewer.loadShape(JC.SHAPE_MEASURES);
    switch (tokAt(1)) {
    case T.search:
      String smarts = stringParameter(slen == 3 ? 2 : 4);
      if (chk)
        return;
      Atom[] atoms = viewer.modelSet.atoms;
      int atomCount = viewer.getAtomCount();
      int[][] maps = viewer.getSmilesMatcher().getCorrelationMaps(smarts,
          atoms, atomCount, viewer.getSelectionSet(false), true, false);
      if (maps == null)
        return;
      setShapeProperty(JC.SHAPE_MEASURES, "maps", maps);
      return;
    }
    switch (slen) {
    case 2:
      switch (getToken(pt).tok) {
      case T.nada:
      case T.on:
        viewer.loadShape(JC.SHAPE_MEASURES);
        setShapeProperty(JC.SHAPE_MEASURES, "hideAll", Boolean.FALSE);
        return;
      case T.list:
        if (!chk)
          eval.showStringPrint(viewer.getMeasurementInfoAsString(), false);
        return;
      case T.string:
        setShapeProperty(JC.SHAPE_MEASURES, "setFormats", stringParameter(1));
        return;
      }
      eval.errorStr(ScriptEvaluator.ERROR_keywordExpected, "ON, OFF, DELETE");
      break;
    case 3: // measure delete N
      // search "smartsString"
      switch (getToken(1).tok) {
      case T.delete:
        if (getToken(2).tok == T.all) {
          if (!chk)
            viewer.clearAllMeasurements();
        } else {
          int i = intParameter(2) - 1;
          if (!chk)
            viewer.deleteMeasurement(i);
        }
        return;
      }
    }

    int nAtoms = 0;
    int expressionCount = 0;
    int modelIndex = -1;
    int atomIndex = -1;
    int ptFloat = -1;
    int[] countPlusIndexes = new int[5];
    float[] rangeMinMax = new float[] { Float.MAX_VALUE, Float.MAX_VALUE };
    boolean isAll = false;
    boolean isAllConnected = false;
    boolean isNotConnected = false;
    boolean isRange = true;
    RadiusData rd = null;
    Boolean intramolecular = null;
    int tokAction = T.opToggle;
    String strFormat = null;
    Font font = null;

    List<Object> points = new List<Object>();
    BS bs = new BS();
    Object value = null;
    TickInfo tickInfo = null;
    int nBitSets = 0;
    int mad = 0;
    for (int i = 1; i < slen; ++i) {
      switch (getToken(i).tok) {
      case T.id:
        if (i != 1)
          invArg();
        id = eval.optParameterAsString(++i);
        continue;
      case T.identifier:
        eval.errorStr(ScriptEvaluator.ERROR_keywordExpected,
            "ALL, ALLCONNECTED, DELETE");
        break;
      default:
        error(ScriptEvaluator.ERROR_expressionOrIntegerExpected);
        break;
      case T.opNot:
        if (tokAt(i + 1) != T.connected)
          invArg();
        i++;
        isNotConnected = true;
        break;
      case T.connected:
      case T.allconnected:
      case T.all:
        isAllConnected = (eval.theTok == T.allconnected);
        atomIndex = -1;
        isAll = true;
        if (isAllConnected && isNotConnected)
          invArg();
        break;
      case T.color:
        colix = C.getColix(eval.getArgbParam(++i));
        i = eval.iToken;
        break;
      case T.offset:
        if (eval.isPoint3f(++i)) {
          // PyMOL offsets -- {x, y, z} in angstroms
          P3 p = getPoint3f(i, false);
          offset = new float[] { 1, p.x, p.y, p.z, 0, 0, 0 };
        } else {
          offset = eval.floatParameterSet(i, 7, 7);
        }
        i = eval.iToken;
        break;
      case T.radius:
      case T.diameter:
        mad = (int) ((eval.theTok == T.radius ? 2000 : 1000) * floatParameter(++i));
        if (id != null && mad <= 0)
          mad = -1;
        break;
      case T.decimal:
        if (rd != null)
          invArg();
        isAll = true;
        isRange = true;
        ptFloat = (ptFloat + 1) % 2;
        rangeMinMax[ptFloat] = floatParameter(i);
        break;
      case T.delete:
        if (tokAction != T.opToggle)
          invArg();
        tokAction = T.delete;
        break;
      case T.font:
        float fontsize = floatParameter(++i);
        String fontface = parameterAsString(++i);
        String fontstyle = parameterAsString(++i);
        if (!chk)
          font = viewer.getFont3D(fontface, fontstyle, fontsize);
        break;
      case T.integer:
        int iParam = intParameter(i);
        if (isAll) {
          isRange = true; // irrelevant if just four integers
          ptFloat = (ptFloat + 1) % 2;
          rangeMinMax[ptFloat] = iParam;
        } else {
          atomIndex = viewer.getAtomIndexFromAtomNumber(iParam);
          if (!chk && atomIndex < 0)
            return;
          if (value != null)
            invArg();
          if ((countPlusIndexes[0] = ++nAtoms) > 4)
            error(ScriptEvaluator.ERROR_badArgumentCount);
          countPlusIndexes[nAtoms] = atomIndex;
        }
        break;
      case T.modelindex:
        modelIndex = intParameter(++i);
        break;
      case T.off:
        if (tokAction != T.opToggle)
          invArg();
        tokAction = T.off;
        break;
      case T.on:
        if (tokAction != T.opToggle)
          invArg();
        tokAction = T.on;
        break;
      case T.range:
        isAll = true;
        isRange = true; // unnecessary
        atomIndex = -1;
        break;
      case T.intramolecular:
      case T.intermolecular:
        intramolecular = Boolean.valueOf(eval.theTok == T.intramolecular);
        isAll = true;
        isNotConnected = (eval.theTok == T.intermolecular);
        break;
      case T.vanderwaals:
        if (ptFloat >= 0)
          invArg();
        rd = eval.encodeRadiusParameter(i, false, true);
        rd.values = rangeMinMax;
        i = eval.iToken;
        isNotConnected = true;
        isAll = true;
        intramolecular = Boolean.valueOf(false);
        if (nBitSets == 1) {
          nBitSets++;
          nAtoms++;
          BS bs2 = BSUtil.copy(bs);
          BSUtil.invertInPlace(bs2, viewer.getAtomCount());
          bs2.and(viewer.getAtomsWithinRadius(5, bs, false, null));
          points.addLast(bs2);
        }
        break;
      case T.bitset:
      case T.expressionBegin:
      case T.leftbrace:
      case T.point3f:
      case T.dollarsign:
        if (eval.theTok == T.bitset || eval.theTok == T.expressionBegin)
          nBitSets++;
        if (atomIndex >= 0)
          invArg();
        eval.expressionResult = Boolean.FALSE;
        value = centerParameter(i);
        if (eval.expressionResult instanceof BS) {
          value = bs = (BS) eval.expressionResult;
          if (!chk && bs.length() == 0)
            return;
        }
        if (value instanceof P3) {
          Point3fi v = new Point3fi();
          v.setT((P3) value);
          v.modelIndex = (short) modelIndex;
          value = v;
        }
        if ((nAtoms = ++expressionCount) > 4)
          error(ScriptEvaluator.ERROR_badArgumentCount);
        i = eval.iToken;
        points.addLast(value);
        break;
      case T.string:
        // measures "%a1 %a2 %v %u"
        strFormat = stringParameter(i);
        break;
      case T.ticks:
        tickInfo = eval.checkTicks(i, false, true, true);
        i = eval.iToken;
        tokAction = T.define;
        break;
      }
    }
    if (rd != null && (ptFloat >= 0 || nAtoms != 2) || nAtoms < 2 && id == null
        && (tickInfo == null || nAtoms == 1))
      error(ScriptEvaluator.ERROR_badArgumentCount);
    if (strFormat != null && strFormat.indexOf(nAtoms + ":") != 0)
      strFormat = nAtoms + ":" + strFormat;
    if (isRange) {
      if (rangeMinMax[1] < rangeMinMax[0]) {
        rangeMinMax[1] = rangeMinMax[0];
        rangeMinMax[0] = (rangeMinMax[1] == Float.MAX_VALUE ? Float.MAX_VALUE
            : -200);
      }
    }
    if (chk)
      return;
    if (value != null || tickInfo != null) {
      if (rd == null)
        rd = new RadiusData(rangeMinMax, 0, null, null);
      if (value == null)
        tickInfo.id = "default";
      if (value != null && strFormat != null && tokAction == T.opToggle)
        tokAction = T.define;
      Text text = null;
      if (font != null)
        text = ((Text) Interface.getOptionInterface("modelset.Text")).newLabel(
            viewer.getGraphicsData(), font, "", colix, (short) 0, 0, 0, null);
      if (text != null)
        text.pymolOffset = offset;
      setShapeProperty(JC.SHAPE_MEASURES, "measure", newMeasurementData(id,
          points).set(tokAction, null, rd, strFormat, null, tickInfo,
          isAllConnected, isNotConnected, intramolecular, isAll, mad, colix,
          text));
      return;
    }
    Object propertyValue = (id == null ? countPlusIndexes : id);
    switch (tokAction) {
    case T.delete:
      setShapeProperty(JC.SHAPE_MEASURES, "delete", propertyValue);
      break;
    case T.on:
      setShapeProperty(JC.SHAPE_MEASURES, "show", propertyValue);
      break;
    case T.off:
      setShapeProperty(JC.SHAPE_MEASURES, "hide", propertyValue);
      break;
    default:
      setShapeProperty(JC.SHAPE_MEASURES, (strFormat == null ? "toggle"
          : "toggleOn"), propertyValue);
      if (strFormat != null)
        setShapeProperty(JC.SHAPE_MEASURES, "setFormats", strFormat);
    }
  }

  private float[] getFlexFitList(BS bs1, BS bs2, String smiles1, boolean isSmarts)
      throws ScriptException {
    int[][] mapSet = AU.newInt2(2);
    getSmilesCorrelation(bs1, bs2, smiles1, null, null, null, null, isSmarts,
        false, mapSet, null);
    if (mapSet[0] == null)
      return null;
    int[][] bondMap1 = viewer.getDihedralMap(mapSet[0]);
    int[][] bondMap2 = (bondMap1 == null ? null : viewer
        .getDihedralMap(mapSet[1]));
    if (bondMap2 == null || bondMap2.length != bondMap1.length)
      return null;
    float[][] angles = new float[bondMap1.length][3];
    Atom[] atoms = viewer.modelSet.atoms;
    getTorsions(atoms, bondMap2, angles, 0);
    getTorsions(atoms, bondMap1, angles, 1);
    float[] data = new float[bondMap1.length * 6];
    for (int i = 0, pt = 0; i < bondMap1.length; i++) {
      int[] map = bondMap1[i];
      data[pt++] = map[0];
      data[pt++] = map[1];
      data[pt++] = map[2];
      data[pt++] = map[3];
      data[pt++] = angles[i][0];
      data[pt++] = angles[i][1];
    }
    return data;
  }

  private static void getTorsions(Atom[] atoms, int[][] bondMap,
                                  float[][] diff, int pt) {
    for (int i = bondMap.length; --i >= 0;) {
      int[] map = bondMap[i];
      float v = Measure.computeTorsion(atoms[map[0]], atoms[map[1]],
          atoms[map[2]], atoms[map[3]], true);
      if (pt == 1) {
        if (v - diff[i][0] > 180)
          v -= 360;
        else if (v - diff[i][0] <= -180)
          v += 360;
      }
      diff[i][pt] = v;
    }
  }

  private float getSmilesCorrelation(BS bsA, BS bsB, String smiles,
                                    List<P3> ptsA, List<P3> ptsB,
                                    M4 m4, List<BS> vReturn,
                                    boolean isSmarts, boolean asMap,
                                    int[][] mapSet, P3 center)
      throws ScriptException {
    float tolerance = (mapSet == null ? 0.1f : Float.MAX_VALUE);
    try {
      if (ptsA == null) {
        ptsA = new List<P3>();
        ptsB = new List<P3>();
      }
      M4 m = new M4();
      P3 c = new P3();

      Atom[] atoms = viewer.modelSet.atoms;
      int atomCount = viewer.getAtomCount();
      int[][] maps = viewer.getSmilesMatcher().getCorrelationMaps(smiles,
          atoms, atomCount, bsA, isSmarts, true);
      if (maps == null)
        eval.evalError(viewer.getSmilesMatcher().getLastException(), null);
      if (maps.length == 0)
        return Float.NaN;
      int[] mapA = maps[0];
      for (int i = 0; i < mapA.length; i++)
        ptsA.addLast(atoms[mapA[i]]);
      maps = viewer.getSmilesMatcher().getCorrelationMaps(smiles, atoms,
          atomCount, bsB, isSmarts, false);
      if (maps == null)
        eval.evalError(viewer.getSmilesMatcher().getLastException(), null);
      if (maps.length == 0)
        return Float.NaN;
      if (asMap) {
        for (int i = 0; i < maps.length; i++)
          for (int j = 0; j < maps[i].length; j++)
            ptsB.addLast(atoms[maps[i][j]]);
        return 0;
      }
      float lowestStdDev = Float.MAX_VALUE;
      int[] mapB = null;
      for (int i = 0; i < maps.length; i++) {
        ptsB.clear();
        for (int j = 0; j < maps[i].length; j++)
          ptsB.addLast(atoms[maps[i][j]]);
        float stddev = Measure.getTransformMatrix4(ptsA, ptsB, m, c);
        Logger.info("getSmilesCorrelation stddev=" + stddev);
        if (vReturn != null) {
          if (stddev < tolerance) {
            BS bs = new BS();
            for (int j = 0; j < maps[i].length; j++)
              bs.set(maps[i][j]);
            vReturn.addLast(bs);
          }
        }
        if (stddev < lowestStdDev) {
          mapB = maps[i];
          if (m4 != null)
            m4.setM(m);
          if (center != null)
            center.setT(c);
          lowestStdDev = stddev;
        }
      }
      if (mapSet != null) {
        mapSet[0] = mapA;
        mapSet[1] = mapB;
      }
      ptsB.clear();
      for (int i = 0; i < mapB.length; i++)
        ptsB.addLast(atoms[mapB[i]]);
      return lowestStdDev;
    } catch (Exception e) {
      eval.evalError(e.toString(), null);
      return 0; // unattainable
    }
  }

  @Override
  public Object getSmilesMatches(String pattern, String smiles, BS bsSelected,
                                 BS bsMatch3D, boolean isSmarts,
                                 boolean asOneBitset) throws ScriptException {
    if (chk) {
      if (asOneBitset)
        return new BS();
      return new String[] { "({})" };
    }

    // just retrieving the SMILES or bioSMILES string

    if (pattern.length() == 0) {
      boolean isBioSmiles = (!asOneBitset);
      Object ret = viewer.getSmiles(0, 0, bsSelected, isBioSmiles, false, true,
          true);
      if (ret == null)
        eval.evalError(viewer.getSmilesMatcher().getLastException(), null);
      return ret;
    }

    boolean asAtoms = true;
    BS[] b;
    if (bsMatch3D == null) {

      // getting a BitSet or BitSet[] from a set of atoms or a pattern.

      asAtoms = (smiles == null);
      if (asAtoms)
        b = viewer.getSmilesMatcher().getSubstructureSetArray(pattern,
            viewer.modelSet.atoms, viewer.getAtomCount(), bsSelected, null,
            isSmarts, false);
      else
        b = viewer.getSmilesMatcher().find(pattern, smiles, isSmarts, false);

      if (b == null) {
        eval.showStringPrint(viewer.getSmilesMatcher().getLastException(),
            false);
        if (!asAtoms && !isSmarts)
          return Integer.valueOf(-1);
        return "?";
      }
    } else {

      // getting a correlation

      List<BS> vReturn = new List<BS>();
      float stddev = getSmilesCorrelation(bsMatch3D, bsSelected, pattern, null,
          null, null, vReturn, isSmarts, false, null, null);
      if (Float.isNaN(stddev)) {
        if (asOneBitset)
          return new BS();
        return new String[] {};
      }
      showString("RMSD " + stddev + " Angstroms");
      b = vReturn.toArray(new BS[vReturn.size()]);
    }
    if (asOneBitset) {
      // sum total of all now, not just first
      BS bs = new BS();
      for (int j = 0; j < b.length; j++)
        bs.or(b[j]);
      if (asAtoms)
        return bs;
      if (!isSmarts)
        return Integer.valueOf(bs.cardinality());
      int[] iarray = new int[bs.cardinality()];
      int pt = 0;
      for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
        iarray[pt++] = i + 1;
      return iarray;
    }
    String[] matches = new String[b.length];
    for (int j = 0; j < b.length; j++)
      matches[j] = (asAtoms ? Escape.eBS(b[j]) : Escape.eBond(b[j]));
    return matches;
  }

  // ScriptMathProcessor extensions
  
  @Override
  public boolean evaluate(ScriptMathProcessor mp, T op, SV[] args, int tok) throws ScriptException {
    switch (tok) {
    case T.abs:
    case T.acos:
    case T.cos:
    case T.now:
    case T.sin:
    case T.sqrt:
      return evaluateMath(mp, args, tok);
    case T.add:
    case T.div:
    case T.mul:
    case T.mul3:
    case T.sub:
    case T.push:
    case T.pop:
      return evaluateList(mp, op.intValue, args);
    case T.array:
    case T.leftsquare:
      return evaluateArray(mp, args, tok == T.leftsquare);
    case T.axisangle:
    case T.quaternion:
      return evaluateQuaternion(mp, args, tok);
    case T.bin:
      return evaluateBin(mp, args);
    case T.col:
    case T.row:
      return evaluateRowCol(mp, args, tok);
    case T.color:
      return evaluateColor(mp, args);
    case T.compare:
      return evaluateCompare(mp, args);
    case T.connected:
      return evaluateConnected(mp, args);
    case T.cross:
      return evaluateCross(mp, args);
    case T.data:
      return evaluateData(mp, args);
    case T.distance:
    case T.dot:
      if (op.tok == T.propselector)
        return evaluateDot(mp, args, tok, op.intValue);
      //$FALL-THROUGH$
    case T.angle:
    case T.measure:
      return evaluateMeasure(mp, args, op.tok);
    case T.file:
    case T.load:
      return evaluateLoad(mp, args, tok);
    case T.find:
      return evaluateFind(mp, args);
    case T.function:
      return evaluateUserFunction(mp, (String) op.value, args, op.intValue,
          op.tok == T.propselector);
    case T.format:
    case T.label:
      return evaluateLabel(mp, op.intValue, args);
    case T.getproperty:
      return evaluateGetProperty(mp, args, op.tok == T.propselector);
    case T.helix:
      return evaluateHelix(mp, args);
    case T.hkl:
    case T.plane:
    case T.intersection:
      return evaluatePlane(mp, args, tok);
    case T.javascript:
    case T.script:
      return evaluateScript(mp, args, tok);
    case T.join:
    case T.split:
    case T.trim:
      return evaluateString(mp, op.intValue, args);
    case T.point:
      return evaluatePoint(mp, args);
    case T.prompt:
      return evaluatePrompt(mp, args);
    case T.random:
      return evaluateRandom(mp, args);
    case T.replace:
      return evaluateReplace(mp, args);
    case T.search:
    case T.smiles:
    case T.substructure:
      return evaluateSubstructure(mp, args, tok);
    case T.cache:
      return evaluateCache(mp, args);
    case T.modulation:
      return evaluateModulation(mp, args);
    case T.sort:
    case T.count:
      return evaluateSort(mp, args, tok);
    case T.symop:
      return evaluateSymop(mp, args, op.tok == T.propselector);
//    case Token.volume:
  //    return evaluateVolume(args);
    case T.tensor:
      return evaluateTensor(mp, args);
    case T.within:
      return evaluateWithin(mp, args);
    case T.contact:
      return evaluateContact(mp, args);
    case T.write:
      return evaluateWrite(mp, args);
    }
    return false;
  }

  private boolean evaluateModulation(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    String type = "D";
    float t = Float.NaN;
    P3 t456 = null;
    int pt = -1;
    switch (args.length) {
    case 0:
      break;
    case 1:
      pt = 0;
      break;
    case 2:
      type = SV.sValue(args[0]).toUpperCase();
      t = SV.fValue(args[1]);
      break;
    default:
      return false;
    }
    if (pt >= 0) {
      if (args[pt].tok == T.point3f)
        t456 = (P3) args[pt].value;
      else
        t = SV.fValue(args[pt]);
    }
    if (t456 == null && t < 1e6)
      t456 = P3.new3(t,  t,  t);
    BS bs = SV.getBitSet(mp.getX(), false);
    return mp.addXList(viewer.getModulationList(bs, type, t456));
  }

  private boolean evaluateTensor(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    // {*}.tensor()
    // {*}.tensor("isc")            // only within this atom set
    // {atomindex=1}.tensor("isc")  // all to this atom
    // {*}.tensor("efg","eigenvalues")
    if (args.length > 2)
      return false;
    BS bs = SV.getBitSet(mp.getX(), false);
    String tensorType = (args.length == 0 ? null : SV.sValue(args[0]).toLowerCase());
    JmolNMRInterface calc = viewer.getNMRCalculation();      
    if ("unique".equals(tensorType))
      return mp.addXBs(calc.getUniqueTensorSet(bs));
    String infoType = (args.length < 2 ? null : SV.sValue(args[1]).toLowerCase());
    return mp.addXList(calc.getTensorInfo(tensorType, infoType, bs));
  }

  private boolean evaluateCache(ScriptMathProcessor mp, SV[] args) {
    if (args.length > 0)
      return false;
    return mp.addXMap(viewer.cacheList());
  }

  private boolean evaluateCompare(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    // compare([{bitset} or {positions}],[{bitset} or {positions}] [,"stddev"])
    // compare({bitset},{bitset}[,"SMARTS"|"SMILES"],smilesString [,"stddev"])
    // returns matrix4f for rotation/translation or stddev
    // compare({bitset},{bitset},"ISOMER")  12.1.5
    // compare({bitset},{bitset},smartsString, "BONDS") 13.1.17
    // compare({bitset},{bitset},"SMILES", "BONDS") 13.3.9

    if (args.length < 2 || args.length > 5)
      return false;
    float stddev;
    String sOpt = SV.sValue(args[args.length - 1]);
    boolean isStdDev = sOpt.equalsIgnoreCase("stddev");
    boolean isIsomer = sOpt.equalsIgnoreCase("ISOMER");
    boolean isBonds = sOpt.equalsIgnoreCase("BONDS");
    boolean isSmiles = (!isIsomer && args.length > (isStdDev ? 3 : 2));
    BS bs1 = (args[0].tok == T.bitset ? (BS) args[0].value : null);
    BS bs2 = (args[1].tok == T.bitset ? (BS) args[1].value : null);
    String smiles1 = (bs1 == null ? SV.sValue(args[0]) : "");
    String smiles2 = (bs2 == null ? SV.sValue(args[1]) : "");
    M4 m = new M4();
    stddev = Float.NaN;
    List<P3> ptsA, ptsB;
    if (isSmiles) {
      if (bs1 == null || bs2 == null)
        return false;
    }
    if (isBonds) {
      if (args.length != 4)
        return false;
      smiles1 = SV.sValue(args[2]);
      isSmiles = smiles1.equalsIgnoreCase("SMILES");
      if (isSmiles)
        smiles1 = viewer.getSmiles(0, 0, bs1, false, false, false, false);       
      float[] data = getFlexFitList(bs1, bs2, smiles1, !isSmiles);
      return (data == null ? mp.addXStr("") : mp.addXAF(data));
    }
    if (isIsomer) {
      if (args.length != 3)
        return false;
      if (bs1 == null && bs2 == null)
        return mp.addXStr(viewer.getSmilesMatcher().getRelationship(smiles1,
            smiles2).toUpperCase());
      String mf1 = (bs1 == null ? viewer.getSmilesMatcher()
          .getMolecularFormula(smiles1, false) : JmolMolecule
          .getMolecularFormula(viewer.getModelSet().atoms, bs1, false));
      String mf2 = (bs2 == null ? viewer.getSmilesMatcher()
          .getMolecularFormula(smiles2, false) : JmolMolecule
          .getMolecularFormula(viewer.getModelSet().atoms, bs2, false));
      if (!mf1.equals(mf2))
        return mp.addXStr("NONE");
      if (bs1 != null)
        smiles1 = (String) getSmilesMatches("", null, bs1, null, false, true);
      boolean check;
      if (bs2 == null) {
        // note: find smiles1 IN smiles2 here
        check = (viewer.getSmilesMatcher().areEqual(smiles2, smiles1) > 0);
      } else {
        check = (((BS) getSmilesMatches(smiles1, null, bs2, null, false, true))
            .nextSetBit(0) >= 0);
      }
      if (!check) {
        // MF matched, but didn't match SMILES
        String s = smiles1 + smiles2;
        if (s.indexOf("/") >= 0 || s.indexOf("\\") >= 0 || s.indexOf("@") >= 0) {
          if (smiles1.indexOf("@") >= 0
              && (bs2 != null || smiles2.indexOf("@") >= 0)) {
            // reverse chirality centers
            smiles1 = viewer.getSmilesMatcher().reverseChirality(smiles1);
            if (bs2 == null) {
              check = (viewer.getSmilesMatcher().areEqual(smiles1, smiles2) > 0);
            } else {
              check = (((BS) getSmilesMatches(smiles1, null, bs2, null, false,
                  true)).nextSetBit(0) >= 0);
            }
            if (check)
              return mp.addXStr("ENANTIOMERS");
          }
          // remove all stereochemistry from SMILES string
          if (bs2 == null) {
            check = (viewer.getSmilesMatcher().areEqual("/nostereo/" + smiles2,
                smiles1) > 0);
          } else {
            Object ret = getSmilesMatches("/nostereo/" + smiles1, null, bs2,
                null, false, true);
            check = (((BS) ret).nextSetBit(0) >= 0);
          }
          if (check)
            return mp.addXStr("DIASTERIOMERS");
        }
        // MF matches, but not enantiomers or diasteriomers
        return mp.addXStr("CONSTITUTIONAL ISOMERS");
      }
      //identical or conformational 
      if (bs1 == null || bs2 == null)
        return mp.addXStr("IDENTICAL");
      stddev = getSmilesCorrelation(bs1, bs2, smiles1, null, null, null, null,
          false, false, null, null);
      return mp.addXStr(stddev < 0.2f ? "IDENTICAL"
          : "IDENTICAL or CONFORMATIONAL ISOMERS (RMSD=" + stddev + ")");
    } else if (isSmiles) {
      ptsA = new List<P3>();
      ptsB = new List<P3>();
      sOpt = SV.sValue(args[2]);
      boolean isMap = sOpt.equalsIgnoreCase("MAP");
      isSmiles = (sOpt.equalsIgnoreCase("SMILES"));
      boolean isSearch = (isMap || sOpt.equalsIgnoreCase("SMARTS"));
      if (isSmiles || isSearch)
        sOpt = (args.length > 3 ? SV.sValue(args[3]) : null);
      if (sOpt == null)
        return false;
      stddev = getSmilesCorrelation(bs1, bs2, sOpt, ptsA, ptsB, m, null,
          !isSmiles, isMap, null, null);
      if (isMap) {
        int nAtoms = ptsA.size();
        if (nAtoms == 0)
          return mp.addXStr("");
        int nMatch = ptsB.size() / nAtoms;
        List<int[][]> ret = new List<int[][]>();
        for (int i = 0, pt = 0; i < nMatch; i++) {
          int[][] a = AU.newInt2(nAtoms);
          ret.addLast(a);
          for (int j = 0; j < nAtoms; j++, pt++)
            a[j] = new int[] { ((Atom) ptsA.get(j)).index,
                ((Atom) ptsB.get(pt)).index };
        }
        return mp.addXList(ret);
      }
    } else {
      ptsA = eval.getPointVector(args[0], 0);
      ptsB = eval.getPointVector(args[1], 0);
      if (ptsA != null && ptsB != null)
        stddev = Measure.getTransformMatrix4(ptsA, ptsB, m, null);
    }
    return (isStdDev || Float.isNaN(stddev) ? mp.addXFloat(stddev) : mp
        .addXM4(m));
  }

  private boolean evaluateContact(ScriptMathProcessor mp, SV[] args) {
    if (args.length < 1 || args.length > 3)
      return false;
    int i = 0;
    float distance = 100;
    int tok = args[0].tok;
    switch (tok) {
    case T.decimal:
    case T.integer:
      distance = SV.fValue(args[i++]);
      break;
    case T.bitset:
      break;
    default:
      return false;
    }
    if (i == args.length || !(args[i].value instanceof BS))
      return false;
    BS bsA = BSUtil.copy(SV.bsSelectVar(args[i++]));
    if (chk)
      return mp.addXBs(new BS());
    BS bsB = (i < args.length ? BSUtil.copy(SV
        .bsSelectVar(args[i])) : null);
    RadiusData rd = new RadiusData(null,
        (distance > 10 ? distance / 100 : distance),
        (distance > 10 ? EnumType.FACTOR : EnumType.OFFSET), EnumVdw.AUTO);
    bsB = setContactBitSets(bsA, bsB, true, Float.NaN, rd, false);
    bsB.or(bsA);
    return mp.addXBs(bsB);
  }

//  private boolean evaluateVolume(ScriptVariable[] args) throws ScriptException {
//    ScriptVariable x1 = mp.getX();
//    if (x1.tok != Token.bitset)
//      return false;
//    String type = (args.length == 0 ? null : ScriptVariable.sValue(args[0]));
//    return mp.addX(viewer.getVolume((BitSet) x1.value, type));
//  }

  private boolean evaluateSort(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    if (args.length > 1)
      return false;
    if (tok == T.sort) {
      int n = (args.length == 0 ? 0 : args[0].asInt());
      return mp.addXVar(mp.getX().sortOrReverse(n));
    }
    SV x = mp.getX();
    SV match = (args.length == 0 ? null : args[0]);
    if (x.tok == T.string) {
      int n = 0;
      String s = SV.sValue(x);
      if (match == null)
        return mp.addXInt(0);
      String m = SV.sValue(match);
      for (int i = 0; i < s.length(); i++) {
        int pt = s.indexOf(m, i);
        if (pt < 0)
          break;
        n++;
        i = pt;
      }
      return mp.addXInt(n);
    }
    List<SV> counts = new  List<SV>();
    SV last = null;
    SV count = null;
    List<SV> xList = SV.getVariable(x.value)
        .sortOrReverse(0).getList();
    if (xList == null)
      return (match == null ? mp.addXStr("") : mp.addXInt(0));
    for (int i = 0, nLast = xList.size(); i <= nLast; i++) {
      SV a = (i == nLast ? null : xList.get(i));
      if (match != null && a != null && !SV.areEqual(a, match))
        continue;
      if (SV.areEqual(a, last)) {
        count.intValue++;
        continue;
      } else if (last != null) {
        List<SV> y = new  List<SV>();
        y.addLast(last);
        y.addLast(count);
        counts.addLast(SV.getVariableList(y));
      }
      count = SV.newI(1);
      last = a; 
    }
    if (match == null)
      return mp.addXVar(SV.getVariableList(counts));
    if (counts.isEmpty())
      return mp.addXInt(0);
    return mp.addXVar(counts.get(0).getList().get(1));

  }

  private boolean evaluateSymop(ScriptMathProcessor mp, SV[] args,
                                boolean haveBitSet) throws ScriptException {
    // {xxx}.symop()
    // symop({xxx}    
    if (args.length == 0)
      return false;
    SV x1 = (haveBitSet ? mp.getX() : null);
    if (x1 != null && x1.tok != T.bitset)
      return false;
    BS bs = (x1 != null ? (BS) x1.value : args.length > 2
        && args[1].tok == T.bitset ? (BS) args[1].value : viewer
        .getAllAtoms());
    String xyz;
    switch (args[0].tok) {
    case T.string:
      xyz = SV.sValue(args[0]);
      break;
    case T.matrix4f:
      xyz = args[0].escape();
      break;
    default:
      xyz = null;
    }
    int iOp = (xyz == null ? args[0].asInt() : 0);
    P3 pt = (args.length > 1 ? mp.ptValue(args[1], true) : null);
    if (args.length == 2 && !Float.isNaN(pt.x))
      return mp.addXObj(viewer.getSymmetryInfo(bs, xyz, iOp, pt, null, null,
          T.point));
    String desc = (args.length == 1 ? "" : SV.sValue(args[args.length - 1]))
        .toLowerCase();
    int tok = T.draw;
    if (args.length == 1 || desc.equalsIgnoreCase("matrix")) {
      tok = T.matrix4f;
    } else if (desc.equalsIgnoreCase("array") || desc.equalsIgnoreCase("list")) {
      tok = T.list;
    } else if (desc.equalsIgnoreCase("description")) {
      tok = T.label;
    } else if (desc.equalsIgnoreCase("xyz")) {
      tok = T.info;
    } else if (desc.equalsIgnoreCase("translation")) {
      tok = T.translation;
    } else if (desc.equalsIgnoreCase("axis")) {
      tok = T.axis;
    } else if (desc.equalsIgnoreCase("plane")) {
      tok = T.plane;
    } else if (desc.equalsIgnoreCase("angle")) {
      tok = T.angle;
    } else if (desc.equalsIgnoreCase("axispoint")) {
      tok = T.point;
    } else if (desc.equalsIgnoreCase("center")) {
      tok = T.center;
    }
    return mp
        .addXObj(viewer.getSymmetryInfo(bs, xyz, iOp, pt, null, desc, tok));
  }

  private boolean evaluateBin(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    if (args.length != 3)
      return false;
    SV x1 = mp.getX();
    boolean isListf = (x1.tok == T.listf);
    if (!isListf && x1.tok != T.varray)
      return mp.addXVar(x1);
    float f0 = SV.fValue(args[0]);
    float f1 = SV.fValue(args[1]);
    float df = SV.fValue(args[2]);
    float[] data;
    if (isListf) {
      data = (float[]) x1.value;
    } else {
      List<SV> list = x1.getList();
      data = new float[list.size()];
      for (int i = list.size(); --i >= 0; )
        data[i] = SV.fValue(list.get(i));
    }
    int nbins = (int) Math.floor((f1 - f0) / df + 0.01f);
    int[] array = new int[nbins];
    int nPoints = data.length;
    for (int i = 0; i < nPoints; i++) {
      float v = data[i];
      int bin = (int) Math.floor((v - f0) / df);
      if (bin < 0)
        bin = 0;
      else if (bin >= nbins)
        bin = nbins - 1;
      array[bin]++;
    }
    return mp.addXAI(array);
  }

  private boolean evaluateHelix(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    if (args.length < 1 || args.length > 5)
      return false;
    // helix({resno=3})
    // helix({resno=3},"point|axis|radius|angle|draw|measure|array")
    // helix(resno,"point|axis|radius|angle|draw|measure|array")
    // helix(pt1, pt2, dq, "point|axis|radius|angle|draw|measure|array|")
    // helix(pt1, pt2, dq, "draw","someID")
    // helix(pt1, pt2, dq)
    int pt = (args.length > 2 ? 3 : 1);
    String type = (pt >= args.length ? "array" : SV
        .sValue(args[pt]));
    int tok = T.getTokFromName(type);
    if (args.length > 2) {
      // helix(pt1, pt2, dq ...)
      P3 pta = mp.ptValue(args[0], true);
      P3 ptb = mp.ptValue(args[1], true);
      if (args[2].tok != T.point4f)
        return false;
      Quaternion dq = Quaternion.newP4((P4) args[2].value);
      switch (tok) {
      case T.nada:
        break;
      case T.point:
      case T.axis:
      case T.radius:
      case T.angle:
      case T.measure:
        return mp.addXObj(Measure.computeHelicalAxis(null, tok, pta, ptb, dq));
      case T.array:
        String[] data = (String[]) Measure.computeHelicalAxis(null, T.list,
            pta, ptb, dq);
        if (data == null)
          return false;
        return mp.addXAS(data);
      default:
        return mp.addXObj(Measure.computeHelicalAxis(type, T.draw, pta, ptb,
            dq));
      }
    } else {
      BS bs = (args[0].value instanceof BS ? (BS) args[0].value
          : eval.compareInt(T.resno, T.opEQ, args[0].asInt()));
      switch (tok) {
      case T.point:
        return mp.addXObj(viewer.getHelixData(bs, T.point));
      case T.axis:
        return mp.addXObj(viewer.getHelixData(bs, T.axis));
      case T.radius:
        return mp.addXObj(viewer.getHelixData(bs, T.radius));
      case T.angle:
        return mp.addXFloat(((Float) viewer.getHelixData(bs, T.angle))
            .floatValue());
      case T.draw:
      case T.measure:
        return mp.addXObj(viewer.getHelixData(bs, tok));
      case T.array:
        String[] data = (String[]) viewer.getHelixData(bs, T.list);
        if (data == null)
          return false;
        return mp.addXAS(data);
      }
    }
    return false;
  }

  private boolean evaluateDot(ScriptMathProcessor mp, SV[] args, int tok,
                              int intValue) throws ScriptException {
    // distance and dot
    switch (args.length) {
    case 1:
      if (tok == T.dot)
        return false;
      //$FALL-THROUGH$
    case 2:
      break;
    default:
      return false;
    }
    SV x1 = mp.getX();
    SV x2 = args[0];
    P3 pt2 = (x2.tok == T.varray ? null : mp.ptValue(x2, false));
    P4 plane2 = mp.planeValue(x2);
    if (tok == T.distance) {
      int minMax = intValue & T.minmaxmask;
      boolean isMinMax = (minMax == T.min || minMax == T.max);
      boolean isAll = minMax == T.minmaxmask;
      switch (x1.tok) {
      case T.bitset:
        BS bs = SV.bsSelectVar(x1);
        BS bs2 = null;
        boolean returnAtom = (isMinMax && args.length == 2 && args[1]
            .asBoolean());
        switch (x2.tok) {
        case T.bitset:
          bs2 = (x2.tok == T.bitset ? SV.bsSelectVar(x2) : null);
          //$FALL-THROUGH$
        case T.point3f:
          Atom[] atoms = viewer.modelSet.atoms;
          if (returnAtom) {
            float dMinMax = Float.NaN;
            int iMinMax = Integer.MAX_VALUE;
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
              float d = (bs2 == null ? atoms[i].distanceSquared(pt2)
                  : ((Float) eval.getBitsetProperty(bs2, intValue, atoms[i],
                      plane2, x1.value, null, false, x1.index, false))
                      .floatValue());
              if (minMax == T.min ? d >= dMinMax : d <= dMinMax)
                continue;
              dMinMax = d;
              iMinMax = i;
            }
            return mp.addXBs(iMinMax == Integer.MAX_VALUE ? new BS() : BSUtil
                .newAndSetBit(iMinMax));
          }
          if (isAll) {
            if (bs2 == null) {
              float[] data = new float[bs.cardinality()];
              for (int p = 0, i = bs.nextSetBit(0); i >= 0; i = bs
                  .nextSetBit(i + 1), p++)
                data[p] = atoms[i].distance(pt2);
              return mp.addXAF(data);
            }
            float[][] data2 = new float[bs.cardinality()][bs2.cardinality()];
            for (int p = 0, i = bs.nextSetBit(0); i >= 0; i = bs
                .nextSetBit(i + 1), p++)
              for (int q = 0, j = bs2.nextSetBit(0); j >= 0; j = bs2
                  .nextSetBit(j + 1), q++)
                data2[p][q] = atoms[i].distance(atoms[j]);
            return mp.addXAFF(data2);
          }
          if (isMinMax) {
            float[] data = new float[bs.cardinality()];
            for (int i = bs.nextSetBit(0), p = 0; i >= 0; i = bs
                .nextSetBit(i + 1))
              data[p++] = ((Float) eval.getBitsetProperty(bs2, intValue,
                  atoms[i], plane2, x1.value, null, false, x1.index, false))
                  .floatValue();
            return mp.addXAF(data);
          }
          return mp.addXObj(eval.getBitsetProperty(bs, intValue, pt2, plane2,
              x1.value, null, false, x1.index, false));
        }
      }
    }
    return mp.addXFloat(getDistance(mp, x1, x2, tok));
  }

  private float getDistance(ScriptMathProcessor mp, SV x1, SV x2, int tok) throws ScriptException {
    P3 pt1 = mp.ptValue(x1, true);
    P4 plane1 = mp.planeValue(x1);
    P3 pt2 = mp.ptValue(x2, true);
    P4 plane2 = mp.planeValue(x2);
    if (tok == T.dot) {
      if (plane1 != null && plane2 != null)
        // q1.dot(q2) assume quaternions
        return plane1.x * plane2.x + plane1.y * plane2.y + plane1.z
            * plane2.z + plane1.w * plane2.w;
      // plane.dot(point) =
      if (plane1 != null)
        pt1 = P3.new3(plane1.x, plane1.y, plane1.z);
      // point.dot(plane)
      if (plane2 != null)
        pt2 = P3.new3(plane2.x, plane2.y, plane2.z);
      return pt1.x * pt2.x + pt1.y * pt2.y + pt1.z * pt2.z;
    }

    if (plane1 == null)
      return (plane2 == null ? pt2.distance(pt1) : Measure.distanceToPlane(
          plane2, pt1));
    return Measure.distanceToPlane(plane1, pt2);
  }

  private boolean evaluateMeasure(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    int nPoints = 0;
    switch (tok) {
    case T.measure:
      // note: min/max are always in Angstroms
      // note: order is not important (other than min/max)
      // measure({a},{b},{c},{d}, min, max, format, units)
      // measure({a},{b},{c}, min, max, format, units)
      // measure({a},{b}, min, max, format, units)
      // measure({a},{b},{c},{d}, min, max, format, units)
      // measure({a} {b} "minArray") -- returns array of minimum distance values
      
      List<Object> points = new  List<Object>();
      float[] rangeMinMax = new float[] { Float.MAX_VALUE, Float.MAX_VALUE };
      String strFormat = null;
      String units = null;
      boolean isAllConnected = false;
      boolean isNotConnected = false;
      int rPt = 0;
      boolean isNull = false;
      RadiusData rd = null;
      int nBitSets = 0;
      float vdw = Float.MAX_VALUE;
      boolean asMinArray = false;
      boolean asArray = false;
      for (int i = 0; i < args.length; i++) {
        switch (args[i].tok) {
        case T.bitset:
          BS bs = (BS) args[i].value;
          if (bs.length() == 0)
            isNull = true;
          points.addLast(bs);
          nPoints++;
          nBitSets++;
          break;
        case T.point3f:
          Point3fi v = new Point3fi();
          v.setT((P3) args[i].value);
          points.addLast(v);
          nPoints++;
          break;
        case T.integer:
        case T.decimal:
          rangeMinMax[rPt++ % 2] = SV.fValue(args[i]);
          break;

        case T.string:
          String s = SV.sValue(args[i]);
          if (s.equalsIgnoreCase("vdw") || s.equalsIgnoreCase("vanderwaals"))
            vdw = (i + 1 < args.length && args[i + 1].tok == T.integer ? args[++i]
                .asInt()
                : 100) / 100f;
          else if (s.equalsIgnoreCase("notConnected"))
            isNotConnected = true;
          else if (s.equalsIgnoreCase("connected"))
            isAllConnected = true;
          else if (s.equalsIgnoreCase("minArray"))
            asMinArray = (nBitSets >= 1);
          else if (s.equalsIgnoreCase("asArray"))
            asArray = (nBitSets >= 1);
          else if (PT.isOneOf(s.toLowerCase(),
              ";nm;nanometers;pm;picometers;angstroms;ang;au;") || s.endsWith("hz"))
            units = s.toLowerCase();
          else
            strFormat = nPoints + ":" + s;
          break;
        default:
          return false;
        }
      }
      if (nPoints < 2 || nPoints > 4 || rPt > 2 || isNotConnected
          && isAllConnected)
        return false;
      if (isNull)
        return mp.addXStr("");
      if (vdw != Float.MAX_VALUE && (nBitSets != 2 || nPoints != 2))
        return mp.addXStr("");
      rd = (vdw == Float.MAX_VALUE ? new RadiusData(rangeMinMax, 0, null, null)
          : new RadiusData(null, vdw, EnumType.FACTOR, EnumVdw.AUTO));
      return mp.addXObj((newMeasurementData(null, points)).set(0, null, rd, strFormat, units, null, isAllConnected,
          isNotConnected, null, true, 0, (short) 0, null).getMeasurements(asArray, asMinArray));
    case T.angle:
      if ((nPoints = args.length) != 3 && nPoints != 4)
        return false;
      break;
    default: // distance
      if ((nPoints = args.length) != 2)
        return false;
    }
    P3[] pts = new P3[nPoints];
    for (int i = 0; i < nPoints; i++)
      pts[i] = mp.ptValue(args[i], true);
    switch (nPoints) {
    case 2:
      return mp.addXFloat(pts[0].distance(pts[1]));
    case 3:
      return mp.addXFloat(Measure.computeAngleABC(pts[0], pts[1], pts[2], true));
    case 4:
      return mp.addXFloat(Measure.computeTorsion(pts[0], pts[1], pts[2], pts[3],
          true));
    }
    return false;
  }

  private MeasurementData newMeasurementData(String id, List<Object> points) {
    return ((MeasurementData) Interface
        .getOptionInterface("modelset.MeasurementData")).init(id, viewer,
        points);
  }

  private boolean evaluateUserFunction(ScriptMathProcessor mp, String name, SV[] args,
                                       int tok, boolean isSelector)
      throws ScriptException {
    SV x1 = null;
    if (isSelector) {
      x1 = mp.getX();
      if (x1.tok != T.bitset)
        return false;
    }
    mp.wasX = false;
    List<SV> params = new  List<SV>();
    for (int i = 0; i < args.length; i++) {
      params.addLast(args[i]);
    }
    if (isSelector) {
      return mp.addXObj(eval.getBitsetProperty(SV.bsSelectVar(x1), tok,
          null, null, x1.value, new Object[] { name, params }, false, x1.index,
          false));
    }
    SV var = eval.runFunctionRet(null, name, params, null, true, true, false);
    return (var == null ? false : mp.addXVar(var));
  }

  private boolean evaluateFind(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    if (args.length == 0)
      return false;

    // {*}.find("MF")
    // {*}.find("SEQENCE")
    // {*}.find("SMARTS", "CCCC")
    // "CCCC".find("SMARTS", "CC")
    // "CCCC".find("SMILES", "MF")
    // {2.1}.find("CCCC",{1.1}) // find pattern "CCCC" in {2.1} with conformation given by {1.1}
    // {*}.find("ccCCN","BONDS")

    SV x1 = mp.getX();
    String sFind = SV.sValue(args[0]);
    String flags = (args.length > 1 && args[1].tok != T.on
        && args[1].tok != T.off ? SV.sValue(args[1]) : "");
    boolean isSequence = sFind.equalsIgnoreCase("SEQUENCE");
    boolean isSmiles = sFind.equalsIgnoreCase("SMILES");
    boolean isSearch = sFind.equalsIgnoreCase("SMARTS");
    boolean isMF = sFind.equalsIgnoreCase("MF");
    if (isSmiles || isSearch || x1.tok == T.bitset) {
      int iPt = (isSmiles || isSearch ? 2 : 1);
      BS bs2 = (iPt < args.length && args[iPt].tok == T.bitset ? (BS) args[iPt++].value
          : null);
      boolean asBonds = ("bonds".equalsIgnoreCase(SV
          .sValue(args[args.length - 1])));
      boolean isAll = (asBonds || args[args.length - 1].tok == T.on);
      Object ret = null;
      switch (x1.tok) {
      case T.string:
        String smiles = SV.sValue(x1);
        if (bs2 != null)
          return false;
        if (flags.equalsIgnoreCase("mf")) {
          ret = viewer.getSmilesMatcher().getMolecularFormula(smiles, isSearch);
          if (ret == null)
            eval.evalError(viewer.getSmilesMatcher().getLastException(), null);
        } else {
          ret = getSmilesMatches(flags, smiles, null, null, isSearch,
              !isAll);
        }
        break;
      case T.bitset:
        if (isMF)
          return mp.addXStr(JmolMolecule.getMolecularFormula(
              viewer.getModelSet().atoms, (BS) x1.value, false));
        if (isSequence)
          return mp.addXStr(viewer.getSmiles(-1, -1, (BS) x1.value, true, isAll,
              isAll, false));
        if (isSmiles || isSearch)
          sFind = flags;
        BS bsMatch3D = bs2;
        if (asBonds) {
          // this will return a single match
          int[][] map = viewer.getSmilesMatcher().getCorrelationMaps(sFind,
              viewer.modelSet.atoms, viewer.getAtomCount(), (BS) x1.value,
              !isSmiles, true);
          ret = (map.length > 0 ? viewer.getDihedralMap(map[0]) : new int[0]);
        } else {
          ret = getSmilesMatches(sFind, null, (BS) x1.value, bsMatch3D,
              !isSmiles, !isAll);
        }
        break;
      }
      if (ret == null)
        eval.error(ScriptEvaluator.ERROR_invalidArgument);
      return mp.addXObj(ret);
    }
    boolean isReverse = (flags.indexOf("v") >= 0);
    boolean isCaseInsensitive = (flags.indexOf("i") >= 0);
    boolean asMatch = (flags.indexOf("m") >= 0);
    boolean isList = (x1.tok == T.varray);
    boolean isPattern = (args.length == 2);
    if (isList || isPattern) {
      JmolPatternMatcher pm = getPatternMatcher();
      Pattern pattern = null;
      try {
        pattern = pm.compile(sFind, isCaseInsensitive);
      } catch (Exception e) {
        eval.evalError(e.toString(), null);
      }
      String[] list = SV.listValue(x1);
      if (Logger.debugging)
        Logger.debug("finding " + sFind);
      BS bs = new BS();
      int ipt = 0;
      int n = 0;
      Matcher matcher = null;
      List<String> v = (asMatch ? new List<String>() : null);
      for (int i = 0; i < list.length; i++) {
        String what = list[i];
        matcher = pattern.matcher(what);
        boolean isMatch = matcher.find();
        if (asMatch && isMatch || !asMatch && isMatch == !isReverse) {
          n++;
          ipt = i;
          bs.set(i);
          if (asMatch)
            v.addLast(isReverse ? what.substring(0, matcher.start())
                + what.substring(matcher.end()) : matcher.group());
        }
      }
      if (!isList) {
        return (asMatch ? mp.addXStr(v.size() == 1 ? (String) v.get(0) : "")
            : isReverse ? mp.addXBool(n == 1) : asMatch ? mp.addXStr(n == 0 ? ""
                : matcher.group()) : mp.addXInt(n == 0 ? 0 : matcher.start() + 1));
      }
      if (n == 1)
        return mp.addXStr(asMatch ? (String) v.get(0) : list[ipt]);
      String[] listNew = new String[n];
      if (n > 0)
        for (int i = list.length; --i >= 0;)
          if (bs.get(i)) {
            --n;
            listNew[n] = (asMatch ? (String) v.get(n) : list[i]);
          }
      return mp.addXAS(listNew);
    }
    return mp.addXInt(SV.sValue(x1).indexOf(sFind) + 1);
  }

  private JmolPatternMatcher pm;
  private JmolPatternMatcher getPatternMatcher() {
    return (pm == null ? pm = (JmolPatternMatcher) Interface.getOptionInterface("util.PatternMatcher") : pm);
  }

  private boolean evaluateGetProperty(ScriptMathProcessor mp, SV[] args, boolean isAtomProperty) 
    throws ScriptException {
    int pt = 0;
    String propertyName = (args.length > pt ? SV.sValue(args[pt++])
        .toLowerCase() : "");
    boolean isJSON = false;
    if (propertyName.equals("json") && args.length > pt) {
      isJSON = true;
      propertyName = SV.sValue(args[pt++]);
    }
      
    if (propertyName.startsWith("$")) {
      // TODO
    }
    if (isAtomProperty && !propertyName.equalsIgnoreCase("bondInfo"))
      propertyName = "atomInfo." + propertyName;      
    Object propertyValue = "";
    if (propertyName.equalsIgnoreCase("fileContents") && args.length > 2) {
      String s = SV.sValue(args[1]);
      for (int i = 2; i < args.length; i++)
        s += "|" + SV.sValue(args[i]);
      propertyValue = s;
      pt = args.length;
    } else if (args.length > pt) {
      switch (args[pt].tok) {
      case T.bitset:
        propertyValue = SV.bsSelectVar(args[pt++]);
        if (propertyName.equalsIgnoreCase("bondInfo") &&  args.length > pt && args[pt].tok == T.bitset)
          propertyValue = new BS[] { (BS) propertyValue , SV.bsSelectVar(args[pt]) };
        break;
      case T.string:
        if (viewer.checkPropertyParameter(propertyName))
          propertyValue = args[pt++].value;
        break;
      }
    }
    if (isAtomProperty) {
      SV x = mp.getX();
      if (x.tok != T.bitset)
        return false;
      int iAtom = SV.bsSelectVar(x).nextSetBit(0);
      if (iAtom < 0)
        return mp.addXStr("");
      propertyValue = BSUtil.newAndSetBit(iAtom);
    }
    Object property = viewer.getProperty(null, propertyName, propertyValue);
    if (pt < args.length)
      property = viewer.extractProperty(property, args, pt);
    if (isAtomProperty && property instanceof List)
      property = (((List<?>) property).size() > 0 ? ((List<?>) property).get(0) : "");
    return mp.addXObj(isJSON ? PT.toJSON(null, property) : 
      SV.isVariableType(property) ? property : Escape
        .toReadable(propertyName, property));
  }

  private boolean evaluatePlane(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    if (tok == T.hkl && args.length != 3 
        || tok == T.intersection && args.length != 2 && args.length != 3 
        || args.length == 0 || args.length > 4)
      return false;
    P3 pt1, pt2, pt3;
    P4 plane;
    V3 norm, vTemp;

    switch (args.length) {
    case 1:
      if (args[0].tok == T.bitset) {
        BS bs = SV.getBitSet(args[0], false);
        if (bs.cardinality() == 3) {
          List<P3> pts = viewer.getAtomPointVector(bs);
          V3 vNorm = new V3();
          V3 vAB = new V3();
          V3 vAC = new V3();
          plane = new P4();
          Measure.getPlaneThroughPoints(pts.get(0), pts.get(1), pts.get(2), vNorm , vAB, vAC, plane);
          return mp.addXPt4(plane);
        }
      }
      Object pt = Escape.uP(SV.sValue(args[0]));
      if (pt instanceof P4)
        return mp.addXPt4((P4)pt);
      return mp.addXStr("" + pt);
    case 2:
      if (tok == T.intersection) {
        // intersection(plane, plane)
        // intersection(point, plane)
        if (args[1].tok != T.point4f)
          return false;
        pt3 = new P3();
        norm = new V3();
        vTemp = new V3();

        plane = (P4) args[1].value;
        if (args[0].tok == T.point4f) {
          List<Object> list = Measure.getIntersectionPP((P4) args[0].value,
              plane);
          if (list == null)
            return mp.addXStr("");
          return mp.addXList(list);
        }
        pt2 = mp.ptValue(args[0], false);
        if (pt2 == null)
          return mp.addXStr("");
        return mp.addXPt(Measure.getIntersection(pt2, null, plane, pt3, norm, vTemp));
      }
      //$FALL-THROUGH$
    case 3:
    case 4:
      switch (tok) {
      case T.hkl:
        // hkl(i,j,k)
        return mp.addXPt4(eval.getHklPlane(P3.new3(
            SV.fValue(args[0]), SV.fValue(args[1]),
            SV.fValue(args[2]))));
      case T.intersection:
        pt1 = mp.ptValue(args[0], false);
        pt2 = mp.ptValue(args[1], false);
        if (pt1 == null || pt2 == null)
          return mp.addXStr("");
        V3 vLine = V3.newV(pt2);
        vLine.normalize();
        if (args[2].tok == T.point4f) {
          // intersection(ptLine, vLine, plane)
          pt3 = new P3();
          norm = new V3();
          vTemp = new V3();
          pt1 = Measure.getIntersection(pt1, vLine, (P4) args[2].value, pt3, norm, vTemp);
          if (pt1 == null)
            return mp.addXStr("");
          return mp.addXPt(pt1);
        }
        pt3 = mp.ptValue(args[2], false);
        if (pt3 == null)
          return mp.addXStr("");
        // interesection(ptLine, vLine, pt2); // IE intersection of plane perp to line through pt2
        V3 v = new V3();
        Measure.projectOntoAxis(pt3, pt1, vLine, v);
        return mp.addXPt(pt3);
      }
      switch (args[0].tok) {
      case T.integer:
      case T.decimal:
        if (args.length == 3) {
          // plane(r theta phi)
          float r = SV.fValue(args[0]); 
          float theta = SV.fValue(args[1]);  // longitude, azimuthal, in xy plane
          float phi = SV.fValue(args[2]);    // 90 - latitude, polar, from z
          // rotate {0 0 r} about y axis need to stay in the x-z plane
          norm = V3.new3(0, 0, 1);
          pt2 = P3.new3(0, 1, 0);
          Quaternion q = Quaternion.newVA(pt2, phi);
          q.getMatrix().transform(norm);
          // rotate that vector around z
          pt2.set(0, 0, 1);
          q = Quaternion.newVA(pt2, theta);
          q.getMatrix().transform(norm);
          pt2.setT(norm);
          pt2.scale(r);
          plane = new P4();
          Measure.getPlaneThroughPoint(pt2, norm, plane);
          return mp.addXPt4(plane);          
        }
        break;
      case T.bitset:
      case T.point3f:
        pt1 = mp.ptValue(args[0], false);
        pt2 = mp.ptValue(args[1], false);
        if (pt2 == null)
          return false;
        pt3 = (args.length > 2
            && (args[2].tok == T.bitset || args[2].tok == T.point3f) ? mp.ptValue(
            args[2], false)
            : null);
        norm = V3.newV(pt2);
        if (pt3 == null) {
          plane = new P4();
          if (args.length == 2 || !args[2].asBoolean()) {
            // plane(<point1>,<point2>) or 
            // plane(<point1>,<point2>,false)
            pt3 = P3.newP(pt1);
            pt3.add(pt2);
            pt3.scale(0.5f);
            norm.sub(pt1);
            norm.normalize();
          } else {
            // plane(<point1>,<vLine>,true)
            pt3 = pt1;
          }
          Measure.getPlaneThroughPoint(pt3, norm, plane);
          return mp.addXPt4(plane);
        }
        // plane(<point1>,<point2>,<point3>)
        // plane(<point1>,<point2>,<point3>,<pointref>)
        V3 vAB = new V3();
        V3 vAC = new V3();
        float nd = Measure.getDirectedNormalThroughPoints(pt1, pt2, pt3,
            (args.length == 4 ? mp.ptValue(args[3], true) : null), norm, vAB, vAC);
        return mp.addXPt4(P4.new4(norm.x, norm.y, norm.z, nd));
      }
    }
    if (args.length != 4)
      return false;
    float x = SV.fValue(args[0]);
    float y = SV.fValue(args[1]);
    float z = SV.fValue(args[2]);
    float w = SV.fValue(args[3]);
    return mp.addXPt4(P4.new4(x, y, z, w));
  }

  private boolean evaluatePoint(ScriptMathProcessor mp, SV[] args) {
    if (args.length != 1 && args.length != 3 && args.length != 4)
      return false;
    switch (args.length) {
    case 1:
      if (args[0].tok == T.decimal || args[0].tok == T.integer)
        return mp.addXInt(args[0].asInt());
      String s = SV.sValue(args[0]);
      if (args[0].tok == T.varray)
        s = "{" + s + "}";
      Object pt = Escape.uP(s);
      if (pt instanceof P3)
        return mp.addXPt((P3) pt);
      return mp.addXStr("" + pt);
    case 3:
      return mp.addXPt(P3.new3(args[0].asFloat(), args[1].asFloat(), args[2].asFloat()));
    case 4:
      return mp.addXPt4(P4.new4(args[0].asFloat(), args[1].asFloat(), args[2].asFloat(), args[3].asFloat()));
    }
    return false;
  }

  private boolean evaluatePrompt(ScriptMathProcessor mp, SV[] args) {
    //x = prompt("testing")
    //x = prompt("testing","defaultInput")
    //x = prompt("testing","yes|no|cancel", true)
    //x = prompt("testing",["button1", "button2", "button3"])

    if (args.length != 1 && args.length != 2 && args.length != 3)
      return false;
    String label = SV.sValue(args[0]);
    String[] buttonArray = (args.length > 1 && args[1].tok == T.varray ?
        SV.listValue(args[1]) : null);
    boolean asButtons = (buttonArray != null || args.length == 1 || args.length == 3 && args[2].asBoolean());
    String input = (buttonArray != null ? null : args.length >= 2 ? SV.sValue(args[1]) : "OK");
    String s = "" + viewer.prompt(label, input, buttonArray, asButtons);
    return (asButtons && buttonArray != null ? mp.addXInt(Integer.parseInt(s) + 1) : mp.addXStr(s));
  }

  private boolean evaluateReplace(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    if (args.length != 2)
      return false;
    SV x = mp.getX();
    String sFind = SV.sValue(args[0]);
    String sReplace = SV.sValue(args[1]);
    String s = (x.tok == T.varray ? null : SV.sValue(x));
    if (s != null)
      return mp.addXStr(PT.simpleReplace(s, sFind, sReplace));
    String[] list = SV.listValue(x);
    for (int i = list.length; --i >= 0;)
      list[i] = PT.simpleReplace(list[i], sFind, sReplace);
    return mp.addXAS(list);
  }

  private boolean evaluateString(ScriptMathProcessor mp, int tok, SV[] args)
      throws ScriptException {
    if (args.length > 1)
      return false;
    SV x = mp.getX();
    if (x.tok == T.varray) {
      mp.addXVar(x);
      return evaluateList(mp, tok, args);
    }
    String s = (tok == T.split && x.tok == T.bitset
        || tok == T.trim && x.tok == T.varray ? null : SV
        .sValue(x));
    String sArg = (args.length == 1 ? SV.sValue(args[0])
        : tok == T.trim ? "" : "\n");
    switch (tok) {
    case T.split:
      if (x.tok == T.bitset) {
        BS bsSelected = SV.bsSelectVar(x);
        sArg = "\n";
        int modelCount = viewer.getModelCount();
        s = "";
        for (int i = 0; i < modelCount; i++) {
          s += (i == 0 ? "" : "\n");
          BS bs = viewer.getModelUndeletedAtomsBitSet(i);
          bs.and(bsSelected);
          s += Escape.eBS(bs);
        }
      }
      return mp.addXAS(PT.split(s, sArg));
    case T.join:
      if (s.length() > 0 && s.charAt(s.length() - 1) == '\n')
        s = s.substring(0, s.length() - 1);
      return mp.addXStr(PT.simpleReplace(s, "\n", sArg));
    case T.trim:
      if (s != null)
        return mp.addXStr(PT.trim(s, sArg));      
      String[] list = SV.listValue(x);
      for (int i = list.length; --i >= 0;)
        list[i] = PT.trim(list[i], sArg);
      return mp.addXAS(list);
    }
    return mp.addXStr("");
  }

  /**
   * array.add(x)
   * array.add(sep, x)
   * array.sub(x)
   * array.mul(x)
   * array.mul3(x)
   * array.div(x)
   * array.push()
   * array.pop()
   * 
   * @param mp
   * @param tok
   * @param args
   * @return T/F
   * @throws ScriptException
   */
  private boolean evaluateList(ScriptMathProcessor mp, int tok, SV[] args)
      throws ScriptException {
    int len = args.length;
    SV x1 = mp.getX();
    SV x2;
    switch (tok) {
    case T.push:
      return (len == 1 && mp.addXVar(x1.pushPop(args[0])));
    case T.pop:
      return (len == 0 && mp.addXVar(x1.pushPop(null)));
    case T.add:
      if (len != 1 && len != 2)
        return false;
      break;
    default:
      if (len != 1)
        return false;
    }
    String[] sList1 = null, sList2 = null, sList3 = null;

    if (len == 2) {
      // [xxxx].add("\t", [...])
      int itab = (args[0].tok == T.string ? 0 : 1);
      String tab = SV.sValue(args[itab]);
      sList1 = (x1.tok == T.varray ? SV.listValue(x1) : PT.split(SV.sValue(x1),
          "\n"));
      x2 = args[1 - itab];
      sList2 = (x2.tok == T.varray ? SV.listValue(x2) : PT.split(SV.sValue(x2),
          "\n"));
      sList3 = new String[len = Math.max(sList1.length, sList2.length)];
      for (int i = 0; i < len; i++)
        sList3[i] = (i >= sList1.length ? "" : sList1[i]) + tab
            + (i >= sList2.length ? "" : sList2[i]);
      return mp.addXAS(sList3);
    }
    x2 = (len == 0 ? SV.newV(T.all, "all") : args[0]);
    boolean isAll = (x2.tok == T.all);
    if (x1.tok != T.varray && x1.tok != T.string)
      return mp.binaryOp(opTokenFor(tok), x1, x2);
    boolean isScalar1 = SV.isScalar(x1);
    boolean isScalar2 = SV.isScalar(x2);

    float[] list1 = null;
    float[] list2 = null;
    List<SV> alist1 = x1.getList();
    List<SV> alist2 = x2.getList();

    if (x1.tok == T.varray) {
      len = alist1.size();
    } else if (isScalar1) {
      len = Integer.MAX_VALUE;
    } else {
      sList1 = (PT.split(SV.sValue(x1), "\n"));
      list1 = new float[len = sList1.length];
      PT.parseFloatArrayData(sList1, list1);
    }
    if (isAll) {
      float sum = 0f;
      if (x1.tok == T.varray) {
        for (int i = len; --i >= 0;)
          sum += SV.fValue(alist1.get(i));
      } else if (!isScalar1) {
        for (int i = len; --i >= 0;)
          sum += list1[i];
      }
      return mp.addXFloat(sum);
    }
    if (tok == T.join && x2.tok == T.string) {
      SB sb = new SB();
      if (isScalar1)
        sb.append(SV.sValue(x1));
      else
        for (int i = 0; i < len; i++)
          sb.appendO(i > 0 ? x2.value : null).append(SV.sValue(alist1.get(i)));
      return mp.addXStr(sb.toString());
    }

    SV scalar = null;
    if (isScalar2) {
      scalar = x2;
    } else if (x2.tok == T.varray) {
      len = Math.min(len, alist2.size());
    } else {
      sList2 = PT.split(SV.sValue(x2), "\n");
      list2 = new float[sList2.length];
      PT.parseFloatArrayData(sList2, list2);
      len = Math.min(len, list2.length);
    }

    T token = opTokenFor(tok);

    SV[] olist = new SV[len];

    SV a = (isScalar1 ? x1 : null);
    SV b;
    for (int i = 0; i < len; i++) {
      if (isScalar2)
        b = scalar;
      else if (x2.tok == T.varray)
        b = alist2.get(i);
      else if (Float.isNaN(list2[i]))
        b = SV.getVariable(SV.unescapePointOrBitsetAsVariable(sList2[i]));
      else
        b = SV.newV(T.decimal, Float.valueOf(list2[i]));
      if (!isScalar1) {
        if (x1.tok == T.varray)
          a = alist1.get(i);
        else if (Float.isNaN(list1[i]))
          a = SV.getVariable(SV.unescapePointOrBitsetAsVariable(sList1[i]));
        else
          a = SV.newV(T.decimal, Float.valueOf(list1[i]));
      }
      if (tok == T.join) {
        if (a.tok != T.varray) {
          List<SV> l = new List<SV>();
          l.addLast(a);
          a = SV.getVariableList(l);
        }
      }
      if (!mp.binaryOp(token, a, b))
        return false;
      olist[i] = mp.getX();
    }
    return mp.addXAV(olist);
  }

  private T opTokenFor(int tok) {
    switch (tok) {
    case T.add:
    case T.join:
      return T.tokenPlus;
    case T.sub:
      return T.tokenMinus;
    case T.mul:
      return T.tokenTimes;
    case T.mul3:
      return T.tokenMul3;
    case T.div:
      return T.tokenDivide;
    }
    return null;
  }

  private boolean evaluateRowCol(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    if (args.length != 1)
      return false;
    int n = args[0].asInt() - 1;
    SV x1 = mp.getX();
    float[] f;
    switch (x1.tok) {
    case T.matrix3f:
      if (n < 0 || n > 2)
        return false;
      M3 m = (M3) x1.value;
      switch (tok) {
      case T.row:
        f = new float[3];
        m.getRow(n, f);
        return mp.addXAF(f);
      case T.col:
      default:
        f = new float[3];
        m.getColumn(n, f);
        return mp.addXAF(f);
      }
    case T.matrix4f:
      if (n < 0 || n > 2)
        return false;
      M4 m4 = (M4) x1.value;
      switch (tok) {
      case T.row:
        f = new float[4];
        m4.getRow(n, f);
        return mp.addXAF(f);
      case T.col:
      default:
        f = new float[4];
        m4.getColumn(n, f);
        return mp.addXAF(f);
      }
    }
    return false;

  }

  private boolean evaluateArray(ScriptMathProcessor mp, 
                                SV[] args, boolean allowMatrix) {
    int len = args.length;
    if (allowMatrix && (len == 4 || len == 3)) {
      boolean isMatrix = true;
      for (int i = 0; i < len && isMatrix; i++)
        isMatrix = (args[i].tok == T.varray && args[i].getList().size() == len);
      if (isMatrix) {
        float[] m = new float[len * len];
        int pt = 0;
        for (int i = 0; i < len && isMatrix; i++) {
          List<SV> list = args[i].getList();
          for (int j = 0; j < len; j++) {
            float x = SV.fValue(list.get(j));
            if (Float.isNaN(x)) {
              isMatrix = false;
              break;
            }
            m[pt++] = x;
          }
        }
        if (isMatrix) {
          if (len == 3)
            return mp.addXM3(M3.newA(m));
          return mp.addXM4(M4.newA(m));
        }
      }
    }
    SV[] a = new SV[args.length];
    for (int i = a.length; --i >= 0;)
      a[i] = SV.newT(args[i]);
    return mp.addXAV(a);
  }

  private boolean evaluateMath(ScriptMathProcessor mp, SV[] args, int tok) {
    if (tok == T.now) {
      if (args.length == 1 && args[0].tok == T.string)
        return mp.addXStr((new Date()) + "\t" + SV.sValue(args[0]));
      return mp.addXInt(((int) System.currentTimeMillis() & 0x7FFFFFFF)
          - (args.length == 0 ? 0 : args[0].asInt()));
    }
    if (args.length != 1)
      return false;
    if (tok == T.abs) {
      if (args[0].tok == T.integer)
        return mp.addXInt(Math.abs(args[0].asInt()));
      return mp.addXFloat(Math.abs(args[0].asFloat()));
    }
    double x = SV.fValue(args[0]);
    switch (tok) {
    case T.acos:
      return mp.addXFloat((float) (Math.acos(x) * 180 / Math.PI));
    case T.cos:
      return mp.addXFloat((float) Math.cos(x * Math.PI / 180));
    case T.sin:
      return mp.addXFloat((float) Math.sin(x * Math.PI / 180));
    case T.sqrt:
      return mp.addXFloat((float) Math.sqrt(x));
    }
    return false;
  }

  private boolean evaluateQuaternion(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    P3 pt0 = null;
    // quaternion([quaternion array]) // mean
    // quaternion([quaternion array1], [quaternion array2], "relative") //
    // difference array
    // quaternion(matrix)
    // quaternion({atom1}) // quaternion (1st if array)
    // quaternion({atomSet}, nMax) // nMax quaternions, by group; 0 for all
    // quaternion({atom1}, {atom2}) // difference 
    // quaternion({atomSet1}, {atomset2}, nMax) // difference array, by group; 0 for all
    // quaternion(vector, theta)
    // quaternion(q0, q1, q2, q3)
    // quaternion("{x, y, z, w"})
    // quaternion("best")
    // quaternion(center, X, XY)
    // quaternion(mcol1, mcol2)
    // quaternion(q, "id", center) // draw code
    // axisangle(vector, theta)
    // axisangle(x, y, z, theta)
    // axisangle("{x, y, z, theta"})
    int nArgs = args.length;
    int nMax = Integer.MAX_VALUE;
    boolean isRelative = false;
    if (tok == T.quaternion) {
      if (nArgs > 1 && args[nArgs - 1].tok == T.string
          && ((String) args[nArgs - 1].value).equalsIgnoreCase("relative")) {
        nArgs--;
        isRelative = true;
      }
      if (nArgs > 1 && args[nArgs - 1].tok == T.integer
          && args[0].tok == T.bitset) {
        nMax = args[nArgs - 1].asInt();
        if (nMax <= 0)
          nMax = Integer.MAX_VALUE - 1;
        nArgs--;
      }
    }

    switch (nArgs) {
    case 0:
    case 1:
    case 4:
      break;
    case 2:
      if (tok == T.quaternion) {
        if (args[0].tok == T.varray && (args[1].tok == T.varray || args[1].tok == T.on))
          break;
        if (args[0].tok == T.bitset
            && (args[1].tok == T.integer || args[1].tok == T.bitset))
          break;
      }
      if ((pt0 = mp.ptValue(args[0], false)) == null || tok != T.quaternion
          && args[1].tok == T.point3f)
        return false;
      break;
    case 3:
      if (tok != T.quaternion)
        return false;
      if (args[0].tok == T.point4f) {
        if (args[2].tok != T.point3f && args[2].tok != T.bitset)
          return false;
        break;
      }
      for (int i = 0; i < 3; i++)
        if (args[i].tok != T.point3f && args[i].tok != T.bitset)
          return false;
      break;
    default:
      return false;
    }
    Quaternion q = null;
    Quaternion[] qs = null;
    P4 p4 = null;
    switch (nArgs) {
    case 0:
      return mp.addXPt4(Quaternion.newQ(viewer.getRotationQuaternion())
          .toPoint4f());
    case 1:
    default:
      if (tok == T.quaternion && args[0].tok == T.varray) {
        Quaternion[] data1 = getQuaternionArray(args[0].getList(), T.list);
        Object mean = Quaternion.sphereMean(data1, null, 0.0001f);
        q = (mean instanceof Quaternion ? (Quaternion) mean : null);
        break;
      } else if (tok == T.quaternion && args[0].tok == T.bitset) {
        qs = viewer.getAtomGroupQuaternions((BS) args[0].value, nMax);
      } else if (args[0].tok == T.matrix3f) {
        q = Quaternion.newM((M3) args[0].value);
      } else if (args[0].tok == T.point4f) {
        p4 = (P4) args[0].value;
      } else {
        String s = SV.sValue(args[0]);
        Object v = Escape.uP(s.equalsIgnoreCase("best") ? viewer
            .getOrientationText(T.best, null) : s);
        if (!(v instanceof P4))
          return false;
        p4 = (P4) v;
      }
      if (tok == T.axisangle)
        q = Quaternion.newVA(P3.new3(p4.x, p4.y, p4.z), p4.w);
      break;
    case 2:
      if (tok == T.quaternion) {
        if (args[0].tok == T.varray && args[1].tok == T.varray) {
          Quaternion[] data1 = getQuaternionArray(args[0].getList(), T.list);
          Quaternion[] data2 = getQuaternionArray(args[1].getList(), T.list);
          qs = Quaternion.div(data2, data1, nMax, isRelative);
          break;
        }
        if (args[0].tok == T.varray  && args[1].tok == T.on) {
          Quaternion[] data1 = getQuaternionArray(args[0].getList(), T.list);
          float[] stddev = new float[1];
          Quaternion.sphereMean(data1, stddev, 0.0001f);
          return mp.addXFloat(stddev[0]);
        }
        if (args[0].tok == T.bitset && args[1].tok == T.bitset) {
          Quaternion[] data1 = viewer.getAtomGroupQuaternions(
              (BS) args[0].value, Integer.MAX_VALUE);
          Quaternion[] data2 = viewer.getAtomGroupQuaternions(
              (BS) args[1].value, Integer.MAX_VALUE);
          qs = Quaternion.div(data2, data1, nMax, isRelative);
          break;
        }
      }
      P3 pt1 = mp.ptValue(args[1], false);
      p4 = mp.planeValue(args[0]);
      if (pt1 != null)
        q = Quaternion.getQuaternionFrame(P3.new3(0, 0, 0), pt0, pt1);
      else
        q = Quaternion.newVA(pt0, SV.fValue(args[1]));
      break;
    case 3:
      if (args[0].tok == T.point4f) {
        P3 pt = (args[2].tok == T.point3f ? (P3) args[2].value : viewer
            .getAtomSetCenter((BS) args[2].value));
        return mp.addXStr((Quaternion.newP4((P4) args[0].value)).draw("q",
            SV.sValue(args[1]), pt, 1f));
      }
      P3[] pts = new P3[3];
      for (int i = 0; i < 3; i++)
        pts[i] = (args[i].tok == T.point3f ? (P3) args[i].value : viewer
            .getAtomSetCenter((BS) args[i].value));
      q = Quaternion.getQuaternionFrame(pts[0], pts[1], pts[2]);
      break;
    case 4:
      if (tok == T.quaternion)
        p4 = P4.new4(SV.fValue(args[1]), SV.fValue(args[2]),
            SV.fValue(args[3]), SV.fValue(args[0]));
      else
        q = Quaternion
            .newVA(
                P3.new3(SV.fValue(args[0]), SV.fValue(args[1]),
                    SV.fValue(args[2])), SV.fValue(args[3]));
      break;
    }
    if (qs != null) {
      if (nMax != Integer.MAX_VALUE) {
        List<P4> list = new List<P4>();
        for (int i = 0; i < qs.length; i++)
          list.addLast(qs[i].toPoint4f());
        return mp.addXList(list);
      }
      q = (qs.length > 0 ? qs[0] : null);
    }
    return mp.addXPt4((q == null ? Quaternion.newP4(p4) : q).toPoint4f());
  }

  private boolean evaluateRandom(ScriptMathProcessor mp, SV[] args) {
    if (args.length > 2)
      return false;
    float lower = (args.length < 2 ? 0 : SV.fValue(args[0]));
    float range = (args.length == 0 ? 1 : SV
        .fValue(args[args.length - 1]));
    range -= lower;
    return mp.addXFloat((float) (Math.random() * range) + lower);
  }

  private boolean evaluateCross(ScriptMathProcessor mp, SV[] args) {
    if (args.length != 2)
      return false;
    SV x1 = args[0];
    SV x2 = args[1];
    if (x1.tok != T.point3f || x2.tok != T.point3f)
      return false;
    V3 a = V3.newV((P3) x1.value);
    V3 b = V3.newV((P3) x2.value);
    a.cross(a, b);
    return mp.addXPt(P3.newP(a));
  }

  private boolean evaluateLoad(ScriptMathProcessor mp, SV[] args, int tok) throws ScriptException {
    if (args.length > 2 || args.length < 1)
      return false;
    String file = SV.sValue(args[0]);
    file = file.replace('\\', '/');
    int nBytesMax = (args.length == 2 ? args[1].asInt() : -1);
    if (viewer.isJS && file.startsWith("?")) {
      if (tok == T.file)
        return mp.addXStr("");
      file = eval.loadFileAsync("load()_", file, mp.oPt, true);
      // A ScriptInterrupt will be thrown, and an asynchronous
      // file load will initiate, which will return to the script 
      // at this command when the load operation has completed.
      // Note that we need to have just a simple command here.
      // The evaluation will be repeated up to this point, so for example,
      // x = (i++) + load("?") would increment i twice.
    }
    return mp.addXStr(tok == T.load ? viewer.getFileAsString4(file, nBytesMax,
        false, false, true) : viewer.getFilePath(file, false));
  }

  private boolean evaluateWrite(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    if (args.length == 0)
      return false;
    return mp.addXStr(write(args));
  }

  private boolean evaluateScript(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    if (tok == T.javascript && args.length != 1 || args.length == 0
        || args.length > 2)
      return false;
    String s = SV.sValue(args[0]);
    SB sb = new SB();
    switch (tok) {
    case T.script:
      String appID = (args.length == 2 ? SV.sValue(args[1]) : ".");
      // options include * > . or an appletID with or without "jmolApplet"
      if (!appID.equals("."))
        sb.append(viewer.jsEval(appID + "\1" + s));
      if (appID.equals(".") || appID.equals("*"))
        eval.runScriptBuffer(s, sb);
      break;
    case T.javascript:
      sb.append(viewer.jsEval(s));
      break;
    }
    s = sb.toString();
    float f;
    return (Float.isNaN(f = PT.parseFloatStrict(s)) ? mp.addXStr(s) : s
        .indexOf(".") >= 0 ? mp.addXFloat(f) : mp.addXInt(PT.parseInt(s)));
  }

  private boolean evaluateData(ScriptMathProcessor mp, SV[] args) {

    // x = data("somedataname") # the data
    // x = data("data2d_xxxx") # 2D data (x,y paired values)
    // x = data("data2d_xxxx", iSelected) # selected row of 2D data, with <=0
    // meaning "relative to the last row"
    // x = data("property_x", "property_y") # array mp.addition of two property
    // sets
    // x = data({atomno < 10},"xyz") # (or "pdb" or "mol") coordinate data in
    // xyz, pdb, or mol format
    // x = data(someData,ptrFieldOrColumn,nBytes,firstLine) # extraction of a
    // column of data based on a field (nBytes = 0) or column range (nBytes >
    // 0)
    if (args.length != 1 && args.length != 2 && args.length != 4)
      return false;
    String selected = SV.sValue(args[0]);
    String type = (args.length == 2 ? SV.sValue(args[1]) : "");

    if (args.length == 4) {
      int iField = args[1].asInt();
      int nBytes = args[2].asInt();
      int firstLine = args[3].asInt();
      float[] f = Parser.parseFloatArrayFromMatchAndField(selected, null, 0,
          0, null, iField, nBytes, null, firstLine);
      return mp.addXStr(Escape.escapeFloatA(f, false));
    }

    if (selected.indexOf("data2d_") == 0) {
      // tab, newline separated data
      float[][] f1 = viewer.getDataFloat2D(selected);
      if (f1 == null)
        return mp.addXStr("");
      if (args.length == 2 && args[1].tok == T.integer) {
        int pt = args[1].intValue;
        if (pt < 0)
          pt += f1.length;
        if (pt >= 0 && pt < f1.length)
          return mp.addXStr(Escape.escapeFloatA(f1[pt], false));
        return mp.addXStr("");
      }
      return mp.addXStr(Escape.escapeFloatAA(f1, false));
    }

    // parallel mp.addition of float property data sets

    if (selected.indexOf("property_") == 0) {
      float[] f1 = viewer.getDataFloat(selected);
      if (f1 == null)
        return mp.addXStr("");
      float[] f2 = (type.indexOf("property_") == 0 ? viewer.getDataFloat(type)
          : null);
      if (f2 != null) {
        f1 = AU.arrayCopyF(f1, -1);
        for (int i = Math.min(f1.length, f2.length); --i >= 0;)
          f1[i] += f2[i];
      }
      return mp.addXStr(Escape.escapeFloatA(f1, false));
    }

    // some other data type -- just return it

    if (args.length == 1) {
      Object[] data = viewer.getData(selected);
      return mp.addXStr(data == null ? "" : "" + data[1]);
    }
    // {selected atoms} XYZ, MOL, PDB file format
    return mp.addXStr(viewer.getData(selected, type));
  }

  private boolean evaluateLabel(ScriptMathProcessor mp, int intValue, SV[] args)
      throws ScriptException {
    // NOT {xxx}.label
    // {xxx}.label("....")
    // {xxx}.yyy.format("...")
    // (value).format("...")
    // format("....",a,b,c...)

    SV x1 = (args.length < 2 ? mp.getX() : null);
    String format = (args.length == 0 ? "%U" : SV.sValue(args[0]));
    boolean asArray = T.tokAttr(intValue, T.minmaxmask);
    if (x1 == null)
      return mp.addXStr(SV.sprintfArray(args));
    BS bs = SV.getBitSet(x1, true);
    if (bs == null)
      return mp.addXObj(SV.sprintf(Txt.formatCheck(format), x1));
    return mp.addXObj(getBitsetIdent(bs, format,
          x1.value, true, x1.index, asArray));
  }

  private boolean evaluateWithin(ScriptMathProcessor mp, SV[] args) throws ScriptException {
    if (args.length < 1 || args.length > 5)
      return false;
    int i = args.length;
    float distance = 0;
    Object withinSpec = args[0].value;
    String withinStr = "" + withinSpec;
    int tok = args[0].tok;
    if (tok == T.string)
      tok = T.getTokFromName(withinStr);
    boolean isVdw = (tok == T.vanderwaals);
    if (isVdw) {
      distance = 100;
      withinSpec = null;
    }
    BS bs;
    boolean isWithinModelSet = false;
    boolean isWithinGroup = false;
    boolean isDistance = (isVdw || tok == T.decimal || tok == T.integer);
    RadiusData rd = null;
    switch (tok) {
    case T.branch:
      if (i != 3 || !(args[1].value instanceof BS)
          || !(args[2].value instanceof BS))
        return false;
      return mp.addXBs(viewer.getBranchBitSet(((BS) args[2].value)
          .nextSetBit(0), ((BS) args[1].value).nextSetBit(0), true));
    case T.smiles:
    case T.substructure: // same as "SMILES"
    case T.search:
      // within("smiles", "...", {bitset})
      // within("smiles", "...", {bitset})
      BS bsSelected = null;
      boolean isOK = true;
      switch (i) {
      case 2:
        break;
      case 3:
        isOK = (args[2].tok == T.bitset);
        if (isOK)
          bsSelected = (BS) args[2].value;
        break;
      default:
        isOK = false;
      }
      if (!isOK)
        eval.error(ScriptEvaluator.ERROR_invalidArgument);
      return mp.addXObj(getSmilesMatches(SV.sValue(args[1]), null, bsSelected,
          null, tok == T.search, mp.asBitSet));
    }
    if (withinSpec instanceof String) {
      if (tok == T.nada) {
        tok = T.spec_seqcode;
        if (i > 2)
          return false;
        i = 2;
      }
    } else if (isDistance) {
      if (!isVdw)
        distance = SV.fValue(args[0]);
      if (i < 2)
        return false;
      switch (tok = args[1].tok) {
      case T.on:
      case T.off:
        isWithinModelSet = args[1].asBoolean();
        i = 0;
        break;
      case T.string:
        String s = SV.sValue(args[1]);
        if (s.startsWith("$"))
          return mp.addXBs(getAtomsNearSurface(distance, s.substring(1)));
        isWithinGroup = (s.equalsIgnoreCase("group"));
        isVdw = (s.equalsIgnoreCase("vanderwaals"));
        if (isVdw) {
          withinSpec = null;
          tok = T.vanderwaals;
        } else {
          tok = T.group;
        }
        break;
      }
    } else {
      return false;
    }
    P3 pt = null;
    P4 plane = null;
    switch (i) {
    case 1:
      // within (sheet)
      // within (helix)
      // within (boundbox)
      switch (tok) {
      case T.helix:
      case T.sheet:
      case T.boundbox:
        return mp.addXBs(viewer.getAtomBits(tok, null));
      case T.basepair:
        return mp.addXBs(viewer.getAtomBits(tok, ""));
      case T.spec_seqcode:
        return mp.addXBs(viewer.getAtomBits(T.sequence, withinStr));
      }
      return false;
    case 2:
      // within (atomName, "XX,YY,ZZZ")
      switch (tok) {
      case T.spec_seqcode:
        tok = T.sequence;
        break;
      case T.atomname:
      case T.atomtype:
      case T.basepair:
      case T.sequence:
        return mp.addXBs(viewer.getAtomBits(tok, SV
            .sValue(args[args.length - 1])));
      }
      break;
    case 3:
      switch (tok) {
      case T.on:
      case T.off:
      case T.group:
      case T.vanderwaals:
      case T.plane:
      case T.hkl:
      case T.coord:
        break;
      case T.sequence:
        // within ("sequence", "CII", *.ca)
        withinStr = SV.sValue(args[2]);
        break;
      default:
        return false;
      }
      // within (distance, group, {atom collection})
      // within (distance, true|false, {atom collection})
      // within (distance, plane|hkl, [plane definition] )
      // within (distance, coord, [point or atom center] )
      break;
    }
    i = args.length - 1;
    if (args[i].value instanceof P4) {
      plane = (P4) args[i].value;
    } else if (args[i].value instanceof P3) {
      pt = (P3) args[i].value;
      if (SV.sValue(args[1]).equalsIgnoreCase("hkl"))
        plane = eval.getHklPlane(pt);
    }
    if (i > 0 && plane == null && pt == null && !(args[i].value instanceof BS))
      return false;
    if (plane != null)
      return mp.addXBs(viewer.getAtomsNearPlane(distance, plane));
    if (pt != null)
      return mp.addXBs(viewer.getAtomsNearPt(distance, pt));
    bs = (args[i].tok == T.bitset ? SV.bsSelectVar(args[i]) : null);
    if (tok == T.sequence)
      return mp.addXBs(viewer.getSequenceBits(withinStr, bs));
    if (bs == null)
      bs = new BS();
    if (!isDistance)
      return mp.addXBs(viewer.getAtomBits(tok, bs));
    if (isWithinGroup)
      return mp.addXBs(viewer.getGroupsWithin((int) distance, bs));
    if (isVdw)
      rd = new RadiusData(null, (distance > 10 ? distance / 100 : distance),
          (distance > 10 ? EnumType.FACTOR : EnumType.OFFSET), EnumVdw.AUTO);
    return mp.addXBs(viewer.getAtomsWithinRadius(distance, bs,
        isWithinModelSet, rd));
  }

  private BS getAtomsNearSurface(float distance, String surfaceId) {
    Object[] data = new Object[] { surfaceId, null, null };
    if (chk)
      return new BS();
    if (eval.getShapePropertyData(JC.SHAPE_ISOSURFACE, "getVertices", data))
      return viewer.getAtomsNearPts(distance, (P3[]) data[1], (BS) data[2]);
    data[1] = Integer.valueOf(0);
    data[2] = Integer.valueOf(-1);
    if (eval.getShapePropertyData(JC.SHAPE_DRAW, "getCenter", data))
      return viewer.getAtomsNearPt(distance, (P3) data[2]);
    return new BS();
  }
  private boolean evaluateColor(ScriptMathProcessor mp, SV[] args) {
    // color("hsl", {r g b})         # r g b in 0 to 255 scale 
    // color("rwb")                  # "" for most recently used scheme for coloring by property
    // color("rwb", min, max)        # min/max default to most recent property mapping 
    // color("rwb", min, max, value) # returns color
    // color("$isosurfaceId")        # info for a given isosurface
    // color("$isosurfaceId", value) # color for a given mapped isosurface value
    
    String colorScheme = (args.length > 0 ? SV.sValue(args[0])
        : "");
    if (colorScheme.equalsIgnoreCase("hsl") && args.length == 2) {
      P3 pt = P3.newP(SV.ptValue(args[1]));
      float[] hsl = new float[3];
      ColorEncoder.RGBtoHSL(pt.x, pt.y, pt.z, hsl);
      pt.set(hsl[0]*360, hsl[1]*100, hsl[2]*100);
      return mp.addXPt(pt);
    }
    boolean isIsosurface = colorScheme.startsWith("$");
    ColorEncoder ce = (isIsosurface ? null : viewer.getColorEncoder(colorScheme));
    if (!isIsosurface && ce == null)
      return mp.addXStr("");
    float lo = (args.length > 1 ? SV.fValue(args[1])
        : Float.MAX_VALUE);
    float hi = (args.length > 2 ? SV.fValue(args[2])
        : Float.MAX_VALUE);
    float value = (args.length > 3 ? SV.fValue(args[3])
        : Float.MAX_VALUE);
    boolean getValue = (value != Float.MAX_VALUE || lo != Float.MAX_VALUE
        && hi == Float.MAX_VALUE);
    boolean haveRange = (hi != Float.MAX_VALUE);
    if (!haveRange && colorScheme.length() == 0) {
      value = lo;
      float[] range = viewer.getCurrentColorRange();
      lo = range[0];
      hi = range[1];
    }
    if (isIsosurface) {
      // isosurface color scheme      
      String id = colorScheme.substring(1);
      Object[] data = new Object[] { id, null};
      if (!viewer.getShapePropertyData(JC.SHAPE_ISOSURFACE, "colorEncoder", data))
        return mp.addXStr("");
      ce = (ColorEncoder) data[1];
    } else {
      ce.setRange(lo, hi, lo > hi);
    }
    Map<String, Object> key = ce.getColorKey();
    if (getValue)
      return mp.addXPt(CU.colorPtFromInt2(ce
          .getArgb(hi == Float.MAX_VALUE ? lo : value)));
    return mp.addXVar(SV.getVariableMap(key));
  }

  private boolean evaluateConnected(ScriptMathProcessor mp, SV[] args) {
    /*
     * Two options here:
     * 
     * connected(1, 3, "single", {carbon})
     * 
     * connected(1, 3, "partial 3.1", {carbon})
     * 
     * means "atoms connected to carbon by from 1 to 3 single bonds"
     * 
     * connected(1.0, 1.5, "single", {carbon}, {oxygen})
     * 
     * means "single bonds from 1.0 to 1.5 Angstroms between carbon and oxygen"
     * 
     * the first returns an atom bitset; the second returns a bond bitset.
     */

    if (args.length > 5)
      return false;
    float min = Integer.MIN_VALUE, max = Integer.MAX_VALUE;
    float fmin = 0, fmax = Float.MAX_VALUE;

    int order = JmolEdge.BOND_ORDER_ANY;
    BS atoms1 = null;
    BS atoms2 = null;
    boolean haveDecimal = false;
    boolean isBonds = false;
    for (int i = 0; i < args.length; i++) {
      SV var = args[i];
      switch (var.tok) {
      case T.bitset:
        isBonds = (var.value instanceof BondSet);
        if (isBonds && atoms1 != null)
          return false;
        if (atoms1 == null)
          atoms1 = SV.bsSelectVar(var);
        else if (atoms2 == null)
          atoms2 = SV.bsSelectVar(var);
        else
          return false;
        break;
      case T.string:
        String type = SV.sValue(var);
        if (type.equalsIgnoreCase("hbond"))
          order = JmolEdge.BOND_HYDROGEN_MASK;
        else
          order = ScriptEvaluator.getBondOrderFromString(type);
        if (order == JmolEdge.BOND_ORDER_NULL)
          return false;
        break;
      case T.decimal:
        haveDecimal = true;
        //$FALL-THROUGH$
      default:
        int n = var.asInt();
        float f = var.asFloat();
        if (max != Integer.MAX_VALUE)
          return false;

        if (min == Integer.MIN_VALUE) {
          min = Math.max(n, 0);
          fmin = f;
        } else {
          max = n;
          fmax = f;
        }
      }
    }
    if (min == Integer.MIN_VALUE) {
      min = 1;
      max = 100;
      fmin = JC.DEFAULT_MIN_CONNECT_DISTANCE;
      fmax = JC.DEFAULT_MAX_CONNECT_DISTANCE;
    } else if (max == Integer.MAX_VALUE) {
      max = min;
      fmax = fmin;
      fmin = JC.DEFAULT_MIN_CONNECT_DISTANCE;
    }
    if (atoms1 == null)
      atoms1 = viewer.getAllAtoms();
    if (haveDecimal && atoms2 == null)
      atoms2 = atoms1;
    if (atoms2 != null) {
      BS bsBonds = new BS();
      viewer
          .makeConnections(fmin, fmax, order,
              T.identify, atoms1, atoms2, bsBonds,
              isBonds, false, 0);
      return mp.addXVar(SV.newV(T.bitset, new BondSet(bsBonds, viewer
          .getAtomIndices(viewer.getAtomBits(T.bonds, bsBonds)))));
    }
    return mp.addXBs(viewer.getAtomsConnected(min, max, order, atoms1));
  }

  private boolean evaluateSubstructure(ScriptMathProcessor mp, SV[] args, int tok)
      throws ScriptException {
    // select substucture(....) legacy - was same as smiles(), now search()
    // select smiles(...)
    // select search(...)  now same as substructure
    if (args.length == 0)
      return false;
    BS bs = new BS();
    String pattern = SV.sValue(args[0]);
    if (pattern.length() > 0)
      try {
        BS bsSelected = (args.length == 2 && args[1].tok == T.bitset ? SV
            .bsSelectVar(args[1])
            : null);
        bs = viewer.getSmilesMatcher().getSubstructureSet(pattern,
            viewer.getModelSet().atoms, viewer.getAtomCount(), bsSelected,
            tok != T.smiles && tok != T.substructure, false);
      } catch (Exception e) {
        eval.evalError(e.toString(), null);
      }
    return mp.addXBs(bs);
  }

  /**
   * calculates the statistical value for x, y, and z independently
   * 
   * @param pointOrSVArray
   * @param tok
   * @return Point3f or "NaN"
   */
  @SuppressWarnings("unchecked")
  private Object getMinMaxPoint(Object pointOrSVArray, int tok) {
    P3[] data = null;
    List<SV> sv = null;
    int ndata = 0;
    if (pointOrSVArray instanceof Quaternion[]) {
      data = (P3[]) pointOrSVArray;
      ndata = data.length;
    } else if (pointOrSVArray instanceof List<?>) {
      sv = (List<SV>) pointOrSVArray;
      ndata = sv.size();
    }
    if (sv != null || data != null) {
      P3 result = new P3();
      float[] fdata = new float[ndata];
      boolean ok = true;
      for (int xyz = 0; xyz < 3 && ok; xyz++) {
        for (int i = 0; i < ndata; i++) {
          P3 pt = (data == null ? SV.ptValue(sv.get(i)) : data[i]);
          if (pt == null) {
            ok = false;
            break;
          }
          switch (xyz) {
          case 0:
            fdata[i] = pt.x;
            break;
          case 1:
            fdata[i] = pt.y;
            break;
          case 2:
            fdata[i] = pt.z;
            break;
          }
        }
        if (!ok)
          break;
        Object f = getMinMax(fdata, tok);
        if (f instanceof Float) {
          float value = ((Float) f).floatValue();
          switch (xyz) {
          case 0:
            result.x = value;
            break;
          case 1:
            result.y = value;
            break;
          case 2:
            result.z = value;
            break;
          }
        } else {
          break;
        }
      }
      return result;
    }
    return "NaN";
  }

  private Object getMinMaxQuaternion(List<SV> svData, int tok) {
    Quaternion[] data;
    switch (tok) {
    case T.min:
    case T.max:
    case T.sum:
    case T.sum2:
      return "NaN";
    }

    // only stddev and average

    while (true) {
      data = getQuaternionArray(svData, T.list);
      if (data == null)
        break;
      float[] retStddev = new float[1];
      Quaternion result = Quaternion.sphereMean(data, retStddev, 0.0001f);
      switch (tok) {
      case T.average:
        return result;
      case T.stddev:
        return Float.valueOf(retStddev[0]);
      }
      break;
    }
    return "NaN";
  }

  @SuppressWarnings("unchecked")
  private Quaternion[] getQuaternionArray(Object quaternionOrSVData, int itype) {
    Quaternion[] data;
    switch (itype) {
    case T.quaternion:
      data = (Quaternion[]) quaternionOrSVData;
      break;
    case T.point4f:
      P4[] pts = (P4[]) quaternionOrSVData;
      data = new Quaternion[pts.length];
      for (int i = 0; i < pts.length; i++)
        data[i] = Quaternion.newP4(pts[i]);
      break;
    case T.list:
      List<SV> sv = (List<SV>) quaternionOrSVData;
      data = new Quaternion[sv.size()];
      for (int i = 0; i < sv.size(); i++) {
        P4 pt = SV.pt4Value(sv.get(i));
        if (pt == null)
          return null;
        data[i] = Quaternion.newP4(pt);
      }
      break;
    default:
      return null;
    }
    return data;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object getMinMax(Object floatOrSVArray, int tok) {
    float[] data = null;
    List<SV> sv = null;
    int ndata = 0;
    while (true) {
      if (PT.isAF(floatOrSVArray)) {
        data = (float[]) floatOrSVArray;
        ndata = data.length;
        if (ndata == 0)
          break;
      } else if (floatOrSVArray instanceof List<?>) {
        sv = (List<SV>) floatOrSVArray;
        ndata = sv.size();
        if (ndata == 0)
          break;
        SV sv0 = sv.get(0);
        if (sv0.tok == T.string && ((String) sv0.value).startsWith("{")) {
          Object pt = SV.ptValue(sv0);
          if (pt instanceof P3)
            return getMinMaxPoint(sv, tok);
          if (pt instanceof P4)
            return getMinMaxQuaternion(sv, tok);
          break;
        }
      } else {
        break;
      }
      double sum;
      switch (tok) {
      case T.min:
        sum = Float.MAX_VALUE;
        break;
      case T.max:
        sum = -Float.MAX_VALUE;
        break;
      default:
        sum = 0;
      }
      double sum2 = 0;
      int n = 0;
      for (int i = ndata; --i >= 0;) {
        float v = (data == null ? SV.fValue(sv.get(i)) : data[i]);
        if (Float.isNaN(v))
          continue;
        n++;
        switch (tok) {
        case T.sum2:
        case T.stddev:
          sum2 += ((double) v) * v;
          //$FALL-THROUGH$
        case T.sum:
        case T.average:
          sum += v;
          break;
        case T.min:
          if (v < sum)
            sum = v;
          break;
        case T.max:
          if (v > sum)
            sum = v;
          break;
        }
      }
      if (n == 0)
        break;
      switch (tok) {
      case T.average:
        sum /= n;
        break;
      case T.stddev:
        if (n == 1)
          break;
        sum = Math.sqrt((sum2 - sum * sum / n) / (n - 1));
        break;
      case T.min:
      case T.max:
      case T.sum:
        break;
      case T.sum2:
        sum = sum2;
        break;
      }
      return Float.valueOf((float) sum);
    }
    return "NaN";
  }

  private void capture() throws ScriptException {
    // capture "filename"
    // capture "filename" ROTATE axis degrees // y 5 assumed; axis and degrees optional
    // capture "filename" SPIN axis  // y assumed; axis optional
    // capture off/on
    // capture "" or just capture   -- end
    if (!chk && !viewer.allowCapture()) {
      showString("Cannot capture on this platform");
      return;
    }
    int fps = viewer.getInt(T.animationfps);
    float endTime = 10; // ten seconds by default
    int mode = 0;
    String fileName = "";
    Map<String, Object> params = viewer.captureParams;
    boolean looping = !viewer.getAnimationReplayMode().name().equals("ONCE");
    int tok = tokAt(1);
    String sfps = "";
    switch (tok) {
    case T.nada:
      mode = T.end;
      break;
    case T.string:
      fileName = eval.optParameterAsString(1);
      if (fileName.length() == 0) {
        mode = T.end;
        break;
      }
      if (!fileName.endsWith(".gif"))
        fileName += ".gif";
      String s = null;
      String axis = "y";
      int i = 2;
      switch (tokAt(i)) {
      case T.rock:
        looping = true;
        i = 3;
        axis = (tokAt(3) == T.integer ? "y" : eval.optParameterAsString(i++)
            .toLowerCase());
        int n = (tokAt(i) == T.nada ? 5 : intParameter(i++));
        s = "; rotate Y 10 10;delay 2.0; rotate Y -10 -10; delay 2.0;rotate Y -10 -10; delay 2.0;rotate Y 10 10;delay 2.0";
        s = PT.simpleReplace(s, "10", "" + n);
        break;
      case T.spin:
        looping = true;
        i = 3;
        axis = eval.optParameterAsString(i).toLowerCase();
        if (axis.length() > 0)
          i++;
        s = "; rotate Y 360 30;delay 15.0;";
        if (tokAt(i) == T.integer)
          sfps = " " + (fps = intParameter(i++));
        break;
      case T.decimal:
        endTime = floatParameter(2);
        break;
      case T.integer:
        fps = intParameter(2);
        break;
      }
      if (s != null) {
        if (!chk)
          viewer.setNavigationMode(false);
        if (axis == "" || "xyz".indexOf(axis) < 0)
          axis = "y";
        s = PT.simpleReplace(s, "Y", axis);
        s = "capture " + Escape.eS(fileName) + sfps + s + ";capture;";
        eval.script(0, null, s);
        return;
      }
      if (params != null)
        params = new Hashtable<String, Object>();
      mode = T.movie;
      params = new Hashtable<String, Object>();
      if (!looping)
        showString(GT.o(GT._("Note: Enable looping using {0}"),
            new Object[] { "ANIMATION MODE LOOP" }));
      showString(GT.o(GT._("Animation delay based on: {0}"),
          new Object[] { "ANIMATION FPS " + fps }));
      params.put("captureFps", Integer.valueOf(fps));
      break;
    case T.cancel:
    case T.on:
    case T.off:
      checkLength(2);
      mode = tok;
      break;
    default:
      invArg();
    }
    if (chk || params == null)
      return;
    params.put("type", "GIF");
    params.put("fileName", fileName);
    params.put("quality", Integer.valueOf(-1));
    params.put("endTime", Long.valueOf(System.currentTimeMillis() + (long)(endTime * 1000)));
    params.put("captureMode", Integer.valueOf(mode));
    params.put("captureLooping", looping ? Boolean.TRUE : Boolean.FALSE);
    String msg = viewer.processWriteOrCapture(params);
    Logger.info(msg);
  }


}
