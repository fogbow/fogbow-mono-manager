package org.fogbowcloud.manager.occi;

import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class KeyStoneServer extends ServerResource {

	@Get
	public String fetch() {
		KeyStoneApplication keyStoneApplication = new KeyStoneApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String token = req.getHeaders().getValues(OCCIHeaders.X_AUTH_TOKEN);
		keyStoneApplication.checkUserByToken(token);

		return keyStoneApplication.getUserFromToken(token);
	}
}
