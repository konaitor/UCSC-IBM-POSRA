/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-11-24 15:00:42 -0600 (Sun, 24 Nov 2013) $
 * $Revision: 19010 $
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
 *  Lesser General License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.render;

import java.util.Map;

import org.jmol.api.JmolRendererInterface;
import org.jmol.api.JmolRepaintManager;
import org.jmol.java.BS;
import org.jmol.modelset.ModelSet;
import org.jmol.script.T;
import org.jmol.shape.Shape;
import org.jmol.util.GData;
import org.jmol.util.Logger;
import org.jmol.util.Rectangle;
import org.jmol.viewer.JC;
import org.jmol.viewer.ShapeManager;
import org.jmol.viewer.Viewer;

public class RepaintManager implements JmolRepaintManager {

  private Viewer viewer;
  private ShapeManager shapeManager;
  private ShapeRenderer[] renderers;

  public RepaintManager() {
    // required for reflection
  }
  
  private final BS bsTranslucent = BS.newN(JC.SHAPE_MAX);
  
  @Override
  public void set(Viewer viewer, ShapeManager shapeManager) {
    this.viewer = viewer;
    this.shapeManager = shapeManager;
  }

  /////////// thread management ///////////
  
  public int holdRepaint = 0;
  private boolean repaintPending;
  
  @Override
  public boolean isRepaintPending() {
    return repaintPending;
  }
  
  @Override
  public void pushHoldRepaint(String why) {
    ++holdRepaint;
  }
  
  @Override
  public void popHoldRepaint(boolean andRepaint, String why) {
    --holdRepaint;
    if (holdRepaint <= 0) {
      holdRepaint = 0;
      if (andRepaint) {
        repaintPending = true;
        repaintNow(why);
      }
    }
  }

  @Override
  synchronized public void requestRepaintAndWait(String why) {
    /**
     * @j2sNative
     * 
     *  if (typeof Jmol != "undefined" && Jmol._repaint) 
     *    Jmol._repaint(this.viewer.applet, false);
     *  this.repaintDone();
     */
    {
      //System.out.println("RM requestRepaintAndWait() " + (test++));
      try {
        repaintNow(why);
        //System.out.println("repaintManager requestRepaintAndWait I am waiting for a repaint: thread=" + Thread.currentThread().getName());
        wait(viewer.global.repaintWaitMs); // more than a second probably means we are locked up here
        if (repaintPending) {
          Logger.error("repaintManager requestRepaintAndWait timeout");
          repaintDone();
        }
      } catch (InterruptedException e) {
        //System.out.println("repaintManager requestRepaintAndWait interrupted thread=" + Thread.currentThread().getName());
      }
    }
    //System.out.println("repaintManager requestRepaintAndWait I am no longer waiting for a repaint: thread=" + Thread.currentThread().getName());
  }

  @Override
  public boolean repaintIfReady(String why) {
    if (repaintPending)
      return false;
    repaintPending = true;
    if (holdRepaint == 0)
      repaintNow(why);
    return true;
  }

  /**
   * @param why  
   */
  private void repaintNow(String why) {
    // from RepaintManager to the System
    // -- "Send me an asynchronous update() event!"
    if (!viewer.haveDisplay)
      return;    
    /**
     * Jmol._repaint(applet,asNewThread)
     * 
     * should invoke 
     * 
     *   setTimeout(applet._applet.viewer.updateJS(width, height)) // may be 0,0
     *   
     * when it is ready to do so.
     * 
     * @j2sNative
     * 
     * if (typeof Jmol != "undefined" && Jmol._repaint)
     *   Jmol._repaint(this.viewer.applet,true);
     * 
     */
    {
      //System.out.println("RepaintMan repaintNow " + why);
      viewer.apiPlatform.repaint(viewer.getDisplay());
    }
     
  }

