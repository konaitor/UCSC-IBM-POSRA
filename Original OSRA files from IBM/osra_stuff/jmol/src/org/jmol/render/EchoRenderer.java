/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-09-26 16:31:12 -0500 (Thu, 26 Sep 2013) $
 * $Revision: 18712 $
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
import org.jmol.modelset.Text;
import org.jmol.script.T;
import org.jmol.shape.Echo;
import org.jmol.util.C;
import org.jmol.viewer.JC;

public class EchoRenderer extends LabelsRenderer {

  @Override
  protected boolean render() {
    if (viewer.isPreviewOnly())
      return false;
    Echo echo = (Echo) shape;
    float scalePixelsPerMicron = (viewer.getBoolean(T.fontscaling) ? viewer
        .getScalePixelsPerAngstrom(true) * 10000 : 0);
    imageFontScaling = viewer.getImageFontScaling();
    boolean haveTranslucent = false;
    for (Text t: echo.objects.values()) {
      if (!t.visible || t.hidden) {
        continue;
      }
      if (t.pointerPt instanceof Atom) {
        if (!((Atom) t.pointerPt).isVisible(-1))
          continue;
      }
      if (t.valign == JC.VALIGN_XYZ) {
        viewer.transformPtScr(t.xyz, pt0i);
        t.setXYZs(pt0i.x, pt0i.y, pt0i.z, pt0i.z);
      } else if (t.movableZPercent != Integer.MAX_VALUE) {
        int z = viewer.zValueFromPercent(t.movableZPercent);
        t.setZs(z, z);
      }
      if (t.pointerPt == null) {
        t.pointer = JC.POINTER_NONE;
      } else {
        t.pointer = JC.POINTER_ON;
        viewer.transformPtScr(t.pointerPt, pt0i);
        t.atomX = pt0i.x;
        t.atomY = pt0i.y;
        t.atomZ = pt0i.z;
        if (t.zSlab == Integer.MIN_VALUE)
          t.zSlab = 1;
      }
      TextRenderer.render(t, viewer, g3d, scalePixelsPerMicron, imageFontScaling,
          false, null, xy);
      if (C.isColixTranslucent(t.bgcolix) || C.isColixTranslucent(t.colix))
        haveTranslucent = true;
    }
    if (!isExport) {
      String frameTitle = viewer.getFrameTitle();
      if (frameTitle != null && frameTitle.length() > 0) {
        if (g3d.setColix(viewer.getColixBackgroundContrast())) {
          if (frameTitle.indexOf("%{") >= 0 || frameTitle.indexOf("@{") >= 0)
            frameTitle = viewer.formatText(frameTitle);
          renderFrameTitle(frameTitle);
        }
      }
    }
    return haveTranslucent;
  }
  
  private void renderFrameTitle(String frameTitle) {
    byte fid = g3d.getFontFidFS("Serif", 14 * imageFontScaling);
    g3d.setFontFid(fid);
    int y = (int) Math.floor(viewer.getScreenHeight() * (g3d.isAntialiased() ? 2 : 1) - 10 * imageFontScaling);
    int x = (int) Math.floor(5 * imageFontScaling);
    g3d.drawStringNoSlab(frameTitle, null, x, y, 0, (short) 0);
  }
}
