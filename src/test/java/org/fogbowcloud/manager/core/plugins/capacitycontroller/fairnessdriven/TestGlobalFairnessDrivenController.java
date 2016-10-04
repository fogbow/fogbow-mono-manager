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
import org.fogbowcloud.manager.core.plugins.capacitycontroller.fairnessdriven.GlobalFairnessDrivenController;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGlobalFairnessDrivenController {
	
	private final double ACCEPTABLE_ERROR = 0.000001;
	private final long TIME_UNIT = 1000 * 60;
	
	private static final String FAKE_DB_PATH = "src/test/resources/testdbaccounting.sqlite";
	private BenchmarkingPlugin benchmarkingPlugin;
	private FCUAccountingPlugin accountingPlugin;
	Properties properties;
	DateUtils dateUtils;
	long now;
	
	GlobalFairnessDrivenController globalFCapacityController;
	FederationMember local, remote1, remote2;
	
	double deltaC, minimumThreshold, maximumThreshold, maximumCapacityOfPeer;

	@Before
	public void setUp() throws Exception {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		properties = new Properties();
		properties.put(FCUAccountingPlugin.ACCOUNTING_DATASTORE_URL, "jdbc:sqlite:" + FAKE_DB_PATH);
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
		properties.put(FairnessDrivenCapacityController.CONTROLLER_DELTA,
				String.valueOf(deltaC));
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MINIMUM_THRESHOLD,
				String.valueOf(minimumThreshold));
		properties.put(FairnessDrivenCapacityController.CONTROLLER_MAXIMUM_THRESHOLD,
				String.valueOf(maximumThreshold));
		
		globalFCapacityController = new GlobalFairnessDrivenController(properties, accountingPlugin);
		globalFCapacityController.setDateUtils(dateUtils);
	}
	
	@After
	public void tearDown() throws IOException {
		File dbFile = new File(FAKE_DB_PATH);
		if (dbFile.exists()) {
			dbFile.delete();
		}
	}
	
	@Test
	public void testGetMaxCapacityToSupplyForOneConsumer(){		
		
		long t1 = 10 * TIME_UNIT;
		long t2 = 20 * TIME_UNIT;
		long t3 = 30 * TIME_UNIT;
		long t4 = 40 * TIME_UNIT;
		long t5 = 50 * TIME_UNIT;
		long t6 = 60 * TIME_UNIT;
		long t7 = 70 * TIME_UNIT;
		long t8 = 80 * TIME_UNIT;
		long t9 = 90 * TIME_UNIT;
		long t10 = 100 * TIME_UNIT;
		
		//second 10, during 40 seconds 
		Order localDonatesToRemoteMemberOrder = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMemberOrder.setState(OrderState.FULFILLED);
		localDonatesToRemoteMemberOrder.setInstanceId("instanceId-order1");
		localDonatesToRemoteMemberOrder.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMemberOrderSpy = Mockito.spy(localDonatesToRemoteMemberOrder);
		Mockito.when(localDonatesToRemoteMemberOrderSpy.getFulfilledTime()).thenReturn(t1);
		
		//second 50, during 100 seconds
		Order remoteMemberDonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMemberDonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMemberDonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMemberDonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMemberDonatesToLocalMemberOrderSpy = Mockito.spy(remoteMemberDonatesToLocalMemberOrder);
		Mockito.when(remoteMemberDonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t5);	
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMemberOrderSpy);
		orders.add(remoteMemberDonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);	

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);		
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMemberOrderSpy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
	}	
	
	@Test
	public void testGetMaxCapacityToSupplyForOneConsumerDoubleTime(){		
		
		final long DOUBLE_TIME = 2; 
		
		long t1 = 10 * TIME_UNIT * DOUBLE_TIME;
		long t2 = 20 * TIME_UNIT * DOUBLE_TIME;
		long t3 = 30 * TIME_UNIT * DOUBLE_TIME;
		long t4 = 40 * TIME_UNIT * DOUBLE_TIME;
		long t5 = 50 * TIME_UNIT * DOUBLE_TIME;
		long t6 = 60 * TIME_UNIT * DOUBLE_TIME;
		long t7 = 70 * TIME_UNIT * DOUBLE_TIME;
		long t8 = 80 * TIME_UNIT * DOUBLE_TIME;
		long t9 = 90 * TIME_UNIT * DOUBLE_TIME;
		long t10 = 100 * TIME_UNIT * DOUBLE_TIME;
		
		//second 10, during 40 seconds 
		Order localDonatesToRemoteMemberOrder = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMemberOrder.setState(OrderState.FULFILLED);
		localDonatesToRemoteMemberOrder.setInstanceId("instanceId-order1");
		localDonatesToRemoteMemberOrder.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMemberOrderSpy = Mockito.spy(localDonatesToRemoteMemberOrder);
		Mockito.when(localDonatesToRemoteMemberOrderSpy.getFulfilledTime()).thenReturn(t1);
		
		//second 50, during 100 seconds
		Order remoteMemberDonatesToLocalMemberOrder = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMemberDonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMemberDonatesToLocalMemberOrder.setInstanceId("instanceId-order2");
		remoteMemberDonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMemberDonatesToLocalMemberOrderSpy = Mockito.spy(remoteMemberDonatesToLocalMemberOrder);
		Mockito.when(remoteMemberDonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t5);	
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMemberOrderSpy);
		orders.add(remoteMemberDonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);	

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);		
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMemberOrderSpy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
	}	
	
	@Test
	public void testGetMaxCapacityToSupplyForOneConsumerWithTwoRequests() {
		
		long t0 = 0;
		long t1 = 10 * TIME_UNIT;
		long t2 = 20 * TIME_UNIT;
		long t3 = 30 * TIME_UNIT;
		long t4 = 40 * TIME_UNIT;
		long t5 = 50 * TIME_UNIT;
		long t6 = 60 * TIME_UNIT;
		long t7 = 70 * TIME_UNIT;
		long t8 = 80 * TIME_UNIT;
		long t9 = 90 * TIME_UNIT;
		long t10 = 100 * TIME_UNIT;
		long t11 = 110 * TIME_UNIT;
		long t12 = 120 * TIME_UNIT;
		
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
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);	
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);		
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1Order1Spy);
		orders.remove(localDonatesToRemoteMember1Order2Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(0.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t11);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(0, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t12);						
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(0, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetMaxCapacityToSupplyForTwoConsumers() {
		
		long t0 = 0;
		long t1 = 10 * TIME_UNIT;
		long t2 = 20 * TIME_UNIT;
		long t3 = 30 * TIME_UNIT;
		long t4 = 40 * TIME_UNIT;
		long t5 = 50 * TIME_UNIT;
		long t6 = 60 * TIME_UNIT;
		long t7 = 70 * TIME_UNIT;
		long t8 = 80 * TIME_UNIT;
		long t9 = 90 * TIME_UNIT;
		long t10 = 100 * TIME_UNIT;
		long t11 = 110 * TIME_UNIT;
		long t12 = 120 * TIME_UNIT;
		
		//second 10, during 40 seconds 
		Order localDonatesToRemoteMember1Order1 = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order1.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order1.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order1.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1Order1Spy = Mockito.spy(localDonatesToRemoteMember1Order1);
		Mockito.when(localDonatesToRemoteMember1Order1Spy.getFulfilledTime()).thenReturn(t1);
		
		//second 50, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder2 = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder2.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder2.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder2.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrder2Spy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder2);
		Mockito.when(remoteMember1DonatesToLocalMemberOrder2Spy.getFulfilledTime()).thenReturn(t5);	
		
		//second 0, during 30 seconds
		Order localDonatesToRemoteMember2Order3 = new Order("order3", new Token("", "user@"+remote2.getId(), null, null), null, null, false, remote2.getId());
		localDonatesToRemoteMember2Order3.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember2Order3.setInstanceId("instanceId-order3");
		localDonatesToRemoteMember2Order3.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember2Order3Spy = Mockito.spy(localDonatesToRemoteMember2Order3);
		Mockito.when(localDonatesToRemoteMember2Order3Spy.getFulfilledTime()).thenReturn(t0);	
		
		//second 25, during 25 seconds
		Order remoteMember2DonatesToLocalMemberOrder4 = new Order("order4", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember2DonatesToLocalMemberOrder4.setState(OrderState.FULFILLED);
		remoteMember2DonatesToLocalMemberOrder4.setInstanceId("instanceId-order4");
		remoteMember2DonatesToLocalMemberOrder4.setProvidingMemberId(remote2.getId());
		Order remoteMember2DonatesToLocalMemberOrder4Spy = Mockito.spy(remoteMember2DonatesToLocalMemberOrder4);
		Mockito.when(remoteMember2DonatesToLocalMemberOrder4Spy.getFulfilledTime()).thenReturn((long)25*TIME_UNIT);	
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1Order1Spy);
		orders.add(remoteMember1DonatesToLocalMemberOrder2Spy);
		orders.add(localDonatesToRemoteMember2Order3Spy);
		orders.add(remoteMember2DonatesToLocalMemberOrder4Spy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order3@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order4@remoteMember2Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1);
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(maximumCapacityOfPeer, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2);
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(4.5, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(4, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(4, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember2Order3Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(3.5, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(3, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(3, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1Order1Spy);
		orders.remove(remoteMember2DonatesToLocalMemberOrder4Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(1, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(1.5, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t11);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(2, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t12);				
		accountingPlugin.update(orders);
		globalFCapacityController.updateCapacity(remote1, this.maximumCapacityOfPeer);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		assertEquals(2.5, globalFCapacityController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testUpdateFairness() {		
		
		long t1 = 5 * TIME_UNIT;
		long t2 = 10 * TIME_UNIT;
		long t3 = 20 * TIME_UNIT;
		long t4 = 30 * TIME_UNIT;
		long t5 = 60 * TIME_UNIT;
		long t6 = 105 * TIME_UNIT;
		long t7 = 120 * TIME_UNIT;
		
		//second 5, during 100 seconds
		Order remoteMember2DonatesToLocalMemberOrder = new Order("order1", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember2DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember2DonatesToLocalMemberOrder.setInstanceId("instanceId-order1");
		remoteMember2DonatesToLocalMemberOrder.setProvidingMemberId(remote2.getId());
		Order remoteMember2DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember2DonatesToLocalMemberOrder);
		Mockito.when(remoteMember2DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t1);

		//second 10, during 50 seconds
		Order localDonatesToRemoteMember1Order = new Order("order2", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order.setInstanceId("instanceId-order2");
		localDonatesToRemoteMember1Order.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1OrderSpy = Mockito.spy(localDonatesToRemoteMember1Order);
		Mockito.when(localDonatesToRemoteMember1OrderSpy.getFulfilledTime()).thenReturn(t2);

		//second 20, during 100 seconds
		Order remoteMember1DonatesToLocalMemberOrder = new Order("order3", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder.setInstanceId("instanceId-order3");
		remoteMember1DonatesToLocalMemberOrder.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrderSpy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder);
		Mockito.when(remoteMember1DonatesToLocalMemberOrderSpy.getFulfilledTime()).thenReturn(t3);

		List<Order> orders = new ArrayList<Order>();
		orders.add(remoteMember2DonatesToLocalMemberOrderSpy);
		orders.add(localDonatesToRemoteMember1OrderSpy);
		orders.add(remoteMember1DonatesToLocalMemberOrderSpy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@remoteMember2Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order3@remoteMember1Id")).thenReturn(1d);
				
		// updating dateUtils		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1);				
		accountingPlugin.update(orders);
		assertEquals(-1, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);
		globalFCapacityController.updateFairness();
		assertEquals(-1, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);		

		// updating dateUtils		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2);						
		accountingPlugin.update(orders);
		assertEquals(-1, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);
		globalFCapacityController.updateFairness();
		assertEquals(-1, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);

		// updating dateUtils		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3);
		accountingPlugin.update(orders);

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4);
		accountingPlugin.update(orders);
		assertEquals(-1, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);
		globalFCapacityController.updateFairness();
		assertEquals(35.0/20.0, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);		

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5);		
		accountingPlugin.update(orders);	
		orders.remove(localDonatesToRemoteMember1OrderSpy);	

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6);
		accountingPlugin.update(orders);	
		orders.remove(remoteMember2DonatesToLocalMemberOrderSpy);	

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7);		
		accountingPlugin.update(orders);
		orders.remove(remoteMember2DonatesToLocalMemberOrderSpy);
		assertEquals(35.0/20.0, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);
		globalFCapacityController.updateFairness();
		assertEquals(4, globalFCapacityController.getCurrentFairness(null), ACCEPTABLE_ERROR);	
	}

	@Test
	public void testGetCurrentFairness() {
		assertEquals(-1, globalFCapacityController.getCurrentFairness(remote1), ACCEPTABLE_ERROR);
		assertEquals(-1, globalFCapacityController.getCurrentFairness(remote2), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetLastFairness() {
		assertEquals(-1, globalFCapacityController.getLastFairness(remote1), ACCEPTABLE_ERROR);
		assertEquals(-1, globalFCapacityController.getLastFairness(remote2), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testGetFairness() {
		assertEquals(1, globalFCapacityController.getFairness(1, 1), ACCEPTABLE_ERROR);
		assertEquals(0, globalFCapacityController.getFairness(0, 1), ACCEPTABLE_ERROR);
		assertEquals(-1, globalFCapacityController.getFairness(1, 0), ACCEPTABLE_ERROR);
		assertEquals(-1, globalFCapacityController.getFairness(0, 0), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFairnessWithNegativeConsumption() {
		assertEquals(-1, globalFCapacityController.getFairness(-1, 0), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFairnessWithNegativeDonation() {
		assertEquals(-1, globalFCapacityController.getFairness(0, -1), ACCEPTABLE_ERROR);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetFairnessWithNegativeValues() {
		assertEquals(-1, globalFCapacityController.getFairness(-1, -1), ACCEPTABLE_ERROR);
	}

}
