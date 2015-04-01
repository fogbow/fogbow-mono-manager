package org.fogbowcloud.manager.core;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.core.Token;

public class DefaultMemberValidator implements FederationMemberValidator {

	public DefaultMemberValidator(Properties properties){
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
