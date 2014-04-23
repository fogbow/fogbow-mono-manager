package org.fogbowcloud.manager.core;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public class RoundRobinMemberPicker implements FederationMemberPicker {

	private int current = -1;
	
	@Override
	public FederationMember pick(List<FederationMember> members) {
		if (members.isEmpty()) {
			return null;
		}
		current = (current + 1) % members.size();
		return members.get(current);
	}

}
