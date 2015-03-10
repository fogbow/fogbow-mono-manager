package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class FCUAccountingPlugin implements AccountingPlugin {
	
	long lastUpdate;

	private BenchmarkingPlugin benchmarkingPlugin;
	private Map<String, ResourceUsage> memberIdToConsumption = new HashMap<String, ResourceUsage>();
	private DateUtils dateUtils;
	private static final Logger LOGGER = Logger.getLogger(FCUAccountingPlugin.class);

	public FCUAccountingPlugin(BenchmarkingPlugin benchmarkingPlugin) {
		this(benchmarkingPlugin, new DateUtils());
	}
	
	public FCUAccountingPlugin(BenchmarkingPlugin benchmarkingPlugin, DateUtils dateUtils) {
		this.benchmarkingPlugin = benchmarkingPlugin;
		this.dateUtils = dateUtils;
		this.lastUpdate = dateUtils.currentTimeMillis();
	}
	
	@Override
	public void update(List<Request> fulfilledRequests, List<ServedRequest> servedRequests) {
		LOGGER.debug("Updating account with fulfilledRequests=" + fulfilledRequests
				+ ", and servedRequests=" + servedRequests);		
		long now = dateUtils.currentTimeMillis();		
		long updateInterval = (now - lastUpdate) / 1000 * 60; //(in minutes)
		LOGGER.debug("update interval=" + updateInterval);
		
		// donating		
		for (ServedRequest servedRequest : servedRequests) {
			double instancePower = benchmarkingPlugin.getPower(servedRequest.getInstanceId());
			long donationInterval = (now - servedRequest
					.getCreationTime()) / 1000 * 60; // (in minutes)
			
			LOGGER.debug("donation interval=" + updateInterval);
			
			if (!memberIdToConsumption.containsKey(servedRequest.getMemberId())) {
				memberIdToConsumption.put(servedRequest.getMemberId(), new ResourceUsage());
			}
			
			if (donationInterval < updateInterval) {
				memberIdToConsumption.get(servedRequest.getMemberId()).addDonation(donationInterval * instancePower);
			} else {
				memberIdToConsumption.get(servedRequest.getMemberId()).addDonation(updateInterval * instancePower);
			}
		}

		// consumption
		for (Request request : fulfilledRequests) {
			if (request.getMemberId() != null) {
				if (!memberIdToConsumption.containsKey(request.getMemberId())) {
					memberIdToConsumption.put(request.getMemberId(), new ResourceUsage());
				}

				double instancePower = benchmarkingPlugin.getPower(request.getInstanceId());
				long consumptionInterval = (now - request.getFulfilledTime()) / 1000 * 60; // (in minutes)
				LOGGER.debug("consumption interval=" + consumptionInterval);

				if (consumptionInterval < updateInterval) {
					memberIdToConsumption.get(request.getMemberId()).addConsumption(
							consumptionInterval * instancePower);
				} else {
					memberIdToConsumption.get(request.getMemberId()).addConsumption(
							updateInterval * instancePower);
				}
			}
		}
	
		LOGGER.debug("current usage of members=" + memberIdToConsumption);
		this.lastUpdate = now;
	}

	@Override
	public double getConsumption(String memberId) {
		if (memberIdToConsumption.get(memberId) == null) {
			return -1;
		}
		return memberIdToConsumption.get(memberId).getConsumption();
	}

	@Override
	public double getDonation(String memberId) {
		if (memberIdToConsumption.get(memberId) == null) {
			return -1;
		}
		return memberIdToConsumption.get(memberId).getDonation();
	}
}
