package org.fogbowcloud.manager.core;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public class RoundRobinMemberPicker implements FederationMemberPicker {

	private int current = -1;

	@Override
	public FederationMember pick(ManagerController facade) {
		List<FederationMember> members = facade.getMembers();
		if (members.isEmpty()) {
			return null;
		}
		current = (current + 1) % members.size();

		for (int i = 0; i < members.size(); i++) {
			FederationMember currentMember = members.get(current);

			String myJid = facade.getProperties().getProperty(ConfigurationConstants.XMPP_JID_KEY);
			if (currentMember.getResourcesInfo().getId().equals(myJid)
					|| !facade.getValidator().canReceiveFrom(currentMember)) {
				current = (current + 1) % members.size();
				continue;
			}
			return members.get(current);
		}
		return null;
	}

}
