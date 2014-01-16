package org.jmol.api;

public interface DSSPInterface {

  String calculateDssp(Object[] bioPolymers, 
                       int bioPolymerCount, Object object, boolean doReport,
                       boolean dsspIgnoreHydrogen, boolean setStructure);
}
