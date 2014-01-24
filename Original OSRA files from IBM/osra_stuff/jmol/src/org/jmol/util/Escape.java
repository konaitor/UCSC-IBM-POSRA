/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2005  The Jmol Development Team
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

import java.util.Iterator;
import java.util.Map;

import javajs.util.List;
import javajs.util.PT;
import javajs.util.SB;
import javajs.util.A4;
import javajs.util.M3;
import javajs.util.M4;
import javajs.util.P3;
import javajs.util.P4;
import javajs.util.T3;


import org.jmol.java.BS;
import org.jmol.script.SV;



public class Escape {

  public static String escapeColor(int argb) {
    return (argb == 0 ? null  :  "[x" + getHexColorFromRGB(argb) + "]");
  }

  public static String getHexColorFromRGB(int argb) {
    if (argb == 0)
      return null;
    String r  = "00" + Integer.toHexString((argb >> 16) & 0xFF);
    r = r.substring(r.length() - 2);
    String g  = "00" + Integer.toHexString((argb >> 8) & 0xFF);
    g = g.substring(g.length() - 2);
    String b  = "00" + Integer.toHexString(argb & 0xFF);
    b = b.substring(b.length() - 2);
    return r + g + b;
  }

  
  /**
   * must be its own, because of the possibility of being null
   * @param xyz
   * @return  {x y z}
   */
  public static String eP(T3 xyz) {
    if (xyz == null)
      return "null";
    return "{" + xyz.x + " " + xyz.y + " " + xyz.z + "}";
  }

  public static String matrixToScript(Object m) {
    return PT.replaceAllCharacters(m.toString(), "\n\r ","").replace('\t',' ');
  }

  public static String eP4(P4 x) {
    return "{" + x.x + " " + x.y + " " + x.z + " " + x.w + "}";
  }
  
  @SuppressWarnings("unchecked")
  public static String e(Object x) {
    if (x == null)
      return "null";
    if (x instanceof String)
      return eS((String) x);
    if (x instanceof List<?>)
      return eV((List<SV>) x);
    if (x instanceof BS) 
      return eBS((BS) x);
    if (x instanceof T3)
      return eP((T3) x);
    if (x instanceof P4)
      return eP4((P4) x);
    if (PT.isAS(x))
      return eAS((String[]) x, true);
    if (PT.isAI(x))
      return eAI((int[]) x);
    if (PT.isAF(x))
      return eAF((float[]) x);
    if (PT.isAD(x))
      return eAD((double[]) x);
    if (PT.isAP(x))
      return eAP((P3[]) x);
    if (x instanceof M3) 
      return PT.simpleReplace(((M3) x).toString(), "\t", ",\t");
    if (x instanceof M4) 
      return PT.simpleReplace(((M4) x).toString(), "\t", ",\t");
    if (x instanceof A4) {
      A4 a = (A4) x;
      return "{" + a.x + " " + a.y + " " + a.z + " " + (float) (a.angle * 180d/Math.PI) + "}";
    }    
    if (x instanceof Map)
      return escapeMap((Map<String, Object>) x);
    if (PT.isAII(x) || PT.isAFF(x) || PT.isADD(x) || PT.isAFFF(x)) 
      return PT.toJSON(null, x);
    return x.toString();
  }

  private final static String escapable = "\\\\\tt\rr\nn\"\""; 

  public static String eS(String str) {
    if (str == null || str.length() == 0)
      return "\"\"";
    boolean haveEscape = false;
    int i = 0;
    for (; i < escapable.length(); i += 2)
      if (str.indexOf(escapable.charAt(i)) >= 0) {
        haveEscape = true;
        break;
      }
    if (haveEscape)
      while (i < escapable.length()) {
        int pt = -1;
        char ch = escapable.charAt(i++);
        char ch2 = escapable.charAt(i++);
        SB sb = new SB();
        int pt0 = 0;
        while ((pt = str.indexOf(ch, pt + 1)) >= 0) {
          sb.append(str.substring(pt0, pt)).appendC('\\').appendC(ch2);
          pt0 = pt + 1;
        }
        sb.append(str.substring(pt0, str.length()));
        str = sb.toString();
      }
    for (i = str.length(); --i >= 0;)
      if (str.charAt(i) > 0x7F)
        str = str.substring(0, i) + unicode(str.charAt(i))
            + str.substring(i + 1);
    return "\"" + str + "\"";
  }

