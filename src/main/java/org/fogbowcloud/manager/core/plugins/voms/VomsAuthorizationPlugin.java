package org.fogbowcloud.manager.core.plugins.voms;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.core.Token;

public class VomsAuthorizationPlugin implements AuthorizationPlugin{

	public VomsAuthorizationPlugin(Properties properties) {
	}
	
	@Override
	public boolean isAutorized(Token token) {
		return true;
	}

}
