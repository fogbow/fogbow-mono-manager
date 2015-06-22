package org.fogbowcloud.manager.core.plugins.identity.cloudstack;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackIdentityPlugin {
	
	private static final String IDENTITY_URL_KEY = "identity_url";
	private static final String CLOUDSTACK_URL = "http://localhost:8080/client/api";
	
	private CloudStackIdentityPlugin createPlugin(HttpClientWrapper httpClient) {
		Properties properties = new Properties();
		properties.put(IDENTITY_URL_KEY, CLOUDSTACK_URL);
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
	
}
