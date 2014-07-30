package org.fogbowcloud.manager.occi;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestServerResource;
import org.restlet.Application;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private ManagerController managerFacade;

	public OCCIApplication(ManagerController facade) {
		this.managerFacade = facade;
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/" + RequestConstants.TERM, RequestServerResource.class);
		router.attach("/" + RequestConstants.TERM + "/", RequestServerResource.class);
		router.attach("/" + RequestConstants.TERM + "/{requestId}", RequestServerResource.class);
		router.attach("/compute", ComputeServerResource.class);
		router.attach("/compute/", ComputeServerResource.class);
		router.attach("/compute/{instanceId}", ComputeServerResource.class);
		router.attach("/members", MemberServerResource.class);
		router.attach("/token", TokenServerResource.class);
		router.attach("/-/", QueryServerResource.class);
		router.attachDefault(new Restlet() {
			@Override
			public void handle(org.restlet.Request request, Response response) {
				bypass(request, response);
			}
		});
		return router;
	}
	
	@Override
	public void handle(org.restlet.Request request, Response response) {
		super.handle(request, response);
		
		if (response.getStatus().getCode() == HttpStatus.SC_METHOD_NOT_ALLOWED){			
			bypass(request, response);
		}
	}
	
	public Token getToken(Map<String, String> attributesToken) {
		return managerFacade.getToken(attributesToken);
	}
	
	public List<FederationMember> getFederationMembers() {		
		return managerFacade.getMembers();
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

	public List<Resource> getAllResources(String authToken) {
		return managerFacade.getAllResouces(authToken);
	}

	public void bypass(org.restlet.Request request, Response response) {
		managerFacade.bypass(request, response);
	}
}