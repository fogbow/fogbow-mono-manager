package org.fogbowcloud.manager.core;

import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public class RoundRobinMemberPicker implements FederationMemberPicker {

	@Override
	public FederationMember pick(List<FederationMember> members) {
		if (members.isEmpty()) {
			return null;
		}
		return members.iterator().next();
	}

}
