package org.fogbowcloud.manager.core.plugins.network.nocloud;

import org.fogbowcloud.manager.occi.model.OCCIException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestNoCloudNetworkPlugin {

	private NoCloudNetworkPlugin noCloudNetworkPlugin;
	
	@Before
	public void setUp() {
		this.noCloudNetworkPlugin = new NoCloudNetworkPlugin(null);
	}
	
	@Test
	public void testGetInstance() {
		Assert.assertNull(this.noCloudNetworkPlugin.getInstance(null, null));
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstance() {
		this.noCloudNetworkPlugin.requestInstance(null, null, null);
	}	

	@Test
	public void testRemoveInstance() {
		this.noCloudNetworkPlugin.removeInstance(null, null);
		//do nothing
	}	
	
}
