/* $RCSfile$
 * $Author: egonw $
 * $Date: 2005-11-10 09:52:44 -0600 (Thu, 10 Nov 2005) $
 * $Revision: 4255 $
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
package org.jmol.modelset;


import org.jmol.util.GData;

import javajs.awt.Font;
import javajs.util.P3;
import javajs.util.PT;

import org.jmol.viewer.JC;
import org.jmol.viewer.Viewer;

public class Text extends Object2d {

  @Override
  public void setScalePixelsPerMicron(float scalePixelsPerMicron) {    
    fontScale = 0;//fontScale * this.scalePixelsPerMicron / scalePixelsPerMicron;
    this.scalePixelsPerMicron = scalePixelsPerMicron;    
  }
  
  public float fontScale;

  public String textUnformatted;
  
  public boolean doFormatText;

  public String[] lines;

  public Font font;
  private byte fid;
  private int ascent;
  public int descent;
  private int lineHeight;

  private int textWidth;
  private int textHeight;
  private String text;
  public String getText() {
    return text;
  }

  private int[] widths;

  private Viewer viewer;

  Text() {
  }

  static public Text newLabel(GData gdata, Font font, String text,
                              short colix, short bgcolix, int align, float scalePixelsPerMicron, float[] value) {
    // for labels and hover
    Text t = new Text();
    t.set(gdata, font, colix, align, true, scalePixelsPerMicron, value);
    t.setText(text);
    t.bgcolix = bgcolix;
    return t;
  }

  public static Text newEcho(Viewer viewer, GData gdata, Font font, String target,
                      short colix, int valign, int align,
                      float scalePixelsPerMicron) {
    // for echo
    Text t = new Text();
    t.set(gdata, font, colix, align, false, scalePixelsPerMicron, null);
    t.viewer = viewer;
    t.target = target;
    if (target.equals("error"))
      valign = JC.VALIGN_TOP;
    t.valign = valign;
    t.z = 2;
    t.zSlab = Integer.MIN_VALUE;
    return t;
  }

  private void set(GData gdata, Font font, short colix, int align, boolean isLabelOrHover,
                   float scalePixelsPerMicron, float[] value) {
    this.scalePixelsPerMicron = scalePixelsPerMicron;
    this.gdata = gdata;
    this.isLabelOrHover = isLabelOrHover;
    this.colix = colix;
    this.align = align;
    this.pymolOffset = value;
    this.setFont(font, isLabelOrHover);
  }

  private void getFontMetrics() {
    descent = font.getDescent();
    ascent = font.getAscent();
    lineHeight = ascent + descent;
  }

  public void setFontFromFid(byte fid) { //labels only
    if (this.fid == fid)
      return;
    fontScale = 0;
    setFont(Font.getFont3D(fid), true);
  }

  public void setText(String text) {
    if (image != null)
      getFontMetrics();
    image = null;
    text = fixText(text);
    if (this.text != null && this.text.equals(text))
      return;
    this.text = text;
    textUnformatted = text;
    doFormatText = (viewer != null && text != null && (text.indexOf("%{") >= 0 || text
        .indexOf("@{") >= 0));
    if (!doFormatText)
      recalc();
  }

  public Object image;
  public float imageScale = 1;

  public  int boxYoff2;
  
  
  public void setImage(Object image) {
    this.image = image;
    // this.text will be file name
    recalc();
  }

  public void setScale(float scale) {
    imageScale = scale;
    recalc();
  }
  
  public void setFont(Font f3d, boolean doAll) {
    font = f3d;
    if (font == null)
      return;
    getFontMetrics();
    if (!doAll)
      return;
    fid = font.fid;
    recalc();
  }

  public void setFontScale(float scale) {
    if (fontScale == scale)
      return;
    fontScale = scale;
    if (fontScale != 0)
      setFont(gdata.getFont3DScaled(font, scale), true);
  }

  String fixText(String text) {
    if (text == null || text.length() == 0)
      return null;
    int pt;
    while ((pt = text.indexOf("\n")) >= 0)
      text = text.substring(0, pt) + "|" + text.substring(pt + 1);
    return text;
  }

  @Override
  protected void recalc() {
    if (image != null) {
      textWidth = textHeight = 0;
      boxWidth = viewer.apiPlatform.getImageWidth(image) * fontScale * imageScale;
      boxHeight = viewer.apiPlatform.getImageHeight(image) * fontScale * imageScale;
      ascent = 0;
      return;
    }
    if (text == null) {
      text = null;
      lines = null;
      widths = null;
      return;
    }
    if (font == null)
      return;
    lines = PT.split(text, "|");
    textWidth = 0;
    widths = new int[lines.length];
    for (int i = lines.length; --i >= 0;)
      textWidth = Math.max(textWidth, widths[i] = stringWidth(lines[i]));
    textHeight = lines.length * lineHeight;
    boxWidth = textWidth + (fontScale >= 2 ? 16 : 8);
    boxHeight = textHeight + (fontScale >= 2 ? 16 : 8);
  }

  public void formatText() {
    text = (viewer == null ? textUnformatted : viewer
        .formatText(textUnformatted));
    recalc();
  }


  public void setPosition(Viewer viewer, int width, int height,
                          float scalePixelsPerMicron, float imageFontScaling,
                          boolean isExact, float[] boxXY) {
    if (boxXY == null)
      boxXY = this.boxXY;
    else
      this.boxXY = boxXY;
    setWindow(width, height, scalePixelsPerMicron);
    if (scalePixelsPerMicron != 0 && this.scalePixelsPerMicron != 0)
      setFontScale(scalePixelsPerMicron / this.scalePixelsPerMicron);
    else if (fontScale != imageFontScaling)
      setFontScale(imageFontScaling);
    if (doFormatText)
      formatText();
    float dx = offsetX * imageFontScaling;
    float dy = offsetY * imageFontScaling;
    xAdj = (fontScale >= 2 ? 8 : 4);
    yAdj = ascent - lineHeight + xAdj;
    if (isLabelOrHover) {
      boxXY[0] = movableX;
      boxXY[1] = movableY;
      if (pymolOffset != null) {
        float pixelsPerAngstrom = viewer.scaleToScreen(z, 1000);
        float pz = pymolOffset[3];
        float dz = (pz < 0 ? -1 : 1) * Math.max(0, Math.abs(pz) - 1) * pixelsPerAngstrom;
        z -= (int) dz;
        pixelsPerAngstrom = viewer.scaleToScreen(z, 1000);
        
        /* for whatever reason, Java returns an 
         * ascent that is considerably higher than a capital X
         * forget leading!
         * ______________________________________________
         *                    leading                      
         *                   ________
         *     X X    
         *      X    ascent
         * __  X X _________ _________         
         * _________ descent 
         *                                   textHeight     
         * _________
         *     X X           lineHeight
         *      X    ascent
         * __  X X__________ _________        ___________        
         * _________ descent  
         *     
         *        
         * 
         */
        dx = getPymolXYOffset(pymolOffset[1], textWidth, pixelsPerAngstrom);
        dy = -getPymolXYOffset(-pymolOffset[2], ascent - descent, pixelsPerAngstrom);
        xAdj = (fontScale >= 2 ? 8 : 4);
        yAdj = 0;
        dy += descent;
        boxXY[0] = movableX - xAdj;
        boxXY[1] = movableY - yAdj;
        y0 = movableY - dy - descent;        
        isExact = true;
        boxYoff2 = -2; // empirica fudge factor 
      } else {
        boxYoff2 = 0;
      }
      setBoxXY(boxWidth, boxHeight, dx, dy, boxXY, isExact);
    } else {
      setPos(fontScale);
    }
    boxX = boxXY[0];
    boxY = boxXY[1];

    // adjust positions if necessary

    if (adjustForWindow)
      setBoxOffsetsInWindow(/*image == null ? fontScale * 5 :*/0,
          isLabelOrHover ? 16 * fontScale + lineHeight : 0, boxY - textHeight);
    if (!isExact)
      y0 = boxY + yAdj;
  }

  private float getPymolXYOffset(float off, int width, float ppa) {
    float f = (off < -1 ? -1 : off > 1 ? 0 : (off - 1) / 2);
    off = (off < -1 || off > 1 ? off + (off < 0 ? 1 : -1) : 0);
    return f * width + off * ppa;
  }

  private void setPos(float scale) {
    float xLeft, xCenter, xRight;
    boolean is3dEcho = (xyz != null);
    if (valign == JC.VALIGN_XY || valign == JC.VALIGN_XYZ) {
      float x = (movableXPercent != Integer.MAX_VALUE ? movableXPercent
          * windowWidth / 100 : is3dEcho ? movableX : movableX * scale);
      float offsetX = this.offsetX * scale;
      xLeft = xRight = xCenter = x + offsetX;
    } else {
      xLeft = 5 * scale;
      xCenter = windowWidth / 2;
      xRight = windowWidth - xLeft;
    }

    // set box X from alignments

    boxXY[0] = xLeft;
    switch (align) {
    case JC.ALIGN_CENTER:
      boxXY[0] = xCenter - boxWidth / 2;
      break;
    case JC.ALIGN_RIGHT:
      boxXY[0] = xRight - boxWidth;
    }

    // set box Y from alignments

    boxXY[1] = 0;
    switch (valign) {
    case JC.VALIGN_TOP:
      break;
    case JC.VALIGN_MIDDLE:
      boxXY[1] = windowHeight / 2;
      break;
    case JC.VALIGN_BOTTOM:
      boxXY[1] = windowHeight;
      break;
    default:
      float y = (movableYPercent != Integer.MAX_VALUE ? movableYPercent
          * windowHeight / 100 : is3dEcho ? movableY : movableY * scale);
      boxXY[1] = (is3dEcho ? y : (windowHeight - y)) + offsetY * scale;
    }

    if (align == JC.ALIGN_CENTER)
      boxXY[1] -= (image != null ? boxHeight : xyz != null ? boxHeight 
          : ascent - boxHeight) / 2;
    else if (image != null)
      boxXY[1] -= 0;
    else if (xyz != null)
      boxXY[1] -= ascent / 2;
  }

  public static void setBoxXY(float boxWidth, float boxHeight, float xOffset,
                               float yOffset, float[] boxXY, boolean isExact) {
    float xBoxOffset, yBoxOffset;

    // these are based on a standard |_ grid, so y is reversed.
    if (xOffset > 0 || isExact) {
      xBoxOffset = xOffset;
    } else {
      xBoxOffset = -boxWidth;
      if (xOffset == 0)
        xBoxOffset /= 2;
      else
        xBoxOffset += xOffset;
    }
    if (isExact) {
      yBoxOffset = -yOffset;
    } else if (yOffset < 0) {
      yBoxOffset = -boxHeight + yOffset;
    } else if (yOffset == 0) {
      yBoxOffset = -boxHeight / 2; // - 2; removed in Jmol 11.7.45 06/24/2009
    } else {
      yBoxOffset = yOffset;
    }
    boxXY[0] += xBoxOffset;
    boxXY[1] += yBoxOffset;
    boxXY[2] = boxWidth;
    boxXY[3] = boxHeight;
  }
  
  private int stringWidth(String str) {
    int w = 0;
    int f = 1;
    int subscale = 1; //could be something less than that
    if (str == null)
      return 0;
    if (str.indexOf("<su") < 0)
      return font.stringWidth(str);
    int len = str.length();
    String s;
    for (int i = 0; i < len; i++) {
      if (str.charAt(i) == '<') {
        if (i + 4 < len
            && ((s = str.substring(i, i + 5)).equals("<sub>") || s
                .equals("<sup>"))) {
          i += 4;
          f = subscale;
          continue;
        }
        if (i + 5 < len
            && ((s = str.substring(i, i + 6)).equals("</sub>") || s
                .equals("</sup>"))) {
          i += 5;
          f = 1;
          continue;
        }
      }
      w += font.stringWidth(str.substring(i, i + 1)) * f;
    }
    return w;
  }

  private float xAdj, yAdj;

  private float y0;

  public P3 pointerPt; // for echo

  public void setXYA(float[] xy, int i) {
    if (i == 0) {
      xy[2] = boxX;
      switch (align) {
      case JC.ALIGN_CENTER:
        xy[2] += boxWidth / 2;
        break;
      case JC.ALIGN_RIGHT:
        xy[2] += boxWidth - xAdj;
        break;
      default:
        xy[2] += xAdj;
      }
      xy[0] = xy[2];
      xy[1] = y0;
    }
    switch (align) {
    case JC.ALIGN_CENTER:
      xy[0] = xy[2] - widths[i] / 2;
      break;
    case JC.ALIGN_RIGHT:
      xy[0] = xy[2] - widths[i];
    }
    xy[1] += lineHeight;
  }

}
