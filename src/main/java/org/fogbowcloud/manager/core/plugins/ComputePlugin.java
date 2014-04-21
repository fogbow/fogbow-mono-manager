package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.core.Category;

public interface ComputePlugin {

	public String requestInstance(String authToken,List<Category> categories, Map<String, String> xOCCIAtt);

	public String getInstancesFromUser(String authToken);
	
	public ResourcesInfo getResourcesInfo (String authToken);
	
	public String removeInstance(String authToken, String instanceId);
}
