package org.fogbowcloud.manager.occi;

import org.fogbowcloud.manager.occi.core.FogbowUtils;
import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class SpecificRequestResource extends ServerResource {

	@Get
	public String fetch() {
		String requestId = (String) getRequestAttributes().get("requestid");

		HttpRequest req = (HttpRequest) getRequest();
		String userToken = FogbowUtils.getToken(req.getHeaders());
		OCCIApplication application = (OCCIApplication) getApplication();
		RequestUnit requestUnit = application.getRequestDetails(userToken, requestId);
		return requestUnit.toHttMessageFormat();
	}

	@Delete
	public String remove() {
		String requestId = (String) getRequestAttributes().get("requestid");

		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String userToken = FogbowUtils.getToken(req.getHeaders());
		application.removeRequest(userToken, requestId);
		return "OK";
	}

}
