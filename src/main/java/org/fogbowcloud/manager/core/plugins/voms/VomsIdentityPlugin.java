package org.fogbowcloud.manager.core.plugins.voms;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.AttributeCertificate;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Token;
import org.italiangrid.voms.VOMSValidators;
import org.italiangrid.voms.ac.VOMSACValidator;
import org.italiangrid.voms.ac.VOMSValidationResult;
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

	private static final String BEGIN_CERTIFICATE_SYNTAX = "-----BEGIN CERTIFICATE-----";
	private static final String END_CERTIFICATE_SYNTAX = "-----END CERTIFICATE-----";
	private static final int DEFAULT_LIFE_TIME = 10;
	private static final String X_509 = "X.509";
	private static final Logger LOGGER = Logger.getLogger(VomsIdentityPlugin.class);
	
	private Properties properties;
	private GeneratorProxyCertificate generatorProxyCertificate;

	public VomsIdentityPlugin(Properties properties) {
		this.generatorProxyCertificate = new GeneratorProxyCertificate();
		new CryptographyRestrictions().removeCryptographyRestrictions();
		this.properties = properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public void setGenerateProxyCertificate(GeneratorProxyCertificate generatorProxyCertificate) {
		this.generatorProxyCertificate = generatorProxyCertificate;
	}

	@Override
	public Token createToken(Map<String, String> userCredentials) {
		ProxyCertificate proxyCert = null;
		try {
			proxyCert = generatorProxyCertificate.generate(userCredentials);
		} catch (Exception e) {
			LOGGER.error("Problems in the generation of the proxy certificate : " + e.getMessage());
			e.printStackTrace();
		}

		String accessId = generateAcessId(proxyCert.getCertificateChain());
		String user = proxyCert.getCredential().getCertificate().getIssuerDN().getName();
		Date expirationTime = proxyCert.getCredential().getCertificate().getNotAfter();

		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
	}

	public String generateAcessId(X509Certificate[] certificateChain) {
		StringBuilder base64Chain = new StringBuilder();

		X509Certificate[] chain = certificateChain;
		try {
			for (X509Certificate x509Certificate : chain) {
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

	@SuppressWarnings("unchecked")
	@Override
	public boolean isValid(String accessId) {
		Collection<X509Certificate> certificates = null;

		CertificateFactory cf = null;
		try {
			cf = CertificateFactory.getInstance(X_509);
			certificates = (Collection<X509Certificate>) cf
					.generateCertificates(new ByteArrayInputStream(accessId
							.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			LOGGER.error("Problems in the generation of the certificate");
			e.printStackTrace();
		}

		for (X509Certificate certificate : certificates) {
			try {
				certificate.checkValidity();
			} catch (CertificateExpiredException e) {
				LOGGER.error("Certificate Expired");
				return false;
			} catch (CertificateNotYetValidException e) {
				LOGGER.error("Certificate not yet valid");
				return false;
			}
		}

		X509Certificate[] theChain = certificates.toArray(new X509Certificate[] {});
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
		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(Token.Constants.VOMS_PASSWORD.getValue(),
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_PASS_VOMS));
		credentials.put(Token.Constants.VOMS_SERVER.getValue(),
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS));

		return createToken(credentials);
	}

	private VOMSACValidator getVOMSValidator() {
		String trust = properties.getProperty(ConfigurationConstants.VOMS_PATH_TRUST);
		String vomsdir = properties.getProperty(ConfigurationConstants.VOMS_PATH_VOMSDIR);
		X509CertChainValidatorExt validatorExt = CertificateValidatorBuilder
				.buildCertificateValidator(trust);
		DefaultVOMSTrustStore VOMSTrustStore = new DefaultVOMSTrustStore(Arrays.asList(vomsdir));

		return VOMSValidators.newValidator(VOMSTrustStore, validatorExt);
	}

	public class GeneratorProxyCertificate {

		public ProxyCertificate generate(Map<String, String> userCredentials) throws Exception {
			char[] keyPassword = userCredentials.get(Token.Constants.VOMS_PASSWORD.getValue())
					.toCharArray();
			String vomsServer = userCredentials.get(Token.Constants.VOMS_SERVER.getValue());

			X509Credential cred = UserCredentials.loadCredentials(keyPassword);

			String pathVomses = properties.getProperty(ConfigurationConstants.VOMS_PATH_VOMSES);
			X509CertChainValidatorExt validatorExt = CertificateValidatorBuilder
					.buildCertificateValidator(pathVomses, null, null, 0L,
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
			proxyOptions
					.setAttributeCertificates(new AttributeCertificate[] { attributeCertificate });

			return ProxyGenerator.generate(proxyOptions, cred.getKey());
		}
	}
	
	private class CryptographyRestrictions{
		
		private void removeCryptographyRestrictions() {
			if (!isRestrictedCryptography()) {
				return;
			}
		    try {
		        final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
		        final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
		        final Class<?> cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission");

		        final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
		        isRestrictedField.setAccessible(true);
		        isRestrictedField.set(null, false);

		        final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
		        defaultPolicyField.setAccessible(true);
		        final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField.get(null);

		        final Field perms = cryptoPermissions.getDeclaredField("perms");
		        perms.setAccessible(true);
		        ((Map<?, ?>) perms.get(defaultPolicy)).clear();

		        final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
		        instance.setAccessible(true);
		        defaultPolicy.add((Permission) instance.get(null));

		    } catch (final Exception e) {
		    }
		}

		private boolean isRestrictedCryptography() {
		    return "Java(TM) SE Runtime Environment".equals(System.getProperty("java.runtime.name"));
		}		
		
	}
}
