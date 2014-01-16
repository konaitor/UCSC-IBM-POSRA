/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2012-09-11 19:29:26 -0500 (Tue, 11 Sep 2012) $
 * $Revision: 17556 $
 *
 * Copyright (C) 2004-2005  The Jmol Development Team
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

package org.jmol.appletjs;

import org.jmol.constant.EnumCallback;
import org.jmol.util.Logger;
import org.jmol.util.Parser;
import org.jmol.util.GenericApplet;

import java.net.URL;
import javajs.util.PT;

import java.util.Hashtable;
import java.util.Map;

/**
 * Java2Script rendition of Jmol using HTML5-only or WebGL-based graphics
 * 
 * @author Bob Hanson hansonr@stolaf.edu, Takanori Nakane, with the assistance
 *         of Jhou Renjian
 * 
 */

public class Jmol extends GenericApplet {

  private Map<String, Object> htParams = new Hashtable<String, Object>();

  public Jmol(Map<String, Object> viewerOptions) {
    if (viewerOptions == null)
      viewerOptions = new Hashtable<String, Object>();
    this.viewerOptions = viewerOptions;
    for (Map.Entry<String, Object> entry : viewerOptions.entrySet())
      htParams.put(entry.getKey().toLowerCase(), entry.getValue());
    documentBase = "" + viewerOptions.get("documentBase");
    codeBase = "" + viewerOptions.get("codePath");
    isJS = true;
    init(this);
  }
  
  @Override
  public Object setStereoGraphics(boolean isStereo) {
    /**
     * @j2sNative
     * 
     * if (isStereo)
     *   return viewer.apiPlatform.context;
     * 
     */
    {
    }
    return null;
  }

  @Override
  protected void initOptions() {
    viewerOptions.remove("debug");
    viewerOptions.put("fullName", fullName);
    haveDocumentAccess = "true".equalsIgnoreCase(""
        + getValue("allowjavascript", "true"));
    mayScript = true;
  }

  @Override
  protected String getParameter(String paramName) {
    Object o = htParams.get(paramName.toLowerCase());
    return (o == null ? null : new String(o.toString()));
  }

  @Override
  protected void doSendJsTextStatus(String message) {
    System.out.println(message);
    // not implemented
  }

  @Override
  protected void doSendJsTextareaStatus(String message) {
    System.out.println(message);
    // not implemented
  }

  String doNotifySync(String info, String appletName) {
    String syncCallback = callbacks.get(EnumCallback.SYNC);
    if (!mayScript || syncCallback == null || !haveDocumentAccess
        && !syncCallback.startsWith("Jmol."))
      return info;
    // ignoring 
    Logger.info("Jmol.notifySync " + appletName + " >> " + info);
    try {
      /**
       * @j2sNative
       * 
       *            if (syncCallback=="Jmol._mySyncCallback") return
       *            Jmol._mySyncCallback(this.htmlName, info, appletName); var f
       *            = eval(syncCallback); return f(this.htmlName, info,
       *            appletName);
       */
      {
        System.out.println(appletName);
      }
    } catch (Exception e) {
      if (!haveNotifiedError)
        if (Logger.debugging) {
          Logger.debug("syncCallback call error to " + syncCallback + ": " + e);
        }
      haveNotifiedError = true;
    }
    return info;
  }

