package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class FCUAccountingPlugin implements AccountingPlugin {
	
	private long lastUpdate;
	private BenchmarkingPlugin benchmarkingPlugin;
	private DateUtils dateUtils;
	private DataStore db;
	
	private static final Logger LOGGER = Logger.getLogger(FCUAccountingPlugin.class);
	
	public FCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin){		
		this(properties, benchmarkingPlugin, new DateUtils());
	}
	
	public FCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin, DateUtils dateUtils) {
		this.benchmarkingPlugin = benchmarkingPlugin;
		this.dateUtils = dateUtils;
		this.lastUpdate = dateUtils.currentTimeMillis();
		
		db = new DataStore(properties);
	}
	
	@Override
	public void update(List<Request> requests, List<ServedRequest> servedRequests) {
		LOGGER.debug("Updating account with requests=" + requests + ", and servedRequests="
				+ servedRequests);		
		long now = dateUtils.currentTimeMillis();		
		long updatingInterval = TimeUnit.MILLISECONDS.toMinutes(now - lastUpdate);
		LOGGER.debug("updating interval=" + updatingInterval);
		
		Map<String, ResourceUsage> usageOfMembers = new HashMap<String, ResourceUsage>();
		// donating	
		for (ServedRequest servedRequest : servedRequests) {
			double instancePower = benchmarkingPlugin.getPower(servedRequest.getInstanceId());
			long donationInterval = TimeUnit.MILLISECONDS.toMinutes(now
					- servedRequest.getCreationTime());
			
			LOGGER.debug("donation interval=" + donationInterval);
			
			String memberId = servedRequest.getMemberId();
			if (!usageOfMembers.containsKey(memberId)) {
				usageOfMembers.put(memberId, new ResourceUsage(memberId));
			}
			
			if (donationInterval < updatingInterval) {
				usageOfMembers.get(memberId).addDonation(donationInterval * instancePower);
			} else {
				usageOfMembers.get(memberId).addDonation(updatingInterval * instancePower);
			}
		}

		// consumption
		for (Request request : requests) {
			String memberId = request.getMemberId();
			if (memberId != null) {
				if (!usageOfMembers.containsKey(memberId)) {
					usageOfMembers.put(memberId, new ResourceUsage(memberId));
				}

				double instancePower = benchmarkingPlugin.getPower(request.getInstanceId());
				long consumptionInterval = TimeUnit.MILLISECONDS.toMinutes(now - request.getFulfilledTime());
				LOGGER.debug("consumption interval=" + consumptionInterval);

				if (consumptionInterval < updatingInterval) {
					usageOfMembers.get(memberId).addConsumption(
							consumptionInterval * instancePower);
				} else {
					usageOfMembers.get(memberId).addConsumption(
							updatingInterval * instancePower);
				}
			}
		}
	
		LOGGER.debug("current usage of members=" + usageOfMembers);

		if (usageOfMembers.isEmpty() || db.updateMembers(usageOfMembers)) {
			this.lastUpdate = now;
			LOGGER.debug("Updating lastUpdate to " + this.lastUpdate);
		}
	}

	@Override
	public Map<String, ResourceUsage> getUsage(List<String> members) {
		return db.getUsage(members);
	}
	
	public DataStore getDatabase(){
		return db;
	}
}
