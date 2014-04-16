package org.fogbowcloud.manager.occi.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.xmpp.core.ResourcesInfo;

public interface ComputePlugin {

	public String requestInstance(String authToken,List<Category> categories, Map<String, String> xOCCIAtt);

	public String getInstancesFromUser(String authToken);
	
	public ResourcesInfo getResourcesInfo (String authToken);
	
	public String removeInstance(String authToken, String instanceId);
}
