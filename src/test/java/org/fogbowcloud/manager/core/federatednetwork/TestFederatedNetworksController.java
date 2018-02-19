package org.fogbowcloud.manager.core.federatednetwork;

import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFederatedNetworksController {

	public static final String DATABASE_FILE_PATH = "test-federated-networks-temp.db";

	@After
	public void clean() {
		new File(DATABASE_FILE_PATH).delete();
	}

	@Test
	public void testUpdateFederatedNetworkMembers() throws SubnetAddressesCapacityReachedException {
		Properties properties = null;
		FederatedNetworksController controller = Mockito
				.spy(new FederatedNetworksController(properties, DATABASE_FILE_PATH));
		Mockito.doReturn(true).when(controller).callFederatedNetworkAgent(Mockito.anyString(),
				Mockito.anyString());

		Token.User user = new Token.User("hardCodedId", "hardCodedName");
		String label = "hardCodedLabel";
		String cidrNotation = "10.0.0.0/24";
		HashSet<FederationMember> members = new HashSet<>();

		String federatedNetworkId = controller.create(user, label, cidrNotation, members);
		Assert.assertNotNull(federatedNetworkId);
		Assert.assertEquals(1, controller.getUserNetworks(user).size());

		Set<String> newMembers = new HashSet<String>();
		newMembers.add("fake-member01");
		newMembers.add("fake-member02");
		controller.updateFederatedNetworkMembers(user, federatedNetworkId, newMembers);

		FederatedNetwork fn = controller.getFederatedNetwork(user, federatedNetworkId);
		Assert.assertEquals(2, fn.getAllowedMembers().size());

		String ipOne = controller.getPrivateIpFromFederatedNetwork(user, federatedNetworkId,
				"fake-orderId");
		String ipTwo = controller.getPrivateIpFromFederatedNetwork(user, federatedNetworkId,
				"fake-orderId2");

		fn = controller.getFederatedNetwork(user, federatedNetworkId);
		Assert.assertNotEquals(ipOne, ipTwo);

		String ipThree = controller.getPrivateIpFromFederatedNetwork(user, federatedNetworkId,
				"fake-orderId3");
		Assert.assertNotEquals(ipOne, ipThree);
	}

	@Test
	public void testRemoveFederatedNetwork() throws SubnetAddressesCapacityReachedException {
		Properties properties = null;
		FederatedNetworksController controller = Mockito
				.spy(new FederatedNetworksController(properties, DATABASE_FILE_PATH));
		Mockito.doReturn(true).when(controller).callFederatedNetworkAgent(Mockito.anyString(),
				Mockito.anyString());
		Mockito.doReturn(true).when(controller).removeFederatedNetworkAgent(Mockito.anyString());

		Token.User user = new Token.User("hardCodedId", "hardCodedName");
		String label = "hardCodedLabel";
		String cidrNotation = "10.0.0.0/24";
		HashSet<FederationMember> members = new HashSet<>();

		String federatedNetworkId = controller.create(user, label, cidrNotation, members);
		Assert.assertNotNull(federatedNetworkId);
		Assert.assertEquals(1, controller.getUserNetworks(user).size());

		controller.deleteFederatedNetwork(user, federatedNetworkId);
		Assert.assertEquals(0, controller.getUserNetworks(user).size());
	}

	@Test
	public void testRemoveFederatedNetworkWhenGetScriptError() throws SubnetAddressesCapacityReachedException {
		Properties properties = null;
		FederatedNetworksController controller = Mockito
				.spy(new FederatedNetworksController(properties, DATABASE_FILE_PATH));
		Mockito.doReturn(true).when(controller).callFederatedNetworkAgent(Mockito.anyString(),
				Mockito.anyString());
		Mockito.doReturn(false).when(controller).removeFederatedNetworkAgent(Mockito.anyString());

		Token.User user = new Token.User("hardCodedId", "hardCodedName");
		String label = "hardCodedLabel";
		String cidrNotation = "10.0.0.0/24";
		HashSet<FederationMember> members = new HashSet<>();

		String federatedNetworkId = controller.create(user, label, cidrNotation, members);
		Assert.assertNotNull(federatedNetworkId);
		Assert.assertEquals(1, controller.getUserNetworks(user).size());

		controller.deleteFederatedNetwork(user, federatedNetworkId);
		Assert.assertEquals(1, controller.getUserNetworks(user).size());

		Mockito.doReturn(true).when(controller).removeFederatedNetworkAgent(Mockito.anyString());
		controller.deleteFederatedNetwork(user, federatedNetworkId);
		Assert.assertEquals(0, controller.getUserNetworks(user).size());
	}

}
