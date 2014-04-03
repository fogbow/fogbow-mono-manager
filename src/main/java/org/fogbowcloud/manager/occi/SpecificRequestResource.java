package org.fogbowcloud.manager.occi;

import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class SpecificRequestResource extends ServerResource {

	@Get
	public String fetch() {
		
		String requestId = (String) getRequestAttributes().get("requestid");

		// TODO What should we do if there are others headers?

		HttpRequest req = (HttpRequest) getRequest();
		String userToken = req.getHeaders().getValues(HeaderConstants.X_AUTH_TOKEN);

		OCCIApplication application = (OCCIApplication) getApplication();
		RequestUnit requestUnit;
		
		requestUnit = application.getRequestDetails(userToken, requestId);		
		return requestUnit.toHttMessageFormat();
	}

	@Delete
	public String remove() {
		OCCIApplication application = (OCCIApplication) getApplication();
		return null;
	}

}
