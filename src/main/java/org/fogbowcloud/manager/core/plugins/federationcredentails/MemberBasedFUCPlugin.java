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
		Map<String, String> credentialsPerProvider = FUCPluginHelper.getCredentialsPerProvider(
				this.properties, request.getRequestingMemberId());
		if (!credentialsPerProvider.isEmpty()) {
			return credentialsPerProvider;
		}
		return FUCPluginHelper.getCredentialsPerProvider(this.properties, FUCPluginHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllFedUsersCredentials() {
		return FUCPluginHelper.getProvidersCredentials(properties, null);
	}
}