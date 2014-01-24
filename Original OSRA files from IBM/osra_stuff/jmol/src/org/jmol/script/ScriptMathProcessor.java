/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2009-06-12 07:58:28 -0500 (Fri, 12 Jun 2009) $
 * $Revision: 11009 $
 *
 * Copyright (C) 2003-2006  Miguel, Jmol Development, www.jmol.org
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.script;

import javajs.util.AU;
import javajs.util.List;
import java.util.Arrays;

import java.util.Hashtable;
import java.util.Set;

import java.util.Map;


import org.jmol.java.BS;
import org.jmol.modelset.BondSet;
import org.jmol.util.BSUtil;
import org.jmol.util.BoxInfo;
import org.jmol.util.Escape;
import org.jmol.util.Logger;

import javajs.util.A4;
import javajs.util.CU;
import javajs.util.DF;
import javajs.util.M3;
import javajs.util.M4;
import javajs.util.P3;
import javajs.util.P4;
import javajs.util.PT;

import org.jmol.util.Quaternion;
import javajs.util.T3;
import javajs.util.V3;
import org.jmol.viewer.Viewer;

public class ScriptMathProcessor {
  /**
   * Reverse Polish Notation Engine for IF, SET, and %{...} -- Bob Hanson
   * 2/16/2007 Just a (not so simple?) RPN processor that can handle boolean,
   * int, float, String, Point3f, BitSet, Array, Hashtable, Matrix3f, Matrix4f
   * 
   * hansonr@stolaf.edu
   * 
   */

  private boolean chk;
  private boolean wasSyntaxCheck;
  private boolean logMessages;
  private ScriptEvaluator eval;
  private Viewer viewer;

  private T[] oStack = new T[8];
  private SV[] xStack = new SV[8];
  private char[] ifStack = new char[8];
  private int ifPt = -1;
  public int oPt = -1;
  private int xPt = -1;
  private int parenCount;
  private int squareCount;
  private int braceCount;
  public boolean wasX;
  private int incrementX;
  private boolean isArrayItem;
  private boolean asVector;
  public boolean asBitSet;
  private int ptid = 0;
  private int ptx = Integer.MAX_VALUE;

  ScriptMathProcessor(ScriptEvaluator eval, boolean isArrayItem,
      boolean asVector, boolean asBitSet) {
    this.eval = eval;
    this.viewer = eval.viewer;
    this.logMessages = eval.logMessages;
    this.chk = wasSyntaxCheck = eval.chk;
    this.isArrayItem = isArrayItem;
    this.asVector = asVector || isArrayItem;
    this.asBitSet = asBitSet;
    wasX = isArrayItem;
    if (logMessages)
      Logger.debug("initialize RPN");
  }

  @SuppressWarnings("unchecked")
  SV getResult(boolean allowUnderflow)
      throws ScriptException {
    boolean isOK = true;
    while (isOK && oPt >= 0)
      isOK = operate();
    if (isOK) {
      if (asVector) {
        List<SV> result = new  List<SV>();
        for (int i = 0; i <= xPt; i++)
          result.addLast(SV.selectItemVar(xStack[i]));
        return SV.newV(T.vector, result);
      }
      if (xPt == 0) {
        SV x = xStack[0];
        if (x.tok == T.bitset || x.tok == T.varray
            || x.tok == T.string || x.tok == T.matrix3f
            || x.tok == T.matrix4f)
          x = SV.selectItemVar(x);
        if (asBitSet && x.tok == 
          T.varray)
          x = SV.newV(T.bitset, SV.unEscapeBitSetArray((List<SV>)x.value, false));
        return x;
      }
    }
    if (!allowUnderflow && (xPt >= 0 || oPt >= 0)) {
      // iToken--;
      eval.error(ScriptEvaluator.ERROR_invalidArgument);
    }
    return null;
  }

  private void putX(SV x) {
    //System.out.println("putX skipping : " + skipping + " " + x);
    if (skipping)
      return;
    if (++xPt == xStack.length)
      xStack = (SV[]) AU.doubleLength(xStack);
    if (logMessages) {
      Logger.debug("\nputX: " + x);
    }

    xStack[xPt] = x;
    ptx = ++ptid;
  }

  private void putOp(T op) {
    if (++oPt >= oStack.length)
      oStack = (T[]) AU.doubleLength(oStack);
    oStack[oPt] = op;
    ptid++;
  }

  private void putIf(char c) {
    if (++ifPt >= ifStack.length)
      ifStack = (char[]) AU.doubleLength(ifStack);
    ifStack[ifPt] = c;
  }

  public boolean addXVar(SV x) {
    // the standard entry point
    putX(x);
    return wasX = true;
  }

  public boolean addXObj(Object x) {
    // the generic, slower, entry point
    SV v = SV.getVariable(x);
    if (v == null)
      return false;
    putX(v);
    return wasX = true;
  }

  public boolean addXStr(String x) {
    putX(SV.newS(x));
    return wasX = true;
  }

  public boolean addXBool(boolean x) {
    putX(SV.getBoolean(x));
    return wasX = true;
  }

  public boolean addXInt(int x) {
    // no check for unary minus
    putX(SV.newI(x));
    return wasX = true;
  }

  public boolean addXList(List<?> x) {
    putX(SV.getVariableList(x));
    return wasX = true;
  }

  public boolean addXMap(Map<String, ?> x) {
    putX(SV.getVariableMap(x));
    return wasX = true;
  }

  public boolean addXM3(M3 x) {
    putX(SV.newV(T.matrix3f, x));
    return wasX = true;
  }

  public boolean addXM4(M4 x) {
    putX(SV.newV(T.matrix4f, x));
    return wasX = true;
  }

  public boolean addXFloat(float x) {
    if (Float.isNaN(x))
      return addXStr("NaN");
    putX(SV.newV(T.decimal, Float.valueOf(x)));
    return wasX = true;
  }

  public boolean addXBs(BS bs) {
    // the standard entry point for bit sets
    putX(SV.newV(T.bitset, bs));
    return wasX = true;
  }

  public boolean addXPt(P3 pt) {
    putX(SV.newV(T.point3f, pt));
    return wasX = true;
  }

  public boolean addXPt4(P4 pt) {
    putX(SV.newV(T.point4f, pt));
    return wasX = true;
  }

