/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2014-01-05 14:47:07 -0600 (Sun, 05 Jan 2014) $
 * $Revision: 19149 $
 *
 * Copyright (C) 2003-2005  The Jmol Development Team
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

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import javajs.util.AU;
import javajs.util.List;
import org.jmol.util.Logger;

/**
 * 
 * Script token class.
 * 
 */
public class T {
  public int tok;
  public Object value;
  public int intValue = Integer.MAX_VALUE;

  public final static T t(int tok) {
    T token = new T();
    token.tok = tok;
    return token;
  }
 
  public final static T tv(int tok, int intValue, Object value) {
    T token = T.t(tok);
    token.intValue = intValue;
    token.value = value;
    return token;
  }
 
  public final static T o(int tok, Object value) {
    T token = T.t(tok);
    token.value = value;
    return token;
  }

  public final static T n(int tok, int intValue) {
    T token = T.t(tok);
    token.intValue = intValue;
    return token;
  }

  public final static T i(int intValue) {
    T token = T.t(integer);
    token.intValue = intValue;
    return token;
  }

  public final static int nada       =  0;
  public final static int integer    =  2;
  public final static int decimal    =  3;
  public final static int string     =  4;
  
  public final static int seqcode    =  5;
  public final static int hash       =  6;  // associative array; Hashtable
  public final static int varray     =  7;  // List<ScriptVariable>
  public final static int point3f    =  8;
  public final static int point4f    =  9;  
  public final static int bitset     =  10;
  
  public final static int matrix3f   = 11;  
  public final static int matrix4f   = 12;  
  // listf "list-float" is specifically for xxx.all.bin, 
  // but it could be developed further
  public final static int listf             = 13;     
  final private static int keyword   = 14;
  

  public final static String[] astrType = {
    "nada", "identifier", "integer", "decimal", "string",
    "seqcode", "hash", "array", "point", "point4", "bitset",
    "matrix3f",  "matrix4f", "listf", "keyword"
  };

  public static boolean tokAttr(int a, int b) {
    return (a & b) == (b & b);
  }
  
  public static boolean tokAttrOr(int a, int b1, int b2) {
    return (a & b1) == (b1 & b1) || (a & b2) == (b2 & b2);
  }
  
 

  // TOKEN BIT FIELDS
  
  // first 9 bits are generally identifier bits
  // or bits specific to a type
  
  /* bit flags:
   * 
   * parameter bit flags:
   * 
   * 3         2         1         0
   * 0987654321098765432109876543210
   *    |   |   |   |   |   |   |     
   *  x                  xxxxxxxxxxx setparam  "set THIS ...."
   *  x     x                        strparam
   *  x    x                         intparam
   *  x   x                          floatparam
   *  x  x                           booleanparam
   * xx                              deprecatedparam
   * x                   xxxxxxxxxxx misc
   * 
   * 3         2         1         0
   * 0987654321098765432109876543210
   *                   x             sciptCommand
   *                  xx             atomExpressionCommand
   *                 x x           o implicitStringCommand (parsing of @{x})
   *                 x x           x implicitStringCommand (no initial parsing of @{x})
   *                x  x             mathExpressionCommand
   *               xx  x             flowCommand
   *              x    x             shapeCommand
   *             x                   noArgs
   *            x                    defaultON
   *                     xxxxxxxxxxx uniqueID (may include math flags)
   * 
   *              
   * math bit flags:
   * 
   * 3         2         1         0
   * 0987654321098765432109876543210
   *    FFFF    FFFF    FFFF    FFFF
   *           x                     expression
   *          xx                     predefined set
   * x       x x                     atomproperty
   * x      xx x                     strproperty
   * x     x x x                     intproperty
   * x    x  x x                     floatproperty
   * x   x     x                     mathproperty
   *    x      x                     mathfunc
   *        
   *        
   *                           xxxxx unique id 1 to 0x1F (31)
   *                          x      min
   *                         x       max
   *                         xx      average
   *                        x        sum
   *                        x x      sum2
   *                        xx       stddev
   *                        xxx      selectedfloat  (including just the atoms selected)
   *                       x         fullfloat (including all atoms, not just selected)
   *                       x???      [available] 
   *                       xxxx      minmaxmask (all)
   *                     xx          maximum number of parameters for function
   *                    x            settable
   *                   
   * 3         2         1         0
   * 0987654321098765432109876543210
   *   x       x                     mathop
   *   x       x           x         comparator
   *                            xxxx unique id (0 to 15)
   *                        xxxx     precedence
   *
   *                        
   * 
   */
   
  //
  // parameter bit flags
  //
  
  public final static int setparam          = (1 << 29); // parameter to set command
  public final static int misc              = (1 << 30); // misc parameter
  public final static int deprecatedparam   = setparam | misc;
  
  public final static int identifier =  misc;

  public final static int scriptCommand            = (1 << 12);
  
  // the command assumes an atom expression as the first parameter
  // -- center, define, delete, display, hide, restrict, select, subset, zap
  public final static int atomExpressionCommand  = (1 << 13) | scriptCommand;
  
  // this implicitString flag indicates that then entire command is an implied quoted string  
  // -- ODD echo, hover, label, message, pause  -- do NOT parse variables the same way
  // -- EVEN help, javascript, cd, gotocmd -- allow for single starting variable
  public final static int implicitStringCommand     = (1 << 14) | scriptCommand;
  
  // this implicitExpression flag indicates that phrases surrounded 
  // by ( ) should be considered the same as { }. 
  // -- elseif, forcmd, ifcmd, print, returncmd, set, var, whilecmd
  public final static int mathExpressionCommand = (1 << 15) | scriptCommand;
  
  // program flow commands include:
  // -- breakcmd, continuecmd, elsecmd, elseif, end, endifcmd, switch, case, 
  //    forcmd, function, ifcmd, whilecmd
  public final static int flowCommand        = (1 << 16) | mathExpressionCommand;

  // these commands will be handled specially
  public final static int shapeCommand   = (1 << 17) | scriptCommand;

  // Command argument compile flags
  
  public final static int noArgs         = (1 << 18);
  public final static int defaultON      = (1 << 19);
  
  public final static int expression           = (1 << 20);
  public final static int predefinedset = (1 << 21) | expression;
  
  public final static int atomproperty  = (1 << 22) | expression | misc; 
  // all atom properties are either a member of one of the next three groups,
  // or they are a point/vector, in which case they are just atomproperty
  public final static int strproperty   = (1 << 23) | atomproperty; // string property
  public final static int intproperty   = (1 << 24) | atomproperty; // int parameter
  public final static int floatproperty = (1 << 25) | atomproperty; // float parameter

  public final static int PROPERTYFLAGS = strproperty | intproperty | floatproperty;

  // parameters that can be set using the SET command
  public final static int strparam   = (1 << 23) | setparam; // string parameter
  public final static int intparam   = (1 << 24) | setparam; // int parameter
  public final static int floatparam = (1 << 25) | setparam; // float parameter
  public final static int booleanparam = (1 << 26) | setparam; // boolean parameter
  public final static int paramTypes = (strparam | intparam | floatparam | booleanparam);
  
  // note: the booleanparam and the mathproperty bits are the same, but there is no
  //       conflict because mathproperty is only checked in ScriptEvaluator.getBitsetProperty
  //       meaning it is coming after a "." as in {*}.min
  
  public final static int mathproperty         = (1 << 26) | expression | misc; // {xxx}.nnnn
  public final static int mathfunc             = (1 << 27) | expression;  
  public final static int mathop               = (1 << 28) | expression;
  public final static int comparator           = mathop | (1 << 8);
  
  public final static int center       = 1 | atomExpressionCommand;
  public final static int define       = 2 | atomExpressionCommand | expression;
  public final static int delete       = 3 | atomExpressionCommand;
  public final static int display      = 4 | atomExpressionCommand | deprecatedparam;
  public final static int fixed        = 5 | atomExpressionCommand | expression; // Jmol 12.0.RC15
  public final static int hide         = 6 | atomExpressionCommand;
  public final static int restrict     = 7 | atomExpressionCommand;
//public final static int select       see mathfunc
  public final static int subset       = 8 | atomExpressionCommand | predefinedset;
  public final static int zap          = 9 | atomExpressionCommand | expression;

  public final static int print        = 1 | mathExpressionCommand;
  public final static int returncmd    = 2 | mathExpressionCommand;
  public final static int set          = 3 | mathExpressionCommand | expression;
  public final static int var          = 4 | mathExpressionCommand;
  public final static int log          = 5 | mathExpressionCommand;
  //public final static int prompt     see mathfunc
  
  public final static int echo  = 1 /* must be odd */ | implicitStringCommand | shapeCommand | setparam;
  public final static int help         = 2 /* must be even */ | implicitStringCommand;
  public final static int hover = 3 /* must be odd */ | implicitStringCommand | defaultON;
//public final static int javascript   see mathfunc
//public final static int label        see mathfunc
  public final static int message      = 5 /* must be odd */ | implicitStringCommand;
  public final static int pause = 7 /* must be odd */ | implicitStringCommand;

  //these commands control flow
  //sorry about GOTO!
//public final static int function     see mathfunc
//public final static int ifcmd        see mathfunc
  public final static int elseif       = 2 | flowCommand;
  public final static int elsecmd      = 3 | flowCommand | noArgs;
  public final static int endifcmd     = 4 | flowCommand | noArgs;
//public final static int forcmd       see mathfunc
  public final static int whilecmd     = 6 | flowCommand;
  public final static int breakcmd     = 7 | flowCommand;
  public final static int continuecmd  = 8 | flowCommand;
  public final static int end          = 9 | flowCommand | expression;
  public final static int switchcmd    = 10 | flowCommand;
  public final static int casecmd      = 11 | flowCommand;
  public final static int catchcmd     = 12 | flowCommand;
  public final static int defaultcmd   = 13 | flowCommand;
  public final static int trycmd       = 14 | flowCommand | noArgs;
  
  public final static int animation    = scriptCommand | 1;
  public final static int assign       = scriptCommand | 2;
  public final static int background   = scriptCommand | 3 | deprecatedparam;
  public final static int bind         = scriptCommand | 4;
  public final static int bondorder    = scriptCommand | 5;
  public final static int calculate    = scriptCommand | 6;
//public final static int cache        see mathfunc
  public final static int capture      = scriptCommand | 7;
  public final static int cd           = scriptCommand | 8 /* must be even */| implicitStringCommand | expression; // must be even
  public final static int centerAt     = scriptCommand | 9;
//public final static int color        see intproperty
//public final static int configuration see intproperty
  public final static int connect = scriptCommand | 10;
  public final static int console      = scriptCommand | 11 | defaultON;
//public final static int data         see mathfunc
  public final static int delay        = scriptCommand | 13 | defaultON;
  public final static int depth = scriptCommand | 14 | intparam | defaultON;
  public final static int exit         = scriptCommand | 15 | noArgs;
  public final static int exitjmol     = scriptCommand | 16 | noArgs;
//public final static int file         see intproperty
  public final static int font         = scriptCommand | 18;
  public final static int frame        = scriptCommand | 19;
//public final static int getproperty  see mathfunc
  public final static int gotocmd      = scriptCommand | 20 /*must be even*/| implicitStringCommand;
  public final static int hbond = scriptCommand | 22 | deprecatedparam | expression | defaultON;
  public final static int history      = scriptCommand | 23 | deprecatedparam;
  public final static int initialize   = scriptCommand | 24 | noArgs;
  public final static int invertSelected = scriptCommand | 25;
//public final static int load         see mathfunc
  public final static int loop         = scriptCommand | 26 | defaultON;
  public final static int mapProperty  = scriptCommand | 28 | expression;
  public final static int minimize     = scriptCommand | 30;
//public final static int model        see mathfunc
//public final static int measure      see mathfunc
  public final static int move         = scriptCommand | 32;
  public final static int moveto = scriptCommand | 34;
  public final static int navigate = scriptCommand | 35;
//public final static int quaternion   see mathfunc
  public final static int parallel     = flowCommand   | 36;
  public final static int plot         = scriptCommand | 37;
  public final static int process      = flowCommand   | 39;
//  public final static int prompt     see mathfunc
//  public final static int push       see mathfunc  = scriptCommand | 40 | noArgs; //internal only
  public final static int quit         = scriptCommand | 41 | noArgs;
  public final static int ramachandran = scriptCommand | 42 | expression;
  public final static int redomove = scriptCommand | 43;
  public final static int refresh      = scriptCommand | 44 | noArgs;
  public final static int reset        = scriptCommand | 45;
  public final static int restore      = scriptCommand | 46;
  public final static int resume = scriptCommand | 47 | noArgs;
  public final static int rotate       = scriptCommand | 48 | defaultON;
  public final static int rotateSelected = scriptCommand | 49;
  public final static int save  = scriptCommand | 50;
//public final static int script   see mathfunc
  public final static int selectionhalos = scriptCommand | 51 | deprecatedparam | defaultON;
  public final static int show         = scriptCommand | 52;
  public final static int slab  = scriptCommand | 53 | intparam | defaultON;
  public final static int spin         = scriptCommand | 55 | deprecatedparam | defaultON;
  public final static int ssbond = scriptCommand | 56 | deprecatedparam | defaultON;
  public final static int step         = scriptCommand | 58 | noArgs;
  public final static int stereo       = scriptCommand | 59 | defaultON;
//public final static int structure    see intproperty
  public final static int sync         = scriptCommand | 60;
  public final static int timeout      = scriptCommand | 62 | setparam;
  public final static int translate    = scriptCommand | 64;
  public final static int translateSelected   = scriptCommand | 66;
  public final static int unbind              = scriptCommand | 68;
  public final static int undomove     = scriptCommand | 69;
  public final static int vibration    = scriptCommand | 70;
  //public final static int write   see mathfunc
  public final static int zoom                = scriptCommand | 72;
  public final static int zoomTo              = scriptCommand | 74;

  // shapes:
  
