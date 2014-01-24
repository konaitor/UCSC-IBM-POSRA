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
import org.jmol.modelset.Text;
import org.jmol.shape.Hover;

public class HoverRenderer extends ShapeRenderer {
  
  private float[] tempXY = new float[3];

  @SuppressWarnings("static-access")
  @Override
  protected boolean render() {
    // hover rendering always involves translucent pass
    if (viewer.isNavigating())
      return false;
    Hover hover = (Hover) shape;
    boolean antialias = g3d.isAntialiased();
    Text text = hover.hoverText;
    if (hover.atomIndex >= 0) {
      Atom atom = modelSet.atoms[hover.atomIndex];
      String label = (hover.specialLabel != null ? hover.specialLabel 
          : hover.atomFormats != null
          && hover.atomFormats[hover.atomIndex] != null ? 
              viewer.modelSet.getLabeler().formatLabel(viewer, atom, hover.atomFormats[hover.atomIndex])
          : hover.labelFormat != null ? viewer.modelSet.getLabeler().formatLabel(viewer, atom, fixLabel(atom, hover.labelFormat))
              : null);
      if (label == null)
        return false;
      text.setText(label);
      text.setXYZs(atom.sX, atom.sY, 1, Integer.MIN_VALUE);
    } else if (hover.text != null) {
      text.setText(hover.text);
      text.setXYZs(hover.xy.x, hover.xy.y, 1, Integer.MIN_VALUE);
    } else {
      return true;
    }
    TextRenderer.render(text, viewer, g3d, 0, antialias ? 2 : 1, false, null, tempXY );
    return true;
  }
  
  String fixLabel(Atom atom, String label) {
    if (label == null)
      return null;
    return (viewer.isJmolDataFrameForModel(atom.getModelIndex()) 
        && label.equals("%U") ?"%W" : label);
  }
}
