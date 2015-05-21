package org.fogbowcloud.manager.core.plugins.memberpicker;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.plugins.prioritization.nof.FederationMemberDebt;
import org.fogbowcloud.manager.core.plugins.prioritization.nof.FederationMemberDebtComparator;

public class NoFMemberPickerPlugin implements FederationMemberPickerPlugin {
		
	private AccountingPlugin accoutingPlugin;
	private String localMemberId;
	private boolean trustworthy = false;

	private static final Logger LOGGER = Logger.getLogger(NoFMemberPickerPlugin.class);
	
	public NoFMemberPickerPlugin(Properties properties, AccountingPlugin accoutingPlugin) {
		this.accoutingPlugin = accoutingPlugin;
		this.localMemberId = properties.getProperty(ConfigurationConstants.XMPP_JID_KEY);
		try {
			this.trustworthy = Boolean.valueOf(properties.getProperty("nof_trustworthy"));			
		} catch (Exception e) {
			LOGGER.error("Error while getting boolean value for nof_trustworhty. The default value is false.", e);
		}
	}
	
	@Override
	public FederationMember pick(List<FederationMember> members) {
		Map<String, ResourceUsage> membersUsage = accoutingPlugin.getMembersUsage();
		LinkedList<FederationMemberDebt> reputableMembers = new LinkedList<FederationMemberDebt>();

		for (FederationMember currentMember : members) {			
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
			reputableMembers.add(new FederationMemberDebt(currentMember, debt));
		}
		
		if (reputableMembers.isEmpty()) {
			return null;
		}
		Collections.sort(reputableMembers, new FederationMemberDebtComparator());
		return reputableMembers.getFirst().getMember();
	}
	
	public boolean getTrustworthy() {
		return trustworthy;
	}
}

