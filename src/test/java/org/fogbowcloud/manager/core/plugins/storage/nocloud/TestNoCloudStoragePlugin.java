package org.fogbowcloud.manager.core.plugins.storage.nocloud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestNoCloudStoragePlugin {
	private Token defaultToken;
	private NoCloudStoragePlugin noCloudStoragePlugin;
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		defaultToken = new Token("", "", null, null);
		noCloudStoragePlugin = new NoCloudStoragePlugin(properties);
	}
	
	@Test(expected = OCCIException.class)
	public void testRequestInstance() {
		noCloudStoragePlugin.requestInstance(defaultToken, new ArrayList<Category>(), 
				new HashMap<String, String>());
	}
	
	@Test
	public void testGetInstance() {
		String instanceId = "1";
		Instance instance = noCloudStoragePlugin.getInstance(defaultToken, instanceId);
		Assert.assertNull(instance);
	}
	
	@Test
	public void testGetInstances() {
		List<Instance> instances = noCloudStoragePlugin.getInstances(defaultToken);
		Assert.assertEquals(0, instances.size());
	}
	
	@Test
	public void doNothing() {
		noCloudStoragePlugin.removeInstances(null);
		noCloudStoragePlugin.removeInstance(defaultToken, null);
	}
}
