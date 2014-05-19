package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.ssh.DefaultSSHTunnel;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.fogbowcloud.manager.xmpp.core.model.DateUtils;
import org.jamppa.component.PacketSender;

public class ManagerController {

	private static final Logger LOGGER = Logger
			.getLogger(ManagerController.class);
	public static final long DEFAULT_SCHEDULER_PERIOD = 30000; // 30 seconds
	private static final long DEFAULT_TOKEN_UPDATE_PERIOD = 300000; // 5 minutes
	private static final long DEFAULT_INSTANCE_MONITORING_PERIOD = 120000; // 2
																			// minutes

	private boolean scheduled = false;
	private boolean tokenUpdatingOn = false;
	private boolean instanceMonitoringOn = false;
	private Timer requestSchedulerTimer;
	private Timer tokenUpdaterTimer;
	private Timer instanceMonitoringTimer;

	private Token tokenMemberLocal;
	private List<FederationMember> members = new LinkedList<FederationMember>();
	private RequestRepository requests = new RequestRepository();
	private FederationMemberPicker memberPicker = new RoundRobinMemberPicker();

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private Properties properties;
	private PacketSender packetSender;
	private FederationMemberValidator validator = new DefaultFederationMemberValidator();

	private DateUtils dateUtils = new DateUtils();
	private SSHTunnel sshTunnel = new DefaultSSHTunnel();

	public ManagerController(Properties properties) {
		this.properties = properties;
		if (properties == null) {
			throw new IllegalArgumentException();
		}
	}

