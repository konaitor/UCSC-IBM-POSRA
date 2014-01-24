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

import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.Atom;
import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.adapter.smarter.MMCifInterface;
import org.jmol.adapter.smarter.MSCifInterface;
import org.jmol.api.Interface;
import org.jmol.api.JmolAdapter;
import org.jmol.api.JmolLineReader;
import org.jmol.api.SymmetryInterface;
import org.jmol.io.CifDataReader;
import org.jmol.io.JmolBinary;
import org.jmol.java.BS;

import org.jmol.script.T;
import javajs.util.List;
import java.util.Hashtable;
import java.util.Map;

import org.jmol.util.Logger;

import javajs.util.P3;
import javajs.util.PT;
import javajs.util.V3;

/**
 * A true line-free CIF file reader for CIF files.
 * 
 * Subclasses of CIF -- mmCIF/PDBx and msCIF -- are initialized from here.
 * 
 * <p>
 * <a href='http://www.iucr.org/iucr-top/cif/'>
 * http://www.iucr.org/iucr-top/cif/ </a>
 * 
 * <a href='http://www.iucr.org/iucr-top/cif/standard/cifstd5.html'>
 * http://www.iucr.org/iucr-top/cif/standard/cifstd5.html </a>
 * 
 * @author Miguel, Egon, and Bob (hansonr@stolaf.edu)
 * 
 *         symmetry added by Bob Hanson:
 * 
 *         setSpaceGroupName() setSymmetryOperator() setUnitCellItem()
 *         setFractionalCoordinates() setAtomCoord()
 *         applySymmetryAndSetTrajectory()
 * 
 */
