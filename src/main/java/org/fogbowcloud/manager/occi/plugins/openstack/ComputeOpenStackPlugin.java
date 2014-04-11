package org.fogbowcloud.manager.occi.plugins.openstack;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.model.InstanceState;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;

public class ComputeOpenStackPlugin implements ComputePlugin {

	private String computeEndPoint;

	public ComputeOpenStackPlugin(String computeEndPoint) {
		this.computeEndPoint = computeEndPoint;
	}

	@Override
	public String requestInstance(List<Category> categories, Map<String, String> xOCCIAtt) {
		// TODO Auto-generated method stub
		return null;
	}

	public InstanceState getInstanceDetails(String instanceId) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<String> getInstancesFromUser(String username) {
		// TODO Auto-generated method stub
		return null;
	}

	public void removeAllInstances(String username) {
		// TODO Auto-generated method stub

	}

	public void removeInstance(String username, String instanceId) {
		// TODO Auto-generated method stub

	}
}
