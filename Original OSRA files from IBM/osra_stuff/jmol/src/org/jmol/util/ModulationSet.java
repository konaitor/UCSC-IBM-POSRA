package org.jmol.util;

import java.util.Hashtable;
import java.util.Map;

import org.jmol.api.JmolModulationSet;
import org.jmol.api.SymmetryInterface;

import javajs.util.List;
import javajs.util.M3;
import javajs.util.Matrix;
import javajs.util.P3;
import javajs.util.T3;

/**
 * A class to group a set of modulations for an atom as a "vibration"
 * Extends V3 so that it will be a displacement, and its value will be an occupancy
 * 
 * @author Bob Hanson hansonr@stolaf.edu 8/9/2013
 * 
 */

public class ModulationSet extends Vibration implements JmolModulationSet {

  public float vOcc = Float.NaN;
  public Map<String, Float> htUij;
  public float vOcc0;

  String id;
  
  private List<Modulation> mods;
  private int modDim;
  private int iop;
  private P3 r0;
  
  private SymmetryInterface symmetry;  
  private M3 gammaE;
  private Matrix gammaIinv;
  private Matrix sigma;
  private Matrix sI;
  private Matrix tau;
  
  private boolean enabled;
  private float scale = 1;
 
  private P3 qtOffset = new P3();
  private boolean isQ;

  private Matrix t;
  
  private ModulationSet modTemp;
  private String strop;
  private boolean isSubsystem;
  private Matrix tFactor;

  @Override
  public float getScale() {
    return scale;
  }
  
  @Override
  public boolean isEnabled() {
    return enabled;
  }


  public ModulationSet() {
    
  }
  
  /**
   * A collection of modulations for a specific atom.
   * 
   * The set of cell wave vectors form the sigma (d x 3) array, one vector per row. 
   * Muliplying sigma by the atom vector r (3 x 1) gives us a (d x 1) column vector.
   * This column vector is the set of coefficients of [x4, x5, x6...] that I will
   * call X. 

   * Similarly, we express the x1' - xn' aspects of the operators as the matrices
   * Gamma_I (d x d epsilons) and s_I (d x 1 shifts). 
   * 
   * In the case of subsystems, these are extracted from:
   * 
   * {Rs_nu | Vs_nu} = W_nu {Rs|Vs} W_nu^-1
   * 
   * Then for X defined as [x4,x5,x6...] we have:
   * 
   * X' = Gamma_I * X + S_I
   * 
   * or
   * 
   * X = (Gamma_I^-1)(X' - S_I)
   * 
   * I call this array "tau". We can think of this as a
   * distance along the asn axis, as in a t-plot. 
   * Ultimately we will need to add in a term allowing 
   * us to adjust the t-value:
   * 
   * X = (Gamma_I^-1)(X' - S_I + t)
   * 
   * X = tau + (Gamma_I^-1)(t)
   * 
   * or, below:
   * 
   *   xt = gammaIinv.mul(t).add(tau)
   * 
   * For subsystem nu, we need to use t_nu, which will be
   * 
   * t_nu = (W_dd - sigma W_3d) t   (van Smaalen, p. 101)
   * 
   * t_nu = (tFactor) t
   * 
   * so this becomes
   * 
   * xt = gammaIinv.mul(tFactor.inverse().mul(t)).add(tau)
   * 
   * Thus we have two subsystem-dependent modulation factors we
   * need to bring in, sigma and tFactor, and two we need to compute,
   * GammaIinv and tau.
   * 
   * @param id
   * @param r
   * @param modDim
   * @param mods
   * @param gammaE
   * @param factors   including sigma and tFactor
   * @param iop
   * @param symmetry
   * @return this
   * 
   * 
   */

