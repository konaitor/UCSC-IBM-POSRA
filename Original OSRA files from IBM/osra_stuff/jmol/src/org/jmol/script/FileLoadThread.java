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

package org.jmol.script;

import org.jmol.api.JmolScriptEvaluator;
import org.jmol.thread.JmolThread;
import org.jmol.viewer.Viewer;

class FileLoadThread extends JmolThread {

  String fileName;
  private String cacheName;
  private String key;

  /**
   * JavaScript only
   * 
   * @param eval
   * @param viewer
   * @param fileName
   * @param key 
   * @param cacheName 
   */
  public FileLoadThread(JmolScriptEvaluator eval, Viewer viewer, String fileName, String key, String cacheName) {
    setViewer(viewer, "FileLoadThread");
    this.fileName = fileName;
    this.key = key;
    this.cacheName = cacheName;
    setEval(eval);
    sc.pc--; // re-start this load command.
  }
  
  @Override
  protected void run1(int mode) throws InterruptedException {
    while (true)
      switch (mode) {
      case INIT: 
        mode = MAIN;
        break;
      case MAIN:
        if (stopped || eval.isStopped()) {
          mode = FINISH;
          break;
        }
        /**
         * @j2sNative
         * 
         * return Jmol._loadFileAsynchronously(this, this.viewer.applet, this.fileName);
         * 
         */
        {
        }
        break;
      case FINISH:
        resumeEval();
        return;
      }
  }

  /**
   * Called by Jmol._loadFileAsyncDone(this.viewer.applet). Allows for callback
   * to set the file name.
   * 
   * @param fileName
   * @param data
   * @throws InterruptedException
   */
  void setData(String fileName, Object data) throws InterruptedException {
    if (fileName != null)
      sc.parentContext.htFileCache.put(key, cacheName = cacheName.substring(0, 
          cacheName.lastIndexOf("_") + 1) + fileName);
    viewer.cachePut(cacheName, data);
    run1(FINISH);
  }   
}