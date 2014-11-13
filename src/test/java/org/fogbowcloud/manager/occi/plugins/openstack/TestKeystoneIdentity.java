package org.fogbowcloud.manager.occi.plugins.openstack;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class TestKeystoneIdentity {

	private final String KEYSTONE_URL = "http://localhost:" + PluginHelper.PORT_ENDPOINT;
	private KeystoneIdentityPlugin keystoneIdentity;
	private PluginHelper pluginHelper;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.IDENTITY_URL, KEYSTONE_URL);
		
		this.keystoneIdentity = new KeystoneIdentityPlugin(properties);
		this.pluginHelper = new PluginHelper();
		this.pluginHelper.initializeKeystoneComponent();
	}

	@After
	public void tearDown() throws Exception {
		this.pluginHelper.disconnectComponent();
	}

	@Test
	public void testValidToken() {
		Assert.assertEquals(PluginHelper.USERNAME,
				this.keystoneIdentity.getToken(PluginHelper.ACCESS_ID).getUser());
	}

	@Test(expected = ResourceException.class)
	public void testInvalidToken() {
		keystoneIdentity.getToken("Invalid Token");
	}

	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(PluginHelper.USERNAME,
				this.keystoneIdentity.getToken(PluginHelper.ACCESS_ID).getUser());
	}

	@Test(expected = ResourceException.class)
	public void testGetNameUserFromTokenInvalid() {
		this.keystoneIdentity.getToken("invalid_token");
	}

	@Test
	public void testGetToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.USERNAME, PluginHelper.USERNAME);
		tokenAttributes.put(KeystoneIdentityPlugin.PASSWORD, PluginHelper.USER_PASS);
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, PluginHelper.TENANT_NAME);
		Token token = this.keystoneIdentity.createToken(tokenAttributes);
		String authToken = token.getAccessId();
		String user = token.getUser();
		String tenantID = token.get(KeystoneIdentityPlugin.TENANT_ID);
		Date expirationDate = token.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.USERNAME, user);		
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(KeystoneIdentityPlugin
				.getDateOpenStackFormat(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION),
				KeystoneIdentityPlugin.getDateOpenStackFormat(expirationDate));
	}

	@Test
	public void testUpgradeToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.USERNAME, PluginHelper.USERNAME);
		tokenAttributes.put(KeystoneIdentityPlugin.PASSWORD, PluginHelper.USER_PASS);
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, PluginHelper.TENANT_NAME);
		Token token = this.keystoneIdentity.createToken(tokenAttributes);
		String authToken = token.getAccessId();
		String tenantID = token.get(KeystoneIdentityPlugin.TENANT_ID);
		Date expirationDate = token.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(KeystoneIdentityPlugin
				.getDateOpenStackFormat(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION),
				KeystoneIdentityPlugin.getDateOpenStackFormat(expirationDate));
		
		Token token2 = this.keystoneIdentity.reIssueToken(token);
		authToken = token2.getAccessId();
		tenantID = token2.get(KeystoneIdentityPlugin.TENANT_ID);
		expirationDate = token2.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(KeystoneIdentityPlugin
				.getDateOpenStackFormat(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION),
				KeystoneIdentityPlugin.getDateOpenStackFormat(expirationDate));
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongUsername() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.USERNAME, "wrong");
		tokenAttributes.put(KeystoneIdentityPlugin.PASSWORD, PluginHelper.USER_PASS);
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, "");
		this.keystoneIdentity.createToken(tokenAttributes);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongPassword() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.USERNAME, PluginHelper.USERNAME);
		tokenAttributes.put(KeystoneIdentityPlugin.PASSWORD, "worng");
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, "");
		this.keystoneIdentity.createToken(tokenAttributes);
	}
	
	@Test
	public void testGetTokenFederationUserUsingADifferentURL() {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.IDENTITY_URL, "http://wrong:8080");
		properties.put(ConfigurationConstants.FEDERATION_USER_NAME_KEY, PluginHelper.USERNAME);
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_KEY, PluginHelper.USER_PASS);
		this.keystoneIdentity = new KeystoneIdentityPlugin(properties);
		
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.USERNAME, PluginHelper.USERNAME);
		tokenAttributes.put(KeystoneIdentityPlugin.PASSWORD, PluginHelper.USER_PASS);
		tokenAttributes.put(KeystoneIdentityPlugin.AUTH_URL, KEYSTONE_URL);
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, PluginHelper.TENANT_NAME);
		this.keystoneIdentity.createToken(tokenAttributes);
		
		try {
			this.keystoneIdentity.createFederationUserToken();
			Assert.fail();
		} catch (OCCIException e) {
			Assert.assertEquals(ResponseConstants.UNKNOWN_HOST, e.getStatus().getDescription());
		}
	}
}