package org.fogbowcloud.manager.core.plugins.network.azure;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.plugins.NetworkPlugin;
import org.fogbowcloud.manager.core.plugins.common.azure.AzureAttributes;
import org.fogbowcloud.manager.core.plugins.compute.azure.AzureConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.instance.Instance.Link;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.utils.KeyStoreType;
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration;
import com.microsoft.windowsazure.management.network.NetworkManagementClient;
import com.microsoft.windowsazure.management.network.NetworkManagementService;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.AddressSpace;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.Subnet;
import com.microsoft.windowsazure.management.network.models.NetworkListResponse.VirtualNetworkSite;
import com.microsoft.windowsazure.management.network.models.NetworkSetConfigurationParameters;

public class AzureNetworkPlugin implements NetworkPlugin {
	// public class AzureNetworkPlugin {

	private static final Logger LOGGER = Logger.getLogger(AzureNetworkPlugin.class);

	public static final String AZURE_NETWORK_DEFAULT_PREFIX_NAME = "fogbow-azure-network";
	public static final String AZURESUB_NETWORK_DEFAULT_PREFIX_NAME = "subnetwork_";

	private final String DEFAULT_SECURITY_GROUP_LABEL = "Fogbow-network-label";

	private String region;

	public AzureNetworkPlugin(Properties properties) throws Exception {
		this.region = properties.getProperty(AzureConfigurationConstants.COMPUTE_AZURE_REGION);
		if (region == null) {
			region = "Central US";
		}
	}

	@Override
	public String requestInstance(Token token, List<Category> categories, Map<String, String> xOCCIAtt) {

		try {

			int numberId = (int) (Math.random() * 10000000);
			String networkName = AZURE_NETWORK_DEFAULT_PREFIX_NAME + numberId;
			String subNetworkName = AZURESUB_NETWORK_DEFAULT_PREFIX_NAME + networkName;
			String gateway = xOCCIAtt.get(OCCIConstants.NETWORK_GATEWAY);
			String networkAddres = xOCCIAtt.get(OCCIConstants.NETWORK_ADDRESS);

			if (networkAddres == null || networkAddres.isEmpty()) {
				throw new OCCIException(ErrorType.BAD_REQUEST, "Network Addres cannot be null");
			}

			NetworkManagementClient networkManagementClient = createNetworkManagementClient(token);
			NetworkConfigModel networkConfigModel = new NetworkConfigModel(
					networkManagementClient.getNetworksOperations().list());

			networkConfigModel.addVirtualNetworkSite(
					createVirtualNetworkSite(gateway, networkAddres, networkName, subNetworkName));

			NetworkSetConfigurationParameters networkConfigs = new NetworkSetConfigurationParameters(
					networkConfigModel.toString());

			networkManagementClient.getNetworksOperations().beginSettingConfiguration(networkConfigs);
			return networkName;

		} catch (Exception e) {
			LOGGER.error("It was not possible to create the Azure Network", e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"It was not possible to create the Azure Network: " + e.getMessage());
		}

	}

	@Override
	public Instance getInstance(Token token, String instanceId) {

		if (instanceId == null) {
			return null;
		}

		NetworkManagementClient networkManagementClient = createNetworkManagementClient(token);
		try {

			NetworkListResponse networks = networkManagementClient.getNetworksOperations().list();
			for (VirtualNetworkSite vns : networks.getVirtualNetworkSites()) {

				if (instanceId.equalsIgnoreCase(vns.getName())) {
					return parseInstance(vns);
				}
			}

		} catch (Exception e) {
			LOGGER.error("It was not possible to get the Azure Network with id " + instanceId, e);
		}
		return null;
	}

