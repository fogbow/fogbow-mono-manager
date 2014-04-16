package org.fogbowcloud.manager.occi.plugins;

import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.model.KeystoneApplication;
import org.fogbowcloud.manager.occi.model.PluginHelper;
import org.fogbowcloud.manager.occi.plugins.openstack.IdentityOpenStackPlugin;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestIdentityOpenStack {

	private final String KEYSTONE_END_POINT_TOKENS = "http://localhost:"
			+ PluginHelper.PORT_ENDPOINT + KeystoneApplication.TARGET_TOKENS;
	private final String KEYSTONE_END_POINT_AUTH_TOKEN = "http://localhost:"
			+ PluginHelper.PORT_ENDPOINT + KeystoneApplication.TARGET_AUTH_TOKEN;

	private IdentityOpenStackPlugin identityOpenStack;
	private PluginHelper pluginHelper;

	@Before
	public void setUp() throws Exception {
		this.identityOpenStack = new IdentityOpenStackPlugin(KEYSTONE_END_POINT_TOKENS,
				KEYSTONE_END_POINT_AUTH_TOKEN);
		this.pluginHelper = new PluginHelper();
		this.pluginHelper.initializeKeystoneComponent();
	}

	@After
	public void tearDown() throws Exception {
		this.pluginHelper.disconnectComponent();
	}

	@Test
	public void testValidToken() {
		Assert.assertTrue(this.identityOpenStack.isValidToken(PluginHelper.AUTH_TOKEN));
	}

	@Test
	public void testInvalidToken() {
		Assert.assertFalse(identityOpenStack.isValidToken("Invalid Token"));
	}

	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(PluginHelper.USERNAME,
				this.identityOpenStack.getUser(PluginHelper.AUTH_TOKEN));
	}

	@Test(expected = OCCIException.class)
	public void testGetNameUserFromTokenInvalid() {
		this.identityOpenStack.getUser("invalid_token");
	}

	@Test
	public void testGetToken() {
		String token = this.identityOpenStack
				.getToken(PluginHelper.USERNAME, PluginHelper.PASSWORD);
		Assert.assertEquals(PluginHelper.AUTH_TOKEN, token);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongUsername() {
		this.identityOpenStack.getToken("wrong", PluginHelper.USERNAME);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongPassword() {
		this.identityOpenStack.getToken(PluginHelper.PASSWORD, "wrong");
	}
}