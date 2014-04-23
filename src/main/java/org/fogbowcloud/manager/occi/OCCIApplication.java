package org.fogbowcloud.manager.occi;

import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestServerResource;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private ManagerFacade managerFacade;

	public OCCIApplication(ManagerFacade facade) {
		this.managerFacade = facade;
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/request", RequestServerResource.class);
		router.attach("/request/{requestId}", RequestServerResource.class);
		router.attach("/compute/", ComputeServerResource.class);
		router.attach("/compute/{instanceId}", ComputeServerResource.class);
		return router;
	}

	public Request getRequest(String authToken, String requestId) {
		return managerFacade.getRequest(authToken, requestId);
	}

	public List<Request> createRequests(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		return managerFacade.createRequests(authToken, categories, xOCCIAtt);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		return managerFacade.getRequestsFromUser(authToken);
	}

	public void removeAllRequests(String authToken) {
		managerFacade.removeAllRequests(authToken);
	}

	public void removeRequest(String authToken, String requestId) {
		managerFacade.removeRequest(authToken, requestId);
	}

	public List<Instance> getInstances(String authToken) {
		return managerFacade.getInstances(authToken);
	}

	public Instance getInstance(String authToken, String instanceId) {
		return managerFacade.getInstance(authToken, instanceId);
	}

	public void removeInstances(String authToken) {
		managerFacade.removeInstances(authToken);
	}

	public void removeInstance(String authToken, String instanceId) {
		managerFacade.removeInstance(authToken, instanceId);
	}
}