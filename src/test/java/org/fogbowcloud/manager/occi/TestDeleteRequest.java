package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.OCCIHeaders;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestDeleteRequest {

	OCCITestHelper requestHelper;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		this.requestHelper = new OCCITestHelper();

		ComputePlugin computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(computePlugin.requestInstance(Mockito.anyString(), Mockito.any(List.class), Mockito.any(Map.class)))
				.thenReturn("");

		IdentityPlugin identityPlugin = Mockito.mock(IdentityPlugin.class);
//		Mockito.when(identityPlugin.getUser(OCCITestHelper.ACCESS_TOKEN)).thenReturn(OCCITestHelper.USER_MOCK);
//		Mockito.when(identityPlugin.getUser(OCCITestHelper.INVALID_TOKEN)).thenThrow(
//				new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED));

		this.requestHelper.initializeComponent(computePlugin, identityPlugin);
	}

	@Test
	public void testRequest() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteEmptyRequest() throws URISyntaxException, HttpException, IOException {
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);
		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(delete);
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Get
		response = client.execute(get);
		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificRequest() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestLocations = OCCITestHelper.getRequestLocations(response);

		Assert.assertEquals(1, requestLocations.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(requestLocations.get(0));
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		response = client.execute(get);

		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteManyRequestsIndividually() throws URISyntaxException, HttpException,
			IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestLocations = OCCITestHelper.getRequestLocations(response);

		Assert.assertEquals(200, requestLocations.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Delete all requests individually
		HttpDelete delete;
		for (String requestLocation : requestLocations) {
			delete = new HttpDelete(requestLocation);
			delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
			delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
			response = client.execute(delete);
			Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		}

		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteAllRequests() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(OCCITestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(RequestConstants.TERM,
				RequestConstants.SCHEME, OCCIHeaders.KIND_CLASS);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		post.addHeader(OCCIHeaders.CATEGORY, category.toHeader());
		post.addHeader(OCCIHeaders.X_OCCI_ATTRIBUTE,
				RequestAttribute.INSTANCE_COUNT.getValue() + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = OCCITestHelper.getRequestLocations(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Delete
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		// Get
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(0, OCCITestHelper.getRequestLocations(response).size());
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteRequestNotFound() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_REQUEST + "/"
				+ "not_found_id");
		delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteWithInvalidToken() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(OCCITestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(OCCIHeaders.CONTENT_TYPE, OCCITestHelper.CONTENT_TYPE_OCCI);
		delete.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.INVALID_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.requestHelper.stopComponent();
	}
}
