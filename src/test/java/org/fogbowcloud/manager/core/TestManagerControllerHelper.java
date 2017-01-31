package org.fogbowcloud.manager.core;

import java.util.Properties;

import org.fogbowcloud.manager.core.ManagerControllerHelper.MonitoringHelper;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.order.Order;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;


public class TestManagerControllerHelper {
	
	@Test
	public void testCheckDeadOrderAttempts() {
		int monitorPeriod = 12000;
		int orderAttempts = 5;
		long now = System.currentTimeMillis();
		
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.MAXIMUM_ORDER_ATTEMPTS_PROPERTIES, String.valueOf(orderAttempts));
		properties.put(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY, String.valueOf(monitorPeriod));
		MonitoringHelper monitoringHelper = new ManagerControllerHelper().new MonitoringHelper(properties);
		
		DateUtils dateUtilsMock = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(now);
		monitoringHelper.setDateUtils(dateUtilsMock);
		Order orderOne = new Order("idOne", null, null, null, true, null);
		monitoringHelper.addAttempt(orderOne);
		Order orderTwo = new Order("idTwo", null, null, null, true, null);
		monitoringHelper.addAttempt(orderTwo);
		
		Mockito.reset(dateUtilsMock);
		long halfTimeout = monitoringHelper.getTimeout() / 2;
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(now + halfTimeout);
		monitoringHelper.setDateUtils(dateUtilsMock);
		Order orderThree = new Order("idThree", null, null, null, true, null);
		monitoringHelper.addAttempt(orderThree);
		Order orderFour = new Order("idFour", null, null, null, true, null);
		monitoringHelper.addAttempt(orderFour);

		Mockito.reset(dateUtilsMock);
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(now);
		monitoringHelper.checkDeadOrderAttempts();
		monitoringHelper.setDateUtils(dateUtilsMock);
		Assert.assertEquals(4, monitoringHelper.getOrderFailedAttempts().keySet().size()); 

		Mockito.reset(dateUtilsMock);
		long timeForExpireTwoOrders = now - halfTimeout - 100;
		Mockito.when(dateUtilsMock.currentTimeMillis()).thenReturn(timeForExpireTwoOrders);
		monitoringHelper.checkDeadOrderAttempts();
		monitoringHelper.setDateUtils(dateUtilsMock);
		Assert.assertEquals(2, monitoringHelper.getOrderFailedAttempts().keySet().size());
	}
	
}
