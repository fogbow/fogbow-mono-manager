package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.core.model.ManagerItem;
import org.fogbowcloud.manager.xmpp.model.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestManagerFacade {

	ManagerFacade managerFacade;
	ManagerTestHelper managerTestHelper;
	
	@Before
	public void setUp() throws Exception {
		managerFacade = new ManagerFacade(new Properties());
		managerTestHelper = new ManagerTestHelper();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorException() throws Exception {
		new ManagerFacade(null);
	}

	@Test
	public void testGet0ItemsFromIQ() {
		managerFacade.updateMembers(new LinkedList<ManagerItem>());
		Assert.assertEquals(0, managerFacade.getMembers().size());
	}

	@Test
	public void testGet1ItemFromIQ() {
		ManagerItem managerItem = new ManagerItem(managerTestHelper.getResources());
		List<ManagerItem> items = new LinkedList<ManagerItem>();
		items.add(managerItem);
		managerFacade.updateMembers(items);
		
		List<ManagerItem> members = managerFacade.getMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(1, managerFacade.getMembers().size());
	}

	@Test
	public void testGetManyItemsFromIQ() {
		ArrayList<ManagerItem> items = new ArrayList<ManagerItem>();
		for (int i = 0; i < 10; i++) {
			items.add(new ManagerItem(managerTestHelper.getResources()));
		}
		managerFacade.updateMembers(items);
		
		List<ManagerItem> members = managerFacade.getMembers();
		Assert.assertEquals(10, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo()
					.getId());
		}
		Assert.assertEquals(10, managerFacade.getMembers().size());
	}
}
