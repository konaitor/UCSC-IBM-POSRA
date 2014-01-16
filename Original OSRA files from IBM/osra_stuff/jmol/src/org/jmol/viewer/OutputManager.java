package org.jmol.viewer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import org.jmol.api.Interface;
import org.jmol.api.JmolImageEncoder;
import org.jmol.i18n.GT;
import org.jmol.io.JmolBinary;
import org.jmol.java.BS;
import org.jmol.script.T;
import org.jmol.util.Escape;

import javajs.util.OC;
import javajs.util.List;
import javajs.util.PT;
import javajs.util.SB;

import org.jmol.util.Logger;
import org.jmol.util.Txt;
import org.jmol.viewer.Viewer.ACCESS;

abstract class OutputManager {

  abstract protected String getLogPath(String fileName);

  abstract String clipImageOrPasteText(String text);

  abstract String getClipboardText();

  abstract OC openOutputChannel(double privateKey,
                                               String fileName,
                                               boolean asWriter,
                                               boolean asAppend)
      throws IOException;

  abstract protected String createSceneSet(String sceneFile, String type, int width,
                                           int height);
           
  protected Viewer viewer;
  protected double privateKey;

  OutputManager setViewer(Viewer viewer, double privateKey) {
    this.viewer = viewer;
    this.privateKey = privateKey;
    return this;
  }

  /**
   * From handleOutputToFile, write text, byte[], or image data to a 
   * file; 
   * 
   * @param params
   * @return null (canceled) or byte[] or String message starting with OK or an error
   *         message; in the case of params.image != null, return the fileName
   */

  private String writeToOutputChannel(Map<String, Object> params) {
    String type = (String) params.get("type");
    String fileName = (String) params.get("fileName");
    String text = (String) params.get("text");
    byte[] bytes = (byte[]) params.get("bytes");
    int quality = getInt(params, "quality", Integer.MIN_VALUE);
    OC out = (OC) params.get("outputChannel");
    boolean closeStream = (out == null);
    int len = -1;
    try {
      if (!viewer.checkPrivateKey(privateKey))
        return "ERROR: SECURITY";
      if (bytes != null) {
        if (out == null)
          out = openOutputChannel(privateKey, fileName, false, false);
        out.write(bytes, 0, bytes.length);
      } else if (text != null) {
        if (out == null)
          out = openOutputChannel(privateKey, fileName, true, false);
        out.append(text);
      } else {
        String errMsg = (String) getOrSaveImage(params);
        if (errMsg != null)
          return errMsg;
        len = ((Integer) params.get("byteCount")).intValue();
      }
    } catch (Exception exc) {
      Logger.errorEx("IO Exception", exc);
      return exc.toString();
    } finally {
      if (out != null) {
        if (closeStream)
          out.closeChannel();
        len = out.getByteCount();
      }
    }
    return (len < 0 ? "Creation of " + fileName + " failed: "
        + viewer.getErrorMessageUn() : "OK " + type + " "
        + (len > 0 ? len + " " : "") + fileName
        + (quality == Integer.MIN_VALUE ? "" : "; quality=" + quality));
  }

  /**
   * 
   * Creates an image of params.type form -- PNG, PNGJ, PNGT, JPG, JPG64, PDF,
   * PPM.
   * 
   * From createImage and getImageAsBytes
   * 
   * @param params
   *        include fileName, type, text, bytes, image, scripts, appendix,
   *        quality, outputStream, and type-specific parameters. If
   *        params.outputChannel != null, then we are passing back the data, and
   *        the channel will not be closed.
   * 
   * @return bytes[] if params.fileName==null and params.outputChannel==null
   *         otherwise, return a message string or null
   * @throws Exception
   * 
   */

