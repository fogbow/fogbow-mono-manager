package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.restlet.Request;
import org.restlet.Response;

public interface ComputePlugin {

	public String requestInstance(String authToken,List<Category> categories, Map<String, String> xOCCIAtt);

	public List<Instance> getInstances(String authToken);
	
	public Instance getInstance(String authToken, String instanceId); 
	
	public void removeInstance(String authToken, String instanceId);
	
	public void removeInstances(String authToken);
	
	public ResourcesInfo getResourcesInfo(Token token);
	
	public Response bypass(Request request);
}
