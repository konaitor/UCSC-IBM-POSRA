package org.jmol.adapter.readers.xtal;

/**
 * http://cms.mpi.univie.ac.at/vasp/
 * 
 * @author Pieremanuele Canepa, MIT, 
 *         Department of Material Sciences and Engineering
 *         
 * 
 * @version 1.0
 */

import javajs.util.PT;

import org.jmol.adapter.smarter.Atom;
import org.jmol.adapter.smarter.AtomSetCollectionReader;

public class AbinitReader extends AtomSetCollectionReader {

  private float[] cellLattice;
  private String atomList[];

  @Override
  protected void initializeReader() {
    setSpaceGroupName("P1");
    doApplySymmetry = true;
    // inputOnly = checkFilter("INPUT");
  }

  @Override
  protected boolean checkLine() throws Exception {
    if (line.contains("natom")) {
      readNoatom();
    } else if (line.contains("ntypat") || line.contains("ntype")) {
      readNotypes();
    } else if (line.contains("typat") || line.contains("type")) {
      //read sequence of types
      readTypesequence();
    } else if (line.contains("Pseudopotential")) {
      readAtomSpecies();
    } else if (line.contains("Symmetries :")) {
      readSpaceGroup();
    } else if (line.contains("Real(R)+Recip(G)")) {
      readIntiallattice();
    } else if (line.contains("xred")) {
      readIntitfinalCoord();
    }
    return true;
  }

  private int nAtom;

  private void readNoatom() throws Exception {
    String[] tokens = getTokensStr(line);
    if (tokens.length <= 2)
      nAtom = parseIntStr(tokens[1]);
  }

  private int nType;

  private void readNotypes() throws Exception {
    String[] tokens = getTokensStr(line);
    if (tokens.length <= 2)
      nType = parseIntStr(tokens[1]);
  }

  private int typeArray[];

  private void readTypesequence() throws Exception {
    typeArray = new int[nAtom];
    int i = 0;
    while (line != null && line.indexOf("wtk") < 0) {
      String tmp = line;
      if (line.contains("type"))
        tmp = PT.simpleReplace(tmp, "type", "");
      if (line.contains("typat"))
        tmp = PT.simpleReplace(tmp, "typat", "");

      String[] tokens = getTokensStr(tmp);
      for (int j = 0; j < tokens.length; j++) {
        typeArray[i] = parseIntStr(tokens[j]);
        i++;
      }
      readLine();
    }
  }

  private void readAtomSpecies() throws Exception {
    atomList = new String[nAtom];
    readLine();
    //- pspini: atom type   1  psp file is Al.psp
    String[] pseudo = getTokensStr(line);
    int pseudoType = parseIntStr(pseudo[4]);
    for (int i = 0; i < nType; i++) { //is this ntype or sequence type ?
      int tokenIndex = 0;
      discardLinesUntilContains("zion");
      String tmp = PT.simpleReplace(line, ".", " ");
      String[] tokens = getTokensStr(tmp);
      if (tokens[0] == "-")
        tokenIndex = 1;
      int atomicNo = parseIntStr(tokens[tokenIndex]);
      if (pseudoType == atomicNo) {
        for (int j = 0; j < typeArray.length; j++) {
          atomList[j] = getElementSymbol(atomicNo);
        }
      }
    }
  }

  // Symmetries : space group P4/m m m (#123); Bravais tP (primitive tetrag.)
  private void readSpaceGroup() throws Exception {
  }

  private void readIntiallattice() throws Exception {

    //    Real(R)+Recip(G) space primitive vectors, cartesian coordinates (Bohr,Bohr^-1):
    //    R(1)= 25.9374361  0.0000000  0.0000000  G(1)=  0.0385543  0.0222593  0.0000000
    //    R(2)=-12.9687180 22.4624785  0.0000000  G(2)=  0.0000000  0.0445187  0.0000000
    //    R(3)=  0.0000000  0.0000000 16.0314917  G(3)=  0.0000000  0.0000000  0.0623772
    //    Unit cell volume ucvol=  9.3402532E+03 bohr^3

    cellLattice = new float[9];
    String data = "";
    int counter = 0;
    while (readLine() != null && line.indexOf("Unit cell volume") < 0) {
      data = line;
      data = PT.simpleReplace(data, "=", "= ");
      String[] tokens = getTokensStr(data);
      cellLattice[counter++] = parseFloatStr(tokens[1]) * ANGSTROMS_PER_BOHR;
      cellLattice[counter++] = parseFloatStr(tokens[2]) * ANGSTROMS_PER_BOHR;
      cellLattice[counter++] = parseFloatStr(tokens[3]) * ANGSTROMS_PER_BOHR;
    }
    setSymmetry();
  }

  private void setSymmetry() throws Exception {
    applySymmetryAndSetTrajectory();
    setSpaceGroupName("P1");
    setFractionalCoordinates(false);
  }

  //xred     -7.0000000000E-02  0.0000000000E+00  0.0000000000E+00
  //          7.0000000000E-02  0.0000000000E+00  0.0000000000E+00
  private void readIntitfinalCoord() throws Exception {
    // Read crystallographic coordinate of intial and 
    // final structures.
    String data = "";
    int count = 0;
    while (readLine() != null && line.contains("znucl")) {
      Atom atom = atomSetCollection.addNewAtom();
      atom.atomName = atomList[count++];
      data = line;
      if (data.contains("xred"))
        PT.simpleReplace(data, "xred", "");
      String[] tokens = getTokensStr(data);
      float x = parseFloatStr(tokens[0]);
      float y = parseFloatStr(tokens[1]);
      float z = parseFloatStr(tokens[2]);
      setAtomCoordXYZ(atom, x, y, z);
    }

  }

}
