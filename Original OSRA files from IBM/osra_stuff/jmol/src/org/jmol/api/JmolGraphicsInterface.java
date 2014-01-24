package org.jmol.api;

import javajs.awt.Font;

public interface JmolGraphicsInterface {

  public abstract boolean isAntialiased();

  public abstract int getRenderHeight();

  public abstract int getRenderWidth();

  public abstract int getSlab();

  public abstract void setSlab(int slabValue);

  public abstract int getColorArgbOrGray(short colix);

  public abstract int getDepth();

  public abstract void setDepth(int depthValue);

  public abstract Font getFont3DScaled(Font font3d, float imageFontScaling);

  public abstract byte getFontFid(float fontSize);

  public abstract boolean isClippedZ(int z);

  public abstract boolean isClippedXY(int diameter, int screenX, int screenY);

  public abstract boolean isInDisplayRange(int x, int y);

  public abstract void renderAllStrings(Object jmolRenderer);

  public abstract void setAmbientOcclusion(int value);

}
