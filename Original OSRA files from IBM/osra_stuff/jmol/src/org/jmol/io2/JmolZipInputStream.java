package org.jmol.io2;


import java.io.InputStream;
import java.util.zip.ZipInputStream;

import javajs.api.ZInputStream;
public class JmolZipInputStream extends ZipInputStream implements ZInputStream {
  
  public JmolZipInputStream(InputStream in) {
    super(in);
  }
  
}