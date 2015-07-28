package org.fogbowcloud.manager.core.plugins.identity.azure;

import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestAzureIdentityPlugin {
	// TODO: Tests get token
	private final static String SUBSCRIPTION_ID_PROPERTY = "local_proxy_account_subscription_id"; 
	private final static String SUBSCRIPTION_ID_VALUE = "subscription_id";
	private final static String KEY_STORE_PATH_PROPERTY = "local_proxy_account_keystore_path";
	private final static String KEY_STORE_PATH_VALUE = "home/test/value";
	private final static String KEYSTORE_PASSWORD = "local_proxy_account_keystore_password";
	private final static String KEYSTORE_PASSWORD_VALUE = "password";
	
	private Properties createProperties(Properties extraProperties) {
		Properties properties = new Properties();
		if (extraProperties != null) {
			properties.putAll(extraProperties);
		}
		properties.put(SUBSCRIPTION_ID_PROPERTY, SUBSCRIPTION_ID_VALUE);
		properties.put(KEY_STORE_PATH_PROPERTY, KEY_STORE_PATH_VALUE);
		properties.put(KEYSTORE_PASSWORD, KEYSTORE_PASSWORD_VALUE);
		return properties;
	}
	
	private static final String ACCESS_ID_PROPERTY = "local_proxy_account_access_id";
	private final static String ACCESS_ID_VALUE = "anyValue";
	
	@Test
	public void testCreateFederationToken() {
		Properties extraProperties = new Properties();
		extraProperties.put(ACCESS_ID_PROPERTY, ACCESS_ID_VALUE);
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(createProperties(extraProperties));
		Token token = azureIdentityPlugin.createFederationUserToken();
		Assert.assertEquals("Fogbow", token.getUser());
		Assert.assertEquals(ACCESS_ID_VALUE, token.getAccessId());
		Assert.assertEquals(3, token.getAttributes().size());
	}
	
	@Test
	public void testCreateFederationTokenWithoutAccessIDSet() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(createProperties(null));
		Token token = azureIdentityPlugin.createFederationUserToken();
		Assert.assertEquals("Fogbow", token.getUser());
		Assert.assertNotNull(token.getAccessId());
		Assert.assertEquals(3, token.getAttributes().size());
	}
	
	@Test
	public void testReissueToken() {
		Token token = new Token(null, null, null, null);
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(new Properties());
		Assert.assertEquals(token,
				azureIdentityPlugin.reIssueToken(token));
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testCreateToken() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(new Properties());
		azureIdentityPlugin.createToken(new HashMap<String, String>());
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testGetCredentials() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(new Properties());
		azureIdentityPlugin.getCredentials();
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testAuthURI() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(new Properties());
		azureIdentityPlugin.getAuthenticationURI();
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testGetFordwableToken() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(new Properties());
		azureIdentityPlugin.getForwardableToken
		(new Token(null, null, null, null));
	}
	
	@Test(expected=OCCIException.class)
	public void testIsValidNullProperties() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(null);
		azureIdentityPlugin.isValid(null);
	}
	
	@Test
	public void isValidNullAccessId() {
		AzureIdentityPlugin azureIdentityPlugin = 
				new AzureIdentityPlugin(new Properties());
		Assert.assertEquals(false,
				azureIdentityPlugin.isValid(Mockito.anyString()));
	}
	
}
