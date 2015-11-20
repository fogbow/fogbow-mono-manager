package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.italiangrid.voms.VOMSAttribute;

public class VOBasedLocalCrendentialsPlugin implements LocalCredentialsPlugin {

	private Properties properties;
	private VomsIdentityPlugin vomsIdentityPlugin;
	
	public VOBasedLocalCrendentialsPlugin(Properties properties) {
		this.properties = properties;
		this.vomsIdentityPlugin = new VomsIdentityPlugin(properties);
	}

	@Override
	public Map<String, String> getLocalCredentials(Request request) {
		if (request == null) {
			return LocalCredentialsHelper.getCredentialsPerRelatedLocalName(
					this.properties, LocalCredentialsHelper.FOGBOW_DEFAULTS);			
		}
		
		String member = getVO(request);
		Map<String, String> credentialsPerMember = LocalCredentialsHelper
				.getCredentialsPerRelatedLocalName(this.properties, member);
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return LocalCredentialsHelper.getCredentialsPerRelatedLocalName(
				this.properties, LocalCredentialsHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return LocalCredentialsHelper.getLocalCredentials(properties, null);
	}
	
	protected String getVO(Request request) {
		String accessId = request.getFederationToken().getAccessId();
		if (!vomsIdentityPlugin.isValid(accessId)) {
			return LocalCredentialsHelper.FOGBOW_DEFAULTS;
		}	

		X509Certificate[] theChain = null;
		try {
			theChain = CertificateUtils.extractCertificates(
					CertificateUtils.parseChain(accessId)).toArray(new X509Certificate[] {});
			for (VOMSAttribute vomsAttribute : vomsIdentityPlugin.getVOMSValidator().validate(theChain)) {
				return vomsAttribute.getVO();
			}
		} catch (Exception e) {}		
		
		return LocalCredentialsHelper.FOGBOW_DEFAULTS;
	}
}