  public final static int axes         = shapeCommand | 2 | deprecatedparam | defaultON;
//public final static int boundbox     see mathproperty
//public final static int contact      see mathfunc
  public final static int cgo          = shapeCommand | 6; // PyMOL Compiled Graphical Object
  public final static int dipole       = shapeCommand | 7;
  public final static int draw         = shapeCommand | 8;
  public final static int frank        = shapeCommand | 10 | deprecatedparam | defaultON;
  public final static int isosurface   = shapeCommand | 12;
  public final static int lcaocartoon  = shapeCommand | 14;
  public final static int measurements = shapeCommand | 16 | setparam;
  public final static int mo           = shapeCommand | 18 | expression;
  public final static int pmesh        = shapeCommand | 20;
  public final static int plot3d       = shapeCommand | 22;
  public final static int polyhedra    = shapeCommand | 24;
  //public final static int spacefill see floatproperty
  public final static int struts       = shapeCommand | 26 | defaultON | expression;
  public final static int unitcell     = shapeCommand | 28 | deprecatedparam | expression | predefinedset | defaultON;
  public final static int vector       = shapeCommand | 30;
  public final static int wireframe    = shapeCommand | 32 | defaultON;


  

  //
  // atom expression terms
  //
  
  public final static int expressionBegin     = expression | 1;
  public final static int expressionEnd       = expression | 2;
  public final static int all          = expression | 3;
  public final static int branch       = expression | 4;
  public final static int coord               = expression | 6;
  public final static int dollarsign          = expression | 7;
  public final static int per                 = expression | 8;
  public final static int isaromatic   = expression | 9;
  public final static int leftbrace           = expression | 10;
  public final static int none                = expression | 11;
  public final static int off          = expression | 12; //for within(dist,false,...)
  public final static int on           = expression | 13; //for within(dist,true,...)
  public final static int rightbrace          = expression | 14;
  public final static int semicolon           = expression | 15;

  // generated by compiler:
  
  public final static int spec_alternate       = expression | 31;
  public final static int spec_atom            = expression | 32;
  public final static int spec_chain           = expression | 33;
  public final static int spec_model           = expression | 34;  // /3, /4
  public final static int spec_model2                 = expression | 35;  // 1.2, 1.3
  public final static int spec_name_pattern    = expression | 36;
  public final static int spec_resid           = expression | 37;
  public final static int spec_seqcode         = expression | 38;
  public final static int spec_seqcode_range   = expression | 39;

  public final static int amino                = predefinedset | 2;
  public final static int dna           = predefinedset | 4;
  public final static int hetero        = predefinedset | 6 | deprecatedparam;
  public final static int helixalpha           = predefinedset | 7;   // Jmol 12.1.14
  public final static int helix310             = predefinedset | 8;   // Jmol 12.1.14
  public final static int helixpi              = predefinedset | 10; 
  public final static int hydrogen      = predefinedset | 12 | deprecatedparam;
  public final static int nucleic       = predefinedset | 14;
  public final static int protein       = predefinedset | 16;
  public final static int purine        = predefinedset | 18;
  public final static int pyrimidine    = predefinedset | 20;
  public final static int rna           = predefinedset | 22;
  public final static int solvent       = predefinedset | 24 | deprecatedparam;
  public final static int sidechain     = predefinedset | 26;
  public final static int surface              = predefinedset | 28;
  public final static int thismodel            = predefinedset | 30;
  public final static int sheet         = predefinedset | 32;
  public final static int spine         = predefinedset | 34;  // 11.9.34
  // these next are predefined in the sense that they are known quantities
  public final static int carbohydrate    = predefinedset | 36;
  public final static int clickable              = predefinedset | 38;
  public final static int displayed              = predefinedset | 40;
  public final static int hidden                 = predefinedset | 42;
  public final static int specialposition = predefinedset | 44;
  public final static int visible                = predefinedset | 46;
  public final static int basemodel              = predefinedset | 48;
  public final static int nonequivalent          = predefinedset | 50;

  
  static int getPrecedence(int tokOperator) {
    return ((tokOperator >> 4) & 0xF);  
  }


  public final static int leftparen    = 0 | mathop | 1 << 4;
  public final static int rightparen   = 1 | mathop | 1 << 4;

  public final static int opIf         = 1 | mathop | 2 << 4 | setparam;   // set ?
  public final static int colon        = 2 | mathop | 2 << 4;

  public final static int comma        = 0 | mathop | 3 << 4;

  public final static int leftsquare   = 0 | mathop | 4 << 4;
  public final static int rightsquare  = 1 | mathop | 4 << 4;

  public final static int opOr         = 0 | mathop | 5 << 4;
  public final static int opXor        = 1 | mathop | 5 << 4;
  public final static int opToggle = 2 | mathop | 5 << 4;

  public final static int opAnd        = 0 | mathop | 6 << 4;
 
  public final static int opNot        = 0 | mathop | 7 << 4;

  public final static int opAND        = 0 | mathop | 8 << 4;

  public final static int opGT         = 0 | comparator | 9 << 4;
  public final static int opGE         = 1 | comparator | 9 << 4;
  public final static int opLE         = 2 | comparator | 9 << 4;
  public final static int opLT         = 3 | comparator | 9 << 4;
  public final static int opEQ  = 4 | comparator | 9 << 4;
  public final static int opNE         = 6 | comparator | 9 << 4;
   
  public final static int minus        = 0 | mathop | 10 << 4;
  public final static int plus         = 1 | mathop | 10 << 4;
 
  public final static int divide         = 0 | mathop | 11 << 4;
  public final static int times          = 1 | mathop | 11 << 4;
  public final static int percent = 2 | mathop | 11 << 4;
  public final static int leftdivide     = 3 | mathop | 11 << 4;  //   quaternion1 \ quaternion2
  
  public final static int unaryMinus   = 0 | mathop | 12 << 4;
  public final static int minusMinus   = 1 | mathop | 12 << 4;
  public final static int plusPlus     = 2 | mathop | 12 << 4;
  public final static int timestimes   = 3 | mathop | 12 << 4;
  
  
  public final static int propselector = 1 | mathop | 13 << 4;

  public final static int andequals    = 2 | mathop | 13 << 4;

  // these atom and math properties are invoked after a ".":
  // x.atoms
  // myset.bonds
  
  // .min and .max, .average, .sum, .sum2, .stddev, and .all 
  // are bitfields added to a preceding property selector
  // for example, x.atoms.max, x.atoms.all
  // .all gets incorporated as minmaxmask
  // .selectedfloat is a special flag used by mapPropety() and plot()
  // to pass temporary float arrays to the .bin() function
  // .allfloat is a special flag for colorShape() to get a full
  // atom float array
  
  public final static int minmaxmask /*all*/ = 0xF << 5; 
  public final static int min           = 1 << 5;
  public final static int max           = 2 << 5;
  public final static int average       = 3 << 5;
  public final static int sum           = 4 << 5;
  public final static int sum2          = 5 << 5;
  public final static int stddev        = 6 << 5;
  public final static int selectedfloat = 7 << 5; //not user-selectable
  public final static int allfloat      = 8 << 5; //not user-selectable

  public final static int settable           = 1 << 11;
  
  // bits 0 - 4 are for an identifier -- DO NOT GO OVER 31!
  // but, note that we can have more than 1 provided other parameters differ
  
  // ___.xxx math properties and all atom properties 
    
  public final static int atoms     = 1 | mathproperty;
  public final static int bonds     = 2 | mathproperty | deprecatedparam;
  public final static int length           = 3 | mathproperty;
  public final static int lines            = 4 | mathproperty;
  public final static int reverse   = 5 | mathproperty;
  public final static int size             = 6 | mathproperty;
  public final static int type      = 8 | mathproperty;
  public final static int boundbox  = 9 | mathproperty | deprecatedparam | shapeCommand | defaultON;
  public final static int xyz       =10 | mathproperty | atomproperty | settable;
  public final static int fracxyz   =11 | mathproperty | atomproperty | settable;
  public final static int screenxyz =12 | mathproperty | atomproperty | settable;
  public final static int fuxyz     =13 | mathproperty | atomproperty | settable;
  public final static int unitxyz   =14 | mathproperty | atomproperty;
  public final static int vibxyz    =15 | mathproperty | atomproperty | settable;
  public final static int w                =16 | mathproperty;
  public final static int keys             =17 | mathproperty; 
  
  // occupancy, radius, and structure are odd, because they takes different meanings when compared
  
  public final static int occupancy     = intproperty | floatproperty | 1 | settable;
  public final static int radius        = intproperty | floatproperty | 2 | deprecatedparam | settable;
  public final static int structure     = intproperty | strproperty   | 3 | setparam | scriptCommand;

  // any new int, float, or string property should be added also to LabelToken.labelTokenIds
  // and the appropriate Atom.atomPropertyXXXX() method
  
  public final static int atomtype      = strproperty | 1 | settable;
  public final static int atomname      = strproperty | 2 | settable;
  public final static int altloc        = strproperty | 3;
  public final static int chain         = strproperty | 4;
  public final static int element       = strproperty | 5 | settable;
  public final static int group         = strproperty | 6;
  public final static int group1        = strproperty | 7;
  public final static int sequence      = strproperty | 8;
  public final static int identify      = strproperty | 9;
  public final static int insertion     = strproperty |10;
  public final static int shape         = strproperty |11;
  public final static int strucid       = strproperty |12;
  public final static int symbol        = strproperty |13 | settable;
  public final static int symmetry      = strproperty |14 | predefinedset;

  public final static int atomno        = intproperty | 1 | settable;
  public final static int atomid        = intproperty | 2;
  public final static int atomindex     = intproperty | 3;
  public final static int bondcount     = intproperty | 4;
  public final static int cell          = intproperty | 5;
  public final static int centroid      = intproperty | 6;
  public final static int chainno       = intproperty | 7;
  public final static int configuration = intproperty | 8 | scriptCommand;
  //color: see xxx(a, b, c, d)
  public final static int elemisono     = intproperty | 9;
  public final static int elemno        = intproperty | 10 | settable;
  //file: see xxx(a)
  public final static int formalcharge  = intproperty | 11 | setparam | settable;
  public final static int groupid       = intproperty | 12;
  public final static int groupindex    = intproperty | 13;
  public final static int model         = intproperty | 14 | scriptCommand;
  public final static int modelindex    = intproperty | 15;
  public final static int molecule      = intproperty | 16;
  public final static int polymer       = intproperty | 17;
  public final static int polymerlength = intproperty | 18;
  public final static int resno         = intproperty | 19;
  public final static int site          = intproperty | 20;
  public final static int strucno       = intproperty | 21;
  public final static int valence       = intproperty | 22 | settable;

  // float values must be multiplied by 100 prior to comparing to integer values

  // max 31 here
  
  public final static int adpmax          = floatproperty | 1;
  public final static int adpmin          = floatproperty | 2;
  public final static int chemicalshift   = floatproperty | 3; // Jmol 13.1.19
  public final static int covalent        = floatproperty | 4;
  public final static int eta             = floatproperty | 5; // Jmol 12.0.RC23
  public final static int magneticshielding = floatproperty | 6;  // Jmol 13.1.19
  public final static int mass            = floatproperty | 7;
  public final static int omega           = floatproperty | 8;
  public final static int phi             = floatproperty | 9;
  public final static int psi             = floatproperty | 10;
  public final static int screenx         = floatproperty | 11;
  public final static int screeny         = floatproperty | 12;
  public final static int screenz         = floatproperty | 13;
  public final static int straightness    = floatproperty | 14;
  public final static int surfacedistance = floatproperty | 15;
  public final static int theta           = floatproperty | 16; // Jmol 12.0.RC23
  public final static int unitx           = floatproperty | 17;
  public final static int unity           = floatproperty | 18;
  public final static int unitz           = floatproperty | 19;
  public final static int vectorscale     = floatproperty | 1 | floatparam;
  public final static int atomx           = floatproperty | 1 | settable;
  public final static int atomy           = floatproperty | 2 | settable;
  public final static int atomz           = floatproperty | 3 | settable;
  public final static int fracx           = floatproperty | 4 | settable;
  public final static int fracy           = floatproperty | 5 | settable;
  public final static int fracz           = floatproperty | 6 | settable;
  public final static int fux             = floatproperty | 7 | settable;
  public final static int fuy             = floatproperty | 8 | settable;
  public final static int fuz             = floatproperty | 9 | settable;
  public final static int ionic           = floatproperty | 11 | settable;
  public final static int partialcharge   = floatproperty | 12 | settable;
  public final static int temperature     = floatproperty | 15 | settable;
  public final static int vibx            = floatproperty | 18 | settable;
  public final static int viby            = floatproperty | 19 | settable;
  public final static int vibz            = floatproperty | 20 | settable;
  public final static int x               = floatproperty | 21 | settable;
  public final static int y               = floatproperty | 22 | settable;
  public final static int z               = floatproperty | 23 | settable;
  public final static int vanderwaals     = floatproperty | 24 | settable | setparam;
  public final static int property        = floatproperty | 25 | settable | setparam | mathproperty;
  public final static int hydrophobic     = floatproperty | 26 | settable | predefinedset;
  public final static int selected        = floatproperty | 27 | settable | predefinedset;
  
  public final static int backbone     = floatproperty | shapeCommand | 1 | predefinedset | defaultON | settable;
  public final static int cartoon      = floatproperty | shapeCommand | 2 | defaultON | settable;
  public final static int dots         = floatproperty | shapeCommand | 3 | defaultON;
  public final static int ellipsoid    = floatproperty | shapeCommand | 4 | defaultON;
  public final static int geosurface   = floatproperty | shapeCommand | 5 | defaultON;
  public final static int halo         = floatproperty | shapeCommand | 6 | defaultON | settable;
  public final static int meshRibbon   = floatproperty | shapeCommand | 7 | defaultON | settable;
  public final static int ribbon       = floatproperty | shapeCommand | 9 | defaultON | settable;
  public final static int rocket       = floatproperty | shapeCommand | 10 | defaultON | settable;
  public final static int spacefill    = floatproperty | shapeCommand | 11 | defaultON | settable;
  public final static int star         = floatproperty | shapeCommand | 12 | defaultON | settable;
  public final static int strands      = floatproperty | shapeCommand | 13 | deprecatedparam | defaultON | settable;
  public final static int trace        = floatproperty | shapeCommand | 14 | defaultON | settable;

