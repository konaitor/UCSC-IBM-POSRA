/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-04-05 09:07:28 -0500 (Thu, 05 Apr 2007) $
 * $Revision: 7326 $
 *
 * Copyright (C) 2003-2005  The Jmol Development Team
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
package org.jmol.io;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import java.util.Map;

import org.jmol.api.Interface;
import org.jmol.api.JmolAdapter;
import org.jmol.api.JmolZipUtility;

import javajs.api.ZInputStream;
import javajs.util.AU;
import javajs.util.Base64;
import javajs.util.Encoding;
import javajs.util.OC;
import javajs.util.List;
import javajs.util.PT;
import javajs.util.SB;

import org.jmol.util.Logger;
import org.jmol.viewer.FileManager;
import org.jmol.viewer.JC;
import org.jmol.viewer.Viewer;


public class JmolBinary {

  public static final String JPEG_CONTINUE_STRING = " #Jmol...\0";
  
  public final static String PMESH_BINARY_MAGIC_NUMBER = "PM\1\0";

  private final static String DELPHI_BINARY_MAGIC_NUMBER = "\24\0\0\0";

  public static String determineSurfaceTypeIs(InputStream is) {
    BufferedReader br;
    try {
      br = getBufferedReader(new BufferedInputStream(is), "ISO-8859-1");
    } catch (IOException e) {
      return null;
    }
    return determineSurfaceFileType(br);
  }
  
  public static String determineSurfaceFileType(BufferedReader bufferedReader) {
    // JVXL should be on the FIRST line of the file, but it may be 
    // after comments or missing.

    // Apbs, Jvxl, or Cube, also efvet and DHBD

    String line = null;
    LimitedLineReader br = null;
    
    try {
      br = new LimitedLineReader(bufferedReader, 16000);
      line = br.getHeader(0);
    } catch (Exception e) {
      //
    }
    if (br == null || line == null || line.length() == 0)
      return null;

    //for (int i = 0; i < 220; i++)
    //  System.out.print(" " + i + ":" + (0 + line.charAt(i)));
    //System.out.println("");
    
    switch (line.charAt(0)) {
    case '@':
      if (line.indexOf("@text") == 0)
        return "Kinemage";
      break;
    case '#':
      if (line.indexOf(".obj") >= 0)
        return "Obj"; // #file: pymol.obj
      if (line.indexOf("MSMS") >= 0)
        return "Msms";
      break;
    case '&':
      if (line.indexOf("&plot") == 0)
        return "Jaguar";
      break;
    case '\r':
    case '\n':
      if (line.indexOf("ZYX") >= 0)
        return "Xplor";
      break;
    }
    if (line.indexOf("Here is your gzipped map") >= 0)
      return "UPPSALA" + line;
    if (line.indexOf("! nspins") >= 0)
      return "CastepDensity";
    if (line.indexOf("<jvxl") >= 0 && line.indexOf("<?xml") >= 0)
      return "JvxlXml";
    if (line.indexOf("#JVXL+") >= 0)
      return "Jvxl+";
    if (line.indexOf("#JVXL") >= 0)
      return "Jvxl";
    if (line.indexOf("<efvet ") >= 0)
      return "Efvet";
    if (line.indexOf("usemtl") >= 0)
      return "Obj";
    if (line.indexOf("# object with") == 0)
      return "Nff";
    if (line.indexOf("BEGIN_DATAGRID_3D") >= 0 || line.indexOf("BEGIN_BANDGRID_3D") >= 0)
      return "Xsf";
    // binary formats: problem here is that the buffered reader
    // may be translating byte sequences into unicode
    // and thus shifting the offset
    int pt0 = line.indexOf('\0');
    if (pt0 >= 0) {
      if (line.indexOf(PMESH_BINARY_MAGIC_NUMBER) == 0)
        return "Pmesh";
      if (line.indexOf(DELPHI_BINARY_MAGIC_NUMBER) == 0)
        return "DelPhi";
      if (line.indexOf("MAP ") == 208)
        return "Mrc";
      if (line.length() > 37 && (line.charAt(36) == 0 && line.charAt(37) == 100 
          || line.charAt(36) == 0 && line.charAt(37) == 100)) { 
           // header19 (short)100
          return "Dsn6";
      }
    }
    
    if (line.indexOf(" 0.00000e+00 0.00000e+00      0      0\n") >= 0)
      return "Uhbd"; // older APBS http://sourceforge.net/p/apbs/code/ci/9527462a39126fb6cd880924b3cc4880ec4b78a9/tree/src/mg/vgrid.c
    
    // Apbs, Jvxl, Obj, or Cube, maybe formatted Plt

    line = br.readLineWithNewline();
    if (line.indexOf("object 1 class gridpositions counts") == 0)
      return "Apbs";

    String[] tokens = PT.getTokens(line);
    String line2 = br.readLineWithNewline();// second line
    if (tokens.length == 2 && PT.parseInt(tokens[0]) == 3
        && PT.parseInt(tokens[1]) != Integer.MIN_VALUE) {
      tokens = PT.getTokens(line2);
      if (tokens.length == 3 && PT.parseInt(tokens[0]) != Integer.MIN_VALUE
          && PT.parseInt(tokens[1]) != Integer.MIN_VALUE
          && PT.parseInt(tokens[2]) != Integer.MIN_VALUE)
        return "PltFormatted";
    }
    String line3 = br.readLineWithNewline(); // third line
    if (line.startsWith("v ") && line2.startsWith("v ") && line3.startsWith("v "))
        return "Obj";
    //next line should be the atom line
    int nAtoms = PT.parseInt(line3);
    if (nAtoms == Integer.MIN_VALUE)
      return (line3.indexOf("+") == 0 ? "Jvxl+" : null);
    if (nAtoms >= 0)
      return "Cube"; //Can't be a Jvxl file
    nAtoms = -nAtoms;
    for (int i = 4 + nAtoms; --i >= 0;)
      if ((line = br.readLineWithNewline()) == null)
        return null;
    int nSurfaces = PT.parseInt(line);
    if (nSurfaces == Integer.MIN_VALUE)
      return null;
    return (nSurfaces < 0 ? "Jvxl" : "Cube"); //Final test looks at surface definition line
  }

