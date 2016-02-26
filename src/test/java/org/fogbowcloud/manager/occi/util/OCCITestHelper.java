package org.fogbowcloud.manager.occi.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.CurrentThreadExecutorService;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIApplication;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.order.OrderRepository;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.restlet.Component;
import org.restlet.data.Protocol;

public class OCCITestHelper {

	public static final String FOGBOW_SMALL_IMAGE = "fogbow_small";
	public static final String MEMBER_ID = "memberId";
	public static final int ENDPOINT_PORT = PluginHelper.getAvailablePort();
	public static final String ACCESS_TOKEN = "HgjhgYUDFTGBgrbelihBDFGB40uyrb";
	public static final String POST_FED_ACCESS_TOKEN = "POST_HgjhgYUDFTGBgrbelihBDFGB40uyrb";
	public static final String INVALID_TOKEN = "invalid-token";
	public static final String URI_FOGBOW_ORDER = "http://localhost:" + ENDPOINT_PORT + "/" + OrderConstants.TERM
			+ "/";
	public static final String URI_FOGBOW_COMPUTE = "http://localhost:" + ENDPOINT_PORT + "/compute/";
	public static final String URI_FOGBOW_MEMBER = "http://localhost:" + ENDPOINT_PORT + "/member";
	public static final String URI_FOGBOW_TOKEN = "http://localhost:" + ENDPOINT_PORT + "/token";
	public static final String URI_FOGBOW_QUERY = "http://localhost:" + ENDPOINT_PORT + "/-/";
	public static final String URI_FOGBOW_LOCAL_QUOTA = "http://localhost:" + ENDPOINT_PORT + "/quota";
	public static final String URI_FOGBOW_QUERY_TYPE_TWO = "http://localhost:" + ENDPOINT_PORT
			+ "/.well-known/org/ogf/occi/-/";
	public static final String USER_MOCK = "user_mock";

	private Component component;
	private OrderRepository orders;

