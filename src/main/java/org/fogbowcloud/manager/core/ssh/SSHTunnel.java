package org.fogbowcloud.manager.core.ssh;

import java.util.Properties;

import org.fogbowcloud.manager.occi.request.Request;

public interface SSHTunnel {

	public void create(Properties properties, Request request) throws Exception;
	
	public void release(Request request);
	
}
