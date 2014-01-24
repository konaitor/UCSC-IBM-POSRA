/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-06-02 12:14:13 -0500 (Sat, 02 Jun 2007) $
 * $Revision: 7831 $
 *
 * Copyright (C) 2000-2005  The Jmol Development Team
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

//  GifEncoder - write out an image as a GIF
// 
//  Transparency handling and variable bit size courtesy of Jack Palevich.
//  
//  Copyright (C)1996,1998 by Jef Poskanzer <jef@mail.acme.com>. All rights reserved.
//  
//  Redistribution and use in source and binary forms, with or without
//  modification, are permitted provided that the following conditions
//  are met:
//  1. Redistributions of source code must retain the above copyright
//     notice, this list of conditions and the following disclaimer.
//  2. Redistributions in binary form must reproduce the above copyright
//     notice, this list of conditions and the following disclaimer in the
//     documentation and/or other materials provided with the distribution.
// 
//  THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
//  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
//  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
//  ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
//  FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
//  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
//  OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
//  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
//  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
//  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
//  SUCH DAMAGE.
// 
//  Visit the ACME Labs Java page for up-to-date versions of this and other
//  fine Java utilities: http://www.acme.com/java/
// 
/// Write out an image as a GIF.
// <P>
// <A HREF="/resources/classes/Acme/JPM/Encoders/GifEncoder.java">Fetch the software.</A><BR>
// <A HREF="/resources/classes/Acme.tar.gz">Fetch the entire Acme package.</A>
// <P>
// @see ToGif


package org.jmol.image;

import org.jmol.script.T;
import javajs.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Map;
import java.io.IOException;

import org.jmol.util.Logger;

/**
 * 
 * GifEncoder extensively modified for Jmol by Bob Hanson
 * 
 * -- much simplified interface with ImageEncoder
 * 
 * -- uses simple Hashtable with Integer()
 * 
 * -- adds adaptive color reduction to generate 256 colors
 *      Reduction algorithm simply removes lower bits of red, green, and blue
 *      one at a time until the number of sets is <= 256. Then it creates a
 *      color for each set that is a weighted average of all the colors for that set.
 *      Seems to work reasonably well. Mapped isosurfaces look pretty crude.
 * 
 * -- allows progressive production of animated GIF via Jmol CAPTURE command
 * 
 * -- uses general purpose javajs.util.OutputChannel for byte-handling options
 *    such as posting to a server, writing to disk, and retrieving bytes.
 *    
 * -- allows JavaScript port
 *    
 * -- Bob Hanson, 24 Sep 2013
 *    
 * @author Bob Hanson hansonr@stolaf.edu
 */

public class GifEncoder extends ImageEncoder {

  private Map<Integer, AdaptiveColorCollection> colorMap;
  protected int[] red, green, blue;

  private class ColorItem {

    AdaptiveColorCollection acc;
    int rgb;
    int count;

    ColorItem(int rgb, int count) {
      this.rgb = rgb;
      this.count = count;
    }
  }

  protected class ColorVector extends List<ColorItem> {

    void sort() {
      CountComparator comparator = new CountComparator();
      Collections.sort(this, comparator);
    }

    protected class CountComparator implements Comparator<ColorItem> {
      @Override
      public int compare(ColorItem a, ColorItem b) {
        return (a == null ? 1 : b == null ? -1 : a.count < b.count ? -1
            : a.count > b.count ? 1 : 0);
      }
    }
  }

  private class AdaptiveColorCollection {
    //int rgb;
    protected int index;
    // counts here are counts of color occurances for this grouped set.
    // ints here allow for 2147483647/0x100 = count of 8388607 for THIS average color, which should be fine.
    private int r;
    private int g;
    private int b;
    private int count;

