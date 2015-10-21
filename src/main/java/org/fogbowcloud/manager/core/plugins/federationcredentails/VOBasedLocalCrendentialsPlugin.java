package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.CertificateUtils;
import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.core.plugins.identity.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.italiangrid.voms.VOMSAttribute;
import org.italiangrid.voms.VOMSValidators;
import org.italiangrid.voms.ac.VOMSACValidator;

public class VOBasedLocalCrendentialsPlugin implements LocalCredentialsPlugin {

	private Properties properties;
	private VOMSACValidator vomSACValidator;
	
	public VOBasedLocalCrendentialsPlugin(Properties properties) {
		this.properties = properties;
		this.vomSACValidator = VOMSValidators.newValidator();
	}

	@Override
	public Map<String, String> getLocalCredentials(Request request) {
		String member = getVO(request);
		Map<String, String> credentialsPerMember = LocalCredentialsHelper
				.getCredentialsPerRelatedLocalName(this.properties, member);
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return LocalCredentialsHelper.getCredentialsPerRelatedLocalName(this.properties,
				LocalCredentialsHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return LocalCredentialsHelper.getLocalCredentials(properties, null);
	}
	
	protected String getVO(Request request) {
		String accessId = request.getFederationToken().getAccessId();
		VomsIdentityPlugin vomsIdentityPlugin = new VomsIdentityPlugin(properties);
		if (!vomsIdentityPlugin.isValid(accessId)) {
			return LocalCredentialsHelper.FOGBOW_DEFAULTS;
		}	

		X509Certificate[] theChain = null;
		try {
			theChain = CertificateUtils.extractCertificates(
					CertificateUtils.parseChain(accessId)).toArray(new X509Certificate[] {});
			for (VOMSAttribute vomsAttribute : vomSACValidator.validate(theChain)) {
				return vomsAttribute.getVO();
			}
		} catch (Exception e) {}		
		
		return LocalCredentialsHelper.FOGBOW_DEFAULTS;
	}

	public void setVomSACValidator(VOMSACValidator vomSACValidator) {
		this.vomSACValidator = vomSACValidator;
	}
}
