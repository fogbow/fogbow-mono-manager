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
			return " ";
		}
		
		return generateResponse(federationMembers);
	}

	private String generateResponse(List<FederationMember> federationMembers) {
		String response = "";
		for (FederationMember federationMember : federationMembers) {
			String id = federationMember.getResourcesInfo().getId();
			String cpuIdle = federationMember.getResourcesInfo().getCpuIdle();
			String cpuInUse = federationMember.getResourcesInfo().getCpuInUse();
			String memIdle = federationMember.getResourcesInfo().getMemIdle();
			String memInUse = federationMember.getResourcesInfo().getMemInUse();

			String flavorStr = "";
			if (federationMember.getResourcesInfo().getFlavours() != null){
				for (Flavor flavor : federationMember.getResourcesInfo().getFlavours()) {
					String nameFlavor = flavor.getName();
					Integer capacityFlavor = flavor.getCapacity();
					flavorStr = "flavour : \"" + nameFlavor + ", capacity=\"" + capacityFlavor + "";
				}
			}

			if (!cpuIdle.equals("")) {
				cpuIdle = " ; " + "cpuIdle=\"" + cpuIdle ;
			}
			if (!cpuInUse.equals("")) {
				cpuInUse = "\" ; " + "cpuInUse=\"" + cpuInUse;
			}
			if (!memIdle.equals("")) {
				memIdle = "\" ; " + "menIdle=\"" + memIdle;
			}
			if (!memInUse.equals("")) {
				memInUse = "\" ; " + "menInUse=\"" + memInUse;
			}
			if (!flavorStr.equals("")) {
				flavorStr = "\" ; " + flavorStr + "\"";
			}

			response += "id=\"" + id + "\"" + cpuIdle + cpuInUse + memIdle 
					+ memInUse + flavorStr + "\"\n";
		}

		return response.trim();
	}
}