  private static String unicode(char c) {
    String s = "0000" + Integer.toHexString(c);
    return "\\u" + s.substring(s.length() - 4);
  }

  public static String eV(List<SV> list) {
    if (list == null)
      return eS("");
    SB s = new SB();
    s.append("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0)
        s.append(", ");
      s.append(escapeNice(list.get(i).asString()));
    }
    s.append("]");
    return s.toString();
  }

  public static String escapeMap(Map<String, Object> ht) {
    SB sb = new SB();
    sb.append("{ ");
    String sep = "";
    for (Map.Entry<String, Object> entry : ht.entrySet()) {
      String key = entry.getKey();
      sb.append(sep).append(eS(key)).appendC(':');
      Object val = entry.getValue();
      if (!(val instanceof SV))
        val = SV.getVariable(val);
      sb.append(((SV)val).escape());
      sep = ","; 
    }
    sb.append(" }");
    return sb.toString();
  }
  
  /**
   * 
   * @param f
   * @param asArray -- FALSE allows bypassing of escape(Object f); TRUE: unnecssary
   * @return tabular string
   */
  public static String escapeFloatA(float[] f, boolean asArray) {
    if (asArray)
      return PT.toJSON(null, f); // or just use escape(f)
    SB sb = new SB();
    for (int i = 0; i < f.length; i++) {
      if (i > 0)
        sb.appendC('\n');
      sb.appendF(f[i]);
    }
    return sb.toString();
  }

  public static String escapeFloatAA(float[][] f, boolean addSemi) {
    SB sb = new SB();
    String eol = (addSemi ? ";\n" : "\n");
    for (int i = 0; i < f.length; i++)
      if (f[i] != null) {
        if (i > 0)
          sb.append(eol);
        for (int j = 0; j < f[i].length; j++)
          sb.appendF(f[i][j]).appendC('\t');
      }
    return sb.toString();
  }

  public static String escapeFloatAAA(float[][][] f, boolean addSemi) {
    SB sb = new SB();
    String eol = (addSemi ? ";\n" : "\n");
    if (f[0] == null || f[0][0] == null)
      return "0 0 0" + eol;
    sb.appendI(f.length).append(" ")
      .appendI(f[0].length).append(" ")
      .appendI(f[0][0].length);
    for (int i = 0; i < f.length; i++)
      if (f[i] != null) {
        sb.append(eol);
        for (int j = 0; j < f[i].length; j++)
          if (f[i][j] != null) {
            sb.append(eol);
            for (int k = 0; k < f[i][j].length; k++)
              sb.appendF(f[i][j][k]).appendC('\t');
          }
      }
    return sb.toString();
  }

  /**
   * 
   * @param list
   *          list of strings to serialize
   * @param nicely TODO
   * @return serialized array
   */
  public static String eAS(String[] list, boolean nicely) {
    if (list == null)
      return eS("");
    SB s = new SB();
    s.append("[");
    for (int i = 0; i < list.length; i++) {
      if (i > 0)
        s.append(", ");
      s.append(nicely ? escapeNice(list[i]) : eS(list[i]));
    }
    s.append("]");
    return s.toString();
  }

  public static String eAI(int[] ilist) {
    if (ilist == null)
      return eS("");
    SB s = new SB();
    s.append("[");
    for (int i = 0; i < ilist.length; i++) {
      if (i > 0)
        s.append(", ");
      s.appendI(ilist[i]);
    }
    return s.append("]").toString();
  }

  public static String eAD(double[] dlist) {
    // from isosurface area or volume calc
    if (dlist == null)
      return eS("");
    SB s = new SB();
    s.append("[");
    for (int i = 0; i < dlist.length; i++) {
      if (i > 0)
        s.append(", ");
      s.appendD(dlist[i]);
    }
    return s.append("]").toString();
  }

  public static String eAF(float[] flist) {
    if (flist == null)
      return eS("");
    SB s = new SB();
    s.append("[");
    for (int i = 0; i < flist.length; i++) {
      if (i > 0)
        s.append(", ");
      s.appendF(flist[i]);
    }
    return s.append("]").toString();
  }

  public static String eAP(T3[] plist) {
    if (plist == null)
      return eS("");
    SB s = new SB();
    s.append("[");
    for (int i = 0; i < plist.length; i++) {
      if (i > 0)
        s.append(", ");
      s.append(eP(plist[i]));
    }
    return s.append("]").toString();
  }