	public void initializeComponentExecutorSameThread(ComputePlugin computePlugin, IdentityPlugin identityPlugin,
			AuthorizationPlugin authorizationPlugin, BenchmarkingPlugin benchmarkingPlugin,
			MapperPlugin mapperPlugin) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY, MEMBER_ID);
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY, DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + FOGBOW_SMALL_IMAGE, "{cpu=1,mem=100}");

		ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
		Mockito.when(executor.scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.anyLong(),
				Mockito.any(TimeUnit.class))).thenAnswer(new Answer<Future<?>>() {
					@Override
					public Future<?> answer(InvocationOnMock invocation) throws Throwable {
						Runnable runnable = (Runnable) invocation.getArguments()[0];
						runnable.run();
						return null;
					}
				});

		ExecutorService benchmarkExecutor = new CurrentThreadExecutorService();

		ManagerController facade = new ManagerController(properties, executor);
		ResourceRepository.init(properties);
		facade.setComputePlugin(computePlugin);
		facade.setLocalCredentailsPlugin(mapperPlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setBenchmarkingPlugin(benchmarkingPlugin);
		facade.setBenchmarkExecutor(benchmarkExecutor);

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public void initializeComponent(ComputePlugin computePlugin, IdentityPlugin identityPlugin,
			AuthorizationPlugin authorizationPlugin) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY, DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + FOGBOW_SMALL_IMAGE, "{cpu=1,mem=100}");

		ResourceRepository.init(properties);

		ManagerController facade = new ManagerController(properties, Mockito.mock(ScheduledExecutorService.class));
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public ManagerController initializeComponentCompute(ComputePlugin computePlugin, IdentityPlugin identityPlugin,
			AuthorizationPlugin authorizationPlugin, ImageStoragePlugin imageStoragePlugin,
			AccountingPlugin accountingPlugin, BenchmarkingPlugin benchmarkingPlugin, Map<String, List<Order>> ordersToAdd,
			MapperPlugin mapperPlugin) throws Exception {
		return initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, accountingPlugin, benchmarkingPlugin, ordersToAdd,
				mapperPlugin, null);
	}
	public ManagerController initializeComponentCompute(ComputePlugin computePlugin, IdentityPlugin identityPlugin,
			AuthorizationPlugin authorizationPlugin, ImageStoragePlugin imageStoragePlugin,
			AccountingPlugin accountingPlugin, BenchmarkingPlugin benchmarkingPlugin, Map<String, List<Order>> ordersToAdd,
			MapperPlugin mapperPlugin, Properties properties) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		if (properties == null) {
			properties = new Properties();
			properties.put(ConfigurationConstants.XMPP_JID_KEY, MEMBER_ID);
			properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY, DefaultDataTestHelper.SERVER_HOST);
			properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
					String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
			
			properties.put(ConfigurationConstants.INSTANCE_DATA_STORE_URL, "jdbc:h2:file:./src/test/resources/fedInstance.db");
			properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_KEY_PATH, "./src/test/resources/post-compute/occi-fake-resources.txt");
			//Image OCCI to FogbowOrder
			properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + "fbc85206-fbcc-4ad9-ae93-54946fdd5df7", "fogbow-ubuntu");
			properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + "m1-xlarge", "Glue2vCPU >= 4 && Glue2RAM >= 8192");
			properties.put(ConfigurationConstants.OCCI_EXTRA_RESOURCES_PREFIX + "m1-medium", "Glue2vCPU >= 2 && Glue2RAM >= 8096");
		}

		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setLocalCredentailsPlugin(mapperPlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setImageStoragePlugin(imageStoragePlugin);
		facade.setAccountingPlugin(accountingPlugin);
		facade.setBenchmarkingPlugin(benchmarkingPlugin);

		orders = new OrderRepository();
		facade.setOrders(orders);
		for (Entry<String, List<Order>> entry : ordersToAdd.entrySet()) {
			for (Order order : entry.getValue())
				orders.addOrder(entry.getKey(), order);
		}

		ResourceRepository.init(properties);

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();

		return facade;
	}

	public void initializeComponentMember(ComputePlugin computePlugin, IdentityPlugin identityPlugin,
			AuthorizationPlugin authorizationPlugin, AccountingPlugin accountingPlugin,
			List<FederationMember> federationMembers, MapperPlugin mapperPlugin) throws Exception {
		initializeComponentMember(computePlugin, identityPlugin, authorizationPlugin,
				accountingPlugin, federationMembers, mapperPlugin, null);
	}
	
	public void initializeComponentMember(ComputePlugin computePlugin, IdentityPlugin identityPlugin,
			AuthorizationPlugin authorizationPlugin, AccountingPlugin accountingPlugin,
			List<FederationMember> federationMembers, MapperPlugin mapperPlugin, AsyncPacketSender packetSender) throws Exception {
		component = new Component();
		component.getServers().add(Protocol.HTTP, ENDPOINT_PORT);

		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		ManagerController facade = new ManagerController(properties);
		facade.setComputePlugin(computePlugin);
		facade.setLocalIdentityPlugin(identityPlugin);
		facade.setFederationIdentityPlugin(identityPlugin);
		facade.setAuthorizationPlugin(authorizationPlugin);
		facade.setAccountingPlugin(accountingPlugin);
		facade.setLocalCredentailsPlugin(mapperPlugin);
		facade.updateMembers(federationMembers);
		
		if (packetSender != null) {
			facade.setPacketSender(packetSender);
		}

		component.getDefaultHost().attach(new OCCIApplication(facade));
		component.start();
	}

	public void stopComponent() throws Exception {
		if (component == null) {
			return;
		}
		component.stop();
	}

	public static List<String> getLocationIds(HttpResponse response) throws ParseException, IOException {
		String responseStr = "";
		try {
			responseStr = EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
			return new ArrayList<String>();
		}

		List<String> orderIds = new ArrayList<String>();
		if (responseStr.contains(HeaderUtils.X_OCCI_LOCATION_PREFIX)) {
			String[] tokens = responseStr.split(HeaderUtils.X_OCCI_LOCATION_PREFIX);

			for (int i = 0; i < tokens.length; i++) {
				if (!tokens[i].equals("")) {
					orderIds.add(tokens[i].trim());
				}
			}
		}
		return orderIds;
	}

	public static List<String> getOrderIdsPerLocationHeader(HttpResponse response)
			throws ParseException, IOException {
		String locationHeaderValue = "";
		Header[] allHeaders = response.getAllHeaders();
		for (Header header : allHeaders) {
			if (header.getName().equals("Location")) {
				locationHeaderValue = header.getValue();
			}
		}

		List<String> orderIds = new ArrayList<String>();
		if (locationHeaderValue.contains(OrderConstants.TERM)) {
			String[] tokens = locationHeaderValue.split(",");
			for (int i = 0; i < tokens.length; i++) {
				if (!tokens[i].equals("")) {
					orderIds.add(tokens[i].trim());
				}
			}
		}
		return orderIds;
	}

	public static String getInstanceIdPerLocationHeader(HttpResponse response) throws ParseException, IOException {
		Header[] allHeaders = response.getAllHeaders();
		for (Header header : allHeaders) {
			if (header.getName().equals("Location")) {
				return header.getValue().replace(URI_FOGBOW_COMPUTE, "");
			}
		}

		return null;
	}
	
	public static String getOCCIAttByHeader(HttpResponse response, String attribute) throws ParseException, IOException {
		Header[] allHeaders = response.getAllHeaders();
		for (Header header : allHeaders) {
			if (header.getName().equals(attribute)) {
				return header.getValue().replace(OCCIHeaders.X_OCCI_ATTRIBUTE, "");
			}
		}

		return null;
	}
	
	public static String getOCCIAttByBody(HttpResponse response, String attribute) throws ParseException, IOException {
		String responseStr = "";
		String attValue = null;
		try {
			responseStr = EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
			return attValue;
		}
		
		Scanner sc = new Scanner(responseStr);
		boolean notFound = true;
		while (sc.hasNextLine() && notFound) {
			String line = sc.nextLine().trim();
			if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE) && line.contains(attribute)) {
				String[] tokens = line.split(attribute+"=");
				attValue = tokens.length > 1 ? tokens[1] : null;
				notFound = true;
			}
		}
		sc.close();
		
		return attValue;
	}

	public static Map<String, String> getLinkAttributes(HttpResponse response) throws ParseException, IOException {

		Map<String, String> attsMap = new HashMap<String, String>();

		String responseStr = "";
		try {
			responseStr = EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
			return attsMap;
		}
		Scanner sc = new Scanner(responseStr);
		boolean notFound = true;
		while (sc.hasNextLine() && notFound) {
			String line = sc.nextLine().trim();
			if (line.startsWith("Link:")) {
				String[] tokens = line.split("Link:");
				String[] attributes = tokens.length > 1 ? tokens[1].trim().split(";") : new String[0];
				for (int i = 0; i < attributes.length; i++) {
					if (!attributes[i].equals("")) {
						String[] keyValue = attributes[i].trim().split("=");
						attsMap.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
					}
				}
				notFound = false;
			}
		}
		sc.close();
		return attsMap;
	}

	public String getStateFromOrderDetails(String orderDetails) {
		StringTokenizer st = new StringTokenizer(orderDetails, "\n");
		Map<String, String> occiAttributes = new HashMap<String, String>();
		while (st.hasMoreElements()) {
			String line = st.nextToken().trim();
			if (line.contains(OCCIHeaders.X_OCCI_ATTRIBUTE)) {
				StringTokenizer st2 = new StringTokenizer(line, ":");
				st2.nextToken(); // X-OCCI-Attribute
				String attToValue = st2.nextToken().trim();
				String[] attAndValue = attToValue.split("=");
				if (attAndValue.length == 2) {
					occiAttributes.put(attAndValue[0], attAndValue[1].replaceAll("\"", ""));
				} else {
					occiAttributes.put(attAndValue[0], "");
				}
			}
		}
		return occiAttributes.get(OrderAttribute.STATE.getValue());
	}

	public static List<URI> getURIList(HttpResponse response) throws URISyntaxException {
		String responseStr = "";
		try {
			responseStr = EntityUtils.toString(response.getEntity(), String.valueOf(Charsets.UTF_8));
		} catch (Exception e) {
			return new ArrayList<URI>();
		}

		List<URI> orderURIs = new ArrayList<URI>();
		String[] tokens = responseStr.trim().split("\n");

		for (int i = 0; i < tokens.length; i++) {
			if (!tokens[i].equals("")) {
				orderURIs.add(new URI(tokens[i].trim()));
			}
		}

		return orderURIs;
	}
}
