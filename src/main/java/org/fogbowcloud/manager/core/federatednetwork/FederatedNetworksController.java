package org.fogbowcloud.manager.core.federatednetwork;

import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.federatednetwork.FederatedNetworkConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.model.Token.User;

public class FederatedNetworksController {

    public static final String FEDERATED_NETWORK_AGENT_PUBLIC_IP_PROP = "federated_network_agent_public_ip";
    private static final String DATABASE_FILE_PATH = "federated-networks.db";

    FederatedNetworksDB database;

    private Properties properties;
    
    private static final Logger LOGGER = Logger.getLogger(FederatedNetworksController.class);

    public FederatedNetworksController() {
        this(new Properties());
    }

    public FederatedNetworksController(Properties properties) {
        this.properties = properties;
        database = new FederatedNetworksDB(DATABASE_FILE_PATH);
    }
    
    protected FederatedNetworksController(Properties properties, String databaseFilePath) {
        this.properties = properties;
        database = new FederatedNetworksDB(databaseFilePath);
    }

    public String create(Token.User user, String label, String cidrNotation, Set<FederationMember> members) {
        SubnetUtils.SubnetInfo subnetInfo = getSubnetInfo(cidrNotation);

        if (!isValid(subnetInfo)) {
        	LOGGER.error("Subnet (" + cidrNotation + ") invalid");
            return null;
        }

        boolean createdSuccessfully = callFederatedNetworkAgent(cidrNotation, subnetInfo.getLowAddress());
        if (createdSuccessfully) {
            String federatedNetworkId = String.valueOf(UUID.randomUUID());
            FederatedNetwork federatedNetwork = new FederatedNetwork(federatedNetworkId, cidrNotation, label, members);
            if (database.addFederatedNetwork(federatedNetwork, user)) {
                return federatedNetworkId;
            } else {
                return null;
            }
        }

        return null;
    }

    public boolean callFederatedNetworkAgent(String cidrNotation, String virtualIpAddress) {
        String permissionFilePath = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_PERMISSION_FILE_PATH);
        String user = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_USER);
        String serverAddress = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_ADDRESS);
        String serverPrivateAddress = getProperties().getProperty(ConfigurationConstants.FEDERATED_NETWORK_AGENT_PRIVATE_ADDRESS);

        ProcessBuilder builder = new ProcessBuilder("ssh", "-o", "UserKnownHostsFile=/dev/null", "-o", "StrictHostKeyChecking=no", "-i", permissionFilePath, user + "@" + serverAddress,
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
    
    public FederatedNetwork getFederatedNetwork(Token.User user, String federatedNetworkId) {
        Collection<FederatedNetwork> allFederatedNetworks = this.getUserNetworks(user);
        for (FederatedNetwork federatedNetwork : allFederatedNetworks) {
            if (federatedNetwork.getId().equals(federatedNetworkId)) {
                return federatedNetwork;
            }
        }

        return null;
    }

	public void updateFederatedNetworkMembers(User user, String federatedNetworkId,
			Set<String> membersSet) {
		FederatedNetwork federatedNetwork = this.getFederatedNetwork(user, federatedNetworkId);
		if (federatedNetwork == null) {
			throw new IllegalArgumentException(
					FederatedNetworkConstants.NOT_FOUND_FEDERATED_NETWORK_MESSAGE
							+ federatedNetworkId);
		}
		for (String member : membersSet) {
			federatedNetwork.addFederationNetworkMember(new FederationMember(member));
		}

		if (!this.database.addFederatedNetwork(federatedNetwork, user)) {
			throw new IllegalArgumentException(
					FederatedNetworkConstants.CANNOT_UPDATE_FEDERATED_NETWORK_IN_DATABASE
							+ federatedNetworkId);
		}
	}

	public String getCIDRFromFederatedNetwork(Token.User user, String federatedNetworkId) {
		FederatedNetwork federatedNetwork = this.getFederatedNetwork(user, federatedNetworkId);
		if (federatedNetwork == null) {
			throw new IllegalArgumentException(
					FederatedNetworkConstants.NOT_FOUND_FEDERATED_NETWORK_MESSAGE
							+ federatedNetworkId);
		}
		return federatedNetwork.getCidr();
	}

	public String getAgentPublicIp() {
		String agentPublicIp = getProperties()
				.getProperty(FederatedNetworksController.FEDERATED_NETWORK_AGENT_PUBLIC_IP_PROP);
		if (agentPublicIp == null) {
			throw new IllegalArgumentException(
					FederatedNetworkConstants.NOT_FOUND_PUBLIC_AGENT_IP_MESSAGE);
		}
		return agentPublicIp;
	}
	
	public String getPrivateIpFromFederatedNetwork(Token.User user, String federatedNetworkId,
			String orderId) throws SubnetAddressesCapacityReachedException {
		FederatedNetwork federatedNetwork = this.getFederatedNetwork(user, federatedNetworkId);
		if (federatedNetwork == null) {
			throw new IllegalArgumentException(
					FederatedNetworkConstants.NOT_FOUND_FEDERATED_NETWORK_MESSAGE
							+ federatedNetworkId);
		}
		String privateIp = null;
		privateIp = federatedNetwork.nextFreeIp(orderId);
		if (!this.database.addFederatedNetwork(federatedNetwork, user)) {
			throw new IllegalArgumentException(
					FederatedNetworkConstants.CANNOT_UPDATE_FEDERATED_NETWORK_IN_DATABASE);
		}
		return privateIp;
	}

}
