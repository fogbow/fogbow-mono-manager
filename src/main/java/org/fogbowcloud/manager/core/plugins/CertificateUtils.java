package org.fogbowcloud.manager.core.plugins;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import eu.emi.security.authn.x509.X509Credential;

public class CertificateUtils {
	
	private static final String PRIVATE_KEY_PEM_TYPE_SUFIX = " PRIVATE KEY";
	private static final String CERTIFICATE_PEM_TYPE = "CERTIFICATE";
	public static final String X_509 = "X.509";
	private static final Logger LOGGER = Logger.getLogger(CertificateUtils.class);

	static {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}
	
	public static String generateAccessId(Collection<X509Certificate> certificateChain) {
		return generateAccessId(certificateChain, null);
	}
	
	public static String generateAccessId(Collection<X509Certificate> certificateChain, 
			X509Credential privKey) {
		StringWriter sw = new StringWriter();
	    PEMWriter pw = new PEMWriter(sw);
		try {
			for (X509Certificate x509Certificate : certificateChain) {
				pw.writeObject(x509Certificate);
				if (privKey != null && privKey.getCertificate().equals(x509Certificate)) {
					pw.writeObject(privKey.getKey());
				}
			}
			pw.close();
			return normalizeChain(sw.toString());
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
		fis.close();
		return certificationChain;		
	}
	
	public static Collection<X509Certificate> extractCertificates(List<PemObject> pemObjects)
			throws CertificateException, UnsupportedEncodingException {
		List<X509Certificate> certificates = new LinkedList<X509Certificate>();
		CertificateFactory cf = CertificateFactory.getInstance(X_509);
		for (PemObject pemObject : pemObjects) {
			if (pemObject.getType().equals(CERTIFICATE_PEM_TYPE)) {
				X509Certificate certificate = (X509Certificate) cf.generateCertificate(
						new ByteArrayInputStream(pemObject.getContent()));
				certificates.add(certificate);
			}
		}
		
		return certificates;
	}
	
	public static KeyPair extractPrivateKey(List<PemObject> chain) throws IOException {
		for (PemObject pemObject : chain) {
			if (pemObject.getType().endsWith(PRIVATE_KEY_PEM_TYPE_SUFIX)) {
				
				StringWriter strWriter = new StringWriter();
				PEMWriter writer = null;
				PEMReader reader = null;
				
				try {
					writer = new PEMWriter(strWriter);
					writer.writeObject(pemObject);
					writer.flush();
					
					String privKeyStr = strWriter.toString();
					reader = new PEMReader(new StringReader(privKeyStr));
					
					KeyPair keyPair = (KeyPair) reader.readObject();
					if (keyPair == null) {
						throw new IllegalArgumentException("Couldn't extract private key from chain.");
					}
					return keyPair;
					
				} finally {
					if (reader != null) {
						reader.close();
					}
					if (writer != null) {
						writer.close();
					}
				}
			}
		}
		return null;
	}

	/**
	 * Removes line breaks inside certificates and parses 
	 * the whole chain including private keys
	 * 
	 * @param certificateChainStr
	 * @return
	 * @throws Exception 
	 */
	public static List<PemObject> parseChain(String certificateChainStr) throws Exception {
		String normalizedCertificateChain = normalizeChain(certificateChainStr);
		List<PemObject> pemObjects = new ArrayList<PemObject>();
		PEMReader reader = new PEMReader(new StringReader(normalizedCertificateChain));
		try {
			while (true) {
				PemObject pemObject = reader.readPemObject();
				if (pemObject == null) {
					break;
				}
				pemObjects.add(pemObject);
			}
			
		} catch (Exception e) {
			LOGGER.error("Couldn't parse chain", e);
			throw e;
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				LOGGER.error("Couldn't close certificate reader", e);
			}
		}
		
		return pemObjects;
	}

	private static String normalizeChain(String certificateChainStr) {
		String certificateChainNoLineBreaks = certificateChainStr.replace("\n", "");
		StringBuilder normalizedCertificateChain = new StringBuilder();
		Pattern pattern = Pattern.compile("(-----BEGIN [A-Z\\s]*-----)([^\\-]*)(-----END [A-Z\\s]*-----)");
		Matcher matcher = pattern.matcher(certificateChainNoLineBreaks);
		
		while (matcher.find()) {
			normalizedCertificateChain.append(matcher.group(1)).append("\n");
			normalizedCertificateChain.append(matcher.group(2)).append("\n");
			normalizedCertificateChain.append(matcher.group(3)).append("\n");
		}
		String normalizedCertificateChainStr = normalizedCertificateChain.toString().trim();
		if (normalizedCertificateChainStr.isEmpty()) {
			throw new IllegalArgumentException(
					"No certificate match was found for " + certificateChainStr);
		}
		return normalizedCertificateChainStr;
	}	
}
