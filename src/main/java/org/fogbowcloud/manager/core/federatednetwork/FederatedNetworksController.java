package org.fogbowcloud.manager.core.federatednetwork;

import org.apache.commons.net.util.SubnetUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;

import java.util.*;

/**
 * Created by arnett on 05/02/18.
 */
public class FederatedNetworksController {

    private static final String DATABASE_FILE_PATH = "federated-networks.db";

    FederatedNetworksDB database;

    Properties properties;

    public FederatedNetworksController() {
        this(new Properties());
    }

    public FederatedNetworksController(Properties properties) {
        this.properties = properties;
        database = new FederatedNetworksDB(DATABASE_FILE_PATH);
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
            return database.addFederatedNetwork(federatedNetwork, user);
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

    public boolean delete(String id) {
        return database.delete(id);
    }

    public Collection<FederatedNetwork> getUserNetworks(Token.User user) {
        return database.getUserNetworks(user);
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
        return database.getAllFederatedNetworks();
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
