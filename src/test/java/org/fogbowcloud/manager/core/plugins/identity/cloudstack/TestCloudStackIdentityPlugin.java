package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.compute.cloudstack.CloudStackTestHelper;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackIdentityPlugin {
	
	private static final String VALID_ACCESS_ID = "api:key";
	private static final String NOT_VALID_ACCESS_ID_BAD_FORMAT = "notvalid";
	private static final String NOT_VALID_ACCESS_ID_NOT_AUTHORIZED = "not:valid";
	
	private static final String IDENTITY_URL_KEY = "identity_url";
	private static final String CLOUDSTACK_URL = "http://localhost:8080/client/api";
	private static final String FEDERATION_API_KEY = "fogbow";
	private static final String FEDERATION_SECRET_KEY = "secret";
	
	private static String RESPONSE_UNAUTHORIZED;
	
	static {
		try {
			RESPONSE_UNAUTHORIZED = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.unauthorized");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private CloudStackIdentityPlugin createPlugin(HttpClientWrapper httpClient) {
		Properties properties = new Properties();
		properties.put(IDENTITY_URL_KEY, CLOUDSTACK_URL);
		properties.put(CloudStackIdentityPlugin.FEDERATION_USER_API_KEY,FEDERATION_API_KEY);
		properties.put(CloudStackIdentityPlugin.FEDERATION_USER_SECRET_KEY, FEDERATION_SECRET_KEY);
		if(httpClient == null) {		
			return new CloudStackIdentityPlugin(properties);
		} else {
			return new CloudStackIdentityPlugin(properties, httpClient);
		}
	}
	
	@Test
	public void testCreateToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(CloudStackIdentityPlugin.API_KEY, "api");
		tokenAttributes.put(CloudStackIdentityPlugin.SECRET_KEY, "key");
		Token token = createPlugin(null).createToken(tokenAttributes);
		Assert.assertEquals(VALID_ACCESS_ID, token.getAccessId());
		Assert.assertEquals("api", token.getUser());
	}
	
	@Test(expected=OCCIException.class)
	public void testCreateTokenWithNullAttributes() {
		createPlugin(null).createToken(null);
	}
	
	@Test(expected=OCCIException.class)
	public void testCreateTokenWithNullApiKey() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(CloudStackIdentityPlugin.SECRET_KEY, "key");
		createPlugin(null).createToken(tokenAttributes);
	}
	
	@Test(expected=OCCIException.class)
	public void testCreateTokenWithNullSecretKey() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(CloudStackIdentityPlugin.API_KEY, "key");
		createPlugin(null).createToken(tokenAttributes);
	}
	
	@Test
	public void testGetToken() {
		String  reissueTokenUrl = CloudStackTestHelper.createURL(
				CloudStackIdentityPlugin.REISSUE_COMMAND);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		Token mockToken = new Token(VALID_ACCESS_ID, null, null, null);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, mockToken, 
				CloudStackTestHelper.GET, reissueTokenUrl, "", 200);
		
		CloudStackIdentityPlugin identityPlugin = createPlugin(httpClient);
		Token token = identityPlugin.getToken(VALID_ACCESS_ID);	
		Assert.assertEquals("api", token.getUser());
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenBadRequest() {
		HttpClientWrapper httpClientNotValid = Mockito.mock(HttpClientWrapper.class);
		CloudStackIdentityPlugin identityPlugin = createPlugin(httpClientNotValid);
		identityPlugin.getToken(NOT_VALID_ACCESS_ID_BAD_FORMAT);
	}
	
	@Test(expected=OCCIException.class)
	public void testGetTokenUnauthorized() {
		String  reissueTokenUrl = CloudStackTestHelper.createURL(
				CloudStackIdentityPlugin.REISSUE_COMMAND);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		Token mockToken = new Token(NOT_VALID_ACCESS_ID_NOT_AUTHORIZED, null, null, null);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, mockToken, 
				CloudStackTestHelper.GET, reissueTokenUrl, RESPONSE_UNAUTHORIZED, 401);
		
		CloudStackIdentityPlugin identityPlugin = createPlugin(httpClient);
		identityPlugin.getToken(NOT_VALID_ACCESS_ID_NOT_AUTHORIZED);	
	}
	
	@Test
	public void testReissueToken() {
		Token token = new Token(null, null, null, null);
		Token token2 = createPlugin(null).reIssueToken(token);
		Assert.assertEquals(token, token2);
	}
	
	
	@Test
	public void testIsValidBadFormat() {
		HttpClientWrapper httpClientNotValid = Mockito.mock(HttpClientWrapper.class);
		CloudStackIdentityPlugin identityPlugin = createPlugin(httpClientNotValid);
		Assert.assertEquals(false, identityPlugin.isValid(NOT_VALID_ACCESS_ID_BAD_FORMAT));
	}
	
	@Test
	public void testIsValidUnauthorized() {
		String  reissueTokenUrl = CloudStackTestHelper.createURL(
				CloudStackIdentityPlugin.REISSUE_COMMAND);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		Token mockToken = new Token(NOT_VALID_ACCESS_ID_NOT_AUTHORIZED, null, null, null);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, mockToken, 
				CloudStackTestHelper.GET, reissueTokenUrl, RESPONSE_UNAUTHORIZED, 401);
		
		CloudStackIdentityPlugin identityPlugin = createPlugin(httpClient);
		Assert.assertEquals(false, identityPlugin.isValid(NOT_VALID_ACCESS_ID_NOT_AUTHORIZED));
	}
	
	@Test
	public void testIsValid() {
		String  reissueTokenUrl = CloudStackTestHelper.createURL(
				CloudStackIdentityPlugin.REISSUE_COMMAND);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		Token mockToken = new Token(VALID_ACCESS_ID, null, null, null);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, mockToken, 
				CloudStackTestHelper.GET, reissueTokenUrl, "", 200);
		
		CloudStackIdentityPlugin identityPlugin = createPlugin(httpClient);
		Assert.assertEquals(true, identityPlugin.isValid(VALID_ACCESS_ID));
	}
	
	@Test
	public void testCreateFederationUser() {
		CloudStackIdentityPlugin identityPlugin = createPlugin(null);
		Token token = identityPlugin.createFederationUserToken();
		Assert.assertEquals(FEDERATION_API_KEY + ":" + FEDERATION_SECRET_KEY, 
				token.getAccessId());
		Assert.assertEquals(FEDERATION_API_KEY, token.getUser());
	}
	
	@Test(expected=OCCIException.class)
	public void testCreateFederationUserWithoutProperties() {
		Properties properties = new Properties();
		properties.put(IDENTITY_URL_KEY, CLOUDSTACK_URL);
		CloudStackIdentityPlugin identityPlugin = new CloudStackIdentityPlugin(properties);
		Token token = identityPlugin.createFederationUserToken();
		Assert.assertNull(token.getUser());
	}
	
	@Test
	public void testGetCredentials() {
		CloudStackIdentityPlugin identityPlugin = createPlugin(null);
		Credential[] credentials = identityPlugin.getCredentials();
		Assert.assertEquals(new Credential
				(CloudStackIdentityPlugin.API_KEY, true, null), credentials[0]);
		Assert.assertEquals(new Credential
				(CloudStackIdentityPlugin.SECRET_KEY, true, null), credentials[1]);
	}
	
	@Test
	public void testGetAuthenticationURI() {
		CloudStackIdentityPlugin identityPlugin = createPlugin(null);
		Assert.assertEquals(null, identityPlugin.getAuthenticationURI());
	}
	
	@Test
	public void testGetFowardableToken() {
		CloudStackIdentityPlugin identityPlugin = createPlugin(null);
		Assert.assertEquals(null, identityPlugin.getForwardableToken(new Token(null, null, null, null)));
	}
	
}
