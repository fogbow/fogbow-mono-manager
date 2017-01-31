package org.fogbowcloud.manager.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.order.Order;

public class ManagerControllerHelper {

	private static final long DEFAULT_INSTANCE_MONITORING_PERIOD = 120000; // 2 minutes
	
	private static final Logger LOGGER = Logger.getLogger(ManagerControllerHelper.class);
	
	public ManagerControllerHelper() {
	}
	
	public static long getInstanceMonitoringPeriod(Properties properties) {
		String instanceMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY);
		final long instanceMonitoringPeriod = instanceMonitoringPeriodStr == null ? DEFAULT_INSTANCE_MONITORING_PERIOD
				: Long.valueOf(instanceMonitoringPeriodStr);
		return instanceMonitoringPeriod;
	}
	
	// inner classes
	public class MonitoringHelper {
		
		public static final String MAXIMUM_ORDER_ATTEMPTS_PROPERTIES = "maximum_order_attempts";
		public static final int MAXIMUM_ORDER_ATTEMPTS_DEFAULT = 1000;
		private static final long GRACE_TIME = 1000 * 60 * 20; // 20 minutes 
		
		private DateUtils dateUtils = new DateUtils();
		private Map<String, OrderAttempt> orderFailedAttempts;
		private Integer maximumAttempts;
		private long instanceMonitoringPeriod;
		
		public MonitoringHelper(Properties properties) {
			this.orderFailedAttempts = new HashMap<String, OrderAttempt>();
			try {
				this.maximumAttempts = Integer.parseInt(properties.getProperty(MAXIMUM_ORDER_ATTEMPTS_PROPERTIES));				
			} catch (Exception e) {
				this.maximumAttempts = MAXIMUM_ORDER_ATTEMPTS_DEFAULT;
			}
			this.instanceMonitoringPeriod = getInstanceMonitoringPeriod(properties);
		}
		
		public synchronized void addAttempt(Order order) {
			String orderId = order.getId();
			OrderAttempt orderAttempt = this.orderFailedAttempts.get(orderId);
			if (orderAttempt == null) {
				this.orderFailedAttempts.put(orderId, new OrderAttempt(1, this.dateUtils.currentTimeMillis())); 
			} else {
				orderAttempt.setAttempts(orderAttempt.getAttempts() + 1);
				LOGGER.debug("This order failed " + orderAttempt.getAttempts() + " attempts.");
			}
		}
		
		public synchronized boolean isMaximumAttempt(Order order) {
			String orderId = order.getId();			
			OrderAttempt orderAttempt = this.orderFailedAttempts.get(orderId);			
			if (orderAttempt != null && orderAttempt.getAttempts() >= this.maximumAttempts) {
				this.orderFailedAttempts.remove(orderId);
				return true;
			}
			return false;
		}
		
		public synchronized void removeOrderAttempt(Order order) {
			String orderId = order.getId();
			OrderAttempt orderAttempt = this.orderFailedAttempts.get(orderId);
			if (orderAttempt != null) {
				this.orderFailedAttempts.remove(orderId);
			}
		}
		
		public synchronized void checkDeadOrderAttempts() {
			try {
				Set<String> keys = new HashSet<String> (this.orderFailedAttempts.keySet());
				for (String key : keys) {
					OrderAttempt orderAttempt = null;
					orderAttempt = this.orderFailedAttempts.get(key);
					if (orderAttempt.getInitTime() > this.getTimeout()) {
						this.orderFailedAttempts.remove(key);
					}
				}				
			} catch (Exception e) {
				LOGGER.warn("Error while checking dead orders.", e);
			}
		}
		
		protected long getTimeout() {
			return this.dateUtils.currentTimeMillis() + (this.instanceMonitoringPeriod * this.maximumAttempts) + GRACE_TIME;
		}
		
		protected Map<String, OrderAttempt> getOrderFailedAttempts() {
			return orderFailedAttempts;
		}
		
		public void setDateUtils(DateUtils dateUtils) {
			this.dateUtils = dateUtils;
		}
		
		private class OrderAttempt {

			private Integer attempts;
			private long initTime;

			public OrderAttempt(Integer attempts, long initTime) {
				super();
				this.attempts = attempts;
				this.initTime = initTime;
			}

			public Integer getAttempts() {
				return attempts;
			}
			
			public void setAttempts(Integer attempts) {
				this.attempts = attempts;
			}

			public long getInitTime() {
				return initTime;
			}

		}
		
	}
	
}
