package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.federatednetwork.FederatedNetwork;
import org.fogbowcloud.manager.core.federatednetwork.FederatedNetworksDB;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by arnett on 08/02/18.
 */
public class TestFederatedNetworksDB {

    public static final String DATABASE_FILE_PATH = "test-federated-networks-temp.db";

    @After
    public void cleanUp() {
        new File(DATABASE_FILE_PATH).delete();
    }

    @Test
    public void testCrudFederatedNetworks() {
        FederatedNetworksDB federatedNetworksDB = new FederatedNetworksDB(DATABASE_FILE_PATH);

        Token.User user = new Token.User("userA", "A");

        String id = "network-1";
        String cidrNotation = "10.0.0.0/24";
        String label = "fakeLabel";
        Collection<FederationMember> members = Arrays.asList(new FederationMember("memberA"));

        boolean added = federatedNetworksDB.addFederatedNetwork(new FederatedNetwork(id, cidrNotation, label, members), user);
        assertTrue(added);

        assertEquals(1, federatedNetworksDB.getAllFederatedNetworks().size());

        Collection<FederatedNetwork> userNetworks = federatedNetworksDB.getUserNetworks(user);
        assertEquals(1, userNetworks.size());

        FederatedNetwork retrievedNetwork = userNetworks.iterator().next();

        assertEquals(id, retrievedNetwork.getId());
        assertEquals(cidrNotation, retrievedNetwork.getCidr());
        assertEquals(label, retrievedNetwork.getLabel());
        assertEquals(members, retrievedNetwork.getAllowedMembers());
    }

}