  private static Encoding getUTFEncodingForStream(BufferedInputStream is) throws IOException {
    /**
     * @j2sNative
     * 
     *  is.resetStream();
     * 
     */
    {
    }
    byte[] abMagic = new byte[4];
    abMagic[3] = 1;
    try{
    is.mark(5);
    } catch (Exception e) {
      return Encoding.NONE;
    }
    is.read(abMagic, 0, 4);
    is.reset();
    return getUTFEncoding(abMagic);
  }

  public static String fixUTF(byte[] bytes) {
    
    Encoding encoding = getUTFEncoding(bytes);
    if (encoding != Encoding.NONE)
    try {
      String s = new String(bytes, encoding.name().replace('_', '-'));
      switch (encoding) {
      case UTF8:
      case UTF_16BE:
      case UTF_16LE:
        // extra byte at beginning removed
        s = s.substring(1);
        break;
      default:
        break;        
      }
      return s;
    } catch (UnsupportedEncodingException e) {
      System.out.println(e);
    }
    return new String(bytes);
  }

  private static Encoding getUTFEncoding(byte[] bytes) {
    if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)
      return Encoding.UTF8;
    if (bytes.length >= 4 && bytes[0] == (byte) 0 && bytes[1] == (byte) 0 
        && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF)
      return Encoding.UTF_32BE;
    if (bytes.length >= 4 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE 
        && bytes[2] == (byte) 0 && bytes[3] == (byte) 0)
      return Encoding.UTF_32LE;
    if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE)
      return Encoding.UTF_16LE;
    if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF)
      return Encoding.UTF_16BE;
    return Encoding.NONE;

  }
  
  private static byte[] getMagic(InputStream is, int n) {
    byte[] abMagic = new byte[n];
    /**
     * @j2sNative
     * 
     * is.resetStream();
     * 
     */
    {
    }
    try {
      is.mark(n + 1);
      is.read(abMagic, 0, n);
    } catch (IOException e) {
    }
    try {
      is.reset();
    } catch (IOException e) {
    }
    return abMagic;
  }

  public static boolean isCompoundDocumentS(InputStream is) {
    return isCompoundDocumentB(getMagic(is, 8));
  }

  public static boolean isCompoundDocumentB(byte[] bytes) {
    return (bytes.length >= 8 && bytes[0] == (byte) 0xD0
        && bytes[1] == (byte) 0xCF && bytes[2] == (byte) 0x11
        && bytes[3] == (byte) 0xE0 && bytes[4] == (byte) 0xA1
        && bytes[5] == (byte) 0xB1 && bytes[6] == (byte) 0x1A 
        && bytes[7] == (byte) 0xE1);
  }

  public static boolean isGzipS(InputStream is) {
    return isGzipB(getMagic(is, 2));
  }

  public static boolean isGzipB(byte[] bytes) {    
      return (bytes != null && bytes.length >= 2 
          && bytes[0] == (byte) 0x1F && bytes[1] == (byte) 0x8B);
  }

  public static boolean isPickleS(InputStream is) {
    return isPickleB(getMagic(is, 2));
  }

  public static boolean isPickleB(byte[] bytes) {    
    return (bytes != null && bytes.length >= 2 
        && bytes[0] == (byte) 0x7D && bytes[1] == (byte) 0x71);
}

  public static boolean isZipS(InputStream is) {
    return isZipB(getMagic(is, 4));
  }

  public static boolean isZipB(byte[] bytes) {
    return (bytes.length >= 4 
        && bytes[0] == 0x50  //PK<03><04> 
        && bytes[1] == 0x4B
        && bytes[2] == 0x03 
        && bytes[3] == 0x04);
  }

  public static boolean isPngZipStream(InputStream is) {
    if (isZipS(is))
      return false;
      byte[] abMagic = getMagic(is, 55); // PNGJ
      return (abMagic[51] == 0x50 
           && abMagic[52] == 0x4E
           && abMagic[53] == 0x47 
           && abMagic[54] == 0x4A);
  }

  public static String getZipRoot(String fileName) {
    int pt = fileName.indexOf("|");
    return (pt < 0 ? fileName : fileName.substring(0, pt));
  }

  public static byte[] getStreamBytes(InputStream is, long n) throws IOException {
    
    //Note: You cannot use InputStream.available() to reliably read
    //      zip data from the web. 
    
    int buflen = (n > 0 && n < 1024 ? (int) n : 1024);
    byte[] buf = new byte[buflen];
    byte[] bytes = new byte[n < 0 ? 4096 : (int) n];
    int len = 0;
    int totalLen = 0;
    while ((n < 0 || totalLen < n) 
        && (len = is.read(buf, 0, buflen)) > 0) {
      totalLen += len;
      if (totalLen > bytes.length)
        bytes = AU.ensureLengthByte(bytes, totalLen * 2);
      System.arraycopy(buf, 0, bytes, totalLen - len, len);
    }
    if (totalLen == bytes.length)
      return bytes;
    buf = new byte[totalLen];
    System.arraycopy(bytes, 0, buf, 0, totalLen);
    return buf;
  }

  public static String getEmbeddedScript(String script) {
    if (script == null)
      return script;
    int pt = script.indexOf(JC.EMBEDDED_SCRIPT_TAG);
    if (pt < 0)
      return script;
    int pt1 = script.lastIndexOf("/*", pt);
    int pt2 = script.indexOf((script.charAt(pt1 + 2) == '*' ? "*" : "") + "*/",
        pt);
    if (pt1 >= 0 && pt2 >= pt)
      script = script.substring(
          pt + JC.EMBEDDED_SCRIPT_TAG.length(), pt2)
          + "\n";
    while ((pt1 = script.indexOf(JPEG_CONTINUE_STRING)) >= 0)
      script = script.substring(0, pt1)
          + script.substring(pt1 + JPEG_CONTINUE_STRING.length() + 4);
    if (Logger.debugging)
      Logger.debug(script);
    return script;
  }

  static JmolZipUtility jzu;
  
  private static JmolZipUtility getJzu() {
    return (jzu == null ? jzu = (JmolZipUtility) Interface.getOptionInterface("io2.ZipUtil") : jzu);
  }

  public static String getZipDirectoryAsStringAndClose(BufferedInputStream t) {
    return getJzu().getZipDirectoryAsStringAndClose(t);
  }

  public static InputStream newGZIPInputStream(BufferedInputStream bis) throws IOException {
    return getJzu().newGZIPInputStream(bis);
  }

  public static ZInputStream newZipInputStream(InputStream in) {
    return getJzu().newZipInputStream(in);
  }

  public static Object getZipFileContents(BufferedInputStream bis,
                                          String[] subFileList, int listPtr, boolean asBufferedInputStream) {
    return getJzu().getZipFileContents(bis, subFileList, listPtr, asBufferedInputStream);
  }

  public static String[] getZipDirectoryAndClose(BufferedInputStream t,
                                                 boolean addManifest) {
    return getJzu().getZipDirectoryAndClose(t, addManifest);
  }

  public static void getAllZipData(BufferedInputStream bis, String[] subFileList,
                                String replace, String string,
                                Map<String, String> fileData) {
    getJzu().getAllZipData(bis, subFileList, replace, string, fileData);
  }

  public static Object getZipFileContentsAsBytes(BufferedInputStream bis,
                                                 String[] subFileList, int i) {
    return getJzu().getZipFileContentsAsBytes(bis, subFileList, i);
  }

  public static Object getStreamAsBytes(BufferedInputStream bis,
                                         OC out) throws IOException {
    byte[] buf = new byte[1024];
    byte[] bytes = (out == null ? new byte[4096] : null);
    int len = 0;
    int totalLen = 0;
    while ((len = bis.read(buf, 0, 1024)) > 0) {
      totalLen += len;
      if (out == null) {
        if (totalLen >= bytes.length)
          bytes = AU.ensureLengthByte(bytes, totalLen * 2);
        System.arraycopy(buf, 0, bytes, totalLen - len, len);
      } else {
        out.write(buf, 0, len);
      }
    }
    bis.close();
    if (out == null) {
      return AU.arrayCopyByte(bytes, totalLen);
    }
    return totalLen + " bytes";
  }


  public static boolean isBase64(SB sb) {
    return (sb.indexOf(";base64,") == 0);
  }

  public static byte[] getBytesFromSB(SB sb) {
    return (isBase64(sb) ? Base64.decodeBase64(sb.substring(8)) : sb.toBytes(0, -1));    
  }
  
  public static BufferedInputStream getBIS(byte[] bytes) {
    return new BufferedInputStream(new ByteArrayInputStream(bytes));
  }

  public static BufferedReader getBR(String string) {
    return new BufferedReader(new StringReader(string));
  }

  public static byte[] getCachedPngjBytes(FileManager fm, String pathName) {
    return (pathName.indexOf(".png") < 0 ? null : getJzu().getCachedPngjBytes(fm, pathName));
  }

  public static boolean cachePngjFile(FileManager fm, String[] data) {
    return getJzu().cachePngjFile(fm, data);
  }
  
  /**
   * A rather complicated means of reading a ZIP file, which could be a 
   * single file, or it could be a manifest-organized file, or it could be
   * a Spartan directory.
   * 
   * @param adapter 
   * 
   * @param is 
   * @param fileName 
   * @param zipDirectory 
   * @param htParams 
   * @param asBufferedReader 
   * @return a single atomSetCollection
   * 
   */
  public static Object getAtomSetCollectionOrBufferedReaderFromZip(JmolAdapter adapter, InputStream is, String fileName, String[] zipDirectory,
                             Map<String, Object> htParams, boolean asBufferedReader) {
    return getJzu().getAtomSetCollectionOrBufferedReaderFromZip(adapter, is, fileName, zipDirectory, htParams, 1, asBufferedReader);
  }

  public static String[] spartanFileList(String name, String zipDirectory) {
    return getJzu().spartanFileList(name, zipDirectory);
  }

  public static void getFileReferences(String script, List<String> fileList) {
    for (int ipt = 0; ipt < FileManager.scriptFilePrefixes.length; ipt++) {
      String tag = FileManager.scriptFilePrefixes[ipt];
      int i = -1;
      while ((i = script.indexOf(tag, i + 1)) >= 0) {
        String s = PT.getQuotedStringAt(script, i);
        if (s.indexOf("::") >= 0)
          s = PT.split(s, "::")[1];
        fileList.addLast(s);
      }
    }
  }

  /**
   * looks at byte 51 for "PNGJxxxxxxxxx+yyyyyyyyy"
   * where xxxxxxxxx is a byte offset to the JMOL data
   * and yyyyyyyyy is the length of the data.
   * 
   * @param bis
   * @return same stream or byte stream
   */
  public static BufferedInputStream checkPngZipStream(BufferedInputStream bis) {
    if (!JmolBinary.isPngZipStream(bis))
      return bis;
    byte[] data = null;
    bis.mark(75);
    try {
      data = JmolBinary.getStreamBytes(bis, 74);
      bis.reset();
      int pt = 0;
      for (int i = 64, f = 1; --i > 54; f *= 10)
        pt += (data[i] - '0') * f;
      int n = 0;
      for (int i = 74, f = 1; --i > 64; f *= 10)
        n += (data[i] - '0') * f;
      while (pt > 0)
        pt -= bis.skip(pt);
      data = JmolBinary.getStreamBytes(bis, n);
      bis.close();
    } catch (Throwable e) {
      data = new byte[0];
    }
    return getBIS(data);
  }

  /**
   * @param bis
   * @param charSet TODO
   * @return Reader
   * @throws IOException
   */
  public static BufferedReader getBufferedReader(BufferedInputStream bis, String charSet)
      throws IOException {
    // could also just make sure we have a buffered input stream here.
    if (getUTFEncodingForStream(bis) == Encoding.NONE)
      return new BufferedReader(new InputStreamReader(bis, (charSet == null ? "UTF-8" : charSet)));
    byte[] bytes = getStreamBytes(bis, -1);
    bis.close();
    return getBR(charSet == null ? fixUTF(bytes) : new String(bytes, charSet));
  }

  /**
   * check a JmolManifest for a reference to a script file (.spt)
   * 
   * @param manifest
   * @return null, "", or a directory entry in the ZIP file
   */

  public static String getManifestScriptPath(String manifest) {
    if (manifest.indexOf("$SCRIPT_PATH$") >= 0)
      return "";
    String ch = (manifest.indexOf('\n') >= 0 ? "\n" : "\r");
    if (manifest.indexOf(".spt") >= 0) {
      String[] s = PT.split(manifest, ch);
      for (int i = s.length; --i >= 0;)
        if (s[i].indexOf(".spt") >= 0)
          return "|" + PT.trim(s[i], "\r\n \t");
    }
    return null;
  }

  public static String getBinaryType(String name) {
    if (name == null)
      return null;
    int i = name.lastIndexOf(".");
    if (i < 0 || (i = JC.binaryExtensions.indexOf(";" + name.substring(i + 1) + "=")) < 0)
        return null;
    i = JC.binaryExtensions.indexOf("=", i);
    name = JC.binaryExtensions.substring(i + 1);
    return name.substring(0, name.indexOf(";"));
  }

  public static boolean checkBinaryType(String fileTypeIn) {
    return (JC.binaryExtensions.indexOf("=" + fileTypeIn + ";") >= 0);
  }

  public static String StreamToString(BufferedInputStream bis) {
    String[] data = new String[1];
    try {
      readAll(getBufferedReader(bis, "UTF-8"), -1, true, data, 0);
    } catch (IOException e) {
    }
    return data[0];
  }

  public static boolean readAll(BufferedReader br, int nBytesMax, boolean allowBinary, String[] data, int i) {
    try {
      SB sb = SB.newN(8192);
      String line;
      if (nBytesMax < 0) {
        line = br.readLine();
        if (allowBinary || line != null && line.indexOf('\0') < 0
            && (line.length() != 4 || line.charAt(0) != 65533
            || line.indexOf("PNG") != 1)) {
          sb.append(line).appendC('\n');
          while ((line = br.readLine()) != null)
            sb.append(line).appendC('\n');
        }
      } else {
        int n = 0;
        int len;
        while (n < nBytesMax && (line = br.readLine()) != null) {
          if (nBytesMax - n < (len = line.length()) + 1)
            line = line.substring(0, nBytesMax - n - 1);
          sb.append(line).appendC('\n');
          n += len + 1;
        }
      }
      br.close();
      data[i] = sb.toString();
      return true;
    } catch (Exception ioe) {
      data[i] = ioe.toString();
      return false;
    }
  }

  public static void addZipEntry(Object zos, String fileName) throws IOException {
    getJzu().addZipEntry(zos, fileName);    
  }

  public static void closeZipEntry(Object zos) throws IOException {
    getJzu().closeZipEntry(zos);
  }

  public static Object getZipOutputStream(Object bos) {
    return getJzu().getZipOutputStream(bos);
  }

  public static int getCrcValue(byte[] bytes) {
    return getJzu().getCrcValue(bytes);
  }

  public static BufferedReader getBufferedReaderForResource(Viewer viewer, 
                                                            Object resourceClass,
                                              String classPath,
                                              String resourceName)
       throws IOException {

     /**
      * @j2sNative
      * 
      *            resourceName = viewer.viewerOptions.get("codePath") +
      *            classPath + resourceName;
      * 
      */
     {
       URL url = resourceClass.getClass().getResource(resourceName);
       boolean ret = true;
       if (url == null) {
         System.err.println("Couldn't find file: " + classPath + resourceName);
         throw new IOException();
       }
       if (ret) // avoids dead code message
         return JmolBinary.getBufferedReader(new BufferedInputStream(
             (InputStream) url.getContent()), null);
     }
     // JavaScript only; here and not in JavaDoc to preserve Eclipse search reference
     return (BufferedReader) viewer.getBufferedReaderOrErrorMessageFromName(
         resourceName, new String[] { null, null }, false);
   }

  public static BufferedInputStream getUnzippedInputStream(BufferedInputStream bis) throws IOException {
    while (isGzipS(bis))
      bis = new BufferedInputStream(newGZIPInputStream(bis));
    return bis;
  }
}

