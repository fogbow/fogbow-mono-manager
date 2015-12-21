package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.localcredentails.MapperHelper;
import org.fogbowcloud.manager.core.plugins.localcredentails.SingleMapperPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSingleMapperPlugin {

	private SingleMapperPlugin singleMapperPlugin;
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
		properties.put(MapperHelper.MAPPER_PREFIX + MapperHelper.FOGBOW_DEFAULTS + 
				MapperHelper.UNDERLINE + CREDENTIAL_ONE, VALUE_ONE_FOGBOW);
		properties.put(MapperHelper.MAPPER_PREFIX + MapperHelper.FOGBOW_DEFAULTS + 
				MapperHelper.UNDERLINE + CREDENTIAL_TWO, VALUE_TWO_FOGBOW);		
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
		this.singleMapperPlugin = new SingleMapperPlugin(properties);		
	}
	
	@Test
	public void testGetAllLocalCredentials() {
		Map<String, Map<String, String>> allLocalCredentials = this.singleMapperPlugin
				.getAllLocalCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allLocalCredentials.get(MapperHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allLocalCredentials.get(MapperHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_TWO));			
		Assert.assertEquals(1, allLocalCredentials.size());	
	}
	
	@Test
	public void testGetLocalCredentials() {
		Request request = new Request(null, null, null, null, false, null);
		Map<String, String> localCredentials = this.singleMapperPlugin.getLocalCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetLocalCredentialsNotFountWithDefaultValue() {
		Request request = new Request(null, null, null, null, false, null);
		Map<String, String> localCredentials = this.singleMapperPlugin.getLocalCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllLocalCredentialsNotFoundWithoutDefaultValue() {
		singleMapperPlugin = new SingleMapperPlugin(new Properties()); 
		Map<String, Map<String, String>> allLocalCredentials = singleMapperPlugin.getAllLocalCredentials();
		Assert.assertTrue(allLocalCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetLocalCredentialsNotFoundWithoutDefaultValue() {
		singleMapperPlugin = new SingleMapperPlugin(new Properties()); 
		Request request = new Request(null, null, null, null, false, null);
		Map<String, String> localCredentials = this.singleMapperPlugin.getLocalCredentials(request);
		Assert.assertTrue(localCredentials.isEmpty());
	}	
	
	@Test
	public void testGetLocalCredentialsWithAccessId() {
		Map<String, String> localCredentials = singleMapperPlugin.getLocalCredentials("accessId");
		Assert.assertEquals(VALUE_ONE_FOGBOW, localCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, localCredentials.get(CREDENTIAL_TWO));
	}
	
}
