package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.CurrentThreadExecutorService;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestOCCIApplication {

	private static final String INSTANCE_ID = "b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private static final Long SCHEDULER_PERIOD = 500L;

	private OCCIApplication occiApplication;
	private Map<String, String> xOCCIAtt;
	private ManagerController managerFacade;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put("scheduler_period", SCHEDULER_PERIOD.toString());
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
				
		ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
		Mockito.when(executor.scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.anyLong(), 
				Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenAnswer(new Answer<Future<?>>() {
			@Override
			public Future<?> answer(InvocationOnMock invocation)
					throws Throwable {
				Runnable runnable = (Runnable) invocation.getArguments()[0];
				runnable.run();
				return null;
			}
		});
		
		managerFacade = new ManagerController(properties, executor);
		occiApplication = new OCCIApplication(managerFacade);

		// default instance count value is 1
		xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(),
				String.valueOf(OrderConstants.DEFAULT_INSTANCE_COUNT));

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		HashMap<String, String> tokenAttr = new HashMap<String, String>();
		Token userToken = new Token(OCCITestHelper.ACCESS_TOKEN, OCCITestHelper.USER_MOCK,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAttr);

		Mockito.when(identityPlugin.getToken(Mockito.anyString())).thenReturn(userToken);

		AuthorizationPlugin authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		BenchmarkingPlugin benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		
		FederationMemberPickerPlugin memberPickerPlugin = Mockito.mock(FederationMemberPickerPlugin.class);
		
		// mocking benchmark executor
		ExecutorService benchmarkExecutor = new CurrentThreadExecutorService();
		
		MapperPlugin mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());
		
		managerFacade.setAuthorizationPlugin(authorizationPlugin);
		managerFacade.setLocalCredentailsPlugin(mapperPlugin);
		managerFacade.setLocalIdentityPlugin(identityPlugin);
		managerFacade.setFederationIdentityPlugin(identityPlugin);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setBenchmarkingPlugin(benchmarkingPlugin);
		managerFacade.setMemberPickerPlugin(memberPickerPlugin);
		managerFacade.setBenchmarkExecutor(benchmarkExecutor);
	}

	@Test
	public void testGetOrderDetails() throws InterruptedException {
		occiApplication.createOrders(OCCITestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Order> orders = occiApplication.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);
		Assert.assertEquals(1, orders.size());
		String orderId = orders.get(0).getId();
		Order orderDetails = occiApplication.getOrder(OCCITestHelper.ACCESS_TOKEN, orderId);

		Assert.assertEquals(orderId, orderDetails.getId());
		Assert.assertNull(orderDetails.getInstanceId());
		Assert.assertEquals(OrderState.OPEN, orderDetails.getState());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetOrderDetailsAfterPeriod() throws InterruptedException {
		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenReturn(INSTANCE_ID);

		managerFacade.setComputePlugin(computePlugin);
		occiApplication.createOrders(OCCITestHelper.ACCESS_TOKEN, new ArrayList<Category>(), xOCCIAtt);
		List<Order> orders = occiApplication.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);
		Assert.assertEquals(1, orders.size());
		String orderId = orders.get(0).getId();
		Order orderDetails = occiApplication.getOrder(OCCITestHelper.ACCESS_TOKEN, orderId);

		Thread.sleep(SCHEDULER_PERIOD * 2);

		Assert.assertEquals(orderId, orderDetails.getId());
		Assert.assertEquals(INSTANCE_ID, orderDetails.getInstanceId());
		Assert.assertEquals(OrderState.FULFILLED, orderDetails.getState());
	}

	@Test
	public void testOrderUser() {
		this.occiApplication.createOrders(OCCITestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Order> ordersFromUser = occiApplication
				.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);

		Assert.assertEquals(1, ordersFromUser.size());
	}

	@Test
	public void testManyOrderUser() {
		int numberOfInstances = 10;
		xOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(), String.valueOf(numberOfInstances));
		this.occiApplication.createOrders(OCCITestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);
		List<Order> ordersFromUser = occiApplication
				.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, ordersFromUser.size());
	}

	@Test
	public void testRemoveAllOrder() throws InterruptedException {
		int numberOfInstances = 10;
		xOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(), String.valueOf(numberOfInstances));
		occiApplication.createOrders(OCCITestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);

		List<Order> ordersFromUser = occiApplication
				.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, ordersFromUser.size());
		occiApplication.removeAllOrders(OCCITestHelper.ACCESS_TOKEN);
		ordersFromUser = occiApplication.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, counterDeletedOrders(ordersFromUser));
	}

	@Test
	public void testRemoveSpecificOrder() {
		int numberOfInstances = 10;
		xOCCIAtt.put(OrderAttribute.INSTANCE_COUNT.getValue(), String.valueOf(numberOfInstances));
		occiApplication.createOrders(OCCITestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				xOCCIAtt);

		List<Order> ordersFromUser = occiApplication
				.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfInstances, ordersFromUser.size());

		occiApplication.removeOrder(OCCITestHelper.ACCESS_TOKEN, ordersFromUser.get(1).getId());
		ordersFromUser = occiApplication.getOrdersFromUser(OCCITestHelper.ACCESS_TOKEN);

		Assert.assertEquals(1, counterDeletedOrders(ordersFromUser));
	}

	private int counterDeletedOrders(List<Order> ordersFromUser) {
		int count = 0;
		for (Order order : ordersFromUser) {
			if (order.getState().equals(OrderState.DELETED)) {
				count++;
			}
		}
		return count;
	}
}
