package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.RequestUnit;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
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
		if (!identityPlugin.isValidToken(userToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, "User is not authorized.");
		}
		if (userToRequestIds.get(userToken) == null
				|| !userToRequestIds.get(userToken).contains(requestId)) {
			throw new OCCIException(ErrorType.NOT_FOUND, "Request id is not found.");
		}
		return requestIdToRequestUnit.get(requestId);
	}

	public void newRequest(String userToken, RequestUnit requestUnit, HttpRequest req) {
		if (!identityPlugin.isValidToken(userToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, "User is not authorized.");
		}
		if (userToRequestIds.get(userToken) == null) {
			userToRequestIds.put(userToken, new ArrayList<String>());
		}

		userToRequestIds.get(userToken).add(requestUnit.getId());
		requestIdToRequestUnit.put(requestUnit.getId(), requestUnit);

		// TODO Here is the correct place to it?
		computePlugin.requestInstance(req);
	}

	public List<RequestUnit> getRequestsFromUser(String userToken) {
		if (!identityPlugin.isValidToken(userToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, "User is not authorized.");
		}

		List<RequestUnit> requests = new ArrayList<RequestUnit>();
		if (userToRequestIds.get(userToken) != null) {
			for (String requestId : userToRequestIds.get(userToken)) {
				requests.add(requestIdToRequestUnit.get(requestId));
			}
		}
		return requests;
	}

}
