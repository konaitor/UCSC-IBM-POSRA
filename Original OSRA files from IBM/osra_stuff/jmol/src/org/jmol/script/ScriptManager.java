/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2006-05-01 16:40:46 -0500 (Mon, 01 May 2006) $
 * $Revision: 5041 $
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;

import javajs.api.ZInputStream;
import javajs.util.List;
import javajs.util.PT;
import javajs.util.SB;


import org.jmol.api.Interface;
import org.jmol.api.JmolScriptEvaluator;
import org.jmol.api.JmolScriptManager;
import org.jmol.io.JmolBinary;
import org.jmol.java.BS;
import org.jmol.thread.JmolThread;
import org.jmol.util.Escape;
import org.jmol.util.Logger;
import org.jmol.viewer.JC;
import org.jmol.viewer.StatusManager;
import org.jmol.viewer.Viewer;

public class ScriptManager implements JmolScriptManager {

  private Viewer viewer;
  private JmolScriptEvaluator eval;
  
  @Override
  public JmolScriptEvaluator getEval() {
    return eval;
  }
  
  private JmolScriptEvaluator evalTemp;

  private Thread[] queueThreads = new Thread[2];
  private boolean[] scriptQueueRunning = new boolean[2];
  private JmolThread commandWatcherThread;
  

  public List<List<Object>> scriptQueue = new  List<List<Object>>();

  @Override
  public List<List<Object>> getScriptQueue() {
    return scriptQueue;
  }

  @Override
  public boolean isScriptQueued() {
    return isScriptQueued;
  }

  public ScriptManager() {
    // by reflection only
  }
  
  @Override
  public void setViewer(Viewer viewer) {
    this.viewer = viewer;
    eval = newScriptEvaluator();
    eval.setCompiler();
  }
 
  private JmolScriptEvaluator newScriptEvaluator() {
    return ((JmolScriptEvaluator) Interface
        .getOptionInterface("script.ScriptEvaluator")).setViewer(viewer);
  }

  @Override
  public void clear(boolean isAll) {
    if (!isAll) {
      evalTemp = null;
      return;
    }
    startCommandWatcher(false);
    interruptQueueThreads();
  }

  @Override
  public String addScript(String strScript, boolean isScriptFile,
                          boolean isQuiet) {
    return (String) addScr("String", strScript, "", isScriptFile, isQuiet);
  }

  private Object addScr(String returnType, String strScript,
                          String statusList, boolean isScriptFile,
                          boolean isQuiet) {
    /**
     * @j2sNative
     *  this.useCommandWatcherThread = false; 
     */
    {}
        
    if (!viewer.global.useScriptQueue) {
      clearQueue();
      viewer.haltScriptExecution();
    }
    if (commandWatcherThread == null && useCommandWatcherThread)
      startCommandWatcher(true);
    if (commandWatcherThread != null && strScript.indexOf("/*SPLIT*/") >= 0) {
      String[] scripts = PT.split(strScript, "/*SPLIT*/");
      for (int i = 0; i < scripts.length; i++)
        addScr(returnType, scripts[i], statusList, isScriptFile, isQuiet);
      return "split into " + scripts.length + " sections for processing";
    }
    boolean useCommandThread = (commandWatcherThread != null && 
        (strScript.indexOf("javascript") < 0 
            || strScript.indexOf("#javascript ") >= 0));
    // scripts with #javascript will be processed at the browser end
    List<Object> scriptItem = new  List<Object>();
    scriptItem.addLast(strScript);
    scriptItem.addLast(statusList);
    scriptItem.addLast(returnType);
    scriptItem.addLast(isScriptFile ? Boolean.TRUE : Boolean.FALSE);
    scriptItem.addLast(isQuiet ? Boolean.TRUE : Boolean.FALSE);
    scriptItem.addLast(Integer.valueOf(useCommandThread ? -1 : 1));
    scriptQueue.addLast(scriptItem);
    //if (Logger.debugging)
    //  Logger.info("ScriptManager queue size=" + scriptQueue.size() + " scripts; added: " 
      //    + strScript + " " + Thread.currentThread().getName());
    startScriptQueue(false);
    //System.out.println("ScriptManager queue 'pending'");
    return "pending";
  }

  //public int getScriptCount() {
  //  return scriptQueue.size();
  //}

