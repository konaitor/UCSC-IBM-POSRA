/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2006  The Jmol Development Team
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 *  02110-1301, USA.
 */

package org.jmol.util;

import java.util.List;

import javajs.util.PT;
import javajs.util.SB;
import javajs.util.T3;
import javajs.util.T4;

public class Txt {

  public static String formatStringS(String strFormat, String key, String strT) {
    return formatString(strFormat, key, strT, Float.NaN, Double.NaN, false);
  }

  public static String formatStringF(String strFormat, String key, float floatT) {
    return formatString(strFormat, key, null, floatT, Double.NaN, false);
  }

  public static String formatStringI(String strFormat, String key, int intT) {
    return formatString(strFormat, key, "" + intT, Float.NaN, Double.NaN, false);
  }
   
  /**
   * sprintf emulation uses (almost) c++ standard string formats 's' string 'i'
   * or 'd' integer 'f' float/decimal 'p' point3f 'q' quaternion/plane/axisangle
   * ' with added "i" in addition to the insipid "d" (digits?)
   * 
   * @param strFormat
   * @param list
   * @param values
   * @return formatted string
   */
  public static String sprintf(String strFormat, String list, Object[] values) {
    if (values == null)
      return strFormat;
    int n = list.length();
    if (n == values.length)
      try {
        for (int o = 0; o < n; o++) {
          if (values[o] == null)
            continue;
          switch (list.charAt(o)) {
          case 's':
            strFormat = formatString(strFormat, "s", (String) values[o],
                Float.NaN, Double.NaN, true);
            break;
          case 'f':
            strFormat = formatString(strFormat, "f", null, ((Float) values[o])
                .floatValue(), Double.NaN, true);
            break;
          case 'i':
            strFormat = formatString(strFormat, "d", "" + values[o], Float.NaN,
                Double.NaN, true);
            strFormat = formatString(strFormat, "i", "" + values[o], Float.NaN,
                Double.NaN, true);
            break;
          case 'd':
            strFormat = formatString(strFormat, "e", null, Float.NaN,
                ((Double) values[o]).doubleValue(), true);
            break;
          case 'p':
            T3 pVal = (T3) values[o];
            strFormat = formatString(strFormat, "p", null, pVal.x, Double.NaN,
                true);
            strFormat = formatString(strFormat, "p", null, pVal.y, Double.NaN,
                true);
            strFormat = formatString(strFormat, "p", null, pVal.z, Double.NaN,
                true);
            break;
          case 'q':
            T4 qVal = (T4) values[o];
            strFormat = formatString(strFormat, "q", null, qVal.x, Double.NaN,
                true);
            strFormat = formatString(strFormat, "q", null, qVal.y, Double.NaN,
                true);
            strFormat = formatString(strFormat, "q", null, qVal.z, Double.NaN,
                true);
            strFormat = formatString(strFormat, "q", null, qVal.w, Double.NaN,
                true);
            break;
          case 'S':
            String[] sVal = (String[]) values[o];
            for (int i = 0; i < sVal.length; i++)
              strFormat = formatString(strFormat, "s", sVal[i], Float.NaN,
                  Double.NaN, true);
            break;
          case 'F':
            float[] fVal = (float[]) values[o];
            for (int i = 0; i < fVal.length; i++)
              strFormat = formatString(strFormat, "f", null, fVal[i],
                  Double.NaN, true);
            break;
          case 'I':
            int[] iVal = (int[]) values[o];
            for (int i = 0; i < iVal.length; i++)
              strFormat = formatString(strFormat, "d", "" + iVal[i], Float.NaN,
                  Double.NaN, true);
            for (int i = 0; i < iVal.length; i++)
              strFormat = formatString(strFormat, "i", "" + iVal[i], Float.NaN,
                  Double.NaN, true);
            break;
          case 'D':
            double[] dVal = (double[]) values[o];
            for (int i = 0; i < dVal.length; i++)
              strFormat = formatString(strFormat, "e", null, Float.NaN,
                  dVal[i], true);
          }

        }
        return PT.simpleReplace(strFormat, "%%", "%");
      } catch (Exception e) {
        //
      }
    System.out.println("TextFormat.sprintf error " + list + " " + strFormat);
    return PT.simpleReplace(strFormat, "%", "?");
  }

  /**
   * generic string formatter  based on formatLabel in Atom
   * 
   * 
   * @param strFormat   .... %width.precisionKEY....
   * @param key      any string to match
   * @param strT     replacement string or null
   * @param floatT   replacement float or Float.NaN
   * @param doubleT  replacement double or Double.NaN -- for exponential
   * @param doOne    mimic sprintf    
   * @return         formatted string
   */