  private Object getOrSaveImage(Map<String, Object> params) throws Exception {
    byte[] bytes = null;
    String errMsg = null;
    String type = ((String) params.get("type")).toUpperCase();
    String fileName = (String) params.get("fileName");
    String[] scripts = (String[]) params.get("scripts");
    Object objImage = params.get("image");
    OC out = (OC) params.get("outputChannel");
    boolean asBytes = (out == null && fileName == null);
    boolean closeChannel = (out == null && fileName != null);
    boolean releaseImage = (objImage == null);
    Object image = (objImage == null ? viewer.getScreenImageBuffer(null, true)
        : objImage);
    boolean isOK = false;
    try {
      if (image == null)
        return errMsg = viewer.getErrorMessage();
      if (out == null)
        out = openOutputChannel(privateKey, fileName, false, false);
      if (out == null)
        return errMsg = "ERROR: canceled";
      fileName = out.getFileName();
      String comment = null;
      Object stateData = null;
      params.put("date", viewer.apiPlatform.getDateFormat(false));
      if (type.startsWith("JP")) {
        type = PT.simpleReplace(type, "E", "");
        if (type.equals("JPG64")) {
          params.put("outputChannelTemp", getOutputChannel(null, null));
          comment = "";
        } else {
          comment = (!asBytes ? (String) getWrappedState(null, null, image,
              null) : "");
        }
      } else if (type.equals("PDF")) {
        comment = "";
      } else if (type.startsWith("PNG")) {
        comment = "";
        boolean isPngj = type.equals("PNGJ");
        if (isPngj) {// get zip file data
          OC outTemp = getOutputChannel(null, null);
          getWrappedState(fileName, scripts, image, outTemp);
          stateData = outTemp.toByteArray();
        } else if (!asBytes) {
          stateData = ((String) getWrappedState(null, scripts, image, null))
              .getBytes();
        }
        if (stateData != null) {
          params.put("applicationData", stateData);
          params.put("applicationPrefix", "Jmol Type");
        }
        if (type.equals("PNGT"))
          params.put("transparentColor", Integer.valueOf(viewer
              .getBackgroundArgb()));
        type = "PNG";
      }
      if (comment != null)
        params.put("comment", comment.length() == 0 ? Viewer.getJmolVersion()
            : comment);
      String[] errRet = new String[1];
      isOK = createTheImage(image, type, out, params, errRet);
      if (closeChannel)
        out.closeChannel();
      if (isOK) {
        if (asBytes)
          bytes = out.toByteArray();
        else if (params.containsKey("captureByteCount"))
          errMsg = "OK: " + params.get("captureByteCount").toString()
              + " bytes";
      } else {
        errMsg = errRet[0];
      }
    } finally {
      if (releaseImage)
        viewer.releaseScreenImage();
      params.put("byteCount", Integer.valueOf(bytes != null ? bytes.length
          : isOK ? out.getByteCount() : -1));
      if (objImage != null) {
        // _ObjExport is saving the texture file -- just return file name, regardless of whether there is an error
        return fileName;
      }
    }
    return (errMsg == null ? bytes : errMsg);
  }

  /**
   * @param fileName
   * @param scripts
   * @param objImage
   * @param out
   * @return either byte[] (a full ZIP file) or String (just an embedded state
   *         script)
   * 
   */

  Object getWrappedState(String fileName, String[] scripts, Object objImage,
                         OC out) {
    int width = viewer.apiPlatform.getImageWidth(objImage);
    int height = viewer.apiPlatform.getImageHeight(objImage);
    if (width > 0 && !viewer.global.imageState && out == null
        || !viewer.global.preserveState)
      return "";
    String s = viewer.getStateInfo3(null, width, height);
    if (out != null) {
      if (fileName != null)
        viewer.fileManager.clearPngjCache(fileName);
      // when writing a file, we need to make sure
      // the pngj cache for that file is cleared
      return createZipSet(s, scripts, true, out);
    }
    // we remove local file references in the embedded states for images
    try {
      s = JC.embedScript(FileManager
          .setScriptFileReferences(s, ".", null, null));
    } catch (Throwable e) {
      // ignore if this uses too much memory
      Logger.error("state could not be saved: " + e.toString());
      s = "Jmol " + Viewer.getJmolVersion();
    }
    return s;
  }

  /**
   * @param objImage
   * @param type
   * @param out
   * @param params
   * @param errRet
   * @return byte array if needed
   * @throws IOException
   */
  private boolean createTheImage(Object objImage, String type,
                                 OC out,
                                 Map<String, Object> params, String[] errRet)
      throws IOException {
    type = type.substring(0, 1) + type.substring(1).toLowerCase();
    JmolImageEncoder ie = (JmolImageEncoder) Interface
        .getInterface("org.jmol.image." + type + "Encoder");
    if (ie == null) {
      errRet[0] = "Image encoder type " + type + " not available";
      return false;
    }
    return ie.createImage(viewer.apiPlatform, type, objImage, out, params,
        errRet);
  }
  
  /////////////////////// general output including logging //////////////////////

