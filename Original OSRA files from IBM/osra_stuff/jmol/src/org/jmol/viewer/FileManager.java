/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-12-29 13:09:57 -0600 (Sun, 29 Dec 2013) $
 * $Revision: 19133 $
 *
 * Copyright (C) 2003-2005  Miguel, Jmol Development Team
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
package org.jmol.viewer;

import java.net.URL;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.Map;

import org.jmol.api.Interface;
import org.jmol.api.JmolDocument;
import org.jmol.api.JmolDomReaderInterface;
import org.jmol.api.JmolFilesReaderInterface;
import org.jmol.io.DataReader;
import org.jmol.io.FileReader;
import org.jmol.io.JmolBinary;
import org.jmol.script.T;

import javajs.api.GenericPlatform;
import javajs.api.BytePoster;
import javajs.api.GenericFileInterface;
import javajs.util.AU;
import javajs.util.Base64;
import javajs.util.OC;
import javajs.util.List;
import javajs.util.PT;
import javajs.util.SB;

import org.jmol.util.Logger;
import org.jmol.util.Txt;
import org.jmol.viewer.Viewer.ACCESS;


public class FileManager implements BytePoster {

  private Viewer viewer;

  FileManager(Viewer viewer) {
    this.viewer = viewer;
    clear();
  }

  void clear() {
    // from zap
    setFileInfo(new String[] { viewer.getZapName() });
    spardirCache = null;
   
  }

  private void setLoadState(Map<String, Object> htParams) {
    if (viewer.getPreserveState()) {
      htParams.put("loadState", viewer.getLoadState(htParams));
    }
  }

  private String pathForAllFiles = "";
  
  String getPathForAllFiles() {
    return pathForAllFiles;
  }
  
  String setPathForAllFiles(String value) {
    if (value.length() > 0 && !value.endsWith("/") && !value.endsWith("|"))
        value += "/";
    return pathForAllFiles = value;
  }

  private String nameAsGiven = "zapped", fullPathName, lastFullPathName, lastNameAsGiven = "zapped", fileName;

  public void setFileInfo(String[] fileInfo) {
    // used by ScriptEvaluator dataFrame and load methods to temporarily save the state here
    fullPathName = fileInfo[0];
    fileName = fileInfo[Math.min(1,  fileInfo.length - 1)];
    nameAsGiven = fileInfo[Math.min(2, fileInfo.length - 1)];
    if (!nameAsGiven.equals("zapped")) {
      lastNameAsGiven = nameAsGiven;
      lastFullPathName = fullPathName;
    }
  }

  String[] getFileInfo() {
    // used by ScriptEvaluator dataFrame method
    return new String[] { fullPathName, fileName, nameAsGiven };
  }

  String getFullPathName(boolean orPrevious) {
    String f =(fullPathName != null ? fullPathName : nameAsGiven);
    return (!orPrevious || !f.equals("zapped") ? f : lastFullPathName != null ? lastFullPathName : lastNameAsGiven);
  }

  String getFileName() {
    return fileName != null ? fileName : nameAsGiven;
  }

  // for applet proxy
  private URL appletDocumentBaseURL = null;
  private String appletProxy;

  String getAppletDocumentBase() {
    return (appletDocumentBaseURL == null ? "" : appletDocumentBaseURL.toString());
  }

  void setAppletContext(String documentBase) {
    try {
      System.out.println("setting document base to \"" + documentBase + "\"");      
      appletDocumentBaseURL = (documentBase.length() == 0 ? null : new URL((URL) null, documentBase, null));
    } catch (MalformedURLException e) {
      System.out.println("error setting document base to " + documentBase);
    }
  }

  void setAppletProxy(String appletProxy) {
    this.appletProxy = (appletProxy == null || appletProxy.length() == 0 ? null
        : appletProxy);
  }


  /////////////// createAtomSetCollectionFromXXX methods /////////////////

  // where XXX = File, Files, String, Strings, ArrayData, DOM, Reader

  /*
   * note -- createAtomSetCollectionFromXXX methods
   * were "openXXX" before refactoring 11/29/2008 -- BH
   * 
   * The problem was that while they did open the file, they
   * (mostly) also closed them, and this was confusing.
   * 
   * The term "clientFile" was replaced by "atomSetCollection"
   * here because that's what it is --- an AtomSetCollection,
   * not a file. The file is closed at this point. What is
   * returned is the atomSetCollection object.
   * 
   * One could say this is just semantics, but there were
   * subtle bugs here, where readers were not always being 
   * closed explicitly. In the process of identifying Out of
   * Memory Errors, I felt it was necessary to clarify all this.
   * 
   * Apologies to those who feel the original clientFile notation
   * was more generalizable or understandable. 
   * 
   */
  Object createAtomSetCollectionFromFile(String name,
                                         Map<String, Object> htParams,
                                         boolean isAppend) {
    if (htParams.get("atomDataOnly") == null) {
      setLoadState(htParams);
    }
    name = viewer.resolveDatabaseFormat(name);
    int pt = name.indexOf("::");
    String nameAsGiven = (pt >= 0 ? name.substring(pt + 2) : name);
    String fileType = (pt >= 0 ? name.substring(0, pt) : null);
    Logger.info("\nFileManager.getAtomSetCollectionFromFile(" + nameAsGiven
        + ")" + (name.equals(nameAsGiven) ? "" : " //" + name));
    String[] names = classifyName(nameAsGiven, true);
    if (names.length == 1)
      return names[0];
    String fullPathName = names[0];
    String fileName = names[1];
    htParams.put("fullPathName", (fileType == null ? "" : fileType + "::")
        + fullPathName.replace('\\', '/'));
    if (viewer.getBoolean(T.messagestylechime) && viewer.getBoolean(T.debugscript))
      viewer.scriptStatus("Requesting " + fullPathName);
    FileReader fileReader = new FileReader(this, viewer, fileName, fullPathName, nameAsGiven,
        fileType, null, htParams, isAppend);
    fileReader.run();
    return fileReader.getAtomSetCollection();
  }

