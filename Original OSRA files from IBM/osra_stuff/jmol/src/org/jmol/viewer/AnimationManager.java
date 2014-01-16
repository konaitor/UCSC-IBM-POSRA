/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2009-06-26 10:56:39 -0500 (Fri, 26 Jun 2009) $
 * $Revision: 11127 $
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
 *  Lesser General License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.viewer;


import java.util.Map;

import org.jmol.thread.JmolThread;
import org.jmol.util.BSUtil;
//import javajs.util.List;

import org.jmol.api.Interface;
import org.jmol.constant.EnumAnimationMode;
import org.jmol.java.BS;
import org.jmol.modelset.ModelSet;

public class AnimationManager {

  private JmolThread animationThread;
  public Viewer viewer;
  
  AnimationManager(Viewer viewer) {
    this.viewer = viewer;
  }

  // used by AnimationThread, Viewer, or StateCreator:
  
  public boolean animationOn;
  public int animationFps;  // set in stateManager
  public int firstFrameDelayMs;
  public int lastFrameDelayMs;

  public void setAnimationOn(boolean animationOn) {
    if (animationOn == this.animationOn)
      return;
    
    if (!animationOn || !viewer.haveModelSet() || viewer.isHeadless()) {
      stopThread(false);
      return;
    }
    if (!viewer.getSpinOn())
      viewer.refresh(3, "Anim:setAnimationOn");
    setAnimationRange(-1, -1);
    resumeAnimation();
  }

  public void stopThread(boolean isPaused) {
    boolean stopped = false;
    if (animationThread != null) {
      animationThread.interrupt();
      animationThread = null;
      stopped = true;
    }
    animationPaused = isPaused;
    if (stopped && !viewer.getSpinOn())
      viewer.refresh(3, "Viewer:setAnimationOff");
    animation(false);
    //stopModulationThread();
    viewer.setStatusFrameChanged(false, true);
    
  }

  public boolean setAnimationNext() {
    return setAnimationRelative(animationDirection);
  }

  public int getCurrentModelIndex() {
    return currentModelIndex;
  }
  
  public boolean currentIsLast() {
    return (isMovie ? lastFramePainted == currentAnimationFrame
        : lastModelPainted == currentModelIndex);
  }

  public boolean currentFrameIs(int f) {
    int i = getCurrentModelIndex();
    return (morphCount == 0 ? i == f : Math.abs(currentMorphModel - f) < 0.001f);
  }

  // required by Viewer or stateCreator
  
  // used by StateCreator or Viewer:
  
  final static int FRAME_FIRST = -1;
  final static int FRAME_LAST = 1;
  final static int MODEL_CURRENT = 0;

  final BS bsVisibleModels = new BS();

  EnumAnimationMode animationReplayMode = EnumAnimationMode.ONCE;

  BS bsDisplay;

  int[] animationFrames;

  boolean isMovie;
  boolean animationPaused;
  
  int currentModelIndex;
  int currentAnimationFrame;
  int morphCount;
  int animationDirection = 1;
  int currentDirection = 1;
  int firstFrameIndex;
  int lastFrameIndex;
  int frameStep;
  int backgroundModelIndex = -1;
  
  float currentMorphModel;
  float firstFrameDelay;
  float lastFrameDelay = 1;
  
  void clear() {
    setMovie(null);
    initializePointers(0);
    setAnimationOn(false);
    setModel(0, true);
    currentDirection = 1;
    currentAtomIndex = -1;
    setAnimationDirection(1);
    setAnimationFps(10);
    setAnimationReplayMode(EnumAnimationMode.ONCE, 0, 0);
    initializePointers(0);
  }
  
  String getModelSpecial(int i) {
    switch (i) {
    case FRAME_FIRST:
      i = firstFrameIndex;
      break;
    case MODEL_CURRENT:
      if (morphCount > 0)
        return "-" + (1 + currentMorphModel);
      i = getCurrentModelIndex();
      break;
    case FRAME_LAST:
      i = lastFrameIndex;
      break;
    }
    return viewer.getModelNumberDotted(i);
  }

  void setDisplay(BS bs) {
    bsDisplay = (bs == null || bs.cardinality() == 0? null : BSUtil.copy(bs));
  }

  void setMorphCount(int n) {
    morphCount = (isMovie ? 0 : n); // for now -- no morphing in movies
  }

