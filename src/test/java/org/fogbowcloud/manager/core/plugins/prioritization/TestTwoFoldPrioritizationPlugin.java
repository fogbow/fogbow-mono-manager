package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.fcfs.FCFSPrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.prioritization.nof.NoFPrioritizationPlugin;
import org.fogbowcloud.manager.occi.order.Order;
import org.junit.Assert;
import org.junit.Test;

public class TestTwoFoldPrioritizationPlugin {

	private static final String NOF_PRIORITIZATION_PLUGIN = "org.fogbowcloud.manager.core.plugins.prioritization.nof.NoFPrioritizationPlugin";
	private TwoFoldPrioritizationPlugin twoFoldPrioritizationPlugin;

	@Test
	public void testConstructor() {
		Properties properties = new Properties();
		properties.put(TwoFoldPrioritizationPlugin.LOCAL_PRIORITIZATION_PLUGIN_CLASS,
				NOF_PRIORITIZATION_PLUGIN);
		properties.put(TwoFoldPrioritizationPlugin.REMOTE_PRIORITIZATION_PLUGIN_CLASS,
				NOF_PRIORITIZATION_PLUGIN);
		AccountingPlugin accountingPlugin = null;
		twoFoldPrioritizationPlugin = new TwoFoldPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertTrue(twoFoldPrioritizationPlugin.getLocalPrioritizationPlugin() instanceof NoFPrioritizationPlugin);
		Assert.assertTrue(twoFoldPrioritizationPlugin.getRemotePrioritizationPlugin() instanceof NoFPrioritizationPlugin);
	}

	@Test
	public void testConstructorWithoutLocalAndRemoteProperties() {
		Properties properties = new Properties();
		AccountingPlugin accountingPlugin = null;
		twoFoldPrioritizationPlugin = new TwoFoldPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertTrue(twoFoldPrioritizationPlugin.getLocalPrioritizationPlugin() instanceof FCFSPrioritizationPlugin);
		Assert.assertTrue(twoFoldPrioritizationPlugin.getRemotePrioritizationPlugin() instanceof FCFSPrioritizationPlugin);
	}

	@Test
	public void testConstructorNullProperties() {
		twoFoldPrioritizationPlugin = new TwoFoldPrioritizationPlugin(null, null);
		Assert.assertTrue(twoFoldPrioritizationPlugin.getLocalPrioritizationPlugin() instanceof FCFSPrioritizationPlugin);
		Assert.assertTrue(twoFoldPrioritizationPlugin.getRemotePrioritizationPlugin() instanceof FCFSPrioritizationPlugin);
	}

	@Test
	public void testTakeFrom() {
		twoFoldPrioritizationPlugin = new TwoFoldPrioritizationPlugin(null, null);
		Order newOrder = null;
		List<Order> ordersWithInstance = new ArrayList<Order>();
		ordersWithInstance.add(new Order("One", null, null, null, true, null));
		ordersWithInstance.add(new Order("Two", null, null, null, false, null));
		Assert.assertNull(twoFoldPrioritizationPlugin.takeFrom(newOrder, ordersWithInstance));
	}
}
