package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.TestManagerController;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.HeaderUtils;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.util.ComputeApplication;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetRequest {

	private OCCITestHelper requestHelper;
	private String instanceLocation = HeaderUtils.X_OCCI_LOCATION + "http://localhost:" + OCCITestHelper.ENDPOINT_PORT
			+ ComputeApplication.TARGET + "/b122f3ad-503c-4abb-8a55-ba8d90cfce9f";

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.requestHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class),
						Mockito.any(Map.class))).thenReturn(instanceLocation);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getUser(OCCITestHelper.ACCESS_TOKEN)).thenReturn(
				OCCITestHelper.USER_MOCK);
		Mockito.when(identityPlugin.getTokenExpiresDate(OCCITestHelper.ACCESS_TOKEN)).thenReturn(
				TestManagerController.getDateISO8601Format(System.currentTimeMillis() + OCCITestHelper.LONG_TIME));
		Mockito.when(identityPlugin.getUser(OCCITestHelper.INVALID_TOKEN)).thenThrow(
				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		requestHelper.initializeComponent(computePlugin, identityPlugin);
	}

	@Test
	public void testGetRequest() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestPlainContent() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestInvalidToken() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.INVALID_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestTwoIds() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, RequestAttribute.INSTANCE_COUNT.getValue()
				+ " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(get);

		Assert.assertEquals(2, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestManyIds() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, RequestAttribute.INSTANCE_COUNT.getValue()
				+ " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(get);

		Assert.assertEquals(200, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificRequest() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, RequestAttribute.INSTANCE_COUNT.getValue()
				+ " = 1");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.getRequestLocations(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestNotFound() throws URISyntaxException, ParseException, IOException,
			HttpException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST + "/" + "not_found");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetStatusRequest() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM, RequestConstants.SCHEME,
				OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE, RequestAttribute.INSTANCE_COUNT.getValue()
				+ " = 1");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.getRequestLocations(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		String requestDetails = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));
		Assert.assertEquals(RequestState.FULFILLED.getValue(),
				requestHelper.getStateFromRequestDetails(requestDetails));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.requestHelper.stopComponent();
	}

}