  public boolean addXNum(SV x) throws ScriptException {
    // corrects for x -3 being x - 3
    // only when coming from expression() or parameterExpression()
    if (wasX)
      switch (x.tok) {
      case T.integer:
        if (x.intValue < 0) {
          addOp(T.tokenMinus);
          x = SV.newI(-x.intValue);
        }
        break;
      case T.decimal:
        float f = ((Float) x.value).floatValue();
        if (f < 0 || f == 0 && 1 / f == Float.NEGATIVE_INFINITY) {
          addOp(T.tokenMinus);
          x = SV.newV(T.decimal, Float.valueOf(-f));
        }
        break;
      }
    putX(x);
    return wasX = true;
  }

  public boolean addXAV(SV[] x) {
    putX(SV.getVariableAV(x));
    return wasX = true;
  }

  public boolean addXAD(double[] x) {
    putX(SV.getVariableAD(x));
    return wasX = true;
  }

  public boolean addXAS(String[] x) {
    putX(SV.getVariableAS(x));
    return wasX = true;
  }

  public boolean addXAI(int[] x) {
    putX(SV.getVariableAI(x));
    return wasX = true;
  }

  public boolean addXAII(int[][] x) {
    putX(SV.getVariableAII(x));
    return wasX = true;
  }

  public boolean addXAF(float[] x) {
    putX(SV.getVariableAF(x));
    return wasX = true;
  }

  public boolean addXAFF(float[][] x) {
    putX(SV.getVariableAFF(x));
    return wasX = true;
  }

  private static boolean isOpFunc(T op) {
    return (T.tokAttr(op.tok, T.mathfunc) && op != T.tokenArraySquare 
        || op.tok == T.propselector
        && T.tokAttr(op.intValue, T.mathfunc));
  }

  private boolean skipping;

  /**
   * addOp The primary driver of the Reverse Polish Notation evaluation engine.
   * 
   * This method loads operators onto the oStack[] and processes them based on a
   * precedence system. Operands are added by addX() onto the xStack[].
   * 
   * We check here for syntax issues that were not caught in the compiler. I
   * suppose that should be done at compilation stage, but this is how it is for
   * now.
   * 
   * The processing of functional arguments and (___?___:___) constructs is
   * carried out by pushing markers onto the stacks that later can be used to
   * fill argument lists or turn "skipping" on or off. Note that in the case of
   * skipped sections of ( ? : ) no attempt is made to do syntax checking.
   * [That's not entirely true -- when syntaxChecking is true, that is, when the
   * user is typing at the Jmol application console, then this code is being
   * traversed with dummy variables. That could be improved, for sure.
   * 
   * Actually, there's plenty of room for improvement here. I did this based on
   * what I learned in High School in 1974 -- 35 years ago! -- when I managed to
   * build a mini FORTRAN compiler from scratch in machine code. That was fun.
   * (This was fun, too.)
   * 
   * -- Bob Hanson, hansonr@stolaf.edu 6/9/2009
   * 
   * 
   * @param op
   * @return false if an error condition arises
   * @throws ScriptException
   */
  public boolean addOp(T op) throws ScriptException {
    return addOpAllowMath(op, true);
  }

  private boolean haveSpaceBeforeSquare;
  private int equalCount;
  private static final String qMods = " w:0 x:1 y:2 z:3 normal:4 eulerzxz:5 eulerzyz:6 vector:-1 theta:-2 axisx:-3 axisy:-4 axisz:-5 axisangle:-6 matrix:-9";
  
