/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2006-02-25 11:44:18 -0600 (Sat, 25 Feb 2006) $
 * $Revision: 4528 $
 *
 * Copyright (C) 2005  Miguel, Jmol Development
 *
 * Contact: jmol-developers@lists.sf.net, jmol-developers@lists.sourceforge.net
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
package org.jmol.renderspecial;




import org.jmol.java.BS;
import org.jmol.render.MeshRenderer;
import org.jmol.script.T;
import org.jmol.shape.Mesh;
import org.jmol.shapespecial.Draw;
import org.jmol.shapespecial.DrawMesh;
import org.jmol.shapespecial.Draw.EnumDrawType;
import org.jmol.util.C;
import org.jmol.util.GData;
import org.jmol.util.Hermite;
import javajs.util.List;
import org.jmol.util.Measure;

import javajs.util.A4;
import javajs.util.M3;
import javajs.util.P3;
import javajs.util.P3i;
import javajs.util.V3;
import org.jmol.viewer.ActionManager;

public class DrawRenderer extends MeshRenderer {

  private EnumDrawType drawType;
  private DrawMesh dmesh;

  private P3[] controlHermites;
  protected P3 pt0 = new P3();
  protected P3 pt1 = new P3();
  protected P3 pt2 = new P3();
  protected final V3 vTemp = new V3();
  protected final V3 vTemp2 = new V3();

  @Override
  protected boolean render() {
    /*
     * Each drawn object, draw.meshes[i], may consist of several polygons, one
     * for each MODEL FRAME. Or, it may be "fixed" and only contain one single
     * polygon.
     * 
     */
    needTranslucent = false;
    imageFontScaling = viewer.getImageFontScaling();
    Draw draw = (Draw) shape;
    for (int i = draw.meshCount; --i >= 0;)
      if (renderMesh(dmesh = (DrawMesh) draw.meshes[i]))
        renderInfo();
    return needTranslucent;
  }

  @Override
  protected boolean isPolygonDisplayable(int i) {
    return Draw.isPolygonDisplayable(dmesh, i)
        && (dmesh.modelFlags == null || dmesh.bsMeshesVisible.get(i));
  }

  @Override
  public boolean renderMesh(Mesh mesh) {
    if (mesh.connections != null) {
      if (mesh.connections[0] < 0)
        return false;
      // bond-bond [ a b   c d  ]
      // bond-atom [ a b   c -1 ]
      // atom-bond [ a -1  c d  ]
      // atom-atom [ a -1  c -1 ]
      
      mesh.vertices = new P3[4];
      mesh.vertexCount = 4;
      int[] c = mesh.connections;
      for (int i = 0; i < 4; i++) {
        mesh.vertices[i] = (c[i] < 0 ? mesh.vertices[i - 1] : viewer
            .getAtomPoint3f(c[i]));
      }
      mesh.recalcAltVertices = true;
    }
    return renderMesh2(mesh);
  }

