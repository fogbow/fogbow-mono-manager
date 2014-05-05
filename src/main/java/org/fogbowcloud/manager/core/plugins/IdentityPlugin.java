package org.fogbowcloud.manager.core.plugins;

import java.util.Map;

import org.fogbowcloud.manager.occi.core.Token;

public interface IdentityPlugin {

	public String getUser(String token);
	
	public Token getToken(Map<String, String> tokenAttributes);
}
