package org.jmol.viewer;

import java.util.Hashtable;


import org.jmol.api.JmolJSpecView;
import org.jmol.java.BS;
import org.jmol.modelset.Atom;
import javajs.util.List;
import org.jmol.util.Logger;
import javajs.util.PT;

public class JSpecView implements JmolJSpecView {

  private Viewer viewer;
  @Override
  public void setViewer(Viewer viewer) {
    this.viewer = viewer;
  }
  
  @Override
  public void atomPicked(int atomIndex) {
    if (atomIndex < 0)
      return;
    String peak = getPeakAtomRecord(atomIndex);
    if (peak != null)
      sendJSpecView(peak + " src=\"JmolAtomSelect\"");
  }
  
  @SuppressWarnings("unchecked")
  private String getPeakAtomRecord(int atomIndex) {
    Atom[] atoms = viewer.modelSet.atoms;
    int iModel = atoms[atomIndex].modelIndex;
    String type = null;
    switch (atoms[atomIndex].getElementNumber()) {
    case 1:
      type = "1HNMR";
      break;
    case 6:
      type = "13CNMR";
      break;
    default:
      return null;
    }
    List<String> peaks = (List<String>) viewer.getModelAuxiliaryInfoValue(iModel,
        "jdxAtomSelect_" + type);
    if (peaks == null)
      return null;
    //viewer.modelSet.htPeaks = null;
    //if (viewer.modelSet.htPeaks == null)
    viewer.modelSet.htPeaks = new Hashtable<String, BS>();
    Hashtable<String, BS> htPeaks = viewer.modelSet.htPeaks;
    for (int i = 0; i < peaks.size(); i++) {
      String peak = peaks.get(i);
      System.out.println("Jmol JSpecView.java peak="  + peak);
      BS bsPeak = htPeaks.get(peak);
      System.out.println("Jmol JSpecView.java bspeak="  + bsPeak);
      if (bsPeak == null) {
        htPeaks.put(peak, bsPeak = new BS());
        String satoms = PT.getQuotedAttribute(peak, "atoms");
        String select = PT.getQuotedAttribute(peak, "select");
        System.out.println("Jmol JSpecView.java satoms select " + satoms + " " + select);
        String script = "";
        if (satoms != null)
          script += "visible & (atomno="
              + PT.simpleReplace(satoms, ",", " or atomno=") + ")";
        else if (select != null)
          script += "visible & (" + select + ")";
        System.out.println("Jmol JSpecView.java script : " + script);
        bsPeak.or(viewer.getAtomBitSet(script));
      }
      System.out.println("Jmol JSpecView bsPeak now : " + bsPeak + " " + atomIndex);
      if (bsPeak.get(atomIndex))
        return peak;
    }
    return null;
  }


  private void sendJSpecView(String peak) {
    String msg = PT.getQuotedAttribute(peak, "title");
    if (msg != null)
      viewer.scriptEcho(Logger.debugging ? peak : msg);
    peak = viewer.fullName + "JSpecView: " + peak;
    Logger.info("Jmol.JSpecView.sendJSpecView Jmol>JSV " + peak);
    viewer.statusManager.syncSend(peak, ">", 0);
  }

  @Override
  public void setModel(int modelIndex) {
    int syncMode = ("sync on".equals(viewer.modelSet
        .getModelSetAuxiliaryInfoValue("jmolscript")) ? StatusManager.SYNC_DRIVER
        : viewer.statusManager.getSyncMode());
    if (syncMode != StatusManager.SYNC_DRIVER)
      return;
    String peak = (String) viewer.getModelAuxiliaryInfoValue(modelIndex, "jdxModelSelect");
    // problem is that SECOND load in jmol will not load new model in JSpecView
    if (peak != null)
      sendJSpecView(peak);
  }

  @Override
  public int getBaseModelIndex(int modelIndex) {
    String baseModel = (String) viewer.getModelAuxiliaryInfoValue(modelIndex,
        "jdxBaseModel");
    if (baseModel != null)
      for (int i = viewer.getModelCount(); --i >= 0;)
        if (baseModel
            .equals(viewer.getModelAuxiliaryInfoValue(i, "jdxModelID")))
          return i;
    return modelIndex;
  }


}
