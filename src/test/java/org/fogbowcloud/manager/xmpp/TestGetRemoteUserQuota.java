package org.fogbowcloud.manager.xmpp;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.model.Token;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetRemoteUserQuota {

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws XMPPException {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		this.managerTestHelper.shutdown();
	}
	
	@Test
	public void testGetRemoteUserQuota() throws Exception{
		
		//ResourcesInfo mock information
		String id = "manager.test.com";
		String cpuIdle = "4";
		String cpuInUse = "6";
		String memIdle = "8";
		String memInUse = "12";
		String instancesIdle = "2";
		String instancesInUse = "3";
		
		managerTestHelper.initializeXMPPManagerComponent(false);

		String acessId = "accessId";
		
		Map<String, String> localCredentials = new HashMap<String, String>();
		localCredentials.put("Cred", "Test");
		
		Token token = new Token("accessId", "user", new Date(), new HashMap<String, String>());

		ResourcesInfo resourcesInfo = new ResourcesInfo(id, cpuIdle, cpuInUse,
				memIdle, memInUse, 
				instancesIdle, instancesInUse);
		
		Mockito.when(managerTestHelper.getLocalCredentialsPlugin().getLocalCredentials(
						Mockito.eq(acessId))).thenReturn(localCredentials);
		
		Mockito.when(managerTestHelper.getIdentityPlugin().createToken(
						Mockito.eq(localCredentials))).thenReturn(token);
		
		Mockito.when(managerTestHelper.getComputePlugin().getResourcesInfo(
						Mockito.eq(token))).thenReturn(resourcesInfo);
		
		ResourcesInfo ReturnedResourcesInfo;

		ReturnedResourcesInfo = ManagerPacketHelper.getRemoteUserQuota(acessId, 
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				managerTestHelper.createPacketSender());
		
		assertEquals(id,ReturnedResourcesInfo.getId());
		assertEquals(cpuIdle,ReturnedResourcesInfo.getCpuIdle());
		assertEquals(cpuInUse,ReturnedResourcesInfo.getCpuInUse());
		assertEquals(memIdle,ReturnedResourcesInfo.getMemIdle());
		assertEquals(memInUse,ReturnedResourcesInfo.getMemInUse());
		assertEquals(instancesIdle,ReturnedResourcesInfo.getInstancesIdle());
		assertEquals(instancesInUse,ReturnedResourcesInfo.getInstancesInUse());


	}
	
	
	@Test
	public void testGetRemoteUserQuotaNoLocalCredentials() throws Exception{
		
		//ResourcesInfo mock information
		String id = "manager.test.com";
		String cpuIdle = "4";
		String cpuInUse = "6";
		String memIdle = "8";
		String memInUse = "12";
		String instancesIdle = "2";
		String instancesInUse = "3";
		
		managerTestHelper.initializeXMPPManagerComponent(false);

		String acessId = "accessId";
		
		Map<String, String> localCredentials = new HashMap<String, String>();
		localCredentials.put("Test", "Test");
		Map<String, Map<String, String>> allLocalCredentials = new HashMap<String, Map<String, String>>(); 
		allLocalCredentials.put("Cred", localCredentials);
		
		Token token = new Token("accessId", "user", new Date(), new HashMap<String, String>());

		ResourcesInfo resourcesInfo = new ResourcesInfo(id, cpuIdle, cpuInUse,
				memIdle, memInUse, 
				instancesIdle, instancesInUse);
		
		Mockito.when(managerTestHelper.getLocalCredentialsPlugin().getLocalCredentials(
						Mockito.eq(acessId))).thenReturn(null);
		Mockito.when(managerTestHelper.getLocalCredentialsPlugin().getAllLocalCredentials())
				.thenReturn(allLocalCredentials);
		
		Mockito.when(managerTestHelper.getIdentityPlugin().createToken(
						Mockito.eq(localCredentials))).thenReturn(token);
		
		Mockito.when(managerTestHelper.getComputePlugin().getResourcesInfo(
						Mockito.eq(token))).thenReturn(resourcesInfo);
		
		ResourcesInfo ReturnedResourcesInfo;

		ReturnedResourcesInfo = ManagerPacketHelper.getRemoteUserQuota(acessId, 
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, 
				managerTestHelper.createPacketSender());
		
		assertEquals(id,ReturnedResourcesInfo.getId());
		assertEquals(cpuIdle,ReturnedResourcesInfo.getCpuIdle());
		assertEquals(cpuInUse,ReturnedResourcesInfo.getCpuInUse());
		assertEquals(memIdle,ReturnedResourcesInfo.getMemIdle());
		assertEquals(memInUse,ReturnedResourcesInfo.getMemInUse());
		assertEquals(instancesIdle,ReturnedResourcesInfo.getInstancesIdle());
		assertEquals(instancesInUse,ReturnedResourcesInfo.getInstancesInUse());


	}
	
	
//	Map<String, Map<String, String>> allLocalCredentials = this.localCredentialsPlugin.getAllLocalCredentials();
//	List<Map<String, String>> credentialsUsed = new ArrayList<Map<String, String>>();
//	for (String localName : allLocalCredentials.keySet()) {
//		Map<String, String> credentials = allLocalCredentials.get(localName);
//		if (credentialsUsed.contains(credentials)) {
//			continue;
//		}
//		credentialsUsed.add(credentials);
//
//		ResourcesInfo resourcesInfo = null;
//		try {
//			resourcesInfo = computePlugin.getResourcesInfo(localIdentityPlugin.createToken(credentials));
//		} catch (Exception e) {
//			LOGGER.warn("Does not possible get resources info with credentials of " + localName);
//		}
//		totalResourcesInfo.addResource(resourcesInfo);
//	}
//	return totalResourcesInfo;
}
