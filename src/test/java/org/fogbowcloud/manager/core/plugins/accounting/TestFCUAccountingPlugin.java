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
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.FCUAccountingPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFCUAccountingPlugin {

	private static final double ACCEPTABLE_ERROR = 0.0;
	private static final String FAKE_DB_PATH = "src/test/resources/testdbaccounting.sqlite";
	private BenchmarkingPlugin benchmarkingPlugin;
	private FCUAccountingPlugin accountingPlugin;
	Properties properties;

	@Before
	public void setUp() throws IOException {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		properties = new Properties();
		properties.put("accounting_datastore_url", "jdbc:sqlite:" + FAKE_DB_PATH);
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "localMemberId");

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
	}

	@After
	public void tearDown() throws IOException {
		File dbFile = new File(FAKE_DB_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}

	@Test
	public void testEmptyOrders() {
		accountingPlugin.update(new ArrayList<Order>());

		Assert.assertNotNull(accountingPlugin.getAccountingInfo());
		Assert.assertEquals(0, accountingPlugin.getAccountingInfo().size());
	}

	@Test
	public void testOneRequestFulfielledByRemoteAndEmptyServedRequests() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remoteMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order order = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		order.setDateUtils(dateUtils);
		order.setState(OrderState.FULFILLED);
		order.setProvidingMemberId("remoteMemberId");
		order.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(order);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		double usage = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 2;

		List<AccountingInfo> accountingInfo = accountingPlugin.getAccountingInfo();

		Assert.assertEquals(1, accountingInfo.size());
		Assert.assertEquals("localMemberId", accountingInfo.get(0).getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.get(0).getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.get(0).getUser());
		Assert.assertEquals(usage, accountingInfo.get(0).getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneEmptyLocalAndOnFulfilledServedRequest() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order order = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		order.setDateUtils(dateUtils);
		order.setState(OrderState.FULFILLED);
		order.setProvidingMemberId("localMemberId");
		order.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(order);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		double usage = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;

		List<AccountingInfo> accountingInfo = accountingPlugin.getAccountingInfo();

		Assert.assertEquals(1, accountingInfo.size());
		Assert.assertEquals("remoteMemberId", accountingInfo.get(0).getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.get(0).getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.get(0).getUser());
		Assert.assertEquals(usage, accountingInfo.get(0).getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneRequestFulfielledByRemoteAndOneFulfilledServedRequest() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remoteMemberId")).thenReturn(3d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order localOrder = new Order("localId1", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder.setDateUtils(dateUtils);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setProvidingMemberId("remoteMemberId");
		localOrder.setInstanceId("instanceId");

		Order servedOrder = new Order("remtoeId1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrder.setDateUtils(dateUtils);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		servedOrder.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(localOrder);
		orders.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		double usageByRemoteUser = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;
		double usageByLocalUser = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 2;

		List<AccountingInfo> accountingInfo = accountingPlugin.getAccountingInfo();

		Assert.assertEquals(2, accountingInfo.size());
		if (accountingInfo.get(0).getUser().equals("localUserId")) {
			// checking local order
			Assert.assertEquals("localMemberId", accountingInfo.get(0).getRequestingMember());
			Assert.assertEquals("remoteMemberId", accountingInfo.get(0).getProvidingMember());
			Assert.assertEquals("localUserId", accountingInfo.get(0).getUser());
			Assert.assertEquals(usageByLocalUser, accountingInfo.get(0).getUsage(),
					ACCEPTABLE_ERROR);

			// checking served order
			Assert.assertEquals("remoteMemberId", accountingInfo.get(1).getRequestingMember());
			Assert.assertEquals("localMemberId", accountingInfo.get(1).getProvidingMember());
			Assert.assertEquals("remoteUserId", accountingInfo.get(1).getUser());
			Assert.assertEquals(usageByRemoteUser, accountingInfo.get(1).getUsage(),
					ACCEPTABLE_ERROR);

		} else {
			// checking local order
			Assert.assertEquals("localMemberId", accountingInfo.get(1).getRequestingMember());
			Assert.assertEquals("remoteMemberId", accountingInfo.get(1).getProvidingMember());
			Assert.assertEquals("localUserId", accountingInfo.get(1).getUser());
			Assert.assertEquals(usageByLocalUser, accountingInfo.get(1).getUsage(),
					ACCEPTABLE_ERROR);

			// checking served order
			Assert.assertEquals("remoteMemberId", accountingInfo.get(0).getRequestingMember());
			Assert.assertEquals("localMemberId", accountingInfo.get(0).getProvidingMember());
			Assert.assertEquals("remoteUserId", accountingInfo.get(0).getUser());
			Assert.assertEquals(usageByRemoteUser, accountingInfo.get(0).getUsage(),
					ACCEPTABLE_ERROR);
		}
	}

	@Test
	public void testSameUserOneRequestFulfielledBySeveralRemoteAndOneFulfilledServedRequest() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remote1MemberId")).thenReturn(3d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remote2MemberId")).thenReturn(5d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order localOrder1 = new Order("localId1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder1.setDateUtils(dateUtils);
		localOrder1.setState(OrderState.FULFILLED);
		localOrder1.setProvidingMemberId("remote1MemberId");
		localOrder1.setInstanceId("instanceId");

		Order localOrder2 = new Order("localId1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder2.setDateUtils(dateUtils);
		localOrder2.setState(OrderState.FULFILLED);
		localOrder2.setProvidingMemberId("remote2MemberId");
		localOrder2.setInstanceId("instanceId");

		Order servedOrder = new Order("remtoeId1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, false, "remote1MemberId");
		servedOrder.setDateUtils(dateUtils);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		servedOrder.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(localOrder1);
		orders.add(localOrder2);
		orders.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking three entries
		Assert.assertEquals(3, accountingPlugin.getAccountingInfo().size());

		// checking usage on remote1
		double usageOnRemote1 = benchmarkingPlugin.getPower("instanceId@remote1MemberId") * 2;
		AccountingInfo accountingInfo = accountingPlugin.getAccountingInfo("userId",
				"localMemberId", "remote1MemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remote1MemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnRemote1, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage on remote1
		double usageOnRemote2 = benchmarkingPlugin.getPower("instanceId@remote2MemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "localMemberId",
				"remote2MemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remote2MemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnRemote2, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage on local
		double usageOnLocal = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "remote1MemberId",
				"localMemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("remote1MemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnLocal, accountingInfo.getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testSeveralUsersOneRequestFulfielledByLocalAndOneFulfilledServedRequest() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(3d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId3@localMemberId")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId4@localMemberId")).thenReturn(5d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order localOrderUser1 = new Order("localId1", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrderUser1.setDateUtils(dateUtils);
		localOrderUser1.setState(OrderState.FULFILLED);
		localOrderUser1.setProvidingMemberId("localMemberId");
		localOrderUser1.setInstanceId("instanceId1");

		Order localOrderUser2 = new Order("localId1", new Token("accessId", "userId2", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrderUser2.setDateUtils(dateUtils);
		localOrderUser2.setState(OrderState.FULFILLED);
		localOrderUser2.setProvidingMemberId("localMemberId");
		localOrderUser2.setInstanceId("instanceId2");

		Order servedOrderUser1 = new Order("remtoeId1", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrderUser1.setDateUtils(dateUtils);
		servedOrderUser1.setState(OrderState.FULFILLED);
		servedOrderUser1.setProvidingMemberId("localMemberId");
		servedOrderUser1.setInstanceId("instanceId3");

		Order servedOrderUser2 = new Order("remtoeId2", new Token("accessId", "userId2", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrderUser2.setDateUtils(dateUtils);
		servedOrderUser2.setState(OrderState.FULFILLED);
		servedOrderUser2.setProvidingMemberId("localMemberId");
		servedOrderUser2.setInstanceId("instanceId4");

		List<Order> orders = new ArrayList<Order>();
		orders.add(localOrderUser1);
		orders.add(localOrderUser2);
		orders.add(servedOrderUser1);
		orders.add(servedOrderUser2);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking three entries
		Assert.assertEquals(4, accountingPlugin.getAccountingInfo().size());

		// checking usage of userId1
		double usageOrderingOnLocal = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		AccountingInfo accountingInfo = accountingPlugin.getAccountingInfo("userId1",
				"localMemberId", "localMemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId1", accountingInfo.getUser());
		Assert.assertEquals(usageOrderingOnLocal, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		double usageOrderingOnRemote = benchmarkingPlugin.getPower("instanceId3@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId1", "remoteMemberId",
				"localMemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId1", accountingInfo.getUser());
		Assert.assertEquals(usageOrderingOnRemote, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage of userId2
		usageOrderingOnLocal = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId2", "localMemberId",
				"localMemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId2", accountingInfo.getUser());
		Assert.assertEquals(usageOrderingOnLocal, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		usageOrderingOnRemote = benchmarkingPlugin.getPower("instanceId4@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId2", "remoteMemberId",
				"localMemberId");

		Assert.assertNotNull(accountingInfo);
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId2", accountingInfo.getUser());
		Assert.assertEquals(usageOrderingOnRemote, accountingInfo.getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testIncrementingUsage() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remoteMemberId")).thenReturn(3d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order localOrder = new Order("localId1", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder.setDateUtils(dateUtils);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setProvidingMemberId("remoteMemberId");
		localOrder.setInstanceId("instanceId");

		Order servedOrder = new Order("remtoeId1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrder.setDateUtils(dateUtils);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		servedOrder.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(localOrder);
		orders.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(2, accountingPlugin.getAccountingInfo().size());

		// checking usage of local user
		double usageByLocalUser = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 2;
		AccountingInfo accountingInfo = accountingPlugin.getAccountingInfo("localUserId",
				"localMemberId", "remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("localUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByLocalUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage of remote user
		double usageByRemoteUser = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("remoteUserId", "remoteMemberId",
				"localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("remoteUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByRemoteUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// updating dateUtils
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(2, accountingPlugin.getAccountingInfo().size());

		// checking usage of local user
		usageByLocalUser = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 4;
		accountingInfo = accountingPlugin.getAccountingInfo("localUserId", "localMemberId",
				"remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("localUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByLocalUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage of remote user
		usageByRemoteUser = benchmarkingPlugin.getPower("instanceId@localMemberId") * 4;
		accountingInfo = accountingPlugin.getAccountingInfo("remoteUserId", "remoteMemberId",
				"localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("remoteUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByRemoteUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testIncrementingUsageAndAddingDiffUserOrder() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remoteMemberId")).thenReturn(3d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@remoteMemberId")).thenReturn(4d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order localOrder = new Order("localId1", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder.setDateUtils(dateUtils);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setProvidingMemberId("remoteMemberId");
		localOrder.setInstanceId("instanceId");

		Order servedOrder = new Order("remtoeId1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrder.setDateUtils(dateUtils);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		servedOrder.setInstanceId("instanceId");

		List<Order> orders = new ArrayList<Order>();
		orders.add(localOrder);
		orders.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(2, accountingPlugin.getAccountingInfo().size());

		// checking usage of local user
		double usageByLocalUser = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 2;
		AccountingInfo accountingInfo = accountingPlugin.getAccountingInfo("localUserId",
				"localMemberId", "remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("localUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByLocalUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage of remote user
		double usageByRemoteUser = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("remoteUserId", "remoteMemberId",
				"localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("remoteUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByRemoteUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// adding order
		Order localOrder2 = new Order("localId2", new Token("accessId", "localUserId2", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder2.setDateUtils(dateUtils);
		localOrder2.setState(OrderState.FULFILLED);
		localOrder2.setProvidingMemberId("remoteMemberId");
		localOrder2.setInstanceId("instanceId2");

		orders.add(localOrder2);

		// updating dateUtils
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(3, accountingPlugin.getAccountingInfo().size());

		// checking usage of local user
		usageByLocalUser = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 4;
		accountingInfo = accountingPlugin.getAccountingInfo("localUserId", "localMemberId",
				"remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("localUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByLocalUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage of remote user
		usageByRemoteUser = benchmarkingPlugin.getPower("instanceId@localMemberId") * 4;
		accountingInfo = accountingPlugin.getAccountingInfo("remoteUserId", "remoteMemberId",
				"localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("remoteUserId", accountingInfo.getUser());
		Assert.assertEquals(usageByRemoteUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage of local user 2
		usageByLocalUser = benchmarkingPlugin.getPower("instanceId2@remoteMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("localUserId2", "localMemberId",
				"remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("localUserId2", accountingInfo.getUser());
		Assert.assertEquals(usageByLocalUser, accountingInfo.getUsage(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testIncrementingUsageAndAddingSameUserOrder() {
		// mocking dateUtils
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(3d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@remoteMemberId")).thenReturn(4d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin,
				dateUtils);

		Order localOrder = new Order("localId1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder.setDateUtils(dateUtils);
		localOrder.setState(OrderState.FULFILLED);
		localOrder.setProvidingMemberId("remoteMemberId");
		localOrder.setInstanceId("instanceId1");

		Order servedOrder = new Order("remtoeId1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrder.setDateUtils(dateUtils);
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		servedOrder.setInstanceId("instanceId1");

		List<Order> orders = new ArrayList<Order>();
		orders.add(localOrder);
		orders.add(servedOrder);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(2, accountingPlugin.getAccountingInfo().size());

		// checking usage on local member
		double usageOnLocalMember = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		AccountingInfo accountingInfo = accountingPlugin.getAccountingInfo("userId",
				"remoteMemberId", "localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnLocalMember, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage on remote member
		double usageOnRemoteMember = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "localMemberId",
				"remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnRemoteMember, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// adding local order
		Order localOrder2 = new Order("localId2", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, true, "localMemberId");
		localOrder2.setDateUtils(dateUtils);
		localOrder2.setState(OrderState.FULFILLED);
		localOrder2.setProvidingMemberId("remoteMemberId");
		localOrder2.setInstanceId("instanceId2");

		orders.add(localOrder2);

		// updating dateUtils
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(2, accountingPlugin.getAccountingInfo().size());

		// checking usage on local member
		usageOnLocalMember = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 4;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "remoteMemberId",
				"localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnLocalMember, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage on remote member
		usageOnRemoteMember = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 4
				+ benchmarkingPlugin.getPower("instanceId2@remoteMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "localMemberId",
				"remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnRemoteMember, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// adding served order
		Order servedOrder2 = new Order("remoteId2", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, false, "remoteMemberId");
		servedOrder2.setDateUtils(dateUtils);
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setProvidingMemberId("localMemberId");
		servedOrder2.setInstanceId("instanceId2");

		orders.add(servedOrder2);

		// updating dateUtils
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		// checking usage
		Assert.assertEquals(2, accountingPlugin.getAccountingInfo().size());

		// checking usage on local member
		usageOnLocalMember = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 6
				+ benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "remoteMemberId",
				"localMemberId");
		Assert.assertEquals("remoteMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("localMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnLocalMember, accountingInfo.getUsage(), ACCEPTABLE_ERROR);

		// checking usage on remote member
		usageOnRemoteMember = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 6
				+ benchmarkingPlugin.getPower("instanceId2@remoteMemberId") * 4;
		accountingInfo = accountingPlugin.getAccountingInfo("userId", "localMemberId",
				"remoteMemberId");
		Assert.assertEquals("localMemberId", accountingInfo.getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.getUser());
		Assert.assertEquals(usageOnRemoteMember, accountingInfo.getUsage(), ACCEPTABLE_ERROR);
	}
}
