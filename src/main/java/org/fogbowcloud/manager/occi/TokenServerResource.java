package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.core.Token.Constants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class TokenServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		Map<String, String> attributesToken = new HashMap<String, String>();
		Constants[] tokenConstants = Token.Constants.values();
		for (int i = 0; i < tokenConstants.length; i++) {
			String tokenConstant = tokenConstants[i].getValue();
			String value = req.getHeaders().getValues(tokenConstant);
			if (value != null) {
				attributesToken.put(tokenConstant, value);
			}
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
