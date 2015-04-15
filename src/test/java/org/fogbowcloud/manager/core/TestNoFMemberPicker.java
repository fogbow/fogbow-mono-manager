package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNoFMemberPicker {

	private AccountingPlugin accountingPlugin;
	private Properties properties;
	private ManagerController facade;

	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put("nof_trustworthy", "false");
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);
		
		facade = Mockito.mock(ManagerController.class);		
	}
	
	@Test
	public void testEmptyMembers() {
		// mocking
		Mockito.when(facade.getMembers()).thenReturn(new ArrayList<FederationMember>());
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		NoFMemberPicker nofPicker = new NoFMemberPicker(properties, accountingPlugin);
		Assert.assertNull(nofPicker.pick(facade.getMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}

	@Test
	public void testOnlyLocalMember() {
		// mocking
		FederationMember localMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", null));
		ArrayList<FederationMember> membersToReturn = new ArrayList<FederationMember>();
		membersToReturn.add(localMember);
		
		Mockito.when(facade.getMembers()).thenReturn(membersToReturn);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		NoFMemberPicker nofPicker = new NoFMemberPicker(properties, accountingPlugin);
		Assert.assertNull(nofPicker.pick(facade.getMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}
	
	@Test
	public void testOneRemoteMember() {
		// mocking facade
		FederationMember localMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", null));
		FederationMember remoteMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL, "", "", "", "", null));
		ArrayList<FederationMember> membersToReturn = new ArrayList<FederationMember>();
		membersToReturn.add(localMember);
		membersToReturn.add(remoteMember);
		Mockito.when(facade.getMembers()).thenReturn(membersToReturn);
		
		// mocking accounting		
		HashMap<String, ResourceUsage> membersUsageToReturn = new HashMap<String, ResourceUsage>();
		ResourceUsage resUsage = new ResourceUsage(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		resUsage.addConsumption(4);
		resUsage.addDonation(16);
		membersUsageToReturn.put(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL, resUsage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsageToReturn);
		
		NoFMemberPicker nofPicker = new NoFMemberPicker(properties, accountingPlugin);
		Assert.assertEquals(remoteMember, nofPicker.pick(facade.getMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}
	
	@Test
	public void testTwoRemoteMembers() {
		// mocking facade
		FederationMember localMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", null));
		FederationMember remoteMember1 = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "1", "", "", "", "", null));
		FederationMember remoteMember2 = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "2", "", "", "", "", null));
		ArrayList<FederationMember> membersToReturn = new ArrayList<FederationMember>();
		membersToReturn.add(localMember);
		membersToReturn.add(remoteMember1);
		membersToReturn.add(remoteMember2);
		Mockito.when(facade.getMembers()).thenReturn(membersToReturn);
		
		// mocking accounting		
		HashMap<String, ResourceUsage> membersUsageToReturn = new HashMap<String, ResourceUsage>();
		ResourceUsage resUsage1 = new ResourceUsage(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "1");
		resUsage1.addConsumption(4);
		resUsage1.addDonation(16);
		membersUsageToReturn.put(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "1", resUsage1);		
		ResourceUsage resUsage2 = new ResourceUsage(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "2");
		resUsage2.addConsumption(10);
		resUsage2.addDonation(16);
		membersUsageToReturn.put(DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "2", resUsage2);		
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsageToReturn);
		
		NoFMemberPicker nofPicker = new NoFMemberPicker(properties, accountingPlugin);
		Assert.assertEquals(remoteMember1, nofPicker.pick(facade.getMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}
}
