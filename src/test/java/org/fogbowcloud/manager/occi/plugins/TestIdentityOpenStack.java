package org.fogbowcloud.manager.occi.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class TestIdentityOpenStack {

	private final String KEYSTONE_URL = "http://localhost:" + PluginHelper.PORT_ENDPOINT;
	private OpenStackIdentityPlugin identityOpenStack;
	private PluginHelper pluginHelper;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put("identity_openstack_url", KEYSTONE_URL);
		this.identityOpenStack = new OpenStackIdentityPlugin(properties);
		this.pluginHelper = new PluginHelper();
		this.pluginHelper.initializeKeystoneComponent();
	}

	@After
	public void tearDown() throws Exception {
		this.pluginHelper.disconnectComponent();
	}

	@Test
	public void testValidToken() {
		Assert.assertEquals(PluginHelper.USERNAME_FOGBOW,
				this.identityOpenStack.getUser(PluginHelper.AUTH_TOKEN));
	}

	@Test(expected = ResourceException.class)
	public void testInvalidToken() {
		identityOpenStack.getUser("Invalid Token");
	}

	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(PluginHelper.USERNAME_FOGBOW,
				this.identityOpenStack.getUser(PluginHelper.AUTH_TOKEN));
	}

	@Test(expected = ResourceException.class)
	public void testGetNameUserFromTokenInvalid() {
		this.identityOpenStack.getUser("invalid_token");
	}

	@Test
	public void testGetToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, PluginHelper.USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, PluginHelper.PASSWORD_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, PluginHelper.TENANTNAME_FOGBOW);
		Token token = this.identityOpenStack.getToken(tokenAttributes);
		String authToken = token.get(OCCIHeaders.X_TOKEN_ACCESS_ID);
		String tenantID = token.get(OCCIHeaders.X_TOKEN_TENANT_ID);
		String expirationDate = token.get(OCCIHeaders.X_TOKEN_EXPIRATION_DATE);
		Assert.assertEquals(PluginHelper.AUTH_TOKEN, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(PluginHelper.EXPIRATION_DATA, expirationDate);
	}

	@Test
	public void testUpgradeToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, PluginHelper.USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, PluginHelper.PASSWORD_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, PluginHelper.TENANTNAME_FOGBOW);
		Token token = this.identityOpenStack.getToken(tokenAttributes);
		String authToken = token.get(OCCIHeaders.X_TOKEN_ACCESS_ID);
		String tenantID = token.get(OCCIHeaders.X_TOKEN_TENANT_ID);
		String expirationDate = token.get(OCCIHeaders.X_TOKEN_EXPIRATION_DATE);
		Assert.assertEquals(PluginHelper.AUTH_TOKEN, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(PluginHelper.EXPIRATION_DATA, expirationDate);

		Map<String, String> tokenAttributes2 = new HashMap<String, String>();
		tokenAttributes2.put(OCCIHeaders.X_TOKEN_ACCESS_ID, PluginHelper.VALID_TOKEN);
		tokenAttributes2.put(OCCIHeaders.X_TOKEN_TENANT_NAME, PluginHelper.TENANTNAME_FOGBOW);
		Token token2 = this.identityOpenStack.updateToken(tokenAttributes2);
		authToken = token2.get(OCCIHeaders.X_TOKEN_ACCESS_ID);
		tenantID = token2.get(OCCIHeaders.X_TOKEN_TENANT_ID);
		expirationDate = token2.get(OCCIHeaders.X_TOKEN_EXPIRATION_DATE);
		Assert.assertEquals(PluginHelper.AUTH_TOKEN, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(PluginHelper.EXPIRATION_DATA, expirationDate);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongUsername() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, "wrong");
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, PluginHelper.PASSWORD_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, "");
		this.identityOpenStack.getToken(tokenAttributes);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongPassword() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, PluginHelper.USERNAME_FOGBOW);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, "worng");
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, "");
		this.identityOpenStack.getToken(tokenAttributes);
	}
}