package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestManagerFacade {

	private static final String INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final String SECOND_INSTANCE_ID = "rt22e67-5fgt-457a-3rt6-gt78124fhj9p";

	ManagerController managerFacade;
	ManagerTestHelper managerTestHelper;

	private static final Long SCHEDULER_PERIOD = 500L;

	@Before
	public void setUp() throws Exception {
		managerFacade = new ManagerController(new Properties());
		managerTestHelper = new ManagerTestHelper();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorException() throws Exception {
		new ManagerController(null);
	}

	@Test
	public void testGet0ItemsFromIQ() {
		managerFacade.updateMembers(new LinkedList<FederationMember>());
		Assert.assertEquals(0, managerFacade.getMembers().size());
	}

	@Test
	public void testGet1ItemFromIQ() throws CertificateException, IOException {
		FederationMember managerItem = new FederationMember(managerTestHelper.getResources());
		List<FederationMember> items = new LinkedList<FederationMember>();
		items.add(managerItem);
		managerFacade.updateMembers(items);

		List<FederationMember> members = managerFacade.getMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(1, managerFacade.getMembers().size());
	}

	@Test
	public void testGetManyItemsFromIQ() throws CertificateException, IOException {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		for (int i = 0; i < 10; i++) {
			items.add(new FederationMember(managerTestHelper.getResources()));
		}
		managerFacade.updateMembers(items);

		List<FederationMember> members = managerFacade.getMembers();
		Assert.assertEquals(10, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		}
		Assert.assertEquals(10, managerFacade.getMembers().size());
	}

	@Test
	public void testGetRequestsByUser() throws InterruptedException {

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerFacade = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ManagerTestHelper.ACCESS_TOKEN_ID)).thenReturn(
				ManagerTestHelper.USER_NAME);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerFacade.setIdentityPlugin(identityPlugin);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setSSHTunnel(sshTunnel);

		managerFacade.createRequests(ManagerTestHelper.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD * 2);

		List<Request> requests = managerFacade
				.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(ManagerTestHelper.USER_NAME, requests.get(0).getUser());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testOneTimeRequestSetClosed() throws InterruptedException {

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerFacade = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(INSTANCE_ID);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ManagerTestHelper.ACCESS_TOKEN_ID)).thenReturn(
				ManagerTestHelper.USER_NAME);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerFacade.setIdentityPlugin(identityPlugin);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setSSHTunnel(sshTunnel);

		managerFacade.createRequests(ManagerTestHelper.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerFacade
				.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerFacade.removeInstance(ManagerTestHelper.ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerFacade.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestState.CLOSED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}

	@Test
	public void testPersistentRequestSetOpen() throws InterruptedException {

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerFacade = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID)
				.thenThrow(
						new OCCIException(ErrorType.QUOTA_EXCEEDED,
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ManagerTestHelper.ACCESS_TOKEN_ID)).thenReturn(
				ManagerTestHelper.USER_NAME);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerFacade.setIdentityPlugin(identityPlugin);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setSSHTunnel(sshTunnel);

		managerFacade.createRequests(ManagerTestHelper.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerFacade
				.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerFacade.removeInstance(ManagerTestHelper.ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerFacade.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());		
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
	}
		
	@Test
	public void testPersistentRequestSetFulfilled() throws InterruptedException {

		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		managerFacade = new ManagerController(properties);

		// default instance count value is 1
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(RequestAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(RequestConstants.DEFAULT_INSTANCE_COUNT));
		xOCCIAtt.put(RequestAttribute.TYPE.getValue(),
				String.valueOf(RequestType.PERSISTENT.getValue()));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class)))
				.thenReturn(INSTANCE_ID, SECOND_INSTANCE_ID);		

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(ManagerTestHelper.ACCESS_TOKEN_ID)).thenReturn(
				ManagerTestHelper.USER_NAME);

		SSHTunnel sshTunnel = Mockito.mock(SSHTunnel.class);

		managerFacade.setIdentityPlugin(identityPlugin);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setSSHTunnel(sshTunnel);

		managerFacade.createRequests(ManagerTestHelper.ACCESS_TOKEN_ID, new ArrayList<Category>(),
				xOCCIAtt);

		Thread.sleep(SCHEDULER_PERIOD);

		List<Request> requests = managerFacade
				.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());

		// removing instance
		managerFacade.removeInstance(ManagerTestHelper.ACCESS_TOKEN_ID, INSTANCE_ID);

		requests = managerFacade.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.OPEN, requests.get(0).getState());		
		Assert.assertNull(requests.get(0).getInstanceId());
		Assert.assertNull(requests.get(0).getMemberId());
		
		//getting second instance
		Thread.sleep(SCHEDULER_PERIOD * 2);

		requests = managerFacade.getRequestsFromUser(ManagerTestHelper.ACCESS_TOKEN_ID);

		Assert.assertEquals(1, requests.size());
		Assert.assertEquals(SECOND_INSTANCE_ID, requests.get(0).getInstanceId());
		Assert.assertEquals(RequestType.PERSISTENT.getValue(),
				requests.get(0).getAttValue(RequestAttribute.TYPE.getValue()));
		Assert.assertEquals(RequestState.FULFILLED, requests.get(0).getState());
		Assert.assertNull(requests.get(0).getMemberId());
	}
}
