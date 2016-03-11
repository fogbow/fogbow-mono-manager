package org.fogbowcloud.manager.occi.member;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;

public class QuotaServerResource extends MemberServerResource {
	
	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		
		String memberId = (String) getRequestAttributes().get("memberId");
		
		String authToken = HeaderUtils.getAuthToken(req.getHeaders(), getResponse(),
				application.getAuthenticationURI());
		
		if (memberId != null) {
			return generateResponse(application.getFederationMemberQuota(memberId, authToken));
		} 
		
		throw new OCCIException(ErrorType.BAD_REQUEST, "The memberId was not specified.");
	}
	
	private String generateResponse(FederationMember member) {
		if (member == null) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "...");
		}

		StringBuilder response = new StringBuilder();
		try {
			double cpuQuota = Double.parseDouble(member.getResourcesInfo().getCpuIdle())
					+ Double.parseDouble(member.getResourcesInfo().getCpuInUse());
			response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": cpuQuota=" + (int) cpuQuota);
		} catch (Exception e) {
			response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": cpuQuota=" + (-2));
		}
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": cpuInUse="
				+ member.getResourcesInfo().getCpuInUse());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": cpuInUseByUser="
				+ member.getResourcesInfo().getCpuInUseByUser());
		response.append("\n");
		try {
			double memQuota = Double.parseDouble(member.getResourcesInfo().getMemIdle())
					+ Double.parseDouble(member.getResourcesInfo().getMemInUse());
			response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memQuota=" + (int) memQuota);
		} catch (Exception e) {
			response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memQuota=" + (-2));
		}
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memInUse="
				+ member.getResourcesInfo().getMemInUse());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": memInUseByUser="
				+ member.getResourcesInfo().getMemInUseByUser());
		response.append("\n");
		try {
			double instanceQuota = Double.parseDouble(member.getResourcesInfo().getInstancesIdle())
					+ Double.parseDouble(member.getResourcesInfo().getInstancesInUse());
			response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": instancesQuota=" + (int) instanceQuota);
		} catch (Exception e) {
			response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": instanceQuota=" + (-2));
		}
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": instancesInUse="
				+ member.getResourcesInfo().getInstancesInUse());
		response.append("\n");
		response.append(OCCIHeaders.X_OCCI_ATTRIBUTE + ": instancesInUseByUser="
				+ Integer.parseInt(member.getResourcesInfo().getInstancesInUseByUser()));

		return response.toString().trim();
	}
}
