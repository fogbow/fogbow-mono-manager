package org.fogbowcloud.manager.core.plugins;

import java.util.List;

import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.occi.request.Request;

public interface AccountingPlugin {
	
	public static final double UNDEFINED = -1;

	public void update(List<Request> requests, List<ServedRequest> servedRequest);

	public double getConsumption(String memberId);

	public double getDonation(String memberId);
}
