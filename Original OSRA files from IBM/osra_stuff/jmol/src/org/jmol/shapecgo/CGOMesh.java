/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-04-14 23:28:16 -0500 (Sat, 14 Apr 2007) $
 * $Revision: 7408 $
 *
 * Copyright (C) 2005  Miguel, The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net, jmol-developers@lists.sf.net
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

package org.jmol.shapecgo;

import org.jmol.java.BS;
import org.jmol.shapespecial.DrawMesh;
import org.jmol.util.C;

import javajs.util.CU;
import javajs.util.List;
import org.jmol.util.Logger;
import org.jmol.util.Normix;
import javajs.util.T3;

/*
 * Compiled Graphical Object -- ala PyMOL
 * for reading PyMOL PSE files
 * 
 */

public class CGOMesh extends DrawMesh {
  
  public List<Object> cmds;

  CGOMesh(String thisID, short colix, int index) {
    super(thisID, colix, index);
  }
  
  public final static int GL_POINTS = 0;
  public final static int GL_LINES = 1;
  public final static int GL_LINE_LOOP = 2;
  public final static int GL_LINE_STRIP = 3;
  public final static int GL_TRIANGLES = 4;
  public final static int GL_TRIANGLE_STRIP = 5;
  public final static int GL_TRIANGLE_FAN = 6;
  

  private final static int[] sizes = new int[] {
     0,  0,  1,  0,  3,
     3,  3,  4, 27, 13,
     1,  1,  1,  1, 13,
    15,  1, 35, 13,  3, 
     2,  3,  9,  1,  2,
     1, 14, 16,  1,  2
  };
  
  public static int getSize(int i) {
    return (i >= 0 && i < sizes.length ? sizes[i] : -1);
  }
  
  public final static int STOP                = 0;
  public final static int SIMPLE_LINE           = 1;
  public final static int BEGIN               = 2;
  public final static int END                 = 3;
  public final static int VERTEX              = 4;
 
  public final static int NORMAL              = 5;
  public final static int COLOR               = 6;
  public final static int SPHERE              = 7;
  public final static int TRICOLOR_TRIANGLE            = 8;
  public final static int CYLINDER            = 9;
  
  public final static int LINEWIDTH           = 10;
  public final static int WIDTHSCALE          = 11;
  public final static int ENABLE              = 12;
  public final static int DISABLE             = 13;
  public final static int SAUSAGE             = 14;

  public final static int CUSTOM_CYLINDER     = 15;
  public final static int DOTWIDTH            = 16;
  public final static int ALPHA_TRIANGLE      = 17;
  public final static int ELLIPSOID           = 18;
  public final static int FONT                = 19;

  public final static int FONT_SCALE          = 20;
  public final static int FONT_VERTEX         = 21;
  public final static int FONT_AXES           = 22;
  public final static int CHAR                = 23;
  public final static int INDENT              = 24;

  public final static int ALPHA               = 25;
  public final static int QUADRIC             = 26;
  public final static int CONE                = 27;
  public final static int RESET_NORMAL        = 28;
  public final static int PICK_COLOR          = 29;

  @Override
  public void clear(String meshType) {
    super.clear(meshType);
    useColix = false;
  }

  @SuppressWarnings("unchecked")
  boolean set(List<Object> list) {
    // vertices will be in list.get(0). normals?
    width = 200;
    diameter = 0;//200;
    useColix = true;
    bsTemp = new BS();
    try {
      if (list.get(0) instanceof Float) {
        cmds = list;
      } else {
        cmds = (List<Object>) list.get(1);
        if (cmds == null)
          cmds = (List<Object>) list.get(0);
        cmds = (List<Object>) cmds.get(1);
      }

      int n = cmds.size();
      for (int i = 0; i < n; i++) {
        int type = ((Number) cmds.get(i)).intValue();
        int len = getSize(type);
        if (len < 0) {
          Logger.error("CGO unknown type: " + type);
          return false;
        }
        switch (type) {
        case SIMPLE_LINE:
          // para_closed_wt-MD-27.9.12.pse
          // total hack.... could be a strip of lines?
          len = 8;
          break;
        case STOP:
          return true;
        case NORMAL:
          addNormix(i);
          break;
        case COLOR:
          addColix(i);
          useColix = false;
          break;
        case CGOMesh.SAUSAGE:
          addColix(i + 7);
          addColix(i + 10);
          break;
        case CGOMesh.TRICOLOR_TRIANGLE:
          addNormix(i + 9);
          addNormix(i + 12);
          addNormix(i + 15);
          addColix(i + 18);
          addColix(i + 21);
          addColix(i + 24);
          break;
        }
        //Logger.info("CGO " + thisID + " type " + type + " len " + len);
        i += len;
      }
      return true;
    } catch (Exception e) {
      Logger.error("CGOMesh error: " + e);
      cmds = null;
      return false;
    }
  }
  
  private void addColix(int i) {
    getPoint(i, vTemp);
    cList.addLast(Short.valueOf(C.getColix(CU.colorPtToFFRGB(vTemp))));
  }

  private void addNormix(int i) {
    getPoint(i, vTemp);
    nList.addLast(Short.valueOf(Normix.get2SidedNormix(vTemp, bsTemp)));
  }

  public List<Short> nList = new List<Short>();
  public List<Short> cList = new List<Short>();
  
  /**
   * 
   * @param i  pointer to PRECEDING item
   * @param pt
   */
  public void getPoint(int i, T3 pt) {
    pt.set(getFloat(++i), getFloat(++i), getFloat(++i));
  }

  /**
   * 
   * @param i pointer to THIS value
   * @return int
   */
  public int getInt(int i) {
    return ((Number) cmds.get(i)).intValue();
  }

  /**
   * 
   * @param i pointer to THIS value
   * @return float
   */
  public float getFloat(int i) {
    return ((Number) cmds.get(i)).floatValue();
  }
  

}
