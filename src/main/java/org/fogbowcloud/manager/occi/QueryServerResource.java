package org.fogbowcloud.manager.occi;

import java.util.List;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.Resource;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class QueryServerResource extends ServerResource {

	@Get
	public String fetch() {
		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		
		List<Resource> allResources = application.getAllResources(authToken);
		return generateResponse(allResources);
	}

	public String generateResponse(List<Resource> allResources) {
		String response = "";
		for (Resource resource : allResources) {
			response += "Category: " + resource.toHeader() + "\n"; 
		}
		return response.trim();
	}
}