  Object createAtomSetCollectionFromFiles(String[] fileNames,
                                          Map<String, Object> htParams,
                                          boolean isAppend) {
    setLoadState(htParams);
    String[] fullPathNames = new String[fileNames.length];
    String[] namesAsGiven = new String[fileNames.length];
    String[] fileTypes = new String[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      int pt = fileNames[i].indexOf("::");
      String nameAsGiven = (pt >= 0 ? fileNames[i].substring(pt + 2)
          : fileNames[i]);
      String fileType = (pt >= 0 ? fileNames[i].substring(0, pt) : null);
      String[] names = classifyName(nameAsGiven, true);
      if (names.length == 1)
        return names[0];
      fullPathNames[i] = names[0];
      fileNames[i] = names[0].replace('\\', '/');
      fileTypes[i] = fileType;
      namesAsGiven[i] = nameAsGiven;
    }
    htParams.put("fullPathNames", fullPathNames);
    htParams.put("fileTypes", fileTypes);
    JmolFilesReaderInterface filesReader = newFilesReader(fullPathNames, namesAsGiven,
        fileTypes, null, htParams, isAppend);
    filesReader.run();
    return filesReader.getAtomSetCollection();
  }

  Object createAtomSetCollectionFromString(String strModel,
                                           Map<String, Object> htParams,
                                           boolean isAppend) {
    setLoadState(htParams);
    boolean isAddH = (strModel.indexOf(JC.ADD_HYDROGEN_TITLE) >= 0);
    String[] fnames = (isAddH ? getFileInfo() : null);
    FileReader fileReader = new FileReader(this, viewer, "string", "string", "string", null,
        JmolBinary.getBR(strModel), htParams, isAppend);
    fileReader.run();
    if (fnames != null)
      setFileInfo(fnames);
    if (!isAppend && !(fileReader.getAtomSetCollection() instanceof String)) {
      viewer.zap(false, true, false);
      setFileInfo(new String[] { strModel == JC.MODELKIT_ZAP_STRING ? JC.MODELKIT_ZAP_TITLE
          : "string"});
    }
    return fileReader.getAtomSetCollection();
  }

  Object createAtomSeCollectionFromStrings(String[] arrayModels,
                                           SB loadScript,
                                           Map<String, Object> htParams,
                                           boolean isAppend) {
    if (!htParams.containsKey("isData")) {
      String oldSep = "\"" + viewer.getDataSeparator() + "\"";
      String tag = "\"" + (isAppend ? "append" : "model") + " inline\"";
      SB sb = new SB();
      sb.append("set dataSeparator \"~~~next file~~~\";\ndata ").append(tag);
      for (int i = 0; i < arrayModels.length; i++) {
        if (i > 0)
          sb.append("~~~next file~~~");
        sb.append(arrayModels[i]);
      }
      sb.append("end ").append(tag).append(";set dataSeparator ")
          .append(oldSep);
      loadScript.appendSB(sb);
    }
    setLoadState(htParams);
    Logger.info("FileManager.getAtomSetCollectionFromStrings(string[])");
    String[] fullPathNames = new String[arrayModels.length];
    DataReader[] readers = new DataReader[arrayModels.length];
    for (int i = 0; i < arrayModels.length; i++) {
      fullPathNames[i] = "string[" + i + "]";
      readers[i] = newDataReader(arrayModels[i]);
    }
    JmolFilesReaderInterface filesReader = newFilesReader(fullPathNames, fullPathNames,
        null, readers, htParams, isAppend);
    filesReader.run();
    return filesReader.getAtomSetCollection();
  }

  Object createAtomSeCollectionFromArrayData(List<Object> arrayData,
                                             Map<String, Object> htParams,
                                             boolean isAppend) {
    // NO STATE SCRIPT -- HERE WE ARE TRYING TO CONSERVE SPACE
    Logger.info("FileManager.getAtomSetCollectionFromArrayData(Vector)");
    int nModels = arrayData.size();
    String[] fullPathNames = new String[nModels];
    DataReader[] readers = new DataReader[nModels];
    for (int i = 0; i < nModels; i++) {
      fullPathNames[i] = "String[" + i + "]";
      readers[i] = newDataReader(arrayData.get(i));
    }
    JmolFilesReaderInterface filesReader = newFilesReader(fullPathNames, fullPathNames,
        null, readers, htParams, isAppend);
    filesReader.run();
    return filesReader.getAtomSetCollection();
  }

  private JmolFilesReaderInterface newFilesReader(String[] fullPathNames,
                                                  String[] namesAsGiven,
                                                  String[] fileTypes,
                                                  DataReader[] readers,
                                                  Map<String, Object> htParams,
                                                  boolean isAppend) {
    JmolFilesReaderInterface fr = (JmolFilesReaderInterface) Interface
        .getOptionInterface("io2.FilesReader");
    fr.set(this, viewer, fullPathNames, namesAsGiven, fileTypes, readers, htParams,
        isAppend);
    return fr;
  }

  private DataReader newDataReader(Object data) {
    String reader = (data instanceof String ? "String"
        : PT.isAS(data) ? "Array" 
        : data instanceof List<?> ? "List" : null);
    if (reader == null)
      return null;
    DataReader dr = (DataReader) Interface.getOptionInterface("io2." + reader + "DataReader");
    return dr.setData(data);
  }

  Object createAtomSetCollectionFromDOM(Object DOMNode,
                                        Map<String, Object> htParams) {
    JmolDomReaderInterface aDOMReader = (JmolDomReaderInterface) Interface.getOptionInterface("io2.DOMReadaer");
    aDOMReader.set(this, viewer, DOMNode, htParams);
    aDOMReader.run();
    return aDOMReader.getAtomSetCollection();
  }