  // mathfunc               means x = somefunc(a,b,c)
  // mathfunc|mathproperty  means x = y.somefunc(a,b,c)
  // 
  // maximum number of parameters is set by the << 9 shift
  // the min/max mask requires that the first number here must not exceed 63
  // the only other requirement is that these numbers be unique


  static int getMaxMathParams(int tokCommand) {
    return  ((tokCommand >> 9) & 0x7);
  }

  // 0 << 9 indicates that ScriptMathProcessor 
  // will check length in second stage of compilation

  // xxx(a,b,c,d,e,...)
  
  public final static int angle            = 1 | 0 << 9 | mathfunc;
  public final static int array            = 2 | 0 << 9 | mathfunc;
  public final static int axisangle        = 3 | 0 << 9 | mathfunc;
  public final static int color            = 4 | 0 << 9 | mathfunc | intproperty | scriptCommand | deprecatedparam | settable;
  public final static int compare          = 5 | 0 << 9 | mathfunc | scriptCommand;
  public final static int connected        = 6 | 0 << 9 | mathfunc;
  public final static int data             = 7 | 0 << 9 | mathfunc | scriptCommand;
  public final static int format           = 8 | 0 << 9 | mathfunc | mathproperty | strproperty | settable;
  public final static int function         = 9 | 0 << 9 | mathfunc | flowCommand;
  public final static int getproperty      = 10 | 0 << 9 | mathfunc | mathproperty | scriptCommand;
  public final static int label            = 11 /* must be odd */| 0 << 9 | mathfunc | mathproperty | strproperty | settable | implicitStringCommand | shapeCommand | defaultON | deprecatedparam; 
  public final static int helix            = 12 | 0 << 9 | mathfunc | predefinedset;
  public final static int measure          = 13 | 0 << 9| mathfunc | shapeCommand | deprecatedparam | defaultON;
  public final static int now              = 14 | 0 << 9 | mathfunc;
  public final static int plane            = 15 | 0 << 9 | mathfunc;
  public final static int point            = 16 | 0 << 9 | mathfunc;
  public final static int pop              = 17 | 0 << 9 | mathfunc | mathproperty | scriptCommand | noArgs; //internal only;
  public final static int quaternion       = 18 | 0 << 9 | mathfunc | scriptCommand;
  public final static int sort             = 19 | 0 << 9 | mathfunc | mathproperty;
  public final static int count            = 20 | 0 << 9 | mathfunc | mathproperty;
  public final static int within           = 21 | 0 << 9 | mathfunc;
  public final static int write            = 22 | 0 << 9 | mathfunc | scriptCommand;
  public final static int cache            = 23 | 0 << 9 | mathfunc | scriptCommand; // new in Jmol 13.1.2
  public final static int tensor           = 24 | 0 << 9 | mathfunc | mathproperty;
  public final static int modulation       = 25 | 0 << 9 | mathfunc | mathproperty | scriptCommand;
  
  // xxx(a)
  
  public final static int acos         = 3 | 1 << 9 | mathfunc;
  public final static int sin          = 4 | 1 << 9 | mathfunc;
  public final static int cos          = 5 | 1 << 9 | mathfunc;
  public final static int sqrt         = 6 | 1 << 9 | mathfunc;
  public final static int file  = 7 | 1 << 9 | mathfunc | intproperty | scriptCommand;
  public final static int forcmd       = 8 | 1 << 9 | mathfunc | flowCommand;
  public final static int ifcmd        = 9 | 1 << 9 | mathfunc | flowCommand;
  public final static int abs          = 10 | 1 << 9 | mathfunc;
  public final static int javascript   = 12 /* must be even */| 1 << 9 | mathfunc | implicitStringCommand;

  // ___.xxx(a)
  
  // a.distance(b) is in a different set -- distance(b,c) -- because it CAN take
  // two parameters and it CAN be a dot-function (but not both together)
  
  public final static int div          = 0 | 1 << 9 | mathfunc | mathproperty;
  public final static int dot          = 1 | 1 << 9 | mathfunc | mathproperty;
  public final static int join         = 2 | 1 << 9 | mathfunc | mathproperty;
  public final static int mul          = 3 | 1 << 9 | mathfunc | mathproperty;
  public final static int mul3         = 4 | 1 << 9 | mathfunc | mathproperty;
  public final static int push         = 5 | 1 << 9 | mathfunc | mathproperty | scriptCommand | noArgs; //internal only;
  public final static int split        = 6 | 1 << 9 | mathfunc | mathproperty;
  public final static int sub          = 7 | 1 << 9 | mathfunc | mathproperty;
  public final static int trim         = 8 | 1 << 9 | mathfunc | mathproperty;  
  public final static int volume       = 9 | 1 << 9 | mathfunc | mathproperty | floatproperty;  
  public final static int col         = 10 | 1 << 9 | mathfunc | mathproperty;
  public final static int row         = 11 | 1 << 9 | mathfunc | mathproperty;

  // xxx(a,b)
  
  public final static int cross = 1 | 2 << 9 | mathfunc;
  public final static int load         = 2 | 2 << 9 | mathfunc | scriptCommand;
  public final static int random       = 4 | 2 << 9 | mathfunc;
  public final static int script       = 5 | 2 << 9 | mathfunc | scriptCommand;
  public final static int substructure = 6 | 2 << 9 | mathfunc | intproperty | strproperty;
  public final static int search       = 7 | 2 << 9 | mathfunc;
  public final static int smiles       = 8 | 2 << 9 | mathfunc;
  public final static int contact = 9 | 2 << 9 | mathfunc | shapeCommand;


  // ___.xxx(a,b)

  // note that distance is here because it can take two forms:
  //     a.distance(b)
  // and
  //     distance(a,b)
  //so it can be a math property and it can have up to two parameters
  
  public final static int add          = 1 | 2 << 9 | mathfunc | mathproperty;
  public final static int distance     = 2 | 2 << 9 | mathfunc | mathproperty;
  public final static int replace      = 3 | 2 << 9 | mathfunc | mathproperty;

  // xxx(a,b,c)
  
  public final static int hkl          = 1 | 3 << 9 | mathfunc;
  public final static int intersection = 2 | 3 << 9 | mathfunc;
  public final static int prompt       = 3 | 3 << 9 | mathfunc | mathExpressionCommand;
  public final static int select       = 4 | 3 << 9 | mathfunc | atomExpressionCommand;

  // ___.xxx(a,b,c)
  
  public final static int bin          = 1 | 3 << 9 | mathfunc | mathproperty;
  public final static int symop = 2 | 3 << 9 | mathfunc | mathproperty | intproperty; 
  public final static int find         = 3 | 3 << 9 | mathfunc | mathproperty;

  // anything beyond 3 are set "unlimited"

  // set parameters 
  
  // deprecated or handled specially in ScriptEvaluator
  
  public final static int bondmode           = deprecatedparam | 1;  
  public final static int fontsize           = deprecatedparam | 2;
  public final static int measurementnumbers = deprecatedparam | 3;
  public final static int scale3d            = deprecatedparam | 4;
  public final static int togglelabel        = deprecatedparam | 5;

  // handled specially in ScriptEvaluator

  public final static int backgroundmodel  = setparam | 2;
  public final static int debug            = setparam | 4;
  public final static int defaultlattice   = setparam | 6;
  public final static int highlight        = setparam | 8;// 12.0.RC14
  public final static int showscript       = setparam | 10;
  public final static int specular         = setparam | 12;
  public final static int trajectory       = setparam | 14;
  public final static int undo             = setparam | 16;
  public final static int usercolorscheme  = setparam | 18;

  // full set of all Jmol "set" parameters

  public final static int animationmode                  = strparam | 1;
  public final static int appletproxy                    = strparam | 2;
  public final static int atomtypes                      = strparam | 4;
  public final static int axescolor                      = strparam | 6;
  public final static int axis1color                     = strparam | 8;
  public final static int axis2color                     = strparam | 10;
  public final static int axis3color                     = strparam | 12;
  public final static int backgroundcolor                = strparam | 14;
  public final static int boundboxcolor                  = strparam | 16;
  public final static int currentlocalpath               = strparam | 18;
  public final static int dataseparator                  = strparam | 20;
  public final static int defaultanglelabel              = strparam | 22;
  public final static int defaultlabelpdb                = strparam | 23;
  public final static int defaultlabelxyz                = strparam | 24;
  public final static int defaultcolorscheme             = strparam | 25;
  public final static int defaultdirectory               = strparam | 26;
  public final static int defaultdistancelabel           = strparam | 27;
  public final static int defaultdropscript              = strparam | 28;
  public final static int defaultloadfilter              = strparam | 29;
  public final static int defaultloadscript              = strparam | 30;
  public final static int defaults                       = strparam | 32;
  public final static int defaulttorsionlabel            = strparam | 34;
  public final static int defaultvdw                     = strparam | 35;
  public final static int edsurlcutoff                   = strparam | 36;
  public final static int edsurlformat                   = strparam | 37;
  public final static int energyunits                    = strparam | 38; 
  public final static int filecachedirectory             = strparam | 39;
  public final static int forcefield                     = strparam | 40;
  public final static int helppath                       = strparam | 41;
  public final static int hoverlabel                     = strparam | 42;
  public final static int language                       = strparam | 44;
  public final static int loadformat                     = strparam | 45;
  public final static int loadligandformat               = strparam | 46;
  public final static int logfile                        = strparam | 47;
  public final static int measurementunits               = strparam | 48; 
  public final static int nmrpredictformat               = strparam | 49;
  public final static int nmrurlformat                   = strparam | 50;
  public final static int pathforallfiles                = strparam | 51;
  public final static int picking                        = strparam | 52;
  public final static int pickingstyle                   = strparam | 54;
  public final static int picklabel                      = strparam | 56;
  public final static int propertycolorscheme            = strparam | 58;
  public final static int quaternionframe                = strparam | 60;
  public final static int smilesurlformat                = strparam | 62;
  public final static int smiles2dimageformat            = strparam | 64;
  public final static int unitcellcolor                  = strparam | 66;
  
  public final static int axesscale                      = floatparam | 2;
  public final static int bondtolerance                  = floatparam | 4;
  public final static int cameradepth                    = floatparam | 6;
  public final static int defaultdrawarrowscale          = floatparam | 8;
  public final static int defaulttranslucent             = floatparam | 10;
  public final static int dipolescale                    = floatparam | 11;
  public final static int drawfontsize                   = floatparam | 12;
  public final static int ellipsoidaxisdiameter          = floatparam | 13;
  public final static int exportscale                    = floatparam | 14;
  public final static int gestureswipefactor             = floatparam | 15;
  public final static int hbondsangleminimum             = floatparam | 16;
  public final static int hbondsdistancemaximum          = floatparam | 17;
  public final static int hoverdelay                     = floatparam | 18;
  public final static int loadatomdatatolerance          = floatparam | 19;  
  public final static int minbonddistance                = floatparam | 20;
  public final static int minimizationcriterion          = floatparam | 21;
  public final static int modulationscale                = floatparam | 22;
  public final static int mousedragfactor                = floatparam | 23;
  public final static int mousewheelfactor               = floatparam | 24;
  public final static int multiplebondradiusfactor       = floatparam | 25;
  public final static int multiplebondspacing            = floatparam | 26;
  public final static int navfps                         = floatparam | 27;
  public final static int navigationdepth                = floatparam | 28;
  public final static int navigationslab                 = floatparam | 29;
  public final static int navigationspeed                = floatparam | 30;
  public final static int navx                           = floatparam | 32;
  public final static int navy                           = floatparam | 34;
  public final static int navz                           = floatparam | 36;
  public final static int particleradius                 = floatparam | 37;
  public final static int pointgroupdistancetolerance    = floatparam | 38;
  public final static int pointgrouplineartolerance      = floatparam | 40;
  public final static int rotationradius                 = floatparam | 44;
  public final static int scaleangstromsperinch          = floatparam | 46;
  public final static int sheetsmoothing                 = floatparam | 48;
  public final static int slabrange                      = floatparam | 49;
  public final static int solventproberadius             = floatparam | 50;
  public final static int spinfps                        = floatparam | 52;
  public final static int spinx                          = floatparam | 54;
  public final static int spiny                          = floatparam | 56;
  public final static int spinz                          = floatparam | 58;
  public final static int starscale                      = floatparam | 59; // Jmol 13.1.15
  public final static int stereodegrees                  = floatparam | 60;
  public final static int strutdefaultradius             = floatparam | 62;
  public final static int strutlengthmaximum             = floatparam | 64;
  public final static int vibrationperiod                = floatparam | 68;
  public final static int vibrationscale                 = floatparam | 70;
  public final static int visualrange                    = floatparam | 72;

  public final static int ambientocclusion               = intparam | 1;               
  public final static int ambientpercent                 = intparam | 2;               
  public final static int animationfps                   = intparam | 4;
  public final static int axesmode                       = intparam | 6;
  public final static int bondradiusmilliangstroms       = intparam | 8;
  public final static int celshadingpower                = intparam | 9;
  public final static int delaymaximumms                 = intparam | 10;
  public final static int diffusepercent                 = intparam | 14;
  public final static int dotdensity                     = intparam | 15;
  public final static int dotscale                       = intparam | 16;
  public final static int ellipsoiddotcount              = intparam | 17;  
  public final static int helixstep                      = intparam | 18;
  public final static int hermitelevel                   = intparam | 19;
  public final static int historylevel                   = intparam | 20;
  public final static int isosurfacepropertysmoothingpower=intparam | 21;
  public final static int loglevel                       = intparam | 22;
  public final static int meshscale                      = intparam | 23;
  public final static int minimizationsteps              = intparam | 24;
  public final static int minpixelselradius              = intparam | 25;
  public final static int percentvdwatom                 = intparam | 26;
  public final static int perspectivemodel               = intparam | 27;
  public final static int phongexponent                  = intparam | 28;
  public final static int pickingspinrate                = intparam | 29;
  public final static int platformspeed                  = intparam | 30;
  public final static int propertyatomnumberfield        = intparam | 31;
  public final static int propertyatomnumbercolumncount  = intparam | 32;
  public final static int propertydatacolumncount        = intparam | 34;
  public final static int propertydatafield              = intparam | 36;
  public final static int repaintwaitms                  = intparam | 37;
  public final static int ribbonaspectratio              = intparam | 38;
  public final static int scriptreportinglevel           = intparam | 40;
  public final static int smallmoleculemaxatoms          = intparam | 42;
  public final static int specularexponent               = intparam | 44;
  public final static int specularpercent                = intparam | 46;
  public final static int specularpower                  = intparam | 48;
  public final static int strandcount                    = intparam | 50;
  public final static int strandcountformeshribbon       = intparam | 52;
  public final static int strandcountforstrands          = intparam | 54;
  public final static int strutspacing                   = intparam | 56;
  public final static int zdepth                         = intparam | 58;
  public final static int zslab                          = intparam | 60;
  public final static int zshadepower                    = intparam | 62;

