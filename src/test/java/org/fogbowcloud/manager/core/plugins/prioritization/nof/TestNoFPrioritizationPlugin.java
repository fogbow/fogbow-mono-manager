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
		properties.put("nof_prioritize_local", "true");
		
		accountingPlugin = Mockito.mock(AccountingPlugin.class);
	}
	
	@Test
	public void testServedRequestsNull() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "memberId");
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom(newRequest, null));
	}
	
	@Test
	public void testEmptyServedRequests() {
		//mocking accounting
		Mockito.when(accountingPlugin.getMembersUsage()).thenReturn(
				new HashMap<String, ResourceUsage>());
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "memberId");
		
		NoFPrioritizationPlugin nofPlugin = new NoFPrioritizationPlugin(properties, accountingPlugin);
		Assert.assertNull(nofPlugin.takeFrom(newRequest, new ArrayList<Request>()));
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
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member2");
		
		Assert.assertEquals(servedRequest, nofPlugin.takeFrom(newRequest, requests));
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
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member2");
		
		Assert.assertNull(nofPlugin.takeFrom(newRequest, requests));
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
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member2");
		
		// checking if take from most recent request
		Assert.assertEquals(servedRequest2, nofPlugin.takeFrom(newRequest, requests));
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
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newRemoteUserId", null,
				new HashMap<String, String>()), null, null, null, false, "member2");
		
		// checking if take from most recent request
		Assert.assertEquals(servedRequest2, nofPlugin.takeFrom(newRequest, requests));
		
		requests.remove(servedRequest2);
		Assert.assertEquals(servedRequest1, nofPlugin.takeFrom(newRequest, requests));
		
		requests.remove(servedRequest1);
		Assert.assertNull(nofPlugin.takeFrom(newRequest, requests));
	}
	
	@Test
	public void testPrioritizeLocalRequest() {
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
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newLocalUserId", null,
				new HashMap<String, String>()), null, null, null, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		Assert.assertEquals(servedRequest, nofPlugin.takeFrom(newRequest, requests));
	}
	
	@Test
	public void testPrioritizeLocalRequestWithThanOneServedRequest() {
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
		
		Request newRequest = new Request("newID", new Token("newAccessId", "newLocalUserId", null,
				new HashMap<String, String>()), null, null, null, true, DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		
		// checking if take from most recent request
		Assert.assertEquals(servedRequest2, nofPlugin.takeFrom(newRequest, requests));
		
		requests.remove(servedRequest2);
		Assert.assertEquals(servedRequest1, nofPlugin.takeFrom(newRequest, requests));
		
		requests.remove(servedRequest1);
		Assert.assertNull(nofPlugin.takeFrom(newRequest, requests));
	}
}
