package org.fogbowcloud.manager.core.federatednetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;

public class FederatedNetworksController {

	public static final String FEDERATED_NETWORK_AGANTE_PUBLIC_IP_PROP = "federated_network_agent_public_ip";
    private Map<Token.User, Collection<FederatedNetwork>> federatedNetworks;
    private Properties properties;
    
    private static final Logger LOGGER = Logger.getLogger(FederatedNetworksController.class);

    public FederatedNetworksController() {
        properties = new Properties();
        federatedNetworks = new HashMap<>();
    }

    public FederatedNetworksController(Properties properties) {
        this.properties = properties;
        federatedNetworks = new HashMap<>();
    }

    public String create(Token.User user, String label, String cidrNotation, Set<FederationMember> members) {
        SubnetUtils.SubnetInfo subnetInfo = getSubnetInfo(cidrNotation);

        if (!isValid(subnetInfo)) {
        	LOGGER.error("Subnet( " + cidrNotation + " ) invalid");
            return null;
        }

        boolean createdSuccessfully = callFederatedNetworkAgent(cidrNotation, subnetInfo.getLowAddress());
        if (createdSuccessfully) {
            String federatedNetworkId = String.valueOf(UUID.randomUUID());
            FederatedNetwork federatedNetwork = new FederatedNetwork(federatedNetworkId, cidrNotation, label, members);
            if (federatedNetworks.containsKey(user)) {
                federatedNetworks.get(user).add(federatedNetwork);
            } else {
                Collection<FederatedNetwork> networks = new HashSet<>(Arrays.asList(federatedNetwork));
                federatedNetworks.put(user, networks);
            }
            return federatedNetworkId;
        }

        return null;
    }

    public boolean callFederatedNetworkAgent(String cidrNotation, String virtualIpAddress) {
        String permissionFilePath = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_PERMISSION_FILE_PATH);
        String user = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_USER);
        String serverAddress = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_ADDRESS);
        String serverPrivateAddress = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_PRIVATE_ADDRESS);

        ProcessBuilder builder = new ProcessBuilder("ssh", "-i", permissionFilePath, user + "@" + serverAddress,
        		"sudo", "/home/ubuntu/config-ipsec", serverPrivateAddress, serverAddress, cidrNotation, virtualIpAddress);        
        LOGGER.info("Trying to call agent with atts (" + cidrNotation + "): " + builder.command());
        
        int resultCode = 0;
        try {
            Process process = builder.start();            
            LOGGER.info("Trying agent with atts (" + cidrNotation + "). Output : " + ProcessUtil.getOutput(process));
            LOGGER.info("Trying agent with atts (" + cidrNotation + "). Error : " + ProcessUtil.getError(process));            
            resultCode = process.waitFor();
            if (resultCode == 0) {
            	return true;
            }
        } catch (Exception e) {
        	 LOGGER.error("", e);
        }
        LOGGER.error("Is not possible call agent. Process command: " + resultCode);
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
