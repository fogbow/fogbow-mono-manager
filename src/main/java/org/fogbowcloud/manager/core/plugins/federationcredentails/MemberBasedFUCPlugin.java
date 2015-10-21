package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.FederationUserCredentailsPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class MemberBasedFUCPlugin implements FederationUserCredentailsPlugin {
	
	private Properties properties;
	
	public MemberBasedFUCPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getFedUserCredentials(Request request) {
		Map<String, String> credentialsPerMember = FUCPluginHelper.getCredentialsPerMember(
				this.properties, request.getRequestingMemberId());
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return FUCPluginHelper.getCredentialsPerMember(this.properties, FUCPluginHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllFedUsersCredentials() {
		return FUCPluginHelper.getMemberCredentials(properties, null);
	}
}