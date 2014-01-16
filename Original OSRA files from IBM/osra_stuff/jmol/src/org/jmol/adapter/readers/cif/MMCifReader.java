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

import java.util.Hashtable;
import java.util.Map;

import javajs.util.List;
import javajs.util.M4;
import javajs.util.P3;
import javajs.util.PT;
import javajs.util.SB;

import org.jmol.adapter.smarter.Atom;
import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.MMCifInterface;
import org.jmol.adapter.smarter.Structure;
import org.jmol.api.JmolAdapter;
import org.jmol.constant.EnumStructure;
import org.jmol.java.BS;
import org.jmol.util.Escape;
import org.jmol.util.Logger;
import org.jmol.util.SimpleUnitCell;

/**
 * @author Bob Hanson (hansonr@stolaf.edu)
 *  
 */
public class MMCifReader implements MMCifInterface {


  public MMCifReader() {
    // for reflection
  }
  
  private CifReader cr;

  private boolean isBiomolecule;
  private boolean byChain, bySymop, isCourseGrained;
  private Map<String, P3> chainAtomMap;
  private Map<String, int[]> chainAtomCounts;

  private  List<Map<String, Object>> vBiomolecules;
  private Map<String, Object> thisBiomolecule;
  private Map<String,M4> htBiomts;
  private Map<String, Map<String, Object>> htSites;

  private Map<String, BS> assemblyIdAtoms;
  private final static int NONE = -1;
  
  private int thisChain = -1;

  private P3 chainSum;
  private int[] chainAtomCount;


  @Override
  public boolean initialize(CifReader r) {
    cr = r;
    byChain = r.checkFilterKey("BYCHAIN");
    bySymop = r.checkFilterKey("BYSYMOP");
    isCourseGrained = byChain || bySymop;
    if (byChain) {
      chainAtomMap = new Hashtable<String, P3>();
      chainAtomCounts = new Hashtable<String, int[]>();
    }
    if (cr.checkFilterKey("BIOMOLECULE")) // PDB format
     cr.filter = PT.simpleReplace(cr.filter, "BIOMOLECULE","ASSEMBLY");
    isBiomolecule = cr.checkFilterKey("ASSEMBLY");
    return isCourseGrained;
  }
  
  @Override
  public void finalizeReader(int nAtoms) throws Exception {
    if (byChain && !isBiomolecule)
      for (String id: chainAtomMap.keySet())
        createParticle(id);
    AtomSetCollection ac = cr.atomSetCollection;
    if (!isCourseGrained && ac.getAtomCount() == nAtoms)
      ac.removeCurrentAtomSet();
    else
      cr.applySymmetryAndSetTrajectory();
    if (htSites != null)
      cr.addSites(htSites);
    if (vBiomolecules != null && vBiomolecules.size() == 1
        && (isCourseGrained || ac.getAtomCount() > 0)) {
      ac.setAtomSetAuxiliaryInfo("biomolecules", vBiomolecules);
      Map<String, Object> ht = vBiomolecules.get(0);
      cr.appendLoadNote("Constructing " + ht.get("name"));
      setBiomolecules(ht);
      if (thisBiomolecule != null)
        ac.applySymmetryBio(thisBiomolecule, cr.notionalUnitCell, cr.applySymmetryToBonds, cr.filter);
    }

  }
  ////////////////////////////////////////////////////////////////
  // assembly data
  ////////////////////////////////////////////////////////////////

  final private static byte OPER_ID = 12;
  final private static byte OPER_XYZ = 13;
  final private static String[] operFields = {
    "_pdbx_struct_oper_list_matrix[1][1]",
    "_pdbx_struct_oper_list_matrix[1][2]", 
    "_pdbx_struct_oper_list_matrix[1][3]", 
    "_pdbx_struct_oper_list_vector[1]", 
    "_pdbx_struct_oper_list_matrix[2][1]", 
    "_pdbx_struct_oper_list_matrix[2][2]", 
    "_pdbx_struct_oper_list_matrix[2][3]", 
    "_pdbx_struct_oper_list_vector[2]", 
    "_pdbx_struct_oper_list_matrix[3][1]", 
    "_pdbx_struct_oper_list_matrix[3][2]", 
    "_pdbx_struct_oper_list_matrix[3][3]", 
    "_pdbx_struct_oper_list_vector[3]",
    "_pdbx_struct_oper_list_id", 
    "_pdbx_struct_oper_list_symmetry_operation" 
  };

