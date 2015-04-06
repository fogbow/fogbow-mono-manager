package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.request.Request;

public interface AccountingPlugin {
	
	public void update(List<Request> requestsWithInstance);

	public Map<String, ResourceUsage> getMembersUsage();
	
	public Map<String, Double> getUsersUsage();
}
