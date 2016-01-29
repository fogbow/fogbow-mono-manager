package org.fogbowcloud.manager.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.mail.MessagingException;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberAuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.PrioritizationPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.plugins.util.SshClientPool;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.OrderDataStore;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Response;

public class ManagerController {

	private static final String SSH_SERVICE_NAME = "ssh";
	protected final int MAX_REQUESTS_PER_THREAD = 25;

	public static final String DEFAULT_COMMON_SSH_USER = "fogbow";

	private static final Logger LOGGER = Logger.getLogger(ManagerController.class);

	private static final int DEFAULT_MAX_WHOISALIVE_MANAGER_COUNT = 100;
	private static final long DEFAULT_BD_UPDATE_PERIOD = 300000; // 5 minute
	private static final long DEFAULT_SCHEDULER_PERIOD = 30000; // 30 seconds
	protected static final int DEFAULT_ASYNC_REQUEST_WAITING_INTERVAL = 300000; // 5
																				// minutes
	private static final long DEFAULT_INSTANCE_MONITORING_PERIOD = 120000; // 2
																			// minutes
	private static final long DEFAULT_SERVED_REQUEST_MONITORING_PERIOD = 120000; // 2
																					// minutes
	private static final long DEFAULT_GARBAGE_COLLECTOR_PERIOD = 240000; // 4
																			// minutes
	private static final long DEFAULT_INSTANCE_IP_MONITORING_PERIOD = 10000; // 10
																				// seconds
	private static final int DEFAULT_MAX_IP_MONITORING_TRIES = 90; // 30 tries
	private static final long DEFAULT_ACCOUNTING_UPDATE_PERIOD = 300000; // 5
																			// minutes
	public static final int DEFAULT_MAX_POOL = 200;

	private final ManagerTimer requestSchedulerTimer;
	private final ManagerTimer instanceMonitoringTimer;
	private final ManagerTimer servedRequestMonitoringTimer;
	private final ManagerTimer garbageCollectorTimer;
	private final ManagerTimer accountingUpdaterTimer;
	private final ManagerTimer requestDBUpdaterTimer;

	private Map<String, Token> instanceIdToToken = new HashMap<String, Token>();
	private final List<FederationMember> members = Collections.synchronizedList(new LinkedList<FederationMember>());
	private RequestRepository requests = new RequestRepository();
	private FederationMemberPickerPlugin memberPickerPlugin;
	private List<Flavor> flavorsProvided;
	private BenchmarkingPlugin benchmarkingPlugin;
	private AccountingPlugin accountingPlugin;
	private ImageStoragePlugin imageStoragePlugin;
	private AuthorizationPlugin authorizationPlugin;
	private ComputePlugin computePlugin;
	private IdentityPlugin localIdentityPlugin;
	private IdentityPlugin federationIdentityPlugin;
	private PrioritizationPlugin prioritizationPlugin;
	private MapperPlugin mapperPlugin;
	private Properties properties;
	private AsyncPacketSender packetSender;
	private FederationMemberAuthorizationPlugin validator;
	private ExecutorService benchmarkExecutor = Executors.newCachedThreadPool();
	private SshClientPool sshClientPool = new SshClientPool();
	private FailedBatch failedBatch = new FailedBatch();

	private Map<String, ForwardedRequest> asynchronousRequests = new ConcurrentHashMap<String, ForwardedRequest>();

	private DateUtils dateUtils = new DateUtils();

	private PoolingHttpClientConnectionManager cm;

	private OrderDataStore requestDB;

	public ManagerController(Properties properties) {
		this(properties, null);
	}

	public ManagerController(Properties properties, ScheduledExecutorService executor) {
		if (properties == null) {
			throw new IllegalArgumentException();
		}
		this.properties = properties;
		setFlavorsProvided(ResourceRepository.getStaticFlavors(properties));
		if (executor == null) {
			this.requestSchedulerTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.instanceMonitoringTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.servedRequestMonitoringTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.garbageCollectorTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.accountingUpdaterTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.requestDBUpdaterTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
		} else {
			this.requestSchedulerTimer = new ManagerTimer(executor);
			this.instanceMonitoringTimer = new ManagerTimer(executor);
			this.servedRequestMonitoringTimer = new ManagerTimer(executor);
			this.garbageCollectorTimer = new ManagerTimer(executor);
			this.accountingUpdaterTimer = new ManagerTimer(executor);
			this.requestDBUpdaterTimer = new ManagerTimer(executor);
		}
		requestDB = new OrderDataStore(properties);
		recoverPreviousRequests();
		triggerRequestDBUpdater();
	}

	public void setDatabase(OrderDataStore database) {
		this.requestDB = database;
	}

	public void setBenchmarkExecutor(ExecutorService benchmarkExecutor) {
		this.benchmarkExecutor = benchmarkExecutor;
	}

	public void setPrioritizationPlugin(PrioritizationPlugin prioritizationPlugin) {
		this.prioritizationPlugin = prioritizationPlugin;
	}

	public void setFlavorsProvided(List<Flavor> flavorsProvided) {
		this.flavorsProvided = flavorsProvided;
	}

	public void setMemberPickerPlugin(FederationMemberPickerPlugin memberPicker) {
		this.memberPickerPlugin = memberPicker;
	}

	public void setBenchmarkingPlugin(BenchmarkingPlugin benchmarkingPlugin) {
		this.benchmarkingPlugin = benchmarkingPlugin;
	}

	public void setAccountingPlugin(AccountingPlugin accountingPlugin) {
		this.accountingPlugin = accountingPlugin;
		// accounging updater may starting only after set accounting plugin
		if (!accountingUpdaterTimer.isScheduled()) {
			triggerAccountingUpdater();
		}
	}

