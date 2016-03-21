package org.fogbowcloud.manager.core;

public interface AsynchronousOrderCallback {
	
	public void success(String instanceId);
	
	public void error(Throwable t);

}