  boolean addOpAllowMath(T op, boolean allowMathFunc) throws ScriptException {

    if (logMessages) {

      Logger.debug("addOp entry\naddOp: " + op ); //+ " oPt=" + oPt + " ifPt = "
         // + ifPt + " skipping=" + skipping + " wasX=" + wasX);
    }

    // are we skipping due to a ( ? : ) construct?
    int tok0 = (oPt >= 0 ? oStack[oPt].tok : 0);
    skipping = (ifPt >= 0 && (ifStack[ifPt] == 'F' || ifStack[ifPt] == 'X'));
    if (skipping) {
      switch (op.tok) {
      case T.leftparen:
        putOp(op);
        return true;
      case T.colon:
        // dumpStacks("skipping -- :");
        if (tok0 != T.colon || ifStack[ifPt] == 'X')
          return true; // ignore if not a clean opstack or T already processed
        // no object here because we were skipping
        // set to flag end of this parens
        ifStack[ifPt] = 'T';
        wasX = false;
        // dumpStacks("(..False...? .skip.. :<--here.... )");
        skipping = false;
        return true;
      case T.rightparen:
        if (tok0 == T.leftparen) {
          oPt--; // clear opstack
          return true;
        }
        // dumpStacks("skipping -- )");
        if (tok0 != T.colon) {
          putOp(op);
          return true;
        }
        wasX = true;
        // and remove markers
        ifPt--;
        oPt -= 2;
        skipping = false;
        // dumpStacks("(..True...? ... : ...skip...)<--here ");
        return true;
      default:
        return true;
      }
    }

    // Do we have the appropriate context for this operator?

    T newOp = null;
    int tok;
    boolean isLeftOp = false;
    boolean isDotSelector = (op.tok == T.propselector);

    if (isDotSelector && !wasX)
      return false;

    boolean isMathFunc = (allowMathFunc && isOpFunc(op));

    // the word "plane" can also appear alone, not as a function
    if (oPt >= 1 && op.tok != T.leftparen && tok0 == T.plane)
      tok0 = oStack[--oPt].tok;

    // math functions as arguments appear without a prefixing operator
    boolean isArgument = (oPt >= 1 && tok0 == T.leftparen);

    switch (op.tok) {
    case T.spacebeforesquare:
      haveSpaceBeforeSquare = true;
      return true;
    case T.comma:
      if (!wasX)
        return false;
      break;
    case T.min:
    case T.max:
    case T.average:
    case T.sum:
    case T.sum2:
    case T.stddev:
    case T.minmaxmask:
      tok = (oPt < 0 ? T.nada : tok0);
      if (!wasX
          || !(tok == T.propselector || tok == T.bonds || tok == T.atoms))
        return false;
      oStack[oPt].intValue |= op.tok;
      return true;
    case T.leftsquare: // {....}[n][m]
      isLeftOp = true;
      if (!wasX || haveSpaceBeforeSquare) {
        squareCount++;
        op = newOp = T.tokenArraySquare;
        haveSpaceBeforeSquare = false;
      }
      break;
    case T.rightsquare:
      break;
    case T.minusMinus:
    case T.plusPlus:
      incrementX = (op.tok == T.plusPlus ? 1 : -1);
      if (ptid == ptx) {
        if (chk)
          return true;
        SV x = xStack[xPt];
        xStack[xPt] = SV.newS("").setv(x, false);
        return x.increment(incrementX);
      }
      break;
    case T.minus:
      if (wasX)
        break;
      addXInt(0);
      op = SV.newV(T.unaryMinus, "-");
      break;
    case T.rightparen: // () without argument allowed only for math funcs
      if (!wasX && oPt >= 1 && tok0 == T.leftparen
          && !isOpFunc(oStack[oPt - 1]))
        return false;
      break;
    case T.opNot:
    case T.leftparen:
      isLeftOp = true;
      //$FALL-THROUGH$
    default:
      if (isMathFunc) {
        if (!isDotSelector && wasX && !isArgument)
          return false;
        newOp = op;
        isLeftOp = true;
        break;
      }
      if (wasX == isLeftOp && tok0 != T.propselector) // for now, because
        // we have .label
        // and .label()
        return false;
      break;
    }

    // do we need to operate?

    while (oPt >= 0
        && tok0 != T.colon
        && (!isLeftOp || tok0 == T.propselector
            && (op.tok == T.propselector || op.tok == T.leftsquare))
        && T.getPrecedence(tok0) >= T.getPrecedence(op.tok)) {

      if (logMessages) {
        Logger.debug("\noperating, oPt=" + oPt + " isLeftOp=" + isLeftOp
            + " oStack[oPt]=" + T.nameOf(tok0) + "        prec="
            + T.getPrecedence(tok0) + " pending op=\""
            + T.nameOf(op.tok) + "\" prec=" + T.getPrecedence(op.tok));
        dumpStacks("operating");
      }
      // ) and ] must wait until matching ( or [ is found
      if (op.tok == T.rightparen && tok0 == T.leftparen) {
        // (x[2]) finalizes the selection
        if (xPt >= 0)
          xStack[xPt] = SV.selectItemVar(xStack[xPt]);
        break;
      }
      if (op.tok == T.rightsquare && tok0 == T.array) {
        break;
      }
      if (op.tok == T.rightsquare && tok0 == T.leftsquare) {
        if (isArrayItem && squareCount == 1 && equalCount == 0) {
          addXVar(SV.newT(T.tokenArraySelector));
          break;
        }
        if (!doBitsetSelect())
          return false;
        break;
      }

      // if not, it's time to operate

      if (!operate())
        return false;
      tok0 = (oPt >= 0 ? oStack[oPt].tok : 0);
    }

    // now add a marker on the xStack if necessary

    if (newOp != null)
      addXVar(SV.newV(T.opEQ, newOp));

    // fix up counts and operand flag
    // right ) and ] are not added to the stack

    switch (op.tok) {
    case T.leftparen:
      //System.out.println("----------(----------");
      parenCount++;
      wasX = false;
      break;
    case T.opIf:
      //System.out.println("---------IF---------");
      boolean isFirst = getX().asBoolean();
      if (tok0 == T.colon)
        ifPt--;
      else
        putOp(T.tokenColon);
      putIf(isFirst ? 'T' : 'F');
      skipping = !isFirst;
      wasX = false;
      // dumpStacks("(.." + isFirst + "...?<--here ... :...skip...) ");
      return true;
    case T.colon:
      //System.out.println("----------:----------");
      if (tok0 != T.colon)
        return false;
      if (ifPt < 0)
        return false;
      ifStack[ifPt] = 'X';
      wasX = false;
      skipping = true;
      // dumpStacks("(..True...? ... :<--here ...skip...) ");
      return true;
    case T.rightparen:
      //System.out.println("----------)----------");
      wasX = true;
      if (parenCount-- <= 0)
        return false;
      if (tok0 == T.colon) {
        // remove markers
        ifPt--;
        oPt--;
        // dumpStacks("(..False...? ...skip... : ...)<--here ");
      }
      oPt--;
      if (oPt < 0)
        return true;
      if (isOpFunc(oStack[oPt]) && !evaluateFunction(T.nada))
        return false;
      skipping = (ifPt >= 0 && ifStack[ifPt] == 'X');
      return true;
    case T.comma:
      wasX = false;
      return true;
    case T.leftsquare:
      squareCount++;
      wasX = false;
      break;
    case T.rightsquare:
      wasX = true;
      if (squareCount-- <= 0 || oPt < 0)
        return false;
      if (oStack[oPt].tok == T.array)
        return evaluateFunction(T.leftsquare);
      oPt--;
      return true;
    case T.propselector:
      wasX = (!allowMathFunc || !T.tokAttr(op.intValue, T.mathfunc));
      break;
    case T.leftbrace:
      braceCount++;
      wasX = false;
      break;
    case T.rightbrace:
      if (braceCount-- <= 0)
        return false;
      wasX = false;
      break;
    case T.opAnd:
    case T.opOr:
      if (!wasSyntaxCheck && xPt < 0)
        return false;
      if (!wasSyntaxCheck && xStack[xPt].tok != T.bitset && xStack[xPt].tok != T.varray) {
        // check to see if we need to evaluate the second operand or not
        // if not, then set this to syntax check in order to skip :)
        // Jmol 12.0.4, Jmol 12.1.2
        boolean tf = getX().asBoolean();
        addXVar(SV.getBoolean(tf));
        if (tf == (op.tok == T.opOr)) { // TRUE or.. FALSE and...
          chk = true;
          op = (op.tok == T.opOr ? T.tokenOrTRUE : T.tokenAndFALSE);
        }
      }
      wasX = false;
      break;
    case T.opEQ:
      if (squareCount == 0)
        equalCount++;
      wasX = false;
      break;
    default:
      wasX = false;
    }

    // add the operator if possible

    putOp(op);

    // dumpStacks("putOp complete");
    if (op.tok == T.propselector
        && (op.intValue & ~T.minmaxmask) == T.function
        && op.intValue != T.function) {
      return evaluateFunction(T.nada);
    }
    return true;
  }

  private boolean doBitsetSelect() {
    if (xPt < 0 || xPt == 0 && !isArrayItem) {
      return false;
    }
    SV var1 = xStack[xPt--];
    SV var = xStack[xPt];
    if (var.tok == T.varray && var1.tok == T.string 
        && var.intValue != Integer.MAX_VALUE) {
      // allow for x[1]["test"][1]["here"]
      // common in getproperty business
      // prior to 12.2/3.18, x[1]["id"] was misread as x[1][0]
      var = (SV) SV.selectItemTok(var, Integer.MIN_VALUE);
    }
    if (var.tok == T.hash) {
      SV v = var.mapValue(SV.sValue(var1));
      xStack[xPt] = (v == null ? SV.newS("") : v);
      return true;
    }
    int i = var1.asInt();
    switch (var.tok) {
    default:
      var = SV.newS(SV.sValue(var));
      //$FALL-THROUGH$
    case T.bitset:
    case T.varray:
    case T.string:
    case T.matrix3f:
    case T.matrix4f:
      xStack[xPt] = (SV) SV.selectItemTok(var, i);
      break;
    }
    return true;
  }