  String outputToFile(Map<String, Object> params) {
    return handleOutputToFile(params, true);
  }

  OC getOutputChannel(String fileName, String[] fullPath) {
    if (!viewer.haveAccess(ACCESS.ALL))
      return null;
    if (fileName != null) {
      fileName = getOutputFileNameFromDialog(fileName, Integer.MIN_VALUE);
      if (fileName == null)
        return null;
    }
    if (fullPath != null)
      fullPath[0] = fileName;
    String localName = (FileManager.isLocal(fileName) ? fileName : null);
    try {
      return openOutputChannel(privateKey, localName, false, false);
    } catch (IOException e) {
      Logger.info(e.toString());
      return null;
    }
  }

  /////////////////////// WRITE and CAPTURE command processing /////////////

  /**
   * 
   * @param params
   *        include fileName, type, text, bytes, scripts, quality, width,
   *        height, bsFrames, nVibes, fullPath
   * @return message
   */

  String processWriteOrCapture(Map<String, Object> params) {
    String fileName = (String) params.get("fileName");
    if (fileName == null)
      return viewer.clipImageOrPasteText((String) params.get("text"));
    BS bsFrames = (BS) params.get("bsFrames");
    int nVibes = getInt(params, "nVibes", 0);
    return (bsFrames != null || nVibes != 0 ? processMultiFrameOutput(fileName,
        bsFrames, nVibes, params) : handleOutputToFile(params, true));
  }

  private static int getInt(Map<String, Object> params, String key, int def) {
    Integer p = (Integer) params.get(key);
    return (p == null ? def : p.intValue());
  }

  private String processMultiFrameOutput(String fileName, BS bsFrames,
                                         int nVibes, Map<String, Object> params) {
    String info = "";
    int n = 0;
    int quality = getInt(params, "quality", -1);
    fileName = setFullPath(params, getOutputFileNameFromDialog(fileName,
        quality));
    if (fileName == null)
      return null;
    int ptDot = fileName.indexOf(".");
    if (ptDot < 0)
      ptDot = fileName.length();

    String froot = fileName.substring(0, ptDot);
    String fext = fileName.substring(ptDot);
    SB sb = new SB();
    if (bsFrames == null) {
      viewer.transformManager.vibrationOn = true;
      sb = new SB();
      for (int i = 0; i < nVibes; i++) {
        for (int j = 0; j < 20; j++) {
          viewer.transformManager.setVibrationT(j / 20f + 0.2501f);
          if (!writeFrame(++n, froot, fext, params, sb))
            return "ERROR WRITING FILE SET: \n" + info;
        }
      }
      viewer.setVibrationOff();
    } else {
      for (int i = bsFrames.nextSetBit(0); i >= 0; i = bsFrames
          .nextSetBit(i + 1)) {
        viewer.setCurrentModelIndex(i);
        if (!writeFrame(++n, froot, fext, params, sb))
          return "ERROR WRITING FILE SET: \n" + info;
      }
    }
    if (info.length() == 0)
      info = "OK\n";
    return info + "\n" + n + " files created";
  }

  private String setFullPath(Map<String, Object> params, String fileName) {
    String[] fullPath = (String[]) params.get("fullPath");
    if (fullPath != null)
      fullPath[0] = fileName;
    if (fileName == null)
      return null;
    params.put("fileName", fileName);
    return fileName;
  }

  String getOutputFromExport(Map<String, Object> params) {
    int width = getInt(params, "width", 0);
    int height = getInt(params, "height", 0);
    String fileName = (String) params.get("fileName");
    if (fileName != null) {
      fileName = setFullPath(params, getOutputFileNameFromDialog(fileName,
          Integer.MIN_VALUE));
      if (fileName == null)
        return null;
    }
    viewer.mustRender = true;
    int saveWidth = viewer.dimScreen.width;
    int saveHeight = viewer.dimScreen.height;
    viewer.resizeImage(width, height, true, true, false);
    viewer.setModelVisibility();
    String data = viewer.repaintManager.renderExport(viewer.gdata,
        viewer.modelSet, params);
    viewer.resizeImage(saveWidth, saveHeight, true, true, true);
    return data;
  }

  /**
   * Called when a simple image is required -- from x=getProperty("image") or
   * for a simple preview PNG image for inclusion in a ZIP file from write
   * xxx.zip or xxx.jmol, or for a PNGJ or PNG image that is being posted
   * because of a URL that contains "?POST?_PNG_" or
   * ?POST?_PNGJ_" or ?POST?_PNGJBIN_".
   * 
   * @param type
   * @param width
   * @param height
   * @param quality
   * @param errMsg
   * @return image bytes or, if an error, null and an error message
   */

