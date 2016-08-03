package org.fogbowcloud.manager.core.plugins.identity.simpletoken;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSimpleTokenIdentityPlugin {

	private SimpleTokenIdentityPlugin simpleTokenIdentityPlugin;
	private String DEFAULT_USER = "user";
	private String DEFAULT_VALID_TOKEN = "4982Y6BGFC-YHDsudhgu";
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put(SimpleTokenIdentityPlugin.SIMPLE_TOKEN_IDENTITY_VALID_TOKEN_ID, DEFAULT_VALID_TOKEN);
		simpleTokenIdentityPlugin = new SimpleTokenIdentityPlugin(properties);
	}
	
	@Test
	public void testCreateToken() {
		Map<String, String> userCredentials = new HashMap<String, String>();
		String tokenId = DEFAULT_VALID_TOKEN + "@" + DEFAULT_USER;
		userCredentials.put(SimpleTokenIdentityPlugin.TOKEN, tokenId);
		Token token = simpleTokenIdentityPlugin.createToken(userCredentials);
		Assert.assertEquals(tokenId, token.getAccessId());
		Assert.assertEquals(DEFAULT_USER, token.getUser());
	}
	
	@Test
	public void testGetToken() {
		Map<String, String> userCredentials = new HashMap<String, String>();
		String accessId = DEFAULT_VALID_TOKEN + "@" + DEFAULT_USER;
		userCredentials.put(SimpleTokenIdentityPlugin.TOKEN, accessId);
		Token token = simpleTokenIdentityPlugin.getToken(accessId);
		Assert.assertEquals(accessId, token.getAccessId());
		Assert.assertEquals(DEFAULT_USER, token.getUser());
	}
	
	@Test
	public void testIsValidTrue() {
		Map<String, String> userCredentials = new HashMap<String, String>();
		String accessId = DEFAULT_VALID_TOKEN + "@" + DEFAULT_USER;
		userCredentials.put(SimpleTokenIdentityPlugin.TOKEN, accessId);
		Assert.assertTrue(simpleTokenIdentityPlugin.isValid(accessId));
	}	
	
	@Test
	public void testIsValidFalse() {
		Map<String, String> userCredentials = new HashMap<String, String>();
		String accessId = "Wrong" + "@" + DEFAULT_USER;
		userCredentials.put(SimpleTokenIdentityPlugin.TOKEN, accessId);
		Assert.assertFalse(simpleTokenIdentityPlugin.isValid(accessId));
	}		
	
	@Test
	public void testReIssueToken() {
		String accessId = DEFAULT_VALID_TOKEN + "@" + DEFAULT_USER;
		Token token = new Token(accessId, DEFAULT_USER, new Date(), new HashMap<String, String>());
		Assert.assertEquals(token, simpleTokenIdentityPlugin.reIssueToken(token));
	}
	
}
