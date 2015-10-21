package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.bouncycastle.util.io.pem.PemObject;
import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.FederationUserCredentailsPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class VOBasedFUCPlugin implements FederationUserCredentailsPlugin {

	private Properties properties;
	
	public VOBasedFUCPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getFedUserCredentials(Request request) {
		String providerMember =  getProviderMember(request);
		Map<String, String> credentialsPerProvider = FUCPluginHelper.getCredentialsPerProvider(this.properties, providerMember);
		if (!credentialsPerProvider.isEmpty()) {
			return credentialsPerProvider;
		}		
		return FUCPluginHelper.getCredentialsPerProvider(this.properties, FUCPluginHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllFedUsersCredentials() {
		return FUCPluginHelper.getProvidersCredentials(properties, null);
	}
	
	protected String getProviderMember(Request request) {
		String accessId = request.getFederationToken().getAccessId();
		VomsIdentityPlugin vomsIdentityPlugin = new VomsIdentityPlugin(properties);
		if (!vomsIdentityPlugin.isValid(accessId)) {
			return FUCPluginHelper.FOGBOW_DEFAULTS;
		}
		
		List<PemObject> chain = null;
		try {
			chain = CertificateUtils.parseChain(accessId);
		} catch (Exception e) {}

		Collection<X509Certificate> certificates = null;
		try {
			certificates = CertificateUtils.extractCertificates(chain);
		} catch (Exception e) {}

		X509Certificate x509Certificate = (X509Certificate) certificates.toArray()[certificates.toArray().length -1];
		String issuer = x509Certificate.getIssuerDN().getName();			
		
		return issuer;
	}

}