  public ModulationSet set(String id, P3 r, int modDim, List<Modulation> mods,
                           M3 gammaE, Matrix[] factors, int iop,
                           SymmetryInterface symmetry) {
    this.id = id + "_" + symmetry.getSpaceGroupName();
    strop = symmetry.getSpaceGroupXyz(iop, false);
    this.modDim = modDim;
    this.mods = mods;
    this.iop = iop;
    this.symmetry = symmetry;
    sigma = factors[0];
    tFactor = factors[1];
    isSubsystem = (tFactor != null);
    
    if (isSubsystem) {
      tFactor = tFactor.inverse();
      //gammaE = new M3();
      //symmetry.getSpaceGroupOperation(iop).getRotationScale(gammaE);
    // no, didn't work, but it really should work, I think....
      // why would we want to use the global gammaE?
    }
    
    this.gammaE = gammaE; // ?? should be gammaE_nu?
    
    Matrix rsvs = symmetry.getOperationRsVs(iop);
    gammaIinv = rsvs.getSubmatrix(3,  3,  modDim,  modDim).inverse();
    sI = rsvs.getSubmatrix(3, 3 + modDim, modDim, 1);
    r0 = P3.newP(r);
    tau = gammaIinv.mul(sigma.mul(Matrix.newT(r, true)).sub(sI));
    if (Logger.debuggingHigh)
      Logger.debug("MODSET create r=" + Escape.eP(r) + " si=" + Escape.e(sI.getArray())
              + " ginv=" + gammaIinv.toString().replace('\n', ' '));
    
    t = new Matrix(null, modDim, 1);
    return this;
  }

  @Override
  public SymmetryInterface getUnitCell() {
    return symmetry;
  }
  /**
   * In general, we have, for Fourier:
   * 
   * u_axis(x) = sum[A1 cos(theta) + B1 sin(theta)]
   * 
   * where axis is x, y, or z, and theta = 2n pi x
   * 
   * More generally, we have for a given rotation that is characterized by
   * 
   * X {x4 x5 x6 ...}
   * 
   * Gamma_E (R3 rotation)
   * 
   * Gamma_I (X rotation)
   * 
   * S_I (X translation)
   * 
   * We allow here only up to x6, simply because we are using standard R3
   * rotation objects Matrix3f, P3, V3.
   * 
   * We desire:
   * 
   * u'(X') = Gamma_E u(X)
   * 
   * which is defined as [private communication, Vaclav Petricek]:
   * 
   * u'(X') = Gamma_E sum[ U_c cos(2 pi (n m).Gamma_I^-1{X - S_I}) + U_s sin(2
   * pi (n m).Gamma_I^-1{X - S_I}) ]
   * 
   * where
   * 
   * U_c and U_s are coefficients for cos and sin, respectively (will be a1 and
   * a2 here)
   * 
   * (n m) is an array of Fourier number coefficients, such as (1 0), (1 -1), or
   * (0 2)
   * 
   * In Jmol we precalculate Gamma_I^-1(X - S_I) as tau, but we still have to
   * factor in Gamma_I^-1(t).
   * 
   * @param fracT
   * @param isQ
   * @return this
   */
  
  public synchronized ModulationSet calculate(T3 fracT, boolean isQ) {
    x = y = z = 0;
    htUij = null;
    vOcc = Float.NaN;
    double[][] a = t.getArray();
    for (int i = 0; i < modDim; i++)
      a[i][0] = 0;
    if (isQ && qtOffset != null) {
      Matrix q = new Matrix(null, 3, 1);
      q.getArray()[0] = new double[] { qtOffset.x, qtOffset.y, qtOffset.z };
      a = (t = sigma.mul(q)).getArray();
    }
    if (fracT != null) {
      switch (modDim) {
      default:
        a[2][0] += fracT.z;
        //$FALL-THROUGH$
      case 2:
        a[1][0] += fracT.y;
        //$FALL-THROUGH$
      case 1:
        a[0][0] += fracT.x;
        break;
      }
      if (isSubsystem)
        t = tFactor.mul(t);
    }
    t = gammaIinv.mul(t).add(tau);
    for (int i = mods.size(); --i >= 0;)
      mods.get(i).apply(this, t.getArray());
    gammaE.transform(this);
    return this;
  }
  
