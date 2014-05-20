package org.fogbowcloud.manager.core;

import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public class RestrictCAsMemberValidator implements
		FederationMemberValidator {

	List<X509Certificate> validCAs = new LinkedList<X509Certificate>();

	@Override
	public boolean canDonateTo(FederationMember member) {
		if (member == null) {
			return false;
		}
		X509Certificate cert = member.getResourcesInfo().getCert();
		try {
			cert.checkValidity();
		} catch (Exception e) {
			return false;
		}

		for (X509Certificate ca : validCAs) {
			try {
				cert.verify(ca.getPublicKey());
				return true;
			} catch (Exception e) {
			}
		}
		return false;
	}

	@Override
	public boolean canReceiveFrom(FederationMember member) {
		if (member == null) {
			return false;
		}
		
		X509Certificate cert = member.getResourcesInfo().getCert();
		
		try {
			cert.checkValidity();
		} catch (Exception e) {
			return false;
		}

		for (X509Certificate ca : validCAs) {
			try {
				cert.verify(ca.getPublicKey());
				return true;
			} catch (Exception e) {
			}
		}
		return false;
	}

	public void setValidCAs(List<X509Certificate> list) {
		this.validCAs = list;

	}

}
