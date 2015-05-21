package org.fogbowcloud.manager.core.plugins.accounting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFCUAccounting {

	private static final double ACCEPTABLE_ERROR = 0.0;
	private BenchmarkingPlugin benchmarkingPlugin;
	FCUAccountingPlugin accountingPlugin;
	Properties properties;

	@Before
	public void setUp() throws IOException {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		properties = new Properties();
		properties.put("accounting_datastore_url", "jdbc:h2:mem:usage");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, "localMemberId");

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
		accountingPlugin.getDatabase().dispose();
	}

	@After
	public void tearDown() throws IOException {
		accountingPlugin.getDatabase().dispose();
	}

	@Test
	public void testEmptyRequests() {
		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);
		accountingPlugin.update(new ArrayList<Request>());

		Assert.assertNotNull(accountingPlugin.getMembersUsage());
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().size());
		Assert.assertNotNull(accountingPlugin.getUsersUsage());
	}

	@Test
	public void testOneRequestFulfielledByRemoteAndEmptyServedRequests() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@remoteMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId("remoteMemberId");
		request1.setInstanceId("instanceId");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId@remoteMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(),
				ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneRequestFulfielledByLocalAndEmptyServedRequests() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId("instanceId");
		request1.setProvidingMemberId("localMemberId");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;

		Assert.assertNotNull(accountingPlugin.getMembersUsage());
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().size());
		Assert.assertNotNull(accountingPlugin.getUsersUsage());
		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testSomeRequestsFulfielledByLocalAndEmptyServedRequests() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(4d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId("instanceId1");
		request1.setProvidingMemberId("localMemberId");
		
		Request request2 = new Request("id2", new Token("accessId", "userId2", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request2.setDateUtils(dateUtils);
		request2.setState(RequestState.FULFILLED);
		request2.setInstanceId("instanceId2");
		request2.setProvidingMemberId("localMemberId");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);
		requests.add(request2);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests);

		// checking usage
		double expectedConsumptionForUser1 = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		double expectedConsumptionForUser2 = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertNotNull(accountingPlugin.getMembersUsage());
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().size());
		Assert.assertNotNull(accountingPlugin.getUsersUsage());
		Assert.assertEquals(2, accountingPlugin.getUsersUsage().size());
		Assert.assertEquals(expectedConsumptionForUser1,
				accountingPlugin.getUsersUsage().get("userId1"), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedConsumptionForUser2,
				accountingPlugin.getUsersUsage().get("userId2"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testEmptyRequestAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId");
		servedRequest.setProvidingMemberId("localMemberId");

		List<Request> servedRequests = new ArrayList<Request>();
		servedRequests.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(servedRequests);

		// checking usage
		double expectedDonation = benchmarkingPlugin.getPower("instanceId@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneRequestFulfilledByRemoteAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId", dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId("remoteMemberId");
		request1.setInstanceId("instanceId1");
		
		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");

		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 2;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneRequestFulfilledByLocalAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId("instanceId1");
		request1.setProvidingMemberId("localMemberId");

		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
				
		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0.0, accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(), ACCEPTABLE_ERROR);
		
		Assert.assertEquals(1,  accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedConsumption, accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRequestsFulfilledByRemoteAndServedRequestMoreThanOneMember() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId1")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMember")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId("remoteMemberId1");
		request1.setInstanceId("instanceId1");

		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId2", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
		
		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage
		double expectedConsumptionFromMember1 = benchmarkingPlugin.getPower("instanceId1@remoteMemberId1") * 2;
		double expectedDonationForMember2 = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(2, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId1"));
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId2"));
		Assert.assertEquals(expectedConsumptionFromMember1, accountingPlugin.getMembersUsage()
				.get("remoteMemberId1").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId1").getDonated(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId2").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember2,
				accountingPlugin.getMembersUsage().get("remoteMemberId2").getDonated(), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRequestsFulfilledByLocalAndServedRequestMoreThanOneMember() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId("instanceId1");
		request1.setProvidingMemberId("localMemberId");

		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");

		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage
		double expectedConsumptionForUserId = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 2;
		double expectedDonationForMember2 = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 2;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember2,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(), ACCEPTABLE_ERROR);

		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedConsumptionForUserId,
				accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}

	@Test
	public void testRequestFulfilledByRemoteAndServedRequestMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId("remoteMemberId");
		request1.setInstanceId("instanceId1");

		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);
		
		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");

		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage is considering 7 minutes
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 7;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 7;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRequestsFulfilledAndServedRequestMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@remoteMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId3@localMemberId")).thenReturn(5d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setProvidingMemberId("remoteMemberId");
		request1.setInstanceId("instanceId1");
		
		Request request2 = new Request("id2", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request2.setDateUtils(dateUtils);
		request2.setState(RequestState.FULFILLED);
		request2.setInstanceId("instanceId3");
		request2.setProvidingMemberId("localMemberId");

		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);
		requestsWithInstance.add(request2);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
		
		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage is considering 7 minutes
		double expectedConsumptionForMember = benchmarkingPlugin.getPower("instanceId1@remoteMemberId") * 7;
		double expectedDonationForMember = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 7;
		double expectedDonationForUser = benchmarkingPlugin.getPower("instanceId3@localMemberId") * 7;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(expectedConsumptionForMember,
				accountingPlugin.getMembersUsage().get("remoteMemberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getDonated(), ACCEPTABLE_ERROR);
		
		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedDonationForUser, accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
	
	@Test
	public void testRequestFulfilledByLocalAndServedRequestMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1@localMemberId")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2@localMemberId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", new Token("accessId", "userId", null,
				new HashMap<String, String>()), null, null, null, true, "localMemberId");
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setInstanceId("instanceId1");
		request1.setProvidingMemberId("localMemberId");

		List<Request> requestsWithInstance = new ArrayList<Request>();
		requestsWithInstance.add(request1);

		Request servedRequest = new Request("instanceToken", new Token("accessId", "userId1", null,
				new HashMap<String, String>()), null, null, null, false, "remoteMemberId", dateUtils);
		servedRequest.setInstanceId("instanceId2");
		servedRequest.setState(RequestState.FULFILLED);
		servedRequest.setProvidingMemberId("localMemberId");
		
		requestsWithInstance.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requestsWithInstance);

		// checking usage is considering 7 minutes
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1@localMemberId") * 7;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2@localMemberId") * 7;

		Assert.assertEquals(1, accountingPlugin.getMembersUsage().size());
		Assert.assertTrue(accountingPlugin.getMembersUsage().containsKey("remoteMemberId"));
		Assert.assertEquals(0, accountingPlugin.getMembersUsage().get("remoteMemberId")
				.getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMembersUsage().get("remoteMemberId").getDonated(),
				ACCEPTABLE_ERROR);

		Assert.assertEquals(1, accountingPlugin.getUsersUsage().size());
		Assert.assertTrue(accountingPlugin.getUsersUsage().containsKey("userId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getUsersUsage().get("userId"), ACCEPTABLE_ERROR);
	}
}
