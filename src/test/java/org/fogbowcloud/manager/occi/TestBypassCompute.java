package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackOCCIComputePlugin;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenstackOCCITestHelper;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCIComputeApplication;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestBypassCompute {

	private static final int LITTLE_SCHEDULE_TIME = 100;
	private static final String FIRST_INSTANCE_ID = "first-instance";
	private static final String SECOND_INSTANCE_ID = "second-instance";
	private static final String THIRD_INSTANCE_ID = "third-instance";
	private static final String FOURTH_INSTANCE_ID = "fourth-instance";
	private static final String FIFTH_INSTANCE_ID = "fifth-instance";
	private PluginHelper pluginHelper = new PluginHelper();
	private OCCITestHelper helper;
	private OpenStackOCCIComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private Token defaultToken;
	private ImageStoragePlugin imageStoragePlugin;
	private MapperPlugin mapperPlugin;
	
	@Before
	public void setup() throws Exception{
		setup(new ArrayList<Order>());
	}
	
	@SuppressWarnings("unchecked")
	private void setup(List<Order> orders) throws Exception {		
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_URL_KEY, PluginHelper.COMPUTE_OCCI_URL);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, OCCIComputeApplication.INSTANCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, OCCIComputeApplication.OS_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, OCCIComputeApplication.RESOURCE_SCHEME);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, OCCIComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(OpenStackConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);
		properties.put(ConfigurationConstants.SCHEDULER_PERIOD_KEY, LITTLE_SCHEDULE_TIME);
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		
		defaultToken = new Token(PluginHelper.ACCESS_ID, PluginHelper.USERNAME,
				DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>());
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavorSmall.setId(SECOND_INSTANCE_ID);
		flavors.add(flavorSmall); 
		flavors.add(new Flavor("medium", "2", "2000", "20"));
		flavors.add(new Flavor("big", "4", "4000", "40"));
		
		this.computePlugin = OpenstackOCCITestHelper.createComputePlugin(properties, flavors);
				
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(PluginHelper.ACCESS_ID)).thenReturn(defaultToken);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(defaultToken);
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");
		
		// three first generated instance ids
		List<String> expectedInstanceIds = new ArrayList<String>();
		expectedInstanceIds.add(FIRST_INSTANCE_ID);
		expectedInstanceIds.add(SECOND_INSTANCE_ID);
		expectedInstanceIds.add(THIRD_INSTANCE_ID);
		expectedInstanceIds.add(FOURTH_INSTANCE_ID);
		expectedInstanceIds.add(FIFTH_INSTANCE_ID);
		
		//initializing fake Cloud Compute Application
		pluginHelper.initializeOCCIComputeComponent(expectedInstanceIds);
		
		mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(new HashMap<String, String>());
		
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		imageStoragePlugin = Mockito.mock(ImageStoragePlugin.class);
		Mockito.when(imageStoragePlugin.getLocalId(Mockito.any(
				Token.class), Mockito.anyString())).thenReturn(PluginHelper.CIRROS_IMAGE_TERM);
		
		Map<String, List<Order>> ordersToAdd = new HashMap<String, List<Order>>();
		ordersToAdd.put(OCCITestHelper.USER_MOCK, orders);
		
		//initializing fogbow OCCI Application
		helper = new OCCITestHelper();
		helper.initializeComponentCompute(computePlugin, identityPlugin, identityPlugin, authorizationPlugin,
				imageStoragePlugin, Mockito.mock(AccountingPlugin.class), Mockito.mock(AccountingPlugin.class),
				Mockito.mock(BenchmarkingPlugin.class), ordersToAdd, mapperPlugin);
	}

	@After
	public void tearDown() throws Exception{
		pluginHelper.disconnectComponent();
		helper.stopComponent();
	}
	
	@Test
	public void testBypassGetComputeOK() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();	
		
		Map<String, String> xOCCIAttr = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));

		//checking if machines were added
		Assert.assertEquals(3, computePlugin.getInstances(defaultToken).size());
		
		//getting instances through fogbow endpoint		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
	
		//checking instances are OK
		Assert.assertEquals(3, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetBypassGetComputeOK() throws Exception {
		//adding instance through fogbow endpoint
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		post.addHeader(OCCIHeaders.CATEGORY, new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS).toHeader());
		post.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS).toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.REQUIREMENTS.getValue() + "="
				+ RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0");
		
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE , OrderAttribute.RESOURCE_KIND.getValue() 
				+ "=" + OrderConstants.COMPUTE_TERM);
		
		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderIDs = OCCITestHelper.getOrderIdsPerLocationHeader(response);
		
		Assert.assertEquals(1, orderIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		
		Map<String, String> xOCCIAttr = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		xOCCIAttr.put(OrderAttribute.RESOURCE_KIND.getValue(), OrderConstants.COMPUTE_TERM);
		
		boolean fail = true;
		for (int i = 0; i < 5; i++) {
			Thread.sleep(LITTLE_SCHEDULE_TIME / 2);
			if (computePlugin.getInstances(defaultToken).size() == 1) {
				fail = false;
				break;				
			}
		}
		
		if (fail) {
			Assert.fail();
		}
		
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		
		//checking if machines were added
		Assert.assertEquals(3, computePlugin.getInstances(defaultToken).size());
		
		//getting instances through fogbow endpoint		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);
	
		//checking instances are OK
		Assert.assertEquals(3, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testBypassGetSpecificComputeOK() throws URISyntaxException, HttpException, IOException, InterruptedException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		
		Map<String, String> xOCCIAttr = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		
		//checking if machine was added
		Assert.assertEquals(1, computePlugin.getInstances(defaultToken).size());
		
		//getting specific instance through fogbow endpoint		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
	
		//checking instance details
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());		
		String instanceDetails = EntityUtils.toString(response.getEntity());
		Assert.assertEquals(FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.ID_CORE_ATTRIBUTE_OCCI));
		Assert.assertEquals(1, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.CORE_ATTRIBUTE_OCCI)));
		Assert.assertEquals(2, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.MEMORY_ATTRIBUTE_OCCI)));
		Assert.assertEquals(64, Integer.parseInt(pluginHelper.getAttValueFromInstanceDetails(instanceDetails,
				OCCIComputeApplication.ARCHITECTURE_ATTRIBUTE_OCCI)));
		Assert.assertEquals("server-" + FIRST_INSTANCE_ID,
				pluginHelper.getAttValueFromInstanceDetails(instanceDetails, OCCIComputeApplication.HOSTNAME_ATTRIBUTE_OCCI));
	}
	
	@Test
	public void testBypassGetComputeWithEmptyToken() throws URISyntaxException, HttpException, IOException {
		//getting instances through fogbow endpoint without auth-token
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);
	
		//checking unauthorized status
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
//		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
//				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
//		Assert.assertEquals(ResponseConstants.UNAUTHORIZED,
//				EntityUtils.toString(response.getEntity()));
	}
	
	@Test
	public void testGetSpecificInstanceNotFound() throws Exception {
		//getting not existing instance through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteComputeNotCreatedThroughFogbow() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();	
		Map<String, String> xOCCIAttr = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		
		//checking if instances were added
		Assert.assertEquals(3, computePlugin.getInstances(defaultToken).size());
		
		//removing instances through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(0, computePlugin.getInstances(defaultToken).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteComputeCreatedThroughFogbowAndCompute() throws URISyntaxException,
			HttpException, IOException, InterruptedException {
		//adding one instance through fogbow endpoint
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_ORDER);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		post.addHeader(OCCIHeaders.CATEGORY, new Category(OrderConstants.TERM,
				OrderConstants.SCHEME, OrderConstants.KIND_CLASS).toHeader());
		post.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS).toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.REQUIREMENTS.getValue() + "="
				+ RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0");
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, OrderAttribute.RESOURCE_KIND.getValue() + "=" 
				+ OrderConstants.COMPUTE_TERM);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		List<String> orderIDs = OCCITestHelper.getOrderIdsPerLocationHeader(response);

		Assert.assertEquals(1, orderIDs.size());
		Assert.assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());
		
		Thread.sleep(LITTLE_SCHEDULE_TIME + 200);
		
		//adding two instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();
		Map<String, String> xOCCIAttr = new HashMap<String, String>();	
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		
		//checking if instance were added
		Assert.assertEquals(3, computePlugin.getInstances(defaultToken).size());
		
		//removing instances through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = HttpClients.createMinimal();
		response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(0, computePlugin.getInstances(defaultToken).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = HttpClients.createMinimal();
	
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteSpecificComputeNotCreatedThroughFogbow() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		
		Map<String, String> xOCCIAttr = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
		
		//checking if instances were added
		Assert.assertEquals(3, computePlugin.getInstances(defaultToken).size());
		
		//removing first instance through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(2, computePlugin.getInstances(defaultToken).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteLastComputeNotCreatedThroughFogbow() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();	
		
		Map<String, String> xOCCIAttr = new HashMap<String, String>();
		String requirementsStr = RequirementsHelper.GLUE_DISK_TERM + " >= 10 && "
				+ RequirementsHelper.GLUE_MEM_RAM_TERM + " > 500 && "
				+ RequirementsHelper.GLUE_VCPU_TERM + " > 0";
		xOCCIAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				defaultToken, categories, xOCCIAttr, PluginHelper.CIRROS_IMAGE_TERM));
				
		//checking if instances were added
		Assert.assertEquals(1, computePlugin.getInstances(defaultToken).size());
		
		//removing first instance through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(0, computePlugin.getInstances(defaultToken).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = HttpClients.createMinimal();
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getLocationIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassQueryInterface() throws URISyntaxException, HttpException, IOException {
		// getting query interface
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		String resourcesStr = EntityUtils.toString(response.getEntity());
		List<Resource> availableResources = getResourcesFromStr(resourcesStr);		
		
		List<Resource> fogbowResources = ResourceRepository.getInstance().getAll();
		List<Resource> occiFakeResourceByApp = OCCIComputeApplication.getResources();	
		
		for (Resource resource : availableResources) {
			Assert.assertTrue(fogbowResources.contains(resource) || occiFakeResourceByApp.contains(resource));
		}	
		
		/*
		 * Joining fogbow and compute resources because they may repeat
		 * resources according to extra-occi-resource configuration for
		 * supporting post-compute
		 */		
		List<Resource> resources = new ArrayList<Resource>();
		for (Resource fogbowResource : fogbowResources) {			
			resources.add(fogbowResource);
		}
		for (Resource fakeResourceByApp : occiFakeResourceByApp) {
			if (!resources.contains(fakeResourceByApp)) {
				resources.add(fakeResourceByApp);
			}
		}
		Assert.assertEquals(resources.size(), availableResources.size());
	}

	//TODO duplicated code at QueryServerResource 
	private List<Resource> getResourcesFromStr(String resourcesStr) {
		String[] lines = resourcesStr.split("\n");
		List<Resource> resources = new ArrayList<Resource>();
		for (String line : lines) {
			if (line.contains(OCCIHeaders.CATEGORY)){
				resources.add(new Resource(line.substring(line.indexOf(":") + 1)));
			}
		}		
		return resources;
	}
}
