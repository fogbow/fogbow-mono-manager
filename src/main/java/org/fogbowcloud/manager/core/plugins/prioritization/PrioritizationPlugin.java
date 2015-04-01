package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.List;

import org.fogbowcloud.manager.occi.request.Request;

public interface PrioritizationPlugin {
	
	public Request chooseForAllocation(List<Request> openRequests);
	
	public Request takeFrom(List<Request> requestWithInstance);
	
}
