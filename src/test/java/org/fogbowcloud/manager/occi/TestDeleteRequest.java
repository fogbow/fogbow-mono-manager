package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.model.FogbowResourceConstants;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.fogbowcloud.manager.occi.plugins.ComputePlugin;
import org.fogbowcloud.manager.occi.plugins.IdentityPlugin;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.engine.adapter.HttpRequest;

public class TestDeleteRequest {

	TestRequestHelper testRequestHelper;

	@Before
	public void setup() throws Exception {
		this.testRequestHelper = new TestRequestHelper();

		HttpResponse response = new DefaultHttpResponseFactory().newHttpResponse(
				new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null), null);

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.any(HttpRequest.class))).thenReturn(
				response);

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.isValidToken(TestRequestHelper.ACCESS_TOKEN)).thenReturn(true);

		this.testRequestHelper.initializeComponent(computePlugin, identityPlugin);
	}

	@Test
	public void testRequest() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteEmptyRequest() throws URISyntaxException, HttpException, IOException {
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);
		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(delete);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Get
		response = client.execute(get);
		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificRequest() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestLocations = TestRequestHelper.getRequestLocations(response);

		Assert.assertEquals(1, requestLocations.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(requestLocations.get(0));
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);

		response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteManyRequestsIndividually() throws URISyntaxException, HttpException,
			IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestLocations = TestRequestHelper.getRequestLocations(response);

		Assert.assertEquals(200, requestLocations.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Delete all requests individually
		HttpDelete delete;
		for (String requestLocation : requestLocations) {
			delete = new HttpDelete(requestLocation);
			delete.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
			delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
			response = client.execute(delete);
			Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		}

		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteAllRequests() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(FogbowResourceConstants.TERM_FOGBOW_REQUEST,
				FogbowResourceConstants.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				FogbowResourceConstants.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestLocations(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Delete
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteRequestNotFound() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST + "/"
				+ "not_found_id");
		delete.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteWithInvalidToken() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, "invalid_token");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.testRequestHelper.stopComponent();
	}
}
