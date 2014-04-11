package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.Request;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private IdentityPlugin identityPlugin;
	private ComputePlugin computePlugin;
	private Map<String, List<String>> userToRequestIds;
	private Map<String, Request> requestIdToRequestUnit;
	
	private static final Logger LOGGER = Logger.getLogger(OCCIApplication.class);

	public OCCIApplication() {
		this.userToRequestIds = new ConcurrentHashMap<String, List<String>>();
		this.requestIdToRequestUnit = new ConcurrentHashMap<String, Request>();
	}

	@Override
	public Restlet createInboundRoot() {
		Router router = new Router(getContext());
		router.attach("/request", RequestServerResource.class);
		router.attach("/request/{requestid}", RequestServerResource.class);
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

	public Request getRequestDetails(String authToken, String requestId) {
		checkUserToken(authToken);
		checkRequestId(authToken, requestId);
		return requestIdToRequestUnit.get(requestId);
	}

	public Request newRequest(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		checkUserToken(authToken);
		
		String user = getIdentityPlugin().getUser(authToken);
		
		if (userToRequestIds.get(user) == null) {
			userToRequestIds.put(user, new ArrayList<String>());
		}
		String requestId = String.valueOf(UUID.randomUUID());
		Request request = new Request(requestId, "", RequestState.OPEN,
				categories, xOCCIAtt);

		userToRequestIds.get(user).add(request.getId());
		requestIdToRequestUnit.put(request.getId(), request);

		submitRequest(request, categories, xOCCIAtt);

		return request;
	}

	// FIXME Should req be an attribute of requestUnit?
	private void submitRequest(Request request, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		// TODO Choose if submit to local or remote cloud and submit
		computePlugin.requestInstance(categories, xOCCIAtt);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		checkUserToken(authToken);
		String user = getIdentityPlugin().getUser(authToken);
		
		List<Request> requests = new ArrayList<Request>();
		if (userToRequestIds.get(user) != null) {
			for (String requestId : userToRequestIds.get(user)) {
				requests.add(requestIdToRequestUnit.get(requestId));
			}
		}
		return requests;
	}

	public void removeAllRequests(String authToken) {
		checkUserToken(authToken);
		String user = getIdentityPlugin().getUser(authToken);
		
		if (userToRequestIds.get(user) != null) {
			for (String requestId : userToRequestIds.get(user)) {
				requestIdToRequestUnit.remove(requestId);
			}
			userToRequestIds.remove(user);
		}
	}

	public void removeRequest(String althToken, String requestId) {
		checkUserToken(althToken);
		checkRequestId(althToken, requestId);
		String user = getIdentityPlugin().getUser(althToken);
		
		userToRequestIds.get(user).remove(requestId);
		requestIdToRequestUnit.get(requestId);
	}

	private void checkRequestId(String authToken, String requestId) {
		String user = getIdentityPlugin().getUser(authToken);
		
		if (userToRequestIds.get(user) == null
				|| !userToRequestIds.get(user).contains(requestId)) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	private void checkUserToken(String userToken) {
		if (!identityPlugin.isValidToken(userToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
}
