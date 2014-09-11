package org.fogbowcloud.manager.core.plugins.common;

import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.core.Token;

public class AllowAllAuthorizationPlugin implements AuthorizationPlugin {

	@Override
	public boolean isAuthorized(Token token) {
		return true;
	}

}
