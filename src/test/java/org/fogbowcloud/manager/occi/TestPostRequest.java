package org.fogbowcloud.manager.occi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.HeaderConstants;
import org.fogbowcloud.manager.occi.model.TestRequestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestPostRequest {

	private TestRequestHelper testRequestHelper;

	@Before
	public void setup() throws Exception {
		this.testRequestHelper = new TestRequestHelper();
		this.testRequestHelper.inicializeComponent();
	}

	@Test
	public void testPostRequest() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestIds(response);

		Assert.assertEquals(1, requestIDs.size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestTwoInstances() throws URISyntaxException, HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST
				+ " = 2");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestIds(response);

		Assert.assertEquals(2, requestIDs.size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostRequestManyInstances() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST
				+ " = 200");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);
		List<String> requestIDs = TestRequestHelper.getRequestIds(response);

		Assert.assertEquals(200, requestIDs.size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryTermRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category("wrong", Request.SCHEME_FOGBOW_REQUEST,
				HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostInvalidCategorySchemeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				"http://schemas.fogbowcloud.org/wrong#", HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostInvalidCategoryClassRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, "mixin");
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostInvalidAttributeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST
				+ " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostInvalidAcessTokenRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, "invalid_acess_token");
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.UNAUTHORIZED_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostInvalidContentTypeRequest() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, "text/plain");
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeInstances() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST
				+ " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeType() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_TYPE_FOGBOW_REQUEST
				+ " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeValidFrom() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST
				+ " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostRequestInvalidAttributeValidUntil() throws URISyntaxException,
			HttpException, IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST + " =\"x\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HeaderConstants.BAD_REQUEST_RESPONSE, response.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void testPostRequestAllAttributes() throws URISyntaxException, HttpException,
			IOException {
		HttpPost post = new HttpPost(TestRequestHelper.URI_FOGBOW_REQUEST);
		Category category = new Category(Request.TERM_FOGBOW_REQUEST,
				Request.SCHEME_FOGBOW_REQUEST, HeaderConstants.KIND_CLASS);
		post.addHeader(HeaderConstants.CONTENT_TYPE, TestRequestHelper.CONTENT_TYPE_OCCI);
		post.addHeader(HeaderConstants.X_AUTH_TOKEN, TestRequestHelper.ACCESS_TOKEN);
		post.addHeader(HeaderConstants.CATEGORY, category.getHeaderFormat());
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_INSTANCE_FOGBOW_REQUEST
				+ "=10");
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_TYPE_FOGBOW_REQUEST
				+ "=\"one-time\"");
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE,
				Request.ATRIBUTE_VALID_UNTIL_FOGBOW_REQUEST + "=\"2014-04-01\"");
		post.addHeader(HeaderConstants.X_OCCI_ATTRIBUTE, Request.ATRIBUTE_VALID_FROM_FOGBOW_REQUEST
				+ "=\"2014-03-30\"");
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(10, TestRequestHelper.getRequestIds(response).size());
		Assert.assertEquals(HeaderConstants.OK_RESPONSE, response.getStatusLine().getStatusCode());
	}

	@After
	public void tearDown() throws Exception {
		this.testRequestHelper.stopComponent();
	}
}
