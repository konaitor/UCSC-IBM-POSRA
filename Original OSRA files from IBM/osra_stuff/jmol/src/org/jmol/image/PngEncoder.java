/* $RCSfile$
 * $Author: nicove $
 * $Date: 2007-03-30 12:26:16 -0500 (Fri, 30 Mar 2007) $
 * $Revision: 7275 $
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
package org.jmol.image;

import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * 
 * Modified by Bob Hanson hansonr@stolaf.edu to be a subclass of ImageEncoder
 * and to use javajs.util.OutputChannel instead of just returning bytes. Also includes: 
 *  
 * -- JavaScript-compatible image processing
 *  
 * -- transparent background option
 *  
 * -- more efficient calculation of needs for pngBytes 
 * 
 * -- PNGJ format:
 * 
 * // IHDR chunk 
 * 
 * // iTXt chunk "Jmol type - <PNG0|PNGJ><0000000pt>+<000000len>" 
 * 
 * // iTXt chunk "Software - Jmol <version>"
 * 
 * // iTXt chunk "Creation Time - <date>"
 * 
 * // tRNS chunk transparent color, if desired
 *
 * // IDAT chunk (image data)
 * 
 * // IEND chunk 
 * 
 * // [JMOL ZIP FILE APPENDIX]
 * 
 * Original Comment:
 * 
 * PngEncoder takes a Java Image object and creates a byte string which can be
 * saved as a PNG file. The Image is presumed to use the DirectColorModel.
 * 
 * Thanks to Jay Denny at KeyPoint Software http://www.keypoint.com/ who let me
 * develop this code on company time.
 * 
 * You may contact me with (probably very-much-needed) improvements, comments,
 * and bug fixes at:
 * 
 * david@catcode.com
 * 
 * @author J. David Eisenberg
 * @author http://catcode.com/pngencoder/
 * @author Christian Ribeaud (christian.ribeaud@genedata.com)
 * @author Bob Hanson (hansonr@stolaf.edu)
 * 
 * @version 1.4, 31 March 2000
 */
public class PngEncoder extends CRCEncoder {

  /** Constants for filters */
  public static final int FILTER_NONE = 0;
  public static final int FILTER_SUB = 1;
  public static final int FILTER_UP = 2;
  public static final int FILTER_LAST = 2;

  private boolean encodeAlpha;
  private int filter = FILTER_NONE;
  private int bytesPerPixel;
  private int compressionLevel;
  private String type;
  private Integer transparentColor;

  private byte[] applicationData;
  private String applicationPrefix;
  private String version;

  
  public PngEncoder() {
    super();
  }

  @Override
  protected void setParams(Map<String, Object> params) {
    if (quality < 0)
      quality = 2;
    else if (quality > 9)
      quality = 9;
    encodeAlpha = false;
    filter = FILTER_NONE;
    compressionLevel = quality;
    transparentColor = (Integer) params.get("transparentColor");
    
    type = (params.get("type") + "0000").substring(0, 4);
    version = (String) params.get("comment");
    applicationData = (byte[]) params.get("applicationData");
    applicationPrefix = (String) params.get("applicationPrefix");
  }

  

  @Override
  protected void generate() throws IOException {
    int[] ptJmol = new int[1];
    if (!pngEncode(ptJmol)) {
      out.cancel();
      return;
    }
    byte[] bytes = getBytes();
    int len = dataLen;
    if (applicationData != null) {
      setJmolTypeText(applicationPrefix, ptJmol[0], bytes, len, applicationData.length,
          type);
      out.write(bytes, 0, len);
      len = (bytes = applicationData).length;
    }
    out.write(bytes, 0, len);
  }


  /**
   * Creates an array of bytes that is the PNG equivalent of the current image,
   * specifying whether to encode alpha or not.
   * 
   * @param ptAppTag
   * @return        true if successful
   * 
   */
  private boolean pngEncode(int[] ptAppTag) {

    byte[] pngIdBytes = { -119, 80, 78, 71, 13, 10, 26, 10 };

    writeBytes(pngIdBytes);
    //hdrPos = bytePos;
    writeHeader();
    ptAppTag[0] = bytePos + 4;
    writeText(getApplicationText(applicationPrefix, type, 0, 0));

    writeText("Software\0Jmol " + version);
    writeText("Creation Time\0" + date);

    if (!encodeAlpha && transparentColor != null)
      writeTransparentColor(transparentColor.intValue());
    //dataPos = bytePos;
    return writeImageData();
  }