  byte[] getImageAsBytes(String type, int width, int height, int quality,
                         String[] errMsg) {
    int saveWidth = viewer.dimScreen.width;
    int saveHeight = viewer.dimScreen.height;
    viewer.mustRender = true;
    viewer.resizeImage(width, height, true, false, false);
    viewer.setModelVisibility();
    viewer.creatingImage = true;
    byte[] bytes = null;
    try {
      Map<String, Object> params = new Hashtable<String, Object>();
      params.put("type", type);
      if (quality > 0)
        params.put("quality", Integer.valueOf(quality));
      Object bytesOrError = getOrSaveImage(params);
      if (bytesOrError instanceof String)
        errMsg[0] = (String) bytesOrError;
      else
        bytes = (byte[]) bytesOrError;
    } catch (Exception e) {
      errMsg[0] = e.toString();
      viewer.setErrorMessage("Error creating image: " + e, null);
    } catch (Error er) {
      viewer.handleError(er, false);
      viewer.setErrorMessage("Error creating image: " + er, null);
      errMsg[0] = viewer.getErrorMessage();
    }
    viewer.creatingImage = false;
    viewer.resizeImage(saveWidth, saveHeight, true, false, true);
    return bytes;
  }

  /**
   * Generates file data and passes it on either to a FileOuputStream (Java) or
   * via POSTing to a url using a ByteOutputStream (JavaScript)
   * 
   * @param fileName
   * @param type
   *        one of: PDB PQR FILE PLOT
   * @param modelIndex
   * @param parameters
   * @return "OK..." or "" or null
   * 
   */

  String writeFileData(String fileName, String type, int modelIndex,
                       Object[] parameters) {
    String[] fullPath = new String[1];
    OC out = getOutputChannel(fileName, fullPath);
    if (out == null)
      return "";
    fileName = fullPath[0];
    String pathName = (type.equals("FILE") ? viewer.getFullPathName(false) : null);
    boolean getCurrentFile = (pathName != null && (pathName.equals("string")
        || pathName.indexOf("[]") >= 0 || pathName.equals("JSNode")));
    boolean asBytes = (pathName != null && !getCurrentFile);
    if (asBytes) {
      pathName = viewer.getModelSetPathName();
      if (pathName == null)
        return null; // zapped
    }
    // The OutputStringBuilder allows us to create strings or byte arrays
    // of a given type, passing just one parameter and maintaining an 
    // output stream all along. For JavaScript, this will be a ByteArrayOutputStream
    // which will then be posted to a server for a return that allows saving.
    out.setType(type);
    String msg = (type.equals("PDB") || type.equals("PQR") ? viewer
        .getPdbAtomData(null, out) : type.startsWith("PLOT") ? viewer.modelSet
        .getPdbData(modelIndex, type.substring(5), viewer
            .getSelectionSet(false), parameters, out) : getCurrentFile ? out
        .append(viewer.getCurrentFileAsString()).toString() : (String) viewer
        .getFileAsBytes(pathName, out));
    out.closeChannel();
    if (msg != null)
      msg = "OK " + msg + " " + fileName;
    return msg;
  }

  private boolean writeFrame(int n, String froot, String fext,
                             Map<String, Object> params, SB sb) {
    String fileName = "0000" + n;
    fileName = setFullPath(params, froot
        + fileName.substring(fileName.length() - 4) + fext);
    String msg = handleOutputToFile(params, false);
    viewer.scriptEcho(msg);
    sb.append(msg).append("\n");
    return msg.startsWith("OK");
  }

  private String getOutputFileNameFromDialog(String fileName, int quality) {
    if (fileName == null || viewer.isKiosk)
      return null;
    boolean useDialog = (fileName.indexOf("?") == 0);
    if (useDialog)
      fileName = fileName.substring(1);
    useDialog |= viewer.isApplet() && (fileName.indexOf("http:") < 0);
    fileName = FileManager.getLocalPathForWritingFile(viewer, fileName);
    if (useDialog)
      fileName = viewer.dialogAsk(quality == Integer.MIN_VALUE ? "Save"
          : "Save Image", fileName);
    return fileName;
  }

