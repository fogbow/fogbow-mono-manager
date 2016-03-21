package org.fogbowcloud.manager.occi.member;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class MemberServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		return generateResponse(application.getFederationMembers(authToken));
	}

	private String generateResponse(List<FederationMember> members) {
		if (members.isEmpty()) {
			return new String();
		}

		StringBuilder response = new StringBuilder();
		for (FederationMember member : members) {
			response.append(member.getId());
			response.append("\n");
		}
		return response.toString();
	}	
}