public class CifReader extends AtomSetCollectionReader implements
    JmolLineReader {

  private MSCifInterface mr;
  private MMCifInterface pr;

  CifDataReader tokenizer = new CifDataReader(this);

  private boolean isMolecular;
  private boolean filterAssembly;
  private boolean allowRotations = true;
  private boolean checkSpecial = true;
  private boolean readIdeal = true;
  private int configurationPtr = Integer.MIN_VALUE;

  private String thisDataSetName = "";
  private String chemicalName = "";
  private String thisStructuralFormula = "";
  private String thisFormula = "";
  private boolean iHaveDesiredModel;
  private boolean isPDB;
  private boolean isPDBX;
  private String molecularType = "GEOM_BOND default";
  private char lastAltLoc = '\0';
  private boolean haveAromatic;
  private int conformationIndex;
  private int nMolecular = 0;
  private String appendedData;
  private boolean skipping;
  private int nAtoms;

  private String auditBlockCode;
  private String lastSpaceGroupName;

  private boolean modulated;
  private boolean lookingForPDB = true;
  private boolean isCourseGrained;

  @Override
  public void initializeReader() throws Exception {
    initializeReaderCif();
  }

  protected void initializeReaderCif() throws Exception {
    appendedData = (String) htParams.get("appendedData");
    String conf = getFilter("CONF ");
    if (conf != null)
      configurationPtr = parseIntStr(conf);
    isMolecular = checkFilterKey("MOLECUL") && !checkFilterKey("BIOMOLECULE"); // molecular; molecule
    readIdeal = !checkFilterKey("NOIDEAL");
    filterAssembly = checkFilterKey("$");

    if (isMolecular) {
      if (!doApplySymmetry) {
        doApplySymmetry = true;
        latticeCells[0] = 1;
        latticeCells[1] = 1;
        latticeCells[2] = 1;
      }
      molecularType = "filter \"MOLECULAR\"";
    }
    checkSpecial = !checkFilterKey("NOSPECIAL");
    atomSetCollection.setCheckSpecial(checkSpecial);
    allowRotations = !checkFilterKey("NOSYM");
    readCifData();
    continuing = false;
  }

  private void readCifData() throws Exception {

    /*
     * Modified for 10.9.64 9/23/06 by Bob Hanson to remove as much as possible
     * of line dependence. a loop could now go:
     * 
     * blah blah blah loop_ _a _b _c 0 1 2 0 3 4 0 5 6 loop_......
     * 
     * we don't actually check that any skipped loop has the proper number of
     * data points --- some multiple of the number of data keys -- but other
     * than that, we are checking here for proper CIF syntax, and Jmol will
     * report if it finds data where a key is supposed to be.
     */
    line = "";
    while ((key = tokenizer.peekToken()) != null)
      if (!readAllData())
        break;
    if (appendedData != null) {
      tokenizer = new CifDataReader(JmolBinary.getBR(appendedData));
      while ((key = tokenizer.peekToken()) != null)
        if (!readAllData())
          break;
    }
  }

  @Override
  public String readNextLine() throws Exception {
    // from CifDataReader
    if (readLine() != null && line.indexOf("#jmolscript:") >= 0)
      checkCurrentLineForScript();
    return line;
  }

  private boolean readAllData() throws Exception {
    if (key.startsWith("data_")) {
      //      if (isPDBX) {
      //        tokenizer.getTokenPeeked();
      //        return true;
      //      }
      if (iHaveDesiredModel)
        return false;
      newModel(++modelNumber);
      if (!skipping)
        processDataParameter();
      nAtoms = atomSetCollection.getAtomCount();
      return true;
    }
    if (lookingForPDB && !isPDBX && key.indexOf(".pdb") >= 0)
      initializeMMCIF();
    if (skipping && key.equals("_audit_block_code")) {
      iHaveDesiredModel = false;
      skipping = false;
    }

    if (key.startsWith("loop_")) {
      if (skipping) {
        tokenizer.getTokenPeeked();
        skipLoop();
      } else {
        processLoopBlock();
      }
      return true;
    }
    // global_ and stop_ are reserved STAR keywords
    // see http://www.iucr.org/iucr-top/lists/comcifs-l/msg00252.html
    // http://www.iucr.org/iucr-top/cif/spec/version1.1/cifsyntax.html#syntax

    // stop_ is not allowed, because nested loop_ is not allowed
    // global_ is a reserved STAR word; not allowed in CIF
    // ah, heck, let's just flag them as CIF ERRORS
    /*
     * if (key.startsWith("global_") || key.startsWith("stop_")) {
     * tokenizer.getTokenPeeked(); continue; }
     */
    if (key.indexOf("_") != 0) {
      Logger.warn("CIF ERROR ? should be an underscore: " + key);
      tokenizer.getTokenPeeked();
    } else if (!getData()) {
      return true;
    }

    if (!skipping) {
      key = fixKey(key);
      if (key.startsWith("_chemical_name") || key.equals("_chem_comp_name")) {
        processChemicalInfo("name");
      } else if (key.startsWith("_chemical_formula_structural")) {
        processChemicalInfo("structuralFormula");
      } else if (key.startsWith("_chemical_formula_sum")
          || key.equals("_chem_comp_formula")) {
        processChemicalInfo("formula");
      } else if (key.equals("_cell_modulation_dimension")) {
        initializeMSCIF(data);
      } else if (key.equals("_citation_title")) {
        appendLoadNote("TITLE: " + tokenizer.fullTrim(data));
      } else if (key.startsWith("_cell_")) {
        processCellParameter();
      } else if (key.startsWith("_symmetry_space_group_name_h-m")
          || key.startsWith("_symmetry_space_group_name_hall")
          || key.contains("_ssg_name")) {
        processSymmetrySpaceGroupName();
      } else if (key.startsWith("_atom_sites_fract_tran")) {
        processUnitCellTransformMatrix();
      } else if (key.equals("_audit_block_code")) {
        auditBlockCode = tokenizer.fullTrim(data).toUpperCase();
        appendLoadNote(auditBlockCode);
        if (htAudit != null && auditBlockCode.contains("_MOD_")) {
          String key = PT.simpleReplace(auditBlockCode, "_MOD_",
              "_REFRNCE_");
          if ((atomSetCollection.symmetry = (SymmetryInterface) htAudit
              .get(key)) != null) {
            notionalUnitCell = atomSetCollection.symmetry.getNotionalUnitCell();
            iHaveUnitCell = true;
          }
        } else if (htAudit != null && symops != null) {
          for (int i = 0; i < symops.size(); i++)
            setSymmetryOperator(symops.get(i));
        }

        if (lastSpaceGroupName != null)
          setSpaceGroupName(lastSpaceGroupName);
      } else if (pr != null) {
        pr.processData(key);
      }
    }
    return true;
  }

  private void initializeMMCIF() {
    isPDBX = true;
    lookingForPDB = false;
    if (pr == null)
      pr = (MMCifInterface) Interface
          .getInterface("org.jmol.adapter.readers.cif.MMCifReader");
    isCourseGrained = pr.initialize(this);
  }

  private void initializeMSCIF(String data) throws Exception {
    if (mr == null)
      ms = mr = (MSCifInterface) Interface
          .getInterface("org.jmol.adapter.readers.cif.MSCifReader");
    modulated = (mr.initialize(this, data) > 0);
  }

  private String fixKey(String key) {
    return PT.simpleReplace(key, ".", "_").toLowerCase();
  }

  protected void newModel(int modelNo) throws Exception {
    if (isPDB)
      setIsPDB();
    skipping = !doGetModel(modelNumber = modelNo, null);
    if (skipping) {
      tokenizer.getTokenPeeked();
      return;
    }
    chemicalName = "";
    thisStructuralFormula = "";
    thisFormula = "";
    if (isCourseGrained)
      atomSetCollection.setAtomSetAuxiliaryInfo("courseGrained", Boolean.TRUE);
    if (nAtoms == atomSetCollection.getAtomCount())
      // we found no atoms -- must revert
      atomSetCollection.removeCurrentAtomSet();
    else
      applySymmetryAndSetTrajectory();
    iHaveDesiredModel = isLastModel(modelNumber);
  }

  @Override
  protected void finalizeReader() throws Exception {
    if (pr != null)
      pr.finalizeReader(nAtoms);
    else
      applySymmetryAndSetTrajectory();
    int n = atomSetCollection.getAtomSetCount();
    if (n > 1)
      atomSetCollection.setCollectionName("<collection of " + n + " models>");
    finalizeReaderASCR();
    String header = tokenizer.getFileHeader();
    if (header.length() > 0)
      atomSetCollection.setAtomSetCollectionAuxiliaryInfo("fileHeader", header);
    if (haveAromatic)
      addJmolScript("calculate aromatic");
  }

  @Override
  protected void doPreSymmetry() {
    if (mr != null)
      mr.setModulation(false);
  }

  @Override
  public void applySymmetryAndSetTrajectory() throws Exception {
    // This speeds up calculation, because no crosschecking
    // No special-position atoms in mmCIF files, because there will
    // be no center of symmetry, no rotation-inversions, 
    // no atom-centered rotation axes, and no mirror or glide planes.
    if (isPDB)
      atomSetCollection.setCheckSpecial(false);
    boolean doCheck = doCheckUnitCell && !isPDB;
    SymmetryInterface sym = applySymTrajASCR();
    if (mr != null) {
      mr.setModulation(true);
      mr.finalizeModulation();
    }
    if (auditBlockCode != null && auditBlockCode.contains("REFRNCE")
        && sym != null) {
      if (htAudit == null)
        htAudit = new Hashtable<String, Object>();
      htAudit.put(auditBlockCode, sym);
    }
    if (doCheck && (bondTypes.size() > 0 || isMolecular))
      setBondingAndMolecules();
    atomSetCollection.setAtomSetAuxiliaryInfo("fileHasUnitCell", Boolean.TRUE);
    atomSetCollection.symmetry = null;
  }

  ////////////////////////////////////////////////////////////////
  // processing methods
  ////////////////////////////////////////////////////////////////

  private Hashtable<String, Object> htAudit;
  private List<String> symops;

  /**
   * initialize a new atom set
   * 
   */
  private void processDataParameter() {
    bondTypes.clear();
    tokenizer.getTokenPeeked();
    thisDataSetName = (key.length() < 6 ? "" : key.substring(5));
    lookingForPDB = (thisDataSetName.length() > 0 && !thisDataSetName
        .equals("global"));
    if (thisDataSetName.length() > 0)
      nextAtomSet();
    if (Logger.debugging)
      Logger.debug(key);
  }

  private void nextAtomSet() {
    atomSetCollection.setAtomSetAuxiliaryInfo("isCIF", Boolean.TRUE);
    if (atomSetCollection.getCurrentAtomSetIndex() >= 0) {
      // note that there can be problems with multi-data mmCIF sets each with
      // multiple models; and we could be loading multiple files!
      atomSetCollection.newAtomSet();
    } else {
      atomSetCollection.setCollectionName(thisDataSetName);
    }
  }

  /**
   * reads some of the more interesting info into specific atomSetAuxiliaryInfo
   * elements
   * 
   * @param type
   *        "name" "formula" etc.
   * @return data
   * @throws Exception
   */
  private String processChemicalInfo(String type) throws Exception {
    if (type.equals("name")) {
      chemicalName = data = tokenizer.fullTrim(data);
      if (!data.equals("?"))
        atomSetCollection.setAtomSetCollectionAuxiliaryInfo("modelLoadNote",
            data);
    } else if (type.equals("structuralFormula")) {
      thisStructuralFormula = data = tokenizer.fullTrim(data);
    } else if (type.equals("formula")) {
      thisFormula = data = tokenizer.fullTrim(data);
    }
    if (Logger.debugging) {
      Logger.debug(type + " = " + data);
    }
    return data;
  }

  /**
   * done by AtomSetCollectionReader
   * 
   * @throws Exception
   */
  private void processSymmetrySpaceGroupName() throws Exception {
    if (key.indexOf("_ssg_name") >= 0)
      modulated = true;
    else if (modulated)
      return;
    data = tokenizer.toUnicode(data);
    setSpaceGroupName(lastSpaceGroupName = (key.indexOf("h-m") > 0 ? "HM:"
        : modulated ? "SSG:" : "Hall:") + data);
  }

  /**
   * unit cell parameters -- two options, so we use MOD 6
   * 
   * @throws Exception
   */
  private void processCellParameter() throws Exception {
    for (int i = JmolAdapter.cellParamNames.length; --i >= 0;)
      if (key.equals(JmolAdapter.cellParamNames[i])) {
        setUnitCellItem(i, parseFloatStr(data));
        return;
      }
  }

  final private static String[] TransformFields = { "x[1][1]", "x[1][2]",
      "x[1][3]", "r[1]", "x[2][1]", "x[2][2]", "x[2][3]", "r[2]", "x[3][1]",
      "x[3][2]", "x[3][3]", "r[3]", };

  /**
   * 
   * the PDB transformation matrix cartesian --> fractional
   * 
   * @throws Exception
   */
  private void processUnitCellTransformMatrix() throws Exception {
    /*
     * PDB:
     
     SCALE1       .024414  0.000000  -.000328        0.00000
     SCALE2      0.000000   .053619  0.000000        0.00000
     SCALE3      0.000000  0.000000   .044409        0.00000

     * CIF:

     _atom_sites.fract_transf_matrix[1][1]   .024414 
     _atom_sites.fract_transf_matrix[1][2]   0.000000 
     _atom_sites.fract_transf_matrix[1][3]   -.000328 
     _atom_sites.fract_transf_matrix[2][1]   0.000000 
     _atom_sites.fract_transf_matrix[2][2]   .053619 
     _atom_sites.fract_transf_matrix[2][3]   0.000000 
     _atom_sites.fract_transf_matrix[3][1]   0.000000 
     _atom_sites.fract_transf_matrix[3][2]   0.000000 
     _atom_sites.fract_transf_matrix[3][3]   .044409 
     _atom_sites.fract_transf_vector[1]      0.00000 
     _atom_sites.fract_transf_vector[2]      0.00000 
     _atom_sites.fract_transf_vector[3]      0.00000 

     */
    float v = parseFloatStr(data);
    if (Float.isNaN(v))
      return;
    for (int i = 0; i < TransformFields.length; i++) {
      if (key.indexOf(TransformFields[i]) >= 0) {
        setUnitCellItem(6 + i, v);
        return;
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // loop_ processing
  ////////////////////////////////////////////////////////////////

  String key;
  String data;

  /**
   * 
   * @return TRUE if data, even if ''; FALSE if '.' or '?' or eof.
   * 
   * @throws Exception
   */
  private boolean getData() throws Exception {
    key = tokenizer.getTokenPeeked();
    data = tokenizer.getNextToken();
    //if (Logger.debugging && data != null && data.charAt(0) != '\0')
    //Logger.debug(key  + " " + data);
    if (data == null) {
      Logger.warn("CIF ERROR ? end of file; data missing: " + key);
      return false;
    }
    return (data.length() == 0 || data.charAt(0) != '\0');
  }

  /**
   * processes loop_ blocks of interest or skips the data
   * 
   * @throws Exception
   */
  private void processLoopBlock() throws Exception {
    tokenizer.getTokenPeeked(); //loop_
    String str = tokenizer.peekToken();
    if (str == null)
      return;
    boolean isLigand = false;
    str = fixKey(str);
    if (lookingForPDB && !isPDBX && str.indexOf("_pdb") >= 0)
      initializeMMCIF();
    if (modulated
        && (str.startsWith("_cell_wave") || str.contains("fourier") || str
            .contains("_special_func"))) {
      if (!mr.processModulationLoopBlock())
        skipLoop();
      return;
    }
    if (str.startsWith("_atom_site_")
        || (isLigand = str.equals("_chem_comp_atom_comp_id"))) {
      if (!processAtomSiteLoopBlock(isLigand))
        return;
      atomSetCollection.setAtomSetName(thisDataSetName);
      atomSetCollection.setAtomSetAuxiliaryInfo("chemicalName", chemicalName);
      atomSetCollection.setAtomSetAuxiliaryInfo("structuralFormula",
          thisStructuralFormula);
      atomSetCollection.setAtomSetAuxiliaryInfo("formula", thisFormula);
      return;
    }
    if (str.startsWith("_symmetry_equiv_pos")
        || str.startsWith("_space_group_symop")
        || str.startsWith("_symmetry_ssg_equiv")) {
      if (ignoreFileSymmetryOperators) {
        Logger.warn("ignoring file-based symmetry operators");
        skipLoop();
      } else {
        processSymmetryOperationsLoopBlock();
      }
      return;
    }
    if (str.startsWith("_citation")) {
      processCitationListBlock();
      return;
    }

    if (pr != null && pr.processPDBLoops(str))
      return;

    if (str.startsWith("_atom_type")) {
      processAtomTypeLoopBlock();
      return;
    }

    if (mr != null && str.equals("_cell_subsystem_code"))
      mr.processSubsystemLoopBlock();

    if (str.startsWith("_geom_bond") && (isMolecular || !doApplySymmetry)) {
      processGeomBondLoopBlock();
      return;
    }
    skipLoop();
  }

  private int fieldProperty(int i) {
    return ((field = tokenizer.loopData[i]).length() > 0
        && (firstChar = field.charAt(0)) != '\0' ? propertyOf[i] : NONE);
  }

  String field;
  private char firstChar = '\0';
  int[] propertyOf = new int[100]; // should be enough
  int[] fieldOf = new int[100];
  String[] fields;
  private int propertyCount;

  private static Map<String, Integer> htFields = new Hashtable<String, Integer>();

  /**
   * sets up arrays and variables for tokenizer.getData()
   * 
   * @param fields
   * @throws Exception
   */
  void parseLoopParameters(String[] fields) throws Exception {
    if (fields == null) {
      // for reading full list of keys, as for matrices
      this.fields = new String[100];
    } else {
      if (!htFields.containsKey(fields[0]))
        for (int i = fields.length; --i >= 0;)
          htFields.put(fields[i], Integer.valueOf(i));
      for (int i = fields.length; --i >= 0;)
        fieldOf[i] = NONE;
      propertyCount = fields.length;
    }
    tokenizer.fieldCount = 0;
    while (true) {
      String str = tokenizer.peekToken();
      if (str == null) {
        tokenizer.fieldCount = 0;
        break;
      }
      if (str.charAt(0) != '_')
        break;
      int pt = tokenizer.fieldCount++;
      str = fixKey(tokenizer.getTokenPeeked());
      if (fields == null) {
        this.fields[propertyOf[pt] = pt] = str;
        fieldOf[pt] = pt;
        continue;
      }
      Integer iField = htFields.get(str);
      int i = (iField == null ? NONE : iField.intValue());
      if ((propertyOf[pt] = i) != NONE)
        fieldOf[i] = pt;
    }
    if (tokenizer.fieldCount > 0)
      tokenizer.loopData = new String[tokenizer.fieldCount];
  }

  /**
   * 
   * used for turning off fractional or nonfractional coord.
   * 
   * @param fieldIndex
   */
  private void disableField(int fieldIndex) {
    int i = fieldOf[fieldIndex];
    if (i != NONE)
      propertyOf[i] = NONE;
  }

  /**
   * 
   * skips all associated loop data
   * 
   * @throws Exception
   */
  void skipLoop() throws Exception {
    String str;
    while ((str = tokenizer.peekToken()) != null && str.charAt(0) == '_')
      str = tokenizer.getTokenPeeked();
    while (tokenizer.getNextDataToken() != null) {
    }
  }

  ////////////////////////////////////////////////////////////////
  // atom type data
  ////////////////////////////////////////////////////////////////

  private Map<String, Float> atomTypes;
  private List<Object[]> bondTypes = new List<Object[]>();

  private String disorderAssembly = ".";
  private String lastDisorderAssembly;

  final private static byte ATOM_TYPE_SYMBOL = 0;
  final private static byte ATOM_TYPE_OXIDATION_NUMBER = 1;

  final private static String[] atomTypeFields = { "_atom_type_symbol",
      "_atom_type_oxidation_number", };

  /**
   * 
   * reads the oxidation number and associates it with an atom name, which can
   * then later be associated with the right atom indirectly.
   * 
   * @throws Exception
   */
  private void processAtomTypeLoopBlock() throws Exception {
    parseLoopParameters(atomTypeFields);
    for (int i = propertyCount; --i >= 0;)
      if (fieldOf[i] == NONE) {
        skipLoop();
        return;
      }

    while (tokenizer.getData()) {
      String atomTypeSymbol = null;
      float oxidationNumber = Float.NaN;
      for (int i = 0; i < tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case NONE:
          break;
        case ATOM_TYPE_SYMBOL:
          atomTypeSymbol = field;
          break;
        case ATOM_TYPE_OXIDATION_NUMBER:
          oxidationNumber = parseFloatStr(field);
          break;
        }
      }
      if (atomTypeSymbol == null || Float.isNaN(oxidationNumber))
        continue;
      if (atomTypes == null)
        atomTypes = new Hashtable<String, Float>();
      atomTypes.put(atomTypeSymbol, Float.valueOf(oxidationNumber));
    }
  }

  ////////////////////////////////////////////////////////////////
  // atom site data
  ////////////////////////////////////////////////////////////////

  final private static byte NONE = -1;
  final private static byte TYPE_SYMBOL = 0;
  final private static byte LABEL = 1;
  final private static byte AUTH_ATOM = 2;
  final private static byte FRACT_X = 3;
  final private static byte FRACT_Y = 4;
  final private static byte FRACT_Z = 5;
  final private static byte CARTN_X = 6;
  final private static byte CARTN_Y = 7;
  final private static byte CARTN_Z = 8;
  final private static byte OCCUPANCY = 9;
  final private static byte B_ISO = 10;
  final private static byte COMP_ID = 11;
  final private static byte AUTH_ASYM_ID = 12;
  final private static byte SEQ_ID = 13;
  final private static byte INS_CODE = 14;
  final private static byte ALT_ID = 15;
  final private static byte GROUP_PDB = 16;
  final private static byte MODEL_NO = 17;
  final private static byte DUMMY_ATOM = 18;

  final private static byte DISORDER_GROUP = 19;
  final private static byte ANISO_LABEL = 20;
  final private static byte ANISO_MMCIF_ID = 21;
  final private static byte ANISO_U11 = 22;
  final private static byte ANISO_U22 = 23;
  final private static byte ANISO_U33 = 24;
  final private static byte ANISO_U12 = 25;
  final private static byte ANISO_U13 = 26;
  final private static byte ANISO_U23 = 27;
  final private static byte ANISO_MMCIF_U11 = 28;
  final private static byte ANISO_MMCIF_U22 = 29;
  final private static byte ANISO_MMCIF_U33 = 30;
  final private static byte ANISO_MMCIF_U12 = 31;
  final private static byte ANISO_MMCIF_U13 = 32;
  final private static byte ANISO_MMCIF_U23 = 33;
  final private static byte U_ISO_OR_EQUIV = 34;
  final private static byte ANISO_B11 = 35;
  final private static byte ANISO_B22 = 36;
  final private static byte ANISO_B33 = 37;
  final private static byte ANISO_B12 = 38;
  final private static byte ANISO_B13 = 39;
  final private static byte ANISO_B23 = 40;
  final private static byte ANISO_BETA_11 = 41;
  final private static byte ANISO_BETA_22 = 42;
  final private static byte ANISO_BETA_33 = 43;
  final private static byte ANISO_BETA_12 = 44;
  final private static byte ANISO_BETA_13 = 45;
  final private static byte ANISO_BETA_23 = 46;
  final private static byte ADP_TYPE = 47;
  final private static byte CHEM_COMP_AC_ID = 48;
  final private static byte CHEM_COMP_AC_NAME = 49;
  final private static byte CHEM_COMP_AC_SYM = 50;
  final private static byte CHEM_COMP_AC_CHARGE = 51;
  final private static byte CHEM_COMP_AC_X = 52;
  final private static byte CHEM_COMP_AC_Y = 53;
  final private static byte CHEM_COMP_AC_Z = 54;
  final private static byte CHEM_COMP_AC_X_IDEAL = 55;
  final private static byte CHEM_COMP_AC_Y_IDEAL = 56;
  final private static byte CHEM_COMP_AC_Z_IDEAL = 57;
  final private static byte DISORDER_ASSEMBLY = 58;
  final private static byte ASYM_ID = 59;
  final private static byte SUBSYS_ID = 60;
  final private static byte SITE_MULT = 61;
  final private static byte THERMAL_TYPE = 62;

  final private static String[] atomFields = { "_atom_site_type_symbol",
      "_atom_site_label", "_atom_site_auth_atom_id", "_atom_site_fract_x",
      "_atom_site_fract_y", "_atom_site_fract_z", "_atom_site_cartn_x",
      "_atom_site_cartn_y", "_atom_site_cartn_z", "_atom_site_occupancy",
      "_atom_site_b_iso_or_equiv", "_atom_site_auth_comp_id",
      "_atom_site_auth_asym_id", "_atom_site_auth_seq_id",
      "_atom_site_pdbx_pdb_ins_code", "_atom_site_label_alt_id",
      "_atom_site_group_pdb", "_atom_site_pdbx_pdb_model_num",
      "_atom_site_calc_flag", "_atom_site_disorder_group",
      "_atom_site_aniso_label", "_atom_site_anisotrop_id",
      "_atom_site_aniso_u_11", "_atom_site_aniso_u_22",
      "_atom_site_aniso_u_33", "_atom_site_aniso_u_12",
      "_atom_site_aniso_u_13", "_atom_site_aniso_u_23",
      "_atom_site_anisotrop_u[1][1]", "_atom_site_anisotrop_u[2][2]",
      "_atom_site_anisotrop_u[3][3]", "_atom_site_anisotrop_u[1][2]",
      "_atom_site_anisotrop_u[1][3]", "_atom_site_anisotrop_u[2][3]",
      "_atom_site_u_iso_or_equiv", "_atom_site_aniso_b_11",
      "_atom_site_aniso_b_22", "_atom_site_aniso_b_33",
      "_atom_site_aniso_b_12", "_atom_site_aniso_b_13",
      "_atom_site_aniso_b_23", "_atom_site_aniso_beta_11",
      "_atom_site_aniso_beta_22", "_atom_site_aniso_beta_33",
      "_atom_site_aniso_beta_12", "_atom_site_aniso_beta_13",
      "_atom_site_aniso_beta_23", "_atom_site_adp_type",
      "_chem_comp_atom_comp_id", "_chem_comp_atom_atom_id",
      "_chem_comp_atom_type_symbol", "_chem_comp_atom_charge",
      "_chem_comp_atom_model_cartn_x", "_chem_comp_atom_model_cartn_y",
      "_chem_comp_atom_model_cartn_z",
      "_chem_comp_atom_pdbx_model_cartn_x_ideal",
      "_chem_comp_atom_pdbx_model_cartn_y_ideal",
      "_chem_comp_atom_pdbx_model_cartn_z_ideal",
      "_atom_site_disorder_assembly", "_atom_site_label_asym_id",
      "_atom_site_subsystem_code", "_atom_site_symmetry_multiplicity",
      "_atom_site_thermal_displace_type" };

  /* to: hansonr@stolaf.edu
   * from: Zukang Feng zfeng@rcsb.rutgers.edu
   * re: Two mmCIF issues
   * date: 4/18/2006 10:30 PM
   * "You should always use _atom_site.auth_asym_id for PDB chain IDs."
   * 
   * 
   */

  /**
   * reads atom data in any order
   * 
   * @param isLigand
   * 
   * @return TRUE if successful; FALS if EOF encountered
   * @throws Exception
   */
  boolean processAtomSiteLoopBlock(boolean isLigand) throws Exception {
    lookingForPDB = false;
    int currentModelNO = -1; // PDBX
    boolean isAnisoData = false;
    String assemblyId = null;
    parseLoopParameters(atomFields);
    if (fieldOf[CHEM_COMP_AC_X_IDEAL] != NONE) {
      isPDB = false;
      setFractionalCoordinates(false);
    } else if (fieldOf[CARTN_X] != NONE || fieldOf[CHEM_COMP_AC_X] != NONE) {
      setFractionalCoordinates(false);
      disableField(FRACT_X);
      disableField(FRACT_Y);
      disableField(FRACT_Z);
    } else if (fieldOf[FRACT_X] != NONE) {
      setFractionalCoordinates(true);
      disableField(CARTN_X);
      disableField(CARTN_Y);
      disableField(CARTN_Z);
    } else if (fieldOf[ANISO_LABEL] != NONE) {
      // standard CIF
      isAnisoData = true;
    } else if (fieldOf[ANISO_MMCIF_ID] != NONE) {
      // MMCIF
      isAnisoData = true;
    } else {
      // it is a different kind of _atom_site loop block
      skipLoop();
      return false;
    }
    int iAtom = -1;
    int modelField = -1;
    int siteMult = 0;
    while (tokenizer.getData()) {
      Atom atom = new Atom();
      assemblyId = null;
      if (isPDBX) {
        if (modelField == -1) {
          for (int i = 0; i < tokenizer.fieldCount; ++i)
            if (fieldProperty(i) == MODEL_NO) {
              modelField = i;
              isPDB = true;
              break;
            }
          if (modelField == -1)
            modelField = -2;
        }
        if (modelField >= 0) {
          fieldProperty(modelField);
          int modelNO = parseIntStr(field);
          if (modelNO != currentModelNO) {
            if (iHaveDesiredModel) {
              skipLoop();
              // but only this atom loop
              skipping = false;
              continuing = true;
              break;
            }
            currentModelNO = modelNO;
            newModel(modelNO);
            if (!skipping)
              nextAtomSet();
          }
          if (skipping)
            continue;
        }
      }
      for (int i = 0; i < tokenizer.fieldCount; ++i) {
        int tok = fieldProperty(i);
        switch (tok) {
        case NONE:
        case MODEL_NO:
          break;
        case CHEM_COMP_AC_SYM:
        case TYPE_SYMBOL:
          String elementSymbol;
          if (field.length() < 2) {
            elementSymbol = field;
          } else {
            char ch1 = Character.toLowerCase(field.charAt(1));
            if (Atom.isValidElementSymbol2(firstChar, ch1))
              elementSymbol = "" + firstChar + ch1;
            else
              elementSymbol = "" + firstChar;
          }
          atom.elementSymbol = elementSymbol;
          if (atomTypes != null && atomTypes.containsKey(field)) {
            float charge = atomTypes.get(field).floatValue();
            atom.formalCharge = Math.round(charge);
            //because otherwise -1.6 is rounded UP to -1, and  1.6 is rounded DOWN to 1
            if (Math.abs(atom.formalCharge - charge) > 0.1)
              if (Logger.debugging) {
                Logger.debug("CIF charge on " + field + " was " + charge
                    + "; rounded to " + atom.formalCharge);
              }
          }
          break;
        case CHEM_COMP_AC_NAME:
        case LABEL:
        case AUTH_ATOM:
          atom.atomName = field;
          break;
        case CHEM_COMP_AC_X_IDEAL:
          float x = parseFloatStr(field);
          if (readIdeal && !Float.isNaN(x))
            atom.x = x;
          break;
        case CHEM_COMP_AC_Y_IDEAL:
          float y = parseFloatStr(field);
          if (readIdeal && !Float.isNaN(y))
            atom.y = y;
          break;
        case CHEM_COMP_AC_Z_IDEAL:
          float z = parseFloatStr(field);
          if (readIdeal && !Float.isNaN(z))
            atom.z = z;
          break;
        case CHEM_COMP_AC_X:
        case CARTN_X:
        case FRACT_X:
          atom.x = parseFloatStr(field);
          break;
        case CHEM_COMP_AC_Y:
        case CARTN_Y:
        case FRACT_Y:
          atom.y = parseFloatStr(field);
          break;
        case CHEM_COMP_AC_Z:
        case CARTN_Z:
        case FRACT_Z:
          atom.z = parseFloatStr(field);
          break;
        case CHEM_COMP_AC_CHARGE:
          atom.formalCharge = parseIntStr(field);
          break;
        case OCCUPANCY:
          float floatOccupancy = parseFloatStr(field);
          if (!Float.isNaN(floatOccupancy))
            atom.foccupancy = floatOccupancy;
          break;
        case B_ISO:
          atom.bfactor = parseFloatStr(field) * (isPDB ? 1 : 100f);
          break;
        case CHEM_COMP_AC_ID:
        case COMP_ID:
          atom.group3 = field;
          break;
        case ASYM_ID:
          assemblyId = field;
          break;
        case AUTH_ASYM_ID:
          atom.chainID = viewer.getChainID(field);
          break;
        case SEQ_ID:
          atom.sequenceNumber = parseIntStr(field);
          break;
        case INS_CODE:
          atom.insertionCode = firstChar;
          break;
        case ALT_ID:
        case SUBSYS_ID:
          atom.altLoc = firstChar;
          break;
        case DISORDER_ASSEMBLY:
          disorderAssembly = field;
          break;
        case DISORDER_GROUP:
          if (firstChar == '-' && field.length() > 1) {
            atom.altLoc = field.charAt(1);
            atom.ignoreSymmetry = true;
          } else {
            atom.altLoc = firstChar;
          }
          break;
        case GROUP_PDB:
          isPDB = true;
          if ("HETATM".equals(field))
            atom.isHetero = true;
          break;
        case DUMMY_ATOM:
          //see http://www.iucr.org/iucr-top/cif/cifdic_html/
          //            1/cif_core.dic/Iatom_site_calc_flag.html
          if ("dum".equals(field)) {
            atom.x = Float.NaN;
            continue; //skip 
          }
          break;
        case THERMAL_TYPE:
        case ADP_TYPE:
          if (field.equalsIgnoreCase("Uiso")) {
            int j = fieldOf[U_ISO_OR_EQUIV];
            if (j != NONE)
              setU(atom, 7, parseFloatStr(tokenizer.loopData[j]));
          }
          break;
        case ANISO_LABEL:
          iAtom = atomSetCollection.getAtomIndexFromName(field);
          if (iAtom < 0)
            continue;
          atom = atomSetCollection.getAtom(iAtom);
          break;
        case ANISO_MMCIF_ID:
          atom = atomSetCollection.getAtom(++iAtom);
          break;
        case ANISO_U11:
        case ANISO_U22:
        case ANISO_U33:
        case ANISO_U12:
        case ANISO_U13:
        case ANISO_U23:
        case ANISO_MMCIF_U11:
        case ANISO_MMCIF_U22:
        case ANISO_MMCIF_U33:
        case ANISO_MMCIF_U12:
        case ANISO_MMCIF_U13:
        case ANISO_MMCIF_U23:
          // Ortep Type 8: D = 2pi^2, C = 2, a*b*
          setU(atom, (propertyOf[i] - ANISO_U11) % 6, parseFloatStr(field));
          break;
        case ANISO_B11:
        case ANISO_B22:
        case ANISO_B33:
        case ANISO_B12:
        case ANISO_B13:
        case ANISO_B23:
          // Ortep Type 4: D = 1/4, C = 2, a*b*
          setU(atom, 6, 4);
          setU(atom, (propertyOf[i] - ANISO_B11) % 6, parseFloatStr(field));
          break;
        case ANISO_BETA_11:
        case ANISO_BETA_22:
        case ANISO_BETA_33:
        case ANISO_BETA_12:
        case ANISO_BETA_13:
        case ANISO_BETA_23:
          //Ortep Type 0: D = 1, c = 2 -- see org.jmol.symmetry/UnitCell.java
          setU(atom, 6, 0);
          setU(atom, (propertyOf[i] - ANISO_BETA_11) % 6, parseFloatStr(field));
          break;
        case SITE_MULT:
          if (modulated)
            siteMult = parseIntStr(field);
        }
      }
      if (isAnisoData)
        continue;
      if (Float.isNaN(atom.x) || Float.isNaN(atom.y) || Float.isNaN(atom.z)) {
        Logger.warn("atom " + atom.atomName
            + " has invalid/unknown coordinates");
        continue;
      }
      if (!filterCIFAtom(atom, iAtom, assemblyId))
        continue;
      setAtomCoord(atom);

      if (pr != null && !pr.checkAtom(atom, assemblyId, atomCount))
        continue;
      if (atom.elementSymbol == null && atom.atomName != null) {
        String sym = atom.atomName;
        int pt = 0;
        while (pt < sym.length() && Character.isLetter(sym.charAt(pt)))
          pt++;
        atom.elementSymbol = (pt == 0 || pt > 2 ? "Xx" : sym.substring(0, pt));
      }
      atomSetCollection.addAtomWithMappedName(atom);
      atomCount++;
      if (modulated) {
        //        if (subid != null)
        //          mr.addSubsystem(subid, null, atom.atomName);
        if (siteMult != 0)
          atom.vib = V3.new3(siteMult, 0, Float.NaN);
      }
    }
    if (isPDB)
      setIsPDB();
    atomSetCollection.setAtomSetAuxiliaryInfo("isCIF", Boolean.TRUE);
    if (isPDBX && skipping)
      skipping = false;
    return true;
  }

  protected boolean filterCIFAtom(Atom atom, int iAtom, String assemblyId) {
    if (!filterAtom(atom, iAtom))
      return false;
    if (filterAssembly && filterReject(filter, "$", assemblyId))
      return false;
    if (configurationPtr > 0) {
      if (!disorderAssembly.equals(lastDisorderAssembly)) {
        lastDisorderAssembly = disorderAssembly;
        lastAltLoc = '\0';
        conformationIndex = configurationPtr;
      }
      // ignore atoms that have no designation
      if (atom.altLoc != '\0') {
        // count down until we get the desired index into the list
        if (conformationIndex >= 0 && atom.altLoc != lastAltLoc) {
          lastAltLoc = atom.altLoc;
          conformationIndex--;
        }
        if (conformationIndex != 0) {
          Logger.info("ignoring " + atom.atomName);
          return false;
        }
      }
    }
    return true;
  }

  final private static byte CITATION_ID = 0;
  final private static byte CITATION_TITLE = 1;

  final private static String[] citationFields = { "_citation_id",
      "_citation_title" };

  private void processCitationListBlock() throws Exception {
    parseLoopParameters(citationFields);
    float[] m = new float[16];
    m[15] = 1;
    while (tokenizer.getData()) {
      for (int i = 0; i < tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case CITATION_ID:
          break;
        case CITATION_TITLE:
          appendLoadNote("TITLE: " + tokenizer.toUnicode(field));
          break;
        }
      }
    }
  }

  ////////////////////////////////////////////////////////////////
  // symmetry operations
  ////////////////////////////////////////////////////////////////

  final private static byte SYMOP_XYZ = 0;
  final private static byte SYM_EQUIV_XYZ = 1;
  final private static byte SYM_SSG_XYZ = 2;
  final private static byte SYM_SSG_OP = 3;

  final private static String[] symmetryOperationsFields = {
      "_space_group_symop_operation_xyz", "_symmetry_equiv_pos_as_xyz",
      "_symmetry_ssg_equiv_pos_as_xyz",
      "_space_group_symop_ssg_operation_algebraic" };

  /**
   * retrieves symmetry operations
   * 
   * @throws Exception
   */
  private void processSymmetryOperationsLoopBlock() throws Exception {
    parseLoopParameters(symmetryOperationsFields);
    int nRefs = 0;
    symops = new List<String>();
    for (int i = propertyCount; --i >= 0;)
      if (fieldOf[i] != NONE)
        nRefs++;
    if (nRefs != 1) {
      Logger
          .warn("?que? _symmetry_equiv or _space_group_symop property not found");
      skipLoop();
      return;
    }
    int n = 0;
    while (tokenizer.getData()) {
      boolean ssgop = false;
      for (int i = 0; i < tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case SYM_SSG_XYZ:
          // check for non-standard record x~1~,x~2~,x~3~,x~4~  kIsfCqpM.cif
          if (field.indexOf('~') >= 0)
            field = PT.simpleReplace(field, "~", "");
          //$FALL-THROUGH$
        case SYM_SSG_OP:
          modulated = true;
          ssgop = true;
          //$FALL-THROUGH$
        case SYMOP_XYZ:
        case SYM_EQUIV_XYZ:
          if (allowRotations || ++n == 1)
            if (!modulated || ssgop) {
              symops.addLast(field);
              setSymmetryOperator(field);
            }
          break;
        }
      }
    }
  }

  public int getBondOrder(String field) {
    switch (field.charAt(0)) {
    default:
      Logger.warn("unknown CIF bond order: " + field);
      //$FALL-THROUGH$
    case 'S':
      return JmolAdapter.ORDER_COVALENT_SINGLE;
    case 'D':
      return JmolAdapter.ORDER_COVALENT_DOUBLE;
    case 'T':
      return JmolAdapter.ORDER_COVALENT_TRIPLE;
    case 'A':
      haveAromatic = true;
      return JmolAdapter.ORDER_AROMATIC;
    }
  }

  final private static byte GEOM_BOND_ATOM_SITE_LABEL_1 = 0;
  final private static byte GEOM_BOND_ATOM_SITE_LABEL_2 = 1;
  final private static byte GEOM_BOND_DISTANCE = 2;
  final private static byte CCDC_GEOM_BOND_TYPE = 3;

  //final private static byte GEOM_BOND_SITE_SYMMETRY_2 = 3;

  final private static String[] geomBondFields = {
      "_geom_bond_atom_site_label_1", "_geom_bond_atom_site_label_2",
      "_geom_bond_distance", "_ccdc_geom_bond_type"
  //  "_geom_bond_site_symmetry_2",
  };

  /**
   * 
   * reads bond data -- N_ijk symmetry business is ignored, so we only indicate
   * bonds within the unit cell to just the original set of atoms. "connect"
   * script or "set forceAutoBond" will override these values, but see below.
   * 
   * @throws Exception
   */
  private void processGeomBondLoopBlock() throws Exception {
    parseLoopParameters(geomBondFields);
    for (int i = propertyCount; --i >= 0;)
      if (propertyOf[i] != CCDC_GEOM_BOND_TYPE && fieldOf[i] == NONE) {
        Logger.warn("?que? missing property: " + geomBondFields[i]);
        skipLoop();
        return;
      }

    String name1 = null;
    String name2 = null;
    Integer order = Integer.valueOf(1);
    int bondCount = 0;
    while (tokenizer.getData()) {
      int atomIndex1 = -1;
      int atomIndex2 = -1;
      float distance = 0;
      float dx = 0;
      //String siteSym2 = null;
      for (int i = 0; i < tokenizer.fieldCount; ++i) {
        switch (fieldProperty(i)) {
        case NONE:
          break;
        case GEOM_BOND_ATOM_SITE_LABEL_1:
          atomIndex1 = atomSetCollection.getAtomIndexFromName(name1 = field);
          break;
        case GEOM_BOND_ATOM_SITE_LABEL_2:
          atomIndex2 = atomSetCollection.getAtomIndexFromName(name2 = field);
          break;
        case GEOM_BOND_DISTANCE:
          distance = parseFloatStr(field);
          int pt = field.indexOf('(');
          if (pt >= 0) {
            char[] data = field.toCharArray();
            // 3.567(12) --> 0.012
            String sdx = field.substring(pt + 1, field.length() - 1);
            int n = sdx.length();
            for (int j = pt; --j >= 0;) {
              if (data[j] == '.')
                --j;
              data[j] = (--n < 0 ? '0' : sdx.charAt(n));
            }
            dx = parseFloatStr(String.valueOf(data));
            if (Float.isNaN(dx)) {
              Logger.info("error reading uncertainty for " + line);
              dx = 0.015f;
            }
            // TODO -- this is the full +/- (dx) in x.xxx(dx) -- is that too large?
          } else {
            dx = 0.015f;
          }
          break;
        case CCDC_GEOM_BOND_TYPE:
          order = Integer.valueOf(getBondOrder(field));
          break;
        //case GEOM_BOND_SITE_SYMMETRY_2:
        //siteSym2 = field;
        //break;
        }
      }
      if (atomIndex1 < 0 || atomIndex2 < 0 || distance == 0)
        continue;
      bondCount++;
      bondTypes.addLast(new Object[] { name1, name2, Float.valueOf(distance),
          Float.valueOf(dx), order });
    }
    if (bondCount > 0) {
      Logger.info(bondCount + " bonds read");
      if (!doApplySymmetry) {
        isMolecular = true;
        doApplySymmetry = true;
        latticeCells[0] = latticeCells[1] = latticeCells[2] = 1;
      }
    }
  }

  /////////////////////////////////////
  //  bonding and molecular 
  /////////////////////////////////////

  private float[] atomRadius;
  private BS[] bsConnected;
  private BS[] bsSets;
  final private P3 ptOffset = new P3();
  private BS bsMolecule;
  private BS bsExclude;
  private int firstAtom;
  private int atomCount;

  private Atom[] atoms;

  /**
   * (1) If GEOM_BOND records are present, we (a) use them to generate bonds (b)
   * add H atoms to bonds if necessary (c) turn off autoBonding ("hasBonds") (2)
   * If MOLECULAR, then we (a) use {1 1 1} if lattice is not defined (b) use
   * atomSetCollection.bonds[] to construct a preliminary molecule and connect
   * as we go (c) check symmetry for connections to molecule in any one of the
   * 27 3x3 adjacent cells (d) move those atoms and their connected branch set
   * (e) iterate as necessary to get all atoms desired (f) delete unselected
   * atoms (g) set all coordinates as Cartesians (h) remove all unit cell
   * information
   */
  private void setBondingAndMolecules() {
    Logger.info("CIF creating molecule "
        + (bondTypes.size() > 0 ? " using GEOM_BOND records" : ""));
    atoms = atomSetCollection.getAtoms();
    firstAtom = atomSetCollection.getLastAtomSetAtomIndex();
    int nAtoms = atomSetCollection.getLastAtomSetAtomCount();
    atomCount = firstAtom + nAtoms;

    // get list of sites based on atom names

    bsSets = new BS[nAtoms];
    symmetry = atomSetCollection.getSymmetry();
    for (int i = firstAtom; i < atomCount; i++) {
      int ipt = atomSetCollection.getAtomIndexFromName(atoms[i].atomName)
          - firstAtom;
      if (bsSets[ipt] == null)
        bsSets[ipt] = new BS();
      bsSets[ipt].set(i - firstAtom);
    }

    // if molecular, we need atom connection lists and radii

    if (isMolecular) {
      atomRadius = new float[atomCount];
      for (int i = firstAtom; i < atomCount; i++) {
        int elemnoWithIsotope = JmolAdapter.getElementNumber(atoms[i]
            .getElementSymbol());
        atoms[i].elementNumber = (short) elemnoWithIsotope;
        int charge = (atoms[i].formalCharge == Integer.MIN_VALUE ? 0
            : atoms[i].formalCharge);
        if (elemnoWithIsotope > 0)
          atomRadius[i] = JmolAdapter.getBondingRadiusFloat(elemnoWithIsotope,
              charge);
      }
      bsConnected = new BS[atomCount];
      for (int i = firstAtom; i < atomCount; i++)
        bsConnected[i] = new BS();

      // Set up a working set of atoms in the "molecule".

      bsMolecule = new BS();

      // Set up a working set of atoms that should be excluded 
      // because they would map onto an equivalent atom's position.

      bsExclude = new BS();
    }

    boolean isFirst = true;
    while (createBonds(isFirst)) {
      isFirst = false;
      // main loop continues until no new atoms are found
    }

    if (isMolecular) {

      // Set bsAtoms to control which atoms and 
      // bonds are delivered by the iterators.

      if (atomSetCollection.bsAtoms == null)
        atomSetCollection.bsAtoms = new BS();
      atomSetCollection.bsAtoms.clearBits(firstAtom, atomCount);
      atomSetCollection.bsAtoms.or(bsMolecule);
      atomSetCollection.bsAtoms.andNot(bsExclude);

      // Set atom positions to be Cartesians and clear out unit cell
      // so that the model displays without it.

      for (int i = firstAtom; i < atomCount; i++) {
        if (atomSetCollection.bsAtoms.get(i))
          symmetry.toCartesian(atoms[i], true);
        else if (Logger.debugging)
          Logger.debug(molecularType + " removing " + i + " "
              + atoms[i].atomName + " " + atoms[i]);
      }
      atomSetCollection.setAtomSetAuxiliaryInfo("notionalUnitcell", null);
      if (nMolecular++ == atomSetCollection.getCurrentAtomSetIndex()) {
        atomSetCollection
            .clearGlobalBoolean(AtomSetCollection.GLOBAL_FRACTCOORD);
        atomSetCollection.clearGlobalBoolean(AtomSetCollection.GLOBAL_SYMMETRY);
        atomSetCollection
            .clearGlobalBoolean(AtomSetCollection.GLOBAL_UNITCELLS);
      }

    }

    // Set return info to enable desired defaults.

    if (bondTypes.size() > 0)
      atomSetCollection.setAtomSetAuxiliaryInfo("hasBonds", Boolean.TRUE);

    // Clear temporary fields.

    bondTypes.clear();
    atomRadius = null;
    bsSets = null;
    bsConnected = null;
    bsMolecule = null;
    bsExclude = null;
  }

  /**
   * Use the site bitset to check for atoms that are within +/-dx Angstroms of
   * the specified distances in GEOM_BOND where dx is determined by the
   * uncertainty (dx) in the record. Note that this also "connects" the atoms
   * that might have been moved in a previous iteration.
   * 
   * Also connect H atoms based on a distance <= 1.1 Angstrom from a nearby
   * atom.
   * 
   * Then create molecules.
   * 
   * @param doInit
   * @return TRUE if need to continue
   */
  private boolean createBonds(boolean doInit) {

    // process GEOM_BOND records
    for (int i = bondTypes.size(); --i >= 0;) {
      Object[] o = bondTypes.get(i);
      float distance = ((Float) o[2]).floatValue();
      float dx = ((Float) o[3]).floatValue();
      int order = ((Integer) o[4]).intValue();
      int iatom1 = atomSetCollection.getAtomIndexFromName((String) o[0]);
      int iatom2 = atomSetCollection.getAtomIndexFromName((String) o[1]);
      BS bs1 = bsSets[iatom1 - firstAtom];
      BS bs2 = bsSets[iatom2 - firstAtom];
      if (bs1 == null || bs2 == null)
        continue;
      for (int j = bs1.nextSetBit(0); j >= 0; j = bs1.nextSetBit(j + 1))
        for (int k = bs2.nextSetBit(0); k >= 0; k = bs2.nextSetBit(k + 1)) {
          if ((!isMolecular || !bsConnected[j + firstAtom].get(k))
              && symmetry.checkDistance(atoms[j + firstAtom], atoms[k
                  + firstAtom], distance, dx, 0, 0, 0, ptOffset))
            addNewBond(j + firstAtom, k + firstAtom, order);
        }
    }

    // do a quick check for H-X bonds if we have GEOM_BOND

    if (bondTypes.size() > 0)
      for (int i = firstAtom; i < atomCount; i++)
        if (atoms[i].elementNumber == 1) {
          boolean checkAltLoc = (atoms[i].altLoc != '\0');
          for (int k = firstAtom; k < atomCount; k++)
            if (k != i
                && atoms[k].elementNumber != 1
                && (!checkAltLoc || atoms[k].altLoc == '\0' || atoms[k].altLoc == atoms[i].altLoc)) {
              if (!bsConnected[i].get(k)
                  && symmetry.checkDistance(atoms[i], atoms[k], 1.1f, 0, 0, 0,
                      0, ptOffset))
                addNewBond(i, k, 1);
            }
        }
    if (!isMolecular)
      return false;

    // generate the base atom set

    if (doInit)
      for (int i = firstAtom; i < atomCount; i++)
        if (atoms[i].atomSite + firstAtom == i && !bsMolecule.get(i))
          setBs(atoms, i, bsConnected, bsMolecule);

    // Now look through unchecked atoms for ones that
    // are within bonding distance of the "molecular" set
    // in any one of the 27 adjacent cells in 444 - 666.
    // If an atom is found, move it along with its "branch"
    // to the new location. BUT also check that we are
    // not overlaying another atom -- if that happens
    // go ahead and move it, but mark it as excluded.

    float bondTolerance = viewer.getFloat(T.bondtolerance);
    BS bsBranch = new BS();
    P3 cart1 = new P3();
    P3 cart2 = new P3();
    int nFactor = 2; // 1 was not enough. (see data/cif/triclinic_issue.cif)
    for (int i = firstAtom; i < atomCount; i++)
      if (!bsMolecule.get(i) && !bsExclude.get(i))
        for (int j = bsMolecule.nextSetBit(0); j >= 0; j = bsMolecule
            .nextSetBit(j + 1))
          if (symmetry.checkDistance(atoms[j], atoms[i], atomRadius[i]
              + atomRadius[j] + bondTolerance, 0, nFactor, nFactor, nFactor,
              ptOffset)) {
            setBs(atoms, i, bsConnected, bsBranch);
            for (int k = bsBranch.nextSetBit(0); k >= 0; k = bsBranch
                .nextSetBit(k + 1)) {
              atoms[k].add(ptOffset);
              cart1.setT(atoms[k]);
              symmetry.toCartesian(cart1, true);
              BS bs = bsSets[atomSetCollection
                  .getAtomIndexFromName(atoms[k].atomName) - firstAtom];
              if (bs != null)
                for (int ii = bs.nextSetBit(0); ii >= 0; ii = bs
                    .nextSetBit(ii + 1)) {
                  if (ii + firstAtom == k)
                    continue;
                  cart2.setT(atoms[ii + firstAtom]);
                  symmetry.toCartesian(cart2, true);
                  if (cart2.distance(cart1) < 0.1f) {
                    bsExclude.set(k);
                    break;
                  }
                }
              bsMolecule.set(k);
            }
            return true;
          }
    return false;
  }

  /**
   * add the bond and mark it for molecular processing
   * 
   * @param i
   * @param j
   * @param order
   */
  private void addNewBond(int i, int j, int order) {
    atomSetCollection.addNewBondWithOrder(i, j, order);
    if (!isMolecular)
      return;
    bsConnected[i].set(j);
    bsConnected[j].set(i);
  }

  /**
   * iteratively run through connected atoms, adding them to the set
   * 
   * @param atoms
   * @param iatom
   * @param bsBonds
   * @param bs
   */
  private void setBs(Atom[] atoms, int iatom, BS[] bsBonds, BS bs) {
    BS bsBond = bsBonds[iatom];
    bs.set(iatom);
    for (int i = bsBond.nextSetBit(0); i >= 0; i = bsBond.nextSetBit(i + 1)) {
      if (!bs.get(i))
        setBs(atoms, i, bsBonds, bs);
    }
  }

}
