package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.RequirementsHelper;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.common.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Request;
import org.restlet.Response;

public class TestCloudStackComputePlugin {

	private static final String DEFAULT_SERVICE_OFFERING = "62d5f174-2f1e-42f0-931e-07600a05470e";
	private static final String DEFAULT_SMALEST_DISK_OFFERING = "e9c2a08d-6ca4-4b81-8e21-5ff2a103b7cb";
	private static final String COMPUTE_DEFAULT_ZONE = "root";
	private static final String OS_TYPE = "Ubuntu";
	private static final String IMAGE_DOWNLOADED_BASE_URL = "http://127.0.0.1";
	private static final String IMAGE_DOWNLOADED_BASE_PATH = "/var/www";
	private static final String ZONE_ID = "zoneId";

	private static String RESPONSE_LIST_DISK_OFFERINGS;
	private static String RESPONSE_MULTIPLE_INSTANCES;
	private static String RESPONSE_ONE_INSTANCE;
	private static String RESPONSE_NO_INSTANCE;
	private static String RESPONSE_DEPLOY_VM;
	private static String RESPONSE_RESOURCES_INFO;
	private static String RESPONSE_LIST_TEMPLATES;
	private static String RESPONSE_GET_FLAVOR;
	private static String RESPONSE_OS_TYPE;