  /**
   * general routine for creating an image or writing data to a file
   * 
   * passes request to statusManager to pass along to app or applet
   * jmolStatusListener interface
   * 
   * @param params
   *        include: fileName: starts with ? --> use file dialog; type: PNG,
   *        JPG, etc.; text: String to output; bytes: byte[] or null if an
   *        image; scripts for scenes; quality: for JPG and PNG; width: image
   *        width; height: image height; fullPath: String[] return
   * 
   * @param doCheck
   * @return null (canceled) or a message starting with OK or an error message
   */
  protected String handleOutputToFile(Map<String, Object> params, boolean doCheck) {

    // org.jmol.image.AviCreator does create AVI animations from JPEGs
    //but these aren't read by standard readers, so that's pretty much useless.

    String sret = null;
    String fileName = (String) params.get("fileName");
    if (fileName == null)
      return null;
    String type = (String) params.get("type");
    String text = (String) params.get("text");
    int width = getInt(params, "width", 0);
    int height = getInt(params, "height", 0);
    int quality = getInt(params, "quality", Integer.MIN_VALUE);
    int captureMode = getInt(params, "captureMode", Integer.MIN_VALUE);
    if (captureMode != Integer.MIN_VALUE && !viewer.allowCapture())
      return "ERROR: Cannot capture on this platform.";
    boolean mustRender = (quality != Integer.MIN_VALUE);
    // localName will be fileName only if we are able to write to disk.
    String localName = null;
    if (captureMode != Integer.MIN_VALUE) {
      doCheck = false; // will be checked later
      mustRender = false;
      type = "GIF";
    }
    if (doCheck)
      fileName = getOutputFileNameFromDialog(fileName, quality);
    fileName = setFullPath(params, fileName);
    if (fileName == null)
      return null;
    params.put("fileName", fileName);
    // JSmol/HTML5 WILL produce a localName now
    if (FileManager.isLocal(fileName))
      localName = fileName;
    int saveWidth = viewer.dimScreen.width;
    int saveHeight = viewer.dimScreen.height;
    viewer.creatingImage = true;
    if (mustRender) {
      viewer.mustRender = true;
      viewer.resizeImage(width, height, true, false, false);
      viewer.setModelVisibility();
    }
    try {
      if (type.equals("JMOL"))
        type = "ZIPALL";
      if (type.equals("ZIP") || type.equals("ZIPALL")) {
        String[] scripts = (String[]) params.get("scripts");
        if (scripts != null && type.equals("ZIP"))
          type = "ZIPALL";
        OC out = getOutputChannel(fileName, null);
        sret = createZipSet(text, scripts, type.equals("ZIPALL"), out);
      } else if (type.equals("SCENE")) {
        sret = createSceneSet(fileName, text, width, height);
      } else {
        // see if application wants to do it (returns non-null String)
        // both Jmol application and applet return null
        byte[] bytes = (byte[]) params.get("bytes");
        // String return here
        sret = viewer.statusManager.createImage(fileName, type, text, bytes,
            quality);
        if (sret == null) {
          // allow Jmol to do it            
          String msg = null;
          if (captureMode != Integer.MIN_VALUE) {
            OC out = null;
            Map<String, Object> cparams = viewer.captureParams;
            switch (captureMode) {
            case T.movie:
              if (cparams != null)
                ((OC) cparams.get("outputChannel"))
                    .closeChannel();
              out = getOutputChannel(localName, null);
              if (out == null) {
                sret = msg = "ERROR: capture canceled";
                viewer.captureParams = null;
              } else {
                localName = out.getFileName();
                msg = type + "_STREAM_OPEN " + localName;
                viewer.captureParams = params;
                params.put("captureFileName", localName);
                params.put("captureCount", Integer.valueOf(1));
                params.put("captureMode", Integer.valueOf(T.movie));
              }
              break;
            default:
              if (cparams == null) {
                sret = msg = "ERROR: capture not active";
              } else {
                params = cparams;
                switch (captureMode) {
                default:
                  sret = msg = "ERROR: CAPTURE MODE=" + captureMode + "?";
                  break;
                case T.add:
                  if (Boolean.FALSE == params.get("captureEnabled")) {
                    sret = msg = "capturing OFF; use CAPTURE ON/END/CANCEL to continue";
                  } else {
                    int count = getInt(params, "captureCount", 1);
                    params.put("captureCount", Integer.valueOf(++count));
                    msg = type + "_STREAM_ADD " + count;
                  }
                  break;
                case T.on:
                case T.off:
                  params = cparams;
                  params.put("captureEnabled",
                      (captureMode == T.on ? Boolean.TRUE : Boolean.FALSE));
                  sret = type + "_STREAM_"
                      + (captureMode == T.on ? "ON" : "OFF");
                  params.put("captureMode", Integer.valueOf(T.add));
                  break;
                case T.end:
                case T.cancel:
                  params = cparams;
                  params.put("captureMode", Integer.valueOf(captureMode));
                  fileName = (String) params.get("captureFileName");
                  msg = type + "_STREAM_"
                      + (captureMode == T.end ? "CLOSE " : "CANCEL ")
                      + params.get("captureFileName");
                  viewer.captureParams = null;
                  viewer.prompt(GT._("Capture")
                      + ": "
                      + (captureMode == T.cancel ? GT._("canceled") : GT.o(GT._(
                          "{0} saved"), fileName)), "OK", null,
                      true);
                }
                break;
              }
              break;
            }
            if (out != null)
              params.put("outputChannel", out);
          }
          params.put("fileName", localName);
          if (sret == null)
            sret = writeToOutputChannel(params);
          viewer.statusManager.createImage(sret, type, null, null, quality);
          if (msg != null)
            viewer.showString(msg + " (" + params.get("captureByteCount")
                + " bytes)", false);
        }
      }
    } catch (Throwable er) {
      //er.printStackTrace();
      Logger.error(viewer.setErrorMessage(sret = "ERROR creating image??: "
          + er, null));
    } finally {
      viewer.creatingImage = false;
      if (quality != Integer.MIN_VALUE)
        viewer.resizeImage(saveWidth, saveHeight, true, false, true);
    }
    return sret;
  }

