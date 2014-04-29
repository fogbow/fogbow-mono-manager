package org.fogbowcloud.manager.core;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.xmpp.util.ManagerTestHelper;
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
		managerFacade.updateMembers(new LinkedList<FederationMember>());
		Assert.assertEquals(0, managerFacade.getMembers().size());
	}

	@Test
	public void testGet1ItemFromIQ() throws CertificateException, IOException {
		FederationMember managerItem = new FederationMember(managerTestHelper.getResources());
		List<FederationMember> items = new LinkedList<FederationMember>();
		items.add(managerItem);
		managerFacade.updateMembers(items);
		
		List<FederationMember> members = managerFacade.getMembers();
		Assert.assertEquals(1, members.size());
		Assert.assertEquals("abc", members.get(0).getResourcesInfo().getId());
		Assert.assertEquals(1, managerFacade.getMembers().size());
	}

	@Test
	public void testGetManyItemsFromIQ() throws CertificateException, IOException {
		ArrayList<FederationMember> items = new ArrayList<FederationMember>();
		for (int i = 0; i < 10; i++) {
			items.add(new FederationMember(managerTestHelper.getResources()));
		}
		managerFacade.updateMembers(items);
		
		List<FederationMember> members = managerFacade.getMembers();
		Assert.assertEquals(10, members.size());
		for (int i = 0; i < 10; i++) {
			Assert.assertEquals("abc", members.get(0).getResourcesInfo()
					.getId());
		}
		Assert.assertEquals(10, managerFacade.getMembers().size());
	}
}
