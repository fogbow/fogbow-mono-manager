package org.fogbowcloud.manager.core.plugins.prioritization;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class PriotizeRemoteRequestPlugin implements PrioritizationPlugin {

	private static final Logger LOGGER = Logger.getLogger(PriotizeRemoteRequestPlugin.class);
	
	public PriotizeRemoteRequestPlugin(Properties properties, AccountingPlugin accountingPlugin){
		
	}
	
	@Override
	public Request takeFrom(Request newRequest, List<Request> requestsWithInstance) {
		LOGGER.debug("Choosing request to take instance from requestsWithInstance="
				+ requestsWithInstance + " for requestMember=" + newRequest.getRequestingMemberId());
		
		if (newRequest.isLocal()) {
			return null;
		}
		
		List<Request> federationUserRequests = filterRequestsFulfilledByFedUser(requestsWithInstance);
		LOGGER.debug("federationUserRequests=" + federationUserRequests);
		return getMostRecentRequest(federationUserRequests);
	}
	
	private List<Request> filterRequestsFulfilledByFedUser(List<Request> requestsWithInstance) {
		List<Request> federationUserRequests = new ArrayList<Request>();
		for (Request currentRequest : requestsWithInstance) {
			federationUserRequests.add(currentRequest);
		}
		return federationUserRequests;
	}

	private Request getMostRecentRequest(List<Request> requests) {
		if (requests.isEmpty()) {
			return null;
		}
		Request mostRecentRequest = requests.get(0);
		for (Request currentRequest : requests) {
			if (new Date(mostRecentRequest.getFulfilledTime()).compareTo(new Date(currentRequest
					.getFulfilledTime())) < 0) {
				mostRecentRequest = currentRequest;
			}
		}
		return mostRecentRequest;
	}
}