    AdaptiveColorCollection(int rgb, int index) {
      //this.rgb = rgb;
      this.index = index;
      if (rgb >= 0)
        transparentIndex = index;
    }
    void addRgb(int rgb, int count) {
      this.count += count;
      b += (rgb & 0xFF) * count;
      g += ((rgb >> 8) & 0xFF) * count;
      r += ((rgb >> 16) & 0xFF) * count;
    }

    void setRgb() {
      red[index] = (r / count) & 0xff;
      green[index] =(g / count) & 0xff;
      blue[index] = (b / count) & 0xff;
    }
  }

  private boolean interlaced;
  private boolean addHeader = true;
  private boolean addImage = true;
  private boolean addTrailer = true;
  private int delayTime100ths = -1;
  private boolean looping;
  private Map<String, Object> params;
  private int byteCount;

  /**
   * we allow for animated GIF by being able to re-enter
   * the code with different parameters held in params
   * 
   * 
   */
  @Override
  protected void setParams(Map<String, Object> params) {
    this.params = params;
    interlaced = Boolean.TRUE == params.get("interlaced");
    if (interlaced || !params.containsKey("captureMode"))
      return;
    try {
      byteCount = ((Integer) params.get("captureByteCount")).intValue();
    } catch (Exception e) {
      // ignore
    }
    switch (((Integer) params.get("captureMode")).intValue()) {
    case T.movie:
      params.put("captureMode", Integer.valueOf(T.add));
      addImage = false;
      addTrailer = false;
      break;
    case T.add:
      addHeader = false;
      addTrailer = false;
      int fps = Math.abs(((Integer) params.get("captureFps")).intValue());
      delayTime100ths =  (fps == 0 ? 0 : 100 / fps);
      looping = (Boolean.FALSE != params.get("captureLooping"));
      break;
    case T.end:
      addHeader = false;
      addImage = false;
      break;
    case T.cancel:
      addHeader = false;
      addImage = false;
      /**
       * @j2sNative
       * 
       * this.out.cancel();
       *  
       */
      {}
    }
  }


  // Adapted from ppmtogif, which is based on GIFENCOD by David
  // Rowley <mgardi@watdscu.waterloo.edu>.  Lempel-Zim compression
  // based on "compress".

  private int bitsPerPixel = 1;
  protected int transparentIndex = -1;

  @Override
  protected void generate() throws IOException {
    if (addHeader)
      writeHeader();
    addHeader = false; // only one header
    if (addImage) {
      createColorTable();
      writeGraphicControlExtension();
      if (delayTime100ths >= 0 && looping)
        writeNetscapeLoopExtension();
      writeImage();
    }
  }

  @Override
  protected void close() {
    if (addTrailer) {
      writeTrailer();
      super.close();
    }
    params.put("captureByteCount", Integer.valueOf(byteCount));
  }

  /**
   * includes logical screen descriptor
   * @throws IOException
   */
  private void writeHeader() throws IOException {
    putString("GIF89a");
    putWord(width);
    putWord(height);
    putByte(0); // no global color table -- using local instead
    putByte(0); // no background
    putByte(0); // no pixel aspect ratio given
  }

  /**
   * generates a 256-color or fewer color table consisting of a 
   * set of red, green, blue arrays and a hash table pointing to a color index;
   * adapts to situations where more than 256 colors are present.
   * 
   */
  private void createColorTable() {
    ColorVector colors = getColors();
    Map<Integer, AdaptiveColorCollection> colors256 = getBest256(colors);
    int nTotal = colors256.size();
    bitsPerPixel = (nTotal <= 2 ? 1 : nTotal <= 4 ? 2 : nTotal <= 16 ? 4 : 8);
    colorMap = finalizeColorMap(colors, colors256);
  }

