package org.fogbowcloud.manager.occi;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private IdentityPlugin identityPlugin;
	private ComputePlugin computePlugin;
	private Map<String, List<RequestUnit>> userToRequests;
	
	public OCCIApplication(){
		this.userToRequests = new ConcurrentHashMap<String, List<RequestUnit>>();
	}
	
	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/request", Request.class);
		return router;
	}
	
	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
	}
	
	public ComputePlugin getComputePlugin() {
		return computePlugin;
	}
	
	public Map<String, List<RequestUnit>> getUserToRequest(){
		return userToRequests;
	}
	
	public void setIdentityPlugin(IdentityPlugin identityPlugin) {
		this.identityPlugin = identityPlugin;		
	}
	
	public IdentityPlugin getIdentityPlugin() {
		return identityPlugin;
	}

}
