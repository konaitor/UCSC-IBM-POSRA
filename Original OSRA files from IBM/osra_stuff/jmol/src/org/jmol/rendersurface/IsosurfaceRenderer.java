/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-04-25 09:53:35 -0500 (Wed, 25 Apr 2007) $
 * $Revision: 7491 $
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
package org.jmol.rendersurface;

import org.jmol.jvxl.data.JvxlCoder;
import org.jmol.jvxl.readers.Parameters;
import org.jmol.render.MeshRenderer;
import org.jmol.script.T;
import org.jmol.shapesurface.Isosurface;
import org.jmol.shapesurface.IsosurfaceMesh;
import org.jmol.util.C;
import org.jmol.util.GData;
import javajs.util.List;
import org.jmol.util.Normix;
import javajs.util.P3;
import javajs.util.P3i;
import javajs.util.V3;

public class IsosurfaceRenderer extends MeshRenderer {

  protected boolean iHideBackground;
  protected boolean isBicolorMap;
  protected short backgroundColix;
  protected int nError = 0;
  protected float[] vertexValues;
  protected IsosurfaceMesh imesh;
  private Isosurface isosurface;
  private boolean isNavigationMode;
  private boolean iShowNormals;
  private boolean showNumbers;
  private Boolean showKey;
  private boolean hasColorRange;
  private int meshScale = -1;
  private int mySlabValue;
  private int globalSlabValue;

  @Override
  protected boolean render() {
    return renderIso();
  }

  protected boolean renderIso() {
    
    setGlobals();

    for (int i = isosurface.meshCount; --i >= 0;) {
      mesh = imesh = (IsosurfaceMesh) isosurface.meshes[i];
      if (imesh.connections != null && !viewer.getModelSet().atoms[imesh.connections[0]].isVisible(0))
        continue;
      hasColorRange = false;
      if (renderMeshSlab()) {
        if (!isExport)
          renderInfo();
        if (isExport && isGhostPass) {
          exportPass = 1;
          renderMeshSlab();
          exportPass = 2;
        }
      }
    }
    return needTranslucent;
  }

  private void setGlobals() {
    needTranslucent = false;
    iShowNormals = viewer.getTestFlag(4);
    showNumbers = viewer.getTestFlag(3);
    isosurface = (Isosurface) shape;
    // exporters will do two passes here if there is translucency
    // first pass is #2 (translucent), then #1 (opaque).
    exportPass = (isExport ? 2 : 0); 
    isNavigationMode = viewer.getBoolean(T.navigationmode);
    showKey = (viewer.getBoolean(T.isosurfacekey) ? Boolean.TRUE : null);
    isosurface.keyXy = null;
    meshScale = -1;
    globalSlabValue = g3d.getSlab();
    mySlabValue = (isNavigationMode ? (int) viewer.getNavigationOffset().z : Integer.MAX_VALUE);
  }

  protected void renderInfo() {
    if (hasColorRange && imesh.colorEncoder != null && Boolean.TRUE == showKey)
      showKey();
  }
  
  private void showKey() {
    showKey = Boolean.FALSE; // once only
    int[] colors = null;
    short[] colixes = null;
    List<Object>[] vContours = null;
    int n = 0;
    int type = 0;
    if (imesh.showContourLines) {
      vContours = imesh.getContours();
      if (vContours == null) {
        colixes = imesh.jvxlData.contourColixes;
        if (colixes == null)
          return;
        n = colixes.length; 
      } else {
        n = vContours.length;
        type = 1;
      }
    } else {
      colors = imesh.colorEncoder.getColorSchemeArray(imesh.colorEncoder.currentPalette);
      n = (colors == null ? 0  : colors.length);
      type = 2;
    }
    if (n < 2)
      return;
    int factor = (g3d.isAntialiased() ? 2 : 1);
    int height = viewer.getScreenHeight() * factor;
    int dy = height / 2 / (n - 1);
    int y = height / 4 * 3 - dy;
    int x = 10 * factor;
    int dx = 20 * factor;
    
    isosurface.keyXy = new int[] { x / factor, 0, (x + dx) / factor, (y + dy) / factor, dy / factor };
    for (int i = 0; i < n; i++, y -= dy) {
      switch (type) {
      case 0:
        if (!g3d.setColix(colixes[i]))
          return;
        break;
      case 1:
        if (!g3d.setColix(((short[]) vContours[i].get(JvxlCoder.CONTOUR_COLIX))[0]))
          return;
        break;
      case 2:
        g3d.setColor(colors[i]);
        break;
      }
      g3d.fillRect(x, y, 5, Integer.MIN_VALUE, dx, dy);
    }
    isosurface.keyXy[1] = (y + dy) / factor;
  }
  
