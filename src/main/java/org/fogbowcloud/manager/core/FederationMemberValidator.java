package org.fogbowcloud.manager.core;

import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface FederationMemberValidator {
	
	public boolean validateDonatorMember(FederationMember member);
	public boolean validateReceivingMember(FederationMember member);
	public void setValidCAs(List<X500Principal> list);
	
}