  void dumpStacks(String message) {
    Logger.debug("\n\n------------------\nRPN stacks: " + message + "\n");
    for (int i = 0; i <= xPt; i++)
      Logger.debug("x[" + i + "]: " + xStack[i]);
    Logger.debug("\n");
    for (int i = 0; i <= oPt; i++)
      Logger.debug("o[" + i + "]: " + oStack[i] + " prec="
          + T.getPrecedence(oStack[i].tok));
    Logger.debug(" ifStack = " + (new String(ifStack)).substring(0, ifPt + 1));
  }

  public SV getX() throws ScriptException {
    if (xPt < 0)
      eval.error(ScriptEvaluator.ERROR_endOfStatementUnexpected);
    SV v = SV.selectItemVar(xStack[xPt]);
    xStack[xPt--] = null;
    return v;
  }

  private boolean evaluateFunction(int tok) throws ScriptException {
    T op = oStack[oPt--];
    // for .xxx or .xxx() functions
    // we store the token in the intValue field of the propselector token
    if (tok == T.nada)
      tok = (op.tok == T.propselector ? op.intValue & ~T.minmaxmask : op.tok);

    int nParamMax = T.getMaxMathParams(tok); // note - this is NINE for
    // dot-operators
    int nParam = 0;
    int pt = xPt;
    while (pt >= 0 && xStack[pt--].value != op)
      nParam++;
    if (nParamMax > 0 && nParam > nParamMax)
      return false;
    SV[] args = new SV[nParam];
    for (int i = nParam; --i >= 0;)
      args[i] = getX();
    xPt--;
    // no script checking of functions because
    // we cannot know what variables are real
    // if this is a property selector, as in x.func(), then we
    // just exit; otherwise we add a new TRUE to xStack
    return (!chk ? eval.getExtension().evaluate(this, op, args, tok)
        : op.tok == T.propselector ? true : addXBool(true));
  }
  
  
  @SuppressWarnings("unchecked")
  private boolean operate() throws ScriptException {

    T op = oStack[oPt--];
    P3 pt;
    M3 m;
    String s;

    if (logMessages) {
      dumpStacks("operate: " + op);
    }

    // check for a[3][2] 
    if (isArrayItem && squareCount == 0 && equalCount == 1 && oPt < 0
        && (op.tok == T.opEQ)) {
      return true;
    }

    SV x2 = getX();
    if (x2 == T.tokenArraySelector)
      return false;

    //unnecessary  -- getX() does this. x2 = selectX(x2);

    // unary:

    if (op.tok == T.minusMinus || op.tok == T.plusPlus) {
      if (!chk && !x2.increment(incrementX))
        return false;
      wasX = true;
      putX(x2); // reverse getX()
      return true;
    }
    if (op.tok == T.opNot) {
      if (chk)
        return addXBool(true);
      switch (x2.tok) {
      case T.point4f: // quaternion
        return addXPt4((Quaternion.newP4((P4) x2.value)).inv().toPoint4f());
      case T.matrix3f:
        m = M3.newM((M3) x2.value);
        m.invert();
        return addXM3(m);
      case T.matrix4f:
        M4 m4 = M4.newM((M4) x2.value);
        m4.invert();
        return addXM4(m4);
      case T.bitset:
        return addXBs(BSUtil.copyInvert(SV.bsSelectVar(x2),
            (x2.value instanceof BondSet ? viewer.getBondCount() : viewer
                .getAtomCount())));
      default:
        return addXBool(!x2.asBoolean());
      }
    }
    int iv = op.intValue & ~T.minmaxmask;
    if (op.tok == T.propselector) {
      switch (iv) {
      case T.identifier:
        return getAllProperties(x2, (String) op.value);
      case T.length:
      case T.count:
      case T.size:
        if (iv == T.length && x2.value instanceof BondSet)
          break;
        return addXInt(SV.sizeOf(x2));
      case T.type:
        return addXStr(typeOf(x2));
      case T.keys:
        if (x2.tok != T.hash)
          return addXStr("");
        Set<String> keyset = ((Map<String, SV>) x2.value).keySet();
        String[] keys = keyset.toArray(new String[keyset.size()]);
        Arrays.sort(keys);
        return addXAS(keys);
      case T.lines:
        switch (x2.tok) {
        case T.matrix3f:
        case T.matrix4f:
          s = SV.sValue(x2);
          s = PT.simpleReplace(s.substring(1, s.length() - 1), "],[",
              "]\n[");
          break;
        case T.string:
          s = (String) x2.value;
          break;
        default:
          s = SV.sValue(x2);
        }
        s = PT.simpleReplace(s, "\n\r", "\n").replace('\r', '\n');
        return addXAS(PT.split(s, "\n"));
      case T.color:
        switch (x2.tok) {
        case T.string:
        case T.varray:
          s = SV.sValue(x2);
          pt = new P3();
          return addXPt(CU.colorPtFromString(s, pt));
        case T.integer:
        case T.decimal:
          return addXPt(viewer.getColorPointForPropertyValue(SV
              .fValue(x2)));
        case T.point3f:
          return addXStr(Escape.escapeColor(CU
              .colorPtToFFRGB((P3) x2.value)));
        default:
          // handle bitset later
        }
        break;
      case T.boundbox:
        return (chk ? addXStr("x") : getBoundBox(x2));
      }
      if (chk)
        return addXStr(SV.sValue(x2));
      if (x2.tok == T.string) {
        Object v = SV
            .unescapePointOrBitsetAsVariable(SV.sValue(x2));
        if (!(v instanceof SV))
          return false;
        x2 = (SV) v;
      }
      if (op.tok == x2.tok)
        x2 = getX();
      return getPointOrBitsetOperation(op, x2);
    }

    // binary:
    SV x1 = getX();
    if (chk) {
      if (op == T.tokenAndFALSE || op == T.tokenOrTRUE)
        chk = false;
      return addXVar(SV.newT(x1));
    }
    
    return binaryOp(op, x1, x2);
  }

  //  public SV selectX(SV x2) {
  //    return (x2.tok == T.varray || x2.tok == T.matrix3f || x2.tok == T.matrix4f ? SV
  //        .selectItemVar(x2) : x2);
  //  }

