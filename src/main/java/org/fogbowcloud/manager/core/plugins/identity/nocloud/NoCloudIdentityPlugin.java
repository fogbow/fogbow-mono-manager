package org.fogbowcloud.manager.core.plugins.identity.nocloud;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.Token;

public class NoCloudIdentityPlugin implements IdentityPlugin {

	protected static final String FAKE_USERNAME = "no-user";
	
	public NoCloudIdentityPlugin(Properties properties) {}
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		return new Token(String.valueOf(UUID.randomUUID()), new Token.User(FAKE_USERNAME, FAKE_USERNAME), 
				null, new HashMap<String, String>());
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		return new Token(accessId, new Token.User(FAKE_USERNAME, FAKE_USERNAME), null, 
				new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {
		return true;
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential("", false, null) };
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return null;
	}

}
