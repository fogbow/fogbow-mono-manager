package org.fogbowcloud.manager.core;

import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;

public class DefaultMemberValidator implements FederationMemberValidator {

	//TODO review the real need of these constructor
	public DefaultMemberValidator(){
		
	}

	public DefaultMemberValidator(Properties properties){
		
	}
	
	@Override
	public boolean canDonateTo(FederationMember member) {
		return true;
	}

	@Override
	public boolean canReceiveFrom(FederationMember member) {
		return true;
	}

}
