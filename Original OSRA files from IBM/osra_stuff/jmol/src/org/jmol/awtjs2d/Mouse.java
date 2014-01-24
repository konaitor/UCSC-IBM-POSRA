/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2012-09-11 19:29:26 -0500 (Tue, 11 Sep 2012) $
 * $Revision: 17556 $
 *
 * Copyright (C) 2002-2005  The Jmol Development Team
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
 *  Lesser General License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.awtjs2d;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import org.jmol.script.T;
import org.jmol.util.Escape;
import org.jmol.util.Logger;

import javajs.J2SRequireImport;
import javajs.api.EventManager;
import javajs.api.GenericMouseInterface;
import javajs.api.PlatformViewer;
import javajs.awt.event.Event;
import javajs.util.V3;
import org.jmol.viewer.Viewer;

/**
 * JavaScript interface from JmolJSmol.js via handleOldJvm10Event (for now)
 * 
 * J2SRequireImport is needed because we want to allow JavaScript access to java.awt.Event constant names
 * 
 */

@J2SRequireImport({javajs.awt.event.Event.class})
public class Mouse implements GenericMouseInterface {

  private Viewer viewer;
  private EventManager manager;
  //private double privateKey;

  /**
   * @param privateKey -- not used in JavaScript  
   * @param viewer 
   * @param display 
   */
  public Mouse(double privateKey, PlatformViewer viewer, Object display) {
    //this.privateKey = privateKey; could be used for clipboard access
    this.viewer = (Viewer) viewer;
    this.manager = this.viewer.getActionManager();
  }

  @Override
  public void clear() {
    // nothing to do here now -- see ActionManager
  }

  @Override
  public void dispose() {
    // nothing to do here
  }

  @Override
  public boolean processEvent(int id, int x, int y, int modifiers, long time) {
    if (id != -1)
      modifiers = applyLeftMouse(modifiers);
    switch (id) {
    case -1: // JavaScript
      wheeled(time, x, modifiers);
      break;
    case Event.MOUSE_DOWN:
      xWhenPressed = x;
      yWhenPressed = y;
      modifiersWhenPressed10 = modifiers;
      pressed(time, x, y, modifiers, false);
      break;
    case Event.MOUSE_DRAG:
      dragged(time, x, y, modifiers);
      break;
    case Event.MOUSE_ENTER:
      entered(time, x, y);
      break;
    case Event.MOUSE_EXIT:
      exited(time, x, y);
      break;
    case Event.MOUSE_MOVE:
      moved(time, x, y, modifiers);
      break;
    case Event.MOUSE_UP:
      released(time, x, y, modifiers);
      // simulate a mouseClicked event for us
      if (x == xWhenPressed && y == yWhenPressed
          && modifiers == modifiersWhenPressed10) {
        // the underlying code will turn this into dbl clicks for us
        clicked(time, x, y, modifiers, 1);
      }
      break;
    default:
      return false;
    }
    return true;
  }

  /**
   * 
   * called by JSmol as processTwoPointGesture(canvas.touches);
   * 
   * @param touches
   *     [[finger1 touches],[finger2 touches]]
   *     where finger touches are [[x0,y0],[x1,y1],[x2,y2],...] 
   *    
   */
  @Override
  public void processTwoPointGesture(float[][][] touches) {
    
    if (touches[0].length < 2)
      return;
    float[][] t1 = touches[0];
    float[][] t2 = touches[1];
    float[] t1first = t1[0];
    float[] t1last = t1[t2.length - 1];
    float x1first = t1first[0];
    float x1last = t1last[0]; 
    float dx1 = x1last - x1first;
    float y1first = t1first[1];
    float y1last = t1last[1]; 
    float dy1 = y1last - y1first;
    V3 v1 = V3.new3(dx1, dy1, 0);
    float d1 = v1.length();
    float[] t2first = t2[0];
    float[] t2last = t2[t2.length - 1];
    float x2first = t2first[0];
    float x2last = t2last[0]; 
    float dx2 = x2last - x2first;
    float y2first = t2first[1];
    float y2last = t2last[1]; 
    float dy2 = y2last - y2first;
    V3 v2 = V3.new3(dx2, dy2, 0);    
    float d2 = v2.length();
    // rooted finger --> zoom (at this position, perhaps?)
    if (d1 < 1 || d2 < 1)
      return;
    v1.normalize();
    v2.normalize();
    float cos12 = (v1.dot(v2));
    // cos12 > 0.8 (same direction) will be required to indicate drag
    // cos12 < -0.8 (opposite directions) will be required to indicate zoom or rotate
    if (cos12 > 0.8) {
      // two co-aligned motions -- translate
      // just use finger 1, last move
      int deltaX = (int) (x1last - t1[t1.length - 2][0]); 
      int deltaY = (int) (y1last - t1[t1.length - 2][1]); 
      viewer.translateXYBy(deltaX, deltaY);
    } else if (cos12 < -0.8) {
      // two classic zoom motions -- zoom
      v1 = V3.new3(x2first - x1first, y2first - y1first, 0);
      v2 = V3.new3(x2last - x1last, y2last - y1last, 0);
      float dx = v2.length() - v1.length();
      wheeled(System.currentTimeMillis(), dx < 0 ? -1 : 1, Event.MOUSE_WHEEL);
    }
  }
  
