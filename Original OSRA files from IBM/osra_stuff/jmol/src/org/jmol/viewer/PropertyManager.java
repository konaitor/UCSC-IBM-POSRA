/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-12-29 13:09:57 -0600 (Sun, 29 Dec 2013) $
 * $Revision: 19133 $
 *
 * Copyright (C) 2003-2005  The Jmol Development Team
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
package org.jmol.viewer;

import javajs.util.Base64;
import javajs.util.List;
import javajs.util.PT;
import javajs.util.SB;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;

import java.util.Map;
import java.util.Properties;

import org.jmol.api.JmolPropertyManager;
import org.jmol.api.SymmetryInterface;
import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.Bond;
import org.jmol.modelset.BondSet;
import org.jmol.modelset.Chain;
import org.jmol.modelset.Group;
import org.jmol.modelset.LabelToken;
import org.jmol.modelset.Model;
import org.jmol.modelset.ModelSet;
import org.jmol.script.SV;
import org.jmol.script.T;
import org.jmol.util.BSUtil;
import org.jmol.util.C;
import org.jmol.util.Elements;
import org.jmol.util.Escape;
import org.jmol.util.JmolEdge;
import org.jmol.util.JmolMolecule;
import org.jmol.util.Logger;

import javajs.util.M3;
import javajs.util.P3;
import org.jmol.util.Quaternion;
import org.jmol.util.Txt;

/**
 * 
 * The PropertyManager handles all operations relating to delivery of properties
 * with the getProperty() method, or its specifically cast forms
 * getPropertyString() or getPropertyJSON().
 * 
 * It is instantiated by reflection
 * 
 */

public class PropertyManager implements JmolPropertyManager {

  public PropertyManager() {
    // required for reflection
  }

  Viewer viewer;
  private Map<String, Integer> map = new Hashtable<String, Integer>();

  @Override
  public void setViewer(Viewer viewer) {
    this.viewer = viewer;
    for (int i = 0, p = 0; i < propertyTypes.length; i += 3)
      map.put(propertyTypes[i].toLowerCase(), Integer.valueOf(p++));
  }

  @Override
  public int getPropertyNumber(String infoType) {
    Integer n = map.get(infoType == null ? "" : infoType.toLowerCase());
    return (n == null ? -1 : n.intValue());
  }

  @Override
  public String getDefaultPropertyParam(int propID) {
    return (propID < 0 ? "" : propertyTypes[propID * 3 + 2]);
  }

  @Override
  public boolean checkPropertyParameter(String name) {
    int propID = getPropertyNumber(name);
    String type = getParamType(propID);
    return (type.length() > 0 && type != atomExpression);
  }

  private final static String atomExpression = "<atom selection>";

  private final static String[] propertyTypes = {
    "appletInfo"      , "", "",
    "fileName"        , "", "",
    "fileHeader"      , "", "",
    "fileContents"    , "<pathname>", "",
    "fileContents"    , "", "",
    "animationInfo"   , "", "",
    "modelInfo"       , atomExpression, "{*}",
    //"X -vibrationInfo", "", "",  //not implemented -- see modelInfo
    "ligandInfo"      , atomExpression, "{*}",
    "shapeInfo"       , "", "",
    "measurementInfo" , "", "",
    
    "centerInfo"      , "", "",
    "orientationInfo" , "", "",
    "transformInfo"   , "", "",
    "atomList"        , atomExpression, "(visible)",
    "atomInfo"        , atomExpression, "(visible)",
    
    "bondInfo"        , atomExpression, "(visible)",
    "chainInfo"       , atomExpression, "(visible)",
    "polymerInfo"     , atomExpression, "(visible)",
    "moleculeInfo"    , atomExpression, "(visible)",
    "stateInfo"       , "<state type>", "all",
    
    "extractModel"    , atomExpression, "(visible)",
    "jmolStatus"      , "statusNameList", "",
    "jmolViewer"      , "", "",
    "messageQueue"    , "", "",
    "auxiliaryInfo"   , atomExpression, "{*}",
    
    "boundBoxInfo"    , "", "",  
    "dataInfo"        , "<data type>", "types",
    "image"           , "<width=www,height=hhh>", "",
    "evaluate"        , "<expression>", "",
    "menu"            , "<type>", "current",
    "minimizationInfo", "", "",
    "pointGroupInfo"  , atomExpression, "(visible)",
    "fileInfo"        , "<type>", "",
    "errorMessage"    , "", "",
    "mouseInfo"       , "", "",
    "isosurfaceInfo"  , "", "",
    "isosurfaceData"  , "", "",
    "consoleText"     , "", "",
    "JSpecView"       , "<key>", "",
    "scriptQueueInfo" , "", "",
    "nmrInfo" , "<elementSymbol> or 'all' or 'shifts'", "all",
    "variableInfo","<name>","all"
  };

  private final static int PROP_APPLET_INFO = 0;
  private final static int PROP_FILENAME = 1;
  private final static int PROP_FILEHEADER = 2;
  private final static int PROP_FILECONTENTS_PATH = 3;
  private final static int PROP_FILECONTENTS = 4;

  private final static int PROP_ANIMATION_INFO = 5;
  private final static int PROP_MODEL_INFO = 6;
  //private final static int PROP_VIBRATION_INFO = 7; //not implemented -- see auxiliaryInfo
  private final static int PROP_LIGAND_INFO = 7;
  private final static int PROP_SHAPE_INFO = 8;
  private final static int PROP_MEASUREMENT_INFO = 9;

  private final static int PROP_CENTER_INFO = 10;
  private final static int PROP_ORIENTATION_INFO = 11;
  private final static int PROP_TRANSFORM_INFO = 12;
  private final static int PROP_ATOM_LIST = 13;
  private final static int PROP_ATOM_INFO = 14;

  private final static int PROP_BOND_INFO = 15;
  private final static int PROP_CHAIN_INFO = 16;
  private final static int PROP_POLYMER_INFO = 17;
  private final static int PROP_MOLECULE_INFO = 18;
  private final static int PROP_STATE_INFO = 19;

  private final static int PROP_EXTRACT_MODEL = 20;
  private final static int PROP_JMOL_STATUS = 21;
  private final static int PROP_JMOL_VIEWER = 22;
  private final static int PROP_MESSAGE_QUEUE = 23;
  private final static int PROP_AUXILIARY_INFO = 24;

  private final static int PROP_BOUNDBOX_INFO = 25;
  private final static int PROP_DATA_INFO = 26;
  private final static int PROP_IMAGE = 27;
  private final static int PROP_EVALUATE = 28;
  private final static int PROP_MENU = 29;
  private final static int PROP_MINIMIZATION_INFO = 30;
  private final static int PROP_POINTGROUP_INFO = 31;
  private final static int PROP_FILE_INFO = 32;
  private final static int PROP_ERROR_MESSAGE = 33;
  private final static int PROP_MOUSE_INFO = 34;
  private final static int PROP_ISOSURFACE_INFO = 35;
  private final static int PROP_ISOSURFACE_DATA = 36;
  private final static int PROP_CONSOLE_TEXT = 37;
  private final static int PROP_JSPECVIEW = 38;
  private final static int PROP_SCRIPT_QUEUE_INFO = 39;
  private final static int PROP_NMR_INFO = 40;
  private final static int PROP_VAR_INFO = 41;
  private final static int PROP_COUNT = 42;

