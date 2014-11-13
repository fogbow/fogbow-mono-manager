package org.fogbowcloud.manager.core.plugins.common;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.core.Token;

public class AllowAllAuthorizationPlugin implements AuthorizationPlugin {

	public AllowAllAuthorizationPlugin(Properties properties) {
		// Do Nothing
	}
	
	@Override
	public boolean isAuthorized(Token token) {
		return true;
	}

}