  private boolean renderMeshSlab() {
    volumeRender = (imesh.jvxlData.colorDensity && imesh.jvxlData.allowVolumeRender);
    int thisSlabValue = mySlabValue;
    frontOnly = mesh.frontOnly;
    if (!isNavigationMode) {
      meshSlabValue = imesh.jvxlData.slabValue; 
      if (meshSlabValue != Integer.MIN_VALUE  
          && imesh.jvxlData.isSlabbable) {
        P3[] points = imesh.jvxlData.boundingBox;
        float z0 = Float.MAX_VALUE;
        float z1 = Float.MIN_VALUE;
        for (int i = points.length; --i >= 0;) {
          pt2f.setT(points[i]);
          viewer.transformPt3f(pt2f, pt2f);
          if (pt2f.z < z0)
            z0 = pt2f.z;
          if (pt2f.z > z1)
            z1 = pt2f.z;
        }
        thisSlabValue = Math.round(z0 + (z1 - z0) * (100f - meshSlabValue)/100);
        frontOnly &= (meshSlabValue >= 100);
      }
    }
    boolean tcover = g3d.getTranslucentCoverOnly();
    g3d.setTranslucentCoverOnly(frontOnly || !viewer.getBoolean(T.translucent));
    thePlane = imesh.jvxlData.jvxlPlane;
    vertexValues = mesh.vertexValues;
    boolean isOK;
    if (thisSlabValue != Integer.MAX_VALUE && imesh.jvxlData.isSlabbable) {
      g3d.setSlab(thisSlabValue);
      isOK = renderMesh(mesh);
      g3d.setSlab(globalSlabValue);
    } else {
      isOK = renderMesh(mesh);
    }
    g3d.setTranslucentCoverOnly(tcover);
    return isOK;
  }
  
  @Override
  protected void render2(boolean isExport) {
    if (volumeRender) {
      renderPoints();
      return;
    }
    switch (imesh.dataType) {
    case Parameters.SURFACE_LONEPAIR:
      renderLonePair(false);
      return;
    case Parameters.SURFACE_RADICAL:
      renderLonePair(true);
      return;
    }
    isBicolorMap = imesh.jvxlData.isBicolorMap;
    render2b(isExport);
    if (!g3d.setColix(C.BLACK)) // must be 1st pass
      return;
    if (imesh.showContourLines)
      renderContourLines();
  }
  
  private void renderLonePair(boolean isRadical) {
    pt2f.setT(vertices[1]);
    viewer.transformPt3f(pt2f, pt2f);
    int r = (int) viewer.scaleToScreen((int)pt2f.z, 100);
    if (r < 1)
      r = 1;
    if (!isRadical) {
      V3 v1 = new V3();
      V3 v2 = new V3();
      pt1f.setT(vertices[0]);
      viewer.transformPt3f(pt1f, pt1f);
      v1.sub2(pt2f, pt1f);
      v2.set(v1.x, v1.y, v1.z + 1);
      v2.cross(v2,v1);
      v2.normalize();
      float f = viewer.scaleToScreen((int)pt1f.z, 100);
      v2.scale(f);
      pt1f.add2(pt2f, v2);
      pt2f.sub(v2);
      screens[0].set(Math.round(pt1f.x),Math.round(pt1f.y),Math.round(pt1f.z));
      g3d.fillSphereI(r, screens[0]);
    }
    screens[1].set(Math.round(pt2f.x),Math.round(pt2f.y),Math.round(pt2f.z));
    g3d.fillSphereI(r, screens[1]);
  }
  
