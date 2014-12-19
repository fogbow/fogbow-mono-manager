package org.fogbowcloud.manager.core;

public interface AsynchronousRequestCallback {
	
	public void success(String instanceId);
	
	public void error(Throwable t);

}
