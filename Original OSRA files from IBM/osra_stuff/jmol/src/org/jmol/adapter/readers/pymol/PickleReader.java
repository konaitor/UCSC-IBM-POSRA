package org.jmol.adapter.readers.pymol;

import javajs.util.List;
import javajs.util.SB;

import org.jmol.util.Logger;

import java.util.Hashtable;

import java.util.Map;

import org.jmol.api.JmolDocument;
import org.jmol.viewer.Viewer;

/**
 * generic Python Pickle file reader
 * only utilizing records needed for PyMOL.
 * 
 * It appears we must read integers littleEndian and doubles bigEndian.
 * 
 * 2013.04.06 -- added memo functions. PyMOL pickling is using LONG_BINPUT way too often. 
 * This results in a huge unnecessary memory overhead. My only solution is to only
 * cache Strings in memo, and then only selectively -- not parts of movie; not when 
 * markCount > 5 (residues). This seems to work, but it is still way overkill, since each 
 * atom generates several items. My only   
 * 
 * see http://www.picklingtools.com/
 * 
 * @author Bob Hanson hansonr@stolaf.edu
 * 
 */
class PickleReader {

  private Viewer viewer;
  private JmolDocument binaryDoc;
  private List<Object> stack = new List<Object>();
  private List<Integer> marks = new List<Integer>();
  private List<Object> build = new List<Object>();

  private Map<Integer, Object> memo = new Hashtable<Integer, Object>();
  
  private boolean logging;
  private int id;
  private int markCount;
  private int filePt;
  private int emptyListPt;
  private Object thisSection;
  private boolean inMovie;
  private boolean inNames;
  private String thisName;
  private int lastMark;
  private int retrieveCount;

  private final static byte APPEND = 97; /* a */
  private final static byte APPENDS = 101; /* e */
  private final static byte BINFLOAT = 71; /* G */
  private final static byte BININT = 74; /* J */
  private final static byte BININT1 = 75; /* K */
  private final static byte BININT2 = 77; /* M */
  private final static byte BINPUT = 113; /* q */
  private final static byte BINSTRING = 84; /* T */
  private final static byte BINUNICODE = 87; /* X */
  private final static byte BUILD = 98; /* b */
  private final static byte EMPTY_DICT = 125; /* } */
  private final static byte EMPTY_LIST = 93; /* ] */
  private final static byte GLOBAL = 99; /* c */
  private final static byte LONG_BINPUT = 114; /* r */
  private final static byte MARK = 40; /* ( */
  private final static byte NONE = 78; /* N */
  private final static byte OBJ = 111; /* o */
  private final static byte SETITEM = 115; /* s */
  private final static byte SETITEMS = 117; /* u */
  private final static byte SHORT_BINSTRING = 85; /* U */
  private final static byte STOP = 46; /* . */
  private final static byte BINGET = 104; /* h */
  private final static byte LONG_BINGET = 106; /* j */
  private final static byte TUPLE = 116; /* t */
  private final static byte INT = 73; /* I */


//  private final static byte BINPERSID = 81; /* Q */
//  private final static byte DICT = 100; /* d */
//  private final static byte DUP = 50; /* 2 */
//  private final static byte EMPTY_TUPLE = 41; /* ) */
//  private final static byte FLOAT = 70; /* F */
//  private final static byte GET = 103; /* g */
//  private final static byte INST = 105; /* i */
//  private final static byte LIST = 108; /* l */
//  private final static byte LONG = 76; /* L */
//  private final static byte PERSID = 80; /* P */
//  private final static byte POP = 48; /* 0 */
//  private final static byte POP_MARK = 49; /* 1 */
//  private final static byte PUT = 112; /* p */
//  private final static byte REDUCE = 82; /* R */
//  private final static byte STRING = 83; /* S */
//  private final static byte UNICODE = 86; /* V */

  PickleReader(JmolDocument doc, Viewer viewer) {
    binaryDoc = doc;
    this.viewer = viewer;
  }

  private void log(String s) {
    viewer.log(s + "\0");
  }
  
