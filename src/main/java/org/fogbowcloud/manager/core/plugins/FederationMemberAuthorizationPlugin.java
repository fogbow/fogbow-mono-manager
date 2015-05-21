package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;

public interface FederationMemberAuthorizationPlugin {
	
	public boolean canDonateTo(FederationMember member, Token requestingUserToken);
	
	public boolean canReceiveFrom(FederationMember member);
	
}
