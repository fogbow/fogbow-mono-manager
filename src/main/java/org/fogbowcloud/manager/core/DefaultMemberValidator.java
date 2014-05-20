package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.model.FederationMember;

public class DefaultMemberValidator implements FederationMemberValidator {

	@Override
	public boolean canDonateTo(FederationMember member) {
		return true;
	}

	@Override
	public boolean canReceiveFrom(FederationMember member) {
		return true;
	}

}
