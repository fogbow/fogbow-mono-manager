package org.fogbowcloud.manager.core.plugins.compute.openstack;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.Flavor;

public class OpenstackOCCITestHelper {

	public static OpenStackOCCIComputePlugin createComputePlugin(Properties properties, List<Flavor> flavors) {
		OpenStackOCCIComputePlugin computePlugin = new OpenStackOCCIComputePlugin(properties);
		computePlugin.setFlavors(flavors);
		return computePlugin;
	}

}
