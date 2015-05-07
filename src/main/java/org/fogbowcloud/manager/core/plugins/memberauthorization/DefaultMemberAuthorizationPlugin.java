package org.fogbowcloud.manager.core.plugins.memberauthorization;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class DefaultMemberAuthorizationPlugin implements FederationMemberAuthorizationPlugin {

	public DefaultMemberAuthorizationPlugin(Properties properties){
	}
	
	@Override
	public boolean canReceiveFrom(FederationMember member) {
		return true;
	}

	@Override
	public boolean canDonateTo(FederationMember member,
			Token requestingUserToken) {
		return true;
	}

}
