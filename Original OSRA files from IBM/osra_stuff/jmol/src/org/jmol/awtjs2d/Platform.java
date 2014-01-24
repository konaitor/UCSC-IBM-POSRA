package org.jmol.awtjs2d;

import java.net.URL;


import org.jmol.api.Interface;

import javajs.api.GenericPlatform;
import javajs.api.GenericFileInterface;
import javajs.api.GenericMouseInterface;
import javajs.api.GenericMenuInterface;
import javajs.api.PlatformViewer;
import javajs.awt.Font;
import javajs.util.AjaxURLStreamHandlerFactory;
import javajs.util.P3;

/**
 * JavaScript 2D canvas version requires Ajax-based URL stream processing.
 * 
 * Jmol "display" --> HTML5 "canvas"
 * Jmol "image" --> HTML5 "canvas" (because we need width and height)
 * Jmol "graphics" --> HTML5 "context(2d)" (one for display, one off-screen for fonts)
 * Jmol "font" --> JmolFont
 * Jmol "fontMetrics" --> HTML5 "context(2d)"
 * (Not fully implemented) 
 * 
 * @author Bob Hanson
 *
 */
public class Platform implements GenericPlatform {
  Object canvas;
  PlatformViewer viewer;
  Object context;
  
	@Override
  public void setViewer(PlatformViewer viewer, Object canvas) {
	  /**
	   * @j2sNative
	   * 
     *     this.viewer = viewer;
     *     this.canvas = canvas;
     *     if (canvas != null) {
	   *       this.context = canvas.getContext("2d");
	   *       canvas.imgdata = this.context.getImageData(0, 0, canvas.width, canvas.height);
	   *       canvas.buf8 = canvas.imgdata.data;
	   *     }
	   */
	  {}
		//
		try {
		  URL.setURLStreamHandlerFactory(new AjaxURLStreamHandlerFactory());
		} catch (Throwable e) {
		  // that's fine -- already created	
		}
	}

  @Override
  public boolean isSingleThreaded() {
    return true;
  }

  @Override
  public Object getJsObjectInfo(Object[] jsObject, String method, Object[] args) {
    /**
     * we must use Object[] here to hide [HTMLUnknownElement] and [Attribute] from Java2Script
     * @j2sNative
     * 
     * if (method == "localName")return jsObject[0]["nodeName"];
     * return (args == null ? jsObject[0][method] : jsObject[0][method](args[0]));
     * 
     * 
     */
    {
      return null;
    }
  }

  @Override
  public boolean isHeadless() {
    return false;
  }

  @Override
  public GenericMouseInterface getMouseManager(double privateKey, Object display) {
    return new Mouse(privateKey, viewer, display);
  }

  // /// Display

	@Override
  public void convertPointFromScreen(Object canvas, P3 ptTemp) {
	  // from JmolMultiTouchClientAdapter.fixXY
		Display.convertPointFromScreen(canvas, ptTemp);
	}

	@Override
  public void getFullScreenDimensions(Object canvas, int[] widthHeight) {
		Display.getFullScreenDimensions(canvas, widthHeight);
	}

  @Override
  public GenericMenuInterface getMenuPopup(String menuStructure,
                                         char type) {
    String c = (type == 'j' ? "awtjs2d.JSmolPopup" : "awtjs2d.JSModelKitPopup");
    GenericMenuInterface jmolpopup = (GenericMenuInterface) Interface
        .getOptionInterface(c);
    try {
      if (jmolpopup != null)
        jmolpopup.jpiInitialize(viewer, menuStructure);
    } catch (Exception e) {
      c = "Exception creating " + c + ":" + e;
      System.out.println(c);
      return null;
    }
    return jmolpopup;
  }

	@Override
  public boolean hasFocus(Object canvas) {
		return Display.hasFocus(canvas);
	}

	@Override
  public String prompt(String label, String data, String[] list,
			boolean asButtons) {
		return Display.prompt(label, data, list, asButtons);
	}

	/**
	 * legacy apps will use this
	 * 
	 * @param context
	 * @param size
	 */
	@Override
  public void renderScreenImage(Object context, Object size) {
		Display.renderScreenImage(viewer, context, size);
	}

  @Override
  public void drawImage(Object context, Object canvas, int x, int y, int width,
                        int height) {
    
    // from Viewer.render1
    Image.drawImage(context, canvas, x, y, width, height);
  }

	@Override
  public void requestFocusInWindow(Object canvas) {
		Display.requestFocusInWindow(canvas);
	}

	@Override
  public void repaint(Object canvas) {
		Display.repaint(canvas);
	}

	@Override
  public void setTransparentCursor(Object canvas) {
		Display.setTransparentCursor(canvas);
	}

	@Override
  public void setCursor(int c, Object canvas) {
		Display.setCursor(c, canvas);
	}

	// //// Image

	@Override
  public Object allocateRgbImage(int windowWidth, int windowHeight,
			int[] pBuffer, int windowSize, boolean backgroundTransparent, boolean isImageWrite) {
		return Image.allocateRgbImage(windowWidth, windowHeight, pBuffer,
				windowSize, backgroundTransparent, (isImageWrite ? null : canvas));
	}

  @Override
  public void notifyEndOfRendering() {
  }

  /**
   * could be byte[] (from ZIP file) or String (local file name) or URL
   * @param data 
   * @return image object
   * 
   */
	@Override
  public Object createImage(Object data) {
	  // N/A in JS
	  return null;
	}

	@Override
  public void disposeGraphics(Object gOffscreen) {
	  // N/A
	}

