/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-12-11 16:07:42 -0600 (Wed, 11 Dec 2013) $
 * $Revision: 19081 $
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

package org.jmol.render;

import org.jmol.modelset.Atom;
import org.jmol.modelset.Group;
import org.jmol.modelset.Text;
import org.jmol.script.T;
import org.jmol.shape.Labels;
import javajs.util.P3;
import javajs.util.P3i;

import org.jmol.util.Point3fi;
import org.jmol.viewer.JC;

public class LabelsRenderer extends FontLineShapeRenderer {

  // offsets are from the font baseline

  final int[] minZ = new int[1];

  protected int ascent;
  protected int descent;
  protected float sppm;
  protected float[] xy = new float[3];
  private P3i screen = new P3i();

  byte fidPrevious;
  
  private int zCutoff;
  private P3 pTemp = new P3();

  protected short bgcolix;
  protected short labelColix;
  
  private byte fid;

  private Atom atom;
  protected Point3fi atomPt;

  private boolean isExact;

  private int offset;

  protected int textAlign;

  private int pointer;

  protected int zSlab = Integer.MIN_VALUE;

  private int zBox;

  private float[] boxXY;

  private float scalePixelsPerMicron;
  
  @Override
  protected boolean render() {
    fidPrevious = 0;
    zCutoff = viewer.getZShadeStart();

    Labels labels = (Labels) shape;

    String[] labelStrings = labels.strings;
    short[] bgcolixes = labels.bgcolixes;
    if (isExport)
      bgcolixes = g3d.getBgColixes(bgcolixes);
    byte[] fids = labels.fids;
    int[] offsets = labels.offsets;
    if (labelStrings == null)
      return false;
    Atom[] atoms = modelSet.atoms;
    short backgroundColixContrast = viewer.getColixBackgroundContrast();
    int backgroundColor = viewer.getBackgroundArgb();
    sppm = viewer.getScalePixelsPerAngstrom(true);
    scalePixelsPerMicron = (viewer.getBoolean(T.fontscaling) ? sppm * 10000f
        : 0);
    imageFontScaling = viewer.getImageFontScaling();
    int iGroup = -1;
    minZ[0] = Integer.MAX_VALUE;
    boolean isAntialiased = g3d.isAntialiased();
    for (int i = labelStrings.length; --i >= 0;) {
      atomPt = atom = atoms[i];
      if (!atom.isVisible(myVisibilityFlag))
        continue;
      String label = labelStrings[i];
      if (label == null 
          || label.length() == 0 || labels.mads != null
          && labels.mads[i] < 0)
        continue;
      labelColix = labels.getColix2(i, atom, false);
      bgcolix = labels.getColix2(i, atom, true);
      if (bgcolix == 0 && g3d.getColorArgbOrGray(labelColix) == backgroundColor)
        labelColix = backgroundColixContrast;
      fid = ((fids == null || i >= fids.length || fids[i] == 0) ? labels.zeroFontId
          : fids[i]);
      int offsetFull = (offsets == null || i >= offsets.length ? 0 : offsets[i]);
      boolean labelsFront = ((offsetFull & JC.LABEL_FRONT_FLAG) != 0);
      boolean labelsGroup = ((offsetFull & JC.LABEL_GROUP_FLAG) != 0);
      isExact = ((offsetFull & JC.LABEL_EXACT_OFFSET_FLAG) != 0);
      offset = offsetFull >> JC.LABEL_FLAG_OFFSET;
      textAlign = Labels.getAlignment(offsetFull);
      pointer = offsetFull & JC.LABEL_POINTER_FLAGS;
      zSlab = atom.sZ - atom.sD / 2 - 3;
      if (zCutoff > 0 && zSlab > zCutoff)
        continue;
      if (zSlab < 1)
        zSlab = 1;
      zBox = zSlab;
      if (labelsGroup) {
        Group group = atom.getGroup();
        int ig = group.getGroupIndex();
        if (ig != iGroup) {
          group.getMinZ(atoms, minZ);
          iGroup = ig;
        }
        zBox = minZ[0];
      } else if (labelsFront) {
        zBox = 1;
      }
      if (zBox < 1)
        zBox = 1;

      Text text = labels.getLabel(i);
      boxXY = (!isExport || viewer.creatingImage ? labels.getBox(i)
          : new float[5]);
      if (boxXY == null)
        labels.putBox(i, boxXY = new float[5]);
      text = renderLabelOrMeasure(text, label);
      if (text != null)
        labels.putLabel(i, text);
      if (isAntialiased) {
        boxXY[0] /= 2;
        boxXY[1] /= 2;
      }
      boxXY[4] = zBox;
    }
    return false;
  }

  protected Text renderLabelOrMeasure(Text text, String label) {
    boolean newText = false;
    if (text != null) {
      if (text.font == null)
        text.setFontFromFid(fid);
      text.atomX = atomPt.sX; // just for pointer
      text.atomY = atomPt.sY;
      text.atomZ = zSlab;
      if (text.pymolOffset == null) {
        text.setXYZs(atomPt.sX, atomPt.sY, zBox, zSlab);
        text.setColix(labelColix);
        text.setBgColix(bgcolix);
      } else {
        if (text.pymolOffset[0] == 1)
          pTemp.setT(atomPt);
        else
          pTemp.set(0, 0, 0);
        pTemp.x += text.pymolOffset[4];
        pTemp.y += text.pymolOffset[5];
        pTemp.z += text.pymolOffset[6];
        viewer.transformPtScr(pTemp, screen);
        text.setXYZs(screen.x, screen.y, screen.z, zSlab);
        text.setScalePixelsPerMicron(sppm);
      }
    } else {
      boolean isLeft = (textAlign == JC.ALIGN_LEFT || textAlign == JC.ALIGN_NONE);
      if (fid != fidPrevious || ascent == 0) {
        g3d.setFontFid(fid);
        fidPrevious = fid;
        font3d = g3d.getFont3DCurrent();
        if (isLeft) {
          ascent = font3d.getAscent();
          descent = font3d.getDescent();
        }
      }
      boolean isSimple = isLeft
          && (imageFontScaling == 1 && scalePixelsPerMicron == 0
              && label.indexOf("|") < 0 && label.indexOf("<su") < 0);
      if (isSimple) {
        boolean doPointer = ((pointer & JC.POINTER_ON) != 0);
        short pointerColix = ((pointer & JC.POINTER_BACKGROUND) != 0
            && bgcolix != 0 ? bgcolix : labelColix);
        boxXY[0] = atomPt.sX;
        boxXY[1] = atomPt.sY;
        TextRenderer.renderSimpleLabel(g3d, font3d, label, labelColix, bgcolix,
            boxXY, zBox, zSlab, JC.getXOffset(offset), JC
                .getYOffset(offset), ascent, descent, doPointer, pointerColix,
            isExact);
        atomPt = null;
      } else {
        text = Text.newLabel(g3d.getGData(), font3d, label, labelColix,
            bgcolix, textAlign, 0, null);
        text.atomX = atomPt.sX; // just for pointer
        text.atomY = atomPt.sY;
        text.atomZ = zSlab;
        text.setXYZs(atomPt.sX, atomPt.sY, zBox, zSlab);
        newText = true;
      }
    }
    if (atomPt != null) {
      if (text.pymolOffset == null) {
        text.setOffset(offset);
        if (textAlign != JC.ALIGN_NONE)
          text.setAlignment(textAlign);
      }
      text.setPointer(pointer);
      TextRenderer.render(text, viewer, g3d, scalePixelsPerMicron,
          imageFontScaling, isExact, boxXY, xy);
    }
    return (newText ? text : null);
  }
}