  @Override
  public void clearQueue() {
    scriptQueue.clear();
  }

  @Override
  public void waitForQueue() {
    // just can't do this in JavaScript. 
    // if we are here and it is single-threaded, and there is
    // a script running, then that's a problem.
    
    if (viewer.isSingleThreaded)
      return;
    int n = 0;
    while (isQueueProcessing()) {
      try {
        Thread.sleep(100);
        if (((n++) % 10) == 0)
          if (Logger.debugging) {
            Logger.debug("...scriptManager waiting for queue: "
                + scriptQueue.size() + " thread="
                + Thread.currentThread().getName());
          }
      } catch (InterruptedException e) {
      }
    }
  }

  @Override
  public boolean isQueueProcessing() {
    return queueThreads[0] != null || queueThreads[1] != null;
  }

  synchronized private void flushQueue(String command) {
    for (int i = scriptQueue.size(); --i >= 0;) {
      String strScript = (String) (scriptQueue.get(i).get(0));
      if (strScript.indexOf(command) == 0) {
        scriptQueue.remove(i);
        if (Logger.debugging)
          Logger.debug(scriptQueue.size() + " scripts; removed: " + strScript);
      }
    }
  }

  private void startScriptQueue(boolean startedByCommandWatcher) {
    int pt = (startedByCommandWatcher ? 1 : 0);
    if (scriptQueueRunning[pt])
      return;
    scriptQueueRunning[pt] = true;
    queueThreads[pt] = new ScriptQueueThread(this, viewer,
        startedByCommandWatcher, pt);
    queueThreads[pt].start();
  }

  @Override
  public List<Object> getScriptItem(boolean watching, boolean isByCommandWatcher) {
    if (viewer.isSingleThreaded && viewer.queueOnHold)
      return null;
    List<Object> scriptItem = scriptQueue.get(0);
    int flag = (((Integer) scriptItem.get(5)).intValue());
    boolean isOK = (watching ? flag < 0 
        : isByCommandWatcher ? flag == 0
        : flag == 1);
    //System.out.println("checking queue for thread " + (watching ? 1 : 0) + "watching = " + watching + " isbycommandthread=" + isByCommandWatcher + "  flag=" + flag + " isOK = " + isOK + " " + scriptItem.get(0));
    return (isOK ? scriptItem : null);
  }

 private boolean useCommandWatcherThread = false;

  @Override
  synchronized public void startCommandWatcher(boolean isStart) {
    useCommandWatcherThread = isStart;
    if (isStart) {
      if (commandWatcherThread != null)
        return;
      commandWatcherThread = (JmolThread) Interface
      .getOptionInterface("script.CommandWatcherThread");
      commandWatcherThread.setManager(this, viewer, null);
      commandWatcherThread.start();
    } else {
      if (commandWatcherThread == null)
        return;
      clearCommandWatcherThread();
    }
    if (Logger.debugging) {
      Logger.debug("command watcher " + (isStart ? "started" : "stopped")
          + commandWatcherThread);
    }
  }

  /*
   * CommandWatcher thread handles processing of 
   * command scripts independently of the user thread.
   * This is important for the signed applet, where the
   * thread opening remote files cannot be the browser's,
   * and commands that utilize JavaScript must.
   * 
   * We need two threads for the signed applet, because commands
   * that involve JavaScript -- the "javascript" command or math javascript() --
   * must run on a thread created by the thread generating the applet call.
   * 
   * This CommandWatcher thread, on the other hand, is created by the applet at 
   * start up -- it can cross domains, but it can't run JavaScript. 
   * 
   * The 5th vector position is an Integer flag.
   * 
   *   -1  -- Owned by CommandWatcher; ready for thread assignment
   *    0  -- Owned by CommandWatcher; running
   *    1  -- Owned by the JavaScript-enabled/browser-limited thread
   * 
   * If the command is to be ignored by the CommandWatcher, the flag is set 
   * to 1. For the watcher, the flag is first set to -1. This means the
   * command watcher owns it, and the standard script thread should
   * ignore it. The current script queue cycles.
   * 
   * if the CommandWatcher sees a -1 in element 5 of the 0 (next) queue position
   * vector, then it says, "That's mine -- I'll take it." It sets the
   * flag to 0 and starts the script queue. When that script queue removes the
   * 0-position item, the previous script queue takes off again and 
   * finishes the run.
   *  
   */

