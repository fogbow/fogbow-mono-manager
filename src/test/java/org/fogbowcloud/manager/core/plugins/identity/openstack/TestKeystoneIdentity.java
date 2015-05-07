package org.fogbowcloud.manager.core.plugins.identity.openstack;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.identity.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.json.JSONException;
import org.json.JSONObject;
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
	public void testCreateToken() throws JSONException {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.USERNAME, PluginHelper.USERNAME);
		tokenAttributes.put(KeystoneIdentityPlugin.PASSWORD, PluginHelper.USER_PASS);
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_NAME, PluginHelper.TENANT_NAME);
		Token token = this.keystoneIdentity.createToken(tokenAttributes);
		
		String user = token.getUser();
		Date expirationDate = token.getExpirationDate();
		
		String plainJson = new String(Base64.decodeBase64(token.getAccessId()
				.getBytes(Charsets.UTF_8)), Charsets.UTF_8);
		JSONObject tokenJson = new JSONObject(plainJson);
		
		Assert.assertEquals(PluginHelper.USERNAME, user);		
		Assert.assertEquals(PluginHelper.ACCESS_ID, tokenJson.optString(KeystoneIdentityPlugin.ACCESS_PROP));
		Assert.assertEquals(PluginHelper.TENANT_ID, token.get(KeystoneIdentityPlugin.TENANT_ID));
		Assert.assertEquals(PluginHelper.TENANT_ID, tokenJson.optString(KeystoneIdentityPlugin.TENANT_ID));
		Assert.assertEquals(PluginHelper.TENANT_NAME, token.get(KeystoneIdentityPlugin.TENANT_NAME));
		Assert.assertEquals(PluginHelper.TENANT_NAME, tokenJson.optString(KeystoneIdentityPlugin.TENANT_NAME));
		
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
		Token token2 = this.keystoneIdentity.reIssueToken(
				this.keystoneIdentity.getToken(token.getAccessId()));
		
		String authToken = token2.getAccessId();
		String tenantID = token2.get(KeystoneIdentityPlugin.TENANT_ID);
		Date expirationDate = token2.getExpirationDate();
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
	
	@Test(expected=JSONException.class)
	public void testGetTokenFederationUserShallNotReturnEncodedJSON() throws JSONException {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.IDENTITY_URL, KEYSTONE_URL);
		properties.put(ConfigurationConstants.FEDERATION_USER_NAME_KEY, PluginHelper.USERNAME);
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_KEY, PluginHelper.USER_PASS);
		this.keystoneIdentity = new KeystoneIdentityPlugin(properties);
		
		Token federationUserToken = this.keystoneIdentity.createFederationUserToken();
		new JSONObject(federationUserToken.getAccessId());
	}
	
	@Test
	public void testGetTokenWithNoJson() throws JSONException {
		Token token = this.keystoneIdentity.getToken(PluginHelper.ACCESS_ID);
		Assert.assertNotNull(token);
		Assert.assertEquals(PluginHelper.USERNAME, token.getUser());		
		Assert.assertEquals(PluginHelper.TENANT_ID, token.get(KeystoneIdentityPlugin.TENANT_ID));
		Assert.assertEquals(PluginHelper.TENANT_NAME, token.get(KeystoneIdentityPlugin.TENANT_NAME));
	}
}