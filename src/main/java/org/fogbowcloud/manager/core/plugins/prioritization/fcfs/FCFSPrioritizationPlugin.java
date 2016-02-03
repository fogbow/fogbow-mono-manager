package org.fogbowcloud.manager.core.plugins.prioritization.fcfs;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.occi.order.Order;

public class FCFSPrioritizationPlugin implements PrioritizationPlugin {

	public FCFSPrioritizationPlugin(Properties properties, AccountingPlugin accountingPlugin) {
	}

	@Override
	public Order takeFrom(Order newOrder, List<Order> ordersWithInstance) {
		return null;
	}
}
