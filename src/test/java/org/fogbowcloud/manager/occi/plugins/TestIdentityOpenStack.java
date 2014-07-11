package org.fogbowcloud.manager.occi.plugins;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIException;
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
		properties.put(ConfigurationConstants.IDENTITY_URL, KEYSTONE_URL);
		
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
		Assert.assertEquals(PluginHelper.USERNAME,
				this.identityOpenStack.getToken(PluginHelper.ACCESS_ID).getUser());
	}

	@Test(expected = ResourceException.class)
	public void testInvalidToken() {
		identityOpenStack.getToken("Invalid Token");
	}

	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(PluginHelper.USERNAME,
				this.identityOpenStack.getToken(PluginHelper.ACCESS_ID).getUser());
	}

	@Test(expected = ResourceException.class)
	public void testGetNameUserFromTokenInvalid() {
		this.identityOpenStack.getToken("invalid_token");
	}

	@Test
	public void testGetToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OpenStackIdentityPlugin.USER_KEY, PluginHelper.USERNAME);
		tokenAttributes.put(OpenStackIdentityPlugin.PASSWORD_KEY, PluginHelper.USER_PASS);
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_NAME_KEY, PluginHelper.TENANT_NAME);
		Token token = this.identityOpenStack.createToken(tokenAttributes);
		String authToken = token.getAccessId();
		String user = token.getUser();
		String tenantID = token.get(OpenStackIdentityPlugin.TENANT_ID_KEY);
		Date expirationDate = token.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.USERNAME, user);		
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(OpenStackIdentityPlugin
				.getDateOpenStackFormat(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION),
				OpenStackIdentityPlugin.getDateOpenStackFormat(expirationDate));
	}

	@Test
	public void testUpgradeToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OpenStackIdentityPlugin.USER_KEY, PluginHelper.USERNAME);
		tokenAttributes.put(OpenStackIdentityPlugin.PASSWORD_KEY, PluginHelper.USER_PASS);
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_NAME_KEY, PluginHelper.TENANT_NAME);
		Token token = this.identityOpenStack.createToken(tokenAttributes);
		String authToken = token.getAccessId();
		String tenantID = token.get(OpenStackIdentityPlugin.TENANT_ID_KEY);
		Date expirationDate = token.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(OpenStackIdentityPlugin
				.getDateOpenStackFormat(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION),
				OpenStackIdentityPlugin.getDateOpenStackFormat(expirationDate));
		
		Token token2 = this.identityOpenStack.reIssueToken(token);
		authToken = token2.getAccessId();
		tenantID = token2.get(OpenStackIdentityPlugin.TENANT_ID_KEY);
		expirationDate = token2.getExpirationDate();
		Assert.assertEquals(PluginHelper.ACCESS_ID, authToken);
		Assert.assertEquals(PluginHelper.TENANT_ID, tenantID);
		Assert.assertEquals(OpenStackIdentityPlugin
				.getDateOpenStackFormat(DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION),
				OpenStackIdentityPlugin.getDateOpenStackFormat(expirationDate));
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongUsername() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OpenStackIdentityPlugin.USER_KEY, "wrong");
		tokenAttributes.put(OpenStackIdentityPlugin.PASSWORD_KEY, PluginHelper.USER_PASS);
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_NAME_KEY, "");
		this.identityOpenStack.createToken(tokenAttributes);
	}

	@Test(expected = OCCIException.class)
	public void testGetTokenWrongPassword() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(OpenStackIdentityPlugin.USER_KEY, PluginHelper.USERNAME);
		tokenAttributes.put(OpenStackIdentityPlugin.PASSWORD_KEY, "worng");
		tokenAttributes.put(OpenStackIdentityPlugin.TENANT_NAME_KEY, "");
		this.identityOpenStack.createToken(tokenAttributes);
	}
}