  String setLogFile(String value) {
    String path = null;
    String logFilePath = viewer.getLogFilePath();
    /**
     * @j2sNative
     * 
     *            if (typeof value == "function") path = value;
     * 
     */
    if (logFilePath == null || value.indexOf("\\") >= 0) {
      value = null;
    } else if (value.startsWith("http://") || value.startsWith("https://")) {
      // allow for remote logging
      path = value;
    } else if (value.indexOf("/") >= 0) {
      value = null;
    } else if (value.length() > 0) {
      if (!value.startsWith("JmolLog_"))
        value = "JmolLog_" + value;
      path = getLogPath(logFilePath + value);
    }
    if (path == null)
      value = null;
    else
      Logger.info(GT.o(GT._("Setting log file to {0}"), path));
    if (value == null || !viewer.haveAccess(ACCESS.ALL)) {
      Logger.info(GT._("Cannot set log file path."));
      value = null;
    } else {
      viewer.logFileName = path;
      viewer.global.setS("_logFile", viewer.isApplet() ? value : path);
    }
    return value;
  }

  void logToFile(String data) {
    try {
      boolean doClear = (data.equals("$CLEAR$"));
      if (data.indexOf("$NOW$") >= 0)
        data = PT.simpleReplace(data, "$NOW$", viewer.apiPlatform
            .getDateFormat(false));
      if (viewer.logFileName == null) {
        Logger.info(data);
        return;
      }
      @SuppressWarnings("resource")
      OC out = (viewer.haveAccess(ACCESS.ALL) ? openOutputChannel(privateKey,
          viewer.logFileName, true, !doClear) : null);
      if (!doClear) {
        int ptEnd = data.indexOf('\0');
        if (ptEnd >= 0)
          data = data.substring(0, ptEnd);
        out.append(data);
        if (ptEnd < 0)
          out.append("\n");
      }
      String s = out.closeChannel();
      Logger.info(s);
    } catch (Exception e) {
      if (Logger.debugging)
        Logger.debug("cannot log " + data);
    }
  }

  protected final static String SCENE_TAG = "###scene.spt###";