  /**
   * not used in Jmol project -- will close reader
   * 
   * @param fullPathName
   * @param name
   * @param reader
   * @param htParams 
   * @return fileData
   */
  Object createAtomSetCollectionFromReader(String fullPathName, String name,
                                           Object reader,
                                           Map<String, Object> htParams) {
    FileReader fileReader = new FileReader(this, viewer, name, fullPathName, name, null,
        reader, htParams, false);
    fileReader.run();
    return fileReader.getAtomSetCollection();
  }

  /////////////// generally useful file I/O methods /////////////////

  // mostly internal to FileManager and its enclosed classes

  BufferedInputStream getBufferedInputStream(String fullPathName) {
    Object ret = getBufferedReaderOrErrorMessageFromName(fullPathName,
        new String[2], true, true);
    return (ret instanceof BufferedInputStream ? (BufferedInputStream) ret
        : null);
  }

  public Object getBufferedInputStreamOrErrorMessageFromName(
                                                             String name,
                                                             String fullName,
                                                             boolean showMsg,
                                                             boolean checkOnly,
                                                             byte[] outputBytes,
                                                             boolean allowReader) {
    byte[] cacheBytes = null;
    if (outputBytes == null) {
      cacheBytes = (fullName == null || pngjCache == null ? null : JmolBinary
          .getCachedPngjBytes(this, fullName));
      if (cacheBytes == null)
        cacheBytes = (byte[]) cacheGet(name, true);
    }
    BufferedInputStream bis = null;
    Object ret = null;
    String errorMessage = null;
    try {
      if (cacheBytes == null) {
        boolean isPngjBinaryPost = (name.indexOf("?POST?_PNGJBIN_") >= 0);
        boolean isPngjPost = (isPngjBinaryPost || name.indexOf("?POST?_PNGJ_") >= 0);
        if (name.indexOf("?POST?_PNG_") > 0 || isPngjPost) {
          String[] errMsg = new String[1];
          byte[] bytes = viewer.getImageAsBytes(isPngjPost ? "PNGJ" : "PNG", 0, 0, -1, errMsg);
          if (errMsg[0] != null)
            return errMsg[0];
          if (isPngjBinaryPost) {
            outputBytes = bytes;
            name = PT.simpleReplace(name, "?_", "=_");
          } else {
            name = new SB().append(name).append("=").appendSB(
                Base64.getBase64(bytes)).toString();
          }
        }
        int iurl = urlTypeIndex(name);
        boolean isURL = (iurl >= 0);
        String post = null;
        if (isURL && (iurl = name.indexOf("?POST?")) >= 0) {
          post = name.substring(iurl + 6);
          name = name.substring(0, iurl);
        }
        boolean isApplet = (appletDocumentBaseURL != null);
        if (name.indexOf(".png") >= 0 && pngjCache == null
            && viewer.cachePngFiles())
          JmolBinary.cachePngjFile(this, null);
        if (isApplet || isURL) {
          if (isApplet && isURL && appletProxy != null)
            name = appletProxy + "?url=" + urlEncode(name);
          URL url = (isApplet ? new URL(appletDocumentBaseURL, name, null)
              : new URL((URL) null, name, null));
          if (checkOnly)
            return null;
          name = url.toString();
          if (showMsg && name.toLowerCase().indexOf("password") < 0)
            Logger.info("FileManager opening 1 " + name);
          ret = viewer.apiPlatform.getBufferedURLInputStream(url, outputBytes, post);
          byte[] bytes = null;
          if (ret instanceof SB) {
            SB sb = (SB) ret;
            if (allowReader && !JmolBinary.isBase64(sb))
              return JmolBinary.getBR(sb.toString());
            bytes = JmolBinary.getBytesFromSB(sb);
          } else if (PT.isAB(ret)) {
            bytes = (byte[]) ret;
          }
          if (bytes != null)
            ret = JmolBinary.getBIS(bytes);
        } else if ((cacheBytes = (byte[]) cacheGet(name, true)) == null) {
          if (showMsg)
            Logger.info("FileManager opening 2 " + name);
          ret = viewer.apiPlatform.getBufferedFileInputStream(name);
        }
        if (ret instanceof String)
          return ret;
      }
      bis = (cacheBytes == null ? (BufferedInputStream) ret : JmolBinary.getBIS(cacheBytes));
      if (checkOnly) {
        bis.close();
        bis = null;
      }
      return bis;
    } catch (Exception e) {
      try {
        if (bis != null)
          bis.close();
      } catch (IOException e1) {
      }
      errorMessage = "" + e;
    }
    return errorMessage;
  }
  
  private String urlEncode(String name) {
    try {
      return URLEncoder.encode(name, "utf-8");
    } catch (UnsupportedEncodingException e) {
      return name;
    }
  }

  public String getEmbeddedFileState(String fileName) {
    String[] dir = null;
    dir = getZipDirectory(fileName, false);
    if (dir.length == 0) {
      String state = viewer.getFileAsString4(fileName, -1, false, true, false);
      return (state.indexOf(JC.EMBEDDED_SCRIPT_TAG) < 0 ? ""
          : JmolBinary.getEmbeddedScript(state));
    }
    for (int i = 0; i < dir.length; i++)
      if (dir[i].indexOf(".spt") >= 0) {
        String[] data = new String[] { fileName + "|" + dir[i], null };
        getFileDataOrErrorAsString(data, -1, false, false, false);
        return data[1];
      }
    return "";
  }

