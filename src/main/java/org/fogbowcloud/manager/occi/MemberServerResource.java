package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
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
		
		String federationAuthToken = HeaderUtils.getFederationAuthToken(
				req.getHeaders(), getResponse(), application.getAuthenticationURI());
		
		List<FederationMember> federationMembers = application.getFederationMembers(federationAuthToken);
		if (federationMembers.size() == 0) {
			return new String();
		}
		
		return generateResponse(federationMembers);
	}

	private String generateResponse(List<FederationMember> federationMembers) {
		StringBuilder response = new StringBuilder();
		for (FederationMember federationMember : federationMembers) {
			Map<String, String> resourcesInfoMap = new HashMap<String, String>();
			ResourcesInfo resourcesInfo = federationMember.getResourcesInfo();
			resourcesInfoMap.put("id", resourcesInfo.getId());
			resourcesInfoMap.put("cpuIdle", resourcesInfo.getCpuIdle());
			resourcesInfoMap.put("cpuInUse", resourcesInfo.getCpuInUse());
			resourcesInfoMap.put("memIdle", resourcesInfo.getMemIdle());
			resourcesInfoMap.put("memInUse", resourcesInfo.getMemInUse());
			resourcesInfoMap.put("instancesIdle", resourcesInfo.getInstancesIdle());
			resourcesInfoMap.put("instancesInUse", resourcesInfo.getInstancesInUse());
			
			for (Entry<String, String> resourceInfoEntry : resourcesInfoMap.entrySet()) {
				response.append(resourceInfoEntry.getKey()).append("=")
						.append(resourceInfoEntry.getValue()).append(";");
			}
			response.append("\n");
		}

		return response.toString().trim();
	}
}
