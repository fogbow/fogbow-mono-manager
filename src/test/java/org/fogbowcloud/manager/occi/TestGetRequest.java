package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.occi.core.RequestState;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestGetRequest {

	private TestRequestHelper testRequestHelper;

	@Before
	public void setup() throws Exception {
		this.testRequestHelper = new TestRequestHelper();
		testRequestHelper.inicializeComponent();
	}

	@Test
	public void testGetRequest() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestPlainContent() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, "text/plain");
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(0, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestInvalidTOken() throws URISyntaxException, HttpException, IOException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, "invalid_token");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HeaderConstants.UNAUTHORIZED_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestTwoIds() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(get);
		
		Assert.assertEquals(2, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetResquestManyIds() throws URISyntaxException, HttpException, IOException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST);
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		client = new DefaultHttpClient();
		response = client.execute(get);

		Assert.assertEquals(200, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetSpecificRequest() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 1");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST + "/"
				+ TestRequestHelper.getRequestIds(response).get(0));
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetRequestNotFound() throws URISyntaxException, ParseException, IOException,
			HttpException {
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST + "/" + "not_found");
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HeaderConstants.NOT_FOUND_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetStatusRequest() throws URISyntaxException, ParseException, IOException,
			HttpException {
		// Post
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST + " = 1");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		// Get
		HttpGet get = new HttpGet(TestRequestHelper.URI_FOGBOW_REQUEST + "/"
				+ TestRequestHelper.getRequestIds(response).get(0));
		get.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		get.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(), TestRequestHelper.UTF_8);
		Assert.assertEquals(RequestState.OPEN.getValue(), responseStr);
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.testRequestHelper.stopComponent();
	}

}
