package org.fogbowcloud.manager.core.plugins.authorization;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.model.Token;

public class AllowAllAuthorizationPlugin implements AuthorizationPlugin {

	public AllowAllAuthorizationPlugin(Properties properties) {
		// Do Nothing
	}
	
	@Override
	public boolean isAuthorized(Token token) {
		return true;
	}

}
