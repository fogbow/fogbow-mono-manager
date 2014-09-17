package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.openstack.OpenStackOCCIComputePlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;
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
	
	@Before
	public void setup() throws Exception{
		setup(new ArrayList<Request>());
	}
	
	private void setup(List<Request> requests) throws Exception {		
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.COMPUTE_OCCI_URL_KEY, PluginHelper.COMPUTE_OCCI_URL);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_INSTANCE_SCHEME_KEY, OCCIComputeApplication.INSTANCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_OS_SCHEME_KEY, OCCIComputeApplication.OS_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_RESOURCE_SCHEME_KEY, OCCIComputeApplication.RESOURCE_SCHEME);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_SMALL_KEY, OCCIComputeApplication.SMALL_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_MEDIUM_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_FLAVOR_LARGE_KEY, OCCIComputeApplication.MEDIUM_FLAVOR_TERM);
		properties.put(ConfigurationConstants.COMPUTE_OCCI_IMAGE_PREFIX + PluginHelper.LINUX_X86_TERM, PluginHelper.CIRROS_IMAGE_TERM);
		properties.put(ConfigurationConstants.SCHEDULER_PERIOD_KEY, LITTLE_SCHEDULE_TIME);
		properties.put(ConfigurationConstants.SSH_PRIVATE_HOST_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		
		computePlugin = new OpenStackOCCIComputePlugin(properties);
		
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(PluginHelper.ACCESS_ID)).thenReturn(
				new Token(PluginHelper.ACCESS_ID, PluginHelper.USERNAME, DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
						new HashMap<String, String>()));
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");
		
		// three first generated instance ids
		List<String> expectedInstanceIds = new ArrayList<String>();
		expectedInstanceIds.add(FIRST_INSTANCE_ID);
		expectedInstanceIds.add(SECOND_INSTANCE_ID);
		expectedInstanceIds.add(THIRD_INSTANCE_ID);
		expectedInstanceIds.add(FOURTH_INSTANCE_ID);
		expectedInstanceIds.add(FIFTH_INSTANCE_ID);
		
		//initializing fake Cloud Compute Application
		pluginHelper.initializeComputeComponent(expectedInstanceIds);
		
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		//initializing fogbow OCCI Application
		helper = new OCCITestHelper();
		helper.initializeComponentCompute(computePlugin, identityPlugin, authorizationPlugin, requests);
	}
	
	@After
	public void tearDown() throws Exception{
		pluginHelper.disconnectComponent();
		helper.stopComponent();
	}
	
	
	@Test
	public void testBypassPostComputeOK() throws URISyntaxException, HttpException, IOException {
		//post compute through fogbow endpoint
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		httpPost.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS).toHeader());
		
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpPost);
		
		Assert.assertEquals(PluginHelper.COMPUTE_OCCI_URL + OpenStackOCCIComputePlugin.COMPUTE_ENDPOINT
				+ FIRST_INSTANCE_ID, response.getFirstHeader(HeaderUtils.normalize("Location")).getValue());
		Assert.assertEquals(1, response.getHeaders(HeaderUtils.normalize("Location")).length);
		Assert.assertTrue(response.getEntity().getContentType().getValue().startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(1, response.getHeaders(HeaderUtils.normalize("Content-Type")).length);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		String message = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		Assert.assertEquals(ResponseConstants.OK, message);
	}
	
	@Test
	public void testBypassPostComputeWithWrongMediaTypeTextPlain() throws URISyntaxException, HttpException, IOException {
		//post compute through fogbow endpoint
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.CONTENT_TYPE, "invalid-type");
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		httpPost.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS).toHeader());
		
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpPost);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassPostComputeWithoutMediaType() throws URISyntaxException, HttpException, IOException {
		//post compute through fogbow endpoint
		HttpPost httpPost = new HttpPost(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpPost.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		httpPost.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS).toHeader());
		
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpPost);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassGetComputeOK() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));

		//checking if machines were added
		Assert.assertEquals(3, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//getting instances through fogbow endpoint		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
	
		//checking instances are OK
		Assert.assertEquals(3, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetBypassGetComputeOK() throws Exception {
		//adding instance through fogbow endpoint
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		post.addHeader(OCCIHeaders.CATEGORY, new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS).toHeader());
		post.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS).toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = OCCITestHelper.getRequestIds(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		Thread.sleep(LITTLE_SCHEDULE_TIME + 15);
		
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		
		//checking if machines were added
		Assert.assertEquals(3, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//getting instances through fogbow endpoint		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);
	
		//checking instances are OK
		Assert.assertEquals(3, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testBypassGetSpecificComputeOK() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		
		//checking if machine was added
		Assert.assertEquals(1, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//getting specific instance through fogbow endpoint		
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = new DefaultHttpClient();
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
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);
	
		//checking unauthorized status
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
		Assert.assertTrue(response.getFirstHeader(OCCIHeaders.CONTENT_TYPE).getValue()
				.startsWith(OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE));
		Assert.assertEquals(ResponseConstants.UNAUTHORIZED,
				EntityUtils.toString(response.getEntity()));
	}
	
	@Test
	public void testGetSpecificInstanceNotFound() throws Exception {
		//getting not existing instance through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteComputeNotCreatedThroughFogbow() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		
		//checking if instances were added
		Assert.assertEquals(3, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//removing instances through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(0, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteComputeCreatedThroughFogbowAndCompute() throws URISyntaxException,
			HttpException, IOException, InterruptedException {
		//adding one instance through fogbow endpoint
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		post.addHeader(OCCIHeaders.CATEGORY, new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, RequestConstants.KIND_CLASS).toHeader());
		post.addHeader(OCCIHeaders.CATEGORY, new Category(PluginHelper.LINUX_X86_TERM,
				RequestConstants.TEMPLATE_OS_SCHEME, RequestConstants.MIXIN_CLASS).toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = OCCITestHelper.getRequestIds(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		
		Thread.sleep(LITTLE_SCHEDULE_TIME + 15);
		
		//adding two instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		
		//checking if instance were added
		Assert.assertEquals(3, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//removing instances through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = new DefaultHttpClient();
		response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(0, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteSpecificComputeNotCreatedThroughFogbow() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(SECOND_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		Assert.assertEquals(THIRD_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
		
		//checking if instances were added
		Assert.assertEquals(3, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//removing first instance through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(2, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);

		Assert.assertEquals(2, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassDeleteLastComputeNotCreatedThroughFogbow() throws URISyntaxException, HttpException, IOException {
		//adding instances directly on compute endpoint
		List<Category> categories = new ArrayList<Category>();		
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OpenStackOCCIComputePlugin.getOSScheme(), RequestConstants.MIXIN_CLASS));
		Assert.assertEquals(FIRST_INSTANCE_ID, computePlugin.requestInstance(
				PluginHelper.ACCESS_ID, categories, new HashMap<String, String>()));
				
		//checking if instances were added
		Assert.assertEquals(1, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//removing first instance through fogbow endpoint
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + FIRST_INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(ResponseConstants.OK, EntityUtils.toString(response.getEntity()));
	
		//checking through compute endpoint 
		Assert.assertEquals(0, computePlugin.getInstances(PluginHelper.ACCESS_ID).size());
		
		//checking through fogbow endpoint
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		client = new DefaultHttpClient();
		response = client.execute(httpGet);

		Assert.assertEquals(0, OCCITestHelper.getRequestIds(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testBypassQueryInterface() throws URISyntaxException, HttpException, IOException {
		// getting query interface
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.TEXT_PLAIN_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, PluginHelper.ACCESS_ID);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		String resourcesStr = EntityUtils.toString(response.getEntity());
		List<Resource> availableResources = getResourcesFromStr(resourcesStr);
		
		List<Resource> fogResources = ResourceRepository.getInstance().getAll();
		List<Resource> compResources = OCCIComputeApplication.getResources();

		for (Resource resource : availableResources) {
			Assert.assertTrue(fogResources.contains(resource) || compResources.contains(resource));
		}
		
		/*
		 * expected is (fog + comp - 2) because there are two repeated resources
		 * at both (compute and resource_tpl)
		 */ 
		Assert.assertEquals(fogResources.size() + compResources.size() - 2, availableResources.size());
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
