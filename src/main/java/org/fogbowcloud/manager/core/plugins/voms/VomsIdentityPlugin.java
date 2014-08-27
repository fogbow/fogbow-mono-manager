package org.fogbowcloud.manager.core.plugins.voms;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.AttributeCertificate;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.Credential;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
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
import org.italiangrid.voms.util.FilePermissionHelper;

import eu.emi.security.authn.x509.X509CertChainValidatorExt;
import eu.emi.security.authn.x509.X509Credential;
import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;
import eu.emi.security.authn.x509.proxy.ProxyCertificateOptions;
import eu.emi.security.authn.x509.proxy.ProxyGenerator;

public class VomsIdentityPlugin implements IdentityPlugin {

	public static final String CREDENTIALS_PATH_DEFAULT = "$HOME/.globus";
	private static final int DEFAULT_LIFE_TIME = 10;
	public static final String PASSWORD = "password";
	public static final String SERVER_NAME = "serverName";
	public static final String PATH_USERCRED = "pathUserCred";
	public static final String PATH_USERKEY = "pathUserKey";

	private static final Logger LOGGER = Logger.getLogger(VomsIdentityPlugin.class);

	private Properties properties;
	private GeneratorProxyCertificate generatorProxyCertificate;

	static {
		CryptographyRestrictions.removeCryptographyRestrictions();
	}