  /**
   * just check for a file as being readable. Do not go into a zip file
   * 
   * @param filename
   * @return String[2] where [0] is fullpathname and [1] is error message or null
   */
  String[] getFullPathNameOrError(String filename) {
    String[] names = classifyName(filename, true);
    if (names == null || names[0] == null || names.length < 2)
      return new String[] { null, "cannot read file name: " + filename };
    String name = names[0];
    String fullPath = names[0].replace('\\', '/');
    name = JmolBinary.getZipRoot(name);
    Object errMsg = getBufferedInputStreamOrErrorMessageFromName(name, fullPath, false, true, null, false);
    return new String[] { fullPath,
        (errMsg instanceof String ? (String) errMsg : null) };
  }

  Object getBufferedReaderOrErrorMessageFromName(String name,
                                                 String[] fullPathNameReturn,
                                                 boolean isBinary,
                                                 boolean doSpecialLoad) {
    Object data = cacheGet(name, false);
    boolean isBytes = PT.isAB(data);
    byte[] bytes = (isBytes ? (byte[]) data : null);
    if (name.startsWith("cache://")) {
      if (data == null)
        return "cannot read " + name;
      if (isBytes) {
        bytes = (byte[]) data;
      } else {
        return JmolBinary.getBR((String) data);
      }
    }
    String[] names = classifyName(name, true);
    if (names == null)
      return "cannot read file name: " + name;
    if (fullPathNameReturn != null)
      fullPathNameReturn[0] = names[0].replace('\\', '/');
    return getUnzippedReaderOrStreamFromName(names[0], bytes,
        false, isBinary, false, doSpecialLoad, null);
  }

  public Object getUnzippedReaderOrStreamFromName(String name, byte[] bytes,
                                                  boolean allowZipStream,
                                                  boolean forceInputStream,
                                                  boolean isTypeCheckOnly,
                                                  boolean doSpecialLoad,
                                                  Map<String, Object> htParams) {
    String[] subFileList = null;
    String[] info = (bytes == null && doSpecialLoad ? getSpartanFileList(name)
        : null);
    String name00 = name;
    if (info != null) {
      if (isTypeCheckOnly)
        return info;
      if (info[2] != null) {
        String header = info[1];
        Map<String, String> fileData = new Hashtable<String, String>();
        if (info.length == 3) {
          // we need information from the output file, info[2]
          String name0 = getObjectAsSections(info[2], header, fileData);
          fileData.put("OUTPUT", name0);
          info = JmolBinary.spartanFileList(name, fileData.get(name0));
          if (info.length == 3) {
            // might have a second option
            name0 = getObjectAsSections(info[2], header, fileData);
            fileData.put("OUTPUT", name0);
            info = JmolBinary.spartanFileList(info[1], fileData.get(name0));
          }
        }
        // load each file individually, but return files IN ORDER
        SB sb = new SB();
        if (fileData.get("OUTPUT") != null)
          sb.append(fileData.get(fileData.get("OUTPUT")));
        String s;
        for (int i = 2; i < info.length; i++) {
          name = info[i];
          name = getObjectAsSections(name, header, fileData);
          Logger.info("reading " + name);
          s = fileData.get(name);
          sb.append(s);
        }
        s = sb.toString();
        if (spardirCache == null)
          spardirCache = new Hashtable<String, byte[]>();
        spardirCache.put(name00.replace('\\', '/'), s.getBytes());
        return JmolBinary.getBR(s);
      }
      // continuing...
      // here, for example, for an SPT file load that is not just a type check
      // (type check is only for application file opening and drag-drop to
      // determine if
      // script or load command should be used)
    }

    if (bytes == null && pngjCache != null) {
      bytes = JmolBinary.getCachedPngjBytes(this, name);
      if (bytes != null && htParams != null)
        htParams.put("sourcePNGJ", Boolean.TRUE);
    }
    String fullName = name;
    if (name.indexOf("|") >= 0) {
      subFileList = PT.split(name, "|");
      if (bytes == null)
        Logger.info("FileManager opening 3 " + name);
      name = subFileList[0];
    }
    Object t = (bytes == null ? getBufferedInputStreamOrErrorMessageFromName(
        name, fullName, true, false, null, !forceInputStream)
        : JmolBinary.getBIS(bytes));
    try {
      if (t instanceof String)
        return t;
      if (t instanceof BufferedReader)
        return t;
      BufferedInputStream bis = JmolBinary.getUnzippedInputStream((BufferedInputStream) t);
      if (JmolBinary.isCompoundDocumentS(bis)) {
        JmolDocument doc = (JmolDocument) Interface
            .getOptionInterface("io2.CompoundDocument");
        doc.setStream(bis, true);
        return JmolBinary.getBR(doc.getAllDataFiles(
            "Molecule", "Input").toString());
      }
      if (JmolBinary.isPickleS(bis))
        return bis;
      bis = JmolBinary.checkPngZipStream(bis);
      if (JmolBinary.isZipS(bis)) {
        if (allowZipStream)
          return JmolBinary.newZipInputStream(bis);
        Object o = JmolBinary.getZipFileContents(bis, subFileList, 1, forceInputStream);
        return (o instanceof String ? JmolBinary.getBR((String) o) : o);
      }
      return (forceInputStream ? bis : JmolBinary.getBufferedReader(bis, null));
    } catch (Exception ioe) {
      return ioe.toString();
    }
  }

  private String[] getSpartanFileList(String name) {
      // check for .spt file type -- Jmol script
      if (name.endsWith(".spt"))
        return new String[] { null, null, null }; // DO NOT actually load any file
      // check for zipped up spardir -- we'll automatically take first file there
      if (name.endsWith(".spardir.zip"))
        return new String[] { "SpartanSmol", "Directory Entry ", name + "|output"};
      name = name.replace('\\', '/');
      if (!name.endsWith(".spardir") && name.indexOf(".spardir/") < 0)
        return null; 
      // look for .spardir or .spardir/...
      int pt = name.lastIndexOf(".spardir");
      if (pt < 0)
        return null;
      if (name.lastIndexOf("/") > pt) {
        // a single file in the spardir directory is requested
        return new String[] { "SpartanSmol", "Directory Entry ",
            name + "/input", name + "/archive",
            name + "/Molecule:asBinaryString", name + "/proparc" };      
      }
      return new String[] { "SpartanSmol", "Directory Entry ", name + "/output" };
  }

