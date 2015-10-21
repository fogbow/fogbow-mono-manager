package org.fogbowcloud.manager.core.plugins.identity.azure;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.federationcredentails.FUCPluginHelper;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;

public class AzureIdentityPlugin implements IdentityPlugin {

	protected static final String SUBSCRIPTION_ID_PATH = "proxy_account_subscription_id";
	protected static final String KEYSTORE_PATH = "proxy_account_keystore_path";
	protected static final String KEYSTORE_PASSWORD = "proxy_account_keystore_password";
	private static final String USER = "Fogbow";
	
	private static final Logger LOGGER = Logger.getLogger(AzureIdentityPlugin.class);
	
	protected Map<String, Map<String, String>> accessIDs;	
	private Properties properties;
	
	public AzureIdentityPlugin(Properties properties) {
		this.accessIDs = new HashMap<String, Map<String,String>>();
		createTokens(properties);		
		this.properties = properties;
	}

	private void createTokens(Properties properties) {
		Map<String, Map<String, String>> providersCredentials = 
				FUCPluginHelper.getMemberCredentials(properties, null);
		for (String key : providersCredentials.keySet()) {
			Map<String, String> credentials = providersCredentials.get(key);
			createToken(credentials).getAccessId();
		}
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		if ((userCredentials == null) || (userCredentials.get(SUBSCRIPTION_ID_PATH) == null)
				|| (userCredentials.get(KEYSTORE_PASSWORD) == null) 
				|| (userCredentials.get(KEYSTORE_PATH) == null)) {
			LOGGER.warn("User credentials can't be null");
			return null;
		}
		String accessID = getAccessID(userCredentials);
		if (accessID == null) {
			LOGGER.debug("creating new access ID...");
			accessID = UUID.randomUUID().toString();
			LOGGER.debug(accessID + " is the new access ID");
			this.accessIDs.put(accessID, userCredentials);
		}
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, userCredentials.get(SUBSCRIPTION_ID_PATH));
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, userCredentials.get(KEYSTORE_PATH));
		attributes.put(AzureAttributes.KEYSTORE_PASSWORD_KEY, userCredentials.get(KEYSTORE_PASSWORD));		
		return new Token(accessID, USER, new Date(), attributes);			
	}
	
	protected String getAccessID(Map<String, String> userCredentials) {
		for (String accessId : accessIDs.keySet()) {
			Map<String, String> credentials = accessIDs.get(accessId);
			if (credentials.get(SUBSCRIPTION_ID_PATH).equals(userCredentials.get(SUBSCRIPTION_ID_PATH)) &&
			credentials.get(KEYSTORE_PATH).equals(userCredentials.get(KEYSTORE_PATH)) && 
			credentials.get(KEYSTORE_PASSWORD).equals(userCredentials.get(KEYSTORE_PASSWORD))){
				return accessId;
			}
		}
		return null;
	}

	@Override
	public Token reIssueToken(Token token) {
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		if (accessIDs.get(accessId) == null) {
			throw new  OCCIException(ErrorType.UNAUTHORIZED, "This access id does not exist.");
		}
		return createToken(accessIDs.get(accessId));
	}

	@Override
	public boolean isValid(String accessId) {
		if (properties == null) {
			LOGGER.error("User credentials can't be null");
			throw new OCCIException(ErrorType.BAD_REQUEST, "User credentials can't be null");
		}
		if (accessIDs.get(accessId) == null) {
			return false;
		}
		return true;
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[]{ new Credential(AzureAttributes.SUBSCRIPTION_ID_KEY, true, null),
				new Credential(AzureAttributes.KEYSTORE_PATH_KEY, true, null), 
				new Credential(AzureAttributes.KEYSTORE_PASSWORD_KEY, true, null)};
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