  private static String escapeNice(String s) {
    if (s == null)
      return "null";
    float f = PT.parseFloatStrict(s);
    return (Float.isNaN(f) ? eS(s) : s);
  }

  public static Object uABsM(String s) {
    if (s.charAt(0) == '{')
      return uP(s);
    if ((isStringArray(s)
        || s.startsWith("[{") && s.indexOf("[{") == s.lastIndexOf("[{"))
        && s.indexOf(',') < 0 && s.indexOf('.') < 0 && s.indexOf('-') < 0)
      return uB(s);
    if (s.startsWith("[["))
      return unescapeMatrix(s);
    return s;
  }

  public static boolean isStringArray(String s) {
    return s.startsWith("({") && s.lastIndexOf("({") == 0
        && s.indexOf("})") == s.length() - 2;
  }
  public static Object uP(String strPoint) {
    if (strPoint == null || strPoint.length() == 0)
      return strPoint;
    String str = strPoint.replace('\n', ' ').trim();
    if (str.charAt(0) != '{' || str.charAt(str.length() - 1) != '}')
      return strPoint;
    float[] points = new float[5];
    int nPoints = 0;
    str = str.substring(1, str.length() - 1);
    int[] next = new int[1];
    for (; nPoints < 5; nPoints++) {
      points[nPoints] = PT.parseFloatNext(str, next);
      if (Float.isNaN(points[nPoints])) {
        if (next[0] >= str.length() || str.charAt(next[0]) != ',')
          break;
        next[0]++;
        nPoints--;
      }
    }
    if (nPoints == 3)
      return P3.new3(points[0], points[1], points[2]);
    if (nPoints == 4)
      return P4.new4(points[0], points[1], points[2], points[3]);
    return strPoint;
  }

  public static BS uB(String str) {
      char ch;
      int len;
      if (str == null || (len = (str = str.trim()).length()) < 4
          || str.equalsIgnoreCase("({null})") 
          || (ch = str.charAt(0)) != '(' && ch != '[' 
          || str.charAt(len - 1) != (ch == '(' ? ')' : ']')
          || str.charAt(1) != '{' || str.indexOf('}') != len - 2)
        return null;
      len -= 2;
      for (int i = len; --i >= 2;)
        if (!Character.isDigit(ch = str.charAt(i)) && ch != ' ' && ch != '\t'
            && ch != ':')
          return null;
      int lastN = len;
      while (Character.isDigit(str.charAt(--lastN))) {
        // loop
      }
      if (++lastN == len)
        lastN = 0;
      else
        try {
          lastN = Integer.parseInt(str.substring(lastN, len));
        } catch (NumberFormatException e) {
          return null;
        }
      BS bs = BSUtil.newBitSet(lastN);
      lastN = -1;
      int iPrev = -1;
      int iThis = -2;
      for (int i = 2; i <= len; i++) {
        switch (ch = str.charAt(i)) {
        case '\t':
        case ' ':
        case '}':
          if (iThis < 0)
            break;
          if (iThis < lastN)
            return null;
          lastN = iThis;
          if (iPrev < 0)
            iPrev = iThis;
          bs.setBits(iPrev, iThis + 1);
          iPrev = -1;
          iThis = -2;
          break;
        case ':':
          iPrev = lastN = iThis;
          iThis = -2;
          break;
        default:
          if (Character.isDigit(ch)) {
            if (iThis < 0)
              iThis = 0;
            iThis = (iThis * 10) + (ch - 48);
          }
        }
      }
      return (iPrev >= 0 ? null : bs);
    }

  public static Object unescapeMatrix(String strMatrix) {
    if (strMatrix == null || strMatrix.length() == 0)
      return strMatrix;
    String str = strMatrix.replace('\n', ' ').trim();
    if (str.lastIndexOf("[[") != 0 || str.indexOf("]]") != str.length() - 2)
      return strMatrix;
    float[] points = new float[16];
    str = str.substring(2, str.length() - 2).replace('[',' ').replace(']',' ').replace(',',' ');
    int[] next = new int[1];
    int nPoints = 0;
    for (; nPoints < 16; nPoints++) {
      points[nPoints] = PT.parseFloatNext(str, next);
      if (Float.isNaN(points[nPoints])) {
        break;
      }
    }
    if (!Float.isNaN(PT.parseFloatNext(str, next)))
      return strMatrix; // overflow
    if (nPoints == 9)
      return M3.newA(points);
    if (nPoints == 16)
      return M4.newA(points);
    return strMatrix;
  }
/*
  public static Object unescapeArray(String strArray) {
    if (strArray == null || strArray.length() == 0)
      return strArray;
    String str = strArray.replace('\n', ' ').replace(',', ' ').trim();
    if (str.lastIndexOf("[") != 0 || str.indexOf("]") != str.length() - 1)
      return strArray;
    float[] points = Parser.parseFloatArray(str);
    for (int i = 0; i < points.length; i++)
      if (Float.isNaN(points[i]))
        return strArray;
    return points;
  }
*/
  public static String eBS(BS bs) {
    return eB(bs, '(', ')');
  }
  
