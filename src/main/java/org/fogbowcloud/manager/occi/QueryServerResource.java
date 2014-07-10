package org.fogbowcloud.manager.occi;

import java.util.List;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class QueryServerResource extends ServerResource {

	@Get
	public String fetch() {
		if (getRequest().getMethod().equals(Method.HEAD)){
			HttpRequest req = (HttpRequest) getRequest();
			String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
			if (token == null || token.equals("")) {
				Series<Header> responseHeaders = (Series<Header>) getResponse().getAttributes().get("org.restlet.http.headers");
				if (responseHeaders == null) {
					responseHeaders = new Series(Header.class);
					getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
				}
				//FIXME keystone URI hard coded
				responseHeaders.add(new Header(HeaderUtils.WWW_AUTHENTICATE, "Keystone uri='http://localhost:5000/'"));
				getResponse().setStatus(new Status(HttpStatus.SC_UNAUTHORIZED));
			}
			return "";
		} else {
			OCCIApplication application = (OCCIApplication) getApplication();
			HttpRequest req = (HttpRequest) getRequest();
			HeaderUtils.checkOCCIContentType(req.getHeaders());
			String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse());
			
			List<Resource> allResources = application.getAllResources(authToken);
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
