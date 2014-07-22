package org.fogbowcloud.manager.core.plugins.voms;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Token;
import org.italiangrid.voms.VOMSAttribute;
import org.italiangrid.voms.VOMSGenericAttribute;
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
		char[] keyPassword = userCredentials.get("keyPassword").toCharArray();
		String vomsServer = userCredentials.get("atlas");
		int lifeTime = Integer.parseInt(properties.getProperty("lifeTime"));

		X509Credential cred = UserCredentials.loadCredentials(keyPassword);

		X509CertChainValidatorExt validator = CertificateValidatorBuilder
				.buildCertificateValidator();
		VOMSACService service = new DefaultVOMSACService.Builder(validator).build();

		DefaultVOMSACRequest request = new DefaultVOMSACRequest.Builder(vomsServer).lifetime(
				lifeTime).build();
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
		
		VOMSACValidator vomsValidator = getVOMSValidator();
		List<VOMSAttribute> vomsAttrs = vomsValidator.parse(proxyCert.getCertificateChain());

		//FIXME properly create the email 
		String accessId = proxyCert.toString();
		Map<String, String> attributes = new HashMap<String, String>();
		String user = "";
		Date expirationTime = new Date();
		Token token = null;
		if (vomsAttrs.size() > 0) {
		    VOMSAttribute va = vomsAttrs.get(0);
		    	
		    List<VOMSGenericAttribute>  gas = va.getGenericAttributes();
		    for (VOMSGenericAttribute g: gas){ }
		    
		    //create token
		    token = new Token(accessId, user, expirationTime, attributes);
		}
		
		return token;
	}

	//FIXME incomplete
	@Override
	public Token reIssueToken(Token token) {
		Map<String, String> userCredentials = new HashMap<String, String>();
		
		return createToken(userCredentials);
	}

	//FIXME incomplete
	@Override
	public Token getToken(String accessId) {				
		
		X509Certificate certificate = null;
		CertificateFactory cf = null;
		try {
			cf = CertificateFactory.getInstance("X.509");
			certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(accessId
					.getBytes(StandardCharsets.UTF_8)));			
		} catch (Exception e) {
			LOGGER.error("Problems in the generation of the certificate");
			e.printStackTrace();
		}			
				
		X509Certificate[] theChain = { certificate };
		
		VOMSACValidator vomsValidator = getVOMSValidator();
		List<VOMSAttribute> vomsAttrs = vomsValidator.parse(theChain);

		if (vomsAttrs.size() > 0) {
		    VOMSAttribute va = vomsAttrs.get(0);		    		   
		}
		
		return null;
	}
	
	@Override
	public boolean isValid(String accessId) {
		X509Certificate certificate = null;
		
		CertificateFactory cf = null;
		try {
			cf = CertificateFactory.getInstance("X.509");
			certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(accessId
					.getBytes(StandardCharsets.UTF_8)));			
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
		
		if(results.size() != 0  && validCertificate) {
			return true;
		}
		return false;
	}

	//FIXME incomplete
	@Override
	public Token createFederationUserToken() {
		Token tokenFederationCredentials = null;
		
		return reIssueToken(tokenFederationCredentials);
	}
	
	private VOMSACValidator getVOMSValidator() {
		String trust = "src/test/resources/voms/trust-anchors";
		String vomsdir = "src/test/resources/voms/vomsdir";
		X509CertChainValidatorExt validator = CertificateValidatorBuilder
				.buildCertificateValidator(trust);
		return VOMSValidators.newValidator(new DefaultVOMSTrustStore(Arrays.asList(vomsdir)),
				validator);
	}	
}
