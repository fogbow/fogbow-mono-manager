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
import java.util.StringTokenizer;
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
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.plugins.localcredentails.MapperHelper;
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
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderDataStore;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderRepository;
import org.fogbowcloud.manager.occi.order.OrderState;
import org.fogbowcloud.manager.occi.order.OrderType;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.Response;

public class ManagerController {

	private static final String SSH_SERVICE_NAME = "ssh";
	protected final int MAX_ORDERS_PER_THREAD = 25;

	public static final String DEFAULT_COMMON_SSH_USER = "fogbow";

	private static final Logger LOGGER = Logger.getLogger(ManagerController.class);

	private static final int DEFAULT_MAX_WHOISALIVE_MANAGER_COUNT = 100;
	private static final long DEFAULT_BD_UPDATE_PERIOD = 300000; // 5 minute
	private static final long DEFAULT_SCHEDULER_PERIOD = 30000; // 30 seconds
	protected static final int DEFAULT_ASYNC_ORDER_WAITING_INTERVAL = 300000; // 5
																				// minutes
	private static final long DEFAULT_INSTANCE_MONITORING_PERIOD = 120000; // 2
																			// minutes
	private static final long DEFAULT_SERVED_ORDER_MONITORING_PERIOD = 120000; // 2
																					// minutes
	private static final long DEFAULT_GARBAGE_COLLECTOR_PERIOD = 240000; // 4
																			// minutes
	private static final long DEFAULT_INSTANCE_IP_MONITORING_PERIOD = 10000; // 10
																				// seconds
	private static final int DEFAULT_MAX_IP_MONITORING_TRIES = 90; // 30 tries
	private static final long DEFAULT_ACCOUNTING_UPDATE_PERIOD = 300000; // 5
																			// minutes
	public static final int DEFAULT_MAX_POOL = 200;

	private final ManagerTimer orderSchedulerTimer;
	private final ManagerTimer instanceMonitoringTimer;
	private final ManagerTimer servedOrderMonitoringTimer;
	private final ManagerTimer garbageCollectorTimer;
	private final ManagerTimer accountingUpdaterTimer;
	private final ManagerTimer orderDBUpdaterTimer;

	private Map<String, Token> instanceIdToToken = new HashMap<String, Token>();
	private final List<FederationMember> members = Collections.synchronizedList(new LinkedList<FederationMember>());
	private OrderRepository orderRepository = new OrderRepository();
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

	private Map<String, ForwardedOrder> asynchronousOrders = new ConcurrentHashMap<String, ForwardedOrder>();

	private DateUtils dateUtils = new DateUtils();

	private PoolingHttpClientConnectionManager cm;

