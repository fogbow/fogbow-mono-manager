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
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackComputePlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
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

		ComputePlugin computePlugin = new OpenStackComputePlugin(properties);
		try {
			computePlugin = (ComputePlugin) createInstance(
					ConfigurationConstants.COMPUTE_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Compute Plugin not especified in the properties.");
		}
		IdentityPlugin identityPlugin = new OpenStackIdentityPlugin(properties);
		try {
			identityPlugin = (IdentityPlugin) createInstance(
					ConfigurationConstants.IDENTITY_CLASS_KEY, properties);
		} catch (Exception e) {
			LOGGER.warn("Identity Plugin not especified in the properties.");
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
		facade.setIdentityPlugin(identityPlugin);
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
