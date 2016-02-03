package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNoFPrioritizationPlugin {

	private Properties properties;
	private AccountingPlugin accountingPlugin;

	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put("nof_trustworthy", "false");
		properties.put("nof_prioritize_local", "true");
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);
	}
	
	@Test
	public void testServedOrdersNull() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "memberId");
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom(newOrder, null));
	}
	
	@Test
	public void testEmptyServedOrders() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "memberId");
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom(newOrder, new ArrayList<Order>()));
	}
			
	@Test
	public void testTakeFromOneServedOrder() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Order servedOrder = new Order("id", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1");
		servedOrder.setInstanceId("instanceId");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder);
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2");
		
		Assert.assertEquals(servedOrder, nofPlugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testNoTakeFromBecauseDebtIsTheSame() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(30);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Order servedOrder = new Order("id", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1");
		servedOrder.setInstanceId("instanceId");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder);
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2");
		
		Assert.assertNull(nofPlugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testTakeFromMostRecentServedOrder() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order servedOrder1 = new Order("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder1.setInstanceId("instanceId1");
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Order servedOrder2 = new Order("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder2.setInstanceId("instanceId2");
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder1);
		orders.add(servedOrder2);
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2");
		
		// checking if take from most recent order
		Assert.assertEquals(servedOrder2, nofPlugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testMoreThanOneTakeFromServedOrder() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order servedOrder1 = new Order("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder1.setInstanceId("instanceId1");
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Order servedOrder2 = new Order("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder2.setInstanceId("instanceId2");
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder1);
		orders.add(servedOrder2);
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member2");
		
		// checking if take from most recent order
		Assert.assertEquals(servedOrder2, nofPlugin.takeFrom(newOrder, orders));
		
		orders.remove(servedOrder2);
		Assert.assertEquals(servedOrder1, nofPlugin.takeFrom(newOrder, orders));
		
		orders.remove(servedOrder1);
		Assert.assertNull(nofPlugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testPrioritizeLocalOrder() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Order servedOrder = new Order("id", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1");
		servedOrder.setInstanceId("instanceId");
		servedOrder.setState(OrderState.FULFILLED);
		servedOrder.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder);
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newLocalUserId", null,
				new HashMap<String, String>()), null, null, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Assert.assertEquals(servedOrder, nofPlugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testPrioritizeLocalOrderWithThanOneServedOrder() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order servedOrder1 = new Order("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder1.setInstanceId("instanceId1");
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Order servedOrder2 = new Order("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder2.setInstanceId("instanceId2");
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder1);
		orders.add(servedOrder2);
		
		Order newOrder = new Order("newID", new Token("newAccessId", "newLocalUserId", null,
				new HashMap<String, String>()), null, null, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if take from most recent order
		Assert.assertEquals(servedOrder2, nofPlugin.takeFrom(newOrder, orders));
		
		orders.remove(servedOrder2);
		Assert.assertEquals(servedOrder1, nofPlugin.takeFrom(newOrder, orders));
		
		orders.remove(servedOrder1);
		Assert.assertNull(nofPlugin.takeFrom(newOrder, orders));
	}
}
