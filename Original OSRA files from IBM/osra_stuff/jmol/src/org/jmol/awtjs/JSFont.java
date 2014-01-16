package org.jmol.awtjs;

import javajs.awt.Font;

/**
 * methods required by Jmol that access java.awt.Font
 * 
 * private to org.jmol.awtjs
 * 
 */

class JSFont {

	/**
   * @param fontFace  
	 * @param isBold 
	 * @param isItalic 
	 * @param fontSize 
	 * @return null
   */
	static Object newFont(String fontFace, boolean isBold, boolean isItalic,
			float fontSize) {
		return null;
	}

	/**
   * @param graphics  
	 * @param font 
	 * @return null
   */
	static Object getFontMetrics(Object graphics, Object font) {
		return null;
	}

	/**
   * @param fontMetrics  
	 * @return 0
   */
	static int getAscent(Object fontMetrics) {
		return 0;
	}

  /**
   * @param fontMetrics  
   * @return 0
   */
	static int getDescent(Object fontMetrics) {
		return 0;
	}

  /**
   * @param font 
   * @param text 
   * @return 0
   */
	static int stringWidth(Font font, String text) {
		return 0;
	}
}
