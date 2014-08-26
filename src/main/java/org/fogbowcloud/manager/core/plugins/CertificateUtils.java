package org.fogbowcloud.manager.core.plugins;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;

public class CertificateUtils {
	
	public static final String BEGIN_CERTIFICATE_SYNTAX = "-----BEGIN CERTIFICATE-----";
	public static final String END_CERTIFICATE_SYNTAX = "-----END CERTIFICATE-----";
	private static final Logger LOGGER = Logger.getLogger(CertificateUtils.class);

	public static String generateAcessId(Collection<X509Certificate> certificateChain) {
		StringBuilder base64Chain = new StringBuilder();
		try {
			for (X509Certificate x509Certificate : certificateChain) {
				base64Chain.append(BEGIN_CERTIFICATE_SYNTAX + "\n");
				String certString = Base64.encodeBase64String(x509Certificate.getEncoded());
				base64Chain.append(certString);
				base64Chain.append("\n" + END_CERTIFICATE_SYNTAX + "\n");
			}
			return base64Chain.toString().trim();
		} catch (Exception e) {
			LOGGER.error("Problems in converting certificate to string");
		}
		return null;
	}
	
	public static Collection<X509Certificate> getCertificateChainFromFile(
			String filePath) {
		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
			FileInputStream fis = new FileInputStream(filePath);
			@SuppressWarnings("unchecked")
			Collection<X509Certificate> certificationChain = (Collection<X509Certificate>) certFactory
					.generateCertificates(fis);
			LOGGER.debug("certification chain: " + certificationChain);
			fis.close();
			return certificationChain;
		} catch (CertificateException e) {
			LOGGER.error("", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.INVALID_X509_CERTIFICATE_PATH);
		} catch (IOException e) {
			LOGGER.error("", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.INVALID_X509_CERTIFICATE_PATH);
		}
	}
}
