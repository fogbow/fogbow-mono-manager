package org.fogbowcloud.manager.core.plugins.network.opennebula;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.compute.opennebula.OneConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.compute.opennebula.OpenNebulaClientFactory;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vnet.VirtualNetwork;

public class TestOpenNebulaNetworkPlugin {
	private static final String OPEN_NEBULA_NETWORK_BRIDGE = "br0";
	private static final String OPEN_NEBULA_URL = "http://localhost:2633/RPC2";
	private static final String NETWORK_ID = "0";
	private static final String VLAN_LABEL = "fogbow_test_vlan";
	private static final String VLAN_ID = "12";
	private static final String NETWORK_ADDRESS = "10.0.0.0/24";
	private static final String NETWORK_GATEWAY = "10.0.0.1";
	private static final String NETWORK_NAME = "fogbow123456";
	
	private Properties properties;
	private Token defaultToken;
	
	@Before
	public void setUp() {
		properties = new Properties();
		properties.put(OneConfigurationConstants.COMPUTE_ONE_URL, OPEN_NEBULA_URL);
		properties.put(OneConfigurationConstants.NETWORK_ONE_BRIDGE, OPEN_NEBULA_NETWORK_BRIDGE);
		
		defaultToken = new Token("oneadmin:opennebula",
				"oneadmin", DefaultDataTestHelper.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>());
	}
	
	@Test
	public void testRequestInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		OpenNebulaNetworkPlugin plugin = new OpenNebulaNetworkPlugin(properties, clientFactory);
		
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(defaultToken.getAccessId(), 
				OPEN_NEBULA_URL)).thenReturn(oneClient);
		Mockito.when(clientFactory.allocateNetwork(Mockito.eq(oneClient), 
				Mockito.any(String.class))).thenReturn(NETWORK_ID);
		
		List<Category> categories = new LinkedList<Category>();
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ALLOCATION, 
				OCCIConstants.NetworkAllocation.DYNAMIC.getValue());
		xOCCIAtt.put(OCCIConstants.NETWORK_LABEL, VLAN_LABEL);
		xOCCIAtt.put(OCCIConstants.NETWORK_VLAN, VLAN_ID);
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, NETWORK_ADDRESS);
		xOCCIAtt.put(OCCIConstants.NETWORK_GATEWAY, NETWORK_GATEWAY);
		String instanceId = plugin.requestInstance(defaultToken, categories, xOCCIAtt);
		
		Assert.assertEquals(NETWORK_ID, instanceId);
	}
	
	@Test
	public void testGetInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		OpenNebulaNetworkPlugin networkPlugin = new OpenNebulaNetworkPlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		VirtualNetwork vnet = Mockito.mock(VirtualNetwork.class);
		Mockito.when(vnet.getId()).thenReturn(NETWORK_ID);
		Mockito.when(vnet.getName()).thenReturn(NETWORK_NAME);
		Mockito.when(vnet.xpath(Mockito.eq("TEMPLATE/NETWORK_ADDRESS")))
			.thenReturn(NETWORK_ADDRESS);
		Mockito.when(vnet.xpath(Mockito.eq("TEMPLATE/NETWORK_GATEWAY")))
			.thenReturn(NETWORK_GATEWAY);
		Mockito.when(vnet.xpath(Mockito.eq("TEMPLATE/VLAN_ID")))
			.thenReturn("");
		
		Mockito.when(clientFactory.createVirtualNetwork(Mockito.eq(oneClient), 
				Mockito.eq(NETWORK_ID))).thenReturn(vnet);
		
		Instance instance = networkPlugin.getInstance(defaultToken, NETWORK_ID);
		Assert.assertEquals(NETWORK_ID, instance.getId());
	}
	
	@Test
	public void testRemoveInstance() {
		OpenNebulaClientFactory clientFactory = Mockito.mock(OpenNebulaClientFactory.class);
		OpenNebulaNetworkPlugin networkPlugin = new OpenNebulaNetworkPlugin(properties, clientFactory);
		Client oneClient = Mockito.mock(Client.class);
		Mockito.when(clientFactory.createClient(
				defaultToken.getAccessId(), OPEN_NEBULA_URL)).thenReturn(oneClient);
		
		VirtualNetwork vnet = Mockito.mock(VirtualNetwork.class);
		OneResponse response = Mockito.mock(OneResponse.class);
		Mockito.when(response.isError()).thenReturn(false);
		Mockito.when(vnet.delete()).thenReturn(response);
		
		Mockito.when(clientFactory.createVirtualNetwork(Mockito.eq(oneClient), 
				Mockito.eq(NETWORK_ID))).thenReturn(vnet);
		
		networkPlugin.removeInstance(defaultToken, NETWORK_ID);
		Mockito.verify(vnet).delete();
	}

}
