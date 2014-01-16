package org.jmol.script;

import org.jmol.java.BS;
import org.jmol.viewer.ShapeManager;

public interface JmolScriptExtension {

  public JmolScriptExtension init(Object eval);

  public boolean dispatch(int iShape, boolean b, T[] st) throws ScriptException;

  public String plot(T[] args) throws ScriptException;

  public Object getBitsetIdent(BS bs, String label, Object tokenValue,
                               boolean useAtomMap, int index,
                               boolean isExplicitlyAll);

  public boolean evaluateParallel(ScriptContext context,
                                  ShapeManager shapeManager);

  public String write(T[] args) throws ScriptException;

  public Object getSmilesMatches(String pattern, String smiles, BS bsSelected,
                                 BS bsMatch3D, boolean isSmarts,
                                 boolean asOneBitset) throws ScriptException;

  public boolean evaluate(ScriptMathProcessor mp, T op, SV[] args, int tok)
      throws ScriptException;

  public Object getMinMax(Object floatOrSVArray, int intValue);

}
