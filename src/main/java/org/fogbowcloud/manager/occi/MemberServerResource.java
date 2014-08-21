package org.fogbowcloud.manager.occi;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class MemberServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		HeaderUtils.checkOCCIContentType(req.getHeaders());
		
		List<FederationMember> federationMembers = application.getFederationMembers();
		if (federationMembers.size() == 0) {
			return new String();
		}
		
		return generateResponse(federationMembers);
	}

	private String generateResponse(List<FederationMember> federationMembers) {
		StringBuilder response = new StringBuilder();
		for (FederationMember federationMember : federationMembers) {
			String id = federationMember.getResourcesInfo().getId();
			response.append("id=").append(id).append(";");
			String cpuIdle = federationMember.getResourcesInfo().getCpuIdle();
			response.append("cpuIdle=").append(cpuIdle).append(";");
			String cpuInUse = federationMember.getResourcesInfo().getCpuInUse();
			response.append("cpuInUse=").append(cpuInUse).append(";");
			String memIdle = federationMember.getResourcesInfo().getMemIdle();
			response.append("memIdle=").append(memIdle).append(";");
			String memInUse = federationMember.getResourcesInfo().getMemInUse();
			response.append("memInUse=").append(memInUse).append(";");
			
			if (federationMember.getResourcesInfo().getFlavors() != null) {
				for (Flavor flavor : federationMember.getResourcesInfo().getFlavors()) {
					String nameFlavor = flavor.getName();
					Integer capacityFlavor = flavor.getCapacity();
					response.append("flavor: '").append(nameFlavor).append(", capacity=\"")
						.append(capacityFlavor).append("\"';");
				}
			}
			response.append("\n");
		}

		return response.toString().trim();
	}
}
