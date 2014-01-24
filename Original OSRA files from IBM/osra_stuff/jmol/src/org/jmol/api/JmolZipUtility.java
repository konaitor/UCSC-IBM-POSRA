package org.jmol.api;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Map;

import javajs.api.ZInputStream;

import org.jmol.viewer.FileManager;

public interface JmolZipUtility {

  public ZInputStream newZipInputStream(InputStream is);
  
  public String getZipDirectoryAsStringAndClose(BufferedInputStream t);

  public InputStream newGZIPInputStream(InputStream bis) throws IOException;

  public Object getZipFileContents(BufferedInputStream bis,
                                          String[] subFileList, int listPtr, boolean asBufferedInputStream);

  public String[] getZipDirectoryAndClose(BufferedInputStream t,
                                                 boolean addManifest);

  public void getAllZipData(InputStream bis, String[] subFileList,
                                String replace, String string,
                                Map<String, String> fileData);

  public Object getZipFileContentsAsBytes(BufferedInputStream bis,
                                                 String[] subFileList, int i);

  public String cacheZipContents(BufferedInputStream checkPngZipStream,
                                        String shortName,
                                        Map<String, byte[]> pngjCache);

  public Object getAtomSetCollectionOrBufferedReaderFromZip(JmolAdapter adapter,
                                                            InputStream is,
                                                            String fileName,
                                                            String[] zipDirectory,
                                                            Map<String, Object> htParams,
                                                            int i,
                                                            boolean asBufferedReader);

  public String[] spartanFileList(String name, String zipDirectory);

  public byte[] getCachedPngjBytes(FileManager fm, String pathName);

  public boolean cachePngjFile(FileManager fm, String[] data);

  public void addZipEntry(Object zos, String fileName) throws IOException;

  public void closeZipEntry(Object zos) throws IOException;

  public Object getZipOutputStream(Object bos);

  public int getCrcValue(byte[] bytes);

}
