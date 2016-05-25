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

public class TestTwoFoldFairnessDrivenController {
	
	private final double ACCEPTABLE_ERROR = 0.000001;
	private final long TIME_UNIT = 1000 * 60;
	
	private static final String FAKE_DB_PATH = "src/test/resources/testdbaccounting.sqlite";
	private BenchmarkingPlugin benchmarkingPlugin;
	private FCUAccountingPlugin accountingPlugin;
	Properties properties;
	DateUtils dateUtils;
	long now;
	
	long t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12;
	
	TwoFoldCapacityController twoFoldFdController;
	
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
		
		twoFoldFdController = new TwoFoldCapacityController(properties, accountingPlugin);
		twoFoldFdController.setDateUtils(dateUtils);
		
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
	public void testGetMaxCapacityToSupply() {
		
		//second 40, during 50 seconds
		Order localDonatesToRemoteMember1Order1 = new Order("order1", new Token("", "user@"+remote1.getId(), null, null), null, null, false, remote1.getId());
		localDonatesToRemoteMember1Order1.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember1Order1.setInstanceId("instanceId-order1");
		localDonatesToRemoteMember1Order1.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember1Order1Spy = Mockito.spy(localDonatesToRemoteMember1Order1);
		Mockito.when(localDonatesToRemoteMember1Order1Spy.getFulfilledTime()).thenReturn(t4);

		//second 20, during 50 seconds
		Order remoteMember1DonatesToLocalMemberOrder1 = new Order("order2", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder1.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder1.setInstanceId("instanceId-order2");
		remoteMember1DonatesToLocalMemberOrder1.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrder1Spy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder1);
		Mockito.when(remoteMember1DonatesToLocalMemberOrder1Spy.getFulfilledTime()).thenReturn(t2);
		
		//second 40, during 40 seconds
		Order remoteMember1DonatesToLocalMemberOrder2 = new Order("order3", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder2.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder2.setInstanceId("instanceId-order3");
		remoteMember1DonatesToLocalMemberOrder2.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrder2Spy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder2);
		Mockito.when(remoteMember1DonatesToLocalMemberOrder2Spy.getFulfilledTime()).thenReturn(t4);
		
		//second 100, during 20 seconds
		Order remoteMember1DonatesToLocalMemberOrder3 = new Order("order4", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember1DonatesToLocalMemberOrder3.setState(OrderState.FULFILLED);
		remoteMember1DonatesToLocalMemberOrder3.setInstanceId("instanceId-order4");
		remoteMember1DonatesToLocalMemberOrder3.setProvidingMemberId(remote1.getId());
		Order remoteMember1DonatesToLocalMemberOrder3Spy = Mockito.spy(remoteMember1DonatesToLocalMemberOrder3);
		Mockito.when(remoteMember1DonatesToLocalMemberOrder3Spy.getFulfilledTime()).thenReturn(t10);
		
		//second 0, during 90 seconds
		Order localDonatesToRemoteMember2Order1 = new Order("order5", new Token("", "user@"+remote2.getId(), null, null), null, null, false, remote2.getId());
		localDonatesToRemoteMember2Order1.setState(OrderState.FULFILLED);
		localDonatesToRemoteMember2Order1.setInstanceId("instanceId-order5");
		localDonatesToRemoteMember2Order1.setProvidingMemberId(local.getId());
		Order localDonatesToRemoteMember2Order1Spy = Mockito.spy(localDonatesToRemoteMember2Order1);
		Mockito.when(localDonatesToRemoteMember2Order1Spy.getFulfilledTime()).thenReturn(t0);
				
		//second 10, during 30 seconds
		Order remoteMember2DonatesToLocalMemberOrder1 = new Order("order6", new Token("", "user@"+local.getId(), null, null), null, null, false, local.getId());
		remoteMember2DonatesToLocalMemberOrder1.setState(OrderState.FULFILLED);
		remoteMember2DonatesToLocalMemberOrder1.setInstanceId("instanceId-order6");
		remoteMember2DonatesToLocalMemberOrder1.setProvidingMemberId(remote2.getId());
		Order remoteMember2DonatesToLocalMemberOrder1Spy = Mockito.spy(remoteMember2DonatesToLocalMemberOrder1);
		Mockito.when(remoteMember2DonatesToLocalMemberOrder1Spy.getFulfilledTime()).thenReturn(t1);
		
		
		List<Order> orders = new ArrayList<Order>();
		orders.add(localDonatesToRemoteMember1Order1);
		orders.add(remoteMember1DonatesToLocalMemberOrder1Spy);
		orders.add(remoteMember1DonatesToLocalMemberOrder2Spy);
		orders.add(remoteMember1DonatesToLocalMemberOrder3Spy);
		orders.add(localDonatesToRemoteMember2Order1Spy);
		orders.add(remoteMember2DonatesToLocalMemberOrder1Spy);

		Mockito.when(benchmarkingPlugin.getPower("instanceId-order1@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order2@remoteMember1Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order3@remoteMember1Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order4@remoteMember1Id")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order5@localMemberId")).thenReturn(1d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId-order6@remoteMember2Id")).thenReturn(1d);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t1); 	// updating dateUtils				
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t2); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(4.5, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(4.5, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t3); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(4, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(4, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t4); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(4.5, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(3.5, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(remoteMember2DonatesToLocalMemberOrder1Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t5); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(3, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t6); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(2.5, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t7); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(2, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(remoteMember1DonatesToLocalMemberOrder1Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t8); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(1.5, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(remoteMember1DonatesToLocalMemberOrder2Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t9); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(1, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		orders.remove(localDonatesToRemoteMember1Order1Spy);
		orders.remove(localDonatesToRemoteMember2Order1Spy);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t10); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(0.5, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t11); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(0, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
		
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(t12); 		
		accountingPlugin.update(orders);
		twoFoldFdController.updateCapacity(remote1);
		assertEquals(maximumCapacityOfPeer, twoFoldFdController.getMaxCapacityToSupply(remote1), ACCEPTABLE_ERROR);
		twoFoldFdController.updateCapacity(remote2);
		assertEquals(0, twoFoldFdController.getMaxCapacityToSupply(remote2), ACCEPTABLE_ERROR);
	}

}
