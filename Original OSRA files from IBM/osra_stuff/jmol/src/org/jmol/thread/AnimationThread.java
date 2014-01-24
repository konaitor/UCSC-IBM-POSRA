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

package org.jmol.thread;

import org.jmol.util.Logger;
import org.jmol.viewer.Viewer;
import org.jmol.viewer.AnimationManager;

public class AnimationThread extends JmolThread {
  /**
   * 
   */
  private AnimationManager animationManager;
  private int framePointer1;
  private int framePointer2;
  private int intThread;
  private boolean isFirst;
  
  public AnimationThread() {}
  
  @Override
  public int setManager(Object manager, Viewer viewer, Object params) {
    int[] options = (int[]) params;
    framePointer1 = options[0];
    framePointer2 = options[1];
    intThread = options[2];
    animationManager = (AnimationManager) manager;
    setViewer(viewer, "AnimationThread");
    viewer.startHoverWatcher(false);
    return 0;
  }

  @Override
  public void interrupt() {
    if (stopped)
      return;
    stopped = true;
    if (Logger.debugging)
      Logger.debug("animation thread interrupted!");
    try {
      animationManager.setAnimationOn(false);
    } catch (Exception e) {
      // null pointer -- don't care;
    }
    super.interrupt();
  }
  
  @Override
  protected void run1(int mode) throws InterruptedException {
    while (true) {
      //System.out.println("AnimationThread " + mode  + " "  + this + " " + sleepTime);
      switch (mode) {
      case INIT:
        if (Logger.debugging)
          Logger.debug("animation thread " + intThread + " running");
        viewer.requestRepaintAndWait("animationThread");
        viewer.startHoverWatcher(false);
        isFirst = true;
        mode = MAIN;
        break;
      case MAIN:
        //System.out.println("anim thred " + animationManager.getCurrentFrame() +" "+ framePointer);
        if (checkInterrupted() || !animationManager.animationOn) {
          mode = FINISH;
          break;
        }
        if (animationManager.currentFrameIs(framePointer1)) {
          targetTime += animationManager.firstFrameDelayMs;
          sleepTime = (int) (targetTime - (System.currentTimeMillis() - startTime));
          if (!runSleep(sleepTime, CHECK1))
            return;
        }
        mode = CHECK1;
        break;
      case CHECK1:
        if (animationManager.currentFrameIs(framePointer2)) {
          targetTime += animationManager.lastFrameDelayMs;
          sleepTime = (int) (targetTime - (System.currentTimeMillis() - startTime));
          if (!runSleep(sleepTime, CHECK2))
            return;
        }
        mode = CHECK2;
        break;
      case CHECK2:
        if (!isFirst
            && animationManager.currentIsLast()
            && !animationManager.setAnimationNext()) {
          mode = FINISH;
          break;
        }
        isFirst = false;
        targetTime += (int) ((1000f / animationManager.animationFps) + viewer
            .getFrameDelayMs(animationManager.getCurrentModelIndex()));
        mode = CHECK3;
        break;
      case CHECK3:
        while (animationManager.animationOn && !checkInterrupted()
            && !viewer.getRefreshing()) {
          if (!runSleep(10, CHECK3))
            return;
        }
        if (!viewer.getSpinOn())
          viewer.refresh(1, "animationThread");
        sleepTime = (int) (targetTime - (System.currentTimeMillis() - startTime));
        if (!runSleep(sleepTime, MAIN))
          return;
        mode = MAIN;
        break;
      case FINISH:
        if (Logger.debugging)
          Logger.debug("animation thread " + intThread + " exiting");
        animationManager.stopThread(false);
        return;
      }
    }
  }

}