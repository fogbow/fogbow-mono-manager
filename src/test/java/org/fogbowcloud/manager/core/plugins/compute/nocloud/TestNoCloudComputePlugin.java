package org.fogbowcloud.manager.core.plugins.compute.nocloud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestNoCloudComputePlugin {

	private NoCloudComputePlugin noCloudComputePlugin;

	@Before
	public void setUp() {
		Properties properties = new Properties();
		this.noCloudComputePlugin = new NoCloudComputePlugin(properties);
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstance() {
		Token token = new Token("", "", null, null);
		noCloudComputePlugin.requestInstance(token, new ArrayList<Category>(),
				new HashMap<String, String>(), "image");
	}

	@Test
	public void testDoNothing() {
		noCloudComputePlugin.removeInstances(null);
		noCloudComputePlugin.bypass(null, null);
	}

	@Test
	public void testGetResource() {
		ResourcesInfo resourcesInfo = noCloudComputePlugin.getResourcesInfo(null);
		Assert.assertNull(resourcesInfo.getId());
		Assert.assertEquals(NoCloudComputePlugin.ZERO, resourcesInfo.getCpuIdle());
		Assert.assertEquals(NoCloudComputePlugin.ZERO, resourcesInfo.getCpuInUse());
		Assert.assertEquals(NoCloudComputePlugin.ZERO, resourcesInfo.getMemIdle());		
		Assert.assertEquals(NoCloudComputePlugin.ZERO, resourcesInfo.getMemInUse());
	}

	@Test
	public void testGetImageId() {
		Assert.assertEquals(NoCloudComputePlugin.FAKE_IMAGE_ID,
				noCloudComputePlugin.getImageId(null, null));
	}

	@Test
	public void testGetImageState() {
		Assert.assertEquals(ImageState.ACTIVE, noCloudComputePlugin.getImageState(null, null));
	}

	@Test
	public void testGetInstances() {
		Assert.assertEquals(0, noCloudComputePlugin.getInstances(null).size());
	}

	@Test
	public void testGetInstance() {
		Assert.assertNull(noCloudComputePlugin.getInstance(null, null));
	}
}
