package org.fogbowcloud.manager.core.federatednetwork;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;

import java.util.*;

/**
 * Created by arnett on 05/02/18.
 */
public class FederatedNetworksController {

    Map<Token.User, Collection<FederatedNetwork>> federatedNetworks;

    public FederatedNetworksController() {
        federatedNetworks = new HashMap<>();
    }

    public boolean create(Token.User user, String label, String cidrNotation, Set<FederationMember> members) {
        FederatedNetwork federatedNetwork = new FederatedNetwork(label, cidrNotation, members);
        if (federatedNetworks.containsKey(user)) {
            federatedNetworks.get(user).add(federatedNetwork);
        } else {
            Collection<FederatedNetwork> networks = new HashSet<>(Arrays.asList(federatedNetwork));
            federatedNetworks.put(user, networks);
        }

        return true;
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
