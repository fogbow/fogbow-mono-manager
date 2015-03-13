package org.fogbowcloud.manager.core.plugins.accounting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.FCUAccountingPlugin;
import org.fogbowcloud.manager.occi.core.Category;
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
	private final String DATASTORE_PATH = "src/test/resources/accounting";
	FCUAccountingPlugin accountingPlugin;
	Properties properties;

	@Before
	public void setUp() throws IOException {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);

		properties = new Properties();
		properties.put(ConfigurationConstants.ACCOUNTING_DATASTORE_URL_KEY, "jdbc:h2:mem:"
				+ new File(DATASTORE_PATH).getAbsolutePath() + "usage");
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.cleanDirectory(new File(DATASTORE_PATH));
		accountingPlugin.getDatabase().dispose();
	}

	@Test
	public void testEmptyRequests() {
		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin);

		accountingPlugin.update(new ArrayList<Request>(), new ArrayList<ServedRequest>());

		ArrayList<String> memberIds = new ArrayList<String>();
		memberIds.add("memberId");

		Assert.assertNotNull(accountingPlugin.getMemberUsage(memberIds));
		Assert.assertEquals(0, accountingPlugin.getMemberUsage(memberIds).size());
	}

	@Test
	public void testOneRequestEmptyServedRequests() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", null, null, null, null);
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setMemberId("memberId");
		request1.setInstanceId("instanceId");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests, new ArrayList<ServedRequest>());

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId") * 2;

		ArrayList<String> memberIds = new ArrayList<String>();
		memberIds.add("memberId");

		Assert.assertEquals(1, accountingPlugin.getMemberUsage(memberIds).size());
		Assert.assertTrue(accountingPlugin.getMemberUsage(memberIds).containsKey("memberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMemberUsage(memberIds).get("memberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMemberUsage(memberIds).get("memberId").getDonated(),
				ACCEPTABLE_ERROR);
	}

	@Test
	public void testEmptyRequestAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		ServedRequest servedRequest = new ServedRequest("instanceToken", "instanceId", "memberId",
				new ArrayList<Category>(), new HashMap<String, String>(), dateUtils);
		List<ServedRequest> servedRequests = new ArrayList<ServedRequest>();
		servedRequests.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMili = 1000 * 60 * 2;
		now += twoMinutesInMili;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(new ArrayList<Request>(), servedRequests);

		// checking usage
		double expectedDonation = benchmarkingPlugin.getPower("instanceId") * 2;

		ArrayList<String> memberIds = new ArrayList<String>();
		memberIds.add("memberId");

		Assert.assertEquals(1, accountingPlugin.getMemberUsage(memberIds).size());
		Assert.assertTrue(accountingPlugin.getMemberUsage(memberIds).containsKey("memberId"));
		Assert.assertEquals(0, accountingPlugin.getMemberUsage(memberIds).get("memberId").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMemberUsage(memberIds).get("memberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testOneRequestAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", null, null, null, null);
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setMemberId("memberId");
		request1.setInstanceId("instanceId1");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		ServedRequest servedRequest = new ServedRequest("instanceToken", "instanceId2", "memberId",
				new ArrayList<Category>(), new HashMap<String, String>(), dateUtils);
		List<ServedRequest> servedRequests = new ArrayList<ServedRequest>();
		servedRequests.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests, servedRequests);

		// checking usage
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1") * 2;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2") * 2;

		ArrayList<String> memberIds = new ArrayList<String>();
		memberIds.add("memberId");

		Assert.assertEquals(1, accountingPlugin.getMemberUsage(memberIds).size());
		Assert.assertTrue(accountingPlugin.getMemberUsage(memberIds).containsKey("memberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMemberUsage(memberIds).get("memberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMemberUsage(memberIds).get("memberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testRequestsAndServedRequestMoreThanOneMember() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1")).thenReturn(4d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", null, null, null, null);
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setMemberId("memberId1");
		request1.setInstanceId("instanceId1");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		ServedRequest servedRequest = new ServedRequest("instanceToken", "instanceId2",
				"memberId2", new ArrayList<Category>(), new HashMap<String, String>(), dateUtils);
		List<ServedRequest> servedRequests = new ArrayList<ServedRequest>();
		servedRequests.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests, servedRequests);

		// checking usage
		double expectedConsumptionFromMember1 = benchmarkingPlugin.getPower("instanceId1") * 2;
		double expectedDonationForMember2 = benchmarkingPlugin.getPower("instanceId2") * 2;

		ArrayList<String> memberIds = new ArrayList<String>();
		memberIds.add("memberId1");
		memberIds.add("memberId2");

		Assert.assertEquals(2, accountingPlugin.getMemberUsage(memberIds).size());
		Assert.assertTrue(accountingPlugin.getMemberUsage(memberIds).containsKey("memberId1"));
		Assert.assertTrue(accountingPlugin.getMemberUsage(memberIds).containsKey("memberId2"));
		Assert.assertEquals(expectedConsumptionFromMember1, accountingPlugin.getMemberUsage(memberIds)
				.get("memberId1").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMemberUsage(memberIds).get("memberId1").getDonated(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(0, accountingPlugin.getMemberUsage(memberIds).get("memberId2").getConsumed(),
				ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonationForMember2,
				accountingPlugin.getMemberUsage(memberIds).get("memberId2").getDonated(), ACCEPTABLE_ERROR);
	}

	@Test
	public void testRequestAndServedRequestMoreThanOneUpdate() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2")).thenReturn(2d);

		accountingPlugin = new FCUAccountingPlugin(properties, benchmarkingPlugin, dateUtils);

		Request request1 = new Request("id1", null, null, null, null);
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setMemberId("memberId");
		request1.setInstanceId("instanceId1");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		ServedRequest servedRequest = new ServedRequest("instanceToken", "instanceId2", "memberId",
				new ArrayList<Category>(), new HashMap<String, String>(), dateUtils);
		List<ServedRequest> servedRequests = new ArrayList<ServedRequest>();
		servedRequests.add(servedRequest);

		// updating dateUtils
		long twoMinutesInMilli = 1000 * 60 * 2;
		now += twoMinutesInMilli;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests, servedRequests);

		// updating dateUtils
		now += 1000 * 60 * 5; // adding grain Time (5 min)
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);

		accountingPlugin.update(requests, servedRequests);

		// checking usage is considering 7 minutes
		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1") * 7;
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2") * 7;

		ArrayList<String> memberIds = new ArrayList<String>();
		memberIds.add("memberId");

		Assert.assertEquals(1, accountingPlugin.getMemberUsage(memberIds).size());
		Assert.assertTrue(accountingPlugin.getMemberUsage(memberIds).containsKey("memberId"));
		Assert.assertEquals(expectedConsumption,
				accountingPlugin.getMemberUsage(memberIds).get("memberId").getConsumed(), ACCEPTABLE_ERROR);
		Assert.assertEquals(expectedDonation, accountingPlugin.getMemberUsage(memberIds).get("memberId")
				.getDonated(), ACCEPTABLE_ERROR);
	}

}
