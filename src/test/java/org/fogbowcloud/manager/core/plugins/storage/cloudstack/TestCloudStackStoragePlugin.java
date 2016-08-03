package org.fogbowcloud.manager.core.plugins.storage.cloudstack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.http.client.utils.URIBuilder;
import org.fogbowcloud.manager.core.plugins.common.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.compute.cloudstack.CloudStackTestHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderAttribute;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackStoragePlugin {
	
	private static final String COMPUTE_DEFAULT_ZONE = "root";
	private static final String ZONE_ID = "zoneId";
	private static final String VOLUME_ID = "volumeId";
	private static final String VOLUME_ID1 = "dad76621-edcd-4968-a152-74d877d1961b";
	private static final String VOLUME_ID2 = "27838fa7-f998-4768-a2b1-9cf5823d98cf";
	private static final String VOLUME_ID3 = "0875574a-7513-4e88-9def-aaf2aded2d6e";

	private static String RESPONSE_ONE_INSTANCE;
	private static String RESPONSE_MULTIPLE_INSTANCES;
	private static String RESPONSE_NO_INSTANCE;
	private static String RESPONSE_CREATE_VOLUME;
	private static String RESPONSE_DELETE_VOLUME;
	private static String RESPONSE_LIST_DISK_OFFERINGS;
	
	static {
		try {
			RESPONSE_ONE_INSTANCE = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.one_storage_instance");
			RESPONSE_MULTIPLE_INSTANCES = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.multiple_storage_instances");
			RESPONSE_NO_INSTANCE = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.no_storage_instance");
			RESPONSE_CREATE_VOLUME = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.create_volume");
			RESPONSE_DELETE_VOLUME = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.delete_volume");
			RESPONSE_LIST_DISK_OFFERINGS = PluginHelper
					.getContentFile("src/test/resources/cloudstack/response.list_disk_offerings");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private CloudStackStoragePlugin createPlugin(HttpClientWrapper httpClient,
			Properties extraProperties) {
		Properties properties = new Properties();
		if (extraProperties != null) {
			properties.putAll(extraProperties);
		}
		properties.put("compute_cloudstack_api_url",
				CloudStackTestHelper.CLOUDSTACK_URL);
		properties.put("compute_cloudstack_default_zone", COMPUTE_DEFAULT_ZONE);
		properties.put("compute_cloudstack_image_download_base_path",
				CloudStackTestHelper.CLOUDSTACK_URL);
		if (httpClient == null) {
			return Mockito.spy(new CloudStackStoragePlugin(properties));
		} else {
			return Mockito.spy(new CloudStackStoragePlugin(properties, httpClient));
		}
	}
	
	@Test
	public void testRequestInstance() {
		Token token = new Token("api:key", null, new Date(), null);
		List<Category> categories = new ArrayList<Category>();
		HashMap<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OrderAttribute.STORAGE_SIZE.getValue(), "1");
		Properties extraProperties = new Properties();
		extraProperties.put("compute_cloudstack_zone_id", ZONE_ID);

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String volumeName = "volName";
		
		String createVolumeUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.CREATE_VOLUME_COMMAND,
				CloudStackStoragePlugin.ZONE_ID, ZONE_ID,
				CloudStackStoragePlugin.VOLUME_NAME, volumeName,
				CloudStackStoragePlugin.DISK_OFFERING_ID,
				"62d5f174-2f1e-42f0-931e-07600a05470e", 
				CloudStackStoragePlugin.VOLUME_SIZE, "1");
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.POST, createVolumeUrl, RESPONSE_CREATE_VOLUME, 200);
		
		String listDiskOfferingsUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.LIST_DISK_OFFERINGS_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token, 
				CloudStackTestHelper.GET, listDiskOfferingsUrl, RESPONSE_LIST_DISK_OFFERINGS, 200);

		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient,
				extraProperties);
		
		Mockito.doReturn(volumeName).when(storagePlugin).generateName();
		
		storagePlugin.requestInstance(token, categories, xOCCIAtt);
	}
	
	@Test
	public void testGetInstancesOneInstance() {
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String listVolumesUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.LIST_VOLUMES_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listVolumesUrl, RESPONSE_ONE_INSTANCE, 200);
		
		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient, extraProperties);
		List<Instance> instances = storagePlugin.getInstances(token);
		Assert.assertEquals(1, instances.size());
	}
	
	@Test
	public void testGetInstancesNoInstance() {
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String listVolumesUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.LIST_VOLUMES_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listVolumesUrl, RESPONSE_NO_INSTANCE, 200);
		
		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient, extraProperties);
		List<Instance> instances = storagePlugin.getInstances(token);
		Assert.assertEquals(0, instances.size());
	}
	
	@Test
	public void testGetInstancesMultipleInstances() {
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String listVolumesUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.LIST_VOLUMES_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listVolumesUrl, RESPONSE_MULTIPLE_INSTANCES, 200);
		
		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient, extraProperties);
		List<Instance> instances = storagePlugin.getInstances(token);
		Assert.assertEquals(3, instances.size());
	}
	
	@Test
	public void testGetInstance() {
		Token token = new Token("api:key", null, new Date(), null);
		Properties extraProperties = new Properties();

		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);

		String listVolumesUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.LIST_VOLUMES_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token,
				CloudStackTestHelper.GET, listVolumesUrl, RESPONSE_MULTIPLE_INSTANCES, 200);
		
		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient, extraProperties);
		Instance instance = storagePlugin.getInstance(token, VOLUME_ID2);
		Assert.assertEquals(VOLUME_ID2, instance.getId());
	}
	
	@Test
	public void testRemoveInstance() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		
		URIBuilder uriBuilder = CloudStackStoragePlugin.createURIBuilder(
				CloudStackTestHelper.CLOUDSTACK_URL,
				CloudStackStoragePlugin.DELETE_VOLUME_COMMAND);
		uriBuilder.addParameter(CloudStackStoragePlugin.VOLUME_ID, VOLUME_ID);
		String deleteVolumeUrl = uriBuilder.toString();
		
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token, 
				CloudStackTestHelper.GET, deleteVolumeUrl, RESPONSE_DELETE_VOLUME, 200);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		String deleteVolumeSignedUrl = uriBuilder.toString();
		
		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient, null);
		storagePlugin.removeInstance(token, VOLUME_ID);
		Mockito.verify(httpClient).doGet(deleteVolumeSignedUrl);
	}
	
	@Test
	public void testRemoveInstances() {
		Token token = new Token("api:key", null, null, null);
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		
		URIBuilder uriBuilder1 = CloudStackStoragePlugin.createURIBuilder(
				CloudStackTestHelper.CLOUDSTACK_URL,
				CloudStackStoragePlugin.DELETE_VOLUME_COMMAND);
		uriBuilder1.addParameter(CloudStackStoragePlugin.VOLUME_ID, VOLUME_ID1);
		String deleteVolumeUrl1 = uriBuilder1.toString();
		
		URIBuilder uriBuilder2 = CloudStackStoragePlugin.createURIBuilder(
				CloudStackTestHelper.CLOUDSTACK_URL,
				CloudStackStoragePlugin.DELETE_VOLUME_COMMAND);
		uriBuilder2.addParameter(CloudStackStoragePlugin.VOLUME_ID, VOLUME_ID2);
		String deleteVolumeUrl2 = uriBuilder2.toString();
		
		URIBuilder uriBuilder3 = CloudStackStoragePlugin.createURIBuilder(
				CloudStackTestHelper.CLOUDSTACK_URL,
				CloudStackStoragePlugin.DELETE_VOLUME_COMMAND);
		uriBuilder3.addParameter(CloudStackStoragePlugin.VOLUME_ID, VOLUME_ID3);
		String deleteVolumeUrl3 = uriBuilder3.toString();
		
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token, 
				CloudStackTestHelper.GET, deleteVolumeUrl1, RESPONSE_DELETE_VOLUME, 200);
		CloudStackHelper.sign(uriBuilder1, token.getAccessId());
		String deleteVolumeSignedUrl1 = uriBuilder1.toString();
		
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token, 
				CloudStackTestHelper.GET, deleteVolumeUrl2, RESPONSE_DELETE_VOLUME, 200);
		CloudStackHelper.sign(uriBuilder2, token.getAccessId());
		String deleteVolumeSignedUrl2 = uriBuilder2.toString();
		
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token, 
				CloudStackTestHelper.GET, deleteVolumeUrl3, RESPONSE_DELETE_VOLUME, 200);
		CloudStackHelper.sign(uriBuilder3, token.getAccessId());
		String deleteVolumeSignedUrl3 = uriBuilder3.toString();
		
		String listVolumesUrl = CloudStackTestHelper.createURL(
				CloudStackStoragePlugin.LIST_VOLUMES_COMMAND);
		CloudStackTestHelper.recordHTTPClientWrapperRequest(httpClient, token, 
				CloudStackTestHelper.GET, listVolumesUrl, RESPONSE_MULTIPLE_INSTANCES, 200);
		
		CloudStackStoragePlugin storagePlugin = createPlugin(httpClient, null);
		storagePlugin.removeInstances(token);
		Mockito.verify(httpClient).doGet(deleteVolumeSignedUrl1);
		Mockito.verify(httpClient).doGet(deleteVolumeSignedUrl2);
		Mockito.verify(httpClient).doGet(deleteVolumeSignedUrl3);
	}

}