  @SuppressWarnings("unchecked")
  Map<String, Object> getMap(boolean logging) throws Exception {
    this.logging = logging;
    String s;
    byte b;
    int i, mark;
    double d;
    Object o;
    byte[] a;
    Map<String, Object> map;
    List<Object> l;
    boolean going = true;

    while (going) {
      b = binaryDoc.readByte();
      //if (logging)
        //log(" " + b);
      switch (b) {
      case EMPTY_DICT: //}
        push(new Hashtable<String, Object>());
        break;
      case APPEND:
        o = pop();
        ((List<Object>) peek()).addLast(o);
        break;
      case APPENDS:
        l = getObjects(getMark());
        if (inNames && markCount == 2){// && l.size() > 0 && l.get(0) == thisName) {
          int pt = (int) binaryDoc.getPosition();
          System.out.println(" " + thisName + " " + filePt + " " + (pt - filePt));
          List<Object> l2 = new List<Object>();
          l2.addLast(Integer.valueOf(filePt));
          l2.addLast(Integer.valueOf(pt - filePt));
          l.addLast(l2); // [ptr to start of this PyMOL object, length in bytes ] 
        }
        ((List<Object>) peek()).addAll(l);
        break;
      case BINFLOAT:
        d = binaryDoc.readDouble();
        push(Double.valueOf(d));
        break;
      case BININT:
        i = binaryDoc.readIntLE();
        push(Integer.valueOf(i));
        break;
      case BININT1:
        i = binaryDoc.readByte() & 0xff;
        push(Integer.valueOf(i));
        break;
      case BININT2:
        i = (binaryDoc.readByte() & 0xff | ((binaryDoc.readByte() & 0xff) << 8)) & 0xffff;
        push(Integer.valueOf(i));
        break;
      case BINPUT:
        i = binaryDoc.readByte();
        putMemo(i, false);
        break;
      case LONG_BINPUT:
        i = binaryDoc.readIntLE();
        putMemo(i, true);
        break;
      case BINGET:
        i = binaryDoc.readByte();
        o = getMemo(i);
        push(o == null ? "BINGET" + (++id) : o);
        break;
      case LONG_BINGET:
        i = binaryDoc.readIntLE();
        o = getMemo(i);
        if (o == null) {
          Logger.error("did not find memo item for " + i);
          push("LONG_BINGET" + (++id));
        } else {
          push(o);
        }
        
        break;
      case SHORT_BINSTRING:
        i = binaryDoc.readByte() & 0xff;
        a = new byte[i];
        binaryDoc.readByteArray(a, 0, i);
        s = new String(a, "UTF-8");
        if (inNames && markCount == 3 && lastMark == stack.size()) {
          thisName = s;
          filePt = emptyListPt;
        }
        push(s);
        break;
      case BINSTRING:
        i = binaryDoc.readIntLE();
        a = new byte[i];
        binaryDoc.readByteArray(a, 0, i);
        s = new String(a, "UTF-8");
        push(s);
        break;
      case BINUNICODE:
        i = binaryDoc.readIntLE();
        a = new byte[i];
        binaryDoc.readByteArray(a, 0, i);
        s = new String(a, "UTF-8");
        push(s);
        break;
      case EMPTY_LIST:
        emptyListPt = (int) binaryDoc.getPosition() - 1;
        push(new  List<Object>());
        break;
      case GLOBAL:
        l = new List<Object>();
        l.addLast("global");
        l.addLast(readString());
        l.addLast(readString());
        push(l);
        break;
      case BUILD:
        o = pop();
        build.addLast(o);
        break;
      case MARK:
        putMark(stack.size());
        break;
      case NONE:
        push(null);
        break;
      case OBJ:
        push(getObjects(getMark()));
        break;
      case SETITEM:
        o = pop();
        if (!(peek() instanceof String))
          Logger.error(peek() + " is not a string");
        s = (String) pop();
        ((Map<String, Object>) peek()).put(s, o);
        break;
      case SETITEMS:
        mark = getMark();
        l = getObjects(mark);
        o = peek();
        if (o instanceof List) {
          for (i = 0; i < l.size(); i++)
            ((List<Object>) o).addLast(l.get(i));
        } else {
        map = (Map<String, Object>) o;
        for (i = l.size(); --i >= 0;) {
          o = l.get(i);
          s = (String) l.get(--i);
          map.put(s, o);
        }
        }
        break;
      case STOP:
        going = false;
        break;
      case TUPLE:
        // used for view_dict
        push(getObjects(getMark()));
        break;
      case INT:
        /// 0x88000000 for instance
        s = readString();
        try {
          push(Integer.valueOf(Integer.parseInt(s)));
        } catch (Exception e) {
          long ll = Long.parseLong(s);
          push(Integer.valueOf((int) (ll & 0xFFFFFFFF)));
          //System.out.println("INT too large: " + s + " @ " + binaryDoc.getPosition());
          //push(Integer.valueOf(Integer.MAX_VALUE));
        }
        break;
      default:

        // not used?
        Logger.error("Pickle reader error: " + b + " "
            + binaryDoc.getPosition());

        //        switch (b) {
        //        case BINPERSID:
        //          s = (String) pop();
        //          push(new Object[] { "persid", s }); // for now
        //          break;
        //        case DICT:
        //          map = new Hashtable<String, Object>();
        //          mark = getMark();
        //          for (i = list.size(); i >= mark;) {
        //            o = list.remove(--i);
        //            s = (String) list.remove(--i);
        //            map.put(s, o);
        //          }
        //          push(map);
        //          break;
        //        case DUP:
        //          push(peek());
        //          break;
        //        case EMPTY_TUPLE:
        //          push(new Point3f());
        //          break;
        //        case FLOAT:
        //          s = readString();
        //          push(Double.valueOf(s));
        //          break;
        //        case GET:
        //          s = readString();
        //          o = temp.remove(s);
        //          push(o);
        //          break;
        //        case INST:
        //          l = getObjects(getMark());
        //          module = readString();
        //          name = readString();
        //          push(new Object[] { "inst", module, name, l });
        //          break;
        //        case LIST:
        //          push(getObjects(getMark()));
        //          break;
        //        case LONG:
        //          i = (int) binaryDoc.readLong();
        //          push(Long.valueOf(i));
        //          break;
        //        case PERSID:
        //          s = readString();
        //          push(new Object[] { "persid", s });
        //          break;
        //        case POP:
        //          pop();
        //          break;
        //        case POP_MARK:
        //          getObjects(getMark());
        //          break;
        //        case PUT:
        //          s = readString();
        //          temp.put(s, peek());
        //          break;
        //        case REDUCE:
        //          push(new Object[] { "reduce", pop(), pop() });
        //          break;
        //        case STRING:
        //          s = readString();
        //          push(Escape.unescapeUnicode(s));
        //          break;
        //        case UNICODE:
        //          a = readLineBytes();
        //          s = new String(a, "UTF-8");
        //          push(s);
        //          break;
        //        }
      }
    }
    if (logging)
      log("");
    Logger.info("PyMOL Pickle reader cached " + memo.size() + " tokens; retrieved " + retrieveCount);
    memo = null;
    map = (Map<String, Object>) stack.remove(0);
    if (map.size() == 0)
      for (i = stack.size(); --i >= 0;) {
        o = stack.get(i--);
        s = (String) stack.get(i);
        map.put(s, o);
      }
    return map;
  }
  