  /**
   * delivers file contents and directory listing for a ZIP/JAR file into sb
   * 
   * @param name
   * @param header
   * @param fileData
   * @return name of entry
   */
  private String getObjectAsSections(String name, String header,
                                     Map<String, String> fileData) {
    if (name == null)
      return null;
    String[] subFileList = null;
    boolean asBinaryString = false;
    String name0 = name.replace('\\', '/');
    if (name.indexOf(":asBinaryString") >= 0) {
      asBinaryString = true;
      name = name.substring(0, name.indexOf(":asBinaryString"));
    }
    SB sb = null;
    if (fileData.containsKey(name0))
      return name0;
    if (name.indexOf("#JMOL_MODEL ") >= 0) {
      fileData.put(name0, name0 + "\n");
      return name0;
    }
    String fullName = name;
    if (name.indexOf("|") >= 0) {
      subFileList = PT.split(name, "|");
      name = subFileList[0];
    }
    BufferedInputStream bis = null;
    try {
      Object t = getBufferedInputStreamOrErrorMessageFromName(name, fullName,
          false, false, null, false);
      if (t instanceof String) {
        fileData.put(name0, (String) t + "\n");
        return name0;
      }
      bis = (BufferedInputStream) t;
      if (JmolBinary.isCompoundDocumentS(bis)) {
        JmolDocument doc = (JmolDocument) Interface
            .getOptionInterface("io2.CompoundDocument");
        doc.setStream(bis, true);
        doc.getAllDataMapped(name.replace('\\', '/'), "Molecule", fileData);
      } else if (JmolBinary.isZipS(bis)) {
        JmolBinary.getAllZipData(bis, subFileList, name.replace('\\', '/'), "Molecule",
            fileData);
      } else if (asBinaryString) {
        // used for Spartan binary file reading
        JmolDocument bd = (JmolDocument) Interface
            .getOptionInterface("io2.BinaryDocument");
        bd.setStream(bis, false);
        sb = new SB();
        //note -- these headers must match those in ZipUtil.getAllData and CompoundDocument.getAllData
        if (header != null)
          sb.append("BEGIN Directory Entry " + name0 + "\n");
        try {
          while (true)
            sb.append(Integer.toHexString(bd.readByte() & 0xFF)).appendC(' ');
        } catch (Exception e1) {
          sb.appendC('\n');
        }
        if (header != null)
          sb.append("\nEND Directory Entry " + name0 + "\n");
        fileData.put(name0, sb.toString());
      } else {
        BufferedReader br = JmolBinary.getBufferedReader(
            JmolBinary.isGzipS(bis) ? new BufferedInputStream(JmolBinary.newGZIPInputStream(bis)) : bis, null);
        String line;
        sb = new SB();
        if (header != null)
          sb.append("BEGIN Directory Entry " + name0 + "\n");
        while ((line = br.readLine()) != null) {
          sb.append(line);
          sb.appendC('\n');
        }
        br.close();
        if (header != null)
          sb.append("\nEND Directory Entry " + name0 + "\n");
        fileData.put(name0, sb.toString());
      }
    } catch (Exception ioe) {
      fileData.put(name0, ioe.toString());
    }
    if (bis != null)
      try {
        bis.close();
      } catch (Exception e) {
        //
      }
    if (!fileData.containsKey(name0))
      fileData.put(name0, "FILE NOT FOUND: " + name0 + "\n");
    return name0;
  }

  /**
   * 
   * @param fileName
   * @param addManifest
   * @return [] if not a zip file;
   */
  public String[] getZipDirectory(String fileName, boolean addManifest) {
    Object t = getBufferedInputStreamOrErrorMessageFromName(fileName, fileName,
        false, false, null, false);
    return JmolBinary.getZipDirectoryAndClose((BufferedInputStream) t, addManifest);
  }

  public Object getFileAsBytes(String name, OC out,
                               boolean allowZip) {
    // ?? used by eval of "WRITE FILE"
    // will be full path name
    if (name == null)
      return null;
    String fullName = name;
    String[] subFileList = null;
    if (name.indexOf("|") >= 0) {
      subFileList = PT.split(name, "|");
      name = subFileList[0];
      allowZip = true;
    }
    Object t = getBufferedInputStreamOrErrorMessageFromName(name, fullName,
        false, false, null, false);
    if (t instanceof String)
      return "Error:" + t;
    try {
      BufferedInputStream bis = (BufferedInputStream) t;
      Object bytes = (out != null || !allowZip || subFileList == null
          || subFileList.length <= 1 || !JmolBinary.isZipS(bis)
          && !JmolBinary.isPngZipStream(bis) ? JmolBinary.getStreamAsBytes(bis,
          out) : JmolBinary.getZipFileContentsAsBytes(bis, subFileList, 1));
      bis.close();
      return bytes;
    } catch (Exception ioe) {
      return ioe.toString();
    }
  }

  /**
   * 
   * @param data
   *        [0] initially path name, but returned as full path name; [1]file
   *        contents (directory listing for a ZIP/JAR file) or error string
   * @param nBytesMax or -1
   * @param doSpecialLoad
   * @param allowBinary 
   * @param checkProtected TODO
   * @return true if successful; false on error
   */

