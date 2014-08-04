package org.fogbowcloud.manager.core.plugins.openstack;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.occi.core.Token;

public class OpenStackAuthorizationPlugin implements AuthorizationPlugin{

	public OpenStackAuthorizationPlugin(Properties properties) {
	}
	
	@Override
	public boolean isAutorized(Token token) {
		return true;
	}

}
