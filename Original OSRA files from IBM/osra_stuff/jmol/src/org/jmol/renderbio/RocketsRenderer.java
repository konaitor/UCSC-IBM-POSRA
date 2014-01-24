/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-03-11 14:30:16 -0500 (Sun, 11 Mar 2007) $
 * $Revision: 7068 $
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

package org.jmol.renderbio;

import org.jmol.constant.EnumStructure;
import org.jmol.modelsetbio.AlphaPolymer;
import org.jmol.modelsetbio.Helix;
import org.jmol.modelsetbio.Monomer;
import org.jmol.modelsetbio.ProteinStructure;
import org.jmol.modelsetbio.Sheet;
import org.jmol.script.T;
import org.jmol.shapebio.BioShape;
import org.jmol.util.GData;
import javajs.util.P3;
import javajs.util.V3;

public class RocketsRenderer extends StrandsRenderer {

  //private final static float MIN_CONE_HEIGHT = 0.05f;

  protected boolean newRockets = false;
  protected boolean renderArrowHeads;

  @Override
  protected void renderBioShape(BioShape bioShape) {
    if (!(bioShape.bioPolymer instanceof AlphaPolymer))
      return;
    if (wireframeOnly) {
      renderStrands();
      return;
    }
    boolean val = !viewer.getBoolean(T.rocketbarrels);
    if (renderArrowHeads != val) {
      bioShape.falsifyMesh();
      renderArrowHeads = val;
    }
    calcRopeMidPoints(newRockets);
    calcScreenControlPoints(cordMidPoints);
    controlPoints = cordMidPoints;
    renderRockets();
    viewer.freeTempPoints(cordMidPoints);
  }

  protected P3[] cordMidPoints;

  protected boolean isSheet(int i) {
    return structureTypes[i] == EnumStructure.SHEET;
  }

  protected void calcRopeMidPoints(boolean isNewStyle) {
    int midPointCount = monomerCount + 1;
    cordMidPoints = viewer.allocTempPoints(midPointCount);
    ProteinStructure proteinstructurePrev = null;
    P3 point;
    for (int i = 0; i < monomerCount; ++i) {
      point = cordMidPoints[i];
      Monomer residue = monomers[i];
      if (isNewStyle && renderArrowHeads) {
        point.setT(controlPoints[i]);
      } else if (isHelix(i) || !isNewStyle && isSheet(i)) {
        ProteinStructure proteinstructure = residue.getProteinStructure();
        point
            .setT(i - 1 != proteinstructure.getMonomerIndex() ? proteinstructure
                .getAxisStartPoint() : proteinstructure.getAxisEndPoint());
        proteinstructurePrev = proteinstructure;
      } else {
        if (proteinstructurePrev != null)
          point.setT(proteinstructurePrev.getAxisEndPoint());
        else {
          point.setT(controlPoints[i]);
        }
        proteinstructurePrev = null;
      }
    }
    point = cordMidPoints[monomerCount];
    if (proteinstructurePrev != null)
      point.setT(proteinstructurePrev.getAxisEndPoint());
    else {
      point.setT(controlPoints[monomerCount]);
    }
  }

  protected void renderRockets() {
    tPending = false;
    for (int i = bsVisible.nextSetBit(0); i >= 0; i = bsVisible
        .nextSetBit(i + 1)) {
      Monomer monomer = monomers[i];
      if (isHelix(i) || isSheet(i)) {
        renderSpecialSegment(monomer, getLeadColix(i), mads[i]);
      } else {
        renderPending();
        renderHermiteConic(i, true);
      }
    }
    renderPending();
  }

  protected void renderSpecialSegment(Monomer monomer, short thisColix,
                                      short thisMad) {
    ProteinStructure proteinstructure = monomer.getProteinStructure();
    if (tPending) {
      if (proteinstructure == proteinstructurePending && thisMad == mad
          && thisColix == colix
          && proteinstructure.getIndex(monomer) == endIndexPending + 1) {
        ++endIndexPending;
        return;
      }
      renderPending();
    }
    proteinstructurePending = proteinstructure;
    startIndexPending = endIndexPending = proteinstructure.getIndex(monomer);
    colix = thisColix;
    mad = thisMad;
    tPending = true;
  }

  protected boolean tPending;
  private ProteinStructure proteinstructurePending;
  private int startIndexPending;
  private int endIndexPending;

  protected void renderPending() {
    if (!tPending)
      return;
    P3[] segments = proteinstructurePending.getSegments();
    boolean tEnd = (endIndexPending == proteinstructurePending
        .getMonomerCount() - 1);
    if (proteinstructurePending instanceof Helix)
      renderPendingRocketSegment(endIndexPending, segments[startIndexPending],
          segments[endIndexPending], segments[endIndexPending + 1], tEnd);
    else if (proteinstructurePending instanceof Sheet)
      renderPendingSheet(segments[startIndexPending],
          segments[endIndexPending], segments[endIndexPending + 1], tEnd);
    tPending = false;
  }

  private P3 screenA = new P3();
  private P3 screenB = new P3();
  private P3 screenC = new P3();
  private V3 vtemp = new V3();