  private static String formatString(String strFormat, String key, String strT,
                                    float floatT, double doubleT, boolean doOne) {
    if (strFormat == null)
      return null;
    if ("".equals(strFormat))
      return "";
    int len = key.length();
    if (strFormat.indexOf("%") < 0 || len == 0 || strFormat.indexOf(key) < 0)
      return strFormat;

    String strLabel = "";
    int ich, ichPercent, ichKey;
    for (ich = 0; (ichPercent = strFormat.indexOf('%', ich)) >= 0
        && (ichKey = strFormat.indexOf(key, ichPercent + 1)) >= 0;) {
      if (ich != ichPercent)
        strLabel += strFormat.substring(ich, ichPercent);
      ich = ichPercent + 1;
      if (ichKey > ichPercent + 6) {
        strLabel += '%';
        continue;//%12.10x
      }
      try {
        boolean alignLeft = false;
        if (strFormat.charAt(ich) == '-') {
          alignLeft = true;
          ++ich;
        }
        boolean zeroPad = false;
        if (strFormat.charAt(ich) == '0') {
          zeroPad = true;
          ++ich;
        }
        char ch;
        int width = 0;
        while ((ch = strFormat.charAt(ich)) >= '0' && (ch <= '9')) {
          width = (10 * width) + (ch - '0');
          ++ich;
        }
        int precision = Integer.MAX_VALUE;
        boolean isExponential = false;
        if (strFormat.charAt(ich) == '.') {
          ++ich;
          if ((ch = strFormat.charAt(ich)) == '-') {
            isExponential = true;
            ++ich;
          } 
          if ((ch = strFormat.charAt(ich)) >= '0' && ch <= '9') {
            precision = ch - '0';
            ++ich;
          }
          if (isExponential)
            precision = -precision - (strT == null ? 1 : 0);
        }
        String st = strFormat.substring(ich, ich + len);
        if (!st.equals(key)) {
          ich = ichPercent + 1;
          strLabel += '%';
          continue;
        }
        ich += len;
        if (!Float.isNaN(floatT))
          strLabel += PT.formatF(floatT, width, precision, alignLeft,
              zeroPad);
        else if (strT != null)
          strLabel += PT.formatS(strT, width, precision, alignLeft,
              zeroPad);
        else if (!Double.isNaN(doubleT))
          strLabel += PT.formatD(doubleT, width, precision, alignLeft,
              zeroPad, true);
        if (doOne)
          break;
      } catch (IndexOutOfBoundsException ioobe) {
        ich = ichPercent;
        break;
      }
    }
    strLabel += strFormat.substring(ich);
    //if (strLabel.length() == 0)
      //return null;
    return strLabel;
  }

  /**
   * 
   * formatCheck   checks p and q formats and duplicates if necessary
   *               "%10.5p xxxx" ==> "%10.5p%10.5p%10.5p xxxx" 
   * 
   * @param strFormat
   * @return    f or dupicated format
   */
  public static String formatCheck(String strFormat) {
    if (strFormat == null || strFormat.indexOf('p') < 0 && strFormat.indexOf('q') < 0)
      return strFormat;
    strFormat = PT.simpleReplace(strFormat, "%%", "\1");
    strFormat = PT.simpleReplace(strFormat, "%p", "%6.2p");
    strFormat = PT.simpleReplace(strFormat, "%q", "%6.2q");
    String[] format = PT.split(strFormat, "%");
    SB sb = new SB();
    sb.append(format[0]);
    for (int i = 1; i < format.length; i++) {
      String f = "%" + format[i];
      int pt;
      if (f.length() >= 3) {
        if ((pt = f.indexOf('p')) >= 0)
          f = fdup(f, pt, 3);
        if ((pt = f.indexOf('q')) >= 0)
          f = fdup(f, pt, 4);
      }
      sb.append(f);
    }
    return sb.toString().replace('\1', '%');
  }

  /**
   * 
   * fdup      duplicates p or q formats for formatCheck
   *           and the format() function.
   * 
   * @param f
   * @param pt
   * @param n
   * @return     %3.5q%3.5q%3.5q%3.5q or %3.5p%3.5p%3.5p
   */
  private static String fdup(String f, int pt, int n) {
    char ch;
    int count = 0;
    for (int i = pt; --i >= 1; ) {
      if (Character.isDigit(ch = f.charAt(i)))
        continue;
      switch (ch) {
      case '.':
        if (count++ != 0)
          return f;
        continue;
      case '-':
        if (i != 1)
          return f;
        continue;
      default:
        return f;
      }
    }
    String s = f.substring(0, pt + 1);
    SB sb = new SB();
    for (int i = 0; i < n; i++)
      sb.append(s);
    sb.append(f.substring(pt + 1));
    return sb.toString();
  }

