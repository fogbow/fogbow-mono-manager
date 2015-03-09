package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class FCUAccountingPlugin implements AccountingPlugin {
	
	BenchmarkingPlugin benchmarkingPlugin;
	Map<String, ResourceUsage> memberToUsage = new HashMap<String, ResourceUsage>();
	long lastUpdate;
	
	public FCUAccountingPlugin(BenchmarkingPlugin benchmarkingPlugin) {
		this.benchmarkingPlugin = benchmarkingPlugin;
		this.lastUpdate = System.currentTimeMillis();
	}
	
	@Override
	public void update(List<Request> fulfilledRequests, List<ServedRequest> servedRequests) {		
		long now = System.currentTimeMillis();
		long updateInterval = (now - lastUpdate) / 1000 * 60; //(in minutes)
		
		// donating		
		for (ServedRequest servedRequest : servedRequests) {
			double instancePower = benchmarkingPlugin.getPower(servedRequest.getInstanceId());
			long donationInterval = (now - servedRequest
					.getCreationTime()) / 1000 * 60; // (in minutes)
			
			if (!memberToUsage.containsKey(servedRequest.getMemberId())) {
				memberToUsage.put(servedRequest.getMemberId(), new ResourceUsage());
			}
			
			if (donationInterval < updateInterval) {
				memberToUsage.get(servedRequest.getMemberId()).addDonation(donationInterval * instancePower);
			} else {
				memberToUsage.get(servedRequest.getMemberId()).addDonation(updateInterval * instancePower);
			}
		}

		// consumption
		for (Request request : fulfilledRequests) {
			if (request.getMemberId() != null) {
				if (!memberToUsage.containsKey(request.getMemberId())) {
					memberToUsage.put(request.getMemberId(), new ResourceUsage());
				}

				double instancePower = benchmarkingPlugin.getPower(request.getInstanceId());
				long consumptionInterval = (now - request.getFulfilledTime()) / 1000 * 60; // (in minutes)

				if (consumptionInterval < updateInterval) {
					memberToUsage.get(request.getMemberId()).addDonation(
							consumptionInterval * instancePower);
				} else {
					memberToUsage.get(request.getMemberId()).addDonation(
							updateInterval * instancePower);
				}
			}
		}
		
		this.lastUpdate = now;
	}
}
