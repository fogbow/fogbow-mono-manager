package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDeleteRequest {

	TestRequestHelper testRequestHelper;

	@Before
	public void setup() throws Exception {
		this.testRequestHelper = new TestRequestHelper();
		this.testRequestHelper.inicializeComponent();
	}

	@Test
	public void testRequest() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteEmptyRequest() throws URISyntaxException, HttpException, IOException {
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);
		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(delete);
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
		//Get
		response = client.execute(get);
		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteSpecificRequest() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.TERM_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestIds(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
		// Delete
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST + "/"
				+ requestIDs.get(0));
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(delete);

		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);

		response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteManyRequest() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.TERM_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestIds(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());

		
		// Delete
		HttpDelete delete;
		for (String requestId : requestIDs) {
			delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST + "/" + requestId);
			delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
			response = client.execute(delete);
			Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine()
					.getStatusCode());
		}

		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteAllRequest() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.TERM_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestIds(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());

		// Delete
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(delete);
		
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());

		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteRequestNotFound() throws URISyntaxException, HttpException, IOException {
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST + "/"
				+ "invalid_id");
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HeaderConstants.NOT_FOUND_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testDeleteRequestInvalidToken() throws URISyntaxException, HttpException,
			IOException {
		HttpDelete delete = new HttpDelete(TestRequestHelper.URI_FOGBOW_REQUEST);
		delete.addHeader(HeaderConstants.X_AUTH_TOKEN, "invalid_token");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(delete);

		Assert.assertEquals(HeaderConstants.UNAUTHORIZED_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.testRequestHelper.stopComponent();
	}
}
