package org.fogbowcloud.manager.core;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ManagerControllerHelper.MonitoringHelper;
import org.fogbowcloud.manager.core.ManagerControllerHelper.MonitoringHelper.OrderAttempt;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.order.Order;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestManagerControllerHelper {

	private int ORDER_ATTEMPTS = 5;
	private MonitoringHelper monitoringHelper;
	private Properties propertiesDefault;

	@Before
	public void setUp() {
		int monitorPeriod = 12000;
		
		this.propertiesDefault = new Properties();
		this.propertiesDefault.put(ConfigurationConstants.MAXIMUM_ORDER_ATTEMPTS_PROPERTIES, String.valueOf(ORDER_ATTEMPTS));
		this.propertiesDefault.put(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY, String.valueOf(monitorPeriod));
		this.monitoringHelper = new ManagerControllerHelper().new MonitoringHelper(this.propertiesDefault);
	}
	
	@Test
	public void testEraseFailedMonitoringAttempts() {
		Order order = new Order("idOne", null, null, null, true, null);
		int amountAdd = ORDER_ATTEMPTS - 1;
		for (int i = 0; i < amountAdd ; i++) {
			this.monitoringHelper.addFailedMonitoringAttempt(order);
		}
		
		Map<String, OrderAttempt> orderFailedAttempts = this.monitoringHelper.getOrderFailedAttempts();
		Assert.assertEquals(1, orderFailedAttempts.size());
		OrderAttempt orderOneAttempt = orderFailedAttempts.get(order.getId());
		Assert.assertEquals(new Integer(amountAdd), orderOneAttempt.getAttempts());
		
		this.monitoringHelper.eraseFailedMonitoringAttempts(order);
		Assert.assertEquals(0, orderFailedAttempts.size());
	}
	
	@Test
	public void testIsMaximumFailedMonitoringAttempt() {
		Order order = new Order("idOne", null, null, null, true, null);
		int amountAdd = ORDER_ATTEMPTS - 1;
		for (int i = 0; i < amountAdd ; i++) {
			this.monitoringHelper.addFailedMonitoringAttempt(order);
		}
		
		Map<String, OrderAttempt> orderFailedAttempts = this.monitoringHelper.getOrderFailedAttempts();
		Assert.assertEquals(1, orderFailedAttempts.size());
		OrderAttempt orderOneAttempt = orderFailedAttempts.get(order.getId());
		Assert.assertEquals(new Integer(amountAdd), orderOneAttempt.getAttempts());
		
		Assert.assertFalse(this.monitoringHelper.isMaximumFailedMonitoringAttempts(order));
		
		this.monitoringHelper.addFailedMonitoringAttempt(order);
		Assert.assertEquals(new Integer(ORDER_ATTEMPTS), orderOneAttempt.getAttempts());
		
		Assert.assertTrue(this.monitoringHelper.isMaximumFailedMonitoringAttempts(order));
	}
	
	@Test
	public void testAddFailedMonitoringAttempt() {		
		Map<String, OrderAttempt> orderFailedAttempts = this.monitoringHelper.getOrderFailedAttempts();
		Assert.assertEquals(0, orderFailedAttempts.size());
		
		Order orderOne = new Order("idOne", null, null, null, true, null);
		this.monitoringHelper.addFailedMonitoringAttempt(orderOne);
		
		Assert.assertEquals(1, orderFailedAttempts.size());
		OrderAttempt orderOneAttempt = orderFailedAttempts.get(orderOne.getId());
		Assert.assertEquals(new Integer(1), orderOneAttempt.getAttempts());
		
		this.monitoringHelper.addFailedMonitoringAttempt(orderOne);
		
		Assert.assertEquals(1, orderFailedAttempts.size());
		orderOneAttempt = orderFailedAttempts.get(orderOne.getId());
		Assert.assertEquals(new Integer(2), orderOneAttempt.getAttempts());		
		
		Order orderTwo = new Order("idTwo", null, null, null, true, null);
		this.monitoringHelper.addFailedMonitoringAttempt(orderTwo);
		
		Assert.assertEquals(2, orderFailedAttempts.size());
	}
	
	@Test
	public void testCheckFailedMonitoring() {
		long now = System.currentTimeMillis();
			
		DateUtils dateUtilsMock = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(now);
		monitoringHelper.setDateUtils(dateUtilsMock);
		Order orderOne = new Order("idOne", null, null, null, true, null);
		monitoringHelper.addFailedMonitoringAttempt(orderOne);
		Order orderTwo = new Order("idTwo", null, null, null, true, null);
		monitoringHelper.addFailedMonitoringAttempt(orderTwo);
		
		Mockito.reset(dateUtilsMock);
		long monitorPeriod = ManagerControllerHelper.getInstanceMonitoringPeriod(this.propertiesDefault);
		long halfTimeout = monitoringHelper.getTimeout(monitorPeriod) / 2;
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(now + halfTimeout);
		monitoringHelper.setDateUtils(dateUtilsMock);
		Order orderThree = new Order("idThree", null, null, null, true, null);
		monitoringHelper.addFailedMonitoringAttempt(orderThree);
		Order orderFour = new Order("idFour", null, null, null, true, null);
		monitoringHelper.addFailedMonitoringAttempt(orderFour);

		Mockito.reset(dateUtilsMock);
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(now);
		monitoringHelper.checkFailedMonitoring(monitorPeriod);
		monitoringHelper.setDateUtils(dateUtilsMock);
		Assert.assertEquals(4, monitoringHelper.getOrderFailedAttempts().keySet().size()); 

		Mockito.reset(dateUtilsMock);
		long timeForExpireTwoOrders = now - halfTimeout - 100;
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(timeForExpireTwoOrders);
		monitoringHelper.checkFailedMonitoring(monitorPeriod);
		monitoringHelper.setDateUtils(dateUtilsMock);
		Assert.assertEquals(2, monitoringHelper.getOrderFailedAttempts().keySet().size());
	}
	
}
