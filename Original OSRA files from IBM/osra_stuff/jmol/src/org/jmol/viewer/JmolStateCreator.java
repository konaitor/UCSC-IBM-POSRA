package org.jmol.viewer;

import java.util.Map;

import org.jmol.java.BS;
import org.jmol.modelset.Group;
import org.jmol.modelset.Measurement;
import org.jmol.modelset.TickInfo;
import org.jmol.shape.AtomShape;
import org.jmol.shape.Measures;
import org.jmol.shape.Shape;

import javajs.awt.Font;
import javajs.util.List;
import javajs.util.SB;

public abstract class JmolStateCreator {

  abstract void setViewer(Viewer viewer);

  abstract String getStateScript(String type, int width, int height);

  abstract String getSpinState(boolean b);
  
  abstract Map<String, Object> getInfo(Object manager);

  abstract String getSpecularState();
  
  abstract String getLoadState(Map<String, Object> htParams);

  abstract String getModelState(SB sfunc, boolean isAll,
                               boolean withProteinStructure);

  abstract String getFontState(String myType, Font font3d);

  abstract String getFontLineShapeState(String s, String myType, TickInfo[] tickInfos);

  abstract void getShapeSetState(AtomShape atomShape, Shape shape, int monomerCount, Group[] monomers,
                     BS bsSizeDefault, Map<String, BS> temp, Map<String, BS> temp2);

  abstract String getMeasurementState(Measures shape, List<Measurement> mList, int measurementCount,
                             Font font3d, TickInfo tickInfo);

  abstract String getBondState(Shape shape, BS bsOrderSet, boolean reportAll);

  abstract String getAtomShapeSetState(Shape shape, AtomShape[] shapes);

  abstract String getShapeState(Shape shape);

  abstract String getCommands(Map<String, BS> htDefine, Map<String, BS> htMore,
                     String select);

  abstract String getAllSettings(String prefix);

  abstract String getAtomShapeState(AtomShape shape);

  abstract String getTrajectoryState();

  abstract String getFunctionCalls(String selectedFunction);

  abstract String getAtomicPropertyState(byte taintCoord, BS bsSelected);

  abstract void getAtomicPropertyStateBuffer(SB commands, byte type,
                                    BS bs, String name, float[] data);

  abstract void undoMoveAction(int action, int n);

  abstract void undoMoveActionClear(int taintedAtom, int type, boolean clearRedo);

  abstract void syncScript(String script, String applet, int port);

  abstract void mouseScript(String script);

  abstract String getAtomDefs(Map<String, Object> names);

  abstract void getInlineData(SB loadScript, String strModel, boolean isAppend,
                     String defaultLoadFilter);
}
