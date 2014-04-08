package org.fogbowcloud.manager.occi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestOCCIApplication {

	private OCCIApplication occiApplication;
	private final String token = RequestHelper.ACCESS_TOKEN;

	@Before
	public void setUp() {
		this.occiApplication = new OCCIApplication();

		HttpResponse response = new DefaultHttpResponseFactory().newHttpResponse(
				new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(response);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.isValidToken(RequestHelper.ACCESS_TOKEN)).thenReturn(true);

		occiApplication.setIdentityPlugin(identityPlugin);
		occiApplication.setComputePlugin(computePlugin);
	}

	@Test
	public void testGetRequestDetails() {
		this.occiApplication.newRequest(this.token, new ArrayList<Category>(),
				new HashMap<String, String>());
		Map<String, List<String>> userToRequestIds = occiApplication.getUserToRequestIds();
		List<String> list = userToRequestIds.get(this.token);
		String requestId = list.get(0);
		Request requestDetails = occiApplication.getRequestDetails(this.token, requestId);
		String id = requestDetails.getId();

		Assert.assertEquals(requestId, id);
	}

	@Test
	public void testResquestUser() {
		this.occiApplication.newRequest(this.token, new ArrayList<Category>(),
				new HashMap<String, String>());
		List<Request> requestsFromUser = occiApplication.getRequestsFromUser(this.token);

		Assert.assertEquals(1, requestsFromUser.size());
	}

	@Test
	public void testManyResquestUser() {
		int valueRequest = 10;
		for (int i = 0; i < valueRequest; i++) {
			this.occiApplication.newRequest(this.token, new ArrayList<Category>(),
					new HashMap<String, String>());
		}
		List<Request> requestsFromUser = occiApplication.getRequestsFromUser(this.token);

		Assert.assertEquals(valueRequest, requestsFromUser.size());
	}

	@Test
	public void testRemoveAllRequest() {
		int valueRequest = 10;
		for (int i = 0; i < valueRequest; i++) {
			this.occiApplication.newRequest(this.token, new ArrayList<Category>(),
					new HashMap<String, String>());
		}
		List<Request> requestsFromUser = this.occiApplication.getRequestsFromUser(this.token);

		Assert.assertEquals(valueRequest, requestsFromUser.size());

		this.occiApplication.removeAllRequests(this.token);
		requestsFromUser = this.occiApplication.getRequestsFromUser(this.token);

		Assert.assertEquals(0, requestsFromUser.size());
	}

	@Test
	public void testRemoveSpecificRequest() {
		int valueRequest = 10;
		for (int i = 0; i < valueRequest; i++) {
			this.occiApplication.newRequest(this.token, new ArrayList<Category>(),
					new HashMap<String, String>());
		}
		List<Request> requestsFromUser = this.occiApplication.getRequestsFromUser(this.token);

		Assert.assertEquals(valueRequest, requestsFromUser.size());

		occiApplication.removeRequest(this.token, requestsFromUser.get(1).getId());
		requestsFromUser = this.occiApplication.getRequestsFromUser(this.token);

		Assert.assertEquals(valueRequest - 1, requestsFromUser.size());
	}
}
