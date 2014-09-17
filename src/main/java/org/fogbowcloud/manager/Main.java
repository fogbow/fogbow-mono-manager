package org.fogbowcloud.manager;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.DefaultMemberValidator;
import org.fogbowcloud.manager.core.FederationMemberValidator;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.common.AllowAllAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackOCCIComputePlugin;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class Main {

	private static final Logger LOGGER = Logger.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		configureLog4j();

		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(args[0]);
		properties.load(input);

		ComputePlugin computePlugin;
		try {
			computePlugin = (ComputePlugin) createInstance(
					ConfigurationConstants.COMPUTE_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Compute Plugin not especified in the properties.");
			computePlugin = new OpenStackOCCIComputePlugin(properties);
		}

		AuthorizationPlugin authorizationPlugin = null;
		try {
			authorizationPlugin = (AuthorizationPlugin) createInstance(
					ConfigurationConstants.AUTHORIZATION_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Authorization Plugin not especified in the properties.");
			authorizationPlugin = new AllowAllAuthorizationPlugin();
		}
		
		IdentityPlugin localIdentityPlugin = null;
		try {
			localIdentityPlugin = (IdentityPlugin) getIdentityPluginByPrefix(properties,
					ConfigurationConstants.LOCAL_PREFIX);
		} catch (Exception e) {
			LOGGER.warn("Local Identity Plugin not especified in the properties.");
			localIdentityPlugin = new KeystoneIdentityPlugin(properties);
		}
		IdentityPlugin federationIdentityPlugin = null;
		try {
			federationIdentityPlugin = (IdentityPlugin) getIdentityPluginByPrefix(properties,
					ConfigurationConstants.FEDERATION_PREFIX);
		} catch (Exception e) {
			LOGGER.warn("Federation Identity Plugin not especified in the properties.");
			federationIdentityPlugin = new KeystoneIdentityPlugin(properties);
		}

		FederationMemberValidator validator = new DefaultMemberValidator();
		try {
			validator = (FederationMemberValidator) createInstance(
					ConfigurationConstants.MEMBER_VALIDATOR_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Member Validator not especified in the properties.");
		}

		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(localIdentityPlugin);
		facade.setFederationIdentityPlugin(federationIdentityPlugin);
		facade.setValidator(validator);

		ManagerXmppComponent xmpp = new ManagerXmppComponent(
				properties.getProperty(ConfigurationConstants.XMPP_JID_KEY),
				properties.getProperty(ConfigurationConstants.XMPP_PASS_KEY),
				properties.getProperty(ConfigurationConstants.XMPP_HOST_KEY),
				Integer.parseInt(properties.getProperty(ConfigurationConstants.XMPP_PORT_KEY)),
				facade);
		xmpp.setRendezvousAddress(properties.getProperty(ConfigurationConstants.RENDEZVOUS_JID_KEY));
		xmpp.connect();
		xmpp.process(false);
		xmpp.init();
		facade.setPacketSender(xmpp);

		OCCIApplication application = new OCCIApplication(facade);

		Component http = new Component();
		http.getLogger().setLevel(Level.OFF);
		http.getServers().add(Protocol.HTTP,
				Integer.parseInt(properties.getProperty(ConfigurationConstants.HTTP_PORT_KEY)));
		http.getDefaultHost().attach(application);
		http.start();
	}

	private static Object getIdentityPluginByPrefix(Properties properties, String prefix)
			throws Exception {
		Properties pluginProperties = new Properties();
		for (Object keyObj : properties.keySet()) {
			String key = keyObj.toString();
			pluginProperties.put(key, properties.get(key));
			if (key.contains(prefix)) {
				String newKey = key.replace(prefix, "");
				pluginProperties.put(newKey, properties.get(key));
			}
		}
		return createInstance(prefix + ConfigurationConstants.IDENTITY_CLASS_KEY, pluginProperties);
	}

	private static Object createInstance(String propName, Properties properties) throws Exception {
		return Class.forName(properties.getProperty(propName)).getConstructor(Properties.class)
				.newInstance(properties);
	}

	private static void configureLog4j() {
		ConsoleAppender console = new ConsoleAppender();
		console.setThreshold(org.apache.log4j.Level.OFF);
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);
	}
}
