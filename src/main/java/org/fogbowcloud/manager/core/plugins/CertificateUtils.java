package org.fogbowcloud.manager.core.plugins;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.Token;

public class CertificateUtils {
	
	public static final String UTF_8 = "UTF-8";
	public static final String X_509 = "X.509";
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
	
	@SuppressWarnings("unchecked")
	public static Collection<X509Certificate> getCertificateChainFromFile(
			String filePath) throws CertificateException, IOException {
		CertificateFactory certFactory = CertificateFactory.getInstance(X_509);
		FileInputStream fis = new FileInputStream(filePath);
		Collection<X509Certificate> certificationChain = (Collection<X509Certificate>) certFactory
				.generateCertificates(fis);
		LOGGER.debug("certification chain: " + certificationChain);
		fis.close();
		return certificationChain;		
	}
	
	@SuppressWarnings("unchecked")
	public static Collection<X509Certificate> getCertificateChain(String certificateChainStr)
			throws CertificateException, UnsupportedEncodingException {
		CertificateFactory cf = CertificateFactory.getInstance(X_509);
		return (Collection<X509Certificate>) cf.generateCertificates(new ByteArrayInputStream(
				certificateChainStr.getBytes(UTF_8)));
	}

	public static String toCertificateFormat(String content) {
		//TODO review if the first line is needed
		content = content.replace(Token.BREAK_LINE_REPLACE, "");
		String accessIdNormalized = "";
		String[] beginSyntax = content.split(BEGIN_CERTIFICATE_SYNTAX);
		for (String beginToken : beginSyntax) {
			if (!beginToken.isEmpty()) {
				accessIdNormalized += BEGIN_CERTIFICATE_SYNTAX;
				String[] endToken = beginToken.split(END_CERTIFICATE_SYNTAX);
				if (!endToken[0].contains(Token.BREAK_LINE_REPLACE)) {
					accessIdNormalized += Token.BREAK_LINE_REPLACE + endToken[0];
				}
				if (endToken.length == 1) {
					accessIdNormalized += Token.BREAK_LINE_REPLACE;
				}
				accessIdNormalized += END_CERTIFICATE_SYNTAX + Token.BREAK_LINE_REPLACE;
			}
		}
		return accessIdNormalized.trim();
	}	
}