  @Override
  protected void render2(boolean isExport) {
    drawType = dmesh.drawType;
    diameter = dmesh.diameter;
    width = dmesh.width;
    if (mesh.connections != null)
      getConnectionPoints();
    if (mesh.lineData != null) {
      drawLineData(mesh.lineData);
      return;
    }
    boolean isDrawPickMode = (viewer.getPickingMode() == ActionManager.PICKING_DRAW);
    int nPoints = vertexCount;
    boolean isCurved = ((drawType == EnumDrawType.CURVE
        || drawType == EnumDrawType.ARROW || drawType == EnumDrawType.ARC) && vertexCount >= 2);
    boolean isSegments = (drawType == EnumDrawType.LINE_SEGMENT);
    if (width > 0 && isCurved) {
      pt1f.set(0, 0, 0);
      int n = (drawType == EnumDrawType.ARC ? 2 : vertexCount);
      for (int i = 0; i < n; i++)
        pt1f.add(vertices[i]);
      pt1f.scale(1f / n);
      viewer.transformPtScr(pt1f, pt1i);
      diameter = (int) viewer.scaleToScreen(pt1i.z, (int) Math.floor(width * 1000));
      if (diameter == 0)
        diameter = 1;
    }
    if ((dmesh.isVector) && dmesh.haveXyPoints) {
      int ptXY = 0;
      // [x y] or [x,y] refers to an xy point on the screen
      // just a Point3f with z = Float.MAX_VALUE
      //  [x y %] or [x,y %] refers to an xy point on the screen
      // as a percent 
      // just a Point3f with z = -Float.MAX_VALUE
      for (int i = 0; i < 2; i++)
        if (vertices[i].z == Float.MAX_VALUE
            || vertices[i].z == -Float.MAX_VALUE)
          ptXY += i + 1;
      if (--ptXY < 2) {
        renderXyArrow(ptXY);
        return;
      }
    }
    int tension = 5;
    switch (drawType) {
    default:
      render2b(false);
      break;
    case CIRCULARPLANE:
      if (dmesh.scale > 0)
        width *= dmesh.scale;
      render2b(false);
      break;
    case CIRCLE:
      viewer.transformPtScr(vertices[0], pt1i);
      if (diameter == 0 && width == 0)
        width = 1.0f;
      if (dmesh.scale > 0)
        width *= dmesh.scale;
      if (width > 0)
        diameter = (int) viewer.scaleToScreen(pt1i.z, (int) Math.floor(width * 1000));
      if (diameter > 0 && (mesh.drawTriangles || mesh.fillTriangles)) {
        g3d.addRenderer(T.circle);
        g3d.drawFilledCircle(colix, mesh.fillTriangles ? colix : 0, diameter,
            pt1i.x, pt1i.y, pt1i.z);
      }
      break;
    case CURVE:
    case LINE_SEGMENT:
      //unnecessary
      break;
    case ARC:
      //renderArrowHead(controlHermites[nHermites - 2], controlHermites[nHermites - 1], false);
      // 
      // {pt1} {pt2} {ptref} {nDegreesOffset, theta, fractionalOffset}
      float nDegreesOffset = (vertexCount > 3 ? vertices[3].x : 0);
      float theta = (vertexCount > 3 ? vertices[3].y : 360);
      if (theta == 0)
        return;
      float fractionalOffset = (vertexCount > 3 ? vertices[3].z : 0);
      vTemp.sub2(vertices[1], vertices[0]);
      // crossing point
      pt1f.scaleAdd2(fractionalOffset, vTemp, vertices[0]);
      // define rotational axis
      M3 mat = new M3();
      mat.setAA(A4.newVA(vTemp, (float) (nDegreesOffset * Math.PI / 180)));
      // vector to rotate
      vTemp2.sub2(vertexCount > 2 ? vertices[2] : Draw.randomPoint(), vertices[0]);
      vTemp2.cross(vTemp, vTemp2);
      vTemp2.cross(vTemp2, vTemp);
      vTemp2.normalize();
      vTemp2.scale(dmesh.scale / 2);
      mat.transform(vTemp2);
      //control points
      float degrees = theta / 5;
      while (Math.abs(degrees) > 5)
        degrees /= 2;
      nPoints = Math.round (theta / degrees) + 1;
      while (nPoints < 10) {
        degrees /= 2;
        nPoints = Math.round (theta / degrees) + 1;
      }
      mat.setAA(A4.newVA(vTemp, (float) (degrees * Math.PI / 180)));
      screens = viewer.allocTempScreens(nPoints);
      int iBase = nPoints - (dmesh.scale < 2 ? 3 : 3);
      for (int i = 0; i < nPoints; i++) {
        if (i == iBase)
          pt0.setT(pt1);
        pt1.scaleAdd2(1, vTemp2, pt1f);
        if (i == 0)
          pt2.setT(pt1);
        viewer.transformPtScr(pt1, screens[i]);
        mat.transform(vTemp2);
      }
      if (dmesh.isVector && !dmesh.noHead) {
        renderArrowHead(pt0, pt1, 0.3f, false, false, dmesh.isBarb);
        viewer.transformPtScr(pt1f, screens[nPoints - 1]);
      }
      pt1f.setT(pt2);
      break;
    case ARROW:
      if (vertexCount == 2) {
        renderArrowHead(vertices[0], vertices[1], 0, false, true, dmesh.isBarb);
        break;
      }
      int nHermites = 5;
      if (controlHermites == null || controlHermites.length < nHermites + 1) {
        controlHermites = new P3[nHermites + 1];
      }
      Hermite.getHermiteList(tension, vertices[vertexCount - 3],
          vertices[vertexCount - 2], vertices[vertexCount - 1],
          vertices[vertexCount - 1], vertices[vertexCount - 1],
          controlHermites, 0, nHermites, true);
      renderArrowHead(controlHermites[nHermites - 2],
          controlHermites[nHermites - 1], 0, false, false, dmesh.isBarb);
      break;
    }
    if (diameter == 0)
      diameter = 3;
    if (isCurved) {
      g3d.addRenderer(T.hermitelevel);
      for (int i = 0, i0 = 0; i < nPoints - 1; i++) {
        g3d
            .fillHermite(tension, diameter, diameter, diameter, screens[i0],
                screens[i], screens[i + 1], screens[i
                    + (i == nPoints - 2 ? 1 : 2)]);
        i0 = i;
      }
    } else if (isSegments) {
      for (int i = 0; i < nPoints - 1; i++)
        drawLine(i, i + 1, true, vertices[i], vertices[i + 1], screens[i],
            screens[i + 1]);
    }

    if (isDrawPickMode && !isExport) {
      renderHandles();
    }
  }