	@Override
  public int[] grabPixels(Object canvas, int width, int height, 
                          int[] pixels, int startRow, int nRows) {
	  // from PNG and JPG image creators, also g3d.ImageRenderer.plotImage via drawImageToBuffer
	  
	  /**
	   * 
	   * (might be just an object with buf32 defined -- WRITE IMAGE)
	   * 
	   * @j2sNative
	   * 
	   *     if (canvas.image && (width != canvas.width || height != canvas.height))
     *       Jmol._setCanvasImage(canvas, width, height);
	   *     if (canvas.buf32) return canvas.buf32;
	   */
	  {}
    int[] buf = Image.grabPixels(Image.getGraphics(canvas), width, height); 
    /**
     * @j2sNative
     *  
     *  canvas.buf32 = buf;
     * 
     */
    {}
    return buf;
	}

	@Override
  public int[] drawImageToBuffer(Object gOffscreen, Object imageOffscreen,
			Object canvas, int width, int height, int bgcolor) {
	  return grabPixels(canvas, width, height, null, 0, 0);
	}

	@Override
  public int[] getTextPixels(String text, Font font3d, Object context,
			Object image, int width, int height, int ascent) {
		return Image.getTextPixels(text, font3d, context, width, height, ascent);
	}

	@Override
  public void flushImage(Object imagePixelBuffer) {
	  // N/A
	}

	@Override
  public Object getGraphics(Object image) {
		return Image.getGraphics(image);
	}

  @Override
  public int getImageHeight(Object canvas) {
		return (canvas == null ? -1 : Image.getHeight(canvas));
	}

	@Override
  public int getImageWidth(Object canvas) {
		return (canvas == null ? -1 : Image.getWidth(canvas));
	}

	@Override
  public Object getStaticGraphics(Object image, boolean backgroundTransparent) {
		return Image.getStaticGraphics(image, backgroundTransparent);
	}

	@Override
  public Object newBufferedImage(Object image, int w, int h) {
    /**
     * @j2sNative
     * 
     *  if (typeof Jmol != "undefined" && Jmol._getHiddenCanvas)
     *    return Jmol._getHiddenCanvas(this.viewer.applet, "stereoImage", w, h); 
     */
    {}
    return null;
	}

	@Override
  public Object newOffScreenImage(int w, int h) {
    /**
     * @j2sNative
     * 
     *  if (typeof Jmol != "undefined" && Jmol._getHiddenCanvas)
     *    return Jmol._getHiddenCanvas(this.viewer.applet, "textImage", w, h); 
     */
    {}
    return null;
	}

	@Override
  public boolean waitForDisplay(Object echoNameAndPath, Object zipBytes)
			throws InterruptedException {
  
	  /**
	   * 
	   * this is important specifically for retrieving images from
	   * files, as in set echo ID myimage "image.gif"
	   * 
	   * return will be immediate, before the image is created, so here there is
	   * no "wait." Instead, we give it a callback 
	   * 
	   * @j2sNative
	   * 
     * if (typeof Jmol == "undefined" || !Jmol._getHiddenCanvas) return false;
	   * var viewer = this.viewer;
	   * var sc = viewer.getEvalContextAndHoldQueue(viewer.eval);
	   * var echoName = echoNameAndPath[0];
	   * return Jmol._loadImage(this, echoNameAndPath, zipBytes, 
	   *   function(canvas, pathOrError) { viewer.loadImageData(canvas, pathOrError, echoName, sc) }
	   * );
	   * 
	   */	  
	  {
	    return false;	    
	  }
	}

	// /// FONT

	@Override
  public int fontStringWidth(Font font, String text) {
		return JSFont.stringWidth(font, context, text);
	}

	@Override
  public int getFontAscent(Object context) {
		return JSFont.getAscent(context);
	}

	@Override
  public int getFontDescent(Object context) {
		return JSFont.getDescent(context);
	}

	@Override
  public Object getFontMetrics(Font font, Object context) {
		return JSFont.getFontMetrics(font, context);
	}

	@Override
  public Object newFont(String fontFace, boolean isBold, boolean isItalic,
			float fontSize) {
		return JSFont.newFont(fontFace, isBold, isItalic, fontSize, "px");
	}

  @Override
  public String getDateFormat(boolean isoiec8824) {
    /**
     * 
     * Mon Jan 07 2013 19:54:39 GMT-0600 (Central Standard Time)
     * or YYYYMMDDHHmmssOHH'mm'
     * 
     * @j2sNative
     * 
     * if (isoiec8824) {
     *   var d = new Date();
     *   var x = d.toString().split(" ");
     *   var MM = "0" + d.getMonth(); MM = MM.substring(MM.length - 2);
     *   var dd = "0" + d.getDate(); dd = dd.substring(dd.length - 2);
     *   return x[3] + MM + dd + x[4].replace(/\:/g,"") + x[5].substring(3,6) + "'" + x[5].substring(6,8) + "'"   
     * }
     * return ("" + (new Date())).split(" (")[0];
     */
    {
      return null;
    }
  }

  @Override
  public GenericFileInterface newFile(String name) {
    return new JSFile(name);
  }

  @Override
  public Object getBufferedFileInputStream(String name) {
    // n/a for any applet
    return null; 
  }

  @Override
  public Object getBufferedURLInputStream(URL url, byte[] outputBytes,
                                          String post) {
    return JSFile.getBufferedURLInputStream(url, outputBytes, post);
  }

  @Override
  public String getLocalUrl(String fileName) {
    // n/a (dialogs only)
    return null;
  }


}
