package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.localcredentails.MapperHelper;
import org.fogbowcloud.manager.core.plugins.localcredentails.MemberBasedMapperPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestMemberBasedMapperPlugin {
	
	private MemberBasedMapperPlugin memberBasedMapperPlugin;
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
		this.memberBasedMapperPlugin = new MemberBasedMapperPlugin(properties);		
	}
	
	@Test
	public void testGetAllLocalCredentials() {
		Map<String, Map<String, String>> allLocalCredentials = this.memberBasedMapperPlugin
				.getAllLocalCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allLocalCredentials.get(
				MapperHelper.FOGBOW_DEFAULTS).get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allLocalCredentials.get(
				MapperHelper.FOGBOW_DEFAULTS).get(CREDENTIAL_TWO));		
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
		Order order = new Order(null, null, null, null, false, MEMBER_ONE);
		Map<String, String> localCredentials = this.memberBasedMapperPlugin.getLocalCredentials(order);
		Assert.assertEquals(VALUE_ONE, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO, localCredentials.get(CREDENTIAL_TWO));
		
		order = new Order(null, null, null, null, false, MEMBER_TWO);
		localCredentials = this.memberBasedMapperPlugin.getLocalCredentials(order);
		Assert.assertEquals(VALUE_THREE, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_FOUR, localCredentials.get(CREDENTIAL_TWO));
	}
	
	@Test
	public void testGetLocalCredentialsNotFountWithDefaultValue() {
		Order order = new Order(null, null, null, null, false, "notfound");
		Map<String, String> localCredentials = this.memberBasedMapperPlugin.getLocalCredentials(order);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllLocalCredentialsNotFoundWithoutDefaultValue() {
		memberBasedMapperPlugin = new MemberBasedMapperPlugin(new Properties()); 
		Map<String, Map<String, String>> allLocalCredentials = memberBasedMapperPlugin.getAllLocalCredentials();
		Assert.assertTrue(allLocalCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetLocalCredentialsNotFoundWithoutDefaultValue() {
		memberBasedMapperPlugin = new MemberBasedMapperPlugin(new Properties()); 
		Order order = new Order(null, null, null, null, false, null);
		Map<String, String> localCredentials = this.memberBasedMapperPlugin.getLocalCredentials(order);
		Assert.assertTrue(localCredentials.isEmpty());
	}	
	
	@Test

	public void testGetLocalCrendetialsWithAccessId() {
		Assert.assertNull(memberBasedMapperPlugin.getLocalCredentials("accessId"));
	}

	@Test
	public void testGetLocalCredentialsOrderNull() {
		Map<String, String> localCredentials = this.memberBasedMapperPlugin
				.getLocalCredentials(new Order(null, new Token("", null, null, null), null, null,
						false, null));
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}		

}