  boolean getFileDataOrErrorAsString(String[] data, int nBytesMax,
                                     boolean doSpecialLoad, boolean allowBinary, 
                                     boolean checkProtected) {
    data[1] = "";
    String name = data[0];
    if (name == null)
      return false;
    Object t = getBufferedReaderOrErrorMessageFromName(name, data, false,
        doSpecialLoad);
    if (t instanceof String) {
      data[1] = (String) t;
      return false;
    }
    if (checkProtected && !checkSecurity(data[0])) {
      data[1] = "java.io. Security exception: cannot read file " + data[0];
    	return false;
    }
    try {
    return JmolBinary.readAll((BufferedReader) t, nBytesMax, allowBinary, data, 1);
    } catch (Exception e) {
      return false;
    }
  }

  private boolean checkSecurity(String f) {
    // when load() function and local file: 
    if (!f.startsWith("file:"))
       return true;
    int pt = f.lastIndexOf('/');
    // root directory C:/foo or file:///c:/foo or "/foo"
    // no hidden files 
    // no files without extension
    if (f.lastIndexOf(":/") == pt - 1 
      || f.indexOf("/.") >= 0 
      || f.lastIndexOf('.') < f.lastIndexOf('/'))
      return false;
    return true;
  }

  void loadImage(String name, String echoName) {
    Object image = null;
    Object info = null;
    String fullPathName = "";
    while (true) {
      if (name == null)
        break;
      String[] names = classifyName(name, true);
      if (names == null) {
        fullPathName = "cannot read file name: " + name;
        break;
      }
      GenericPlatform apiPlatform = viewer.apiPlatform;
      fullPathName = names[0].replace('\\', '/');
      if (fullPathName.indexOf("|") > 0) {
        Object ret = getFileAsBytes(fullPathName, null, true);
        if (!PT.isAB(ret)) {
          fullPathName = "" + ret;
          break;
        }
        image = (viewer.isJS ? ret : apiPlatform.createImage(ret));
      } else if (viewer.isJS) {
      } else if (urlTypeIndex(fullPathName) >= 0) {
        try {
          image = apiPlatform.createImage(new URL((URL) null, fullPathName,
              null));
        } catch (Exception e) {
          fullPathName = "bad URL: " + fullPathName;
          break;
        }
      } else {
        image = apiPlatform.createImage(fullPathName);
      }
      /**
       * @j2sNative
       * 
       *            info = [echoName, fullPathName];
       * 
       */
      {
        if (image == null)
          break;
      }
      try {
        if (!apiPlatform.waitForDisplay(info, image)) {
          image = null;
          break;
        }
        /**
         * 
         * note -- JavaScript just returns immediately, because we must wait
         * for the image to load, and it is single-threaded
         * 
         * @j2sNative
         *   
         *   fullPathName = null; break;
         */
        {
          if (apiPlatform.getImageWidth(image) < 1) {
            fullPathName = "invalid or missing image " + fullPathName;
            image = null;
          }
          break;
        }
      } catch (Exception e) {
        System.out.println(e.toString());
        fullPathName = e.toString() + " opening 4 " + fullPathName;
        image = null;
        break;
      }
    }
    viewer.loadImageData(image, fullPathName, echoName, null);
  }

  public final static int URL_LOCAL = 3;
  private final static String[] urlPrefixes = { "http:", "https:", "ftp:",
      "file:" };

  public static int urlTypeIndex(String name) {
    if (name == null)
      return -2; // local unsigned applet
    for (int i = 0; i < urlPrefixes.length; ++i) {
      if (name.startsWith(urlPrefixes[i])) {
        return i;
      }
    }
    return -1;
  }
  
  public static boolean isLocal(String fileName) {
    if (fileName == null)
      return false;
    int itype = urlTypeIndex(fileName);
    return (itype < 0 || itype == URL_LOCAL);
  }



  /**
   * [0] and [2] may return same as [1] in the 
   * case of a local unsigned applet.
   * 
   * @param name
   * @param isFullLoad
   *        false only when just checking path
   * @return [0] full path name, [1] file name without path, [2] full URL
   */
  private String[] classifyName(String name, boolean isFullLoad) {
    if (name == null)
      return new String[] { null };
    boolean doSetPathForAllFiles = (pathForAllFiles.length() > 0);
    if (name.startsWith("?")) {
      if ((name = viewer.dialogAsk("Load", name.substring(1))) == null)
        return new String[] { isFullLoad ? "#CANCELED#" : null };
      doSetPathForAllFiles = false;
    }
    GenericFileInterface file = null;
    URL url = null;
    String[] names = null;
    if (name.startsWith("cache://")) {
      names = new String[3];
      names[0] = names[2] = name;
      names[1] = stripPath(names[0]);
      return names;
    }
    name = viewer.resolveDatabaseFormat(name);
    if (name.indexOf(":") < 0 && name.indexOf("/") != 0)
      name = addDirectory(viewer.getDefaultDirectory(), name);
    if (appletDocumentBaseURL == null) {
      // This code is for the app or signed local applet 
      // -- no local file reading for headless
      if (urlTypeIndex(name) >= 0 || viewer.haveAccess(ACCESS.NONE)
          || viewer.haveAccess(ACCESS.READSPT) && !name.endsWith(".spt")
          && !name.endsWith("/")) {
        try {
          url = new URL((URL) null, name, null);
        } catch (MalformedURLException e) {
          return new String[] { isFullLoad ? e.toString() : null };
        }
      } else {
        file = viewer.apiPlatform.newFile(name);
        String s = file.getFullPath();
        // local unsigned applet may have access control issue here and get a null return
        String fname = file.getName();
        names = new String[] { (s == null ? fname : s), fname,
            (s == null ? fname : "file:/" + s.replace('\\', '/')) };
      }
    } else {
      // This code is only for the non-local applet
      try {
        if (name.indexOf(":\\") == 1 || name.indexOf(":/") == 1)
          name = "file:/" + name;
        //        else if (name.indexOf("/") == 0 && viewer.isSignedApplet())
        //        name = "file:" + name;
        url = new URL(appletDocumentBaseURL, name, null);
      } catch (MalformedURLException e) {
        return new String[] { isFullLoad ? e.toString() : null };
      }
    }
    if (url != null) {
      names = new String[3];
      names[0] = names[2] = url.toString();
      names[1] = stripPath(names[0]);
    }
    if (doSetPathForAllFiles) {
      String name0 = names[0];
      names[0] = pathForAllFiles + names[1];
      Logger.info("FileManager substituting " + name0 + " --> " + names[0]);
    }
    if (isFullLoad && (file != null || urlTypeIndex(names[0]) == URL_LOCAL)) {
      String path = (file == null ? PT.trim(names[0].substring(5), "/")
          : names[0]);
      int pt = path.length() - names[1].length() - 1;
      if (pt > 0) {
        path = path.substring(0, pt);
        setLocalPath(viewer, path, true);
      }
    }
    return names;
  }

