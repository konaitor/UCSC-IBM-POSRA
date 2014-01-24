package org.jmol.adapter.smarter;

public interface MSCifInterface extends MSInterface {

  // methods called from org.jmol.adapters.readers.cif.CifReader
 
  boolean processModulationLoopBlock() throws Exception;

  void processSubsystemLoopBlock() throws Exception;
  
}
