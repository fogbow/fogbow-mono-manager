package org.fogbowcloud.manager.core.plugins;

import java.util.Map;

import org.fogbowcloud.manager.occi.request.Request;

public interface LocalCredentialsPlugin {

	public Map<String, String> getLocalCredentials(Request request);

	public Map<String, Map<String, String>> getAllLocalCredentials();
	
}
