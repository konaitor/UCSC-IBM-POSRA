/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-12-02 23:05:12 -0600 (Mon, 02 Dec 2013) $
 * $Revision: 19049 $
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

package org.jmol.api;

import java.io.Reader;
import java.net.URL;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jmol.java.BS;
import org.jmol.util.BoxInfo;

import javajs.api.GenericPlatform;
import javajs.api.JSInterface;
import javajs.awt.Dimension;
import javajs.util.OC;
import javajs.util.M4;
import javajs.util.P3;
import javajs.util.V3;
import org.jmol.viewer.Viewer;

/**
 * JmolViewer is the main API for the Viewer class.
 * (Mosty) we try to not change this (much), whereas the varius 
 * "public" methods of Viewer, ModelSet, and other classes are public
 * only for internal cross-package access, not for external use.
 *
 **/

abstract public class JmolViewer implements JSInterface {

  // several; methods were deprecated and removed in 13.1.15. All are accessible via "getXxxx" methods:
  
  abstract public float getFloat(int tok);
  abstract public int getInt(int tok);
  abstract public boolean getBoolean(int tok);

  //abstract public int getAnimationFps();  see getInt(T.animationFps)
  //abstract public boolean getShowHydrogens(); see getBoolean(T.showhydrogens)
  //abstract public boolean getShowMeasurements(); see getBoolean(T.showmeasurements)
  //abstract public boolean getAxesOrientationRasmol(); see getBoolean(T.axesorientationrasmol)
  //abstract public int getPercentVdwAtom(); see getInt(T.percentvdwatom)
  //abstract public boolean getAutoBond(); see getBoolean(T.autobond))
  //abstract public boolean showModelSetDownload(); deprecated -- was just "true"
  
  /**
   * This is the older main access point for creating an application or applet viewer.
   * 
   * You can also use That is necessary when
   * compiled into JavaScript using Java2Script
   * 
   * In Jmol 11.6 it was manditory that one of the next commands is either
   * 
   * viewer.evalString("ZAP");
   * 
   * or at least:
   * 
   * viewer.setAppletContext("",null,null,"")
   * 
   * One or the other of these was necessary to establish the first modelset,
   * which might be required by one or more later evaluated commands or file
   * loadings.
   * 
   * Starting with Jmol 11.7, setAppletContext is rolled into allocateViewer so
   * that the full initialization is done all at once.
   * 
   * Starting with Jmol 12.3.13, we allow for preconstructed ApiPlatform
   * 
   * 
   * @param display
   * @param modelAdapter
   * @param fullName
   * @param documentBase
   * @param codeBase
   * @param commandOptions
   * @param statusListener
   * @param implementedPlatform
   * @return a JmolViewer object
   */
  protected static JmolViewer allocateViewer(Object display,
                                          JmolAdapter modelAdapter,
                                          String fullName, URL documentBase,
                                          URL codeBase, String commandOptions,
                                          JmolStatusListener statusListener,
                                          GenericPlatform implementedPlatform) {
    Map<String, Object> info = new Hashtable<String, Object>();
    if (display != null)
      info.put("display", display);
    if (modelAdapter != null)
      info.put("adapter", modelAdapter);
    if (statusListener != null)
      info.put("statuslistener", statusListener);
    if (implementedPlatform != null)
      info.put("platform", implementedPlatform);
    if (commandOptions != null)
       info.put("options", commandOptions);
    if (fullName != null)
      info.put("fullname", fullName);
    if (documentBase != null)
      info.put("documentbase", documentBase);
    if (codeBase != null)
      info.put("codebase", codeBase);    
    return new Viewer(info);
  }
  
  /**
   * a simpler option
   * 
   * @param container
   * @param jmolAdapter
   * @return JmolViewer object
   */
  public static JmolViewer allocateViewer(Object container, JmolAdapter jmolAdapter) {
    return allocateViewer(container, jmolAdapter, null, null, null, null, null, null);
  }
  
