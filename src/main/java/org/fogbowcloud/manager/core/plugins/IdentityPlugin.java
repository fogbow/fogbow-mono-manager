package org.fogbowcloud.manager.core.plugins;

import java.util.Map;

import org.fogbowcloud.manager.occi.core.Token;

//TODO Refactor review the need of methods
public interface IdentityPlugin {

	public String getUser(String tokenId);
	
	public String getTokenExpiresDate(String tokenId);
	
	public Token getToken(Map<String, String> tokenCredentials);
	
	public Token updateToken(Token token);

	public Token getToken(String authToken);
}