  private void renderContourLines() {
    // no check here for within distance
    List<Object>[] vContours = imesh.getContours();
    if (vContours == null) {
      if (imesh.jvxlData.contourValues != null)
        hasColorRange = true;
      return;
    }
    
    //if (imesh.jvxlData.vertexDataOnly)
      //return;
    hasColorRange = (mesh.meshColix == 0);
    for (int i = vContours.length; --i >= 0;) {
      List<Object> v = vContours[i];
      if (v.size() < JvxlCoder.CONTOUR_POINTS)
        continue;
      colix = (mesh.meshColix == 0 ? ((short[]) v.get(JvxlCoder.CONTOUR_COLIX))[0]
          : mesh.meshColix);
      if (!g3d.setColix(colix))
        return;
      int n = v.size() - 1;
      for (int j = JvxlCoder.CONTOUR_POINTS; j < n; j++) {
        P3 pt1 = (P3) v.get(j);
        P3 pt2 = (P3) v.get(++j);
        viewer.transformPtScr(pt1, pt1i);
        viewer.transformPtScr(pt2, pt2i);
        if (Float.isNaN(pt1.x) || Float.isNaN(pt2.x))
          break;
        pt1i.z -= 2;
        pt2i.z -= 2;
        g3d.drawLineAB(pt1i, pt2i);
      }
    }
  }
  
  private final P3 ptTemp = new P3();
  private final P3i ptTempi = new P3i();

  @Override
  protected void renderPoints() {
    try {
      if (volumeRender)
        g3d.volumeRender(true);
      boolean slabPoints = ((volumeRender || mesh.polygonCount == 0) && selectedPolyOnly);
      int incr = imesh.vertexIncrement;
      int diam;
      if (mesh.diameter <= 0) {
        diam = viewer.getInt(T.dotscale);
        frontOnly = false;
      } else {
        diam = viewer.getScreenDim() / (volumeRender ? 50 : 100);        
      }
      int ptSize = Math.round(Float.isNaN(mesh.volumeRenderPointSize) ? 150 : mesh.volumeRenderPointSize * 1000);
      if (diam < 1)
        diam = 1;
      int cX = (showNumbers ? viewer.getScreenWidth() / 2 : 0);
      int cY = (showNumbers ? viewer.getScreenHeight() / 2 : 0);
      if (showNumbers)
        g3d.setFontFid(g3d.getFontFidFS("Monospaced", 24));
      for (int i = (!imesh.hasGridPoints || imesh.firstRealVertex < 0 ? 0
          : imesh.firstRealVertex); i < vertexCount; i += incr) {
        if (vertexValues != null && Float.isNaN(vertexValues[i]) || frontOnly
            && transformedVectors[normixes[i]].z < 0 || imesh.jvxlData.thisSet >= 0
            && mesh.vertexSets[i] != imesh.jvxlData.thisSet || !mesh.isColorSolid
            && mesh.vertexColixes != null && !setColix(mesh.vertexColixes[i])
            || haveBsDisplay && !mesh.bsDisplay.get(i)
            || slabPoints && !bsPolygons.get(i))
          continue;
        hasColorRange = true; // maybe
        if (showNumbers && screens[i].z > 10
            && Math.abs(screens[i].x - cX) < 150
            && Math.abs(screens[i].y - cY) < 150) {
          String s = i
              + (mesh.isColorSolid ? "" : " " + mesh.vertexValues[i]);
          g3d.setColix(C.BLACK);
          g3d.drawStringNoSlab(s, null, screens[i].x, screens[i].y,
              screens[i].z - 30, (short) 0);
        }
        if (volumeRender) {
          diam = (int) viewer.scaleToScreen(screens[i].z, ptSize);
          if (diam < 1)
            diam = 1;
          g3d.volumeRender4(diam, screens[i].x, screens[i].y, screens[i].z);
        } else {
          g3d.fillSphereI(diam, screens[i]);
        }
      }
      if (incr == 3) {
        g3d.setColix(isTranslucent ? C.getColixTranslucent3(
            C.GRAY, true, 0.5f) : C.GRAY);
        for (int i = 1; i < vertexCount; i += 3)
          g3d.fillCylinder(GData.ENDCAPS_SPHERICAL, diam / 4, screens[i],
              screens[i + 1]);
        g3d.setColix(isTranslucent ? C.getColixTranslucent3(
            C.YELLOW, true, 0.5f) : C.YELLOW);
        for (int i = 1; i < vertexCount; i += 3)
          g3d.fillSphereI(diam, screens[i]);

        g3d.setColix(isTranslucent ? C.getColixTranslucent3(
            C.BLUE, true, 0.5f) : C.BLUE);
        for (int i = 2; i < vertexCount; i += 3) {
          g3d.fillSphereI(diam, screens[i]);
        }
      }
    } catch (Throwable e) {
      // just in case, need to reset volume rendering
    }
    if (volumeRender)
      g3d.volumeRender(false);
  }

