 /* $RCSfile$
 * $Author: egonw $
 * $Date: 2005-11-10 09:52:44 -0600 (Thu, 10 Nov 2005) $
 * $Revision: 4255 $
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
package org.jmol.viewer;


import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.jmol.api.JmolSceneGenerator;
import org.jmol.java.BS;
import org.jmol.modelset.Bond;
import org.jmol.modelset.ModelSet;
import org.jmol.modelset.Orientation;
import org.jmol.script.SV;

import org.jmol.util.BSUtil;

import javajs.J2SIgnoreImport;
import javajs.util.SB;

import java.util.Arrays;
import java.util.Map.Entry;

@J2SIgnoreImport({Runtime.class})
public class StateManager {

  /* steps in adding a global variable:
   
   In Viewer:
   
   1. add a check in setIntProperty or setBooleanProperty or setFloat.. or setString...
   2. create new set/get methods
   
   In StateManager
   
   3. create the global.xxx variable
   4. in registerParameter() register it so that it shows up as having a value in math
   
   */

  public final static int OBJ_BACKGROUND = 0;
  public final static int OBJ_AXIS1 = 1;
  public final static int OBJ_AXIS2 = 2;
  public final static int OBJ_AXIS3 = 3;
  public final static int OBJ_BOUNDBOX = 4;
  public final static int OBJ_UNITCELL = 5;
  public final static int OBJ_FRANK = 6;
  public final static int OBJ_MAX = 8;
  private final static String objectNameList = "background axis1      axis2      axis3      boundbox   unitcell   frank      ";

  public static String getVariableList(Map<String, SV> htVariables, int nMax,
                                       boolean withSites, boolean definedOnly) {
    SB sb = new SB();
    // user variables only:
    int n = 0;

    String[] list = new String[htVariables.size()];
    for (Map.Entry<String, SV> entry : htVariables.entrySet()) {
      String key = entry.getKey();
      SV var = entry.getValue();
      if ((withSites || !key.startsWith("site_")) && (!definedOnly || key.charAt(0) == '@'))
        list[n++] = key
            + (key.charAt(0) == '@' ? " " + var.asString() : " = "
                + varClip(key, var.escape(), nMax));
    }
    Arrays.sort(list, 0, n);
    for (int i = 0; i < n; i++)
      if (list[i] != null)
        sb.append("  ").append(list[i]).append(";\n");
    if (n == 0 && !definedOnly)
      sb.append("# --no global user variables defined--;\n");
    return sb.toString();
  }
  
  public static int getObjectIdFromName(String name) {
    if (name == null)
      return -1;
    int objID = objectNameList.indexOf(name.toLowerCase());
    return (objID < 0 ? objID : objID / 11);
  }

  static String getObjectNameFromId(int objId) {
    if (objId < 0 || objId >= OBJ_MAX)
      return null;
    return objectNameList.substring(objId * 11, objId * 11 + 11).trim();
  }

  protected final Viewer viewer;
  protected Map<String, Object> saved = new Hashtable<String, Object>();
  
  private String lastOrientation = "";
  private String lastConnections = "";
  private String lastScene = "";
  private String lastSelected = "";
  private String lastState = "";
  private String lastShape = "";
  private String lastCoordinates = "";

  StateManager(Viewer viewer) {
    this.viewer = viewer;
  }
  
  GlobalSettings getGlobalSettings(GlobalSettings gsOld, boolean clearUserVariables) {
    saved.clear();
    return new GlobalSettings(viewer, gsOld, clearUserVariables);
  }

  void clear(GlobalSettings global) {
    viewer.setShowAxes(false);
    viewer.setShowBbcage(false);
    viewer.setShowUnitCell(false);
    global.clear();
  }

  void setCrystallographicDefaults() {
    //axes on and mode unitCell; unitCell on; perspective depth off;
    viewer.setAxesModeUnitCell(true);
    viewer.setShowAxes(true);
    viewer.setShowUnitCell(true);
    viewer.setBooleanProperty("perspectiveDepth", false);
  }

  private void setCommonDefaults() {
    viewer.setBooleanProperty("perspectiveDepth", true);
    viewer.setFloatProperty("bondTolerance",
        JC.DEFAULT_BOND_TOLERANCE);
    viewer.setFloatProperty("minBondDistance",
        JC.DEFAULT_MIN_BOND_DISTANCE);
    viewer.setBooleanProperty("translucent", true);
  }

  void setJmolDefaults() {
    setCommonDefaults();
    viewer.setStringProperty("ColorScheme", "Jmol");
    viewer.setBooleanProperty("axesOrientationRasmol", false);
    viewer.setBooleanProperty("zeroBasedXyzRasmol", false);
    viewer.setIntProperty("percentVdwAtom",
        JC.DEFAULT_PERCENT_VDW_ATOM);
    viewer.setIntProperty("bondRadiusMilliAngstroms",
        JC.DEFAULT_BOND_MILLIANGSTROM_RADIUS);
    viewer.setVdwStr("auto");
  }

  void setRasMolDefaults() {
    setCommonDefaults();
    viewer.setStringProperty("defaultColorScheme", "RasMol");
    viewer.setBooleanProperty("axesOrientationRasmol", true);
    viewer.setBooleanProperty("zeroBasedXyzRasmol", true);
    viewer.setIntProperty("percentVdwAtom", 0);
    viewer.setIntProperty("bondRadiusMilliAngstroms", 1);
    viewer.setVdwStr("Rasmol");
  }

  void setPyMOLDefaults() {
    setCommonDefaults();
    viewer.setStringProperty("measurementUnits", "ANGSTROMS");
    viewer.setBooleanProperty("zoomHeight", true);
  }

  private static Object getNoCase(Map<String, Object> saved, String name) {
    for (Entry<String, Object> e : saved.entrySet())
      if (e.getKey().equalsIgnoreCase(name))
        return e.getValue();
   return null;
  }

  String listSavedStates() {
    String names = "";
    for (String name: saved.keySet())
      names += "\n" + name;
    return names;
  }

  private void deleteSavedType(String type) {
    Iterator<String> e = saved.keySet().iterator();
    while (e.hasNext()) {
      String name = e.next();
      if (name.startsWith(type)) {
        e.remove();
      }
    }
  }

  void deleteSaved(String name) {
    saved.remove(name);
  }
  
  void saveSelection(String saveName, BS bsSelected) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("Selected_");
      return;
    }
    saveName = lastSelected = "Selected_" + saveName;
    saved.put(saveName, BSUtil.copy(bsSelected));
  }

  boolean restoreSelection(String saveName) {
    String name = (saveName.length() > 0 ? "Selected_" + saveName
        : lastSelected);
    BS bsSelected = (BS) getNoCase(saved, name);
    if (bsSelected == null) {
      viewer.select(new BS(), false, 0, false);
      return false;
    }
    viewer.select(bsSelected, false, 0, false);
    return true;
  }

  void saveState(String saveName) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("State_");
      return;
    }
    saveName = lastState = "State_" + saveName;
    saved.put(saveName, viewer.getStateInfo());
  }

  String getSavedState(String saveName) {
    String name = (saveName.length() > 0 ? "State_" + saveName : lastState);
    String script = (String) getNoCase(saved, name);
    return (script == null ? "" : script);
  }

  /*  
   boolean restoreState(String saveName) {
   //not used -- more efficient just to run the script 
   String name = (saveName.length() > 0 ? "State_" + saveName
   : lastState);
   String script = (String) getNoCase(saved, name);
   if (script == null)
   return false;
   viewer.script(script + CommandHistory.NOHISTORYATALL_FLAG);
   return true;
   }
   */
  void saveStructure(String saveName) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("Shape_");
      return;
    }
    saveName = lastShape = "Shape_" + saveName;
    saved.put(saveName, viewer.getStructureState());
  }

  String getSavedStructure(String saveName) {
    String name = (saveName.length() > 0 ? "Shape_" + saveName : lastShape);
    String script = (String) getNoCase(saved, name);
    return (script == null ? "" : script);
  }

  void saveCoordinates(String saveName, BS bsSelected) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("Coordinates_");
      return;
    }
    saveName = lastCoordinates = "Coordinates_" + saveName;
    saved.put(saveName, viewer.getCoordinateState(bsSelected));
  }

  String getSavedCoordinates(String saveName) {
    String name = (saveName.length() > 0 ? "Coordinates_" + saveName
        : lastCoordinates);
    String script = (String) getNoCase(saved, name);
    return (script == null ? "" : script);
  }

  Orientation getOrientation() {
    return new Orientation(viewer, false, null);
  }

  String getSavedOrientationText(String saveName) {
    Orientation o;
    if (saveName != null) {
      o = getOrientationFor(saveName);
      return (o == null ? "" : o.getMoveToText(true));
    }
    SB sb = new SB();
    for (Entry<String, Object> e : saved.entrySet()) {
      String name = e.getKey();
      if (name.startsWith("Orientation_"))
        sb.append(((Orientation) e.getValue()).getMoveToText(true));
    }
    return sb.toString();
  }

  void saveScene(String saveName, Map<String, Object> scene) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("Scene_");
      return;
    }
    Scene o = new Scene(scene);
    o.saveName = lastScene = "Scene_" + saveName;
    saved.put(o.saveName, o);
  }

  boolean restoreScene(String saveName, float timeSeconds) {
    Scene o = getSceneFor(saveName);
    return (o != null && o.restore(timeSeconds));
  }

  private Scene getSceneFor(String saveName) {
    String name = (saveName.length() > 0 ? "Scene_" + saveName
        : lastScene);    
    return (Scene) getNoCase(saved, name);
  }

  private class Scene {
    protected String  saveName;
    private Map<String, Object> scene;
    
    protected Scene(Map<String, Object> scene) {
      this.scene = scene;
    }

    protected boolean restore(float timeSeconds) {
      JmolSceneGenerator gen = (JmolSceneGenerator) scene.get("generator");
      if (gen != null)
        gen.generateScene(scene);
      float[] pv = (float[]) scene.get("pymolView");
      return (pv != null && viewer.movePyMOL(viewer.eval, timeSeconds, pv));
    }
  }

  void saveOrientation(String saveName, float[] pymolView) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("Orientation_");
      return;
    }
    Orientation o = new Orientation(viewer, saveName.equalsIgnoreCase("default"), pymolView);
    o.saveName = lastOrientation = "Orientation_" + saveName;
    saved.put(o.saveName, o);
  }
  
  boolean restoreOrientation(String saveName, float timeSeconds, boolean isAll) {
    Orientation o = getOrientationFor(saveName);
    return (o != null && o.restore(timeSeconds, isAll));
  }

  private Orientation getOrientationFor(String saveName) {
    String name = (saveName.length() > 0 ? "Orientation_" + saveName
        : lastOrientation);    
    return (Orientation) getNoCase(saved, name);
  }

  void saveBonds(String saveName) {
    if (saveName.equalsIgnoreCase("DELETE")) {
      deleteSavedType("Bonds_");
      return;
    }
    Connections b = new Connections();
    b.saveName = lastConnections = "Bonds_" + saveName;
    saved.put(b.saveName, b);
  }

  boolean restoreBonds(String saveName) {
    String name = (saveName.length() > 0 ? "Bonds_" + saveName
        : lastConnections);
    Connections c = (Connections) getNoCase(saved, name);
    return (c != null && c.restore());
  }

  private class Connections {

    protected String saveName;
    protected int bondCount;
    protected Connection[] connections;

    protected Connections() {
      ModelSet modelSet = viewer.getModelSet();
      if (modelSet == null)
        return;
      bondCount = modelSet.bondCount;
      connections = new Connection[bondCount + 1];
      Bond[] bonds = modelSet.bonds;
      for (int i = bondCount; --i >= 0;) {
        Bond b = bonds[i];
        connections[i] = new Connection(b.getAtomIndex1(), b.getAtomIndex2(), b
            .mad, b.colix, b.order, b.getEnergy(), b.getShapeVisibilityFlags());
      }
    }

    protected boolean restore() {
      ModelSet modelSet = viewer.getModelSet();
      if (modelSet == null)
        return false;
      modelSet.deleteAllBonds();
      for (int i = bondCount; --i >= 0;) {
        Connection c = connections[i];
        int atomCount = modelSet.getAtomCount();
        if (c.atomIndex1 >= atomCount || c.atomIndex2 >= atomCount)
          continue;
        Bond b = modelSet.bondAtoms(modelSet.atoms[c.atomIndex1],
            modelSet.atoms[c.atomIndex2], c.order, c.mad, null, c.energy, false, true);
        b.setColix(c.colix);
        b.setShapeVisibilityFlags(c.shapeVisibilityFlags);
      }
      for (int i = bondCount; --i >= 0;)
        modelSet.getBondAt(i).setIndex(i);
      viewer.setShapeProperty(JC.SHAPE_STICKS, "reportAll", null);
      return true;
    }
  }

  private class Connection {
    protected int atomIndex1;
    protected int atomIndex2;
    protected short mad;
    protected short colix;
    protected int order;
    protected float energy;
    protected int shapeVisibilityFlags;

    protected Connection(int atom1, int atom2, short mad, short colix, int order, float energy,
        int shapeVisibilityFlags) {
      atomIndex1 = atom1;
      atomIndex2 = atom2;
      this.mad = mad;
      this.colix = colix;
      this.order = order;
      this.energy = energy;
      this.shapeVisibilityFlags = shapeVisibilityFlags;
    }
  }

  public static String varClip(String name, String sv, int nMax) {
    if (nMax > 0 && sv.length() > nMax)
      sv = sv.substring(0, nMax) + " #...more (" + sv.length()
          + " bytes -- use SHOW " + name + " or MESSAGE @" + name
          + " to view)";
    return sv;
  }


}
