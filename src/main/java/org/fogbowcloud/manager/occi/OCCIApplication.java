package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.FogbowResource;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestUnit;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.engine.adapter.HttpRequest;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private IdentityPlugin identityPlugin;
	private ComputePlugin computePlugin;
	private Map<String, List<String>> userToRequestIds;

	private Map<String, RequestUnit> requestIdToRequestUnit;

	public OCCIApplication() {
		this.userToRequestIds = new ConcurrentHashMap<String, List<String>>();
		this.requestIdToRequestUnit = new ConcurrentHashMap<String, RequestUnit>();
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/request", RequestResource.class);
		router.attach("/request/{requestid}", SpecificRequestResource.class);
		return router;
	}

	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
	}

	public ComputePlugin getComputePlugin() {
		return computePlugin;
	}

	public Map<String, List<String>> getUserToRequestIds() {
		return userToRequestIds;
	}

	public void setIdentityPlugin(IdentityPlugin identityPlugin) {
		this.identityPlugin = identityPlugin;
	}

	public IdentityPlugin getIdentityPlugin() {
		return identityPlugin;
	}

	public RequestUnit getRequestDetails(String userToken, String requestId) {
		checkUserToken(userToken);
		checkRequestId(userToken, requestId);
		return requestIdToRequestUnit.get(requestId);
	}

	public RequestUnit newRequest(String userToken, List<FogbowResource> requestResources,
			Map<String, String> xOCCIAtt) {
		checkUserToken(userToken);
		
		if (userToRequestIds.get(userToken) == null) {
			userToRequestIds.put(userToken, new ArrayList<String>());
		}
		String requestId = String.valueOf(UUID.randomUUID());
		RequestUnit requestUnit = new RequestUnit(requestId, "", RequestState.OPEN,
				requestResources, xOCCIAtt);

		userToRequestIds.get(userToken).add(requestUnit.getId());
		requestIdToRequestUnit.put(requestUnit.getId(), requestUnit);

		submitRequest(requestUnit, requestResources, xOCCIAtt);

		return requestUnit;
	}

	// FIXME Should req be an attribute of requestUnit?
	private void submitRequest(RequestUnit requestUnit, List<FogbowResource> requestResources,
			Map<String, String> xOCCIAtt) {
		// TODO Choose if submit to local or remote cloud and submit
		computePlugin.requestInstance(requestResources, xOCCIAtt);
	}

	public List<RequestUnit> getRequestsFromUser(String userToken) {
		checkUserToken(userToken);

		List<RequestUnit> requests = new ArrayList<RequestUnit>();
		if (userToRequestIds.get(userToken) != null) {
			for (String requestId : userToRequestIds.get(userToken)) {
				requests.add(requestIdToRequestUnit.get(requestId));
			}
		}
		return requests;
	}

	public void removeAllRequests(String userToken) {
		checkUserToken(userToken);

		if (userToRequestIds.get(userToken) != null) {
			for (String requestId : userToRequestIds.get(userToken)) {
				requestIdToRequestUnit.remove(requestId);
			}
			userToRequestIds.remove(userToken);
		}
	}

	public void removeRequest(String userToken, String requestId) {
		checkUserToken(userToken);
		checkRequestId(userToken, requestId);

		userToRequestIds.get(userToken).remove(requestId);
		requestIdToRequestUnit.get(requestId);
	}

	private void checkRequestId(String userToken, String requestId) {
		if (userToRequestIds.get(userToken) == null
				|| !userToRequestIds.get(userToken).contains(requestId)) {
			throw new OCCIException(ErrorType.NOT_FOUND, "Resource not found.");
		}
	}

	private void checkUserToken(String userToken) {
		if (!identityPlugin.isValidToken(userToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, "Authentication required.");
		}
	}
}