  @SuppressWarnings("unchecked")
  public boolean binaryOp(T op, SV x1, SV x2) throws ScriptException {
    P3 pt;
    P4 pt4;
    M3 m;
    String s;
    float f;

    switch (op.tok) {
    case T.opAND:
    case T.opAnd:
      switch (x1.tok) {
      case T.bitset:
        BS bs = SV.bsSelectVar(x1);
        switch (x2.tok) {
        case T.integer:
          int x = x2.asInt();
          return (addXBool(x < 0 ? false : bs.get(x)));
        case T.bitset:
          bs = BSUtil.copy(bs);
          bs.and(SV.bsSelectVar(x2));
          return addXBs(bs);
        }
        break;
      }
      return addXBool(x1.asBoolean() && x2.asBoolean());
    case T.opOr:
      switch (x1.tok) {
      case T.bitset:
        BS bs = BSUtil.copy(SV.bsSelectVar(x1));
        switch (x2.tok) {
        case T.bitset:
          bs.or(SV.bsSelectVar(x2));
          return addXBs(bs);
        case T.integer:
          int x = x2.asInt();
          if (x < 0)
            break;
          bs.set(x);
          return addXBs(bs);
        case T.varray:
          List<SV> sv = (List<SV>) x2.value;
          for (int i = sv.size(); --i >= 0;) {
            int b = sv.get(i).asInt();
            if (b >= 0)
              bs.set(b);
          }
          return addXBs(bs);
        }
        break;
      case T.varray:
        return addXVar(SV.concatList(x1, x2, false));
      }
      return addXBool(x1.asBoolean() || x2.asBoolean());
    case T.opXor:
      if (x1.tok == T.bitset && x2.tok == T.bitset) {
        BS bs = BSUtil.copy(SV.bsSelectVar(x1));
        bs.xor(SV.bsSelectVar(x2));
        return addXBs(bs);
      }
      boolean a = x1.asBoolean();
      boolean b = x2.asBoolean();
      return addXBool(a && !b || b && !a);
    case T.opToggle:
      if (x1.tok != T.bitset || x2.tok != T.bitset)
        return false;
      return addXBs(BSUtil.toggleInPlace(BSUtil.copy(SV.bsSelectVar(x1)),
          SV.bsSelectVar(x2)));
    case T.opLE:
      return addXBool(x1.asFloat() <= x2.asFloat());
    case T.opGE:
      return addXBool(x1.asFloat() >= x2.asFloat());
    case T.opGT:
      return addXBool(x1.asFloat() > x2.asFloat());
    case T.opLT:
      return addXBool(x1.asFloat() < x2.asFloat());
    case T.opEQ:
      return addXBool(SV.areEqual(x1, x2));
    case T.opNE:
      return addXBool(!SV.areEqual(x1, x2));
    case T.plus:
      switch (x1.tok) {
      case T.integer:
        if (!isDecimal(x2))
          return addXInt(x1.intValue + x2.asInt());
        break;
      case T.string:
        return addXVar(SV.newS(SV.sValue(x1) + SV.sValue(x2)));
      case T.point4f:
        Quaternion q1 = Quaternion.newP4((P4) x1.value);
        switch (x2.tok) {
        default:
          return addXPt4(q1.add(x2.asFloat()).toPoint4f());
        case T.point4f:
          return addXPt4(q1.mulQ(Quaternion.newP4((P4) x2.value)).toPoint4f());
        }
      case T.point3f:
        pt = P3.newP((P3) x1.value);
        switch (x2.tok) {
        case T.point3f:
          pt.add((P3) x2.value);
          return addXPt(pt);
        case T.point4f:
          // extract {xyz}
          pt4 = (P4) x2.value;
          pt.add(P3.new3(pt4.x, pt4.y, pt4.z));
          return addXPt(pt);
        default:
          f = x2.asFloat();
          return addXPt(P3.new3(pt.x + f, pt.y + f, pt.z + f));
        }
      case T.matrix3f:
        switch (x2.tok) {
        default:
          return addXFloat(x1.asFloat() + x2.asFloat());
        case T.matrix3f:
          m = M3.newM((M3) x1.value);
          m.add((M3) x2.value);
          return addXM3(m);
        case T.point3f:
          return addXM4(getMatrix4f((M3) x1.value, (P3) x2.value));
        }
      case T.varray:
        return addXVar(SV.concatList(x1, x2, true));
      }
      return addXFloat(x1.asFloat() + x2.asFloat());
    case T.minus:
      switch (x1.tok) {
      case T.integer:
        if (!isDecimal(x2))
          return addXInt(x1.intValue - x2.asInt());
        break;
      case T.string:
        if (!isDecimal(x2) && !isDecimal(x1))
          return addXInt(x1.asInt() - x2.asInt());
        break;
      case T.hash:
        Map<String, SV> ht = new Hashtable<String, SV>(
            (Map<String, SV>) x1.value);
        ht.remove(SV.sValue(x2));
        return addXVar(SV.getVariableMap(ht));
      case T.matrix3f:
        if (x2.tok != T.matrix3f)
          break;
        m = M3.newM((M3) x1.value);
        m.sub((M3) x2.value);
        return addXM3(m);
      case T.matrix4f:
        if (x2.tok != T.matrix4f)
          break;
        M4 m4 = M4.newM((M4) x1.value);
        m4.sub((M4) x2.value);
        return addXM4(m4);
      case T.point3f:
        pt = P3.newP((P3) x1.value);
        switch (x2.tok) {
        case T.point3f:
          pt.sub((P3) x2.value);
          return addXPt(pt);
        case T.point4f:
          // extract {xyz}
          pt4 = (P4) x2.value;
          pt.sub(P3.new3(pt4.x, pt4.y, pt4.z));
          return addXPt(pt);
        }
        f = x2.asFloat();
        return addXPt(P3.new3(pt.x - f, pt.y - f, pt.z - f));
      case T.point4f:
        Quaternion q1 = Quaternion.newP4((P4) x1.value);
        if (x2.tok == T.point4f) {
          Quaternion q2 = Quaternion.newP4((P4) x2.value);
          return addXPt4(q2.mulQ(q1.inv()).toPoint4f());
        }
        return addXPt4(q1.add(-x2.asFloat()).toPoint4f());
      }
      return addXFloat(x1.asFloat() - x2.asFloat());
    case T.unaryMinus:
      switch (x2.tok) {
      case T.integer:
        return addXInt(-x2.asInt());
      case T.point3f:
        pt = P3.newP((P3) x2.value);
        pt.scale(-1f);
        return addXPt(pt);
      case T.point4f:
        pt4 = P4.newPt((P4) x2.value);
        pt4.scale(-1f);
        return addXPt4(pt4);
      case T.matrix3f:
        m = M3.newM((M3) x2.value);
        m.transpose();
        return addXM3(m);
      case T.matrix4f:
        M4 m4 = M4.newM((M4) x2.value);
        m4.transpose();
        return addXM4(m4);
      case T.bitset:
        return addXBs(BSUtil.copyInvert(
            SV.bsSelectVar(x2),
            (x2.value instanceof BondSet ? viewer.getBondCount() : viewer
                .getAtomCount())));
      }
      return addXFloat(-x2.asFloat());
    case T.mul3:
      if (x1.tok == T.point3f && x2.tok == T.point3f) {
        pt = (P3) x1.value;
        P3 pt2 = (P3) x2.value;
        return addXPt(P3.new3(pt.x * pt2.x, pt.y * pt2.y, pt.z * pt2.z));
      }
      //$FALL-THROUGH$
    case T.times:
      switch (x1.tok) {
      case T.integer:
        return (isDecimal(x2) ?
            addXFloat(x1.intValue * x2.asFloat())
                : addXInt(x1.intValue * x2.asInt()));
      case T.string:
        return (isDecimal(x2) || isDecimal(x1) ?
            addXFloat(x1.asFloat() * x2.asFloat()) : 
              addXInt(x1.asInt() * x2.asInt()));
      }      
      pt = (x1.tok == T.matrix3f ? ptValue(x2, false)
          : x2.tok == T.matrix3f ? ptValue(x1, false) : null);
      pt4 = (x1.tok == T.matrix4f ? planeValue(x2)
          : x2.tok == T.matrix4f ? planeValue(x1) : null);
      // checking here to make sure arrays remain arrays and
      // points remain points with matrix operations.
      // we check x2, because x1 could be many things.
      switch (x2.tok) {
      case T.matrix3f:
        if (pt != null) {
          // pt * m
          M3 m3b = M3.newM((M3) x2.value);
          m3b.transpose();
          m3b.transform(pt);
          if (x1.tok == T.varray)
            return addXVar(SV.getVariableAF(new float[] { pt.x, pt.y, pt.z }));
          return addXPt(pt);
        }
        if (pt4 != null)
          // q * m --> q
          return addXPt4((Quaternion.newP4(pt4).mulQ(Quaternion
              .newM((M3) x2.value))).toPoint4f());
          break;
      case T.matrix4f:
        // pt4 * m4
        // [a b c d] * m4
        if (pt4 != null) {
          M4 m4b = M4.newM((M4) x2.value);
          m4b.transpose();
          m4b.transform4(pt4);
          if (x1.tok == T.varray)
            return addXVar(SV.getVariableAF(new float[] { pt4.x, pt4.y, pt4.z,
                pt4.w }));
          return addXPt4(pt4);
        }
        break;
      }
      switch (x1.tok) {
      case T.matrix3f:
        M3 m3 = (M3) x1.value;
        if (pt != null) {
          m3.transform(pt);
          if (x2.tok == T.varray)
            return addXVar(SV.getVariableAF(new float[] { pt.x, pt.y, pt.z }));
          return addXPt(pt);
        }
        switch (x2.tok) {
        case T.matrix3f:
          m = M3.newM((M3) x2.value);
          m.mul2(m3, m);
          return addXM3(m);
        case T.point4f:
          // m * q
          return addXM3(Quaternion.newM(m3)
              .mulQ(Quaternion.newP4((P4) x2.value)).getMatrix());
        }
        f = x2.asFloat();
        A4 aa = new A4();
        aa.setM(m3);
        aa.angle *= f;
        M3 m2 = new M3();
        m2.setAA(aa);
        return addXM3(m2);
      case T.matrix4f:
        M4 m4 = (M4) x1.value;
        if (pt != null) {
          m4.transform(pt);
          if (x2.tok == T.varray)
            return addXVar(SV.getVariableAF(new float[] { pt.x, pt.y, pt.z }));
          return addXPt(pt);
        }
        if (pt4 != null) {
          m4.transform4(pt4);
          if (x2.tok == T.varray)
            return addXVar(SV.getVariableAF(new float[] { pt4.x, pt4.y, pt4.z,
                pt4.w }));
          return addXPt4(pt4);
        }
        if (x2.tok == T.matrix4f) {
          M4 m4b = M4.newM((M4) x2.value);
          m4b.mul2(m4, m4b);
          return addXM4(m4b);
        }
        return addXStr("NaN");
      case T.point3f:
        pt = P3.newP((P3) x1.value);
        switch (x2.tok) {
        case T.point3f:
          P3 pt2 = ((P3) x2.value);
          return addXFloat(pt.x * pt2.x + pt.y * pt2.y + pt.z * pt2.z);
        }
        f = x2.asFloat();
        return addXPt(P3.new3(pt.x * f, pt.y * f, pt.z * f));
      case T.point4f:
        if (x2.tok == T.point4f)
          // quaternion multiplication
          // note that Point4f is {x,y,z,w} so we use that for
          // quaternion notation as well here.
          return addXPt4(Quaternion.newP4((P4) x1.value)
              .mulQ(Quaternion.newP4((P4) x2.value)).toPoint4f());
        return addXPt4(Quaternion.newP4((P4) x1.value).mul(x2.asFloat())
            .toPoint4f());
      }
      return addXFloat(x1.asFloat() * x2.asFloat());
    case T.divide:
      float f2;
      switch (x1.tok) {
      case T.integer:
        if (x2.tok == T.integer && x2.intValue != 0)
          return addXInt(x1.intValue / x2.intValue);
        if (!isDecimal(x2))
          return addXInt(x1.intValue / x2.asInt());
        break;
      case T.string:
        int i2;
        if (!isDecimal(x1) && !isDecimal(x2) && (i2 = x2.asInt()) != 0)
          return addXInt(x1.asInt() / i2);
        break;
      case T.point3f:
        pt = P3.newP((P3) x1.value);
        return addXPt((f2 = x2.asFloat()) == 0 ? P3.new3(Float.NaN, Float.NaN,
            Float.NaN) : P3.new3(pt.x / f2, pt.y / f2, pt.z / f2));
      case T.point4f:
        return addXPt4(x2.tok == T.point4f ? Quaternion.newP4((P4) x1.value)
            .div(Quaternion.newP4((P4) x2.value)).toPoint4f() : (f2 = x2
            .asFloat()) == 0 ? P4.new4(Float.NaN, Float.NaN, Float.NaN,
            Float.NaN) : Quaternion.newP4((P4) x1.value).mul(1 / f2)
            .toPoint4f());
      }
      return addXFloat(x1.asFloat() / x2.asFloat());
    case T.leftdivide:
      f = x2.asFloat();
      if (x1.tok == T.point4f) {
        if (f == 0)
          return addXPt4(P4.new4(Float.NaN, Float.NaN, Float.NaN, Float.NaN));
        if (x2.tok == T.point4f)
          return addXPt4(Quaternion.newP4((P4) x1.value)
              .divLeft(Quaternion.newP4((P4) x2.value)).toPoint4f());
        return addXPt4(Quaternion.newP4((P4) x1.value).mul(1 / f).toPoint4f());
      }
      return addXInt(f == 0 ? 0 : (int) Math.floor(x1.asFloat() / x2.asFloat()));
    case T.timestimes:
      f = (float) Math.pow(x1.asFloat(), x2.asFloat());
      return (x1.tok == T.integer && x2.tok == T.integer ? addXInt((int) f)
          : addXFloat(f));
    case T.percent:
      // more than just modulus

      // float % n round to n digits; n = 0 does "nice" rounding
      // String % -n trim to width n; left justify
      // String % n trim to width n; right justify
      // Point3f % n ah... sets to multiple of unit cell!
      // bitset % n
      // Point3f * Point3f does dot product
      // Point3f / Point3f divides by magnitude
      // float * Point3f gets m agnitude
      // Point4f % n returns q0, q1, q2, q3, or theta
      // Point4f % Point4f
      s = null;
      int n = x2.asInt();
      switch (x1.tok) {
      case T.on:
      case T.off:
      case T.integer:
      default:
        break;
      case T.decimal:
        f = x1.asFloat();
        // neg is scientific notation
        if (n == 0)
          return addXInt(Math.round(f));
        s = DF.formatDecimal(f, n);
        return addXStr(s);
      case T.string:
        s = (String) x1.value;
        if (n == 0)
          return addXStr(PT.trim(s, "\n\t "));
        if (n == 9999)
          return addXStr(s.toUpperCase());
        if (n == -9999)
          return addXStr(s.toLowerCase());
        if (n > 0)
          return addXStr(PT.formatS(s, n, n, false, false));
        return addXStr(PT.formatS(s, n, n - 1, true, false));
      case T.varray:
        String[] list = SV.listValue(x1);
        for (int i = 0; i < list.length; i++) {
          if (n == 0)
            list[i] = list[i].trim();
          else if (n > 0)
            list[i] = PT.formatS(list[i], n, n, true, false);
          else
            list[i] = PT.formatS(s, -n, n, false, false);
        }
        return addXAS(list);
      case T.point3f:
        pt = P3.newP((P3) x1.value);
        viewer.toUnitCell(pt, P3.new3(n, n, n));
        return addXPt(pt);
      case T.point4f:
        pt4 = (P4) x1.value;
        if (x2.tok == T.point3f)
          return addXPt((Quaternion.newP4(pt4)).transformPt((P3) x2.value));
        if (x2.tok == T.point4f) {
          P4 v4 = P4.newPt((P4) x2.value);
          (Quaternion.newP4(pt4)).getThetaDirected(v4);
          return addXPt4(v4);
        }
        if (n == 0 && x2.tok == T.string) {
          s = " " + x2.value.toString().trim().toLowerCase() + ":";
          int i = qMods.indexOf(s);
          n = (i >= 0 ? PT.parseInt(qMods.substring(i + s.length())) : -99);
        }
        switch (n) {
        // q%0 w
        // q%1 x
        // q%2 y
        // q%3 z
        // q%4 normal
        // q%5 EulerZXZ (degrees)
        // q%6 EulerZYZ (degrees)
        // q%-1 vector(1)
        // q%-2 theta
        // q%-3 Matrix column 0
        // q%-4 Matrix column 1
        // q%-5 Matrix column 2
        // q%-6 AxisAngle format
        // q%-9 Matrix format
        case 0:
          return addXFloat(pt4.w);
        case 1:
          return addXFloat(pt4.x);
        case 2:
          return addXFloat(pt4.y);
        case 3:
          return addXFloat(pt4.z);
        }
        Quaternion q = Quaternion.newP4(pt4);
        switch (n) {
        case 4:
          return addXPt(P3.newP(q.getNormal()));
        case 5:
          return addXAF(q.getEulerZXZ());
        case 6:
          return addXAF(q.getEulerZYZ());
        case -1:
          return addXPt(P3.newP(q.getVector(-1)));
        case -2:
          return addXFloat(q.getTheta());
        case -3:
          return addXPt(P3.newP(q.getVector(0)));
        case -4:
          return addXPt(P3.newP(q.getVector(1)));
        case -5:
          return addXPt(P3.newP(q.getVector(2)));
        case -6:
          A4 ax = q.toAxisAngle4f();
          return addXPt4(P4.new4(ax.x, ax.y, ax.z,
              (float) (ax.angle * 180 / Math.PI)));
        case -9:
          return addXM3(q.getMatrix());
        default:
          return addXStr("NaN");
        }
      case T.matrix4f:
        M4 m4 = (M4) x1.value;
        switch (n) {
        case 1:
          M3 m3 = new M3();
          m4.getRotationScale(m3);
          return addXM3(m3);
        case 2:
          V3 v3 = new V3();
          m4.get(v3);
          return addXPt(P3.newP(v3));
        default:
          return false;
        }
      case T.bitset:
        return addXBs(SV.bsSelectRange(x1, n));
      }
      return addXInt(n == 0 ? 0 : x1.asInt() % n);
    }
    return true;
  }

