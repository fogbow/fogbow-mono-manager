package org.fogbowcloud.manager.core.plugins;

import java.util.List;

import org.fogbowcloud.manager.occi.order.Order;

public interface PrioritizationPlugin {
	
	public Order takeFrom(Order newOrder, List<Order> ordersWithInstance);
	
}