  //// static methods used by Eval and Viewer ////

  @Override
  public Object getProperty(String returnType, String infoType, Object paramInfo) {
    if (propertyTypes.length != PROP_COUNT * 3)
      Logger.warn("propertyTypes is not the right length: "
          + propertyTypes.length + " != " + PROP_COUNT * 3);
    Object info;
    if (infoType.indexOf(".") >= 0 || infoType.indexOf("[") >= 0) {
      info = getModelProperty(infoType, paramInfo);
    } else {
      info = getPropertyAsObject(infoType, paramInfo, returnType);
    }
    if (returnType == null)
      return info;
    boolean requestedReadable = returnType.equalsIgnoreCase("readable");
    if (requestedReadable)
      returnType = (isReadableAsString(infoType) ? "String" : "JSON");
    if (returnType.equalsIgnoreCase("String"))
      return (info == null ? "" : info.toString());
    if (requestedReadable)
      return Escape.toReadable(infoType, info);
    else if (returnType.equalsIgnoreCase("JSON"))
      return "{" + PT.toJSON(infoType, info) + "}";
    return info;
  }

  private Object getModelProperty(String propertyName, Object propertyValue) {
    propertyName = propertyName.replace(']', ' ').replace('[', ' ').replace(
        '.', ' ');
    propertyName = PT.simpleReplace(propertyName, "  ", " ");
    String[] names = PT.split(PT.trim(propertyName, " "),
        " ");
    SV[] args = new SV[names.length];
    propertyName = names[0];
    int n;
    for (int i = 1; i < names.length; i++) {
      if ((n = PT.parseInt(names[i])) != Integer.MIN_VALUE)
        args[i] = SV.newI(n);
      else
        args[i] = SV.newV(T.string, names[i]);
    }
    return extractProperty(getProperty(null, propertyName, propertyValue),
        args, 1);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object extractProperty(Object property, SV[] args, int ptr) {
    if (ptr >= args.length)
      return property;
    int pt;
    SV arg = args[ptr++];
    switch (arg.tok) {
    case T.integer:
      pt = arg.asInt() - 1; //one-based, as for array selectors
      if (property instanceof List<?>) {
        List<Object> v = (List<Object>) property;
        if (pt < 0)
          pt += v.size();
        if (pt >= 0 && pt < v.size())
          return extractProperty(v.get(pt), args, ptr);
        return "";
      }
      if (property instanceof M3) {
        M3 m = (M3) property;
        float[][] f = new float[][] { new float[] { m.m00, m.m01, m.m02 },
            new float[] { m.m10, m.m11, m.m12 },
            new float[] { m.m20, m.m21, m.m22 } };
        if (pt < 0)
          pt += 3;
        if (pt >= 0 && pt < 3)
          return extractProperty(f, args, --ptr);
        return "";
      }

      if (PT.isAI(property)) {
        int[] ilist = (int[]) property;
        if (pt < 0)
          pt += ilist.length;
        if (pt >= 0 && pt < ilist.length)
          return Integer.valueOf(ilist[pt]);
        return "";
      }
      if (PT.isAD(property)) {
        double[] dlist = (double[]) property;
        if (pt < 0)
          pt += dlist.length;
        if (pt >= 0 && pt < dlist.length)
          return Double.valueOf(dlist[pt]);
        return "";
      }
      if (PT.isAF(property)) {
        float[] flist = (float[]) property;
        if (pt < 0)
          pt += flist.length;
        if (pt >= 0 && pt < flist.length)
          return Float.valueOf(flist[pt]);
        return "";
      }
      if (PT.isAII(property)) {
        int[][] iilist = (int[][]) property;
        if (pt < 0)
          pt += iilist.length;
        if (pt >= 0 && pt < iilist.length)
          return extractProperty(iilist[pt], args, ptr);
        return "";
      }
      if (PT.isAFF(property)) {
        float[][] fflist = (float[][]) property;
        if (pt < 0)
          pt += fflist.length;
        if (pt >= 0 && pt < fflist.length)
          return extractProperty(fflist[pt], args, ptr);
        return "";
      }
      if (PT.isAS(property)) {
        String[] slist = (String[]) property;
        if (pt < 0)
          pt += slist.length;
        if (pt >= 0 && pt < slist.length)
          return slist[pt];
        return "";
      }
      if (property instanceof Object[]) {
        Object[] olist = (Object[]) property;
        if (pt < 0)
          pt += olist.length;
        if (pt >= 0 && pt < olist.length)
          return olist[pt];
        return "";
      }
      break;

    case T.string:
      String key = arg.asString();
      if (property instanceof Map<?, ?>) {
        Map<String, Object> h = (Map<String, Object>) property;
        if (key.equalsIgnoreCase("keys")) {
          List<Object> keys = new  List<Object>();
          for (String k: h.keySet())
            keys.addLast(k);
          return extractProperty(keys, args, ptr);
        }
        if (!h.containsKey(key)) {
          for (String k: h.keySet())
            if (k.equalsIgnoreCase(key)) {
              key = k;
              break;
            }
        }
        if (h.containsKey(key))
          return extractProperty(h.get(key), args, ptr);
        return "";
      }
      if (property instanceof List<?>) {
        // drill down into vectors for this key
        List<Object> v = (List<Object>) property;
        List<Object> v2 = new  List<Object>();
        ptr--;
        for (pt = 0; pt < v.size(); pt++) {
          Object o = v.get(pt);
          if (o instanceof Map<?, ?>)
            v2.addLast(extractProperty(o, args, ptr));
        }
        return v2;
      }
      break;
    }
    return property;
  }

  //// private static methods ////

  private static String getPropertyName(int propID) {
    return (propID < 0 ? "" : propertyTypes[propID * 3]);
  }

  private static String getParamType(int propID) {
    return (propID < 0 ? "" : propertyTypes[propID * 3 + 1]);
  }

  private final static String[] readableTypes = { "", "stateinfo",
      "extractmodel", "filecontents", "fileheader", "image", "menu",
      "minimizationInfo" };

  private static boolean isReadableAsString(String infoType) {
    for (int i = readableTypes.length; --i >= 0;)
      if (infoType.equalsIgnoreCase(readableTypes[i]))
        return true;
    return false;
  }

  private Object getPropertyAsObject(String infoType, Object paramInfo,
                                     String returnType) {
    //Logger.debug("getPropertyAsObject(\"" + infoType+"\", \"" + paramInfo + "\")");
    if (infoType.equals("tokenList")) {
      return T.getTokensLike((String) paramInfo);
    }
    int id = getPropertyNumber(infoType);
    boolean iHaveParameter = (paramInfo != null && paramInfo.toString()
        .length() > 0);
    Object myParam = (iHaveParameter ? paramInfo : getDefaultPropertyParam(id));
    //myParam may now be a bitset
    switch (id) {
    case PROP_APPLET_INFO:
      return viewer.getAppletInfo();
    case PROP_ANIMATION_INFO:
      return viewer.getAnimationInfo();
    case PROP_ATOM_LIST:
      return viewer.getAtomBitSetVector(myParam);
    case PROP_ATOM_INFO:
      return getAllAtomInfo(viewer.getAtomBitSet(myParam));
    case PROP_AUXILIARY_INFO:
      return viewer.getAuxiliaryInfo(myParam);
    case PROP_BOND_INFO:
      return getAllBondInfo(myParam);
    case PROP_BOUNDBOX_INFO:
      return viewer.getBoundBoxInfo();
    case PROP_CENTER_INFO:
      return viewer.getRotationCenter();
    case PROP_CHAIN_INFO:
      return getAllChainInfo(viewer.getAtomBitSet(myParam));
    case PROP_CONSOLE_TEXT:
      return viewer.getProperty("DATA_API", "consoleText", null);
    case PROP_DATA_INFO:
      return viewer.getData(myParam.toString());
    case PROP_ERROR_MESSAGE:
      return viewer.getErrorMessageUn();
    case PROP_EVALUATE:
      return viewer.evaluateExpression(myParam.toString());
    case PROP_EXTRACT_MODEL:
      return viewer.getModelExtract(myParam, true, false, "MOL");
    case PROP_FILE_INFO:
      return getFileInfo(viewer.getFileData(), myParam.toString());
    case PROP_FILENAME:
      return viewer.getFullPathName(false);
    case PROP_FILEHEADER:
      return viewer.getFileHeader();
    case PROP_FILECONTENTS:
    case PROP_FILECONTENTS_PATH:
      if (iHaveParameter)
        return viewer.getFileAsString(myParam.toString());
      return viewer.getCurrentFileAsString();
    case PROP_IMAGE:
      String params = myParam.toString().toLowerCase();
      int height = -1,
      width = -1;
      int pt;
      if ((pt = params.indexOf("height=")) >= 0)
        height = PT.parseInt(params.substring(pt + 7));
      if ((pt = params.indexOf("width=")) >= 0)
        width = PT.parseInt(params.substring(pt + 6));
      if (width < 0 && height < 0)
        height = width = -1;
      else if (width < 0)
        width = height;
      else
        height = width;
      if (params.indexOf("g64") >= 0 || params.indexOf("base64") >= 0)
        returnType = "string";
      String type = "JPG";
      if (params.indexOf("type=") >= 0)
        type = PT.getTokens(PT.replaceAllCharacter(params.substring(params.indexOf("type=") + 5), ";,", ' '))[0];
      String[] errMsg = new String[1];
      byte[] bytes = viewer.getImageAsBytes(type.toUpperCase(), width,  height, -1, errMsg);
      return (errMsg[0] != null ? errMsg[0] : returnType == null ? bytes : Base64
          .getBase64(bytes).toString());
    case PROP_ISOSURFACE_INFO:
      return viewer.getShapeProperty(JC.SHAPE_ISOSURFACE, "getInfo");
    case PROP_ISOSURFACE_DATA:
      return viewer.getShapeProperty(JC.SHAPE_ISOSURFACE, "getData");
    case PROP_NMR_INFO:
      return viewer.getNMRCalculation().getInfo(myParam.toString());
    case PROP_VAR_INFO:
      return getVariables(myParam.toString());
    case PROP_JMOL_STATUS:
      return viewer.getStatusChanged(myParam.toString());
    case PROP_JMOL_VIEWER:
      return viewer;
    case PROP_JSPECVIEW:
      return viewer.getJspecViewProperties(myParam);
    case PROP_LIGAND_INFO:
      return getLigandInfo(viewer.getAtomBitSet(myParam));
    case PROP_MEASUREMENT_INFO:
      return viewer.getMeasurementInfo();
    case PROP_MENU:
      return viewer.getMenu(myParam.toString());
    case PROP_MESSAGE_QUEUE:
      return viewer.getMessageQueue();
    case PROP_MINIMIZATION_INFO:
      return viewer.getMinimizationInfo();
    case PROP_MODEL_INFO:
      return getModelInfo(viewer.getAtomBitSet(myParam));
    case PROP_MOLECULE_INFO:
      return getMoleculeInfo(viewer.getAtomBitSet(myParam));
    case PROP_MOUSE_INFO:
      return viewer.getMouseInfo();
    case PROP_ORIENTATION_INFO:
      return viewer.getOrientationInfo();
    case PROP_POINTGROUP_INFO:
      return viewer.getPointGroupInfo(myParam);
    case PROP_POLYMER_INFO:
      return getAllPolymerInfo(viewer.getAtomBitSet(myParam));
    case PROP_SCRIPT_QUEUE_INFO:
      return viewer.getScriptQueueInfo();
    case PROP_SHAPE_INFO:
      return viewer.getShapeInfo();
    case PROP_STATE_INFO:
      return viewer.getStateInfo3(myParam.toString(), 0, 0);
    case PROP_TRANSFORM_INFO:
      return viewer.getMatrixRotate();
    }
    String[] data = new String[PROP_COUNT];
    for (int i = 0; i < PROP_COUNT; i++) {
      String paramType = getParamType(i);
      String paramDefault = getDefaultPropertyParam(i);
      String name = getPropertyName(i);
      data[i] = (name.charAt(0) == 'X' ? "" : name
          + (paramType != "" ? " "
              + getParamType(i)
              + (paramDefault != "" ? " #default: "
                  + getDefaultPropertyParam(i) : "") : ""));
    }
    Arrays.sort(data);
    SB info = new SB();
    info.append("getProperty ERROR\n").append(infoType).append(
        "?\nOptions include:\n");
    for (int i = 0; i < PROP_COUNT; i++)
      if (data[i].length() > 0)
        info.append("\n getProperty ").append(data[i]);
    return info.toString();
  }

  private Object getVariables(String name) {
    return (name.toLowerCase().equals("all") ? viewer.global.getAllVariables()
        : viewer.evaluateExpressionAsVariable(name));
  }

  static Object getFileInfo(Object objHeader, String type) {
    Map<String, String> ht = new Hashtable<String, String>();
    if (objHeader == null)
      return ht;
    boolean haveType = (type != null && type.length() > 0);
    if (objHeader instanceof Map) {
      return (haveType ? ((Map<?, ?>) objHeader).get(type) : objHeader);
    }
    String[] lines = PT.split((String) objHeader, "\n");
    // this is meant to be for PDB files only
    if (lines.length == 0
        || lines[0].length() < 6
        || lines[0].charAt(6) != ' '
        || !lines[0].substring(0, 6).equals(
            lines[0].substring(0, 6).toUpperCase())) {
      ht.put("fileHeader", (String) objHeader);
      return ht;
    }
    String keyLast = "";
    SB sb = new SB();
    if (haveType)
      type = type.toUpperCase();
    String key = "";
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.length() < 12)
        continue;
      key = line.substring(0, 6).trim();
      String cont = line.substring(7, 10).trim();
      if (key.equals("REMARK")) {
        key += cont;
      }
      if (!key.equals(keyLast)) {
        if (haveType && keyLast.equals(type))
          return sb.toString();
        if (!haveType) {
          ht.put(keyLast, sb.toString());
          sb = new SB();
        }
        keyLast = key;
      }
      if (!haveType || key.equals(type))
        sb.append(line).appendC('\n');
    }
    if (!haveType) {
      ht.put(keyLast, sb.toString());
    }
    if (haveType)
      return (key.equals(type) ? sb.toString() : "");
    return ht;
  }

  /// info ///

  public List<Map<String, Object>> getMoleculeInfo(Object atomExpression) {
    BS bsAtoms = viewer.getAtomBitSet(atomExpression);
    JmolMolecule[] molecules = viewer.modelSet.getMolecules();
    List<Map<String, Object>> V = new  List<Map<String, Object>>();
    BS bsTemp = new BS();
    for (int i = 0; i < molecules.length; i++) {
      bsTemp = BSUtil.copy(bsAtoms);
      JmolMolecule m = molecules[i];
      bsTemp.and(m.atomList);
      if (bsTemp.length() > 0) {
        Map<String, Object> info = new Hashtable<String, Object>();
        info.put("mf", m.getMolecularFormula(false)); // sets atomCount and nElements
        info.put("number", Integer.valueOf(m.moleculeIndex + 1)); //for now
        info.put("modelNumber", viewer.modelSet.getModelNumberDotted(m.modelIndex));
        info.put("numberInModel", Integer.valueOf(m.indexInModel + 1));
        info.put("nAtoms", Integer.valueOf(m.atomCount));
        info.put("nElements", Integer.valueOf(m.nElements));
        V.addLast(info);
      }
    }
    return V;
  }

  @Override
  public Map<String, Object> getModelInfo(Object atomExpression) {

    BS bsModels = viewer.getModelBitSet(viewer
        .getAtomBitSet(atomExpression), false);

    ModelSet m = viewer.getModelSet();
    Map<String, Object> info = new Hashtable<String, Object>();
    info.put("modelSetName", m.modelSetName);
    info.put("modelCount", Integer.valueOf(m.modelCount));
    info.put("isTainted", Boolean.valueOf(m.tainted != null));
    info.put("canSkipLoad", Boolean.valueOf(m.canSkipLoad));
    info.put("modelSetHasVibrationVectors", Boolean.valueOf(m
        .modelSetHasVibrationVectors()));
    if (m.modelSetProperties != null) {
      info.put("modelSetProperties", m.modelSetProperties);
    }
    info.put("modelCountSelected", Integer.valueOf(BSUtil
        .cardinalityOf(bsModels)));
    info.put("modelsSelected", bsModels);
    List<Map<String, Object>> vModels = new  List<Map<String, Object>>();
    m.getMolecules();

    for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels.nextSetBit(i + 1)) {
      Map<String, Object> model = new Hashtable<String, Object>();
      model.put("_ipt", Integer.valueOf(i));
      model.put("num", Integer.valueOf(m.getModelNumber(i)));
      model.put("file_model", m.getModelNumberDotted(i));
      model.put("name", m.getModelName(i));
      String s = m.getModelTitle(i);
      if (s != null)
        model.put("title", s);
      s = m.getModelFileName(i);
      if (s != null)
        model.put("file", s);
      s = (String) m.getModelAuxiliaryInfoValue(i, "modelID");
      if (s != null)
        model.put("id", s);
      model.put("vibrationVectors", Boolean.valueOf(viewer.modelHasVibrationVectors(i)));
      Model mi = m.models[i];
      model.put("atomCount", Integer.valueOf(mi.atomCount));
      model.put("bondCount", Integer.valueOf(mi.getBondCount()));
      model.put("groupCount", Integer.valueOf(mi.getGroupCount()));
      model.put("moleculeCount", Integer.valueOf(mi.moleculeCount));
      model.put("polymerCount", Integer.valueOf(mi.getBioPolymerCount()));
      model.put("chainCount", Integer.valueOf(m.getChainCountInModel(i, true)));
      if (mi.properties != null) {
        model.put("modelProperties", mi.properties);
      }
      Float energy = (Float) m.getModelAuxiliaryInfoValue(i, "Energy");
      if (energy != null) {
        model.put("energy", energy);
      }
      model.put("atomCount", Integer.valueOf(mi.atomCount));
      vModels.addLast(model);
    }
    info.put("models", vModels);
    return info;
  }

  @Override
  public Map<String, Object> getLigandInfo(Object atomExpression) {
    BS bsAtoms = viewer.getAtomBitSet(atomExpression);
    BS bsSolvent = viewer.getAtomBitSet("solvent");
    Map<String, Object> info = new Hashtable<String, Object>();
    List<Map<String, Object>> ligands = new  List<Map<String, Object>>();
    info.put("ligands", ligands);
    ModelSet ms = viewer.modelSet;
    BS bsExclude = BSUtil.copyInvert(bsAtoms, ms.atomCount);
    bsExclude.or(bsSolvent);
    Atom[] atoms = ms.atoms;
    for (int i = bsAtoms.nextSetBit(0); i >= 0; i = bsAtoms.nextSetBit(i + 1))
      if (atoms[i].isProtein() || atoms[i].isNucleic())
        bsExclude.set(i);
    BS[] bsModelAtoms = new BS[ms.modelCount];
    for (int i = ms.modelCount; --i >= 0;) {
      bsModelAtoms[i] = viewer.getModelUndeletedAtomsBitSet(i);
      bsModelAtoms[i].andNot(bsExclude);
    }
    JmolMolecule[] molList = JmolMolecule.getMolecules(atoms, bsModelAtoms,
        null, bsExclude);
    for (int i = 0; i < molList.length; i++) {
      BS bs = molList[i].atomList;
      Map<String, Object> ligand = new Hashtable<String, Object>();
      ligands.addLast(ligand);
      ligand.put("atoms", Escape.eBS(bs));
      String names = "";
      String sep = "";
      Group lastGroup = null;
      int iChainLast = 0;
      String sChainLast = null;
      String reslist = "";
      String model = "";
      int resnolast = Integer.MAX_VALUE;
      int resnofirst = Integer.MAX_VALUE;
      for (int j = bs.nextSetBit(0); j >= 0; j = bs.nextSetBit(j + 1)) {
        Atom atom = atoms[j];
        if (lastGroup == atom.group)
          continue;
        lastGroup = atom.group;
        int resno = atom.getResno();
        int chain = atom.getChainID();
        if (resnolast != resno - 1) {
          if (reslist.length() != 0 && resnolast != resnofirst)
            reslist += "-" + resnolast;
          chain = -1;
          resnofirst = resno;
        }
        model = "/" + ms.getModelNumberDotted(atom.modelIndex);
        if (iChainLast != 0 && chain != iChainLast)
          reslist += ":" + sChainLast + model;
        if (chain == -1)
          reslist += " " + resno;
        resnolast = resno;
        iChainLast = atom.getChainID();
        sChainLast = atom.getChainIDStr();
        names += sep + atom.getGroup3(false);
        sep = "-";
      }
      reslist += (resnofirst == resnolast ? "" : "-" + resnolast)
          + (iChainLast == 0 ? "" : ":" + sChainLast) + model;
      ligand.put("groupNames", names);
      ligand.put("residueList", reslist.substring(1));
    }
    return info;
  }

  @Override
  public Object getSymmetryInfo(BS bsAtoms, String xyz, int op, P3 pt,
                                P3 pt2, String id, int type) {
    int iModel = -1;
    if (bsAtoms == null) {
      iModel = viewer.getCurrentModelIndex();
      if (iModel < 0)
        return "";
      bsAtoms = viewer.getModelUndeletedAtomsBitSet(iModel);
    }
    int iAtom = bsAtoms.nextSetBit(0);
    if (iAtom < 0)
      return "";
    iModel = viewer.modelSet.atoms[iAtom].modelIndex;
    SymmetryInterface uc = viewer.modelSet.models[iModel].biosymmetry;
    if (uc == null)
      uc = viewer.modelSet.getUnitCell(iModel);
    if (uc == null)
      return "";
    return uc.getSymmetryInfo(viewer.modelSet, iModel, iAtom, uc, xyz, op, pt,
        pt2, id, type);
  }

  
  @Override
  public String getModelExtract(BS bs, boolean doTransform,
                                boolean isModelKit, String type) {
    boolean asV3000 = type.equalsIgnoreCase("V3000");
    boolean asSDF = type.equalsIgnoreCase("SDF");
    boolean asXYZVIB = type.equalsIgnoreCase("XYZVIB");
    boolean asJSON = type.equalsIgnoreCase("JSON") || type.equalsIgnoreCase("CD");
    SB mol = new SB();
    ModelSet ms = viewer.modelSet;
    if (!asXYZVIB && !asJSON) {
      mol.append(isModelKit ? "Jmol Model Kit" : viewer.getFullPathName(false)
          .replace('\\', '/'));
      String version = Viewer.getJmolVersion();
      mol.append("\n__Jmol-").append(version.substring(0, 2));
      int cMM, cDD, cYYYY, cHH, cmm;
      /**
       * @j2sNative
       * 
       * var c = new Date();
       * cMM = c.getMonth();
       * cDD = c.getDate();
       * cYYYY = c.getFullYear();
       * cHH = c.getHours();
       * cmm = c.getMinutes();
       */
      {
        Calendar c = Calendar.getInstance();
        cMM = c.get(Calendar.MONTH);
        cDD = c.get(Calendar.DAY_OF_MONTH);
        cYYYY = c.get(Calendar.YEAR);
        cHH = c.get(Calendar.HOUR_OF_DAY);
        cmm = c.get(Calendar.MINUTE);
      }
      Txt.rightJustify(mol, "_00", "" + (1 + cMM));
      Txt.rightJustify(mol, "00", "" + cDD);
      mol.append(("" + cYYYY).substring(2, 4));
      Txt.rightJustify(mol, "00", "" + cHH);
      Txt.rightJustify(mol, "00", "" + cmm);
      mol.append("3D 1   1.00000     0.00000     0");
      //       This line has the format:
      //  IIPPPPPPPPMMDDYYHHmmddSSssssssssssEEEEEEEEEEEERRRRRR
      //  A2<--A8--><---A10-->A2I2<--F10.5-><---F12.5--><-I6->
      mol.append("\nJmol version ").append(Viewer.getJmolVersion()).append(
          " EXTRACT: ").append(Escape.eBS(bs)).append("\n");
    }
    BS bsAtoms = BSUtil.copy(bs);
    Atom[] atoms = ms.atoms;
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
      if (doTransform && atoms[i].isDeleted())
        bsAtoms.clear(i);
    BS bsBonds = getCovalentBondsForAtoms(ms.bonds, ms.bondCount, bsAtoms);
    if (!asXYZVIB && bsAtoms.cardinality() == 0)
      return "";
    boolean isOK = true;
    Quaternion q = (doTransform ? viewer.getRotationQuaternion() : null);
    if (asSDF) {
      String header = mol.toString();
      mol = new SB();
      BS bsModels = viewer.getModelBitSet(bsAtoms, true);
      for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels
          .nextSetBit(i + 1)) {
        mol.append(header);
        BS bsTemp = BSUtil.copy(bsAtoms);
        bsTemp.and(ms.getModelAtomBitSetIncludingDeleted(i, false));
        bsBonds = getCovalentBondsForAtoms(ms.bonds, ms.bondCount, bsTemp);
        if (!(isOK = addMolFile(mol, bsTemp, bsBonds, false, false, q)))
          break;
        mol.append("$$$$\n");
      }
    } else if (asXYZVIB) {
      LabelToken[] tokens1 = LabelToken.compile(viewer,
          "%-2e %10.5x %10.5y %10.5z %10.5vx %10.5vy %10.5vz\n", '\0', null);
      LabelToken[] tokens2 = LabelToken.compile(viewer,
          "%-2e %10.5x %10.5y %10.5z\n", '\0', null);
      BS bsModels = viewer.getModelBitSet(bsAtoms, true);
      for (int i = bsModels.nextSetBit(0); i >= 0; i = bsModels
          .nextSetBit(i + 1)) {
        BS bsTemp = BSUtil.copy(bsAtoms);
        bsTemp.and(ms.getModelAtomBitSetIncludingDeleted(i, false));
        if (bsTemp.cardinality() == 0)
          continue;
        mol.appendI(bsTemp.cardinality()).appendC('\n');
        Properties props = ms.models[i].properties;
        mol.append("Model[" + (i + 1) + "]: ");
        if (ms.frameTitles[i] != null && ms.frameTitles[i].length() > 0) {
          mol.append(ms.frameTitles[i].replace('\n', ' '));
        } else if (props == null) {
          mol.append("Jmol " + Viewer.getJmolVersion());
        } else {
          SB sb = new SB();
          Enumeration<?> e = props.propertyNames();
          String path = null;
          while (e.hasMoreElements()) {
            String propertyName = (String) e.nextElement();
            if (propertyName.equals(".PATH"))
              path = props.getProperty(propertyName);
            else
              sb.append(";").append(propertyName).append("=").append(
                  props.getProperty(propertyName));
          }
          if (path != null)
            sb.append(";PATH=").append(path);
          path = sb.substring(sb.length() > 0 ? 1 : 0);
          mol.append(path.replace('\n', ' '));
        }
        mol.appendC('\n');
        for (int j = bsTemp.nextSetBit(0); j >= 0; j = bsTemp.nextSetBit(j + 1))
          mol.append(LabelToken.formatLabelAtomArray(viewer, atoms[j],
              (ms.getVibration(j, false) == null ? tokens2 : tokens1), '\0',
              null));
      }
    } else {
      isOK = addMolFile(mol, bsAtoms, bsBonds, asV3000, asJSON, q);
    }
    return (isOK ? mol.toString()
        : "ERROR: Too many atoms or bonds -- use V3000 format.");
  }

  private boolean addMolFile(SB mol, BS bsAtoms, BS bsBonds,
                             boolean asV3000, boolean asJSON, Quaternion q) {
    int nAtoms = bsAtoms.cardinality();
    int nBonds = bsBonds.cardinality();
    if (!asV3000 && !asJSON && (nAtoms > 999 || nBonds > 999))
      return false;
    ModelSet ms = viewer.modelSet;
    int[] atomMap = new int[ms.atomCount];
    P3 pTemp = new P3();
    if (asV3000) {
      mol.append("  0  0  0  0  0  0            999 V3000");
    } else if (asJSON) {
       mol.append("{\"mol\":{\"createdBy\":\"Jmol "+ Viewer.getJmolVersion() + "\",\"a\":[");
    } else {
      Txt.rightJustify(mol, "   ", "" + nAtoms);
      Txt.rightJustify(mol, "   ", "" + nBonds);
      mol.append("  0  0  0  0              1 V2000");
    }
    if (!asJSON)
      mol.append("\n");
    if (asV3000) {
      mol.append("M  V30 BEGIN CTAB\nM  V30 COUNTS ").appendI(nAtoms)
          .append(" ").appendI(nBonds).append(" 0 0 0\n").append(
              "M  V30 BEGIN ATOM\n");
    }
    P3 ptTemp = new P3();
    for (int i = bsAtoms.nextSetBit(0), n = 0; i >= 0; i = bsAtoms
        .nextSetBit(i + 1))
      getAtomRecordMOL(ms, mol, atomMap[i] = ++n, ms.atoms[i], q, pTemp, ptTemp, asV3000,
          asJSON);
    if (asV3000) {
      mol.append("M  V30 END ATOM\nM  V30 BEGIN BOND\n");
    } else if (asJSON) {
      mol.append("],\"b\":[");
    }
    for (int i = bsBonds.nextSetBit(0), n = 0; i >= 0; i = bsBonds
        .nextSetBit(i + 1))
      getBondRecordMOL(mol, ++n, ms.bonds[i], atomMap, asV3000, asJSON);
    // 21 21 0 0 0
    if (asV3000) {
      mol.append("M  V30 END BOND\nM  V30 END CTAB\n");
    }
    if (asJSON)
      mol.append("]}}");
    else {
      mol.append("M  END\n");
    }
    if (!asJSON && !asV3000) {
      float[] pc = ms.getPartialCharges();
      if (pc != null) {
        mol.append("> <JMOL_PARTIAL_CHARGES>\n").appendI(nAtoms)
            .appendC('\n');
        for (int i = bsAtoms.nextSetBit(0), n = 0; i >= 0; i = bsAtoms
            .nextSetBit(i + 1))
          mol.appendI(++n).append(" ").appendF(pc[i]).appendC('\n');
      }
    }
    return true;
  }

  private static BS getCovalentBondsForAtoms(Bond[] bonds, int bondCount, BS bsAtoms) {
    BS bsBonds = new BS();
    for (int i = 0; i < bondCount; i++) {
      Bond bond = bonds[i];
      if (bsAtoms.get(bond.atom1.index) && bsAtoms.get(bond.atom2.index)
          && bond.isCovalent())
        bsBonds.set(i);
    }
    return bsBonds;
  }

  /*
  L-Alanine
  GSMACCS-II07189510252D 1 0.00366 0.00000 0
  Figure 1, J. Chem. Inf. Comput. Sci., Vol 32, No. 3., 1992
  0 0 0 0 0 999 V3000
  M  V30 BEGIN CTAB
  M  V30 COUNTS 6 5 0 0 1
  M  V30 BEGIN ATOM
  M  V30 1 C -0.6622 0.5342 0 0 CFG=2
  M  V30 2 C 0.6622 -0.3 0 0
  M  V30 3 C -0.7207 2.0817 0 0 MASS=13
  M  V30 4 N -1.8622 -0.3695 0 0 CHG=1
  M  V30 5 O 0.622 -1.8037 0 0
  M  V30 6 O 1.9464 0.4244 0 0 CHG=-1
  M  V30 END ATOM
  M  V30 BEGIN BOND
  M  V30 1 1 1 2
  M  V30 2 1 1 3 CFG=1
  M  V30 3 1 1 4
  M  V30 4 2 2 5
  M  V30 5 1 2 6
  M  V30 END BOND
  M  V30 END CTAB
  M  END
   */

  private void getAtomRecordMOL(ModelSet ms, SB mol, int n, Atom a, Quaternion q,
                                P3 pTemp, P3 ptTemp, boolean asV3000,
                                boolean asJSON) {
    //   -0.9920    3.2030    9.1570 Cl  0  0  0  0  0
    //    3.4920    4.0920    5.8700 Cl  0  0  0  0  0
    //012345678901234567890123456789012
    
    if (ms.models[a.modelIndex].isTrajectory)
      a.setFractionalCoordPt(ptTemp, ms.trajectorySteps.get(a.modelIndex)[a.index
          - ms.models[a.modelIndex].firstAtomIndex], true);
    else
      pTemp.setT(a);
    if (q != null)
      q.transformP2(pTemp, pTemp);
    int elemNo = a.getElementNumber();
    String sym = (a.isDeleted() ? "Xx" : Elements
        .elementSymbolFromNumber(elemNo));
    int iso = a.getIsotopeNumber();
    int charge = a.getFormalCharge();
    if (asV3000) {
      mol.append("M  V30 ").appendI(n).append(" ").append(sym).append(" ")
          .appendF(pTemp.x).append(" ").appendF(pTemp.y).append(" ").appendF(
              pTemp.z).append(" 0");
      if (charge != 0)
        mol.append(" CHG=").appendI(charge);
      if (iso != 0)
        mol.append(" MASS=").appendI(iso);
      mol.append("\n");
    } else if (asJSON) {
      if (n != 1)
        mol.append(",");
      mol.append("{");
      if (a.getElementNumber() != 6)
        mol.append("\"l\":\"").append(a.getElementSymbol()).append("\",");
      if (charge != 0)
        mol.append("\"c\":").appendI(charge).append(",");
      if (iso != 0 && iso != Elements.getNaturalIsotope(elemNo))
        mol.append("\"m\":").appendI(iso).append(",");
      mol.append("\"x\":").appendF(a.x).append(",\"y\":").appendF(a.y).append(
          ",\"z\":").appendF(a.z).append("}");
    } else {
      mol.append(Txt.sprintf("%10.5p%10.5p%10.5p",
          "p", new Object[] {pTemp }));
      mol.append(" ").append(sym);
      if (sym.length() == 1)
        mol.append(" ");
      if (iso > 0)
        iso -= Elements.getNaturalIsotope(a.getElementNumber());
      mol.append(" ");
      Txt.rightJustify(mol, "  ", "" + iso);
      Txt.rightJustify(mol, "   ", "" + (charge == 0 ? 0 : 4 - charge));
      mol.append("  0  0  0  0\n");
    }
  }

  private void getBondRecordMOL(SB mol, int n, Bond b, int[] atomMap,
                                boolean asV3000, boolean asJSON) {
    //  1  2  1  0
    int a1 = atomMap[b.atom1.index];
    int a2 = atomMap[b.atom2.index];
    int order = b.getValence();
    if (order > 3)
      order = 1;
    switch (b.order & ~JmolEdge.BOND_NEW) {
    case JmolEdge.BOND_AROMATIC:
      order = (asJSON ? -3 : 4);
      break;
    case JmolEdge.BOND_PARTIAL12:
      order = (asJSON ? -3 : 5);
      break;
    case JmolEdge.BOND_AROMATIC_SINGLE:
      order = (asJSON ? 1: 6);
      break;
    case JmolEdge.BOND_AROMATIC_DOUBLE:
      order = (asJSON ? 2: 7);
      break;
    case JmolEdge.BOND_PARTIAL01:
      order = (asJSON ? -1: 8);
      break;
    }
    if (asV3000) {
      mol.append("M  V30 ").appendI(n).append(" ").appendI(order).append(" ")
          .appendI(a1).append(" ").appendI(a2).appendC('\n');
    } else if (asJSON) {
      if (n != 1)
        mol.append(",");
      mol.append("{\"b\":").appendI(a1 - 1).append(",\"e\":").appendI(a2 - 1);
      if (order != 1) {
        mol.append(",\"o\":");
        if (order < 0) {
          mol.appendF(-order / 2f);
        } else {
          mol.appendI(order);   
        }
      }
      mol.append("}");
    } else {
      Txt.rightJustify(mol, "   ", "" + a1);
      Txt.rightJustify(mol, "   ", "" + a2);
      mol.append("  ").appendI(order).append("  0  0  0\n");
    }
  }

  @Override
  public String getChimeInfo(int tok, BS bs) {
    switch (tok) {
    case T.info:
      break;
    case T.basepair:
      return getBasePairInfo(bs);
    default:
      return getChimeInfoA(viewer.modelSet.atoms, tok, bs);
    }
    SB sb = new SB();
    viewer.modelSet.models[0].getChimeInfo(sb, 0);
    return sb.appendC('\n').toString().substring(1);
  }

  private String getChimeInfoA(Atom[] atoms, int tok, BS bs) {
    SB info = new SB();
    info.append("\n");
    String s = "";
    Chain clast = null;
    Group glast = null;
    int modelLast = -1;
    int n = 0;
    if (bs != null)
      for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
        Atom a = atoms[i];
        switch (tok) {
        default:
          return "";
        case T.selected:
          s = a.getInfo();
          break;
        case T.atoms:
          s = "" + a.getAtomNumber();
          break;
        case T.group:
          s = a.getGroup3(false);
          break;
        case T.chain:
        case T.residue:
        case T.sequence:
          int id = a.getChainID();
          s = (id == 0 ? " " : a.getChainIDStr());
          if (id > 255)
            s = Escape.eS(s);
          switch (tok) {
          case T.residue:
            s = "[" + a.getGroup3(false) + "]"
                + a.getSeqcodeString() + ":" + s;
            break;
          case T.sequence:
            if (a.getModelIndex() != modelLast) {
              info.appendC('\n');
              n = 0;
              modelLast = a.getModelIndex();
              info.append("Model " + a.getModelNumber());
              glast = null;
              clast = null;
            }
            if (a.getChain() != clast) {
              info.appendC('\n');
              n = 0;
              clast = a.getChain();
              info.append("Chain " + s + ":\n");
              glast = null;
            }
            Group g = a.getGroup();
            if (g != glast) {
              if ((n++) % 5 == 0 && n > 1)
                info.appendC('\n');
              Txt.leftJustify(info, "          ", "["
                  + a.getGroup3(false) + "]" + a.getResno() + " ");
              glast = g;
            }
            continue;
          }
          break;
        }        
        if (info.indexOf("\n" + s + "\n") < 0)
          info.append(s).appendC('\n');
      }
    if (tok == T.sequence)
      info.appendC('\n');
    return info.toString().substring(1);
  }

  @Override
  public String getModelFileInfo(BS frames) {
    ModelSet ms = viewer.modelSet;
    SB sb = new SB();
    for (int i = 0; i < ms.modelCount; ++i) {
      if (frames != null && !frames.get(i))
        continue;
      String s = "[\"" + ms.getModelNumberDotted(i) + "\"] = ";
      sb.append("\n\nfile").append(s).append(Escape.eS(ms.getModelFileName(i)));
      String id = (String) ms.getModelAuxiliaryInfoValue(i, "modelID");
      if (id != null)
        sb.append("\nid").append(s).append(Escape.eS(id));
      sb.append("\ntitle").append(s).append(Escape.eS(ms.getModelTitle(i)));
      sb.append("\nname").append(s).append(Escape.eS(ms.getModelName(i)));
      sb.append("\ntype").append(s).append(Escape.eS(ms.getModelFileType(i)));
    }
    return sb.toString();
  }

  public List<Map<String, Object>> getAllAtomInfo(BS bs) {
    List<Map<String, Object>> V = new  List<Map<String, Object>>();
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
      V.addLast(getAtomInfoLong(i));
    }
    return V;
  }

  private Map<String, Object> getAtomInfoLong(int i) {
    ModelSet ms = viewer.modelSet;
    Atom atom = ms.atoms[i];
    Map<String, Object> info = new Hashtable<String, Object>();
    viewer.getAtomIdentityInfo(i, info);
    info.put("element", ms.getElementName(i));
    info.put("elemno", Integer.valueOf(ms.getElementNumber(i)));
    info.put("x", Float.valueOf(atom.x));
    info.put("y", Float.valueOf(atom.y));
    info.put("z", Float.valueOf(atom.z));
    info.put("coord", P3.newP(atom));
    if (ms.vibrations != null && ms.vibrations[i] != null)
      ms.vibrations[i].getInfo(info);
    info.put("bondCount", Integer.valueOf(atom.getCovalentBondCount()));
    info.put("radius", Float.valueOf((float) (atom.getRasMolRadius() / 120.0)));
    info.put("model", atom.getModelNumberForLabel());
    String shape = Atom.atomPropertyString(viewer, atom, T.shape);
    if (shape != null)
      info.put("shape", shape);
    info.put("visible", Boolean.valueOf(atom.isVisible(0)));
    info.put("clickabilityFlags", Integer.valueOf(atom.clickabilityFlags));
    info.put("visibilityFlags", Integer.valueOf(atom.shapeVisibilityFlags));
    info.put("spacefill", Float.valueOf(atom.getRadius()));
    String strColor = Escape.escapeColor(viewer
        .getColorArgbOrGray(atom.colixAtom));
    if (strColor != null)
      info.put("color", strColor);
    info.put("colix", Integer.valueOf(atom.colixAtom));
    boolean isTranslucent = atom.isTranslucent();
    if (isTranslucent)
      info.put("translucent", Boolean.valueOf(isTranslucent));
    info.put("formalCharge", Integer.valueOf(atom.getFormalCharge()));
    info.put("partialCharge", Float.valueOf(atom.getPartialCharge()));
    float d = atom.getSurfaceDistance100() / 100f;
    if (d >= 0)
      info.put("surfaceDistance", Float.valueOf(d));
    if (ms.models[atom.modelIndex].isBioModel) {
      info.put("resname", atom.getGroup3(false));
      char insCode = atom.getInsertionCode();
      int seqNum = atom.getResno();
      if (seqNum > 0)
        info.put("resno", Integer.valueOf(seqNum));
      if (insCode != 0)
        info.put("insertionCode", "" + insCode);
      info.put("name", ms.getAtomName(i));
      info.put("chain", atom.getChainIDStr());
      info.put("atomID", Integer.valueOf(atom.atomID));
      info.put("groupID", Integer.valueOf(atom.getGroupID()));
      if (atom.altloc != '\0')
        info.put("altLocation", "" + atom.altloc);
      info.put("structure", Integer.valueOf(atom.getProteinStructureType()
          .getId()));
      info.put("polymerLength", Integer.valueOf(atom.getPolymerLength()));
      info.put("occupancy", Integer.valueOf(atom.getOccupancy100()));
      int temp = atom.getBfactor100();
      info.put("temp", Integer.valueOf(temp / 100));
    }
    return info;
  }

  public List<Map<String, Object>> getAllBondInfo(Object bsOrArray) {
    List<Map<String, Object>> v = new List<Map<String, Object>>();
    ModelSet ms = viewer.modelSet;
    int bondCount = ms.bondCount;
    Bond[] bonds = ms.bonds;
    BS bs1;
    if (bsOrArray instanceof String) {
      bsOrArray = viewer.getAtomBitSet(bsOrArray);
    }
    if (bsOrArray instanceof BS[]) {
      bs1 = ((BS[]) bsOrArray)[0];
      BS bs2 = ((BS[]) bsOrArray)[1];
      for (int i = 0; i < bondCount; i++) {
        int ia = bonds[i].atom1.index;
        int ib = bonds[i].atom2.index;
        if (bs1.get(ia) && bs2.get(ib) || bs2.get(ia) && bs1.get(ib))
          v.addLast(getBondInfo(i));
      }
    } else if (bsOrArray instanceof BondSet) {
      bs1 = (BS) bsOrArray;
      for (int i = bs1.nextSetBit(0); i >= 0 && i < bondCount; i = bs1
          .nextSetBit(i + 1))
        v.addLast(getBondInfo(i));
    } else if (bsOrArray instanceof BS){
      bs1 = (BS) bsOrArray;
      int thisAtom = (bs1.cardinality() == 1 ? bs1.nextSetBit(0) : -1);
      for (int i = 0; i < bondCount; i++) {
        if (thisAtom >= 0 ? (bonds[i].atom1.index == thisAtom || bonds[i].atom2.index == thisAtom)
            : bs1.get(bonds[i].atom1.index) && bs1.get(bonds[i].atom2.index))
          v.addLast(getBondInfo(i));
      }
    }
    return v;
  }

  private Map<String, Object> getBondInfo(int i) {
    Bond bond = viewer.modelSet.bonds[i];
    Atom atom1 = bond.atom1;
    Atom atom2 = bond.atom2;
    Map<String, Object> info = new Hashtable<String, Object>();
    info.put("_bpt", Integer.valueOf(i));
    Map<String, Object> infoA = new Hashtable<String, Object>();
    viewer.getAtomIdentityInfo(atom1.index, infoA);
    Map<String, Object> infoB = new Hashtable<String, Object>();
    viewer.getAtomIdentityInfo(atom2.index, infoB);
    info.put("atom1", infoA);
    info.put("atom2", infoB);
    info.put("order", Float.valueOf(PT.fVal(JmolEdge
        .getBondOrderNumberFromOrder(bond.order))));
    info.put("type", JmolEdge.getBondOrderNameFromOrder(bond.order));
    info.put("radius", Float.valueOf((float) (bond.mad / 2000.)));
    info.put("length_Ang", Float.valueOf(atom1.distance(atom2)));
    info.put("visible", Boolean.valueOf(bond.shapeVisibilityFlags != 0));
    String strColor = Escape.escapeColor(viewer.getColorArgbOrGray(bond.colix));
    if (strColor != null)
      info.put("color", strColor);
    info.put("colix", Integer.valueOf(bond.colix));
    if (C.isColixTranslucent(bond.colix))
      info.put("translucent", Boolean.TRUE);
    return info;
  }

  public Map<String, List<Map<String, Object>>> getAllChainInfo(BS bs) {
    Map<String, List<Map<String, Object>>> finalInfo = new Hashtable<String, List<Map<String, Object>>>();
    List<Map<String, Object>> modelVector = new  List<Map<String, Object>>();
    int modelCount = viewer.modelSet.modelCount;
    for (int i = 0; i < modelCount; ++i) {
      Map<String, Object> modelInfo = new Hashtable<String, Object>();
      List<Map<String, List<Map<String, Object>>>> info = getChainInfo(i, bs);
      if (info.size() > 0) {
        modelInfo.put("modelIndex", Integer.valueOf(i));
        modelInfo.put("chains", info);
        modelVector.addLast(modelInfo);
      }
    }
    finalInfo.put("models", modelVector);
    return finalInfo;
  }

  private List<Map<String, List<Map<String, Object>>>> getChainInfo(
                                                                    int modelIndex,
                                                                    BS bs) {
    Model model = viewer.modelSet.models[modelIndex];
    int nChains = model.getChainCount(true);
    List<Map<String, List<Map<String, Object>>>> infoChains = new  List<Map<String, List<Map<String, Object>>>>();
    for (int i = 0; i < nChains; i++) {
      Chain chain = model.getChainAt(i);
      List<Map<String, Object>> infoChain = new  List<Map<String, Object>>();
      int nGroups = chain.getGroupCount();
      Map<String, List<Map<String, Object>>> arrayName = new Hashtable<String, List<Map<String, Object>>>();
      for (int igroup = 0; igroup < nGroups; igroup++) {
        Group group = chain.getGroup(igroup);
        if (bs.get(group.firstAtomIndex))
          infoChain.addLast(group.getGroupInfo(igroup));
      }
      if (!infoChain.isEmpty()) {
        arrayName.put("residues", infoChain);
        infoChains.addLast(arrayName);
      }
    }
    return infoChains;
  }

  public Map<String, List<Map<String, Object>>> getAllPolymerInfo(BS bs) {
    Map<String, List<Map<String, Object>>> finalInfo = new Hashtable<String, List<Map<String, Object>>>();
    List<Map<String, Object>> modelVector = new  List<Map<String, Object>>();
    int modelCount = viewer.modelSet.modelCount;
    Model[] models = viewer.modelSet.models;
    for (int i = 0; i < modelCount; ++i)
      if (models[i].isBioModel)
        models[i].getAllPolymerInfo(bs, finalInfo, modelVector);
    finalInfo.put("models", modelVector);
    return finalInfo;
  }

  private String getBasePairInfo(BS bs) {
    SB info = new SB();
    List<Bond> vHBonds = new  List<Bond>();
    viewer.modelSet.calcRasmolHydrogenBonds(bs, bs, vHBonds, true, 1, false, null);
    for (int i = vHBonds.size(); --i >= 0;) {
      Bond b = vHBonds.get(i);
      getAtomResidueInfo(info, b.atom1);
      info.append(" - ");
      getAtomResidueInfo(info, b.atom2);
      info.append("\n");
    }
    return info.toString();
  }

  private static void getAtomResidueInfo(SB info, Atom atom) {
    info.append("[").append(atom.getGroup3(false)).append("]").append(
        atom.getSeqcodeString()).append(":");
    int id = atom.getChainID();
    info.append(id == 0 ? " " : atom.getChainIDStr());
  }

}
