package org.fogbowcloud.manager.core.plugins.x509;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;

public class X509IdentityPlugin implements IdentityPlugin {

	Properties properties;
	private static final Logger LOGGER = Logger.getLogger(X509IdentityPlugin.class);
	public static final String CERTIFICATE_PATH_KEY = "x509-certificate-file-path";
	private static final String BEGIN_CERTIFICATE_SYNTAX = "-----BEGIN CERTIFICATE-----";
	private static final String END_CERTIFICATE_SYNTAX = "-----END CERTIFICATE-----";
	
	public X509IdentityPlugin(Properties properties){
		this.properties = properties;
	}
	
	@Override
	public Token createToken(Map<String, String> userCredentials) {
		Collection<X509Certificate> certificateChain = generateCertificateChain(userCredentials);
		String accessId = CertificateUtils.generateAcessId(certificateChain);
		String user = null;
		Date expirationTime = null;
		for (X509Certificate x509Certificate : certificateChain) {
			expirationTime = x509Certificate.getNotAfter();			
			user = x509Certificate.getIssuerDN().getName();
			break;
		}
		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	private Collection<X509Certificate> generateCertificateChain(Map<String, String> userCredentials) {
		if (!userCredentials.containsKey(CERTIFICATE_PATH_KEY)
				|| !new File(userCredentials.get(CERTIFICATE_PATH_KEY)).exists()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.INVALID_X509_CERTIFICATE_PATH);
		}
		return getCertificateChainFromFile(userCredentials.get(CERTIFICATE_PATH_KEY));
	}

	private Collection<X509Certificate> getCertificateChainFromFile(
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

	@Override
	public Token reIssueToken(Token token) {
		/*
		 * It is not possible to make a token reissue with available
		 * information.
		 */
		return token;
	}

	@Override
	public Token getToken(String accessId) {
		accessId = normalizeAccessId(accessId);
		if (!isValid(accessId)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		Collection<X509Certificate> certificates;
		try {
			certificates = generateCertificates(accessId);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		String user = null;
		Date expirationTime = null;
		for (X509Certificate x509Certificate : certificates) {
			expirationTime = x509Certificate.getNotAfter();
			user = x509Certificate.getIssuerDN().getName();
			break;
		}

		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {	
		//check expired
		accessId = normalizeAccessId(accessId);
		Collection<X509Certificate> certificateChain;
		
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			List<X509Certificate> mylist = new ArrayList<X509Certificate>();
			certificateChain = generateCertificates(accessId);
			for (X509Certificate certificate : certificateChain) {
				try {
					certificate.checkValidity();
					mylist.add(certificate);
				} catch (CertificateExpiredException e) {
					LOGGER.warn("Certificate expired.", e);
					return false;
				} catch (CertificateNotYetValidException e) {
					LOGGER.warn("Certificate not valid yet.", e);
					return false;
				}
			}
						
			// check CAs
			CertPath cp = cf.generateCertPath(mylist);
			PKIXParameters params = new PKIXParameters(getTrustStore());
			params.setRevocationEnabled(false);
			CertPathValidator cpv = CertPathValidator.getInstance(CertPathValidator.getDefaultType());
			cpv.validate(cp, params);
			return true;			
		} catch (Exception e) {
			return false;
		}
	}

	private Set<TrustAnchor> getTrustStore() {
		String trustAnchorDir = properties.getProperty(ConfigurationConstants.X509_CA_DIR_PATH_KEY);
		File trustDir = new File(trustAnchorDir);
		if (!trustDir.exists()) {
			throw new OCCIException(ErrorType.BAD_REQUEST,
					ResponseConstants.INVALID_TRUST_ANCHOR_DIR);
		}

		Set<TrustAnchor> trustedAnchors = new HashSet<TrustAnchor>();

		if (trustDir.isFile()) {
			Collection<X509Certificate> certificateChain = getCertificateChainFromFile(trustAnchorDir);
			for (X509Certificate x509Certificate : certificateChain) {
				trustedAnchors.add(new TrustAnchor(x509Certificate, null));
			}
		} else {
			File[] listOfFiles = trustDir.listFiles();
			for (int i = 0; i < listOfFiles.length; i++) {
				Collection<X509Certificate> certificateChain = getCertificateChainFromFile(listOfFiles[i]
						.getAbsolutePath());

				for (X509Certificate x509Certificate : certificateChain) {
					trustedAnchors.add(new TrustAnchor(x509Certificate, null));
				}
			}
		}
		return trustedAnchors;
	}

	@SuppressWarnings("unchecked")
	private Collection<X509Certificate> generateCertificates(String accessId)
			throws CertificateException, UnsupportedEncodingException {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		return (Collection<X509Certificate>) cf.generateCertificates(new ByteArrayInputStream(
				accessId.getBytes("UTF-8")));
	}

	@Override
	public Token createFederationUserToken() {
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(CERTIFICATE_PATH_KEY,
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_X509_CERTIFICATE_PATH_KEY));				
		return createToken(credentials);
	}

	@Override
	public Credential[] getCredentials() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getAuthenticationURI() {
		// TODO Auto-generated method stub
		return null;
	}

	public static String normalizeAccessId(String accessId) {
		accessId = accessId.replace(Token.BREAK_LINE_REPLACE, "");
		String accessIdNormalized = "";
		String[] beginSyntax = accessId.split(BEGIN_CERTIFICATE_SYNTAX);
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