  private boolean isDecimal(SV x) {
    String s;
    return (x.tok == T.decimal || x.tok == T.string
        && ((s = SV.sValue(x).trim()).indexOf(".") >= 0 || s.indexOf("+") > 0 || s
            .lastIndexOf("-") > 0));
  }

  public P3 ptValue(SV x, boolean allowFloat) throws ScriptException {
    Object pt;
    if (chk)
      return new P3();
    switch (x.tok) {
    case T.point3f:
      return (P3) x.value;
    case T.bitset:
      return (P3) eval.getBitsetProperty(SV.bsSelectVar(x), T.xyz, null, null,
          x.value, null, false, Integer.MAX_VALUE, false);
    case T.string:
      pt = Escape.uP(SV.sValue(x));
      if (pt instanceof P3)
        return (P3) pt;
      break;
    case T.varray:
      pt = Escape.uP("{" + SV.sValue(x) + "}");
      if (pt instanceof P3)
        return (P3) pt;
      break;
    }
    if (!allowFloat)
      return null;
    float f = SV.fValue(x);
    return P3.new3(f, f, f);
  }

  public P4 planeValue(T x) {
    if (chk)
      return new P4();
    switch (x.tok) {
    case T.point4f:
      return (P4) x.value;
    case T.varray:
    case T.string:
      Object pt = Escape.uP(SV.sValue(x));
      return (pt instanceof P4 ? (P4) pt : null);
    case T.bitset:
      // ooooh, wouldn't THIS be nice!
      break;
    }
    return null;
  }

