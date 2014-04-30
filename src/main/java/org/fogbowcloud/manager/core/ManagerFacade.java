package org.fogbowcloud.manager.core;

import java.util.ArrayList;
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
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.jamppa.component.PacketSender;

public class ManagerFacade {

	private static final Logger LOGGER = Logger.getLogger(ManagerFacade.class);
	public static final long DEFAULT_SCHEDULER_PERIOD = 30000;

	private boolean scheduled = false;
	private final Timer timer = new Timer();

	private List<FederationMember> members = new LinkedList<FederationMember>();
	private RequestRepository requests = new RequestRepository();
	private FederationMemberPicker memberPicker = new RoundRobinMemberPicker();

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private Properties properties;
	private PacketSender packetSender;

	private SSHTunnel sshTunnel = new DefaultSSHTunnel();

	public ManagerFacade(Properties properties) {
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
		String token = getFederationUserToken();
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
		requests.removeByUser(user);
	}

	public void removeRequest(String authToken, String requestId) {
		checkRequestId(authToken, requestId);
		requests.remove(requestId);
	}

	private void checkRequestId(String authToken, String requestId) {
		String user = getUser(authToken);
		if (requests.get(user, requestId) == null) {
			LOGGER.warn("User " + user + " does not have requesId " + requestId);
			LOGGER.warn("Throwing OCCIException at checkRequesId: " + ResponseConstants.NOT_FOUND);
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String authToken) {
		List<Instance> instances = new ArrayList<Instance>();
		for (Request request : requests.getByUser(getUser(authToken))) {
			String instanceId = request.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			instances.add(getInstance(authToken, instanceId, request));
		}
		return instances;
	}

	public Instance getInstance(String authToken, String instanceId) {
		Request request = getRequestFromInstance(authToken, instanceId);
		return getInstance(authToken, instanceId, request);
	}

	private Instance getInstance(String authToken, String instanceId, Request request) {
		Instance instance = null;
		if (isLocal(request)) {
			instance = this.computePlugin.getInstance(authToken, instanceId);
		} else {
			instance = getRemoteInstance(request);
		}
		String sshAddress = request.getAttValue(DefaultSSHTunnel.SSH_ADDRESS_ATT);
		if (sshAddress != null) {
			instance.addAttribute(DefaultSSHTunnel.SSH_ADDRESS_ATT, sshAddress);
		}

		return instance;
	}

	private Instance getRemoteInstance(Request request) {
		return ManagerPacketHelper.getRemoteInstance(request, packetSender);
	}

	public void removeInstances(String authToken) {
		for (Request request : requests.getByUser(getUser(authToken))) {
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

	private void removeInstance(String authToken, String instanceId, Request request) {
		sshTunnel.release(request);
		if (isLocal(request)) {
			this.computePlugin.removeInstance(authToken, instanceId);
		} else {
			removeRemoteInstance(request);
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
					LOGGER.warn("Throwing OCCIException at getRequestFromInstance: " + ResponseConstants.UNAUTHORIZED);
					throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
				}
				return request;
			}
		}
		LOGGER.warn("Throwing OCCIException at getRequestFromInstance: " + ResponseConstants.NOT_FOUND);
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	private boolean isLocal(Request request) {
		return request.getMemberId() == null;
	}

	public Request getRequest(String authToken, String requestId) {
		checkRequestId(authToken, requestId);
		return requests.get(requestId);
	}

	public String submitRequestForRemoteMember(List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String token = getFederationUserToken();
		try {
			return computePlugin.requestInstance(token, categories, xOCCIAtt);
		} catch (OCCIException e) {
			if (e.getStatus().getCode() == HttpStatus.SC_BAD_REQUEST) {
				return null;
			}
			throw e;
		}
	}

	// TODO Think about always get new federation user token or store a valid
	// one.
	private String getFederationUserToken() {
		String token = identityPlugin.getToken(properties.getProperty("federation_user_name"),
				properties.getProperty("federation_user_password"));
		return token;
	}

	public Instance getInstanceForRemoteMember(String instanceId) {
		String token = getFederationUserToken();
		try {
			return computePlugin.getInstance(token, instanceId);
		} catch (OCCIException e) {
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND) {
				return null;
			}
			throw e;
		}
	}

	public void removeInstanceForRemoteMember(String instanceId) {
		String token = getFederationUserToken();
		computePlugin.removeInstance(token, instanceId);
	}

	public List<Request> createRequests(String authToken, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		String user = getUser(authToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, authToken, user, categories, xOCCIAtt);
			try {
				sshTunnel.create(properties, request);
			} catch (Exception e) {
				LOGGER.warn("Exception while creating ssh tunnel.", e);
				request.setState(RequestState.FAILED);
			}
			LOGGER.debug("Updated request with tunnel properties : " + request);
			currentRequests.add(request);
			requests.addRequest(user, request);
		}
		if (!scheduled) {
			scheduleRequests();
		}

		return currentRequests;
	}

	private boolean submitRemoteRequest(Request request) {
		FederationMember member = memberPicker.pick(getMembers());
		String memberAddress = member.getResourcesInfo().getId();
		request.setMemberId(memberAddress);

		LOGGER.info("Submiting request " + request.getId() + " to member " + memberAddress);

		String remoteInstanceId = ManagerPacketHelper.remoteRequest(request, memberAddress,
				packetSender);
		if (remoteInstanceId == null) {
			return false;
		}

		request.setState(RequestState.FULFILLED);
		request.setInstanceId(remoteInstanceId);
		return true;
	}

	private boolean submitLocalRequest(Request request) {
		request.setMemberId(null);
		String instanceLocation = null;
		
		LOGGER.info("Submiting local request " + request);
		
		try {
			instanceLocation = computePlugin.requestInstance(request.getAuthToken(),
					request.getCategories(), request.getxOCCIAtt());
		} catch (OCCIException e) {
			if (e.getStatus().equals(ErrorType.BAD_REQUEST)
					&& e.getMessage().contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
				LOGGER.warn("Request failed locally for quota exceeded.", e);
				return false;
			} else {
				// TODO Think this through...
				request.setState(RequestState.FAILED);
				LOGGER.warn("Request failed locally for an unknown reason.", e);
				return true;
			}
		}

		instanceLocation = instanceLocation.replace(HeaderUtils.X_OCCI_LOCATION, "").trim();
		request.setInstanceId(instanceLocation);
		request.setState(RequestState.FULFILLED);
		LOGGER.debug("Fulfilled Request: " + request);
		return true;
	}

	private void scheduleRequests() {
		scheduled = true;
		String schedulerPeriodStr = properties.getProperty("scheduler_period");
		long schedulerPeriod = schedulerPeriodStr == null ? DEFAULT_SCHEDULER_PERIOD : Long
				.valueOf(schedulerPeriodStr);

		timer.scheduleAtFixedRate(new TimerTask() {
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
			for (String keyAttributes : RequestAttribute.getValues()) {
				xOCCIAtt.remove(keyAttributes);
			}
			allFulfilled &= submitLocalRequest(request) || submitRemoteRequest(request);
		}
		if (allFulfilled) {
			LOGGER.info("All request fulfilled.");
			timer.cancel();
			scheduled = false;
		}
	}

	public void setPacketSender(PacketSender packetSender) {
		this.packetSender = packetSender;
	}

	public void setRequests(RequestRepository requests) {
		this.requests = requests;
	}

	public String getToken(String username, String password) {
		return identityPlugin.getToken(username, password);
	}
}
