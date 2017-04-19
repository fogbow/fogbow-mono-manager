package org.fogbowcloud.manager.core.plugins.compute.openstack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.Flavor;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.identity.openstackv2.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.util.HttpPatch;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.NovaV2ComputeApplication;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.restlet.Response;

public class TestNovaV2ComputeOpenStack {

	private static final String GLACE_V2 = "glaceV2";
	private static final String FIRST_INSTANCE_ID = "0";
	private static final String SECOND_INSTANCE_ID = "1";
	private PluginHelper pluginHelper;
	private OpenStackNovaV2ComputePlugin novaV2ComputeOpenStack;
	private Token defaultToken;
	private NovaV2ComputeApplication novaV2Server;
	
	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_NOVAV2_URL_KEY, PluginHelper.COMPUTE_NOVAV2_URL);
		properties.put(OpenStackConfigurationConstants.NETWORK_NOVAV2_URL_KEY, PluginHelper.NETWORK_NOVAV2_URL);
		properties.put(OpenStackConfigurationConstants.COMPUTE_NOVAV2_IMAGE_PREFIX_KEY
				+ PluginHelper.LINUX_X86_TERM, "imageid");
		properties.put(OpenStackConfigurationConstants.COMPUTE_GLANCEV2_URL_KEY, GLACE_V2);
		
		novaV2ComputeOpenStack = new OpenStackNovaV2ComputePlugin(properties);
		
		List<Flavor> flavors = new ArrayList<Flavor>();
		Flavor flavorSmall = new Flavor(OrderConstants.SMALL_TERM, "1", "1000", "10");
		flavorSmall.setId(SECOND_INSTANCE_ID);
		flavors.add(flavorSmall); 
		Flavor flavorMedium = new Flavor("medium", "2", "2000", "20");
		flavorMedium.setId("2");
		flavors.add(flavorMedium);
		Flavor flavorBig = new Flavor("big", "4", "4000", "40");
		flavorBig.setId("3");
		flavors.add(flavorBig);
		novaV2ComputeOpenStack.setFlavors(flavors);
		
		HashMap<String, String> tokenAtt = new HashMap<String, String>();
		tokenAtt.put(KeystoneIdentityPlugin.TENANT_ID, "tenantid");
		tokenAtt.put(KeystoneIdentityPlugin.TENANT_NAME, "tenantname");
		defaultToken = new Token(PluginHelper.ACCESS_ID, new Token.User(PluginHelper.USERNAME, 
				PluginHelper.USERNAME), DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION, tokenAtt);
	
		pluginHelper = new PluginHelper();	
		novaV2Server = pluginHelper.initializeNovaV2ComputeComponent("src/test/resources/openstack");
	}
	
	@After
	public void tearDown() throws Exception {
		pluginHelper.disconnectComponent();
	}
	
	@Test
	public void testRequestOneInstance(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));
		
		Assert.assertEquals(1, novaV2ComputeOpenStack.getInstances(defaultToken).size());
	}
	
	@Test(expected = OCCIException.class)
	public void testCreateRequestWithoutImage() {
		novaV2ComputeOpenStack.requestInstance(defaultToken, new ArrayList<Category>(),
				new HashMap<String, String>(), null);
	}

	@Test(expected = OCCIException.class)
	public void testCreateRequestTenantId() {
		defaultToken.getAttributes().remove(OpenStackNovaV2ComputePlugin.TENANT_ID);
		novaV2ComputeOpenStack.requestInstance(defaultToken, new ArrayList<Category>(),
				new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM);
	}
	
	@Test
	public void testRequestExceedQuota(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		//requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		
		for (int i = 0; i < NovaV2ComputeApplication.MAX_INSTANCE_COUNT; i++) {
			novaV2ComputeOpenStack.requestInstance(
					defaultToken, new LinkedList<Category>(categories), new HashMap<String, String>(), 
					PluginHelper.LINUX_X86_TERM);
		}
		
		try {
			novaV2ComputeOpenStack.requestInstance(
					defaultToken, new LinkedList<Category>(categories), new HashMap<String, String>(), 
					PluginHelper.LINUX_X86_TERM);
			Assert.fail();
		} catch (OCCIException e) {
			Assert.assertEquals(HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE, e.getStatus().getCode());
		}
	}
	
	@Test
	public void testRequestFailsKeyPairDeleted() {
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		//requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME, OrderConstants.MIXIN_CLASS));
		
		try {
			//Last request will fail
			for (int i = 0; i < NovaV2ComputeApplication.MAX_INSTANCE_COUNT + 1; i++) {
				HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
				xOCCIAtt.put(OrderAttribute.DATA_PUBLIC_KEY.getValue(), "public key data");
				novaV2ComputeOpenStack.requestInstance(
						defaultToken, new LinkedList<Category>(categories), xOCCIAtt, 
						PluginHelper.LINUX_X86_TERM);
			}
		} catch (Exception e) {
			// A failure is actually expected
		}
		
		Assert.assertEquals(0, novaV2Server.getPublicKeys().size());
	}
	
	@Test
	public void testRequestOneInstanceWithPublicKey(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.DATA_PUBLIC_KEY.getValue(), "public key data");
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), xOCCIAtt, PluginHelper.LINUX_X86_TERM));
		
		Assert.assertEquals(1, novaV2ComputeOpenStack.getInstances(defaultToken).size());
	}
	
	@Test
	public void testGetInstances(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());	
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), 
				PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		List<Instance> instances = novaV2ComputeOpenStack.getInstances(defaultToken); 
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(FIRST_INSTANCE_ID, instances.get(0).getId());
	}

	@Test
	public void testGetInstances2(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		Map<String, String> mapAttr = new HashMap<String, String>();
		String disk = RequirementsHelper.GLUE_DISK_TERM;
		String mem = RequirementsHelper.GLUE_MEM_RAM_TERM;
		String vCpu = RequirementsHelper.GLUE_VCPU_TERM;
		String requirementsStr = disk + " >= 10 && " + mem + " > 500 && " + vCpu + " > 0";
		mapAttr.put(OrderAttribute.REQUIREMENTS.getValue(), requirementsStr);
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), mapAttr, PluginHelper.LINUX_X86_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), mapAttr, PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		List<Instance> instances = novaV2ComputeOpenStack.getInstances(defaultToken); 
		Assert.assertEquals(2, instances.size());
		for (Instance instance : instances) {
			Assert.assertTrue(FIRST_INSTANCE_ID.equals(instance.getId())
					|| SECOND_INSTANCE_ID.equals(instance.getId()));
		}
	}
	
	@Test
	public void testGetInstance(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());		
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));

		// check getting all instance ids
		Instance instance = novaV2ComputeOpenStack.getInstance(defaultToken, FIRST_INSTANCE_ID); 
		Assert.assertEquals(FIRST_INSTANCE_ID, instance.getId());
		Map<String, String> attributes = instance.getAttributes();
		Assert.assertEquals("active", attributes.get("occi.compute.state"));
		Assert.assertEquals("0.5", attributes.get("occi.compute.memory"));
		Assert.assertEquals("1", attributes.get("occi.compute.cores"));
		Assert.assertEquals("test2", attributes.get("occi.compute.hostname"));
		Assert.assertEquals("Not defined", attributes.get("occi.compute.architecture"));
		Assert.assertEquals("Not defined", attributes.get("occi.compute.speed"));
		Assert.assertEquals(FIRST_INSTANCE_ID, attributes.get("occi.core.id"));
		
		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get("compute"));
		resources.add(ResourceRepository.getInstance().get("os_tpl"));
		resources.add(ResourceRepository.generateFlavorResource(OrderConstants.SMALL_TERM));
		
		for (Resource resource : resources) {
			Assert.assertTrue(instance.getResources().contains(resource));		
		}
	}
	
	@Test
	public void testRemoveInstance(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, new ArrayList<Category>(), new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));
		Assert.assertEquals(1, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		// removing one instance
		novaV2ComputeOpenStack.removeInstance(defaultToken, FIRST_INSTANCE_ID);
		
		// checking if instance was removed
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());		
	}
	
	@Test
	public void testRemoveInstances(){
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		//requesting one default instance
		List<Category> categories = new ArrayList<Category>();
		categories.add(new Category(PluginHelper.LINUX_X86_TERM,
				OrderConstants.TEMPLATE_OS_SCHEME, OrderConstants.MIXIN_CLASS));
		
		Assert.assertEquals(FIRST_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, categories, new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));
		Assert.assertEquals(SECOND_INSTANCE_ID, novaV2ComputeOpenStack.requestInstance(
				defaultToken, categories, new HashMap<String, String>(), PluginHelper.LINUX_X86_TERM));

		Assert.assertEquals(2, novaV2ComputeOpenStack.getInstances(defaultToken).size());
		
		// removing one instance
		novaV2ComputeOpenStack.removeInstances(defaultToken);
		
		// checking if instance was removed
		Assert.assertEquals(0, novaV2ComputeOpenStack.getInstances(defaultToken).size());		
	}
	
	@Test
	public void testUpdateFlavor() throws HttpException, IOException {
		HttpClient client = Mockito.mock(HttpClient.class);
		String nameOne = "nameOne";
		String nameTwo = "nameTwo";
		String idTwo = "idTwo";
		String jsonAllFlavors = "{\"flavors\": [{\"id\": \"1\", \"name\": \"" + nameOne
				+ "\"} , {\"id\": \"" + idTwo + "\", \"name\": \"" + nameTwo + "\"}]}";
		ByteArrayEntity entityAllFlavors = new ByteArrayEntity(jsonAllFlavors.getBytes());
		String jsonFlavorTwo = "{\"flavor\": {\"name\": \"" + nameTwo  + "\", \"ram\": 512, \"vcpus\": 1, \"disk\": 1, \"id\": \"1\"}}";
		ProtocolVersion protocolVersion = new ProtocolVersion("", 0, 1);
		StatusLine statusLine = new BasicStatusLine(protocolVersion, 200, "");
		HttpResponse responseAllFlavors = Mockito.mock(HttpResponse.class);
		Mockito.when(responseAllFlavors.getEntity()).thenReturn(entityAllFlavors);
		Mockito.when(responseAllFlavors.getStatusLine()).thenReturn(statusLine);

		ByteArrayEntity entityFlavorTwo = new ByteArrayEntity(jsonFlavorTwo.getBytes());
		HttpResponse responseFlavorTwo = Mockito.mock(HttpResponse.class);
		Mockito.when(responseFlavorTwo.getEntity()).thenReturn(entityFlavorTwo);
		Mockito.when(responseFlavorTwo.getStatusLine()).thenReturn(statusLine);

		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				responseAllFlavors, responseFlavorTwo, responseAllFlavors);

		List<Flavor> flavors = new ArrayList<Flavor>();
		flavors.add(new Flavor(nameOne, "1", "1", "1"));
		novaV2ComputeOpenStack.setFlavors(flavors);
		novaV2ComputeOpenStack.setClient(client);

		Assert.assertEquals(1, novaV2ComputeOpenStack.getFlavors().size());

		// Updating Flavor List
		novaV2ComputeOpenStack.updateFlavors(defaultToken);

		Assert.assertEquals(2, novaV2ComputeOpenStack.getFlavors().size());
		
		// Adding Flavors that does not exists in the cloud
		novaV2ComputeOpenStack.getFlavors().add(new Flavor("C", "", "", "", 0));
		novaV2ComputeOpenStack.getFlavors().add(new Flavor("D", "", "", "", 0));
		
		Assert.assertEquals(4, novaV2ComputeOpenStack.getFlavors().size());
		
		// Removing Flavors that does not exists in the cloud
		novaV2ComputeOpenStack.updateFlavors(defaultToken);
		
		Assert.assertEquals(2, novaV2ComputeOpenStack.getFlavors().size());
	}
	
	public void tesGettInstanceState() {
		Assert.assertEquals(InstanceState.RUNNING, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "active").getState());
		Assert.assertEquals(InstanceState.FAILED, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "error").getState());
		Assert.assertEquals(InstanceState.PENDING, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "initialized").getState());
		Assert.assertEquals(InstanceState.SUSPENDED, 
				novaV2ComputeOpenStack.getInstance(defaultToken, "suspended").getState());
	}
	
	@Test
	public void testUploadImage() throws Exception {
		String id = "id_123";
		String responseStr = "{\"id\":\"" + id + "\"}";
		HttpResponse createHttpResposeMock = createHttpResponseMock(responseStr, HttpStatus.SC_OK);
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				createHttpResposeMock);

		novaV2ComputeOpenStack.setClient(httpClient);

		List<HttpUriRequest> requests = new ArrayList<HttpUriRequest>();
		requests.add(new HttpPost(GLACE_V2 + OpenStackNovaV2ComputePlugin.V2_IMAGES));
		requests.add(new HttpPatch(GLACE_V2 + OpenStackNovaV2ComputePlugin.V2_IMAGES + "/" + id));
		requests.add(new HttpPut(GLACE_V2 + OpenStackNovaV2ComputePlugin.V2_IMAGES + "/" + id
				+ OpenStackNovaV2ComputePlugin.V2_IMAGES_FILE));
		HttpUriRequestMatcher expectedRequests = new HttpUriRequestMatcher(requests);

		novaV2ComputeOpenStack.uploadImage(new Token("", new Token.User("", ""), null, null), 
				"", "image", "");

		Mockito.verify(httpClient, Mockito.times(3)).execute(Mockito.argThat(expectedRequests));
	}
	
	@Test
	public void testGetResourceInfo() throws Exception {
		JSONObject jsonObsoluteValues = new JSONObject();
		
		String value = "10";
		String halfValue = "5";
		jsonObsoluteValues.put(OpenStackConfigurationConstants.TOTAL_CORES_USED_ATT, halfValue);
		jsonObsoluteValues.put(OpenStackConfigurationConstants.MAX_TOTAL_CORES_ATT, value);
		jsonObsoluteValues.put(OpenStackConfigurationConstants.TOTAL_RAM_USED_ATT, halfValue);
		jsonObsoluteValues.put(OpenStackConfigurationConstants.MAX_TOTAL_RAM_SIZE_ATT, value);
		jsonObsoluteValues.put(OpenStackConfigurationConstants.TOTAL_INSTANCES_USED_ATT, halfValue);
		jsonObsoluteValues.put(OpenStackConfigurationConstants.MAX_TOTAL_INSTANCES_ATT, value);
		
		JSONObject jsonAbsolute = new JSONObject();
		jsonAbsolute.put("absolute", jsonObsoluteValues);
		
		JSONObject jsonLimits = new JSONObject();
		jsonLimits.put("limits", jsonAbsolute);
		
		HttpResponse httpResposeMock = createHttpResponseMock(jsonLimits.toString(), HttpStatus.SC_OK);
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class)))
			.thenReturn(httpResposeMock);
		
		novaV2ComputeOpenStack.setClient(httpClient);
		
		ResourcesInfo resourcesInfo = novaV2ComputeOpenStack.getResourcesInfo(defaultToken);
		Assert.assertEquals(halfValue, resourcesInfo.getCpuIdle());
		Assert.assertEquals(halfValue, resourcesInfo.getCpuInUse());
		Assert.assertEquals(halfValue, resourcesInfo.getInstancesInUse());
		Assert.assertEquals(halfValue, resourcesInfo.getInstancesIdle());
		Assert.assertEquals(halfValue, resourcesInfo.getMemInUse());
		Assert.assertEquals(halfValue, resourcesInfo.getMemIdle());
	}
	
	@Test
	public void testDoRequestExceptions() throws Exception {
		HttpResponse httpResposeMockException = createHttpResponseMock("", HttpStatus.SC_BAD_REQUEST);
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class))).thenReturn(httpResposeMockException);

		novaV2ComputeOpenStack.setClient(httpClient);
		try {
			novaV2ComputeOpenStack.doDeleteRequest("", "");
			Assert.fail();
		} catch (Exception e) {}
		
		try {
			novaV2ComputeOpenStack.doPutRequest("", "", "");
			Assert.fail();
		} catch (Exception e) {}
		
		try {
			novaV2ComputeOpenStack.doPostRequest("", "", null);
			Assert.fail();
		} catch (Exception e) {}
		
		try {
			novaV2ComputeOpenStack.doPutRequest("", "", "");
			Assert.fail();
		} catch (Exception e) {}
		
		try {
			novaV2ComputeOpenStack.doGetRequest("", "");
			Assert.fail();
		} catch (Exception e) {}
	}
	
	@Test
	public void testGetImageState() throws Exception {
		List<JSONObject> list = new ArrayList<JSONObject>();
		String imageOne = "imageOne";
		String imageTwo = "imageTwo";
		String imageThree = "imageThree";
		list.add(new JSONObject("{\"name\": \"" + imageOne  + "\", \"status\": \"active\"}"));
		list.add(new JSONObject("{\"name\": \"" + imageTwo + "\", \"status\": \"saving\"}"));
		list.add(new JSONObject("{\"name\": \"" + imageThree + "\", \"status\": \"x\"}"));
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("images", list);
		HttpResponse httpResposeMock = createHttpResponseMock(jsonObject.toString(), HttpStatus.SC_OK);
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResposeMock);
		novaV2ComputeOpenStack.setClient(httpClient);
				
		Assert.assertEquals(ImageState.ACTIVE, novaV2ComputeOpenStack.getImageState(new Token("",
				new Token.User("", ""), new Date(), new HashMap<String, String>()), imageOne));
	}
	
	@Test
	public void testByPass() {
		Response response = new org.restlet.engine.adapter.HttpResponse(null, null);
		novaV2ComputeOpenStack.bypass(null, response);
		Assert.assertEquals(400, response.getStatus().getCode());
	}
	
	@Test
	public void testGetUsedFlavor() {
		Assert.assertNull(novaV2ComputeOpenStack.getUsedFlavor("0"));
	}
	
	@Test
	public void testGetInstanceState() {
		Assert.assertEquals(InstanceState.SUSPENDED, novaV2ComputeOpenStack.getInstanceState(InstanceState.SUSPENDED.getOcciState()));
		Assert.assertEquals(InstanceState.RUNNING, novaV2ComputeOpenStack.getInstanceState(InstanceState.RUNNING.getOcciState()));
		Assert.assertEquals(InstanceState.PENDING, novaV2ComputeOpenStack.getInstanceState(InstanceState.PENDING.getOcciState()));
		Assert.assertEquals(InstanceState.FAILED, novaV2ComputeOpenStack.getInstanceState("error"));
	}
	
	@Test(expected=OCCIException.class)
	public void testUploadImageWithoutImage() throws Exception {
		novaV2ComputeOpenStack.uploadImage(new Token("", new Token.User("", ""), null, null),
				"", null, "");
	}	
	
	@Test(expected=OCCIException.class)
	public void testUploadImageDeletingImage() throws Exception {
		String id = "id_123";
		String responseStr = "{\"id\":\"" + id + "\"}";
		HttpResponse httpResposeMock = createHttpResponseMock(responseStr, HttpStatus.SC_OK);
		HttpResponse httpResposeMockException = createHttpResponseMock(responseStr, HttpStatus.SC_BAD_REQUEST);
		HttpClient httpClient = Mockito.mock(HttpClient.class);
		Mockito.when(httpClient.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResposeMock, httpResposeMockException, httpResposeMock);

		novaV2ComputeOpenStack.setClient(httpClient);
		novaV2ComputeOpenStack.uploadImage(new Token("", new Token.User("", ""), null, null),
				"", "image", "");
	}	
	
	@Test
	public void testGetDefaultHttpClientTimeout() {
		Properties properties = new Properties();		
		novaV2ComputeOpenStack = new OpenStackNovaV2ComputePlugin(properties);		
		
		Assert.assertEquals(OpenStackNovaV2ComputePlugin.DEFAULT_HTTPCLIENT_TIMEOUT,
				this.novaV2ComputeOpenStack.getHttpClientTimeout());
	}
	
	@Test
	public void testGettHttpClientTimeout() {
		int timeout = 30000;
		Properties properties = new Properties();
		properties.put(OpenStackConfigurationConstants.COMPUTE_HTTPCLIENT_TIMEOUT, String.valueOf(timeout));
		novaV2ComputeOpenStack = new OpenStackNovaV2ComputePlugin(properties);		
		
		Assert.assertEquals(timeout, this.novaV2ComputeOpenStack.getHttpClientTimeout());
	}	

	private HttpResponse createHttpResponseMock(String responseStr, int statusCode) {
		HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
		try {
			HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
			InputStream value = new ByteArrayInputStream(responseStr.getBytes());
			Mockito.when(httpEntity.getContent()).thenReturn(value);
			Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);
			StatusLine statusLine = new BasicStatusLine(
					new ProtocolVersion("", 1, 0), statusCode,"");
			Mockito.when(httpResponse.getStatusLine()).thenReturn(statusLine);
		} catch (Exception e) {
		}
		return httpResponse;
	}

	private static class HttpUriRequestMatcher extends ArgumentMatcher<HttpUriRequest> {

		private List<HttpUriRequest> requests;
		private int cont;
		private boolean valid;

		public HttpUriRequestMatcher(List<HttpUriRequest> requests) {
			this.cont = -1;
			this.valid = true;
			this.requests = requests;
		}

		public boolean matches(Object object) {
			try {
				this.cont++;
				HttpUriRequest comparedRequest = (HttpUriRequest) object;
				HttpUriRequest request = this.requests.get(cont);
				if (!request.getURI().equals(comparedRequest.getURI())) {
					this.valid = false;
				}
				if (!request.getMethod().equals(comparedRequest.getMethod())) {
					this.valid = false;
				}				
			} catch (Exception e) {}
			return this.valid;
		}
	}
	
}