  public void mouseClicked(MouseEvent e) {
    clicked(e.getWhen(), e.getX(), e.getY(), e.getModifiers(), e
        .getClickCount());
  }

  public void mouseEntered(MouseEvent e) {
    entered(e.getWhen(), e.getX(), e.getY());
  }

  public void mouseExited(MouseEvent e) {
    exited(e.getWhen(), e.getX(), e.getY());
  }

  public void mousePressed(MouseEvent e) {
    pressed(e.getWhen(), e.getX(), e.getY(), e.getModifiers(), e
        .isPopupTrigger());
  }

  public void mouseReleased(MouseEvent e) {
    released(e.getWhen(), e.getX(), e.getY(), e.getModifiers());
  }

  public void mouseDragged(MouseEvent e) {
    int modifiers = e.getModifiers();
    /****************************************************************
     * Netscape 4.* Win32 has a problem with mouseDragged if you left-drag then
     * none of the modifiers are selected we will try to fix that here
     ****************************************************************/
    if ((modifiers & Event.BUTTON_MASK) == 0)
      modifiers |= Event.MOUSE_LEFT;
    /****************************************************************/
    dragged(e.getWhen(), e.getX(), e.getY(), modifiers);
  }

  public void mouseMoved(MouseEvent e) {
    moved(e.getWhen(), e.getX(), e.getY(), e.getModifiers());
  }

  public void mouseWheelMoved(MouseWheelEvent e) {
    e.consume();
    wheeled(e.getWhen(), e.getWheelRotation(), e.getModifiers());
  }

  public void keyTyped(KeyEvent ke) {
    ke.consume();
    if (!viewer.menuEnabled())
      return;
    char ch = ke.getKeyChar();
    int modifiers = ke.getModifiers();
    // for whatever reason, CTRL may also drop the 6- and 7-bits,
    // so we are in the ASCII non-printable region 1-31
    if (Logger.debuggingHigh)
      Logger.debug("MouseManager keyTyped: " + ch + " " + (0+ch) + " " + modifiers);
    if (modifiers != 0 && modifiers != Event.SHIFT_MASK) {
      switch (ch) {
      case (char) 11:
      case 'k': // keystrokes on/off
        boolean isON = !viewer.getBooleanProperty("allowKeyStrokes");
        switch (modifiers) {
        case Event.CTRL_MASK:
          viewer.setBooleanProperty("allowKeyStrokes", isON);
          viewer.setBooleanProperty("showKeyStrokes", true);
          break;
        case Event.CTRL_ALT:
        case Event.SHIFT_MASK:
          viewer.setBooleanProperty("allowKeyStrokes", isON);
          viewer.setBooleanProperty("showKeyStrokes", false);
          break;
        }
        clearKeyBuffer();
        viewer.refresh(3, "showkey");
        break;
      case 22:
      case 'v': // paste
        switch (modifiers) {
        case Event.CTRL_MASK:
        	break;
        }
        break;
      case 26:
      case 'z': // undo
        switch (modifiers) {
        case Event.CTRL_MASK:
          viewer.undoMoveAction(T.undomove, 1);
          break;
        case Event.CTRL_SHIFT:
          viewer.undoMoveAction(T.redomove, 1);
          break;
        }
        break;
      case 25:
      case 'y': // redo
        switch (modifiers) {
        case Event.CTRL_MASK:
          viewer.undoMoveAction(T.redomove, 1);
          break;
        }
        break;        
      }
      return;
    }
    if (!viewer.getBooleanProperty("allowKeyStrokes"))
      return;
    addKeyBuffer(ke.getModifiers() == Event.SHIFT_MASK ? Character.toUpperCase(ch) : ch);
  }

