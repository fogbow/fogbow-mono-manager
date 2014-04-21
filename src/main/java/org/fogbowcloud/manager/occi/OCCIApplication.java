package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class OCCIApplication extends Application {

	private IdentityPlugin identityPlugin;
	private ComputePlugin computePlugin;
	
	private RequestRepository requestRepository = new RequestRepository();
	
	private final Timer timer = new Timer();
	protected static final long PERIOD = 50;

	private static final Logger LOGGER = Logger.getLogger(OCCIApplication.class);

	public OCCIApplication() {
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
		for (Request request : requestRepository.get(RequestState.OPEN)) {
			// TODO before submit request we have to check
			try {
				submitLocalRequest(request);
				request.setState(RequestState.FULFILLED);
			} catch (OCCIException e) {
				if (e.getStatus().equals(ErrorType.BAD_REQUEST)
						&& e.getMessage().contains(
								ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
					submitRemoteRequest(request); // FIXME submit more than
													// one at same time
				} else {
					// TODO set state to fail?
					request.setState(RequestState.FAILED);
//					throw e;
				}
			}
		}
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
		return requestRepository.get(requestId);
	}

	public List<Request> createRequests(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		checkUserToken(authToken);

		String user = getIdentityPlugin().getUser(authToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, authToken, "", RequestState.OPEN, categories,
					xOCCIAtt);
			currentRequests.add(request);

			requestRepository.addRequest(user, request);
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
		request.setInstanceId(instanceLocation);
		request.setState(RequestState.FULFILLED);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		checkUserToken(authToken);
		String user = getIdentityPlugin().getUser(authToken);
		return requestRepository.getByUser(user);
	}

	public void removeAllRequests(String authToken) {
		checkUserToken(authToken);
		String user = getIdentityPlugin().getUser(authToken);
		requestRepository.removeByUser(user);
	}

	public void removeRequest(String authToken, String requestId) {
		checkUserToken(authToken);
		checkRequestId(authToken, requestId);
		requestRepository.remove(requestId);
	}

	private void checkRequestId(String authToken, String requestId) {
		String user = getIdentityPlugin().getUser(authToken);
		if (requestRepository.get(user, requestId) == null) {
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	private void checkUserToken(String authToken) {
		if (!identityPlugin.isValidToken(authToken)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}
}
