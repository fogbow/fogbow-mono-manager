package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.List;

import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class FCUAccountingPlugin implements AccountingPlugin {
	
	BenchmarkingPlugin benchmarkingPlugin;

	public FCUAccountingPlugin(BenchmarkingPlugin benchmarkingPlugin) {
		this.benchmarkingPlugin = benchmarkingPlugin;
	}
	
	@Override
	public void update(List<Request> requests, List<ServedRequest> servedRequest) {
		// TODO Auto-generated method stub

	}

}