  /**
   * Fill in the Jmol type text area with number of bytes of PNG data and number
   * of bytes of Jmol state data and fix checksum.
   * 
   * If we do not do this, then the checksum will be wrong, and Jmol and some
   * other programs may not be able to read the PNG image.
   * 
   * This was corrected for Jmol 12.3.30. Between 12.3.7 and 12.3.29, PNG files
   * created by Jmol have incorrect checksums.
   * 
   * @param prefix 
   * @param ptJmolByteText 
   * 
   * @param b
   * @param nPNG
   * @param nState
   * @param type
   */
  private static void setJmolTypeText(String prefix, int ptJmolByteText, byte[] b, int nPNG, int nState, String type) {
    String s = "iTXt" + getApplicationText(prefix, type, nPNG, nState);
    CRCEncoder encoder = new PngEncoder();
    encoder.setData(b, ptJmolByteText);
    encoder.writeString(s);
    encoder.writeCRC();
  }

  private static String getApplicationText(String prefix, String type, int nPNG, int nState) {
    String sPNG = "000000000" + nPNG;
    sPNG = sPNG.substring(sPNG.length() - 9);
    String sState = "000000000" + nState;
    sState = sState.substring(sState.length() - 9);
    return prefix + "\0" + type + (type.equals("PNG") ? "0" : "") + sPNG + "+"
        + sState;
  }

  //  /**
  //   * Set the filter to use
  //   *
  //   * @param whichFilter from constant list
  //   */
  //  public void setFilter(int whichFilter) {
  //    this.filter = (whichFilter <= FILTER_LAST ? whichFilter : FILTER_NONE);
  //  }

  //  /**
  //   * Retrieve filtering scheme
  //   *
  //   * @return int (see constant list)
  //   */
  //  public int getFilter() {
  //    return filter;
  //  }

  //  /**
  //   * Set the compression level to use
  //   *
  //   * @param level 0 through 9
  //   */
  //  public void setCompressionLevel(int level) {
  //    if ((level >= 0) && (level <= 9)) {
  //      this.compressionLevel = level;
  //    }
  //  }

  //  /**
  //   * Retrieve compression level
  //   *
  //   * @return int in range 0-9
  //   */
  //  public int getCompressionLevel() {
  //    return compressionLevel;
  //  }

  /**
   * Write a PNG "IHDR" chunk into the pngBytes array.
   */
  private void writeHeader() {

    writeInt4(13);
    startPos = bytePos;
    writeString("IHDR");
    writeInt4(width);
    writeInt4(height);
    writeByte(8); // bit depth
    writeByte(encodeAlpha ? 6 : 2); // color type or direct model
    writeByte(0); // compression method
    writeByte(0); // filter method
    writeByte(0); // no interlace
    writeCRC();
  }

  private void writeText(String msg) {
    writeInt4(msg.length());
    startPos = bytePos;
    writeString("iTXt" + msg);
    writeCRC();
  }

  /**
   * Write a PNG "tRNS" chunk into the pngBytes array.
   * 
   * @param icolor
   */
  private void writeTransparentColor(int icolor) {

    writeInt4(6);
    startPos = bytePos;
    writeString("tRNS");
    writeInt2((icolor >> 16) & 0xFF);
    writeInt2((icolor >> 8) & 0xFF);
    writeInt2(icolor & 0xFF);
    writeCRC();
  }

  private byte[] scanLines; // the scan lines to be compressed
  private int byteWidth; // width * bytesPerPixel

  //private int hdrPos, dataPos, endPos;
  //private byte[] priorRow;
  //private byte[] leftBytes;


