package org.fogbowcloud.manager.core.plugins.accounting;

import java.util.List;

public interface AccountingDataSatore {
	
	public boolean update(List<AccountingInfo> usage);
	
	public List<AccountingInfo> getAccountingInfo();
	
	public AccountingInfo getAccountingInfo(Object key);
}