  /**
   * legacy only
   * 
   * @param display
   * @param modelAdapter
   * @param fullName
   * @param documentBase
   * @param codeBase
   * @param commandOptions
   * @param statusListener
   * @return JmolViewer object
   */
  public static JmolViewer allocateViewer(Object display,
                                          JmolAdapter modelAdapter,
                                          String fullName, URL documentBase,
                                          URL codeBase, String commandOptions,
                                          JmolStatusListener statusListener) {
    return allocateViewer(display, modelAdapter, fullName, documentBase,
        codeBase, commandOptions, statusListener, null);
  }

  /**
   * sets a custom console -- should be called IMMEDIATELY following allocateViewer
   * 
   * create your console with, perhaps:
   * 
   * new org.openscience.jmol.app.jmolPanel.AppConsole(viewer, displayFrame, 
   *                               externalJPanel, buttonsEnabled);
   * 
   * (see examples/basic/org/jmol/Integration.java
   * 
   * @param console        the console to use  
   * 
   */
  public void setConsole(JmolAppConsoleInterface console) {
    getProperty("DATA_API", "getAppConsole", console); 
  }

  abstract public void setInMotion(boolean isInMotion);

  abstract public BS getSmartsMatch(String smarts, BS bsSelected);
  
  /**
   * an added class for rendering stereo in two independent applets
   * 
   * @param gLeft
   * @param gRight
   * @param width 
   * @param height 
   */
  abstract public void renderScreenImageStereo(Object gLeft, Object gRight, int width, int height);

  static public String getJmolVersion() {
    return Viewer.getJmolVersion();
  }

  static public boolean checkOption(JmolViewer viewer, String option) {
    Object testFlag = viewer.getParameter(option);
    return (testFlag instanceof Boolean && ((Boolean) testFlag).booleanValue()
        || testFlag instanceof Integer && ((Integer) testFlag).intValue() != 0);
  }

  // for POV-Ray -- returns the INI file
  
  abstract public String generateOutputForExport(Map<String, Object> params); 
  
  abstract public void setJmolCallbackListener(JmolCallbackListener jmolCallbackListener);

  abstract public void setJmolStatusListener(JmolStatusListener jmolStatusListener);

  abstract public boolean checkHalt(String strCommand, boolean isInterrupt);
  abstract public void haltScriptExecution();

  abstract public String getOperatingSystemName();
  abstract public String getJavaVersion();
  abstract public String getJavaVendor();

  abstract public boolean haveFrame();

  abstract public void pushHoldRepaint();
  abstract public void popHoldRepaint(String why);

  // for example: getData("selected","XYZ");
  abstract public String getData(String atomExpression, String type);


  // change this to width, height
  @Override
  abstract public void setScreenDimension(int width, int height);
  abstract public int getScreenWidth();
  abstract public int getScreenHeight();

//  public Image getScreenImage() {
//    return getScreenImage(null);
//  }

  abstract public Object getScreenImageBuffer(Object g, boolean isImageWrite);
  abstract public void releaseScreenImage();
  
  abstract public void writeTextFile(String string, String data);
  
  /**
   * 
   * @param params include type, fileName, text, bytes, quality, width, height 
   * @return          null (canceled) or a message starting with OK or an error message
   */
  abstract public String outputToFile(Map<String, Object> params);

  /**
   * @param type 
   * @param width 
   * @param height 
   * @param quality 
   * @param errMsg TODO
   * @return base64-encoded or binary version of the image
   */
  abstract public byte[] getImageAsBytes(String type, int width, int height, int quality, String[] errMsg);

  abstract public int getMotionEventNumber();

  /**
   * Opens the file and creates the model set, given the reader.
   * 
   * name is a text name of the file ... to be displayed in the window no need
   * to pass a BufferedReader ... ... the FileManager will wrap a buffer around
   * it
   * 
   * DO NOT USE IN JMOL -- THIS METHOD IS ONLY HERE BECAUSE IT IS
   * PART OF THE LEGACY INTERFACE
   * IF USED BY ANOTHER APPLICATION, YOU ARE RESPONSIBLE FOR CLOSING THE READER
   * 
   * @param fullPathName
   * @param fileName
   * @param reader
   * @return       null or error message
   */
   
  abstract public String openReader(String fullPathName, String fileName, Reader reader);
  
  /*
   * REMOVED -- this method does not actually open the file
   * 
   * @param fullPathName
   * @param fileName
   * @param clientFile
   * @deprecated
   */
//  abstract public void openClientFile(String fullPathName, String fileName,
  //                           Object clientFile);

