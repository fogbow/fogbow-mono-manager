package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
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

		String username = req.getHeaders().getValues(OpenStackIdentityPlugin.USER_KEY);
		String password = req.getHeaders().getValues(OpenStackIdentityPlugin.PASSWORD_KEY);
		String tanantName = req.getHeaders().getValues(OpenStackIdentityPlugin.TENANT_NAME_KEY);

		Map<String, String> attributesToken = new HashMap<String, String>();
		attributesToken.put(OpenStackIdentityPlugin.USER_KEY, username);
		attributesToken.put(OpenStackIdentityPlugin.PASSWORD_KEY, password);
		attributesToken.put(OpenStackIdentityPlugin.TENANT_NAME_KEY, tanantName);

		return generateResponse(application.getToken(attributesToken));
	}

	public String generateResponse(Token token) {
		if (token == null) {
			return new String();
		}
		return token.getAccessId();
	}
}
