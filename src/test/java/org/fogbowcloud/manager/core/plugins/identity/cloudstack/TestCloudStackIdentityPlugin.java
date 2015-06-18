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
	
	

	public HttpClientWrapper createClientMock(String url) {
		HttpClientWrapper hcw = Mockito.mock(HttpClientWrapper.class);
		Mockito.when(hcw.doPost(url)).thenReturn("{ \"loginresponse\" : { \"timeout\" : \"1800\", " +
			"\"lastname\" : \"cloud\", \"registered\" : \"false\", \"username\" : "
			+ "\"user\", \"firstname\" : \"admin\", \"domainid\" : \"ae389e11-1385-11e5-be87-fa163ec5cca2\", "
			+"\"userid\" : \"879979f7-1392-11e5-be87-fa163ec5cca2\", \"type\" : \"1\", "
			+"\"sessionkey\" : \"eZNcMWVo9ualhWca3uk1ZxpWXZg=\", \"account\" : \"admin\" } }");
		return hcw;
	}
	
	@Test
	public void testCreateToken() {
		Properties properties = new Properties();
		properties.put(IDENTITY_URL_KEY, CLOUDSTACK_URL);
		CloudStackIdentityPlugin cloudstackIdentity = 
				new CloudStackIdentityPlugin(properties, 
						createClientMock("http://localhost:8080/client/api?command="
								+ "login&username=user&password=password&response=json"));
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(CloudStackIdentityPlugin.USER, "user");
		tokenAttributes.put(CloudStackIdentityPlugin.PASSWORD, "password");
		Token token = cloudstackIdentity.createToken(tokenAttributes);
		Assert.assertEquals("eZNcMWVo9ualhWca3uk1ZxpWXZg=", token.getAccessId());
		Assert.assertEquals("user", token.getUser());
		
	}

}
