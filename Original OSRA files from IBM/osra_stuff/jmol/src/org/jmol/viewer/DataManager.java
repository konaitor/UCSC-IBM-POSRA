/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-10-03 20:53:36 -0500 (Wed, 03 Oct 2007) $
 * $Revision: 8351 $
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

import java.util.Hashtable;
import java.util.Map;

import org.jmol.api.JmolDataManager;
import org.jmol.constant.EnumVdw;
import org.jmol.java.BS;
import org.jmol.modelset.AtomCollection;
import org.jmol.script.T;
import org.jmol.util.BSUtil;
import org.jmol.util.Elements;
import org.jmol.util.Escape;
import org.jmol.util.Logger;
import org.jmol.util.Parser;

import javajs.util.AU;
import javajs.util.PT;
import javajs.util.SB;


/*
 * a class for storing and retrieving user data,
 * including atom-related and color-related data
 * 
 */

public class DataManager implements JmolDataManager {

  private Map<String, Object[]> dataValues = new Hashtable<String, Object[]>();
  
  private Viewer viewer;
  
  public DataManager() {
    // for reflection
  }
  
  @Override
  public JmolDataManager set(Viewer viewer) {
    this.viewer = viewer;
    return this;
  }

  @Override
  public void clear() {
    dataValues.clear();
  }
  
  private final static int DATA_TYPE_STRING = 0;
  private final static int DATA_TYPE_AF = 1;
  private final static int DATA_ARRAY_FF = 2;
  private final static int DATA_ARRAY_FFF = 3;
  //private final static int DATA_LABEL = 0;
  private final static int DATA_VALUE = 1;
  private final static int DATA_SELECTION_MAP = 2;
  private final static int DATA_TYPE = 3;
  private final static int DATA_SAVE_IN_STATE = 4;
    
