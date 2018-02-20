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

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.fail;

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
		assertEquals(1, controller.getUserNetworks(user).size());
		int allowedMembersSize = getAllowedMembersInFederatedNetwork(controller, user,federatedNetworkId);
		assertEquals(0, allowedMembersSize);

		Set<FederationMember> federationMembers = new HashSet<>();
		federationMembers.add(new FederationMember("fake-member01"));
		federationMembers.add(new FederationMember("fake-member02"));
		controller.updateFederatedNetworkMembers(user, federatedNetworkId, federationMembers);
		allowedMembersSize = getAllowedMembersInFederatedNetwork(controller, user,federatedNetworkId);
		assertEquals(2, allowedMembersSize);

		FederationMember thirdMember = new FederationMember("fake-member03");
		federationMembers.add(thirdMember);
		controller.updateFederatedNetworkMembers(user, federatedNetworkId, federationMembers);
		allowedMembersSize = getAllowedMembersInFederatedNetwork(controller, user,federatedNetworkId);
		assertEquals(3, allowedMembersSize);

		federationMembers.remove(thirdMember);
		controller.updateFederatedNetworkMembers(user, federatedNetworkId, federationMembers);
		allowedMembersSize = getAllowedMembersInFederatedNetwork(controller, user,federatedNetworkId);
		assertEquals(2, allowedMembersSize);

		federationMembers.add(thirdMember);
		controller.updateFederatedNetworkMembers(user, federatedNetworkId, federationMembers);

		try{
			controller.updateFederatedNetworkMembers(user, federatedNetworkId, null);
			fail();
		} catch (Exception e){
			assertEquals(IllegalArgumentException.class.getName(), e.getClass().getName());
			assertEquals("Invalid member to federated network.", e.getMessage());
		}

		String ipOne = controller.getPrivateIpFromFederatedNetwork(user, federatedNetworkId,
				"fake-orderId");
		String ipTwo = controller.getPrivateIpFromFederatedNetwork(user, federatedNetworkId,
				"fake-orderId2");

		Assert.assertNotEquals(ipOne, ipTwo);

		String ipThree = controller.getPrivateIpFromFederatedNetwork(user, federatedNetworkId,
				"fake-orderId3");
		Assert.assertNotEquals(ipOne, ipThree);
	}
	
	@Test
	public void testIsMemberAllowedInFederatedNetwork() {
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
		assertEquals(1, controller.getUserNetworks(user).size());
		
		Assert.assertFalse(controller.isMemberAllowedInFederatedNetwork(user, federatedNetworkId, new FederationMember("fake-member")));

		Set<FederationMember> newMembers = new HashSet<>();
		newMembers.add(new FederationMember("fake-member01"));
		newMembers.add(new FederationMember("fake-member02"));
		controller.updateFederatedNetworkMembers(user, federatedNetworkId, newMembers);

		FederationMember thirdMember = new FederationMember("fake-member03");
		
		Assert.assertTrue(controller.isMemberAllowedInFederatedNetwork(user, federatedNetworkId, new FederationMember("fake-member01")));
		Assert.assertTrue(controller.isMemberAllowedInFederatedNetwork(user, federatedNetworkId, new FederationMember("fake-member02")));
		Assert.assertFalse(controller.isMemberAllowedInFederatedNetwork(user, federatedNetworkId, thirdMember));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testIsMemberAllowedInFederatedNetworkNullFN() {
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
		assertEquals(1, controller.getUserNetworks(user).size());
		
		controller.isMemberAllowedInFederatedNetwork(user, federatedNetworkId + "error", new FederationMember("fake-member"));
	}

	private int getAllowedMembersInFederatedNetwork(FederatedNetworksController controller, Token.User user, String federatedNetworkId){
		FederatedNetwork fn = controller.getFederatedNetwork(user, federatedNetworkId);
		return fn.getAllowedMembers().size();
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
		assertEquals(1, controller.getUserNetworks(user).size());

		controller.deleteFederatedNetwork(user, federatedNetworkId);
		assertEquals(0, controller.getUserNetworks(user).size());
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
		assertEquals(1, controller.getUserNetworks(user).size());

		controller.deleteFederatedNetwork(user, federatedNetworkId);
		assertEquals(1, controller.getUserNetworks(user).size());

		Mockito.doReturn(true).when(controller).removeFederatedNetworkAgent(Mockito.anyString());
		controller.deleteFederatedNetwork(user, federatedNetworkId);
		assertEquals(0, controller.getUserNetworks(user).size());
	}

}
