package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;

public class SimpleStorageAccountingPlugin implements AccountingPlugin {

	public static final String ACCOUNTING_DATASTORE_URL = "simple_storage_accounting_datastore_url";
	private AccountingDataStore db;
	private DateUtils dateUtils;
	private long lastUpdate;

	private static final Logger LOGGER = Logger.getLogger(SimpleStorageAccountingPlugin.class);

	public SimpleStorageAccountingPlugin(Properties properties) {
		this(properties, new DateUtils());
	}
	
	public SimpleStorageAccountingPlugin(Properties properties, DateUtils dateUtils) {
		this.dateUtils = dateUtils;
		this.lastUpdate = dateUtils.currentTimeMillis();

		properties.put(AccountingDataStore.ACCOUNTING_DATASTORE_URL, 
				properties.getProperty(getDataStoreUrl()));
		db = new AccountingDataStore(properties);
	}

	@Override
	public void update(List<Order> ordersWithInstance) {
		LOGGER.debug("Updating storage account with orders=" + ordersWithInstance);
		long now = dateUtils.currentTimeMillis();
		double updatingInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now - lastUpdate) / 60);
		LOGGER.debug("updating interval=" + updatingInterval);

		Map<AccountingEntryKey, AccountingInfo> usage = new HashMap<AccountingEntryKey, AccountingInfo>();

		for (Order order : ordersWithInstance) {

			if (order.getRequestingMemberId() == null || order.getProvidingMemberId() == null
					|| order.getGlobalInstanceId() == null) {
				continue;
			}
			
			double consumptionInterval = ((double) TimeUnit.MILLISECONDS.toSeconds(now
					- order.getFulfilledTime()) / 60);

			String user = order.getFederationToken().getUser();
			AccountingEntryKey current = new AccountingEntryKey(user,
					order.getRequestingMemberId(), order.getProvidingMemberId());

			if (!usage.keySet().contains(current)) {
				AccountingInfo accountingInfo = new AccountingInfo(current.getUser(),
						current.getRequestingMember(), current.getProvidingMember());
				usage.put(current, accountingInfo);
			}
			
			double instanceUsage = 0; 
			try {
				instanceUsage = getUsage(order, updatingInterval, consumptionInterval);				
			} catch (Exception e) {
				LOGGER.warn("Could not possible get usage of order : " + order.toString());
				continue;
			}

			usage.get(current).addConsumption(instanceUsage);
		}

		LOGGER.debug("current usage=" + usage);

		if ((usage.isEmpty()) || db.update(new ArrayList<AccountingInfo>(usage.values()))) {
			this.lastUpdate = now;
			LOGGER.debug("Updating lastUpdate to " + this.lastUpdate);
		}
	}

	private double getUsage(Order order, double updatingInterval, double consumptionInterval) {
		double instanceUsage = Double.parseDouble(order.getAttValue(
				OrderAttribute.STORAGE_SIZE.getValue()))* Math.min(consumptionInterval, updatingInterval);
		return instanceUsage;
	}
	
	protected String getDataStoreUrl() {
		return ACCOUNTING_DATASTORE_URL;
	}

	@Override
	public List<AccountingInfo> getAccountingInfo() {
		return db.getAccountingInfo();
	}

	@Override
	public AccountingInfo getAccountingInfo(String user, String requestingMember,
			String providingMember) {
		return db.getAccountingInfo(user, requestingMember, providingMember);
	}	
}