  private static String addDirectory(String defaultDirectory, String name) {
    if (defaultDirectory.length() == 0)
      return name;
    char ch = (name.length() > 0 ? name.charAt(0) : ' ');
    String s = defaultDirectory.toLowerCase();
    if ((s.endsWith(".zip") || s.endsWith(".tar")) && ch != '|' && ch != '/')
      defaultDirectory += "|";
    return defaultDirectory
        + (ch == '/'
            || ch == '/'
            || (ch = defaultDirectory.charAt(defaultDirectory.length() - 1)) == '|'
            || ch == '/' ? "" : "/") + name;
  }

  String getDefaultDirectory(String name) {
    String[] names = classifyName(name, true);
    if (names == null)
      return "";
    name = fixPath(names[0]);
    return (name == null ? "" : name.substring(0, name.lastIndexOf("/")));
  }

  private static String fixPath(String path) {
    path = path.replace('\\', '/');
    path = PT.simpleReplace(path, "/./", "/");
    int pt = path.lastIndexOf("//") + 1;
    if (pt < 1)
      pt = path.indexOf(":/") + 1;
    if (pt < 1)
      pt = path.indexOf("/");
    if (pt < 0)
      return null;
    String protocol = path.substring(0, pt);
    path = path.substring(pt);

    while ((pt = path.lastIndexOf("/../")) >= 0) {
      int pt0 = path.substring(0, pt).lastIndexOf("/");
      if (pt0 < 0)
        return PT.simpleReplace(protocol + path, "/../", "/");
      path = path.substring(0, pt0) + path.substring(pt + 3);
    }
    if (path.length() == 0)
      path = "/";
    return protocol + path;
  }

  public String getFilePath(String name, boolean addUrlPrefix,
                            boolean asShortName) {
    String[] names = classifyName(name, false);
    return (names == null || names.length == 1 ? "" : asShortName ? names[1]
        : addUrlPrefix ? names[2] 
        : names[0] == null ? "" 
        : names[0].replace('\\', '/'));
  }

  public static GenericFileInterface getLocalDirectory(Viewer viewer, boolean forDialog) {
    String localDir = (String) viewer
        .getParameter(forDialog ? "currentLocalPath" : "defaultDirectoryLocal");
    if (forDialog && localDir.length() == 0)
      localDir = (String) viewer.getParameter("defaultDirectoryLocal");
    if (localDir.length() == 0)
      return (viewer.isApplet() ? null : viewer.apiPlatform.newFile(System
          .getProperty("user.dir", ".")));
    if (viewer.isApplet() && localDir.indexOf("file:/") == 0)
      localDir = localDir.substring(6);
    GenericFileInterface f = viewer.apiPlatform.newFile(localDir);
    try {
      return f.isDirectory() ? f : f.getParentAsFile();
    } catch (Exception e) {
      return  null;
    }
  }

  /**
   * called by getImageFileNameFromDialog 
   * called by getOpenFileNameFromDialog
   * called by getSaveFileNameFromDialog
   * 
   * called by classifyName for any full file load
   * called from the CD command
   * 
   * currentLocalPath is set in all cases
   *   and is used specifically for dialogs as a first try
   * defaultDirectoryLocal is set only when not from a dialog
   *   and is used only in getLocalPathForWritingFile or
   *   from an open/save dialog.
   * In this way, saving a file from a dialog doesn't change
   *   the "CD" directory. 
   * Neither of these is saved in the state, but 
   * 
   * 
   * @param viewer
   * @param path
   * @param forDialog
   */
  public static void setLocalPath(Viewer viewer, String path,
                                  boolean forDialog) {
    while (path.endsWith("/") || path.endsWith("\\"))
      path = path.substring(0, path.length() - 1);
    viewer.setStringProperty("currentLocalPath", path);
    if (!forDialog)
      viewer.setStringProperty("defaultDirectoryLocal", path);
  }

  public static String getLocalPathForWritingFile(Viewer viewer, String file) {
    if (file.indexOf("file:/") == 0)
      return file.substring(6);
    if (file.indexOf("/") == 0 || file.indexOf(":") >= 0)
      return file;
    GenericFileInterface dir = null;
    try {
      dir = getLocalDirectory(viewer, false);
    } catch (Exception e) {
      // access control for unsigned applet
    }
    return (dir == null ? file : fixPath(dir.toString() + "/" + file));
  }

  public static String setScriptFileReferences(String script, String localPath,
                                               String remotePath,
                                               String scriptPath) {
    if (localPath != null)
      script = setScriptFileRefs(script, localPath, true);
    if (remotePath != null)
      script = setScriptFileRefs(script, remotePath, false);
    script = PT.simpleReplace(script, "\1\"", "\"");
    if (scriptPath != null) {
      while (scriptPath.endsWith("/"))
        scriptPath = scriptPath.substring(0, scriptPath.length() - 1);
      for (int ipt = 0; ipt < scriptFilePrefixes.length; ipt++) {
        String tag = scriptFilePrefixes[ipt];
        script = PT.simpleReplace(script, tag + ".", tag + scriptPath);
      }
    }
    return script;
  }