  final private static byte ASSEM_ID = 0;
  final private static byte ASSEM_OPERS = 1;
  final private static byte ASSEM_LIST = 2;
  final private static String[] assemblyFields = {
    "_pdbx_struct_assembly_gen_assembly_id", 
    "_pdbx_struct_assembly_gen_oper_expression", 
    "_pdbx_struct_assembly_gen_asym_id_list" 
  };

  /*
_pdbx_struct_assembly_gen.assembly_id       1 
_pdbx_struct_assembly_gen.oper_expression   1,2,3,4 
_pdbx_struct_assembly_gen.asym_id_list      A,B,C 
# 
loop_
_pdbx_struct_oper_list.id 
_pdbx_struct_oper_list.type 
_pdbx_struct_oper_list.name 
_pdbx_struct_oper_list.symmetry_operation 
_pdbx_struct_oper_list.matrix[1][1] 
_pdbx_struct_oper_list.matrix[1][2] 
_pdbx_struct_oper_list.matrix[1][3] 
_pdbx_struct_oper_list.vector[1] 
_pdbx_struct_oper_list.matrix[2][1] 
_pdbx_struct_oper_list.matrix[2][2] 
_pdbx_struct_oper_list.matrix[2][3] 
_pdbx_struct_oper_list.vector[2] 
_pdbx_struct_oper_list.matrix[3][1] 
_pdbx_struct_oper_list.matrix[3][2] 
_pdbx_struct_oper_list.matrix[3][3] 
_pdbx_struct_oper_list.vector[3] 
1 'identity operation'         1_555  x,y,z          1.0000000000  0.0000000000  0.0000000000 0.0000000000  0.0000000000  
1.0000000000  0.0000000000 0.0000000000  0.0000000000 0.0000000000 1.0000000000  0.0000000000  
2 'crystal symmetry operation' 15_556 y,x,-z+1       0.0000000000  1.0000000000  0.0000000000 0.0000000000  1.0000000000  
0.0000000000  0.0000000000 0.0000000000  0.0000000000 0.0000000000 -1.0000000000 52.5900000000 
3 'crystal symmetry operation' 10_665 -x+1,-y+1,z    -1.0000000000 0.0000000000  0.0000000000 68.7500000000 0.0000000000  
-1.0000000000 0.0000000000 68.7500000000 0.0000000000 0.0000000000 1.0000000000  0.0000000000  
4 'crystal symmetry operation' 8_666  -y+1,-x+1,-z+1 0.0000000000  -1.0000000000 0.0000000000 68.7500000000 -1.0000000000 
0.0000000000  0.0000000000 68.7500000000 0.0000000000 0.0000000000 -1.0000000000 52.5900000000 
# 

   */

  private String[] assem = null;
  private String data;
  private String key;
  
  @Override
  public void processData(String key) throws Exception {
    if (key.startsWith("_pdbx_entity_nonpoly"))
      processDataNonpoly();
    else if (key.startsWith("_pdbx_struct_assembly_gen"))
      processDataAssemblyGen();
  }
  
  private void processDataNonpoly() throws Exception {
    if (hetatmData == null)
      hetatmData = new String[3];
    for (int i = nonpolyFields.length; --i >= 0;)
      if (cr.key.equals(nonpolyFields[i])) {
        hetatmData[i] = cr.data;
        break;
      }
    if (hetatmData[NONPOLY_NAME] == null || hetatmData[NONPOLY_COMP_ID] == null)
      return;
    addHetero(hetatmData[NONPOLY_COMP_ID], hetatmData[NONPOLY_NAME]);
    hetatmData = null;
  }