  @Override
  protected float[][] doFunctionXY(String functionName, int nX, int nY) {
    /*three options:
     * 
     *  nX > 0  and  nY > 0        return one at a time, with (slow) individual function calls
     *  nX < 0  and  nY > 0        return a string that can be parsed to give the list of values
     *  nX < 0  and  nY < 0        fill the supplied float[-nX][-nY] array directly in JavaScript 
     *  
     */

    //System.out.println("functionXY" + nX + " " + nY  + " " + functionName);
    float[][] fxy = new float[Math.abs(nX)][Math.abs(nY)];
    if (!mayScript || !haveDocumentAccess || nX == 0 || nY == 0)
      return fxy;
    try {
      if (nX > 0 && nY > 0) { // fill with individual function calls (slow)
        for (int i = 0; i < nX; i++)
          for (int j = 0; j < nY; j++) {
            /**
             * @j2sNative
             * 
             *            fxy[i][j] = eval(functionName)(this.htmlName, i, j);
             */
            {
            }
          }
      } else if (nY > 0) { // fill with parsed values from a string (pretty fast)
        String data;
        /**
         * @j2sNative
         * 
         *            data = eval(functionName)(this.htmlName, nX, nY);
         * 
         */
        {
          data = "";
        }
        nX = Math.abs(nX);
        float[] fdata = new float[nX * nY];
        Parser.parseStringInfestedFloatArray(data, null, fdata);
        for (int i = 0, ipt = 0; i < nX; i++) {
          for (int j = 0; j < nY; j++, ipt++) {
            fxy[i][j] = fdata[ipt];
          }
        }
      } else { // fill float[][] directly using JavaScript
        /**
         * @j2sNative
         * 
         *            data = eval(functionName)(htmlName, nX, nY, fxy);
         * 
         */
        {
          System.out.println(functionName);
        }
      }
    } catch (Exception e) {
      Logger.error("Exception " + e + " with nX, nY: " + nX + " " + nY);
    }
    // for (int i = 0; i < nX; i++)
    // for (int j = 0; j < nY; j++)
    //System.out.println("i j fxy " + i + " " + j + " " + fxy[i][j]);
    return fxy;
  }

  @Override
  protected float[][][] doFunctionXYZ(String functionName, int nX, int nY,
                                      int nZ) {
    float[][][] fxyz = new float[Math.abs(nX)][Math.abs(nY)][Math.abs(nZ)];
    if (!mayScript || !haveDocumentAccess || nX == 0 || nY == 0 || nZ == 0)
      return fxyz;
    try {
      /**
       * @j2sNative
       * 
       *            eval(functionName)(this.htmlName, nX, nY, nZ, fxyz);
       * 
       */
      {
      }
    } catch (Exception e) {
      Logger.error("Exception " + e + " for " + functionName
          + " with nX, nY, nZ: " + nX + " " + nY + " " + nZ);
    }
    // for (int i = 0; i < nX; i++)
    // for (int j = 0; j < nY; j++)
    // for (int k = 0; k < nZ; k++)
    //System.out.println("i j k fxyz " + i + " " + j + " " + k + " " + fxyz[i][j][k]);
    return fxyz;
  }

  @Override
  protected void doShowDocument(URL url) {
    /**
     * @j2sNative window.open(url.toString());
     */
    {
      System.out.println(url);
    }

  }

  @Override
  protected String doSendCallback(String callback, Object[] data, String strInfo) {
    if (callback == null || callback.length() == 0) {
    } else if (callback.equals("alert")) {
      /**
       * @j2sNative alert(strInfo); return "";
       */
      {
        System.out.println(strInfo);
      }
    } else {
      String[] tokens = PT.split(callback, ".");
      /**
       * @j2sNative
       * 
       *            try{ 
       *            var o = window[tokens[0]]; 
       *            for (var i = 1; i < tokens.length; i++) 
       *              o = o[tokens[i]];
       *            for (var i = 0; i < data.length; i++) 
       *              data[i] && data[i].booleanValue && (data[i] = data[i].booleanValue());
       *            return o(data[0],data[1],data[2],data[3],data[4],data[5],data[6],data[7]); 
       *            } catch (e) { System.out.println(callback + " failed " + e); }
       */
      {
        System.out.println(tokens + " " + data);
      }
    }
    return "";
  }

  @Override
  protected String doEval(String strEval) {
    try {
      /**
       * @j2sNative
       * 
       *            return "" + eval(strEval);
       */
      {
      }
    } catch (Exception e) {
      Logger.error("# error evaluating " + strEval + ":" + e.toString());
    }
    return "";
  }

  @Override
  protected void doShowStatus(String message) {
    try {
      System.out.println(message);
    } catch (Exception e) {
      //ignore if page is closing
    }
  }

}
