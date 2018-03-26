package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.fogbowcloud.manager.core.AsynchronousOrderCallback;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.ManagerTestHelper;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.jivesoftware.smack.XMPPException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;

public class TestRemoveServeredOrder {

	private static final String INSTANCE_ID = "instanceId";
	private static final String ACCESS_ID = "accessId";
	private static final String ORDER_ID = "orderId";

	private ManagerTestHelper managerTestHelper;
	private ManagerXmppComponent XMPPManagerComponent;

	@Before
	public void setUp() throws Exception {
		this.managerTestHelper = new ManagerTestHelper();
        this.XMPPManagerComponent = managerTestHelper.initializeXMPPManagerComponent(false);
    }

	@After
	public void tearDown() throws Exception {
	    this.XMPPManagerComponent = null;
		this.managerTestHelper.shutdown();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveServeredOrderResourceKindCompute() throws Exception {
		Order order = createOrder(OrderConstants.COMPUTE_TERM);
		ManagerController managerFacade = XMPPManagerComponent.getManagerFacade();
		managerFacade.getManagerDataStoreController().addOrder(order);

		Token token = new Token(
		        ACCESS_ID,
                new Token.User(OCCITestHelper.USER_MOCK, ""),
                null,
                new HashMap<String, String>()
        );
		
		Mockito.when(managerTestHelper.getFederationIdentityPlugin().getToken(ACCESS_ID))
				.thenReturn(token);
		Mockito.when(managerTestHelper.getMapperPlugin().getLocalCredentials(Mockito.anyString()))
				.thenReturn(new HashMap<String, String>());		
		Mockito.when(managerTestHelper.getMapperPlugin().getLocalCredentials(
				Mockito.anyString())).thenReturn(new HashMap<String, String>());
		Mockito.when(managerTestHelper.getIdentityPlugin().createToken(
				Mockito.anyMap())).thenReturn(token);		
		Mockito.when(managerTestHelper.getAuthorizationPlugin().isAuthorized(token)).thenReturn(true);			

		Assert.assertEquals(1, XMPPManagerComponent.getManagerFacade()
				.getOrdersFromUser(token.getAccessId(), false).size());
		
		final BlockingQueue<String> bq = new LinkedBlockingQueue<>();

		ManagerPacketHelper.deleteRemoteOrder(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				order, managerTestHelper.createPacketSender(), new AsynchronousOrderCallback() {

					@Override
					public void success(String instanceId) {
						bq.add("");
					}

					@Override
					public void error(Throwable t) {
					}
				});
		
		String instanceId = bq.poll(1500, TimeUnit.SECONDS);
		
		Assert.assertEquals(0, XMPPManagerComponent.getManagerFacade()
				.getOrdersFromUser(token.getAccessId(), false).size());

		Mockito.verify(managerTestHelper.getComputePlugin(), VerificationModeFactory.times(1))
				.removeInstance(token, INSTANCE_ID);
		
		Assert.assertEquals("", instanceId);


	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRemoveServeredOrderResourceKindStorage() throws Exception {
		Order order = createOrder(OrderConstants.STORAGE_TERM);
		ManagerController managerFacade = XMPPManagerComponent.getManagerFacade();
		managerFacade.getManagerDataStoreController().addOrder(order);

		Token token = new Token(ACCESS_ID, new Token.User(OCCITestHelper.USER_MOCK, ""), null, new HashMap<String, String>());
		
		Mockito.when(managerTestHelper.getFederationIdentityPlugin().getToken(ACCESS_ID))
				.thenReturn(token);
		Mockito.when(managerTestHelper.getMapperPlugin().getLocalCredentials(
				Mockito.anyString())).thenReturn(new HashMap<String, String>());
		Mockito.when(managerTestHelper.getIdentityPlugin().createToken(
				Mockito.anyMap())).thenReturn(token);
		Mockito.when(managerTestHelper.getAuthorizationPlugin().isAuthorized(token)).thenReturn(true);			

		Assert.assertEquals(1, XMPPManagerComponent.getManagerFacade()
				.getOrdersFromUser(token.getAccessId(), false).size());
		
		final BlockingQueue<String> bq = new LinkedBlockingQueue<>();

		ManagerPacketHelper.deleteRemoteOrder(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				order, managerTestHelper.createPacketSender(), new AsynchronousOrderCallback() {

					@Override
					public void success(String instanceId) {
						bq.add("");
					}

					@Override
					public void error(Throwable t) {
					}
				});
		
		String instanceId = bq.poll(1500, TimeUnit.SECONDS);
		
		Assert.assertEquals(0, XMPPManagerComponent.getManagerFacade()
				.getOrdersFromUser(token.getAccessId(), false).size());

		Mockito.verify(managerTestHelper.getStoragePlugin(), VerificationModeFactory.times(1))
				.removeInstance(token, INSTANCE_ID);
		
		Assert.assertEquals("", instanceId);
	}	

	private Order createOrder(String resourceKind) {
		List<Category> categories = new ArrayList<>();
		categories.add(new Category("term1", "scheme1", "class1"));
		Map<String, String> attributes = new HashMap<>();
		attributes.put("key1", "value1");
		attributes.put("key2", "value2");
		attributes.put(OrderAttribute.RESOURCE_KIND.getValue(), resourceKind);
		Order order = new Order(ORDER_ID, new Token(ACCESS_ID,
				new Token.User(OCCITestHelper.USER_MOCK, ""),
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), categories, attributes, false, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		order.setInstanceId(INSTANCE_ID);
		return order;
	}	
	
}