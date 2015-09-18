package org.fogbowcloud.manager.core.plugins.identity.nocloud;

import java.util.Properties;

import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestNoCloudIdentityPlugin {

	private static final int DEFAULT_SIZE_RANDON_ID_GENERATED = 36;
	private NoCloudIdentityPlugin noCloudIdentityPlugin;
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		this.noCloudIdentityPlugin = new NoCloudIdentityPlugin(properties);
	}
	
	@Test
	public void testCreateToken() {
		Token token = noCloudIdentityPlugin.createToken(null);
		Assert.assertEquals(NoCloudIdentityPlugin.FAKE_USERNAME, token.getUser());
		Assert.assertEquals(DEFAULT_SIZE_RANDON_ID_GENERATED, token.getAccessId().length());
	}
	
	@Test
	public void testReIssueToken() {
		Token token = new Token("accessId", "user", null, null);		
		Assert.assertEquals(token, noCloudIdentityPlugin.reIssueToken(token));
	}
	
	@Test
	public void testGetCredentials() {
		Assert.assertNotNull(noCloudIdentityPlugin.getCredentials());
	}
	
	@Test
	public void tesGetForwardableToken() {
		Assert.assertNull(noCloudIdentityPlugin.getForwardableToken(null));
	}
	
	@Test
	public void testGetAuthemticationURI() {
		Assert.assertNull(noCloudIdentityPlugin.getAuthenticationURI());
	}
	
	@Test
	public void testIsValid() {
		Assert.assertTrue(noCloudIdentityPlugin.isValid(null));
	}
}
