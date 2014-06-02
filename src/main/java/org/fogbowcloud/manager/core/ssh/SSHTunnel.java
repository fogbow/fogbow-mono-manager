package org.fogbowcloud.manager.core.ssh;

import java.util.Properties;

import org.fogbowcloud.manager.occi.request.Request;

public interface SSHTunnel {

	public Integer create(Properties properties, Request request) throws Exception;
	
	public void update(String instanceId, Integer port);
	
	public void release(String instanceId);
	
	public void release(Integer port);
	
	public String getPublicAddress(Properties properties, String instanceId);
	
}