  private String createZipSet(String script,
                              String[] scripts, boolean includeRemoteFiles,
                              OC out) {
     List<Object> v = new  List<Object>();
     FileManager fm = viewer.fileManager;
     List<String> fileNames = new  List<String>();
     Hashtable<Object, String> crcMap = new Hashtable<Object, String>();
     boolean haveSceneScript = (scripts != null && scripts.length == 3 && scripts[1]
         .startsWith(SCENE_TAG));
     boolean sceneScriptOnly = (haveSceneScript && scripts[2].equals("min"));
     if (!sceneScriptOnly) {
       JmolBinary.getFileReferences(script, fileNames);
       if (haveSceneScript)
         JmolBinary.getFileReferences(scripts[1], fileNames);
     }
     boolean haveScripts = (!haveSceneScript && scripts != null && scripts.length > 0);
     if (haveScripts) {
       script = wrapPathForAllFiles("script " + Escape.eS(scripts[0]), "");
       for (int i = 0; i < scripts.length; i++)
         fileNames.addLast(scripts[i]);
     }
     int nFiles = fileNames.size();
     List<String> newFileNames = new  List<String>();
     for (int iFile = 0; iFile < nFiles; iFile++) {
       String name = fileNames.get(iFile);
       boolean isLocal = !viewer.isJS && FileManager.isLocal(name);
       String newName = name;
       // also check that somehow we don't have a local file with the same name as
       // a fixed remote file name (because someone extracted the files and then used them)
       if (isLocal || includeRemoteFiles) {
         int ptSlash = name.lastIndexOf("/");
         newName = (name.indexOf("?") > 0 && name.indexOf("|") < 0 ? PT
             .replaceAllCharacters(name, "/:?\"'=&", "_") : FileManager
             .stripPath(name));
         newName = PT.replaceAllCharacters(newName, "[]", "_");
         boolean isSparDir = (fm.spardirCache != null && fm.spardirCache
             .containsKey(name));
         if (isLocal && name.indexOf("|") < 0 && !isSparDir) {
           v.addLast(name);
           v.addLast(newName);
           v.addLast(null); // data will be gotten from disk
         } else {
           // all remote files, and any file that was opened from a ZIP collection
           Object ret = (isSparDir ? fm.spardirCache.get(name) : fm
               .getFileAsBytes(name, null, true));
           if (!PT.isAB(ret))
             return (String) ret;
           newName = addPngFileBytes(name, (byte[]) ret, iFile,
               crcMap, isSparDir, newName, ptSlash, v);
         }
         name = "$SCRIPT_PATH$" + newName;
       }
       crcMap.put(newName, newName);
       newFileNames.addLast(name);
     }
     if (!sceneScriptOnly) {
       script = Txt.replaceQuotedStrings(script, fileNames, newFileNames);
       v.addLast("state.spt");
       v.addLast(null);
       v.addLast(script.getBytes());
     }
     if (haveSceneScript) {
       if (scripts[0] != null) {
         v.addLast("animate.spt");
         v.addLast(null);
         v.addLast(scripts[0].getBytes());
       }
       v.addLast("scene.spt");
       v.addLast(null);
       script = Txt.replaceQuotedStrings(scripts[1], fileNames,
           newFileNames);
       v.addLast(script.getBytes());
     }
     String sname = (haveSceneScript ? "scene.spt" : "state.spt");
     v.addLast("JmolManifest.txt");
     v.addLast(null);
     String sinfo = "# Jmol Manifest Zip Format 1.1\n" + "# Created "
         + (new Date()) + "\n" + "# JmolVersion " + Viewer.getJmolVersion()
         + "\n" + sname;
     v.addLast(sinfo.getBytes());
     v.addLast("Jmol_version_"
         + Viewer.getJmolVersion().replace(' ', '_').replace(':', '.'));
     v.addLast(null);
     v.addLast(new byte[0]);
     if (out.getFileName() != null) {
       byte[] bytes = viewer.getImageAsBytes("PNG", 0, 0, -1, null); 
       if (bytes != null) {
         v.addLast("preview.png");
         v.addLast(null);
         v.addLast(bytes);
       }
     }
     return writeZipFile(privateKey, fm, viewer, out, v, "OK JMOL");
   }

  private String addPngFileBytes(String name, byte[] ret, int iFile,
                                 Hashtable<Object, String> crcMap,
                                 boolean isSparDir, String newName, int ptSlash,
                                 List<Object> v) {
     Integer crcValue = Integer.valueOf(JmolBinary.getCrcValue(ret));
     // only add to the data list v when the data in the file is new
     if (crcMap.containsKey(crcValue)) {
       // let newName point to the already added data
       newName = crcMap.get(crcValue);
     } else {
       if (isSparDir)
         newName = newName.replace('.', '_');
       if (crcMap.containsKey(newName)) {
         // now we have a conflict. To different files with the same name
         // append "[iFile]" to the new file name to ensure it's unique
         int pt = newName.lastIndexOf(".");
         if (pt > ptSlash) // is a file extension, probably
           newName = newName.substring(0, pt) + "[" + iFile + "]"
               + newName.substring(pt);
         else
           newName = newName + "[" + iFile + "]";
       }
       v.addLast(name);
       v.addLast(newName);
       v.addLast(ret);
       crcMap.put(crcValue, newName);
     }
     return newName;
   }