  @Override
  synchronized public void repaintDone() {
    repaintPending = false;
    /**
     * @j2sNative
     * 
     */
    {
      //System.out.println("repaintManager repaintDone thread=" + Thread.currentThread().getName());
      // ignored in JavaScript
      notify(); // to cancel any wait in requestRepaintAndWait()
    }
  }

  
  /////////// renderer management ///////////
  
  
  @Override
  public void clear(int iShape) {
    if (renderers ==  null)
      return;
    if (iShape >= 0)
      renderers[iShape] = null;
    else
      for (int i = 0; i < JC.SHAPE_MAX; ++i)
        renderers[i] = null;
  }

  private ShapeRenderer getRenderer(int shapeID) {
    if (renderers[shapeID] != null)
      return renderers[shapeID];
    String className = JC.getShapeClassName(shapeID, true) + "Renderer";
    try {
      Class<?> shapeClass = Class.forName(className);
      ShapeRenderer renderer = (ShapeRenderer) shapeClass.newInstance();
      renderer.setViewerG3dShapeID(viewer, shapeID);
      return renderers[shapeID] = renderer;
    } catch (Exception e) {
      Logger.errorEx("Could not instantiate renderer:" + className, e);
      return null;
    }
  }

  /////////// actual rendering ///////////
  
  @Override
  public void render(GData gdata, ModelSet modelSet, boolean isFirstPass, int[] minMax) {
    boolean logTime = viewer.getBoolean(T.showtiming);
    try {
      JmolRendererInterface g3d = (JmolRendererInterface) gdata;
      g3d.renderBackground(null);
      if (isFirstPass)  {
        bsTranslucent.clearAll();
        if (minMax != null)
          g3d.renderCrossHairs(minMax, viewer.getScreenWidth(), viewer.getScreenHeight(), 
              viewer.getNavigationOffset(), viewer.getNavigationDepthPercent());
        Rectangle band = viewer.getRubberBandSelection();
          if (band != null && g3d.setColix(viewer.getColixRubberband()))
            g3d.drawRect(band.x, band.y, 0, 0, band.width, band.height);
      }
      if (renderers == null)
        renderers = new ShapeRenderer[JC.SHAPE_MAX];
      String msg = null;
      for (int i = 0; i < JC.SHAPE_MAX && g3d.currentlyRendering(); ++i) {
        Shape shape = shapeManager.getShape(i);
        if (shape == null)
          continue;
        
        if (logTime) {
          msg = "rendering " + JC.getShapeClassName(i, false);
          Logger.startTimer(msg);
        }
        if((isFirstPass || bsTranslucent.get(i)) && getRenderer(i).renderShape(g3d, modelSet, shape))
          bsTranslucent.set(i);
        if (logTime)
          Logger.checkTimer(msg, false);
      }
      g3d.renderAllStrings(null);
    } catch (Exception e) {
      if (!viewer.isJS)
        e.printStackTrace();
      Logger.error("rendering error? " + e);
    }
  }
  
  @Override
  public String renderExport(GData gdata, ModelSet modelSet, Map<String, Object> params) {
    boolean isOK;
    boolean logTime = viewer.getBoolean(T.showtiming);
    viewer.finalizeTransformParameters();
    shapeManager.finalizeAtoms(null, null);
    shapeManager.transformAtoms();
    JmolRendererInterface g3dExport = viewer.initializeExporter(params);
    isOK = (g3dExport != null);
    if (!isOK) {
      Logger.error("Cannot export " + params.get("type"));
      return null;
    }
    g3dExport.renderBackground(g3dExport);
    if (renderers == null)
      renderers = new ShapeRenderer[JC.SHAPE_MAX];
    String msg = null;
    for (int i = 0; i < JC.SHAPE_MAX; ++i) {
      Shape shape = shapeManager.getShape(i);
      if (shape == null)
        continue;
        if (logTime) {
          msg = "rendering " + JC.getShapeClassName(i, false);
          Logger.startTimer(msg);
        }
        getRenderer(i).renderShape(g3dExport, modelSet, shape);
        if (logTime)
          Logger.checkTimer(msg, false);
    }
    g3dExport.renderAllStrings(g3dExport);
    return g3dExport.finalizeOutput();
  }

}
