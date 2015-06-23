package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackIdentityPlugin {
	
	private static final String IDENTITY_URL_KEY = "identity_url";
	private static final String CLOUDSTACK_URL = "http://localhost:8080/client/api";
	private static final String FEDERATION_API_KEY = "fogbow";
	private static final String FEDERATION_SECRET_KEY = "secret";
	
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
		Assert.assertEquals("api:key", token.getAccessId());
		Assert.assertEquals("api", token.getUser());
		
	}
	
	@Test
	public void testGetToken() {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		Mockito.doReturn("").when(httpClient).doGet(Mockito.anyString());
		CloudStackIdentityPlugin csip = createPlugin(httpClient);
		Token token = csip.getToken("api:key");
		Assert.assertEquals("api", token.getUser());
	}
	
	@Test
	public void testReissueToken() {
		Token token = new Token(null, null, null, null);
		Token token2 = createPlugin(null).reIssueToken(token);
		Assert.assertEquals(token, token2);
	}
	
	@Test
	public void testIsValid() {
		String accessID = "notvalid";
		HttpClientWrapper httpClientNotValid = Mockito.mock(HttpClientWrapper.class);
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.NOT_FOUND)).
		when(httpClientNotValid).doGet(Mockito.anyString());
		CloudStackIdentityPlugin csip = createPlugin(httpClientNotValid);
		Assert.assertEquals(false, csip.isValid(accessID));
		accessID = "not:valid";
		Assert.assertEquals(false, csip.isValid(accessID));
		HttpClientWrapper httpClientValid = Mockito.mock(HttpClientWrapper.class);
		Mockito.doReturn("").when(httpClientValid).doGet(Mockito.anyString());
		csip = createPlugin(httpClientValid);
		Assert.assertEquals(true, csip.isValid(accessID));
	}
	
	@Test
	public void testCreateFederationUser() {
		CloudStackIdentityPlugin csip = createPlugin(null);
		Token token = csip.createFederationUserToken();
		Assert.assertEquals(FEDERATION_API_KEY + ":" + FEDERATION_SECRET_KEY, 
				token.getAccessId());
		Assert.assertEquals(FEDERATION_API_KEY, token.getUser());
	}
	
	@Test
	public void testGetCredentials() {
		CloudStackIdentityPlugin csip = createPlugin(null);
		Credential[] credentials = csip.getCredentials();
		Assert.assertEquals(new Credential
				(CloudStackIdentityPlugin.API_KEY, true, null), credentials[0]);
		Assert.assertEquals(new Credential
				(CloudStackIdentityPlugin.SECRET_KEY, true, null), credentials[1]);
	}
	
	@Test
	public void testGetAuthenticationURIandFowardableToken() {
		CloudStackIdentityPlugin csip = createPlugin(null);
		Assert.assertEquals(null, csip.getAuthenticationURI());
		Assert.assertEquals(null, csip.getForwardableToken(new Token(null, null, null, null)));
	}
	
}
