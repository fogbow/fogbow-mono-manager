package org.fogbowcloud.manager.core.plugins.authorization.userwhitelist;

import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;

public class TestUserWhiteListAuthorizationPlugin {
	
	@Test
	public void testIsAuthorized() {
		String userIdOne = "one";
		String userIdTwo = "two";
		
		Properties properties = new Properties();
		properties.put(UserWhiteListAuthorizationPlugin.AUTHORIZATION_USER_WHITELIST, userIdOne + "," + userIdTwo);
		UserWhiteListAuthorizationPlugin userWhiteListAuthorizationPlugin = new UserWhiteListAuthorizationPlugin(properties);
		
		Token token = new Token("accessId", new Token.User(userIdOne, "name"), new Date(), new HashMap<String, String>());
		
		Assert.assertTrue(userWhiteListAuthorizationPlugin.isAuthorized(token));
	}

	@Test
	public void testIsNotAuthorized() {
		String userIdOne = "one";
		String userIdTwo = "two";
		
		Properties properties = new Properties();
		properties.put(UserWhiteListAuthorizationPlugin.AUTHORIZATION_USER_WHITELIST, userIdOne + "," + userIdTwo);
		UserWhiteListAuthorizationPlugin userWhiteListAuthorizationPlugin = new UserWhiteListAuthorizationPlugin(properties);
		
		Token token = new Token("accessId", new Token.User("worng", "name"), new Date(), new HashMap<String, String>());
		
		Assert.assertFalse(userWhiteListAuthorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testIsAuthorizedEmptyList() {
		Properties properties = new Properties();
		properties.put(UserWhiteListAuthorizationPlugin.AUTHORIZATION_USER_WHITELIST, "");
		UserWhiteListAuthorizationPlugin userWhiteListAuthorizationPlugin = new UserWhiteListAuthorizationPlugin(properties);	
		
		Token token = new Token("accessId", new Token.User("id", "name"), new Date(), new HashMap<String, String>());
		Assert.assertFalse(userWhiteListAuthorizationPlugin.isAuthorized(token));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testInitializePluginWithoutPropertie() {
		Properties properties = new Properties();
		new UserWhiteListAuthorizationPlugin(properties);
	}
	
}