	public VomsIdentityPlugin(Properties properties) {
		this.generatorProxyCertificate = new GeneratorProxyCertificate();
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
		} catch (IOException e) {
			LOGGER.error("", e);
			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} catch (Exception e) {
			LOGGER.error("", e);
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);			
		}
		 

		String accessId = CertificateUtils.generateAcessId(Arrays.asList(proxyCert
				.getCertificateChain()));
		String user = null;
		Date expirationTime = null;
		for (X509Certificate x509Certificate : proxyCert.getCertificateChain()) {
			expirationTime = x509Certificate.getNotAfter();
			user = x509Certificate.getIssuerDN().getName();
			break;
		}

		return new Token(accessId, user, expirationTime, new HashMap<String, String>());
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
		accessId = CertificateUtils.toCertificateFormat(accessId);
		if (!isValid(accessId)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}

		Collection<X509Certificate> certificates;
		try {
			certificates = CertificateUtils.getCertificateChain(accessId);
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
		accessId = CertificateUtils.toCertificateFormat(accessId);
		Collection<X509Certificate> certificates;
		try {
			certificates = CertificateUtils.getCertificateChain(accessId);
		} catch (Exception e) {
			LOGGER.warn("Exception while getting certificate chain from " + accessId);
			return false;
		}

		for (X509Certificate certificate : certificates) {
			try {
				certificate.checkValidity();
			} catch (CertificateExpiredException e) {
				LOGGER.warn("Certificate expired.", e);
				return false;
			} catch (CertificateNotYetValidException e) {
				LOGGER.warn("Certificate not valid yet.", e);
				return false;
			}
		}

		X509Certificate[] theChain = certificates.toArray(new X509Certificate[] {});
		VOMSACValidator validator = getVOMSValidator();
		List<VOMSValidationResult> results = validator.validateWithResult(theChain);

		boolean validCertificate = true;
		for (VOMSValidationResult r : results) {
			if (!r.isValid()) {
				LOGGER.warn("Invalid VOMS result. Validation result: " + r.toString());
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
		credentials.put(PASSWORD,
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_PASS_VOMS));
		credentials.put(SERVER_NAME,
				properties.getProperty(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS));

		return createToken(credentials);
	}

	private VOMSACValidator getVOMSValidator() {
		String trust = properties.getProperty(ConfigurationConstants.VOMS_PATH_TRUST_ANCHORS);
		if (trust == null || trust.isEmpty()) {
			trust = DefaultVOMSValidator.DEFAULT_TRUST_ANCHORS_DIR.toString();
		}
		String vomsdir = properties.getProperty(ConfigurationConstants.VOMS_PATH_VOMSDIR);
		if (vomsdir == null || vomsdir.isEmpty()) {
			vomsdir = DefaultVOMSTrustStore.DEFAULT_VOMS_DIR.toString();
		}

		X509CertChainValidatorExt validatorExt = CertificateValidatorBuilder
				.buildCertificateValidator(trust);
		DefaultVOMSTrustStore VOMSTrustStore = new DefaultVOMSTrustStore(Arrays.asList(vomsdir));

		return VOMSValidators.newValidator(VOMSTrustStore, validatorExt);
	}

	public class GeneratorProxyCertificate {

		public ProxyCertificate generate(Map<String, String> userCredentials) throws KeyStoreException, CertificateException, InvalidKeyException, SignatureException, NoSuchAlgorithmException, IOException {
			char[] keyPassword = userCredentials.get(PASSWORD).toCharArray();
			String vomsNameServer = userCredentials.get(SERVER_NAME);

			X509Credential cred;
			if (userCredentials.containsKey(PATH_USERCRED)
					&& userCredentials.containsKey(PATH_USERKEY)) {

				String privateKeyPath = userCredentials.get(PATH_USERKEY);
				String certificatePath = userCredentials.get(PATH_USERKEY);

				FilePermissionHelper.checkPrivateKeyPermissions(privateKeyPath);
				cred = new PEMCredential(new FileInputStream(privateKeyPath), new FileInputStream(
						certificatePath), keyPassword);
			} else {
				cred = UserCredentials.loadCredentials(keyPassword);
			}

			X509CertChainValidatorExt validatorExt = null;
			String pathVomses = properties.getProperty(ConfigurationConstants.VOMS_PATH_VOMSES);
			if (pathVomses == null || pathVomses.isEmpty()) {
				validatorExt = CertificateValidatorBuilder.buildCertificateValidator();
			} else {
				validatorExt = CertificateValidatorBuilder.buildCertificateValidator(pathVomses,
						null, null, 0L, CertificateValidatorBuilder.DEFAULT_NS_CHECKS,
						CertificateValidatorBuilder.DEFAULT_CRL_CHECKS,
						CertificateValidatorBuilder.DEFAULT_OCSP_CHECKS);
			}

			VOMSACService service = new DefaultVOMSACService.Builder(validatorExt).build();

			DefaultVOMSACRequest request = new DefaultVOMSACRequest.Builder(vomsNameServer)
					.lifetime(DEFAULT_LIFE_TIME).build();
			AttributeCertificate attributeCertificate = service.getVOMSAttributeCertificate(cred,
					request);

			ProxyCertificateOptions proxyOptions = new ProxyCertificateOptions(
					cred.getCertificateChain());
			proxyOptions
					.setAttributeCertificates(new AttributeCertificate[] { attributeCertificate });

			return ProxyGenerator.generate(proxyOptions, cred.getKey());
		}
	}

	private static class CryptographyRestrictions {

		private static final String ORACLE_RUNTIME = "Java(TM) SE Runtime Environment";

		private static void removeCryptographyRestrictions() {
			if (!isRestrictedCryptography()) {
				return;
			}
			try {
				final Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
				final Class<?> cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions");
				final Class<?> cryptoAllPermission = Class
						.forName("javax.crypto.CryptoAllPermission");

				final Field isRestrictedField = jceSecurity.getDeclaredField("isRestricted");
				isRestrictedField.setAccessible(true);
				isRestrictedField.set(null, false);

				final Field defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy");
				defaultPolicyField.setAccessible(true);
				final PermissionCollection defaultPolicy = (PermissionCollection) defaultPolicyField
						.get(null);

				final Field perms = cryptoPermissions.getDeclaredField("perms");
				perms.setAccessible(true);
				((Map<?, ?>) perms.get(defaultPolicy)).clear();

				final Field instance = cryptoAllPermission.getDeclaredField("INSTANCE");
				instance.setAccessible(true);
				defaultPolicy.add((Permission) instance.get(null));

			} catch (final Exception e) {
				LOGGER.warn("Failure in removing cryptography restrictions.", e);
			}
		}

		private static boolean isRestrictedCryptography() {
			return ORACLE_RUNTIME.equals(System.getProperty("java.runtime.name"));
		}

	}

	@Override
	public Credential[] getCredentials() {
		return new Credential[] { new Credential(PASSWORD, true, null),
				new Credential(SERVER_NAME, true, null),
				new Credential(PATH_USERCRED, false, CREDENTIALS_PATH_DEFAULT),
				new Credential(PATH_USERKEY, false, CREDENTIALS_PATH_DEFAULT) };
	}

	@Override
	public String getAuthenticationURI() {
		return null;
	}

}
