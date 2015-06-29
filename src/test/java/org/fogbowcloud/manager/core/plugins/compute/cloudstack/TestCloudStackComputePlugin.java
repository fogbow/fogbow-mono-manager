package org.fogbowcloud.manager.core.plugins.compute.cloudstack;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.http.ProtocolVersion;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.identity.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.InstanceState;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestCloudStackComputePlugin {

	private static String ONE_INSTANCE;
	private static final String NO_INSTANCE = "{ \"listvirtualmachinesresponse\" : {}}";
	private static String RESOURCES_INFO;
	private static final String COMPUTE_DEFAULT_ZONE = "root";
	private static final String CLOUDSTACK_URL = "http://10.4.10.247:8080/client/api";
	
	@Before
	public void setUp() throws IOException {
		ONE_INSTANCE = PluginHelper.getContentFile("src/test/resources/cloudstack/response.one_instance");
		RESOURCES_INFO = PluginHelper.getContentFile("src/test/resources/cloudstack/response.resources_info");
	}

	private CloudStackComputePlugin createPlugin(HttpClientWrapper httpClient) {
		Properties properties = new Properties();
		properties.put("compute_cloudstack_api_url", CLOUDSTACK_URL);
		properties.put("compute_cloudstack_default_zone", COMPUTE_DEFAULT_ZONE);
		if (httpClient == null) {
			return new CloudStackComputePlugin(properties);
		} else {
			return new CloudStackComputePlugin(properties, httpClient);
		}
	}
	
	private HttpClientWrapper createWrapperDoGetMock(Token token, Map<String[], String> commandResponse) {
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		ProtocolVersion proto = new ProtocolVersion("HTTP", 1, 1);
		for (Entry<String[], String> entry : commandResponse.entrySet()) {
		    String command = entry.getKey()[0];
		    String response = entry.getValue();
		    URIBuilder uriBuilder = CloudStackComputePlugin.createURIBuilder(CLOUDSTACK_URL, 
		    		command);
		    if (entry.getKey().length > 0) {
		    	String [] parameters = entry.getKey();
		    	for (int i = 1; i < parameters.length; i++) {
		    		String parameter = parameters[i];
		    		uriBuilder.addParameter(parameter.split(":")[0], parameter.split(":")[1]);
		    	}
		    }
			CloudStackHelper.sign(uriBuilder, token.getAccessId());
			HttpResponseWrapper returned = new HttpResponseWrapper(
					new BasicStatusLine(proto, 200, "test reason"),
					response);
			Mockito.when(httpClient.doGet(uriBuilder.toString()))
			.thenReturn(returned);
		}
		return httpClient;
	}

	@Test
	public void testGetInstances() {
		Token token = new Token("api:key", null, null, null);
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands = new String[1];
		commands[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands, ONE_INSTANCE);
		HttpClientWrapper httpClient = createWrapperDoGetMock(token, commandResponse);
		CloudStackComputePlugin cscp = createPlugin(httpClient);
		List<Instance> instances = cscp.getInstances(token);
		Assert.assertEquals(1, instances.size());
		Assert.assertEquals(InstanceState.RUNNING, instances.get(0).getState());
		commandResponse.put(commands, NO_INSTANCE);
		httpClient = createWrapperDoGetMock(token, commandResponse);
		cscp = createPlugin(httpClient);
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
		commands[1] = CloudStackComputePlugin.VM_ID + ":" + vmId;
		commandResponse.put(commands, ONE_INSTANCE);
		HttpClientWrapper httpClient = createWrapperDoGetMock(token, commandResponse);
		CloudStackComputePlugin cscp = createPlugin(httpClient);
		Instance instance = cscp.getInstance(token, vmId);
		Assert.assertEquals(vmId, instance.getId());
		commandResponse.put(commands, NO_INSTANCE);
		httpClient = createWrapperDoGetMock(token, commandResponse);
		cscp = createPlugin(httpClient);
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
		URIBuilder uriBuilder = CloudStackComputePlugin.createURIBuilder
				(CLOUDSTACK_URL, CloudStackComputePlugin.DESTROY_VM_COMMAND);
		uriBuilder.addParameter(CloudStackComputePlugin.VM_ID, vmId);
		CloudStackHelper.sign(uriBuilder, token.getAccessId());
		
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands1 = new String[1];
		commands1[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands1, ONE_INSTANCE);
		HttpClientWrapper httpClient = createWrapperDoGetMock(token, commandResponse);
		
		CloudStackComputePlugin cscp = createPlugin(httpClient);
		cscp.removeInstances(token);
		Mockito.verify(httpClient).doPost(uriBuilder.toString());
	}
	
	@Test 
	public void testGetResourceInfo() {
		//TODO: More Asserts
		Token token = new Token("api:key", null, null, null);
		
		Map<String[], String> commandResponse = new HashMap<String[], String>();
		String[] commands1 = new String[1];
		commands1[0] = CloudStackComputePlugin.LIST_VMS_COMMAND;
		commandResponse.put(commands1, ONE_INSTANCE);
		String[] commands2 = new String[1];
		commands2[0] = CloudStackComputePlugin.LIST_RESOURCE_LIMITS_COMMAND;
		commandResponse.put(commands2, RESOURCES_INFO);
		
		HttpClientWrapper httpClient = createWrapperDoGetMock(token, commandResponse);
		CloudStackComputePlugin cscp = createPlugin(httpClient);
		ResourcesInfo ri = cscp.getResourcesInfo(token);
		Assert.assertEquals("1", ri.getInstancesIdle());
	
	}

}
