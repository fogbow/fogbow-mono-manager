package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPrioritizeRemotePlugin {

	private Properties properties;
	private AccountingPlugin accountingPlugin;

	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);	
	}
	
	@Test
	public void testTakeNullForLocalOrder() {		
		PriotizeRemoteOrderPlugin plugin = new PriotizeRemoteOrderPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order servedOrder1 = new Order("id1", new Token("accessId", new Token.User("remoteUserId",
				"remoteUserId") , null, new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder1.setInstanceId("instanceId1");
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Order servedOrder2 = new Order("id2", new Token("accessId", new Token.User("remoteUserId",
				"remoteUserId"), null, new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder2.setInstanceId("instanceId2");
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder1);
		orders.add(servedOrder2);
		
		Order newOrder = new Order("newID", new Token("newAccessId", new Token.User("newRemoteUserId",
				"newRemoteUserId"), null, new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		
		Assert.assertNull(plugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testTakeMostRecentServedOrder() {		
		PriotizeRemoteOrderPlugin plugin = new PriotizeRemoteOrderPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order servedOrder1 = new Order("id1", new Token("accessId", new Token.User("remoteUserId",
				"remoteUserId"), null, new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder1.setInstanceId("instanceId1");
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Order servedOrder2 = new Order("id2", new Token("accessId", new Token.User("remoteUserId",
				"remoteUserId"), null, new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder2.setInstanceId("instanceId2");
		servedOrder2.setState(OrderState.FULFILLED);
		servedOrder2.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder1);
		orders.add(servedOrder2);
		
		Order newOrder = new Order("newID", new Token("newAccessId", new Token.User("newRemoteUserId",
				"newRemoteUserId"), null, new HashMap<String, String>()), null, null, false, "member2", dateUtils);
		
		// checking if take from most recent order
		Assert.assertEquals(servedOrder2, plugin.takeFrom(newOrder, orders));
	}

	@Test
	public void testTakeMostRecentLocalOrder() {		
		PriotizeRemoteOrderPlugin plugin = new PriotizeRemoteOrderPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
				
		Order order1 = new Order("id1", new Token("accessId", new Token.User("localUserId",
				"localUserId"), null, new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		order1.setInstanceId("instanceId1");
		order1.setState(OrderState.FULFILLED);
		order1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Order order2 = new Order("id2", new Token("accessId", new Token.User("localUserId",
				"localUserId"), null, new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		order2.setInstanceId("instanceId2");
		order2.setState(OrderState.FULFILLED);
		order2.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(order1);
		orders.add(order2);
		
		Order newOrder = new Order("newID", new Token("newAccessId", new Token.User("newRemoteUserId",
				"newRemoteUserId"), null, new HashMap<String, String>()), null, null, false, "member2", dateUtils);
		
		// checking if take from most recent order
		Assert.assertEquals(order2, plugin.takeFrom(newOrder, orders));
	}
	
	@Test
	public void testTakeMostRecentAccrossLocalAndServedOrder() {		
		PriotizeRemoteOrderPlugin plugin = new PriotizeRemoteOrderPlugin(properties, accountingPlugin);
			
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order servedOrder1 = new Order("id1", new Token("accessId", new Token.User("remoteUserId",
				"remoteUserId"), null, new HashMap<String, String>()), null, null, false, "member1", dateUtils);
		servedOrder1.setInstanceId("instanceId1");
		servedOrder1.setState(OrderState.FULFILLED);
		servedOrder1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		now += 30;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Order order2 = new Order("id2", new Token("accessId", new Token.User("localUserId",
				"localUserId"), null, new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		order2.setInstanceId("instanceId2");
		order2.setState(OrderState.FULFILLED);
		order2.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		now += 30;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		Order order3 = new Order("id3", new Token("accessId", new Token.User("localUserId",
				"localUserId"), null, new HashMap<String, String>()), null, null, true,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, dateUtils);
		order3.setInstanceId("instanceId3");
		order3.setState(OrderState.FULFILLED);
		order3.setProvidingMemberId("localMemberId");
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(servedOrder1);
		orders.add(order2);
		orders.add(order3);
		
		Order newOrder = new Order("newID", new Token("newAccessId", new Token.User("newRemoteUserId",
				"newRemoteUserId"), null, new HashMap<String, String>()), null, null, false, "member2", dateUtils);
		
		// checking if take from most recent order
		Assert.assertEquals(order3, plugin.takeFrom(newOrder, orders));
	}
}
