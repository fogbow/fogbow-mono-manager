package org.fogbowcloud.manager.core.plugins.accounting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestSimpleStorageAccountingPlugin {

	private static final double ACCEPTABLE_ERROR = 0.0;
	private static final String FAKE_DB_PATH = "src/test/resources/testsimplestorageaccounting.sqlite";
	private SimpleStorageAccountingPlugin accountingPlugin;
	Properties properties;

	@Before
	public void setUp() throws IOException {
		properties = new Properties();
		properties.put(SimpleStorageAccountingPlugin.ACCOUNTING_DATASTORE_URL, "jdbc:sqlite:" + FAKE_DB_PATH);
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "localMemberId");

		accountingPlugin = new SimpleStorageAccountingPlugin(properties);
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
	public void testOneOrderFulfielledByRemote() {
		// mocking dateUtils
		long now = System.currentTimeMillis();
		String requestingMemberId = "localMemberId";
		String providingMemberId = "remoteMemberId";
		String instanceId = "instanceId";

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin = new SimpleStorageAccountingPlugin(properties, dateUtils);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		String sizeStr = "20";
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), sizeStr);
		Order order = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, xOCCIAtt, true, requestingMemberId);
		order.setDateUtils(dateUtils);
		order.setState(OrderState.FULFILLED);
		order.setProvidingMemberId(providingMemberId);
		order.setInstanceId(instanceId);

		List<Order> orders = new ArrayList<Order>();
		orders.add(order);

		// updating dateUtils
		long minutes = 10;
		now += 1000 * 60 * minutes;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		List<AccountingInfo> accountingInfo = accountingPlugin.getAccountingInfo();

		Assert.assertEquals(1, accountingInfo.size());
		Assert.assertEquals("localMemberId", accountingInfo.get(0).getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.get(0).getProvidingMember());
		Assert.assertEquals("userId", accountingInfo.get(0).getUser());
		Assert.assertEquals(Double.parseDouble(sizeStr) * minutes, accountingInfo.get(0).getUsage(), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=Exception.class)
	public void testOneOrderFulfielledWithoutSizeAttribute() {
		// mocking dateUtils
		long now = System.currentTimeMillis();
		String requestingMemberId = "localMemberId";
		String providingMemberId = "remoteMemberId";
		String instanceId = "instanceId";

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin = new SimpleStorageAccountingPlugin(properties, dateUtils);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		Order order = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, xOCCIAtt, true, requestingMemberId);
		order.setDateUtils(dateUtils);
		order.setState(OrderState.FULFILLED);
		order.setProvidingMemberId(providingMemberId);
		order.setInstanceId(instanceId);

		List<Order> orders = new ArrayList<Order>();
		orders.add(order);

		accountingPlugin.update(orders);
	}	
	
	@Test
	public void testOneOrderOpen() {
		// mocking dateUtils
		long now = System.currentTimeMillis();
		String requestingMemberId = "localMemberId";
		String providingMemberId = "remoteMemberId";

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin = new SimpleStorageAccountingPlugin(properties, dateUtils);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		String sizeStr = "20";
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), sizeStr);
		Order order = new Order("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, xOCCIAtt, true, requestingMemberId);
		order.setDateUtils(dateUtils);
		order.setState(OrderState.FULFILLED);
		order.setProvidingMemberId(providingMemberId);
		order.setInstanceId(null);

		List<Order> orders = new ArrayList<Order>();
		orders.add(order);

		// updating dateUtils
		long minutes = 10;
		now += 1000 * 60 * minutes;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		List<AccountingInfo> accountingInfo = accountingPlugin.getAccountingInfo();

		Assert.assertEquals(0, accountingInfo.size());
	}	
	
	@Test
	public void testTwoOrderFulfielledByRemoteAddingSameUserOrder() {
		// mocking dateUtils
		long now = System.currentTimeMillis();
		String requestingMemberId = "localMemberId";
		String providingMemberId = "remoteMemberId";
		String instanceId = "instanceId";

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin = new SimpleStorageAccountingPlugin(properties, dateUtils);

		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		String sizeStr = "20";
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), sizeStr);
		String userId = "userId";
		Order orderOne = new Order("id1", new Token("accessId", userId, null,
				new HashMap<String, String>()), null, xOCCIAtt, true, requestingMemberId);
		orderOne.setDateUtils(dateUtils);
		orderOne.setState(OrderState.FULFILLED);
		orderOne.setProvidingMemberId(providingMemberId);
		orderOne.setInstanceId(instanceId);
		
		Order orderTwo = new Order("id1", new Token("accessId", userId, null,
				new HashMap<String, String>()), null, xOCCIAtt, true, requestingMemberId);
		orderTwo.setDateUtils(dateUtils);
		orderTwo.setState(OrderState.FULFILLED);
		orderTwo.setProvidingMemberId(providingMemberId);
		orderTwo.setInstanceId(instanceId);		

		List<Order> orders = new ArrayList<Order>();
		orders.add(orderOne);
		orders.add(orderTwo);

		// updating dateUtils
		long minutes = 10;
		now += 1000 * 60 * minutes;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(orders);

		List<AccountingInfo> accountingInfo = accountingPlugin.getAccountingInfo();

		Assert.assertEquals(1, accountingInfo.size());
		Assert.assertEquals("localMemberId", accountingInfo.get(0).getRequestingMember());
		Assert.assertEquals("remoteMemberId", accountingInfo.get(0).getProvidingMember());
		Assert.assertEquals(userId, accountingInfo.get(0).getUser());
		Assert.assertEquals((Double.parseDouble(sizeStr) * minutes) * orders.size(), accountingInfo.get(0).getUsage(), ACCEPTABLE_ERROR);
	}
	
}
