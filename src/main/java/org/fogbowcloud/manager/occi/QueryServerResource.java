package org.fogbowcloud.manager.occi;

import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class QueryServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(QueryServerResource.class);

	@Get
	public String fetch() {
		LOGGER.debug("Executing the query interface fetch method");
		if (getRequest().getMethod().equals(Method.HEAD)){
			LOGGER.debug("It is a HEAD method request");
			HttpRequest req = (HttpRequest) getRequest();
			String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
			LOGGER.debug("Auth Token = " + token);
			if (token == null || token.equals("")) {
				//FIXME keystone URI hard coded
				HeaderUtils.setResponseHeader(getResponse(), HeaderUtils.WWW_AUTHENTICATE, "Keystone uri='http://localhost:5000/'");
				getResponse().setStatus(new Status(HttpStatus.SC_UNAUTHORIZED));
			}
			return "";
		} else {
			LOGGER.debug("It is a GET method request");
			OCCIApplication application = (OCCIApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
			LOGGER.debug("Auth Token = " + authToken);			
			List<Resource> allResources = application.getAllResources(authToken);
			LOGGER.debug("All Resources = " + allResources);
			return generateResponse(allResources);
		}
		
	}

	public String generateResponse(List<Resource> allResources) {
		String response = "";		
		for (Resource resource : allResources) {
			response += "Category: " + resource.toHeader() + "\n"; 
		}
		return "\n" + response.trim();
	}
}
