package org.jmol.api;

import java.util.Map;

import javajs.api.GenericPlatform;
import javajs.util.OC;


public interface JmolImageEncoder {

  public boolean createImage(GenericPlatform apiPlatform, String type,
                             Object objImage, OC out,
                             Map<String, Object> params, String[] errRet);
}
