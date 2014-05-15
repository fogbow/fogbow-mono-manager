package org.fogbowcloud.manager.core;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import org.fogbowcloud.manager.core.model.FederationMember;

public class DefaultFederationMemberValidator implements FederationMemberValidator {
	
	List<X509Certificate> validCAs = new LinkedList<X509Certificate>();
	
	@Override
	public boolean canDonateTo(FederationMember member) {
		boolean canDonate = false;
		X509Certificate cert = member.getResourcesInfo().getCert();
		
		for (X509Certificate ca: validCAs) {
			try {
				cert.verify(ca.getPublicKey());
				canDonate = true;
			} catch (InvalidKeyException e) {
			} catch (CertificateException e) {
			} catch (NoSuchAlgorithmException e) {
			} catch (NoSuchProviderException e) {
			} catch (SignatureException e) {
			}
		}
		
		try {
			cert.checkValidity();
		} catch (Exception e) {
			canDonate = false;
		} 
		return canDonate ;
	}

	@Override
	public boolean canReceiveFrom(FederationMember member) {
		boolean canDonate = false;
		X509Certificate cert = member.getResourcesInfo().getCert();
		for (X509Certificate ca: validCAs) {
			try {
				cert.verify(ca.getPublicKey());
				canDonate = true;
			} catch (InvalidKeyException e) {
			} catch (CertificateException e) {
			} catch (NoSuchAlgorithmException e) {
			} catch (NoSuchProviderException e) {
			} catch (SignatureException e) {
			}
		}
		
		try {
			cert.checkValidity();
		} catch (CertificateExpiredException e) {
			canDonate = false;
		} catch (CertificateNotYetValidException e) {
			canDonate = false;
		}
		return canDonate ;
	}

	public void setValidCAs(List<X509Certificate> list) {
		this.validCAs = list;
		
	}
	
}
