/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2006-10-20 07:48:25 -0500 (Fri, 20 Oct 2006) $
 * $Revision: 5991 $
 *
 * Copyright (C) 2003-2005  Miguel, Jmol Development, www.jmol.org
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
package org.jmol.adapter.readers.cif;

import org.jmol.adapter.smarter.MSCifInterface;

import javajs.util.Matrix;
import javajs.util.PT;


public class MSCifReader extends MSReader implements MSCifInterface {

  public MSCifReader() {
    // for reflection
  }
  
  private String field;
  
  // incommensurate modulation
  ////////////////////////////////////////////////////////////////
 
//  The occupational distortion of a given atom or rigid group is
//  usually parameterized by Fourier series. Each term of the series
//  commonly adopts two different representations: the sine-cosine
//  form,
//           Pc cos(2\p k r)+Ps sin(2\p k r),
//  and the modulus-argument form,
//           |P| cos(2\p k r+\d),
//  where k is the wave vector of the term and r is the atomic
//  average position. _atom_site_occ_Fourier_param_phase is the phase
//  (\d/2\p) in cycles corresponding to the Fourier term defined by
//  _atom_site_occ_Fourier_atom_site_label and
//  _atom_site_occ_Fourier_wave_vector_seq_id.

  private final static int WV_ID = 0;
  private final static int WV_X = 1;
  private final static int WV_Y = 2;
  private final static int WV_Z = 3;
  private final static int FWV_ID = 4;
  private final static int FWV_X = 5;
  private final static int FWV_Y = 6;
  private final static int FWV_Z = 7;
  private final static int FWV_Q1_COEF = 8;
  private final static int FWV_Q2_COEF = 9;
  private final static int FWV_Q3_COEF = 10;
  
  private final static int FWV_DISP_LABEL = 11;
  private final static int FWV_DISP_AXIS = 12;
  private final static int FWV_DISP_SEQ_ID = 13;
  private final static int FWV_DISP_COS = 14;
  private final static int FWV_DISP_SIN = 15;
  private final static int FWV_DISP_MODULUS = 16;
  private final static int FWV_DISP_PHASE = 17;
  
  private final static int FWV_OCC_LABEL = 18;
  private final static int FWV_OCC_SEQ_ID = 19;
  private final static int FWV_OCC_COS = 20;
  private final static int FWV_OCC_SIN = 21;
  private final static int FWV_OCC_MODULUS = 22;
  private final static int FWV_OCC_PHASE = 23;
  
  private final static int DISP_SPEC_LABEL = 24;
  private final static int DISP_SAW_AX = 25; 
  private final static int DISP_SAW_AY = 26;
  private final static int DISP_SAW_AZ = 27;
  private final static int DISP_SAW_C = 28;
  private final static int DISP_SAW_W = 29;
  
  private final static int OCC_SPECIAL_LABEL = 30;
  private final static int OCC_CRENEL_C = 31;
  private final static int OCC_CRENEL_W = 32;

  private final static int FWV_U_LABEL = 33;
  private final static int FWV_U_TENS = 34;
  private final static int FWV_U_SEQ_ID = 35;
  private final static int FWV_U_COS = 36;
  private final static int FWV_U_SIN = 37;  
  private final static int FWV_U_MODULUS = 38;
  private final static int FWV_U_PHASE = 39;

  private final static int FD_ID = 40;
  private final static int FO_ID = 41;
  private final static int FU_ID = 42;

  private final static int FDP_ID = 43;
  private final static int FOP_ID = 44;
  private final static int FUP_ID = 45;
  
  private final static int JANA_OCC_ABS_LABEL = 46;
  private final static int JANA_OCC_ABS_O_0 = 47;
  
  private final static int DEPR_FD_COS = 48;
  private final static int DEPR_FD_SIN = 49;
  private final static int DEPR_FO_COS = 50;
  private final static int DEPR_FO_SIN = 51;
  private final static int DEPR_FU_COS = 52;
  private final static int DEPR_FU_SIN = 53;
  