  private void putMemo(int i, boolean doCheck) {
    Object o = peek();
    if (o instanceof String) {
      if (doCheck && markCount >= 6 || markCount == 3 && inMovie)
        return;
      memo.put(Integer.valueOf(i), o);
      //System.out.println("caching string " + o + " at " + binaryDoc.getPosition());
    }
  }

  private Object getMemo(int i) {
    Object o = memo.get(Integer.valueOf(i));
    if (o == null)
      return o;
    System.out.println("retrieving string " + o + " at " + binaryDoc.getPosition());
    retrieveCount++;
    return o;
  }

  private List<Object> getObjects(int mark) {
    int n = stack.size() - mark;
    List<Object> args = new  List<Object>();
    for (int j = 0; j < n; j++)
      args.addLast(null);
    for (int j = n, i = stack.size(); --i >= mark;)
      args.set(--j, stack.remove(i));
    return args;
  }

  //  private byte[] readLineBytes() throws Exception {
  //    String s = readString();
  //    return s.getBytes();
  //  }

  private String readString() throws Exception {
    SB sb = new SB();
    while (true) {
      byte b = binaryDoc.readByte();
      if (b == 0xA)
        break;
      sb.appendC((char) b);
    }
    return sb.toString();
  }

  private void putMark(int i) {
    if (logging)
      log("\n " + Integer.toHexString((int) binaryDoc.getPosition()) + " [");
    marks.addLast(Integer.valueOf(lastMark = i));
    markCount++;
    switch (markCount) {
    case 2:
      thisSection = stack.get(i - 2);
      inMovie = "movie".equals(thisSection);
      inNames = "names".equals(thisSection);
      break;
    default:
      break;
    }
  }

  private int getMark() {
    return marks.remove(--markCount).intValue();
  }

  private void push(Object o) {
    if (logging
        && (o instanceof String || o instanceof Double || o instanceof Integer))
      log((o instanceof String ? "'" + o + "'" : o) + ", ");
    stack.addLast(o);
  }

  private Object peek() {
    return stack.get(stack.size() - 1);
  }

  private Object pop() {
    return stack.remove(stack.size() - 1);
  }

}