  private void processDataAssemblyGen() throws Exception {
    data = cr.data;
    key = cr.key;
    if (assem == null)
      assem = new String[3];
    if (key.indexOf("assembly_id") >= 0)
      assem[ASSEM_ID] = data = cr.tokenizer.fullTrim(data);
    else if (key.indexOf("oper_expression") >= 0)
      assem[ASSEM_OPERS] = data = cr.tokenizer.fullTrim(data);
    else if (key.indexOf("asym_id_list") >= 0)
      assem[ASSEM_LIST] = data = cr.tokenizer.fullTrim(data);
    if (assem[0] != null && assem[1] != null && assem[2] != null)
      addAssembly();
  }

  private boolean processAssemblyGenBlock() throws Exception {
    parseLoopParameters(assemblyFields);
    while (cr.tokenizer.getData()) {
      assem = new String[3];
      int count = 0;
      int p;
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (p = fieldProperty(i)) {
        case ASSEM_ID:
        case ASSEM_OPERS:
        case ASSEM_LIST:
          count++;
          assem[p] = field;
          break;
        }
      }
      if (count == 3)
        addAssembly();
    }
    assem = null;
    return true;
  }

  private void addAssembly() throws Exception {
    String id = assem[ASSEM_ID];
    int iMolecule = cr.parseIntStr(id);
    String list = assem[ASSEM_LIST];
    cr.appendLoadNote("found biomolecule " + id + ": " + list);
    if (!cr.checkFilterKey("ASSEMBLY " + id + ";"))
      return;
    if (vBiomolecules == null) {
      vBiomolecules = new  List<Map<String,Object>>();
    }
    Map<String, Object> info = new Hashtable<String, Object>();
    info.put("name", "biomolecule " + id);
    info.put("molecule", iMolecule == Integer.MIN_VALUE ? id : Integer.valueOf(iMolecule));
    info.put("assemblies", "$" + list.replace(',', '$'));
    info.put("operators", decodeAssemblyOperators(assem[ASSEM_OPERS]));
    info.put("biomts", new  List<M4>());
    thisBiomolecule = info;
    Logger.info("assembly " + id + " operators " + assem[ASSEM_OPERS] + " ASYM_IDs " + assem[ASSEM_LIST]);
    vBiomolecules.addLast(info);
    assem = null;
  }

  private String decodeAssemblyOperators(String ops) {
    
//    Identifies the operation of collection of operations 
//    from category PDBX_STRUCT_OPER_LIST.  
//
//    Operation expressions may have the forms:
//
//     (1)        the single operation 1
//     (1,2,5)    the operations 1, 2, 5
//     (1-4)      the operations 1,2,3 and 4
//     (1,2)(3,4) the combinations of operations
//                3 and 4 followed by 1 and 2 (i.e.
//                the cartesian product of parenthetical
//                groups applied from right to left)
    int pt = ops.indexOf(")(");
    if (pt >= 0)
      return crossBinary(decodeAssemblyOperators(ops.substring(0, pt + 1)),decodeAssemblyOperators(ops.substring(pt + 1)));
    if (ops.startsWith("(")) {
      if (ops.indexOf("-") >= 0)
        ops = Escape.uB("({" + ops.substring(1, ops.length() - 1).replace('-', ':') + "})").toString();
      ops = PT.simpleReplace(ops, " ", "");
      ops = ops.substring(1, ops.length() - 1);
    }
    return ops;
  }

  private String crossBinary(String ops1,
                             String ops2) {
    SB sb = new SB();
    String[] opsLeft = PT.split(ops1, ",");
    String[] opsRight = PT.split(ops2, ",");
    for (int i = 0; i < opsLeft.length; i++)
      for (int j = 0; j < opsRight.length; j++)
        sb.append(",").append(opsLeft[i]).append("|").append(opsRight[j]);
    //System.out.println((ops1 + "\n" + ops2 + "\n" + sb.toString()).length());
    return sb.toString().substring(1);
  }

  private boolean processStructOperListBlock() throws Exception {
    parseLoopParameters(operFields);
    float[] m = new float[16];
    m[15] = 1;
    while (cr.tokenizer.getData()) {
      int count = 0;
      String id = null;
      String xyz = null;
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        int p = fieldProperty(i);
        switch (p) {
        case NONE :
          break;
        case OPER_ID:
          id = field;
          break;
        case OPER_XYZ:
          xyz = field;
          break;
        default:
          m[p] = cr.parseFloatStr(field);
          ++count;
        }
      }
      if (id != null && (count == 12 || xyz != null && cr.symmetry != null)) {
        Logger.info("assembly operator " + id + " " + xyz);
        M4 m4 = new M4();
        if (count != 12) {
          cr.symmetry.getMatrixFromString(xyz, m, false, 0);
          m[3] *= cr.symmetry.getUnitCellInfoType(SimpleUnitCell.INFO_A) / 12;
          m[7] *= cr.symmetry.getUnitCellInfoType(SimpleUnitCell.INFO_B) / 12;
          m[11] *= cr.symmetry.getUnitCellInfoType(SimpleUnitCell.INFO_C) / 12;
        }
        m4.setA(m, 0);
        if (htBiomts == null)
          htBiomts = new Hashtable<String, M4>();
        htBiomts.put(id, m4);
      }
    }
    return true;
  }


  ////////////////////////////////////////////////////////////////
  // HETATM identity
  ////////////////////////////////////////////////////////////////

  final private static byte NONPOLY_ENTITY_ID = 0;
  final private static byte NONPOLY_NAME = 1;
  final private static byte NONPOLY_COMP_ID = 2;

  final private static String[] nonpolyFields = { 
      "_pdbx_entity_nonpoly_entity_id",
      "_pdbx_entity_nonpoly_name", 
      "_pdbx_entity_nonpoly_comp_id", 
  };
  
  /**
   * 
   * optional nonloop format -- see 1jsa.cif
   * 
   */
  private String[] hetatmData;

  private String field;

  private char firstChar;

  
  ////////////////////////////////////////////////////////////////
  // HETATM identity
  ////////////////////////////////////////////////////////////////
  
  final private static byte CHEM_COMP_ID = 0;
  final private static byte CHEM_COMP_NAME = 1;

  final private static String[] chemCompFields = { 
      "_chem_comp_id",
      "_chem_comp_name",
  };
  

  /**
   * 
   * a general name definition field. Not all hetero
   * @return true if successful; false to skip
   * 
   * @throws Exception
   */
  private boolean processChemCompLoopBlock() throws Exception {
    parseLoopParameters(chemCompFields);
    while (cr.tokenizer.getData()) {
      String groupName = null;
      String hetName = null;
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case NONE:
          break;
        case CHEM_COMP_ID:
          groupName = field;
          break;
        case CHEM_COMP_NAME:
          hetName = field;
          break;
        }
      }
      if (groupName != null && hetName != null)
        addHetero(groupName, hetName);
    }
    return true;
  }

  /**
   * 
   * a HETERO name definition field. Maybe not all hetero? nonpoly?

   * @return true if successful; false to skip
   * 
   * @throws Exception
   */
  private boolean processNonpolyLoopBlock() throws Exception {
    parseLoopParameters(nonpolyFields);
    while (cr.tokenizer.getData()) {
      String groupName = null;
      String hetName = null;
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case NONE:
        case NONPOLY_ENTITY_ID:
          break;
        case NONPOLY_COMP_ID:
          groupName = field;
          break;
        case NONPOLY_NAME:
          hetName = field;
          break;
        }
      }
      if (groupName == null || hetName == null)
        return false;
      addHetero(groupName, hetName);
    }
    return true;
  }

  private Map<String, String> htHetero;

  private int propertyCount;

  private int[] fieldOf;

  private void addHetero(String groupName, String hetName) {
    if (!JmolAdapter.isHetero(groupName))
      return;
    if (htHetero == null)
      htHetero = new Hashtable<String, String>();
    htHetero.put(groupName, hetName);
    if (Logger.debugging) {
      Logger.debug("hetero: " + groupName + " = " + hetName);
    }
  }
  
  ////////////////////////////////////////////////////////////////
  // helix and turn structure data
  ////////////////////////////////////////////////////////////////

  final private static byte CONF_TYPE_ID = 0;
  final private static byte BEG_ASYM_ID = 1;
  final private static byte BEG_SEQ_ID = 2;
  final private static byte BEG_INS_CODE = 3;
  final private static byte END_ASYM_ID = 4;
  final private static byte END_SEQ_ID = 5;
  final private static byte END_INS_CODE = 6;
  final private static byte STRUCT_ID = 7;
  final private static byte SERIAL_NO = 8;
  final private static byte HELIX_CLASS = 9;


  final private static String[] structConfFields = { 
      "_struct_conf_conf_type_id",
      "_struct_conf_beg_auth_asym_id", 
      "_struct_conf_beg_auth_seq_id",
      "_struct_conf_pdbx_beg_pdb_ins_code",
      "_struct_conf_end_auth_asym_id", 
      "_struct_conf_end_auth_seq_id",
      "_struct_conf_pdbx_end_pdb_ins_code",
      "_struct_conf_id", 
      "_struct_conf_pdbx_pdb_helix_id", 
      "_struct_conf_pdbx_pdb_helix_class"
  };

  /**
   * identifies ranges for HELIX and TURN
   * 
   * @return true if successful; false to skip
   * @throws Exception
   */
  private boolean processStructConfLoopBlock() throws Exception {
    parseLoopParameters(structConfFields);
    for (int i = propertyCount; --i >= 0;)
      if (fieldOf[i] == NONE) {
        Logger.warn("?que? missing property: " + structConfFields[i]);
        return false;
      }
    while (cr.tokenizer.getData()) {
      Structure structure = new Structure(-1, EnumStructure.HELIX, EnumStructure.HELIX, null, 0, 0);
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case NONE:
          break;
        case CONF_TYPE_ID:
          if (field.startsWith("TURN"))
            structure.structureType = structure.substructureType = EnumStructure.TURN;
          else if (!field.startsWith("HELX"))
            structure.structureType = structure.substructureType = EnumStructure.NONE;
          break;
        case BEG_ASYM_ID:
          structure.startChainStr = field;
          structure.startChainID = cr.viewer.getChainID(field);
          break;
        case BEG_SEQ_ID:
          structure.startSequenceNumber = cr.parseIntStr(field);
          break;
        case BEG_INS_CODE:
          structure.startInsertionCode = firstChar;
          break;
        case END_ASYM_ID:
          structure.endChainStr = field;
          structure.endChainID = cr.viewer.getChainID(field);
          break;
        case END_SEQ_ID:
          structure.endSequenceNumber = cr.parseIntStr(field);
          break;
        case HELIX_CLASS:
          structure.substructureType = Structure.getHelixType(cr.parseIntStr(field));
          break;
        case END_INS_CODE:
          structure.endInsertionCode = firstChar;
          break;
        case STRUCT_ID:
          structure.structureID = field;
          break;
        case SERIAL_NO:
          structure.serialID = cr.parseIntStr(field);
          break;
        }
      }
      cr.atomSetCollection.addStructure(structure);
    }
    return true;
  }
  ////////////////////////////////////////////////////////////////
  // sheet structure data
  ////////////////////////////////////////////////////////////////

  final private static byte SHEET_ID = 0;
  final private static byte STRAND_ID = 7;

  final private static String[] structSheetRangeFields = {
    "_struct_sheet_range_sheet_id",  //unused placeholder
    "_struct_sheet_range_beg_auth_asym_id",
    "_struct_sheet_range_beg_auth_seq_id",
    "_struct_sheet_range_pdbx_beg_pdb_ins_code",
    "_struct_sheet_range_end_auth_asym_id",
    "_struct_sheet_range_end_auth_seq_id",
    "_struct_sheet_range_pdbx_end_pdb_ins_code", 
    "_struct_sheet_range_id",
  };

  /**
   * 
   * identifies sheet ranges
   * @return true if successful; false to skip
   * 
   * @throws Exception
   */
  private boolean processStructSheetRangeLoopBlock() throws Exception {
    parseLoopParameters(structSheetRangeFields);
    for (int i = propertyCount; --i >= 0;)
      if (fieldOf[i] == NONE) {
        Logger.warn("?que? missing property:" + structSheetRangeFields[i]);
        return false;
      }
    while (cr.tokenizer.getData()) {
      Structure structure = new Structure(-1, EnumStructure.SHEET, EnumStructure.SHEET, null, 0, 0);
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case BEG_ASYM_ID:
          structure.startChainID = cr.viewer.getChainID(field);
          break;
        case BEG_SEQ_ID:
          structure.startSequenceNumber = cr.parseIntStr(field);
          break;
        case BEG_INS_CODE:
          structure.startInsertionCode = firstChar;
          break;
        case END_ASYM_ID:
          structure.endChainID = cr.viewer.getChainID(field);
          break;
        case END_SEQ_ID:
          structure.endSequenceNumber = cr.parseIntStr(field);
          break;
        case END_INS_CODE:
          structure.endInsertionCode = firstChar;
          break;
        case SHEET_ID:
          structure.strandCount = 1;
          structure.structureID = field;
          break;
        case STRAND_ID:
          structure.serialID = cr.parseIntStr(field);
          break;
        }
      }
      cr.atomSetCollection.addStructure(structure);
    }
    return true;
  }

  private void parseLoopParameters(String[] fields) throws Exception {
    cr.parseLoopParameters(fields);
    propertyCount = fields.length;
    fieldOf = cr.fieldOf;
  }

  final private static byte SITE_ID = 0;
  final private static byte SITE_COMP_ID = 1;
  final private static byte SITE_ASYM_ID = 2;
  final private static byte SITE_SEQ_ID = 3;
  final private static byte SITE_INS_CODE = 4; //???

  final private static String[] structSiteRangeFields = {
    "_struct_site_gen_site_id",  
    "_struct_site_gen_auth_comp_id", 
    "_struct_site_gen_auth_asym_id", 
    "_struct_site_gen_auth_seq_id",  
    "_struct_site_gen_label_alt_id",  //should be an insertion code, not an alt ID? 
  };

  