  public static String eBond(BS bs) {
    return eB(bs, '[', ']');
  }
  
  private static String eB(BS bs, char chOpen, char chClose) {
    if (bs == null)
      return chOpen + "{}" + chClose;
    SB s = new SB();
    s.append(chOpen + "{");
    int imax = bs.length();
    int iLast = -1;
    int iFirst = -2;
    int i = -1;
    while (++i <= imax) {
      boolean isSet = bs.get(i);
      if (i == imax || iLast >= 0 && !isSet) {
        if (iLast >= 0 && iFirst != iLast)
          s.append((iFirst == iLast - 1 ? " " : ":") + iLast);
        if (i == imax)
          break;
        iLast = -1;
      }
      if (bs.get(i)) {
        if (iLast < 0) {
          s.append((iFirst == -2 ? "" : " ") + i);
          iFirst = i;
        }
        iLast = i;
      }
    }
    s.append("}").appendC(chClose);
    return s.toString();
  }

  public static String toReadable(String name, Object info) {
    SB sb =new SB();
    String sep = "";
    if (info == null)
      return "null";
    if (info instanceof String)
      return packageReadable(name, null, eS((String) info));
    if (PT.isAS(info)) {
      sb.append("[");
      int imax = ((String[]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).append(eS(((String[]) info)[i]));
        sep = ",";
      }
      sb.append("]");
      return packageReadableSb(name, "String[" + imax + "]", sb);
    }
    if (PT.isAI(info)) {
      sb.append("[");
      int imax = ((int[]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).appendI(((int[]) info)[i]);
        sep = ",";
      }
      sb.append("]");
      return packageReadableSb(name, "int[" + imax + "]", sb);
    }
    if (PT.isAF(info)) {
      sb.append("[");
      int imax = ((float[]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).appendF(((float[]) info)[i]);
        sep = ",";
      }
      sb.append("]");
      return packageReadableSb(name, "float[" + imax + "]", sb);
    }
    if (PT.isAD(info)) {
      sb.append("[");
      int imax = ((double[]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).appendD(((double[]) info)[i]);
        sep = ",";
      }
      sb.append("]");
      return packageReadableSb(name, "double[" + imax + "]", sb);
    }
    if (PT.isAP(info)) {
      sb.append("[");
      int imax = ((P3[]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).append(eP(((P3[])info)[i]));
        sep = ",";
      }
      sb.append("]");
      return packageReadableSb(name, "point3f[" + imax + "]", sb);
    }
    if (PT.isASS(info)) {
      sb.append("[");
      int imax = ((String[][]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).append(toReadable(null, ((String[][]) info)[i]));
        sep = ",\n";
      }
      sb.append("]");
      return packageReadableSb(name, "String[" + imax + "][]", sb);
    }
    if (PT.isAII(info)) {
      sb.append("[");
      int imax = ((int[][]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).append(toReadable(null, ((int[][]) info)[i]));
        sep = ",";
      }
      sb.append("]");
      return packageReadableSb(name, "int[" + imax + "][]", sb);
    }
    if (PT.isAFF(info)) {
      sb.append("[\n");
      int imax = ((float[][]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).append(toReadable(null, ((float[][]) info)[i]));
        sep = ",\n";
      }
      sb.append("]");
      return packageReadableSb(name, "float[][]", sb);
    }
    if (PT.isADD(info)) {
      sb.append("[\n");
      int imax = ((double[][]) info).length;
      for (int i = 0; i < imax; i++) {
        sb.append(sep).append(toReadable(null, ((double[][]) info)[i]));
        sep = ",\n";
      }
      sb.append("]");
      return packageReadableSb(name, "double[][]", sb);
    }
    if (info instanceof List<?>) {
      int imax = ((List<?>) info).size();
      for (int i = 0; i < imax; i++) {
        sb.append(toReadable(name + "[" + (i + 1) + "]", ((List<?>) info).get(i)));
      }
      return packageReadableSb(name, "List[" + imax + "]", sb);
    }
    if (info instanceof M3
        || info instanceof T3
        || info instanceof P4
        || info instanceof A4) {
      sb.append(e(info));
      return packageReadableSb(name, null, sb);
    }
    if (info instanceof Map<?, ?>) {
      Iterator<?> e = ((Map<?, ?>) info).keySet().iterator();
      while (e.hasNext()) {
        String key = (String) e.next();
        sb.append(toReadable((name == null ? "" : name + ".") + key,
            ((Map<?, ?>) info).get(key)));
      }
      return sb.toString();
    }
    return packageReadable(name, null, info.toString());
  }

