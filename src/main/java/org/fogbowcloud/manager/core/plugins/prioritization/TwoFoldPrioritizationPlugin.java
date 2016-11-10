package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.MainHelper;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.fcfs.FCFSPrioritizationPlugin;
import org.fogbowcloud.manager.occi.order.Order;

public class TwoFoldPrioritizationPlugin implements PrioritizationPlugin {

	protected static final String REMOTE_PRIORITIZATION_PLUGIN_CLASS = "remote_prioritization_plugin_class";
	protected static final String LOCAL_PRIORITIZATION_PLUGIN_CLASS = "local_prioritization_plugin_class";
	private PrioritizationPlugin localPrioritizationPlugin;
	private PrioritizationPlugin remotePrioritizationPlugin;
	
	private static final Logger LOGGER = Logger.getLogger(TwoFoldPrioritizationPlugin.class);
	
	public TwoFoldPrioritizationPlugin(Properties properties, AccountingPlugin accountingPlugin) {
		try {
			localPrioritizationPlugin = (PrioritizationPlugin) MainHelper.createInstanceWithAccountingPlugin(
					LOCAL_PRIORITIZATION_PLUGIN_CLASS, properties, accountingPlugin);
		} catch (Exception e) {
			LOGGER.warn("A valid local prioritization plugin was not specified in properties. "
					+ "Using the default one.",	e);
			localPrioritizationPlugin = new FCFSPrioritizationPlugin(properties, accountingPlugin);
		}
		
		try {
			remotePrioritizationPlugin = (PrioritizationPlugin) MainHelper.createInstanceWithAccountingPlugin(
					REMOTE_PRIORITIZATION_PLUGIN_CLASS, properties, accountingPlugin);
		} catch (Exception e) {
			LOGGER.warn("A valid remote prioritization plugin was not specified in properties. "
					+ "Using the default one.",	e);
			remotePrioritizationPlugin = new FCFSPrioritizationPlugin(properties, accountingPlugin);
		}
	}

	@Override
	public Order takeFrom(Order newOrder, List<Order> ordersWithInstance) {
		List<Order> localOrders = new ArrayList<Order>();
		List<Order> servedOrders = new ArrayList<Order>();
		for (Order currentOrder : ordersWithInstance) {
			if (currentOrder.isLocal()) {
				localOrders.add(currentOrder);
			} else {
				servedOrders.add(currentOrder);
			}
		}
		Order localPreemption = localPrioritizationPlugin.takeFrom(newOrder, localOrders);
		return (localPreemption != null) ? localPreemption : 
			remotePrioritizationPlugin.takeFrom(newOrder, servedOrders);
	}
	
	protected PrioritizationPlugin getLocalPrioritizationPlugin() {
		return localPrioritizationPlugin;
	}
	
	protected PrioritizationPlugin getRemotePrioritizationPlugin() {
		return remotePrioritizationPlugin;
	}
}
