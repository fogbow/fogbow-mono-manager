package org.fogbowcloud.manager.core.plugins;

import org.fogbowcloud.manager.occi.model.Token;

public interface AuthorizationPlugin {
	
	public boolean isAuthorized(Token token);
	
}