  final private static String[] modulationFields = {
      "_cell_wave_vector_seq_id", 
      "_cell_wave_vector_x", 
      "_cell_wave_vector_y", 
      "_cell_wave_vector_z", 
      "_atom_site_fourier_wave_vector_seq_id",  
      "_atom_site_fourier_wave_vector_x", // 5
      "_atom_site_fourier_wave_vector_y", 
      "_atom_site_fourier_wave_vector_z",
      "_jana_atom_site_fourier_wave_vector_q1_coeff", 
      "_jana_atom_site_fourier_wave_vector_q2_coeff",  
      "_jana_atom_site_fourier_wave_vector_q3_coeff", // 10
      "_atom_site_displace_fourier_atom_site_label", 
      "_atom_site_displace_fourier_axis", 
      "_atom_site_displace_fourier_wave_vector_seq_id", // 13 
      "_atom_site_displace_fourier_param_cos",  
      "_atom_site_displace_fourier_param_sin", // 15
      "_atom_site_displace_fourier_param_modulus", 
      "_atom_site_displace_fourier_param_phase", 
      "_atom_site_occ_fourier_atom_site_label", 
      "_atom_site_occ_fourier_wave_vector_seq_id", 
      "_atom_site_occ_fourier_param_cos", // 20
      "_atom_site_occ_fourier_param_sin",
      "_atom_site_occ_fourier_param_modulus", 
      "_atom_site_occ_fourier_param_phase", 
      "_atom_site_displace_special_func_atom_site_label", 
      "_atom_site_displace_special_func_sawtooth_ax", // 25 
      "_atom_site_displace_special_func_sawtooth_ay", 
      "_atom_site_displace_special_func_sawtooth_az", 
      "_atom_site_displace_special_func_sawtooth_c", 
      "_atom_site_displace_special_func_sawtooth_w", 
      "_atom_site_occ_special_func_atom_site_label", // 30
      "_atom_site_occ_special_func_crenel_c",
      "_atom_site_occ_special_func_crenel_w",

      "_atom_site_u_fourier_atom_site_label",
      "_atom_site_u_fourier_tens_elem",
      "_atom_site_u_fourier_wave_vector_seq_id", // 35
      "_atom_site_u_fourier_param_cos",
      "_atom_site_u_fourier_param_sin",
      "_atom_site_u_fourier_param_modulus",
      "_atom_site_u_fourier_param_phase",
      
      "_atom_site_displace_fourier_id", // 40
      "_atom_site_occ_fourier_id",
      "_atom_site_u_fourier_id",

      "_atom_site_displace_fourier_param_id", // 43
      "_atom_site_occ_fourier_param_id",
      "_atom_site_u_fourier_param_id",
      
      "_jana_atom_site_occ_fourier_absolute_site_label", // 46
      "_jana_atom_site_occ_fourier_absolute",
      // deprecated:
      "_atom_site_displace_fourier_cos", // 48
      "_atom_site_displace_fourier_sin",
      "_atom_site_occ_fourier_cos",
      "_atom_site_occ_fourier_sin",
      "_atom_site_u_fourier_cos",
      "_atom_site_u_fourier_sin"
  };
  private static final int NONE = -1;
  
