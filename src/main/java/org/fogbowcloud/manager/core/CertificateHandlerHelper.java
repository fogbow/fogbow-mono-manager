package org.fogbowcloud.manager.core;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;

public class CertificateHandlerHelper {
	private static final String CERTIFICATE_KEY = "certificate";
	private final static Logger LOGGER = Logger
			.getLogger(CertificateHandlerHelper.class.getName());

	public static String getBase64Certificate(Properties properties)
			throws CertificateException, IOException {
		Certificate cert = getCertificate(properties);
		byte[] base64Certificate;
		try {
			base64Certificate = Base64.encodeBase64(cert.getEncoded());
		} catch (NullPointerException n) {
			return null;
		}
		return new String(base64Certificate);
	}

	public static X509Certificate getCertificate(Properties properties)
			throws CertificateException {
		String path = properties.getProperty(CERTIFICATE_KEY);
		if (path == null || path.isEmpty()) {
			LOGGER.warning("Empty Path.");
			return null;
		}
		CertificateFactory certFactory;
		certFactory = CertificateFactory.getInstance("X.509");
		FileInputStream input = null;
		try {
			input = new FileInputStream(path);
		} catch (FileNotFoundException e1) {
			LOGGER.warning("File does not Exist." + e1);
			return null;
		}
		X509Certificate cert = null;
		try {
			try {
				cert = (X509Certificate) certFactory
						.generateCertificate(new FileInputStream(path));
			} catch (FileNotFoundException e) {
				LOGGER.warning("Wring Path, File does not Exist." + e);
				return null;
			}
		} catch (CertificateException e) {
			LOGGER.warning("Certificate does not Exist" + e);
			return null;
		}
		finally {
			try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return cert;
	}
	
	public static X509Certificate getCertificate(String path)
			throws CertificateException {
		
		CertificateFactory certFactory;
		certFactory = CertificateFactory.getInstance("X.509");
		X509Certificate cert = null;
		try {
			try {
				cert = (X509Certificate) certFactory
						.generateCertificate(new FileInputStream(path));
			} catch (FileNotFoundException e) {
				LOGGER.warning("Wring Path, File does not Exist." + e);
				return null;
			}
		} catch (CertificateException e) {
			LOGGER.warning("Certificate does not Exist" + e);
			return null;
		}
		return cert;
	}
	
	public static X509Certificate parseCertificate(String base64StringCertificate)
			throws CertificateException {
		byte[] base64BytesCertificate;
		try {
			base64BytesCertificate = base64StringCertificate.getBytes();
		} catch (NullPointerException n) {
			return null;
		}
		CertificateFactory certFactory = CertificateFactory
				.getInstance("X.509");
		byte[] certificateBytes = Base64.decodeBase64(base64BytesCertificate);
		InputStream in = new ByteArrayInputStream(certificateBytes);
		X509Certificate cert = null;
		try {
			cert = (X509Certificate) certFactory.generateCertificate(in);
		} catch (CertificateException e) {
			LOGGER.warning("Certificate can not be generated.");
		}
		return cert;
	}
}