  public void addUTens(String utens, float v) {
    if (htUij == null)
      htUij = new Hashtable<String, Float>();
    Float f = htUij.get(utens);
    if (Logger.debuggingHigh)
      Logger.debug("MODSET " + id + " utens=" + utens + " f=" + f + " v="+ v);
    if(f != null)
      v += f.floatValue();
    htUij.put(utens, Float.valueOf(v));

  }

  
  /**
   * Set modulation "t" value, which sets which unit cell in sequence we are
   * looking at; d=1 only.
   * 
   * @param isOn
   * @param qtOffset
   * @param isQ
   * @param scale
   * 
   */
  @Override
  public synchronized void setModTQ(T3 a, boolean isOn, T3 qtOffset, boolean isQ,
                       float scale) {
    if (enabled)
      addTo(a, -1);
    enabled = false;
    this.scale = scale;
    if (qtOffset != null) {
      this.qtOffset.setT(qtOffset);
      this.isQ = isQ;
      if (isQ)
        qtOffset = null;
      calculate(qtOffset, isQ);
    }
    if (isOn) {
      addTo(a, 1);
      enabled = true;
    }
  }

  @Override
  public void addTo(T3 a, float scale) {
    ptTemp.setT(this);
    ptTemp.scale(this.scale * scale);
    symmetry.toCartesian(ptTemp, true);
    a.add(ptTemp);
  }
    
  @Override
  public String getState() {
    String s = "";
    if (qtOffset != null && qtOffset.length() > 0)
      s += "; modulation " + Escape.eP(qtOffset) + " " + isQ + ";\n";
    s += "modulation {selected} " + (enabled ? "ON" : "OFF");
    return s;
  }

  @Override
  public Object getModulation(String type, T3 t456) {
    getModTemp();
    if (type.equals("D")) {
      return P3.newP(t456 == null ? r0 : modTemp.calculate(t456, false));
    }
    return null;
  }

  P3 ptTemp = new P3();
  
  @Override
  public void setTempPoint(T3 a, T3 t456, float vibScale, float scale) {
    if (!enabled)
      return;
    getModTemp();
    addTo(a, -1);
    modTemp.calculate(t456, false).addTo(a, scale);
  }
    
  private void getModTemp() {
    if (modTemp != null)
      return;
    modTemp = new ModulationSet();
    modTemp.id = id;
    modTemp.tau = tau;
    modTemp.mods = mods;
    modTemp.gammaE = gammaE;
    modTemp.modDim = modDim;
    modTemp.gammaIinv = gammaIinv;
    modTemp.sigma = sigma;
    modTemp.r0 = r0;
    modTemp.symmetry = symmetry;
    modTemp.t = t;
  }

  @Override
  public void getInfo(Map<String, Object> info) {
    Hashtable<String, Object> modInfo = new Hashtable<String, Object>();
    modInfo.put("id", id);
    modInfo.put("r0", r0);
    modInfo.put("tau", tau.getArray());
    modInfo.put("modDim", Integer.valueOf(modDim));
    modInfo.put("gammaE", gammaE);
    modInfo.put("gammaIinv", gammaIinv.getArray());
    modInfo.put("sI", sI.getArray());
    modInfo.put("sigma", sigma.getArray());
    modInfo.put("symop", Integer.valueOf(iop + 1));
    modInfo.put("strop", strop);
    modInfo.put("unitcell", symmetry.getUnitCellInfo());

    List<Hashtable<String, Object>> mInfo = new List<Hashtable<String, Object>>();
    for (int i = 0; i < mods.size(); i++)
      mInfo.addLast(mods.get(i).getInfo());
    modInfo.put("mods", mInfo);
    info.put("modulation", modInfo);
  }


}
