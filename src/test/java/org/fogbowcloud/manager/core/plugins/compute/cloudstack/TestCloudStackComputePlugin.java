package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.http.ProtocolVersion;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.model.ImageState;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.identity.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackComputePlugin {
	
	private static final String COMPUTE_DEFAULT_ZONE = "root";
	private static final String CLOUDSTACK_URL = "http://localhost:8080/client/api";
	private static final String OS_TYPE = "Ubuntu";
	private static final String IMAGE_DOWNLOADED_BASE_URL = "http://127.0.0.1";
	private static final String IMAGE_DOWNLOADED_BASE_PATH = "/var/www";
	private static final String ZONE_ID = "zoneId";

	private static String RESPONSE_ONE_INSTANCE;
	private static final String RESPONSE_NO_INSTANCE = "{ \"listvirtualmachinesresponse\" : {}}";
	private static String RESPONSE_DEPLOY_VM;
	private static String RESPONSE_RESOURCES_INFO;
	private static String RESPONSE_OS_TYPE;
	private static String RESPONSE_LIST_TEMPLATES;
	private static String RESPONSE_GET_FLAVOR;
	
	@Before
	public void setUp() throws IOException {
		RESPONSE_ONE_INSTANCE = PluginHelper
				.getContentFile("src/test/resources/cloudstack/response.one_instance");
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
	}

	private CloudStackComputePlugin createPlugin(HttpClientWrapper httpClient, Properties extraProperties) {
		Properties properties = new Properties();
		if (extraProperties != null) {
			properties.putAll(extraProperties);
		} 
		properties.put("compute_cloudstack_api_url", CLOUDSTACK_URL);
		properties.put("compute_cloudstack_default_zone", COMPUTE_DEFAULT_ZONE);
		properties.put("compute_cloudstack_image_download_base_path",
				CLOUDSTACK_URL);
		properties.put("compute_cloudstack_image_download_base_path", IMAGE_DOWNLOADED_BASE_PATH);
		properties.put("compute_cloudstack_image_download_base_url", IMAGE_DOWNLOADED_BASE_URL);
		if (httpClient == null) {
			return new CloudStackComputePlugin(properties);
		} else {
			return new CloudStackComputePlugin(properties, httpClient);
		}
	}

	private HttpClientWrapper createHttpClientWrapperMock(Token token,
			Map<String[], String> commandResponse, String[] requestType) {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		ProtocolVersion proto = new ProtocolVersion("HTTP", 1, 1);
		int count = 0;
		for (Entry<String[], String> entry : commandResponse.entrySet()) {
			String command = entry.getKey()[0];
			String response = entry.getValue();
			URIBuilder uriBuilder = CloudStackComputePlugin.createURIBuilder(
					CLOUDSTACK_URL, command);
			if (entry.getKey().length > 0) {
				String[] parameters = entry.getKey();
				for (int i = 1; i < parameters.length; i++) {
					String parameter = parameters[i];
					uriBuilder.addParameter(parameter.split(" ")[0],
							parameter.split(" ")[1]);
				}
			}
			CloudStackHelper.sign(uriBuilder, token.getAccessId());
			HttpResponseWrapper returned = new HttpResponseWrapper(
					new BasicStatusLine(proto, 200, "test reason"), response);
			if (requestType[count].equals("get")) {
				Mockito.when(httpClient.doGet(uriBuilder.toString()))
						.thenReturn(returned);
			} else {
				Mockito.when(httpClient.doPost(uriBuilder.toString()))
						.thenReturn(returned);
			}
			count++;
		}
		return httpClient;
	}
	
	@Test
	public void testRequestInstace() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		Map<String[], String> commandResponse = new LinkedHashMap<String[], String>();
		String[] commandsDeployVM = new String[4];
		commandsDeployVM[0] = CloudStackComputePlugin.DEPLOY_VM_COMMAND;
		commandsDeployVM[1] = CloudStackComputePlugin.TEMPLATE_ID + " " + imageId;
		commandsDeployVM[2] = CloudStackComputePlugin.ZONE_ID + " " + ZONE_ID;
		commandsDeployVM[3] = "serviceofferingid 62d5f174-2f1e-42f0-931e-07600a05470e";
		String commandsGetFlavor[] = new String[1];
		commandsGetFlavor[0] = CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND;
		commandResponse.put(commandsDeployVM, RESPONSE_DEPLOY_VM);
		commandResponse.put(commandsGetFlavor, RESPONSE_GET_FLAVOR);
		String[] requestType = new String[2];
		requestType[0] = "post";
		requestType[1] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token, commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		cscp.requestInstance(token, categories, new HashMap<String, String>(), imageId);
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceNullImageId() {
		List<Category> categories = new ArrayList<Category>();
		Token token = new Token("api:key", null, new Date(), null);
		CloudStackComputePlugin cscp = createPlugin(null, null);
		cscp.requestInstance(token, categories, new HashMap<String, String>(), null);
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceZoneIdNull() {
		List<Category> categories = new ArrayList<Category>();
		Token token = new Token("api:key", null, new Date(), null);
		CloudStackComputePlugin cscp = createPlugin(null, null);
		cscp.requestInstance(token, categories, new HashMap<String, String>(), "");
	}
	
	@Test
	public void testRequestInstanceWithUserData() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		Map<String[], String> commandResponse = new LinkedHashMap<String[], String>();
		String[] commandsDeployVM = new String[5];
		commandsDeployVM[0] = CloudStackComputePlugin.DEPLOY_VM_COMMAND;
		commandsDeployVM[1] = CloudStackComputePlugin.TEMPLATE_ID + " " + imageId;
		commandsDeployVM[2] = CloudStackComputePlugin.ZONE_ID + " " + ZONE_ID;
		commandsDeployVM[3] = "serviceofferingid 62d5f174-2f1e-42f0-931e-07600a05470e";
		commandsDeployVM[4] = CloudStackComputePlugin.USERDATA + " userdata";
		String commandsGetFlavor[] = new String[1];
		commandsGetFlavor[0] = CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND;
		commandResponse.put(commandsDeployVM, RESPONSE_DEPLOY_VM);
		commandResponse.put(commandsGetFlavor, RESPONSE_GET_FLAVOR);
		String[] requestType = new String[2];
		requestType[0] = "post";
		requestType[1] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token, commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		HashMap<String, String> occiAttributes = new HashMap<String, String>();
		occiAttributes.put(RequestAttribute.USER_DATA_ATT.getValue(), "userdata");
		cscp.requestInstance(token, categories, occiAttributes, imageId);
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithBadResponse() {
		List<Category> categories = new ArrayList<Category>();
		String imageId = "imageId";
		categories.add(new Category(RequestConstants.SMALL_TERM,
				RequestConstants.TEMPLATE_RESOURCE_SCHEME, RequestConstants.MIXIN_CLASS));
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		Map<String[], String> commandResponse = new LinkedHashMap<String[], String>();
		String[] commandsDeployVM = new String[5];
		commandsDeployVM[0] = CloudStackComputePlugin.DEPLOY_VM_COMMAND;
		commandsDeployVM[1] = CloudStackComputePlugin.TEMPLATE_ID + " " + imageId;
		commandsDeployVM[2] = CloudStackComputePlugin.ZONE_ID + " " + ZONE_ID;
		commandsDeployVM[3] = "serviceofferingid 62d5f174-2f1e-42f0-931e-07600a05470e";
		commandsDeployVM[4] = CloudStackComputePlugin.USERDATA + " userdata";
		String commandsGetFlavor[] = new String[1];
		commandsGetFlavor[0] = CloudStackComputePlugin.LIST_SERVICE_OFFERINGS_COMMAND;
		commandResponse.put(commandsDeployVM, "whatever");
		commandResponse.put(commandsGetFlavor, RESPONSE_GET_FLAVOR);
		String[] requestType = new String[2];
		requestType[0] = "post";
		requestType[1] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token, commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		HashMap<String, String> occiAttributes = new HashMap<String, String>();
		occiAttributes.put(RequestAttribute.USER_DATA_ATT.getValue(), "userdata");
		cscp.requestInstance(token, categories, occiAttributes, imageId);
	}
	  
	@Test
	public void testGetInstances() {
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands = new String[1];
		commands[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands, RESPONSE_ONE_INSTANCE);
		String[] requestType = new String[1];
		requestType[0] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		List<Instance> instances = cscp.getInstances(token);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(InstanceState.RUNNING, instances.get(0).getState());
		commandResponse.put(commands, RESPONSE_NO_INSTANCE);
		httpClient = createHttpClientWrapperMock(token, commandResponse, requestType);
		cscp = createPlugin(httpClient, null);
		instances = cscp.getInstances(token);
		Assert.assertEquals(0, instances.size());
	}

	@Test
	public void testGetInstance() {
		String vmId = "50b2b99a-8215-4437-9dfe-17382242e08c";
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands = new String[2];
		commands[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commands[1] = CloudStackComputePlugin.VM_ID + " " + vmId;
		commandResponse.put(commands, RESPONSE_ONE_INSTANCE);
		String[] requestType = new String[1];
		requestType[0] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		Instance instance = cscp.getInstance(token, vmId);
		Assert.assertEquals(vmId, instance.getId());
		commandResponse.put(commands, RESPONSE_NO_INSTANCE);
		httpClient = createHttpClientWrapperMock(token, commandResponse, requestType);
		cscp = createPlugin(httpClient, null);
		try {
			cscp.getInstance(token, vmId);
		} catch (OCCIException e) {
			return;
		}
		fail();
	}

	@Test
	public void testRemoveInstance() {
		String vmId = "50b2b99a-8215-4437-9dfe-17382242e08c";
		Token token = new Token("api:key", null, null, null);
		URIBuilder uriBuilder = CloudStackComputePlugin.createURIBuilder(
				CLOUDSTACK_URL, CloudStackComputePlugin.DESTROY_VM_COMMAND);
		uriBuilder.addParameter(CloudStackComputePlugin.VM_ID, vmId);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands1 = new String[1];
		commands1[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands1, RESPONSE_ONE_INSTANCE);
		String[] requestType = new String[1];
		requestType[0] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);

		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		cscp.removeInstances(token);
		Mockito.verify(httpClient).doPost(uriBuilder.toString());
	}

	@Test
	public void testGetResourceInfo() {
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands1 = new String[1];
		commands1[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands1, RESPONSE_ONE_INSTANCE);
		String[] commands2 = new String[1];
		commands2[0] = CloudStackComputePlugin.LIST_RESOURCE_LIMITS_COMMAND;
		commandResponse.put(commands2, RESPONSE_RESOURCES_INFO);
		String[] requestType = new String[2];
		requestType[0] = "get";
		requestType[1] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		ResourcesInfo ri = cscp.getResourcesInfo(token);
		Assert.assertEquals("1", ri.getInstancesIdle());
		Assert.assertEquals("512", ri.getMemInUse());
		Assert.assertEquals("1", ri.getCpuInUse());
	}

	@Test
	public void testGetResourcesWithNoInstances() {
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new LinkedHashMap<String[], String>();
		String[] commands1 = new String[1];
		commands1[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands1, RESPONSE_NO_INSTANCE);
		String[] commands2 = new String[1];
		commands2[0] = CloudStackComputePlugin.LIST_RESOURCE_LIMITS_COMMAND;
		commandResponse.put(commands2, RESPONSE_RESOURCES_INFO);
		String[] requestType = new String[2];
		requestType[0] = "get";
		requestType[1] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		ResourcesInfo ri = cscp.getResourcesInfo(token);
		Assert.assertEquals("0", ri.getMemInUse());
		Assert.assertEquals("0", ri.getCpuInUse());
	}

	@Test
	public void testBypass() {
		CloudStackComputePlugin cscp = createPlugin(null, null);
		try {
			cscp.bypass(null, null);
		} catch (UnsupportedOperationException e) {
			return;
		}
		fail();
	}

	@Test
	public void testUploadImage() {
		String imageName = "name";
		String diskFormat = "format";
		String hypervisor = "KVM";
		String imagePath = "/var/www/cirros.img";
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String imageURL = imagePath.replace(IMAGE_DOWNLOADED_BASE_PATH, IMAGE_DOWNLOADED_BASE_URL + "/");
		String[] commands1 = new String[9];
		commands1[0] = CloudStackComputePlugin.REGISTER_TEMPLATE_COMMAND;
		commands1[1] = CloudStackComputePlugin.DISPLAY_TEXT + " "+ imageName;
		commands1[2] = CloudStackComputePlugin.FORMAT + " "+ diskFormat.toUpperCase();
		commands1[3] = CloudStackComputePlugin.HYPERVISOR + " "+ hypervisor;
		commands1[4] = CloudStackComputePlugin.NAME + " "+ imageName;
		commands1[5] = CloudStackComputePlugin.OS_TYPE_ID + " "+ OS_TYPE;
		commands1[6] = CloudStackComputePlugin.ZONE_ID + " "+ ZONE_ID;
		commands1[7] = CloudStackComputePlugin.URL + " "+  imageURL;
		commands1[8] = CloudStackComputePlugin.IS_PUBLIC + " "
				+ Boolean.TRUE.toString();
		commandResponse.put(commands1, "response");
		String[] requestType = new String[2];
		requestType[0] = "post";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_image_download_os_type_id", OS_TYPE);
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		cscp.uploadImage(token, imagePath, imageName, diskFormat);
	}
	
	@Test
	public void testUploadImageWithoutOSType() {
		String imageName = "name";
		String diskFormat = "format";
		String hypervisor = "KVM";
		String imagePath = "/var/www/cirros.img";
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new LinkedHashMap<String[], String>();
		String imageURL = imagePath.replace(IMAGE_DOWNLOADED_BASE_PATH, IMAGE_DOWNLOADED_BASE_URL + "/");
		String[] uploadImageCommands = new String[9];
		uploadImageCommands[0] = CloudStackComputePlugin.REGISTER_TEMPLATE_COMMAND;
		uploadImageCommands[1] = CloudStackComputePlugin.DISPLAY_TEXT + " "+ imageName;
		uploadImageCommands[2] = CloudStackComputePlugin.FORMAT + " "+ diskFormat.toUpperCase();
		uploadImageCommands[3] = CloudStackComputePlugin.HYPERVISOR + " "+ hypervisor;
		uploadImageCommands[4] = CloudStackComputePlugin.NAME + " "+ imageName;
		uploadImageCommands[5] = CloudStackComputePlugin.OS_TYPE_ID + " b0201c53-1385-11e5-be87-fa163ec5cca2";
		uploadImageCommands[6] = CloudStackComputePlugin.ZONE_ID + " "+ ZONE_ID;
		uploadImageCommands[7] = CloudStackComputePlugin.URL + " "+  imageURL;
		uploadImageCommands[8] = CloudStackComputePlugin.IS_PUBLIC + " "
				+ Boolean.TRUE.toString();
		String[] listOSTypeCommand = new String[1];
		listOSTypeCommand[0] = CloudStackComputePlugin.LIST_OS_TYPES_COMMAND;
		commandResponse.put(uploadImageCommands, "response");
		commandResponse.put(listOSTypeCommand, RESPONSE_OS_TYPE);
		String[] requestType = new String[2];
		requestType[0] = "post";
		requestType[1] = "get";
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);
		CloudStackComputePlugin cscp = createPlugin(httpClient, extraProperties);
		cscp.uploadImage(token, imagePath, imageName, diskFormat);
	}
	
	@Test
	public void testGetImageId() {
		Token token = new Token("api:key", null, null, null);
		String[] commands = new String[2];
		commands[0] = CloudStackComputePlugin.LIST_TEMPLATES_COMMAND;
		commands[1] = CloudStackComputePlugin.TEMPLATE_FILTER + " executable";
		String[] requestType = new String[1];
		requestType[0] = "get";
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		commandResponse.put(commands, RESPONSE_LIST_TEMPLATES);
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		String imageId = cscp.getImageId(token, "cirros-123");
		Assert.assertEquals("f8340307-52c6-4aec-a224-8ff84538107e",imageId);
		imageId = cscp.getImageId(token, "doesnt-exist");
		Assert.assertNull(imageId);
	}
	
	@Test
	public void testGetImageState() {
		Token token = new Token("api:key", null, null, null);
		String[] commands = new String[2];
		commands[0] = CloudStackComputePlugin.LIST_TEMPLATES_COMMAND;
		commands[1] = CloudStackComputePlugin.TEMPLATE_FILTER + " executable";
		String[] requestType = new String[1];
		requestType[0] = "get";
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		commandResponse.put(commands, RESPONSE_LIST_TEMPLATES);
		HttpClientWrapper httpClient = createHttpClientWrapperMock(token,
				commandResponse, requestType);
		CloudStackComputePlugin cscp = createPlugin(httpClient, null);
		ImageState imageState = cscp.getImageState(token, "cirros-123");
		Assert.assertEquals(ImageState.ACTIVE, imageState);
		imageState = cscp.getImageState(token, "centos-failed");
		Assert.assertEquals(ImageState.PENDING, imageState);
		imageState = cscp.getImageState(token, "doenst-exist");
		Assert.assertNull(imageState);
	}

}