  private void getConnectionPoints() {
    // now we screens and any adjustment to positions
    // we need to set the actual control points
    
    
    vertexCount = 3;
    float dmax = Float.MAX_VALUE;
    int i0 = 0;
    int j0 = 0;
    for (int i = 0; i < 2; i++)
      for (int j = 2; j < 4; j++) {
        float d = vertices[i].distance(vertices[j]);
        if (d < dmax) {
          dmax = d;
          i0 = i;
          j0 = j;
        }
      }
    pt0.ave(vertices[0], vertices[1]);
    pt2.ave(vertices[2], vertices[3]);
    pt1.ave(pt0, pt2);
    vertices[3] = P3.newP(vertices[i0]);
    vertices[3].add(vertices[j0]);
    vertices[3].scale(0.5f);
    vertices[1] = P3.newP(pt1); 
    vertices[0] = P3.newP(pt0);
    vertices[2] = P3.newP(pt2);

    for (int i = 0; i < 4; i++)
      viewer.transformPtScr(vertices[i], screens[i]);

    float f = 4 * getArrowScale(); // bendiness
    float endoffset = 0.2f;
    float offsetside = (width == 0 ? 0.1f : width);
    
    pt0.set(screens[0].x, screens[0].y, screens[0].z);
    pt1.set(screens[1].x, screens[1].y, screens[1].z);
    pt2.set(screens[3].x, screens[3].y, screens[3].z);
    float dx = (screens[1].x - screens[0].x) * f;
    float dy = (screens[1].y - screens[0].y) * f;
    
    if (dmax == 0 || Measure.computeTorsion(pt2, pt0, P3.new3(pt0.x, pt0.y, 10000f), pt1, false) > 0) {
      dx = -dx;
      dy = -dy;
    }
    pt2.set(dy, -dx, 0);
    pt1.add(pt2);
    viewer.unTransformPoint(pt1, vertices[1]);
    pt2.scale(offsetside);
    vTemp.sub2(vertices[1], vertices[0]);
    vTemp.scale(endoffset); 
    vertices[0].add(vTemp);
    vTemp.sub2(vertices[1], vertices[2]);
    vTemp.scale(endoffset); 
    vertices[2].add(vTemp);
    for (int i = 0; i < 3; i++) {
      viewer.transformPtScr(vertices[i], screens[i]);
      if (offsetside != 0) {
        screens[i].x += Math.round(pt2.x);
        screens[i].y += Math.round(pt2.y);
        pt1.set(screens[i].x, screens[i].y, screens[i].z);
        viewer.unTransformPoint(pt1 , vertices[i]);
      }
    }
  }

  private void drawLineData(List<P3[]> lineData) {
    if (diameter == 0)
      diameter = 3;
    for (int i = lineData.size(); --i >= 0;) {
      P3[] pts = lineData.get(i);
      viewer.transformPtScr(pts[0], pt1i);
      viewer.transformPtScr(pts[1], pt2i);
      drawLine(-1, -2, true, pts[0], pts[1], pt1i, pt2i);
    }
  }

  private void renderXyArrow(int ptXY) {
    int ptXYZ = 1 - ptXY;
    P3[] arrowPt = new P3[2];
    arrowPt[ptXYZ] = pt1;
    arrowPt[ptXY] = pt0;
    // set up (0,0,0) to ptXYZ in real and screen coordinates
    pt0.set(screens[ptXY].x, screens[ptXY].y, screens[ptXY].z);
    viewer.rotatePoint(vertices[ptXYZ], pt1);
    pt1.z *= -1;
    float zoomDimension = viewer.getScreenDim();
    float scaleFactor = zoomDimension / 20f;
    pt1.scaleAdd2(dmesh.scale * scaleFactor, pt1, pt0);
    if (diameter == 0)
      diameter = 1;
    pt1i.set(Math.round(pt0.x), Math.round(pt0.y),Math.round(pt0.z));
    pt2i.set(Math.round(pt1.x), Math.round(pt1.y), Math.round(pt1.z));
    if (diameter < 0)
      g3d.drawDottedLine(pt1i, pt2i);
    else
      g3d.fillCylinder(GData.ENDCAPS_FLAT, diameter, pt1i, pt2i);
    renderArrowHead(pt0, pt1, 0, true, false, false);
  }

  private final P3 pt0f = new P3();
  protected P3i pt0i = new P3i();