  void interruptQueueThreads() {
    for (int i = 0; i < queueThreads.length; i++) {
      if (queueThreads[i] != null)
        queueThreads[i].interrupt();
    }
  }

  public void clearCommandWatcherThread() {
    if (commandWatcherThread == null)
      return;
    commandWatcherThread.interrupt();
    commandWatcherThread = null;
  }

  @Override
  public void queueThreadFinished(int pt) {
    queueThreads[pt].interrupt();
    scriptQueueRunning[pt] = false;
    queueThreads[pt] = null;
    viewer.setSyncDriver(StatusManager.SYNC_ENABLE);
    viewer.queueOnHold = false;
  }

  public void runScriptNow() {
    // from ScriptQueueThread
    if (scriptQueue.size() > 0) {
      List<Object> scriptItem = getScriptItem(true, true);
      if (scriptItem != null) {
        scriptItem.set(5, Integer.valueOf(0));
        startScriptQueue(true);
      }
    }
  }

  private int scriptIndex;
  private boolean isScriptQueued = true;

  @Override
  public Object evalStringWaitStatusQueued(String returnType, String strScript,
                                           String statusList,
                                           boolean isScriptFile,
                                           boolean isQuiet, boolean isQueued) {
    // from the scriptManager or scriptWait()
    if (strScript == null)
      return null;
    String str = checkScriptExecution(strScript, false);
    if (str != null)
      return str;
    SB outputBuffer = (statusList == null
        || statusList.equals("output") ? new SB() : null);

    // typically request:
    // "+scriptStarted,+scriptStatus,+scriptEcho,+scriptTerminated"
    // set up first with applet.jmolGetProperty("jmolStatus",statusList)
    // flush list
    String oldStatusList = viewer.statusManager.getStatusList();
    viewer.getStatusChanged(statusList);
    if (viewer.isSyntaxCheck)
      Logger.info("--checking script:\n" + eval.getScript() + "\n----\n");
    boolean historyDisabled = (strScript.indexOf(")") == 0);
    if (historyDisabled)
      strScript = strScript.substring(1);
    historyDisabled = historyDisabled || !isQueued; // no history for scriptWait
    // 11.5.45
    viewer.setErrorMessage(null, null);
    boolean isOK = (isScriptFile ? eval.compileScriptFile(strScript, isQuiet)
        : eval.compileScriptString(strScript, isQuiet));
    String strErrorMessage = eval.getErrorMessage();
    String strErrorMessageUntranslated = eval.getErrorMessageUntranslated();
    viewer.setErrorMessage(strErrorMessage, strErrorMessageUntranslated);
    viewer.refresh(7,"script complete");
    if (isOK) {
      isScriptQueued = isQueued;
      if (!isQuiet)
        viewer.setScriptStatus(null, strScript, -2 - (++scriptIndex), null);
      eval.evaluateCompiledScript(viewer.isSyntaxCheck, viewer.isSyntaxAndFileCheck,
          historyDisabled, viewer.listCommands, outputBuffer, isQueued || !viewer.isSingleThreaded);
    } else {
      viewer.scriptStatus(strErrorMessage);
      viewer.setScriptStatus("Jmol script terminated", strErrorMessage, 1,
          strErrorMessageUntranslated);
      viewer.setStateScriptVersion(null); // set by compiler
    }
    if (strErrorMessage != null && viewer.autoExit)
      viewer.exitJmol();
    if (viewer.isSyntaxCheck) {
      if (strErrorMessage == null)
        Logger.info("--script check ok");
      else
        Logger.error("--script check error\n" + strErrorMessageUntranslated);
      Logger.info("(use 'exit' to stop checking)");
    }
    isScriptQueued = true;
    if (returnType.equalsIgnoreCase("String"))
      return strErrorMessageUntranslated;
    if (outputBuffer != null)
      return (strErrorMessageUntranslated == null ? outputBuffer.toString()
          : strErrorMessageUntranslated);
    // get Vector of Vectors of Vectors info ("object") or, for the applet, JSON ("json")
    Object info = viewer.getProperty(returnType, "jmolStatus", statusList);
    viewer.getStatusChanged(oldStatusList);
    return info;
  }
  
