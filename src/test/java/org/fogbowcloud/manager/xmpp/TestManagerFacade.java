package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.xmpp.core.ManagerFacade;
import org.fogbowcloud.manager.xmpp.core.ManagerItem;
import org.fogbowcloud.manager.xmpp.core.ManagerModel;
import org.fogbowcloud.manager.xmpp.model.ManagerTestHelper;
import org.fogbowcloud.manager.xmpp.model.RendezvousItemCopy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.packet.IQ;

public class TestManagerFacade {

	ManagerModel managerModel;
	ManagerFacade managerFacade;

	@Before
	public void setUp() {
		managerModel = new ManagerModel();
		managerFacade = new ManagerFacade(managerModel);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorException() {
		new ManagerFacade(null);
	}

	@Test
	public void testConstructor() {
		ManagerFacade managerFacade2 = new ManagerFacade(managerModel);
		Assert.assertEquals(managerModel, managerFacade2.getManagerModel());
	}

	@Test
	public void testGet0ItemsFromIQ() {
		IQ iq = ManagerTestHelper
				.createResponse(new ArrayList<RendezvousItemCopy>());
		List<ManagerItem> members = managerFacade.getItemsFromIQ(iq);
		Assert.assertEquals(0, members.size());
		Assert.assertEquals(0, managerFacade.getManagerModel().getMembers()
				.size());
	}

	@Test
	public void testGet1ItemFromIQ() {
		List<RendezvousItemCopy> items = new ArrayList<RendezvousItemCopy>();
		items.add(new RendezvousItemCopy(ManagerTestHelper.getResources()));
		IQ iq = ManagerTestHelper.createResponse(items);
		List<ManagerItem> members = managerFacade.getItemsFromIQ(iq);
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(1, managerFacade.getManagerModel().getMembers()
				.size());
	}

	@Test
	public void testGetManyItemsFromIQ() {
		List<RendezvousItemCopy> items = new ArrayList<RendezvousItemCopy>();
		for (int i = 0; i < 10; i++) {
			items.add(new RendezvousItemCopy(ManagerTestHelper.getResources()));
		}
		IQ iq = ManagerTestHelper.createResponse(items);
		List<ManagerItem> members = managerFacade.getItemsFromIQ(iq);
		Assert.assertEquals(10, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo()
					.getId());
		}
		Assert.assertEquals(10, managerFacade.getManagerModel().getMembers()
				.size());
	}
}
