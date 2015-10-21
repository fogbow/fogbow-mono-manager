package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.FederationUserCredentailsPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class SingleFUCPlugin implements FederationUserCredentailsPlugin {

	private Properties properties;
	
	public SingleFUCPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getFedUserCredentials(Request request) {
		return FUCPluginHelper.getCredentialsPerMember(this.properties, FUCPluginHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllFedUsersCredentials() {
		return FUCPluginHelper.getMemberCredentials(properties,
				Arrays.asList(new String[] { FUCPluginHelper.FOGBOW_DEFAULTS }));
	}
}