  static private String typeOf(SV x) {
    int tok = (x == null ? T.nada : x.tok);
    switch (tok) {
    case T.on:
    case T.off:
      return "boolean";
    case T.bitset:
      return (x.value instanceof BondSet ? "bondset" : "bitset");
    case T.integer:
    case T.decimal:
    case T.point3f:
    case T.point4f:
    case T.string:
    case T.varray:
    case T.hash:
    case T.matrix3f:
    case T.matrix4f:
      return T.astrType[tok];
    }
    return "?";
  }

  private boolean getAllProperties(SV x2, String abbr)
      throws ScriptException {
    if (x2.tok != T.bitset)
      return false;
    if (chk)
      return addXStr("");
    BS bs = SV.bsSelectVar(x2);
    List<T> tokens;
    int n = bs.cardinality();
    if (n == 0
        || (tokens = T.getAtomPropertiesLike(abbr.substring(0, abbr
            .length() - 1))) == null)
      return addXStr("");
    Map<String, Object> ht = new Hashtable<String, Object>();
    int index = (n == 1 ? bs.nextSetBit(0) : Integer.MAX_VALUE);
    for (int i = tokens.size(); --i >= 0;) {
      T t = tokens.get(i);
      int tok = t.tok;
      switch (tok) {
      case T.configuration:
      case T.cell:
        continue;
      default:
        if (index == Integer.MAX_VALUE)
          tok |= T.minmaxmask;
        ht.put((String) t.value, SV.getVariable(
            eval.getBitsetProperty(bs, tok, null, null, null, null, false, index, true)));
      }
    }
    return addXMap(ht);
  }

