package org.fogbowcloud.manager.core;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;

public class CertificateHandlerHelper {
	private static final String CERTIFICATE_KEY = "certificate";
	private final static String CONFIG_PATH = "src/test/resources/manager.conf.test";

	public static String convertToSendingFormat() throws CertificateException,
			IOException {
		Certificate cert = getCertificate();
		byte[] base64Certificate = Base64.encodeBase64(cert.getEncoded());
		return new String(base64Certificate);
	}

	public static Certificate getCertificate() throws IOException,
			CertificateException {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(CONFIG_PATH);
		properties.load(input);
		String path = properties.getProperty(CERTIFICATE_KEY);

		final CertificateFactory certFactory = CertificateFactory
				.getInstance("X.509");
		input = new FileInputStream(path);
		final Certificate cert = (Certificate) certFactory
				.generateCertificate(new FileInputStream(path));
		input.close();
		return cert;
	}
	
	public static Certificate convertToCertificateFormat(
			String base64StringCertificate) throws CertificateException {
		byte[] base64BytesCertificate = base64StringCertificate.getBytes();
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		byte[] certificateBytes = Base64.decodeBase64(base64BytesCertificate);
		InputStream in = new ByteArrayInputStream(certificateBytes);
		X509Certificate cert = (X509Certificate)certFactory.generateCertificate(in);
		return cert;
	}
}
