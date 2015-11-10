package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.localcredentails.LocalCredentialsHelper;
import org.fogbowcloud.manager.core.plugins.localcredentails.MemberBasedLocalCrendetialsPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestMemberBasedLocalCrendetialsPlugin {
	
	private MemberBasedLocalCrendetialsPlugin memberBasedLocalCrendetialsPlugin;
	private final String CREDENTIAL_ONE = "credOne";
	private final String CREDENTIAL_TWO = "credTwo";
	private String MEMBER_ONE = "memberOne";
	private String MEMBER_TWO = "memberTwo";
	private String VALUE_ONE_FOGBOW = "valueOneFogbow";
	private String VALUE_TWO_FOGBOW = "valueTwoFogbow";	
	private String VALUE_ONE = "valueOne";
	private String VALUE_TWO = "valueTwo";
	private String VALUE_THREE = "valueThree";
	private String VALUE_FOUR = "valueFour";
	private Properties properties;
	
	@Before
	public void setUp() {
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
		this.memberBasedLocalCrendetialsPlugin = new MemberBasedLocalCrendetialsPlugin(properties);		
	}
	
	@Test
	public void testGetAllLocalCredentials() {
		Map<String, Map<String, String>> allLocalCredentials = this.memberBasedLocalCrendetialsPlugin
				.getAllLocalCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allLocalCredentials.get(
				LocalCredentialsHelper.FOGBOW_DEFAULTS).get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allLocalCredentials.get(
				LocalCredentialsHelper.FOGBOW_DEFAULTS).get(CREDENTIAL_TWO));		
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
		Request request = new Request(null, null, null, null, false, MEMBER_ONE);
		Map<String, String> localCredentials = this.memberBasedLocalCrendetialsPlugin.getLocalCredentials(request);
		Assert.assertEquals(VALUE_ONE, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, localCredentials.get(CREDENTIAL_TWO));
		
		request = new Request(null, null, null, null, false, MEMBER_TWO);
		localCredentials = this.memberBasedLocalCrendetialsPlugin.getLocalCredentials(request);
		Assert.assertEquals(VALUE_THREE, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_FOUR, localCredentials.get(CREDENTIAL_TWO));
	}
	
	@Test
	public void testGetLocalCredentialsNotFountWithDefaultValue() {
		Request request = new Request(null, null, null, null, false, "notfound");
		Map<String, String> localCredentials = this.memberBasedLocalCrendetialsPlugin.getLocalCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllLocalCredentialsNotFoundWithoutDefaultValue() {
		memberBasedLocalCrendetialsPlugin = new MemberBasedLocalCrendetialsPlugin(new Properties()); 
		Map<String, Map<String, String>> allLocalCredentials = memberBasedLocalCrendetialsPlugin.getAllLocalCredentials();
		Assert.assertTrue(allLocalCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetLocalCredentialsNotFoundWithoutDefaultValue() {
		memberBasedLocalCrendetialsPlugin = new MemberBasedLocalCrendetialsPlugin(new Properties()); 
		Request request = new Request(null, null, null, null, false, null);
		Map<String, String> localCredentials = this.memberBasedLocalCrendetialsPlugin.getLocalCredentials(request);
		Assert.assertTrue(localCredentials.isEmpty());
	}	
	
	@Test
	public void testGetLocalCrendetialsWithAccessId() {
		Assert.assertNull(memberBasedLocalCrendetialsPlugin.getLocalCredentials("accessId"));
	}
}
