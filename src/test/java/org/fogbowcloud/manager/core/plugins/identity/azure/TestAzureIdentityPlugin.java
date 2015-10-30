package org.fogbowcloud.manager.core.plugins.identity.azure;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.localcredentails.LocalCredentialsHelper;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestAzureIdentityPlugin {
	 
	private static final String PREFIX_PROPERTIES = LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + 
			LocalCredentialsHelper.FOGBOW_DEFAULTS + LocalCredentialsHelper.UNDERLINE;
	private static final String SUBSCRIPTION_ID_VALUE = "subscription_id";
	private static final String KEY_STORE_PATH_VALUE = "home/test/value";
	private static final String KEYSTORE_PASSWORD_VALUE = "password";
	
	private Properties createProperties(Properties extraProperties) {
		Properties properties = new Properties();
		if (extraProperties != null) {
			properties.putAll(extraProperties);
		}
		properties.put(PREFIX_PROPERTIES + AzureIdentityPlugin.
				SUBSCRIPTION_ID_PATH, SUBSCRIPTION_ID_VALUE);
		properties.put(PREFIX_PROPERTIES + AzureIdentityPlugin.
				KEYSTORE_PATH, KEY_STORE_PATH_VALUE);
		properties.put(PREFIX_PROPERTIES + AzureIdentityPlugin.
				KEYSTORE_PASSWORD, KEYSTORE_PASSWORD_VALUE);
		return properties;
	}
	
	private static String validAccessId = "access_id";
	private static final String NOT_VALID_ACCESS_ID = "not_valid";
	private static final String ACCESS_ID_PROPERTY = "local_proxy_account_access_id";
	private static final String ACCESS_ID_VALUE_PROPERTY = "access_id";
	private static final String DEFAULT_USER = "Fogbow";
	
	@Test(expected=OCCIException.class)
	public void testGetTokenNullProperties() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(null);
		azureIdentityPlugin.getToken(validAccessId);
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenNullAccessID() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(createProperties(null));
		azureIdentityPlugin.getToken(validAccessId);
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenInvalidAccessID() {
		Properties extraProperties = new Properties();
		extraProperties.put(ACCESS_ID_PROPERTY, ACCESS_ID_VALUE_PROPERTY);
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(createProperties(extraProperties));
		azureIdentityPlugin.getToken(NOT_VALID_ACCESS_ID);
	}
	
	@Test
	public void testGetToken() {
		Properties extraProperties = new Properties();
		extraProperties.put(ACCESS_ID_PROPERTY, ACCESS_ID_VALUE_PROPERTY);
		Properties properties = createProperties(extraProperties);
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(properties);
		String accessId = azureIdentityPlugin.getAccessID(LocalCredentialsHelper
				.getLocalCredentials(properties, null).get(LocalCredentialsHelper.FOGBOW_DEFAULTS));
		Token token = azureIdentityPlugin.getToken(accessId);
		Assert.assertEquals(DEFAULT_USER, token.getUser());
		Assert.assertEquals(accessId, token.getAccessId());
		Assert.assertEquals(4, token.getAttributes().size());
	}
	
	@Test
	public void testReissueToken() {
		Token token = new Token(null, null, null, null);
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(new Properties());
		Assert.assertEquals(token, azureIdentityPlugin.reIssueToken(token));
	}
	
	@Test
	public void testCreateToken() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(null);
		Map<String, String> credentails = new HashMap<String, String>();
		credentails.put(AzureIdentityPlugin.SUBSCRIPTION_ID_PATH, SUBSCRIPTION_ID_VALUE);
		credentails.put(AzureIdentityPlugin.KEYSTORE_PATH, KEY_STORE_PATH_VALUE);
		credentails.put(AzureIdentityPlugin.KEYSTORE_PASSWORD, KEYSTORE_PASSWORD_VALUE);
		Token token = azureIdentityPlugin.createToken(credentails);
		Assert.assertEquals(azureIdentityPlugin.getAccessID(credentails), token.getAccessId());
		
		token = azureIdentityPlugin.createToken(credentails);
		Assert.assertEquals(azureIdentityPlugin.getAccessID(credentails), token.getAccessId());		
	}
	
	@Test
	public void testGetCredentials() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(new Properties());
		Credential[] credentials  = azureIdentityPlugin.getCredentials();
		Assert.assertEquals(AzureAttributes.SUBSCRIPTION_ID_KEY, credentials[0].getName());
		Assert.assertEquals(AzureAttributes.KEYSTORE_PATH_KEY, credentials[1].getName());
		Assert.assertEquals(AzureAttributes.KEYSTORE_PASSWORD_KEY, credentials[2].getName());
	}
	
	@Test
	public void testAuthURI() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(new Properties());
		Assert.assertNull(azureIdentityPlugin.getAuthenticationURI());
	}
	
	@Test
	public void testGetFordwableToken() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(new Properties());
		Assert.assertNull(azureIdentityPlugin.getForwardableToken
		(new Token(null, null, null, null)));
	}
	
	@Test(expected=OCCIException.class)
	public void testIsValidNullProperties() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(null);
		azureIdentityPlugin.isValid(null);
	}
	
	@Test
	public void isValidNullAccessId() {
		AzureIdentityPlugin azureIdentityPlugin = new AzureIdentityPlugin(new Properties());
		Assert.assertEquals(false, azureIdentityPlugin.isValid(null));
	}
	
}
