package org.fogbowcloud.manager.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;

public class NoFMemberPicker implements FederationMemberPicker {
		
	private AccountingPlugin accoutingPlugin;
	private String localMemberId;

	public NoFMemberPicker(Properties properties, AccountingPlugin accoutingPlugin) {
		this.accoutingPlugin = accoutingPlugin;
		this.localMemberId = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
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
			
			double reputation = 0d;
			if (membersUsage.containsKey(memberId)) {
				reputation = membersUsage.get(memberId).getConsumed()
						- membersUsage.get(memberId).getDonated()
						+ Math.sqrt(membersUsage.get(memberId).getDonated());
			}
			reputableMembers.add(new ReputableFederationMember(currentMember, reputation));
		}
		
		if (reputableMembers.isEmpty()) {
			return null;
		}
		Collections.sort(reputableMembers, new ReputableFederationMemberComparator());
		return reputableMembers.getLast().getMember();
	}

	class ReputableFederationMember {

		private FederationMember member;
		private double reputation;

		public ReputableFederationMember(FederationMember member, double reputation) {
			this.member = member;
			this.reputation = reputation;
		}

		public FederationMember getMember() {
			return member;
		}

		public double getReputation() {
			return reputation;
		}
	}
	
	class ReputableFederationMemberComparator implements Comparator<ReputableFederationMember> {
		@Override
		public int compare(ReputableFederationMember firstReputableMember,
				ReputableFederationMember secondReputableMember) {

			return new Double(firstReputableMember.getReputation()).compareTo(new Double(
					secondReputableMember.getReputation()));
		}
	}
}