  /**
   * Generate a list of all unique colors in the image.
   * 
   * @return the vector
   */
  private ColorVector getColors() {
    ColorVector colorVector = new ColorVector();
    Map<Integer, ColorItem> ciHash = new Hashtable<Integer, ColorItem>();
    int nColors = 0;
    Integer key;
    int ptTransparent = -1;
    
    for (int pt = 0, row = 0, transparentRgb = -1; row < height; ++row) {
      for (int col = 0; col < width; ++col, pt++) {
        int rgb = pixels[pt];
        boolean isTransparent = (rgb >= 0);
        if (isTransparent) {
          if (ptTransparent < 0) {
            // First transparent color; remember it.
            ptTransparent = nColors;
            transparentRgb = rgb;
          } else if (rgb != transparentRgb) {
            // A second transparent color; replace it with
            // the first one.
            pixels[pt] = rgb = transparentRgb;
          }
        }
        ColorItem item = ciHash.get(key = Integer.valueOf(rgb));
        if (item == null) {
          item = new ColorItem(rgb, 1);
          ciHash.put(key, item);
          colorVector.addLast(item);
          nColors++;
        } else {
          item.count++;
        }
      }
    }
    ciHash = null;
   
    if (Logger.debugging)
      Logger.debug("# total image colors = " + nColors);
    // sort by frequency
    colorVector.sort();
    return colorVector;
  }

  /**
   * reduce GIF color collection to 256 or fewer by grouping shadings;
   * create an initial color hash that is only to the final colors.
   * 
   * @param colorVector
   * @return nTotal;
   */
  private Map<Integer, AdaptiveColorCollection> getBest256(ColorVector colorVector) {
    // mask allows reducing colors by shading changes
    int mask = 0x010101;
    int nColors = colorVector.size();
    int nMax = Math.max(nColors - 1, 0); // leave top 1 untouched
    int nTotal = Integer.MAX_VALUE;
    int index = 0;
    Map<Integer, AdaptiveColorCollection> ht = null;
    while (nTotal > 255) {
      nTotal = nColors;
      index = 0;
      ht = new Hashtable<Integer, AdaptiveColorCollection>();
      for (int i = 0; i < nMax; i++) {
        ColorItem item = colorVector.get(i);
        int rgb = (nTotal < 256 ? item.rgb : item.rgb & ~mask);
        Integer key = Integer.valueOf(rgb);
        if ((item.acc = ht.get(key)) == null)
          ht.put(key, item.acc = new AdaptiveColorCollection(rgb, index++));
        else
          nTotal--;
      }
      mask |= (mask <<= 1);
      //if (Logger.debugging)
    }
    ColorItem item = colorVector.get(nMax);
    ht.put(Integer.valueOf(item.rgb),
        item.acc = new AdaptiveColorCollection(item.rgb, index++));
    if (Logger.debugging)
      Logger.debug("# GIF colors = " + ht.size());
    return ht;
  }

  /**
   * Create final color table red green blue arrays and generate final
   * colorHash.
   * 
   * @param colors
   * @param colors256
   * @return map from all unique colors to a specific index
   */
  private Map<Integer, AdaptiveColorCollection> finalizeColorMap(
                                                                 List<ColorItem> colors,
                                                                 Map<Integer, AdaptiveColorCollection> colors256) {
    int mapSize = 1 << bitsPerPixel;
    red = new int[mapSize];
    green = new int[mapSize];
    blue = new int[mapSize];
    int nColors = colors.size();
    Map<Integer, AdaptiveColorCollection> ht = new Hashtable<Integer, AdaptiveColorCollection>();
    for (int i = 0; i < nColors; i++) {
      ColorItem item = colors.get(i);
      int rgb = item.rgb;
      item.acc.addRgb(rgb, item.count);
      ht.put(Integer.valueOf(rgb), item.acc);
    }
    for (AdaptiveColorCollection acc : colors256.values())
      acc.setRgb();
    return ht;
  }

