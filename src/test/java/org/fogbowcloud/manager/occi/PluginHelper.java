package org.fogbowcloud.manager.occi;

import org.restlet.Component;
import org.restlet.data.Protocol;

public class PluginHelper {

	private Component component;
	
	public static final int PORT_ENDPOINT = 8182;
	
	public PluginHelper() {
		this.component = new Component();
	}
	
	public void initializeComponent() throws Exception {
		this.component = new Component();
		this.component.getServers().add(Protocol.HTTP, PORT_ENDPOINT);
		
		KeyStoneApplication keyStoneApplication = new KeyStoneApplication();
		
		this.component.getDefaultHost().attach(keyStoneApplication);
		this.component.start();				
	}
	
	public void disconnectComponent() throws Exception {
		this.component.stop();
	}
}
