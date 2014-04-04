package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestGetRequest {

	private TestRequestHelper testRequestHelper;

	@Before
	public void setup() throws Exception {
		this.testRequestHelper = new TestRequestHelper();

		HttpResponse response = new DefaultHttpResponseFactory().newHttpResponse(
				new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn(response);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.isValidToken(TestRequestHelper.ACCESS_TOKEN)).thenReturn(true);

		testRequestHelper.initializeComponent(computePlugin, identityPlugin);
	}

	@Test
	public void testGetRequest() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestPlainContent() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestInvalidToken() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, "invalid_token");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestTwoIds() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(get);

		Assert.assertEquals(2, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestManyIds() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(get);

		Assert.assertEquals(200, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificRequest() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 1");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.getRequestLocations(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestNotFound() throws URISyntaxException, ParseException, IOException,
			HttpException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST + "/" + "not_found");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetStatusRequest() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM,
				FogbowResourceConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 1");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.getRequestLocations(response).get(0));
		get.addHeader(OCCIHeaders.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		String requestDetails = EntityUtils.toString(response.getEntity(), TestRequestHelper.UTF_8);
		Assert.assertEquals(RequestState.OPEN.getValue(),
				testRequestHelper.getStateFromRequestDetails(requestDetails));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.testRequestHelper.stopComponent();
	}

}