//  loop_
//  _struct_site_gen.id 
//  _struct_site_gen.site_id 
//  _struct_site_gen.pdbx_num_res 
//  _struct_site_gen.label_comp_id 
//  _struct_site_gen.label_asym_id 
//  _struct_site_gen.label_seq_id 
//  _struct_site_gen.auth_comp_id 
//  _struct_site_gen.auth_asym_id 
//  _struct_site_gen.auth_seq_id 
//  _struct_site_gen.label_atom_id 
//  _struct_site_gen.label_alt_id 
//  _struct_site_gen.symmetry 
//  _struct_site_gen.details 
//  1 CAT 5 GLN A 92  GLN A 92  . . ? ? 
//  2 CAT 5 GLU A 58  GLU A 58  . . ? ? 
//  3 CAT 5 HIS A 40  HIS A 40  . . ? ? 
//  4 CAT 5 TYR A 38  TYR A 38  . . ? ? 
//  5 CAT 5 PHE A 100 PHE A 100 . . ? ? 
//  # 


  /**
   * 
   * identifies structure sites
   * @return true if successful; false to skip
   * 
   * @throws Exception
   */
  private boolean processStructSiteBlock() throws Exception {
    parseLoopParameters(structSiteRangeFields);
    for (int i = 3; --i >= 0;)
      if (fieldOf[i] == NONE) {
        Logger.warn("?que? missing property: " + structSiteRangeFields[i]);
        return false;
      }
    String siteID = "";
    String seqNum = "";
    String insCode = "";
    String chainID = "";
    String resID = "";
    String group = "";
    Map<String, Object> htSite = null;
    htSites = new Hashtable<String, Map<String, Object>>();
    while (cr.tokenizer.getData()) {
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case SITE_ID:
          if (group != "") {
            String groups = (String) htSite.get("groups");
            groups += (groups.length() == 0 ? "" : ",") + group;
            group = "";
            htSite.put("groups", groups);
          }
          siteID = field;
          htSite = htSites.get(siteID);
          if (htSite == null) {
            htSite = new Hashtable<String, Object>();
            //htSite.put("seqNum", "site_" + (++siteNum));
            htSite.put("groups", "");
            htSites.put(siteID, htSite);
          }
          seqNum = "";
          insCode = "";
          chainID = "";
          resID = "";
          break;
        case SITE_COMP_ID:
          resID = field;
          break;
        case SITE_ASYM_ID:
          chainID = field;
          break;
        case SITE_SEQ_ID:
          seqNum = field;
          break;
        case SITE_INS_CODE: //optional
          insCode = field;
          break;
        }
        if (seqNum != "" && resID != "")
          group = "[" + resID + "]" + seqNum
            + (insCode.length() > 0 ?  "^" + insCode : "")
            + (chainID.length() > 0 ? ":" + chainID : "");
      }      
    }
    if (group != "") {
      String groups = (String) htSite.get("groups");
      groups += (groups.length() == 0 ? "" : ",") + group;
      group = "";
      htSite.put("groups", groups);
    }
    return true;
  }

  private int fieldProperty(int i) {
    return ((field = cr.tokenizer.loopData[i]).length() > 0 
        && (firstChar = field.charAt(0)) != '\0' ? 
            cr.propertyOf[i] : NONE);
  }

  private void setBiomolecules(Map<String, Object> biomolecule) {
    if (!isBiomolecule || assemblyIdAtoms == null && chainAtomCounts == null)
      return;
    M4 mident = M4.newM(null);
    String[] ops = PT.split((String) biomolecule.get("operators"), ",");
    String assemblies = (String) biomolecule.get("assemblies");
    List<M4> biomts = new List<M4>();
    biomolecule.put("biomts", biomts);
    biomts.addLast(mident);
    for (int j = 0; j < ops.length; j++) {
      M4 m = getOpMatrix(ops[j]);
      if (m != null && !m.equals(mident))
        biomts.addLast(m);
    }
    BS bsAll = new BS();
    P3 sum = new P3();
    int count = 0;
    int nAtoms = 0;
    String[] ids = PT.split(assemblies, "$");
    for (int j = 1; j < ids.length; j++) {
        String id = ids[j];
        if (assemblyIdAtoms != null) {
          BS bs = assemblyIdAtoms.get(id);
          if (bs != null) {
            System.out.println(id + " " + bs.cardinality());
            bsAll.or(bs);
          }
        } else if (isCourseGrained) {
          P3 asum = chainAtomMap.get(id);
          int c = chainAtomCounts.get(id)[0];
          if (asum != null) {
            if (bySymop) {
              sum.add(asum);
              count += c;
            } else {
              createParticle(id);
              nAtoms++;
            }
          }
        }
      }
    if (isCourseGrained) {
      if (bySymop) {
        nAtoms = 1;
        Atom a1 = new Atom();
        a1.setT(sum);
        a1.scale(1f / count);
        a1.radius = 16;
      }
    } else {
      nAtoms = bsAll.cardinality();
      if (nAtoms < cr.atomSetCollection.getAtomCount())
        cr.atomSetCollection.bsAtoms = bsAll;
    }
    biomolecule.put("atomCount", Integer.valueOf(nAtoms * ops.length));
  }

  private void createParticle(String id) {
    P3 asum = chainAtomMap.get(id);
    int c = chainAtomCounts.get(id)[0];
    Atom a = new Atom();
    a.setT(asum);
    a.scale(1f/c);
    a.elementSymbol = "Pt";
    a.chainID = cr.viewer.getChainID(id);
    a.radius = 16;
    cr.atomSetCollection.addAtom(a);
  }

  private M4 getOpMatrix(String ops) {
    if (htBiomts == null)
      return M4.newM(null);
    int pt = ops.indexOf("|");
    if (pt >= 0) {
      M4 m = M4.newM(htBiomts.get(ops.substring(0, pt)));
      m.mulM4(htBiomts.get(ops.substring(pt+1)));
      return m;
    }
    return htBiomts.get(ops);
  }


  ////////////////////////////////////////////////////////////////
  // bond data
  ////////////////////////////////////////////////////////////////

  final private static byte CHEM_COMP_BOND_ATOM_ID_1 = 0;
  final private static byte CHEM_COMP_BOND_ATOM_ID_2 = 1;
  final private static byte CHEM_COMP_BOND_VALUE_ORDER = 2;
  final private static byte CHEM_COMP_BOND_AROMATIC_FLAG = 3;
  final private static String[] chemCompBondFields = {
    "_chem_comp_bond_atom_id_1",
    "_chem_comp_bond_atom_id_2",
    "_chem_comp_bond_value_order",
    "_chem_comp_bond_pdbx_aromatic_flag", 
  };
  
  private boolean processLigandBondLoopBlock() throws Exception {
    parseLoopParameters(chemCompBondFields);
    for (int i = propertyCount; --i >= 0;)
      if (fieldOf[i] == NONE) {
        Logger.warn("?que? missing property: " + chemCompBondFields[i]);
        return false;
      }
    int order = 0;
    boolean isAromatic = false;
    while (cr.tokenizer.getData()) {
      int atomIndex1 = -1;
      int atomIndex2 = -1;
      order = 0;
      isAromatic = false;
      for (int i = 0; i < cr.tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case CHEM_COMP_BOND_ATOM_ID_1:
          atomIndex1 = cr.atomSetCollection.getAtomIndexFromName(field);
          break;
        case CHEM_COMP_BOND_ATOM_ID_2:
          atomIndex2 = cr.atomSetCollection.getAtomIndexFromName(field);
          break;
        case CHEM_COMP_BOND_AROMATIC_FLAG:
          isAromatic = (field.charAt(0) == 'Y');
          break;
        case CHEM_COMP_BOND_VALUE_ORDER:
          order = cr.getBondOrder(field);
          break;
        }
      }
      if (atomIndex1 < 0 || atomIndex2 < 0)
        continue;
      if (isAromatic)
        switch (order) {
        case JmolAdapter.ORDER_COVALENT_SINGLE:
          order = JmolAdapter.ORDER_AROMATIC_SINGLE;
          break;
        case JmolAdapter.ORDER_COVALENT_DOUBLE:
          order = JmolAdapter.ORDER_AROMATIC_DOUBLE;
          break;
        }
      cr.atomSetCollection.addNewBondWithOrder(atomIndex1, atomIndex2, order);
    }
    return true;
  }

  @Override
  public boolean checkAtom(Atom atom, String assemblyId, int index) {
    if (byChain && !isBiomolecule) {
      if (thisChain != atom.chainID) {
        thisChain = atom.chainID;
        String id = "" + atom.chainID;
        chainSum = chainAtomMap.get(id);
        if (chainSum == null) {
          chainAtomMap.put(id, chainSum = new P3());
          chainAtomCounts.put(id, chainAtomCount = new int[1]);
        }
      }
      chainSum.add(atom);
      chainAtomCount[0]++;
      return false;
    }
    if (isBiomolecule && isCourseGrained) {
      P3 sum = chainAtomMap.get(assemblyId);
      if (sum == null) {
        chainAtomMap.put(assemblyId, sum = new P3());
        chainAtomCounts.put(assemblyId, new int[1]);
      }
      chainAtomCounts.get(assemblyId)[0]++;
      sum.add(atom);
      return false;
    }
    if (assemblyId != null) {
      if (assemblyIdAtoms == null)
        assemblyIdAtoms = new Hashtable<String, BS>();
      BS bs = assemblyIdAtoms.get(assemblyId);
      if (bs == null)
        assemblyIdAtoms.put(assemblyId, bs = new BS());
      bs.set(index);
    }
    if (atom.isHetero && htHetero != null) {
      cr.atomSetCollection.setAtomSetAuxiliaryInfo("hetNames", htHetero);
      cr.atomSetCollection.setAtomSetCollectionAuxiliaryInfo("hetNames",
          htHetero);
      htHetero = null;
    }
    return true;
  }

  @Override
  public boolean processPDBLoops(String str) throws Exception {
    if (str.startsWith("_pdbx_struct_oper_list"))
      return processStructOperListBlock();
    if (str.startsWith("_pdbx_struct_assembly_gen"))
      return processAssemblyGenBlock();

    if (isCourseGrained)
      return false;

    if (str.startsWith("_struct_site_gen"))
      return processStructSiteBlock();
    if (str.startsWith("_chem_comp_bond"))
      return processLigandBondLoopBlock();
    if (str.startsWith("_chem_comp"))
      return processChemCompLoopBlock();
    if (str.startsWith("_pdbx_entity_nonpoly"))
      return processNonpolyLoopBlock();
    if (str.startsWith("_struct_conf")
        && !str.startsWith("_struct_conf_type"))
      return processStructConfLoopBlock();
    if (str.startsWith("_struct_sheet_range"))
      return processStructSheetRangeLoopBlock();
    return false;
  }
}
