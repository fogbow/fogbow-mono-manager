package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class TokenServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		String username = req.getHeaders().getValues(OCCIHeaders.X_TOKEN_USER);
		String password = req.getHeaders().getValues(OCCIHeaders.X_TOKEN_PASS);
		String tanantName = req.getHeaders().getValues(OCCIHeaders.X_TOKEN_TENANT_NAME);

		Map<String, String> attributesToken = new HashMap<String, String>();
		attributesToken.put(OCCIHeaders.X_TOKEN_USER, username);
		attributesToken.put(OCCIHeaders.X_TOKEN_PASS, password);
		attributesToken.put(OCCIHeaders.X_TOKEN_TENANT_NAME, tanantName);

		return generateResponse(application.getToken(attributesToken));
	}

	public String generateResponse(Token token) {
		if(token == null || token.getAttributes().size() == 0) {
			return new String();
		}
		
		Map<String, String> tokenAttributes = token.getAttributes();
		String tokenId = tokenAttributes.get(OCCIHeaders.X_TOKEN);
		String tenantId = tokenAttributes.get(OCCIHeaders.X_TOKEN_TENANT_ID);

		return OCCIHeaders.X_TOKEN + ":" + tokenId + "\n" + OCCIHeaders.X_TOKEN_TENANT_ID + ":"
				+ tenantId;
	}
}