  /**
   * Sets all local file references in a script file to point to files within
   * dataPath. If a file reference contains dataPath, then the file reference is
   * left with that RELATIVE path. Otherwise, it is changed to a relative file
   * name within that dataPath. 
   * 
   * Only file references starting with "file://" are changed.
   * 
   * @param script
   * @param dataPath
   * @param isLocal 
   * @return revised script
   */
  private static String setScriptFileRefs(String script, String dataPath,
                                                boolean isLocal) {
    if (dataPath == null)
      return script;
    boolean noPath = (dataPath.length() == 0);
    List<String> fileNames = new  List<String>();
    JmolBinary.getFileReferences(script, fileNames);
    List<String> oldFileNames = new  List<String>();
    List<String> newFileNames = new  List<String>();
    int nFiles = fileNames.size();
    for (int iFile = 0; iFile < nFiles; iFile++) {
      String name0 = fileNames.get(iFile);
      String name = name0;
      if (isLocal == isLocal(name)) {
        int pt = (noPath ? -1 : name.indexOf("/" + dataPath + "/"));
        if (pt >= 0) {
          name = name.substring(pt + 1);
        } else {
          pt = name.lastIndexOf("/");
          if (pt < 0 && !noPath)
            name = "/" + name;
          if (pt < 0 || noPath)
            pt++;
          name = dataPath + name.substring(pt);
        }
      }
      Logger.info("FileManager substituting " + name0 + " --> " + name);
      oldFileNames.addLast("\"" + name0 + "\"");
      newFileNames.addLast("\1\"" + name + "\"");
    }
    return Txt.replaceStrings(script, oldFileNames, newFileNames);
  }

  public static String[] scriptFilePrefixes = new String[] { "/*file*/\"",
      "FILE0=\"", "FILE1=\"" };

  public static String stripPath(String name) {
    int pt = Math.max(name.lastIndexOf("|"), name.lastIndexOf("/"));
    return name.substring(pt + 1);
  }

  public static String fixFileNameVariables(String format, String fname) {
    String str = PT.simpleReplace(format, "%FILE", fname);
    if (str.indexOf("%LC") < 0)
      return str;
    fname = fname.toLowerCase();
    str = PT.simpleReplace(str, "%LCFILE", fname);
    if (fname.length() == 4)
      str = PT.simpleReplace(str, "%LC13", fname.substring(1, 3));
    return str;
  }

  public Map<String, byte[]> pngjCache;
  public Map<String, byte[]> spardirCache;
  
  public void clearPngjCache(String fileName) {
    if (fileName != null && (pngjCache == null || !pngjCache.containsKey(getCanonicalName(JmolBinary.getZipRoot(fileName)))))
        return;
      pngjCache = null;
      Logger.info("PNGJ cache cleared");
  }


  private Map<String, Object> cache = new Hashtable<String, Object>();

  void cachePut(String key, Object data) {
    key = key.replace('\\', '/');
    if (Logger.debugging)
      Logger.debug("cachePut " + key);
    if (data == null || "".equals(data)) { // J2S error -- cannot implement Int32Array.equals 
      cache.remove(key); 
      return;
    }
      cache.put(key, data);
      JmolBinary.getCachedPngjBytes(this, key);
  }
  
  public Object cacheGet(String key, boolean bytesOnly) {
    key = key.replace('\\', '/');
    // in the case of JavaScript local file reader, 
    // this will be a cached file, and the filename will not be known.
    int pt = key.indexOf("|");
    if (pt >= 0)
      key = key.substring(0, pt);
    if (Logger.debugging)
      Logger.debug("cacheGet " + key + " " + cache.containsKey(key));
    Object data = cache.get(key);
    return (bytesOnly && (data instanceof String) ? null : data);
  }

  void cacheClear() {
    Logger.info("cache cleared");
    cache.clear();
    clearPngjCache(null);
  }

  public int cacheFileByNameAdd(String fileName, boolean isAdd) {
    if (fileName == null || !isAdd && fileName.equalsIgnoreCase("")) {
      cacheClear();
      return -1;
    }
    Object data;
    if (isAdd) {
      fileName = viewer.resolveDatabaseFormat(fileName);
      data = getFileAsBytes(fileName, null, true);
      if (data instanceof String)
        return 0;
      cachePut(fileName, data);
    } else {
      if (fileName.endsWith("*"))
        return AU.removeMapKeys(cache, fileName.substring(0, fileName.length() - 1));
      data = cache.remove(fileName.replace('\\', '/'));
    }
    return (data == null ? 0 : data instanceof String ? ((String) data).length()
        : ((byte[]) data).length);
  }

  Map<String, Integer> cacheList() {
    Map<String, Integer> map = new Hashtable<String, Integer>();
    for (Map.Entry<String, Object> entry : cache.entrySet())
      map.put(entry.getKey(), Integer
          .valueOf(PT.isAB(entry.getValue()) ? ((byte[]) entry
              .getValue()).length : entry.getValue().toString().length()));
    return map;
  }

  public String getCanonicalName(String pathName) {
    String[] names = classifyName(pathName, true);
    return (names == null ? pathName : names[2]);
  }

  @Override
  public String postByteArray(String fileName, byte[] bytes) {
    Object ret = getBufferedInputStreamOrErrorMessageFromName(fileName, null, false,
            false, bytes, false);
    if (ret instanceof String)
      return (String) ret;
    try {
      ret = JmolBinary.getStreamAsBytes((BufferedInputStream) ret, null);
    } catch (IOException e) {
      try {
        ((BufferedInputStream) ret).close();
      } catch (IOException e1) {
        // ignore
      }
    }
    return (ret == null ? "" : JmolBinary.fixUTF((byte[]) ret));
  }

}