  private static String packageReadableSb(String infoName, String infoType,
                                        SB sb) {
    return packageReadable(infoName, infoType, sb.toString());
  }
  
  private static String packageReadable(String infoName, String infoType,
                                        String info) {
    String s = (infoType == null ? "" : infoType + "\t");
    if (infoName == null)
      return s + info;
    return "\n" + infoName + "\t" + (infoType == null ? "" : "*" + infoType + "\t") + info;
  }

  public static String escapeModelFileNumber(int iv) {
    return "" + (iv / 1000000) + "." + (iv % 1000000);
  }

  public static String encapsulateData(String name, Object data, int depth) {
    return "  DATA \"" + name + "\"\n" + 
        (depth == 2 ?
          escapeFloatAA((float[][]) data, true) + ";\n"
          : depth == 3 ?
              escapeFloatAAA((float[][][]) data, true) + ";\n"
          : data) + "    END \"" + name + "\";\n";
  }

//  public static String escapeXml(Object value) {
//    if (value instanceof String)
//      return XmlUtil.wrapCdata(value.toString());
//    String s = "" + value;
//    if (s.length() == 0 || s.charAt(0) != '[')
//      return s;
//    return XmlUtil.wrapCdata(toReadable(null, value));
//  }

  public static String unescapeUnicode(String s) {
    int ichMax = s.length();
    SB sb = SB.newN(ichMax);
    int ich = 0;
    while (ich < ichMax) {
      char ch = s.charAt(ich++);
      if (ch == '\\' && ich < ichMax) {
        ch = s.charAt(ich++);
        switch (ch) {
        case 'u':
          if (ich < ichMax) {
            int unicode = 0;
            for (int k = 4; --k >= 0 && ich < ichMax;) {
              char chT = s.charAt(ich);
              int hexit = getHexitValue(chT);
              if (hexit < 0)
                break;
              unicode <<= 4;
              unicode += hexit;
              ++ich;
            }
            ch = (char) unicode;
          }
        }
      }
      sb.appendC(ch);
    }
    return sb.toString();
  }
  
  public static int getHexitValue(char ch) {
    if (ch >= 48 && ch <= 57)
      return ch - 48;
    else if (ch >= 97 && ch <= 102)
      return 10 + ch - 97;
    else if (ch >= 65 && ch <= 70)
      return 10 + ch - 65;
    else
      return -1;
  }

  public static String[] unescapeStringArray(String data) {
    // was only used for  LOAD "[\"...\",\"....\",...]" (coming from implicit string)
    // now also used for simulation peaks array from JSpecView,
    // which double-escapes strings, I guess
    //TODO -- should recognize '..' as well as "..." ?
    if (data == null || !data.startsWith("[") || !data.endsWith("]"))
      return null; 
    List<String> v = new  List<String>();
    int[] next = new int[1];
    next[0] = 1;
    while (next[0] < data.length()) {
      String s = PT.getQuotedStringNext(data, next);
      if (s == null)
        return null;
      v.addLast(PT.simpleReplace(s, "\\\"", "\""));      
      while (next[0] < data.length() && data.charAt(next[0]) != '"')
        next[0]++;
    }    
    return v.toArray(new String[v.size()]);
  }

  public static boolean isAV(Object x) {
    /**
     * @j2sNative
     *  return Clazz.instanceOf(x[0], org.jmol.scriSV);
     */
    {
    return x instanceof SV[];
    }
  }


}
