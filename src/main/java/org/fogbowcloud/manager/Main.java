package org.fogbowcloud.manager;

import java.io.FileInputStream;
import java.util.Properties;

import org.fogbowcloud.manager.core.FederationMemberValidator;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class Main {

	public static void main(String[] args) throws Exception {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(args[0]);
		properties.load(input);

		ComputePlugin computePlugin = (ComputePlugin) createInstance(
				"compute_class", properties);
		IdentityPlugin identityPlugin = (IdentityPlugin) createInstance(
				"identity_class", properties);

		FederationMemberValidator validator = (FederationMemberValidator) createInstance(
				"member_validator", properties);
		
		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setIdentityPlugin(identityPlugin);
		facade.setValidator(validator);
		
		ManagerXmppComponent xmpp = new ManagerXmppComponent(
				properties.getProperty("xmpp_jid"),
				properties.getProperty("xmpp_password"),
				properties.getProperty("xmpp_host"),
				Integer.parseInt(properties.getProperty("xmpp_port")), facade);
		xmpp.setRendezvousAddress(properties.getProperty("rendezvous_jid"));
		xmpp.connect();
		xmpp.process(false);
		xmpp.init();
		facade.setPacketSender(xmpp);

		OCCIApplication application = new OCCIApplication(facade);

		Component http = new Component();
		http.getServers().add(Protocol.HTTP,
				Integer.parseInt(properties.getProperty("http_port")));
		http.getDefaultHost().attach(application);
		http.start();
	}

	private static Object createInstance(String propName, Properties properties)
			throws Exception {
		return Class.forName(properties.getProperty(propName))
				.getConstructor(Properties.class).newInstance(properties);
	}

}
