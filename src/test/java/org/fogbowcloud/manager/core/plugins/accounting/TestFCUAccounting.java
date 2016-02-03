package org.fogbowcloud.manager.core.plugins.accounting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFCUAccounting {

	private static final double ACCEPTABLE_ERROR = 0.0;
	private static final String TEST_DB_PATH = "src/test/resources/testdbaccounting.sqlite";
	private BenchmarkingPlugin benchmarkingPlugin;
	FCUAccountingPlugin accountingPlugin;
	Properties properties;

	@Before
	public void setUp() throws IOException {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		properties = new Properties();
		properties.put("accounting_datastore_url", "jdbc:sqlite:" + TEST_DB_PATH);
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "localMemberId");

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
	}

	@After
	public void tearDown() throws IOException {
		File dbFile = new File(TEST_DB_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}

	@Test
	public void testEmptyOrders() {
		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
		accountingPlugin.update(new ArrayList<Order>());

		Assert.assertNotNull(accountingPlugin.getMembersUsage());
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().size());
		Assert.assertNotNull(accountingPlugin.getUsersUsage());
	}

	@Test
	public void testOneOrderFulfielledByRemoteAndEmptyServedOrders() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remoteMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId("remoteMemberId");
		order1.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(order1);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(),
				ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneOrderFulfielledByLocalAndEmptyServedOrders() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId("instanceId");
		order1.setProvidingMemberId("localMemberId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(order1);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;

		Assert.assertNotNull(accountingPlugin.getMembersUsage());
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().size());
		Assert.assertNotNull(accountingPlugin.getUsersUsage());
		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testSomeOrdersFulfielledByLocalAndEmptyServedOrders() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(4d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId("instanceId1");
		order1.setProvidingMemberId("localMemberId");
		
		Order order2 = new Order("id2", new Token("accessId", "userId2", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order2.setDateUtils(dateUtils);
		order2.setState(OrderState.FULFILLED);
		order2.setInstanceId("instanceId2");
		order2.setProvidingMemberId("localMemberId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(order1);
		orders.add(order2);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		double expectedConsumptionForUser1 = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		double expectedConsumptionForUser2 = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertNotNull(accountingPlugin.getMembersUsage());
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().size());
		Assert.assertNotNull(accountingPlugin.getUsersUsage());
		Assert.assertEquals(2, accountingPlugin.getUsersUsage().size());
		Assert.assertEquals(expectedConsumptionForUser1,
				accountingPlugin.getUsersUsage().get("userId1"), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedConsumptionForUser2,
				accountingPlugin.getUsersUsage().get("userId2"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testEmptyOrderAndOneServedOrder() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId");
		servedOrder.setProvidingMemberId("localMemberId");

		List<Order> servedOrders = new ArrayList<Order>();
		servedOrders.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(servedOrders);

		// checking usage
		double expectedDonation = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneOrderFulfilledByRemoteAndOneServedOrder() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId", dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId("remoteMemberId");
		order1.setInstanceId("instanceId1");
		
		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");

		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 2;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneOrderFulfilledByLocalAndOneServedOrder() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId("instanceId1");
		order1.setProvidingMemberId("localMemberId");

		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
				
		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0.0, accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(), ACCEPTABLE_ERROR);
		
		Assert.assertEquals(1,  accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedConsumption, accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testOrdersFulfilledByRemoteAndServedOrderMoreThanOneMember() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId1")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMember")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId("remoteMemberId1");
		order1.setInstanceId("instanceId1");

		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId2", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		
		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage
		double expectedConsumptionFromMember1 = benchmarkingPlugin.getPower("instanceId1@remoteMemberId1") * 2;
		double expectedDonationForMember2 = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(2, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId1"));
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId2"));
		Assert.assertEquals(expectedConsumptionFromMember1, accountingPlugin.getMembersUsage()
				.get("remoteMemberId1").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId1").getDonated(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId2").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember2,
				accountingPlugin.getMembersUsage().get("remoteMemberId2").getDonated(), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testOrdersFulfilledByLocalAndServedOrderMoreThanOneMember() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId("instanceId1");
		order1.setProvidingMemberId("localMemberId");

		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");

		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage
		double expectedConsumptionForUserId = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		double expectedDonationForMember2 = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember2,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(), ACCEPTABLE_ERROR);

		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedConsumptionForUserId,
				accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOrderFulfilledByRemoteAndServedOrderMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId("remoteMemberId");
		order1.setInstanceId("instanceId1");

		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);
		
		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");

		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage is considering 7 minutes
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 7;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 7;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testOrdersFulfilledAndServedOrderMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId3@localMemberId")).thenReturn(5d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId("remoteMemberId");
		order1.setInstanceId("instanceId1");
		
		Order order2 = new Order("id2", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order2.setDateUtils(dateUtils);
		order2.setState(OrderState.FULFILLED);
		order2.setInstanceId("instanceId3");
		order2.setProvidingMemberId("localMemberId");

		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);
		ordersWithInstance.add(order2);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		
		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage is considering 7 minutes
		double expectedConsumptionForMember = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 7;
		double expectedDonationForMember = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 7;
		double expectedDonationForUser = benchmarkingPlugin.getPower("instanceId3@localMemberId") * 7;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumptionForMember,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
		
		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedDonationForUser, accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testOrderFulfilledByLocalAndServedOrderMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Order order1 = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order1.setDateUtils(dateUtils);
		order1.setState(OrderState.FULFILLED);
		order1.setInstanceId("instanceId1");
		order1.setProvidingMemberId("localMemberId");

		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(order1);

		Order servedOrder = new Order("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId", dateUtils);
		servedOrder.setInstanceId("instanceId2");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		
		ordersWithInstance.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(ordersWithInstance);

		// checking usage is considering 7 minutes
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 7;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 7;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(),
				ACCEPTABLE_ERROR);

		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
}