  public void keyPressed(KeyEvent ke) {
    if (viewer.isApplet())
      ke.consume();
    manager.keyPressed(ke.getKeyCode(), ke.getModifiers());
  }

  public void keyReleased(KeyEvent ke) {
    ke.consume();
    manager.keyReleased(ke.getKeyCode());
  }

  private String keyBuffer = "";

  private void clearKeyBuffer() {
    if (keyBuffer.length() == 0)
      return;
    keyBuffer = "";
    if (viewer.getBooleanProperty("showKeyStrokes"))
      viewer
          .evalStringQuietSync("!set echo _KEYSTROKES; set echo bottom left;echo \"\"", true, true);
  }

  private void addKeyBuffer(char ch) {
    if (ch == 10) {
      sendKeyBuffer();
      return;
    }
    if (ch == 8) {
      if (keyBuffer.length() > 0)
        keyBuffer = keyBuffer.substring(0, keyBuffer.length() - 1);
    } else {
      keyBuffer += ch;
    }
    if (viewer.getBooleanProperty("showKeyStrokes"))
      viewer
          .evalStringQuietSync("!set echo _KEYSTROKES; set echo bottom left;echo "
              + Escape.eS("\1" + keyBuffer), true, true);
  }

  private void sendKeyBuffer() {
    String kb = keyBuffer;
    if (viewer.getBooleanProperty("showKeyStrokes"))
      viewer
          .evalStringQuietSync("!set echo _KEYSTROKES; set echo bottom left;echo "
              + Escape.eS(keyBuffer), true, true);
    clearKeyBuffer();
    viewer.evalStringQuietSync(kb, false, true);
  }

  private void entered(long time, int x, int y) {
    wheeling = false;
    manager.mouseEnterExit(time, x, y, false);
  }

  private void exited(long time, int x, int y) {
    wheeling = false;
    manager.mouseEnterExit(time, x, y, true);
  }
  /**
   * 
   * @param time
   * @param x
   * @param y
   * @param modifiers
   * @param clickCount
   */
  private void clicked(long time, int x, int y, int modifiers, int clickCount) {
    clearKeyBuffer();
    // clickedCount is not reliable on some platforms
    // so we will just deal with it ourselves
    manager.mouseAction(Event.CLICKED, time, x, y, 1, modifiers);
  }

  private boolean isMouseDown; // Macintosh may not recognize CTRL-SHIFT-LEFT as drag, only move
  private boolean wheeling;
  
  private void moved(long time, int x, int y, int modifiers) {
    clearKeyBuffer();
    if (isMouseDown)
      manager.mouseAction(Event.DRAGGED, time, x, y, 0, applyLeftMouse(modifiers));
    else
      manager.mouseAction(Event.MOVED, time, x, y, 0, modifiers);
  }

  private void wheeled(long time, int rotation, int modifiers) {
    clearKeyBuffer();
    wheeling = true;
    manager.mouseAction(Event.WHEELED, time, 0, rotation, 0, 
        modifiers & ~Event.BUTTON_MASK | Event.MOUSE_WHEEL);
  }

  /**
   * 
   * @param time
   * @param x
   * @param y
   * @param modifiers
   * @param isPopupTrigger
   */
  private void pressed(long time, int x, int y, int modifiers,
                    boolean isPopupTrigger) {
    clearKeyBuffer();
    isMouseDown = true;
    wheeling = false;
    manager.mouseAction(Event.PRESSED, time, x, y, 0, modifiers);
  }

  private void released(long time, int x, int y, int modifiers) {
    isMouseDown = false;
    wheeling = false;
    manager.mouseAction(Event.RELEASED, time, x, y, 0, modifiers);
  }

  private void dragged(long time, int x, int y, int modifiers) {
    if (wheeling)
      return;
    if ((modifiers & Event.MAC_COMMAND) == Event.MAC_COMMAND)
      modifiers = modifiers & ~Event.MOUSE_RIGHT | Event.CTRL_MASK; 
    manager.mouseAction(Event.DRAGGED, time, x, y, 0, modifiers);
  }

  private static int applyLeftMouse(int modifiers) {
    // if neither BUTTON2 or BUTTON3 then it must be BUTTON1
    return ((modifiers & Event.BUTTON_MASK) == 0) ? (modifiers | Event.MOUSE_LEFT)
        : modifiers;
  }

  private int xWhenPressed, yWhenPressed, modifiersWhenPressed10;

}
