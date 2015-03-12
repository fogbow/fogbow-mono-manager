package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.request.Request;

public interface AccountingPlugin {
	
	public void update(List<Request> requests, List<ServedRequest> servedRequest);

	public Map<String, ResourceUsage> getUsage(List<String> members);
}
