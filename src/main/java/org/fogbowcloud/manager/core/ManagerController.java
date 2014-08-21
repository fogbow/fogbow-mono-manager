package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.ssh.DefaultSSHTunnel;
import org.fogbowcloud.manager.core.ssh.SSHTunnel;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.jamppa.component.PacketSender;
import org.restlet.Response;

public class ManagerController {

	private static final Logger LOGGER = Logger.getLogger(ManagerController.class);
	public static final long DEFAULT_SCHEDULER_PERIOD = 30000; // 30 seconds
	private static final long DEFAULT_TOKEN_UPDATE_PERIOD = 300000; // 5 minutes
	private static final long DEFAULT_INSTANCE_MONITORING_PERIOD = 120000; // 2
																			// minutes

	private final ManagerTimer requestSchedulerTimer;
	private final ManagerTimer tokenUpdaterTimer;
	private final ManagerTimer instanceMonitoringTimer;

	private Token federationUserToken;
	private List<FederationMember> members = new LinkedList<FederationMember>();
	private RequestRepository requests = new RequestRepository();
	private FederationMemberPicker memberPicker = new RoundRobinMemberPicker();

	private AuthorizationPlugin authorizationPlugin;
	private ComputePlugin computePlugin;
	private IdentityPlugin localIdentityPlugin;
	private IdentityPlugin federationIdentityPlugin;
	private Properties properties;
	private PacketSender packetSender;
	private FederationMemberValidator validator = new DefaultMemberValidator();

	private DateUtils dateUtils = new DateUtils();
	private SSHTunnel sshTunnel = new DefaultSSHTunnel();

	public ManagerController(Properties properties) {
		this(properties, Executors.newScheduledThreadPool(10));
	}

	public ManagerController(Properties properties, ScheduledExecutorService executor) {
		if (properties == null) {
			throw new IllegalArgumentException();
		}
		this.properties = properties;
		this.requestSchedulerTimer = new ManagerTimer(executor);
		this.tokenUpdaterTimer = new ManagerTimer(executor);
		this.instanceMonitoringTimer = new ManagerTimer(executor);
	}

	public void setSSHTunnel(SSHTunnel sshTunnel) {
		this.sshTunnel = sshTunnel;
	}

