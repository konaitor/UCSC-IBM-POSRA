package org.jmol.shape;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;


import org.jmol.java.BS;
import org.jmol.modelset.Object2d;
import org.jmol.modelset.Text;
import org.jmol.util.Logger;

import javajs.api.GenericPlatform;
import javajs.awt.Font;
import javajs.util.P3;
import org.jmol.util.Txt;

public abstract class Object2dShape extends Shape {

  // Echo, Hover, JmolImage

  public Map<String, Text> objects = new Hashtable<String, Text>();
  Object2d currentObject;
  Font currentFont;
  Object currentColor;
  Object currentBgColor;
  float currentTranslucentLevel;
  float currentBgTranslucentLevel;
  protected String thisID;
  
  boolean isHover;
  boolean isAll;

  protected void setPropOS(String propertyName, Object value, BS bsSelected) {
    if ("allOff" == propertyName) {
      currentObject = null;
      isAll = true;
      objects = new Hashtable<String, Text>();
      return;
    }

    if ("delete" == propertyName) {
      if (currentObject == null) {
        if (isAll || thisID != null) {
          Iterator<Text> e = objects.values().iterator();
          while (e.hasNext()) {
            Text text = e.next();
            if (isAll
                || Txt.isMatch(text.target.toUpperCase(), thisID, true,
                    true)) {
              e.remove();
            }
          }
        }
        return;
      }
      objects.remove(currentObject.target);
      currentObject = null;
      return;
    }

    if ("off" == propertyName) {
      if (isAll) {
        objects = new Hashtable<String, Text>();
        isAll = false;
        currentObject = null;
      }
      if (currentObject == null) {
        return;
      }

      objects.remove(currentObject.target);
      currentObject = null;
      return;
    }

    if ("model" == propertyName) {
      int modelIndex = ((Integer) value).intValue();
      if (currentObject == null) {
        if (isAll)
          for (Text t : objects.values())
            t.setModel(modelIndex);
        return;
      }
      currentObject.setModel(modelIndex);
      return;
    }

    if ("align" == propertyName) {
      String align = (String) value;
      if (currentObject == null) {
        if (isAll)
          for (Object2d obj : objects.values())
            obj.setAlignmentLCR(align);
        return;
      }
      if (!currentObject.setAlignmentLCR(align))
        Logger.error("unrecognized align:" + align);
      return;
    }

    if ("bgcolor" == propertyName) {
      currentBgColor = value;
      if (currentObject == null) {
        if (isAll) {
          Iterator<Text> e = objects.values().iterator();
          while (e.hasNext()) {
            e.next().setBgColixO(value);
          }
        }
        return;
      }
      currentObject.setBgColixO(value);
      return;
    }

    if ("color" == propertyName) {
      currentColor = value;
      if (currentObject == null) {
        if (isAll || thisID != null) {
          Iterator<Text> e = objects.values().iterator();
          while (e.hasNext()) {
            Text text = e.next();
            if (isAll
                || Txt.isMatch(text.target.toUpperCase(), thisID, true,
                    true)) {
              text.setColixO(value);
            }
          }
        }
        return;
      }
      currentObject.setColixO(value);
      return;
    }

    if ("target" == propertyName) {
      String target = (String) value;
      isAll = target.equals("all");
      if (isAll || target.equals("none")) {
        currentObject = null;
      }
      //handled by individual types -- echo or hover
      return;
    }

    boolean isBackground;
    if ((isBackground = ("bgtranslucency" == propertyName))
        || "translucency" == propertyName) {
      boolean isTranslucent = ("translucent" == value);
      if (isBackground)
        currentBgTranslucentLevel = (isTranslucent ? translucentLevel : 0);
      else
        currentTranslucentLevel = (isTranslucent ? translucentLevel : 0);
      if (currentObject == null) {
        if (isAll) {
          Iterator<Text> e = objects.values().iterator();
          while (e.hasNext()) {
            e.next().setTranslucent(translucentLevel, isBackground);
          }
        }
        return;
      }
      currentObject.setTranslucent(translucentLevel, isBackground);
      return;
    }

    if (propertyName == "deleteModelAtoms") {
      int modelIndex = ((int[]) ((Object[]) value)[2])[0];
      Iterator<Text> e = objects.values().iterator();
      while (e.hasNext()) {
        Text text = e.next();
        if (text.modelIndex == modelIndex) {
          e.remove();
        } else if (text.modelIndex > modelIndex) {
          text.modelIndex--;
        }
      }
      return;
    }

    setPropS(propertyName, value, bsSelected);
  }

  @Override
  protected void initModelSet() {
    currentObject = null;
    isAll = false;
  }


  @Override
  public void setVisibilityFlags(BS bs) {
    if (!isHover)
      for (Text t : objects.values())
        t.setVisibility(t.modelIndex < 0 || bs.get(t.modelIndex));
  }

  @Override
  public Map<String, Object> checkObjectClicked(int x, int y, int modifiers, BS bsVisible, boolean drawPicking) {
    if (isHover || modifiers == 0)
      return null;
    boolean isAntialiased = viewer.isAntialiased();
    for (Object2d obj: objects.values()) {
      if (obj.checkObjectClicked(isAntialiased, x, y, bsVisible)) {
        String s = obj.getScript();
        if (s != null) {
          viewer.evalStringQuiet(s);
        }
        Map<String, Object> map = new Hashtable<String, Object>();
        map.put("pt", (obj.xyz == null ? new P3() : obj.xyz));
        int modelIndex = obj.modelIndex;
        if (modelIndex < 0)
          modelIndex = 0;
        map.put("modelIndex", Integer.valueOf(modelIndex));
        map.put("model", viewer.getModelNumberDotted(modelIndex));
        map.put("id", obj.target);
        map.put("type", "echo");
        return map;
      }
    }
    return null;
  }

  @Override
  public boolean checkObjectHovered(int x, int y, BS bsVisible) {
    if (isHover)
      return false;
    boolean haveScripts = false;
    boolean isAntialiased = viewer.isAntialiased();
    for (Object2d obj: objects.values()) {
      String s = obj.getScript();
      if (s != null) {
        haveScripts = true;
        if (obj.checkObjectClicked(isAntialiased, x, y, bsVisible)) {
          viewer.setCursor(GenericPlatform.CURSOR_HAND);
          return true;
        }
      }
    }
    if (haveScripts)
      viewer.setCursor(GenericPlatform.CURSOR_DEFAULT);
    return false;
  }

}