  /**
   * @param i
   * @param pointStart
   * @param pointBeforeEnd
   *        ignored now that arrow heads protrude beyond end of rocket
   * @param pointEnd
   * @param tEnd
   */
  private void renderPendingRocketSegment(int i, P3 pointStart,
                                          P3 pointBeforeEnd, P3 pointEnd,
                                          boolean tEnd) {
    viewer.transformPt3f(pointStart, screenA);
    viewer.transformPt3f(pointEnd, screenB);
    int zMid = (int) Math.floor((screenA.z + screenB.z) / 2f);
    int diameter = (int) viewer.scaleToScreen(zMid, mad);
    if (g3d.setColix(colix)) {
      g3d.fillCylinderBits(GData.ENDCAPS_FLAT, diameter, screenA, screenB);
      if (tEnd && renderArrowHeads) {
        vtemp.sub2(pointEnd, pointStart);
        vtemp.normalize();
        screenA.scaleAdd2(4.0f, vtemp, pointEnd);
        viewer.transformPt3f(screenA, screenC);
        renderCone(i, pointEnd, screenA, screenB, screenC);
      }
      if (startIndexPending == endIndexPending)
        return;
      P3 t = screenB;
      screenB = screenC;
      screenC = t;
    }
  }

  //  rockets --not satisfactory yet
  /**
   * @param i
   *        IGNORED
   * @param pointBegin
   *        IGNORED
   * @param pointEnd
   *        IGNORED
   * @param screenPtBegin
   * @param screenPtEnd
   * 
   */
  protected void renderCone(int i, P3 pointBegin, P3 pointEnd,
                            P3 screenPtBegin, P3 screenPtEnd) {
    int coneDiameter = (mad << 1) - (mad >> 1);
    coneDiameter = (int) viewer.scaleToScreen(
        (int) Math.floor(screenPtBegin.z), coneDiameter);
    g3d.fillConeSceen3f(GData.ENDCAPS_FLAT, coneDiameter, screenPtBegin,
        screenPtEnd);
  }

  private void renderPendingSheet(P3 ptStart, P3 pointBeforeEnd,
                                  P3 ptEnd, boolean tEnd) {
    if (!g3d.setColix(colix))
      return;
    if (corners[0] == null)
      for (int i = 8; --i >= 0;) {
        corners[i] = new P3();
        screenCorners[i] = new P3();
      }
    if (tEnd && renderArrowHeads) {
      setBox(1.25f, 0.333f, pointBeforeEnd);
      ptTip.scaleAdd2(-0.5f, vH, ptEnd);
      for (int i = 4; --i >= 0;) {
        P3 corner = corners[i];
        corner.setT(ptC);
        if ((i & 1) != 0)
          corner.add(vW);
        if ((i & 2) != 0)
          corner.add(vH);
        viewer.transformPt3f(corner, screenCorners[i]);
      }
      corners[4].setT(ptTip);
      viewer.transformPt3f(ptTip, screenCorners[4]);
      corners[5].add2(ptTip, vH);
      viewer.transformPt3f(corners[5], screenCorners[5]);

      g3d.fillTriangle3f(screenCorners[0], screenCorners[1], screenCorners[4],
          true);
      g3d.fillTriangle3f(screenCorners[2], screenCorners[3], screenCorners[5],
          true);
      for (int i = 0; i < 12; i += 4) {
        int i0 = arrowHeadFaces[i];
        int i1 = arrowHeadFaces[i + 1];
        int i2 = arrowHeadFaces[i + 2];
        int i3 = arrowHeadFaces[i + 3];
        g3d.fillQuadrilateral(screenCorners[i0], screenCorners[i1],
            screenCorners[i2], screenCorners[i3]);
      }
      ptEnd = pointBeforeEnd;
    }
    setBox(1f, 0.25f, ptStart);
    vTemp.sub2(ptEnd, ptStart);
    buildBox(ptC, vW, vH, vTemp);
    for (int i = 0; i < 6; ++i) {
      int i0 = boxFaces[i * 4];
      int i1 = boxFaces[i * 4 + 1];
      int i2 = boxFaces[i * 4 + 2];
      int i3 = boxFaces[i * 4 + 3];
      g3d.fillQuadrilateral(screenCorners[i0], screenCorners[i1],
          screenCorners[i2], screenCorners[i3]);
    }
  }

  private void setBox(float w, float h, P3 pt) {
    Sheet sheet = (Sheet) proteinstructurePending;
    float scale = mad / 1000f;
    vW.setT(sheet.getWidthUnitVector());
    vW.scale(scale * w);
    vH.setT(sheet.getHeightUnitVector());
    vH.scale(scale * h);
    ptC.ave(vW, vH);
    ptC.sub2(pt, ptC);
  }

  private void buildBox(P3 pointCorner, V3 scaledWidthVector,
                        V3 scaledHeightVector, V3 lengthVector) {
    for (int i = 8; --i >= 0;) {
      P3 corner = corners[i];
      corner.setT(pointCorner);
      if ((i & 1) != 0)
        corner.add(scaledWidthVector);
      if ((i & 2) != 0)
        corner.add(scaledHeightVector);
      if ((i & 4) != 0)
        corner.add(lengthVector);
      viewer.transformPt3f(corner, screenCorners[i]);
    }
  }

  private final V3 vTemp = new V3();
  private final P3 ptC = new P3();
  private final P3 ptTip = new P3();
  private final V3 vW = new V3();
  private final V3 vH = new V3();
  private final P3[] corners = new P3[8];
  private final P3[] screenCorners = new P3[8];

  private final static byte[] boxFaces =
  {
    0, 1, 3, 2,
    0, 2, 6, 4,
    0, 4, 5, 1,
    7, 5, 4, 6,
    7, 6, 2, 3,
    7, 3, 1, 5 };

  private final static byte arrowHeadFaces[] =
  {0, 1, 3, 2,
   0, 4, 5, 2,
   1, 4, 5, 3};

}