  void morph(float modelIndex) {
    int m = (int) modelIndex;
    if (Math.abs(m - modelIndex) < 0.001f)
      modelIndex = m;
    else if (Math.abs(m - modelIndex) > 0.999f)
      modelIndex = m = m + 1;
    float f = modelIndex - m;
    m -= 1;
    if (f == 0) {
      currentMorphModel = m;
      setModel(m, true);
      return;
    }
    int m1;
    setModel(m, true);
    m1 = m + 1;
    currentMorphModel = m + f;
    if (m1 == m || m1 < 0 || m < 0)
      return;
    viewer.modelSet.morphTrajectories(m, m1, f);
  }  

  void setModel(int modelIndex, boolean clearBackgroundModel) {
    if (modelIndex < 0)
      stopThread(false);
    int formerModelIndex = currentModelIndex;
    ModelSet modelSet = viewer.getModelSet();
    int modelCount = (modelSet == null ? 0 : modelSet.modelCount);
    if (modelCount == 1)
      currentModelIndex = modelIndex = 0;
    else if (modelIndex < 0 || modelIndex >= modelCount)
      modelIndex = -1;
    String ids = null;
    boolean isSameSource = false;
    if (currentModelIndex != modelIndex) {
      if (modelCount > 0) {
        boolean toDataModel = viewer.isJmolDataFrameForModel(modelIndex);
        boolean fromDataModel = viewer.isJmolDataFrameForModel(currentModelIndex);
        if (fromDataModel)
          viewer.setJmolDataFrame(null, -1, currentModelIndex);
        if (currentModelIndex != -1)
          viewer.saveModelOrientation();
        if (fromDataModel || toDataModel) {
          ids = viewer.getJmolFrameType(modelIndex) 
          + " "  + modelIndex + " <-- " 
          + " " + currentModelIndex + " " 
          + viewer.getJmolFrameType(currentModelIndex);
          
          isSameSource = (viewer.getJmolDataSourceFrame(modelIndex) == viewer
              .getJmolDataSourceFrame(currentModelIndex));
        }
      }
      currentModelIndex = modelIndex;
      if (ids != null) {
        if (modelIndex >= 0)
          viewer.restoreModelOrientation(modelIndex);
        if (isSameSource && (ids.indexOf("quaternion") >= 0 
            || ids.indexOf("plot") < 0
            && ids.indexOf("ramachandran") < 0
            && ids.indexOf(" property ") < 0)) {
          viewer.restoreModelRotation(formerModelIndex);
        }
      }
    }
    setViewer(clearBackgroundModel);
  }

  void setBackgroundModelIndex(int modelIndex) {
    ModelSet modelSet = viewer.getModelSet();
    if (modelSet == null || modelIndex < 0 || modelIndex >= modelSet.modelCount)
      modelIndex = -1;
    backgroundModelIndex = modelIndex;
    if (modelIndex >= 0)
      viewer.setTrajectory(modelIndex);
    viewer.setTainted(true);
    setFrameRangeVisible(); 
  }
  
  void initializePointers(int frameStep) {
    firstFrameIndex = 0;
    lastFrameIndex = (frameStep == 0 ? 0 : getFrameCount()) - 1;
    this.frameStep = frameStep;
    viewer.setFrameVariables();
  }

  void setAnimationDirection(int animationDirection) {
    this.animationDirection = animationDirection;
    //if (animationReplayMode != ANIMATION_LOOP)
      //currentDirection = 1;
  }

  void setAnimationFps(int animationFps) {
    this.animationFps = animationFps;
    viewer.setFrameVariables();
  }

  // 0 = once
  // 1 = loop
  // 2 = palindrome
  
  void setAnimationReplayMode(EnumAnimationMode animationReplayMode,
                                     float firstFrameDelay,
                                     float lastFrameDelay) {
    this.firstFrameDelay = firstFrameDelay > 0 ? firstFrameDelay : 0;
    firstFrameDelayMs = (int)(this.firstFrameDelay * 1000);
    this.lastFrameDelay = lastFrameDelay > 0 ? lastFrameDelay : 0;
    lastFrameDelayMs = (int)(this.lastFrameDelay * 1000);
    this.animationReplayMode = animationReplayMode;
    viewer.setFrameVariables();
  }

  void setAnimationRange(int framePointer, int framePointer2) {
    int frameCount = getFrameCount();
    if (framePointer < 0) framePointer = 0;
    if (framePointer2 < 0) framePointer2 = frameCount;
    if (framePointer >= frameCount) framePointer = frameCount - 1;
    if (framePointer2 >= frameCount) framePointer2 = frameCount - 1;
    firstFrameIndex = framePointer;
    currentMorphModel = firstFrameIndex;
    lastFrameIndex = framePointer2;
    frameStep = (framePointer2 < framePointer ? -1 : 1);
    rewindAnimation();
  }

