package org.fogbowcloud.manager.core.federatednetwork;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;

import java.util.*;

/**
 * Created by arnett on 05/02/18.
 */
public class FederatedNetworksController {

    Map<Token.User, Collection<FederatedNetwork>> federatedNetworks;
    Properties properties;

    public FederatedNetworksController(Properties properties) {
        this.properties = properties;
        federatedNetworks = new HashMap<>();
    }

    public boolean create(Token.User user, String label, String cidrNotation, Set<FederationMember> members) {
        //TODO:replace cidrNotation and virtualIpAddress
        boolean createdSuccessfully = callFederatedNetworkAgent("192.168.2.0/24", "192.168.2.1");
        if (createdSuccessfully) {
            FederatedNetwork federatedNetwork = new FederatedNetwork(label, cidrNotation, members);
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

    private boolean callFederatedNetworkAgent(String cidrNotation, String virtualIpAddress) {
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
}