  public final static int allowembeddedscripts           = booleanparam | 2;
  public final static int allowgestures                  = booleanparam | 4;
  public final static int allowkeystrokes                = booleanparam | 5;
  public static final int allowmodelkit                  = booleanparam | 6; // Jmol 12.RC15
  public final static int allowmoveatoms                 = booleanparam | 7; // Jmol 12.1.21
  public static final int allowmultitouch                = booleanparam | 8; // Jmol 11.9.24
  public final static int allowrotateselected            = booleanparam | 9;
  public final static int antialiasdisplay               = booleanparam | 10;
  public final static int antialiasimages                = booleanparam | 12;
  public final static int antialiastranslucent           = booleanparam | 14;
  public final static int appendnew                      = booleanparam | 16;
  public final static int applysymmetrytobonds           = booleanparam | 18;
  public final static int atompicking                    = booleanparam | 20;
  public final static int autobond                       = booleanparam | 22;
  public final static int autofps                        = booleanparam | 24;
//  public final static int autoloadorientation            = booleanparam | 26;
  public final static int axesmolecular                  = booleanparam | 28;
  public final static int axesorientationrasmol          = booleanparam | 30;
  public final static int axesunitcell                   = booleanparam | 32;
  public final static int axeswindow                     = booleanparam | 34;
  public final static int bondmodeor                     = booleanparam | 36;
  public final static int bondpicking                    = booleanparam | 38;
// set mathproperty  public final static int bonds                          = booleanparam | 40;
  public final static int cartoonbaseedges               = booleanparam | 41;
  public final static int cartoonrockets                 = booleanparam | 42;
  public final static int cartoonsfancy                  = booleanparam | 43;
  public final static int cartoonladders                 = booleanparam | 44;
  public final static int celshading                     = booleanparam | 45;
  public final static int chaincasesensitive             = booleanparam | 46;
  public final static int colorrasmol                    = booleanparam | 47;
  public final static int debugscript                    = booleanparam | 48;
  public final static int defaultstructuredssp           = booleanparam | 49;
  public final static int disablepopupmenu               = booleanparam | 50;
  public final static int displaycellparameters          = booleanparam | 52;
  public final static int dotsselectedonly               = booleanparam | 53;
  public final static int dotsurface                     = booleanparam | 54;
  public final static int dragselected                   = booleanparam | 55;
  public final static int drawhover                      = booleanparam | 56;
  public final static int drawpicking                    = booleanparam | 57;
  public final static int dsspcalchydrogen               = booleanparam | 58;
  public final static int ellipsoidarcs                  = booleanparam | 60;  
  public final static int ellipsoidarrows                = booleanparam | 61;  
  public final static int ellipsoidaxes                  = booleanparam | 62;  
  public final static int ellipsoidball                  = booleanparam | 63;  
  public final static int ellipsoiddots                  = booleanparam | 64;  
  public final static int ellipsoidfill                  = booleanparam | 65;  
  public final static int filecaching                    = booleanparam | 66;
  public final static int fontcaching                    = booleanparam | 68;
  public final static int fontscaling                    = booleanparam | 69;
  public final static int forceautobond                  = booleanparam | 70;
  public final static int fractionalrelative             = booleanparam | 72;
// see shapecommand public final static int frank                          = booleanparam | 72;
  public final static int greyscalerendering             = booleanparam | 74;
  public final static int hbondsbackbone                 = booleanparam | 76;
  public final static int hbondsrasmol                   = booleanparam | 77;
  public final static int hbondssolid                    = booleanparam | 78;
// see predefinedset  public final static int hetero                         = booleanparam | 80;
  public final static int hidenameinpopup                = booleanparam | 82;
  public final static int hidenavigationpoint            = booleanparam | 84;
  public final static int hidenotselected                = booleanparam | 86;
  public final static int highresolution                 = booleanparam | 88;
// see predefinedset  public final static int hydrogen                       = booleanparam | 90;
  public final static int imagestate                     = booleanparam | 92;
  public static final int iskiosk                        = booleanparam | 93; // 11.9.29
  public final static int isosurfacekey                  = booleanparam | 94;
  public final static int isosurfacepropertysmoothing    = booleanparam | 95;
  public final static int justifymeasurements            = booleanparam | 96;
  public final static int languagetranslation            = booleanparam | 97;
  public final static int legacyautobonding              = booleanparam | 98;
  public final static int legacyhaddition                = booleanparam | 99;
  public final static int logcommands                    = booleanparam | 100;
  public final static int loggestures                    = booleanparam | 101;
  public final static int measureallmodels               = booleanparam | 102;
  public final static int measurementlabels              = booleanparam | 103;
  public final static int messagestylechime              = booleanparam | 104;
  public final static int minimizationrefresh            = booleanparam | 105;
  public final static int minimizationsilent             = booleanparam | 106;
  public final static int modelkitmode                   = booleanparam | 107;  // 12.0.RC15
  public final static int monitorenergy                  = booleanparam | 108;
  public final static int multiprocessor                 = booleanparam | 109;
  public final static int navigatesurface                = booleanparam | 110;
  public final static int navigationmode                 = booleanparam | 111;
  public final static int navigationperiodic             = booleanparam | 112;
  public final static int partialdots                    = booleanparam | 113; // 12.1.46
  public final static int pdbaddhydrogens                = booleanparam | 114;
  public final static int pdbgetheader                   = booleanparam | 115;
  public final static int pdbsequential                  = booleanparam | 116;
  public final static int perspectivedepth               = booleanparam | 117;
  public final static int preservestate                  = booleanparam | 118;
  public final static int rangeselected                  = booleanparam | 119;
  public final static int refreshing                     = booleanparam | 120;
  public final static int ribbonborder                   = booleanparam | 122;
  public final static int rocketbarrels                  = booleanparam | 124;
  public final static int saveproteinstructurestate      = booleanparam | 126;
  public final static int scriptqueue                    = booleanparam | 128;
  public final static int selectallmodels                = booleanparam | 130;
  public final static int selecthetero                   = booleanparam | 132;
  public final static int selecthydrogen                 = booleanparam | 134;
  // see commands public final static int selectionhalo                  = booleanparam | 136;
  public final static int showaxes                       = booleanparam | 138;
  public final static int showboundbox                   = booleanparam | 140;
  public final static int showfrank                      = booleanparam | 142;
  public final static int showhiddenselectionhalos       = booleanparam | 144;
  public final static int showhydrogens                  = booleanparam | 146;
  public final static int showkeystrokes                 = booleanparam | 148;
  public final static int showmeasurements               = booleanparam | 150;
  public final static int showmultiplebonds              = booleanparam | 152;
  public final static int shownavigationpointalways      = booleanparam | 154;
// see intparam  public final static int showscript                     = booleanparam | 156;
  public final static int showtiming                     = booleanparam | 158;
  public final static int showunitcell                   = booleanparam | 160;
  public final static int slabbyatom                     = booleanparam | 162;
  public final static int slabbymolecule                 = booleanparam | 164;
  public final static int slabenabled                    = booleanparam | 166;
  public final static int smartaromatic                  = booleanparam | 168;
// see predefinedset  public final static int solvent                        = booleanparam | 170;
  public final static int solventprobe                   = booleanparam | 172;
// see intparam  public final static int specular                       = booleanparam | 174;
  public final static int ssbondsbackbone                = booleanparam | 176;
  public final static int statusreporting                = booleanparam | 178;
  public final static int strutsmultiple                 = booleanparam | 179;
  public final static int syncmouse                      = booleanparam | 180;
  public final static int syncscript                     = booleanparam | 182;
  public final static int testflag1                      = booleanparam | 184;
  public final static int testflag2                      = booleanparam | 186;
  public final static int testflag3                      = booleanparam | 188;
  public final static int testflag4                      = booleanparam | 189;
  public final static int tracealpha                     = booleanparam | 190;
  public final static int translucent                    = booleanparam | 191;
  public final static int twistedsheets                  = booleanparam | 192;
  public final static int usearcball                     = booleanparam | 193;
  public final static int useminimizationthread          = booleanparam | 194;
  public final static int usenumberlocalization          = booleanparam | 196;
  public final static int vectorsymmetry                 = booleanparam | 197;
  public final static int waitformoveto                  = booleanparam | 198;
  public final static int windowcentered                 = booleanparam | 199;
  public final static int wireframerotation              = booleanparam | 200;
  public final static int zerobasedxyzrasmol             = booleanparam | 202;
  public final static int zoomenabled                    = booleanparam | 204;
  public final static int zoomheight                     = booleanparam | 206;
  public final static int zoomlarge                      = booleanparam | 207;
  public final static int zshade                         = booleanparam | 208;

  
  // misc

