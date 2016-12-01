package org.fogbowcloud.manager.core.plugins.localcredentails;

import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.occi.order.Order;

public class FederationUserBasedMapperPlugin implements MapperPlugin {

	private Properties properties;
	private IdentityPlugin federationIdentityPlugin;
	private static final Logger LOGGER = Logger.getLogger(FederationUserBasedMapperPlugin.class);
	
	public FederationUserBasedMapperPlugin(Properties properties) {
		this.properties = properties;
		
		LOGGER.debug("Using FederationUserBasedLocalCredentialsPlugin");
		federationIdentityPlugin = null;		
		try {
			federationIdentityPlugin = (IdentityPlugin) getIdentityPluginByPrefix(properties,
					ConfigurationConstants.FEDERATION_PREFIX);
			LOGGER.debug("federationPlugin is null?" + (federationIdentityPlugin == null));
		} catch (Exception e) {
			throw new IllegalArgumentException("Federation Identity Plugin not especified in the properties.", e);
		}
	}
	
	private Object getIdentityPluginByPrefix(Properties properties, String prefix)
			throws Exception {
		Properties pluginProperties = new Properties();
		for (Object keyObj : properties.keySet()) {
			String key = keyObj.toString();
			pluginProperties.put(key, properties.get(key));
			if (key.startsWith(prefix)) {
				String newKey = key.replace(prefix, "");
				pluginProperties.put(newKey, properties.get(key));
			}
		}
		return createInstance(prefix + ConfigurationConstants.IDENTITY_CLASS_KEY, pluginProperties);
	}

	private Object createInstance(String propName, Properties properties) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class)
				.newInstance(properties);
	}

	@Override
	public Map<String, String> getLocalCredentials(Order order) {
		String userName = order.getFederationToken().getUser().getName();
		String normalizedUserName = MapperHelper.normalizeUser(userName);

		LOGGER.debug("NormalizedFederationUsername=" + normalizedUserName);
		Map<String, String> credentialsPerMember = MapperHelper
				.getCredentialsPerRelatedLocalName(this.properties, normalizedUserName);

		LOGGER.debug("Credentials for " + normalizedUserName + " are " + credentialsPerMember);
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return MapperHelper.getCredentialsPerRelatedLocalName(this.properties,
				MapperHelper.FOGBOW_DEFAULTS);
	}

	@Override
	public Map<String, Map<String, String>> getAllLocalCredentials() {
		return MapperHelper.getLocalCredentials(properties, null);
	}

	@Override
	public Map<String, String> getLocalCredentials(String accessId) {
		String userName = this.federationIdentityPlugin.getToken(accessId).getUser().getName();
		String normalizedUserName = MapperHelper.normalizeUser(userName);
		LOGGER.debug("NormalizeFederationUserName=" + normalizedUserName);

		Map<String, String> credentialsPerMember = MapperHelper
				.getCredentialsPerRelatedLocalName(this.properties, normalizedUserName);

		LOGGER.debug("Credentials for " + normalizedUserName + " are " + credentialsPerMember);
		if (!credentialsPerMember.isEmpty()) {
			return credentialsPerMember;
		}
		return MapperHelper.getCredentialsPerRelatedLocalName(this.properties,
				MapperHelper.FOGBOW_DEFAULTS);
	}
}