	private void recoverPreviousRequests() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					initializeManager();
				} catch (Exception e) {
					LOGGER.error("Could not recover requests.", e);
				}
			}
		}).start();
	}

	protected void initializeManager() throws SQLException, JSONException {
		LOGGER.debug("Recovering previous requests.");
		for (Request request : this.requestDB.getOrders()) {
			Instance instance = null;
			try {
				if (request.getState().equals(RequestState.FULFILLED)
						|| request.getState().equals(RequestState.DELETED)) {
					instance = getInstance(request);
					LOGGER.debug(instance.getId() + " was recovered to request " + request.getId());
				}
			} catch (Exception e) {
				LOGGER.debug(request.getGlobalInstanceId() + " does not exist anymore.");
				if (request.getState().equals(RequestState.DELETED)) {
					continue;
				}
				instanceRemoved(request);
			}
			requests.addRequest(request.getFederationToken().getUser(), request);
		}
		if (!requestSchedulerTimer.isScheduled() && requests.getRequestsIn(RequestState.OPEN).size() > 0) {
			triggerRequestScheduler();
		}
		LOGGER.debug("Previous requests recovered.");
	}

	private String getSSHCommonUser() {
		String sshCommonUser = properties.getProperty(ConfigurationConstants.SSH_COMMON_USER);
		return sshCommonUser == null ? DEFAULT_COMMON_SSH_USER : sshCommonUser;
	}

	private void triggerAccountingUpdater() {
		String accountingUpdaterPeriodStr = properties.getProperty(ConfigurationConstants.ACCOUNTING_UPDATE_PERIOD_KEY);
		final long accountingUpdaterPeriod = accountingUpdaterPeriodStr == null ? DEFAULT_ACCOUNTING_UPDATE_PERIOD
				: Long.valueOf(accountingUpdaterPeriodStr);

		accountingUpdaterTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					updateAccounting();					
				} catch (Throwable e) {
					LOGGER.error("Erro while updating accounting", e);
				}
			}
		}, 0, accountingUpdaterPeriod);
	}

	private void updateAccounting() {
		List<Request> requestsWithInstances = new ArrayList<Request>(
				requests.getRequestsIn(RequestState.FULFILLED, RequestState.DELETED));
		List<String> requestIds = new LinkedList<String>();
		for (Request request : requestsWithInstances) {
			requestIds.add(request.getId());
		}
		LOGGER.debug("Usage accounting is about to be updated. " + "The following requests do have instances: "
				+ requestIds);
		accountingPlugin.update(requestsWithInstances);
	}

	public void setAuthorizationPlugin(AuthorizationPlugin authorizationPlugin) {
		this.authorizationPlugin = authorizationPlugin;
	}

	public void setImageStoragePlugin(ImageStoragePlugin imageStoragePlugin) {
		this.imageStoragePlugin = imageStoragePlugin;
	}

	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
		// garbage collector may starting only after set compute plugin
		if (!garbageCollectorTimer.isScheduled()) {
			triggerGarbageCollector();
		}
	}

	public void setLocalIdentityPlugin(IdentityPlugin identityPlugin) {
		this.localIdentityPlugin = identityPlugin;
	}

	public void setFederationIdentityPlugin(IdentityPlugin federationIdentityPlugin) {
		this.federationIdentityPlugin = federationIdentityPlugin;
	}

	private void triggerGarbageCollector() {
		String garbageCollectorPeriodStr = properties.getProperty(ConfigurationConstants.GARBAGE_COLLECTOR_PERIOD_KEY);
		final long garbageCollectorPeriod = garbageCollectorPeriodStr == null ? DEFAULT_GARBAGE_COLLECTOR_PERIOD
				: Long.valueOf(garbageCollectorPeriodStr);

		garbageCollectorTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {	
				try {
					garbageCollector();					
				} catch (Throwable e) {
					LOGGER.error("Erro while executing garbage collector", e);
				}
			}
		}, 0, garbageCollectorPeriod);
	}

	protected void garbageCollector() {
		LOGGER.debug("Garbage collector...");
		if (computePlugin != null) {
			List<Instance> federationInstances = getAllFogbowFederationInstances();
			LOGGER.debug("Number of federation instances = " + federationInstances.size());
			for (Instance instance : federationInstances) {
				LOGGER.debug("federation instance=" + instance.getId());
				Request request = requests.getRequestByInstance(instance.getId());
				LOGGER.debug("request for instance " + instance.getId() + " is " + request);
				if ((!instanceHasRequestRelatedTo(null, generateGlobalId(instance.getId(), null)) && request != null
						&& !request.isLocal()) || request == null) {
					// this is an orphan instance
					LOGGER.debug("Removing the orphan instance " + instance.getId());
					this.computePlugin.removeInstance(getTokenPerInstance(instance.getId()), instance.getId());
				}
			}
		}
	}

	protected Token getTokenPerInstance(String instanceId) {
		return this.instanceIdToToken.get(instanceId);
	}

	protected List<Instance> getAllFogbowFederationInstances() {
		this.instanceIdToToken = new HashMap<String, Token>();
		List<Instance> federationInstances = new ArrayList<Instance>();
		Map<String, Map<String, String>> allLocalCredentials = this.mapperPlugin.getAllLocalCredentials();
		for (String localName : allLocalCredentials.keySet()) {
			Map<String, String> credentials = allLocalCredentials.get(localName);
			List<Instance> instances = null;
			try {
				Token token = this.localIdentityPlugin.createToken(credentials);
				instances = this.computePlugin.getInstances(token);
				for (Instance instance : instances) {
					if (this.instanceIdToToken.get(instance.getId()) == null) {
						this.instanceIdToToken.put(instance.getId(), token);
						federationInstances.add(instance);
					}
				}
			} catch (Exception e) {
				LOGGER.warn("Does not possible get instances " + "with credentials of " + localName);
			}
		}
		return federationInstances;
	}

	public boolean instanceHasRequestRelatedTo(String requestId, String instanceId) {
		LOGGER.debug("Checking if instance " + instanceId + " is related to request " + requestId);
		// checking federation local user instances for local users
		if (requestId == null) {
			for (Request request : requests.getAllRequests()) {
				if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED, RequestState.SPAWNING)) {
					String reqInstanceId = generateGlobalId(request.getInstanceId(), request.getProvidingMemberId());
					if (reqInstanceId != null && reqInstanceId.equals(instanceId)) {
						LOGGER.debug("The instance " + instanceId + " is related to request " + request.getId());
						return true;
					}
				}
			}
		} else {
			// checking federation local users instances for remote members
			Request request = requests.get(requestId);
			LOGGER.debug("The request with id " + requestId + " is " + request);
			if (request == null) {
				return false;
			}
			if (instanceId == null) {
				return true;
			}

			// it is possible that the asynchronous request has not received
			// instanceId yet
			if ((request.getState().in(RequestState.OPEN) || request.getState().in(RequestState.PENDING)) 
						&& asynchronousRequests.containsKey(requestId)) {
				LOGGER.debug("The instance " + instanceId + " is related to request " + request.getId());
				return true;
			} else if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED, RequestState.SPAWNING)) {
				String reqInstanceId = generateGlobalId(request.getInstanceId(), request.getProvidingMemberId());
				if (reqInstanceId != null && reqInstanceId.equals(instanceId)) {
					LOGGER.debug("The instance " + instanceId + " is related to request " + request.getId());
					return true;
				}
			}
		}
		return false;
	}

	public void updateMembers(List<FederationMember> members) {
		LOGGER.debug("Updating members: " + members);
		if (members == null) {
			throw new IllegalArgumentException();
		}
		synchronized (this.members) {
			this.members.clear();
			for (FederationMember member : members) {
				this.members.add(member);
			}
		}
	}

	public List<FederationMember> getRendezvousMembers() {
		List<FederationMember> membersCopy = null;
		synchronized (this.members) {
			membersCopy = new LinkedList<FederationMember>(members);
		}
		boolean containsThis = false;
		for (FederationMember member : membersCopy) {
			if (member.getId().equals(
					properties.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {
				containsThis = true;
				break;
			}
		}
		if (!containsThis) {
			membersCopy.add(new FederationMember(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY)));
		}
		return membersCopy;
	}
	
	public FederationMember getFederationMemberQuota(String federationMemberId, String accessId) {
		try {
			if (federationMemberId.equals(properties
					.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {
				return new FederationMember(getResourcesInfo(this.getLocalCredentials(accessId)));
			}
			
			return new FederationMember(ManagerPacketHelper.getRemoteUserQuota
					(accessId, federationMemberId, packetSender));
		} catch (Exception e) {
			LOGGER.error("Error while trying to get member [" + accessId + "] quota from ["
					+ federationMemberId + "]", e);
			return null;
		}
	}

	public Map<String, String> getLocalCredentials(String accessId) {
		return mapperPlugin.getLocalCredentials(accessId);
	}

	public ResourcesInfo getResourcesInfo() {
		return getResourcesInfo(null);
	}

	public ResourcesInfo getResourcesInfo(Map<String, String> localCredentials) {
		ResourcesInfo totalResourcesInfo = new ResourcesInfo();
		totalResourcesInfo.setId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));

		if (localCredentials != null) {
			totalResourcesInfo
					.addResource(computePlugin.getResourcesInfo(localIdentityPlugin.createToken(localCredentials)));
			return totalResourcesInfo;
		}

		Map<String, Map<String, String>> allLocalCredentials = this.mapperPlugin.getAllLocalCredentials();
		List<Map<String, String>> credentialsUsed = new ArrayList<Map<String, String>>();
		for (String localName : allLocalCredentials.keySet()) {
			Map<String, String> credentials = allLocalCredentials.get(localName);
			if (credentialsUsed.contains(credentials)) {
				continue;
			}
			credentialsUsed.add(credentials);

			ResourcesInfo resourcesInfo = null;
			try {
				resourcesInfo = computePlugin.getResourcesInfo(localIdentityPlugin.createToken(credentials));
			} catch (Exception e) {
				LOGGER.warn("Does not possible get resources info with credentials of " + localName);
			}
			totalResourcesInfo.addResource(resourcesInfo);
		}
		return totalResourcesInfo;
	}

	public String getUser(String accessId) {
		Token token = getTokenFromFederationIdP(accessId);
		if (token == null) {
			return null;
		}
		return token.getUser();
	}

	public List<Request> getRequestsFromUser(String federationAccessToken) {
		return getRequestsFromUser(federationAccessToken, true);
	}
	
	public List<Request> getRequestsFromUser(String federationAccessToken, boolean findLocalRequest) {
		String user = getUser(federationAccessToken);
		return requests.getByUser(user, findLocalRequest);
	}

	public void removeAllRequests(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing all requests of user: " + user);
		requests.removeByUser(user);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
	}

	public void removeRequest(String accessId, String requestId) {
		LOGGER.debug("Removing requestId: " + requestId);
		checkRequestId(accessId, requestId);
		Request request = requests.get(requestId);
		if (request != null 
				&& request.getProvidingMemberId() != null 
				&& (request.getState().equals(RequestState.OPEN) 
						|| request.getState().equals(RequestState.PENDING))) {
			removeAsynchronousRemoteRequests(request, true);
		}
		requests.remove(requestId);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
	}
	
	public void removeRequestForRemoteMember(String accessId, String requestId) {
		LOGGER.debug("Removing requestId for remote member: " + requestId);
		checkRequestId(accessId, requestId, false);
		Request request = requests.get(requestId, false);
		if (request != null && request.getInstanceId() != null) {
			try {
				computePlugin.removeInstance(localIdentityPlugin.getToken(accessId), 
						request.getInstanceId());				
			} catch (Exception e) { 
			}
		}
		requests.exclude(request.getId());
	}	

	private void checkRequestId(String accessId, String requestId) {
		checkRequestId(accessId, requestId, true);
	}
	
	private void checkRequestId(String accessId, String requestId, boolean lookingForLocalRequest) {
		String user = getUser(accessId);
		if (requests.get(user, requestId, lookingForLocalRequest) == null) {
			LOGGER.debug("User " + user + " does not have requesId " + requestId);
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String accessId) {
		LOGGER.debug("Retrieving instances of token " + accessId);
		List<Instance> instances = new ArrayList<Instance>();
		for (Request request : requests.getByUser(getUser(accessId))) {
			String instanceId = request.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			try {
				instances.add(generateInstanceWithGlobalId(request.getInstanceId(), request.getProvidingMemberId()));
			} catch (Exception e) {
				LOGGER.warn("Exception thown while retrieving instance " + instanceId + ".", e);
			}
		}
		return instances;
	}

	public Instance generateInstanceWithGlobalId(String instanceId, String memberId) {
		return new Instance(generateGlobalId(instanceId, memberId));
	}

	private String generateGlobalId(String instanceId, String memberId) {
		if (memberId == null) {
			memberId = this.properties.get(ConfigurationConstants.XMPP_JID_KEY).toString();
		}
		return instanceId + Request.SEPARATOR_GLOBAL_ID + memberId;
	}

	public Instance getInstance(String accessId, String instanceId) {
		Request request = getRequestForInstance(accessId, instanceId);
		return getInstance(request);
	}

	private Instance getInstanceSSHAddress(Request request) {
		Instance instance = null;
		if (isFulfilledByLocalMember(request)) {
			instance = new Instance(request.getInstanceId());
			Map<String, String> serviceAddresses = getExternalServiceAddresses(request.getId());
			if (serviceAddresses != null) {
				instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
				instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
				serviceAddresses.remove(SSH_SERVICE_NAME);
				instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
			}
		} else {
			LOGGER.debug(request.getInstanceId() + " is remote, going out to " + request.getProvidingMemberId()
					+ " to get its information.");
			instance = getRemoteInstance(request);
		}
		return instance;
	}

	private Instance getInstance(Request request) {
		Instance instance = null;
		if (isFulfilledByLocalMember(request)) {
			LOGGER.debug(request.getInstanceId() + " is local, getting its information in the local cloud.");
			instance = this.computePlugin.getInstance(getFederationUserToken(request), request.getInstanceId());

			instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
			Map<String, String> serviceAddresses = getExternalServiceAddresses(request.getId());
			if (serviceAddresses != null) {
				instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
				serviceAddresses.remove(SSH_SERVICE_NAME);
				instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
			}

			Category osCategory = getImageCategory(request.getCategories());
			if (osCategory != null) {
				instance.addResource(ResourceRepository.createImageResource(osCategory.getTerm()));
			}

		} else {
			LOGGER.debug(request.getInstanceId() + " is remote, going out to " + request.getProvidingMemberId()
					+ " to get its information.");
			instance = getRemoteInstance(request);
		}
		return instance;
	}

	private static Category getImageCategory(List<Category> categories) {
		if (categories == null) {
			return null;
		}
		Category osCategory = null;
		for (Category category : categories) {
			if (category.getScheme().equals(RequestConstants.TEMPLATE_OS_SCHEME)) {
				osCategory = category;
				break;
			}
		}
		return osCategory;
	}

	private CloseableHttpClient createReverseTunnelHttpClient() {
		this.cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(DEFAULT_MAX_POOL);
		cm.setDefaultMaxPerRoute(DEFAULT_MAX_POOL);
		return HttpClients.custom().setConnectionManager(cm).build();
	}

	private HttpClient reverseTunnelHttpClient = createReverseTunnelHttpClient();

	@SuppressWarnings("unchecked")
	private Map<String, String> getExternalServiceAddresses(String tokenId) {

		if (tokenId == null || tokenId.isEmpty()) {
			return null;
		}

		String hostAddr = properties.getProperty(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY);
		if (hostAddr == null) {
			return null;
		}

		HttpResponse response = null;
		try {
			String httpHostPort = properties.getProperty(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY);
			HttpGet httpGet = new HttpGet("http://" + hostAddr + ":" + httpHostPort + "/token/" + tokenId + "/all");
			response = reverseTunnelHttpClient.execute(httpGet);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				JSONObject jsonPorts = new JSONObject(EntityUtils.toString(response.getEntity()));
				if (jsonPorts.isNull(SSH_SERVICE_NAME)) {
					return null;
				}
				Iterator<String> serviceIterator = jsonPorts.keys();
				Map<String, String> servicePerAddress = new HashMap<String, String>();
				String sshPublicHostIP = properties.getProperty(ConfigurationConstants.TOKEN_HOST_PUBLIC_ADDRESS_KEY);
				while (serviceIterator.hasNext()) {
					String service = (String) serviceIterator.next();
					String port = jsonPorts.optString(service);
					servicePerAddress.put(service, sshPublicHostIP + ":" + port);
				}
				return servicePerAddress;
			}
		} catch (Throwable e) {
			LOGGER.warn("", e);
		} finally {
			if (response != null) {
				try {
					response.getEntity().getContent().close();
				} catch (IOException e) {
					// Best effort, may fail if the content was already closed.
				}
			}
		}
		return null;
	}

	private Instance getRemoteInstance(Request request) {
		return getRemoteInstance(request.getProvidingMemberId(), request.getInstanceId());
	}

	private Instance getRemoteInstance(String memberId, String instanceId) {
		return ManagerPacketHelper.getRemoteInstance(memberId, instanceId, packetSender);
	}

	public void removeInstances(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing instances of user: " + user);
		for (Request request : requests.getByUser(user)) {
			String instanceId = request.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			removeInstance(normalizeInstanceId(instanceId), request);
		}
	}

	private static String normalizeInstanceId(String instanceId) {
		if (instanceId.contains(Request.SEPARATOR_GLOBAL_ID)) {
			String[] partsInstanceId = instanceId.split(Request.SEPARATOR_GLOBAL_ID);
			instanceId = partsInstanceId[0];
		}
		return instanceId;
	}

	public void removeInstance(String federationToken, String instanceId) {
		Request request = getRequestForInstance(federationToken, instanceId);
		instanceId = normalizeInstanceId(instanceId);
		removeInstance(instanceId, request);
	}

	private void removeInstance(String instanceId, Request request) {
		if (isFulfilledByLocalMember(request)) {
			this.computePlugin.removeInstance(getFederationUserToken(request), instanceId);
		} else {
			removeRemoteInstance(request);
		}
		instanceRemoved(request);
	}

	private boolean isFulfilledByLocalMember(Request request) {
		if (request.getProvidingMemberId() != null
				&& request.getProvidingMemberId().equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
			return true;
		}
		return false;
	}

	private void instanceRemoved(Request request) {
		updateAccounting();
		benchmarkingPlugin.remove(request.getInstanceId());

		request.setInstanceId(null);
		request.setProvidingMemberId(null);

		if (request.getState().equals(RequestState.DELETED) || !request.isLocal()) {
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
				&& request.getAttValue(RequestAttribute.TYPE.getValue()).equals(RequestType.PERSISTENT.getValue());
	}

	private void removeRemoteRequest(final String providingMember, final Request request) {
		ManagerPacketHelper.deleteRemoteRequest(providingMember ,request, packetSender, new AsynchronousRequestCallback() {
			
			@Override
			public void success(String instanceId) {
				LOGGER.info("Servered request id " + request.getId() + " on " + providingMember + " removed");
			}
			
			@Override
			public void error(Throwable t) {
				LOGGER.warn("Error while removing servered request id " + request.getId() + " on " + providingMember);
			}
		});
	}
	
	private void removeRemoteInstance(Request request) {
		ManagerPacketHelper.deleteRemoteInstace(request, packetSender);
	}

	public Request getRequestForInstance(String federationToken, String instanceId) {
		String user = getUser(federationToken);
		LOGGER.debug("Getting instance " + instanceId + " of user " + user);
		List<Request> userRequests = requests.getAllLocalRequests();

		for (Request request : userRequests) {
			if (instanceId
					.equals(request.getInstanceId() + Request.SEPARATOR_GLOBAL_ID + request.getProvidingMemberId())) {
				if (!request.getFederationToken().getUser().equals(user)) {
					throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
				}
				return request;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	public Request getRequest(String accessId, String requestId) {
		LOGGER.debug("Getting requestId " + requestId);
		checkRequestId(accessId, requestId);
		return requests.get(requestId);
	}

	public FederationMember getFederationMember(String memberId) {
		for (FederationMember member : members) {
			if (member.getId().equals(memberId)) {
				return member;
			}
		}
		if (memberId.equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
			return new FederationMember(getResourcesInfo());
		}
		return null;
	}

	public void queueServedRequest(String requestingMemberId, List<Category> categories, Map<String, String> xOCCIAtt,
			String requestId, Token requestingUserToken) {

		normalizeBatchId(requestingMemberId, xOCCIAtt);

		LOGGER.info("Queueing request with categories: " + categories + " and xOCCIAtt: " + xOCCIAtt
				+ " for requesting member: " + requestingMemberId + " with requestingToken " + requestingUserToken);
		Request request = new Request(requestId, requestingUserToken, categories, xOCCIAtt, false, requestingMemberId);
		requests.addRequest(requestingUserToken.getUser(), request);

		if (!requestSchedulerTimer.isScheduled()) {
			triggerRequestScheduler();
		}
	}

	protected String createUserDataUtilsCommand(Request request) throws IOException, MessagingException {
		return UserdataUtils.createBase64Command(request, properties);
	}

	protected Instance waitForSSHPublicAddress(Request request) {
		int remainingTries = DEFAULT_MAX_IP_MONITORING_TRIES;
		while (remainingTries-- > 0) {
			try {
				Instance instance = getInstanceSSHAddress(request);
				Map<String, String> attributes = instance.getAttributes();
				if (attributes != null) {
					String sshPublicAddress = attributes.get(Instance.SSH_PUBLIC_ADDRESS_ATT);
					if (sshPublicAddress != null) {
						return instance;
					}
				}
				Thread.sleep(DEFAULT_INSTANCE_IP_MONITORING_PERIOD);
			} catch (Exception e) {
				LOGGER.warn("Exception while retrieving SSH public address", e);
			}
		}
		return null;
	}

	private String getManagerSSHPublicKey() {
		String publicSSHKeyFilePath = properties.getProperty(ConfigurationConstants.SSH_PUBLIC_KEY_PATH);
		if (publicSSHKeyFilePath == null || publicSSHKeyFilePath.isEmpty()) {
			return null;
		}
		File managerPublicKeyFile = new File(publicSSHKeyFilePath);
		if (!managerPublicKeyFile.exists()) {
			return null;
		}
		try {
			return IOUtils.toString(new FileInputStream(managerPublicKeyFile));
		} catch (IOException e) {
			LOGGER.warn("Could not read manager public key file", e);
			return null;
		}
	}

	private String getManagerSSHPrivateKeyFilePath() {
		String publicKeyFilePath = properties.getProperty(ConfigurationConstants.SSH_PRIVATE_KEY_PATH);
		if (publicKeyFilePath == null || publicKeyFilePath.isEmpty()) {
			return null;
		}
		return publicKeyFilePath;
	}

	private void removePublicKeyFromCategoriesAndAttributes(Map<String, String> xOCCIAtt, List<Category> categories) {
		xOCCIAtt.remove(RequestAttribute.DATA_PUBLIC_KEY.getValue());
		Category toRemove = null;
		for (Category category : categories) {
			if (category.getTerm().equals(RequestConstants.PUBLIC_KEY_TERM)) {
				toRemove = category;
				break;
			}
		}
		if (toRemove != null) {
			categories.remove(toRemove);
		}
	}

	protected void normalizeBatchId(String jidKey, Map<String, String> xOCCIAtt) {
		// adding xmpp jid key in the batch id
		xOCCIAtt.put(RequestAttribute.BATCH_ID.getValue(),
				jidKey + "@" + xOCCIAtt.get(RequestAttribute.BATCH_ID.getValue()));
	}

	private void populateWithManagerPublicKey(Map<String, String> xOCCIAtt, List<Category> categories) {
		String publicKey = getManagerSSHPublicKey();
		if (publicKey == null) {
			return;
		}
		xOCCIAtt.put(RequestAttribute.DATA_PUBLIC_KEY.getValue(), publicKey);
		for (Category category : categories) {
			if (category.getTerm().equals(RequestConstants.PUBLIC_KEY_TERM)) {
				return;
			}
		}
		categories.add(new Category(RequestConstants.PUBLIC_KEY_TERM, RequestConstants.CREDENTIALS_RESOURCE_SCHEME,
				RequestConstants.MIXIN_CLASS));
	}

	protected void preemption(Request requestToPreemption) {
		removeInstance(requestToPreemption.getInstanceId(), requestToPreemption);
	}

	private void triggerServedRequestMonitoring() {
		String servedRequestMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.SERVED_REQUEST_MONITORING_PERIOD_KEY);
		final long servedRequestMonitoringPeriod = servedRequestMonitoringPeriodStr == null
				? DEFAULT_SERVED_REQUEST_MONITORING_PERIOD : Long.valueOf(servedRequestMonitoringPeriodStr);

		servedRequestMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {	
				try {
					monitorServedRequests();
				} catch (Throwable e) {
					LOGGER.error("Erro while monitoring served requests", e);
				}
			}
		}, 0, servedRequestMonitoringPeriod);
	}

	private String getLocalImageId(List<Category> categories, Token federationUserToken) {
		if (imageStoragePlugin == null) {
			return null;
		}
		Category osCategory = getImageCategory(categories);
		String localImageId = null;
		if (osCategory != null) {
			String globalImageId = osCategory.getTerm();
			localImageId = imageStoragePlugin.getLocalId(federationUserToken, globalImageId);
		}
		LOGGER.debug("Image " + osCategory.getTerm() + " corresponds locally to " + localImageId);
		return localImageId;
	}

	protected Token getFederationUserToken(Request request) {
		LOGGER.debug("Getting federation user token.");
		return localIdentityPlugin.createToken(mapperPlugin.getLocalCredentials(request));
	}

	public Instance getInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " for remote member.");
		try {
			Request servedRequest = requests.getRequestByInstance(instanceId);
			Instance instance = computePlugin.getInstance(getFederationUserToken(servedRequest), instanceId);
			if (servedRequest != null) {
				Map<String, String> serviceAddresses = getExternalServiceAddresses(servedRequest.getId());
				if (serviceAddresses != null) {
					instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
					instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
					serviceAddresses.remove(SSH_SERVICE_NAME);
					instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
				}

				Category osCategory = getImageCategory(servedRequest.getCategories());
				if (osCategory != null) {
					instance.addResource(ResourceRepository.createImageResource(osCategory.getTerm()));
				}
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

		updateAccounting();
		benchmarkingPlugin.remove(instanceId);
		computePlugin.removeInstance(getFederationUserToken(requests.getRequestByInstance(instanceId)), instanceId);
	}

	public Token getTokenFromFederationIdP(String accessId) {
		Token token = federationIdentityPlugin.getToken(accessId);
		if (!authorizationPlugin.isAuthorized(token)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
		}
		return token;
	}

	public List<Request> createRequests(String federationAccessTokenStr, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		Token federationToken = getTokenFromFederationIdP(federationAccessTokenStr);

		LOGGER.debug("Federation User Token: " + federationToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		xOCCIAtt.put(RequestAttribute.BATCH_ID.getValue(), String.valueOf(UUID.randomUUID()));

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, federationToken, new LinkedList<Category>(categories),
					new HashMap<String, String>(xOCCIAtt), true, properties.getProperty("xmpp_jid"));
			LOGGER.info("Created request: " + request);
			currentRequests.add(request);
			requests.addRequest(federationToken.getUser(), request);
		}
		if (!requestSchedulerTimer.isScheduled()) {
			triggerRequestScheduler();
		}

		return currentRequests;
	}

	private void triggerRequestDBUpdater() {
		String bdUpdaterPeriodStr = properties.getProperty(ConfigurationConstants.ORDER_BD_UPDATER_PERIOD_KEY);
		long schedulerPeriod = bdUpdaterPeriodStr == null ? DEFAULT_BD_UPDATE_PERIOD : Long.valueOf(bdUpdaterPeriodStr);
		requestDBUpdaterTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					updateRequestDB();
				} catch (Throwable e) {
					LOGGER.error("Could not update the database.", e);
				}
			}
		}, 60000, schedulerPeriod);
	}

	protected void updateRequestDB() throws SQLException, JSONException {
		LOGGER.debug("Database update start.");
		List<Request> orders = this.requestDB.getOrders();
		Map<String, Request> ordersDB = new HashMap<String, Request>();
		for (Request request : orders) {
			ordersDB.put(request.getId(), request);
		}
		List<Request> allRequests = new ArrayList<Request>(requests.getAllRequests());
		for (Request request : allRequests) {
			if (ordersDB.get(request.getId()) == null) {
				requestDB.addOrder(request);
			} else {
				requestDB.updateOrder(request);
				ordersDB.remove(request.getId());
			}
		}
		for (String key : ordersDB.keySet()) {
			requestDB.removeOrder(ordersDB.get(key));
		}
		LOGGER.debug("Database update finish.");
	}

	protected void triggerInstancesMonitor() {
		String instanceMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY);
		final long instanceMonitoringPeriod = instanceMonitoringPeriodStr == null ? DEFAULT_INSTANCE_MONITORING_PERIOD
				: Long.valueOf(instanceMonitoringPeriodStr);

		instanceMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					monitorInstancesForLocalRequests();					
				} catch (Throwable e) {
					LOGGER.error("Erro while monitoring instances for local requests", e);
				}
			}
		}, 0, instanceMonitoringPeriod);
	}

	protected void monitorInstancesForLocalRequests() {
		boolean turnOffTimer = true;
		LOGGER.info("Monitoring instances.");

		for (Request request : requests.getAllLocalRequests()) {
			if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED, RequestState.SPAWNING)) {
				turnOffTimer = false;
			}

			if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED)) {
				try {
					LOGGER.debug("Monitoring instance of request: " + request);
					removeFailedInstance(request, getInstance(request));
				} catch (Throwable e) {
					LOGGER.debug("Error while getInstance of " + request.getInstanceId(), e);
					instanceRemoved(requests.get(request.getId()));
				}
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are no requests.");
			instanceMonitoringTimer.cancel();
		}
	}

	private void removeFailedInstance(Request request, Instance instance) {
		if (instance == null) {
			return;
		}
		if (InstanceState.FAILED.equals(instance.getState())) {
			try {
				removeInstance(instance.getId(), request);
			} catch (Throwable t) {
				// Best effort
				LOGGER.warn("Error while removing stale instance.", t);
			}
		}
	}

	private void createAsynchronousRemoteInstance(final Request request, List<FederationMember> allowedMembers) {
		if (packetSender == null) {
			return;
		}

		FederationMember member = memberPickerPlugin.pick(allowedMembers);
		if (member == null) {
			return;
		}

		final String memberAddress = member.getId();

		Map<String, String> xOCCIAttCopy = new HashMap<String, String>(request.getxOCCIAtt());
		List<Category> categoriesCopy = new LinkedList<Category>(request.getCategories());
		populateWithManagerPublicKey(xOCCIAttCopy, categoriesCopy);
		request.setProvidingMemberId(memberAddress);
		request.setState(RequestState.PENDING);

		LOGGER.info("Submiting request " + request + " to member " + memberAddress);
		
		ForwardedRequest forwardedRequest = new ForwardedRequest(request, dateUtils.currentTimeMillis());
		ForwardedRequest forwardedRequestBefore = asynchronousRequests.get(request.getId());
		forwardedRequest.addMembersServered(
				forwardedRequestBefore != null ? forwardedRequestBefore.getMembersServered() : null);
		
		asynchronousRequests.put(request.getId(),forwardedRequest);
		ManagerPacketHelper.asynchronousRemoteRequest(request.getId(), categoriesCopy, xOCCIAttCopy, memberAddress, 
				federationIdentityPlugin.getForwardableToken(request.getFederationToken()), 
				packetSender, new AsynchronousRequestCallback() {
					@Override
					public void success(String instanceId) {
						LOGGER.debug("The request " + request + " forwarded to " + memberAddress + " gets instance "
								+ instanceId);
						if (asynchronousRequests.get(request.getId()) == null) {
							return;
						}
						if (instanceId == null) {
							if (request.getState().equals(RequestState.PENDING)) {
								request.setState(RequestState.OPEN);								
							}
							return;
						}

						if (request.getState().in(RequestState.DELETED)) {
							return;
						}

						// reseting time stamp
						asynchronousRequests.get(request.getId()).setTimeStamp(dateUtils.currentTimeMillis());

						request.setInstanceId(instanceId);
						request.setProvidingMemberId(memberAddress);
						try {
							execBenchmark(request);
						} catch (Throwable e) {
							LOGGER.error("Error while executing the benchmark in " + instanceId
									+ " from member " + memberAddress + ".", e);
							if (request.getState().equals(RequestState.PENDING)) {
								request.setState(RequestState.OPEN);
							}
							return;
						}
					}

					@Override
					public void error(Throwable t) {
						LOGGER.debug("The request " + request + " forwarded to " + memberAddress
								+ " gets error ", t);
						if (request.getState().equals(RequestState.PENDING)) {
							request.setState(RequestState.OPEN);
						}
						request.setProvidingMemberId(null);
					}
				});
	}

	protected boolean isRequestForwardedtoRemoteMember(String requestId) {
		return asynchronousRequests.containsKey(requestId);
	}

	private void wakeUpSleepingHosts(Request request) {
		String greenSitterJID = properties.getProperty("greensitter_jid");
		if (greenSitterJID != null && packetSender != null) {
			String vcpu = RequirementsHelper.getSmallestValueForAttribute(request.getRequirements(),
					RequirementsHelper.GLUE_VCPU_TERM);
			String mem = RequirementsHelper.getSmallestValueForAttribute(request.getRequirements(),
					RequirementsHelper.GLUE_MEM_RAM_TERM);
			ManagerPacketHelper.wakeUpSleepingHost(Integer.valueOf(vcpu), Integer.valueOf(mem), greenSitterJID,
					packetSender);
		}
	}

	private void execBenchmark(final Request request) {

		benchmarkExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Instance instance = null;
				if (getManagerSSHPublicKey() != null) {
					instance = waitForSSHPublicAddress(request);
					waitForSSHConnectivity(instance);
				}

				try {
					benchmarkingPlugin.run(request.getGlobalInstanceId(), instance);
				} catch (Exception e) {
					LOGGER.warn("Couldn't run benchmark.", e);
				}

				if (instance != null) {
					LOGGER.debug("Replacing public keys on " + request.getId());
					replacePublicKeys(instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT), request);
					LOGGER.debug("Public keys replaced on " + request.getId());
				}

				if (request.isLocal() && !isFulfilledByLocalMember(request)) {
					removeAsynchronousRemoteRequests(request, false);
					asynchronousRequests.remove(request.getId());
				}

				if (!request.isLocal()) {
					ManagerPacketHelper.replyToServedRequest(request, packetSender);
				}

				if (!request.getState().in(RequestState.DELETED)) {
					request.setState(RequestState.FULFILLED);
				}

				LOGGER.debug("Fulfilled Request: " + request);
			}
		});

		if (request.isLocal() && !instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}

		if (!request.isLocal() && !servedRequestMonitoringTimer.isScheduled()) {
			triggerServedRequestMonitoring();
		}
	}
	
	private void removeAsynchronousRemoteRequests(final Request request, boolean removeAll) {
		ForwardedRequest forwardedRequest = asynchronousRequests.get(request.getId());
		for (String memberServered : forwardedRequest != null ? forwardedRequest.getMembersServered() : new ArrayList<String>()) {						
			if (memberServered == null || forwardedRequest.getRequest() == null 
					|| (!removeAll && request.getProvidingMemberId().equals(memberServered) )) {
				continue;
			}
			try {
				removeRemoteRequest(memberServered, request);		
			} catch (Exception e) {
			}
		}
	}	
	
	protected void replacePublicKeys(String sshPublicAddress, Request request) {
		String requestPublicKey = request.getAttValue(RequestAttribute.DATA_PUBLIC_KEY.getValue());
		if (requestPublicKey == null) {
			requestPublicKey = "";
		}
		String sshCmd = "echo \"" + requestPublicKey + "\" > ~/.ssh/authorized_keys";
		try {
			Command sshOutput = execOnInstance(sshPublicAddress, sshCmd);
			if (sshOutput.getExitStatus() != 0) {
				LOGGER.error("Could not replace SSH public key. Exit value = " + sshOutput.getExitStatus());
			}
		} catch (Exception e) {
			LOGGER.error("Could not replace SSH public key.", e);
		}
	}

	protected void waitForSSHConnectivity(Instance instance) {
		if (instance == null || instance.getAttributes() == null
				|| instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT) == null) {
			return;
		}
		int retries = DEFAULT_MAX_IP_MONITORING_TRIES;
		while (retries-- > 0) {
			try {
				Command sshOutput = execOnInstance(instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT),
						"echo HelloWorld");
				if (sshOutput.getExitStatus() == 0) {
					break;
				}
			} catch (Exception e) {
				LOGGER.debug("Check for SSH connectivity failed. " + retries + " retries left.", e);
			}
			try {
				Thread.sleep(DEFAULT_INSTANCE_IP_MONITORING_PERIOD);
			} catch (InterruptedException e) {
			}
		}
	}

	private Command execOnInstance(String sshPublicAddress, String cmd) throws Exception {
		SSHClient sshClient = sshClientPool.getClient(sshPublicAddress, getSSHCommonUser(),
				getManagerSSHPrivateKeyFilePath());
		Session session = sshClient.startSession();
		Command command = session.exec(cmd);
		command.join();
		return command;
	}

	private void triggerRequestScheduler() {
		String schedulerPeriodStr = properties.getProperty(ConfigurationConstants.SCHEDULER_PERIOD_KEY);
		long schedulerPeriod = schedulerPeriodStr == null ? DEFAULT_SCHEDULER_PERIOD : Long.valueOf(schedulerPeriodStr);
		requestSchedulerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					checkAndSubmitOpenRequests();					
				} catch (Throwable e) {
					LOGGER.error("Erro while checking and submitting open requests", e);
				}
			}
		}, 0, schedulerPeriod);
	}

	protected void checkAndSubmitOpenRequests() {
		boolean allFulfilled = true;
		failedBatch.clear();
		LOGGER.debug("Checking and submiting requests.");

		// removing requests that reach timeout
		checkPedingRequests();
		for (Request request : new ArrayList<Request>(requests.getRequestsIn(RequestState.OPEN))) {
			if (!request.getState().equals(RequestState.OPEN)) {
				LOGGER.debug("The request " + request.getId() + " is no longer open.");
				continue;
			}

			LOGGER.debug(request.getId() + " being considered for scheduling.");
			Map<String, String> xOCCIAtt = request.getxOCCIAtt();
			if (request.isIntoValidPeriod()) {
				boolean isFulfilled = false;
				if (request.isLocal()) {
					for (String keyAttributes : RequestAttribute.getValues()) {
						xOCCIAtt.remove(keyAttributes);
					}

					String requirements = request.getRequirements();
					List<FederationMember> allowedFederationMembers = getAllowedFederationMembers(requirements);

					if (RequirementsHelper.matchLocation(requirements,
							properties.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {

						if (!isFulfilled
								&& !failedBatch.batchExists(request.getBatchId(), FailedBatchType.FEDERATION_USER)) {
							isFulfilled = createLocalInstanceWithFederationUser(request);
							if (!isFulfilled) {
								failedBatch.failBatch(request.getBatchId(), FailedBatchType.FEDERATION_USER);
							}
						}
					}
					if (!isFulfilled) {
						createAsynchronousRemoteInstance(request, allowedFederationMembers);
					}
					allFulfilled &= isFulfilled;
				} else { // it is served Request
					if (!failedBatch.batchExists(request.getBatchId(), FailedBatchType.FEDERATION_USER)) {
						isFulfilled = createLocalInstanceWithFederationUser(request);
						if (!isFulfilled) {
							failedBatch.failBatch(request.getBatchId(), FailedBatchType.FEDERATION_USER);
						}
					}
					allFulfilled &= isFulfilled;
				}
			} else if (request.isExpired()) {
				request.setState(RequestState.CLOSED);
			} else {
				allFulfilled = false;
			}
		}
		if (allFulfilled) {
			LOGGER.info("All requests fulfilled.");
		}
	}

	protected List<FederationMember> getAllowedFederationMembers(String requirements) {
		List<FederationMember> federationMembers = new ArrayList<FederationMember>(members);
		List<FederationMember> allowedFederationMembers = new ArrayList<FederationMember>();
		for (FederationMember federationMember : federationMembers) {
			if (federationMember.getId()
					.equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
				continue;
			}
			if (!getValidator().canReceiveFrom(federationMember)) {
				continue;
			}
			if (!RequirementsHelper.matchLocation(requirements, federationMember.getId())) {
				continue;
			}
			allowedFederationMembers.add(federationMember);
		}
		return allowedFederationMembers;
	}

	protected void monitorServedRequests() {
		LOGGER.info("Monitoring served requests.");

		List<Request> servedRequests = requests.getAllServedRequests();
		for (Request request : servedRequests) {
			if (!isInstanceBeingUsedByRemoteMember(request)) {
				LOGGER.debug("The instance " + request.getInstanceId() + " is not being used anymore by "
						+ request.getRequestingMemberId() + " and will be removed.");
				if (request.getInstanceId() != null) {
					try {
						removeInstanceForRemoteMember(request.getInstanceId());						
					} catch (Exception e) {}
				}
				requests.exclude(request.getId());
			}
		}

		if (requests.getAllServedRequests().isEmpty()) {
			LOGGER.info("There are no remote requests. Canceling remote request monitoring.");
			servedRequestMonitoringTimer.cancel();
		}
	}

	private boolean isInstanceBeingUsedByRemoteMember(Request servedRequest) {
		try {
			String globalInstanceId = servedRequest.getGlobalInstanceId();
			ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(globalInstanceId, servedRequest, packetSender);
			return true;
		} catch (OCCIException e) {
			return false;
		}
	}

	protected void checkPedingRequests() {
		Collection<ForwardedRequest> forwardedRequests = asynchronousRequests.values();
		for (ForwardedRequest forwardedRequest : forwardedRequests) {
			if (timoutReached(forwardedRequest.getTimeStamp()) 
					&& forwardedRequest.getRequest().getState().equals(RequestState.PENDING)){
				LOGGER.debug("The forwarded request " + forwardedRequest.getRequest().getId()
						+ " reached timeout and is been removed from asynchronousRequests list.");
				forwardedRequest.getRequest().setState(RequestState.OPEN);
			}
		}
	}

	private boolean timoutReached(long timeStamp) {
		long nowMilli = dateUtils.currentTimeMillis();
		Date now = new Date(nowMilli);

		String asyncRequestWaitingIntervalStr = properties
				.getProperty(ConfigurationConstants.ASYNC_REQUEST_WAITING_INTERVAL_KEY);
		final int asyncRequestWaitingInterval = asyncRequestWaitingIntervalStr == null
				? DEFAULT_ASYNC_REQUEST_WAITING_INTERVAL : Integer.valueOf(asyncRequestWaitingIntervalStr);

		Calendar c = Calendar.getInstance();
		c.setTime(new Date(timeStamp));		
		c.add(Calendar.MILLISECOND, asyncRequestWaitingInterval);
		return now.after(c.getTime());
	}

	protected boolean createLocalInstanceWithFederationUser(Request request) {
		LOGGER.info("Submitting request " + request + " with federation user locally.");

		FederationMember member = null;
		boolean isRemoteDonation = !properties.getProperty("xmpp_jid").equals(request.getRequestingMemberId());
		try {
			member = getFederationMember(request.getRequestingMemberId());
		} catch (Exception e) {
		}

		if (isRemoteDonation && !validator.canDonateTo(member, request.getFederationToken())) {
			return false;
		}

		try {
			return createInstance(request);
		} catch (Exception e) {
			LOGGER.info("Could not create instance with federation user locally. ", e);
			return false;
		}
	}

	protected boolean createInstance(Request request) {

		LOGGER.debug("Submiting request with categories: " + request.getCategories() + " and xOCCIAtt: "
				+ request.getxOCCIAtt() + " for requesting member: " + request.getRequestingMemberId()
				+ " with requestingToken " + request.getRequestingMemberId());

		try {
			try {
				String command = createUserDataUtilsCommand(request);
				request.putAttValue(RequestAttribute.USER_DATA_ATT.getValue(), command);
				request.addCategory(new Category(RequestConstants.USER_DATA_TERM, RequestConstants.SCHEME,
						RequestConstants.MIXIN_CLASS));
			} catch (Exception e) {
				LOGGER.warn("Exception while creating userdata.", e);
				return false;
			}

			Token federationUserToken = getFederationUserToken(request);
			String localImageId = getLocalImageId(request.getCategories(), federationUserToken);
			List<Category> categories = new LinkedList<Category>();
			for (Category category : request.getCategories()) {
				if (category.getScheme().equals(RequestConstants.TEMPLATE_OS_SCHEME)) {
					continue;
				}
				categories.add(category);
			}

			Map<String, String> xOCCIAttCopy = new HashMap<String, String>(request.getxOCCIAtt());
			removePublicKeyFromCategoriesAndAttributes(xOCCIAttCopy, categories);
			String instanceId = computePlugin.requestInstance(federationUserToken, categories, xOCCIAttCopy,
					localImageId);
			request.setState(RequestState.SPAWNING);
			request.setInstanceId(instanceId);
			request.setProvidingMemberId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));
			execBenchmark(request);
			return instanceId != null;
		} catch (OCCIException e) {
			ErrorType errorType = e.getType();
			if (errorType == ErrorType.QUOTA_EXCEEDED) {
				LOGGER.warn("Request failed locally for quota exceeded.", e);
				ArrayList<Request> requestsWithInstances = new ArrayList<Request>(
						requests.getRequestsIn(RequestState.FULFILLED, RequestState.DELETED));
				Request requestToPreempt = prioritizationPlugin.takeFrom(request, requestsWithInstances);
				if (requestToPreempt == null) {
					throw e;
				}
				preemption(requestToPreempt);
				return createInstance(request);
			} else if (errorType == ErrorType.UNAUTHORIZED) {
				LOGGER.warn("Request failed locally for user unauthorized.", e);
				return false;
			} else if (errorType == ErrorType.BAD_REQUEST) {
				LOGGER.warn("Request failed locally for image not found.", e);
				return false;
			} else if (errorType == ErrorType.NO_VALID_HOST_FOUND) {
				LOGGER.warn(
						"Request failed because no valid host was found," + " we will try to wake up a sleeping host.",
						e);
				wakeUpSleepingHosts(request);
				return false;
			} else {
				LOGGER.warn("Request failed locally for an unknown reason.", e);
				return false;
			}
		}
	}

	public void setPacketSender(AsyncPacketSender packetSender) {
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

	public FederationMemberAuthorizationPlugin getValidator() {
		return validator;
	}

	public void setValidator(FederationMemberAuthorizationPlugin validator) {
		this.validator = validator;
	}

	protected List<Request> getServedRequests() {
		return requests.getAllServedRequests();
	}

	public MapperPlugin getLocalCredentailsPlugin() {
		return mapperPlugin;
	}

	public void setLocalCredentailsPlugin(MapperPlugin mapperPlugin) {
		this.mapperPlugin = mapperPlugin;
	}

	public List<Resource> getAllResouces(String accessId) {
		Token userToken = getTokenFromFederationIdP(accessId);
		LOGGER.debug("User Token: " + userToken);
		return ResourceRepository.getInstance().getAll();
	}

	/**
	 * This method will not be supported in next releases.
	 * 
	 * @param request
	 * @param response
	 */
	@Deprecated
	public void bypass(org.restlet.Request request, Response response) {
		LOGGER.debug("Bypassing request: " + request);
		computePlugin.bypass(request, response);
	}

	public String getAuthenticationURI() {
		return localIdentityPlugin.getAuthenticationURI();
	}

	public Integer getMaxWhoIsAliveManagerCount() {
		String max = properties.getProperty(ConfigurationConstants.PROP_MAX_WHOISALIVE_MANAGER_COUNT);
		if (max == null) {
			return DEFAULT_MAX_WHOISALIVE_MANAGER_COUNT;
		}
		return Integer.parseInt(max);
	}

	public List<Instance> getInstancesFullInfo(String authToken) {
		List<Request> requestsFromUser = getRequestsFromUser(authToken);
		List<Instance> allFullInstances = new ArrayList<Instance>();
		LOGGER.debug("Getting all instances and your information.");
		for (Request request : requestsFromUser) {
			Instance instance = null;
			if (isFulfilledByLocalMember(request)) {
				LOGGER.debug(request.getInstanceId() + " is local, getting its information in the local cloud.");
				instance = this.computePlugin.getInstance(getFederationUserToken(request), request.getInstanceId());

				Map<String, String> serviceAddresses = getExternalServiceAddresses(request.getId());
				if (serviceAddresses != null) {
					instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
					instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
					serviceAddresses.remove(SSH_SERVICE_NAME);
					instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
				}

				Category osCategory = getImageCategory(request.getCategories());
				if (osCategory != null) {
					instance.addResource(ResourceRepository.createImageResource(osCategory.getTerm()));
				}
			} else {
				LOGGER.debug(request.getInstanceId() + " is remote, going out to " + request.getProvidingMemberId()
						+ " to get its information.");
				instance = getRemoteInstance(request);
			}
			allFullInstances.add(instance);
		}
		return allFullInstances;
	}

	public List<Flavor> getFlavorsProvided() {
		return this.flavorsProvided;
	}

	public List<ResourceUsage> getMembersUsage(String federationAccessId) {
		checkFederationAccessId(federationAccessId);
		return new ArrayList<ResourceUsage>(accountingPlugin.getMembersUsage().values());
	}

	private void checkFederationAccessId(String federationAccessId) {
		Token federationToken = getTokenFromFederationIdP(federationAccessId);
		if (federationToken == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
	}

	public Map<String, Double> getUsersUsage(String federationAccessId) {
		checkFederationAccessId(federationAccessId);

		return accountingPlugin.getUsersUsage();
	}
	
	public ResourcesInfo getResourceInfoForRemoteMember(String accessId) {		
		Map<String, String> localCredentials = getLocalCredentials(accessId);
		return getResourcesInfo(localCredentials);
	}

	protected FailedBatch getFailedBatches() {
		return failedBatch;
	}

	protected class FailedBatch {
		private Map<String, FailedBatchType> failedBatches = new HashMap<String, FailedBatchType>();

		public void failBatch(String batchId, FailedBatchType failedBatchType) {
			failedBatches.put(batchId, failedBatchType);
		}

		public boolean batchExists(String batchId, FailedBatchType failedBatchType) {
			if (failedBatches.get(batchId) != null) {
				return true;
			}
			return false;
		}

		protected List<String> getFailedBatchIdsPerType(FailedBatchType failedBatchType) {
			List<String> failedBatchIds = new ArrayList<String>();
			HashMap<String, FailedBatchType> newFailedBatches = new HashMap<String, FailedBatchType>(failedBatches);
			for (String key : newFailedBatches.keySet()) {
				if (newFailedBatches.get(key).equals(failedBatchType)) {
					failedBatchIds.add(key);
				}
			}
			return failedBatchIds;
		}

		public void clear() {
			failedBatches.clear();
		}
	}

	protected enum FailedBatchType { FEDERATION_USER }

	private static class ForwardedRequest {

		private Request request;
		private long timeStamp;
		private List<String> membersServered;


		public ForwardedRequest(Request request, long timeStamp) {
			this.request = request;
			this.timeStamp = timeStamp;
			this.membersServered = new ArrayList<String>();
		}

		public void setTimeStamp(long timeStamp) {
			this.timeStamp = timeStamp;
		}

		public Request getRequest() {
			return request;
		}

		public long getTimeStamp() {
			return timeStamp;
		}

		public List<String> getMembersServered() {
			return membersServered;
		}

		public void addMembersServered(List<String> membersServered) {
			if (membersServered == null || !membersServered.contains(request.getProvidingMemberId())) {
				this.membersServered.add(request.getProvidingMemberId());
			}
			if (membersServered == null) {
				return;
			}
			this.membersServered.addAll(membersServered);
		}
	}

}
