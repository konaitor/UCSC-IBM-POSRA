package javajs.api;

public interface JSInterface {

  int cacheFileByName(String fileName, boolean isAdd);
  
  void cachePut(String key, Object data);
  
	Object getApplet();

	boolean processMouseEvent(int id, int x, int y, int modifiers, long time);

  void openFileAsyncSpecial(String fileName, int flags);

    void processTwoPointGesture(float[][][] touches);

	void setScreenDimension(int width, int height);

	void startHoverWatcher(boolean enable);

	void updateJS(int width, int height);

	
}

