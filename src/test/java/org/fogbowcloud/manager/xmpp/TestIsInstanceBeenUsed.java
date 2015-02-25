package org.fogbowcloud.manager.xmpp;

import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.core.OCCIException;
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
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null);
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId(INSTANCE_DEFAULT);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeenUsedByRemoteMember(INSTANCE_DEFAULT
				+ Request.SEPARATOR_GLOBAL_ID + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_URL, managerTestHelper.createPacketSender());
	}
	
	@Test
	public void testIsInstanceBeenUsedByDeleted() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null);
		request1.setState(RequestState.DELETED);
		request1.setInstanceId(INSTANCE_DEFAULT);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeenUsedByRemoteMember(INSTANCE_DEFAULT
				+ Request.SEPARATOR_GLOBAL_ID + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_URL, managerTestHelper.createPacketSender());
	}
	
	@Test(expected=OCCIException.class)
	public void testInstanceIsNotBeenUsedThereIsOpen() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting request repository
		Request request1 = new Request("id1", managerTestHelper.getDefaultLocalToken(), null, null, null);
		request1.setState(RequestState.OPEN);
		
		RequestRepository requestRepository = new RequestRepository();
		requestRepository.addRequest(managerTestHelper.getDefaultLocalToken().getUser(), request1);
		managerController.setRequests(requestRepository);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeenUsedByRemoteMember(INSTANCE_DEFAULT, MANAGER_COMPONENT_URL,
				managerTestHelper.createPacketSender());
	}

	@Test(expected = OCCIException.class)
	public void testInstanceIsNotBeenUsedThereIsNotRequest() throws Exception {
		ManagerController managerController = createManagerController();
		
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeenUsedByRemoteMember("anyvalue", MANAGER_COMPONENT_URL,
				managerTestHelper.createPacketSender());
	}
	
	private ManagerController createManagerController() {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.FEDERATION_USER_NAME_KEY, "fogbow");
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_KEY, "fogbow");
		properties.put(ConfigurationConstants.FEDERATION_USER_TENANT_NAME_KEY, "fogbow");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "manager.test.com");
		properties.put(ConfigurationConstants.SSH_PRIVATE_HOST_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put(ConfigurationConstants.MAX_WHOISALIVE_MANAGER_COUNT,
				ManagerTestHelper.MAX_WHOISALIVE_MANAGER_COUNT);
		
		ManagerController managerFacade = new ManagerController(properties);
		return managerFacade;
	}

}
