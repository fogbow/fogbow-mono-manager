package org.fogbowcloud.manager.occi;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class LocalQuotaServerResource extends ServerResource {

	@Get
	public String fetch() {
		OCCIApplication application = (OCCIApplication) getApplication();
		HttpRequest req = (HttpRequest) getRequest();
		String localAccessToken = HeaderUtils.getLocalAuthToken(req.getHeaders());
		if (localAccessToken == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		ResourcesInfo localUserQuota = application.getLocalUserQuota(localAccessToken);
		Map<String, String> resourcesInfoMap = new HashMap<String, String>();
		resourcesInfoMap.put("cpuIdle", localUserQuota.getCpuIdle());
		resourcesInfoMap.put("cpuInUse", localUserQuota.getCpuInUse());
		resourcesInfoMap.put("memIdle", localUserQuota.getMemIdle());
		resourcesInfoMap.put("memInUse", localUserQuota.getMemInUse());
		resourcesInfoMap.put("instancesIdle", localUserQuota.getInstancesIdle());
		resourcesInfoMap.put("instancesInUse", localUserQuota.getInstancesInUse());
		
		StringBuilder response = new StringBuilder();
		for (Entry<String, String> resourceInfoEntry : resourcesInfoMap.entrySet()) {
			response.append(resourceInfoEntry.getKey()).append("=")
					.append(resourceInfoEntry.getValue()).append(";");
		}
		return response.toString().trim();
	}
	
}
