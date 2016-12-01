package org.fogbowcloud.manager.core.plugins.identity.x509;

import java.io.File;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
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

import org.apache.log4j.Logger;
import org.bouncycastle.util.io.pem.PemObject;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;

@Deprecated
public class X509IdentityPlugin implements IdentityPlugin {

	Properties properties;
	private static final Logger LOGGER = Logger.getLogger(X509IdentityPlugin.class);
	public static final String CERTIFICATE_PATH_KEY = "x509CertificatePath";

	public X509IdentityPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		Collection<X509Certificate> certificateChain = generateCertificateChain(userCredentials);
		String accessId = CertificateUtils.generateAccessId(certificateChain);
		String userId = null;
		String userNameCN = null;
		Date expirationTime = null;
		for (X509Certificate x509Certificate : certificateChain) {
			expirationTime = x509Certificate.getNotAfter();
			userId = x509Certificate.getIssuerDN().getName();
			userNameCN = VomsIdentityPlugin.getUserNameInCertificate(x509Certificate);
			break;
		}
		
		Token.User user = new Token.User(userId, userNameCN != null ? userNameCN : userId);
		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	private Collection<X509Certificate> generateCertificateChain(Map<String, String> userCredentials) {
		try {
			return CertificateUtils.getCertificateChainFromFile(userCredentials
					.get(CERTIFICATE_PATH_KEY));
		} catch (Exception e) {
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
		List<PemObject> chain = null;
		try {
			chain = CertificateUtils.parseChain(accessId);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		if (!isValid(chain)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		Collection<X509Certificate> certificates;
		try {
			certificates = CertificateUtils.extractCertificates(chain);
		} catch (Exception e) {
			LOGGER.error("", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		X509Certificate x509Certificate = certificates.iterator().next();
		String userId = x509Certificate.getIssuerDN().getName();
		Date expirationTime = x509Certificate.getNotAfter();
		String userNameCN = VomsIdentityPlugin.getUserNameInCertificate(x509Certificate);

		Token.User user = new Token.User(userId, userNameCN != null ? userNameCN : userId);
		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	
	@Override
	public boolean isValid(String accessId) {
		List<PemObject> chain = null;
		try {
			chain = CertificateUtils.parseChain(accessId);
		} catch (Exception e1) {
			LOGGER.warn("Exception while parsing PEM chain from " + accessId);
			return false;
		}
		return isValid(chain);
	}
	
	private boolean isValid(List<PemObject> chain) {
		try {
			CertificateFactory certFactory = CertificateFactory.getInstance(CertificateUtils.X_509);
			List<X509Certificate> certificates = new ArrayList<X509Certificate>();
			Collection<X509Certificate> certificateChain = CertificateUtils.extractCertificates(chain);
			for (X509Certificate certificate : certificateChain) {
				try {
					certificate.checkValidity();
					certificates.add(certificate);
				} catch (CertificateExpiredException e) {
					LOGGER.warn("Certificate expired.", e);
					return false;
				} catch (CertificateNotYetValidException e) {
					LOGGER.warn("Certificate not valid yet.", e);
					return false;
				}
			}

			// check CAs
			CertPath cp = certFactory.generateCertPath(certificates);
			PKIXParameters params = new PKIXParameters(getCACertificates());
			params.setRevocationEnabled(false);
			CertPathValidator cpv = CertPathValidator.getInstance(CertPathValidator
					.getDefaultType());
			cpv.validate(cp, params);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private Set<TrustAnchor> getCACertificates() {		
		String caDirPath = properties.getProperty("x509_ca_dir_path");
		File caDir = new File(caDirPath);
		if (!caDir.exists() || !caDir.isDirectory()) {
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_CA_DIR);
		}

		Set<TrustAnchor> trustedAnchors = new HashSet<TrustAnchor>();

		File[] listOfFiles = caDir.listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			Collection<X509Certificate> certificateChain;
			try {
				certificateChain = CertificateUtils.getCertificateChainFromFile(listOfFiles[i]
						.getAbsolutePath());
				for (X509Certificate x509Certificate : certificateChain) {
					trustedAnchors.add(new TrustAnchor(x509Certificate, null));
				}
			} catch (Exception e) {
				/*
				 * If this exception happens, the certification chain of the
				 * specific file is not added to trusted anchors
				 */
				LOGGER.warn("Exception while getting " + listOfFiles[i].getAbsolutePath()
						+ " certificate chain.");
				LOGGER.warn("", e);
			}
		}
		return trustedAnchors;
	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(CERTIFICATE_PATH_KEY, true, null) };
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

	@Override
	public Token getForwardableToken(Token originalToken) {
		return null;
	}
}
