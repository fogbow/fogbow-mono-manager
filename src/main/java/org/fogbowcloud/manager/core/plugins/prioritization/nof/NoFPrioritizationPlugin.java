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
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.occi.order.Order;

public class NoFPrioritizationPlugin implements PrioritizationPlugin {

	private AccountingPlugin accountingPlugin;
	private String localMemberId;
	private boolean trustworthy = false;
	private boolean prioritizeLocal = true;
	
	private static final Logger LOGGER = Logger.getLogger(NoFPrioritizationPlugin.class);

	public NoFPrioritizationPlugin(Properties properties, AccountingPlugin accountingPlugin) {
		this.accountingPlugin = accountingPlugin;
		this.localMemberId = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
		try {
			this.trustworthy = Boolean.valueOf(properties.getProperty("nof_trustworthy").trim());
		} catch (Exception e) {
			LOGGER.error(
					"Error while getting boolean value for nof_trustworhty. The default value is false.",
					e);
		}

		try {
			this.prioritizeLocal = Boolean.valueOf(properties.getProperty("nof_prioritize_local")
					.trim());
		} catch (Exception e) {
			LOGGER.error(
					"Error while getting boolean value for nof_prioritize_local. The default value is true.",
					e);
		}
	}
	
	@Override
	public Order takeFrom(Order newOrder, List<Order> ordersWithInstance) {
		LOGGER.debug("Choosing order to take instance from ordersWithInstance="
				+ ordersWithInstance + " for requestMember=" + newOrder.getRequestingMemberId());
		if (ordersWithInstance == null) {			
			return null;
		}
		
		List<String> servedMemberIds = getServedMemberIds(ordersWithInstance);
		LOGGER.debug("Current servedMemberIds=" + servedMemberIds);
		List<AccountingInfo> accounting = accountingPlugin.getAccountingInfo();
		Map<String, ResourceUsage> membersUsage = NoFHelper.calculateMembersUsage(localMemberId, accounting);
		LOGGER.debug("Current membersUsage=" + membersUsage);		
		LinkedList<FederationMemberDebt> memberDebts = calctMemberDebts(servedMemberIds, membersUsage);
		if (memberDebts.isEmpty()) {
			LOGGER.debug("There are no member debts.");
			return null;
		}

		Collections.sort(memberDebts, new FederationMemberDebtComparator());
		LOGGER.debug("Current memberDebts=" + memberDebts);
		
		double requestingMemberDebt = calcDebt(membersUsage, newOrder.getRequestingMemberId());
		LOGGER.debug("Requesting member debt=" + requestingMemberDebt);
		FederationMemberDebt firstMember = memberDebts.getFirst();
		if (firstMember.getDebt() < requestingMemberDebt) {
			String memberId = firstMember.getMember().getResourcesInfo().getId();
			List<Order> memberRequests = filterByRequestingMember(memberId, ordersWithInstance);
			return getMostRecentOrder(memberRequests);
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

	private List<String> getServedMemberIds(List<Order> orders) {
		List<String> servedMemberIds = new LinkedList<String>();
		for (Order currentOrder : orders) {
			if (!servedMemberIds.contains(currentOrder.getRequestingMemberId())) {
				servedMemberIds.add(currentOrder.getRequestingMemberId());
			}
		}
		return servedMemberIds;
	}

	protected double calcDebt(Map<String, ResourceUsage> membersUsage, String memberId) {
		double debt = 0;
		if (localMemberId.equals(memberId)) {
			if (prioritizeLocal) {
				return Double.MAX_VALUE;
			} else {
				return -1;
			}
		}

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

	private Order getMostRecentOrder(List<Order> memberorders) {
		if (memberorders.isEmpty()) {
			return null;
		}
		Order mostRecentOrder = memberorders.get(0);
		for (Order currentOrder : memberorders) {
			if (new Date(mostRecentOrder.getFulfilledTime()).compareTo(new Date(currentOrder.getFulfilledTime())) < 0) {
				mostRecentOrder = currentOrder;
			}
		}
		return mostRecentOrder;
	}

	private List<Order> filterByRequestingMember(String requestingMemberId, List<Order> orders) {
		List<Order> filteredOrders = new LinkedList<Order>();
		for (Order currentOrder : orders) {
			if (currentOrder.getRequestingMemberId().equals(requestingMemberId)){
				filteredOrders.add(currentOrder);
			}
		}
		return filteredOrders;
	}
}
