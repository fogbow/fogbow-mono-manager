package org.fogbowcloud.manager.xmpp;

import java.util.ArrayList;
import java.util.List;

import org.fogbowcloud.manager.xmpp.core.ManagerItem;
import org.fogbowcloud.manager.xmpp.core.ManagerModel;
import org.fogbowcloud.manager.xmpp.core.ResourcesInfo;
import org.fogbowcloud.manager.xmpp.model.ManagerTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestManagerModel {

	private ManagerModel managerModel;
	private ResourcesInfo resources;

	@Before
	public void setUp() {
		resources = ManagerTestHelper.getResources();
		managerModel = new ManagerModel();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUpdateWithException() {
		managerModel.update(null);
	}

	@Test
	public void testUpdateEmptyList() {
		List<ManagerItem> resources = new ArrayList<ManagerItem>();
		managerModel.update(resources);
		Assert.assertEquals(resources, managerModel.getMembers());
	}

	@Test
	public void testUpdate1Member() {
		List<ManagerItem> members = new ArrayList<ManagerItem>();
		members.add(new ManagerItem(resources));
		managerModel.update(members);
		Assert.assertEquals(1, members.size());
		Assert.assertEquals(members, managerModel.getMembers());
	}

	@Test
	public void testUpdateManyMembers() {
		List<ManagerItem> members = new ArrayList<ManagerItem>();
		for (int i = 0; i < 10; i++) {
			resources.setId("" + i);
			members.add(new ManagerItem(resources));
		}
		managerModel.update(members);
		Assert.assertEquals(10, members.size());
		Assert.assertEquals(members, managerModel.getMembers());
	}
}
