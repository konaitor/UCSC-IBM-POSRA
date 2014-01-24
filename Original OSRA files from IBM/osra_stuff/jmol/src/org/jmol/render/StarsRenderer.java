/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-12-11 16:07:42 -0600 (Wed, 11 Dec 2013) $
 * $Revision: 19081 $

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

package org.jmol.render;

import org.jmol.modelset.Atom;
import org.jmol.script.T;
import org.jmol.shape.Shape;
import org.jmol.shape.Stars;
import org.jmol.util.GData;

public class StarsRenderer extends ShapeRenderer {

  private int mar; // milliangstrom radius
  private int width;

  @Override
  protected boolean render() {
    Stars stars = (Stars) shape;
    if (stars.mads == null)
      return false;
    boolean needTranslucent = false;
    mar = (int) (viewer.getFloat(T.starscale) * 1000);
    if (mar == 0 && (g3d.isAntialiased() || isExport))
      mar = 50;
    Atom[] atoms = modelSet.atoms;
    for (int i = modelSet.getAtomCount(); --i >= 0;) {
      Atom atom = atoms[i];
      if (!atom.isVisible(myVisibilityFlag))
        continue;
      colix = Shape.getColix(stars.colixes, i, atom);
      if (g3d.setColix(colix))
        render1(atom, stars.mads[i]);
      else
        needTranslucent = true;
    }
    return needTranslucent;
  }

  private void render1(Atom atom, short mad) {
    int x = atom.sX;
    int y = atom.sY;
    int z = atom.sZ;
    int d = (int) viewer.scaleToScreen(z, mad);
    d -= (d & 1) ^ 1; // round down to odd value
    int r = d / 2;
    if (r < 1)
      r = 1;
    if (mar > 0) {
      width = (int) viewer.scaleToScreen(z, mar);
      if (width == 0)
        width = 1;
      if (width == 1 && g3d.isAntialiased())
        width = 2;
    } else {
      // added to strengthen:
      drawLine(x - r - 1, y + 1, z, x - r - 1 + d, y + 1, z);
      drawLine(x + 1, y + 1 - r, z, x + 1, y + 1 - r + d, z);
    }
    drawLine(x - r, y, z, x - r + d, y, z);
    drawLine(x, y - r, z, x, y - r + d, z);
    drawLine(x, y, z - r, x, y, z - r + d);
  }

  private void drawLine(int xA, int yA, int zA, int xB, int yB, int zB) {
    if (mar > 0)
      g3d.fillCylinderXYZ(colix, colix, GData.ENDCAPS_FLAT, width, xA, yA, zA,
          xB, yB, zB);
    else
      g3d.drawLineXYZ(xA, yA, zA, xB, yB, zB);
  }

}