  /**
   * creates entries in htModulation with a key of the form:
   * 
   * type_id_axis;atomLabel
   * 
   * where type = W|F|D|O (wave vector, Fourier index, displacement, occupancy);
   * id = 1|2|3|0|S (Fourier index, Crenel(0), sawtooth); axis (optional) =
   * 0|x|y|z (0 indicates irrelevant -- occupancy); and ;atomLabel is only for D
   * and O.
   * 
   * @throws Exception
   */
  @Override
  public boolean processModulationLoopBlock() throws Exception {
    if (modAverage)
      return false;
    CifReader cr = (CifReader) this.cr;
    if (cr.atomSetCollection.getCurrentAtomSetIndex() < 0)
      cr.atomSetCollection.newAtomSet();
    cr.parseLoopParameters(modulationFields);
    int tok;
    while (cr.tokenizer.getData()) {
      boolean ignore = false;
      String id = null;
      String atomLabel = null;
      String axis = null;
      double[] pt = new double[] { Double.NaN, Double.NaN, Double.NaN };
      double c = Double.NaN;
      double w = Double.NaN;
      String fid = null;
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (tok = fieldProperty(cr, i)) {
        case FD_ID:
        case FO_ID:
        case FU_ID:
        case WV_ID:
        case FWV_ID:
          pt[0] = pt[1] = pt[2] = 0;
          //$FALL-THROUGH$
        case FWV_DISP_SEQ_ID:
        case FWV_OCC_SEQ_ID:
        case FWV_U_SEQ_ID:
        case FDP_ID:
        case FOP_ID:
        case FUP_ID:
          switch (tok) {
          case WV_ID:
            id = "W_";
            break;
          case FWV_ID:
            id = "F_";
            break;
          case FD_ID:
          case FO_ID:
          case FU_ID:
            fid = "?" + field;
            pt[2] = 1;
            continue;
          case FDP_ID:
          case FOP_ID:
          case FUP_ID:
            atomLabel = axis = "*";
            //$FALL-THROUGH$
          case FWV_DISP_SEQ_ID:
          case FWV_OCC_SEQ_ID:
          case FWV_U_SEQ_ID:
            id = Character.toUpperCase(modulationFields[tok].charAt(11))
                + "_";
            break;
          }
          id += field;
          break;
        case JANA_OCC_ABS_LABEL:
          id = "J_O";
          pt[0] = pt[2] = 1;
          //$FALL-THROUGH$
        case DISP_SPEC_LABEL:
          if (id == null)
          id = "D_S";
          //$FALL-THROUGH$
        case OCC_SPECIAL_LABEL:
          if (id == null)
            id = "O_0";
          axis = "0";
          //$FALL-THROUGH$
        case FWV_DISP_LABEL:
        case FWV_OCC_LABEL:
        case FWV_U_LABEL:
          atomLabel = field;
          break;
        case FWV_DISP_AXIS:
          if (modAxes != null && modAxes.indexOf(axis.toUpperCase()) < 0)
            ignore = true;
          axis = field;
          break;
        case FWV_U_TENS:
          axis = field.toUpperCase();
          break;
        case DEPR_FO_COS:
        case DEPR_FD_COS:
        case DEPR_FU_COS:
        case FWV_OCC_COS:
        case FWV_DISP_COS:
        case FWV_U_COS:
        case OCC_CRENEL_C:
          pt[2] = 0;
          //$FALL-THROUGH$
        case WV_X:
        case FWV_X:
        case DISP_SAW_AX:
          pt[0] = cr.parseFloatStr(field);
          break;
        case FWV_Q1_COEF:
          id += "_coefs_";
          pt = new double[modDim];
          pt[0] = cr.parseFloatStr(field);
          break;
        case FWV_DISP_MODULUS:
        case FWV_OCC_MODULUS:
        case FWV_U_MODULUS:
          pt[0] = cr.parseFloatStr(field);
          pt[2] = 1;
          break;
        case DEPR_FO_SIN:
        case FWV_OCC_SIN:
          axis = "0";
          //$FALL-THROUGH$
        case WV_Y:
        case FWV_Y:
        case FWV_Q2_COEF:
        case FWV_DISP_SIN:
        case FWV_DISP_PHASE:
        case FWV_OCC_PHASE:
        case FWV_U_SIN:
        case FWV_U_PHASE:
        case OCC_CRENEL_W:
        case DISP_SAW_AY:
        case JANA_OCC_ABS_O_0:
        case DEPR_FU_SIN:
        case DEPR_FD_SIN:
          pt[1] = cr.parseFloatStr(field);
          break;
        case WV_Z:
        case FWV_Z:
        case FWV_Q3_COEF:
        case DISP_SAW_AZ:
          pt[2] = cr.parseFloatStr(field);
          break;
        case DISP_SAW_C:
          c = cr.parseFloatStr(field);
          break;
        case DISP_SAW_W:
          w = cr.parseFloatStr(field);
          break;
        }
        if (ignore || id == null || atomLabel != null
            && !atomLabel.equals("*") && cr.rejectAtomName(atomLabel))
          continue;
        double d = 0;
        for (int j = 0; j < pt.length; j++)
          d += pt[j];
        if (Double.isNaN(d) || d > 1e10 || d == 0)
          continue;
        switch (id.charAt(0)) {
        case 'W':
        case 'F':
          break;
        case 'D':
        case 'O':
        case 'U':
        case 'J':
          if (atomLabel == null || axis == null)
            continue;
          if (id.equals("D_S")) {
            // saw tooth displacement  center/width/Axyz
            if (Double.isNaN(c) || Double.isNaN(w))
              continue;
            if (pt[0] != 0)
              addMod("D_S#x;" + atomLabel, fid, new double[] {c, w, pt[0]});
            if (pt[1] != 0)
              addMod("D_S#y;" + atomLabel, fid, new double[] {c, w, pt[1]});
            if (pt[2] != 0)
              addMod("D_S#z;" + atomLabel, fid, new double[] {c, w, pt[2]});
            continue;
          }
          id += "#" + axis + ";" + atomLabel;
          break;
        }
        addMod(id, fid, pt);
      }
    }
    return true;
  }
    
  private void addMod(String id, String fid, double[] params) {
    if (fid != null)
      id += fid;
    addModulation(null, id, params, -1);
  }

  //loop_
  //_cell_subsystem_code
  //_cell_subsystem_description
  //_cell_subsystem_matrix_W_1_1
  //_cell_subsystem_matrix_W_1_2
  //_cell_subsystem_matrix_W_1_3
  //_cell_subsystem_matrix_W_1_4
  //_cell_subsystem_matrix_W_2_1
  //_cell_subsystem_matrix_W_2_2
  //_cell_subsystem_matrix_W_2_3
  //_cell_subsystem_matrix_W_2_4
  //_cell_subsystem_matrix_W_3_1
  //_cell_subsystem_matrix_W_3_2
  //_cell_subsystem_matrix_W_3_3
  //_cell_subsystem_matrix_W_3_4
  //_cell_subsystem_matrix_W_4_1
  //_cell_subsystem_matrix_W_4_2
  //_cell_subsystem_matrix_W_4_3
  //_cell_subsystem_matrix_W_4_4
  //1 '1-st subsystem' 1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1
  //2 '2-nd subsystem' 1 0 0 1 0 1 0 0 0 0 1 0 0 0 0 1
  //
  
  @Override
  public void processSubsystemLoopBlock() throws Exception {
    CifReader cr = (CifReader) this.cr;
    cr.parseLoopParameters(null);
    while (cr.tokenizer.getData()) {
      fieldProperty(cr, 0);
      String id = field;
      addSubsystem(id, getSubSystemMatrix(cr, 1));
    }
  }

  private Matrix getSubSystemMatrix(CifReader cr, int i) {
    Matrix m = new Matrix(null, 3 + modDim, 3 + modDim);
    double[][] a = m.getArray();
    String key;
    int p;
    for (; i < cr.tokenizer.fieldCount; ++i) {
      if ((p = fieldProperty(cr, i)) < 0 
          || !(key = cr.fields[p]).contains("_w_"))
        continue;
      String[] tokens = PT.split(key, "_");
      int r = cr.parseIntStr(tokens[tokens.length - 2]) - 1;
      int c = cr.parseIntStr(tokens[tokens.length - 1]) - 1;
      a[r][c] = cr.parseFloatStr(field);
    }
    return m;
  }

  private int fieldProperty(CifReader cr, int i) {
    return ((field = cr.tokenizer.loopData[i]).length() > 0 
        && field.charAt(0) != '\0' ? 
            cr.propertyOf[i] : NONE);
  }

  }
