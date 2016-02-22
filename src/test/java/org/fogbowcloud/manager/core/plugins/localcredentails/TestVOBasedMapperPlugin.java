package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.identity.voms.Fixture;
import org.fogbowcloud.manager.core.plugins.identity.voms.Utils;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.localcredentails.MapperHelper;
import org.fogbowcloud.manager.core.plugins.localcredentails.VOBasedMapperPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class TestVOBasedMapperPlugin {

	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "test.vo";
	
	private VOBasedMapperPlugin vOBasedMapperPlugin;
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
	private Order orderDefault;
	private String accessId;
	
	@Before
	public void setUp() throws Exception {
		this.properties = new Properties();
		properties.put(MapperHelper.MAPPER_PREFIX + MapperHelper.FOGBOW_DEFAULTS + MapperHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE_FOGBOW);
		properties.put(MapperHelper.MAPPER_PREFIX + MapperHelper.FOGBOW_DEFAULTS + MapperHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO_FOGBOW);		
		properties.put(MapperHelper.MAPPER_PREFIX + MEMBER_ONE + MapperHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE);
		properties.put(MapperHelper.MAPPER_PREFIX + MEMBER_ONE + MapperHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO);
		properties.put(MapperHelper.MAPPER_PREFIX + MEMBER_TWO + MapperHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_THREE);
		properties.put(MapperHelper.MAPPER_PREFIX + MEMBER_TWO + MapperHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(MapperHelper.MAPPER_PREFIX + "wrong" + MapperHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(MapperHelper.MAPPER_PREFIX + "wr" + MapperHelper.UNDERLINE + "ong" + MapperHelper.UNDERLINE
				+ "trash " + MapperHelper.UNDERLINE +  CREDENTIAL_TWO, VALUE_FOUR);		
		properties.put(MapperHelper.MAPPER_PREFIX + "without-underline", VALUE_FOUR);
		
		properties.put(VomsIdentityPlugin.PROP_PATH_TRUST_ANCHORS,
				"src/test/resources/voms/trust-anchors");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(VomsIdentityPlugin.PROP_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_PASS, VOMS_PASSWORD);
		properties.put(VomsIdentityPlugin.PROP_VOMS_FEDERATION_USER_SERVER, VOMS_SERVER);
				
		this.vOBasedMapperPlugin = new VOBasedMapperPlugin(properties);
		
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		this.accessId = CertificateUtils.generateAccessId(
				Arrays.asList(proxy.getCertificateChain()), proxy.getCredential());
		
		this.orderDefault = new Order(this.accessId, new Token(accessId, null, null, null),
				null, null, false, null);
	}
	
	@Test
	public void testGetMember() {
		String member = this.vOBasedMapperPlugin.getVO(orderDefault);
		Assert.assertEquals(MEMBER_ONE, member);
	}
	
	@Test
	public void testGetAllLocalCredentials() {
		Map<String, Map<String, String>> allLocalCredentials = this.vOBasedMapperPlugin.getAllLocalCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allLocalCredentials.get(MapperHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allLocalCredentials.get(MapperHelper.FOGBOW_DEFAULTS)
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
		Map<String, String> localCredentials = this.vOBasedMapperPlugin.getLocalCredentials(this.orderDefault);
		Assert.assertEquals(VALUE_ONE, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsWithVomsTokenInvalid() {
		this.orderDefault.setFederationToken(new Token(this.accessId.replace("4","0"), null, null, null));
		Map<String, String> localCredentials = this.vOBasedMapperPlugin.getLocalCredentials(this.orderDefault);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsTokenWrong() {
		this.orderDefault.setFederationToken(new Token("123", null, null, null));
		Map<String, String> localCredentials = this.vOBasedMapperPlugin.getLocalCredentials(this.orderDefault);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsNotFountWithDefaultValue() {
		Order order = new Order(null, new Token("", null, null, null), null, null, false, null);
		order.setProvidingMemberId("notfound");
		Map<String, String> localCredentials = this.vOBasedMapperPlugin.getLocalCredentials(order);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllLocalCredentialsNotFoundWithoutDefaultValue() {
		vOBasedMapperPlugin = new VOBasedMapperPlugin(new Properties()); 
		Map<String, Map<String, String>> allLocalCredentials = vOBasedMapperPlugin.getAllLocalCredentials();
		Assert.assertTrue(allLocalCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetLocalCredentialsNotFoundWithoutDefaultValue() {
		vOBasedMapperPlugin = new VOBasedMapperPlugin(new Properties()); 
		Order order = new Order(null, new Token("", null, null, null), null, null, false, null);
		Map<String, String> localCredentials = this.vOBasedMapperPlugin.getLocalCredentials(order);
		Assert.assertTrue(localCredentials.isEmpty());
	}
	
	@Test
	public void testGetLocalCredentialsOrderNull() { 
		Map<String, String> localCredentials = this.vOBasedMapperPlugin
				.getLocalCredentials(new Order(null, new Token("", null, null, null), null, null,
						false, null));
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));	
	}		
	
	@Test
	public void testGetCredentialsWithAccessId() {
		Map<String, String> localCredentials = this.vOBasedMapperPlugin.getLocalCredentials("123");
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}
	
}
