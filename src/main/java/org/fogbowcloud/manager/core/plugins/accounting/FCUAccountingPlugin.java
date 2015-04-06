package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class FCUAccountingPlugin implements AccountingPlugin {

	private long lastUpdate;
	private BenchmarkingPlugin benchmarkingPlugin;
	private DateUtils dateUtils;
	private DataStore db;
	private String localMemberId;

	private static final Logger LOGGER = Logger.getLogger(FCUAccountingPlugin.class);

	public FCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin) {
		this(properties, benchmarkingPlugin, new DateUtils());
	}

	public FCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin,
			DateUtils dateUtils) {
		this.benchmarkingPlugin = benchmarkingPlugin;
		this.dateUtils = dateUtils;
		this.lastUpdate = dateUtils.currentTimeMillis();
		this.localMemberId = properties.getProperty("xmpp_jid");

		db = new DataStore(properties);
	}

	@Override
	public void update(List<Request> requestsWithInstance) {
		LOGGER.debug("Updating account with requests=" + requestsWithInstance);
		long now = dateUtils.currentTimeMillis();
		double updatingInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now - lastUpdate) / 60);
		LOGGER.debug("updating interval=" + updatingInterval);

		Map<String, ResourceUsage> usageOfMembers = new HashMap<String, ResourceUsage>();
		Map<String, Double> usageOfUsers = new HashMap<String, Double>();

		for (Request request : requestsWithInstance) {
			//consumption
			if (request.isLocal()) { 
				double consumptionInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now
						- request.getFulfilledTime()) / 60);
				
				updateUsage(request.getGlobalInstanceId(),
						Math.min(consumptionInterval, updatingInterval), false, request.getProvidingMemberId(),
						request.getFederationToken().getUser(), usageOfMembers, usageOfUsers);
				
			} else { //donation
				double donationInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now
						- request.getFulfilledTime()) / 60);
				
				updateUsage(request.getGlobalInstanceId(),
						Math.min(donationInterval, updatingInterval), true,
						request.getRequestingMemberId(), null, usageOfMembers, usageOfUsers);
				
			}			
		}
		LOGGER.debug("current usage of members=" + usageOfMembers);
		LOGGER.debug("current usage of users=" + usageOfUsers);

		if ((usageOfMembers.isEmpty() && usageOfUsers.isEmpty())
				|| db.update(usageOfMembers, usageOfUsers)) {
			this.lastUpdate = now;
			LOGGER.debug("Updating lastUpdate to " + this.lastUpdate);
		}
	}

	private void updateUsage(String globalInstanceId, double usageInterval, boolean isDonation,
			String memberId, String userId, Map<String, ResourceUsage> usageOfMembers,
			Map<String, Double> usageOfUsers) {
		
		double instancePower = benchmarkingPlugin.getPower(globalInstanceId);
		double instanceUsage = instancePower * usageInterval;

		ResourceUsage memberUsage = usageOfMembers.get(memberId);
		if (memberUsage == null && !isLocalMember(memberId)) {
			memberUsage = new ResourceUsage(memberId);
			usageOfMembers.put(memberId, memberUsage);
		}

		if (isDonation) {
			memberUsage.addDonation(instanceUsage);
		} else {
			if (!isLocalMember(memberId)) {
				memberUsage.addConsumption(instanceUsage);
			} else {
				LOGGER.debug("Updating usageOfUsers.");
				if (!usageOfUsers.containsKey(userId)) {
					usageOfUsers.put(userId, 0d);
				}
				usageOfUsers.put(userId, usageOfUsers.get(userId) + instanceUsage);
			}
		}
	}

	private boolean isLocalMember(String memberId) {
		return memberId == null || memberId.equals(localMemberId);
	}

	@Override
	public Map<String, ResourceUsage> getMembersUsage() {
		return db.getMembersUsage();
	}

	public DataStore getDatabase() {
		return db;
	}

	@Override
	public Map<String, Double> getUsersUsage() {
		return db.getUsersUsage();
	}
}
