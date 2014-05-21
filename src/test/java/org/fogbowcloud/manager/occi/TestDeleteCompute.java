package org.fogbowcloud.manager.occi;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.util.DefaultDataTest;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteCompute {

	public static String INSTANCE_ID = "1234567ujhgf45hdb4w";
	public static String OTHER_INSTANCE_ID = "otherInstanceId";

	private OCCITestHelper helper;

	@Before
	public void setup() throws Exception {
		this.helper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.doNothing().when(computePlugin).removeInstances(OCCITestHelper.ACCESS_TOKEN);
		Mockito.doNothing().when(computePlugin)
				.removeInstance(OCCITestHelper.ACCESS_TOKEN, INSTANCE_ID);
		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(new Token("1", OCCITestHelper.USER_MOCK, new Date(),
				new HashMap<String, String>()));

		List<Request> requests = new LinkedList<Request>();
		Request request1 = new Request("1", new Token(OCCITestHelper.ACCESS_TOKEN,
				OCCITestHelper.USER_MOCK, DefaultDataTest.TOKEN_FUTURE_EXPIRATION,
				new HashMap<String, String>()), null, null);
		request1.setInstanceId(INSTANCE_ID);
		requests.add(request1);
		Request request2 = new Request("2", new Token("otherToken", "otherUser",
				DefaultDataTest.TOKEN_FUTURE_EXPIRATION, new HashMap<String, String>()), null, null);
		request2.setInstanceId(OTHER_INSTANCE_ID);
		requests.add(request2);

		this.helper.initializeComponentCompute(computePlugin, identityPlugin, requests);
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testDelete() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceOtherUser() throws Exception {
		HttpGet httpGet = new HttpGet(OCCITestHelper.URI_FOGBOW_COMPUTE + OTHER_INSTANCE_ID);
		httpGet.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpGet.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpGet);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificInstanceNotFound() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + "wrong");
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE + INSTANCE_ID);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "wrong");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testEmptyAccessToken() throws Exception {
		HttpDelete httpDelete = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		httpDelete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		httpDelete.addHeader(OCCIHeaders.X_AUTH_TOKEN, "");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(httpDelete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testWrongContentType() throws Exception {
		HttpDelete get = new HttpDelete(OCCITestHelper.URI_FOGBOW_COMPUTE);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "wrong");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
}
