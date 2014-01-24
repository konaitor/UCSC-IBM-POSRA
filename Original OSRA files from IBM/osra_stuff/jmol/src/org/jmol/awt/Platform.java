package org.jmol.awt;

import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JDialog;


import netscape.javascript.JSObject;

import org.jmol.api.Interface;

import javajs.api.GenericPlatform;
import javajs.api.GenericFileInterface;
import javajs.api.GenericMouseInterface;
import javajs.api.GenericMenuInterface;
import javajs.api.PlatformViewer;
import javajs.awt.Font;
import javajs.util.P3;

public class Platform implements GenericPlatform {

  PlatformViewer viewer;
  
  @Override
  public void setViewer(PlatformViewer viewer, Object display) {
    this.viewer = viewer;
  }
  
  ///// Display 

  @Override
  public void convertPointFromScreen(Object display, P3 ptTemp) {
    Display.convertPointFromScreen(display, ptTemp);
  }

  @Override
  public void getFullScreenDimensions(Object display, int[] widthHeight) {
    Display.getFullScreenDimensions(display, widthHeight);        
  }
  
  @Override
  public GenericMenuInterface getMenuPopup(String menuStructure, char type) {
    GenericMenuInterface jmolpopup = (GenericMenuInterface) Interface.getOptionInterface(
        type == 'j' ? "popup.JmolPopup" : "modelkit.ModelKitPopup");
    if (jmolpopup != null)
      jmolpopup.jpiInitialize(viewer, menuStructure);
    return jmolpopup;
  }

  @Override
  public boolean hasFocus(Object display) {
    return Display.hasFocus(display);
  }

  @Override
  public String prompt(String label, String data, String[] list,
                       boolean asButtons) {
    return Display.prompt(label, data, list, asButtons);
  }

  /**
   * legacy apps will use this
   * 
   * @param g
   * @param size
   */
  @Override
  public void renderScreenImage(Object g, Object size) {
    Display.renderScreenImage(viewer, g, size);
  }

  @Override
  public void requestFocusInWindow(Object display) {
    Display.requestFocusInWindow(display);
  }

  @Override
  public void repaint(Object display) {
    Display.repaint(display);
  }

  @Override
  public void setTransparentCursor(Object display) {
    Display.setTransparentCursor(display);
  }

  @Override
  public void setCursor(int c, Object display) {
    Display.setCursor(c, display);
  }

  ////// Mouse

  @Override
  public GenericMouseInterface getMouseManager(double privateKey, Object display) {
    return new Mouse(privateKey, viewer, display);
  }

  ////// Image 

  @Override
  public Object allocateRgbImage(int windowWidth, int windowHeight,
                                 int[] pBuffer, int windowSize,
                                 boolean backgroundTransparent, boolean isImageWrite) {
    return Image.allocateRgbImage(windowWidth, windowHeight, pBuffer, windowSize, backgroundTransparent);
  }

  /**
   * could be byte[] (from ZIP file) or String (local file name) or URL
   * @param data 
   * @return image object
   * 
   */
  @Override
  public Object createImage(Object data) {
    return Image.createImage(data);
  }

  @Override
  public void disposeGraphics(Object gOffscreen) {
    Image.disposeGraphics(gOffscreen);
  }

  @Override
  public void drawImage(Object g, Object img, int x, int y, int width, int height) {
    Image.drawImage(g, img, x, y, width, height);
  }

  @Override
  public int[] grabPixels(Object imageobj, int width, int height, int[] pixels, int startRow, int nRows) {
    return Image.grabPixels(imageobj, width, height, pixels, startRow, nRows); 
  }

  @Override
  public int[] drawImageToBuffer(Object gOffscreen, Object imageOffscreen,
                                 Object imageobj, int width, int height, int bgcolor) {
    return Image.drawImageToBuffer(gOffscreen, imageOffscreen, imageobj, width, height, bgcolor);
  }

