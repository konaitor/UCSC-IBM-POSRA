package org.jmol.api;

import java.util.Map;
import java.util.Properties;

import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import org.jmol.modelset.Chain;
import org.jmol.modelset.Group;
import org.jmol.modelset.Model;
import org.jmol.modelset.ModelLoader;

public interface JmolBioResolver {

  public Group distinguishAndPropagateGroup(Chain chain, String group3, int seqcode,
                                                  int firstAtomIndex, int maxAtomIndex, 
                                                  int modelIndex, int[] specialAtomIndexes,
                                                  Atom[] atoms);
  
  public void initializeHydrogenAddition();

  public void finalizeHydrogens();

  public void setHaveHsAlready(boolean b);

  public void addImplicitHydrogenAtoms(JmolAdapter adapter, int i, int nH);

  public void initialize(ModelLoader modelLoader);

  public String fixPropertyValue(BS bsAtoms, String data);

  public Model getBioModel(int modelIndex,
                        int trajectoryBaseIndex, String jmolData,
                        Properties modelProperties,
                        Map<String, Object> modelAuxiliaryInfo);

  public void iterateOverAllNewStructures(JmolAdapter adapter,
                                          Object atomSetCollection);

  }