  void pauseAnimation() {
    stopThread(true);
  }
  
  void reverseAnimation() {
    currentDirection = -currentDirection;
    if (!animationOn)
      resumeAnimation();
  }
  
  void repaintDone() {
    lastModelPainted = currentModelIndex;
    lastFramePainted = currentAnimationFrame;
  }
  
  void resumeAnimation() {
    if(currentModelIndex < 0)
      setAnimationRange(firstFrameIndex, lastFrameIndex);
    if (getFrameCount() <= 1) {
      animation(false);
      return;
    }
    animation(true);
    animationPaused = false;
    if (animationThread == null) {
      intAnimThread++;
      animationThread = (JmolThread) Interface.getOptionInterface("thread.AnimationThread");
      animationThread.setManager(this, viewer, new int[] {firstFrameIndex, lastFrameIndex, intAnimThread} );
      animationThread.start();
    }
  }

  void setAnimationLast() {
    setFrame(animationDirection > 0 ? lastFrameIndex : firstFrameIndex);
  }

  void rewindAnimation() {
    setFrame(animationDirection > 0 ? firstFrameIndex : lastFrameIndex);
    currentDirection = 1;
    viewer.setFrameVariables();
  }
  
  boolean setAnimationPrevious() {
    return setAnimationRelative(-animationDirection);
  }

  float getAnimRunTimeSeconds() {
    int frameCount = getFrameCount();
    if (firstFrameIndex == lastFrameIndex || lastFrameIndex < 0
        || firstFrameIndex < 0 || lastFrameIndex >= frameCount
        || firstFrameIndex >= frameCount)
      return 0;
    int i0 = Math.min(firstFrameIndex, lastFrameIndex);
    int i1 = Math.max(firstFrameIndex, lastFrameIndex);
    float nsec = 1f * (i1 - i0) / animationFps + firstFrameDelay
        + lastFrameDelay;
    for (int i = i0; i <= i1; i++)
      nsec += viewer.getFrameDelayMs(modelIndexForFrame(i)) / 1000f;
    return nsec;
  }

  /**
   * support for PyMOL movies and 
   * anim FRAMES [....]
   * 
   * currently no support for scripted movies
   * 
   * @param info
   */
  void setMovie(Map<String, Object> info) {
    isMovie = (info != null && info.get("scripts") == null);
    if (isMovie) {
      animationFrames = (int[]) info.get("frames");
      if (animationFrames == null || animationFrames.length == 0) {
        isMovie = false;
      } else {
        currentAnimationFrame = ((Integer) info.get("currentFrame")).intValue();
        if (currentAnimationFrame < 0 || currentAnimationFrame >= animationFrames.length)
          currentAnimationFrame = 0;
      }
      setFrame(currentAnimationFrame);
    } 
    if (!isMovie) {
      //movie = null;
      animationFrames = null;
    }
    viewer.setBooleanProperty("_ismovie", isMovie);
    bsDisplay = null;
    currentMorphModel = morphCount = 0;
  }

  int[] getAnimationFrames() {
    return animationFrames;
  }

  int getCurrentFrameIndex() {
    return currentAnimationFrame;
  }

  int modelIndexForFrame(int i) {
    return (isMovie ? animationFrames[i] - 1 : i);
  }

  int getFrameCount() {
    return (isMovie ? animationFrames.length : viewer.getModelCount());
  }

  void setFrame(int i) {
    try {
    if (isMovie) {
      int iModel = modelIndexForFrame(i);
      currentAnimationFrame = i;
      i = iModel;
    } else {
      currentAnimationFrame = i;
    }
    setModel(i, true);
    } catch (Exception e) {
      // ignore
    }
  }

  // private methods and fields
   
  private int lastFramePainted;
  private int lastModelPainted;
  private int intAnimThread;
  public int currentAtomIndex = -1;
  private void setViewer(boolean clearBackgroundModel) {
    viewer.setTrajectory(currentModelIndex);
    viewer.setFrameOffset(currentModelIndex);
    if (currentModelIndex == -1 && clearBackgroundModel)
      setBackgroundModelIndex(-1);  
    viewer.setTainted(true);
    setFrameRangeVisible();
    viewer.setStatusFrameChanged(false, true);
    if (viewer.modelSet != null && !viewer.global.selectAllModels)
        viewer.setSelectionSubset(viewer.getModelUndeletedAtomsBitSet(currentModelIndex));
  }

