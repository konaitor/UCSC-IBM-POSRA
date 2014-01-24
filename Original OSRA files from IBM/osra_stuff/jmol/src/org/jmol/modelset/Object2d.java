package org.jmol.modelset;

import org.jmol.java.BS;
import org.jmol.util.C;
import org.jmol.util.GData;
import javajs.util.P3;
import org.jmol.viewer.JC;

public abstract class Object2d {

  // Echo, Label

  public boolean isLabelOrHover;
  protected GData gdata;
  public P3 xyz;
  public String target;
  public String script;
  public short colix;
  public short bgcolix;
  public int pointer;

  public int align;
  public int valign;
  public int atomX, atomY, atomZ = Integer.MAX_VALUE;
  public int movableX, movableY, movableZ;
  public int movableXPercent = Integer.MAX_VALUE;
  public int movableYPercent = Integer.MAX_VALUE;
  public int movableZPercent = Integer.MAX_VALUE;

  protected int offsetX;
  protected int offsetY;
  public int z = 1; // front plane
  public int zSlab = Integer.MIN_VALUE; // z for slabbing purposes -- may be near an atom

  // PyMOL-type offset
  // [mode, screenoffsetx,y,z (applied after tranform), positionOffsetx,y,z (applied before transform)]
  public float[] pymolOffset;

  protected int windowWidth;
  protected int windowHeight;
  protected boolean adjustForWindow;
  public float boxWidth;
  public float boxHeight;
  public float boxX;
  public float boxY;

  public int modelIndex = -1;
  public boolean visible = true;
  public boolean hidden = false;

  public float[] boxXY = new float[5];

  public float scalePixelsPerMicron;

  public float getScalePixelsPerMicron() {
    return scalePixelsPerMicron;
  }

  public void setScalePixelsPerMicron(float scalePixelsPerMicron) {
    this.scalePixelsPerMicron = scalePixelsPerMicron;
  }

  abstract protected void recalc();

  public void setModel(int modelIndex) {
    this.modelIndex = modelIndex;
  }

  public void setVisibility(boolean TF) {
    visible = TF;
  }

  public void setXYZ(P3 xyz, boolean doAdjust) {
    this.xyz = xyz;
    if (xyz == null)
      this.zSlab = Integer.MIN_VALUE;
    if (doAdjust) {
      valign = (xyz == null ? JC.VALIGN_XY : JC.VALIGN_XYZ);
      setAdjustForWindow(xyz == null);
    }
  }

  public void setAdjustForWindow(boolean TF) {
    adjustForWindow = TF;
  }

  public void setColix(short colix) {
    this.colix = colix;
  }

  public void setColixO(Object value) {
    colix = C.getColixO(value);
  }

  public void setTranslucent(float level, boolean isBackground) {
    if (isBackground) {
      if (bgcolix != 0)
        bgcolix = C.getColixTranslucent3(bgcolix, !Float.isNaN(level), level);
    } else {
      colix = C.getColixTranslucent3(colix, !Float.isNaN(level), level);
    }
  }

  public void setBgColix(short colix) {
    this.bgcolix = colix;
  }

  public void setBgColixO(Object value) {
    bgcolix = (value == null ? (short) 0 : C.getColixO(value));
  }

  private void setMovableX(int x) {
    valign = (valign == JC.VALIGN_XYZ ? JC.VALIGN_XYZ : JC.VALIGN_XY);
    movableX = x;
    movableXPercent = Integer.MAX_VALUE;
  }

  private void setMovableY(int y) {
    valign = (valign == JC.VALIGN_XYZ ? JC.VALIGN_XYZ : JC.VALIGN_XY);
    movableY = y;
    movableYPercent = Integer.MAX_VALUE;
  }

  //  public void setMovableZ(int z) {
  //    if (valign != VALIGN_XYZ)
  //      valign = VALIGN_XY;
  //    movableZ = z;
  //    movableZPercent = Integer.MAX_VALUE;
  //  }

  public void setMovableXPercent(int x) {
    valign = (valign == JC.VALIGN_XYZ ? JC.VALIGN_XYZ : JC.VALIGN_XY);
    movableX = Integer.MAX_VALUE;
    movableXPercent = x;
  }

  public void setMovableYPercent(int y) {
    valign = (valign == JC.VALIGN_XYZ ? JC.VALIGN_XYZ : JC.VALIGN_XY);
    movableY = Integer.MAX_VALUE;
    movableYPercent = y;
  }

