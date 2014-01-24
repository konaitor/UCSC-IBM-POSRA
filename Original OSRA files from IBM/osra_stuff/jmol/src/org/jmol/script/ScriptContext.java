/* $RCSfile$
 * $Author$
 * $Date$
 * $Revision$
 *
 * Copyright (C) 2005  The Jmol Development Team
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

import java.util.Map;

import org.jmol.api.JmolParallelProcessor;

import javajs.util.SB;

public class ScriptContext {
  
  private static int contextCount = 0;

  public T[][] aatoken;
  public boolean allowJSThreads;
  boolean chk;
  public String contextPath = " >> ";
  public Map<String, SV> contextVariables;
  boolean displayLoadErrorsSave;
  public String errorMessage;
  String errorMessageUntranslated;
  public String errorType;
  public boolean executionPaused;
  public boolean executionStepping;
  public String functionName;
  public int iCommandError = -1;
  public int id;
  public boolean isComplete = true;
  boolean isFunction;
  public boolean isJSThread;
  boolean isStateScript;
  boolean isTryCatch;
  int iToken;
  int lineEnd = Integer.MAX_VALUE;
  public int[][] lineIndices;
  short[] lineNumbers;
  public boolean mustResumeEval;
  public SB outputBuffer;
  JmolParallelProcessor parallelProcessor;
  public ScriptContext parentContext;
  public int pc;
  public int pcEnd = Integer.MAX_VALUE;
  public String script;
  String scriptExtensions;
  public String scriptFileName;
  int scriptLevel;
  public T[] statement;
  Map<String, String> htFileCache;
  int statementLength;
  ContextToken token;
  int tryPt;
  T theToken;
  int theTok;
  
  ScriptContext() {
    id = ++contextCount;
  }

}