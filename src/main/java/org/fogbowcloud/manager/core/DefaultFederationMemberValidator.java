package org.fogbowcloud.manager.core;

import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.fogbowcloud.manager.core.model.FederationMember;

public class DefaultFederationMemberValidator implements FederationMemberValidator {

	@Override
	public boolean validateDonatorMember(FederationMember member) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean validateReceivingMember(FederationMember member) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setValidCAs(List<X500Principal> list) {
		// TODO Auto-generated method stub
		
	}
	
}
