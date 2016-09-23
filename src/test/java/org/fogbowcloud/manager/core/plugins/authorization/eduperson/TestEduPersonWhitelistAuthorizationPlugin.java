package org.fogbowcloud.manager.core.plugins.authorization.eduperson;

import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Test;

public class TestEduPersonWhitelistAuthorizationPlugin {

	@Test(expected=IllegalArgumentException.class)
	public void testNoWhiteListDefined() {
		new EduPersonWhitelistAuthorizationPlugin(new Properties());
	}
	
	@Test
	public void testTokenNull() {
		Properties properties = new Properties();
		properties.setProperty("authorization_eduperson_whitelist", "idp2.com,idp3.com");
		EduPersonWhitelistAuthorizationPlugin authorizationPlugin = 
				new EduPersonWhitelistAuthorizationPlugin(properties);
		
		Assert.assertFalse(authorizationPlugin.isAuthorized(null));
	}
	
	@Test
	public void testTokenWithNoEduPersonAttribute() {
		Properties properties = new Properties();
		properties.setProperty("authorization_eduperson_whitelist", "idp2.com,idp3.com");
		EduPersonWhitelistAuthorizationPlugin authorizationPlugin = 
				new EduPersonWhitelistAuthorizationPlugin(properties);
		
		Token token = new Token("accessId", new Token.User("user", "user"), 
				new Date(), new HashMap<String, String>());
		Assert.assertFalse(authorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testTokenWithEduPersonAttributeInWrongFormat() {
		Properties properties = new Properties();
		properties.setProperty("authorization_eduperson_whitelist", "idp2.com,idp3.com");
		EduPersonWhitelistAuthorizationPlugin authorizationPlugin = 
				new EduPersonWhitelistAuthorizationPlugin(properties);
		
		HashMap<String, String> attrs = new HashMap<String, String>();
		attrs.put("eduPersonPrincipalName", "whatever");
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), attrs);
		Assert.assertFalse(authorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testTokenWhitelistEmpty() {
		Properties properties = new Properties();
		properties.setProperty("authorization_eduperson_whitelist", "");
		EduPersonWhitelistAuthorizationPlugin authorizationPlugin = 
				new EduPersonWhitelistAuthorizationPlugin(properties);
		
		HashMap<String, String> attrs = new HashMap<String, String>();
		attrs.put("eduPersonPrincipalName", "whatever@idp1.com");
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), attrs);
		Assert.assertFalse(authorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testTokenFromInstitutionNotInWhitelist() {
		Properties properties = new Properties();
		properties.setProperty("authorization_eduperson_whitelist", "idp2.com,idp3.com");
		EduPersonWhitelistAuthorizationPlugin authorizationPlugin = 
				new EduPersonWhitelistAuthorizationPlugin(properties);
		
		HashMap<String, String> attrs = new HashMap<String, String>();
		attrs.put("eduPersonPrincipalName", "whatever@idp1.com");
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), attrs);
		Assert.assertFalse(authorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testTokenFromInstitutionInWhitelist() {
		Properties properties = new Properties();
		properties.setProperty("authorization_eduperson_whitelist", "idp1.com,idp2.com");
		EduPersonWhitelistAuthorizationPlugin authorizationPlugin = 
				new EduPersonWhitelistAuthorizationPlugin(properties);
		
		HashMap<String, String> attrs = new HashMap<String, String>();
		attrs.put("eduPersonPrincipalName", "whatever@idp1.com");
		Token token = new Token("accessId", new Token.User("user", "user"), new Date(), attrs);
		Assert.assertTrue(authorizationPlugin.isAuthorized(token));
	}
}
