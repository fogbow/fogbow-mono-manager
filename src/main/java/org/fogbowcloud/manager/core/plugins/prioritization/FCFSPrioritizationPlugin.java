package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class FCFSPrioritizationPlugin implements PrioritizationPlugin {

	public FCFSPrioritizationPlugin(Properties properties, AccountingPlugin accountingPlugin) {
	}

	@Override
	public Request takeFrom(Request newRequest, List<Request> requestsWithInstance) {
		return null;
	}
}
