/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2011  The Jmol Development Team
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 *  02110-1301, USA.
 */

package unused;

//import javajs.util.P3;
//
//import org.jmol.util.Logger;
//import org.jmol.viewer.AnimationManager;
//import org.jmol.viewer.Viewer;

public class ModulationThread {//extends JmolThread {
//  /**
//   * 
//   */
//  private P3 modT;
//  private int modT2;
//
//  private AnimationManager animationManager;
//  
//
//  public ModulationThread() {
//  }
//
//  @Override
//  public int setManager(Object manager, Viewer viewer, Object params) {
//    int[] options = (int[]) params;
//    int t = options[0];
//    modT = P3.new3(t, t, t);
//    modT2 = options[1];
//    animationManager = (AnimationManager) manager;
//    setViewer(viewer, "ModulationThread");
//    viewer.startHoverWatcher(false);      
//    return 0;
//  }
//  
//  @Override
//  public void interrupt() {
//    if (stopped)
//      return;
//    stopped = true;
//    if (Logger.debugging)
//      Logger.debug("animation thread interrupted!");
//    animationManager.modulationPlay = false;
//    try {
//    } catch (Exception e) {
//      // null pointer -- don't care;
//    }
//    super.interrupt();
//  }
//  
//  @Override
//  protected void run1(int mode) throws InterruptedException {
//    while (true) {
//      //System.out.println("AnimationThread " + mode  + " "  + this + " " + sleepTime);
//      switch (mode) {
//      case INIT:
//        if (Logger.debugging)
//          Logger.debug("modulation thread running");
//        viewer.requestRepaintAndWait("modulationThread");
//        viewer.startHoverWatcher(false);
//        mode = MAIN;
//        break;
//      case MAIN:
//        //System.out.println("anim thred " + animationManager.getCurrentFrame() +" "+ framePointer);
//        if (checkInterrupted() || !animationManager.modulationPlay || modT.x > modT2) {
//          mode = FINISH;
//          break;
//        }
//        mode = CHECK1;
//        break;
//      case CHECK1:
//        modT.x++;
//        viewer.setModulation(true, modT, true, Integer.MAX_VALUE, true);
//        mode = CHECK2;
//        break;
//      case CHECK2:
//        targetTime += (int) (1000f / animationManager.modulationFps);
//        mode = CHECK3;
//        break;
//      case CHECK3:
//        while (animationManager.modulationPlay && !checkInterrupted()
//            && !viewer.getRefreshing()) {
//          if (!runSleep(10, CHECK3))
//            return;
//        }
//        viewer.refresh(1, "modulationThread");
//        sleepTime = (int) (targetTime - (System.currentTimeMillis() - startTime));
//        if (!runSleep(sleepTime, MAIN))
//          return;
//        mode = MAIN;
//        break;
//      case FINISH:
//        if (Logger.debugging)
//          Logger.debug("modulation thread exiting");
//        animationManager.stopModulationThread();
//        return;
//      }
//    }
//  }

}