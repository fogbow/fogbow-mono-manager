package org.fogbowcloud.manager.occi;

import java.util.List;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class QueryServerResource extends ServerResource {

	@Get
	public String fetch() {
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		return generateResponse(ResourceRepository.getAll());
	}

	public String generateResponse(List<Resource> allResources) {
		String response = "";
		for (Resource resource : allResources) {
			response += "Category: " + resource.toHeader() + "\n"; 
		}
		return response.trim();
	}
}
