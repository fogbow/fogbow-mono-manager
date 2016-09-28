package org.fogbowcloud.manager.core.plugins.network.azure;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.client.HttpClient;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.compute.azure.AzureConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.management.network.NetworkManagementClient;
import com.microsoft.windowsazure.management.network.NetworkOperations;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.AddressSpace;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.Subnet;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.VirtualNetworkSite;
import com.microsoft.windowsazure.management.network.models.NetworkSetConfigurationParameters;

public class TestAzureNetworkPlugin {

	private static final String DEFAULT_GATEWAY_INFO = "000000-gateway_info";
	private static final String DEFAULT_TENANT_ID = "tenantId";
	private static final String DEFAULT_NETWORK_URL = "http://localhost:0000";
	
	private AzureNetworkPlugin azureNetworkPlugin;
	private NetworkManagementClient networkManagementClientMock;
	private Configuration configurationMock;
	private NetworkOperations networkOperationsMock;

	private Token defaultToken;
	private HttpClient client;
	
	private String azureRegion = "Central US";
	private String subscriptionId = "subscId01";
	private String keystorePath = "keystorePath01";
	private String keyStorePassword = "keyStorePass01";
	
	
	@Before
	public void setUp() throws Exception {
		
		networkManagementClientMock = Mockito.mock(NetworkManagementClient.class);
		configurationMock = Mockito.mock(Configuration.class);
		networkOperationsMock = Mockito.mock(NetworkOperations.class);
		
		Properties properties = new Properties();
		properties.put(AzureConfigurationConstants.COMPUTE_AZURE_REGION, azureRegion);
		this.azureNetworkPlugin = Mockito.spy(new AzureNetworkPlugin(properties));
		
		Mockito.doReturn(configurationMock)
			.when(azureNetworkPlugin).createConfiguration(Mockito.any(Token.class));
		Mockito.doReturn(networkManagementClientMock)
			.when(azureNetworkPlugin).createNetworkManagementClient(Mockito.any(Token.class));
		
		Mockito.doReturn(networkOperationsMock).when(networkManagementClientMock).getNetworksOperations();
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(AzureAttributes.SUBSCRIPTION_ID_KEY, subscriptionId);
		attributes.put(AzureAttributes.KEYSTORE_PATH_KEY, keystorePath);
		attributes.put(AzureAttributes.KEYSTORE_PASSWORD_KEY, keyStorePassword);
		this.defaultToken = new Token("accessId", new Token.User("user", "user"), 
				new Date(), attributes);
		
	}
	
	@After
	public void validate() {
	    Mockito.validateMockitoUsage();
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutTenantId() {
		Token token = new Token("accessId", new Token.User("user", "user"), 
				new Date(), new HashMap<String, String>());
		azureNetworkPlugin.requestInstance(token, null, null);
	}
	
	@Test
	public void testRequestInstanceSuccess() throws Exception{
		
		String gateway = "192.168.0.1";
		String address = "192.168.0.0/24";
		
		ArrayList<VirtualNetworkSite> virtualNetworkSites = new ArrayList<VirtualNetworkSite>();
		
		NetworkListResponse networkListResponse = new NetworkListResponse();
		networkListResponse.setRequestId("request01");
		networkListResponse.setStatusCode(200);
		networkListResponse.setVirtualNetworkSites(virtualNetworkSites);
		
		OperationResponse operationResponse = new OperationResponse();
		
		//Preparing mocks to this test.
		Mockito.doReturn(networkListResponse).when(networkOperationsMock).list();
		Mockito.doReturn(operationResponse).when(networkOperationsMock)
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_GATEWAY, gateway);
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, address);
		
		List<Category> categories = new ArrayList<Category>();
		
		String networkId = azureNetworkPlugin.requestInstance(defaultToken, categories, xOCCIAtt);
		