  /**
   * Write the image data into the pngBytes array. This will write one or more
   * PNG "IDAT" chunks. In order to conserve memory, this method grabs as many
   * rows as will fit into 32K bytes, or the whole image; whichever is less.
   * 
   * 
   * @return true if no errors; false if error grabbing pixels
   */
  private boolean writeImageData() {

    bytesPerPixel = (encodeAlpha ? 4 : 3);
    byteWidth = width * bytesPerPixel;

    int scanWidth = byteWidth + 1; // the added 1 is for the filter byte

    //boolean doFilter = (filter != FILTER_NONE);

    int rowsLeft = height; // number of rows remaining to write
    //int startRow = 0; // starting row to process this time through
    int nRows; // how many rows to grab at a time

    int scanPos; // where we are in the scan lines

    Deflater deflater = new Deflater(compressionLevel);
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream(1024);

    DeflaterOutputStream compBytes = new DeflaterOutputStream(outBytes,
        deflater);

    int pt = 0; // overall image byte pointer
    
    // Jmol note: The entire image has been stored in pixels[] already
    
    try {
      while (rowsLeft > 0) {
        nRows = Math.max(1, Math.min(32767 / scanWidth, rowsLeft));
        scanLines = new byte[scanWidth * nRows];
        //        if (doFilter)
        //          switch (filter) {
        //          case FILTER_SUB:
        //            leftBytes = new byte[16];
        //            break;
        //          case FILTER_UP:
        //            priorRow = new byte[scanWidth - 1];
        //            break;
        //          }
        int nPixels = width * nRows;
        scanPos = 0;
        //startPos = 1;
        for (int i = 0; i < nPixels; i++, pt++) {
          if (i % width == 0) {
            scanLines[scanPos++] = (byte) filter;
            //startPos = scanPos;
          }
          scanLines[scanPos++] = (byte) ((pixels[pt] >> 16) & 0xff);
          scanLines[scanPos++] = (byte) ((pixels[pt] >> 8) & 0xff);
          scanLines[scanPos++] = (byte) ((pixels[pt]) & 0xff);
          if (encodeAlpha) {
            scanLines[scanPos++] = (byte) ((pixels[pt] >> 24) & 0xff);
          }
          //          if (doFilter && i % width == width - 1) {
          //            switch (filter) {
          //            case FILTER_SUB:
          //              filterSub();
          //              break;
          //            case FILTER_UP:
          //              filterUp();
          //              break;
          //            }
          //          }
        }

        /*
         * Write these lines to the output area
         */
        compBytes.write(scanLines, 0, scanPos);

        //startRow += nRows;
        rowsLeft -= nRows;
      }
      compBytes.close();

      /*
       * Write the compressed bytes
       */
      byte[] compressedLines = outBytes.toByteArray();
      writeInt4(compressedLines.length);
      startPos = bytePos;
      writeString("IDAT");
      writeBytes(compressedLines);
      writeCRC();
      writeEnd();
      deflater.finish();
      return true;
    } catch (IOException e) {
      System.err.println(e.toString());
      return false;
    }
  }

  /**
   * Write a PNG "IEND" chunk into the pngBytes array.
   */
  private void writeEnd() {
    writeInt4(0);
    startPos = bytePos;
    writeString("IEND");
    writeCRC();
  }

  ///**
  //* Perform "sub" filtering on the given row.
  //* Uses temporary array leftBytes to store the original values
  //* of the previous pixels.  The array is 16 bytes long, which
  //* will easily hold two-byte samples plus two-byte alpha.
  //*
  //*/
  //private void filterSub() {
  // int offset = bytesPerPixel;
  // int actualStart = startPos + offset;
  // int leftInsert = offset;
  // int leftExtract = 0;
  // //byte current_byte;
  //
  // for (int i = actualStart; i < startPos + byteWidth; i++) {
  //   leftBytes[leftInsert] = scanLines[i];
  //   scanLines[i] = (byte) ((scanLines[i] - leftBytes[leftExtract]) % 256);
  //   leftInsert = (leftInsert + 1) % 0x0f;
  //   leftExtract = (leftExtract + 1) % 0x0f;
  // }
  //}
  //
  ///**
  //* Perform "up" filtering on the given row. Side effect: refills the prior row
  //* with current row
  //* 
  //*/
  //private void filterUp() {
  // int nBytes = width * bytesPerPixel;
  // for (int i = 0; i < nBytes; i++) {
  //   int pt = startPos + i;
  //   byte b = scanLines[pt];
  //   scanLines[pt] = (byte) ((scanLines[pt] - priorRow[i]) % 256);
  //   priorRow[i] = b;
  // }
  //}

}
