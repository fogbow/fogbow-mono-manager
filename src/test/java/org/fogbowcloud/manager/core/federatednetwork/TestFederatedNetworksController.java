package org.fogbowcloud.manager.core.federatednetwork;

import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFederatedNetworksController {

	private String databaseFilePath = "test.db";

	@Ignore
	@After
	public void clean() {
		File here = new File("");
		File DBfile = new File(here.getAbsolutePath() + "/" + this.databaseFilePath);
		DBfile.delete();
	}

	@Ignore
	@Test
	public void testUpdateFederatedNetworkMembers() {
		Properties properties = null;
		FederatedNetworksController controller = Mockito
				.spy(new FederatedNetworksController(properties, this.databaseFilePath));
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
	}

}