		Mockito.verify(networkOperationsMock, Mockito.times(1))
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		Assert.assertNotNull(networkId);
		Assert.assertTrue(networkId.startsWith(AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME));
		Assert.assertEquals(1, virtualNetworkSites.size());
		
	}
	
	@Test
	public void testRequestInstanceSuccessWithExistingNetwork() throws Exception{
		
		String gatewayA = "192.168.2.1";
		String addressA = "192.168.2.0/24";
		String networkName = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"A1234";
		
		VirtualNetworkSite vns = createVirtualNetworSite(addressA, networkName);
		
		String gatewayB = "192.168.2.1";
		String addressB = "192.168.2.0/24";
		
		ArrayList<VirtualNetworkSite> virtualNetworkSites = new ArrayList<VirtualNetworkSite>();
		virtualNetworkSites.add(vns);
		
		NetworkListResponse networkListResponse = new NetworkListResponse();
		networkListResponse.setRequestId("request01");
		networkListResponse.setStatusCode(200);
		networkListResponse.setVirtualNetworkSites(virtualNetworkSites);
		
		OperationResponse operationResponse = new OperationResponse();
		
		//Preparing mocks to this test.
		Mockito.doReturn(networkListResponse).when(networkOperationsMock).list();
		Mockito.doReturn(operationResponse).when(networkOperationsMock)
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_GATEWAY, gatewayB);
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, addressB);
		
		List<Category> categories = new ArrayList<Category>();
		
		String networkId = azureNetworkPlugin.requestInstance(defaultToken, categories, xOCCIAtt);
		
		Mockito.verify(networkOperationsMock, Mockito.times(1))
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		Assert.assertNotNull(networkId);
		Assert.assertTrue(networkId.startsWith(AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME));
		Assert.assertEquals(2, virtualNetworkSites.size());
		
	}

	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutNetworkAddressError() throws Exception{
		
		String gateway = "192.168.0.1";
		String address = "192.168.0.0/24";
		
		ArrayList<VirtualNetworkSite> virtualNetworkSites = new ArrayList<VirtualNetworkSite>();
		
		NetworkListResponse networkListResponse = new NetworkListResponse();
		networkListResponse.setRequestId("request01");
		networkListResponse.setStatusCode(200);
		networkListResponse.setVirtualNetworkSites(virtualNetworkSites);
		
		OperationResponse operationResponse = new OperationResponse();
		
		//Preparing mocks to this test.
		Mockito.doReturn(networkListResponse).when(networkOperationsMock).list();
		Mockito.doReturn(operationResponse).when(networkOperationsMock)
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_GATEWAY, gateway);
		//xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, address);
		
		List<Category> categories = new ArrayList<Category>();
		
		String networkId = azureNetworkPlugin.requestInstance(defaultToken, categories, xOCCIAtt);
		
	}
	
	@Test
	public void getInstanceSuccess() throws Exception{
		
		String gatewayA = "192.168.0.1";
		String addressA = "192.168.0.0/24";
		String networkNameA = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"A1234";
		
		String gatewayB = "192.168.0.1";
		String addressB = "192.168.0.0/24";
		String networkNameB = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"B1234";
		
		VirtualNetworkSite vnsA = createVirtualNetworSite(addressA, networkNameA);
		VirtualNetworkSite vnsB = createVirtualNetworSite(addressB, networkNameB);
		
		ArrayList<VirtualNetworkSite> virtualNetworkSites = new ArrayList<VirtualNetworkSite>();
		virtualNetworkSites.add(vnsA);
		virtualNetworkSites.add(vnsB);
		
		NetworkListResponse networkListResponse = new NetworkListResponse();
		networkListResponse.setRequestId("request01");
		networkListResponse.setStatusCode(200);
		networkListResponse.setVirtualNetworkSites(virtualNetworkSites);
		
		OperationResponse operationResponse = new OperationResponse();
		
		//Preparing mocks to this test.
		Mockito.doReturn(networkListResponse).when(networkOperationsMock).list();
		Mockito.doReturn(operationResponse).when(networkOperationsMock)
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		
		Instance intanceA = azureNetworkPlugin.getInstance(defaultToken, networkNameA);
		Instance intanceB = azureNetworkPlugin.getInstance(defaultToken, networkNameB);
		
		Assert.assertNotNull(intanceA);
		Assert.assertNotNull(intanceB);
		Assert.assertEquals(networkNameA, intanceA.getId());
		Assert.assertEquals(networkNameB, intanceB.getId());
		Assert.assertEquals(addressA, intanceA.getAttributes().get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(addressB, intanceB.getAttributes().get(OCCIConstants.NETWORK_ADDRESS));
		
	}
	
	@Test
	public void removeInstanceSuccess() throws Exception {
		
		
		String gatewayA = "192.168.0.1";
		String addressA = "192.168.0.0/24";
		String networkNameA = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"A1234";
		
		String gatewayB = "192.168.0.1";
		String addressB = "192.168.0.0/24";
		String networkNameB = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"B1234";
		
		VirtualNetworkSite vnsA = createVirtualNetworSite(addressA, networkNameA);
		VirtualNetworkSite vnsB = createVirtualNetworSite(addressB, networkNameB);
		
		ArrayList<VirtualNetworkSite> virtualNetworkSites = new ArrayList<VirtualNetworkSite>();
		virtualNetworkSites.add(vnsA);
		virtualNetworkSites.add(vnsB);
		
		NetworkListResponse networkListResponse = new NetworkListResponse();
		networkListResponse.setRequestId("request01");
		networkListResponse.setStatusCode(200);
		networkListResponse.setVirtualNetworkSites(virtualNetworkSites);
		
		OperationResponse operationResponse = new OperationResponse();
		
		//Preparing mocks to this test.
		Mockito.doReturn(networkListResponse).when(networkOperationsMock).list();
		Mockito.doReturn(operationResponse).when(networkOperationsMock)
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		
		Assert.assertEquals(2, virtualNetworkSites.size());
		azureNetworkPlugin.removeInstance(defaultToken, networkNameA);
		Assert.assertEquals(1, virtualNetworkSites.size());
		
		Instance intanceA = azureNetworkPlugin.getInstance(defaultToken, networkNameA);
		Instance intanceB = azureNetworkPlugin.getInstance(defaultToken, networkNameB);
		
		Assert.assertNull(intanceA);
		Assert.assertNotNull(intanceB);
		
	}
	
	@Test(expected=OCCIException.class)
	public void removeInstanceNotFound() throws Exception {
		
		
		String gatewayA = "192.168.0.1";
		String addressA = "192.168.0.0/24";
		String networkNameA = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"A1234";
		
		String networkNameB = AzureNetworkPlugin.AZURE_NETWORK_DEFAULT_PREFIX_NAME+"B1234";
		
		VirtualNetworkSite vnsA = createVirtualNetworSite(addressA, networkNameA);
		
		ArrayList<VirtualNetworkSite> virtualNetworkSites = new ArrayList<VirtualNetworkSite>();
		virtualNetworkSites.add(vnsA);
		
		NetworkListResponse networkListResponse = new NetworkListResponse();
		networkListResponse.setRequestId("request01");
		networkListResponse.setStatusCode(200);
		networkListResponse.setVirtualNetworkSites(virtualNetworkSites);
		
		OperationResponse operationResponse = new OperationResponse();
		
		//Preparing mocks to this test.
		Mockito.doReturn(networkListResponse).when(networkOperationsMock).list();
		Mockito.doReturn(operationResponse).when(networkOperationsMock)
			.beginSettingConfiguration(Mockito.any(NetworkSetConfigurationParameters.class));
		
		azureNetworkPlugin.removeInstance(defaultToken, networkNameB);
		
	}
	
	
	private VirtualNetworkSite createVirtualNetworSite(String addressA, String networkName) {
		AddressSpace address = new AddressSpace();
		ArrayList<String> addressPrefix = new ArrayList<String>();
		addressPrefix.add(addressA);
		address.setAddressPrefixes(addressPrefix);
		
		Subnet subnet = new Subnet();
		subnet.setAddressPrefix(addressA);
		subnet.setName(AzureNetworkPlugin.AZURESUB_NETWORK_DEFAULT_PREFIX_NAME+networkName);
		ArrayList<Subnet> subnets = new ArrayList<Subnet>();
		subnets.add(subnet);
		
		VirtualNetworkSite vns = new VirtualNetworkSite();
		vns.setLocation(azureRegion);
		vns.setName(networkName);
		vns.setSubnets(subnets);
		vns.setAddressSpace(address);
		return vns;
	}
	

}