	public void setSSHTunnel(SSHTunnel sshTunnel) {
		this.sshTunnel = sshTunnel;
	}

	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
	}

	public void setIdentityPlugin(IdentityPlugin identityPlugin) {
		this.identityPlugin = identityPlugin;
	}

	public void updateMembers(List<FederationMember> members) {
		LOGGER.debug("Updating members: " + members);
		if (members == null) {
			throw new IllegalArgumentException();
		}
		this.members = members;
	}

	public List<FederationMember> getMembers() {
		return members;
	}

	public void setMembers(List<FederationMember> members) {
		this.members = members;
	}

	public ResourcesInfo getResourcesInfo() {
		Token token = getFederationUserToken();
		ResourcesInfo resourcesInfo = computePlugin.getResourcesInfo(token);
		resourcesInfo.setId(properties.getProperty("xmpp_jid"));
		return resourcesInfo;
	}

	public String getUser(String authToken) {
		return identityPlugin.getUser(authToken);
	}

	public List<Request> getRequestsFromUser(String authToken) {
		String user = getUser(authToken);
		return requests.getByUser(user);
	}

	public void removeAllRequests(String authToken) {
		String user = getUser(authToken);
		LOGGER.debug("Removing all requests of user: " + user);
		requests.removeByUser(user);
	}

	public void removeRequest(String authToken, String requestId) {
		LOGGER.debug("Removing requestId: " + requestId);
		checkRequestId(authToken, requestId);
		requests.remove(requestId);
	}

	private void checkRequestId(String authToken, String requestId) {
		String user = getUser(authToken);
		if (requests.get(user, requestId) == null) {
			LOGGER.debug("User " + user + " does not have requesId "
					+ requestId);
			throw new OCCIException(ErrorType.NOT_FOUND,
					ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String authToken) {
		LOGGER.debug("Getting instances of token " + authToken);
		List<Instance> instances = new ArrayList<Instance>();
		for (Request request : requests.getByUser(getUser(authToken))) {
			String instanceId = request.getInstanceId();
			LOGGER.debug("InstanceId " + instanceId);
			if (instanceId == null) {
				continue;
			}
			try {

				instances.add(getInstance(request));
			} catch (Exception e) {
				LOGGER.warn("Exception thown while getting instance "
						+ instanceId + ".", e);
			}
		}
		return instances;
	}

	public Instance getInstance(String authToken, String instanceId) {
		Request request = getRequestFromInstance(authToken, instanceId);
		return getInstance(request);
	}

	private Instance getInstance(Request request) {
		Instance instance = null;
		if (isLocal(request)) {
			LOGGER.debug(request.getInstanceId()
					+ " is local, getting its information in the local cloud.");
			instance = this.computePlugin.getInstance(request.getToken()
					.getAccessId(), request.getInstanceId());
		} else {
			LOGGER.debug(request.getInstanceId() + " is remote, going out to "
					+ request.getMemberId() + " to get its information.");
			instance = getRemoteInstance(request);
		}
		String sshAddress = request
				.getAttValue(DefaultSSHTunnel.SSH_ADDRESS_ATT);
		if (sshAddress != null) {
			instance.addAttribute(DefaultSSHTunnel.SSH_ADDRESS_ATT, sshAddress);
		}
		return instance;
	}

	private Instance getRemoteInstance(Request request) {
		return ManagerPacketHelper.getRemoteInstance(request, packetSender);
	}

	public void removeInstances(String authToken) {
		String user = getUser(authToken);
		LOGGER.debug("Removing instances of user: " + user);
		for (Request request : requests.getByUser(user)) {
			String instanceId = request.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			removeInstance(authToken, instanceId, request);
		}
	}

	public void removeInstance(String authToken, String instanceId) {
		Request request = getRequestFromInstance(authToken, instanceId);
		removeInstance(authToken, instanceId, request);
	}

	private void removeInstance(String authToken, String instanceId,
			Request request) {
		sshTunnel.release(request);
		if (isLocal(request)) {
			this.computePlugin.removeInstance(authToken, instanceId);
		} else {
			removeRemoteInstance(request);
		}
		request.setInstanceId(null);
		request.setMemberId(null);
		updateRequestState(request);
	}

	private void updateRequestState(Request request) {
		if (request.getAttValue(RequestAttribute.TYPE.getValue()) != null
				&& request.getAttValue(RequestAttribute.TYPE.getValue())
						.equals(RequestType.PERSISTENT.getValue())) {
			request.setState(RequestState.OPEN);
			if (!scheduled) {
				scheduleRequests();
			}
		} else {
			request.setState(RequestState.CLOSED);
		}
	}

	private void removeRemoteInstance(Request request) {
		ManagerPacketHelper.deleteRemoteInstace(request, packetSender);
	}

	public Request getRequestFromInstance(String authToken, String instanceId) {
		String user = getUser(authToken);
		LOGGER.debug("Getting instance " + instanceId + " of user " + user);
		List<Request> userRequests = requests.getAll();
		for (Request request : userRequests) {
			if (instanceId.equals(request.getInstanceId())) {
				if (!request.getUser().equals(user)) {
					throw new OCCIException(ErrorType.UNAUTHORIZED,
							ResponseConstants.UNAUTHORIZED);
				}
				return request;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND,
				ResponseConstants.NOT_FOUND);
	}

	private boolean isLocal(Request request) {
		return request.getMemberId() == null;
	}

	public Request getRequest(String authToken, String requestId) {
		LOGGER.debug("Getting requestId " + requestId);
		checkRequestId(authToken, requestId);
		return requests.get(requestId);
	}

	public FederationMember getFederationMember(String memberId) {
		for (FederationMember member : members) {
			if (member.getResourcesInfo().getId().equals(memberId)) {
				return member;
			}
		}
		return null;
	}

	public String submitRequestForRemoteMember(String memberId,
			List<Category> categories, Map<String, String> xOCCIAtt) {

		if (!validator.canDonateTo(getFederationMember(memberId))) {
			return null;
		}
		LOGGER.info("Submiting request with categories: " + categories
				+ " and xOCCIAtt: " + xOCCIAtt + " for remote member.");
		String token = getFederationUserToken().getAccessId();// (OCCIHeaders.X_TOKEN_ACCESS_ID);
		try {
			return computePlugin.requestInstance(token, categories, xOCCIAtt);
		} catch (OCCIException e) {
			if (e.getStatus().getCode() == HttpStatus.SC_BAD_REQUEST) {
				return null;
			}
			throw e;
		}
	}

	protected Token getFederationUserToken() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		String username = properties.getProperty("federation_user_name");
		String password = properties.getProperty("federation_user_password");
		String tenantName = properties
				.getProperty("federation_user_tenant_name");
		tokenAttributes.put(OCCIHeaders.X_TOKEN_USER, username);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_PASS, password);
		tokenAttributes.put(OCCIHeaders.X_TOKEN_TENANT_NAME, tenantName);

		if (tokenMemberLocal == null || tokenMemberLocal.isExpiredToken()) {
			Token token = identityPlugin.getToken(tokenAttributes);
			this.tokenMemberLocal = token;
		}
		return this.tokenMemberLocal;
	}

	public Instance getInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " for remote member.");
		String token = getFederationUserToken().getAccessId();// get(OCCIHeaders.X_TOKEN_ACCESS_ID);
		try {
			return computePlugin.getInstance(token, instanceId);
		} catch (OCCIException e) {
			LOGGER.warn("Exception while getting instance " + instanceId
					+ " for remote member.", e);
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND) {
				return null;
			}
			throw e;
		}
	}

	public void removeInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Removing instance " + instanceId + " for remote member.");
		String token = getFederationUserToken().getAccessId();// (OCCIHeaders.X_TOKEN_ACCESS_ID);
		computePlugin.removeInstance(token, instanceId);
	}

	public List<Request> createRequests(String authToken,
			List<Category> categories, Map<String, String> xOCCIAtt) {
		String user = getUser(authToken);

		Token userToken = identityPlugin.getToken(authToken);
		LOGGER.debug("User Token: " + userToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt
				.get(RequestAttribute.INSTANCE_COUNT.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, userToken, user,
					categories, xOCCIAtt);
			try {
				sshTunnel.create(properties, request);
			} catch (Exception e) {
				LOGGER.warn("Exception while creating ssh tunnel.", e);
				request.setState(RequestState.FAILED);
			}
			LOGGER.info("Created request: " + request);
			currentRequests.add(request);
			requests.addRequest(user, request);
		}
		if (!scheduled) {
			scheduleRequests();
		}
		if (!tokenUpdatingOn) {
			turnOnTokenUpdating();
		}

		return currentRequests;
	}

	protected void turnOnInstancesMonitoring() {
		instanceMonitoringOn = true;
		String instanceMonitoringPeriodStr = properties
				.getProperty("instance_monitoring_period");
		final long instanceMonitoringPeriod = instanceMonitoringPeriodStr == null ? DEFAULT_INSTANCE_MONITORING_PERIOD
				: Long.valueOf(instanceMonitoringPeriodStr);

		instanceMonitoringTimer = new Timer();
		instanceMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitorInstances();
			}
		}, 0, instanceMonitoringPeriod);
	}

	protected void monitorInstances() {
		boolean turnOffTimer = true;

		// monitoring fulfilled requests
		for (Request request : requests.get(RequestState.FULFILLED)) {
			turnOffTimer = false;
			try {
				getInstance(request);
			} catch (OCCIException e) {
				updateRequestState(requests.get(request.getId()));
			}
		}

		// monitoring deleted requests
		for (Request request : requests.get(RequestState.DELETED)) {
			turnOffTimer = false;
			try {
				getInstance(request);
			} catch (OCCIException e) {
				requests.excluding(request.getId());
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are not requests.");
			instanceMonitoringTimer.cancel();
			instanceMonitoringOn = false;
		}
	}

	private void turnOnTokenUpdating() {
		tokenUpdatingOn = true;
		String tokenUpdatePeriodStr = properties
				.getProperty("token_update_period");
		final long tokenUpdatePeriod = tokenUpdatePeriodStr == null ? DEFAULT_TOKEN_UPDATE_PERIOD
				: Long.valueOf(tokenUpdatePeriodStr);

		tokenUpdaterTimer = new Timer();
		tokenUpdaterTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkAndUpdateRequestToken(tokenUpdatePeriod);
			}
		}, 0, tokenUpdatePeriod);
	}

	protected void checkAndUpdateRequestToken(long tokenUpdatePeriod) {
		List<Request> allRequests = requests.getAll();
		boolean turnOffTimer = true;
		List<String> usersUpdated = new ArrayList<String>();
		for (Request request : allRequests) {
			boolean isUserUpdated = false;
			for (String user : usersUpdated) {
				if (user.equals(request.getUser())) {
					isUserUpdated = true;
					break;
				}
			}

			if (!request.getState().equals(RequestState.CLOSED)
					&& !request.getState().equals(RequestState.FAILED)
					&& !isUserUpdated) {
				turnOffTimer = false;
				long validInterval = request.getToken().getExpirationDate()
						.getTime()
						- dateUtils.currentTimeMillis();
				if (validInterval < 2 * tokenUpdatePeriod) {
					Token newToken = identityPlugin.updateToken(request
							.getToken());
					requests.get(request.getId()).setToken(newToken);

					for (Request requestByUser : requests.getByUser(request
							.getUser())) {
						if (!requestByUser.getState().equals(
								RequestState.CLOSED)
								&& !requestByUser.getState().equals(
										RequestState.FAILED)) {
							requests.get(requestByUser.getId()).getToken()
									.setAccessId(newToken.getAccessId());
						}
					}

					usersUpdated.add(request.getUser());
				}
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are not requests.");
			tokenUpdaterTimer.cancel();
			tokenUpdatingOn = false;
		}
	}

	private boolean submitRemoteRequest(Request request) {
		FederationMember member = memberPicker.pick(this);
		if (member == null) {
			return false;
		}
		String memberAddress = member.getResourcesInfo().getId();
		request.setMemberId(memberAddress);

		LOGGER.info("Submiting request " + request + " to member "
				+ memberAddress);

		String remoteInstanceId = ManagerPacketHelper.remoteRequest(request,
				memberAddress, packetSender);
		if (remoteInstanceId == null) {
			return false;
		}

		request.setState(RequestState.FULFILLED);
		request.setInstanceId(remoteInstanceId);
		if (!instanceMonitoringOn) {
			turnOnInstancesMonitoring();
		}
		return true;
	}

	private boolean submitLocalRequest(Request request) {
		request.setMemberId(null);
		String instanceId = null;

		LOGGER.info("Submiting local request " + request);

		try {
			instanceId = computePlugin.requestInstance(request.getToken()
					.getAccessId(), request.getCategories(), request
					.getxOCCIAtt());
		} catch (OCCIException e) {
			if (e.getStatus().getCode() == HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE) {
				LOGGER.warn("Request failed locally for quota exceeded.", e);
				return false;
			} else {
				// TODO Think this through...
				request.setState(RequestState.FAILED);
				LOGGER.warn("Request failed locally for an unknown reason.", e);
				return true;
			}
		}

		request.setInstanceId(instanceId);
		request.setState(RequestState.FULFILLED);
		LOGGER.debug("Fulfilled Request: " + request);
		if (!instanceMonitoringOn) {
			turnOnInstancesMonitoring();
		}
		return true;
	}

	private void scheduleRequests() {
		scheduled = true;
		String schedulerPeriodStr = properties.getProperty("scheduler_period");
		long schedulerPeriod = schedulerPeriodStr == null ? DEFAULT_SCHEDULER_PERIOD
				: Long.valueOf(schedulerPeriodStr);

		requestSchedulerTimer = new Timer();
		requestSchedulerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkAndSubmitOpenRequests();
			}
		}, 0, schedulerPeriod);
	}

	private void checkAndSubmitOpenRequests() {
		boolean allFulfilled = true;
		LOGGER.debug("Checking and submiting requests.");

		for (Request request : requests.get(RequestState.OPEN)) {
			Map<String, String> xOCCIAtt = request.getxOCCIAtt();
			if (request.isIntoValidPeriod()) {
				for (String keyAttributes : RequestAttribute.getValues()) {
					xOCCIAtt.remove(keyAttributes);
				}
				allFulfilled &= submitLocalRequest(request)
						|| submitRemoteRequest(request);
			} else if (request.isExpired()) {
				request.setState(RequestState.CLOSED);
			} else {
				allFulfilled = false;
			}
		}
		if (allFulfilled) {
			LOGGER.info("All request fulfilled.");
			requestSchedulerTimer.cancel();
			scheduled = false;
		}
	}

	public void setPacketSender(PacketSender packetSender) {
		this.packetSender = packetSender;
	}

	public void setRequests(RequestRepository requests) {
		this.requests = requests;
	}

	public Token getToken(Map<String, String> attributesToken) {
		return identityPlugin.getToken(attributesToken);
	}

	public Properties getProperties() {
		return properties;
	}

	public void setDateUtils(DateUtils dateUtils) {
		this.dateUtils = dateUtils;
	}

	public FederationMemberValidator getValidator() {
		return validator;
	}
}
