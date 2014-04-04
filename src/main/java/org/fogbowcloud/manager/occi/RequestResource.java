package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.occi.core.FogbowUtils;
import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

public class RequestResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = FogbowUtils.getToken(req.getHeaders());

		return FogbowUtils.generateResponseId(application.getRequestsFromUser(userToken), req);
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = FogbowUtils.getToken(req.getHeaders());

		application.removeAllRequests(userToken);
		return "OK";
	}

	@Post
	public String post() {		
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = FogbowUtils.getToken(req.getHeaders());
		
		//check
		FogbowUtils.checkFogbowHeaders(req.getHeaders());
		int numberOfInstances = FogbowUtils.getAttributeInstances(req.getHeaders());
		
		List<RequestUnit> currentRequestUnits = new ArrayList<RequestUnit>();
		for (int i = 0; i < numberOfInstances; i++) {			
			currentRequestUnits.add(application.newRequest(userToken, req));
		}
		return FogbowUtils.generateResponseId(currentRequestUnits, req);
	}

}
