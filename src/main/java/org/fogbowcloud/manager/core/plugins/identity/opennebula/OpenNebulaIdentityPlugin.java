package org.fogbowcloud.manager.core.plugins.identity.opennebula;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;

public class OpenNebulaIdentityPlugin implements IdentityPlugin {
	
	public static final String USERNAME = "username";
	public static final String USER_PASSWORD = "password";
	
	private Properties properties;
	private String openNebulaEndpoint;
	private OpenNebulaClientFactory clientFactory;
	
	private final static Logger LOGGER = Logger.getLogger(OpenNebulaIdentityPlugin.class);

	public OpenNebulaIdentityPlugin(Properties properties) {
		this(properties, new OpenNebulaClientFactory());
	}
		
	public OpenNebulaIdentityPlugin(Properties properties, OpenNebulaClientFactory clientFactory) {
		this.properties = properties;
		this.openNebulaEndpoint = properties.getProperty(ConfigurationConstants.IDENTITY_URL);
		this.clientFactory = clientFactory;
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		LOGGER.debug("Creating token with credentials: " + userCredentials);
		String username = userCredentials.get(USERNAME);
		String userPass = userCredentials.get(USER_PASSWORD);
		String accessId = username + ":" + userPass;
		return new Token(accessId, username, null, new HashMap<String, String>());
	}

	private void checkUserExists(String username, UserPool userPool) {
		for( User user : userPool ) {
			if (username.equals(user.getName())) {
				return;
			}
        }
		LOGGER.error("User " + username + " was not found in user pool " + userPool);
		throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.INVALID_USER_OR_PASSWORD);
	}

	@Override
	public Token reIssueToken(Token token) {		
		return getToken(token.getAccessId());
	}

	@Override
	public Token getToken(String accessId) {
		LOGGER.debug("Getting token with accessId: " + accessId);
		return getToken(accessId, this.openNebulaEndpoint);
	}
	
	public Token getToken(String accessId, String openNebulaEndpoint) {
		if (accessId.contains("Basic ")) {
			accessId = new String(Base64.decodeBase64(accessId.replace("Basic ", "")));			
		}
		
		Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);

		UserPool userPool = new UserPool(oneClient);
		OneResponse response = userPool.info();
		if (response.isError()) {
			LOGGER.error(response.getErrorMessage());
			throw new OCCIException(ErrorType.UNAUTHORIZED, response.getErrorMessage());
		}
		String username = accessId.split(":")[0];
		checkUserExists(username, userPool);
		return new Token(accessId, username, null, new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {
		try {
			getToken(accessId);
			return true;
		} catch (OCCIException e) {
			return false;
		}
	}

	@Override
	public Token createFederationUserToken() {
		Map<String, String> federationUserCredentials = new HashMap<String, String>();
		String username = properties.getProperty(ConfigurationConstants.FEDERATION_USER_NAME_KEY);
		String password = properties.getProperty(ConfigurationConstants.FEDERATION_USER_PASS_KEY);

		federationUserCredentials.put(USERNAME, username);
		federationUserCredentials.put(USER_PASSWORD, password);		
		return createToken(federationUserCredentials);
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(USERNAME, true, null),
				new Credential(USER_PASSWORD, true, null) };
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
