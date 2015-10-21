package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.LocalCredentialsPlugin;
import org.fogbowcloud.manager.occi.request.Request;

public class SingleLocalCrendentialsPlugin implements LocalCredentialsPlugin {

	private Properties properties;
	
	public SingleLocalCrendentialsPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getLocalCredentials(Request request) {
		return LocalCredentialsHelper.getCredentialsPerRelatedLocalName(this.properties, LocalCredentialsHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return LocalCredentialsHelper.getLocalCredentials(properties,
				Arrays.asList(new String[] { LocalCredentialsHelper.FOGBOW_DEFAULTS }));
	}
}
