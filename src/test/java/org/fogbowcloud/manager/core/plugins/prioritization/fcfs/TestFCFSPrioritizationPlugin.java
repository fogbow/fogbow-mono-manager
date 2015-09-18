package org.fogbowcloud.manager.core.plugins.prioritization.fcfs;

import org.junit.Assert;

public class TestFCFSPrioritizationPlugin {

	public void testTakeFrom() {
		FCFSPrioritizationPlugin fcfsPrioritizationPlugin = new FCFSPrioritizationPlugin(null, null);
		Assert.assertNull(fcfsPrioritizationPlugin.takeFrom(null, null));
	}	
}
