package org.jmol.util;

import java.util.Hashtable;

/**
 * A class to allow for more complex vibrations and associated phenomena, such
 * as modulated crystals, including Fourier series, Crenel functions, and
 * sawtooth functions
 * 
 * @author Bob Hanson hansonr@stolaf.edu 8/8/2013
 * 
 */

public class Modulation {

  private static final double TWOPI = 2 * Math.PI;

  private double[] qCoefs;

  private double a1;
  private double a2;
  private double center;
  private double left, right;

  private char axis;
  private final char type;
  private double[] params;

  private String utens;

  public static final char TYPE_DISP_FOURIER = 'f';
  public static final char TYPE_DISP_SAWTOOTH = 's';
  public static final char TYPE_OCC_FOURIER = 'o';
  public static final char TYPE_OCC_CRENEL = 'c';
  public static final char TYPE_U_FOURIER = 'u';

  /**
   * Each atomic modulation involves a fractional coordinate wave vector q, a
   * Fourier power n, a modulation axis (x, y, or, z), and specified parameters
   * that depend upon the type of function.
   * 
   * 
   * @param axis
   * @param type
   * @param params
   * @param utens
   *        TODO
   * @param qCoefs
   */
  public Modulation(char axis, char type, double[] params, String utens,
      double[] qCoefs) {
    if (Logger.debuggingHigh)
      Logger
          .debug("MOD create " + Escape.e(qCoefs) + " axis=" + axis + " type="
              + type + " params=" + Escape.e(params) + " utens=" + utens);
    this.axis = axis;
    this.type = type;
    this.utens = utens;
    this.params = params;
    this.qCoefs = qCoefs;
    switch (type) {
    case TYPE_DISP_FOURIER:
    case TYPE_OCC_FOURIER:
    case TYPE_U_FOURIER:
      a1 = params[0]; // cos
      a2 = params[1]; // sin
      //System.out.println("ccos=" + a1 + " csin=" + a2);
      break;
    case TYPE_DISP_SAWTOOTH:
    case TYPE_OCC_CRENEL:
      center = params[0];
      double width = params[1];
      if (width > 1)
        width = 1; // http://b-incstrdb.ehu.es/incstrdb/CIFFile.php?RefCode=Bi-Sr-Ca-Cu-O_rNdCbetq
      left = center - width / 2;
      right = center + width / 2;
      if (left < 0)
        left += 1;
      if (right > 1)
        right -= 1;
      if (left >= right && left - right < 0.01f)
        left = right + 0.01f;
      a1 = 2 * params[2] / params[1];
      break;
    }
  }

  
  /**
   * see note in ModulationSet
   * 
   * @param ms
   * @param t
   *        -- Vector of coordinates for [x4, x5, x6, ...] 
   * 
   * 
   */

  void apply(ModulationSet ms, double[][] t) {
    double nt = 0;
    for (int i = qCoefs.length; --i >= 0;)
      nt += qCoefs[i] * t[i][0];
    double v = 0;
    //if (type == TYPE_OCC_CRENEL)
    //delta = 0;

    switch (type) {
    case TYPE_DISP_FOURIER:
    case TYPE_OCC_FOURIER:
    case TYPE_U_FOURIER:
      double theta = TWOPI * nt;
      if (a1 != 0)
        v += a1 * Math.cos(theta);
      if (a2 != 0)
        v += a2 * Math.sin(theta);
      if (Logger.debuggingHigh)
        Logger.debug("MOD " + ms.id + " " + Escape.e(qCoefs) + " axis=" + axis
            + " v=" + v + " ccos,csin=" + a1 + "," + a2 + " / theta=" + theta);
      break;
    case TYPE_OCC_CRENEL:

      //  An occupational crenel function along the internal space is
      //  defined as follows:
      //
      //           p(x4)=1   if x4 belongs to the interval [c-w/2,c+w/2]
      //           p(x4)=0   if x4 is outside the interval [c-w/2,c+w/2],

      nt -= Math.floor(nt);
      ms.vOcc = (range(nt) ? 1 : 0);
      ms.vOcc0 = Float.NaN; // absolute
      //System.out.println("MOD " + ms.r + " " +  ms.delta + " " + ms.epsilon + " " + ms.id + " " + ms.v + " l=" + left + " x=" + x4 + " r=" + right);
      return;
    case TYPE_DISP_SAWTOOTH:

      //  _atom_site_displace_special_func_sawtooth_ items are the
      //  adjustable parameters of a sawtooth function. A displacive sawtooth
      //  function along the internal space is defined as follows:
      //
      //    u_x = 2a_x[(x4 − c)/w] 
      //             
      //  for x4 belonging to the interval [c − (w/2), c + (w/2)], where ax,
      //  ay and az are the amplitudes (maximum displacements) along each
      //  crystallographic axis, w is its width, x4 is the internal coordinate
      //  and c is the centre of the function in internal space. ux, uy and
      //  uz must be expressed in relative units.

      // here we have set a1 = 2a_xyz/w 

      nt -= Math.floor(nt);
      if (!range(nt))
        return;

      // x < L < c
      //
      //           /|
      //          / |
      //         / x------------->
      //         |  |   L     /|
      // --------+--|---|----c-+------
      //         0  R   |   /  1       
      //                |  /         
      //                | /
      //                |/

      // becomes

      //                         /|
      //                        / |
      //                       / x|
      //                L     /   |
      // --------+------|----c-+--|----
      //         0      |   /  1  R     
      //                |  /         
      //                | /
      //                |/

      // x > R > c
      //
      //              /|
      //             / |
      //            /  |
      //           /   |         L
      // --------+c----|---------|---+-------
      //         0     R         |   1
      //        <-----------------x / 
      //                         | /
      //                         |/

      // becomes

      //              /|
      //             / |
      //            /  |
      //     L     /   |
      // ----|---+c----|-------------+--------
      //     |   0     R             1
      //     |x /                  
      //     | /
      //     |/

      if (left > right) {
        if (nt < left && left < center)
          nt += 1;
        else if (nt > right && right > center)
          nt -= 1;
      }
      v = a1 * (nt - center);
      break;
    }

    switch (axis) {
    case 'x':
      ms.x += v;
      break;
    case 'y':
      ms.y += v;
      break;
    case 'z':
      ms.z += v;
      break;
    case 'U':
      ms.addUTens(utens, (float) v);
      break;
    default:
      if (Float.isNaN(ms.vOcc))
        ms.vOcc = 0;
      ms.vOcc += (float) v;
    }
  }

  /**
   * Check that left < x4 < right, but allow for folding
   * 
   * @param x4
   * @return true only if x4 is in the (possibly folded) range of left and right
   * 
   */
  private boolean range(double x4) {
    return (left < right ? left <= x4 && x4 <= right : left <= x4
        || x4 <= right);
  }

  public Hashtable<String, Object> getInfo() {
    Hashtable<String, Object> info = new Hashtable<String, Object>();
    info.put("type", "" + type + axis);
    info.put("params", params);
    info.put("qCoefs", qCoefs);
    if (utens != null)
      info.put("Utens", utens);
    return info;
  }

}