  @Override
  public void setData(String type, Object[] data, int arrayCount, int actualAtomCount,
               int matchField, int matchFieldColumnCount, int field,
               int fieldColumnCount) {
    //Eval
    /*
     * data[0] -- label
     * data[1] -- string or float[] or float[][] or float[][][]
     * data[2] -- selection bitset or int[] atomMap when field > 0
     * data[3] -- arrayDepth 0(String),1(float[]),2,3(float[][][])
     * data[4] -- Boolean.TRUE == saveInState
     * 
     * matchField = data must match atomNo in this column, >= 1
     * field = column containing the data, >= 1:
     *   0 ==> values are a simple list; clear the data
     *   Integer.MAX_VALUE ==> values are a simple list; don't clear the data
     *   Integer.MIN_VALUE ==> one SINGLE data value should be used for all selected atoms
     */
    if (type == null) {
      clear();
      return;
    }
    type = type.toLowerCase();
    if (type.equals("element_vdw")) {
      String stringData = ((String) data[DATA_VALUE]).trim();
      if (stringData.length() == 0) {
        viewer.userVdwMars = null;
        viewer.userVdws = null;
        viewer.bsUserVdws = null;
        return;
      }
      if (viewer.bsUserVdws == null)
        viewer.setUserVdw(viewer.defaultVdw);
      Parser.parseFloatArrayFromMatchAndField(stringData, viewer.bsUserVdws, 1, 0,
          (int[]) data[DATA_SELECTION_MAP], 2, 0, viewer.userVdws, 1);
      for (int i = viewer.userVdws.length; --i >= 0;)
        viewer.userVdwMars[i] = (int) Math.floor(viewer.userVdws[i] * 1000);
      return;
    }
    if (data[DATA_SELECTION_MAP] != null && arrayCount > 0) {
      boolean createNew = (matchField != 0 || field != Integer.MIN_VALUE
          && field != Integer.MAX_VALUE);
      Object[] oldData = dataValues.get(type);
      BS bs;
      float[] f = (oldData == null || createNew ? new float[actualAtomCount]
          : AU.ensureLengthA(((float[]) oldData[1]), actualAtomCount));

      // check to see if the data COULD be interpreted as a string of float values
      // and if so, do that. This pre-fetches the tokens in that case.

      int depth = ((Integer)data[DATA_TYPE]).intValue();
      String stringData = (depth == DATA_TYPE_STRING ? (String) data[DATA_VALUE] : null);
      float[] floatData = (depth == DATA_TYPE_AF ? (float[]) data[DATA_VALUE] : null);
      String[] strData = null;
      if (field == Integer.MIN_VALUE
          && (strData = PT.getTokens(stringData)).length > 1)
        field = 0;

      if (field == Integer.MIN_VALUE) {
        // set the selected data elements to a single value
        bs = (BS) data[DATA_SELECTION_MAP];
        setSelectedFloats(PT.parseFloat(stringData), bs, f);
      } else if (field == 0 || field == Integer.MAX_VALUE) {
        // just get the selected token values
        bs = (BS) data[DATA_SELECTION_MAP];
        if (floatData != null) {
          if (floatData.length == bs.cardinality())
            for (int i = bs.nextSetBit(0), pt = 0; i >= 0; i = bs
                .nextSetBit(i + 1), pt++)
              f[i] = floatData[pt];
          else
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
              f[i] = floatData[i];
        } else {
          Parser.parseFloatArrayBsData(strData == null ? PT.getTokens(stringData)
              : strData, bs, f);
        }
      } else if (matchField <= 0) {
        // get the specified field >= 1 for the selected atoms
        bs = (BS) data[DATA_SELECTION_MAP];
        Parser.parseFloatArrayFromMatchAndField(stringData, bs, 0, 0, null,
            field, fieldColumnCount, f, 1);
      } else {
        // get the selected field, with an integer match in a specified field
        // in this case, bs is created and indicates which data points were set
        int[] iData = (int[]) data[DATA_SELECTION_MAP];
        Parser.parseFloatArrayFromMatchAndField(stringData, null, matchField,
            matchFieldColumnCount, iData, field, fieldColumnCount, f, 1);
        bs = new BS();
        for (int i = iData.length; --i >= 0;)
          if (iData[i] >= 0)
            bs.set(iData[i]);
      }
      if (oldData != null && oldData[DATA_SELECTION_MAP] instanceof BS && !createNew)
        bs.or((BS) (oldData[DATA_SELECTION_MAP]));
      data[DATA_TYPE] = Integer.valueOf(DATA_TYPE_AF);
      data[DATA_SELECTION_MAP] = bs;
      data[DATA_VALUE] = f;
      if (type.indexOf("property_atom.") == 0) {
        int tok = T.getSettableTokFromString(type = type.substring(14));
        if (tok == T.nada) {
          Logger.error("Unknown atom property: " + type);
          return;
        }
        int nValues = bs.cardinality();
        float[] fValues = new float[nValues];
        for (int n = 0, i = bs.nextSetBit(0); n < nValues; i = bs
            .nextSetBit(i + 1))
          fValues[n++] = f[i];
        viewer.setAtomProperty(bs, tok, 0, 0, null, fValues, null);
        return;
      }
    }
    dataValues.put(type, data);
  }

  /**
   * 
   * @param f
   * @param bs
   * @param data
   */
  private static void setSelectedFloats(float f, BS bs, float[] data) {
    boolean isAll = (bs == null);
    int i0 = (isAll ? 0 : bs.nextSetBit(0));
    for (int i = i0; i >= 0 && i < data.length; i = (isAll ? i + 1 : bs.nextSetBit(i + 1)))
      data[i] = f;
  }

  @Override
  public Object[] getData(String type) {
    if (dataValues.size() == 0 || type == null)
      return null;
    if (!type.equalsIgnoreCase("types"))
      return dataValues.get(type);
    String[] info = new String[2];
    info[0] = "types";
    info[1] = "";
    int n = 0;
    for (String name : dataValues.keySet())
      info[1] += (n++ > 0 ? "\n" : "") + name;
    return info;
  }

  @Override
  public float[] getDataFloatA(String label) {
    if (dataValues.size() == 0)
      return null;
    Object[] data = getData(label);
    if (data == null || ((Integer)data[DATA_TYPE]).intValue() != DATA_TYPE_AF)
      return null;
    return (float[]) data[DATA_VALUE];
  }

