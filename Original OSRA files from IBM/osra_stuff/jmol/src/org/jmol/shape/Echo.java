/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-10-17 12:05:38 -0500 (Thu, 17 Oct 2013) $
 * $Revision: 18805 $
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

package org.jmol.shape;

import org.jmol.java.BS;
import org.jmol.modelset.Object2d;
import org.jmol.modelset.Text;
import org.jmol.script.T;
import org.jmol.util.C;
import javajs.util.P3;
import org.jmol.util.Txt;
import org.jmol.viewer.JC;

public class Echo extends TextShape {

  /*
   * set echo Text.TOP    [Text.LEFT|Text.CENTER|Text.RIGHT]
   * set echo MIDDLE [Text.LEFT|Text.CENTER|Text.RIGHT]
   * set echo BOTTOM [Text.LEFT|Text.CENTER|Text.RIGHT]
   * set echo name   [Text.LEFT|Text.CENTER|Text.RIGHT]
   * set echo name  x-position y-position
   * set echo none  to initiate setting default settings
   * 
   */

  private final static String FONTFACE = "Serif";
  private final static int FONTSIZE = 20;
  private final static short COLOR = C.RED;
    
  @Override
  public void initShape() {
    super.initShape();
    setProperty("target", "top", null);
  }

  @Override
  public void setProperty(String propertyName, Object value, BS bs) {

    if ("scalereference" == propertyName) {
      if (currentObject != null) {
        float val = ((Float) value).floatValue();
        currentObject.setScalePixelsPerMicron(val == 0 ? 0 : 10000f / val);
      }
      return;
    }

    if ("point" == propertyName) {
      if (currentObject == null)
        return;
      Text t = (Text) currentObject;
      t.pointerPt = (value == null ? null : (P3) value); // could be an atom.
      t.pointer = (value == null ? JC.POINTER_NONE : JC.POINTER_ON);
      return;
    }
    if ("xyz" == propertyName) {
      if (currentObject != null && viewer.getBoolean(T.fontscaling))
        currentObject.setScalePixelsPerMicron(viewer
            .getScalePixelsPerAngstrom(false) * 10000f);
      // continue on to Object2d setting
    }

    if ("scale" == propertyName) {
      if (currentObject == null) {
        if (isAll)
          for (Text t : objects.values())
            t.setScale(((Float) value).floatValue());
        return;
      }
      ((Text) currentObject).setScale(((Float) value).floatValue());
      return;
    }
    if ("image" == propertyName) {
      if (currentObject == null) {
        if (isAll)
          for (Text t : objects.values())
            t.setImage(value);
        return;
      }
      ((Text) currentObject).setImage(value);
      return;
    }
    if ("thisID" == propertyName) {
      String target = (String) value;
      currentObject = objects.get(target);
      if (currentObject == null && Txt.isWild(target))
        thisID = target.toUpperCase();
      return;
    }

    if ("hidden" == propertyName) {
      boolean isHidden = ((Boolean) value).booleanValue();
      if (currentObject == null) {
        if (isAll || thisID != null)
          for (Text t : objects.values())
            if (isAll
                || Txt.isMatch(t.target.toUpperCase(), thisID, true,
                    true))
              t.hidden = isHidden;
        return;
      }
      ((Text) currentObject).hidden = isHidden;
      return;
    }

    if (Object2d.setProperty(propertyName, value, currentObject))
      return;

    if ("target" == propertyName) {
      thisID = null;
      String target = ((String) value).intern().toLowerCase();
      if (target == "none" || target == "all") {
        // process in Object2dShape
      } else {
        isAll = false;
        Text text = objects.get(target);
        if (text == null) {
          int valign = JC.VALIGN_XY;
          int halign = JC.ALIGN_LEFT;
          if ("top" == target) {
            valign = JC.VALIGN_TOP;
            halign = JC.ALIGN_CENTER;
          } else if ("middle" == target) {
            valign = JC.VALIGN_MIDDLE;
            halign = JC.ALIGN_CENTER;
          } else if ("bottom" == target) {
            valign = JC.VALIGN_BOTTOM;
          }
          text = Text.newEcho(viewer, gdata, gdata.getFont3DFS(FONTFACE,
              FONTSIZE), target, COLOR, valign, halign, 0);
          text.setAdjustForWindow(true);
          objects.put(target, text);
          if (currentFont != null)
            text.setFont(currentFont, true);
          if (currentColor != null)
            text.setColixO(currentColor);
          if (currentBgColor != null)
            text.setBgColixO(currentBgColor);
          if (currentTranslucentLevel != 0)
            text.setTranslucent(currentTranslucentLevel, false);
          if (currentBgTranslucentLevel != 0)
            text.setTranslucent(currentBgTranslucentLevel, true);
        }
        currentObject = text;
        return;
      }
    }
    setPropTS(propertyName, value, null);
  }

  @Override
  public boolean getPropertyData(String property, Object[] data) {
    if ("currentTarget" == property) {
      return (currentObject != null && (data[0] = currentObject.target) != null);
    }
    if (property == "checkID") {
      String key = ((String) data[0]).toUpperCase();
      boolean isWild = Txt.isWild(key);
      for (Text t: objects.values()) {
        String id = t.target;
        if (id.equalsIgnoreCase(key) || isWild
            && Txt.isMatch(id.toUpperCase(), key, true, true)) {
          data[1] = id;
          return true;
        }
      }
      return false;
    }
    return false;
  }

  @Override
  public String getShapeState() {
    return viewer.getShapeState(this);
  }
}