	private OrderDataStore orderDB;

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
			this.orderSchedulerTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.instanceMonitoringTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.servedOrderMonitoringTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.garbageCollectorTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.accountingUpdaterTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.orderDBUpdaterTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
		} else {
			this.orderSchedulerTimer = new ManagerTimer(executor);
			this.instanceMonitoringTimer = new ManagerTimer(executor);
			this.servedOrderMonitoringTimer = new ManagerTimer(executor);
			this.garbageCollectorTimer = new ManagerTimer(executor);
			this.accountingUpdaterTimer = new ManagerTimer(executor);
			this.orderDBUpdaterTimer = new ManagerTimer(executor);
		}
		orderDB = new OrderDataStore(properties);
		recoverPreviousOrders();
		triggerOrderDBUpdater();
	}

	public void setDatabase(OrderDataStore database) {
		this.orderDB = database;
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

	private void recoverPreviousOrders() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					initializeManager();
				} catch (Exception e) {
					LOGGER.error("Could not recover orders.", e);
				}
			}
		}).start();
	}

	protected void initializeManager() throws SQLException, JSONException {
		LOGGER.debug("Recovering previous orders.");
		for (Order order : this.orderDB.getOrders()) {
			Instance instance = null;
			try {
				if (order.getState().equals(OrderState.FULFILLED)
						|| order.getState().equals(OrderState.DELETED)) {
					instance = getInstance(order);
					LOGGER.debug(instance.getId() + " was recovered to request " + order.getId());
				}
			} catch (Exception e) {
				LOGGER.debug(order.getGlobalInstanceId() + " does not exist anymore.");
				if (order.getState().equals(OrderState.DELETED)) {
					continue;
				}
				instanceRemoved(order);
			}
			orderRepository.addOrder(order.getFederationToken().getUser(), order);
		}
		if (!orderSchedulerTimer.isScheduled() && orderRepository.getOrdersIn(OrderState.OPEN).size() > 0) {
			triggerOrderScheduler();
		}
		LOGGER.debug("Previous orders recovered.");
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
		List<Order> ordersWithInstances = new ArrayList<Order>(
				orderRepository.getOrdersIn(OrderState.FULFILLED, OrderState.DELETED));
		List<String> orderIds = new LinkedList<String>();
		for (Order order : ordersWithInstances) {
			orderIds.add(order.getId());
		}
		LOGGER.debug("Usage accounting is about to be updated. " + "The following orders do have instances: "
				+ orderIds);
		accountingPlugin.update(ordersWithInstances);
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
				Order order = orderRepository.getOrderByInstance(instance.getId());
				LOGGER.debug("Order for instance " + instance.getId() + " is " + order);
				if ((!instanceHasOrderRelatedTo(null, generateGlobalId(instance.getId(), null)) && order != null
						&& !order.isLocal()) || order == null) {
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

	public boolean instanceHasOrderRelatedTo(String orderId, String instanceId) {
		LOGGER.debug("Checking if instance " + instanceId + " is related to order " + orderId);
		// checking federation local user instances for local users
		if (orderId == null) {
			for (Order order : orderRepository.getAllOrders()) {
				if (order.getState().in(OrderState.FULFILLED, OrderState.DELETED, OrderState.SPAWNING)) {
					String reqInstanceId = generateGlobalId(order.getInstanceId(), order.getProvidingMemberId());
					if (reqInstanceId != null && reqInstanceId.equals(instanceId)) {
						LOGGER.debug("The instance " + instanceId + " is related to order " + order.getId());
						return true;
					}
				}
			}
		} else {
			// checking federation local users instances for remote members
			Order order = orderRepository.get(orderId);
			LOGGER.debug("The order with id " + orderId + " is " + order);
			if (order == null) {
				return false;
			}
			if (instanceId == null) {
				return true;
			}

			// it is possible that the asynchronous order has not received
			// instanceId yet
			if ((order.getState().in(OrderState.OPEN) || order.getState().in(OrderState.PENDING)) 
						&& asynchronousOrders.containsKey(orderId)) {
				LOGGER.debug("The instance " + instanceId + " is related to order " + order.getId());
				return true;
			} else if (order.getState().in(OrderState.FULFILLED, OrderState.DELETED, OrderState.SPAWNING)) {
				String reqInstanceId = generateGlobalId(order.getInstanceId(), order.getProvidingMemberId());
				if (reqInstanceId != null && reqInstanceId.equals(instanceId)) {
					LOGGER.debug("The instance " + instanceId + " is related to order " + order.getId());
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

	public List<Order> getOrdersFromUser(String federationAccessToken) {
		return getOrdersFromUser(federationAccessToken, true);
	}
	
	public List<Order> getOrdersFromUser(String federationAccessToken, boolean findLocalOrder) {
		String user = getUser(federationAccessToken);
		return orderRepository.getByUser(user, findLocalOrder);
	}

	public void removeAllOrders(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing all orders of user: " + user);
		orderRepository.removeByUser(user);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
	}

	public void removeOrder(String accessId, String orderId) {
		LOGGER.debug("Removing orderId: " + orderId);
		checkOrderId(accessId, orderId);
		Order order = orderRepository.get(orderId);
		if (order != null 
				&& order.getProvidingMemberId() != null 
				&& (order.getState().equals(OrderState.OPEN) 
						|| order.getState().equals(OrderState.PENDING))) {
			removeAsynchronousRemoteOrders(order, true);
		}
		orderRepository.remove(orderId);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
	}
	
	public void removeOrderForRemoteMember(String accessId, String orderId) {
		LOGGER.debug("Removing orderId for remote member: " + orderId);
		checkOrderId(accessId, orderId, false);
		Order order = orderRepository.get(orderId, false);
		if (order != null && order.getInstanceId() != null) {
			try {
				computePlugin.removeInstance(localIdentityPlugin.getToken(accessId), 
						order.getInstanceId());				
			} catch (Exception e) { 
			}
		}
		orderRepository.exclude(order.getId());
	}	

	private void checkOrderId(String accessId, String orderId) {
		checkOrderId(accessId, orderId, true);
	}
	
	private void checkOrderId(String accessId, String orderId, boolean lookingForLocalOrder) {
		String user = getUser(accessId);
		if (orderRepository.get(user, orderId, lookingForLocalOrder) == null) {
			LOGGER.debug("User " + user + " does not have requesId " + orderId);
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String accessId) {
		LOGGER.debug("Retrieving instances of token " + accessId);
		List<Instance> instances = new ArrayList<Instance>();
		for (Order order : orderRepository.getByUser(getUser(accessId))) {
			String instanceId = order.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			try {
				instances.add(generateInstanceWithGlobalId(order.getInstanceId(), order.getProvidingMemberId()));
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
		return instanceId + Order.SEPARATOR_GLOBAL_ID + memberId;
	}

	public Instance getInstance(String accessId, String instanceId) {
		Order order = getOrderForInstance(accessId, instanceId);
		return getInstance(order);
	}

	private Instance getInstanceSSHAddress(Order order) {
		Instance instance = null;
		if (isFulfilledByLocalMember(order)) {
			instance = new Instance(order.getInstanceId());
			Map<String, String> serviceAddresses = getExternalServiceAddresses(order.getId());
			if (serviceAddresses != null) {
				instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
				instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
				serviceAddresses.remove(SSH_SERVICE_NAME);
				instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
			}
		} else {
			LOGGER.debug(order.getInstanceId() + " is remote, going out to " + order.getProvidingMemberId()
					+ " to get its information.");
			instance = getRemoteInstance(order);
		}
		return instance;
	}

	private Instance getInstance(Order order) {
		Instance instance = null;
		if (isFulfilledByLocalMember(order)) {
			LOGGER.debug(order.getInstanceId() + " is local, getting its information in the local cloud.");
			instance = this.computePlugin.getInstance(getFederationUserToken(order), order.getInstanceId());

			instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
			Map<String, String> serviceAddresses = getExternalServiceAddresses(order.getId());
			if (serviceAddresses != null) {
				instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
				serviceAddresses.remove(SSH_SERVICE_NAME);
				instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
			}

			Category osCategory = getImageCategory(order.getCategories());
			if (osCategory != null) {
				instance.addResource(ResourceRepository.createImageResource(osCategory.getTerm()));
			}

		} else {
			LOGGER.debug(order.getInstanceId() + " is remote, going out to " + order.getProvidingMemberId()
					+ " to get its information.");
			instance = getRemoteInstance(order);
		}
		return instance;
	}

	private static Category getImageCategory(List<Category> categories) {
		if (categories == null) {
			return null;
		}
		Category osCategory = null;
		for (Category category : categories) {
			if (category.getScheme().equals(OrderConstants.TEMPLATE_OS_SCHEME)) {
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

	private Instance getRemoteInstance(Order order) {
		return getRemoteInstance(order.getProvidingMemberId(), order.getInstanceId());
	}

	private Instance getRemoteInstance(String memberId, String instanceId) {
		return ManagerPacketHelper.getRemoteInstance(memberId, instanceId, packetSender);
	}

	public void removeInstances(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing instances of user: " + user);
		for (Order order : orderRepository.getByUser(user)) {
			String instanceId = order.getInstanceId();
			if (instanceId == null) {
				continue;
			}
			removeInstance(normalizeInstanceId(instanceId), order);
		}
	}

	private static String normalizeInstanceId(String instanceId) {
		if (instanceId.contains(Order.SEPARATOR_GLOBAL_ID)) {
			String[] partsInstanceId = instanceId.split(Order.SEPARATOR_GLOBAL_ID);
			instanceId = partsInstanceId[0];
		}
		return instanceId;
	}

	public void removeInstance(String federationToken, String instanceId) {
		Order order = getOrderForInstance(federationToken, instanceId);
		instanceId = normalizeInstanceId(instanceId);
		removeInstance(instanceId, order);
	}

	private void removeInstance(String instanceId, Order order) {
		if (isFulfilledByLocalMember(order)) {
			this.computePlugin.removeInstance(getFederationUserToken(order), instanceId);
		} else {
			removeRemoteInstance(order);
		}
		instanceRemoved(order);
	}

	private boolean isFulfilledByLocalMember(Order order) {
		if (order.getProvidingMemberId() != null
				&& order.getProvidingMemberId().equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
			return true;
		}
		return false;
	}

	private void instanceRemoved(Order order) {
		updateAccounting();
		benchmarkingPlugin.remove(order.getInstanceId());

		order.setInstanceId(null);
		order.setProvidingMemberId(null);

		if (order.getState().equals(OrderState.DELETED) || !order.isLocal()) {
			orderRepository.exclude(order.getId());
		} else if (isPersistent(order)) {
			LOGGER.debug("Order: " + order + ", setting state to " + OrderState.OPEN);
			order.setState(OrderState.OPEN);
			if (!orderSchedulerTimer.isScheduled()) {
				triggerOrderScheduler();
			}
		} else {
			LOGGER.debug("Order: " + order + ", setting state to " + OrderState.CLOSED);
			order.setState(OrderState.CLOSED);
		}
	}

	private boolean isPersistent(Order order) {
		return order.getAttValue(OrderAttribute.TYPE.getValue()) != null
				&& order.getAttValue(OrderAttribute.TYPE.getValue()).equals(OrderType.PERSISTENT.getValue());
	}

	private void removeRemoteOrder(final String providingMember, final Order order) {
		ManagerPacketHelper.deleteRemoteOrder(providingMember ,order, packetSender, new AsynchronousOrderCallback() {
			
			@Override
			public void success(String instanceId) {
				LOGGER.info("Servered order id " + order.getId() + " on " + providingMember + " removed");
			}
			
			@Override
			public void error(Throwable t) {
				LOGGER.warn("Error while removing servered order id " + order.getId() + " on " + providingMember);
			}
		});
	}
	
	private void removeRemoteInstance(Order order) {
		ManagerPacketHelper.deleteRemoteInstace(order, packetSender);
	}

	public Order getOrderForInstance(String federationToken, String instanceId) {
		String user = getUser(federationToken);
		LOGGER.debug("Getting instance " + instanceId + " of user " + user);
		List<Order> userOrders = orderRepository.getAllLocalOrders();

		for (Order order : userOrders) {
			if (instanceId
					.equals(order.getInstanceId() + Order.SEPARATOR_GLOBAL_ID + order.getProvidingMemberId())) {
				if (!order.getFederationToken().getUser().equals(user)) {
					throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
				}
				return order;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	public Order getOrder(String accessId, String orderId) {
		LOGGER.debug("Getting orderId " + orderId);
		checkOrderId(accessId, orderId);
		return orderRepository.get(orderId);
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

	public void queueServedOrder(String requestingMemberId, List<Category> categories, Map<String, String> xOCCIAtt,
			String orderId, Token requestingUserToken) {

		normalizeBatchId(requestingMemberId, xOCCIAtt);

		LOGGER.info("Queueing order with categories: " + categories + " and xOCCIAtt: " + xOCCIAtt
				+ " for requesting member: " + requestingMemberId + " with requestingToken " + requestingUserToken);
		Order order = new Order(orderId, requestingUserToken, categories, xOCCIAtt, false, requestingMemberId);
		orderRepository.addOrder(requestingUserToken.getUser(), order);

		if (!orderSchedulerTimer.isScheduled()) {
			triggerOrderScheduler();
		}
	}

	protected String createUserDataUtilsCommand(Order order) throws IOException, MessagingException {
		return UserdataUtils.createBase64Command(order, properties);
	}

	protected Instance waitForSSHPublicAddress(Order order) {
		int remainingTries = DEFAULT_MAX_IP_MONITORING_TRIES;
		while (remainingTries-- > 0) {
			try {
				Instance instance = getInstanceSSHAddress(order);
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
		xOCCIAtt.remove(OrderAttribute.DATA_PUBLIC_KEY.getValue());
		Category toRemove = null;
		for (Category category : categories) {
			if (category.getTerm().equals(OrderConstants.PUBLIC_KEY_TERM)) {
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
		xOCCIAtt.put(OrderAttribute.BATCH_ID.getValue(),
				jidKey + "@" + xOCCIAtt.get(OrderAttribute.BATCH_ID.getValue()));
	}

	private void populateWithManagerPublicKey(Map<String, String> xOCCIAtt, List<Category> categories) {
		String publicKey = getManagerSSHPublicKey();
		if (publicKey == null) {
			return;
		}
		xOCCIAtt.put(OrderAttribute.DATA_PUBLIC_KEY.getValue(), publicKey);
		for (Category category : categories) {
			if (category.getTerm().equals(OrderConstants.PUBLIC_KEY_TERM)) {
				return;
			}
		}
		categories.add(new Category(OrderConstants.PUBLIC_KEY_TERM, OrderConstants.CREDENTIALS_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
	}

	protected void preemption(Order orderToPreemption) {
		removeInstance(orderToPreemption.getInstanceId(), orderToPreemption);
	}

	private void triggerServedOrderMonitoring() {
		String servedOrderMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.SERVED_ORDER_MONITORING_PERIOD_KEY);
		final long servedOrderMonitoringPeriod = servedOrderMonitoringPeriodStr == null
				? DEFAULT_SERVED_ORDER_MONITORING_PERIOD : Long.valueOf(servedOrderMonitoringPeriodStr);

		servedOrderMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {	
				try {
					monitorServedOrders();
				} catch (Throwable e) {
					LOGGER.error("Erro while monitoring served orders", e);
				}
			}
		}, 0, servedOrderMonitoringPeriod);
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

	protected Token getFederationUserToken(Order order) {
		LOGGER.debug("Getting federation user token.");
		return localIdentityPlugin.createToken(mapperPlugin.getLocalCredentials(order));
	}

	public Instance getInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " for remote member.");
		try {
			Order servedOrder = orderRepository.getOrderByInstance(instanceId);
			Instance instance = computePlugin.getInstance(getFederationUserToken(servedOrder), instanceId);
			if (servedOrder != null) {
				Map<String, String> serviceAddresses = getExternalServiceAddresses(servedOrder.getId());
				if (serviceAddresses != null) {
					instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
					instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
					serviceAddresses.remove(SSH_SERVICE_NAME);
					instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
				}

				Category osCategory = getImageCategory(servedOrder.getCategories());
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
		computePlugin.removeInstance(getFederationUserToken(orderRepository.getOrderByInstance(instanceId)), instanceId);
	}

	public Token getTokenFromFederationIdP(String accessId) {
		Token token = federationIdentityPlugin.getToken(accessId);
		if (!authorizationPlugin.isAuthorized(token)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
		}
		return token;
	}

	public List<Order> createOrders(String federationAccessTokenStr, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		Token federationToken = getTokenFromFederationIdP(federationAccessTokenStr);

		LOGGER.debug("Federation User Token: " + federationToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(OrderAttribute.INSTANCE_COUNT.getValue()));
		LOGGER.info("Order " + instanceCount + " instances");

		xOCCIAtt.put(OrderAttribute.BATCH_ID.getValue(), String.valueOf(UUID.randomUUID()));

		List<Order> currentOrders = new ArrayList<Order>();
		for (int i = 0; i < instanceCount; i++) {
			String orderId = String.valueOf(UUID.randomUUID());
			Order order = new Order(orderId, federationToken, new LinkedList<Category>(categories),
					new HashMap<String, String>(xOCCIAtt), true, properties.getProperty("xmpp_jid"));
			LOGGER.info("Created order: " + order);
			currentOrders.add(order);
			orderRepository.addOrder(federationToken.getUser(), order);
		}
		if (!orderSchedulerTimer.isScheduled()) {
			triggerOrderScheduler();
		}

		return currentOrders;
	}

	private void triggerOrderDBUpdater() {
		String bdUpdaterPeriodStr = properties.getProperty(ConfigurationConstants.ORDER_BD_UPDATER_PERIOD_KEY);
		long schedulerPeriod = bdUpdaterPeriodStr == null ? DEFAULT_BD_UPDATE_PERIOD : Long.valueOf(bdUpdaterPeriodStr);
		orderDBUpdaterTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					updateOrderDB();
				} catch (Throwable e) {
					LOGGER.error("Could not update the database.", e);
				}
			}
		}, 60000, schedulerPeriod);
	}

	protected void updateOrderDB() throws SQLException, JSONException {
		LOGGER.debug("Database update start.");
		List<Order> orders = this.orderDB.getOrders();
		Map<String, Order> ordersDB = new HashMap<String, Order>();
		for (Order order : orders) {
			ordersDB.put(order.getId(), order);
		}
		List<Order> allOrders = new ArrayList<Order>(orderRepository.getAllOrders());
		for (Order order : allOrders) {
			if (ordersDB.get(order.getId()) == null) {
				orderDB.addOrder(order);
			} else {
				orderDB.updateOrder(order);
				ordersDB.remove(order.getId());
			}
		}
		for (String key : ordersDB.keySet()) {
			orderDB.removeOrder(ordersDB.get(key));
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
					monitorInstancesForLocalOrders();					
				} catch (Throwable e) {
					LOGGER.error("Erro while monitoring instances for local orders", e);
				}
			}
		}, 0, instanceMonitoringPeriod);
	}

	protected void monitorInstancesForLocalOrders() {
		boolean turnOffTimer = true;
		LOGGER.info("Monitoring instances.");

		for (Order order : orderRepository.getAllLocalOrders()) {
			if (order.getState().in(OrderState.FULFILLED, OrderState.DELETED, OrderState.SPAWNING)) {
				turnOffTimer = false;
			}

			if (order.getState().in(OrderState.FULFILLED, OrderState.DELETED)) {
				try {
					LOGGER.debug("Monitoring instance of order: " + order);
					removeFailedInstance(order, getInstance(order));
				} catch (Throwable e) {
					LOGGER.debug("Error while getInstance of " + order.getInstanceId(), e);
					instanceRemoved(orderRepository.get(order.getId()));
				}
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are no orders.");
			instanceMonitoringTimer.cancel();
		}
	}

	private void removeFailedInstance(Order order, Instance instance) {
		if (instance == null) {
			return;
		}
		if (InstanceState.FAILED.equals(instance.getState())) {
			try {
				removeInstance(instance.getId(), order);
			} catch (Throwable t) {
				// Best effort
				LOGGER.warn("Error while removing stale instance.", t);
			}
		}
	}

	private void createAsynchronousRemoteInstance(final Order order, List<FederationMember> allowedMembers) {
		if (packetSender == null) {
			return;
		}

		FederationMember member = memberPickerPlugin.pick(allowedMembers);
		if (member == null) {
			return;
		}

		final String memberAddress = member.getId();

		Map<String, String> xOCCIAttCopy = new HashMap<String, String>(order.getxOCCIAtt());
		List<Category> categoriesCopy = new LinkedList<Category>(order.getCategories());
		populateWithManagerPublicKey(xOCCIAttCopy, categoriesCopy);
		order.setProvidingMemberId(memberAddress);
		order.setState(OrderState.PENDING);

		LOGGER.info("Submiting order " + order + " to member " + memberAddress);
		
		ForwardedOrder forwardedOrder = new ForwardedOrder(order, dateUtils.currentTimeMillis());
		ForwardedOrder forwardedOrderBefore = asynchronousOrders.get(order.getId());
		forwardedOrder.addMembersServered(
				forwardedOrderBefore != null ? forwardedOrderBefore.getMembersServered() : null);
		
		asynchronousOrders.put(order.getId(),forwardedOrder);
		ManagerPacketHelper.asynchronousRemoteOrder(order.getId(), categoriesCopy, xOCCIAttCopy, memberAddress, 
				federationIdentityPlugin.getForwardableToken(order.getFederationToken()), 
				packetSender, new AsynchronousOrderCallback() {
					@Override
					public void success(String instanceId) {
						LOGGER.debug("The order " + order + " forwarded to " + memberAddress + " gets instance "
								+ instanceId);
						if (asynchronousOrders.get(order.getId()) == null) {
							return;
						}
						if (instanceId == null) {
							if (order.getState().equals(OrderState.PENDING)) {
								order.setState(OrderState.OPEN);								
							}
							return;
						}

						if (order.getState().in(OrderState.DELETED)) {
							return;
						}

						// reseting time stamp
						asynchronousOrders.get(order.getId()).setTimeStamp(dateUtils.currentTimeMillis());

						order.setInstanceId(instanceId);
						order.setProvidingMemberId(memberAddress);
						try {
							execBenchmark(order);
						} catch (Throwable e) {
							LOGGER.error("Error while executing the benchmark in " + instanceId
									+ " from member " + memberAddress + ".", e);
							if (order.getState().equals(OrderState.PENDING)) {
								order.setState(OrderState.OPEN);
							}
							return;
						}
					}

					@Override
					public void error(Throwable t) {
						LOGGER.debug("The order " + order + " forwarded to " + memberAddress
								+ " gets error ", t);
						if (order.getState().equals(OrderState.PENDING)) {
							order.setState(OrderState.OPEN);
						}
						order.setProvidingMemberId(null);
					}
				});
	}

	protected boolean isOrderForwardedtoRemoteMember(String orderId) {
		return asynchronousOrders.containsKey(orderId);
	}

	private void wakeUpSleepingHosts(Order order) {
		String greenSitterJID = properties.getProperty("greensitter_jid");
		if (greenSitterJID != null && packetSender != null) {
			String vcpu = RequirementsHelper.getSmallestValueForAttribute(order.getRequirements(),
					RequirementsHelper.GLUE_VCPU_TERM);
			String mem = RequirementsHelper.getSmallestValueForAttribute(order.getRequirements(),
					RequirementsHelper.GLUE_MEM_RAM_TERM);
			ManagerPacketHelper.wakeUpSleepingHost(Integer.valueOf(vcpu), Integer.valueOf(mem), greenSitterJID,
					packetSender);
		}
	}

	private void execBenchmark(final Order order) {

		benchmarkExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Instance instance = null;
				if (getManagerSSHPublicKey() != null) {
					instance = waitForSSHPublicAddress(order);
					waitForSSHConnectivity(instance);
				}

				try {
					benchmarkingPlugin.run(order.getGlobalInstanceId(), instance);
				} catch (Exception e) {
					LOGGER.warn("Couldn't run benchmark.", e);
				}

				if (instance != null) {
					LOGGER.debug("Replacing public keys on " + order.getId());
					replacePublicKeys(instance.getAttributes().get(Instance.SSH_PUBLIC_ADDRESS_ATT), order);
					LOGGER.debug("Public keys replaced on " + order.getId());
				}

				if (order.isLocal() && !isFulfilledByLocalMember(order)) {
					removeAsynchronousRemoteOrders(order, false);
					asynchronousOrders.remove(order.getId());
				}

				if (!order.isLocal()) {
					ManagerPacketHelper.replyToServedOrder(order, packetSender);
				}

				if (!order.getState().in(OrderState.DELETED)) {
					order.setState(OrderState.FULFILLED);
				}

				LOGGER.debug("Fulfilled order: " + order);
			}
		});

		if (order.isLocal() && !instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}

		if (!order.isLocal() && !servedOrderMonitoringTimer.isScheduled()) {
			triggerServedOrderMonitoring();
		}
	}
	
	private void removeAsynchronousRemoteOrders(final Order order, boolean removeAll) {
		ForwardedOrder forwardedOrder = asynchronousOrders.get(order.getId());
		for (String memberServered : forwardedOrder != null ? forwardedOrder.getMembersServered() : new ArrayList<String>()) {						
			if (memberServered == null || forwardedOrder.getOrder() == null 
					|| (!removeAll && order.getProvidingMemberId().equals(memberServered) )) {
				continue;
			}
			try {
				removeRemoteOrder(memberServered, order);		
			} catch (Exception e) {
			}
		}
	}	
	
	protected void replacePublicKeys(String sshPublicAddress, Order order) {
		String orderPublicKey = order.getAttValue(OrderAttribute.DATA_PUBLIC_KEY.getValue());
		if (orderPublicKey == null) {
			orderPublicKey = "";
		}
		String sshCmd = "echo \"" + orderPublicKey + "\" > ~/.ssh/authorized_keys";
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

	private void triggerOrderScheduler() {
		String schedulerPeriodStr = properties.getProperty(ConfigurationConstants.SCHEDULER_PERIOD_KEY);
		long schedulerPeriod = schedulerPeriodStr == null ? DEFAULT_SCHEDULER_PERIOD : Long.valueOf(schedulerPeriodStr);
		orderSchedulerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					checkAndSubmitOpenOrders();					
				} catch (Throwable e) {
					LOGGER.error("Erro while checking and submitting open orders", e);
				}
			}
		}, 0, schedulerPeriod);
	}

	protected void checkAndSubmitOpenOrders() {
		boolean allFulfilled = true;
		failedBatch.clear();
		LOGGER.debug("Checking and submiting orders.");

		// removing orders that reach timeout
		checkPedingOrders();
		for (Order order : new ArrayList<Order>(orderRepository.getOrdersIn(OrderState.OPEN))) {
			if (!order.getState().equals(OrderState.OPEN)) {
				LOGGER.debug("The order " + order.getId() + " is no longer open.");
				continue;
			}

			LOGGER.debug(order.getId() + " being considered for scheduling.");
			Map<String, String> xOCCIAtt = order.getxOCCIAtt();
			if (order.isIntoValidPeriod()) {
				boolean isFulfilled = false;
				if (order.isLocal()) {
					for (String keyAttributes : OrderAttribute.getValues()) {
						xOCCIAtt.remove(keyAttributes);
					}

					String requirements = order.getRequirements();
					List<FederationMember> allowedFederationMembers = getAllowedFederationMembers(requirements);

					if (RequirementsHelper.matchLocation(requirements,
							properties.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {

						if (!isFulfilled
								&& !failedBatch.batchExists(order.getBatchId(), FailedBatchType.FEDERATION_USER)) {
							isFulfilled = createLocalInstanceWithFederationUser(order);
							if (!isFulfilled) {
								failedBatch.failBatch(order.getBatchId(), FailedBatchType.FEDERATION_USER);
							}
						}
					}
					if (!isFulfilled) {
						createAsynchronousRemoteInstance(order, allowedFederationMembers);
					}
					allFulfilled &= isFulfilled;
				} else { // it is served Order
					if (!failedBatch.batchExists(order.getBatchId(), FailedBatchType.FEDERATION_USER)) {
						isFulfilled = createLocalInstanceWithFederationUser(order);
						if (!isFulfilled) {
							failedBatch.failBatch(order.getBatchId(), FailedBatchType.FEDERATION_USER);
						}
					}
					allFulfilled &= isFulfilled;
				}
			} else if (order.isExpired()) {
				order.setState(OrderState.CLOSED);
			} else {
				allFulfilled = false;
			}
		}
		if (allFulfilled) {
			LOGGER.info("All orders fulfilled.");
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

	protected void monitorServedOrders() {
		LOGGER.info("Monitoring served orders.");

		List<Order> servedOrders = orderRepository.getAllServedOrders();
		for (Order order : servedOrders) {
			if (!isInstanceBeingUsedByRemoteMember(order)) {
				LOGGER.debug("The instance " + order.getInstanceId() + " is not being used anymore by "
						+ order.getRequestingMemberId() + " and will be removed.");
				if (order.getInstanceId() != null) {
					try {
						removeInstanceForRemoteMember(order.getInstanceId());						
					} catch (Exception e) {}
				}
				orderRepository.exclude(order.getId());
			}
		}

		if (orderRepository.getAllServedOrders().isEmpty()) {
			LOGGER.info("There are no remote orders. Canceling remote order monitoring.");
			servedOrderMonitoringTimer.cancel();
		}
	}

	private boolean isInstanceBeingUsedByRemoteMember(Order servedOrder) {
		try {
			String globalInstanceId = servedOrder.getGlobalInstanceId();
			ManagerPacketHelper.checkIfInstanceIsBeingUsedByRemoteMember(globalInstanceId, servedOrder, packetSender);
			return true;
		} catch (OCCIException e) {
			return false;
		}
	}

	protected void checkPedingOrders() {
		Collection<ForwardedOrder> forwardedOrders = asynchronousOrders.values();
		for (ForwardedOrder forwardedOrder : forwardedOrders) {
			if (timoutReached(forwardedOrder.getTimeStamp()) 
					&& forwardedOrder.getOrder().getState().equals(OrderState.PENDING)){
				LOGGER.debug("The forwarded order " + forwardedOrder.getOrder().getId()
						+ " reached timeout and is been removed from asynchronousOrders list.");
				forwardedOrder.getOrder().setState(OrderState.OPEN);
			}
		}
	}

	private boolean timoutReached(long timeStamp) {
		long nowMilli = dateUtils.currentTimeMillis();
		Date now = new Date(nowMilli);

		String asyncOrderWaitingIntervalStr = properties
				.getProperty(ConfigurationConstants.ASYNC_ORDER_WAITING_INTERVAL_KEY);
		final int asyncRequestWaitingInterval = asyncOrderWaitingIntervalStr == null
				? DEFAULT_ASYNC_ORDER_WAITING_INTERVAL : Integer.valueOf(asyncOrderWaitingIntervalStr);

		Calendar c = Calendar.getInstance();
		c.setTime(new Date(timeStamp));		
		c.add(Calendar.MILLISECOND, asyncRequestWaitingInterval);
		return now.after(c.getTime());
	}

	protected boolean createLocalInstanceWithFederationUser(Order order) {
		LOGGER.info("Submitting order " + order + " with federation user locally.");

		FederationMember member = null;
		boolean isRemoteDonation = !properties.getProperty("xmpp_jid").equals(order.getRequestingMemberId());
		try {
			member = getFederationMember(order.getRequestingMemberId());
		} catch (Exception e) {
		}

		if (isRemoteDonation && !validator.canDonateTo(member, order.getFederationToken())) {
			return false;
		}

		try {
			return createInstance(order);
		} catch (Exception e) {
			LOGGER.info("Could not create instance with federation user locally. ", e);
			return false;
		}
	}

	protected boolean createInstance(Order order) {

		LOGGER.debug("Submiting order with categories: " + order.getCategories() + " and xOCCIAtt: "
				+ order.getxOCCIAtt() + " for requesting member: " + order.getRequestingMemberId()
				+ " with requestingToken " + order.getRequestingMemberId());

		try {
			try {
				String command = createUserDataUtilsCommand(order);
				order.putAttValue(OrderAttribute.USER_DATA_ATT.getValue(), command);
				order.addCategory(new Category(OrderConstants.USER_DATA_TERM, OrderConstants.SCHEME,
						OrderConstants.MIXIN_CLASS));
			} catch (Exception e) {
				LOGGER.warn("Exception while creating userdata.", e);
				return false;
			}

			Token federationUserToken = getFederationUserToken(order);
			String localImageId = getLocalImageId(order.getCategories(), federationUserToken);
			List<Category> categories = new LinkedList<Category>();
			for (Category category : order.getCategories()) {
				if (category.getScheme().equals(OrderConstants.TEMPLATE_OS_SCHEME)) {
					continue;
				}
				categories.add(category);
			}

			Map<String, String> xOCCIAttCopy = new HashMap<String, String>(order.getxOCCIAtt());
			removePublicKeyFromCategoriesAndAttributes(xOCCIAttCopy, categories);
			String instanceId = computePlugin.requestInstance(federationUserToken, categories, xOCCIAttCopy,
					localImageId);
			order.setState(OrderState.SPAWNING);
			order.setInstanceId(instanceId);
			order.setProvidingMemberId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));
			execBenchmark(order);
			return instanceId != null;
		} catch (OCCIException e) {
			ErrorType errorType = e.getType();
			if (errorType == ErrorType.QUOTA_EXCEEDED) {
				LOGGER.warn("Order failed locally for quota exceeded.", e);
				ArrayList<Order> ordersWithInstances = new ArrayList<Order>(
						orderRepository.getOrdersIn(OrderState.FULFILLED, OrderState.DELETED));
				Order orderToPreempt = prioritizationPlugin.takeFrom(order, ordersWithInstances);
				if (orderToPreempt == null) {
					throw e;
				}
				preemption(orderToPreempt);
				return createInstance(order);
			} else if (errorType == ErrorType.UNAUTHORIZED) {
				LOGGER.warn("Order failed locally for user unauthorized.", e);
				return false;
			} else if (errorType == ErrorType.BAD_REQUEST) {
				LOGGER.warn("Order failed locally for image not found.", e);
				return false;
			} else if (errorType == ErrorType.NO_VALID_HOST_FOUND) {
				LOGGER.warn(
						"Order failed because no valid host was found," + " we will try to wake up a sleeping host.",
						e);
				wakeUpSleepingHosts(order);
				return false;
			} else {
				LOGGER.warn("Order failed locally for an unknown reason.", e);
				return false;
			}
		}
	}

	public void setPacketSender(AsyncPacketSender packetSender) {
		this.packetSender = packetSender;
	}

	public void setOrders(OrderRepository orders) {
		this.orderRepository = orders;
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

	protected List<Order> getServedOrders() {
		return orderRepository.getAllServedOrders();
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
		List<Order> ordersFromUser = getOrdersFromUser(authToken);
		List<Instance> allFullInstances = new ArrayList<Instance>();
		LOGGER.debug("Getting all instances and your information.");
		for (Order order : ordersFromUser) {
			Instance instance = null;
			if (isFulfilledByLocalMember(order)) {
				LOGGER.debug(order.getInstanceId() + " is local, getting its information in the local cloud.");
				instance = this.computePlugin.getInstance(getFederationUserToken(order), order.getInstanceId());

				Map<String, String> serviceAddresses = getExternalServiceAddresses(order.getId());
				if (serviceAddresses != null) {
					instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, serviceAddresses.get(SSH_SERVICE_NAME));
					instance.addAttribute(Instance.SSH_USERNAME_ATT, getSSHCommonUser());
					serviceAddresses.remove(SSH_SERVICE_NAME);
					instance.addAttribute(Instance.EXTRA_PORTS_ATT, new JSONObject(serviceAddresses).toString());
				}

				Category osCategory = getImageCategory(order.getCategories());
				if (osCategory != null) {
					instance.addResource(ResourceRepository.createImageResource(osCategory.getTerm()));
				}
			} else {
				LOGGER.debug(order.getInstanceId() + " is remote, going out to " + order.getProvidingMemberId()
						+ " to get its information.");
				instance = getRemoteInstance(order);
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

	public List<AccountingInfo> getAccountingInfo(String federationAccessId) {
		Token federationToken = getTokenFromFederationIdP(federationAccessId);
		if (federationToken == null) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
		}
		
		if (!isAdminUser(federationToken)) {
			throw new OCCIException(ErrorType.FORBIDDEN, ResponseConstants.FORBIDDEN);
		}
		return accountingPlugin.getAccountingInfo();
	}

	protected boolean isAdminUser(Token federationToken) {
		String adminUserStr = properties.getProperty(ConfigurationConstants.ADMIN_USERS);
		if (adminUserStr == null || adminUserStr.isEmpty()) {
			return true;
		}
		System.out.println(adminUserStr);
		String normalizedUser = MapperHelper.normalizeUser(federationToken.getUser());
		StringTokenizer st = new StringTokenizer(adminUserStr, ";");
		while (st.hasMoreTokens()) {
			if (normalizedUser.equals(st.nextToken().trim())) {
				return true;
			}
		}
		return false;
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

	private static class ForwardedOrder {

		private Order order;
		private long timeStamp;
		private List<String> membersServered;


		public ForwardedOrder(Order order, long timeStamp) {
			this.order = order;
			this.timeStamp = timeStamp;
			this.membersServered = new ArrayList<String>();
		}

		public void setTimeStamp(long timeStamp) {
			this.timeStamp = timeStamp;
		}

		public Order getOrder() {
			return order;
		}

		public long getTimeStamp() {
			return timeStamp;
		}

		public List<String> getMembersServered() {
			return membersServered;
		}

		public void addMembersServered(List<String> membersServered) {
			if (membersServered == null || !membersServered.contains(order.getProvidingMemberId())) {
				this.membersServered.add(order.getProvidingMemberId());
			}
			if (membersServered == null) {
				return;
			}
			this.membersServered.addAll(membersServered);
		}
	}

}
