package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.restlet.Response;
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
			LOGGER.debug("Fogbow resources = " + allResources);
			
			Response response = new Response(getRequest());
			application.bypass(getRequest(), response);			
			if (response.getStatus().getCode() == HttpStatus.SC_OK){
				try {
					String localCloudResources = response.getEntity().getText();
					LOGGER.debug("Local cloud resources: " + localCloudResources);
					return generateResponse(allResources, localCloudResources);
				} catch (Exception e) {
					LOGGER.error("Exception while reading local cloud resources ...", e);
				}
			}
			return generateResponse(allResources, "");
		}
		
	}

	private String generateResponse(List<Resource> fogbowResources, String localCloudResources) {
		String response = "";		
		for (Resource resource : fogbowResources) {
			response += "Category: " + resource.toHeader() + "\n"; 
		}
		
		//adding local cloud resources
		for (Resource localResource : getResourcesFromStr(localCloudResources)) {
			boolean alreadyExists = false;
			for (Resource fogResource : fogbowResources) {
				if (fogResource.matches(localResource)){
					alreadyExists = true;
					break;
				}
			}
			if (!alreadyExists) {
				response += "Category: " + localResource.toHeader() + "\n";
			}
		}		
		return "\n" + response.trim();
	}
	
	private List<Resource> getResourcesFromStr(String resourcesStr) {
		String[] lines = resourcesStr.split("\n");
		List<Resource> resources = new ArrayList<Resource>();
		for (String line : lines) {
			if (line.contains(OCCIHeaders.CATEGORY)){
				resources.add(new Resource(line.substring(line.indexOf(":") + 1)));
			}
		}		
		return resources;
	}
}