  private void writeGraphicControlExtension() {
    if (transparentIndex != -1 || delayTime100ths >= 0) {
      putByte(0x21); // graphic control extension
      putByte(0xf9); // graphic control label
      putByte(4); // block size
      int packedBytes = (transparentIndex == -1 ? 0 : 1) | (delayTime100ths > 0 ? 2 : 0);
      putByte(packedBytes); 
      putWord(delayTime100ths > 0 ? delayTime100ths : 0);
      putByte(transparentIndex == -1 ? 0 : transparentIndex);
      putByte(0); // end-of-block
    }
  }

// see  http://www.vurdalakov.net/misc/gif/netscape-looping-application-extension
//      +---------------+
//   0  |     0x21      |  Extension Label
//      +---------------+
//   1  |     0xFF      |  Application Extension Label
//      +---------------+
//   2  |     0x0B      |  Block Size
//      +---------------+
//   3  |               | 
//      +-             -+
//   4  |               | 
//      +-             -+
//   5  |               | 
//      +-             -+
//   6  |               | 
//      +-  NETSCAPE   -+  Application Identifier (8 bytes)
//   7  |               | 
//      +-             -+
//   8  |               | 
//      +-             -+
//   9  |               | 
//      +-             -+
//  10  |               | 
//      +---------------+
//  11  |               | 
//      +-             -+
//  12  |      2.0      |  Application Authentication Code (3 bytes)
//      +-             -+
//  13  |               | 
//      +===============+                      --+
//  14  |     0x03      |  Sub-block Data Size   |
//      +---------------+                        |
//  15  |     0x01      |  Sub-block ID          |
//      +---------------+                        | Application Data Sub-block
//  16  |               |                        |
//      +-             -+  Loop Count (2 bytes)  |
//  17  |               |                        |
//      +===============+                      --+
//  18  |     0x00      |  Block Terminator
//      +---------------+

  private void writeNetscapeLoopExtension() {
    putByte(0x21); // graphic control extension
    putByte(0xff); // netscape loop extension
    putByte(0x0B); // block size
    putString("NETSCAPE2.0");
    putByte(3); 
    putByte(1); 
    putWord(0); // loop indefinitely
    putByte(0); // end-of-block
    
  }

  private int initCodeSize;
  private int curpt;

  private void writeImage() {
    putByte(0x2C);
    putWord(0); //left
    putWord(0); //top
    putWord(width);
    putWord(height);

    //    <Packed Fields>  =      LISx xZZZ

    //    L Local Color Table Flag
    //    I Interlace Flag
    //    S Sort Flag
    //    x Reserved
    //    ZZZ Size of Local Color Table

    int packedFields = 0x80 | (interlaced ? 0x40 : 0) | (bitsPerPixel - 1);
    putByte(packedFields);
    int colorMapSize = 1 << bitsPerPixel;
    for (int i = 0; i < colorMapSize; i++) {
      putByte(red[i]);
      putByte(green[i]);
      putByte(blue[i]);
    }
    putByte(initCodeSize = (bitsPerPixel <= 1 ? 2 : bitsPerPixel));
    compress();
    putByte(0);
  }

  private void writeTrailer() {
    // Write the GIF file terminator
    putByte(0x3B);
  }

  ///// compression routines /////
  
  private static final int EOF = -1;

  // Return the next pixel from the image
  private int nextPixel() {
    if (countDown-- == 0)
      return EOF;
    int colorIndex = colorMap.get(Integer.valueOf(pixels[curpt])).index;
    // Bump the current X position
    ++curx;
    if (curx == width) {
      // If we are at the end of a scan line, set curx back to the beginning
      // If we are interlaced, bump the cury to the appropriate spot,
      // otherwise, just increment it.
      curx = 0;
      if (interlaced)
        updateY(INTERLACE_PARAMS[pass], INTERLACE_PARAMS[pass + 4]);
      else
       ++cury;
    }
    curpt = cury * width + curx;
    return colorIndex & 0xff;
  }

  private static final int[] INTERLACE_PARAMS = {
    8, 8, 4, 2, 
    4, 2, 1, 0};

