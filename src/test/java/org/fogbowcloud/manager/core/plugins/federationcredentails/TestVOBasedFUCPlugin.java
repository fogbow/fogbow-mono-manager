package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.Fixture;
import org.fogbowcloud.manager.core.plugins.identity.voms.Utils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.italiangrid.voms.VOMSAttribute;
import org.italiangrid.voms.ac.VOMSACValidator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class TestVOBasedFUCPlugin {

	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "test.vo";
	
	private VOBasedFUCPlugin vOBasedFUCPlugin;
	private final String CREDENTIAL_ONE = "credOne";
	private final String CREDENTIAL_TWO = "credTwo";
	private String MEMBER_ONE = "vofogbow";
	private String MEMBER_TWO = "providerTwo";
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
		properties.put(FUCPluginHelper.FUC_PREFIX + FUCPluginHelper.FOGBOW_DEFAULTS + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE_FOGBOW);
		properties.put(FUCPluginHelper.FUC_PREFIX + FUCPluginHelper.FOGBOW_DEFAULTS + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO_FOGBOW);		
		properties.put(FUCPluginHelper.FUC_PREFIX + MEMBER_ONE + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE);
		properties.put(FUCPluginHelper.FUC_PREFIX + MEMBER_ONE + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO);
		properties.put(FUCPluginHelper.FUC_PREFIX + MEMBER_TWO + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_THREE);
		properties.put(FUCPluginHelper.FUC_PREFIX + MEMBER_TWO + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(FUCPluginHelper.FUC_PREFIX + "wrong" + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(FUCPluginHelper.FUC_PREFIX + "wr" + FUCPluginHelper.UNDERLINE + "ong" + FUCPluginHelper.UNDERLINE
				+ "trash " + FUCPluginHelper.UNDERLINE +  CREDENTIAL_TWO, VALUE_FOUR);		
		properties.put(FUCPluginHelper.FUC_PREFIX + "without-underline", VALUE_FOUR);
		
		properties.put(VomsIdentityPlugin.PROP_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_PASS, VOMS_PASSWORD);
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_SERVER, VOMS_SERVER);
		
		
		this.vOBasedFUCPlugin = new VOBasedFUCPlugin(properties);
		
		VOMSACValidator vomsACValidator = Mockito.mock(VOMSACValidator.class);
		VOMSAttribute vomsAttribute = Mockito.mock(VOMSAttribute.class);
		Mockito.when(vomsAttribute.getVO()).thenReturn(MEMBER_ONE);
		List<VOMSAttribute> vomsAttributes = new ArrayList<VOMSAttribute>();
		vomsAttributes.add(vomsAttribute);
		Mockito.when(vomsACValidator.validate(Mockito.any(X509Certificate[].class)))
				.thenReturn(vomsAttributes);
		
		vOBasedFUCPlugin.setVomSACValidator(vomsACValidator);
		
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		this.accessId = CertificateUtils.generateAccessId(
				Arrays.asList(proxy.getCertificateChain()), proxy.getCredential());
		
		this.requestDefault = new Request(this.accessId, new Token(accessId, null, null, null),
				null, null, false, null);
	}
	
	@Test
	public void testGetMember() {
		String member = this.vOBasedFUCPlugin.getVOMember(requestDefault);
		Assert.assertEquals(MEMBER_ONE, member);
	}
	
	@Test
	public void testGetAllFedUsersCredentials() {
		Map<String, Map<String, String>> allFedUserCredentials = this.vOBasedFUCPlugin.getAllFedUsersCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allFedUserCredentials.get(FUCPluginHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allFedUserCredentials.get(FUCPluginHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_TWO));		
		Assert.assertEquals(VALUE_ONE, allFedUserCredentials.get(MEMBER_ONE)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, allFedUserCredentials.get(MEMBER_ONE)
				.get(CREDENTIAL_TWO));
		Assert.assertEquals(VALUE_THREE, allFedUserCredentials.get(MEMBER_TWO)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_FOUR, allFedUserCredentials.get(MEMBER_TWO)
				.get(CREDENTIAL_TWO));	
		Assert.assertEquals(5, allFedUserCredentials.size());	
	}
	
	@Test
	public void testGetFedUserCredentials() {
		Map<String, String> fedUserCredentials = this.vOBasedFUCPlugin.getFedUserCredentials(this.requestDefault);
		Assert.assertEquals(VALUE_ONE, fedUserCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, fedUserCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetFedUserCredentialsWithVomsTokenInvalid() {
		this.requestDefault.setFederationToken(new Token(this.accessId.replace("4","0"), null, null, null));
		Map<String, String> fedUserCredentials = this.vOBasedFUCPlugin.getFedUserCredentials(this.requestDefault);
		Assert.assertEquals(VALUE_ONE_FOGBOW, fedUserCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, fedUserCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetFedUserCredentialsTokenWrong() {
		this.requestDefault.setFederationToken(new Token("123", null, null, null));
		Map<String, String> fedUserCredentials = this.vOBasedFUCPlugin.getFedUserCredentials(this.requestDefault);
		Assert.assertEquals(VALUE_ONE_FOGBOW, fedUserCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, fedUserCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetFedUserCredentialsNotFountWithDefaultValue() {
		Request request = new Request(null, new Token("", null, null, null), null, null, false, null);
		request.setProvidingMemberId("notfound");
		Map<String, String> fedUserCredentials = this.vOBasedFUCPlugin.getFedUserCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, fedUserCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, fedUserCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllFedUsersCredentialsNotFoundWithoutDefaultValue() {
		vOBasedFUCPlugin = new VOBasedFUCPlugin(new Properties()); 
		Map<String, Map<String, String>> allFedUserCredentials = vOBasedFUCPlugin.getAllFedUsersCredentials();
		Assert.assertTrue(allFedUserCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetFedUserCredentialsNotFoundWithoutDefaultValue() {
		vOBasedFUCPlugin = new VOBasedFUCPlugin(new Properties()); 
		Request request = new Request(null, new Token("", null, null, null), null, null, false, null);
		request.setProvidingMemberId(MEMBER_ONE);
		Map<String, String> fedUserCredentials = this.vOBasedFUCPlugin.getFedUserCredentials(request);
		Assert.assertTrue(fedUserCredentials.isEmpty());
	}	
	
}
