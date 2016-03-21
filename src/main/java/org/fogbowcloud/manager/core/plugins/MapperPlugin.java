package org.fogbowcloud.manager.core.plugins;

import java.util.Map;

import org.fogbowcloud.manager.occi.order.Order;

public interface MapperPlugin {

	public Map<String, String> getLocalCredentials(Order order);

	public Map<String, Map<String, String>> getAllLocalCredentials();
	
	public Map<String, String> getLocalCredentials(String accessId);
	
}
