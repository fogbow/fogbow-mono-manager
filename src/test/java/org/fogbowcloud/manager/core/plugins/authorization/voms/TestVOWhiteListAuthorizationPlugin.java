package org.fogbowcloud.manager.core.plugins.authorization.voms;

import java.util.Arrays;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.Fixture;
import org.fogbowcloud.manager.core.plugins.identity.voms.Utils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class TestVOWhiteListAuthorizationPlugin {
	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "test.vo";	
	private String VO_MEMBER = "test.vo";
	private Properties properties;
	private String accessId;
	
	private VOWhiteListAuthorizationPlugin vOWhiteListAuthorizationPlugin;
	
	@Before
	public void setUp() throws Exception {
		this.properties = new Properties();		
		
		properties.put(VomsIdentityPlugin.PROP_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_PASS, VOMS_PASSWORD);
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_SERVER, VOMS_SERVER);
		
		properties.put(VOWhiteListAuthorizationPlugin.AUTHORIZATION_VOMS_WHITELIST, VO_MEMBER);
				
		this.vOWhiteListAuthorizationPlugin = new VOWhiteListAuthorizationPlugin(properties);
		
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		this.accessId = CertificateUtils.generateAccessId(
				Arrays.asList(proxy.getCertificateChain()), proxy.getCredential());		
	}
	
	@Test
	public void testIsAuthorized() {
		Token token = new Token(accessId, "user", null, null);
		Assert.assertTrue(this.vOWhiteListAuthorizationPlugin.isAuthorized(token));
	}

	@Test
	public void testIsNotAuthorized() {
		properties.put(VOWhiteListAuthorizationPlugin.AUTHORIZATION_VOMS_WHITELIST, "vo1,vo2,vo3");
		this.vOWhiteListAuthorizationPlugin = new VOWhiteListAuthorizationPlugin(properties);
		Token token = new Token(accessId, "user", null, null);
		Assert.assertFalse(this.vOWhiteListAuthorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testWrongAccessId() {
		Token token = new Token("123", "user", null, null);
		Assert.assertFalse(this.vOWhiteListAuthorizationPlugin.isAuthorized(token));
	}
	
	@Test
	public void testNullAccessId() {
		Token token = new Token(null, "user", null, null);
		Assert.assertFalse(this.vOWhiteListAuthorizationPlugin.isAuthorized(token));
	}		
	
	@Test(expected=IllegalArgumentException.class)
	public void testPropertiesNull() {
		this.vOWhiteListAuthorizationPlugin = new VOWhiteListAuthorizationPlugin(null);
	}		
	
	@Test(expected=IllegalArgumentException.class)
	public void testWithoutPropertie() {
		properties = new Properties();
		this.vOWhiteListAuthorizationPlugin = new VOWhiteListAuthorizationPlugin(properties);
		this.vOWhiteListAuthorizationPlugin = new VOWhiteListAuthorizationPlugin(null);
	}			
}
