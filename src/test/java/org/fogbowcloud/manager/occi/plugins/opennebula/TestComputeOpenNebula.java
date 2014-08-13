package org.fogbowcloud.manager.occi.plugins.opennebula;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.opennebula.OpenNebulaComputePlugin;
import org.junit.Before;
import org.junit.Test;

public class TestComputeOpenNebula {

	private static final String FIRST_INSTANCE_ID = "0";
	private Properties properties;
	private OpenNebulaComputePlugin computeOpenNebula;
	
	@Before
	public void setUp(){
		properties = new Properties();
		
		computeOpenNebula = new OpenNebulaComputePlugin(properties);
	}
	
	@Test
	public void testBypassDoesNotWork() {
		
	}
	
	@Test
	public void testRequestInstance(){
		
	}
	
	@Test
	public void testRemoveInstance(){
		
	}
	
	@Test
	public void testRemoveInstances(){
		
	}
	
	@Test
	public void testGetInstance(){
		// mocking client
//				
//		String tokenAccessId = PluginHelper.USERNAME + ":" + PluginHelper.USER_PASS;
//		Instance instance = computeOpenNebula.getInstance(tokenAccessId, FIRST_INSTANCE_ID);
//		Assert.assertEquals(FIRST_INSTANCE_ID, instance.getId());
	}
	
	@Test
	public void testGetInstances(){
		
	}

	@Test
	public void testGetResourcesInfo(){
		
	}	
}