  private String checkScriptExecution(String strScript, boolean isInsert) {
    String str = strScript;
    if (str.indexOf("\1##") >= 0)
      str = str.substring(0, str.indexOf("\1##"));
    if (checkResume(str))
      return "script processing resumed";
    if (checkStepping(str))
      return "script processing stepped";
    if (checkHalt(str, isInsert))
      return "script execution halted";
    return null;
  }

  private boolean checkResume(String str) {
    if (str.equalsIgnoreCase("resume")) {
      viewer.setScriptStatus("", "execution resumed", 0, null);
      eval.resumePausedExecution();
      return true;
    }
    return false;
  }

  private boolean checkStepping(String str) {
    if (str.equalsIgnoreCase("step")) {
      eval.stepPausedExecution();
      return true;
    }
    if (str.equalsIgnoreCase("?")) {
      viewer.scriptStatus(eval.getNextStatement());
      return true;
    }
    return false;
  }

  @Override
  public String evalStringQuietSync(String strScript, boolean isQuiet,
                                    boolean allowSyncScript) {
    // central point for all incoming script processing
    // all menu items, all mouse movement -- everything goes through this method
    // by setting syncScriptTarget = ">" the user can direct that all scripts
    // initiated WITHIN this applet (not sent to it)
    // we append #NOSYNC; here so that the receiving applet does not attempt
    // to pass it back to us or any other applet.
    //System.out.println("OK, I'm in evalStringQUiet");
    if (allowSyncScript && viewer.statusManager.syncingScripts
        && strScript.indexOf("#NOSYNC;") < 0)
      viewer.syncScript(strScript + " #NOSYNC;", null, 0);
    if (eval.isPaused() && strScript.charAt(0) != '!')
      strScript = '!' + PT.trim(strScript, "\n\r\t ");
    boolean isInsert = (strScript.length() > 0 && strScript.charAt(0) == '!');
    if (isInsert)
      strScript = strScript.substring(1);
    String msg = checkScriptExecution(strScript, isInsert);
    if (msg != null)
      return msg;
    if (viewer.isScriptExecuting() && (isInsert || eval.isPaused())) {
      viewer.setInsertedCommand(strScript);
      if (strScript.indexOf("moveto ") == 0)
        flushQueue("moveto ");
      return "!" + strScript;
    }
    viewer.setInsertedCommand("");
    if (isQuiet)
      strScript += JC.SCRIPT_EDITOR_IGNORE;
    return addScript(strScript, false, isQuiet
        && !viewer.getBoolean(T.messagestylechime));
  }

  @Override
  public boolean checkHalt(String str, boolean isInsert) {
    if (str.equalsIgnoreCase("pause")) {
      viewer.pauseScriptExecution();
      if (viewer.scriptEditorVisible)
        viewer.setScriptStatus("", "paused -- type RESUME to continue", 0, null);
      return true;
    }
    if (str.equalsIgnoreCase("menu")) {
      viewer.getProperty("DATA_API", "getPopupMenu", "\0");
      return true;
    }
    str = str.toLowerCase();
    boolean exitScript = false;
    String haltType = null;
    if (str.startsWith("exit")) {
      viewer.haltScriptExecution();
      viewer.clearScriptQueue();
      viewer.clearTimeouts();
      exitScript = str.equals(haltType = "exit");
    } else if (str.startsWith("quit")) {
      viewer.haltScriptExecution();
      exitScript = str.equals(haltType = "quit");
    }
    if (haltType == null)
      return false;
    // !quit or !exit
    if (isInsert) {
      viewer.clearThreads();
      viewer.queueOnHold = false;
    }
    if (isInsert || viewer.global.waitForMoveTo) {
      viewer.stopMotion();
    }
    Logger.info(viewer.isSyntaxCheck ? haltType
        + " -- stops script checking" : (isInsert ? "!" : "") + haltType
        + " received");
    viewer.isSyntaxCheck = false;
    return exitScript;
  }

  @Override
  public BS getAtomBitSetEval(JmolScriptEvaluator eval,
                                  Object atomExpression) {
    if (eval == null) {
      eval = evalTemp;
      if (eval == null)
        eval = evalTemp = newScriptEvaluator();
    }
    return eval.getAtomBitSet(atomExpression);
  }

