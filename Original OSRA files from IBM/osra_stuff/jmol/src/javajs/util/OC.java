package javajs.util;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;


import javajs.api.BytePoster;

/**
 * 
 * A generic output method. JmolOutputChannel can be used to:
 * 
 * add characters to a StringBuffer 
 *   using fileName==null, append() and toString()
 *   
 * add bytes utilizing ByteArrayOutputStream 
 *   using writeBytes(), writeByteAsInt(), append()*, and bytesAsArray()
 *       *append() can be used as long as os==ByteArrayOutputStream
 *        or it is not used before one of the writeByte methods. 
 * 
 * output characters to a FileOutputStream 
 *  using os==FileOutputStream, asWriter==true, append(), and closeChannel()
 *  
 * output bytes to a FileOutputStream 
 *  using os==FileOutputStream, writeBytes(), writeByteAsInt(), append(), and closeChannel()
 * 
 * post characters or bytes to a remote server
 *  using fileName=="http://..." or "https://...",
 *    writeBytes(), writeByteAsInt(), append(), and closeChannel()
 *    
 * send characters or bytes to a JavaScript function
 *  when JavaScript and (typeof fileName == "function")
 *  
 *  
 *  @author hansonr  Bob Hanson hansonr@stolaf.edu  9/2013
 *  
 *  
 */

public class OC extends OutputStream {
 
  private BytePoster bytePoster; // only necessary for writing to http:// or https://
  private String fileName;
  private BufferedWriter bw;
  private boolean isLocalFile;
  private int byteCount;
  private boolean isCanceled;
  private boolean closed;
  private OutputStream os;
  private SB sb;
  private String type;
  
  public OC setParams(BytePoster bytePoster, String fileName,
                                     boolean asWriter, OutputStream os) {
    this.bytePoster = bytePoster;
    this.fileName = fileName;
    this.os = os;
    isLocalFile = (fileName != null && !(fileName.startsWith("http://") || fileName
        .startsWith("https://")));
    if (asWriter && os != null)
      bw = new BufferedWriter(new OutputStreamWriter(os));
    return this;
  }

  public String getFileName() {
    return fileName;
  }
  
  public int getByteCount() {
    return byteCount;
  }

  /**
   * 
   * @param type  user-identified type (PNG, JPG, etc)
   */
  public void setType(String type) {
    this.type = type;
  }
  
  public String getType() {
    return type;
  }

  /**
   * will go to string buffer if bw == null and os == null
   * 
   * @param s
   * @return this, for chaining like a standard StringBuffer
   * 
   */
  public OC append(String s) {
    try {
      if (bw != null) {
        bw.write(s);
      } else if (os == null) {
        if (sb == null)
          sb = new SB();
        sb.append(s);
      } else {
        byte[] b = s.getBytes();
        os.write(b, 0, b.length);
        byteCount += b.length;
        return this;
      }
    } catch (IOException e) {
      // ignore
    }
    byteCount += s.length(); // not necessarily exactly correct if unicode
    return this;
  }

  /**
   * @j2sOverride
   */
  @Override
  public void write(byte[] buf, int i, int len) {
    if (os == null)
      os = new ByteArrayOutputStream();
    /**
     * @j2sNative
     * 
     *            this.os.write(buf, i, len);
     * 
     */
    {
      try {
        os.write(buf, i, len);
      } catch (IOException e) {
      }
    }
    byteCount += len;
  }
  
  /**
   * @param b  
   */
  public void writeByteAsInt(int b) {
    if (os == null)
      os = new ByteArrayOutputStream();
    /**
     * @j2sNative
     * 
     *  this.os.writeByteAsInt(b);
     * 
     */
    {
      try {
        os.write(b);
      } catch (IOException e) {
      }
    }
    byteCount++;
  }
  
  /**
   * Will break JavaScript if used.
   * 
   * @j2sIgnore
   * 
   * @param b
   */
  @Override
  @Deprecated
  public void write(int b) {
    // required by standard ZipOutputStream -- do not use, as it will break JavaScript methods
    if (os == null)
      os = new ByteArrayOutputStream();
    try {
      os.write(b);
    } catch (IOException e) {
    }
    byteCount++;
  }

//  /**
//   * Will break if used; no equivalent in JavaScript.
//   * 
//   * @j2sIgnore
//   * 
//   * @param b
//   */
//  @Override
//  @Deprecated
//  public void write(byte[] b) {
//    // not used in JavaScript due to overloading problem there
//    write(b, 0, b.length);
//  }

  public void cancel() {
    isCanceled = true;
    closeChannel();
  }

  public String closeChannel() {
    if (closed)
      return null;
    // can't cancel file writers
    try {
      if (bw != null) {
        bw.flush();
        bw.close();
      } else if (os != null) {
        os.flush();
        os.close();
      }
    } catch (Exception e) {
      // ignore closing issues
    }
    if (isCanceled) {
      closed = true;
      return null;
    }
    if (fileName == null || closed)
      return (sb == null ? null : sb.toString());
    closed = true;
    /**
     * @j2sNative
     * 
     *            var data = (this.sb == null ? this.toByteArray() :
     *            this.sb.toString()); if (typeof this.fileName == "function") {
     *            this.fileName(data); } else { Jmol._doAjax(this.fileName,
     *            null, data); }
     *            
     * 
     */
    {
      if (!isLocalFile) // unsigned applet could do this
        return postByteArray();
    }
    return null;
  }

  public byte[] toByteArray() {
    return (os instanceof ByteArrayOutputStream ? ((ByteArrayOutputStream)os).toByteArray() : null);
  }

  @Override
  @Deprecated
  public void close() {
    closeChannel();
  }

  @Override
  public String toString() {
    if (bw != null)
      try {
        bw.flush();
      } catch (IOException e) {
        // TODO
      }
    if (sb != null)
      return closeChannel();
    return byteCount + " bytes";
  }

  private String postByteArray() {
    byte[] bytes = (sb == null ? toByteArray() : sb.toString().getBytes());
    return bytePoster.postByteArray(fileName, bytes);
  }
}
