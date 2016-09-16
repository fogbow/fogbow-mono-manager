package org.fogbowcloud.manager.xmpp;

import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.core.util.ManagerTestHelper;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderRepository;
import org.fogbowcloud.manager.occi.order.OrderState;
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
		
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId(INSTANCE_DEFAULT);
		
		OrderRepository orderRepository = new OrderRepository();
		orderRepository.addOrder(managerTestHelper.getDefaultFederationToken().getUser().getId(), order1);
		managerController.setOrders(orderRepository);
		
		Order servedOrder = new Order(order1.getId(), new Token("accessId", new Token.User("userId1", ""), null,
				new HashMap<String, String>()), null, null, false, MANAGER_COMPONENT_URL);
		servedOrder.setInstanceId(INSTANCE_DEFAULT);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
				
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(INSTANCE_DEFAULT
				+ Order.SEPARATOR_GLOBAL_ID + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				servedOrder, managerTestHelper.createPacketSender());
	}
	
	@Test
	public void testIsInstanceBeenUsedByDeleted() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		order1.setState(OrderState.DELETED);
		order1.setInstanceId(INSTANCE_DEFAULT);
		
		OrderRepository orderRepository = new OrderRepository();
		orderRepository.addOrder(managerTestHelper.getDefaultFederationToken().getUser().getId(), order1);
		managerController.setOrders(orderRepository);
		
		Order servedOrder = new Order(order1.getId(), new Token("accessId", new Token.User("userId1", ""), null,
				new HashMap<String, String>()), null, null, false, MANAGER_COMPONENT_URL);
		servedOrder.setInstanceId(INSTANCE_DEFAULT);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(INSTANCE_DEFAULT
				+ Order.SEPARATOR_GLOBAL_ID + DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				servedOrder, managerTestHelper.createPacketSender());
	}
	
	@Test(expected=OCCIException.class)
	public void testInstanceIsNotBeenUsedThereIsOpen() throws Exception {
		ManagerController managerController = createManagerController();
	
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		// setting order repository
		Order order1 = new Order("id1", managerTestHelper.getDefaultFederationToken(), null, null, true, "");
		order1.setState(OrderState.OPEN);
		
		OrderRepository orderRepository = new OrderRepository();
		orderRepository.addOrder(managerTestHelper.getDefaultFederationToken().getUser().getId(), order1);
		managerController.setOrders(orderRepository);
		
		Order servedOrder = new Order(order1.getId(), new Token("accessId", new Token.User("userId1", ""), null,
				new HashMap<String, String>()), null, null, false, MANAGER_COMPONENT_URL);
		servedOrder.setInstanceId(INSTANCE_DEFAULT);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(INSTANCE_DEFAULT, servedOrder,
				managerTestHelper.createPacketSender());
	}
	
	@Test(expected = OCCIException.class)
	public void testInstanceIsNotBeenUsedThereIsNotOrder() throws Exception {
		ManagerController managerController = createManagerController();
		
		managerTestHelper.initializeXMPPManagerComponent(false, managerController);
		
		Order servedOrder = new Order("id1", new Token("accessId", new Token.User("userId1", ""), null,
				new HashMap<String, String>()), null, null, false, MANAGER_COMPONENT_URL);
		servedOrder.setInstanceId(INSTANCE_DEFAULT);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if instance is been used
		ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember("anyvalue", servedOrder,
				managerTestHelper.createPacketSender());		
	}
	
	private ManagerController createManagerController() {
		Properties properties = new Properties();
		properties.put("local_proxy_account_user_name", "fogbow");
		properties.put("local_proxy_account_password", "fogbow");
		properties.put("local_proxy_account_tenant_name", "fogbow");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "manager.test.com");
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put("max_whoisalive_manager_count",
				ManagerTestHelper.MAX_WHOISALIVE_MANAGER_COUNT);
		
		boolean initializeBD = false;
		ManagerController managerFacade = new ManagerController(properties, null, initializeBD);
		return managerFacade;
	}

}