  @Override
  public Object scriptCheckRet(String strScript, boolean returnContext) {
    // from ConsoleTextPane.checkCommand() and applet Jmol.scriptProcessor()
    if (strScript.indexOf(")") == 0 || strScript.indexOf("!") == 0) // history
      // disabled
      strScript = strScript.substring(1);
    ScriptContext sc = newScriptEvaluator().checkScriptSilent(strScript);
    if (returnContext || sc.errorMessage == null)
      return sc;
    return sc.errorMessage;
  }

  //////////////////////// open file async ///////////////////////

  /**
   * 
   * From file dropping.
   * 
   * @param fileName 
   * @param flags 1=pdbCartoons 
   * 
   */
  @Override
  public void openFileAsync(String fileName, int flags) {
    boolean pdbCartoons = (flags == 1);
    String cmd = null;
    fileName = fileName.trim();
    boolean allowScript = (!fileName.startsWith("\t"));
    if (!allowScript)
      fileName = fileName.substring(1);
    fileName = fileName.replace('\\', '/');
    boolean isCached = fileName.startsWith("cache://");
    if (viewer.isApplet() && fileName.indexOf("://") < 0)
      fileName = "file://" + (fileName.startsWith("/") ? "" : "/") + fileName;
    try {
      if (fileName.endsWith(".pse")) {
        cmd = (isCached ? "" : "zap;") + "load SYNC " + Escape.eS(fileName)
            + " filter 'DORESIZE'";
        return;
      }
      if (fileName.endsWith("jvxl")) {
        cmd = "isosurface ";
        return;
      }
      if (!fileName.toLowerCase().endsWith(".spt")) {
        String type = getFileTypeName(fileName);
        if (type == null) {
          type = JmolBinary.determineSurfaceTypeIs(viewer
              .getBufferedInputStream(fileName));
          if (type != null)
            cmd = "if (_filetype == 'Pdb') { isosurface sigma 1.0 within 2.0 {*} "
                + Escape.eS(fileName)
                + " mesh nofill }; else; { isosurface "
                + Escape.eS(fileName) + "}";
          return;
        } else if (type.equals("Jmol")) {
          cmd = "script ";
        } else if (type.equals("Cube")) {
          cmd = "isosurface sign red blue ";
        } else if (!type.equals("spt")) {
          cmd = viewer.global.defaultDropScript;
          cmd = PT.simpleReplace(cmd, "%FILE", fileName);
          cmd = PT.simpleReplace(cmd, "%ALLOWCARTOONS", ""
              + pdbCartoons);
          if (cmd.toLowerCase().startsWith("zap") && isCached)
            cmd = cmd.substring(3);
          return;
        }
      }
      if (allowScript && viewer.scriptEditorVisible && cmd == null)
        viewer.showEditor(new String[] { fileName,
            viewer.getFileAsString(fileName) });
      else
        cmd = (cmd == null ? "script " : cmd) + Escape.eS(fileName);
    } finally {
      if (cmd != null)
        viewer.evalString(cmd);
    }
  }

  private String getFileTypeName(String fileName) {
    int pt = fileName.indexOf("::");
    if (pt >= 0)
      return fileName.substring(0, pt);
    if (fileName.startsWith("="))
      return "pdb";
    Object br = viewer.fileManager.getUnzippedReaderOrStreamFromName(fileName, null,
        true, false, true, true, null);
    if (br instanceof BufferedReader)
      return viewer.getModelAdapter().getFileTypeName(br);
    if (br instanceof ZInputStream) {
      String zipDirectory = getZipDirectoryAsString(fileName);
      if (zipDirectory.indexOf("JmolManifest") >= 0)
        return "Jmol";
      return viewer.getModelAdapter().getFileTypeName(
          JmolBinary.getBR(zipDirectory));
    }
    if (PT.isAS(br)) {
      return ((String[]) br)[0];
    }
    return null;
  }

  private String getZipDirectoryAsString(String fileName) {
    Object t = viewer.fileManager.getBufferedInputStreamOrErrorMessageFromName(
        fileName, fileName, false, false, null, false);
    return JmolBinary.getZipDirectoryAsStringAndClose((BufferedInputStream) t);
  }

}
