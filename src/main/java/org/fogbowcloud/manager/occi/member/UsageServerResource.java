package org.fogbowcloud.manager.occi.member;

import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;

public class UsageServerResource extends MemberServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());

		String memberId = (String) getRequestAttributes().get("memberId");

		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());

		if (memberId != null) {
			return generateResponse(memberId, application.getUsage(authToken, memberId));
		}

		throw new OCCIException(ErrorType.BAD_REQUEST, "The memberId was not specified.");
	}

	private String generateResponse(String memberId, double usage) {
		StringBuilder response = new StringBuilder();
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memberId=" + memberId);
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": usage=" + usage);

		return response.toString().trim();
	}
}
