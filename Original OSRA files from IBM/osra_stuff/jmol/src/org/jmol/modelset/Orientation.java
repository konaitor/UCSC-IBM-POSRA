package org.jmol.modelset;

import javajs.util.M3;
import javajs.util.P3;

import org.jmol.script.T;
import org.jmol.util.Escape;
import org.jmol.viewer.Viewer;

public class Orientation {

  public String saveName;

  private M3 rotationMatrix = new M3();
  private float xTrans, yTrans;
  private float zoom, rotationRadius;
  private P3 center = new P3();
  private P3 navCenter = new P3();
  private float xNav = Float.NaN;
  private float yNav = Float.NaN;
  private float navDepth = Float.NaN;
  private float cameraDepth = Float.NaN;
  private float cameraX = Float.NaN;
  private float cameraY = Float.NaN;
  private boolean windowCenteredFlag;
  private boolean navigationMode;
  //boolean navigateSurface;
  private String moveToText;

  private float[] pymolView;

  private Viewer viewer;
  
  public Orientation(Viewer viewer, boolean asDefault, float[] pymolView) {
    this.viewer = viewer;
    if (pymolView != null) {
      this.pymolView = pymolView;
      moveToText = "moveTo -1.0 PyMOL " + Escape.eAF(pymolView);
      return;
    } 
    viewer.finalizeTransformParameters();
    if (asDefault) {
      M3 rotationMatrix = (M3) viewer
          .getModelSetAuxiliaryInfoValue("defaultOrientationMatrix");
      if (rotationMatrix == null)
        this.rotationMatrix.setIdentity();
      else
        this.rotationMatrix.setM(rotationMatrix);
    } else {
      viewer.getRotation(this.rotationMatrix);
    }
    xTrans = viewer.getTranslationXPercent();
    yTrans = viewer.getTranslationYPercent();
    zoom = viewer.getZoomSetting();
    center.setT(viewer.getRotationCenter());
    windowCenteredFlag = viewer.isWindowCentered();
    rotationRadius = viewer.getFloat(T.rotationradius);
    navigationMode = viewer.getBoolean(T.navigationmode);
    //navigateSurface = viewer.getNavigateSurface();
    moveToText = viewer.getMoveToText(-1);
    if (navigationMode) {
      xNav = viewer.getNavigationOffsetPercent('X');
      yNav = viewer.getNavigationOffsetPercent('Y');
      navDepth = viewer.getNavigationDepthPercent();
      navCenter = P3.newP(viewer.getNavigationCenter());
    }
    if (viewer.getCamera().z != 0) { // PyMOL mode
      cameraDepth = viewer.getCameraDepth();
      cameraX = viewer.getCamera().x;
      cameraY = viewer.getCamera().y;
    }
  }

  public String getMoveToText(boolean asCommand) {
    return (asCommand ? "   " + moveToText + "\n  save orientation " 
        + Escape.eS(saveName.substring(12)) + ";\n" : moveToText);
  }
  
  public boolean restore(float timeSeconds, boolean isAll) {
    if (isAll) {
      viewer.setBooleanProperty("windowCentered", windowCenteredFlag);
      viewer.setBooleanProperty("navigationMode", navigationMode);
      //viewer.setBooleanProperty("navigateSurface", navigateSurface);
      if (pymolView == null)
        viewer.moveTo(viewer.eval, timeSeconds, center, null, Float.NaN, rotationMatrix, zoom, xTrans,
            yTrans, rotationRadius, navCenter, xNav, yNav, navDepth, cameraDepth, cameraX, cameraY);
      else
        viewer.movePyMOL(viewer.eval, timeSeconds, pymolView);
    } else {
      viewer.setRotationMatrix(rotationMatrix);
    }
    return true;
  }
}