  private void setFrameRangeVisible() {
    bsVisibleModels.clearAll();
    if (backgroundModelIndex >= 0)
      bsVisibleModels.set(backgroundModelIndex);
    if (currentModelIndex >= 0) {
      bsVisibleModels.set(currentModelIndex);
      return;
    }
    if (frameStep == 0)
      return;
    int nDisplayed = 0;
    int frameDisplayed = 0;
    for (int iframe = firstFrameIndex; iframe != lastFrameIndex; iframe += frameStep) {
      int i = modelIndexForFrame(iframe);
      if (!viewer.isJmolDataFrameForModel(i)) {
        bsVisibleModels.set(i);
        nDisplayed++;
        frameDisplayed = iframe;
      }
    }
    int i = modelIndexForFrame(lastFrameIndex);
    if (firstFrameIndex == lastFrameIndex || !viewer.isJmolDataFrameForModel(i)
        || nDisplayed == 0) {
      bsVisibleModels.set(i);
      if (nDisplayed == 0)
        firstFrameIndex = lastFrameIndex;
      nDisplayed = 0;
    }
    if (nDisplayed == 1 && currentModelIndex < 0)
      setFrame(frameDisplayed);   
  }

  private void animation(boolean TF) {
    animationOn = TF; 
    viewer.setBooleanProperty("_animating", TF);
  }
  
  private boolean setAnimationRelative(int direction) {
    int frameStep = getFrameStep(direction);
    int thisFrame = (isMovie ? currentAnimationFrame : currentModelIndex);
    int frameNext = thisFrame + frameStep;
    float morphStep = 0f, nextMorphFrame = 0f;
    boolean isDone;
    if (morphCount > 0) {
      morphStep = 1f / (morphCount + 1);
      nextMorphFrame = currentMorphModel + frameStep * morphStep;
      isDone = isNotInRange(nextMorphFrame);
    } else {
      isDone = isNotInRange(frameNext);
    }
    if (isDone) {
      switch (animationReplayMode) {
      case ONCE:
        return false;
      case LOOP:
        nextMorphFrame = frameNext = (animationDirection == currentDirection ? firstFrameIndex
            : lastFrameIndex);
        break;
      case PALINDROME:
        currentDirection = -currentDirection;
        frameNext -= 2 * frameStep;
        nextMorphFrame -= 2 * frameStep * morphStep;
      }
    }
    //Logger.debug("next="+modelIndexNext+" dir="+currentDirection+" isDone="+isDone);
    //System.out.println("setAnimRel dir=" + direction + " step=" + frameStep + " this=" + thisFrame + " next=" + frameNext + " morphcount=" + morphCount + " done=" + isDone + " mode=" + animationReplayMode);
    if (morphCount < 1) {
      if (frameNext < 0 || frameNext >= getFrameCount())
        return false;
      setFrame(frameNext);
      return true;
    }
    morph(nextMorphFrame + 1);
    return true;
  }

  private boolean isNotInRange(float frameNext) {
    float f = frameNext - 0.001f;
    return (f > firstFrameIndex && f > lastFrameIndex 
        || (f = frameNext + 0.001f) < firstFrameIndex
        && f < lastFrameIndex);
  }

  private int getFrameStep(int direction) {
    return frameStep * direction * currentDirection;
  }

//private JmolThread modulationThread;
//public boolean modulationPlay;
//public float modulationFps = 1;
//public BS bsModulating;
//
//public void setModulationFps(float fps) {
//  if (fps > 0)
//    modulationFps = fps;
//  else
//    stopModulationThread();
//}

//public void setModulationPlay(int modT1, int modT2) {
//if (modT1 == Integer.MAX_VALUE || !viewer.haveModelSet() || viewer.isHeadless()) {
//  stopThread(false);
//  return;
//}
//if (modulationThread == null) {
//  modulationPlay = true;
//  modulationThread = (JmolThread) Interface.getOptionInterface("thread.ModulationThread");
//  modulationThread.setManager(this, viewer, new int[] {modT1, modT2} );
//  modulationThread.start();
//}
//}
//public void stopModulationThread() {
//if (modulationThread != null) {
//  modulationThread.interrupt();
//  modulationThread = null;
//}
//modulationPlay = false;
//}
//


}