  public static M4 getMatrix4f(M3 matRotate, T3 vTranslate) {
    return M4.newMV(matRotate, vTranslate == null ? new V3() : V3.newV(vTranslate));
  }

  private boolean getBoundBox(SV x2) {
    if (x2.tok != T.bitset)
      return false;
    if (chk)
      return addXStr("");
    BoxInfo b = viewer.getBoxInfo(SV.bsSelectVar(x2), 1);
    P3[] pts = b.getBoundBoxPoints(true);
    List<P3> list = new  List<P3>();
    for (int i = 0; i < 4; i++)
      list.addLast(pts[i]);
    return addXList(list);
  }

  private boolean getPointOrBitsetOperation(T op, SV x2)
      throws ScriptException {
    switch (x2.tok) {
    case T.varray:
      switch (op.intValue) {
      case T.min:
      case T.max:
      case T.average:
      case T.stddev:
      case T.sum:
      case T.sum2:
        return addXObj(eval.getExtension().getMinMax(x2.getList(), op.intValue));
      case T.sort:
      case T.reverse:
        return addXVar(x2
            .sortOrReverse(op.intValue == T.reverse ? Integer.MIN_VALUE : 1));
      }
      SV[] list2 = new SV[x2.getList().size()];
      for (int i = 0; i < list2.length; i++) {
        Object v = SV.unescapePointOrBitsetAsVariable(x2.getList()
            .get(i));
        if (!(v instanceof SV)
            || !getPointOrBitsetOperation(op, (SV) v))
          return false;
        list2[i] = xStack[xPt--];
      }
      return addXAV(list2);
    case T.point3f:
      switch (op.intValue) {
      case T.atomx:
      case T.x:
        return addXFloat(((P3) x2.value).x);
      case T.atomy:
      case T.y:
        return addXFloat(((P3) x2.value).y);
      case T.atomz:
      case T.z:
        return addXFloat(((P3) x2.value).z);
      case T.xyz:
        P3 pt = P3.newP((P3) x2.value);
        // assumes a fractional coordinate
        viewer.toCartesian(pt, true);
        return addXPt(pt);
      case T.fracx:
      case T.fracy:
      case T.fracz:
      case T.fracxyz:
        P3 ptf = P3.newP((P3) x2.value);
        viewer.toFractional(ptf, true);
        return (op.intValue == T.fracxyz ? addXPt(ptf)
            : addXFloat(op.intValue == T.fracx ? ptf.x
                : op.intValue == T.fracy ? ptf.y : ptf.z));
      case T.fux:
      case T.fuy:
      case T.fuz:
      case T.fuxyz:
        P3 ptfu = P3.newP((P3) x2.value);
        viewer.toFractional(ptfu, false);
        return (op.intValue == T.fracxyz ? addXPt(ptfu)
            : addXFloat(op.intValue == T.fux ? ptfu.x
                : op.intValue == T.fuy ? ptfu.y : ptfu.z));
      case T.unitx:
      case T.unity:
      case T.unitz:
      case T.unitxyz:
        P3 ptu = P3.newP((P3) x2.value);
        viewer.toUnitCell(ptu, null);
        viewer.toFractional(ptu, false);
        return (op.intValue == T.unitxyz ? addXPt(ptu)
            : addXFloat(op.intValue == T.unitx ? ptu.x
                : op.intValue == T.unity ? ptu.y : ptu.z));
      }
      break;
    case T.point4f:
      switch (op.intValue) {
      case T.atomx:
      case T.x:
        return addXFloat(((P4) x2.value).x);
      case T.atomy:
      case T.y:
        return addXFloat(((P4) x2.value).y);
      case T.atomz:
      case T.z:
        return addXFloat(((P4) x2.value).z);
      case T.w:
        return addXFloat(((P4) x2.value).w);
      }
      break;
    case T.bitset:
      if (op.intValue == T.bonds && x2.value instanceof BondSet)
        return addXVar(x2);
      BS bs = SV.bsSelectVar(x2);
      if (bs.cardinality() == 1 && (op.intValue & T.minmaxmask) == 0)
        op.intValue |= T.min;
      Object val = eval.getBitsetProperty(bs, op.intValue, null, null,
          x2.value, op.value, false, x2.index, true);
      if (op.intValue != T.bonds)
        return addXObj(val);
      return addXVar(SV.newV(T.bitset, new BondSet(
          (BS) val, viewer.getAtomIndices(bs))));
    }
    return false;
  }

  public SV evalOp(T token) throws ScriptException {
    if (!addOp(token) || !operate())
      return null;
    return xStack[xPt--];
  }
  
}
