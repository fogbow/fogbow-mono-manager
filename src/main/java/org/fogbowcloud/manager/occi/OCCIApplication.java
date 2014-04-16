package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestsBox;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private IdentityPlugin identityPlugin;
	private ComputePlugin computePlugin;
	private Map<String, RequestsBox> userToRequestIds;
	private Map<String, Request> requestIdToRequest;
	private final Timer timer = new Timer();
	protected static final long PERIOD = 50;

	private static final Logger LOGGER = Logger.getLogger(OCCIApplication.class);

	public OCCIApplication() {
		this.userToRequestIds = new ConcurrentHashMap<String, RequestsBox>();
		this.requestIdToRequest = new ConcurrentHashMap<String, Request>();
		submitRequests();
	}

	private void submitRequests() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				checkAndSubmitOpenRequests();
			}
		}, 0, PERIOD);
	}

	private void checkAndSubmitOpenRequests() {
		Iterator<Entry<String, RequestsBox>> iter = userToRequestIds.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, RequestsBox> entry = iter.next();
			String user = entry.getKey();
			RequestsBox requestBoxes = entry.getValue();
			List<String> openIds = new ArrayList<String>();
			openIds.addAll(requestBoxes.getOpenIds());

			for (String requestId : openIds) {
				Request request = requestIdToRequest.get(requestId);
				// TODO before submit request we have to check
				try {
					submitLocalRequest(request);
					userToRequestIds.get(user).openToFulfilled(request.getId());
				} catch (OCCIException e) {
					if (e.getStatus().equals(ErrorType.BAD_REQUEST)
							&& e.getMessage().contains(
									ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
						submitRemoteRequest(request); // FIXME submit more than
														// one at same time
					} else {
						// TODO set state to fail?
						updateStateToFailed(user, request);
						throw e;
					}
				}
			}
		}
	}

	private void updateStateToFailed(String user, Request request) {
		userToRequestIds.get(user).openToFailed(request.getId());
		requestIdToRequest.get(request.getId()).setState(RequestState.FAILED);
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

	public void setIdentityPlugin(IdentityPlugin identityPlugin) {
		this.identityPlugin = identityPlugin;
	}

	public IdentityPlugin getIdentityPlugin() {
		return identityPlugin;
	}

	public Request getRequestDetails(String authToken, String requestId) {
		checkUserToken(authToken);
		checkRequestId(authToken, requestId);
		return requestIdToRequest.get(requestId);
	}

	public List<Request> newRequests(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		checkUserToken(authToken);

		String user = getIdentityPlugin().getUser(authToken);

		if (userToRequestIds.get(user) == null) {
			userToRequestIds.put(user, new RequestsBox());
		}

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, authToken, "", RequestState.OPEN, categories,
					xOCCIAtt);
			currentRequests.add(request);

			userToRequestIds.get(user).addOpen(request.getId());
			requestIdToRequest.put(request.getId(), request);
		}
		return currentRequests;
	}

	private void submitRemoteRequest(Request request) {
		// TODO Auto-generated method stub

	}

	private void submitLocalRequest(Request request) {
		// Removing all xOCCI Attribute specific to request type
		Map<String, String> xOCCIAtt = request.getxOCCIAtt();
		for (String keyAttributes : RequestAttribute.getValues()) {
			xOCCIAtt.remove(keyAttributes);
		}
		String instanceLocation = computePlugin.requestInstance(request.getAuthToken(),
				request.getCategories(), xOCCIAtt);
		instanceLocation = instanceLocation.replace(HeaderUtils.X_OCCI_LOCATION, "").trim();
		updateStateToFulfilled(request.getId(), instanceLocation);
	}

	private void updateStateToFulfilled(String requestId, String instanceLocation) {
		requestIdToRequest.get(requestId).setInstanceId(instanceLocation);
		requestIdToRequest.get(requestId).setState(RequestState.FULFILLED);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		checkUserToken(authToken);
		String user = getIdentityPlugin().getUser(authToken);

		List<Request> requests = new ArrayList<Request>();
		if (userToRequestIds.get(user) != null) {
			for (String requestId : userToRequestIds.get(user).getAllRequestIds()) {
				requests.add(requestIdToRequest.get(requestId));
			}
		}
		return requests;
	}

	public void removeAllRequests(String authToken) {
		checkUserToken(authToken);
		String user = getIdentityPlugin().getUser(authToken);

		if (userToRequestIds.get(user) != null) {
			for (String requestId : userToRequestIds.get(user).getAllRequestIds()) {
				requestIdToRequest.remove(requestId);
			}
			userToRequestIds.remove(user);
		}
	}

	public void removeRequest(String authToken, String requestId) {
		checkUserToken(authToken);
		checkRequestId(authToken, requestId);
		String user = getIdentityPlugin().getUser(authToken);

		userToRequestIds.get(user).remove(requestId);
		requestIdToRequest.remove(requestId);
	}

	private void checkRequestId(String authToken, String requestId) {
		String user = getIdentityPlugin().getUser(authToken);

		if (userToRequestIds.get(user) == null || !userToRequestIds.get(user).contains(requestId)) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	private void checkUserToken(String authToken) {
		if (!identityPlugin.isValidToken(authToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
}
