package org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.FCUAccountingPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestPairwiseFairnessDrivenController {

	private final double ACCEPTABLE_ERROR = 0.000001;
	private final long TIME_UNIT = 1000 * 60;
	
	private static final String FAKE_DB_PATH = "src/test/resources/testdbaccounting.sqlite";
	private BenchmarkingPlugin benchmarkingPlugin;
	private FCUAccountingPlugin accountingPlugin;
	Properties properties;
	DateUtils dateUtils;
	long now;
	
	long t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12;
	
	PairwiseFairnessDrivenController fdController;
	FederationMember local, remote1, remote2;
	
	double deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer;

	@Before
	public void setUp() throws Exception {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		properties = new Properties();
		properties.put("accounting_datastore_url", "jdbc:sqlite:" + FAKE_DB_PATH);
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "localMemberId");

		dateUtils = Mockito.mock(DateUtils.class);
		now = 0;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);
		
		local = new FederationMember(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));
		remote1 = new FederationMember("remoteMember1Id");
		remote2 = new FederationMember("remoteMember2Id");	
		
		deltaC = 0.1;
		minimumThreshold = 0.8;
		maximumThreshold = 1;
		maximumCapacityOfPeer = 5;
		properties.put(FairnessDrivenCapacityController.CONTROLLER_DELTA, deltaC+"");
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MINIMUM_THRESHOLD, minimumThreshold+"");
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MAXIMUM_THRESHOLD, maximumThreshold+"");
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MAXIMUM_CAPACITY, maximumCapacityOfPeer+"");
		
		fdController = new PairwiseFairnessDrivenController(properties, accountingPlugin);
		fdController.setDateUtils(dateUtils);
		
		t0 = 0;
		t1 = 10 * TIME_UNIT;
		t2 = 20 * TIME_UNIT;
		t3 = 30 * TIME_UNIT;
		t4 = 40 * TIME_UNIT;
		t5 = 50 * TIME_UNIT;
		t6 = 60 * TIME_UNIT;
		t7 = 70 * TIME_UNIT;
		t8 = 80 * TIME_UNIT;
		t9 = 90 * TIME_UNIT;
		t10 = 100 * TIME_UNIT;
		t11 = 110 * TIME_UNIT;
		t12 = 120 * TIME_UNIT;
	}

	@After
	public void tearDown() throws IOException {
		File dbFile = new File(FAKE_DB_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}
	
	@Test
	public void testGetMaxCapacityToSupplyForOneConsumer() {
		
		//second 10, during 40 seconds
		Order localDonatesToRemoteMember1Order = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1OrderSpy = Mockito.spy(localDonatesToRemoteMember1Order);
		Mockito.when(localDonatesToRemoteMember1OrderSpy.getFulfilledTime()).thenReturn(t1);

		//second 50, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder);
		Mockito.when(remoteMember1DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t5);
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1OrderSpy);
		orders.add(remoteMember1DonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1); 	// updating dateUtils				
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2); 					
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3); 	
		accountingPlugin.update(orders);
		assertEquals(4.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4); 	
		accountingPlugin.update(orders);
		assertEquals(4, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5); 	
		accountingPlugin.update(orders);
		assertEquals(3.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1OrderSpy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6); 	
		accountingPlugin.update(orders);
		assertEquals(3, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7); 	
		accountingPlugin.update(orders);
		assertEquals(2.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8); 	
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9); 	
		accountingPlugin.update(orders);
		assertEquals(1.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10); 	
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetMaxCapacityToSupplyForOneConsumerDoubleTime() {
		
		final long DOUBLE_TIME = 2; 
		t1 *= DOUBLE_TIME;
		t2 *= DOUBLE_TIME;
		t3 *= DOUBLE_TIME;
		t4 *= DOUBLE_TIME;
		t5 *= DOUBLE_TIME;
		t6 *= DOUBLE_TIME;
		t7 *= DOUBLE_TIME;
		t8 *= DOUBLE_TIME;
		t9 *= DOUBLE_TIME;
		t10 *= DOUBLE_TIME;
		
		//second 10, during 40 seconds
		Order localDonatesToRemoteMember1Order = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1OrderSpy = Mockito.spy(localDonatesToRemoteMember1Order);
		Mockito.when(localDonatesToRemoteMember1OrderSpy.getFulfilledTime()).thenReturn(t1);

		//second 50, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder);
		Mockito.when(remoteMember1DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t5);
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1OrderSpy);
		orders.add(remoteMember1DonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1); 	// updating dateUtils				
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2); 					
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3); 	
		accountingPlugin.update(orders);
		assertEquals(4.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4); 	
		accountingPlugin.update(orders);
		assertEquals(4, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5); 	
		accountingPlugin.update(orders);
		assertEquals(3.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1OrderSpy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6); 	
		accountingPlugin.update(orders);
		assertEquals(3, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7); 	
		accountingPlugin.update(orders);
		assertEquals(2.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8); 	
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9); 	
		accountingPlugin.update(orders);
		assertEquals(1.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10); 	
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetMaxCapacityToSupplyForOneConsumerWithTwoRequests() {
		
		//second 10, during 40 seconds
		Order localDonatesToRemoteMember1Order1 = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order1.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order1.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order1.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1Order1Spy = Mockito.spy(localDonatesToRemoteMember1Order1);
		Mockito.when(localDonatesToRemoteMember1Order1Spy.getFulfilledTime()).thenReturn(t1);

		//second 50, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder);
		Mockito.when(remoteMember1DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t5);
		
		//second 0, during 50 seconds
		Order localDonatesToRemoteMember1Order2 = new Order("order3", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order2.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order2.setInstanceId("instanceId-order3");
		localDonatesToRemoteMember1Order2.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1Order2Spy = Mockito.spy(localDonatesToRemoteMember1Order2);
		Mockito.when(localDonatesToRemoteMember1Order2Spy.getFulfilledTime()).thenReturn(t0);

		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1Order1Spy);
		orders.add(remoteMember1DonatesToLocalMemberOrderSpy);
		orders.add(localDonatesToRemoteMember1Order2Spy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order3@localMemberId")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1); 	// updating dateUtils				
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2); 					
		accountingPlugin.update(orders);
		assertEquals(4.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3); 					
		accountingPlugin.update(orders);
		assertEquals(4, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4); 					
		accountingPlugin.update(orders);
		assertEquals(3.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5); 					
		accountingPlugin.update(orders);
		assertEquals(3, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1Order1Spy);
		orders.remove(localDonatesToRemoteMember1Order2Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6); 					
		accountingPlugin.update(orders);
		assertEquals(2.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7); 					
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8); 					
		accountingPlugin.update(orders);
		assertEquals(1.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9); 					
		accountingPlugin.update(orders);
		assertEquals(1, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10); 					
		accountingPlugin.update(orders);
		assertEquals(0.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t11); 					
		accountingPlugin.update(orders);
		assertEquals(0, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t12); 					
		accountingPlugin.update(orders);
		assertEquals(0, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);		
	}
	
	@Test
	public void testGetMaxCapacityToSupplyForTwoConsumers() {
		
		//second 10, during 40 seconds
		Order localDonatesToRemoteMember1Order1 = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order1.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order1.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order1.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1Order1Spy = Mockito.spy(localDonatesToRemoteMember1Order1);
		Mockito.when(localDonatesToRemoteMember1Order1Spy.getFulfilledTime()).thenReturn(t1);

		//second 50, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder);
		Mockito.when(remoteMember1DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t5);

		//second 50, during 100 seconds
		Order localDonatesToRemoteMember2Order1 = new Order("order3", new Token("", "user@"+remote2.getId(), null, null), null, null, false, remote2.getId());
		localDonatesToRemoteMember2Order1.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember2Order1.setInstanceId("instanceId-order3");
		localDonatesToRemoteMember2Order1.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember2Order1Spy = Mockito.spy(localDonatesToRemoteMember2Order1);
		Mockito.when(localDonatesToRemoteMember2Order1Spy.getFulfilledTime()).thenReturn(t5);
		
		//second 10, during 40 seconds
		Order remoteMember2DonatesToLocalMemberOrder = new Order("order4", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember2DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember2DonatesToLocalMemberOrder.setInstanceId("instanceId-order4");
		remoteMember2DonatesToLocalMemberOrder.setProvidingMemberId(remote2.getId());
		Order remoteMember2DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember2DonatesToLocalMemberOrder);
		Mockito.when(remoteMember2DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t1);

		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1Order1Spy);
		orders.add(remoteMember1DonatesToLocalMemberOrderSpy);
		orders.add(localDonatesToRemoteMember2Order1Spy);
		orders.add(remoteMember2DonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order3@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order4@remoteMember2Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1); 	// updating dateUtils				
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2); 					
		accountingPlugin.update(orders);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3); 					
		accountingPlugin.update(orders);
		assertEquals(4.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4); 					
		accountingPlugin.update(orders);
		assertEquals(4, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5); 					
		accountingPlugin.update(orders);
		assertEquals(3.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1Order1Spy);
		orders.remove(remoteMember2DonatesToLocalMemberOrderSpy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6); 					
		accountingPlugin.update(orders);
		assertEquals(3, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7); 					
		accountingPlugin.update(orders);
		assertEquals(2.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8); 					
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9); 					
		accountingPlugin.update(orders);
		assertEquals(1.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(4.5, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10); 					
		accountingPlugin.update(orders);
		assertEquals(2, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t11); 					
		accountingPlugin.update(orders);
		assertEquals(2.5, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(4.5, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t12); 					
		accountingPlugin.update(orders);
		assertEquals(3, fdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(4, fdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testUpdateFairness() {
		
		//second 10, during 50 seconds
		Order localDonatesToRemoteMember1Order = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1OrderSpy = Mockito.spy(localDonatesToRemoteMember1Order);
		Mockito.when(localDonatesToRemoteMember1OrderSpy.getFulfilledTime()).thenReturn(t1);

		//second 20, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder);
		Mockito.when(remoteMember1DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t2);

		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1OrderSpy);
		orders.add(remoteMember1DonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
						
		// updating dateUtils		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1);				
		accountingPlugin.update(orders);
		assertEquals(-1, fdController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);
		fdController.getControllers().put(remote1, new HillClimbingAlgorithm(deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer));		
		fdController.updateFairness(remote1);
		assertEquals(-1, fdController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);		

		// updating dateUtils		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2);						
		accountingPlugin.update(orders);
		assertEquals(-1, fdController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);
		fdController.updateFairness(remote1);
		assertEquals(0, fdController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);

		// updating dateUtils		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3);
		accountingPlugin.update(orders);
		assertEquals(0, fdController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);
		fdController.updateFairness(remote1);
		assertEquals(0.5, fdController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetFairness() {
		assertEquals(1, fdController.getFairness(1, 1), ACCEPTABLE_ERROR);
		assertEquals(0, fdController.getFairness(0, 1), ACCEPTABLE_ERROR);
		assertEquals(-1, fdController.getFairness(1, 0), ACCEPTABLE_ERROR);
		assertEquals(-1, fdController.getFairness(0, 0), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFairnessWithNegativeConsumption() {
		assertEquals(-1, fdController.getFairness(-1, 0), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFairnessWithNegativeDonation() {
		assertEquals(-1, fdController.getFairness(0, -1), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFairnessWithNegativeValues() {
		assertEquals(-1, fdController.getFairness(-1, -1), ACCEPTABLE_ERROR);
	}

}
