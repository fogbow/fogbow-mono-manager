package org.fogbowcloud.manager.occi;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.occi.instance.ComputeServerResource;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.member.MemberServerResource;
import org.fogbowcloud.manager.occi.member.QuotaServerResource;
import org.fogbowcloud.manager.occi.member.UsageServerResource;
import org.fogbowcloud.manager.occi.member.UsageServerResource.ResourceUsage;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.network.NetworkServerResource;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderServerResource;
import org.fogbowcloud.manager.occi.storage.StorageLinkRepository.StorageLink;
import org.fogbowcloud.manager.occi.storage.StorageLinkServerResource;
import org.fogbowcloud.manager.occi.storage.StorageServerResource;
import org.restlet.Application;
import org.restlet.Response;
import org.restlet.Restlet;
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
		router.attach("/" + OrderConstants.TERM, OrderServerResource.class);
		router.attach("/" + OrderConstants.TERM + "/", OrderServerResource.class);
		router.attach("/" + OrderConstants.TERM + "/{orderId}", OrderServerResource.class);		
		router.attach("/" + OrderConstants.STORAGE_TERM, StorageServerResource.class);
		router.attach("/" + OrderConstants.STORAGE_TERM + "/", StorageServerResource.class);
		router.attach("/" + OrderConstants.STORAGE_TERM + "/{storageId}", StorageServerResource.class);
		router.attach("/" + OrderConstants.STORAGE_TERM + "/" + OrderConstants.STORAGE_LINK_TERM + "/"
				, StorageLinkServerResource.class);
		router.attach("/" + OrderConstants.STORAGE_TERM + "/" + OrderConstants.STORAGE_LINK_TERM
				+ "/{storageLinkId}", StorageLinkServerResource.class);
		router.attach("/" + OrderConstants.NETWORK_TERM + "/", NetworkServerResource.class);
		router.attach("/" + OrderConstants.COMPUTE_TERM, ComputeServerResource.class);
		router.attach("/" + OrderConstants.COMPUTE_TERM + "/", ComputeServerResource.class);
		router.attach("/" + OrderConstants.COMPUTE_TERM + "/{instanceId}", ComputeServerResource.class);
		router.attach("/member/accounting/" + OrderConstants.COMPUTE_TERM, AccountingServerResource.class);
		router.attach("/member/accounting/" + OrderConstants.STORAGE_TERM, AccountingServerResource.class);
		router.attach("/member", MemberServerResource.class);
		router.attach("/member/{memberId}/quota", QuotaServerResource.class);
		router.attach("/member/{memberId}/quota/", QuotaServerResource.class);
		router.attach("/member/{memberId}/usage", UsageServerResource.class);
		//TODO remove this endpoint
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
				&& !request.getOriginalRef().getPath().startsWith("/" + OrderConstants.TERM)) {
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
	
	@SuppressWarnings("unchecked")
	public static void normalizeHeadersForBypass(org.restlet.Request request) {
		Series<Header> requestHeaders = (Series<Header>) request.getAttributes().get("org.restlet.http.headers");
		requestHeaders.add(OCCIHeaders.X_AUTH_TOKEN, requestHeaders.getFirstValue(HeaderUtils
				.normalize(OCCIHeaders.X_AUTH_TOKEN)));
		requestHeaders.removeFirst(HeaderUtils.normalize(OCCIHeaders.X_AUTH_TOKEN));
	}

	
	public List<FederationMember> getFederationMembers(String accessId) {		
		return managerFacade.getRendezvousMembers();
	}
	
	public FederationMember getFederationMemberQuota(String federationMemberId, String accessId) {		
		return managerFacade.getFederationMemberQuota(federationMemberId, accessId);
	}	

	public StorageLink getStorageLink(String authToken, String storageLinkId) {
		return managerFacade.getStorageLink(authToken, storageLinkId);
	}
	
	public Order getOrder(String authToken, String orderId) {
		return managerFacade.getOrder(authToken, orderId);
	}

	public StorageLink createStorageLink(String federationAuthToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		return managerFacade.createStorageLink(federationAuthToken, categories, xOCCIAtt);
	}
	
	public List<Order> createOrders(String federationAuthToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		return managerFacade.createOrders(federationAuthToken, categories, xOCCIAtt);
	}

	public List<StorageLink> getStorageLinksFromUser(String authToken) {
		return managerFacade.getStorageLinkFromUser(authToken);
	}
	
	public List<Order> getOrdersFromUser(String authToken) {
		return managerFacade.getOrdersFromUser(authToken);
	}

	public void removeAllOrders(String authToken) {
		managerFacade.removeAllOrders(authToken);
	}
	
	public void removeAllStorageLink(String authToken) {
		managerFacade.removeAllOrders(authToken);
	}	

	public void removeOrder(String authToken, String orderId) {
		managerFacade.removeOrder(authToken, orderId);
	}
	
	public void removeStorageLink(String authToken, String storageLinkId) {
		managerFacade.removeStorageLink(authToken, storageLinkId);
	}	

	public List<Instance> getInstances(String authToken) {
		return getInstances(authToken, OrderConstants.COMPUTE_TERM);
	}
	
	public List<Instance> getInstances(String authToken, String resourceKind) {
		return managerFacade.getInstances(authToken, resourceKind);
	}
	
	public List<Instance> getInstancesFullInfo(String authToken) {
		return managerFacade.getInstancesFullInfo(authToken);
	}

	public Instance getInstance(String authToken, String instanceId) {
		return getInstance(authToken, instanceId, OrderConstants.COMPUTE_TERM);
	}
	
	public Instance getInstance(String authToken, String instanceId, String resourceKind) {
		return managerFacade.getInstance(authToken, instanceId, resourceKind);
	}

	public void removeInstances(String authToken) {
		removeInstances(authToken, OrderConstants.COMPUTE_TERM);
	}
	
	public void removeInstances(String authToken, String resourceKind) {
		managerFacade.removeInstances(authToken, resourceKind);
	}

	public void removeInstance(String authToken, String instanceId) {
		removeInstance(authToken, instanceId, OrderConstants.COMPUTE_TERM);
	}
	
	public void removeInstance(String authToken, String instanceId, String resourceKind) {
		managerFacade.removeInstance(authToken, instanceId, resourceKind);
	}

	public List<Resource> getAllResources(String authToken) {
		return managerFacade.getAllResouces(authToken);
	}

	/**
	 * This method will not be supported in next releases.
	 * @param request
	 * @param response
	 */
	@Deprecated
	public void bypass(org.restlet.Request request, Response response) {
		managerFacade.bypass(request, response);
	}

	public String getAuthenticationURI() {
		return managerFacade.getAuthenticationURI();
	}
	
	public Properties getProperties() {
		return managerFacade.getProperties();
	}
	
	public List<Flavor> getFlavorsProvided(){
		return managerFacade.getFlavorsProvided();
	}

	public String getUser(String authToken) {
		return managerFacade.getUser(authToken);
	}

	public List<AccountingInfo> getAccountingInfo(String authToken, String resourceKing) {
		return managerFacade.getAccountingInfo(authToken, resourceKing);
	}

	public ResourceUsage getUsages(String authToken, String memberId) {
		return managerFacade.getUsages(authToken, memberId);
	}
}