  public final static int absolute      = misc  | 2;
  public final static int addhydrogens  = misc  | 4;
  public final static int adjust        = misc  | 6;
  public final static int align         = misc  | 8;
  public final static int allconnected  = misc  | 10;
  public final static int angstroms     = misc  | 12;
  public final static int anisotropy    = misc  | 14;
  public final static int append        = misc  | 15;
  public final static int arc           = misc  | 16 | expression;
  public final static int area          = misc  | 18;
  public final static int aromatic      = misc  | 20 | predefinedset;
  public final static int arrow         = misc  | 22;
  public final static int as            = misc  | 24; // for LOAD and ISOSURFACE only
  public final static int atomicorbital = misc  | 26;
  public final static int auto   = misc  | 28;
  public final static int axis   = misc  | 30;
  public final static int babel         = misc  | 32;
  public final static int babel21       = misc  | 34; 
  public final static int back          = misc  | 35;
  public final static int balls         = misc  | 36;
  public final static int barb          = misc  | 37;
  public final static int backlit       = misc  | 38;
  public final static int best          = misc  | 39;
  public final static int basepair      = misc  | 40;
  public final static int binary        = misc  | 42;
  public final static int blockdata     = misc  | 44;
  public final static int bondset       = misc  | 46;
  public final static int bottom        = misc  | 47;
  public final static int brillouin     = misc  | 48;
  public final static int cancel        = misc  | 50;
  public final static int cap           = misc  | 51 | expression;
  public final static int cavity        = misc  | 52;
  public final static int check         = misc  | 54;
  public final static int chemical      = misc  | 55;
  public final static int circle        = misc  | 56;
  public final static int clash         = misc  | 57;
  public final static int clear         = misc  | 58;
  public final static int clipboard     = misc  | 60;
  public final static int collapsed     = misc  | 62;
  public final static int colorscheme   = misc  | 64;
  public final static int command       = misc  | 66;
  public final static int commands      = misc  | 68;
  public final static int constraint    = misc  | 70;
  public final static int contour       = misc  | 72;
  public final static int contourlines  = misc  | 74;
  public final static int contours      = misc  | 76;
  public final static int corners       = misc  | 78;
  public final static int create = misc  | 80;
  public final static int criterion     = misc  | 81;
  public final static int crossed       = misc  | 82;
  public final static int curve         = misc  | 84;
  public final static int cutoff        = misc  | 86;
  public final static int cylinder      = misc  | 88;
  public final static int density        = misc  | 90;
  public final static int dssp           = misc  | 91;
  public final static int diameter       = misc  | 92;
  public final static int direction      = misc  | 94;
  public final static int discrete       = misc  | 96;
  public final static int displacement   = misc  | 98;
  public final static int distancefactor = misc  | 100;
  public final static int dotted         = misc  | 102;
  public final static int downsample     = misc  | 104;
  public final static int drawing        = misc  | 105;
  public final static int eccentricity   = misc  | 106;
  public final static int ed             = misc  | 108 | expression;
  public final static int edges          = misc  | 109;
  public final static int energy         = misc  | 110;
  public final static int error          = misc  | 111;
  public final static int facecenteroffset = misc  | 113;
  public final static int fill    = misc  | 114;
  public final static int filter         = misc  | 116;
  public final static int first   = misc  | 118;
  public final static int fixedtemp      = misc  | 122;
  public final static int flat           = misc  | 124;
  public final static int fps            = misc  | 126 | expression;
  public final static int from           = misc  | 128;
  public final static int front   = misc  | 130;
  public final static int frontedges     = misc  | 132;
  public final static int frontlit = misc  | 134;
  public final static int frontonly = misc  | 136;
  public final static int full            = misc  | 137;
  public final static int fullplane       = misc  | 138;
  public final static int fullylit = misc  | 140;
  public final static int functionxy     = misc  | 142;
  public final static int functionxyz    = misc  | 144;
  public final static int gridpoints     = misc  | 146;
  public final static int homo           = misc  | 149;
  public final static int id             = misc  | 150 | expression;
  public final static int ignore         = misc  | 152;
  public final static int inchi          = misc  | 153;
  public final static int inchikey       = misc  | 154;
  public final static int image          = misc  | 155;
  public final static int in             = misc  | 156;
  public final static int increment      = misc  | 157;
  public final static int info    = misc  | 158;
  public final static int inline         = misc  | 159;
  public final static int insideout      = misc  | 160;
  public final static int interior       = misc  | 162;
  public final static int internal       = misc  | 164;
  public final static int intramolecular = misc  | 165;
  public final static int intermolecular = misc  | 166;
  public final static int jmol    = misc  | 168;
  public final static int last    = misc  | 169;
  public final static int lattice        = misc  | 170;
  public final static int lighting       = misc  | 171;
  public final static int left    = misc  | 172;
  public final static int line           = misc  | 174;
  public final static int link           = misc  | 175;
  public final static int linedata       = misc  | 176;
  public final static int list    = misc  | 177; // just "list"
  public final static int lobe           = misc  | 178;
  public final static int lonepair       = misc  | 180;
  public final static int lp             = misc  | 182;
  public final static int lumo           = misc  | 184;
  public final static int manifest       = misc  | 186;
  public final static int maxset         = misc  | 190;
  public final static int menu           = misc  | 191;
  public final static int mep            = misc  | 192;
  public final static int mesh    = misc  | 194;
  public final static int middle         = misc  | 195;
  public final static int minset         = misc  | 196;
  public final static int mlp            = misc  | 198;
  public final static int mode           = misc  | 200;
  public final static int modify         = misc  | 201;
  public final static int modifyorcreate = misc  | 202;
  public final static int modelbased     = misc  | 204;
  public final static int molecular      = misc  | 205;
  public final static int monomer        = misc  | 206;
  public final static int morph          = misc  | 207;
  public final static int movie          = misc  | 208;
  public final static int mrc            = misc  | 209;
  public final static int msms           = misc  | 210;
  public final static int name           = misc  | 211;
  public final static int nci            = misc  | 212;
  public final static int next    = misc  | 213;
  public final static int nmr            = misc  | 214;
  public final static int nocontourlines  = misc  | 215;
  public final static int nocross        = misc  | 216;
  public final static int nodebug        = misc  | 217;
  public final static int nodots  = misc  | 218;
  public final static int noedges        = misc  | 220;
  public final static int nofill  = misc  | 222;
  public final static int nohead         = misc  | 224;
  public final static int noload         = misc  | 226;
  public final static int nomesh  = misc  | 228;
  public final static int noplane        = misc  | 230;
  public final static int normal         = misc  | 232;
  public final static int notfrontonly  = misc  | 234;
  public final static int notriangles   = misc  | 236;
  public final static int obj            = misc  | 238;
  public final static int object         = misc  | 240;
  public final static int offset         = misc  | 242;
  public final static int offsetside     = misc  | 244;
  public final static int once           = misc  | 246;
  public final static int only           = misc  | 248;
  public final static int opaque         = misc  | 250;
  public final static int options        = misc  | 251;
  public final static int orbital        = misc  | 252;
  public final static int orientation    = misc  | 253;
  public final static int origin         = misc  | 254; // 12.1.51
  public final static int out            = misc  | 255;
  public final static int packed         = misc  | 256;
  public final static int palindrome     = misc  | 258;
  public final static int parameters     = misc  | 259;
  public final static int path           = misc  | 260;
  public final static int pdb            = misc  | 262 | expression;
  public final static int pdbheader      = misc  | 264;
  public final static int period         = misc  | 266;
  public final static int perpendicular  = misc  | 268;
  public final static int phase          = misc  | 270;
  public final static int play    = misc  | 272;
  public final static int playrev = misc  | 274;
  public final static int pocket         = misc  | 276;
  public final static int pointgroup     = misc  | 278;
  public final static int pointsperangstrom = misc  | 280;
  public final static int polygon        = misc  | 282;
  public final static int prev    = misc  | 284;
  public final static int probe   = misc  | 285;
  public final static int pymol   = misc  | 286;
  public final static int rad            = misc  | 287;
  public final static int radical        = misc  | 288;
  public final static int range   = misc  | 290;
  public final static int rasmol  = misc  | 292;
  public final static int reference      = misc  | 294;
  public final static int remove         = misc  | 295;
  public final static int residue = misc  | 296;
  public final static int resolution     = misc  | 298;
  public final static int reversecolor   = misc  | 300;
  public final static int rewind         = misc  | 302;
  public final static int right          = misc  | 304;
  public final static int rock           = misc  | 305;
  public final static int rotate45       = misc  | 306;
  public final static int rotation = misc  | 308;
  public final static int rubberband     = misc  | 310;
  public final static int sasurface      = misc  | 312;
  public final static int scale          = misc  | 314;
  public final static int scene          = misc  | 315; // Jmol 12.3.32
  public final static int selection      = misc  | 316;
  public final static int shapely        = misc  | 320;
  public final static int sigma          = misc  | 322;
  public final static int sign           = misc  | 323;
  public final static int silent         = misc  | 324;
  public final static int solid          = misc  | 326;
  public final static int spacegroup     = misc  | 328;
  public final static int sphere  = misc  | 330;
  public final static int squared        = misc  | 332;
  public final static int state          = misc  | 334;
  public final static int stop           = misc  | 338;
  public final static int supercell      = misc  | 339;//
  public final static int ticks          = misc  | 340; 
  public final static int title          = misc  | 342;
  public final static int titleformat    = misc  | 344;
  public final static int to             = misc  | 346 | expression;
  public final static int top            = misc  | 348 | expression;
  public final static int torsion        = misc  | 350;
  public final static int transform      = misc  | 352;
  public final static int translation   = misc  | 354;
  public final static int triangles     = misc  | 358;
  public final static int url             = misc  | 360 | expression;
  public final static int user            = misc  | 362;
  public final static int val             = misc  | 364;
  public final static int variable        = misc  | 366;
  public final static int variables       = misc  | 368;
  public final static int vertices        = misc  | 370;
  public final static int spacebeforesquare      = misc  | 371;
  public final static int width           = misc  | 372;
  
  
  // predefined Tokens: 
  
  public final static T tokenSpaceBeforeSquare = o(spacebeforesquare, " ");
  public final static T tokenOn  = tv(on, 1, "on");
  public final static T tokenOff = tv(off, 0, "off");
  public final static T tokenAll = o(all, "all");
  public final static T tokenIf = o(ifcmd, "if");
  public final static T tokenAnd = o(opAnd, "and");
  public final static T tokenAndSpec = o(opAND, "");
  public final static T tokenOr  = o(opOr, "or");
  public final static T tokenAndFALSE = o(opAnd, "and");
  public final static T tokenOrTRUE = o(opOr, "or");
  public final static T tokenOpIf  = o(opIf, "?");
  public final static T tokenComma = o(comma, ",");
  public final static T tokenDefineString = tv(define, string, "@");
  public final static T tokenPlus = o(plus, "+");
  public final static T tokenMinus = o(minus, "-");
  public final static T tokenMul3 = o(mul3, "mul3"); // used only in internal calc.
  public final static T tokenTimes = o(times, "*");
  public final static T tokenDivide = o(divide, "/");

  public final static T tokenLeftParen = o(leftparen, "(");
  public final static T tokenRightParen = o(rightparen, ")");
  public final static T tokenArraySquare = o(array, "[");
  public final static T tokenArraySelector = o(leftsquare, "[");
 
  public final static T tokenExpressionBegin = o(expressionBegin, "expressionBegin");
  public final static T tokenExpressionEnd   = o(expressionEnd, "expressionEnd");
  public final static T tokenConnected       = o(connected, "connected");
  public final static T tokenCoordinateBegin = o(leftbrace, "{");
  public final static T tokenRightBrace = o(rightbrace, "}");
  public final static T tokenCoordinateEnd = tokenRightBrace;
  public final static T tokenColon           = o(colon, ":");
  public final static T tokenSetCmd          = o(set, "set");
  public final static T tokenSet             = tv(set, '=', "");
  public final static T tokenSetArray        = tv(set, '[', "");
  public final static T tokenSetProperty     = tv(set, '.', "");
  public final static T tokenSetVar          = tv(set, '=', "var");
  public final static T tokenEquals          = o(opEQ, "=");
  public final static T tokenScript          = o(script, "script");
  public final static T tokenSwitch          = o(switchcmd, "switch");
    
  private static Map<String, T> tokenMap = new Hashtable<String, T>();
  public static void addToken(String ident, T token) {
    tokenMap.put(ident, token);
  }
  
  public static T getTokenFromName(String name) {
    // this one needs to NOT be lower case for ScriptCompiler
    return tokenMap.get(name);
  }
  
  public static int getTokFromName(String name) {
    T token = getTokenFromName(name.toLowerCase());
    return (token == null ? nada : token.tok);
  }


  
  /**
   * note: nameOf is a very inefficient mechanism for getting 
   * the name of a token. But it is only used for error messages
   * and listings of variables and such.
   * 
   * @param tok
   * @return     the name of the token or 0xAAAAAA
   */
  public static String nameOf(int tok) {
    for (T token : tokenMap.values()) {
      if (token.tok == tok)
        return "" + token.value;
    }
    return "0x"+Integer.toHexString(tok);
   }
   
  @Override
  public String toString() {
    return toString2();
  }
  
  ////////command sets ///////

  protected String toString2() {
    return "Token["
    + astrType[tok < keyword ? tok : keyword]
    + "("+(tok%(1<<9))+"/0x" + Integer.toHexString(tok) + ")"
    + ((intValue == Integer.MAX_VALUE) ? "" : " intValue=" + intValue
        + "(0x" + Integer.toHexString(intValue) + ")")
    + ((value == null) ? "" : value instanceof String ? " value=\"" + value
        + "\"" : " value=" + value) + "]";
  }

  /**
   * retrieves an unsorted list of viable commands that could be
   * completed by this initial set of characters. If fewer than
   * two characters are given, then only the "preferred" command
   * is given (measure, not monitor, for example), and in all cases
   * if both a singular and a plural might be returned, only the
   * singular is returned.
   * 
   * @param strBegin initial characters of the command, or null
   * @return UNSORTED semicolon-separated string of viable commands
   */
  public static String getCommandSet(String strBegin) {
    String cmds = "";
    Map<String, Boolean> htSet = new Hashtable<String, Boolean>();
    int nCmds = 0;
    String s = (strBegin == null || strBegin.length() == 0 ? null : strBegin
        .toLowerCase());
    boolean isMultiCharacter = (s != null && s.length() > 1);
    for (Map.Entry<String, T> entry : tokenMap.entrySet()) {
      String name = entry.getKey();
      T token = entry.getValue();
      if ((token.tok & scriptCommand) != 0
          && (s == null || name.indexOf(s) == 0)
          && (isMultiCharacter || ((String) token.value).equals(name)))
        htSet.put(name, Boolean.TRUE);
    }
    for (Map.Entry<String, Boolean> entry : htSet.entrySet()) {
      String name = entry.getKey();
      if (name.charAt(name.length() - 1) != 's'
          || !htSet.containsKey(name.substring(0, name.length() - 1)))
        cmds += (nCmds++ == 0 ? "" : ";") + name;
    }
    return cmds;
  }
  
  public static List<T> getAtomPropertiesLike(String type) {
    type = type.toLowerCase();
    List<T> v = new  List<T>();
    boolean isAll = (type.length() == 0);
    for (Map.Entry<String, T> entry : tokenMap.entrySet()) {
      String name = entry.getKey();
      if (name.charAt(0) == '_')
        continue;
      T token = entry.getValue();
      if (tokAttr(token.tok, atomproperty) && (isAll || name.toLowerCase().startsWith(type))) {
        if (isAll || !((String) token.value).toLowerCase().startsWith(type))
          token = o(token.tok, name);
        v.addLast(token);
      }
    }
    return (v.size() == 0 ? null : v);
  }

  public static String[] getTokensLike(String type) {
    int attr = (type.equals("setparam") ? setparam 
        : type.equals("misc") ? misc 
        : type.equals("mathfunc") ? mathfunc : scriptCommand);
    int notattr = (attr == setparam ? deprecatedparam : nada);
    List<String> v = new  List<String>();
    for (Map.Entry<String, T> entry : tokenMap.entrySet()) {
      String name = entry.getKey();
      T token = entry.getValue();
      if (tokAttr(token.tok, attr) && (notattr == nada || !tokAttr(token.tok, notattr)))
        v.addLast(name);
    }
    String[] a = v.toArray(new String[v.size()]);
    Arrays.sort(a);
    return a;
  }

  public static int getSettableTokFromString(String s) {
    int tok = getTokFromName(s);
    return (tok != nada && tokAttr(tok, settable) 
          && !tokAttr(tok, mathproperty) ? tok : nada);
  }

  public static String completeCommand(Map<String, ?> map, boolean isSet, 
                                       boolean asCommand, 
                                       String str, int n) {
    if (map == null)
      map = tokenMap;
    else
      asCommand = false;
    List<String> v = new  List<String>();
    str = str.toLowerCase();
    for (String name : map.keySet()) {
      if (!name.startsWith(str))
        continue;
      int tok = getTokFromName(name);
      if (asCommand ? tokAttr(tok, scriptCommand) 
          : isSet ? tokAttr(tok, setparam) && !tokAttr(tok, deprecatedparam) 
          : true)
        v.addLast(name);
    }
    return AU.sortedItem(v, n);
  }

