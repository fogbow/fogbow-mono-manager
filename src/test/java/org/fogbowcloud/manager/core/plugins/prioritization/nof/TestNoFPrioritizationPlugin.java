package org.fogbowcloud.manager.core.plugins.prioritization.nof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.ResourceUsage;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestNoFPrioritizationPlugin {

	private Properties properties;
	private AccountingPlugin accountingPlugin;

	@Before
	public void setUp(){
		properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put("nof_trustworthy", "false");
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);
	}
	
	@Test
	public void testServedRequestsNull() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom("memberId", null));
	}
	
	@Test
	public void testEmptyServedRequests() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom("memberId", new ArrayList<Request>()));
	}
	
	@Test
	public void testNoServedRequest() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);		
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Request localRequest1 = new Request("id1", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		localRequest1.setInstanceId("instanceId1");
		localRequest1.setState(RequestState.FULFILLED);
		localRequest1.setProvidingMemberId("localMemberId");
		
		Request localRequest2 = new Request("id2", new Token("accessId", "localUserId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		localRequest2.setInstanceId("instanceId2");
		localRequest2.setState(RequestState.FULFILLED);
		localRequest2.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(localRequest1);
		requests.add(localRequest2);
		
		// check if there is no instance to take from because there is no served request
		Assert.assertNull(nofPlugin.takeFrom("member2", requests));
	}
	
	@Test
	public void testTakeFromOneServedRequest() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Request servedRequest = new Request("id", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member1");
		servedRequest.setInstanceId("instanceId");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest);
		
		Assert.assertEquals(servedRequest, nofPlugin.takeFrom("member2", requests));
	}
	
	@Test
	public void testNoTakeFromBecauseDebtIsTheSame() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(30);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		Request servedRequest = new Request("id", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member1");
		servedRequest.setInstanceId("instanceId");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest);
		
		Assert.assertNull(nofPlugin.takeFrom("member2", requests));
	}
	
	@Test
	public void testTakeFromMostRecentServedRequest() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request servedRequest1 = new Request("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member1", dateUtils);
		servedRequest1.setInstanceId("instanceId1");
		servedRequest1.setState(RequestState.FULFILLED);
		servedRequest1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Request servedRequest2 = new Request("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member1", dateUtils);
		servedRequest2.setInstanceId("instanceId2");
		servedRequest2.setState(RequestState.FULFILLED);
		servedRequest2.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest1);
		requests.add(servedRequest2);
		
		// checking if take from most recent request
		Assert.assertEquals(servedRequest2, nofPlugin.takeFrom("member2", requests));
	}
	
	@Test
	public void testMoreThanOneTakeFromServedRequest() {
		//mocking accounting
		HashMap<String, ResourceUsage> membersUsage = new HashMap<String, ResourceUsage>();
		ResourceUsage member1Usage = new ResourceUsage("member1");
		member1Usage.addConsumption(20);
		member1Usage.addDonation(10);
		
		ResourceUsage member2Usage = new ResourceUsage("member2");
		member2Usage.addConsumption(30);
		member2Usage.addDonation(10);
		
		membersUsage.put("member1", member1Usage);
		membersUsage.put("member2", member2Usage);
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				membersUsage);
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		
		// mocking dateUtils
		long now = System.currentTimeMillis();
		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		
		Request servedRequest1 = new Request("id1", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member1", dateUtils);
		servedRequest1.setInstanceId("instanceId1");
		servedRequest1.setState(RequestState.FULFILLED);
		servedRequest1.setProvidingMemberId("localMemberId");
		
		// mocking dateUtils
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + 30);
		
		Request servedRequest2 = new Request("id2", new Token("accessId", "remoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member1", dateUtils);
		servedRequest2.setInstanceId("instanceId2");
		servedRequest2.setState(RequestState.FULFILLED);
		servedRequest2.setProvidingMemberId("localMemberId");
		
		List<Request> requests = new ArrayList<Request>();
		requests.add(servedRequest1);
		requests.add(servedRequest2);
		
		// checking if take from most recent request
		Assert.assertEquals(servedRequest2, nofPlugin.takeFrom("member2", requests));
		
		requests.remove(servedRequest2);
		Assert.assertEquals(servedRequest1, nofPlugin.takeFrom("member2", requests));
		
		requests.remove(servedRequest1);
		Assert.assertNull(nofPlugin.takeFrom("member2", requests));
	}
}
