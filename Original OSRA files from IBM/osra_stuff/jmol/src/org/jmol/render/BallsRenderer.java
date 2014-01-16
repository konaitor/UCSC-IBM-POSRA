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


import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.script.T;
import org.jmol.shape.Balls;
import org.jmol.shape.Shape;

public class BallsRenderer extends ShapeRenderer {

  @Override
  protected boolean render() {
    boolean needTranslucent = false;
    if (isExport || viewer.checkMotionRendering(T.atoms)) {
      Atom[] atoms = modelSet.atoms;
      short[] colixes = ((Balls) shape).colixes;
      BS bsOK = viewer.getRenderableBitSet();
      for (int i = bsOK.nextSetBit(0); i >= 0; i = bsOK.nextSetBit(i + 1)) {
        Atom atom = atoms[i];
        if (atom.sD > 0
            && (atom.getShapeVisibilityFlags() & myVisibilityFlag) != 0) {
          if (g3d.setColix(colixes == null ? atom.getColix() : Shape.getColix(colixes, i, atom))) {
            g3d.drawAtom(atom);
          } else {
            needTranslucent = true;
          }
        }
      }
    }
    return needTranslucent;
  }

}
