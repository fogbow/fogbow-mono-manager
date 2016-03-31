package org.fogbowcloud.manager.core.plugins.memberpicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.AccountingInfo;
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
		Mockito.when(facade.getRendezvousMembers()).thenReturn(new ArrayList<FederationMember>());
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(
				new ArrayList<AccountingInfo>());
		
		NoFMemberPickerPlugin nofPicker = new NoFMemberPickerPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPicker.pick(facade.getRendezvousMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}

	@Test
	public void testOnlyLocalMember() {
		// mocking
		FederationMember localMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", "", ""));
		ArrayList<FederationMember> membersToReturn = new ArrayList<FederationMember>();
		membersToReturn.add(localMember);
		
		Mockito.when(facade.getRendezvousMembers()).thenReturn(membersToReturn);
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(
				new ArrayList<AccountingInfo>());
		
		NoFMemberPickerPlugin nofPicker = new NoFMemberPickerPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPicker.pick(facade.getRendezvousMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}
	
	@Test
	public void testOneRemoteMember() {
		// mocking facade
		FederationMember localMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", "", ""));
		FederationMember remoteMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL, "", "", "", "", "", ""));
		ArrayList<FederationMember> membersToReturn = new ArrayList<FederationMember>();
		membersToReturn.add(localMember);
		membersToReturn.add(remoteMember);
		Mockito.when(facade.getRendezvousMembers()).thenReturn(membersToReturn);
		
		// mocking accounting		
		List<AccountingInfo> accounting = new ArrayList<AccountingInfo>();
		AccountingInfo accountingEntry = new AccountingInfo("user",
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL);
		accountingEntry.addConsumption(4);
		accounting.add(accountingEntry);
		
		accountingEntry = new AccountingInfo("user",
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		accountingEntry.addConsumption(16);
		accounting.add(accountingEntry);
		
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(
				accounting);
		
		NoFMemberPickerPlugin nofPicker = new NoFMemberPickerPlugin(properties, accountingPlugin);
		Assert.assertEquals(remoteMember, nofPicker.pick(facade.getRendezvousMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}
	
	@Test
	public void testTwoRemoteMembers() {
		// mocking facade
		FederationMember localMember = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", "", ""));
		FederationMember remoteMember1 = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "1", "", "", "", "", "", ""));
		FederationMember remoteMember2 = new FederationMember(new ResourcesInfo(
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "2", "", "", "", "", "", ""));
		ArrayList<FederationMember> membersToReturn = new ArrayList<FederationMember>();
		membersToReturn.add(localMember);
		membersToReturn.add(remoteMember1);
		membersToReturn.add(remoteMember2);
		Mockito.when(facade.getRendezvousMembers()).thenReturn(membersToReturn);
		
		// mocking accounting		
		List<AccountingInfo> accounting = new ArrayList<AccountingInfo>();
		AccountingInfo accountingEntry = new AccountingInfo("user",
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "1");
		accountingEntry.addConsumption(4);
		accounting.add(accountingEntry);
		
		accountingEntry = new AccountingInfo("user",
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "1",
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		accountingEntry.addConsumption(16);
		accounting.add(accountingEntry);
		
		accountingEntry = new AccountingInfo("user",
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL,
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "2");
		accountingEntry.addConsumption(10);
		accounting.add(accountingEntry);
		
		accountingEntry = new AccountingInfo("user",
				DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL + "2",
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		accountingEntry.addConsumption(16);
		accounting.add(accountingEntry);
		
		Mockito.when(accountingPlugin.getAccountingInfo()).thenReturn(
				accounting);
		
		NoFMemberPickerPlugin nofPicker = new NoFMemberPickerPlugin(properties, accountingPlugin);
		Assert.assertEquals(remoteMember1, nofPicker.pick(facade.getRendezvousMembers()));
		Assert.assertFalse(nofPicker.getTrustworthy());
	}
}