	@Override
	public void removeInstance(Token token, String instanceId) {

		NetworkManagementClient networkManagementClient = createNetworkManagementClient(token);
		try {

			NetworkListResponse networks = networkManagementClient.getNetworksOperations().list();
			NetworkConfigModel networkConfigModel = new NetworkConfigModel(networks);

			if (!networkConfigModel.removeVirtualNetworkSite(instanceId)) {
				throw new OCCIException(ErrorType.BAD_REQUEST, "Instance not found");
			}

			NetworkSetConfigurationParameters networkConfigs = new NetworkSetConfigurationParameters(
					networkConfigModel.toString());

			networkManagementClient.getNetworksOperations().beginSettingConfiguration(networkConfigs);

		} catch (Exception e) {
			LOGGER.error("It was not possible to get the Azure Network with id " + instanceId, e);
			throw new OCCIException(ErrorType.BAD_REQUEST,
					"Error while trying to remove network " + instanceId + " - ERROR: " + e.getMessage());
		}

	}

	private Instance parseInstance(VirtualNetworkSite network) {

		List<Resource> resources = new ArrayList<Resource>();
		resources.add(ResourceRepository.getInstance().get(OrderConstants.NETWORK_TERM));

		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(OCCIConstants.ID, network.getName());
		attributes.put(OCCIConstants.TITLE, network.getName());
		attributes.put(OCCIConstants.NETWORK_LABEL, network.getLabel());
		attributes.put(OCCIConstants.NETWORK_VLAN, "");
		attributes.put(OCCIConstants.NETWORK_STATE, network.getState());
		attributes.put(OCCIConstants.NETWORK_ADDRESS, network.getAddressSpace().getAddressPrefixes().get(0));
		if (network.getGateway() != null) {
			attributes.put(OCCIConstants.NETWORK_GATEWAY,
					network.getGateway().getVPNClientAddressPool().getAddressPrefixes().get(0));
		}
		attributes.put(OCCIConstants.NETWORK_ALLOCATION, OCCIConstants.NetworkAllocation.DYNAMIC.getValue());

		return new Instance(network.getName(), resources, attributes, new ArrayList<Link>(), null);
	}

	public VirtualNetworkSite createVirtualNetworkSite(String gateway, String netmask, String networkName,
			String subNetworkName) {

		VirtualNetworkSite virtualNetworkSite = new VirtualNetworkSite();

		AddressSpace addressSpace = new AddressSpace();
		ArrayList<String> addresses = new ArrayList<String>();
		addresses.add(netmask);
		addressSpace.setAddressPrefixes(addresses);

		virtualNetworkSite.setAddressSpace(addressSpace);
		virtualNetworkSite.setLabel(DEFAULT_SECURITY_GROUP_LABEL);
		virtualNetworkSite.setName(networkName);
		virtualNetworkSite.setLocation(region);

		Subnet subnet = new Subnet();
		subnet.setName(subNetworkName);
		subnet.setAddressPrefix(netmask);
		ArrayList<Subnet> subnets = new ArrayList<Subnet>();
		subnets.add(subnet);
		virtualNetworkSite.setSubnets(subnets);

		return virtualNetworkSite;

	}

	protected String getPassword() {
		return UUID.randomUUID().toString();
	}

	public List<Instance> getInstances(Token token) {

		return null;
	}

	protected Configuration createConfiguration(Token token) {
		try {
			return ManagementConfiguration.configure(new URI(AzureConfigurationConstants.AZURE_BASE_URL),
					token.get(AzureAttributes.SUBSCRIPTION_ID_KEY), token.get(AzureAttributes.KEYSTORE_PATH_KEY),
					token.get(AzureAttributes.KEYSTORE_PASSWORD_KEY), KeyStoreType.jks);
		} catch (Exception e) {
			throw new OCCIException(ErrorType.BAD_REQUEST, "Can't create azure configuration");
		}
	}

	protected NetworkManagementClient createNetworkManagementClient(Token token) {
		Configuration config = createConfiguration(token);
		NetworkManagementClient networkManagementClient = NetworkManagementService.create(config);
		return networkManagementClient;
	}

}