  @Override
  public float getDataFloat(String label, int atomIndex) {
    if (dataValues.size() > 0) {
      Object[] data = getData(label);
      if (data != null && ((Integer)data[DATA_TYPE]).intValue() == DATA_TYPE_AF) {
        float[] f = (float[]) data[DATA_VALUE];
        if (atomIndex < f.length)
          return f[atomIndex];
      }
    }
    return Float.NaN;
  }

  @Override
  public float[][] getDataFloat2D(String label) {
    if (dataValues.size() == 0)
      return null;
    Object[] data = getData(label);
    if (data == null || ((Integer)data[DATA_TYPE]).intValue() != DATA_ARRAY_FF)
      return null;
    return (float[][]) data[DATA_VALUE];
  }

  @Override
  public float[][][] getDataFloat3D(String label) {
    if (dataValues.size() == 0)
      return null;
    Object[] data = getData(label);
    if (data == null || ((Integer)data[DATA_TYPE]).intValue() != DATA_ARRAY_FFF)
      return null;
    return (float[][][]) data[DATA_VALUE];
  }

  @Override
  public void deleteModelAtoms(int firstAtomIndex, int nAtoms, BS bsDeleted) {
    if (dataValues.size() == 0)
      return;
    for (String name: dataValues.keySet()) {
      if (name.indexOf("property_") == 0) {
        Object[] obj = dataValues.get(name);
        BSUtil.deleteBits((BS) obj[2], bsDeleted);
        switch (((Integer)obj[3]).intValue()) {
        case DATA_TYPE_AF:
          obj[1] = AU.deleteElements(obj[1], firstAtomIndex, nAtoms);
          break;
        case DATA_ARRAY_FF:
          obj[1] = AU.deleteElements(obj[1], firstAtomIndex, nAtoms);
          break;
        default:
          // is there anything else??
          break;
        }
      }
    }    
  }

  @Override
  public String getDefaultVdwNameOrData(EnumVdw type, BS bs) {
    SB sb = new SB();
    sb.append(type.getVdwLabel()).append("\n");
    boolean isAll = (bs == null);
    int i0 = (isAll ? 1 : bs.nextSetBit(0));
    int i1 = (isAll ? Elements.elementNumberMax : bs.length());
    for (int i = i0; i < i1 && i >= 0; i = (isAll ? i + 1 : bs
        .nextSetBit(i + 1)))
      sb.appendI(i).appendC('\t').appendF(
          type == EnumVdw.USER ? viewer.userVdws[i] : Elements
              .getVanderwaalsMar(i, type) / 1000f).appendC('\t').append(
          Elements.elementSymbolFromNumber(i)).appendC('\n');
    return (bs == null ? sb.toString() : "\n  DATA \"element_vdw\"\n"
        + sb.append("  end \"element_vdw\";\n\n").toString());
  }

  @Override
  public boolean getDataState(JmolStateCreator sc, SB sb) {
    if (dataValues.size() == 0)
      return false;
    boolean haveData = false;
    for (String name : dataValues.keySet()) {
      if (name.indexOf("property_") == 0) {
        Object[] obj = dataValues.get(name);
        if (obj.length > DATA_SAVE_IN_STATE
            && obj[DATA_SAVE_IN_STATE] == Boolean.FALSE)
          continue;
        haveData = true;
        Object data = obj[1];
        if (data != null && ((Integer) obj[3]).intValue() == 1) {
          sc.getAtomicPropertyStateBuffer(sb, AtomCollection.TAINT_MAX,
              (BS) obj[2], name, (float[]) data);
          sb.append("\n");
        } else {
          sb.append("\n").append(Escape.encapsulateData(name, data, 0));//j2s issue?
        }
      } else if (name.indexOf("data2d") == 0) {
        Object[] obj = dataValues.get(name);
        Object data = obj[1];
        if (data != null && ((Integer) obj[3]).intValue() == 2) {
          haveData = true;
          sb.append("\n").append(Escape.encapsulateData(name, data, 2));
        }
      } else if (name.indexOf("data3d") == 0) {
        Object[] obj = dataValues.get(name);
        Object data = obj[1];
        if (data != null && ((Integer) obj[3]).intValue() == 3) {
          haveData = true;
          sb.append("\n").append(Escape.encapsulateData(name, data, 3));
        }
      }
    }
    return haveData;
  }
  
}
