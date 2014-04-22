package org.fogbowcloud.manager.core.plugins;

public interface IdentityPlugin {

	public String getUser(String token);
	
	public String getToken(String username, String password);
}
