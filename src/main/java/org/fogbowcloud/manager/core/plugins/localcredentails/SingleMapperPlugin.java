package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.occi.order.Order;

public class SingleMapperPlugin implements MapperPlugin {

	private Properties properties;
	
	public SingleMapperPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getLocalCredentials(Order order) {
		return MapperHelper.getCredentialsPerRelatedLocalName(this.properties, MapperHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return MapperHelper.getLocalCredentials(properties,
				Arrays.asList(new String[] { MapperHelper.FOGBOW_DEFAULTS }));
	}

	@Override
	public Map<String, String> getLocalCredentials(String accessId) {
		return getLocalCredentials(new Order("", null, "", "", "", 
				new Date().getTime(), false, null, null, null));
	}
}
