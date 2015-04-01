package org.fogbowcloud.manager.core.plugins;

import java.util.List;

import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.occi.request.Request;

public interface PrioritizationPlugin {
	
	public Request takeFrom(String requestingMemberId, List<Request> requestsWithInstance, List<ServedRequest> servedRequests);
	
}