  /**
   * 
   *   Group 1 : Every 8th. row, starting with row 0.              (Pass 1)
   *   
   *   Group 2 : Every 8th. row, starting with row 4.              (Pass 2)
   *   
   *   Group 3 : Every 4th. row, starting with row 2.              (Pass 3)
   *   
   *   Group 4 : Every 2nd. row, starting with row 1.              (Pass 4)
   * 
   * @param yNext
   * @param yNew
   */
  private void updateY(int yNext, int yNew) {
    cury += yNext;
    if (yNew >= 0 && cury >= height) {
      cury = yNew;
      ++pass;
    }
  }

  // Write out a word to the GIF file
  private void putWord(int w) {
    putByte(w);
    putByte(w >> 8);
  }

  // GIFCOMPR.C       - GIF Image compression routines
  //
  // Lempel-Ziv compression based on 'compress'.  GIF modifications by
  // David Rowley (mgardi@watdcsu.waterloo.edu)

  // General DEFINEs

  private static final int BITS = 12;

  private static final int HSIZE = 5003; // 80% occupancy

  // GIF Image compression - modified 'compress'
  //
  // Based on: compress.c - File compression ala IEEE Computer, June 1984.
  //
  // By Authors:  Spencer W. Thomas      (decvax!harpo!utah-cs!utah-gr!thomas)
  //              Jim McKie              (decvax!mcvax!jim)
  //              Steve Davies           (decvax!vax135!petsd!peora!srd)
  //              Ken Turkowski          (decvax!decwrl!turtlevax!ken)
  //              James A. Woods         (decvax!ihnp4!ames!jaw)
  //              Joe Orost              (decvax!vax135!petsd!joe)

  private int nBits; // number of bits/code
  private int maxbits = BITS; // user settable max # bits/code
  private int maxcode; // maximum code, given n_bits
  private int maxmaxcode = 1 << BITS; // should NEVER generate this code

  private final static int MAXCODE(int nBits) {
    return (1 << nBits) - 1;
  }

  private int[] htab = new int[HSIZE];
  private int[] codetab = new int[HSIZE];

  private int hsize = HSIZE; // for dynamic table sizing

  private int freeEnt = 0; // first unused entry

  // block compression parameters -- after all codes are used up,
  // and compression rate changes, start over.
  private boolean clearFlag = false;

  // Algorithm:  use open addressing double hashing (no chaining) on the
  // prefix code / next character combination.  We do a variant of Knuth's
  // algorithm D (vol. 3, sec. 6.4) along with G. Knott's relatively-prime
  // secondary probe.  Here, the modular division first probe is gives way
  // to a faster exclusive-or manipulation.  Also do block compression with
  // an adaptive reset, whereby the code table is cleared when the compression
  // ratio decreases, but after the table fills.  The variable-length output
  // codes are re-sized at this point, and a special CLEAR code is generated
  // for the decompressor.  Late addition:  construct the table according to
  // file size for noticeable speed improvement on small files.  Please direct
  // questions about this implementation to ames!jaw.

  private int clearCode;
  private int EOFCode;

  private int countDown;
  private int pass = 0;
  private int curx, cury;

