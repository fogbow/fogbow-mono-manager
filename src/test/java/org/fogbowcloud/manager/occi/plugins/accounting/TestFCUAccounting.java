package org.fogbowcloud.manager.occi.plugins.accounting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.accounting.FCUAccountingPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestFCUAccounting {

	private BenchmarkingPlugin benchmarkingPlugin;

	@Before
	public void setUp() {
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
	}

	@Test
	public void testEmptyRequests() {
		FCUAccountingPlugin accountingPlugin = new FCUAccountingPlugin(benchmarkingPlugin);

		accountingPlugin.update(new ArrayList<Request>(), new ArrayList<ServedRequest>());

		Assert.assertEquals(AccountingPlugin.UNDEFINED,
				accountingPlugin.getConsumption("memberId"), 0.0);
		Assert.assertEquals(AccountingPlugin.UNDEFINED, accountingPlugin.getDonation("memberId"),
				0.0);
	}

	@Test
	public void testOneRequestEmptyServedRequests() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId")).thenReturn(2d);

		FCUAccountingPlugin accountingPlugin = new FCUAccountingPlugin(benchmarkingPlugin,
				dateUtils);

		Request request1 = new Request("id1", null, null, null, null);
		request1.setDateUtils(dateUtils);
		request1.setState(RequestState.FULFILLED);
		request1.setMemberId("memberId");
		request1.setInstanceId("instanceId");

		List<Request> requests = new ArrayList<Request>();
		requests.add(request1);

		long twoMinutes = 1000 * 60 * 2;

		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + twoMinutes);

		accountingPlugin.update(requests, new ArrayList<ServedRequest>());

		double expectedConsumption = benchmarkingPlugin.getPower("instanceId")
				* (twoMinutes / 1000 * 60);

		Assert.assertEquals(expectedConsumption, accountingPlugin.getConsumption("memberId"), 0.0);
		Assert.assertEquals(0, accountingPlugin.getDonation("memberId"), 0.0);
	}
	
	@Test
	public void testEmptyRequestAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId")).thenReturn(2d);

		FCUAccountingPlugin accountingPlugin = new FCUAccountingPlugin(benchmarkingPlugin,
				dateUtils);

		ServedRequest servedRequest = new ServedRequest("instanceToken", "instanceId", "memberId",
				new ArrayList<Category>(), new HashMap<String, String>(), dateUtils);
		List<ServedRequest> servedRequests = new ArrayList<ServedRequest>();
		servedRequests.add(servedRequest);

		// updating dateUtils
		long twoMinutes = 1000 * 60 * 2;
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + twoMinutes);

		accountingPlugin.update(new ArrayList<Request>(), servedRequests);

		double expectedDonation = benchmarkingPlugin.getPower("instanceId")
				* (twoMinutes / 1000 * 60);

		Assert.assertEquals(0, accountingPlugin.getConsumption("memberId"), 0.0);
		Assert.assertEquals(expectedDonation, accountingPlugin.getDonation("memberId"), 0.0);
	}

	@Test
	public void testOneRequestAndOneServedRequest() {
		long now = System.currentTimeMillis();

		DateUtils dateUtils = Mockito.mock(DateUtils.class);
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now);
		Mockito.when(benchmarkingPlugin.getPower("instanceId1")).thenReturn(2d);
		Mockito.when(benchmarkingPlugin.getPower("instanceId2")).thenReturn(2d);

		FCUAccountingPlugin accountingPlugin = new FCUAccountingPlugin(benchmarkingPlugin,
				dateUtils);

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
		Mockito.when(dateUtils.currentTimeMillis()).thenReturn(now + twoMinutesInMilli);

		accountingPlugin.update(requests, servedRequests);

		double expectedConsumption = benchmarkingPlugin.getPower("instanceId1") * (twoMinutesInMilli / 1000 * 60);
		double expectedDonation = benchmarkingPlugin.getPower("instanceId2")	* (twoMinutesInMilli / 1000 * 60);

		Assert.assertEquals(expectedConsumption, accountingPlugin.getConsumption("memberId"), 0.0);
		Assert.assertEquals(expectedDonation, accountingPlugin.getDonation("memberId"), 0.0);
	}

}