  @Override
  public int[] getTextPixels(String text, Font font3d, Object gObj,
                             Object image, int width, int height, int ascent) {
    return Image.getTextPixels(text, font3d, gObj, image, width, height, ascent);
  }

  @Override
  public void flushImage(Object imagePixelBuffer) {
    Image.flush(imagePixelBuffer);
  }

  @Override
  public Object getGraphics(Object image) {
    return Image.getGraphics(image);
  }

  @Override
  public int getImageHeight(Object image) {
    return (image == null ? -1 : Image.getHeight(image));
  }

  @Override
  public int getImageWidth(Object image) {
    return (image == null ? -1 : Image.getWidth(image));
  }

  @Override
  public Object getStaticGraphics(Object image, boolean backgroundTransparent) {
    return Image.getStaticGraphics(image, backgroundTransparent);
  }

  @Override
  public Object newBufferedImage(Object image, int w, int h) {
    return Image.newBufferedImage(image, w, h);
  }

  @Override
  public Object newOffScreenImage(int w, int h) {
    return Image.newBufferedImage(w, h);
  }

  @Override
  public boolean waitForDisplay(Object ignored, Object image) throws InterruptedException {
    Image.waitForDisplay(viewer, image);
    return true;
  }

  
  ///// FONT
  
  @Override
  public int fontStringWidth(Font font, String text) {
    return AwtFont.stringWidth(font.getFontMetrics(), text);
  }

  @Override
  public int getFontAscent(Object fontMetrics) {
    return AwtFont.getAscent(fontMetrics);
  }

  @Override
  public int getFontDescent(Object fontMetrics) {
    return AwtFont.getDescent(fontMetrics);
  }

  @Override
  public Object getFontMetrics(Font font, Object graphics) {
    return AwtFont.getFontMetrics(font, graphics);
  }

  @Override
  public Object newFont(String fontFace, boolean isBold, boolean isItalic, float fontSize) {
    return AwtFont.newFont(fontFace, isBold, isItalic, fontSize);
  }

  /// misc

  @Override
  public Object getJsObjectInfo(Object[] jsObject, String method, Object[] args) {
    JSObject DOMNode = (JSObject) jsObject[0];
    if (method == null) {
      String namespaceURI = (String) DOMNode.getMember("namespaceURI");
      String localName = (String) DOMNode.getMember("localName");
      return "namespaceURI=\"" + namespaceURI + "\" localName=\"" + localName + "\"";
    }
    return (args == null ? DOMNode.getMember(method) : DOMNode.call(method, args));
  }

  @Override
  public boolean isHeadless() {
    return GraphicsEnvironment.isHeadless();
  }

  @Override
  public boolean isSingleThreaded() {
    return false;
  }

  @Override
  public void notifyEndOfRendering() {
    // N/A
  }

  /**
   * @param p 
   * @return The hosting frame or JDialog.
   */
  static public Window getWindow(Container p) {
    while (p != null) {
      if (p instanceof Frame)
        return (Frame) p;
      else if (p instanceof JDialog)
        return (JDialog) p;
      else if (p instanceof JmolFrame)
        return ((JmolFrame) p).getFrame();
      p = p.getParent();
    }
    return null;
  }

  @Override
  public String getDateFormat(boolean isoiec8824) {
    return (isoiec8824 ? "D:"
        + new SimpleDateFormat("YYYYMMddHHmmssX").format(new Date()) + "'00'"
        : (new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z"))
            .format(new Date()));
  }

  @Override
  public GenericFileInterface newFile(String name) {
    return new AwtFile(name);
  }

  @Override
  public Object getBufferedFileInputStream(String name) {
    return AwtFile.getBufferedFileInputStream(name);
  }

  @Override
  public Object getBufferedURLInputStream(URL url, byte[] outputBytes,
                                          String post) {
    return AwtFile.getBufferedURLInputStream(url, outputBytes, post);
  }

  @Override
  public String getLocalUrl(String fileName) {
    return AwtFile.getLocalUrl(newFile(fileName));
  }

    
}
