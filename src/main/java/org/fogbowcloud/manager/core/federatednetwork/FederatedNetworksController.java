package org.fogbowcloud.manager.core.federatednetwork;

import org.apache.commons.net.util.SubnetUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;

import java.util.*;

public class FederatedNetworksController {

	public static final String FEDERATED_NETWORK_AGANTE_PUBLIC_IP_PROP = "federated_network_agante_public_ip";
    private Map<Token.User, Collection<FederatedNetwork>> federatedNetworks;
    private Properties properties;

    public FederatedNetworksController() {
        properties = new Properties();
        federatedNetworks = new HashMap<>();
    }

    public FederatedNetworksController(Properties properties) {
        this.properties = properties;
        federatedNetworks = new HashMap<>();
    }

    public boolean create(Token.User user, String label, String cidrNotation, Set<FederationMember> members) {
        SubnetUtils.SubnetInfo subnetInfo = getSubnetInfo(cidrNotation);

        if (!isValid(subnetInfo)) {
            return false;
        }

        // TODO: replace cidrNotation and virtualIpAddress
        cidrNotation = "192.168.2.0/24";
        boolean createdSuccessfully = callFederatedNetworkAgent(cidrNotation, subnetInfo.getLowAddress());
        if (createdSuccessfully) {
            String federatedNetworkId = String.valueOf(UUID.randomUUID());
            FederatedNetwork federatedNetwork = new FederatedNetwork(federatedNetworkId, label, cidrNotation, members);
            if (federatedNetworks.containsKey(user)) {
                federatedNetworks.get(user).add(federatedNetwork);
            } else {
                Collection<FederatedNetwork> networks = new HashSet<>(Arrays.asList(federatedNetwork));
                federatedNetworks.put(user, networks);
            }
            return true;
        }

        return false;
    }

    public boolean callFederatedNetworkAgent(String cidrNotation, String virtualIpAddress) {
        String permissionFilePath = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_PERMISSION_FILE_PATH);
        String user = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_USER);
        String serverAddress = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_ADDRESS);
        String serverPrivateAddress = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_PRIVATE_ADDRESS);

        String format = "ssh -i %s %s@%s -c sudo /home/ubuntu/config-ipsec %s %s %s %s";
        String command = String.format(format, permissionFilePath, user, serverAddress, serverPrivateAddress, serverAddress,
                cidrNotation, virtualIpAddress);

        ProcessBuilder builder = new ProcessBuilder(command);
        try {
            Process process = builder.start();
            int resultCode = process.waitFor();
            if (resultCode == 0) return true;
        } catch (Exception e) {
            //TODO: info logs
        }
        return false;
    }

    public Properties getProperties() {
        return properties;
    }

    public boolean remove(String label) {
        for (Collection<FederatedNetwork> networks : federatedNetworks.values()) {
            FederatedNetwork toBeRemoved = null;
            for (FederatedNetwork network : networks) {
                if (network.getLabel().equals(label)) {
                    toBeRemoved = network;
                    break;
                }
            }

            if (toBeRemoved != null) {
                networks.remove(toBeRemoved);
                return true;
            }
        }

        return false;
    }

    public Collection<FederatedNetwork> getUserNetworks(Token.User user) {
        if (user != null && federatedNetworks.containsKey(user)) {
            return federatedNetworks.get(user);
        }

        return null;
    }

    private static SubnetUtils.SubnetInfo getSubnetInfo(String cidrNotation) {
        return new SubnetUtils(cidrNotation).getInfo();
    }

    private static boolean isValid(SubnetUtils.SubnetInfo subnetInfo) {
        int lowAddress = subnetInfo.asInteger(subnetInfo.getLowAddress());
        int highAddress = subnetInfo.asInteger(subnetInfo.getHighAddress());
        return highAddress - lowAddress > 1;
    }

    public Collection<FederatedNetwork> getAllFederatedNetworks() {
        Collection<FederatedNetwork> allFederatedNetworks = new ArrayList<FederatedNetwork>();
        for (Collection<FederatedNetwork> networks : federatedNetworks.values()) {
            allFederatedNetworks.addAll(networks);
        }
        return allFederatedNetworks;
    }

    public FederatedNetwork getFederatedNetwork(String federatedNetworkId) {
        Collection<FederatedNetwork> allFederatedNetworks = this.getAllFederatedNetworks();
        FederatedNetwork federatedNetwork = null;
        for (FederatedNetwork federatedNetworkIterator : allFederatedNetworks) {
            if (federatedNetworkIterator.getId().equals(federatedNetworkId)) {
                federatedNetwork = federatedNetworkIterator;
            }
        }
        return federatedNetwork;
    }

}
