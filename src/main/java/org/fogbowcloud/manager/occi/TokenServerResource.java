package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.Token;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.data.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public class TokenServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		
		Map<String, String> attributesToken = new HashMap<String, String>();
		Series<Header> headers = req.getHeaders();
		for (Header header : headers) {
			attributesToken.put(header.getName(), new String(header.getValue()));
		}
		
		return generateResponse(application.getToken(attributesToken));
	}

	public String generateResponse(Token token) {
		if (token == null) {
			return new String();
		}
		return token.getAccessId();
	}
}
