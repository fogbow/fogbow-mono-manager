package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.Fixture;
import org.fogbowcloud.manager.core.plugins.identity.voms.Utils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.localcredentails.LocalCredentialsHelper;
import org.fogbowcloud.manager.core.plugins.localcredentails.VOBasedLocalCrendentialsPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class TestVOBasedLocalCrendetialsPlugin {

	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "test.vo";
	
	private VOBasedLocalCrendentialsPlugin vOBasedLocalCrendentialsPlugin;
	private final String CREDENTIAL_ONE = "credOne";
	private final String CREDENTIAL_TWO = "credTwo";
	private String MEMBER_ONE = "test.vo";
	private String MEMBER_TWO = "memberTwo";
	private String VALUE_ONE_FOGBOW = "valueOneFogbow";
	private String VALUE_TWO_FOGBOW = "valueTwoFogbow";	
	private String VALUE_ONE = "valueOne";
	private String VALUE_TWO = "valueTwo";
	private String VALUE_THREE = "valueThree";
	private String VALUE_FOUR = "valueFour";
	private Properties properties;
	private Request requestDefault;
	private String accessId;
	
	@Before
	public void setUp() throws Exception {
		this.properties = new Properties();
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + LocalCredentialsHelper.FOGBOW_DEFAULTS + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE_FOGBOW);
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + LocalCredentialsHelper.FOGBOW_DEFAULTS + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO_FOGBOW);		
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + MEMBER_ONE + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE);
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + MEMBER_ONE + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO);
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + MEMBER_TWO + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_THREE);
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + MEMBER_TWO + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + "wrong" + LocalCredentialsHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + "wr" + LocalCredentialsHelper.UNDERLINE + "ong" + LocalCredentialsHelper.UNDERLINE
				+ "trash " + LocalCredentialsHelper.UNDERLINE +  CREDENTIAL_TWO, VALUE_FOUR);		
		properties.put(LocalCredentialsHelper.LOCAL_CREDENTIAL_PREFIX + "without-underline", VALUE_FOUR);
		
		properties.put(VomsIdentityPlugin.PROP_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_PASS, VOMS_PASSWORD);
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_SERVER, VOMS_SERVER);
				
		this.vOBasedLocalCrendentialsPlugin = new VOBasedLocalCrendentialsPlugin(properties);
		
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		this.accessId = CertificateUtils.generateAccessId(
				Arrays.asList(proxy.getCertificateChain()), proxy.getCredential());
		
		this.requestDefault = new Request(this.accessId, new Token(accessId, null, null, null),
				null, null, false, null);
	}
	
	@Test
	public void testGetMember() {
		String member = this.vOBasedLocalCrendentialsPlugin.getVO(requestDefault);
		Assert.assertEquals(MEMBER_ONE, member);
	}
	
	@Test
	public void testGetAllLocalCredentials() {
		Map<String, Map<String, String>> allLocalCredentials = this.vOBasedLocalCrendentialsPlugin.getAllLocalCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allLocalCredentials.get(LocalCredentialsHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allLocalCredentials.get(LocalCredentialsHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_TWO));		
		Assert.assertEquals(VALUE_ONE, allLocalCredentials.get(MEMBER_ONE)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, allLocalCredentials.get(MEMBER_ONE)
				.get(CREDENTIAL_TWO));
		Assert.assertEquals(VALUE_THREE, allLocalCredentials.get(MEMBER_TWO)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_FOUR, allLocalCredentials.get(MEMBER_TWO)
				.get(CREDENTIAL_TWO));	
		Assert.assertEquals(5, allLocalCredentials.size());	
	}
	
	@Test
	public void testGetLocalCredentials() {
		Map<String, String> localCredentials = this.vOBasedLocalCrendentialsPlugin.getLocalCredentials(this.requestDefault);
		Assert.assertEquals(VALUE_ONE, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsWithVomsTokenInvalid() {
		this.requestDefault.setFederationToken(new Token(this.accessId.replace("4","0"), null, null, null));
		Map<String, String> localCredentials = this.vOBasedLocalCrendentialsPlugin.getLocalCredentials(this.requestDefault);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsTokenWrong() {
		this.requestDefault.setFederationToken(new Token("123", null, null, null));
		Map<String, String> localCredentials = this.vOBasedLocalCrendentialsPlugin.getLocalCredentials(this.requestDefault);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsNotFountWithDefaultValue() {
		Request request = new Request(null, new Token("", null, null, null), null, null, false, null);
		request.setProvidingMemberId("notfound");
		Map<String, String> localCredentials = this.vOBasedLocalCrendentialsPlugin.getLocalCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllLocalCredentialsNotFoundWithoutDefaultValue() {
		vOBasedLocalCrendentialsPlugin = new VOBasedLocalCrendentialsPlugin(new Properties()); 
		Map<String, Map<String, String>> allLocalCredentials = vOBasedLocalCrendentialsPlugin.getAllLocalCredentials();
		Assert.assertTrue(allLocalCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetLocalCredentialsNotFoundWithoutDefaultValue() {
		vOBasedLocalCrendentialsPlugin = new VOBasedLocalCrendentialsPlugin(new Properties()); 
		Request request = new Request(null, new Token("", null, null, null), null, null, false, null);
		Map<String, String> localCredentials = this.vOBasedLocalCrendentialsPlugin.getLocalCredentials(request);
		Assert.assertTrue(localCredentials.isEmpty());
	}	
	
}
