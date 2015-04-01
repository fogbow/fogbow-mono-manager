package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.request.Request;

public class NoFPrioritizationPlugin implements PrioritizationPlugin {

	private AccountingPlugin accountingPlugin;
	private String localMemberId;
	private boolean trustworthy = false;
	
	private static final Logger LOGGER = Logger.getLogger(NoFPrioritizationPlugin.class);

	public NoFPrioritizationPlugin(Properties properties, AccountingPlugin accountingPlugin) {
		this.accountingPlugin = accountingPlugin;
		this.localMemberId = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
		try {
			this.trustworthy = Boolean.valueOf(properties.getProperty("nof_trustworthy"));			
		} catch (Exception e) {
			LOGGER.error("Error while getting boolean value for nof_trustworhty. The default value is false.", e);
		}
	}
	
	@Override
	public Request takeFrom(String requestingMemberId, List<Request> requestsWithInstance, List<Request> servedRequests) {
		LOGGER.debug("Choosing request to take instance from requestsWithInstance="
				+ requestsWithInstance + " or servedRequests=" + servedRequests
				+ " for requestMember=" + requestingMemberId);
		if (localMemberId.equals(requestingMemberId) || servedRequests == null) {
			return null;
		}
		
		List<String> remoteMembers = getRemoteMembers(servedRequests);
		Map<String, ResourceUsage> membersUsage = accountingPlugin.getMembersUsage();
		LOGGER.debug("Current membersUsage=" + membersUsage);
		double requestingMemberDebt = calcDebt(membersUsage, requestingMemberId);
		
		LinkedList<FederationMemberDebt> reputableMembers = getReputableMembers(remoteMembers, membersUsage);	
		if (reputableMembers.isEmpty()) {
			return null;
		}

		Collections.sort(reputableMembers, new FederationMemberDebtComparator());
		LOGGER.debug("reputablesMembers=" + reputableMembers);
		
		FederationMemberDebt firstMember = reputableMembers.getFirst();
		if (firstMember.getDebt() < requestingMemberDebt) {
			String memberId = firstMember.getMember().getResourcesInfo().getId();
			List<Request> memberRequests = filterRequests(memberId, servedRequests);
			return getMostRecentRequest(memberRequests);
		}
		return null;
	}

	private LinkedList<FederationMemberDebt> getReputableMembers(List<String> remoteMembers,
			Map<String, ResourceUsage> membersUsage) {
		LinkedList<FederationMemberDebt> reputableMembers = new LinkedList<FederationMemberDebt>();
		for (String currentMemberId : remoteMembers) {
			if (localMemberId.equals(currentMemberId)) {
				continue;
			}
			double debt = calcDebt(membersUsage, currentMemberId);
			reputableMembers.add(new FederationMemberDebt(currentMemberId, debt));
		}
		return reputableMembers;
	}

	private List<String> getRemoteMembers(List<Request> remoteRequests) {
		List<String> members = new LinkedList<String>();
		for (Request remoteRequest : remoteRequests) {
			if (!members.contains(remoteRequest.getRequestingMemberId())) {
				members.add(remoteRequest.getRequestingMemberId());
			}
		}
		return members;
	}

	private double calcDebt(Map<String, ResourceUsage> membersUsage, String memberId) {
		double debt = 0;
		if (membersUsage.containsKey(memberId)) {
			debt = membersUsage.get(memberId).getConsumed()
					- membersUsage.get(memberId).getDonated();
			if (!trustworthy) {
				debt = Math.max(0,
						debt + Math.sqrt(membersUsage.get(memberId).getDonated()));
			}
		}
		return debt;
	}

	private Request getMostRecentRequest(List<Request> memberRequests) {
		if (memberRequests.isEmpty()) {
			return null;
		}
		Request lastRequest = memberRequests.get(0);
		for (Request currentRequest : memberRequests) {
			if (new Date(lastRequest.getFulfilledTime()).compareTo(new Date(currentRequest.getFulfilledTime())) < 0) {
				lastRequest = currentRequest;
			}
		}
		return null;
	}

	private List<Request> filterRequests(String memberId, List<Request> requests) {
		List<Request> filteredRequests = new LinkedList<Request>();
		for (Request currentRequest : requests) {
			if (currentRequest.getRequestingMemberId().equals(memberId)){
				filteredRequests.add(currentRequest);
			}
		}
		return filteredRequests;
	}
}
