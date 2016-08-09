package org.fogbowcloud.manager.core.plugins.network.cloudstack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.plugins.common.cloudstack.CloudStackHelper;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.plugins.network.opennebula.OpenNebulaNetworkPlugin;
import org.fogbowcloud.manager.core.plugins.util.HttpClientWrapper;
import org.fogbowcloud.manager.core.plugins.util.HttpResponseWrapper;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vnet.VirtualNetwork;

public class TestCloudstackNetworkPlugin {
	
	
	private static final ProtocolVersion PROTO = new ProtocolVersion("HTTP", 1, 1);
	
	private Properties properties;
	private Token defaultToken;
	
	@Before
	public void setUp() {
	
		defaultToken = new Token("oneadmin:opennebula",
				"oneadmin", DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
		
	}
	
	@Test
	public void testRequestInstance() {
		
		String networkAddress = "10.10.0.0/24";
		String networkGateway = "10.10.0.1";
		
		String INSTANCE_ID = "mockid100";
		
		String responseContent = "{createnetworkresponse:{network:{id:"+INSTANCE_ID+"}}}";
		
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackNetworkPlugin plugin = createPlugin(httpClient, new Properties());
		
		
		HttpResponseWrapper returned = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, 200, "Success"), responseContent);
		
		
		Mockito.when(httpClient.doPost(Mockito.anyString())).thenReturn(returned);
		
		List<Category> categories = new LinkedList<Category>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, networkAddress);
		xOCCIAtt.put(OCCIConstants.NETWORK_GATEWAY, networkGateway);
		String instanceId = plugin.requestInstance(defaultToken, categories, xOCCIAtt);
		
		Assert.assertEquals(INSTANCE_ID, instanceId);
	}
	
	@Test
	public void testGetInstance() {
		
		String INSTANCE_ID = "mockid100";
		String INSTANCE_NAME = "instanceName";
		String INSTANCE_STATE = "ACTIVE";
		String INSTANCE_CIDR = "10.10.0.0/24";
		String INSTANCE_GATEWAY = "10.10.0.1";
		
		String responseContent = "{listnetworksresponse:{network:[{id:"+INSTANCE_ID+","
				+ "name:\""+INSTANCE_NAME+"\","
				+ "state:\""+INSTANCE_STATE+"\","
				+ "cidr:\""+INSTANCE_CIDR+"\","
				+ "gateway:\""+INSTANCE_GATEWAY+"\"}]}}";
		
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackNetworkPlugin plugin = createPlugin(httpClient, new Properties());
		
		HttpResponseWrapper returned = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, 200, "Success"), responseContent);
		Mockito.when(httpClient.doPost(Mockito.anyString())).thenReturn(returned);
		
		Instance instance = plugin.getInstance(defaultToken, INSTANCE_ID);
		
		Assert.assertEquals(INSTANCE_ID, instance.getAttributes().get(OCCIConstants.ID));
		Assert.assertEquals(INSTANCE_NAME, instance.getAttributes().get(OCCIConstants.TITLE));
		Assert.assertEquals(INSTANCE_STATE, instance.getAttributes().get(OCCIConstants.NETWORK_STATE));
		Assert.assertEquals(INSTANCE_CIDR, instance.getAttributes().get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(INSTANCE_GATEWAY, instance.getAttributes().get(OCCIConstants.NETWORK_GATEWAY));
		

	}
	
	@Test
	public void testRemoveInstance() {
		
		String responseContent = "ok";
		String INSTANCE_ID = "mockid100";
		
		HttpClientWrapper httpClient = Mockito.mock(HttpClientWrapper.class);
		CloudStackNetworkPlugin plugin = createPlugin(httpClient, new Properties());
		
		URIBuilder uriBuilder = plugin.createURIBuilder("http://mock:8080", plugin.DELETE_NETWORK_COMMAND);
		uriBuilder.addParameter(plugin.NETWORK_ID, INSTANCE_ID);
		CloudStackHelper.sign(uriBuilder, defaultToken.getAccessId());
		
		HttpResponseWrapper returned = new HttpResponseWrapper
				(new BasicStatusLine(PROTO, 200, "Success"), responseContent);
		Mockito.when(httpClient.doPost(Mockito.eq(uriBuilder.toString()))).thenReturn(returned);
		
		
	}

	private CloudStackNetworkPlugin createPlugin(HttpClientWrapper httpClient,
			Properties extraProperties) {
		
		Properties properties = new Properties();
		if (extraProperties != null) {
			properties.putAll(extraProperties);
		}
		properties.put("network_cloudstack_api_url","mockUrl");
		properties.put("network_cloudstack_zone_id", "01");
		properties.put("network_cloudstack_netoffering_id","offering01");
		
		if (httpClient == null) {
			return new CloudStackNetworkPlugin(properties);
		} else {
			return new CloudStackNetworkPlugin(properties, httpClient);
		}
	}
}
