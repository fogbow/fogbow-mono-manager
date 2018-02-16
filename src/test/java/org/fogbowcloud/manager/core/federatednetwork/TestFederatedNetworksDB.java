package org.fogbowcloud.manager.core.federatednetwork;

import org.fogbowcloud.manager.core.federatednetwork.FederatedNetwork;
import org.fogbowcloud.manager.core.federatednetwork.FederatedNetworksDB;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        Set<FederationMember> members = new HashSet<FederationMember>();
        members.add(new FederationMember("memberA"));

        boolean added = federatedNetworksDB.addFederatedNetwork(new FederatedNetwork(id, cidrNotation, label, members), user);
        assertTrue(added);

        assertEquals(1, federatedNetworksDB.getAllFederatedNetworks().size());

        Collection<FederatedNetwork> userNetworks = federatedNetworksDB.getUserNetworks(user);
        assertEquals(1, userNetworks.size());

        FederatedNetwork retrievedNetwork = userNetworks.iterator().next();

        assertEquals(id, retrievedNetwork.getId());
        assertEquals(cidrNotation, retrievedNetwork.getCidr());
        assertEquals(label, retrievedNetwork.getLabel());
        assertEquals(members.size(), retrievedNetwork.getAllowedMembers().size());
    }
    
    @Test
    public void testUpdateFederatedNetworks() throws SubnetAddressesCapacityReachedException {
        FederatedNetworksDB federatedNetworksDB = new FederatedNetworksDB(DATABASE_FILE_PATH);

        Token.User user = new Token.User("userA", "A");

        String id = "network-1";
        String cidrNotation = "10.0.0.0/24";
        String label = "fakeLabel";
        Set<FederationMember> members = new HashSet<FederationMember>();
        members.add(new FederationMember("memberA"));
        
        FederatedNetwork federatedNetwork = new FederatedNetwork(id, cidrNotation, label, members);

        boolean added = federatedNetworksDB.addFederatedNetwork(federatedNetwork, user);
        assertTrue(added);

        assertEquals(1, federatedNetworksDB.getAllFederatedNetworks().size());

        Collection<FederatedNetwork> userNetworks = federatedNetworksDB.getUserNetworks(user);
        assertEquals(1, userNetworks.size());

        FederatedNetwork retrievedNetwork = userNetworks.iterator().next();

        assertEquals(id, retrievedNetwork.getId());
        assertEquals(cidrNotation, retrievedNetwork.getCidr());
        assertEquals(label, retrievedNetwork.getLabel());
        assertEquals(members.size(), retrievedNetwork.getAllowedMembers().size());
        
        federatedNetwork.addFederationNetworkMember(new FederationMember("memberB"));
        String firstIp = federatedNetwork.nextFreeIp("fake-orderId");
        added = federatedNetworksDB.addFederatedNetwork(federatedNetwork, user);
        assertTrue(added);
        
        assertEquals(1, federatedNetworksDB.getAllFederatedNetworks().size());
        userNetworks = federatedNetworksDB.getUserNetworks(user);
        assertEquals(1, userNetworks.size());
        
        retrievedNetwork = userNetworks.iterator().next();
        assertEquals(2, retrievedNetwork.getAllowedMembers().size());
        String actual = retrievedNetwork.nextFreeIp("fake-orderId1");
        Assert.assertNotEquals(firstIp, actual);
    }
    
    @Test
    public void testParseFederatedNetworks() {
    	FederatedNetworksDB federatedNetworksDB = new FederatedNetworksDB(DATABASE_FILE_PATH);
    	String jsonArray = new String("[{\"id\":\"4ba56476-cf53-4b9b-90ed-468e9d216940\",\"cidrNotation\":\"10.0.0.0/24\",\"label\":\"testetst\",\"allowedMembers\":[{\"lastTime\":1518809284222,\"id\":\"manager.atmosphere.secure.lsd.ufcg.edu.br\"},{\"lastTime\":1518809284222,\"id\":\"manager.atmosphere.lsd.ufcg.edu.br\"}],\"ipsServed\":1,\"freedIps\":[],\"orderIpMap\":{}}]");
    	Set<FederatedNetwork> fn = federatedNetworksDB.parseFederatedNetworks(jsonArray);
    	Assert.assertEquals(1, fn.size());
    }

}