  public void setMovableZPercent(int z) {
    if (valign != JC.VALIGN_XYZ)
      valign = JC.VALIGN_XY;
    movableZ = Integer.MAX_VALUE;
    movableZPercent = z;
  }

  public void setZs(int z, int zSlab) {
    this.z = z;
    this.zSlab = zSlab;
  }

  public void setXYZs(int x, int y, int z, int zSlab) {
    setMovableX(x);
    setMovableY(y);
    setZs(z, zSlab);
  }

  public void setScript(String script) {
    this.script = (script == null || script.length() == 0 ? null : script);
  }

  public String getScript() {
    return script;
  }

  public void setOffset(int offset) {
    //Labels only
    offsetX = JC.getXOffset(offset);
    offsetY = JC.getYOffset(offset);
    pymolOffset = null;
    valign = JC.VALIGN_XY;
  }

  public boolean setAlignmentLCR(String align) {
    if ("left".equals(align))
      return setAlignment(JC.ALIGN_LEFT);
    if ("center".equals(align))
      return setAlignment(JC.ALIGN_CENTER);
    if ("right".equals(align))
      return setAlignment(JC.ALIGN_RIGHT);
    return false;
  }

  public boolean setAlignment(int align) {
    if (this.align != align) {
      this.align = align;
      recalc();
    }
    return true;
  }

  public void setPointer(int pointer) {
    this.pointer = pointer;
  }

  public void setBoxOffsetsInWindow(float margin, float vMargin, float vTop) {
    // not labels

    // these coordinates are (0,0) in top left
    // (user coordinates are (0,0) in bottom left)
    float bw = boxWidth + margin;
    float x = boxX;
    if (x + bw > windowWidth)
      x = windowWidth - bw;
    if (x < margin)
      x = margin;
    boxX = x;

    float bh = boxHeight;
    float y = vTop;
    if (y + bh > windowHeight)
      y = windowHeight - bh;
    if (y < vMargin)
      y = vMargin;
    boxY = y;
  }

  public void setWindow(int width, int height, float scalePixelsPerMicron) {
    windowWidth = width;
    windowHeight = height;
    if (pymolOffset == null && this.scalePixelsPerMicron < 0
        && scalePixelsPerMicron != 0)
      this.scalePixelsPerMicron = scalePixelsPerMicron;
  }

  public boolean checkObjectClicked(boolean isAntialiased, int x, int y,
                                    BS bsVisible) {
    if (hidden || script == null || modelIndex >= 0 && !bsVisible.get(modelIndex))
      return false;
    if (isAntialiased) {
      x <<= 1;
      y <<= 1;
    }
    return (x >= boxX && x <= boxX + boxWidth && y >= boxY && y <= boxY
        + boxHeight);
  }

  public static boolean setProperty(String propertyName, Object value,
                                    Object2d currentObject) {

    if ("script" == propertyName) {
      if (currentObject != null)
        currentObject.setScript((String) value);
      return true;
    }

    if ("xpos" == propertyName) {
      if (currentObject != null)
        currentObject.setMovableX(((Integer) value).intValue());
      return true;
    }

    if ("ypos" == propertyName) {
      if (currentObject != null)
        currentObject.setMovableY(((Integer) value).intValue());
      return true;
    }

    if ("%xpos" == propertyName) {
      if (currentObject != null)
        currentObject.setMovableXPercent(((Integer) value).intValue());
      return true;
    }

    if ("%ypos" == propertyName) {
      if (currentObject != null)
        currentObject.setMovableYPercent(((Integer) value).intValue());
      return true;
    }

    if ("%zpos" == propertyName) {
      if (currentObject != null)
        currentObject.setMovableZPercent(((Integer) value).intValue());
      return true;
    }

    if ("xypos" == propertyName) {
      if (currentObject == null)
        return true;
      P3 pt = (P3) value;
      currentObject.setXYZ(null, true);
      if (pt.z == Float.MAX_VALUE) {
        currentObject.setMovableX((int) pt.x);
        currentObject.setMovableY((int) pt.y);
      } else {
        currentObject.setMovableXPercent((int) pt.x);
        currentObject.setMovableYPercent((int) pt.y);
      }
      return true;
    }

    if ("xyz" == propertyName) {
      if (currentObject != null) {
        currentObject.setXYZ((P3) value, true);
      }
      return true;
    }
    return false;
  }

}
