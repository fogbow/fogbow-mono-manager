package org.fogbowcloud.manager.core.plugins.accounting;

import java.sql.SQLException;
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
	
	long lastUpdate;

	private BenchmarkingPlugin benchmarkingPlugin;
	//TODO this map should be removed from code and getting directly from BD
	private Map<String, ResourceUsage> memberUsage = new HashMap<String, ResourceUsage>();
	private DateUtils dateUtils;
	private static final Logger LOGGER = Logger.getLogger(FCUAccountingPlugin.class);
	
	private DataStorage database;

	public FCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin){		
		this(properties, benchmarkingPlugin, new DateUtils());
	}
	
	public FCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin, DateUtils dateUtils) {
		this.benchmarkingPlugin = benchmarkingPlugin;
		this.dateUtils = dateUtils;
		this.lastUpdate = dateUtils.currentTimeMillis();
		
//		database = new Database(properties);
	}
	
	@Override
	public void update(List<Request> fulfilledRequests, List<ServedRequest> servedRequests) {
		
//		Map<String, ResourceUsage> memberUsage = new HashMap<String, ResourceUsage>();
		LOGGER.debug("Updating account with fulfilledRequests=" + fulfilledRequests
				+ ", and servedRequests=" + servedRequests);		
		long now = dateUtils.currentTimeMillis();		
		long updatingInterval = TimeUnit.MILLISECONDS.toMinutes(now - lastUpdate);
		LOGGER.debug("updating interval=" + updatingInterval);
		
		// donating	
		for (ServedRequest servedRequest : servedRequests) {
			double instancePower = benchmarkingPlugin.getPower(servedRequest.getInstanceId());
			long donationInterval = TimeUnit.MILLISECONDS.toMinutes(now
					- servedRequest.getCreationTime());
			
			LOGGER.debug("donation interval=" + donationInterval);
			
			String memberId = servedRequest.getMemberId();
			if (!memberUsage.containsKey(memberId)) {
				memberUsage.put(memberId, new ResourceUsage(memberId));
			}
			
			if (donationInterval < updatingInterval) {
				memberUsage.get(memberId).addDonation(donationInterval * instancePower);
			} else {
				memberUsage.get(memberId).addDonation(updatingInterval * instancePower);
			}
		}

		// consumption
		for (Request request : fulfilledRequests) {
			String memberId = request.getMemberId();
			if (memberId != null) {
				if (!memberUsage.containsKey(memberId)) {
					memberUsage.put(memberId, new ResourceUsage(memberId));
				}

				double instancePower = benchmarkingPlugin.getPower(request.getInstanceId());
				long consumptionInterval = TimeUnit.MILLISECONDS.toMinutes(now - request.getFulfilledTime());
				LOGGER.debug("consumption interval=" + consumptionInterval);

				if (consumptionInterval < updatingInterval) {
					memberUsage.get(memberId).addConsumption(
							consumptionInterval * instancePower);
				} else {
					memberUsage.get(memberId).addConsumption(
							updatingInterval * instancePower);
				}
			}
		}
	
		LOGGER.debug("current usage of members=" + memberUsage);
//		try {
//			database.updateMembers(memberUsage);
//		} catch (SQLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		this.lastUpdate = now;
	}

	@Override
	public Map<String, ResourceUsage> getUsage(List<String> members) {
		//TODO getting memberUsage map from BD here
		Map<String, ResourceUsage> toReturn = new HashMap<String, ResourceUsage>();
		for (String memberId : members) {
			if (memberUsage.containsKey(memberId)) {
				toReturn.put(memberId, memberUsage.get(memberId));
			}
		}
		return toReturn;
	}
}
