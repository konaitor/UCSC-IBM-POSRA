package org.jmol.g3d;

import javajs.awt.Font;
import javajs.util.P3i;


class TextString extends P3i {
  
  String text;
  Font font;
  int argb, bgargb;

  void setText(String text, Font font, int argb, int bgargb, int x, int y, int z) {
    this.text = text;
    this.font = font;
    this.argb = argb;
    this.bgargb = bgargb;
    this.x = x;
    this.y = y;
    this.z = z;
  }
  
  @Override
  public String toString() {
    return super.toString() + " " + text;
  }
}