  private void renderArrowHead(P3 pt1, P3 pt2, float factor2,
                               boolean isTransformed, boolean withShaft,
                               boolean isBarb) {
    if (dmesh.noHead)
      return;
    float fScale = getArrowScale();
    if (isTransformed)
      fScale *= 40;
    if (factor2 > 0)
      fScale *= factor2;

    pt0f.setT(pt1);
    pt2f.setT(pt2);
    float d = pt0f.distance(pt2f);
    if (d == 0)
      return;
    vTemp.sub2(pt2f, pt0f);
    vTemp.normalize();
    vTemp.scale(fScale / 5);
    if (!withShaft)
      pt2f.add(vTemp);
    vTemp.scale(5);
    pt1f.sub2(pt2f, vTemp);
    if (isTransformed) {
      pt1i.set(Math.round(pt1f.x),Math.round(pt1f.y),Math.round(pt1f.z));
      pt2i.set(Math.round(pt2f.x), Math.round(pt2f.y), Math.round(pt2f.z));
    } else {
      viewer.transformPtScr(pt2f, pt2i);
      viewer.transformPtScr(pt1f, pt1i);
      viewer.transformPtScr(pt0f, pt0i);
    }
    if (pt2i.z == 1 || pt1i.z == 1) //slabbed
      return;
    int headDiameter;
    if (diameter > 0) {
      headDiameter = diameter * 3;
    } else {
      vTemp.set(pt2i.x - pt1i.x, pt2i.y - pt1i.y, pt2i.z - pt1i.z);
      headDiameter = Math.round(vTemp.length() * .5f);
      diameter = headDiameter / 5;
    }
    if (diameter < 1)
      diameter = 1;
    if (headDiameter > 2)
      g3d.fillConeScreen(GData.ENDCAPS_FLAT, headDiameter, pt1i, pt2i,
          isBarb);
    if (withShaft)
      g3d.fillCylinderScreen3I(GData.ENDCAPS_OPENEND, diameter, pt0i, pt1i, null, null, mad / 2000f);
  }

  private float getArrowScale() {
    float fScale = (dmesh.isScaleSet ? dmesh.scale : 0);
    if (fScale == 0)
      fScale = viewer.getFloat(T.defaultdrawarrowscale) * (dmesh.connections == null ? 1f : 0.5f);
    if (fScale <= 0)
      fScale = 0.5f;
    return fScale;
  }

  private final BS bsHandles = new BS();
  
  private void renderHandles() {
    int diameter = Math.round(10 * imageFontScaling);
    switch (drawType) {
    case NONE:
      return;
    default:
      short colixFill = C.getColixTranslucent3(C.GOLD, true,
          0.5f);
      bsHandles.clearAll();
      g3d.addRenderer(T.circle);
      for (int i = dmesh.polygonCount; --i >= 0;) {
        if (!isPolygonDisplayable(i))
          continue;
        int[] vertexIndexes = dmesh.polygonIndexes[i];
        if (vertexIndexes == null)
          continue;
        for (int j = (dmesh.isTriangleSet ? 3 : vertexIndexes.length); --j >= 0;) {
          int k = vertexIndexes[j];
          if (bsHandles.get(k))
            continue;
          bsHandles.set(k);
          g3d.drawFilledCircle(C.GOLD, colixFill, diameter,
              screens[k].x, screens[k].y, screens[k].z);
        }
      }
      break;
    }
  }

  private void renderInfo() {
    if (mesh.title == null || viewer.getDrawHover()
        || !g3d.setColix(viewer.getColixBackgroundContrast()))
      return;
    for (int i = dmesh.polygonCount; --i >= 0;)
      if (isPolygonDisplayable(i)) {
        //just the first line of the title -- nothing fancy here.
        float size = viewer.getFloat(T.drawfontsize);
        if (size <= 0)
          size = 14;
        byte fid = g3d.getFontFid(size * imageFontScaling);
        g3d.setFontFid(fid);
        String s = mesh.title[i < mesh.title.length ? i : mesh.title.length - 1];
        int pt = 0;
        if (s.length() > 1 && s.charAt(0) == '>') {
          pt = dmesh.polygonIndexes[i].length - 1;
          s = s.substring(1);
          if (drawType == EnumDrawType.ARC)
            pt1f.setT(pt2f);
        }
        if (drawType != EnumDrawType.ARC)
          pt1f.setT(vertices[dmesh.polygonIndexes[i][pt]]);
        viewer.transformPtScr(pt1f, pt1i);
        int offset = Math.round(5 * imageFontScaling);
        g3d.drawString(s, null, pt1i.x + offset, pt1i.y - offset, pt1i.z,
            pt1i.z, (short) 0);
        break;
      }
  }

}
