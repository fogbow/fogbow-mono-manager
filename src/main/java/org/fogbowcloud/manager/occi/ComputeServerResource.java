package org.fogbowcloud.manager.occi;

import java.util.List;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class ComputeServerResource extends ServerResource{

	@Get
	public String fetch() {		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
//		HeaderUtils.checkOCCIContentType(req.getHeaders());
		String authToken = HeaderUtils.getAuthToken(req.getHeaders());
		String idVM = (String) getRequestAttributes().get("instanceId");

		if (idVM == null) {
			application.getInstances(authToken);			
		}
//		return application.getInstance(authToken, idVM);
		return null;
	}
	
	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String authToken = HeaderUtils.getAuthToken(req.getHeaders());
		String idVM = (String) getRequestAttributes().get("instanceId");

		if (idVM == null) {
//			return application.removeInstances(authToken);
			return null;
		}
//		return application.removeInstance(authToken, idVM);
		return null;
	}
	
	@Post
	public String post() {
		return null;
	}
	
	protected static String generateResponse(List<String> instances, HttpRequest req) {
		String requestEndpoint = req.getHostRef() + req.getHttpCall().getRequestUri();
		String response = "";
		for (String location : instances) {
			response += HeaderUtils.X_OCCI_LOCATION + requestEndpoint + "/" + location + "\n";			
		}
		if (response.equals("")) {
			response = "Empty";
		}
		return response;
	}
}