  /**
   * generic method to create a zip file based on
   * http://www.exampledepot.com/egs/java.util.zip/CreateZip.html
   * @param privateKey 
   * @param fm 
   * @param viewer 
   * 
   * @param out
   * @param fileNamesAndByteArrays
   *        Vector of [filename1, bytes|null, filename2, bytes|null, ...]
   * @param msg
   * @return msg bytes filename or errorMessage or byte[]
   */

  private String writeZipFile(double privateKey, FileManager fm, Viewer viewer,
                             OC out,
                             List<Object> fileNamesAndByteArrays, String msg) {
    
    byte[] buf = new byte[1024];
    long nBytesOut = 0;
    long nBytes = 0;
    String outFileName = out.getFileName();
    Logger.info("creating zip file " + (outFileName == null ? "" : outFileName)
        + "...");
    String fileList = "";
    try {
      OutputStream bos;
      /**
       * 
       * no need for buffering here
       * 
       * @j2sNative
       * 
       * bos = out;
       * 
       */
      {
        bos = new BufferedOutputStream(out);
      }
      OutputStream zos = (OutputStream) JmolBinary.getZipOutputStream(bos);
      for (int i = 0; i < fileNamesAndByteArrays.size(); i += 3) {
        String fname = (String) fileNamesAndByteArrays.get(i);
        byte[] bytes = null;
        Object data = fm.cacheGet(fname, false);
        if (data instanceof Map<?, ?>)
          continue;
        if (fname.indexOf("file:/") == 0) {
          fname = fname.substring(5);
          if (fname.length() > 2 && fname.charAt(2) == ':') // "/C:..." DOS/Windows
            fname = fname.substring(1);
        } else if (fname.indexOf("cache://") == 0) {
          fname = fname.substring(8);
        }
        String fnameShort = (String) fileNamesAndByteArrays.get(i + 1);
        if (fnameShort == null)
          fnameShort = fname;
        if (data != null)
          bytes = (PT.isAB(data) ? (byte[]) data : ((String) data)
              .getBytes());
        if (bytes == null)
          bytes = (byte[]) fileNamesAndByteArrays.get(i + 2);
        String key = ";" + fnameShort + ";";
        if (fileList.indexOf(key) >= 0) {
          Logger.info("duplicate entry");
          continue;
        }
        fileList += key;
        JmolBinary.addZipEntry(zos, fnameShort);
        int nOut = 0;
        if (bytes == null) {
          // get data from disk
          BufferedInputStream in = viewer.getBufferedInputStream(fname);
          int len;
          while ((len = in.read(buf, 0, 1024)) > 0) {
            zos.write(buf, 0, len);
            nOut += len;
          }
          in.close();
        } else {
          // data are already in byte form
          zos.write(bytes, 0, bytes.length);
          nOut += bytes.length;
        }
        nBytesOut += nOut;
        JmolBinary.closeZipEntry(zos);
        Logger.info("...added " + fname + " (" + nOut + " bytes)");
      }
      zos.flush();
      zos.close();
      Logger.info(nBytesOut + " bytes prior to compression");
      String ret = out.closeChannel();
      if (ret != null) {
        if (ret.indexOf("Exception") >= 0)
          return ret;
        msg += " " + ret;
      }
      nBytes = out.getByteCount();
    } catch (IOException e) {
      Logger.info(e.toString());
      return e.toString();
    }
    String fileName = out.getFileName();
    return (fileName == null ? null : msg + " " + nBytes + " " + fileName);
  }

  protected String wrapPathForAllFiles(String cmd, String strCatch) {
    String vname = "v__" + ("" + Math.random()).substring(3);
    return "# Jmol script\n{\n\tVar "
        + vname
        + " = pathForAllFiles\n\tpathForAllFiles=\"$SCRIPT_PATH$\"\n\ttry{\n\t\t"
        + cmd + "\n\t}catch(e){" + strCatch + "}\n\tpathForAllFiles = " + vname
        + "\n}\n";
  }

}
