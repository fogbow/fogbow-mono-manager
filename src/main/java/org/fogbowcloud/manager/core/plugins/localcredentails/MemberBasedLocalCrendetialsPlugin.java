package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class MemberBasedLocalCrendetialsPlugin implements LocalCredentialsPlugin {
	
	private Properties properties;
	
	public MemberBasedLocalCrendetialsPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getLocalCredentials(Request request) {
		if (request == null) {
			return LocalCredentialsHelper.getCredentialsPerRelatedLocalName(
					this.properties, LocalCredentialsHelper.FOGBOW_DEFAULTS);	
		}
		Map<String, String> credentialsPerMember = LocalCredentialsHelper.getCredentialsPerRelatedLocalName(
				this.properties, request.getRequestingMemberId());
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

	@Override
	public Map<String, String> getLocalCredentials(String accessId) {
		return null;
	}
}