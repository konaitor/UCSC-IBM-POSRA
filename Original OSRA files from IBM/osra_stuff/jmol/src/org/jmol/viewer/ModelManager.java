/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2013-10-17 12:05:38 -0500 (Thu, 17 Oct 2013) $
 * $Revision: 18805 $

 *
 * Copyright (C) 2003-2005  Miguel, Jmol Development, www.jmol.org
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
package org.jmol.viewer;


import org.jmol.java.BS;
import org.jmol.modelset.ModelLoader;
import org.jmol.modelset.ModelSet;

import javajs.util.SB;

class ModelManager {

  private final Viewer viewer;
  ModelSet modelSet;

  private String modelSetPathName;
  private String fileName;

  ModelManager(Viewer viewer) {
    this.viewer = viewer;
  }

  void zap() {
    modelSetPathName = fileName = null;
    new ModelLoader(viewer, viewer.getZapName(), null, null, null, null);
  }
  
  String getModelSetFileName() {
    return (fileName != null ? fileName : viewer.getZapName());
  }

  String getModelSetPathName() {
    return modelSetPathName;
  }
  
  void createModelSet(String fullPathName, String fileName,
                          SB loadScript, Object atomSetCollection,
                          BS bsNew, boolean isAppend) {
    String modelSetName = null;
    if (isAppend) {
      modelSetName = modelSet.modelSetName;
      if (modelSetName.equals("zapped"))
        modelSetName = null;
      else if (modelSetName.indexOf(" (modified)") < 0)
        modelSetName += " (modified)";
    } else if (atomSetCollection == null) {
      zap();
    } else {
      this.modelSetPathName = fullPathName;
      this.fileName = fileName;
    }
    if (atomSetCollection != null) {
      if (modelSetName == null) {
        modelSetName = viewer.getModelAdapter().getAtomSetCollectionName(
            atomSetCollection);
        if (modelSetName != null) {
          modelSetName = modelSetName.trim();
          if (modelSetName.length() == 0)
            modelSetName = null;
        }
        if (modelSetName == null)
          modelSetName = reduceFilename(fileName);
      }
      new ModelLoader(viewer, modelSetName, loadScript,
          atomSetCollection, (isAppend ? modelSet : null), bsNew);
    }
    if (modelSet.getAtomCount() == 0 && !modelSet.getModelSetAuxiliaryInfoBoolean("isPyMOL"))
      zap();
  }

  private static String reduceFilename(String fileName) {
    if (fileName == null)
      return null;
    int ichDot = fileName.indexOf('.');
    if (ichDot > 0)
      fileName = fileName.substring(0, ichDot);
    if (fileName.length() > 24)
      fileName = fileName.substring(0, 20) + " ...";
    return fileName;
  }

  void createAtomDataSet(Object atomSetCollection, int tokType) {
    ModelLoader.createAtomDataSet(viewer, modelSet, tokType, atomSetCollection,
    viewer.getSelectionSet(false));    
  }

}