  abstract public void showUrl(String urlString);

  abstract public void calcAtomsMinMax(BS bs, BoxInfo boxInfo);
  abstract public P3 getBoundBoxCenter();
  abstract public V3 getBoundBoxCornerVector();
  abstract public int getMeasurementCount();
  abstract public String getMeasurementStringValue(int i);
  abstract public int[] getMeasurementCountPlusIndices(int i);

  abstract public Object getDisplay();

  abstract public BS getElementsPresentBitSet(int modelIndex);

  abstract public int findNearestAtomIndex(int x, int y);

  abstract public String script(String script);
  abstract public Object scriptCheck(String script);
  abstract public String scriptWait(String script);
  abstract public Object scriptWaitStatus(String script, String statusList);
  abstract public String loadInline(String strModel);
  abstract public String loadInlineAppend(String strModel, boolean isAppend);
  abstract public String loadInline(String strModel, char newLine);
  abstract public String loadInline(String[] arrayModels);
  /**
   * 
   * @param arrayModels and array of models, each of which is a String
   * @param isAppend
   * @return null or error message
   */
  abstract public String loadInline(String[] arrayModels, boolean isAppend);
  /**
   * 
   * NOTE: THIS METHOD DOES NOT PRESERVE THE STATE
   * 
   * @param arrayData a Vector of models, where each model is either a String
   *                  or a String[] or a Vector<String>
   * @param isAppend TRUE to append models (no ZAP)
   * @return null or error message
   */
  abstract public String loadInline(List<Object> arrayData, boolean isAppend);

  abstract public String evalStringQuiet(String script);
  abstract public boolean isScriptExecuting();

  abstract public String getModelSetName();
  abstract public String getModelSetFileName();
  abstract public String getModelSetPathName();
  abstract public String getEmbeddedFileState(String filename);
  abstract public String getFileAsString(String filename);
  abstract public Properties getModelSetProperties();
  abstract public Map<String, Object> getModelSetAuxiliaryInfo();
  abstract public int getModelNumber(int modelIndex);
  abstract public String getModelName(int modelIndex);
  abstract public String getModelNumberDotted(int modelIndex);
  abstract public Properties getModelProperties(int modelIndex);
  abstract public String getModelProperty(int modelIndex, String propertyName);
  abstract public Map<String, Object> getModelAuxiliaryInfo(int modelIndex);
  // changed in Jmol 13.1.5 to remove ambiguity in JavaScript
  abstract public Object getModelAuxiliaryInfoValue(int modelIndex, String keyName);
  abstract public boolean modelHasVibrationVectors(int modelIndex);

  abstract public int getModelCount();
  abstract public int getDisplayModelIndex();
  abstract public BS getVisibleFramesBitSet();
  abstract public int getAtomCount();
  abstract public int getBondCount(); // NOT THE REAL BOND COUNT -- just an array maximum
  abstract public int getGroupCount();
  abstract public int getChainCount();
  abstract public int getPolymerCount();
  abstract public int getAtomCountInModel(int modelIndex);
  abstract public int getBondCountInModel(int modelIndex);  // use -1 here for "all"
  abstract public int getGroupCountInModel(int modelIndex);
  abstract public int getChainCountInModel(int modelIindex);
  abstract public int getPolymerCountInModel(int modelIndex);
  abstract public int getSelectionCount();

  abstract public void addSelectionListener(JmolSelectionListener listener);
  abstract public void removeSelectionListener(JmolSelectionListener listener);
//BH 2/2006  abstract public BitSet getSelectionSet();

  abstract public void homePosition();

  abstract public Map<String, String> getHeteroList(int modelIndex);


  abstract public boolean getPerspectiveDepth();
  abstract public boolean getShowAxes(); 
  abstract public boolean getShowBbcage();

  abstract public int getAtomNumber(int atomIndex);
  abstract public String getAtomName(int atomIndex);
  abstract public String getAtomInfo(int atomIndex); // also gets measurement information for points

  abstract public int getZoomPercent(); //deprecated
  abstract public float getZoomPercentFloat();
  abstract public M4 getUnscaledTransformMatrix();

  abstract public int getBackgroundArgb();
  
