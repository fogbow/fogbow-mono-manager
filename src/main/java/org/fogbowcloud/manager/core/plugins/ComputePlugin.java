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

	public String requestInstance(Token token,List<Category> categories, 
			Map<String, String> xOCCIAtt, String imageId);

	public List<Instance> getInstances(Token token);
	
	public Instance getInstance(Token token, String instanceId); 
	
	public void removeInstance(Token token, String instanceId);
	
	public void removeInstances(Token token);
	
	public ResourcesInfo getResourcesInfo(Token token);
	
	public void bypass(Request request, Response response);

	public void uploadImage(Token token, String imagePath, String imageName, String diskFormat);
	
	public String getImageId(Token token, String imageName);
	
}
