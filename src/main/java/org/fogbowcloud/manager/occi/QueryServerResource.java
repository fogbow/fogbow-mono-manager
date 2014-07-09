package org.fogbowcloud.manager.occi;

import java.util.List;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.restlet.data.MediaType;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

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

	@Override
	protected Representation head() throws ResourceException {		
		HttpRequest req = (HttpRequest) getRequest();		
		HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		return super.head(); 
	}
	
	public void fetchMetadata() {
		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
		//this call is only to check authToken
		application.getRequestsFromUser(authToken);
		
	}

	public String generateResponse(List<Resource> allResources) {
		String response = "";
		for (Resource resource : allResources) {
			response += "Category: " + resource.toHeader() + "\n"; 
		}
		return response.trim();
	}
}
