package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestIsInstanceBeenUsed {

	public static final String MANAGER_COMPONENT_URL = "manager.test.com";
	public static final String INSTANCE_DEFAULT = "instance";

	private ManagerTestHelper managerTestHelper;

	@Before
	public void setUp() throws XMPPException {
		this.managerTestHelper = new ManagerTestHelper();
	}

	@After
	public void tearDown() throws Exception {
		managerTestHelper.shutdown();
	}

	@Test
	public void testIsInstanceBeenUsedByFulfilled() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null, true, "");
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId(INSTANCE_DEFAULT);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		Request servedRequest = new Request(request1.getId(), new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, MANAGER_COMPONENT_URL);
		servedRequest.setInstanceId(INSTANCE_DEFAULT);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
				
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(INSTANCE_DEFAULT
				+ Request.SEPARATOR_GLOBAL_ID + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				servedRequest, managerTestHelper.createPacketSender());
	}
	
	@Test
	public void testIsInstanceBeenUsedByDeleted() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null, true, "");
		request1.setState(RequestState.DELETED);
		request1.setInstanceId(INSTANCE_DEFAULT);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		Request servedRequest = new Request(request1.getId(), new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, MANAGER_COMPONENT_URL);
		servedRequest.setInstanceId(INSTANCE_DEFAULT);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(INSTANCE_DEFAULT
				+ Request.SEPARATOR_GLOBAL_ID + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				servedRequest, managerTestHelper.createPacketSender());
	}
	
	@Test(expected=OCCIException.class)
	public void testInstanceIsNotBeenUsedThereIsOpen() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null, true, "");
		request1.setState(RequestState.OPEN);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		Request servedRequest = new Request(request1.getId(), new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, MANAGER_COMPONENT_URL);
		servedRequest.setInstanceId(INSTANCE_DEFAULT);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(INSTANCE_DEFAULT, servedRequest,
				managerTestHelper.createPacketSender());
	}
	
	@Test(expected = OCCIException.class)
	public void testInstanceIsNotBeenUsedThereIsNotRequest() throws Exception {
		ManagerController managerController = createManagerController();
		
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		Request servedRequest = new Request("id1", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, MANAGER_COMPONENT_URL);
		servedRequest.setInstanceId(INSTANCE_DEFAULT);
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember("anyvalue", servedRequest,
				managerTestHelper.createPacketSender());		
	}
	
	private ManagerController createManagerController() {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.FEDERATION_USER_NAME_KEY, "fogbow");
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_KEY, "fogbow");
		properties.put(ConfigurationConstants.FEDERATION_USER_TENANT_NAME_KEY, "fogbow");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "manager.test.com");
		properties.put(ConfigurationConstants.TUNNEL_SSH_PRIVATE_HOST_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TUNNEL_SSH_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put(ConfigurationConstants.MAX_WHOISALIVE_MANAGER_COUNT,
				ManagerTestHelper.MAX_WHOISALIVE_MANAGER_COUNT);
		
		ManagerController managerFacade = new ManagerController(properties);
		return managerFacade;
	}

}
