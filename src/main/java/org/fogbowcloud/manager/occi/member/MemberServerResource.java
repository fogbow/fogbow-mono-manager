package org.fogbowcloud.manager.occi.member;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class MemberServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		
		String memberId = (String) getRequestAttributes().get("memberId");
		
		String federationAuthToken = HeaderUtils.getAuthToken(
				req.getHeaders(), getResponse(), application.getAuthenticationURI());
		
		
		if (memberId != null) {
			return generateFederationMemberQuota(application.getFederationMemberQuota
					(memberId, federationAuthToken));
		}
		
		
		List<FederationMember> federationMembers = application.getFederationMembers(federationAuthToken);
		if (federationMembers.size() == 0) {
			return new String();
		}
		
		return generateFederationMembersResponse(federationMembers);
	}

	private String generateFederationMemberQuota(FederationMember federationMember) {
		if (federationMember == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "...");
		}
		
		StringBuilder response = new StringBuilder();
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": cpuIdle="
				+ federationMember.getResourcesInfo().getCpuIdle());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": cpuInUse="
				+ federationMember.getResourcesInfo().getCpuInUse());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memIdle="
				+ federationMember.getResourcesInfo().getMemIdle());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memInUse="
				+ federationMember.getResourcesInfo().getMemInUse());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": instancesIdle="
				+ federationMember.getResourcesInfo().getInstancesIdle());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": instancesInUse="
				+ federationMember.getResourcesInfo().getInstancesInUse());
		
		return response.toString().trim();
	}
	
	private String generateFederationMembersResponse(List<FederationMember> federationMembers) {
		StringBuilder response = new StringBuilder();
		for (FederationMember federationMember : federationMembers) {
			response.append(federationMember.getId());
			response.append("\n");
		}
		return response.toString();
	}
	
}
