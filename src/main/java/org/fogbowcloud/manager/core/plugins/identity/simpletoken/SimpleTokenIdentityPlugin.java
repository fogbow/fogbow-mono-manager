package org.fogbowcloud.manager.core.plugins.identity.simpletoken;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;

public class SimpleTokenIdentityPlugin implements IdentityPlugin {

	protected static final String TOKEN = "token";
	protected static final String SIMPLE_TOKEN_IDENTITY_VALID_TOKEN_ID = "simple_token_identity_valid_token_id";
	private String validTokenId;
	
	public SimpleTokenIdentityPlugin(Properties properties) {
		this.validTokenId = properties.getProperty(SIMPLE_TOKEN_IDENTITY_VALID_TOKEN_ID);
	}
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		String tokenCredential = userCredentials.get(TOKEN);
		if (tokenCredential == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		TokenHelper tokenHelper = new TokenHelper(tokenCredential);
		return new Token(tokenHelper.getId(), tokenHelper.getUser(),
				new Date(), new HashMap<String, String>());
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		if (accessId == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		TokenHelper tokenHelper = new TokenHelper(accessId);
		return new Token(tokenHelper.getId(), tokenHelper.getUser(),
				new Date(), new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {
		if (accessId == null) {
			return false;
		}		
		TokenHelper tokenHelper = new TokenHelper(accessId);
		if (!tokenHelper.getValidToken().equals(validTokenId)) {
			return false;
		}
		return true;
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(TOKEN, true, null)};
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return originalToken;
	}
	
	private class TokenHelper {
		private String id;
		private String validToken;
		private String user;
		
		public TokenHelper(String token) {
			String[] peaceTokenCredential = token.split("@");
			if (peaceTokenCredential.length <= 1 ) {
				throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.UNAUTHORIZED);
			}
			this.id = token;
			this.validToken = peaceTokenCredential[0];
			this.user = peaceTokenCredential[1];			
		}
		
		public String getId() {
			return id;
		}
		
		public String getUser() {
			return user;
		}
		
		public String getValidToken() {
			return validToken;
		}
		
	}
}
