package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Token;

public interface IdentityPlugin {

	public Token createToken(Map<String, String> userCredentials);

	public Token reIssueToken(Token token);

	public Token getToken(String accessId);

	public boolean isValid(String accessId);
	
	public Token createFederationUserToken();
	
	public List<? extends Enum> getCredentials();
}
