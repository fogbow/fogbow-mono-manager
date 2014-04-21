package org.fogbowcloud.manager.core.plugins;

public interface IdentityPlugin {

	public boolean isValidToken(String token);

	public String getUser(String token);
	
	public String getToken(String username, String password);
}
