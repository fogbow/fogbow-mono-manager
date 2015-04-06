package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
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
	public Request takeFrom(String requestingMemberId, List<Request> requestsWithInstance) {
		LOGGER.debug("Choosing request to take instance from requestsWithInstance="
				+ requestsWithInstance + " for requestMember=" + requestingMemberId);
		if (localMemberId.equals(requestingMemberId) || requestsWithInstance == null) {
			return null;
		}
		
		List<String> servedMemberIds = getServedMemberIds(requestsWithInstance);
		LOGGER.debug("Current servedMemberIds=" + servedMemberIds);
		Map<String, ResourceUsage> membersUsage = accountingPlugin.getMembersUsage();
		LOGGER.debug("Current membersUsage=" + membersUsage);		
		LinkedList<FederationMemberDebt> memberDebts = calctMemberDebts(servedMemberIds, membersUsage);
		if (memberDebts.isEmpty()) {
			LOGGER.debug("There are no member debts.");
			return null;
		}

		Collections.sort(memberDebts, new FederationMemberDebtComparator());
		LOGGER.debug("Current memberDebts=" + memberDebts);
		
		double requestingMemberDebt = calcDebt(membersUsage, requestingMemberId);
		FederationMemberDebt firstMember = memberDebts.getFirst();
		if (firstMember.getDebt() < requestingMemberDebt) {
			String memberId = firstMember.getMember().getResourcesInfo().getId();
			List<Request> memberRequests = filterByRequestingMember(memberId, requestsWithInstance);
			return getMostRecentRequest(memberRequests);
		}
		return null;
	}

	private LinkedList<FederationMemberDebt> calctMemberDebts(List<String> servedMembers,
			Map<String, ResourceUsage> membersUsage) {
		LinkedList<FederationMemberDebt> memberDebts = new LinkedList<FederationMemberDebt>();
		for (String currentMemberId : servedMembers) {
			if (localMemberId.equals(currentMemberId)) {
				continue;
			}
			double debt = calcDebt(membersUsage, currentMemberId);
			memberDebts.add(new FederationMemberDebt(currentMemberId, debt));
		}
		return memberDebts;
	}

	private List<String> getServedMemberIds(List<Request> requests) {
		List<String> servedMemberIds = new LinkedList<String>();
		for (Request currentRequest : requests) {
			if (!currentRequest.isLocal()
					&& !servedMemberIds.contains(currentRequest.getRequestingMemberId())) {
				servedMemberIds.add(currentRequest.getRequestingMemberId());
			}
		}
		return servedMemberIds;
	}

	protected double calcDebt(Map<String, ResourceUsage> membersUsage, String memberId) {
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
		Request mostRecentRequest = memberRequests.get(0);
		for (Request currentRequest : memberRequests) {
			if (new Date(mostRecentRequest.getFulfilledTime()).compareTo(new Date(currentRequest.getFulfilledTime())) < 0) {
				mostRecentRequest = currentRequest;
			}
		}
		return mostRecentRequest;
	}

	private List<Request> filterByRequestingMember(String requestingMemberId, List<Request> requests) {
		List<Request> filteredRequests = new LinkedList<Request>();
		for (Request currentRequest : requests) {
			if (currentRequest.getRequestingMemberId().equals(requestingMemberId)){
				filteredRequests.add(currentRequest);
			}
		}
		return filteredRequests;
	}
}
