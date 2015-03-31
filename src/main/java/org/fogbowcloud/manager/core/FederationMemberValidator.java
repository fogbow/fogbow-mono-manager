package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.core.Token;

public interface FederationMemberValidator {
	
	public boolean canDonateTo(FederationMember member, Token requestingUserToken);
	
	public boolean canReceiveFrom(FederationMember member);
	
}
