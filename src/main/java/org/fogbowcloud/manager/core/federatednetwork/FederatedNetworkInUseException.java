package org.fogbowcloud.manager.core.federatednetwork;

public class FederatedNetworkInUseException extends Exception {

    public FederatedNetworkInUseException(String fedNetwork, String orderId) {
        super("The federated network with id [" + fedNetwork + "] is in use by order [" + orderId + "].");
    }
}
