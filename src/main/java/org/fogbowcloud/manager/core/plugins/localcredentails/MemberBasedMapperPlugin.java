package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.occi.order.Order;

public class MemberBasedMapperPlugin implements MapperPlugin {
	
	private Properties properties;
	
	public MemberBasedMapperPlugin(Properties properties) {
		this.properties = properties;
	}

	@Override
	public Map<String, String> getLocalCredentials(Order order) {
		if (order == null) {
			return MapperHelper.getCredentialsPerRelatedLocalName(
					this.properties, MapperHelper.FOGBOW_DEFAULTS);	
		}
		Map<String, String> credentialsPerMember = MapperHelper.getCredentialsPerRelatedLocalName(
				this.properties, order.getRequestingMemberId());
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return MapperHelper.getCredentialsPerRelatedLocalName(
				this.properties, MapperHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return MapperHelper.getLocalCredentials(properties, null);
	}

	@Override
	public Map<String, String> getLocalCredentials(String accessId) {
		return null;
	}
}