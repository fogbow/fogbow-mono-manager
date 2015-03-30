package org.fogbowcloud.manager.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;

public class NoFMemberPicker implements FederationMemberPicker {
		
	private AccountingPlugin accoutingPlugin;
	private String localMemberId;
	private boolean trustworthy = false;

	private static final Logger LOGGER = Logger.getLogger(NoFMemberPicker.class);
	
	public NoFMemberPicker(Properties properties, AccountingPlugin accoutingPlugin) {
		this.accoutingPlugin = accoutingPlugin;
		this.localMemberId = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
		try {
			this.trustworthy = Boolean.valueOf(properties.getProperty("nof_trustworthy"));			
		} catch (Exception e) {
			LOGGER.error("Error while getting boolean valued from ", e);
		}
	}
	
	@Override
	public FederationMember pick(ManagerController facade) {
		List<FederationMember> onlineMembers = facade.getMembers();
		Map<String, ResourceUsage> membersUsage = accoutingPlugin.getMembersUsage();
		LinkedList<ReputableFederationMember> reputableMembers = new LinkedList<ReputableFederationMember>();

		for (FederationMember currentMember : onlineMembers) {			
			String memberId = currentMember.getResourcesInfo().getId();			
			if (localMemberId.equals(memberId)) {
				continue;
			}
			
			double debt = 0d;
			if (membersUsage.containsKey(memberId)) {
				debt = membersUsage.get(memberId).getConsumed()
						- membersUsage.get(memberId).getDonated();
				if (!trustworthy) {
					debt = Math.max(0,
							debt + Math.sqrt(membersUsage.get(memberId).getDonated()));
				}
			}
			reputableMembers.add(new ReputableFederationMember(currentMember, debt));
		}
		
		if (reputableMembers.isEmpty()) {
			return null;
		}
		Collections.sort(reputableMembers, new ReputableFederationMemberComparator());
		return reputableMembers.getFirst().getMember();
	}

	class ReputableFederationMember {

		private FederationMember member;
		private double debt;

		public ReputableFederationMember(FederationMember member, double debt) {
			this.member = member;
			this.debt = debt;
		}

		public FederationMember getMember() {
			return member;
		}

		public double getDebt() {
			return debt;
		}
	}
	
	class ReputableFederationMemberComparator implements Comparator<ReputableFederationMember> {
		@Override
		public int compare(ReputableFederationMember firstReputableMember,
				ReputableFederationMember secondReputableMember) {

			return new Double(firstReputableMember.getDebt()).compareTo(new Double(
					secondReputableMember.getDebt()));
		}
	}

	public boolean getTrustworthy() {
		return trustworthy;
	}
}