	static {
		try {
			RESPONSE_ONE_INSTANCE = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.one_instance");
			RESPONSE_MULTIPLE_INSTANCES = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.multiple_instances");
			RESPONSE_NO_INSTANCE = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.no_instance");
			RESPONSE_RESOURCES_INFO = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.resources_info");
			RESPONSE_OS_TYPE = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.os_type");
			RESPONSE_LIST_TEMPLATES = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.list_template");
			RESPONSE_GET_FLAVOR = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.get_flavor");
			RESPONSE_DEPLOY_VM = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.deploy_vm");
			RESPONSE_LIST_DISK_OFFERINGS = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.list_disk_offerings_complete");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private CloudStackComputePlugin createPlugin(HttpClientWrapper httpClient,
			Properties extraProperties) {
		Properties properties = new Properties();
		properties.put("compute_cloudstack_api_url",
				CloudStackTestHelper.CLOUDSTACK_URL);
		properties.put("compute_cloudstack_default_zone", COMPUTE_DEFAULT_ZONE);
		properties.put("compute_cloudstack_image_download_base_path",
				CloudStackTestHelper.CLOUDSTACK_URL);
		properties.put("compute_cloudstack_image_download_base_path",
				IMAGE_DOWNLOADED_BASE_PATH);
		properties.put("compute_cloudstack_image_download_base_url",
				IMAGE_DOWNLOADED_BASE_URL);
		properties.put("compute_cloudstack_default_networkid", "01");
		
		if (extraProperties != null) {
			properties.putAll(extraProperties);
		}
		if (httpClient == null) {
			return new CloudStackComputePlugin(properties);
		} else {
			return new CloudStackComputePlugin(properties, httpClient);
		}
	}

	@Test
	public void testRequestInstace() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.DEPLOY_VM_COMMAND,
				CloudStackComputePlugin.TEMPLATE_ID, imageId,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.SERVICE_OFFERING_ID,
				DEFAULT_SERVICE_OFFERING,
				CloudStackComputePlugin.DISK_OFFERING_ID,
				DEFAULT_SMALEST_DISK_OFFERING,
				CloudStackComputePlugin.NETWORK_IDS, "01");
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, deployyVMUrl, RESPONSE_DEPLOY_VM, 200);

		String getVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getVMUrl, RESPONSE_GET_FLAVOR, 200);

		String getDiskOfeering = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_DISK_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getDiskOfeering, RESPONSE_LIST_DISK_OFFERINGS, 200);
		
		CloudStackComputePlugin computePlugin = createPlugin(httpClient,
				extraProperties);
		HashMap<String, String> occiAttributes = new HashMap<String, String>();
		occiAttributes.put(OrderAttribute.REQUIREMENTS.getValue(), 
				RequirementsHelper.GLUE_DISK_TERM + " > " + CloudStackComputePlugin.ZERO_DYNAMIC_DISK_OFFERING);
		computePlugin.requestInstance(token, categories,
				occiAttributes, imageId);
	}
	
	@Test
	public void testRequestInstaceIgnoringDiskOffering() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.DEPLOY_VM_COMMAND,
				CloudStackComputePlugin.TEMPLATE_ID, imageId,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.SERVICE_OFFERING_ID,
				DEFAULT_SERVICE_OFFERING,
				CloudStackComputePlugin.NETWORK_IDS, "01");
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, deployyVMUrl, RESPONSE_DEPLOY_VM, 200);

		String getVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getVMUrl, RESPONSE_GET_FLAVOR, 200);

		String getDiskOfeering = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_DISK_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getDiskOfeering, RESPONSE_LIST_DISK_OFFERINGS, 200);
		
		CloudStackComputePlugin computePlugin = createPlugin(httpClient,
				extraProperties);
		HashMap<String, String> occiAttributes = new HashMap<String, String>();
		computePlugin.requestInstance(token, categories,
				occiAttributes, imageId);
	}	
	
	@Test
	public void testRequestInstaceWithEmptyDefaultNetworkId() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		String emptyComputeCloudstackDefaultNetworkId = "";
		extraProperties.put(CloudStackComputePlugin.COMPUTE_CLOUDSTACK_DEFAULT_NETWORKID, 
				emptyComputeCloudstackDefaultNetworkId);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.DEPLOY_VM_COMMAND,
				CloudStackComputePlugin.TEMPLATE_ID, imageId,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.SERVICE_OFFERING_ID,
				DEFAULT_SERVICE_OFFERING,
				CloudStackComputePlugin.DISK_OFFERING_ID,
				DEFAULT_SMALEST_DISK_OFFERING,
				CloudStackComputePlugin.NETWORK_IDS, "01");
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, deployyVMUrl, RESPONSE_DEPLOY_VM, 200);

		String getVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getVMUrl, RESPONSE_GET_FLAVOR, 200);

		String getDiskOfeering = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_DISK_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getDiskOfeering, RESPONSE_LIST_DISK_OFFERINGS, 200);
		
		CloudStackComputePlugin computePlugin = createPlugin(httpClient, extraProperties);
		try {
			computePlugin.requestInstance(token, categories, 
					new HashMap<String, String>(), imageId);
			Assert.fail();
		} catch (OCCIException e) {	
			Assert.assertEquals(CloudStackComputePlugin.DEFAULT_NETWORK_ID_IS_EMPTY, 
					e.getStatus().getDescription());
		}
	}	

	@Test(expected = OCCIException.class)
	public void testRequestInstanceNullImageId() {
		List<Category> categories = new ArrayList<Category>();
		Token token = new Token("api:key", null, new Date(), null);
		CloudStackComputePlugin computePlugin = createPlugin(null, null);
		computePlugin.requestInstance(token, categories,
				new HashMap<String, String>(), null);
	}

	@Test(expected = OCCIException.class)
	public void testRequestInstanceZoneIdNull() {
		List<Category> categories = new ArrayList<Category>();
		Token token = new Token("api:key", null, new Date(), null);
		CloudStackComputePlugin computePlugin = createPlugin(null, null);
		computePlugin.requestInstance(token, categories,
				new HashMap<String, String>(), "");
	}

	@Test
	public void testRequestInstanceWithUserData() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		String networkId = "01";
		extraProperties.put("compute_cloudstack_default_networkid", networkId);
		
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.DEPLOY_VM_COMMAND,
				CloudStackComputePlugin.TEMPLATE_ID, imageId,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.SERVICE_OFFERING_ID,
				DEFAULT_SERVICE_OFFERING,
				CloudStackComputePlugin.USERDATA, "userdata",
				CloudStackComputePlugin.DISK_OFFERING_ID,
				DEFAULT_SMALEST_DISK_OFFERING,
				CloudStackComputePlugin.NETWORK_IDS, networkId);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, deployyVMUrl, RESPONSE_DEPLOY_VM, 200);
		String getVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getVMUrl, RESPONSE_GET_FLAVOR, 200);

		String getDiskOfeering = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_DISK_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getDiskOfeering, RESPONSE_LIST_DISK_OFFERINGS, 200);
		
		HashMap<String, String> occiAttributes = new HashMap<String, String>();
		occiAttributes.put(OrderAttribute.USER_DATA_ATT.getValue(),
				"userdata");
		occiAttributes.put(OrderAttribute.REQUIREMENTS.getValue(), 
				RequirementsHelper.GLUE_DISK_TERM + " > " + CloudStackComputePlugin.ZERO_DYNAMIC_DISK_OFFERING);		
		CloudStackComputePlugin computePlugin = createPlugin(httpClient,
				extraProperties);
		computePlugin.requestInstance(token, categories, occiAttributes,
				imageId);
	}

	private static final String BAD_RESPONSE_STRING = "badResponse";

	@Test(expected = OCCIException.class)
	public void testRequestInstanceWithBadResponse() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(OrderConstants.SMALL_TERM,
				OrderConstants.TEMPLATE_RESOURCE_SCHEME,
				OrderConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		extraProperties.put("compute_cloudstack_default_networkid", "01");

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.DEPLOY_VM_COMMAND,
				CloudStackComputePlugin.TEMPLATE_ID, imageId,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.SERVICE_OFFERING_ID,
				DEFAULT_SERVICE_OFFERING,
				CloudStackComputePlugin.USERDATA, "userdata",
				CloudStackComputePlugin.DISK_OFFERING_ID,
				DEFAULT_SMALEST_DISK_OFFERING,
				CloudStackComputePlugin.NETWORK_IDS, "01");
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, deployyVMUrl, BAD_RESPONSE_STRING, 200);
		String getVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getVMUrl, RESPONSE_GET_FLAVOR, 200);

		String getDiskOfeering = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_DISK_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getDiskOfeering, RESPONSE_LIST_DISK_OFFERINGS, 200);
		
		CloudStackComputePlugin computePlugin = createPlugin(httpClient,
				extraProperties);
		HashMap<String, String> occiAttributes = new HashMap<String, String>();
		occiAttributes.put(OrderAttribute.USER_DATA_ATT.getValue(), "userdata");
		occiAttributes.put(OrderAttribute.REQUIREMENTS.getValue(), 
				RequirementsHelper.GLUE_DISK_TERM + " > " + CloudStackComputePlugin.ZERO_DYNAMIC_DISK_OFFERING);
		computePlugin.requestInstance(token, categories, occiAttributes, imageId);
	}

	@Test
	public void testGetInstancesOneInstance() {
		Token token = new Token("api:key", null, null, null);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, deployyVMUrl, RESPONSE_ONE_INSTANCE, 200);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		List<Instance> instances = cscp.getInstances(token);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(InstanceState.RUNNING, instances.get(0).getState());
	}

	@Test
	public void testGetInstancesMultipleInstances() {
		Token token = new Token("api:key", null, null, null);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, deployyVMUrl,
				RESPONSE_MULTIPLE_INSTANCES, 200);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		List<Instance> instances = cscp.getInstances(token);
		Assert.assertEquals(2, instances.size());
		Assert.assertEquals(InstanceState.RUNNING, instances.get(0).getState());
	}

	@Test
	public void testGetInstancesNoInstance() {
		Token token = new Token("api:key", null, null, null);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, deployyVMUrl, RESPONSE_NO_INSTANCE, 200);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		List<Instance> instances = cscp.getInstances(token);
		Assert.assertEquals(0, instances.size());
	}

	private static final String VM_ID = "50b2b99a-8215-4437-9dfe-17382242e08c";

	@Test
	public void testGetInstance() {
		Token token = new Token("api:key", null, null, null);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_VMS_COMMAND,
				CloudStackComputePlugin.VM_ID, VM_ID);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, deployyVMUrl, RESPONSE_ONE_INSTANCE, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		Instance instance = computePlugin.getInstance(token, VM_ID);
		Assert.assertEquals(VM_ID, instance.getId());
	}

	@Test(expected = OCCIException.class)
	public void testGetNoInstance() {
		Token token = new Token("api:key", null, null, null);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String deployyVMUrl = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_VMS_COMMAND,
				CloudStackComputePlugin.VM_ID, VM_ID);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, deployyVMUrl, RESPONSE_NO_INSTANCE, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		computePlugin.getInstance(token, VM_ID);
	}

	@Test
	public void testRemoveInstancesWithExceptionDontStop() {
		Token token = new Token("api:key", null, null, null);

		CloudStackComputePlugin cloudstackComputePlugin = Mockito.spy(
				new CloudStackComputePlugin(new Properties()));
		List<Instance> instances = new ArrayList<Instance>();
		String instanceId = "One";
		instances.add(new Instance(instanceId));
		instances.add(new Instance("Two"));
		instances.add(new Instance("Three"));
		
		Mockito.doNothing().when(cloudstackComputePlugin).removeInstance(
				Mockito.eq(token), Mockito.anyString());
		Mockito.doThrow(new OCCIException(ErrorType.BAD_REQUEST, DEFAULT_SMALEST_DISK_OFFERING)).when(cloudstackComputePlugin)
				.removeInstance(Mockito.eq(token), Mockito.anyString());
		Mockito.doReturn(instances).when(cloudstackComputePlugin).getInstances(Mockito.eq(token));
			
		// action
		cloudstackComputePlugin.removeInstances(token);
		// check
		Mockito.verify(cloudstackComputePlugin, Mockito.times(instances.size()))
				.removeInstance(Mockito.any(Token.class), Mockito.anyString());
	}
	
	@Test
	public void testRemoveInstance() {
		Token token = new Token("api:key", null, null, null);
		URIBuilder uriBuilder = CloudStackComputePlugin.createURIBuilder(
				CloudStackTestHelper.CLOUDSTACK_URL,
				CloudStackComputePlugin.DESTROY_VM_COMMAND);
		uriBuilder.addParameter(CloudStackComputePlugin.VM_ID, VM_ID);
		uriBuilder.addParameter(CloudStackComputePlugin.VM_EXPUNGE, "true");
		CloudStackHelper.sign(uriBuilder, token.getAccessId());

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String getInstancesURL = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, getInstancesURL,
				RESPONSE_ONE_INSTANCE, 200);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		cscp.removeInstances(token);
		Mockito.verify(httpClient).doPost(uriBuilder.toString());
	}

	@Test
	public void testRemoveInstanceWithoutInstance() {
		Token token = new Token("api:key", null, null, null);
		URIBuilder uriBuilder = CloudStackComputePlugin.createURIBuilder(
				CloudStackTestHelper.CLOUDSTACK_URL,
				CloudStackComputePlugin.DESTROY_VM_COMMAND);
		uriBuilder.addParameter(CloudStackComputePlugin.VM_ID, VM_ID);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String getInstancesURL = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		CloudStackTestHelper
				.recordHTTPClientWrapperRequest(httpClient, token,
						CloudStackTestHelper.GET, getInstancesURL,
						RESPONSE_NO_INSTANCE, 200);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		cscp.removeInstances(token);
		Mockito.verify(httpClient, Mockito.never()).doPost(
				uriBuilder.toString());
	}

	@Test
	public void testGetResourceInfoOneInstance() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String urlListVM = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		String urlListResources = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_RESOURCE_LIMITS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, urlListVM, RESPONSE_ONE_INSTANCE, 200);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, urlListResources,
				RESPONSE_RESOURCES_INFO, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		ResourcesInfo ri = computePlugin.getResourcesInfo(token);
		Assert.assertEquals("1", ri.getInstancesIdle());
		Assert.assertEquals("512", ri.getMemInUse());
		Assert.assertEquals("1", ri.getCpuInUse());
	}

	@Test
	public void testGetResourcesInfoMultipleInstances() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String urlListVM = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		String urlListResources = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_RESOURCE_LIMITS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, urlListVM,
				RESPONSE_MULTIPLE_INSTANCES, 200);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, urlListResources,
				RESPONSE_RESOURCES_INFO, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		ResourcesInfo ri = computePlugin.getResourcesInfo(token);
		Assert.assertEquals("0", ri.getInstancesIdle());
		Assert.assertEquals("1024", ri.getMemInUse());
		Assert.assertEquals("2", ri.getCpuInUse());
	}

	@Test
	public void testGetResourcesNoInstances() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String urlListVM = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_VMS_COMMAND);
		String urlListResources = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_RESOURCE_LIMITS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, urlListVM, RESPONSE_NO_INSTANCE, 200);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, urlListResources,
				RESPONSE_RESOURCES_INFO, 200);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		ResourcesInfo ri = cscp.getResourcesInfo(token);
		Assert.assertEquals("0", ri.getMemInUse());
		Assert.assertEquals("0", ri.getCpuInUse());
	}

	@Test
	public void testBypass() {
		CloudStackComputePlugin cscp = createPlugin(null, null);
		Response response = new Response(new Request());
		cscp.bypass(null, response);
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatus().getCode());
		Assert.assertEquals(ResponseConstants.CLOUD_NOT_SUPPORT_OCCI_INTERFACE, 
				response.getStatus().getDescription());
	}

	private static final String IMAGE_NAME = "name";
	private static final String DISK_FORMAT = "FORMAT";
	private static final String HYPERVISOR = "KVM";
	private static final String VALID_IMAGE_PATH = "/var/www/cirros.img";
	private static final String IMAGE_URL = VALID_IMAGE_PATH.replace(
			IMAGE_DOWNLOADED_BASE_PATH, IMAGE_DOWNLOADED_BASE_URL + "/");

	@Test
	public void testUploadImage() {
		Token token = new Token("api:key", null, null, null);
		String registerTemplateURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.REGISTER_TEMPLATE_COMMAND,
				CloudStackComputePlugin.DISPLAY_TEXT, IMAGE_NAME,
				CloudStackComputePlugin.FORMAT, DISK_FORMAT,
				CloudStackComputePlugin.HYPERVISOR, HYPERVISOR,
				CloudStackComputePlugin.NAME, IMAGE_NAME,
				CloudStackComputePlugin.OS_TYPE_ID, OS_TYPE,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.URL, IMAGE_URL,
				CloudStackComputePlugin.IS_PUBLIC, Boolean.TRUE.toString());
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, registerTemplateURL, "response", 200);

		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_image_download_os_type_id",
				OS_TYPE);
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		cscp.uploadImage(token, VALID_IMAGE_PATH, IMAGE_NAME, DISK_FORMAT);
	}

	private static final String NOT_VALID_IMAGE_PATH = "/other/path/cirros.img";

	@Test(expected = OCCIException.class)
	public void testUploadImageNotValidPath() {
		Token token = new Token("api:key", null, null, null);
		String registerTemplateURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.REGISTER_TEMPLATE_COMMAND,
				CloudStackComputePlugin.DISPLAY_TEXT, IMAGE_NAME,
				CloudStackComputePlugin.FORMAT, DISK_FORMAT,
				CloudStackComputePlugin.HYPERVISOR, HYPERVISOR,
				CloudStackComputePlugin.NAME, IMAGE_NAME,
				CloudStackComputePlugin.OS_TYPE_ID, OS_TYPE,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.URL, IMAGE_URL,
				CloudStackComputePlugin.IS_PUBLIC, Boolean.TRUE.toString());
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, registerTemplateURL, "response", 200);

		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_image_download_os_type_id",
				OS_TYPE);
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		cscp.uploadImage(token, NOT_VALID_IMAGE_PATH, IMAGE_NAME, DISK_FORMAT);
	}

	private static final String OS_TYPE_ID = "b0201c53-1385-11e5-be87-fa163ec5cca2";

	@Test
	public void testUploadImageWithoutOSType() {
		Token token = new Token("api:key", null, null, null);
		String registerTemplateURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.REGISTER_TEMPLATE_COMMAND,
				CloudStackComputePlugin.DISPLAY_TEXT, IMAGE_NAME,
				CloudStackComputePlugin.FORMAT, DISK_FORMAT,
				CloudStackComputePlugin.HYPERVISOR, HYPERVISOR,
				CloudStackComputePlugin.NAME, IMAGE_NAME,
				CloudStackComputePlugin.OS_TYPE_ID, OS_TYPE_ID,
				CloudStackComputePlugin.ZONE_ID, ZONE_ID,
				CloudStackComputePlugin.URL, IMAGE_URL,
				CloudStackComputePlugin.IS_PUBLIC, Boolean.TRUE.toString());
		String listOSTypeURL = CloudStackTestHelper
				.createURL(CloudStackComputePlugin.LIST_OS_TYPES_COMMAND);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, registerTemplateURL, "response", 200);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listOSTypeURL, RESPONSE_OS_TYPE, 200);

		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		cscp.uploadImage(token, VALID_IMAGE_PATH, IMAGE_NAME, DISK_FORMAT);
	}

	private static final String VALID_IMAGE_ID = "f8340307-52c6-4aec-a224-8ff84538107e";
	private static final String VALID_IMAGE_NAME = "cirros-123";
	private static final String TEMPLATE_FILTER_EXECUTABLE = "executable";

	@Test
	public void testGetImageId() {
		Token token = new Token("api:key", null, null, null);
		String listTemplatesURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_TEMPLATES_COMMAND,
				CloudStackComputePlugin.TEMPLATE_FILTER,
				TEMPLATE_FILTER_EXECUTABLE);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listTemplatesURL,
				RESPONSE_LIST_TEMPLATES, 200);
		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		String imageId = computePlugin.getImageId(token, VALID_IMAGE_NAME);
		Assert.assertEquals(VALID_IMAGE_ID, imageId);
	}

	private static final String INVALID_IMAGE_NAME = "not-valid-name";

	@Test
	public void testGetImageBadRequest() {
		Token token = new Token("api:key", null, null, null);
		String listTemplatesURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_TEMPLATES_COMMAND,
				CloudStackComputePlugin.TEMPLATE_FILTER,
				TEMPLATE_FILTER_EXECUTABLE);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listTemplatesURL,
				RESPONSE_LIST_TEMPLATES, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		String imageId = computePlugin.getImageId(token, INVALID_IMAGE_NAME);
		Assert.assertNull(imageId);
	}

	@Test
	public void testGetImageStateActive() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String listTemplatesURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_TEMPLATES_COMMAND,
				CloudStackComputePlugin.TEMPLATE_FILTER,
				TEMPLATE_FILTER_EXECUTABLE);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listTemplatesURL,
				RESPONSE_LIST_TEMPLATES, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		ImageState imageState = computePlugin.getImageState(token,
				VALID_IMAGE_NAME);
		Assert.assertEquals(ImageState.ACTIVE, imageState);
	}

	private static final String PENDING_IMAGE_NAME = "centos-failed";

	@Test
	public void testGetImageStatePending() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String listTemplatesURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_TEMPLATES_COMMAND,
				CloudStackComputePlugin.TEMPLATE_FILTER,
				TEMPLATE_FILTER_EXECUTABLE);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listTemplatesURL,
				RESPONSE_LIST_TEMPLATES, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		ImageState imageState = computePlugin.getImageState(token,
				PENDING_IMAGE_NAME);
		Assert.assertEquals(ImageState.PENDING, imageState);
	}

	@Test
	public void testGetImageStateBadRequest() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		String listTemplatesURL = CloudStackTestHelper.createURL(
				CloudStackComputePlugin.LIST_TEMPLATES_COMMAND,
				CloudStackComputePlugin.TEMPLATE_FILTER,
				TEMPLATE_FILTER_EXECUTABLE);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listTemplatesURL,
				RESPONSE_LIST_TEMPLATES, 200);

		CloudStackComputePlugin computePlugin = createPlugin(httpClient, null);
		ImageState imageState = computePlugin.getImageState(token,
				INVALID_IMAGE_NAME);
		Assert.assertNull(imageState);
	}
	
	@Test
	public void testGetAsyncJobStatus() throws JSONException {		
		JSONObject inProcessJson = new JSONObject();
		JSONObject jobStatusJson = new JSONObject();
		int inProcessStatus = 0;
		jobStatusJson.put(CloudStackComputePlugin.JSON_JOB_STATUS, inProcessStatus);
		inProcessJson.put(CloudStackComputePlugin.JSON_QUERY_ASYNC_JOB_RESULT_RESPONSE, jobStatusJson);
		
		JSONObject completeJson = new JSONObject();
		jobStatusJson = new JSONObject();
		int completeStatus = 1;
		jobStatusJson.put(CloudStackComputePlugin.JSON_JOB_STATUS, completeStatus);
		completeJson.put(CloudStackComputePlugin.JSON_QUERY_ASYNC_JOB_RESULT_RESPONSE, jobStatusJson);		
		
		// mock
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		URIBuilder uriBuilder = null;
		try {
			uriBuilder = new URIBuilder("http://localhost:0000");
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
		final Token token = new Token("api:key", null, null, null);
		final ProtocolVersion PROTO = new ProtocolVersion("HTTP", 1, 1);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		final HttpResponseWrapper responseInProcess = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, 200, "test reason"), inProcessJson.toString());
		final HttpResponseWrapper responseComplete = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, 200, "test reason"), completeJson.toString());		
		Mockito.when(httpClient.doGet(Mockito.anyString())).thenReturn(responseInProcess, responseComplete);
		
		CloudStackComputePlugin cloudStackComputePlugin = createPlugin(httpClient, null);
		JSONObject responseJson = Mockito.mock(JSONObject.class);
		Mockito.when(responseJson.has(Mockito.anyString())).thenReturn(true);
		Mockito.when(responseJson.optString(Mockito.anyString())).thenReturn("id=10");
		JSONObject asyncJobStatus = cloudStackComputePlugin.getAsyncJobStatus(responseJson , token);
		
		String responseExpected = completeJson.getJSONObject(
				CloudStackComputePlugin.JSON_QUERY_ASYNC_JOB_RESULT_RESPONSE).toString();
		Assert.assertEquals(responseExpected, asyncJobStatus.toString());
	}

}
