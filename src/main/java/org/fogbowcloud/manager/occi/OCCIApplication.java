package org.fogbowcloud.manager.occi;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
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
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.routing.Router;
import org.restlet.util.Series;

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
		router.attach("/.well-known/org/ogf/occi/-/", QueryServerResource.class);
		router.attachDefault(new Restlet() {
			@Override
			public void handle(org.restlet.Request request, Response response) {
				normalizeBypass(request, response);
			}
		});
		return router;
	}
	
	@Override
	public void handle(org.restlet.Request request, Response response) {
		super.handle(request, response);
		
		/*
		 * The request will be bypassed only if response status was
		 * Method_NOT_ALLOWED and request path is not fogbow_request. Local
		 * private cloud does not treat fogbow_request requests.
		 */
		if (response.getStatus().getCode() == HttpStatus.SC_METHOD_NOT_ALLOWED
				&& !request.getOriginalRef().getPath().startsWith("/" + RequestConstants.TERM)) {
			normalizeBypass(request, response);
		}
	}

	@SuppressWarnings("unchecked")
	private void normalizeBypass(org.restlet.Request request, Response response) {
		Response newResponse = new Response(request);		
		normalizeHeadersForBypass(request);	
		
		bypass(request, newResponse);

		Series<org.restlet.engine.header.Header> responseHeaders = (Series<org.restlet.engine.header.Header>) newResponse
				.getAttributes().get("org.restlet.http.headers");
		if (responseHeaders != null) {
			// removing restlet default headers that will be added automatically
			responseHeaders.removeAll(HeaderConstants.HEADER_CONTENT_LENGTH);
			responseHeaders.removeAll(HeaderConstants.HEADER_CONTENT_TYPE);
			responseHeaders.removeAll(HeaderUtils.normalize(HeaderConstants.HEADER_CONTENT_TYPE));
			responseHeaders.removeAll(HeaderConstants.HEADER_DATE);
			responseHeaders.removeAll(HeaderConstants.HEADER_SERVER);
			responseHeaders.removeAll(HeaderConstants.HEADER_VARY);
			responseHeaders.removeAll(HeaderConstants.HEADER_ACCEPT_RANGES);
			newResponse.getAttributes().put("org.restlet.http.headers", responseHeaders);
		}
		response.setEntity(newResponse.getEntity());
		response.setStatus(newResponse.getStatus());
		response.setAttributes(newResponse.getAttributes());
	}

	public Token getToken(Map<String, String> attributesToken) {
		return managerFacade.getToken(attributesToken);
	}
	
	private static void normalizeHeadersForBypass(org.restlet.Request request) {
		Series<Header> requestHeaders = (Series<Header>) request.getAttributes().get("org.restlet.http.headers");
		String localAuthToken = requestHeaders.getFirstValue(HeaderUtils.normalize(OCCIHeaders.X_LOCAL_AUTH_TOKEN));
		if (localAuthToken == null) {
			return;
		}
		requestHeaders.removeFirst(HeaderUtils.normalize(OCCIHeaders.X_FEDERATION_AUTH_TOKEN));
		requestHeaders.removeFirst(HeaderUtils.normalize(OCCIHeaders.X_LOCAL_AUTH_TOKEN));
		requestHeaders.add(new Header(OCCIHeaders.X_AUTH_TOKEN, localAuthToken));
	}
	
	public List<FederationMember> getFederationMembers() {		
		return managerFacade.getMembers();
	}

	public Request getRequest(String authToken, String requestId) {
		return managerFacade.getRequest(authToken, requestId);
	}

	public List<Request> createRequests(String federationAuthToken, String localAuthToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		return managerFacade.createRequests(federationAuthToken, localAuthToken, categories, xOCCIAtt);
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
	
	public List<Instance> getInstancesFullInfo(String authToken) {
		return managerFacade.getInstancesFullInfo(authToken);
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

	public String getAuthenticationURI() {
		return managerFacade.getAuthenticationURI();
	}
	
	public Properties getProperties() {
		return managerFacade.getProperties();
	}

}