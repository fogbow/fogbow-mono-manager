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
	private static final String SUBSCRIPTION_ID_PATH = "local_proxy_account_subscription_id";
	protected static final String SUBSCRIPTION_ID_CREDENTIAL = "subscriptionID";
	private static final String KEYSTORE_PATH = "local_proxy_account_keystore_path";
	protected static final String KEYSTORE_PATH_CREDENTIAL = "keystorePath";
	private static final String KEYSTORE_PASSWORD = "local_proxy_account_keystore_password";
	protected static final String KEYSTORE_PASSWORD_CREDENTIAL = "keystorePassword";
	private static final String USER = "Fogbow";

	private Properties properties;
	protected String accessID;

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
		if (accessID == null) {
			LOGGER.warn("The token wasn't created yet, please try"
					+ "to create federation user");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"The token wasn't created yet, please try"
							+ "to create federation user");
		}
		if (!accessID.equals(accessId)) {
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
		if ((accessID == null) 
				|| (!accessID.equals(accessId))) {
			return false;
		}
		return true;
	}

	@Override
	public Token createFederationUserToken() {
		if ((properties == null)
				||(properties.getProperty(SUBSCRIPTION_ID_PATH) == null)
				|| (properties.getProperty(KEYSTORE_PASSWORD) == null)
				|| (properties.getProperty(KEYSTORE_PATH) == null)) {
			LOGGER.error("User credentials can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"User credentials can't be null");
		}
		if (accessID == null) {
			LOGGER.debug("creating new access ID...");
			accessID = UUID.randomUUID().toString();
			LOGGER.debug(accessID + " is the new access ID");
		}
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(properties.getProperty(KEYSTORE_PATH), 
				properties.getProperty(KEYSTORE_PASSWORD));
		attributes.put(SUBSCRIPTION_ID_CREDENTIAL,
				properties.getProperty(SUBSCRIPTION_ID_PATH));
		return new Token(accessID, USER,
				new Date(), attributes);
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[]{ new Credential(SUBSCRIPTION_ID_CREDENTIAL, true, null),
				new Credential(KEYSTORE_PATH_CREDENTIAL, true, null), 
				new Credential(KEYSTORE_PASSWORD_CREDENTIAL, true, null)};
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
