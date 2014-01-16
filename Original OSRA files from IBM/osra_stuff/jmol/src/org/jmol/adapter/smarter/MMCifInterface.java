package org.jmol.adapter.smarter;

import org.jmol.adapter.readers.cif.CifReader;

public interface MMCifInterface {


  boolean checkAtom(Atom atom, String assemblyID, int index);

  void finalizeReader(int nAtoms) throws Exception;

  boolean initialize(CifReader cifReader);

  void processData(String key) throws Exception;

  boolean processPDBLoops(String str) throws Exception;

}
