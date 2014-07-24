package org.fogbowcloud.manager.core.plugins.voms;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.AttributeCertificate;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Token;
import org.italiangrid.voms.VOMSAttribute;
import org.italiangrid.voms.VOMSGenericAttribute;
import org.italiangrid.voms.VOMSValidators;
import org.italiangrid.voms.ac.VOMSACValidator;
import org.italiangrid.voms.ac.VOMSValidationResult;
import org.italiangrid.voms.ac.impl.DefaultVOMSValidator;
import org.italiangrid.voms.credential.UserCredentials;
import org.italiangrid.voms.request.VOMSACService;
import org.italiangrid.voms.request.impl.DefaultVOMSACRequest;
import org.italiangrid.voms.request.impl.DefaultVOMSACService;
import org.italiangrid.voms.store.impl.DefaultVOMSTrustStore;
import org.italiangrid.voms.util.CertificateValidatorBuilder;

import eu.emi.security.authn.x509.X509CertChainValidatorExt;
import eu.emi.security.authn.x509.X509Credential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;
import eu.emi.security.authn.x509.proxy.ProxyCertificateOptions;
import eu.emi.security.authn.x509.proxy.ProxyGenerator;

public class VomsIdentityPlugin implements IdentityPlugin {

	private static final int DEFAULT_LIFE_TIME = 10;
	private static final String X_509 = "X.509";
	private static final Logger LOGGER = Logger.getLogger(VomsIdentityPlugin.class);

	private Properties properties;

	public VomsIdentityPlugin(Properties properties) {
		this.properties = properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		char[] keyPassword = userCredentials.get(Token.Constants.VOMS_PASSWORD.getValue())
				.toCharArray();
		String vomsServer = userCredentials.get(Token.Constants.VOMS_SERVER.getValue());

		X509Credential cred = UserCredentials.loadCredentials(keyPassword);

		String pathVomeses = properties.getProperty(ConfigurationConstants.VOMS_PATH_VOMSES);
		X509CertChainValidatorExt validatorExt = CertificateValidatorBuilder
				.buildCertificateValidator(pathVomeses, null, null, 0L,
						CertificateValidatorBuilder.DEFAULT_NS_CHECKS,
						CertificateValidatorBuilder.DEFAULT_CRL_CHECKS,
						CertificateValidatorBuilder.DEFAULT_OCSP_CHECKS);

		VOMSACService service = new DefaultVOMSACService.Builder(validatorExt).build();

		DefaultVOMSACRequest request = new DefaultVOMSACRequest.Builder(vomsServer).lifetime(
				DEFAULT_LIFE_TIME).build();
		AttributeCertificate attributeCertificate = service.getVOMSAttributeCertificate(cred,
				request);

		ProxyCertificateOptions proxyOptions = new ProxyCertificateOptions(
				cred.getCertificateChain());
		proxyOptions.setAttributeCertificates(new AttributeCertificate[] { attributeCertificate });

		ProxyCertificate proxyCert = null;
		try {
			proxyCert = ProxyGenerator.generate(proxyOptions, cred.getKey());
		} catch (Exception e) {
			LOGGER.error("Problems in the generation of the proxy certificate");
		}

		String accessId = proxyCert.getCredential().getCertificate().toString();
		String user = proxyCert.getCredential().getCertificate().getIssuerDN().getName();
		Date expirationTime = proxyCert.getCredential().getCertificate().getNotAfter();

		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	@Override
	public Token reIssueToken(Token token) {
		Map<String, String> userCredentials = new HashMap<String, String>();
		userCredentials.put(Token.Constants.VOMS_PASSWORD.getValue(),
				token.get(Token.Constants.VOMS_PASSWORD.getValue()));
		userCredentials.put(Token.Constants.VOMS_SERVER.getValue(),
				token.get(Token.Constants.VOMS_SERVER.getValue()));

		return createToken(userCredentials);
	}

	@Override
	public Token getToken(String accessId) {
		X509Certificate certificate = null;
		CertificateFactory cf = null;
		try {
			cf = CertificateFactory.getInstance(X_509);
			certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(
					accessId.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			LOGGER.error("Problems in the generation of the certificate");
			e.printStackTrace();
		}
		String user = certificate.getIssuerDN().getName();
		Date expirationTime = certificate.getNotAfter();
		
		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	@Override
	public boolean isValid(String accessId) {
		X509Certificate certificate = null;

		CertificateFactory cf = null;
		try {
			cf = CertificateFactory.getInstance(X_509);
			certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(
					accessId.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			LOGGER.error("Problems in the generation of the certificate");
			e.printStackTrace();
		}

		try {
			certificate.checkValidity();
		} catch (CertificateExpiredException e) {
			LOGGER.error("Certificate Expired");
			return false;
		} catch (CertificateNotYetValidException e) {
			LOGGER.error("Certificate not yet valid");
			return false;
		}

		X509Certificate[] theChain = { certificate };
		VOMSACValidator validator = getVOMSValidator();
		List<VOMSValidationResult> results = validator.validateWithResult(theChain);

		boolean validCertificate = true;
		for (VOMSValidationResult r : results) {
			if (!r.isValid()) {
				LOGGER.error("Invalid voms result");
				validCertificate = false;
			}
		}

		if (results.size() != 0 && validCertificate) {
			return true;
		}
		return false;
	}

	@Override
	public Token createFederationUserToken() {
		Map<String, String> tokenCredentials = new HashMap<String, String>();
		tokenCredentials.put(Token.Constants.VOMS_PASSWORD.getValue(),
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_PASS_VOMS));
		tokenCredentials.put(Token.Constants.VOMS_SERVER.getValue(),
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS));
		Token tokenFederationCredentials = new Token("accessId", "user", new Date(),
				tokenCredentials);

		return reIssueToken(tokenFederationCredentials);
	}

	private VOMSACValidator getVOMSValidator() {
		String trust = properties.getProperty(ConfigurationConstants.VOMS_PATH_TRUST);
		String vomsdir = properties.getProperty(ConfigurationConstants.VOMS_PATH_VOMSDIR);
		X509CertChainValidatorExt validatorExt = CertificateValidatorBuilder
				.buildCertificateValidator(trust);
		DefaultVOMSTrustStore VOMSTrustStore = new DefaultVOMSTrustStore(Arrays.asList(vomsdir));

		return VOMSValidators.newValidator(VOMSTrustStore, validatorExt);
	}
}