  @Override
  protected void renderTriangles(boolean fill, boolean iShowTriangles,
                                 boolean isExport) {
    g3d.addRenderer(T.triangles);
    int[][] polygonIndexes = mesh.polygonIndexes;
    colix = (isGhostPass ? mesh.slabColix
        : !fill && mesh.meshColix != 0 ? mesh.meshColix : mesh.colix);
    short[] vertexColixes = (!fill && mesh.meshColix != 0 ? null
        : mesh.vertexColixes);
    if (isTranslucentInherit)
      colix = C.copyColixTranslucency(mesh.slabColix, mesh.colix);
    g3d.setColix(colix);
    boolean generateSet = isExport;
    if (generateSet) {
      if (frontOnly && fill)
        frontOnly = false;
      bsPolygonsToExport.clearAll();
    }
    if (exportType == GData.EXPORT_CARTESIAN) {
      frontOnly = false;
    }
    boolean colorSolid = (isGhostPass && (!isBicolorMap)
        || vertexColixes == null || mesh.isColorSolid);
    boolean noColor = (isGhostPass && !isBicolorMap || vertexColixes == null || !fill
        && mesh.meshColix != 0);
    boolean isPlane = (imesh.jvxlData.jvxlPlane != null);
    short colix = this.colix;
    if (isPlane && !colorSolid && !fill && mesh.fillTriangles) {
      colorSolid = true;
      colix = C.BLACK;
    }
    /*  only an idea -- causes flickering
        if (isPlane && colorSolid) {
          g3d.setNoisySurfaceShade(screens[polygonIndexes[0][0]], 
              screens[polygonIndexes[mesh.polygonCount / 2][1]], screens[polygonIndexes[mesh.polygonCount - 1][2]]);
        }
    */
    boolean colorArrayed = (colorSolid && mesh.polygonColixes != null);
    if (colorArrayed && !fill && mesh.fillTriangles)
      colorArrayed = false;
    short[] contourColixes = imesh.jvxlData.contourColixes;
    // two-sided means like a plane, with no front/back distinction

    hasColorRange = !colorSolid && !isBicolorMap;
    int diam;
    if (mesh.diameter <= 0) {
      diam = (meshScale < 0 ? meshScale = viewer.getInt(T.meshscale)
          : meshScale);
      if (g3d.isAntialiased())
        diam *= 2;
    } else {
      diam = viewer.getScreenDim() / 100;
    }
    if (diam < 1)
      diam = 1;
    for (int i = mesh.polygonCount; --i >= 0;) {
      int[] polygon = polygonIndexes[i];
      if (polygon == null || selectedPolyOnly && !bsPolygons.get(i))
        continue;
      int iA = polygon[0];
      int iB = polygon[1];
      int iC = polygon[2];
      if (imesh.jvxlData.thisSet >= 0 && mesh.vertexSets != null
          && mesh.vertexSets[iA] != imesh.jvxlData.thisSet)
        continue;
      if (haveBsDisplay
          && (!mesh.bsDisplay.get(iA) || !mesh.bsDisplay.get(iB) || !mesh.bsDisplay
              .get(iC)))
        continue;
      short nA = normixes[iA];
      short nB = normixes[iB];
      short nC = normixes[iC];
      int check = checkNormals(nA, nB, nC);
      if (fill && check == 0)
        continue;
      short colixA, colixB, colixC;
      if (colorSolid) {
        if (colorArrayed && i < mesh.polygonColixes.length) {
          short c = mesh.polygonColixes[i];
          if (c == 0)
            continue;
          colix = c;
        }
        colixA = colixB = colixC = colix;
      } else {
        colixA = vertexColixes[iA];
        colixB = vertexColixes[iB];
        colixC = vertexColixes[iC];
        if (isBicolorMap) {
          if (colixA != colixB || colixB != colixC)
            continue;
          if (isGhostPass) {
            colixA = colixB = colixC = C.copyColixTranslucency(mesh.slabColix,
                colixA);
          }
        }
      }
      if (fill) {
        if (generateSet) {
          bsPolygonsToExport.set(i);
          continue;
        }
        if (iB == iC) {
          setColix(colixA);
          if (iA == iB)
            g3d.fillSphereI(diam, screens[iA]);
          else
            g3d.fillCylinder(GData.ENDCAPS_SPHERICAL, diam, screens[iA],
                screens[iB]);
        } else if (iShowTriangles) {
          g3d.fillTriangle(screens[iA], colixA, nA, screens[iB], colixB, nB,
              screens[iC], colixC, nC, 0.1f);
        } else {
          if (isTranslucentInherit && vertexColixes != null) {
            colixA = C.copyColixTranslucency(mesh.slabColix, vertexColixes[iA]);
            colixB = C.copyColixTranslucency(mesh.slabColix, vertexColixes[iB]);
            colixC = C.copyColixTranslucency(mesh.slabColix, vertexColixes[iC]);
          }
          g3d.fillTriangle3CN(screens[iA], colixA, nA, screens[iB], colixB, nB,
              screens[iC], colixC, nC);
        }
        if (iShowNormals)
          renderNormals();
      } else {
        // mesh only
        // check: 1 (ab) | 2(bc) | 4(ac)
        check &= polygon[3];
        if (iShowTriangles)
          check = 7;
        if (check == 0)
          continue;
        pt1i.setT(screens[iA]);
        pt2i.setT(screens[iB]);
        pt3i.setT(screens[iC]);
        pt1i.z -= 2;
        pt2i.z -= 2;
        pt3i.z -= 2;
        if (noColor) {
        } else if (colorArrayed) {
          g3d.setColix(mesh.fillTriangles ? C.BLACK : contourColixes[polygon[4]
              % contourColixes.length]);
        } else {
          drawTriangle(pt1i, colixA, pt2i, colixB, pt3i, colixC, check, diam);
          continue;
        }
        drawTriangle(pt1i, colix, pt2i, colix, pt3i, colix, check, diam);
      }
    }
    if (generateSet)
      exportSurface(colorSolid ? colix : 0);
  }

  private void renderNormals() {
    // Logger.debug("mesh renderPoints: " + vertexCount);
    if (!g3d.setColix(C.WHITE))
      return;
    g3d.setFontFid(g3d.getFontFidFS("Monospaced", 24));
    V3[] vertexVectors = Normix.getVertexVectors();
    for (int i = vertexCount; --i >= 0;) {
      if (vertexValues != null && Float.isNaN(vertexValues[i]))
        continue;
      if (i > 100)
        continue;
      ptTemp.setT(vertices[i]);
      short n = mesh.normixes[i];
      // -n is an intensity2sided and does not correspond to a true normal
      // index
      if (n >= 0) {
        ptTemp.scaleAdd2(3, vertexVectors[n], ptTemp);
        viewer.transformPtScr(ptTemp, ptTempi);
        g3d.drawLineAB(screens[i], ptTempi);
        //g3d.drawStringNoSlab("" + n, null, ptTempi.x, ptTempi.y, ptTempi.z);
      }
    }
  }

}