	public void setAuthorizationPlugin(AuthorizationPlugin authorizationPlugin) {
		this.authorizationPlugin = authorizationPlugin;
	}
	
	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
	}

	public void setLocalIdentityPlugin(IdentityPlugin identityPlugin) {
		this.localIdentityPlugin = identityPlugin;
	}

	public void setFederationIdentityPlugin(IdentityPlugin federationIdentityPlugin) {
		this.federationIdentityPlugin = federationIdentityPlugin;
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
		resourcesInfo.setId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));
		return resourcesInfo;
	}

	public String getUser(String accessId) {
		Token token = getTokenFromFederationIdP(accessId);
		if (token == null) {
			return null;
		}
		return token.getUser();
	}

	public List<Request> getRequestsFromUser(String accessId) {
		String user = getUser(accessId);
		return requests.getByUser(user);
	}

	public void removeAllRequests(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing all requests of user: " + user);
		requests.removeByUser(user);
	}

	public void removeRequest(String accessId, String requestId) {
		LOGGER.debug("Removing requestId: " + requestId);
		checkRequestId(accessId, requestId);
		requests.remove(requestId);
	}

	private void checkRequestId(String accessId, String requestId) {
		String user = getUser(accessId);
		if (requests.get(user, requestId) == null) {
			LOGGER.debug("User " + user + " does not have requesId " + requestId);
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String accessId) {
		LOGGER.debug("Getting instances of token " + accessId);
		List<Instance> instances = new ArrayList<Instance>();
		for (Request request : requests.getByUser(getUser(accessId))) {
			String instanceId = request.getInstanceId();
			LOGGER.debug("InstanceId " + instanceId);
			if (instanceId == null) {
				continue;
			}
			try {
				instances.add(new Instance(request.getInstanceId()));
			} catch (Exception e) {
				LOGGER.warn("Exception thown while getting instance " + instanceId + ".", e);
			}
		}
		return instances;
	}

	public Instance getInstance(String accessId, String instanceId) {
		Request request = getRequestForInstance(accessId, instanceId);
		return getInstance(request);
	}

	private Instance getInstance(Request request) {
		Instance instance = null;
		if (isLocal(request)) {
			LOGGER.debug(request.getInstanceId()
					+ " is local, getting its information in the local cloud.");
			instance = this.computePlugin.getInstance(request.getToken().getAccessId(),
					request.getInstanceId());
			String sshAddress = request.getAttValue(DefaultSSHTunnel.SSH_PUBLIC_ADDRESS_ATT);
			if (sshAddress != null) {
				instance.addAttribute(DefaultSSHTunnel.SSH_PUBLIC_ADDRESS_ATT, sshAddress);
			}
		} else {
			LOGGER.debug(request.getInstanceId() + " is remote, going out to "
					+ request.getMemberId() + " to get its information.");
			instance = getRemoteInstance(request);
		}
		return instance;
	}

	private Instance getRemoteInstance(Request request) {
		return ManagerPacketHelper.getRemoteInstance(request, packetSender);
	}

	public void removeInstances(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing instances of user: " + user);
		for (Request request : requests.getByUser(user)) {
			String instanceId = request.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			removeInstance(accessId, instanceId, request);
		}
	}

	public void removeInstance(String accessId, String instanceId) {
		Request request = getRequestForInstance(accessId, instanceId);
		removeInstance(accessId, instanceId, request);
	}

	private void removeInstance(String accessId, String instanceId, Request request) {
		if (isLocal(request)) {
			this.computePlugin.removeInstance(accessId, instanceId);
		} else {
			removeRemoteInstance(request);
		}
		instanceRemoved(request);
	}

	private void instanceRemoved(Request request) {
		sshTunnel.release(request.getInstanceId());
		request.setInstanceId(null);
		request.setMemberId(null);

		if (request.getState().equals(RequestState.DELETED)) {
			requests.exclude(request.getId());
		} else if (isPersistent(request)) {
			LOGGER.debug("Request: " + request + ", setting state to " + RequestState.OPEN);
			request.setState(RequestState.OPEN);
			if (!requestSchedulerTimer.isScheduled()) {
				triggerRequestScheduler();
			}
		} else {
			LOGGER.debug("Request: " + request + ", setting state to " + RequestState.CLOSED);
			request.setState(RequestState.CLOSED);
		}
	}

	private boolean isPersistent(Request request) {
		return request.getAttValue(RequestAttribute.TYPE.getValue()) != null
				&& request.getAttValue(RequestAttribute.TYPE.getValue()).equals(
						RequestType.PERSISTENT.getValue());
	}

	private void removeRemoteInstance(Request request) {
		ManagerPacketHelper.deleteRemoteInstace(request, packetSender);
	}

	public Request getRequestForInstance(String accessId, String instanceId) {
		String user = getUser(accessId);
		LOGGER.debug("Getting instance " + instanceId + " of user " + user);
		List<Request> userRequests = requests.getAll();
		for (Request request : userRequests) {
			if (instanceId.equals(request.getInstanceId())) {
				if (!request.getToken().getUser().equals(user)) {
					throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
				}
				return request;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	private boolean isLocal(Request request) {
		return request.getMemberId() == null;
	}

	public Request getRequest(String accessId, String requestId) {
		LOGGER.debug("Getting requestId " + requestId);
		checkRequestId(accessId, requestId);
		return requests.get(requestId);
	}

	public FederationMember getFederationMember(String memberId) {
		for (FederationMember member : members) {
			if (member.getResourcesInfo().getId().equals(memberId)) {
				return member;
			}
		}
		if (memberId.equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
			return new FederationMember(getResourcesInfo());
		}
		return null;
	}

	public String createInstanceForRemoteMember(String memberId, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		Integer sshPort = null;
		
		FederationMember member = null;
		try {
			member = getFederationMember(memberId);
		} catch (Exception e) {
		}		
		
		if (!validator.canDonateTo(member)) {
			return null;
		}			
		LOGGER.info("Submiting request with categories: " + categories + " and xOCCIAtt: "
				+ xOCCIAtt + " for remote member.");
		String federationTokenAccessId = getFederationUserToken().getAccessId();
		try {
			sshPort = sshTunnel.create(properties, new Request(null, null, categories, xOCCIAtt));
		} catch (Exception e) {
			LOGGER.warn("Exception while creating ssh tunnel.", e);
			return null;
		}
		try {
			String instanceId = computePlugin.requestInstance(federationTokenAccessId, categories,
					xOCCIAtt);
			if (instanceId != null) {
				sshTunnel.update(instanceId, sshPort);
			} else {
				sshTunnel.release(sshPort);
			}
			return instanceId;
		} catch (OCCIException e) {
			sshTunnel.release(sshPort);
			if (e.getStatus().getCode() == HttpStatus.SC_BAD_REQUEST) {
				return null;
			}
			throw e;
		}
	}

	protected Token getFederationUserToken() {
		if (federationUserToken != null
				&& localIdentityPlugin.isValid(federationUserToken.getAccessId())) {
			return federationUserToken;
		}

		federationUserToken = localIdentityPlugin.createFederationUserToken();
		return federationUserToken;
	}

	public Instance getInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " for remote member.");
		String federationTokenAccessId = getFederationUserToken().getAccessId();
		try {
			Instance instance = computePlugin.getInstance(federationTokenAccessId, instanceId);
			String sshAddress = sshTunnel.getPublicAddress(properties, instanceId);
			if (sshAddress != null) {
				instance.addAttribute(DefaultSSHTunnel.SSH_PUBLIC_ADDRESS_ATT, sshAddress);
			}
			return instance;
		} catch (OCCIException e) {
			LOGGER.warn("Exception while getting instance " + instanceId + " for remote member.", e);
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND) {
				return null;
			}
			throw e;
		}
	}

	public void removeInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Removing instance " + instanceId + " for remote member.");
		String federationTokenAccessId = getFederationUserToken().getAccessId();
		computePlugin.removeInstance(federationTokenAccessId, instanceId);
		sshTunnel.release(instanceId);
	}

	public Token getTokenFromFederationIdP(String accessId) {
		Token token = federationIdentityPlugin.getToken(accessId);
		if (!authorizationPlugin.isAuthorized(token)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
		}
		return token;
	}

	public List<Request> createRequests(String accessId, List<Category> categories,
			Map<String, String> xOCCIAtt) {

		Token userToken = getTokenFromFederationIdP(accessId);
		LOGGER.debug("User Token: " + userToken);
		
		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, userToken,
					new LinkedList<Category>(categories), new HashMap<String, String>(xOCCIAtt));
			LOGGER.info("Created request: " + request);
			currentRequests.add(request);
			requests.addRequest(userToken.getUser(), request);
		}
		if (!requestSchedulerTimer.isScheduled()) {
			triggerRequestScheduler();
		}
		if (!tokenUpdaterTimer.isScheduled()) {
			triggerTokenUpdater();
		}

		return currentRequests;
	}

	protected void triggerInstancesMonitor() {
		String instanceMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY);
		final long instanceMonitoringPeriod = instanceMonitoringPeriodStr == null ? DEFAULT_INSTANCE_MONITORING_PERIOD
				: Long.valueOf(instanceMonitoringPeriodStr);

		instanceMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitorInstances();
			}
		}, 0, instanceMonitoringPeriod);
	}

	protected void monitorInstances() {
		boolean turnOffTimer = true;
		LOGGER.info("Monitoring instances.");

		for (Request request : requests.getAll()) {
			if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED)) {
				turnOffTimer = false;
				try {
					LOGGER.debug("Monitoring instance of request: " + request);
					getInstance(request);
				} catch (OCCIException e) {
					LOGGER.debug("Error while getInstance of " + request.getInstanceId(), e);
					instanceRemoved(requests.get(request.getId()));
				}
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are not requests.");
			instanceMonitoringTimer.cancel();
		}
	}

	private void triggerTokenUpdater() {
		String tokenUpdatePeriodStr = properties
				.getProperty(ConfigurationConstants.TOKEN_UPDATE_PERIOD_KEY);
		final long tokenUpdatePeriod = tokenUpdatePeriodStr == null ? DEFAULT_TOKEN_UPDATE_PERIOD
				: Long.valueOf(tokenUpdatePeriodStr);

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

		LOGGER.info("Checking and updating request token.");

		for (Request request : allRequests) {
			try {
				if (request.getState().notIn(RequestState.CLOSED, RequestState.FAILED)) {
					turnOffTimer = false;
					long validInterval = request.getToken().getExpirationDate().getTime()
							- dateUtils.currentTimeMillis();
					LOGGER.debug("Valid interval of requestId " + request.getId() + " is "
							+ validInterval);
					if (validInterval < 2 * tokenUpdatePeriod) {
						Token newToken = localIdentityPlugin.reIssueToken(request.getToken());
						LOGGER.info("Setting new token " + newToken + " on request "
								+ request.getId());
						requests.get(request.getId()).setToken(newToken);
					}
				}
			} catch (Exception e) {
				LOGGER.error("Exception while checking token.", e);
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are not requests.");
			tokenUpdaterTimer.cancel();
		}
	}

	private boolean createRemoteInstance(Request request) {
		FederationMember member = memberPicker.pick(this);
		if (member == null) {
			return false;
		}
		String memberAddress = member.getResourcesInfo().getId();
		request.setMemberId(memberAddress);

		LOGGER.info("Submiting request " + request + " to member " + memberAddress);

		String remoteInstanceId = ManagerPacketHelper.remoteRequest(request, memberAddress,
				packetSender);
		if (remoteInstanceId == null) {
			return false;
		}

		request.setState(RequestState.FULFILLED);
		request.setInstanceId(remoteInstanceId);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
		return true;
	}

	private boolean createLocalInstance(Request request) {
		request.setMemberId(null);
		String instanceId = null;
		Integer port = null;

		LOGGER.info("Submiting local request " + request);

		try {
			try {
				port = sshTunnel.create(properties, request);
			} catch (Exception e) {
				LOGGER.warn("Exception while creating ssh tunnel.", e);
				request.setState(RequestState.FAILED);
				return false;
			}		
			
			instanceId = computePlugin.requestInstance(request.getToken().getAccessId(),
					request.getCategories(), request.getxOCCIAtt());
			if (instanceId == null) {
				sshTunnel.release(port);
				return false;
			}
			sshTunnel.update(instanceId, port);
		} catch (OCCIException e) {
			if (port != null) {
				sshTunnel.release(port);
			}
			int statusCode = e.getStatus().getCode();
			if (statusCode == HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE
					|| statusCode == HttpStatus.SC_UNAUTHORIZED) {
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
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
		return true;
	}

	private void triggerRequestScheduler() {
		String schedulerPeriodStr = properties
				.getProperty(ConfigurationConstants.SCHEDULER_PERIOD_KEY);
		long schedulerPeriod = schedulerPeriodStr == null ? DEFAULT_SCHEDULER_PERIOD : Long
				.valueOf(schedulerPeriodStr);

		requestSchedulerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkAndSubmitOpenRequests();
			}
		}, 0, schedulerPeriod);
	}

	protected void checkAndSubmitOpenRequests() {
		boolean allFulfilled = true;
		LOGGER.debug("Checking and submiting requests.");

		for (Request request : requests.get(RequestState.OPEN)) {
			Map<String, String> xOCCIAtt = request.getxOCCIAtt();
			if (request.isIntoValidPeriod()) {
				for (String keyAttributes : RequestAttribute.getValues()) {
					xOCCIAtt.remove(keyAttributes);
				}
				allFulfilled &= createLocalInstance(request)
						|| createLocalInstanceWithFederationUser(request)
						|| createRemoteInstance(request);
			} else if (request.isExpired()) {
				request.setState(RequestState.CLOSED);
			} else {
				allFulfilled = false;
			}
		}
		if (allFulfilled) {
			LOGGER.info("All request fulfilled.");
			requestSchedulerTimer.cancel();
		}
	}

	private boolean createLocalInstanceWithFederationUser(Request request) {
		request.setMemberId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));

		LOGGER.info("Submiting request " + request + " with federation user locally.");

		String remoteInstanceId = null;
		try {
			remoteInstanceId = createInstanceForRemoteMember(properties.getProperty("xmpp_jid"),
					request.getCategories(), request.getxOCCIAtt());
		} catch (Exception e) {
			LOGGER.info("Could not create instance with federation user locally." + e);
		}

		if (remoteInstanceId == null) {
			return false;
		}

		request.setState(RequestState.FULFILLED);
		request.setInstanceId(remoteInstanceId);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
		return true;
	}

	public void setPacketSender(PacketSender packetSender) {
		this.packetSender = packetSender;
	}

	public void setRequests(RequestRepository requests) {
		this.requests = requests;
	}

	public Token getToken(Map<String, String> attributesToken) {
		return localIdentityPlugin.createToken(attributesToken);
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

	public void setValidator(FederationMemberValidator validator) {
		this.validator = validator;
	}

	public List<Resource> getAllResouces(String accessId) {
		Token userToken = getTokenFromFederationIdP(accessId);
		LOGGER.debug("User Token: " + userToken);
		return ResourceRepository.getInstance().getAll();
	}

	public void bypass(org.restlet.Request request, Response response) {
		LOGGER.debug("Bypassing request: " + request);
		computePlugin.bypass(request, response);
	}
}
