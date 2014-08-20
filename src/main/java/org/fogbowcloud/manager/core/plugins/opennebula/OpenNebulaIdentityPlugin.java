package org.fogbowcloud.manager.core.plugins.opennebula;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.CredentialsInterface;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.user.User;
import org.opennebula.client.user.UserPool;

public class OpenNebulaIdentityPlugin implements IdentityPlugin {
	
	public static final String USERNAME = "username";
	public static final String USER_PASSWORD = "password";
	public static final String AUTH_URL = "authUrl";
	
	private Properties properties;
	private String openNebulaEndpoint;
	private OpenNebulaClientFactory clientFactory;
	
	private final static Logger LOGGER = Logger.getLogger(OpenNebulaIdentityPlugin.class);

	public OpenNebulaIdentityPlugin(Properties properties) {
		this.properties = properties;
		this.openNebulaEndpoint = properties.getProperty(ConfigurationConstants.IDENTITY_URL);
		this.clientFactory = new OpenNebulaClientFactory();
	}
	
	/**
	 * This constructor must be used only for run tests
	 * 
	 * @param properties
	 * @param oneClient
	 */
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
		String authUrl = userCredentials.get(AUTH_URL);
		String accessId = username + ":" + userPass;
		if (authUrl != null && !authUrl.isEmpty()) {
			return getToken(accessId, authUrl);
		}
		return getToken(accessId);
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
		try {
			Client oneClient = clientFactory.createClient(accessId, openNebulaEndpoint);

			UserPool userPool = new UserPool(oneClient);
			OneResponse rc = userPool.info();
			if (rc.isError()) {
				LOGGER.error(rc.getErrorMessage());
				throw new OCCIException(ErrorType.UNAUTHORIZED, rc.getErrorMessage());
			}
			String username = accessId.split(":")[0];
			checkUserExists(username, userPool);
			return new Token(accessId, username, null, new HashMap<String, String>());
		} catch (ClientConfigurationException e) {
			LOGGER.error(e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}
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

	@SuppressWarnings("rawtypes")
	@Override
	public List<? extends Enum> getCredentials() {
		return Arrays.asList(Credentials.values());
	}
	
 	private enum Credentials implements CredentialsInterface{
		USERNAME("username", CredentialsInterface.REQUIRED_FEATURE, null),
		USER_PASSWORD("password", CredentialsInterface.REQUIRED_FEATURE, null), 
		AUTH_URL("authUrl", CredentialsInterface.REQUIRED_FEATURE, null);
 		
 		private String name;
 		private String valueDefault;
 		private String feature;
 		
 		private Credentials(String name, String feature, String valueDefault) {
 			this.name = name;
 			this.valueDefault = valueDefault;
 			this.feature = feature;
		} 		

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public String getValueDefault() {
			return this.valueDefault;
		}

		@Override
		public String getFeature() {
			return this.feature;
		}
 	}

}
