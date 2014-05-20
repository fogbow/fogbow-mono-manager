package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface FederationMemberValidator {
	
	public boolean canDonateTo(FederationMember member);
	
	public boolean canReceiveFrom(FederationMember member);
	
}
