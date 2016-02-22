package org.fogbowcloud.manager.core.plugins;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.order.Order;

public interface AccountingPlugin {
	
	public void update(List<Order> ordersWithInstance);

	public List<AccountingInfo> getAccountingInfo();
	
	public AccountingInfo getAccountingInfo(Object userKey);
	
	@Deprecated
	public Map<String, ResourceUsage> getMembersUsage();
	
	@Deprecated
	public Map<String, Double> getUsersUsage();
}
