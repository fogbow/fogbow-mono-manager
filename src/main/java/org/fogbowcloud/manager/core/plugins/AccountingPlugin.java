package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.order.Order;

public interface AccountingPlugin {
	
	public void update(List<Order> ordersWithInstance);

	public Map<String, ResourceUsage> getMembersUsage();
	
	public Map<String, Double> getUsersUsage();
}
