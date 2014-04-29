package org.fogbowcloud.manager.occi;

import org.restlet.engine.adapter.HttpRequest;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class TokenServerResource extends ServerResource {
		
	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		
		String username = req.getHeaders().getValues("username");
		String password = req.getHeaders().getValues("password");
		
		return application.getToken(username, password);		
	}	
}
