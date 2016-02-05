package org.fogbowcloud.manager.core.plugins.accounting.userbased;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.request.Request;

public class UserBasedFCUAccountingPlugin implements AccountingPlugin {

	private BenchmarkingPlugin benchmarkingPlugin;
	private AccountingDataStore db;
	private DateUtils dateUtils;
	private long lastUpdate;

	private static final Logger LOGGER = Logger.getLogger(UserBasedFCUAccountingPlugin.class);

	public UserBasedFCUAccountingPlugin(Properties properties, BenchmarkingPlugin benchmarkingPlugin) {
		this(properties, benchmarkingPlugin, new DateUtils());
	}
	
	public UserBasedFCUAccountingPlugin(Properties properties,
			BenchmarkingPlugin benchmarkingPlugin, DateUtils dateUtils) {
		this.benchmarkingPlugin = benchmarkingPlugin;
		this.dateUtils = dateUtils;
		this.lastUpdate = dateUtils.currentTimeMillis();

		db = new AccountingDataStore(properties);
	}

	@Override
	public void update(List<Request> requestsWithInstance) {
		LOGGER.debug("Updating account with requests=" + requestsWithInstance);
		long now = dateUtils.currentTimeMillis();
		double updatingInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now - lastUpdate) / 60);
		LOGGER.debug("updating interval=" + updatingInterval);

		Map<AccountingEntryKey, AccountingInfo> usage = new HashMap<AccountingEntryKey, AccountingInfo>();

		for (Request request : requestsWithInstance) {

			double consumptionInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now
					- request.getFulfilledTime()) / 60);

			String user = request.getFederationToken().getUser();
			AccountingEntryKey current = new AccountingEntryKey(user,
					request.getRequestingMemberId(), request.getProvidingMemberId());

			if (!usage.keySet().contains(current)) {
				AccountingInfo accountingInfo = new AccountingInfo(current.getUser(),
						current.getRequestingMember(), current.getProvidingMember());
				usage.put(current, accountingInfo);
			}

			double instancePower = benchmarkingPlugin.getPower(request.getGlobalInstanceId());
			double instanceUsage = instancePower * Math.min(consumptionInterval, updatingInterval);

			usage.get(current).addConsuption(instanceUsage);
		}

		LOGGER.debug("current usage=" + usage);

		if ((usage.isEmpty()) || db.update(new ArrayList<AccountingInfo>(usage.values()))) {
			this.lastUpdate = now;
			LOGGER.debug("Updating lastUpdate to " + this.lastUpdate);
		}
	}

	@Override
	public List<AccountingInfo> getAccountingInfo() {
		return db.getAccountingInfo();
	}

	@Override
	public AccountingInfo getAccountingInfo(Object entryKey) {
		return db.getAccountingInfo(entryKey);
	}

	@Override
	public Map<String, ResourceUsage> getMembersUsage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Double> getUsersUsage() {
		// TODO Auto-generated method stub
		return null;
	}

}