  private void compress() {

    // Calculate number of bits we are expecting
    countDown = width * height;

    // Indicate which pass we are on (if interlace)
    pass = 0;
    // Set up the current x and y position
    curx = 0;
    cury = 0;

    // Set up the necessary values
    clearFlag = false;
    nBits = initCodeSize + 1;
    maxcode = MAXCODE(nBits);

    clearCode = 1 << initCodeSize;
    EOFCode = clearCode + 1;
    freeEnt = clearCode + 2;

    // Set up the 'byte output' routine
    bufPt = 0;

    int ent = nextPixel();

    int hshift = 0;
    int fcode;
    for (fcode = hsize; fcode < 65536; fcode *= 2)
      ++hshift;
    hshift = 8 - hshift; // set hash code range bound

    int hsizeReg = hsize;
    clearHash(hsizeReg); // clear hash table

    output(clearCode);

    int c;
    outer_loop: while ((c = nextPixel()) != EOF) {
      fcode = (c << maxbits) + ent;
      int i = (c << hshift) ^ ent; // xor hashing

      if (htab[i] == fcode) {
        ent = codetab[i];
        continue;
      } else if (htab[i] >= 0) // non-empty slot
      {
        int disp = hsizeReg - i; // secondary hash (after G. Knott)
        if (i == 0)
          disp = 1;
        do {
          if ((i -= disp) < 0)
            i += hsizeReg;

          if (htab[i] == fcode) {
            ent = codetab[i];
            continue outer_loop;
          }
        } while (htab[i] >= 0);
      }
      output(ent);
      ent = c;
      if (freeEnt < maxmaxcode) {
        codetab[i] = freeEnt++; // code -> hashtable
        htab[i] = fcode;
      } else {
        clearBlock();
      }
    }
    // Put out the final code.
    output(ent);
    output(EOFCode);
  }

  // output
  //
  // Output the given code.
  // Inputs:
  //      code:   A n_bits-bit integer.  If == -1, then EOF.  This assumes
  //              that n_bits =< wordsize - 1.
  // Outputs:
  //      Outputs code to the file.
  // Assumptions:
  //      Chars are 8 bits long.
  // Algorithm:
  //      Maintain a BITS character long buffer (so that 8 codes will
  // fit in it exactly).  Use the VAX insv instruction to insert each
  // code in turn.  When the buffer fills up empty it and start over.

  private int curAccum = 0;
  private int curBits = 0;

  private int masks[] = { 0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F,
      0x003F, 0x007F, 0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF,
      0x7FFF, 0xFFFF };

  private void output(int code) {
    curAccum &= masks[curBits];

    if (curBits > 0)
      curAccum |= (code << curBits);
    else
      curAccum = code;

    curBits += nBits;

    while (curBits >= 8) {
      byteOut((byte) (curAccum & 0xff));
      curAccum >>= 8;
      curBits -= 8;
    }

    // If the next entry is going to be too big for the code size,
    // then increase it, if possible.
    if (freeEnt > maxcode || clearFlag) {
      if (clearFlag) {
        maxcode = MAXCODE(nBits = initCodeSize + 1);
        clearFlag = false;
      } else {
        ++nBits;
        if (nBits == maxbits)
          maxcode = maxmaxcode;
        else
          maxcode = MAXCODE(nBits);
      }
    }

    if (code == EOFCode) {
      // At EOF, write the rest of the buffer.
      while (curBits > 0) {
        byteOut((byte) (curAccum & 0xff));
        curAccum >>= 8;
        curBits -= 8;
      }
      flushBytes();
    }
  }

  // Clear out the hash table

  // table clear for block compress
  private void clearBlock() {
    clearHash(hsize);
    freeEnt = clearCode + 2;
    clearFlag = true;

    output(clearCode);
  }

  // reset code table
  private void clearHash(int hsize) {
    for (int i = 0; i < hsize; ++i)
      htab[i] = -1;
  }

  // GIF-specific routines (byte array buffer)

  // Number of bytes so far in this 'packet'
  private int bufPt;

  // Define the storage for the packet accumulator
  final private byte[] buf = new byte[256];

  // Add a byte to the end of the current packet, and if it is 254
  // byte, flush the packet to disk.
  private void byteOut(byte c) {
    buf[bufPt++] = c;
    if (bufPt >= 254)
      flushBytes();
  }

  // Flush the packet to disk, and reset the accumulator
  protected void flushBytes() {
    if (bufPt > 0) {
      putByte(bufPt);
      out.write(buf, 0, bufPt);
      byteCount += bufPt;
      bufPt = 0;
    }
  }
  
}
