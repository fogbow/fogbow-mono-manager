package org.fogbowcloud.manager.core;

import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.FederationMember;

public class RestrictCAsMemberValidator implements FederationMemberValidator {

	private static final Logger LOGGER = Logger
			.getLogger(RestrictCAsMemberValidator.class);
	private List<X509Certificate> validCAs;

	public RestrictCAsMemberValidator(Properties properties) {
		String caDir = properties.getProperty("member_validator_ca_dir");
		File caDirFile = new File(caDir);
		List<X509Certificate> validCAsList = new LinkedList<X509Certificate>();
		for (File caFile : caDirFile.listFiles()) {
			try {
				X509Certificate certificate = CertificateHandlerHelper
						.getCertificate(caFile.getAbsolutePath());
				validCAsList.add(certificate);
			} catch (CertificateException e) {
				LOGGER.warn("Failed to load CA certificate file.", e);
			}
		}
		setValidCAs(validCAsList);
	}
	
	protected RestrictCAsMemberValidator() {
		
	}
	
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
