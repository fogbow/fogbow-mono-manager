package org.fogbowcloud.manager.core.plugins.identity.azure;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;

public class AzureIdentityPlugin implements IdentityPlugin {

	private static final Logger LOGGER = Logger
			.getLogger(AzureIdentityPlugin.class);
	private static final String SUBSCRIPTION_ID = "local_proxy_account_subscription_id";
	private static final String KEYSTORE_PATH = "local_proxy_account_keystore_path";
	private static final String KEYSTORE_PASSWORD = "local_proxy_account_keystore_password";
	private static final String ACCESS_ID = "local_proxy_account_access_id";
	private static final String USER = "Fogbow";

	private Properties properties;

	public AzureIdentityPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		if (properties == null) {
			LOGGER.error("User credentials can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"User credentials can't be null");
		}
		if (properties.getProperty(ACCESS_ID) == null) {
			LOGGER.warn("The token wasn't created yet, please try"
					+ "to create federation user");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"The token wasn't created yet, please try"
							+ "to create federation user");
		}
		if (!properties.getProperty(ACCESS_ID).equals(accessId)) {
			LOGGER.warn("Only federation user is allowed on this "
					+ "version of azure plugin");
			throw new  OCCIException(ErrorType.BAD_REQUEST,
					"Only federation user is allowed on this "
							+ "version of azure plugin");
		}
		return createFederationUserToken();
	}

	@Override
	public boolean isValid(String accessId) {
		if (properties == null) {
			LOGGER.error("User credentials can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"User credentials can't be null");
		}
		if ((properties.getProperty(ACCESS_ID) == null) 
				|| (!properties.getProperty(ACCESS_ID).equals(accessId))) {
			return false;
		}
		return true;
	}

	@Override
	public Token createFederationUserToken() {
		if ((properties == null)
				||(properties.getProperty(SUBSCRIPTION_ID) == null)
				|| (properties.getProperty(KEYSTORE_PASSWORD) == null)
				|| (properties.getProperty(KEYSTORE_PATH) == null)) {
			LOGGER.error("User credentials can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"User credentials can't be null");
		}
		if (properties.get(ACCESS_ID) == null) {
			LOGGER.debug("creating new access ID...");
			UUID accessID = UUID.randomUUID();
			properties.put(ACCESS_ID, accessID.toString());
		}
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(properties.getProperty(KEYSTORE_PATH), 
				properties.getProperty(KEYSTORE_PASSWORD));
		attributes.put(ACCESS_ID, properties.getProperty(ACCESS_ID));
		return new Token(properties.getProperty(ACCESS_ID), USER,
				new Date(), attributes);
	}

	@Override
	public Credential[] getCredentials() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getAuthenticationURI() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		throw new UnsupportedOperationException();
	}

}
