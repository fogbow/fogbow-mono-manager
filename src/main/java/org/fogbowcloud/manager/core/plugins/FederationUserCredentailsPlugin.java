package org.fogbowcloud.manager.core.plugins;

import java.util.Map;

import org.fogbowcloud.manager.occi.request.Request;

public interface FederationUserCredentailsPlugin {

	Map<String, String> getFedUserCredentials(Request request);

	Map<String, Map<String, String>> getAllFedUsersCredentials();
	
}