  static {

    // OK for J2S compiler even though T is not final because 
    // tokenMap is private
    
    Object[] arrayPairs  = {

    // atom expressions

      "(",            tokenLeftParen,
      ")",            tokenRightParen,
      "and",          tokenAnd,
      "&",            null,
      "&&",           null,
      "or",           tokenOr,
      "|",            null,
      "||",           null,
      "?",            tokenOpIf,
      ",",            tokenComma,
      "+=",           T.t(andequals),
      "-=",           null,
      "*=",           null,
      "/=",           null,
      "\\=",          null,
      "&=",           null,
      "|=",           null,
      "not",          T.t(opNot),
      "!",            null,
      "xor",          T.t(opXor),
    //no-- don't do this; it interferes with define
    //  "~",            null,
      "tog",          T.t(opToggle),
      "<",            T.t(opLT),
      "<=",           T.t(opLE),
      ">=",           T.t(opGE),
      ">",            T.t(opGT),
      "=",            tokenEquals,
      "==",           null,
      "!=",           T.t(opNE),
      "<>",           null,
      "within",       T.t(within),
      ".",            T.t(per),
      "[",            T.t(leftsquare),
      "]",            T.t(rightsquare),
      "{",            T.t(leftbrace),
      "}",            T.t(rightbrace),
      "$",            T.t(dollarsign),
      "%",            T.t(percent),
      ":",            tokenColon,
      ";",            T.t(semicolon),
      "++",           T.t(plusPlus),
      "--",           T.t(minusMinus),
      "**",           T.t(timestimes),
      "+",            tokenPlus,
      "-",            tokenMinus,
      "*",            tokenTimes,
      "/",            tokenDivide,
      "\\",           T.t(leftdivide),
    
    // commands
        
      "animation",         T.t(animation),
      "anim",              null,
      "assign",            T.t(assign),
      "axes",              T.t(axes),
      "backbone",          T.t(backbone),
      "background",        T.t(background),
      "bind",              T.t(bind),
      "bondorder",         T.t(bondorder),
      "boundbox",          T.t(boundbox),
      "boundingBox",       null,
      "break",             T.t(breakcmd),
      "calculate",         T.t(calculate),
      "capture",           T.t(capture),
      "cartoon",           T.t(cartoon),
      "cartoons",          null,
      "case",              T.t(casecmd),
      "catch",             T.t(catchcmd),
      "cd",                T.t(cd),
      "center",            T.t(center),
      "centre",            null,
      "centerat",          T.t(centerAt),
      "cgo",               T.t(cgo),
      "color",             T.t(color),
      "colour",            null,
      "compare",           T.t(compare),
      "configuration",     T.t(configuration),
      "conformation",      null,
      "config",            null,
      "connect",           T.t(connect),
      "console",           T.t(console),
      "contact",           T.t(contact),
      "contacts",          null,
      "continue",          T.t(continuecmd),
      "data",              T.t(data),
      "default",           T.t(defaultcmd),
      "define",            T.t(define),
      "@",                 null,
      "delay",             T.t(delay),
      "delete",            T.t(delete),
      "density",           T.t(density),
      "depth",             T.t(depth),
      "dipole",            T.t(dipole),
      "dipoles",           null,
      "display",           T.t(display),
      "dot",               T.t(dot),
      "dots",              T.t(dots),
      "draw",              T.t(draw),
      "echo",              T.t(echo),
      "ellipsoid",         T.t(ellipsoid),
      "ellipsoids",        null,
      "else",              T.t(elsecmd),
      "elseif",            T.t(elseif),
      "end",               T.t(end),
      "endif",             T.t(endifcmd),
      "exit",              T.t(exit),
      "file",              T.t(file),
      "files",             null,
      "font",              T.t(font),
      "for",               T.t(forcmd),
      "format",            T.t(format),
      "frame",             T.t(frame),
      "frames",            null,
      "frank",             T.t(frank),
      "function",          T.t(function),
      "functions",         null,
      "geosurface",        T.t(geosurface),
      "getProperty",       T.t(getproperty),
      "goto",              T.t(gotocmd),
      "halo",              T.t(halo),
      "halos",             null,
      "helix",             T.t(helix),
      "helixalpha",        T.t(helixalpha),
      "helix310",          T.t(helix310),
      "helixpi",           T.t(helixpi),
      "hbond",             T.t(hbond),
      "hbonds",            null,
      "help",              T.t(help),
      "hide",              T.t(hide),
      "history",           T.t(history),
      "hover",             T.t(hover),
      "if",                T.t(ifcmd),
      "in",                T.t(in),
      "initialize",        T.t(initialize),
      "invertSelected",    T.t(invertSelected),
      "isosurface",        T.t(isosurface),
      "javascript",        T.t(javascript),
      "label",             T.t(label),
      "labels",            null,
      "lcaoCartoon",       T.t(lcaocartoon),
      "lcaoCartoons",      null,
      "load",              T.t(load),
      "log",               T.t(log),
      "loop",              T.t(loop),
      "measure",           T.t(measure),
      "measures",          null,
      "monitor",           null,
      "monitors",          null,
      "meshribbon",        T.t(meshRibbon),
      "meshribbons",       null,
      "message",           T.t(message),
      "minimize",          T.t(minimize),
      "minimization",      null,
      "mo",                T.t(mo),
      "model",             T.t(model),
      "models",            null,
      "modulation",        T.t(modulation),
      "move",              T.t(move),
      "moveTo",            T.t(moveto),
      "navigate",          T.t(navigate),
      "navigation",        null,
      "origin",            T.t(origin),
      "out",               T.t(out),
      "parallel",          T.t(parallel),
      "pause",             T.t(pause),
      "wait",              null,
      "plot",              T.t(plot),
      "plot3d",            T.t(plot3d),
      "pmesh",             T.t(pmesh),
      "polygon",           T.t(polygon),
      "polyhedra",         T.t(polyhedra),
      "print",             T.t(print),
      "process",           T.t(process),
      "prompt",            T.t(prompt),
      "quaternion",        T.t(quaternion),
      "quaternions",       null,
      "quit",              T.t(quit),
      "ramachandran",      T.t(ramachandran),
      "rama",              null,
      "refresh",           T.t(refresh),
      "reset",             T.t(reset),
      "unset",             null,
      "restore",           T.t(restore),
      "restrict",          T.t(restrict),
      "return",            T.t(returncmd),
      "ribbon",            T.t(ribbon),
      "ribbons",           null,
      "rocket",            T.t(rocket),
      "rockets",           null,
      "rotate",            T.t(rotate),
      "rotateSelected",    T.t(rotateSelected),
      "save",              T.t(save),
      "script",            tokenScript,
      "source",            null,
      "select",            T.t(select),
      "selectionHalos",    T.t(selectionhalos),
      "selectionHalo",     null,
      "showSelections",    null,
      "set",               tokenSetCmd,
      "sheet",             T.t(sheet),
      "show",              T.t(show),
      "slab",              T.t(slab),
      "spacefill",         T.t(spacefill),
      "cpk",               null,
      "spin",              T.t(spin),
      "ssbond",            T.t(ssbond),
      "ssbonds",           null,
      "star",              T.t(star),
      "stars",             null,
      "step",              T.t(step),
      "steps",             null,
      "stereo",            T.t(stereo),
      "strand",            T.t(strands),
      "strands",           null,
      "structure",         T.t(structure),
      "_structure",        null,
      "strucNo",           T.t(strucno),
      "struts",            T.t(struts),
      "strut",             null,
      "subset",            T.t(subset),
      "switch",            tokenSwitch,
      "synchronize",       T.t(sync),
      "sync",              null,
      "trace",             T.t(trace),
      "translate",         T.t(translate),
      "translateSelected", T.t(translateSelected),
      "try",               T.t(trycmd),
      "unbind",            T.t(unbind),
      "unitcell",          T.t(unitcell),
      "var",               T.t(var),
      "vector",            T.t(vector),
      "vectors",           null,
      "vibration",         T.t(vibration),
      "while",             T.t(whilecmd),
      "wireframe",         T.t(wireframe),
      "write",             T.t(write),
      "zap",               T.t(zap),
      "zoom",              T.t(zoom),
      "zoomTo",            T.t(zoomTo),
                            
      //                   show parameters
  
      "atom",              T.t(atoms),
      "atoms",             null,
      "axis",              T.t(axis),
      "axisangle",         T.t(axisangle),
      "basepair",          T.t(basepair),
      "basepairs",         null,
      "orientation",       T.t(orientation),
      "orientations",      null,
      "pdbheader",         T.t(pdbheader),                          
      "polymer",           T.t(polymer),
      "polymers",          null,
      "residue",           T.t(residue),
      "residues",          null,
      "rotation",          T.t(rotation),
      "row",               T.t(row),
      "sequence",          T.t(sequence),
      "shape",             T.t(shape),
      "state",             T.t(state),
      "symbol",            T.t(symbol),
      "symmetry",          T.t(symmetry),
      "spaceGroup",        T.t(spacegroup),
      "transform",         T.t(transform),
      "translation",       T.t(translation),
      "url",               T.t(url),
  
      // misc
  
      "abs",             T.t(abs),
      "absolute",        T.t(absolute),
      "acos",            T.t(acos),
      "add",             T.t(add),
      "adpmax",          T.t(adpmax),
      "adpmin",          T.t(adpmin),
      "align",           T.t(align),
      "all",             tokenAll,
      "altloc",          T.t(altloc),
      "altlocs",         null,
      "ambientOcclusion", T.t(ambientocclusion),
      "amino",           T.t(amino),
      "angle",           T.t(angle),
      "array",           T.t(array),
      "as",              T.t(as),
      "atomID",          T.t(atomid),
      "_atomID",         null,
      "_a",              null, 
      "atomIndex",       T.t(atomindex),
      "atomName",        T.t(atomname),
      "atomno",          T.t(atomno),
      "atomType",        T.t(atomtype),
      "atomX",           T.t(atomx),
      "atomY",           T.t(atomy),
      "atomZ",           T.t(atomz),
      "average",         T.t(average),
      "babel",           T.t(babel),
      "babel21",         T.t(babel21), 
      "back",            T.t(back),
      "backlit",         T.t(backlit),
      "balls",           T.t(balls),
      "baseModel",       T.t(basemodel), // Jmol 12.3.19
      "best",            T.t(best), // rotate BEST
      "bin",             T.t(bin),
      "bondCount",       T.t(bondcount),
      "bottom",          T.t(bottom),
      "branch",          T.t(branch),
      "brillouin",       T.t(brillouin),
      "bzone",           null,
      "wignerSeitz",     null,
      "cache",           T.t(cache), // Jmol 12.3.24 
      "carbohydrate",    T.t(carbohydrate),
      "cell",            T.t(cell),
      "chain",           T.t(chain),
      "chains",          null,
      "chainNo",         T.t(chainno),
      "chemicalShift",   T.t(chemicalshift),
      "cs",              null,
      "clash",           T.t(clash),
      "clear",           T.t(clear),
      "clickable",       T.t(clickable),
      "clipboard",       T.t(clipboard),
      "connected",       T.t(connected),
      "constraint",      T.t(constraint),
      "contourLines",    T.t(contourlines),
      "coord",           T.t(coord),
      "coordinates",     null,
      "coords",          null,
      "cos",             T.t(cos),
      "cross",           T.t(cross),
      "covalent",        T.t(covalent),
      "direction",       T.t(direction),
      "displacement",    T.t(displacement),
      "displayed",       T.t(displayed),
      "distance",        T.t(distance),
      "div",             T.t(div),
      "DNA",             T.t(dna),
      "dotted",          T.t(dotted),
      "DSSP",            T.t(dssp),
      "element",         T.t(element),
      "elemno",          T.t(elemno),
      "_e",              T.t(elemisono),
      "error",           T.t(error),
      "exportScale",     T.t(exportscale),
      "fill",            T.t(fill),
      "find",            T.t(find),
      "fixedTemperature",T.t(fixedtemp),
      "forcefield",      T.t(forcefield),
      "formalCharge",    T.t(formalcharge),
      "charge",          null, 
      "eta",             T.t(eta),
      "front",           T.t(front),
      "frontlit",        T.t(frontlit),
      "frontOnly",       T.t(frontonly),
      "fullylit",        T.t(fullylit),
      "fx",              T.t(fracx),
      "fy",              T.t(fracy),
      "fz",              T.t(fracz),
      "fxyz",            T.t(fracxyz),
      "fux",             T.t(fux),
      "fuy",             T.t(fuy),
      "fuz",             T.t(fuz),
      "fuxyz",           T.t(fuxyz),
      "group",           T.t(group),
      "groups",          null,
      "group1",          T.t(group1),
      "groupID",         T.t(groupid),
      "_groupID",        null, 
      "_g",              null, 
      "groupIndex",      T.t(groupindex),
      "hidden",          T.t(hidden),
      "highlight",       T.t(highlight),
      "hkl",             T.t(hkl),
      "hydrophobic",     T.t(hydrophobic),
      "hydrophobicity",  null,
      "hydro",           null,
      "id",              T.t(id),
      "identify",        T.t(identify),
      "ident",           null,
      "image",           T.t(image),
      "info",            T.t(info),
      "inline",          T.t(inline),
      "insertion",       T.t(insertion),
      "insertions",      null, 
      "intramolecular",  T.t(intramolecular),
      "intra",           null,
      "intermolecular",  T.t(intermolecular),
      "inter",           null,
      "ionic",           T.t(ionic),
      "ionicRadius",     null,
      "isAromatic",      T.t(isaromatic),
      "Jmol",            T.t(jmol),
      "join",            T.t(join),
      "keys",            T.t(keys),
      "last",            T.t(last),
      "left",            T.t(left),
      "length",          T.t(length),
      "lines",           T.t(lines),
      "list",            T.t(list),
      "magneticShielding", T.t(magneticshielding),
      "ms",              null,
      "mass",            T.t(mass),
      "max",             T.t(max),
      "mep",             T.t(mep),
      "mesh",            T.t(mesh),
      "middle",          T.t(middle),
      "min",             T.t(min),
      "mlp",             T.t(mlp),
      "mode",            T.t(mode),
      "modify",          T.t(modify),
      "modifyOrCreate",  T.t(modifyorcreate),
      "molecule",        T.t(molecule),
      "molecules",       null, 
      "modelIndex",      T.t(modelindex),
      "monomer",         T.t(monomer),
      "morph",           T.t(morph),
      "movie",           T.t(movie),
      "mul",             T.t(mul),
      "mul3",            T.t(mul3),
      "nci",             T.t(nci),
      "next",            T.t(next),
      "noDots",          T.t(nodots),
      "noFill",          T.t(nofill),
      "noMesh",          T.t(nomesh),
      "none",            T.t(none),
      "null",            null,
      "inherit",         null,
      "normal",          T.t(normal),
      "noContourLines",  T.t(nocontourlines),
      "nonequivalent",   T.t(nonequivalent),
      "notFrontOnly",    T.t(notfrontonly),
      "noTriangles",     T.t(notriangles),
      "now",             T.t(now),
      "nucleic",         T.t(nucleic),
      "occupancy",       T.t(occupancy),
      "off",             tokenOff, 
      "false",           null, 
      "on",              tokenOn,
      "true",            null, 
      "omega",           T.t(omega),
      "only",            T.t(only),
      "opaque",          T.t(opaque),
      "options",         T.t(options),
      "partialCharge",   T.t(partialcharge),
      "phi",             T.t(phi),
      "plane",           T.t(plane),
      "planar",          null,
      "play",            T.t(play),
      "playRev",         T.t(playrev),
      "point",           T.t(point),
      "points",          null,
      "pointGroup",      T.t(pointgroup),
      "polymerLength",   T.t(polymerlength),
      "pop",             T.t(pop),
      "previous",        T.t(prev),
      "prev",            null,
      "probe",           T.t(probe),
      "property",        T.t(property),
      "properties",      null,
      "protein",         T.t(protein),
      "psi",             T.t(psi),
      "purine",          T.t(purine),
      "push",            T.t(push),
      "PyMOL",           T.t(pymol),
      "pyrimidine",      T.t(pyrimidine),
      "random",          T.t(random),
      "range",           T.t(range),
      "rasmol",          T.t(rasmol),
      "replace",         T.t(replace),
      "resno",           T.t(resno),
      "resume",          T.t(resume),
      "rewind",          T.t(rewind),
      "reverse",         T.t(reverse),
      "right",           T.t(right),
      "RNA",             T.t(rna),
      "rock",            T.t(rock),
      "rubberband",      T.t(rubberband),
      "saSurface",       T.t(sasurface),
      "scale",           T.t(scale),
      "scene",           T.t(scene),
      "search",          T.t(search),
      "smarts",          null,
      "selected",        T.t(selected),
      "shapely",         T.t(shapely),
      "sidechain",       T.t(sidechain),
      "sin",             T.t(sin),
      "site",            T.t(site),
      "size",            T.t(size),
      "smiles",          T.t(smiles),
      "substructure",    T.t(substructure),  // 12.0 substructure-->smiles (should be smarts, but for legacy reasons, need this to be smiles
      "solid",           T.t(solid),
      "sort",            T.t(sort),
      "specialPosition", T.t(specialposition),
      "sqrt",            T.t(sqrt),
      "split",           T.t(split),
      "starScale",       T.t(starscale),
      "stddev",          T.t(stddev),
      "straightness",    T.t(straightness),
      "structureId",     T.t(strucid),
      "supercell",       T.t(supercell),
      "sub",             T.t(sub),
      "sum",             T.t(sum), // sum
      "sum2",            T.t(sum2), // sum of squares
      "surface",         T.t(surface),
      "surfaceDistance", T.t(surfacedistance),
      "symop",           T.t(symop),
      "sx",              T.t(screenx),
      "sy",              T.t(screeny),
      "sz",              T.t(screenz),
      "sxyz",            T.t(screenxyz),
      "temperature",     T.t(temperature),
      "relativeTemperature", null,
      "tensor",          T.t(tensor),
      "theta",           T.t(theta),
      "thisModel",       T.t(thismodel),
      "ticks",           T.t(ticks),
      "top",             T.t(top),
      "torsion",         T.t(torsion),
      "trajectory",      T.t(trajectory),
      "trajectories",    null,
      "translucent",     T.t(translucent),
      "triangles",       T.t(triangles),
      "trim",            T.t(trim),
      "type",            T.t(type),
      "ux",              T.t(unitx),
      "uy",              T.t(unity),
      "uz",              T.t(unitz),
      "uxyz",            T.t(unitxyz),
      "user",            T.t(user),
      "valence",         T.t(valence),
      "vanderWaals",     T.t(vanderwaals),
      "vdw",             null,
      "vdwRadius",       null,
      "visible",         T.t(visible),
      "volume",          T.t(volume),
      "vx",              T.t(vibx),
      "vy",              T.t(viby),
      "vz",              T.t(vibz),
      "vxyz",            T.t(vibxyz),
      "xyz",             T.t(xyz),
      "w",               T.t(w),
      "x",               T.t(x),
      "y",               T.t(y),
      "z",               T.t(z),

      // more misc parameters
      "addHydrogens",    T.t(addhydrogens),
      "allConnected",    T.t(allconnected),
      "angstroms",       T.t(angstroms),
      "anisotropy",      T.t(anisotropy),
      "append",          T.t(append),
      "arc",             T.t(arc),
      "area",            T.t(area),
      "aromatic",        T.t(aromatic),
      "arrow",           T.t(arrow),
      "auto",            T.t(auto),
      "barb",            T.t(barb),
      "binary",          T.t(binary),
      "blockData",       T.t(blockdata),
      "cancel",          T.t(cancel),
      "cap",             T.t(cap),
      "cavity",          T.t(cavity),
      "centroid",        T.t(centroid),
      "check",           T.t(check),
      "chemical",        T.t(chemical),
      "circle",          T.t(circle),
      "collapsed",       T.t(collapsed),
      "col",             T.t(col),
      "colorScheme",     T.t(colorscheme),
      "command",         T.t(command),
      "commands",        T.t(commands),
      "contour",         T.t(contour),
      "contours",        T.t(contours),
      "corners",         T.t(corners),
      "count",           T.t(count),
      "criterion",       T.t(criterion),
      "create",          T.t(create),
      "crossed",         T.t(crossed),
      "curve",           T.t(curve),
      "cutoff",          T.t(cutoff),
      "cylinder",        T.t(cylinder),
      "diameter",        T.t(diameter),
      "discrete",        T.t(discrete),
      "distanceFactor",  T.t(distancefactor),
      "downsample",      T.t(downsample),
      "drawing",         T.t(drawing),
      "eccentricity",    T.t(eccentricity),
      "ed",              T.t(ed),
      "edges",           T.t(edges),
      "energy",          T.t(energy),
      "exitJmol",        T.t(exitjmol),
      "faceCenterOffset",T.t(facecenteroffset),
      "filter",          T.t(filter),
      "first",           T.t(first),
      "fixed",           T.t(fixed),
      "fix",             null,
      "flat",            T.t(flat),
      "fps",             T.t(fps),
      "from",            T.t(from),
      "frontEdges",      T.t(frontedges),
      "full",            T.t(full),
      "fullPlane",       T.t(fullplane),
      "functionXY",      T.t(functionxy),
      "functionXYZ",     T.t(functionxyz),
      "gridPoints",      T.t(gridpoints),
      "homo",            T.t(homo),
      "ignore",          T.t(ignore),
      "InChI",           T.t(inchi),
      "InChIKey",        T.t(inchikey),
      "increment",       T.t(increment),
      "insideout",       T.t(insideout),
      "interior",        T.t(interior),
      "intersection",    T.t(intersection),
      "intersect",       null,
      "internal",        T.t(internal),
      "lattice",         T.t(lattice),
      "line",            T.t(line),
      "lineData",        T.t(linedata),
      "link",            T.t(link),
      "lobe",            T.t(lobe),
      "lonePair",        T.t(lonepair),
      "lp",              T.t(lp),
      "lumo",            T.t(lumo),
      "manifest",        T.t(manifest),
      "mapProperty",     T.t(mapProperty),
      "map",             null,
      "maxSet",          T.t(maxset),
      "menu",            T.t(menu),
      "minSet",          T.t(minset),
      "modelBased",      T.t(modelbased),
      "molecular",       T.t(molecular),
      "mrc",             T.t(mrc),
      "msms",            T.t(msms),
      "name",            T.t(name),
      "nmr",             T.t(nmr),
      "noCross",         T.t(nocross),
      "noDebug",         T.t(nodebug),
      "noEdges",         T.t(noedges),
      "noHead",          T.t(nohead),
      "noLoad",          T.t(noload),
      "noPlane",         T.t(noplane),
      "object",          T.t(object),
      "obj",             T.t(obj),
      "offset",          T.t(offset),
      "offsetSide",      T.t(offsetside),
      "once",            T.t(once),
      "orbital",         T.t(orbital),
      "atomicOrbital",   T.t(atomicorbital),
      "packed",          T.t(packed),
      "palindrome",      T.t(palindrome),
      "parameters",      T.t(parameters),
      "path",            T.t(path),
      "pdb",             T.t(pdb),
      "period",          T.t(period),
      "periodic",        null,
      "perpendicular",   T.t(perpendicular),
      "perp",            null,
      "phase",           T.t(phase),
      "pocket",          T.t(pocket),
      "pointsPerAngstrom", T.t(pointsperangstrom),
      "radical",         T.t(radical),
      "rad",             T.t(rad),
      "reference",       T.t(reference),
      "remove",          T.t(remove),
      "resolution",      T.t(resolution),
      "reverseColor",    T.t(reversecolor),
      "rotate45",        T.t(rotate45),
      "selection",       T.t(selection),
      "sigma",           T.t(sigma),
      "sign",            T.t(sign),
      "silent",          T.t(silent),
      "sphere",          T.t(sphere),
      "squared",         T.t(squared),
      "stop",            T.t(stop),
      "title",           T.t(title),
      "titleFormat",     T.t(titleformat),
      "to",              T.t(to),
      "value",           T.t(val),
      "variable",        T.t(variable),
      "variables",       T.t(variables),
      "vertices",        T.t(vertices),
      "width",           T.t(width),

      // set params

      "backgroundModel",                          T.t(backgroundmodel),
      "celShading",                               T.t(celshading),
      "celShadingPower",                          T.t(celshadingpower),
      "debug",                                    T.t(debug),
      "defaultLattice",                           T.t(defaultlattice),
      "measurements",                             T.t(measurements),
      "measurement",                              null,
      "scale3D",                                  T.t(scale3d),
      "toggleLabel",                              T.t(togglelabel),
      "userColorScheme",                          T.t(usercolorscheme),
      "timeout",                                  T.t(timeout),
      "timeouts",                                 null,
      
      // string
      
      "animationMode",                            T.t(animationmode),
      "appletProxy",                              T.t(appletproxy),
      "atomTypes",                                T.t(atomtypes),
      "axesColor",                                T.t(axescolor),
      "axis1Color",                               T.t(axis1color),
      "axis2Color",                               T.t(axis2color),
      "axis3Color",                               T.t(axis3color),
      "backgroundColor",                          T.t(backgroundcolor),
      "bondmode",                                 T.t(bondmode),
      "boundBoxColor",                            T.t(boundboxcolor),
      "boundingBoxColor",                         null,
      "currentLocalPath",                         T.t(currentlocalpath),
      "dataSeparator",                            T.t(dataseparator),
      "defaultAngleLabel",                        T.t(defaultanglelabel),
      "defaultColorScheme",                       T.t(defaultcolorscheme),
      "defaultColors",                            null,
      "defaultDirectory",                         T.t(defaultdirectory),
      "defaultDistanceLabel",                     T.t(defaultdistancelabel),
      "defaultDropScript",                        T.t(defaultdropscript), 
      "defaultLabelPDB",                          T.t(defaultlabelpdb),
      "defaultLabelXYZ",                          T.t(defaultlabelxyz),
      "defaultLoadFilter",                        T.t(defaultloadfilter),
      "defaultLoadScript",                        T.t(defaultloadscript),
      "defaults",                                 T.t(defaults),
      "defaultTorsionLabel",                      T.t(defaulttorsionlabel),
      "defaultVDW",                               T.t(defaultvdw),
      "drawFontSize",                             T.t(drawfontsize),
      "edsUrlCutoff",                             T.t(edsurlcutoff),
      "edsUrlFormat",                             T.t(edsurlformat),
      "energyUnits",                              T.t(energyunits),
      "fileCacheDirectory",                       T.t(filecachedirectory),
      "fontsize",                                 T.t(fontsize),
      "helpPath",                                 T.t(helppath),
      "hoverLabel",                               T.t(hoverlabel),
      "language",                                 T.t(language),
      "loadFormat",                               T.t(loadformat),
      "loadLigandFormat",                         T.t(loadligandformat),
      "logFile",                                  T.t(logfile),
      "measurementUnits",                         T.t(measurementunits),
      "nmrPredictFormat",                         T.t(nmrpredictformat),
      "nmrUrlFormat",                             T.t(nmrurlformat),
      "pathForAllFiles",                          T.t(pathforallfiles),
      "picking",                                  T.t(picking),
      "pickingStyle",                             T.t(pickingstyle),
      "pickLabel",                                T.t(picklabel),
      "platformSpeed",                            T.t(platformspeed),
      "propertyColorScheme",                      T.t(propertycolorscheme),
      "quaternionFrame",                          T.t(quaternionframe),
      "smilesUrlFormat",                          T.t(smilesurlformat),
      "smiles2dImageFormat",                      T.t(smiles2dimageformat),
      "unitCellColor",                            T.t(unitcellcolor),

      // float
      
      "axesScale",                                T.t(axesscale),
      "axisScale",                                null, // legacy
      "bondTolerance",                            T.t(bondtolerance),
      "cameraDepth",                              T.t(cameradepth),
      "defaultDrawArrowScale",                    T.t(defaultdrawarrowscale),
      "defaultTranslucent",                       T.t(defaulttranslucent),
      "dipoleScale",                              T.t(dipolescale),
      "ellipsoidAxisDiameter",                    T.t(ellipsoidaxisdiameter),
      "gestureSwipeFactor",                       T.t(gestureswipefactor),
      "hbondsAngleMinimum",                       T.t(hbondsangleminimum),
      "hbondsDistanceMaximum",                    T.t(hbondsdistancemaximum),
      "hoverDelay",                               T.t(hoverdelay),
      "loadAtomDataTolerance",                    T.t(loadatomdatatolerance),
      "minBondDistance",                          T.t(minbonddistance),
      "minimizationCriterion",                    T.t(minimizationcriterion),
      "modulationScale",                          T.t(modulationscale),
      "mouseDragFactor",                          T.t(mousedragfactor),
      "mouseWheelFactor",                         T.t(mousewheelfactor),
      "navFPS",                                   T.t(navfps),
      "navigationDepth",                          T.t(navigationdepth),
      "navigationSlab",                           T.t(navigationslab),
      "navigationSpeed",                          T.t(navigationspeed),
      "navX",                                     T.t(navx),
      "navY",                                     T.t(navy),
      "navZ",                                     T.t(navz),
      "particleRadius",                           T.t(particleradius),
      "pointGroupDistanceTolerance",              T.t(pointgroupdistancetolerance),
      "pointGroupLinearTolerance",                T.t(pointgrouplineartolerance),
      "radius",                                   T.t(radius),
      "rotationRadius",                           T.t(rotationradius),
      "scaleAngstromsPerInch",                    T.t(scaleangstromsperinch),
      "sheetSmoothing",                           T.t(sheetsmoothing),
      "slabRange",                                T.t(slabrange),
      "solventProbeRadius",                       T.t(solventproberadius),
      "spinFPS",                                  T.t(spinfps),
      "spinX",                                    T.t(spinx),
      "spinY",                                    T.t(spiny),
      "spinZ",                                    T.t(spinz),
      "stereoDegrees",                            T.t(stereodegrees),
      "strutDefaultRadius",                       T.t(strutdefaultradius),
      "strutLengthMaximum",                       T.t(strutlengthmaximum),
      "vectorScale",                              T.t(vectorscale),
      "vectorSymmetry",                           T.t(vectorsymmetry),
      "vibrationPeriod",                          T.t(vibrationperiod),
      "vibrationScale",                           T.t(vibrationscale),
      "visualRange",                              T.t(visualrange),

      // int

      "ambientPercent",                           T.t(ambientpercent),
      "ambient",                                  null, 
      "animationFps",                             T.t(animationfps),
      "axesMode",                                 T.t(axesmode),
      "bondRadiusMilliAngstroms",                 T.t(bondradiusmilliangstroms),
      "delayMaximumMs",                           T.t(delaymaximumms),
      "diffusePercent",                           T.t(diffusepercent),
      "diffuse",                                  null, 
      "dotDensity",                               T.t(dotdensity),
      "dotScale",                                 T.t(dotscale),
      "ellipsoidDotCount",                        T.t(ellipsoiddotcount),
      "helixStep",                                T.t(helixstep),
      "hermiteLevel",                             T.t(hermitelevel),
      "historyLevel",                             T.t(historylevel),
      "lighting",                                 T.t(lighting),
      "logLevel",                                 T.t(loglevel),
      "meshScale",                                T.t(meshscale),
      "minimizationSteps",                        T.t(minimizationsteps),
      "minPixelSelRadius",                        T.t(minpixelselradius),
      "percentVdwAtom",                           T.t(percentvdwatom),
      "perspectiveModel",                         T.t(perspectivemodel),
      "phongExponent",                            T.t(phongexponent),
      "pickingSpinRate",                          T.t(pickingspinrate),
      "propertyAtomNumberField",                  T.t(propertyatomnumberfield),
      "propertyAtomNumberColumnCount",            T.t(propertyatomnumbercolumncount),
      "propertyDataColumnCount",                  T.t(propertydatacolumncount),
      "propertyDataField",                        T.t(propertydatafield),
      "repaintWaitMs",                            T.t(repaintwaitms),
      "ribbonAspectRatio",                        T.t(ribbonaspectratio),
      "scriptReportingLevel",                     T.t(scriptreportinglevel),
      "showScript",                               T.t(showscript),
      "smallMoleculeMaxAtoms",                    T.t(smallmoleculemaxatoms),
      "specular",                                 T.t(specular),
      "specularExponent",                         T.t(specularexponent),
      "specularPercent",                          T.t(specularpercent),
      "specPercent",                              null,
      "specularPower",                            T.t(specularpower),
      "specpower",                                null, 
      "strandCount",                              T.t(strandcount),
      "strandCountForMeshRibbon",                 T.t(strandcountformeshribbon),
      "strandCountForStrands",                    T.t(strandcountforstrands),
      "strutSpacing",                             T.t(strutspacing),
      "zDepth",                                   T.t(zdepth),
      "zSlab",                                    T.t(zslab),
      "zshadePower",                              T.t(zshadepower),

      // boolean

      "allowEmbeddedScripts",                     T.t(allowembeddedscripts),
      "allowGestures",                            T.t(allowgestures),
      "allowKeyStrokes",                          T.t(allowkeystrokes),
      "allowModelKit",                            T.t(allowmodelkit),
      "allowMoveAtoms",                           T.t(allowmoveatoms),
      "allowMultiTouch",                          T.t(allowmultitouch),
      "allowRotateSelected",                      T.t(allowrotateselected),
      "antialiasDisplay",                         T.t(antialiasdisplay),
      "antialiasImages",                          T.t(antialiasimages),
      "antialiasTranslucent",                     T.t(antialiastranslucent),
      "appendNew",                                T.t(appendnew),
      "applySymmetryToBonds",                     T.t(applysymmetrytobonds),
      "atomPicking",                              T.t(atompicking),
      "autobond",                                 T.t(autobond),
      "autoFPS",                                  T.t(autofps),
//      "autoLoadOrientation",                      new Token(autoloadorientation),
      "axesMolecular",                            T.t(axesmolecular),
      "axesOrientationRasmol",                    T.t(axesorientationrasmol),
      "axesUnitCell",                             T.t(axesunitcell),
      "axesWindow",                               T.t(axeswindow),
      "bondModeOr",                               T.t(bondmodeor),
      "bondPicking",                              T.t(bondpicking),
      "bonds",                                    T.t(bonds),
      "bond",                                     null, 
      "cartoonBaseEdges",                         T.t(cartoonbaseedges),
      "cartoonsFancy",                            T.t(cartoonsfancy),
      "cartoonFancy",                             null,
      "cartoonLadders",                           T.t(cartoonladders),
      "cartoonRockets",                           T.t(cartoonrockets),
      "chainCaseSensitive",                       T.t(chaincasesensitive),
      "colorRasmol",                              T.t(colorrasmol),
      "debugScript",                              T.t(debugscript),
      "defaultStructureDssp",                     T.t(defaultstructuredssp),
      "disablePopupMenu",                         T.t(disablepopupmenu),
      "displayCellParameters",                    T.t(displaycellparameters),
      "dotsSelectedOnly",                         T.t(dotsselectedonly),
      "dotSurface",                               T.t(dotsurface),
      "dragSelected",                             T.t(dragselected),
      "drawHover",                                T.t(drawhover),
      "drawPicking",                              T.t(drawpicking),
      "dsspCalculateHydrogenAlways",              T.t(dsspcalchydrogen),
      "ellipsoidArcs",                            T.t(ellipsoidarcs),
      "ellipsoidArrows",                          T.t(ellipsoidarrows),
      "ellipsoidAxes",                            T.t(ellipsoidaxes),
      "ellipsoidBall",                            T.t(ellipsoidball),
      "ellipsoidDots",                            T.t(ellipsoiddots),
      "ellipsoidFill",                            T.t(ellipsoidfill),
      "fileCaching",                              T.t(filecaching),
      "fontCaching",                              T.t(fontcaching),
      "fontScaling",                              T.t(fontscaling),
      "forceAutoBond",                            T.t(forceautobond),
      "fractionalRelative",                       T.t(fractionalrelative),
// see commands     "frank",                                    new Token(frank),
      "greyscaleRendering",                       T.t(greyscalerendering),
      "hbondsBackbone",                           T.t(hbondsbackbone),
      "hbondsRasmol",                             T.t(hbondsrasmol),
      "hbondsSolid",                              T.t(hbondssolid),
      "hetero",                                   T.t(hetero),
      "hideNameInPopup",                          T.t(hidenameinpopup),
      "hideNavigationPoint",                      T.t(hidenavigationpoint),
      "hideNotSelected",                          T.t(hidenotselected),
      "highResolution",                           T.t(highresolution),
      "hydrogen",                                 T.t(hydrogen),
      "hydrogens",                                null,
      "imageState",                               T.t(imagestate),
      "isKiosk",                                  T.t(iskiosk),
      "isosurfaceKey",                            T.t(isosurfacekey),
      "isosurfacePropertySmoothing",              T.t(isosurfacepropertysmoothing),
      "isosurfacePropertySmoothingPower",         T.t(isosurfacepropertysmoothingpower),
      "justifyMeasurements",                      T.t(justifymeasurements),
      "languageTranslation",                      T.t(languagetranslation),
      "legacyAutoBonding",                        T.t(legacyautobonding),
      "legacyHAddition",                          T.t(legacyhaddition),
      "logCommands",                              T.t(logcommands),
      "logGestures",                              T.t(loggestures),
      "measureAllModels",                         T.t(measureallmodels),
      "measurementLabels",                        T.t(measurementlabels),
      "measurementNumbers",                       T.t(measurementnumbers),
      "messageStyleChime",                        T.t(messagestylechime),
      "minimizationRefresh",                      T.t(minimizationrefresh),
      "minimizationSilent",                       T.t(minimizationsilent),
      "modelkitMode",                             T.t(modelkitmode),
      "monitorEnergy",                            T.t(monitorenergy),
      "multipleBondRadiusFactor",                 T.t(multiplebondradiusfactor),
      "multipleBondSpacing",                      T.t(multiplebondspacing),
      "multiProcessor",                           T.t(multiprocessor),
      "navigateSurface",                          T.t(navigatesurface),
      "navigationMode",                           T.t(navigationmode),
      "navigationPeriodic",                       T.t(navigationperiodic),
      "partialDots",                              T.t(partialdots),
      "pdbAddHydrogens",                          T.t(pdbaddhydrogens),
      "pdbGetHeader",                             T.t(pdbgetheader),
      "pdbSequential",                            T.t(pdbsequential),
      "perspectiveDepth",                         T.t(perspectivedepth),
      "preserveState",                            T.t(preservestate),
      "rangeSelected",                            T.t(rangeselected),
      "redoMove",                                 T.t(redomove),
      "refreshing",                               T.t(refreshing),
      "ribbonBorder",                             T.t(ribbonborder),
      "rocketBarrels",                            T.t(rocketbarrels),
      "saveProteinStructureState",                T.t(saveproteinstructurestate),
      "scriptQueue",                              T.t(scriptqueue),
      "selectAllModels",                          T.t(selectallmodels),
      "selectHetero",                             T.t(selecthetero),
      "selectHydrogen",                           T.t(selecthydrogen),
// see commands     "selectionHalos",                           new Token(selectionhalo),
      "showAxes",                                 T.t(showaxes),
      "showBoundBox",                             T.t(showboundbox),
      "showBoundingBox",                          null,
      "showFrank",                                T.t(showfrank),
      "showHiddenSelectionHalos",                 T.t(showhiddenselectionhalos),
      "showHydrogens",                            T.t(showhydrogens),
      "showKeyStrokes",                           T.t(showkeystrokes),
      "showMeasurements",                         T.t(showmeasurements),
      "showMultipleBonds",                        T.t(showmultiplebonds),
      "showNavigationPointAlways",                T.t(shownavigationpointalways),
// see intparam      "showScript",                               new Token(showscript),
      "showTiming",                               T.t(showtiming),
      "showUnitcell",                             T.t(showunitcell),
      "slabByAtom",                               T.t(slabbyatom),
      "slabByMolecule",                           T.t(slabbymolecule),
      "slabEnabled",                              T.t(slabenabled),
      "smartAromatic",                            T.t(smartaromatic),
      "solvent",                                  T.t(solvent),
      "solventProbe",                             T.t(solventprobe),
// see intparam     "specular",                                 new Token(specular),
      "ssBondsBackbone",                          T.t(ssbondsbackbone),
      "statusReporting",                          T.t(statusreporting),
      "strutsMultiple",                           T.t(strutsmultiple),
      "syncMouse",                                T.t(syncmouse),
      "syncScript",                               T.t(syncscript),
      "testFlag1",                                T.t(testflag1),
      "testFlag2",                                T.t(testflag2),
      "testFlag3",                                T.t(testflag3),
      "testFlag4",                                T.t(testflag4),
      "traceAlpha",                               T.t(tracealpha),
      "twistedSheets",                            T.t(twistedsheets),
      "undo",                                     T.t(undo),
      "undoMove",                                 T.t(undomove),
      "useArcBall",                               T.t(usearcball),
      "useMinimizationThread",                    T.t(useminimizationthread),
      "useNumberLocalization",                    T.t(usenumberlocalization),
      "waitForMoveTo",                            T.t(waitformoveto),
      "windowCentered",                           T.t(windowcentered),
      "wireframeRotation",                        T.t(wireframerotation),
      "zeroBasedXyzRasmol",                       T.t(zerobasedxyzrasmol),
      "zoomEnabled",                              T.t(zoomenabled),
      "zoomHeight",                               T.t(zoomheight),
      "zoomLarge",                                T.t(zoomlarge),
      "zShade",                                   T.t(zshade),

    };

    T tokenLast = null;
    String stringThis;
    T tokenThis;
    String lcase;
    for (int i = 0; i + 1 < arrayPairs.length; i += 2) {
      stringThis = (String) arrayPairs[i];
      lcase = stringThis.toLowerCase();
      tokenThis = (T) arrayPairs[i + 1];
      if (tokenThis == null)
        tokenThis = tokenLast;
      if (tokenThis.value == null)
        tokenThis.value = stringThis;
      if (tokenMap.get(lcase) != null)
        Logger.error("duplicate token definition:" + lcase);
      tokenMap.put(lcase, tokenThis);
      tokenLast = tokenThis;
    }
    arrayPairs = null;
    //Logger.info(arrayPairs.length + " script command tokens");
  }

  public static int getParamType(int tok) {
    if (!tokAttr(tok, setparam))
      return nada;
    return tok & paramTypes;
  }
  
  public static void getTokensType(Map<String, Object> map, int attr) {
    for (Entry<String, T> e: tokenMap.entrySet()) {
      T t = e.getValue();
      if (tokAttr(t.tok, attr))
        map.put(e.getKey(), e.getValue());
    }
  }

  /**
   * commands that allow implicit ID as first parameter
   * 
   * @param cmdtok
   * @return true or false 
   */
  public static boolean isIDcmd(int cmdtok) {
    switch (cmdtok) {
    case T.isosurface:
    case T.draw:
    case T.cgo:
    case T.pmesh:
    case T.contact:
      return true;
    default:
      return false;
    }
  }

}