  abstract public float getAtomRadius(int atomIndex);
  abstract public P3 getAtomPoint3f(int atomIndex);
  abstract public int getAtomArgb(int atomIndex);
  abstract public int getAtomModelIndex(int atomIndex);

  abstract public float getBondRadius(int bondIndex);
  abstract public P3 getBondPoint3f1(int bondIndex);
  abstract public P3 getBondPoint3f2(int bondIndex);
  abstract public int getBondArgb1(int bondIndex);
  abstract public int getBondArgb2(int bondIndex);
  abstract public int getBondOrder(int bondIndex);
  abstract public int getBondModelIndex(int bondIndex);

  abstract public P3[] getPolymerLeadMidPoints(int modelIndex, int polymerIndex);
  
  abstract public short getMadBond();

  abstract public void rebond();

  abstract public void refresh(int isOrientationChange, String strWhy);

  abstract public void notifyViewerRepaintDone();

  abstract public boolean getBooleanProperty(String propertyName);

  // removed for Jmol 13.1.7
  // public boolean getBooleanProperty(String key, boolean doICare) {
  //  return getBooleanProperty(key); // don't ask for what doesn't exist; you should care!
  // }
  abstract public Object getParameter(String name);

  abstract public String getSetHistory(int howFarBack);
  
  abstract public boolean havePartialCharges();

  abstract public boolean isApplet();

  abstract public String getAltLocListInModel(int modelIndex);

  abstract public String getStateInfo();
  
  abstract public void syncScript(String script, String applet, int port);  

  //but NOTE that if you use the following, you are
  //bypassing the script history. If you want a script history, use
  //viewer.script("set " + propertyName + " " + value);
  
  abstract public void setColorBackground(String colorName);
  abstract public void setShowAxes(boolean showAxes);
  abstract public void setShowBbcage(boolean showBbcage);
  abstract public void setJmolDefaults();
  abstract public void setRasmolDefaults();

  abstract public void setBooleanProperty(String propertyName, boolean value);
  abstract public void setIntProperty(String propertyName, int value);
  abstract public void setFloatProperty(String propertyName, float value);
  abstract public void setStringProperty(String propertyName, String value);

  abstract public void setModeMouse(int modeMouse); //only MOUSEMODE_NONE, prior to nulling viewer

  abstract public void setShowHydrogens(boolean showHydrogens);
  abstract public void setShowMeasurements(boolean showMeasurements);
  abstract public void setPerspectiveDepth(boolean perspectiveDepth);
  abstract public void setAutoBond(boolean autoBond);
  abstract public void setMarBond(short marBond);
  abstract public void setBondTolerance(float bondTolerance);
  abstract public void setMinBondDistance(float minBondDistance);
  abstract public void setAxesOrientationRasmol(boolean axesMessedUp);
  abstract public void setPercentVdwAtom(int percentVdwAtom);
  
  //for each of these the script equivalent is shown  
  abstract public void setAnimationFps(int framesPerSecond);
  //viewer.script("animation fps x.x")
  abstract public void setFrankOn(boolean frankOn);
  //viewer.script("frank on")
  abstract public void setDebugScript(boolean debugScript);
  //viewer.script("set logLevel 5/4")
  //viewer.script("set debugScript on/off")
  abstract public void deleteMeasurement(int i);
  //viewer.script("measures delete " + (i + 1));
  abstract public void clearMeasurements();
  //viewer.script("measures delete");
  abstract public void setVectorScale(float vectorScaleValue);
  //viewer.script("vector scale " + vectorScaleValue);
  abstract public void setVibrationScale(float vibrationScaleValue);
  //viewer.script("vibration scale " + vibrationScaleValue);
  abstract public void setVibrationPeriod(float vibrationPeriod);
  //viewer.script("vibration " + vibrationPeriod);
  abstract public void selectAll();
  //viewer.script("select all");
  abstract public void clearSelection();
  //viewer.script("select none");
  //viewer.script("select ({2 3:6})");
  abstract public void setSelectionSet(BS newSelection);
  //viewer.script("selectionHalos ON"); //or OFF
  abstract public void setSelectionHalos(boolean haloEnabled);
  //viewer.script("center (selected)");
  abstract public void setCenterSelected(); 

