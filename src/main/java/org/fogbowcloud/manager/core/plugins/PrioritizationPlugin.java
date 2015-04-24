package org.fogbowcloud.manager.core.plugins;

import java.util.List;

import org.fogbowcloud.manager.occi.request.Request;

public interface PrioritizationPlugin {
	
	public Request takeFrom(Request newRequest, List<Request> requestsWithInstance);
	
}
