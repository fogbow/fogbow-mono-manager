package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.model.ComputeApplication;
import org.fogbowcloud.manager.occi.model.RequestHelper;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestOCCIApplication {

	private OCCIApplication occiApplication;
	private String instanceLocation = HeaderUtils.X_OCCI_LOCATION + "http://localhost:"
			+ RequestHelper.ENDPOINT_PORT + ComputeApplication.TARGET
			+ "/b122f3ad-503c-4abb-8a55-ba8d90cfce9f";
	private String expectedInstanceId = instanceLocation.replace(HeaderUtils.X_OCCI_LOCATION, "")
			.trim();

	@Before
	public void setUp() {
		this.occiApplication = new OCCIApplication();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(instanceLocation);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.isValidToken(RequestHelper.ACCESS_TOKEN)).thenReturn(true);
		Mockito.when(identityPlugin.getUser(RequestHelper.ACCESS_TOKEN)).thenReturn(
				RequestHelper.USER_MOCK);

		occiApplication.setIdentityPlugin(identityPlugin);
		occiApplication.setComputePlugin(computePlugin);
	}

	@Test
	public void testGetRequestDetails() {
		this.occiApplication.newRequest(RequestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				new HashMap<String, String>());
		occiApplication.newRequest(RequestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				new HashMap<String, String>());
		List<Request> requests = occiApplication.getRequestsFromUser(RequestHelper.USER_MOCK);
		Assert.assertEquals(1, requests.size());
		String requestId = requests.get(0).getId();
		Request requestDetails = occiApplication.getRequestDetails(RequestHelper.ACCESS_TOKEN,
				requestId);

		Assert.assertEquals(requestId, requestDetails.getId());
		Assert.assertEquals(expectedInstanceId, requestDetails.getInstanceId());
		Assert.assertEquals(RequestState.FULFILLED, requestDetails.getState());
	}

	@Test
	public void testResquestUser() {
		this.occiApplication.newRequest(RequestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
				new HashMap<String, String>());
		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(RequestHelper.ACCESS_TOKEN);

		Assert.assertEquals(1, requestsFromUser.size());
	}

	@Test
	public void testManyResquestUser() {
		int valueRequest = 10;
		for (int i = 0; i < valueRequest; i++) {
			this.occiApplication.newRequest(RequestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
					new HashMap<String, String>());
		}
		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(RequestHelper.ACCESS_TOKEN);

		Assert.assertEquals(valueRequest, requestsFromUser.size());
	}

	@Test
	public void testRemoveAllRequest() {
		int numberOfRequests = 10;
		for (int i = 0; i < numberOfRequests; i++) {
			occiApplication.newRequest(RequestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
					new HashMap<String, String>());
		}
		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(RequestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfRequests, requestsFromUser.size());

		occiApplication.removeAllRequests(RequestHelper.ACCESS_TOKEN);
		requestsFromUser = occiApplication.getRequestsFromUser(RequestHelper.ACCESS_TOKEN);

		Assert.assertEquals(0, requestsFromUser.size());
	}

	@Test
	public void testRemoveSpecificRequest() {
		int numberOfRequests = 10;
		for (int i = 0; i < numberOfRequests; i++) {
			occiApplication.newRequest(RequestHelper.ACCESS_TOKEN, new ArrayList<Category>(),
					new HashMap<String, String>());
		}
		List<Request> requestsFromUser = occiApplication
				.getRequestsFromUser(RequestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfRequests, requestsFromUser.size());

		occiApplication.removeRequest(RequestHelper.ACCESS_TOKEN, requestsFromUser.get(1).getId());
		requestsFromUser = occiApplication.getRequestsFromUser(RequestHelper.ACCESS_TOKEN);

		Assert.assertEquals(numberOfRequests - 1, requestsFromUser.size());
	}
}