  //not used in Jmol application:
  
  abstract public void rotateFront();
  // "To" was removed in the next, because they don't 
  // rotate "TO" anything. They just rotate.
  
  abstract public void rotateXDeg(int degrees);
  abstract public void rotateYDeg(int degrees);
  abstract public void rotateX(float radians);
  abstract public void rotateY(float radians);
  abstract public void rotateZ(float radians);

  abstract public JmolAdapter getModelAdapter();

  @Override
  abstract public void openFileAsyncSpecial(String fileName, int flags);
  public void openFileAsync(String fileName) {
    openFileAsyncSpecial(fileName, 0);    
  }
  
  abstract public Object getFileAsBytes(String fullPathName, OC out);

  abstract public String getErrorMessage();
  abstract public String getErrorMessageUn();

  abstract public String getModelFileName(int modelIndex);

  public String menuStructure;

  abstract public void processMultitouchEvent(int groupID, int eventType, int touchID, int iData,
                           P3 pt, long time);

  @Override
  abstract public boolean processMouseEvent(int id, int x, int y, int modifiers,
                                     long when);

  public void mouseEvent(int id, int x, int y, int modifiers,
                         long when) {
    processMouseEvent(id, x, y, modifiers, when);
  }

  public GenericPlatform apiPlatform; // used in Viewer and JmolViewer

  /**
   * old -- not used in 12.2
   * 
   * @param g 
   * @param currentSize 
   * @param rectClip  // unused
   */
  public void renderScreenImage(Object g, Object currentSize,
                                Object rectClip) {
    apiPlatform.renderScreenImage(g, currentSize);
  }

  public Object getJsObjectInfo(Object[] jsObject, String method, Object[] args) {
    return apiPlatform.getJsObjectInfo(jsObject, method, args);
  }

  public static String getJmolValueAsString(JmolViewer jmolViewer, String var) {
    return (jmolViewer == null ? "" : "" + jmolViewer.getParameter(var));
  }

  @Override
  abstract public void cachePut(String key, Object data);

  abstract public void renderScreenImage(Object g, int width, int height);
  abstract public String evalFile(String strFilename);
  abstract public String evalString(String strScript);

  abstract public String openStringInline(String strModel);
  abstract public String openDOM(Object DOMNode);
  abstract public String openFile(String fileName);
  abstract public String openFiles(String[] fileNames);
  // File reading now returns the error directly.
  // The following was NOT what you think it was:
  //   abstract public String getOpenFileError();
  // Somewhere way back when, "openFile" became a method that did not create
  // the model set, but just an intermediary AtomSetCollection called the "clientFile"
  // (and did not necessarily close the file)
  // then "getOpenFileError()" actually created the model set, deallocated the file open thread,
  // and closed the file.
  //
  // For Jmol 11.7.14, the openXXX methods in this interface do everything --
  // open the file, create the intermediary atomSetCollection, close the file,
  // deallocate the file open thread, create the ModelSet, and return any error message.
  // so there is no longer any need for getOpenFileError().
  
  /**
   * @param returnType "JSON", "string", "readable", and anything else returns the Java object.
   * @param infoType 
   * @param paramInfo  
   * @return            property data -- see org.jmol.viewer.PropertyManager.java
   */
  abstract public Object getProperty(String returnType, String infoType, Object paramInfo);

  abstract public Object evaluateExpression(Object expression);

  abstract public Dimension resizeInnerPanel(int width, int height);

  abstract public String getAtomDefs(Map<String, Object> names);

  /**
   * run a script immediately and return output buffer string
   * Jmol 13.1.15
   * 
   * @param script
   * @return string from ScriptEvaluator#outputBuffer
   */
  abstract public String runScript(String script);
  abstract public int modelGetLastVibrationIndex(int i, int tok);
  abstract public String extractMolData(String what);
  
  /**
   * used in JSmolApplet.js for drag-drop behavior.
   * 
   * @param fileName
   * @param isAdd
   * @return number of bytes cached
   */
  @Override
  abstract public int cacheFileByName(String fileName, boolean isAdd);
  
  abstract public String getClipboardText();
  
  abstract public String clipImageOrPasteText(String text);
  abstract public void notifyStatusReady(boolean isReady);
 
}

