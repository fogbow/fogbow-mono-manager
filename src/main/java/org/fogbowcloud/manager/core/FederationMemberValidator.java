package org.fogbowcloud.manager.core;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;

import org.fogbowcloud.manager.core.model.FederationMember;

public interface FederationMemberValidator {
	
	public boolean canDonateTo(FederationMember member) throws CertificateExpiredException, CertificateNotYetValidException;
	
	public boolean canReceiveFrom(FederationMember member);
	
}
