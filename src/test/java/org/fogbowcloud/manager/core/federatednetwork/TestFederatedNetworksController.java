package org.fogbowcloud.manager.core.federatednetwork;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by arnett on 06/02/18.
 */
public class TestFederatedNetworksController {

    @Test
    public void testCrudOperationsForFederatedNetworks() {
        FederatedNetworksController controller = new FederatedNetworksController();

        Token.User user = new Token.User("hardCodedId", "hardCodedName");
        String label = "hardCodedLabel";
        String cidrNotation = "10.0.0.0/24";
        HashSet<FederationMember> members = new HashSet<>();

        assertTrue(controller.create(user, label, cidrNotation, members));
        assertEquals(1, controller.getUserNetworks(user).size());

        assertTrue(controller.remove(label));
        assertEquals(0, controller.getUserNetworks(user).size());
    }

}
