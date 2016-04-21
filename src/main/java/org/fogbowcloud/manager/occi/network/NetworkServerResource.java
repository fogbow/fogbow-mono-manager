package org.fogbowcloud.manager.occi.network;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class NetworkServerResource extends ServerResource {
	private static final Logger LOGGER = Logger.getLogger(NetworkServerResource.class);
	
	@Get
	public StringRepresentation fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest request = (HttpRequest) getRequest();
		String federationAuthToken = HeaderUtils.getAuthToken(request.getHeaders(), getResponse(), application.getAuthenticationURI());
		HeaderUtils.getAccept(request.getHeaders());
		
		String user = application.getUser(ComputeServerResource.normalizeAuthToken(federationAuthToken));
		if (user == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		StringRepresentation stringRepresentation = new StringRepresentation(user);
		return stringRepresentation;
	}
	
}