  public static void leftJustify(SB s, String s1, String s2) {
    s.append(s2);
    int n = s1.length() - s2.length();
    if (n > 0)
      s.append(s1.substring(0, n));
  }
  
  public static void rightJustify(SB s, String s1, String s2) {
    int n = s1.length() - s2.length();
    if (n > 0)
      s.append(s1.substring(0, n));
    s.append(s2);
  }
  
  public static String safeTruncate(float f, int n) {
    if (f > -0.001 && f < 0.001)
      f = 0;
    return (f + "         ").substring(0,n);
  }

  public static boolean isWild(String s) {
    return s != null && (s.indexOf("*") >= 0 || s.indexOf("?") >= 0);
  }

  public static boolean isMatch(String s, String strWildcard,
                                boolean checkStar, boolean allowInitialStar) {
    int ich = 0;
    int cchWildcard = strWildcard.length();
    int cchs = s.length();
    if (cchs == 0 || cchWildcard == 0)
      return (cchs == cchWildcard || cchWildcard == 1 && strWildcard.charAt(0) == '*');
    boolean isStar0 = (checkStar && allowInitialStar ? strWildcard.charAt(0) == '*' : false);
    if (isStar0 && strWildcard.charAt(cchWildcard - 1) == '*')
      return (cchWildcard < 3 || s.indexOf(strWildcard.substring(1,
          cchWildcard - 1)) >= 0); 
    String qqq = "????";
    while (qqq.length() < s.length())
      qqq += qqq;
    if (checkStar) {
      if (allowInitialStar && isStar0)
        strWildcard = qqq + strWildcard.substring(1);
      if (strWildcard.charAt(ich = strWildcard.length() - 1) == '*')
        strWildcard = strWildcard.substring(0, ich) + qqq;
      cchWildcard = strWildcard.length();
    }

    if (cchWildcard < cchs)
      return false;

    ich = 0;

    // atom name variant (trimLeadingMarks == false)

    // -- each ? matches ONE character if not at end
    // -- extra ? at end ignored

    //group3 variant (trimLeadingMarks == true)

    // -- each ? matches ONE character if not at end
    // -- extra ? at beginning reduced to match length
    // -- extra ? at end ignored

    while (cchWildcard > cchs) {
      if (allowInitialStar && strWildcard.charAt(ich) == '?') {
        ++ich;
      } else if (strWildcard.charAt(ich + cchWildcard - 1) != '?') {
        return false;
      }
      --cchWildcard;
    }

    for (int i = cchs; --i >= 0;) {
      char charWild = strWildcard.charAt(ich + i);
      if (charWild == '?')
        continue;
      if (charWild != s.charAt(i) && (charWild != '\1' || s.charAt(i) != '?'))
          return false;
    }
    return true;
  }

  public static String join(String[] s, char c, int i0) {
    if (s.length < i0)
      return null;
    SB sb = new SB();
    sb.append(s[i0++]);
    for (int i = i0; i < s.length; i++)
      sb.appendC(c).append(s[i]);
    return sb.toString();
  }

  public static String replaceQuotedStrings(String s, List<String> list,
                                            List<String> newList) {
    int n = list.size();
    for (int i = 0; i < n; i++) {
      String name = list.get(i);
      String newName = newList.get(i);
      if (!newName.equals(name))
        s = PT.simpleReplace(s, "\"" + name + "\"", "\"" + newName
            + "\"");
    }
    return s;
  }

  public static String replaceStrings(String s, List<String> list,
                                      List<String> newList) {
    int n = list.size();
    for (int i = 0; i < n; i++) {
      String name = list.get(i);
      String newName = newList.get(i);
      if (!newName.equals(name))
        s = PT.simpleReplace(s, name, newName);
    }
    return s;
  }

  /**
   * For @{....}
   * 
   * @param script
   * @param ichT
   * @param len
   * @return     position of "}"
   */
  public static int ichMathTerminator(String script, int ichT, int len) {
    int nP = 1;
    char chFirst = '\0';
    char chLast = '\0';
    while (nP > 0 && ++ichT < len) {
      char ch = script.charAt(ichT);
      if (chFirst != '\0') {
        if (chLast == '\\') {
          ch = '\0';
        } else if (ch == chFirst) {
          chFirst = '\0';
        }
        chLast = ch;
        continue;
      }
      switch(ch) {
      case '\'':
      case '"':
        chFirst = ch;
        break;
      case '{':
        nP++;
        break;
      case '}':
        nP--;
        break;
      }
    }
    return ichT;
  }

}
