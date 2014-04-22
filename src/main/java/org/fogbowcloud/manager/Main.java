package org.fogbowcloud.manager;

import java.io.FileInputStream;
import java.util.Properties;

import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class Main {

	public static void main(String[] args) throws Exception {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(args[0]);
		properties.load(input);
		
		ManagerFacade facade = new ManagerFacade(properties);
		
		ManagerXmppComponent xmpp = new ManagerXmppComponent(properties.getProperty("xmpp_jid"), 
				properties.getProperty("xmpp_password"), 
				properties.getProperty("xmpp_host"), 
				Integer.parseInt(properties.getProperty("xmpp_port")), 
				facade);
		xmpp.connect();
		xmpp.process(false);
		
		OCCIApplication application = new OCCIApplication(facade);
		
		Component http = new Component();
		http.getServers().add(Protocol.HTTP, 8182);
		http.getDefaultHost().attach(application);
		http.start();
	}

}
